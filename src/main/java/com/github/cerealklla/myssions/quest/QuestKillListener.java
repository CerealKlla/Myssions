package com.github.cerealklla.myssions.quest;

import com.github.cerealklla.myssions.registration.ModAttachments;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;

/**
 * Tracks kill-type milestones (design doc item 7) -- on a {@link LivingDeathEvent} killed by a
 * player, checks every one of that player's in-progress quests for a {@link
 * QuestMilestone#killTargetEntityType()} matching the dead entity's type. Each qualifying kill
 * both reports milestone progress AND grants one unit of that milestone's own {@link
 * QuestMilestone#proofId()} item (via {@link ProofItemCatalog}), up to the milestone's {@code
 * requiredCount} -- so by the time the kill count is done, the player already holds exactly enough
 * proof to turn in, no separate drop-chance roll needed.
 *
 * <p><b>Geo-fenced to the quest's own real location, 2026-10-11</b> (explicit user correction: "if I
 * kill zombies somewhere else [it] will not count toward the quest Milestones ... that is how it
 * SHOULD behave") -- a kill only counts if the instance has a real {@link
 * QuestInstance#hintPos()}/{@link QuestInstance#hintDimensionId()} (set by {@code
 * NpcQuests#registerRegionHook}/{@code #findOldHarlen} for the quests that actually have one) and
 * the dead entity is within {@link #GEOFENCE_RADIUS_BLOCKS} of it in the same dimension. A quest
 * with no hint at all (the genuinely vague, distant wilderness/road quests -- {@code
 * timberWolves}/{@code webChokedGrove}/both Mayor quests, see {@code NpcQuests}' own doc) has no
 * real location to fence against, so those are deliberately left as "kill anywhere" -- unchanged,
 * not an oversight.
 *
 * <p><b>Region-tag fast path, 2026-10-12</b> -- a region-kill quest (one with a real {@link
 * QuestInstance#knowledgePlaceId()}, i.e. an off-site quest whose target is a real Cartographyr
 * Region/Settlement) now checks {@code MobRegionTagListener}'s own spawn-time tag first: if the
 * dead entity was tagged with the same place id, it counts regardless of its *current* distance
 * from the quest's hint point. This is both cheaper (a persistent-data read instead of a distance
 * calculation) and more correct than the old pure-distance check -- a mob that wanders or chases the
 * player outside the hint radius after spawning still correctly counts as "from" that region, which
 * the distance check alone could never express. An untagged dead entity (spawned before this
 * feature existed, or Cartographyr not loaded, or the spawn point wasn't inside any real
 * Region/Settlement) falls back to the original distance check unchanged.
 *
 * <p><b>Night-only milestones, 2026-10-11</b> -- a kill against a {@link QuestMilestone#nightOnly()}
 * milestone only counts while {@code level.isDarkOutside()} (same call {@code QuestNpcEntity}'s own
 * day/night gating already uses), so a quest that claims "this only happens at night" actually
 * enforces it instead of being flavor text a player can ignore. Silent no-op during the day, same
 * shape as the geofence check above -- no current quest sets this flag yet.
 */
public final class QuestKillListener {

    // Flagged tunable, same "placeholder, revisit by feel" convention every other distance/radius
    // constant in this mod uses -- generous enough to cover a plot's own grounds and immediate
    // surroundings ("near the fields"), not a pixel-precise point.
    private static final double GEOFENCE_RADIUS_BLOCKS = 64.0;

    @SubscribeEvent
    public void onDeath(LivingDeathEvent event) {
        if (!(event.getSource().getEntity() instanceof ServerPlayer player)) {
            return;
        }
        Identifier deadType = BuiltInRegistries.ENTITY_TYPE.getKey(event.getEntity().getType());
        BlockPos deadPos = event.getEntity().blockPosition();
        String deadDimensionId = player.level().dimension().identifier().toString();
        Long deadTaggedPlaceId = event.getEntity().getPersistentData().getLong(MobRegionTagListener.TAG_KEY).orElse(null);
        var data = player.getData(ModAttachments.QUEST_DATA);
        for (QuestInstance instance : data.quests().values()) {
            if (instance.status() != QuestStatus.IN_PROGRESS) {
                continue;
            }
            if (!isWithinQuestArea(instance, deadDimensionId, deadPos, deadTaggedPlaceId)) {
                // Temporary diagnostic (2026-10-12, same report as the night-check log below) --
                // only logged for a quest that actually has a real hint location set (a geofenced
                // quest), so this doesn't spam for the "kill anywhere" quests that never geofence.
                if (instance.hintPos().isPresent()) {
                    com.github.cerealklla.myssions.MyssionsMod.LOGGER.info(
                            "[NightKillDebug] player={} killed {} at {} -- rejected by geofence, instance {} hint={} in dim {}",
                            player.getName().getString(), deadType, deadPos, instance.definitionId(),
                            instance.hintPos().get(), instance.hintDimensionId().orElse("?"));
                }
                continue;
            }
            QuestRegistry.get(instance.definitionId()).ifPresent(definition -> {
                for (QuestMilestone milestone : definition.milestones()) {
                    if (milestone.killTargetEntityType().isEmpty() || !milestone.killTargetEntityType().get().equals(deadType)) {
                        continue;
                    }
                    if (milestone.nightOnly() && !player.level().isDarkOutside()) {
                        // Temporary diagnostic (2026-10-12, real report: "not getting credit for
                        // killing them in the area at night") -- remove once the real cause is
                        // confirmed live.
                        com.github.cerealklla.myssions.MyssionsMod.LOGGER.info(
                                "[NightKillDebug] player={} killed {} at {} -- milestone={} nightOnly=true but isDarkOutside()=false",
                                player.getName().getString(), deadType, deadPos, milestone.id());
                        continue;
                    }
                    int current = instance.milestoneProgress().getOrDefault(milestone.id(), 0);
                    if (current >= milestone.requiredCount()) {
                        continue; // Already fully progressed -- no further proof to grant.
                    }
                    QuestEngine.reportMilestoneProgress(player, instance.instanceId(), milestone.id(), 1);
                    milestone.proofId().ifPresent(proofId -> {
                        ItemStack proof = ProofItemCatalog.create(proofId);
                        if (!player.getInventory().add(proof)) {
                            player.drop(proof, false);
                        }
                    });
                }
            });
        }
    }

    private static boolean isWithinQuestArea(QuestInstance instance, String deadDimensionId, BlockPos deadPos, Long deadTaggedPlaceId) {
        // Real report, 2026-10-12: a kill made right at the quest's own real target didn't count.
        // Root cause -- the tag match used to be the *only* check whenever both ids were present,
        // returning false outright on any mismatch instead of falling back to the distance check
        // below. A mismatch is a real, expected case, not just a hypothetical: the mob's tag comes
        // from a fresh, live point-in-polygon lookup at its own spawn position
        // (CartographyrRegionBridge#findRegionOrSettlementPlaceIdAt), which can resolve to a
        // *different* overlapping Region/Settlement than the one pickWeightedNearby originally chose
        // for this quest -- e.g. a Settlement entity and its surrounding natural Region both contain
        // the exact same point, and whichever happens to be first in that lookup's iteration order
        // wins, independent of which one the quest actually named. Same reasoning extends to the
        // player simply standing a few blocks off the true center (the waypoint is a vague circle,
        // never an exact pin) and fighting mobs that wandered slightly -- a tag mismatch there is
        // normal, not disqualifying. The tag is now only ever a fast-path short-circuit for a
        // confirmed match; anything else still gets the real distance check a plain "kill near here"
        // quest always relied on.
        if (instance.knowledgePlaceId().isPresent() && deadTaggedPlaceId != null
                && deadTaggedPlaceId.longValue() == instance.knowledgePlaceId().get()) {
            return true;
        }
        if (instance.hintPos().isEmpty() || instance.hintDimensionId().isEmpty()) {
            return true; // No real location set for this quest -- "kill anywhere" is the deliberate behavior.
        }
        if (!instance.hintDimensionId().get().equals(deadDimensionId)) {
            return false;
        }
        return instance.hintPos().get().distSqr(deadPos) <= GEOFENCE_RADIUS_BLOCKS * GEOFENCE_RADIUS_BLOCKS;
    }
}
