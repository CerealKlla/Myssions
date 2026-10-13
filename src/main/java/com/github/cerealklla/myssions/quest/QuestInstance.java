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
 * never changes; for a region quest it's live-recomputed every tick by {@code QuestWaypointTicker}
 * via {@code bridge.LyfeWaypointBridge#resolveLocationText} (added 2026-10-12 -- Lyfe now owns the
 * entire "how much is known, how should it be phrased" decision; this field is just where the
 * already-decided answer gets cached for the client, which has no way to ask Lyfe itself).
 *
 * <p>{@code pendingRewardItems} holds the Gold/Item reward stacks still waiting to be picked up
 * while {@link #status()} is {@link QuestStatus#AWAITING_REWARD_PICKUP}. {@code rewardMultiplier}
 * scales Gold/Item/XP rewards (region-quest distance scaling, or the road-quest efficiency bonus).
 *
 * <p>{@code knowledgePlaceId}/{@code realLocationName} -- the real Cartographyr place this quest is
 * about (a region quest's target, or a road quest's destination settlement) and its true name, used
 * as the real {@code bridge.LyfeWaypointBridge} knowledge key so "already been there" resolves to
 * full knowledge for free. (The old {@code directionPhrase} field was removed 2026-10-12 -- Lyfe now
 * computes direction/distance live from the reference point granted at assignment, instead of this
 * mod caching a static precomputed phrase.) {@code mobsSpawned} is whether {@code
 * QuestMobSpawnTicker} has already topped up live mobs near this instance's target to guarantee
 * completability.
 *
 * <p>{@code assignedAtRealEpochMillis} (added 2026-10-13, fills this record's last remaining codec
 * slot -- real live-test report: a {@link QuestTimeLimit.Kind#GAME_TICKS_FROM_ASSIGNMENT} deadline
 * (e.g. the road-connection quest's 6-real-hour limit) is anchored to {@link #assignedAtGameTime()},
 * the world's own tick counter -- which does NOT advance while the server process itself is down,
 * so every deploy-triggered restart this session silently paused every in-flight timed quest's
 * countdown for real minutes at a time, contradicting "reflects the real, clock time deadline."
 * {@code Optional.empty()} for any instance assigned before this field existed (falls back to the
 * old tick-only behavior -- see {@code QuestTimeLimit#deadlineGameTime}/{@code QuestDeadlineTicker}/
 * {@code client.ActiveQuestOverlay}, all three of which now prefer this real timestamp when present).
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
                             Optional<Long> knowledgePlaceId, Optional<String> realLocationName,
                             boolean mobsSpawned, EscortData escort, RoadQuestData roadQuest,
                             Optional<Long> assignedAtRealEpochMillis) {

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
            Codec.STRING.optionalFieldOf("real_location_name").forGetter(QuestInstance::realLocationName),
            Codec.BOOL.optionalFieldOf("mobs_spawned", false).forGetter(QuestInstance::mobsSpawned),
            EscortData.CODEC.optionalFieldOf("escort", EscortData.NONE).forGetter(QuestInstance::escort),
            RoadQuestData.CODEC.optionalFieldOf("road_quest", RoadQuestData.NONE).forGetter(QuestInstance::roadQuest),
            Codec.LONG.optionalFieldOf("assigned_at_real_epoch_millis").forGetter(QuestInstance::assignedAtRealEpochMillis)
    ).apply(instance, QuestInstance::new));

    public static QuestInstance freshlyAssigned(Identifier definitionId, long gameTime) {
        return new QuestInstance(UUID.randomUUID(), definitionId, QuestStatus.IN_PROGRESS, Map.of(), gameTime, Optional.empty(), Optional.empty(),
                Optional.empty(), List.of(), 1.0, Optional.empty(), Optional.empty(), false, EscortData.NONE, RoadQuestData.NONE,
                Optional.of(System.currentTimeMillis()));
    }

    public QuestInstance withStatus(QuestStatus newStatus) {
        return new QuestInstance(instanceId, definitionId, newStatus, milestoneProgress, assignedAtGameTime, hintPos, hintDimensionId,
                locationName, pendingRewardItems, rewardMultiplier, knowledgePlaceId, realLocationName, mobsSpawned, escort, roadQuest, assignedAtRealEpochMillis);
    }

    public QuestInstance withProgress(String milestoneId, int amount) {
        Map<String, Integer> updated = new java.util.HashMap<>(milestoneProgress);
        updated.put(milestoneId, amount);
        return new QuestInstance(instanceId, definitionId, status, Map.copyOf(updated), assignedAtGameTime, hintPos, hintDimensionId,
                locationName, pendingRewardItems, rewardMultiplier, knowledgePlaceId, realLocationName, mobsSpawned, escort, roadQuest, assignedAtRealEpochMillis);
    }

    public QuestInstance withHint(BlockPos pos, String dimensionId, Optional<String> newLocationName) {
        return new QuestInstance(instanceId, definitionId, status, milestoneProgress, assignedAtGameTime, Optional.of(pos), Optional.of(dimensionId),
                newLocationName, pendingRewardItems, rewardMultiplier, knowledgePlaceId, realLocationName, mobsSpawned, escort, roadQuest, assignedAtRealEpochMillis);
    }

    public QuestInstance withRewardMultiplier(double newRewardMultiplier) {
        return new QuestInstance(instanceId, definitionId, status, milestoneProgress, assignedAtGameTime, hintPos, hintDimensionId,
                locationName, pendingRewardItems, newRewardMultiplier, knowledgePlaceId, realLocationName, mobsSpawned, escort, roadQuest, assignedAtRealEpochMillis);
    }

    /** Transitions to {@link QuestStatus#AWAITING_REWARD_PICKUP} carrying the still-uncollected reward stacks. */
    public QuestInstance withPendingRewardItems(List<ItemStack> items) {
        return new QuestInstance(instanceId, definitionId, QuestStatus.AWAITING_REWARD_PICKUP, milestoneProgress, assignedAtGameTime, hintPos, hintDimensionId,
                locationName, List.copyOf(items), rewardMultiplier, knowledgePlaceId, realLocationName, mobsSpawned, escort, roadQuest, assignedAtRealEpochMillis);
    }

    /** Sets the real-knowledge wiring for a region quest -- see this class's own doc. {@code locationName} is left unset; {@code QuestWaypointTicker} fills it in on its very next tick via {@code bridge.LyfeWaypointBridge#resolveLocationText}. */
    public QuestInstance withRegionKnowledge(long newKnowledgePlaceId, String newRealLocationName) {
        return new QuestInstance(instanceId, definitionId, status, milestoneProgress, assignedAtGameTime, hintPos, hintDimensionId,
                locationName, pendingRewardItems, rewardMultiplier,
                Optional.of(newKnowledgePlaceId), Optional.of(newRealLocationName), mobsSpawned, escort, roadQuest, assignedAtRealEpochMillis);
    }

    /** Upgrades {@link #locationName()} once the player's real knowledge of {@link #knowledgePlaceId()} is good enough -- see {@code QuestWaypointTicker}. */
    public QuestInstance withUpgradedLocationName(String upgradedLocationName) {
        return new QuestInstance(instanceId, definitionId, status, milestoneProgress, assignedAtGameTime, hintPos, hintDimensionId,
                Optional.of(upgradedLocationName), pendingRewardItems, rewardMultiplier, knowledgePlaceId, realLocationName, mobsSpawned, escort, roadQuest, assignedAtRealEpochMillis);
    }

    public QuestInstance withMobsSpawned() {
        return new QuestInstance(instanceId, definitionId, status, milestoneProgress, assignedAtGameTime, hintPos, hintDimensionId,
                locationName, pendingRewardItems, rewardMultiplier, knowledgePlaceId, realLocationName, true, escort, roadQuest, assignedAtRealEpochMillis);
    }

    /** Resets {@link #mobsSpawned()} to {@code false} for one cycle -- used by {@code QuestEscortTicker} to re-trigger {@code QuestMobSpawnTicker}'s existing spawn-guarantee logic at each new unguarded stake. */
    public QuestInstance withMobsSpawnedReset() {
        return new QuestInstance(instanceId, definitionId, status, milestoneProgress, assignedAtGameTime, hintPos, hintDimensionId,
                locationName, pendingRewardItems, rewardMultiplier, knowledgePlaceId, realLocationName, false, escort, roadQuest, assignedAtRealEpochMillis);
    }

    /** Binds the live caravan entity and its full route -- set once, right after the caravan is spawned at assignment (see {@code NpcQuests#registerEscortHook}). */
    public QuestInstance withEscort(UUID caravanUuid, List<BlockPos> route) {
        return new QuestInstance(instanceId, definitionId, status, milestoneProgress, assignedAtGameTime, hintPos, hintDimensionId,
                locationName, pendingRewardItems, rewardMultiplier, knowledgePlaceId, realLocationName, mobsSpawned,
                new EscortData(Optional.of(caravanUuid), List.copyOf(route)), roadQuest, assignedAtRealEpochMillis);
    }

    /** Pops the just-reached waypoint off the front of the escort route -- see {@code QuestEscortTicker}. */
    public QuestInstance withEscortWaypointReached() {
        List<BlockPos> remaining = escort.remainingRoute().isEmpty() ? escort.remainingRoute() : escort.remainingRoute().subList(1, escort.remainingRoute().size());
        return new QuestInstance(instanceId, definitionId, status, milestoneProgress, assignedAtGameTime, hintPos, hintDimensionId,
                locationName, pendingRewardItems, rewardMultiplier, knowledgePlaceId, realLocationName, mobsSpawned,
                new EscortData(escort.entityUuid(), List.copyOf(remaining)), roadQuest, assignedAtRealEpochMillis);
    }

    /** Sets this road-connection instance's origin settlement and stake-count thresholds -- the destination settlement itself reuses {@link #withRegionKnowledge} (its placeId goes into {@link #knowledgePlaceId()}). See {@code RoadQuestLogic#acceptOffer}. */
    public QuestInstance withRoadQuestData(long newRoadOriginPlaceId, int newRoadMinStakes, int newRoadOptimalStakeCap) {
        return new QuestInstance(instanceId, definitionId, status, milestoneProgress, assignedAtGameTime, hintPos, hintDimensionId,
                locationName, pendingRewardItems, rewardMultiplier, knowledgePlaceId, realLocationName, mobsSpawned, escort,
                new RoadQuestData(Optional.of(newRoadOriginPlaceId), newRoadMinStakes, newRoadOptimalStakeCap), assignedAtRealEpochMillis);
    }

    /**
     * A plot-upgrade milestone's real required amount is resolved per-instance (the plot's live
     * shortfall at acceptance time), not the static {@link QuestMilestone#requiredCount()} the
     * record itself carries only as a meaningless placeholder for this kind -- see {@code
     * QuestText}'s own doc for how it's packed onto {@link #locationName()}.
     */
    public boolean isMilestoneComplete(QuestMilestone milestone) {
        int required = milestone.isPlotUpgradeMilestone() ? plotUpgradeRequiredAmount(milestone) : milestone.requiredCount();
        return milestoneProgress.getOrDefault(milestone.id(), 0) >= required;
    }

    private int plotUpgradeRequiredAmount(QuestMilestone milestone) {
        QuestText.PlotUpgradeAmounts amounts = QuestText.resolvePlotUpgradeAmounts(this).orElse(new QuestText.PlotUpgradeAmounts(0, 0));
        return "stone".equals(milestone.proofId().orElse("")) ? amounts.stoneNeeded() : amounts.woodNeeded();
    }
}
