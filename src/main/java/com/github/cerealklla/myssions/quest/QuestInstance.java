package com.github.cerealklla.myssions.quest;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

/**
 * One single acceptance of a {@link QuestDefinition} -- created the moment a quest is assigned
 * (see {@code api.Myssions#assignQuest}), stored keyed by its own {@link #instanceId()} in {@code
 * quest.PlayerQuestData}. {@code milestoneProgress} maps a {@link QuestMilestone#id()} to how many
 * units of it this instance has completed so far; a milestone absent from the map simply means
 * zero progress, not an error.
 *
 * <p>{@code instanceId} (added 2026-10-10, real user correction: "The Unique ID was meant to be a
 * UUID and hidden from the user, that way two players can't be on the exact same quest as the
 * rewards may be different, location details, etc") is a real, randomly-generated, per-acceptance
 * identity -- never shown in any UI. {@link #definitionId()} still names which static {@link
 * QuestDefinition} this instance is of, but is no longer the instance's own key: the same player
 * can hold two completely independent instances of the same definition at once (e.g. "Night
 * Raiders" offered by two different Farmers in two different settlements), each with its own
 * progress, hint location, and eventual rewards -- a single shared `definitionId` key could never
 * represent that.
 *
 * <p>{@code hintPos}/{@code hintDimensionId} (design doc item 2) are an optional *true*, hidden
 * target position this specific instance cares about -- never shown directly to the player. {@code
 * quest.QuestWaypointTicker} uses it (plus the player's own knowledge quality, via {@code
 * bridge.LyfeWaypointBridge}) to compute and push the actual vague-circle hint they see.
 *
 * <p>{@code locationName} (added 2026-10-10, same day's on-site-quest retrofit) is a real place
 * name -- e.g. a settlement's own name -- to substitute into a quest's {@code {location}}-tokened
 * flavor text (see {@code quest.QuestText#resolve}). Empty for a quest with no resolvable real
 * location (the token then falls back to a generic phrase).
 *
 * <p>{@code pendingRewardItems} (added 2026-10-11, explicit user spec: turning in a quest should
 * open a reward-collection GUI with a "Take All" button, only actually flagging the quest complete
 * once everything's been collected -- see {@code quest.QuestRewardMenu}'s own doc) holds the
 * Gold/Item reward stacks still waiting to be picked up while {@link #status()} is {@link
 * QuestStatus#AWAITING_REWARD_PICKUP}. Persisted (not just an in-memory menu) so closing the GUI
 * without collecting everything and coming back later doesn't lose or duplicate anything.
 *
 * <p>{@code rewardMultiplier} (added 2026-10-11, explicit user spec: "scale the reward with the
 * difficulty/distance from starting point a player would need to travel") -- applied to Gold/Item/XP
 * reward amounts in {@code QuestEngine} when a quest's real target turned out to be a real
 * Cartographyr Region/Settlement a meaningful distance from the issuing NPC (see {@code
 * NpcQuests#registerRegionHook}). {@code 1.0} (no scaling) for every quest that doesn't set it --
 * on-site quests, and anything built before this existed.
 */
public record QuestInstance(UUID instanceId, Identifier definitionId, QuestStatus status, Map<String, Integer> milestoneProgress,
                             long assignedAtGameTime, Optional<BlockPos> hintPos, Optional<String> hintDimensionId,
                             Optional<String> locationName, List<ItemStack> pendingRewardItems, double rewardMultiplier) {

    public static final Codec<QuestInstance> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            UUIDUtil.CODEC.fieldOf("instance_id").forGetter(QuestInstance::instanceId),
            Identifier.CODEC.fieldOf("definition_id").forGetter(QuestInstance::definitionId),
            Codec.STRING.xmap(QuestStatus::valueOf, QuestStatus::name).fieldOf("status").forGetter(QuestInstance::status),
            Codec.unboundedMap(Codec.STRING, Codec.INT).optionalFieldOf("milestone_progress", Map.of()).forGetter(QuestInstance::milestoneProgress),
            Codec.LONG.fieldOf("assigned_at_game_time").forGetter(QuestInstance::assignedAtGameTime),
            BlockPos.CODEC.optionalFieldOf("hint_pos").forGetter(QuestInstance::hintPos),
            Codec.STRING.optionalFieldOf("hint_dimension_id").forGetter(QuestInstance::hintDimensionId),
            Codec.STRING.optionalFieldOf("location_name").forGetter(QuestInstance::locationName),
            ItemStack.CODEC.listOf().optionalFieldOf("pending_reward_items", List.of()).forGetter(QuestInstance::pendingRewardItems),
            Codec.DOUBLE.optionalFieldOf("reward_multiplier", 1.0).forGetter(QuestInstance::rewardMultiplier)
    ).apply(instance, QuestInstance::new));

    public static QuestInstance freshlyAssigned(Identifier definitionId, long gameTime) {
        return new QuestInstance(UUID.randomUUID(), definitionId, QuestStatus.IN_PROGRESS, Map.of(), gameTime, Optional.empty(), Optional.empty(), Optional.empty(), List.of(), 1.0);
    }

    public QuestInstance withStatus(QuestStatus newStatus) {
        return new QuestInstance(instanceId, definitionId, newStatus, milestoneProgress, assignedAtGameTime, hintPos, hintDimensionId, locationName, pendingRewardItems, rewardMultiplier);
    }

    public QuestInstance withProgress(String milestoneId, int amount) {
        Map<String, Integer> updated = new java.util.HashMap<>(milestoneProgress);
        updated.put(milestoneId, amount);
        return new QuestInstance(instanceId, definitionId, status, Map.copyOf(updated), assignedAtGameTime, hintPos, hintDimensionId, locationName, pendingRewardItems, rewardMultiplier);
    }

    public QuestInstance withHint(BlockPos pos, String dimensionId, Optional<String> newLocationName) {
        return new QuestInstance(instanceId, definitionId, status, milestoneProgress, assignedAtGameTime, Optional.of(pos), Optional.of(dimensionId), newLocationName, pendingRewardItems, rewardMultiplier);
    }

    public QuestInstance withRewardMultiplier(double newRewardMultiplier) {
        return new QuestInstance(instanceId, definitionId, status, milestoneProgress, assignedAtGameTime, hintPos, hintDimensionId, locationName, pendingRewardItems, newRewardMultiplier);
    }

    /** Transitions to {@link QuestStatus#AWAITING_REWARD_PICKUP} carrying the still-uncollected reward stacks. */
    public QuestInstance withPendingRewardItems(List<ItemStack> items) {
        return new QuestInstance(instanceId, definitionId, QuestStatus.AWAITING_REWARD_PICKUP, milestoneProgress, assignedAtGameTime, hintPos, hintDimensionId, locationName, List.copyOf(items), rewardMultiplier);
    }

    public boolean isMilestoneComplete(QuestMilestone milestone) {
        return milestoneProgress.getOrDefault(milestone.id(), 0) >= milestone.requiredCount();
    }
}
