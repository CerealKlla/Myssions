package com.github.cerealklla.myssions.quest;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;

/**
 * Optional per-quest-definition, per-PLAYER acceptance gate -- added 2026-11-08 for Terrytories'
 * "certain quest chains may be blocked until you have a sufficiently high reputation with a
 * settlement." Deliberately NOT the same thing as {@link QuestEligibility}: that one is checked
 * quest-definition-wide, before any player is involved, at board-roll time ("should this quest even
 * exist on a board slot right now"); this one is checked the moment one specific player tries to
 * accept one specific quest ("can THIS player accept it"), inside {@link
 * QuestEngine#assignQuest} -- a player-specific reputation threshold has no meaning at roll time,
 * since the board doesn't know who will eventually try to accept its offer.
 *
 * <p>A quest with no registered gate is always acceptable -- every existing quest is unaffected.
 * In-memory only (re-registered at startup by whichever quest defines it), same shape as {@link
 * QuestAssignmentHooks}/{@link QuestEligibility}.
 */
public final class QuestAcceptanceGate {

    @FunctionalInterface
    public interface Gate {
        /** @return {@code null} if {@code player} may accept this quest, otherwise the rejection message to show them. */
        String checkAcceptance(ServerPlayer player, UUID plotId);
    }

    private static final Map<Identifier, Gate> GATES = new HashMap<>();

    private QuestAcceptanceGate() {
    }

    public static void register(Identifier questId, Gate gate) {
        GATES.put(questId, gate);
    }

    /** {@code null} if {@code player} may accept {@code questId} right now (including: no gate registered at all, or no issuing plot to check against). */
    public static String checkAcceptance(ServerPlayer player, UUID plotId, Identifier questId) {
        Gate gate = GATES.get(questId);
        return gate == null ? null : gate.checkAcceptance(player, plotId);
    }
}
