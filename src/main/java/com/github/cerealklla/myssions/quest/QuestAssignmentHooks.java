package com.github.cerealklla.myssions.quest;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;

import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;

/**
 * Optional "something happens the moment this specific quest is assigned" hook (added for design
 * doc items 8/9 -- a talk-to-NPC quest needs the NPC to actually exist somewhere once accepted).
 * Fired by {@code quest.QuestTurnIn} right after a successful {@code api.Myssions#assignQuest}.
 * Most quests register no hook at all; this exists so a quest's own spawn-an-NPC behavior stays
 * next to its own definition (see {@code quest.NpcQuests#findOldHarlen}) rather than needing
 * {@code QuestTurnIn} itself to special-case individual quest ids.
 */
public final class QuestAssignmentHooks {

    private static final Map<Identifier, BiConsumer<ServerPlayer, Identifier>> HOOKS = new ConcurrentHashMap<>();

    private QuestAssignmentHooks() {
    }

    public static void register(Identifier questId, BiConsumer<ServerPlayer, Identifier> hook) {
        HOOKS.put(questId, hook);
    }

    public static void fire(ServerPlayer player, Identifier questId) {
        BiConsumer<ServerPlayer, Identifier> hook = HOOKS.get(questId);
        if (hook != null) {
            hook.accept(player, questId);
        }
    }
}
