package com.github.cerealklla.myssions.quest;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;

/**
 * Optional "can this specific quest instance still ever be completed for real" pre-check -- added
 * 2026-10-13, alongside {@link QuestCompletionHooks}, for the same Plot Upgrade race this session
 * already found and fixed one layer too late: two players' instances (or a stale duplicate) can
 * target the exact same real-world one-time effect (raising a plot to one specific Tier), and only
 * the first one to actually turn in can ever cause it for real.
 *
 * <p>{@link QuestCompletionHooks} fires too late to stop the second player from losing materials
 * over this -- by the time it runs, {@link QuestEngine#tryCompleteQuest} has already consumed every
 * delivered item and queued the quest's own reward. This fires FIRST, before {@code
 * tryCompleteQuest} consumes anything at all, so a quest instance whose real-world effect has
 * already happened through some other path gets failed outright -- fully deleted (explicit user
 * spec, 2026-10-13: "treat it as failed, delete it, and tell the player that someone already
 * completed the quest before them"), same real "pretend this never happened" delete {@code
 * QuestEngine#abandonQuest} already uses, not a third status. Nothing is consumed, nothing is paid.
 *
 * <p>Same shape as {@link QuestAssignmentHooks}/{@link QuestCompletionHooks} -- a plain {@code
 * Map<Identifier, Hook>}, defaulting to "still viable" (true) when nothing is registered, since most
 * quests have no real-world one-time effect to race against at all.
 */
public final class QuestViabilityHooks {

    @FunctionalInterface
    public interface Hook {
        boolean isStillViable(ServerPlayer player, UUID instanceId, Identifier questId);
    }

    private static final Map<Identifier, Hook> HOOKS = new ConcurrentHashMap<>();

    private QuestViabilityHooks() {
    }

    public static void register(Identifier questId, Hook hook) {
        HOOKS.put(questId, hook);
    }

    public static boolean isStillViable(ServerPlayer player, UUID instanceId, Identifier questId) {
        Hook hook = HOOKS.get(questId);
        return hook == null || hook.isStillViable(player, instanceId, questId);
    }
}
