package com.github.cerealklla.myssions.quest;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;

/**
 * Optional "this quest instance has ended, for any reason" hook -- added 2026-10-13, the generic
 * sibling {@link QuestCompletionHooks} deliberately isn't (that class's own doc is explicit it only
 * fires on genuine milestone-driven success, specifically to drive a real one-time external effect
 * like the Plot Upgrade quest's Tier raise -- firing it again here for a failure/abandon would be
 * wrong for that use case). This one is for the opposite, broader need: "whatever this quest handed
 * the player or built in the world needs cleaning up now, however this ended" -- e.g. the Establish
 * Highway quest's granted stakes (inventory items) and any already-placed stake entities/partial
 * paving (world state), both needing removal on failure or abandon, not just success.
 *
 * <p>Same shape as {@link QuestAssignmentHooks}/{@link QuestCompletionHooks} -- a plain {@code
 * Map<Identifier, Hook>}, defaulting to a no-op when nothing is registered. Fired from the three
 * real termination points in {@link QuestEngine} that had no hook infrastructure at all before this:
 * {@code finalizeInstance} ({@link EndReason#COMPLETED}), {@code failQuest} ({@link
 * EndReason#FAILED}, deadline expiry via {@code QuestDeadlineTicker}), and {@code abandonQuest}
 * ({@link EndReason#ABANDONED}, player-initiated). A hook registered here should handle all three
 * reasons it cares about itself (via the {@code reason} parameter) rather than assuming only one.
 */
public final class QuestEndHooks {

    public enum EndReason { COMPLETED, FAILED, ABANDONED }

    @FunctionalInterface
    public interface Hook {
        void onEnded(ServerPlayer player, UUID instanceId, Identifier questId, EndReason reason);
    }

    private static final Map<Identifier, Hook> HOOKS = new ConcurrentHashMap<>();

    private QuestEndHooks() {
    }

    public static void register(Identifier questId, Hook hook) {
        HOOKS.put(questId, hook);
    }

    public static void fire(ServerPlayer player, UUID instanceId, Identifier questId, EndReason reason) {
        Hook hook = HOOKS.get(questId);
        if (hook != null) {
            hook.onEnded(player, instanceId, questId, reason);
        }
    }
}
