package com.github.cerealklla.myssions.quest;

import java.util.List;

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
 */
public record QuestDefinition(Identifier id, String name, String description, String issuerId,
                               List<QuestMilestone> milestones, List<QuestReward> rewards, QuestTimeLimit timeLimit,
                               List<QuestReward> acceptanceGrants, boolean isSpecialAssignment) {

    public static final Codec<QuestDefinition> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Identifier.CODEC.fieldOf("id").forGetter(QuestDefinition::id),
            Codec.STRING.fieldOf("name").forGetter(QuestDefinition::name),
            Codec.STRING.fieldOf("description").forGetter(QuestDefinition::description),
            Codec.STRING.fieldOf("issuer_id").forGetter(QuestDefinition::issuerId),
            QuestMilestone.CODEC.listOf().fieldOf("milestones").forGetter(QuestDefinition::milestones),
            QuestReward.CODEC.listOf().fieldOf("rewards").forGetter(QuestDefinition::rewards),
            QuestTimeLimit.CODEC.optionalFieldOf("time_limit", QuestTimeLimit.NONE).forGetter(QuestDefinition::timeLimit),
            QuestReward.CODEC.listOf().optionalFieldOf("acceptance_grants", List.of()).forGetter(QuestDefinition::acceptanceGrants),
            Codec.BOOL.optionalFieldOf("is_special_assignment", false).forGetter(QuestDefinition::isSpecialAssignment)
    ).apply(instance, QuestDefinition::new));

    /**
     * Legacy 7-arg shape, kept so every existing quest-definition registration across this mod's 110+
     * quest content files doesn't need touching -- {@code acceptanceGrants} defaults to none, {@code
     * isSpecialAssignment} to false (an ordinary board-offered/5-active-cap-counted quest, same as
     * every quest registered before 2026-10-13). See this class's own doc for what both new fields do.
     */
    public QuestDefinition(Identifier id, String name, String description, String issuerId,
                           List<QuestMilestone> milestones, List<QuestReward> rewards, QuestTimeLimit timeLimit) {
        this(id, name, description, issuerId, milestones, rewards, timeLimit, List.of(), false);
    }
}
