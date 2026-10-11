package com.github.cerealklla.myssions.quest;

import java.util.Map;
import java.util.Optional;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;

/**
 * One player's own progress against a {@link QuestDefinition} -- created the moment a quest is
 * assigned to them (see {@code api.Myssions#assignQuest}), stored in their own {@code
 * quest.PlayerQuestData} attachment. {@code milestoneProgress} maps a {@link QuestMilestone#id()}
 * to how many units of it this player has completed so far (compared against that milestone's own
 * {@link QuestMilestone#requiredCount()} to decide "done"); a milestone absent from the map simply
 * means zero progress, not an error.
 *
 * <p>{@code hintPos}/{@code hintDimension} (added 2026-10-10, design doc item 2) are an optional
 * *true*, hidden target position this specific instance cares about -- never shown directly to the
 * player. {@code quest.QuestWaypointTicker} uses it (plus the player's own knowledge quality, via
 * {@code bridge.LyfeWaypointBridge}) to compute and push the actual vague-circle hint they see.
 * Per-instance, not per-definition, since where the hint points can genuinely vary by who got the
 * quest and when (e.g. "Find Old Harlen" picks a fresh nearby spot for each player).
 */
public record QuestInstance(Identifier definitionId, QuestStatus status, Map<String, Integer> milestoneProgress,
                             long assignedAtGameTime, Optional<BlockPos> hintPos, Optional<String> hintDimensionId) {

    public static final Codec<QuestInstance> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Identifier.CODEC.fieldOf("definition_id").forGetter(QuestInstance::definitionId),
            Codec.STRING.xmap(QuestStatus::valueOf, QuestStatus::name).fieldOf("status").forGetter(QuestInstance::status),
            Codec.unboundedMap(Codec.STRING, Codec.INT).optionalFieldOf("milestone_progress", Map.of()).forGetter(QuestInstance::milestoneProgress),
            Codec.LONG.fieldOf("assigned_at_game_time").forGetter(QuestInstance::assignedAtGameTime),
            BlockPos.CODEC.optionalFieldOf("hint_pos").forGetter(QuestInstance::hintPos),
            Codec.STRING.optionalFieldOf("hint_dimension_id").forGetter(QuestInstance::hintDimensionId)
    ).apply(instance, QuestInstance::new));

    public static QuestInstance freshlyAssigned(Identifier definitionId, long gameTime) {
        return new QuestInstance(definitionId, QuestStatus.IN_PROGRESS, Map.of(), gameTime, Optional.empty(), Optional.empty());
    }

    public QuestInstance withStatus(QuestStatus newStatus) {
        return new QuestInstance(definitionId, newStatus, milestoneProgress, assignedAtGameTime, hintPos, hintDimensionId);
    }

    public QuestInstance withProgress(String milestoneId, int amount) {
        Map<String, Integer> updated = new java.util.HashMap<>(milestoneProgress);
        updated.put(milestoneId, amount);
        return new QuestInstance(definitionId, status, Map.copyOf(updated), assignedAtGameTime, hintPos, hintDimensionId);
    }

    public QuestInstance withHint(BlockPos pos, String dimensionId) {
        return new QuestInstance(definitionId, status, milestoneProgress, assignedAtGameTime, Optional.of(pos), Optional.of(dimensionId));
    }

    public boolean isMilestoneComplete(QuestMilestone milestone) {
        return milestoneProgress.getOrDefault(milestone.id(), 0) >= milestone.requiredCount();
    }
}
