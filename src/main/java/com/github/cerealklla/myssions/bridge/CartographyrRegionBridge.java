package com.github.cerealklla.myssions.bridge;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import com.github.cerealklla.cartographyr.api.Cartography;
import com.github.cerealklla.cartographyr.geo.Classification;
import com.github.cerealklla.cartographyr.geo.DisplayText;
import com.github.cerealklla.cartographyr.geo.GeographicEntity;

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
        candidates.addAll(Cartography.findEntities(level, Classification.NATURAL));
        candidates.addAll(Cartography.findEntities(level, Classification.CONSTRUCTED));

        List<GeographicEntity> inRange = new ArrayList<>();
        List<Double> distances = new ArrayList<>();
        for (GeographicEntity entity : candidates) {
            if (!entity.dimension().equals(level.dimension())) {
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
