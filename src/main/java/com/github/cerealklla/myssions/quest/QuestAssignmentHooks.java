package com.github.cerealklla.myssions.quest;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;

/**
 * Optional "something happens the moment this specific quest is assigned" hook (added for design
 * doc items 8/9 -- a talk-to-NPC quest needs the NPC to actually exist somewhere once accepted;
 * widened 2026-10-10 to also carry the issuing plot's id, for on-site quests that need to resolve
 * their own real location -- see {@code NpcQuests}' six "near my X" Farmer/Lumberjack hooks).
 * Fired by {@code quest.QuestTurnIn} right after a successful {@code api.Myssions#assignQuest}.
 * Most quests register no hook at all; this exists so a quest's own spawn-an-NPC/set-a-hint
 * behavior stays next to its own definition rather than needing {@code QuestTurnIn} itself to
 * special-case individual quest ids.
 */
public final class QuestAssignmentHooks {

    @FunctionalInterface
    public interface Hook {
        void onAssigned(ServerPlayer player, Identifier questId, UUID plotId);
    }

    private static final Map<Identifier, Hook> HOOKS = new ConcurrentHashMap<>();

    private QuestAssignmentHooks() {
    }

    public static void register(Identifier questId, Hook hook) {
        HOOKS.put(questId, hook);
    }

    public static void fire(ServerPlayer player, Identifier questId, UUID plotId) {
        Hook hook = HOOKS.get(questId);
        if (hook != null) {
            hook.onAssigned(player, questId, plotId);
        }
    }
}
