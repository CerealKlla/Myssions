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
 */
public record QuestDefinition(Identifier id, String name, String description, String issuerId,
                               List<QuestMilestone> milestones, List<QuestReward> rewards, QuestTimeLimit timeLimit) {

    public static final Codec<QuestDefinition> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Identifier.CODEC.fieldOf("id").forGetter(QuestDefinition::id),
            Codec.STRING.fieldOf("name").forGetter(QuestDefinition::name),
            Codec.STRING.fieldOf("description").forGetter(QuestDefinition::description),
            Codec.STRING.fieldOf("issuer_id").forGetter(QuestDefinition::issuerId),
            QuestMilestone.CODEC.listOf().fieldOf("milestones").forGetter(QuestDefinition::milestones),
            QuestReward.CODEC.listOf().fieldOf("rewards").forGetter(QuestDefinition::rewards),
            QuestTimeLimit.CODEC.optionalFieldOf("time_limit", QuestTimeLimit.NONE).forGetter(QuestDefinition::timeLimit)
    ).apply(instance, QuestDefinition::new));
}
