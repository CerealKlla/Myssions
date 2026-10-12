package com.github.cerealklla.myssions.bridge;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import com.github.cerealklla.cartographyr.api.Cartography;
import com.github.cerealklla.cartographyr.geo.Classification;
import com.github.cerealklla.cartographyr.geo.DisplayText;
import com.github.cerealklla.cartographyr.geo.GeographicEntity;
import com.github.cerealklla.cartographyr.geo.Layer;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.fml.ModList;

/**
 * Picks a real, named Cartographyr Region or Settlement near a quest giver for a quest's actual
 * target -- explicit user correction, 2026-10-11: "they were always supposed to pick real,
 * Cartographyr locations, not vague text ... that's how player knowledge, cartographyr, and
 * historian begin to be utilized." Optional soft dependency, same isolation convention as every
 * other bridge in this mod -- every call site must check {@link #isLoaded()} itself before
 * touching this class at all.
 */
public final class CartographyrRegionBridge {

    private CartographyrRegionBridge() {
    }

    public static boolean isLoaded() {
        return ModList.get().isLoaded("cartographyr");
    }

    public record RegionTarget(BlockPos pos, ResourceKey<Level> dimension, String displayName, double distanceBlocks, long placeId) {
    }

    // Don't pick something that's basically right next to the quest giver already -- the whole
    // point is "travel somewhere," and this also keeps a region from colliding with the giver's
    // own settlement.
    private static final double MIN_DISTANCE_BLOCKS = 48.0;

    /**
     * Weighted-random real Region/Settlement within {@code maxRadiusBlocks} of {@code origin}, in
     * the same dimension, favoring closer candidates ("higher odds of closer than further from the
     * starting point" -- explicit user spec) via a linear falloff with a small floor so even the
     * farthest in-range candidate still has *some* chance. Empty if nothing qualifies (including
     * when Cartographyr simply hasn't discovered anything out there yet).
     */
    public static Optional<RegionTarget> pickWeightedNearby(ServerLevel level, BlockPos origin, double maxRadiusBlocks, RandomSource random) {
        List<GeographicEntity> candidates = new ArrayList<>();
        // Layer-filtered, not a blanket Classification scan -- real live report, 2026-10-12: an
        // unfiltered Classification.CONSTRUCTED query also matches every individual Settlemynts plot
        // (its own zone layer), not just whole settlements, so a region-kill quest could pick a plot
        // -- including one inside the issuing player's own settlement ("Kill 6 Zombies ... in
        // Muffin's Home," a Private Residence plot) -- as its "real location" target. Zombies spawned
        // there burned in daylight and were killed by town guards before the player could do
        // anything, exactly the on-site-combat problem this quest shape was built to avoid in the
        // first place (see design-document.md's taxonomy, and the 2026-10-11 removal of the original
        // on-site kill quests). Only Region/Settlement-layer entities are real quest-target candidates.
        candidates.addAll(Cartography.findEntities(level, Classification.NATURAL, Layer.REGION_ID));
        candidates.addAll(Cartography.findEntities(level, Classification.CONSTRUCTED, Layer.SETTLEMENT_ID));

        List<GeographicEntity> inRange = new ArrayList<>();
        List<Double> distances = new ArrayList<>();
        for (GeographicEntity entity : candidates) {
            if (!entity.dimension().equals(level.dimension())) {
                continue;
            }
            // A settlement's own center can easily sit more than MIN_DISTANCE_BLOCKS away from one
            // particular NPC near its edge in a large settlement -- that minimum alone doesn't
            // reliably exclude the issuing player's own settlement (see this method's own doc for the
            // real report this came from). The robust check is whether the quest giver's own position
            // falls inside this candidate's shape at all, regardless of distance-to-center.
            if (entity.geometry().contains(origin.getX(), origin.getZ())) {
                continue;
            }
            double dx = entity.geometry().centerBlockX() - origin.getX();
            double dz = entity.geometry().centerBlockZ() - origin.getZ();
            double dist = Math.sqrt(dx * dx + dz * dz);
            if (dist < MIN_DISTANCE_BLOCKS || dist > maxRadiusBlocks) {
                continue;
            }
            inRange.add(entity);
            distances.add(dist);
        }
        if (inRange.isEmpty()) {
            return Optional.empty();
        }

        double[] weights = new double[inRange.size()];
        double totalWeight = 0.0;
        for (int i = 0; i < inRange.size(); i++) {
            double fraction = distances.get(i) / maxRadiusBlocks; // 0 (close) .. 1 (far)
            weights[i] = Math.max(0.05, 1.0 - fraction);
            totalWeight += weights[i];
        }
        double roll = random.nextDouble() * totalWeight;
        int chosen = inRange.size() - 1;
        for (int i = 0; i < weights.length; i++) {
            roll -= weights[i];
            if (roll <= 0) {
                chosen = i;
                break;
            }
        }

        GeographicEntity entity = inRange.get(chosen);
        int x = entity.geometry().centerBlockX();
        int z = entity.geometry().centerBlockZ();
        int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
        return Optional.of(new RegionTarget(new BlockPos(x, y, z), entity.dimension(), DisplayText.shortForEntity(entity),
                distances.get(chosen), entity.id().value()));
    }

    /**
     * The real Region/Settlement (never a Settlemynts plot or other finer-grained layer -- same
     * layer filter as {@link #pickWeightedNearby}'s own doc explains, since this must agree with
     * what a region-kill quest's {@code knowledgePlaceId} was actually resolved from) whose shape
     * contains {@code pos}, if any -- added 2026-10-12 for {@code quest.MobRegionTagListener}, which
     * tags a freshly-spawned hostile mob with this value at spawn time so {@code QuestKillListener}
     * can check a simple tag match instead of a kill-time distance/geometry check. Uses {@code
     * Cartography.getEntitiesAt} (grid-indexed, O(1)-ish) rather than a {@code findEntities} scan --
     * this runs once per mob spawn, so it needs to stay cheap.
     */
    public static Optional<Long> findRegionOrSettlementPlaceIdAt(ServerLevel level, BlockPos pos) {
        for (GeographicEntity entity : Cartography.getEntitiesAt(level, pos.getX(), pos.getZ())) {
            if (entity.layerId().equals(Layer.REGION_ID) || entity.layerId().equals(Layer.SETTLEMENT_ID)) {
                return Optional.of(entity.id().value());
            }
        }
        return Optional.empty();
    }

    /**
     * Wraps {@code Cartography.findOrPredictSettlementNear} for the "unborn town" quest style
     * (added 2026-10-11) -- see that method's own doc. Deliberately never calls {@code
     * ServerLevel#getHeight} for a {@link com.github.cerealklla.cartographyr.geo.LifecycleState#PLANNED}
     * result: that call loads/generates the target chunk's heightmap, which is exactly the forced
     * generation the user explicitly rejected for this feature. A planned (not-yet-generated)
     * result instead reuses {@code origin}'s own Y as a harmless approximation -- only ever used for
     * a hint/waypoint marker, never real navigation that needs an accurate ground height.
     */
    public static Optional<RegionTarget> findUnbornTownNear(ServerLevel level, BlockPos origin, double radiusBlocks) {
        return Cartography.findOrPredictSettlementNear(level, origin, radiusBlocks).map(entity -> {
            int x = entity.geometry().centerBlockX();
            int z = entity.geometry().centerBlockZ();
            int y = entity.lifecycleState() == com.github.cerealklla.cartographyr.geo.LifecycleState.PLANNED
                    ? origin.getY() : level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
            double dx = x - origin.getX();
            double dz = z - origin.getZ();
            double dist = Math.sqrt(dx * dx + dz * dz);
            return new RegionTarget(new BlockPos(x, y, z), entity.dimension(), DisplayText.shortForEntity(entity),
                    dist, entity.id().value());
        });
    }
}
