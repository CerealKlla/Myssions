package com.github.cerealklla.myssions.quest;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.BiPredicate;

import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;

/**
 * Optional per-quest-definition eligibility predicate, checked before a quest is ever offered on a
 * board slot -- closes the gap design-document.md's quest taxonomy flagged under the unborn-town
 * quest's "Known Phase 1 gap" ("no per-quest eligibility predicate exists to restrict this... {@code
 * IssuerQuestPools}/{@code IssuerQuestBoard} have no such mechanism yet"). Added 2026-10-11 for the
 * "buy an item from a local shop" quest, which must not even be rolled if the settlement has nothing
 * resolvable to offer.
 *
 * <p>A quest with no registered predicate is always eligible -- every existing quest is unaffected.
 * In-memory only (re-registered at startup by whichever quest defines it, same shape as {@code
 * QuestAssignmentHooks}), not persisted.
 */
public final class QuestEligibility {

    private static final Map<Identifier, BiPredicate<ServerLevel, UUID>> PREDICATES = new HashMap<>();

    private QuestEligibility() {
    }

    public static void register(Identifier questId, BiPredicate<ServerLevel, UUID> predicate) {
        PREDICATES.put(questId, predicate);
    }

    /** @param plotId the issuing plot considering offering this quest */
    public static boolean isEligible(ServerLevel level, UUID plotId, Identifier questId) {
        BiPredicate<ServerLevel, UUID> predicate = PREDICATES.get(questId);
        return predicate == null || predicate.test(level, plotId);
    }

    /** True if this quest has a registered eligibility predicate at all -- used by {@code bridge.QuestBoardMidnightTicker}'s "generating a resolved-target quest also grants one free extra roll" rule. */
    public static boolean hasPredicate(Identifier questId) {
        return PREDICATES.containsKey(questId);
    }
}
