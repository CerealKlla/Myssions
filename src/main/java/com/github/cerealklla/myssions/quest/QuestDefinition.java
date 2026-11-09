package com.github.cerealklla.myssions.quest;

import java.util.List;
import java.util.Optional;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.resources.Identifier;

/**
 * The static template for a quest -- registered once (by this mod's own sample content, or by any
 * other mod via {@code api.Myssions#registerQuestDefinition}) and never mutated after that. A
 * player's own progress against one is tracked separately as a {@link QuestInstance}, keyed by
 * this definition's own {@link #id()} -- the same "definition vs. instance" split every other
 * template-backed system in this suite uses (e.g. Blueprynts' Blueprint vs. a plot's bound
 * Construction Box).
 *
 * <p>{@code issuerId} is a plain free-form string (e.g. a Mayor's player name, an NPC name, or a
 * synthetic id like {@code "the_ancients"}) -- intentionally not tied to any other mod's own ID
 * system yet, so Myssions never needs a hard dependency on who's actually handing out a quest.
 * {@code api.Myssions#getCompletedCountForIssuer} is what lets a future karma/reputation system
 * build on top of this without this mod needing to know what "reputation" even means.
 *
 * <p>{@link #acceptanceGrants} (added 2026-10-13, explicit user spec -- "a blacksmith creates an
 * experimental weapon and wants you to go test it for him") -- a fixed, static list of items handed
 * to the player the instant {@code QuestEngine#assignQuest} creates their instance, reusing {@link
 * QuestReward}'s existing {@code ITEM} kind rather than a new reward type. For anything whose
 * granted item must vary per-instance (resolved only at accept time, e.g. the Plot Upgrade quest's
 * dynamically-sized shortfall, or the Establish Highway quest's distance-scaled stake count), a quest
 * still registers a {@link QuestAssignmentHooks} callback directly instead -- this field only covers
 * the common "always hand over the same fixed item(s)" case. Cleaning an acceptance-granted item back
 * up when the quest ends is a separate, per-instance concern (the exact item/amount already handed
 * over, or a world position a mechanic built from it) -- see {@link QuestEndHooks}' own doc, a quest
 * that needs end-of-life cleanup registers its own hook there rather than this field growing a
 * matching "removals" list.
 *
 * <p>{@link #isSpecialAssignment} (added 2026-10-13, for the Mayor's "Establish Highway" quest) --
 * exempts every instance of this definition from both {@code QuestEngine#MAX_ACCEPTED_QUESTS} (the
 * player's own 5-concurrent-quest cap) and the ordinary {@code IssuerQuestBoard}'s 5-slot-per-plot
 * cap (special-assignment offers live on a separate, unbounded {@code SpecialAssignmentBoard}
 * instead), and is shown under its own "Mayor's Special Assignments" heading in the Quest Log rather
 * than mixed into the ordinary active-quest list.
 *
 * <p>{@link #disableDecline}/{@link #disableAbandon} (added 2026-11-06, explicit user spec) -- a
 * quest with {@code disableDecline} true hides the "Decline" button on its own Available-quest row
 * (see {@code menu.client.QuestLogTab}); {@code disableAbandon} hides/refuses "Abandon Quest" once
 * it's in progress (enforced in {@code QuestEngine#abandonQuest} itself, not just hidden client-side
 * -- a modified client can't bypass this by sending the payload directly). Both default {@code false}
 * for every quest registered before this field existed.
 */
public record QuestDefinition(Identifier id, String name, String description, String issuerId,
                               List<QuestMilestone> milestones, List<QuestReward> rewards, QuestTimeLimit timeLimit,
                               List<QuestReward> acceptanceGrants, boolean isSpecialAssignment,
                               Optional<Identifier> unlocksQuestId, boolean disableDecline, boolean disableAbandon) {

    public static final Codec<QuestDefinition> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Identifier.CODEC.fieldOf("id").forGetter(QuestDefinition::id),
            Codec.STRING.fieldOf("name").forGetter(QuestDefinition::name),
            Codec.STRING.fieldOf("description").forGetter(QuestDefinition::description),
            Codec.STRING.fieldOf("issuer_id").forGetter(QuestDefinition::issuerId),
            QuestMilestone.CODEC.listOf().fieldOf("milestones").forGetter(QuestDefinition::milestones),
            QuestReward.CODEC.listOf().fieldOf("rewards").forGetter(QuestDefinition::rewards),
            QuestTimeLimit.CODEC.optionalFieldOf("time_limit", QuestTimeLimit.NONE).forGetter(QuestDefinition::timeLimit),
            QuestReward.CODEC.listOf().optionalFieldOf("acceptance_grants", List.of()).forGetter(QuestDefinition::acceptanceGrants),
            Codec.BOOL.optionalFieldOf("is_special_assignment", false).forGetter(QuestDefinition::isSpecialAssignment),
            Identifier.CODEC.optionalFieldOf("unlocks_quest_id").forGetter(QuestDefinition::unlocksQuestId),
            Codec.BOOL.optionalFieldOf("disable_decline", false).forGetter(QuestDefinition::disableDecline),
            Codec.BOOL.optionalFieldOf("disable_abandon", false).forGetter(QuestDefinition::disableAbandon)
    ).apply(instance, QuestDefinition::new));

    /**
     * Legacy 7-arg shape, kept so every existing quest-definition registration across this mod's 110+
     * quest content files doesn't need touching -- every field added since defaults to its own
     * original "ordinary quest" behavior. See this class's own doc for what each added field does.
     */
    public QuestDefinition(Identifier id, String name, String description, String issuerId,
                           List<QuestMilestone> milestones, List<QuestReward> rewards, QuestTimeLimit timeLimit) {
        this(id, name, description, issuerId, milestones, rewards, timeLimit, List.of(), false, Optional.empty(), false, false);
    }

    /**
     * 9-arg shape (pre-{@link #unlocksQuestId}, added 2026-11-06 for Myssions' new QuestChain
     * mechanic -- see that field's own doc on {@code PlayerQuestData#availableQuestIds}) -- kept so
     * every existing call site passing {@code acceptanceGrants}/{@code isSpecialAssignment}
     * explicitly doesn't need touching. {@code unlocksQuestId}/{@code disableDecline}/{@code
     * disableAbandon} all default to their own "ordinary quest" behavior.
     */
    public QuestDefinition(Identifier id, String name, String description, String issuerId,
                           List<QuestMilestone> milestones, List<QuestReward> rewards, QuestTimeLimit timeLimit,
                           List<QuestReward> acceptanceGrants, boolean isSpecialAssignment) {
        this(id, name, description, issuerId, milestones, rewards, timeLimit, acceptanceGrants, isSpecialAssignment, Optional.empty(), false, false);
    }

    /**
     * 10-arg shape (pre-{@code disableDecline}/{@code disableAbandon}, same day) -- kept so any
     * direct caller already passing {@code unlocksQuestId} explicitly (rather than via {@link
     * #withUnlocksQuestId}) doesn't need touching either.
     */
    public QuestDefinition(Identifier id, String name, String description, String issuerId,
                           List<QuestMilestone> milestones, List<QuestReward> rewards, QuestTimeLimit timeLimit,
                           List<QuestReward> acceptanceGrants, boolean isSpecialAssignment, Optional<Identifier> unlocksQuestId) {
        this(id, name, description, issuerId, milestones, rewards, timeLimit, acceptanceGrants, isSpecialAssignment, unlocksQuestId, false, false);
    }

    /**
     * Completing this quest makes {@code nextQuestId} show up under "Available" in the player's
     * Quest Log (see {@code PlayerQuestData#availableQuestIds}) -- a player explicitly accepts it
     * from there, same as any other quest, rather than it being auto-assigned. The simplest possible
     * "QuestChain" -- a quest naming the one quest after it -- rather than a separate chain-grouping
     * concept, since a linked list of definitions is already everything "completing A unlocks B"
     * needs.
     */
    public QuestDefinition withUnlocksQuestId(Identifier nextQuestId) {
        return new QuestDefinition(id, name, description, issuerId, milestones, rewards, timeLimit,
                acceptanceGrants, isSpecialAssignment, Optional.of(nextQuestId), disableDecline, disableAbandon);
    }

    /** See this class's own doc for what both flags do. */
    public QuestDefinition withOfferFlags(boolean disableDecline, boolean disableAbandon) {
        return new QuestDefinition(id, name, description, issuerId, milestones, rewards, timeLimit,
                acceptanceGrants, isSpecialAssignment, unlocksQuestId, disableDecline, disableAbandon);
    }
}
