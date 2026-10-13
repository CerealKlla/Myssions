package com.github.cerealklla.myssions.quest;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import com.github.cerealklla.cartographyr.api.Cartography;
import com.github.cerealklla.cartographyr.geo.Classification;
import com.github.cerealklla.cartographyr.geo.GeographicEntity;
import com.github.cerealklla.cartographyr.geo.Layer;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * "NPC towns should be able to generate these [Establish Highway] quests themselves automatically
 * too" -- explicit user follow-up, 2026-10-13, confirmed scoped to natural (NPC) villages only (a
 * player-founded settlement always has a real Mayor, who uses the paid Town Hall sign button
 * instead) and free (no gold cost -- there's no real NPC-held treasury to charge against). Mirrors
 * {@code bridge.QuestBoardMidnightTicker}'s own "once a day, per candidate, roll a chance" shape.
 *
 * <p>Posts through the exact same {@code RoadQuestLogic#postOffer} every other poster (the player
 * button, this ticker) already uses -- {@code postingPlotId}/{@code posterPlayerId} both {@code
 * Optional.empty()}, exactly what that method's own doc (and {@code RoadQuestRegistry.Entry}'s own
 * field docs) already anticipated ("empty for an NPC-auto-posted offer with no real plot concept" /
 * "empty for an NPC posting (flat 1/3-quality grant instead)") well before this ticker existed to
 * actually trigger either case. A natural village posting a quest is otherwise handled identically
 * to a player-posted one from here on -- same {@link RoadQuestRegistry} state machine, same stake
 * grant/progress/buyback/6-hour-deadline/fail-abandon-cleanup, same knowledge-transfer-on-accept
 * fallback.
 *
 * <p>Destination candidates are any other settlement (natural or player-founded) within {@link
 * #SEARCH_RADIUS_BLOCKS}, distance-weighted toward closer ones (same shape as {@code
 * bridge.CartographyrRegionBridge#pickWeightedNearby}), excluding any pair that already has a
 * {@link RoadQuestRegistry} entry of any kind -- a natural village can never re-roll a pair it (or
 * the other side) has already posted/connected.
 *
 * <p>Registered unconditionally in {@code MyssionsMod}, same precedent as {@code RoadQuestTicker}/
 * {@code RoadQuestLogic} themselves (both already reference Cartographyr types directly with no
 * {@code ModList} guard) -- this whole quest style has no meaningful existence without Cartographyr
 * loaded at all.
 */
public final class NaturalVillageHighwayTicker {

    private static final long TICKS_PER_DAY = 24000L;
    private static final double GENERATE_CHANCE = 0.5;
    // Matches the player-driven Establish Highway map picker's own search radius (Settlemynts'
    // own ESTABLISH_HIGHWAY_SEARCH_RADIUS_BLOCKS) -- same "2 real miles" feel, kept as its own
    // constant here since this ticker has no reason to depend on that class.
    private static final double SEARCH_RADIUS_BLOCKS = 2.0 * 1609.0;
    private static final double MIN_DISTANCE_BLOCKS = 48.0;

    private long lastKnownDay = -1;
    private final RandomSource random = RandomSource.create();

    @SubscribeEvent
    public void onServerTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        ServerLevel level = server.overworld();
        long currentDay = Math.floorDiv(level.getGameTime(), TICKS_PER_DAY);
        if (lastKnownDay < 0) {
            lastKnownDay = currentDay; // First tick since this listener was created -- establish the baseline only.
            return;
        }
        if (currentDay <= lastKnownDay) {
            return;
        }
        lastKnownDay = currentDay;

        RoadQuestRegistry registry = RoadQuestRegistry.get(server);
        List<GeographicEntity> naturalVillages = new ArrayList<>(Cartography.findEntities(level, Classification.NATURAL, Layer.SETTLEMENT_ID));
        List<GeographicEntity> allSettlements = new ArrayList<>(naturalVillages);
        allSettlements.addAll(Cartography.findEntities(level, Classification.CONSTRUCTED, Layer.SETTLEMENT_ID));

        for (GeographicEntity origin : naturalVillages) {
            if (random.nextDouble() >= GENERATE_CHANCE) {
                continue;
            }
            pickDestination(level, origin, allSettlements, registry).ifPresent(destinationPlaceId ->
                    RoadQuestLogic.postOffer(level, origin.id().value(), destinationPlaceId, Optional.empty(), Optional.empty()));
        }
    }

    private Optional<Long> pickDestination(ServerLevel level, GeographicEntity origin, List<GeographicEntity> allSettlements, RoadQuestRegistry registry) {
        long originPlaceId = origin.id().value();
        List<GeographicEntity> inRange = new ArrayList<>();
        List<Double> distances = new ArrayList<>();
        for (GeographicEntity candidate : allSettlements) {
            if (candidate.id().value() == originPlaceId || !candidate.dimension().equals(level.dimension())) {
                continue;
            }
            if (registry.get(originPlaceId, candidate.id().value()).isPresent()) {
                continue; // Already posted/in-progress/connected -- not a valid new destination.
            }
            double dx = candidate.geometry().centerBlockX() - origin.geometry().centerBlockX();
            double dz = candidate.geometry().centerBlockZ() - origin.geometry().centerBlockZ();
            double dist = Math.sqrt(dx * dx + dz * dz);
            if (dist < MIN_DISTANCE_BLOCKS || dist > SEARCH_RADIUS_BLOCKS) {
                continue;
            }
            inRange.add(candidate);
            distances.add(dist);
        }
        if (inRange.isEmpty()) {
            return Optional.empty();
        }

        double[] weights = new double[inRange.size()];
        double totalWeight = 0.0;
        for (int i = 0; i < inRange.size(); i++) {
            double fraction = distances.get(i) / SEARCH_RADIUS_BLOCKS;
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
        return Optional.of(inRange.get(chosen).id().value());
    }
}
