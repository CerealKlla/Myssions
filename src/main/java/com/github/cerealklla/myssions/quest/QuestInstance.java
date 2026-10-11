package com.github.cerealklla.myssions.quest;

import java.util.Map;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.resources.Identifier;

/**
 * One player's own progress against a {@link QuestDefinition} -- created the moment a quest is
 * assigned to them (see {@code api.Myssions#assignQuest}), stored in their own {@code
 * quest.PlayerQuestData} attachment. {@code milestoneProgress} maps a {@link QuestMilestone#id()}
 * to how many units of it this player has completed so far (compared against that milestone's own
 * {@link QuestMilestone#requiredCount()} to decide "done"); a milestone absent from the map simply
 * means zero progress, not an error.
 */
public record QuestInstance(Identifier definitionId, QuestStatus status, Map<String, Integer> milestoneProgress,
                             long assignedAtGameTime) {

    public static final Codec<QuestInstance> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Identifier.CODEC.fieldOf("definition_id").forGetter(QuestInstance::definitionId),
            Codec.STRING.xmap(QuestStatus::valueOf, QuestStatus::name).fieldOf("status").forGetter(QuestInstance::status),
            Codec.unboundedMap(Codec.STRING, Codec.INT).optionalFieldOf("milestone_progress", Map.of()).forGetter(QuestInstance::milestoneProgress),
            Codec.LONG.fieldOf("assigned_at_game_time").forGetter(QuestInstance::assignedAtGameTime)
    ).apply(instance, QuestInstance::new));

    public static QuestInstance freshlyAssigned(Identifier definitionId, long gameTime) {
        return new QuestInstance(definitionId, QuestStatus.IN_PROGRESS, Map.of(), gameTime);
    }

    public QuestInstance withStatus(QuestStatus newStatus) {
        return new QuestInstance(definitionId, newStatus, milestoneProgress, assignedAtGameTime);
    }

    public QuestInstance withProgress(String milestoneId, int amount) {
        Map<String, Integer> updated = new java.util.HashMap<>(milestoneProgress);
        updated.put(milestoneId, amount);
        return new QuestInstance(definitionId, status, Map.copyOf(updated), assignedAtGameTime);
    }

    public boolean isMilestoneComplete(QuestMilestone milestone) {
        return milestoneProgress.getOrDefault(milestone.id(), 0) >= milestone.requiredCount();
    }
}
