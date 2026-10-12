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
     * Weighted-random real Region within {@code maxRadiusBlocks} of {@code origin}, in the same
     * dimension, favoring closer candidates ("higher odds of closer than further from the starting
     * point" -- explicit user spec) via a linear falloff with a small floor so even the farthest
     * in-range candidate still has *some* chance. Empty if nothing qualifies (including when
     * Cartographyr simply hasn't discovered anything out there yet).
     *
     * <p><b>Natural Regions only, never a Settlement</b> -- real live report, 2026-10-12 (twice): an
     * earlier version of this method also matched every individual Settlemynts plot (its own zone
     * layer) as a candidate, letting a region-kill quest pick a plot inside the issuing player's own
     * settlement ("Kill 6 Zombies ... in Muffin's Home"), where town guards killed the mobs before the
     * player could do anything -- fixed that day by narrowing candidates to whole Region/Settlement
     * entities only. That fix was incomplete: a whole *Settlement* has the identical problem one level
     * up, confirmed live the same way ("Raiders on the Old Road" landed inside "Percot," an existing
     * settlement, with "their guards would certainly interfere... when I got there there actually were
     * no pillagers at all" -- submitted via the new in-game Report Bug button). No region-kill quest's
     * mobs can ever survive guarded settlement turf regardless of flavor text, so Settlement-layer
     * entities are no longer a candidate kind at all here -- every quest using this method targets
     * open wilderness exclusively now, matching the user's own fix suggestion directly ("needs to be
     * based on a location of a road between settlements, not within one").
     */
    public static Optional<RegionTarget> pickWeightedNearby(ServerLevel level, BlockPos origin, double maxRadiusBlocks, RandomSource random) {
        List<GeographicEntity> candidates = new ArrayList<>(Cartography.findEntities(level, Classification.NATURAL, Layer.REGION_ID));

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
            // Real report, 2026-10-12: "Pest Control Out Back" (kill 5 Spiders) landed its target
            // dead center in open ocean ("The Shoals") -- a region/settlement's own centerBlockX/Z
            // is just its geometric centroid, with no guarantee it's dry land, and this quest family
            // guarantees mob completability by spawning directly at that point (see
            // quest.QuestMobSpawnTicker), which is meaningless underwater for a land mob. Rejected
            // outright rather than just down-weighted -- an impossible-to-complete quest is worse
            // than a slightly smaller candidate pool.
            if (isOpenWaterAt(level, entity.geometry().centerBlockX(), entity.geometry().centerBlockZ())) {
                continue;
            }
            // Real report, 2026-10-12: a player stood at the quest's own named location and the
            // name shown didn't match, and kills there weren't counting. Root cause -- this method
            // trusts a candidate's own centerBlockX/Z to be real, on-the-ground proof of "this is
            // where that place is," but it never checks whether that exact point actually resolves
            // back to the SAME entity through a live point-in-polygon lookup. Region and Settlement
            // geometry can overlap (a settlement sits inside its surrounding region by design), so a
            // candidate's centroid can land inside a *different* overlapping entity than itself --
            // e.g. a Region candidate whose own center happens to fall inside a smaller Settlement
            // nested within it. Both {@code MobRegionTagListener} (tags a spawned mob by a live
            // lookup at its spawn position) and Lyfe's own on-site "Location:" HUD line resolve
            // whatever's actually found at that exact point, which is this candidate's own id only
            // if the two agree -- rejecting any candidate where they don't guarantees the quest's
            // advertised name, the live HUD name, and the kill-credit tag id all describe the same
            // place.
            if (!resolvesToSelf(level, entity)) {
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

    /** True if the real surface at (x, z) is a fluid (ocean/river/lake), not dry ground -- see {@link #pickWeightedNearby}'s own doc for why this excludes a candidate outright. */
    private static boolean isOpenWaterAt(ServerLevel level, int x, int z) {
        int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
        return !level.getFluidState(new BlockPos(x, y - 1, z)).isEmpty();
    }

    /**
     * True if looking {@code entity} back up by its own centroid, through the exact same lookup
     * {@link #findRegionOrSettlementPlaceIdAt} uses (and that {@code MobRegionTagListener} tags a
     * spawned mob with), resolves to {@code entity} itself -- see {@link #pickWeightedNearby}'s own
     * doc for why a mismatch here is the real root cause worth rejecting a candidate over. Reuses
     * that method rather than re-implementing the lookup, so "does this candidate own its own
     * center" can never silently drift from what a mob spawned there would actually be tagged with.
     */
    private static boolean resolvesToSelf(ServerLevel level, GeographicEntity entity) {
        BlockPos center = new BlockPos(entity.geometry().centerBlockX(), 0, entity.geometry().centerBlockZ());
        return findRegionOrSettlementPlaceIdAt(level, center)
                .map(placeId -> placeId == entity.id().value())
                .orElse(false);
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
