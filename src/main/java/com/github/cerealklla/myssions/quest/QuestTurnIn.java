package com.github.cerealklla.myssions.quest;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.github.cerealklla.myssions.registration.ModAttachments;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;

/**
 * All server-side "talking to a quest giver" logic -- called from the always-registered NPC/sign
 * interact listeners (shift+right-click, {@code bridge.QuestNpcInteractListener}/
 * {@code QuestSignInteractListener}) and from Settlemynts' own "Press H" chat menu (via {@code
 * api.Myssions}). Three real operations, kept separate rather than one ambiguous "interact" (the
 * old shape, before the chat-menu rework): {@link #getMenu} (read-only), {@link #acceptBoardOffer}
 * (mutating), {@link #completeQuest} (mutating). {@link #quickInteract} is the shift+right-click
 * convenience that picks one of those for you.
 */
public final class QuestTurnIn {

    private QuestTurnIn() {
    }

    /** Read-only snapshot of this plot's quest-giver menu for {@code player} right now -- see {@link QuestGiverMenu}'s own doc. */
    public static QuestGiverMenu getMenu(ServerPlayer player, UUID plotId, String zoneTypeId) {
        List<Identifier> pool = IssuerQuestPools.poolFor(zoneTypeId);
        if (pool.isEmpty()) {
            return QuestGiverMenu.EMPTY;
        }
        var data = player.getData(ModAttachments.QUEST_DATA);

        List<QuestGiverMenu.ActiveEntry> activeEntries = new ArrayList<>();
        for (QuestInstance instance : data.quests().values()) {
            boolean isActive = instance.status() == QuestStatus.IN_PROGRESS || instance.status() == QuestStatus.AWAITING_REWARD_PICKUP;
            if (!isActive || !pool.contains(instance.definitionId())) {
                continue;
            }
            QuestDefinition definition = QuestRegistry.get(instance.definitionId()).orElse(null);
            if (definition == null) {
                continue;
            }
            // Already past its milestones and just waiting on reward pickup -- "ready" unconditionally,
            // clicking Complete Quest reopens the reward GUI rather than re-checking anything.
            boolean ready = instance.status() == QuestStatus.AWAITING_REWARD_PICKUP || isReadyToComplete(player, definition, instance);
            boolean needsTalk = !ready && hasIncompleteTalkMilestone(definition, instance);
            activeEntries.add(new QuestGiverMenu.ActiveEntry(instance.instanceId(), definition.name(), ready, needsTalk));
        }

        List<QuestGiverMenu.NewOffer> newOffers = new ArrayList<>();
        IssuerQuestBoard board = IssuerQuestBoard.get(player.level().getServer());
        for (Identifier offeredId : board.get(plotId)) {
            if (data.quests().values().stream().anyMatch(i -> i.definitionId().equals(offeredId))) {
                continue; // Already has a record of this one -- stale for this player, leave it for someone else.
            }
            QuestDefinition definition = QuestRegistry.get(offeredId).orElse(null);
            if (definition != null) {
                newOffers.add(new QuestGiverMenu.NewOffer(offeredId, definition.name(), definition.description()));
            }
        }
        return new QuestGiverMenu(activeEntries, newOffers);
    }

    /** True if every non-optional milestone is already done, or would be done by a deliver milestone consuming proof the player is currently holding -- i.e. what {@link #completeQuest} would actually achieve right now. */
    private static boolean isReadyToComplete(ServerPlayer player, QuestDefinition definition, QuestInstance instance) {
        for (QuestMilestone milestone : definition.milestones()) {
            if (milestone.optional() || instance.isMilestoneComplete(milestone)) {
                continue;
            }
            if (milestone.isBuyMilestone()) {
                Optional<Identifier> itemId = BuyItemTargets.get(player.level().getServer()).get(instance.instanceId())
                        .map(BuyItemTargets.Target::itemId);
                if (itemId.isPresent() && QuestEngine.hasEnoughRealItem(player, itemId.get(), milestone.requiredCount())) {
                    continue;
                }
                return false;
            }
            if (milestone.isGatherMilestone()) {
                Optional<Identifier> itemId = milestone.proofId().flatMap(s -> Optional.ofNullable(Identifier.tryParse(s)));
                if (itemId.isPresent() && QuestEngine.hasEnoughRealItem(player, itemId.get(), milestone.requiredCount())) {
                    continue;
                }
                return false;
            }
            if (milestone.isDeliverMilestone() && milestone.proofId().isPresent()
                    && QuestEngine.hasEnoughProof(player, milestone.proofId().get(), milestone.requiredCount())) {
                continue;
            }
            return false;
        }
        return true;
    }

    /** True if any non-optional, not-yet-complete milestone of this instance is specifically a talk milestone -- see {@link QuestGiverMenu.ActiveEntry#needsTalk()}'s own doc. */
    private static boolean hasIncompleteTalkMilestone(QuestDefinition definition, QuestInstance instance) {
        for (QuestMilestone milestone : definition.milestones()) {
            if (!milestone.optional() && !instance.isMilestoneComplete(milestone) && milestone.isTalkMilestone()) {
                return true;
            }
        }
        return false;
    }

    /**
     * Accepts one specific offer (by definition id) sitting on the plot's board -- "I've got a job
     * that needs doing" -> pick one -> "Accept" (or the board-offer branch of {@link
     * #quickInteract}). Removes just that one offer (the board's other offers, if any, are
     * untouched -- see {@code IssuerQuestBoard}'s own doc, widened 2026-10-11 from a single-slot
     * board) and fires the quest's own assignment hook (see {@code QuestAssignmentHooks}' own doc)
     * on success.
     */
    public static Optional<UUID> acceptBoardOffer(ServerPlayer player, UUID plotId, Identifier questId) {
        IssuerQuestBoard board = IssuerQuestBoard.get(player.level().getServer());
        if (!board.get(plotId).contains(questId)) {
            player.sendSystemMessage(Component.literal("I don't have anything for you right now."));
            return Optional.empty();
        }
        var data = player.getData(ModAttachments.QUEST_DATA);
        // Only blocks a genuinely still-active instance of this exact definition -- repeating a
        // quest you've already completed/failed is allowed (explicit 2026-10-12 user choice: "Allow
        // unlimited repeats"). The previous version blocked on ANY record at all (in progress,
        // completed, OR failed), which meant a quest could only ever be accepted once per player,
        // forever -- real report: with only ~11 quests per profession, a player who'd completed a
        // handful kept hitting dead "I don't have anything new for you right now" offers whenever the
        // nightly board-roll happened to pick something they'd already done, with no way to ever
        // clear that.
        boolean alreadyActive = data.quests().values().stream()
                .anyMatch(i -> i.definitionId().equals(questId)
                        && (i.status() == QuestStatus.IN_PROGRESS || i.status() == QuestStatus.AWAITING_REWARD_PICKUP));
        if (alreadyActive) {
            player.sendSystemMessage(Component.literal("You're already working on that one."));
            return Optional.empty();
        }
        Optional<UUID> instanceId = QuestEngine.assignQuest(player, questId);
        instanceId.ifPresent(id -> {
            board.remove(plotId, questId);
            QuestAssignmentHooks.fire(player, id, questId, plotId);
        });
        return instanceId;
    }

    /**
     * "Complete Quest" on the chat menu's active-quest sub-screen -- see {@code
     * QuestEngine#tryCompleteQuest}'s own doc for exactly what "complete" means (best-effort deliver,
     * then check every milestone is actually done). Sends a real message either way.
     */
    public static boolean completeQuest(ServerPlayer player, UUID instanceId) {
        boolean completed = QuestEngine.tryCompleteQuest(player, instanceId);
        if (!completed) {
            player.sendSystemMessage(Component.literal("You still have more to do before this is finished."));
        }
        return completed;
    }

    /**
     * Shift+right-click convenience (unchanged UX from before the chat-menu rework): try to
     * complete (or reopen the reward GUI for) the first active quest from this issuer's pool; if
     * none are ready but at least one is in progress, just say so; otherwise fall through to
     * offering/accepting whatever's on the board (now possibly several -- picks the first one not
     * already held, same "just grab something" convenience this shortcut has always been).
     */
    public static void quickInteract(ServerPlayer player, UUID plotId, String zoneTypeId) {
        List<Identifier> pool = IssuerQuestPools.poolFor(zoneTypeId);
        if (!pool.isEmpty()) {
            var data = player.getData(ModAttachments.QUEST_DATA);
            List<QuestInstance> active = data.quests().values().stream()
                    .filter(i -> (i.status() == QuestStatus.IN_PROGRESS || i.status() == QuestStatus.AWAITING_REWARD_PICKUP)
                            && pool.contains(i.definitionId()))
                    .toList();
            for (QuestInstance instance : active) {
                if (QuestEngine.tryCompleteQuest(player, instance.instanceId())) {
                    return;
                }
            }
            if (!active.isEmpty()) {
                player.sendSystemMessage(Component.literal("Still working on that for me? Come back once you're done."));
                return;
            }
        }
        List<Identifier> offers = IssuerQuestBoard.get(player.level().getServer()).get(plotId);
        if (offers.isEmpty()) {
            player.sendSystemMessage(Component.literal("I don't have anything for you right now."));
            return;
        }
        acceptBoardOffer(player, plotId, offers.get(0));
    }
}
