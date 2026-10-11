package com.github.cerealklla.myssions.quest;

import java.util.List;

import com.github.cerealklla.myssions.registration.ModAttachments;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;

/**
 * Shared "talk to the quest giver" logic, called from both the NPC interact listener ({@code
 * quest.QuestNpcInteractListener}, Lumberjack/Farmer) and the sign interact listener ({@code
 * quest.QuestSignInteractListener}, standing in for a Mayor NPC that doesn't exist -- see that
 * class's own doc). Two cases, checked in order:
 *
 * <ol>
 *   <li>The player already has an in-progress quest from this pool -- if it has a ready "deliver"
 *   milestone and they're holding enough proof, turn it in; otherwise just a flavor message
 *   (nothing silently happens).</li>
 *   <li>Otherwise, offer the first quest in the pool they don't already have any record of
 *   (in progress, completed, or failed) -- auto-assigns it directly, no confirmation dialog this
 *   pass (see {@code context/decisions.md} for this simplification).</li>
 * </ol>
 */
public final class QuestTurnIn {

    private QuestTurnIn() {
    }

    public static void interact(ServerPlayer player, String zoneTypeId) {
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

        for (Identifier questId : pool) {
            if (data.quests().containsKey(questId)) {
                continue;
            }
            if (QuestEngine.assignQuest(player, questId)) {
                QuestAssignmentHooks.fire(player, questId);
                return;
            }
        }
        player.sendSystemMessage(Component.literal("I don't have anything for you right now."));
    }
}
