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
 * <p>{@code locationName} is the *currently displayed* resolved phrase substituted into a quest's
 * {@code {location}}-tokened flavor text (see {@code quest.QuestText#resolve}) -- for an on-site
 * quest it's set once at assignment and never changes; for a region quest (see below) it starts as
 * {@link #directionPhrase()} and is live-upgraded by {@code QuestWaypointTicker} once the player's
 * real knowledge of {@link #knowledgePlaceId()} is good enough. Empty for a quest with no resolvable
 * real location (the token then falls back to a generic phrase).
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
 *
 * <p><b>Region-quest fields, added 2026-10-11</b> (explicit user correction/clarification: a quest's
 * real target was always meant to be a real Cartographyr place, with vague *text* -- not pre-granted
 * knowledge -- giving the initial vague marker, sharpening to a precise one for free once the player
 * turns out to already know that real place from ordinary exploration, same as any other knowledge):
 * <ul>
 *   <li>{@code knowledgePlaceId} -- the real Cartographyr {@code geo.EntityId#value()} of the picked
 *   Region/Settlement, used as the {@code bridge.LyfeWaypointBridge} placeId *instead of* a synthetic
 *   per-instance one. This is what makes "already been there" naturally resolve to full knowledge --
 *   {@code location.LocationTracker} in Lyfe already calls {@code markVisited} with this exact same
 *   real key during ordinary exploration, so a player's existing knowledge just carries over with no
 *   special-casing needed. Empty for anything that isn't a real-Cartographyr-backed quest (on-site
 *   quests, {@code findOldHarlen}'s synthetic point).</li>
 *   <li>{@code directionPhrase} -- the stable "to the east of Bramton" text, computed once at
 *   assignment from the real bearing giver-&gt;target, never changes afterward.</li>
 *   <li>{@code realLocationName} -- the target's true Cartographyr name (e.g. "the Whispering
 *   Forest"), known internally from the start but not shown in {@link #locationName()} until the
 *   player's knowledge of {@link #knowledgePlaceId()} is good enough.</li>
 *   <li>{@code mobsSpawned} -- whether {@code quest.QuestMobSpawnTicker} has already topped up live
 *   mobs near this instance's target to guarantee the kill milestone is actually completable (fires
 *   once, the first time a player gets close) -- "there may be other wolves there and they'd also
 *   count, but it should make sure the player is able to do the quest otherwise."</li>
 * </ul>
 */
public record QuestInstance(UUID instanceId, Identifier definitionId, QuestStatus status, Map<String, Integer> milestoneProgress,
                             long assignedAtGameTime, Optional<BlockPos> hintPos, Optional<String> hintDimensionId,
                             Optional<String> locationName, List<ItemStack> pendingRewardItems, double rewardMultiplier,
                             Optional<Long> knowledgePlaceId, Optional<String> directionPhrase, Optional<String> realLocationName,
                             boolean mobsSpawned) {

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
            Codec.BOOL.optionalFieldOf("mobs_spawned", false).forGetter(QuestInstance::mobsSpawned)
    ).apply(instance, QuestInstance::new));

    public static QuestInstance freshlyAssigned(Identifier definitionId, long gameTime) {
        return new QuestInstance(UUID.randomUUID(), definitionId, QuestStatus.IN_PROGRESS, Map.of(), gameTime, Optional.empty(), Optional.empty(),
                Optional.empty(), List.of(), 1.0, Optional.empty(), Optional.empty(), Optional.empty(), false);
    }

    public QuestInstance withStatus(QuestStatus newStatus) {
        return new QuestInstance(instanceId, definitionId, newStatus, milestoneProgress, assignedAtGameTime, hintPos, hintDimensionId,
                locationName, pendingRewardItems, rewardMultiplier, knowledgePlaceId, directionPhrase, realLocationName, mobsSpawned);
    }

    public QuestInstance withProgress(String milestoneId, int amount) {
        Map<String, Integer> updated = new java.util.HashMap<>(milestoneProgress);
        updated.put(milestoneId, amount);
        return new QuestInstance(instanceId, definitionId, status, Map.copyOf(updated), assignedAtGameTime, hintPos, hintDimensionId,
                locationName, pendingRewardItems, rewardMultiplier, knowledgePlaceId, directionPhrase, realLocationName, mobsSpawned);
    }

    public QuestInstance withHint(BlockPos pos, String dimensionId, Optional<String> newLocationName) {
        return new QuestInstance(instanceId, definitionId, status, milestoneProgress, assignedAtGameTime, Optional.of(pos), Optional.of(dimensionId),
                newLocationName, pendingRewardItems, rewardMultiplier, knowledgePlaceId, directionPhrase, realLocationName, mobsSpawned);
    }

    public QuestInstance withRewardMultiplier(double newRewardMultiplier) {
        return new QuestInstance(instanceId, definitionId, status, milestoneProgress, assignedAtGameTime, hintPos, hintDimensionId,
                locationName, pendingRewardItems, newRewardMultiplier, knowledgePlaceId, directionPhrase, realLocationName, mobsSpawned);
    }

    /** Transitions to {@link QuestStatus#AWAITING_REWARD_PICKUP} carrying the still-uncollected reward stacks. */
    public QuestInstance withPendingRewardItems(List<ItemStack> items) {
        return new QuestInstance(instanceId, definitionId, QuestStatus.AWAITING_REWARD_PICKUP, milestoneProgress, assignedAtGameTime, hintPos, hintDimensionId,
                locationName, List.copyOf(items), rewardMultiplier, knowledgePlaceId, directionPhrase, realLocationName, mobsSpawned);
    }

    /** Sets the real-knowledge wiring for a region quest -- see this class's own doc. {@code locationName} starts equal to {@code newDirectionPhrase}, upgraded later by {@code QuestWaypointTicker}. */
    public QuestInstance withRegionKnowledge(long newKnowledgePlaceId, String newDirectionPhrase, String newRealLocationName) {
        return new QuestInstance(instanceId, definitionId, status, milestoneProgress, assignedAtGameTime, hintPos, hintDimensionId,
                Optional.of(newDirectionPhrase), pendingRewardItems, rewardMultiplier,
                Optional.of(newKnowledgePlaceId), Optional.of(newDirectionPhrase), Optional.of(newRealLocationName), mobsSpawned);
    }

    /** Upgrades {@link #locationName()} once the player's real knowledge of {@link #knowledgePlaceId()} is good enough -- see {@code QuestWaypointTicker}. */
    public QuestInstance withUpgradedLocationName(String upgradedLocationName) {
        return new QuestInstance(instanceId, definitionId, status, milestoneProgress, assignedAtGameTime, hintPos, hintDimensionId,
                Optional.of(upgradedLocationName), pendingRewardItems, rewardMultiplier, knowledgePlaceId, directionPhrase, realLocationName, mobsSpawned);
    }

    public QuestInstance withMobsSpawned() {
        return new QuestInstance(instanceId, definitionId, status, milestoneProgress, assignedAtGameTime, hintPos, hintDimensionId,
                locationName, pendingRewardItems, rewardMultiplier, knowledgePlaceId, directionPhrase, realLocationName, true);
    }

    public boolean isMilestoneComplete(QuestMilestone milestone) {
        return milestoneProgress.getOrDefault(milestone.id(), 0) >= milestone.requiredCount();
    }
}
