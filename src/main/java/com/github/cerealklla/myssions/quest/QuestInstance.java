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
 * can hold two completely independent instances of the same definition at once, each with its own
 * progress, hint location, and eventual rewards.
 *
 * <p>{@code hintPos}/{@code hintDimensionId} (design doc item 2) are an optional *true*, hidden
 * target position this specific instance cares about -- never shown directly to the player. An
 * escort quest keeps this continuously updated to the live caravan's own position instead of a
 * single fixed point, which is what lets the existing kill-geofence/mob-spawn-guarantee logic track
 * a moving target with no changes to either.
 *
 * <p>{@code locationName} is the *currently displayed* resolved phrase substituted into a quest's
 * {@code {location}}-tokened flavor text -- for an on-site quest it's set once at assignment and
 * never changes; for a region quest it starts as {@link #directionPhrase()} and is live-upgraded by
 * {@code QuestWaypointTicker} once the player's real knowledge of {@link #knowledgePlaceId()} is
 * good enough.
 *
 * <p>{@code pendingRewardItems} holds the Gold/Item reward stacks still waiting to be picked up
 * while {@link #status()} is {@link QuestStatus#AWAITING_REWARD_PICKUP}. {@code rewardMultiplier}
 * scales Gold/Item/XP rewards (region-quest distance scaling, or the road-quest efficiency bonus).
 *
 * <p>{@code knowledgePlaceId}/{@code directionPhrase}/{@code realLocationName} -- the real
 * Cartographyr place this quest is about (a region quest's target, or a road quest's destination
 * settlement), its stable direction phrase, and its true name, used as the real {@code
 * bridge.LyfeWaypointBridge} knowledge key so "already been there" resolves to full knowledge for
 * free. {@code mobsSpawned} is whether {@code QuestMobSpawnTicker} has already topped up live mobs
 * near this instance's target to guarantee completability.
 *
 * <p>{@link #escort()} bundles the caravan-raid quest style's own state (added 2026-10-11) --
 * {@code entityUuid} is the live {@code quest.entity.CaravanEntity} this instance owns;
 * {@code remainingRoute} is every waypoint not yet reached, popped by {@code QuestEscortTicker} as
 * the caravan advances (empty, after starting non-empty, means it reached the end alive).
 *
 * <p>{@link #roadQuest()} bundles the inter-settlement road-connection quest style's own state
 * (added 2026-10-11, see {@code RoadQuestLogic}'s own doc) -- {@code originPlaceId} is the
 * settlement that posted it (the destination reuses {@link #knowledgePlaceId()} above);
 * {@code minStakes}/{@code optimalStakeCap} are the thresholds {@code RoadQuestTicker} checks the
 * player's live Highway Stake count against. Both nested records exist specifically to keep this
 * record's own field count within Mojang's {@code RecordCodecBuilder} group-arity limit (16) as
 * more quest styles have been added.
 */
public record QuestInstance(UUID instanceId, Identifier definitionId, QuestStatus status, Map<String, Integer> milestoneProgress,
                             long assignedAtGameTime, Optional<BlockPos> hintPos, Optional<String> hintDimensionId,
                             Optional<String> locationName, List<ItemStack> pendingRewardItems, double rewardMultiplier,
                             Optional<Long> knowledgePlaceId, Optional<String> directionPhrase, Optional<String> realLocationName,
                             boolean mobsSpawned, EscortData escort, RoadQuestData roadQuest) {

    public record EscortData(Optional<UUID> entityUuid, List<BlockPos> remainingRoute) {
        public static final EscortData NONE = new EscortData(Optional.empty(), List.of());

        public static final Codec<EscortData> CODEC = RecordCodecBuilder.create(i -> i.group(
                UUIDUtil.CODEC.optionalFieldOf("entity_uuid").forGetter(EscortData::entityUuid),
                BlockPos.CODEC.listOf().optionalFieldOf("remaining_route", List.of()).forGetter(EscortData::remainingRoute)
        ).apply(i, EscortData::new));
    }

    public record RoadQuestData(Optional<Long> originPlaceId, int minStakes, int optimalStakeCap) {
        public static final RoadQuestData NONE = new RoadQuestData(Optional.empty(), 0, 0);

        public static final Codec<RoadQuestData> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.LONG.optionalFieldOf("origin_place_id").forGetter(RoadQuestData::originPlaceId),
                Codec.INT.optionalFieldOf("min_stakes", 0).forGetter(RoadQuestData::minStakes),
                Codec.INT.optionalFieldOf("optimal_stake_cap", 0).forGetter(RoadQuestData::optimalStakeCap)
        ).apply(i, RoadQuestData::new));
    }

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
            Codec.DOUBLE.optionalFieldOf("reward_multiplier", 1.0).forGetter(QuestInstance::rewardMultiplier),
            Codec.LONG.optionalFieldOf("knowledge_place_id").forGetter(QuestInstance::knowledgePlaceId),
            Codec.STRING.optionalFieldOf("direction_phrase").forGetter(QuestInstance::directionPhrase),
            Codec.STRING.optionalFieldOf("real_location_name").forGetter(QuestInstance::realLocationName),
            Codec.BOOL.optionalFieldOf("mobs_spawned", false).forGetter(QuestInstance::mobsSpawned),
            EscortData.CODEC.optionalFieldOf("escort", EscortData.NONE).forGetter(QuestInstance::escort),
            RoadQuestData.CODEC.optionalFieldOf("road_quest", RoadQuestData.NONE).forGetter(QuestInstance::roadQuest)
    ).apply(instance, QuestInstance::new));

    public static QuestInstance freshlyAssigned(Identifier definitionId, long gameTime) {
        return new QuestInstance(UUID.randomUUID(), definitionId, QuestStatus.IN_PROGRESS, Map.of(), gameTime, Optional.empty(), Optional.empty(),
                Optional.empty(), List.of(), 1.0, Optional.empty(), Optional.empty(), Optional.empty(), false, EscortData.NONE, RoadQuestData.NONE);
    }

    public QuestInstance withStatus(QuestStatus newStatus) {
        return new QuestInstance(instanceId, definitionId, newStatus, milestoneProgress, assignedAtGameTime, hintPos, hintDimensionId,
                locationName, pendingRewardItems, rewardMultiplier, knowledgePlaceId, directionPhrase, realLocationName, mobsSpawned, escort, roadQuest);
    }

    public QuestInstance withProgress(String milestoneId, int amount) {
        Map<String, Integer> updated = new java.util.HashMap<>(milestoneProgress);
        updated.put(milestoneId, amount);
        return new QuestInstance(instanceId, definitionId, status, Map.copyOf(updated), assignedAtGameTime, hintPos, hintDimensionId,
                locationName, pendingRewardItems, rewardMultiplier, knowledgePlaceId, directionPhrase, realLocationName, mobsSpawned, escort, roadQuest);
    }

    public QuestInstance withHint(BlockPos pos, String dimensionId, Optional<String> newLocationName) {
        return new QuestInstance(instanceId, definitionId, status, milestoneProgress, assignedAtGameTime, Optional.of(pos), Optional.of(dimensionId),
                newLocationName, pendingRewardItems, rewardMultiplier, knowledgePlaceId, directionPhrase, realLocationName, mobsSpawned, escort, roadQuest);
    }

    public QuestInstance withRewardMultiplier(double newRewardMultiplier) {
        return new QuestInstance(instanceId, definitionId, status, milestoneProgress, assignedAtGameTime, hintPos, hintDimensionId,
                locationName, pendingRewardItems, newRewardMultiplier, knowledgePlaceId, directionPhrase, realLocationName, mobsSpawned, escort, roadQuest);
    }

    /** Transitions to {@link QuestStatus#AWAITING_REWARD_PICKUP} carrying the still-uncollected reward stacks. */
    public QuestInstance withPendingRewardItems(List<ItemStack> items) {
        return new QuestInstance(instanceId, definitionId, QuestStatus.AWAITING_REWARD_PICKUP, milestoneProgress, assignedAtGameTime, hintPos, hintDimensionId,
                locationName, List.copyOf(items), rewardMultiplier, knowledgePlaceId, directionPhrase, realLocationName, mobsSpawned, escort, roadQuest);
    }

    /** Sets the real-knowledge wiring for a region quest -- see this class's own doc. {@code locationName} starts equal to {@code newDirectionPhrase}, upgraded later by {@code QuestWaypointTicker}. */
    public QuestInstance withRegionKnowledge(long newKnowledgePlaceId, String newDirectionPhrase, String newRealLocationName) {
        return new QuestInstance(instanceId, definitionId, status, milestoneProgress, assignedAtGameTime, hintPos, hintDimensionId,
                Optional.of(newDirectionPhrase), pendingRewardItems, rewardMultiplier,
                Optional.of(newKnowledgePlaceId), Optional.of(newDirectionPhrase), Optional.of(newRealLocationName), mobsSpawned, escort, roadQuest);
    }

    /** Upgrades {@link #locationName()} once the player's real knowledge of {@link #knowledgePlaceId()} is good enough -- see {@code QuestWaypointTicker}. */
    public QuestInstance withUpgradedLocationName(String upgradedLocationName) {
        return new QuestInstance(instanceId, definitionId, status, milestoneProgress, assignedAtGameTime, hintPos, hintDimensionId,
                Optional.of(upgradedLocationName), pendingRewardItems, rewardMultiplier, knowledgePlaceId, directionPhrase, realLocationName, mobsSpawned, escort, roadQuest);
    }

    public QuestInstance withMobsSpawned() {
        return new QuestInstance(instanceId, definitionId, status, milestoneProgress, assignedAtGameTime, hintPos, hintDimensionId,
                locationName, pendingRewardItems, rewardMultiplier, knowledgePlaceId, directionPhrase, realLocationName, true, escort, roadQuest);
    }

    /** Resets {@link #mobsSpawned()} to {@code false} for one cycle -- used by {@code QuestEscortTicker} to re-trigger {@code QuestMobSpawnTicker}'s existing spawn-guarantee logic at each new unguarded stake. */
    public QuestInstance withMobsSpawnedReset() {
        return new QuestInstance(instanceId, definitionId, status, milestoneProgress, assignedAtGameTime, hintPos, hintDimensionId,
                locationName, pendingRewardItems, rewardMultiplier, knowledgePlaceId, directionPhrase, realLocationName, false, escort, roadQuest);
    }

    /** Binds the live caravan entity and its full route -- set once, right after the caravan is spawned at assignment (see {@code NpcQuests#registerEscortHook}). */
    public QuestInstance withEscort(UUID caravanUuid, List<BlockPos> route) {
        return new QuestInstance(instanceId, definitionId, status, milestoneProgress, assignedAtGameTime, hintPos, hintDimensionId,
                locationName, pendingRewardItems, rewardMultiplier, knowledgePlaceId, directionPhrase, realLocationName, mobsSpawned,
                new EscortData(Optional.of(caravanUuid), List.copyOf(route)), roadQuest);
    }

    /** Pops the just-reached waypoint off the front of the escort route -- see {@code QuestEscortTicker}. */
    public QuestInstance withEscortWaypointReached() {
        List<BlockPos> remaining = escort.remainingRoute().isEmpty() ? escort.remainingRoute() : escort.remainingRoute().subList(1, escort.remainingRoute().size());
        return new QuestInstance(instanceId, definitionId, status, milestoneProgress, assignedAtGameTime, hintPos, hintDimensionId,
                locationName, pendingRewardItems, rewardMultiplier, knowledgePlaceId, directionPhrase, realLocationName, mobsSpawned,
                new EscortData(escort.entityUuid(), List.copyOf(remaining)), roadQuest);
    }

    /** Sets this road-connection instance's origin settlement and stake-count thresholds -- the destination settlement itself reuses {@link #withRegionKnowledge} (its placeId goes into {@link #knowledgePlaceId()}). See {@code RoadQuestLogic#acceptOffer}. */
    public QuestInstance withRoadQuestData(long newRoadOriginPlaceId, int newRoadMinStakes, int newRoadOptimalStakeCap) {
        return new QuestInstance(instanceId, definitionId, status, milestoneProgress, assignedAtGameTime, hintPos, hintDimensionId,
                locationName, pendingRewardItems, rewardMultiplier, knowledgePlaceId, directionPhrase, realLocationName, mobsSpawned, escort,
                new RoadQuestData(Optional.of(newRoadOriginPlaceId), newRoadMinStakes, newRoadOptimalStakeCap));
    }

    public boolean isMilestoneComplete(QuestMilestone milestone) {
        return milestoneProgress.getOrDefault(milestone.id(), 0) >= milestone.requiredCount();
    }
}
