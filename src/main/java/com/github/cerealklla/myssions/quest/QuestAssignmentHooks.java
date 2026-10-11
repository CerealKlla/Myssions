package com.github.cerealklla.myssions.quest;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;

/**
 * Optional "something happens the moment this specific quest is assigned" hook (added for design
 * doc items 8/9 -- a talk-to-NPC quest needs the NPC to actually exist somewhere once accepted;
 * widened 2026-10-10 to also carry the new instance's own {@code instanceId} and the issuing
 * {@code plotId} -- needed since both the per-player-NPC-spawn case and the on-site-location case
 * must mutate/resolve a *specific instance*, not just "the" instance of a definition (a player can
 * now hold two independent instances of the same definition at once -- see {@link
 * QuestInstance}'s own doc). Fired by {@code quest.QuestTurnIn} right after a successful assignment.
 * Most quests register no hook at all; this exists so a quest's own spawn-an-NPC/set-a-hint
 * behavior stays next to its own definition rather than needing {@code QuestTurnIn} itself to
 * special-case individual quest ids.
 */
public final class QuestAssignmentHooks {

    @FunctionalInterface
    public interface Hook {
        void onAssigned(ServerPlayer player, UUID instanceId, Identifier questId, UUID plotId);
    }

    private static final Map<Identifier, Hook> HOOKS = new ConcurrentHashMap<>();

    private QuestAssignmentHooks() {
    }

    public static void register(Identifier questId, Hook hook) {
        HOOKS.put(questId, hook);
    }

    public static void fire(ServerPlayer player, UUID instanceId, Identifier questId, UUID plotId) {
        Hook hook = HOOKS.get(questId);
        if (hook != null) {
            hook.onAssigned(player, instanceId, questId, plotId);
        }
    }
}
