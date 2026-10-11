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
            activeEntries.add(new QuestGiverMenu.ActiveEntry(instance.instanceId(), definition.name(), ready));
        }

        Optional<QuestGiverMenu.NewOffer> newOffer = Optional.empty();
        IssuerQuestBoard board = IssuerQuestBoard.get(player.level().getServer());
        Identifier offeredId = board.get(plotId).orElse(null);
        if (offeredId != null && data.quests().values().stream().noneMatch(i -> i.definitionId().equals(offeredId))) {
            QuestDefinition definition = QuestRegistry.get(offeredId).orElse(null);
            if (definition != null) {
                newOffer = Optional.of(new QuestGiverMenu.NewOffer(offeredId, definition.name(), definition.description()));
            }
        }
        return new QuestGiverMenu(activeEntries, newOffer);
    }

    /** True if every non-optional milestone is already done, or would be done by a deliver milestone consuming proof the player is currently holding -- i.e. what {@link #completeQuest} would actually achieve right now. */
    private static boolean isReadyToComplete(ServerPlayer player, QuestDefinition definition, QuestInstance instance) {
        for (QuestMilestone milestone : definition.milestones()) {
            if (milestone.optional() || instance.isMilestoneComplete(milestone)) {
                continue;
            }
            if (milestone.isDeliverMilestone() && milestone.proofId().isPresent()
                    && QuestEngine.hasEnoughProof(player, milestone.proofId().get(), milestone.requiredCount())) {
                continue;
            }
            return false;
        }
        return true;
    }

    /**
     * Accepts the plot's current board offer -- "I've got a job that needs doing" -> "Accept" (or
     * the board-offer branch of {@link #quickInteract}). Clears the board slot and fires the
     * quest's own assignment hook (see {@code QuestAssignmentHooks}' own doc) on success.
     */
    public static Optional<UUID> acceptBoardOffer(ServerPlayer player, UUID plotId) {
        IssuerQuestBoard board = IssuerQuestBoard.get(player.level().getServer());
        Identifier offeredId = board.get(plotId).orElse(null);
        if (offeredId == null) {
            player.sendSystemMessage(Component.literal("I don't have anything for you right now."));
            return Optional.empty();
        }
        var data = player.getData(ModAttachments.QUEST_DATA);
        if (data.quests().values().stream().anyMatch(i -> i.definitionId().equals(offeredId))) {
            // Already has a record of it (in progress/completed/failed) -- the board slot is stale
            // for this player specifically; leave it on the board for someone else.
            player.sendSystemMessage(Component.literal("I don't have anything new for you right now."));
            return Optional.empty();
        }
        Optional<UUID> instanceId = QuestEngine.assignQuest(player, offeredId);
        instanceId.ifPresent(id -> {
            board.clear(plotId);
            QuestAssignmentHooks.fire(player, id, offeredId, plotId);
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
     * offering/accepting whatever's on the board.
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
        acceptBoardOffer(player, plotId);
    }
}
