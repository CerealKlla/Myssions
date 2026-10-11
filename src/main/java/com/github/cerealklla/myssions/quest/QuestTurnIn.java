package com.github.cerealklla.myssions.quest;

import java.util.List;
import java.util.UUID;

import com.github.cerealklla.myssions.registration.ModAttachments;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;

/**
 * Shared "talk to the quest giver" logic, called from both the NPC interact listener ({@code
 * bridge.QuestNpcInteractListener}, Lumberjack/Farmer) and the sign interact listener ({@code
 * bridge.QuestSignInteractListener}, standing in for a Mayor NPC that doesn't exist -- see that
 * class's own doc). Two cases, checked in order:
 *
 * <ol>
 *   <li>The player already has an in-progress quest from this issuer's own pool -- if it has a
 *   ready "deliver" milestone and they're holding enough proof, turn it in; otherwise just a
 *   flavor message (nothing silently happens).</li>
 *   <li>Otherwise, offer whatever's currently sitting on *this specific plot's* {@link
 *   IssuerQuestBoard} slot (added 2026-10-10, explicit user spec: "at midnight, NPCs who do not
 *   currently have a quest available will decide with a 50% chance to generate a quest. Upon a
 *   player accepting a quest it will be removed from the NPC so other players can't take it.") --
 *   an empty slot means nothing to offer at all; no fallback to "just pick anything from the
 *   pool," since that would defeat the whole point of the board being a scarce, per-plot resource.
 *   Accepting clears the board slot, same transaction.</li>
 * </ol>
 */
public final class QuestTurnIn {

    private QuestTurnIn() {
    }

    /**
     * Read-only mirror of {@link #interact}'s own branching -- never mutates anything, so other
     * mods' UI (e.g. Settlemynts' "Press H" wishlist screen, 2026-10-10) can show what interacting
     * would currently do without actually doing it.
     */
    public static QuestGiverPreview preview(ServerPlayer player, UUID plotId, String zoneTypeId) {
        List<Identifier> pool = IssuerQuestPools.poolFor(zoneTypeId);
        if (pool.isEmpty()) {
            return QuestGiverPreview.NOTHING;
        }
        var data = player.getData(ModAttachments.QUEST_DATA);

        for (Identifier questId : pool) {
            QuestInstance instance = data.quests().get(questId);
            if (instance == null || instance.status() != QuestStatus.IN_PROGRESS) {
                continue;
            }
            QuestDefinition definition = QuestRegistry.get(questId).orElse(null);
            if (definition == null) {
                return QuestGiverPreview.NOTHING;
            }
            for (QuestMilestone milestone : definition.milestones()) {
                if (milestone.isDeliverMilestone() && !instance.isMilestoneComplete(milestone)) {
                    boolean ready = milestone.proofId().isPresent()
                            && QuestEngine.hasEnoughProof(player, milestone.proofId().get(), milestone.requiredCount());
                    return new QuestGiverPreview(
                            ready ? QuestGiverPreview.Kind.READY_TO_TURN_IN : QuestGiverPreview.Kind.WAITING_ON_PLAYER,
                            questId, definition.name(),
                            ready ? "Bring that back to me." : "Still working on that for me? Come back once you're done.");
                }
            }
            return new QuestGiverPreview(QuestGiverPreview.Kind.WAITING_ON_PLAYER, questId, definition.name(),
                    "Still working on that for me? Come back once you're done.");
        }

        IssuerQuestBoard board = IssuerQuestBoard.get(player.level().getServer());
        Identifier offeredId = board.get(plotId).orElse(null);
        if (offeredId == null) {
            return QuestGiverPreview.NOTHING;
        }
        if (data.quests().containsKey(offeredId)) {
            return QuestGiverPreview.NOTHING;
        }
        QuestDefinition def = QuestRegistry.get(offeredId).orElse(null);
        if (def == null) {
            return QuestGiverPreview.NOTHING;
        }
        return new QuestGiverPreview(QuestGiverPreview.Kind.NEW_OFFER, offeredId, def.name(), def.description());
    }

    public static void interact(ServerPlayer player, UUID plotId, String zoneTypeId) {
        List<Identifier> pool = IssuerQuestPools.poolFor(zoneTypeId);
        if (pool.isEmpty()) {
            return;
        }
        var data = player.getData(ModAttachments.QUEST_DATA);

        for (Identifier questId : pool) {
            QuestInstance instance = data.quests().get(questId);
            if (instance == null || instance.status() != QuestStatus.IN_PROGRESS) {
                continue;
            }
            QuestDefinition definition = QuestRegistry.get(questId).orElse(null);
            if (definition == null) {
                return;
            }
            for (QuestMilestone milestone : definition.milestones()) {
                if (milestone.isDeliverMilestone() && !instance.isMilestoneComplete(milestone)) {
                    if (!QuestEngine.tryDeliverProof(player, questId, milestone.id())) {
                        player.sendSystemMessage(Component.literal("You don't have what I asked for yet."));
                    }
                    return;
                }
            }
            player.sendSystemMessage(Component.literal("Still working on that for me? Come back once you're done."));
            return;
        }

        IssuerQuestBoard board = IssuerQuestBoard.get(player.level().getServer());
        Identifier offeredId = board.get(plotId).orElse(null);
        if (offeredId == null) {
            player.sendSystemMessage(Component.literal("I don't have anything for you right now."));
            return;
        }
        if (data.quests().containsKey(offeredId)) {
            // Already has a record of it (completed/failed) -- the board slot is stale for this
            // player specifically; leave it on the board for someone else rather than re-offering.
            player.sendSystemMessage(Component.literal("I don't have anything new for you right now."));
            return;
        }
        if (QuestEngine.assignQuest(player, offeredId)) {
            board.clear(plotId);
            QuestAssignmentHooks.fire(player, offeredId);
        }
    }
}
