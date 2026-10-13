package com.github.cerealklla.myssions.quest;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;

/**
 * Optional "something happens the moment this specific quest's required milestones are satisfied"
 * hook -- added 2026-10-13, the real fix for a confirmed-live bug: the Plot Upgrade quest correctly
 * consumed the player's real wood/stone shortfall and paid out its own gold/XP reward, but nothing
 * ever told Settlemynts to actually raise the plot's Tier, since the quest's own delivery milestone
 * (deliberately a plain inventory consume, see {@code QuestEngine#tryDeliverProof}'s own doc) never
 * touches the plot's Construction Box the real "Upgrade Plot" button funds from.
 *
 * <p>Mirrors {@link QuestAssignmentHooks} exactly, same {@code Map<Identifier, Hook>} shape, just
 * fired at the opposite end of a quest's life -- added instead of hardcoding a Settlemynts call
 * directly in {@code QuestEngine}, per explicit user request: a bridge call like this still has to
 * live *somewhere* that knows what "Plot Upgrade" means (here, that's {@code
 * quest.QuestHooks#registerPlotUpgradeHook}, which already owns the matching acceptance-time bridge
 * call), but the engine itself -- {@code QuestEngine#reportMilestoneProgress}, where this fires --
 * never needs to know Settlemynts exists at all. Most quests register no hook; this exists so a
 * quest's own "do something real in another mod when this finishes" behavior stays next to its own
 * registration instead of needing the engine to special-case individual quest ids.
 */
public final class QuestCompletionHooks {

    @FunctionalInterface
    public interface Hook {
        void onCompleted(ServerPlayer player, UUID instanceId, Identifier questId);
    }

    private static final Map<Identifier, Hook> HOOKS = new ConcurrentHashMap<>();

    private QuestCompletionHooks() {
    }

    public static void register(Identifier questId, Hook hook) {
        HOOKS.put(questId, hook);
    }

    public static void fire(ServerPlayer player, UUID instanceId, Identifier questId) {
        Hook hook = HOOKS.get(questId);
        if (hook != null) {
            hook.onCompleted(player, instanceId, questId);
        }
    }
}
