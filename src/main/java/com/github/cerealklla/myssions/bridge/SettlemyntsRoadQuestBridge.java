package com.github.cerealklla.myssions.bridge;

import java.util.Optional;
import java.util.UUID;

import com.github.cerealklla.settlemynts.api.Settlemynts;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.fml.ModList;

/**
 * Inter-settlement road-connection quest support -- optional soft dependency into Settlemynts'
 * three new Highway-quest facade methods (added 2026-10-11, see that class's own doc and {@code
 * roadway.HighwayQuestSupport} for the real implementation). Same isolation convention as every
 * other bridge in this mod: every call site must check {@link #isLoaded()} first.
 */
public final class SettlemyntsRoadQuestBridge {

    private SettlemyntsRoadQuestBridge() {
    }

    public static boolean isLoaded() {
        return ModList.get().isLoaded("settlemynts");
    }

    public record Progress(int stakeCount, boolean connected) {
    }

    public static Optional<UUID> findCoreIdForPlaceId(ServerLevel level, long placeId) {
        return Settlemynts.findCoreIdForPlaceId(level, placeId);
    }

    public static void grantHighwayStakes(ServerPlayer player, UUID instanceId, Optional<UUID> coreIdA, Optional<UUID> coreIdB, int tier, int count) {
        Settlemynts.grantHighwayStakes(player, instanceId, coreIdA, coreIdB, tier, count);
    }

    public static Progress checkProgress(ServerLevel level, UUID instanceId, BlockPos nearHint, long placeIdA, long placeIdB) {
        var result = Settlemynts.checkHighwayProgress(level, instanceId, nearHint, placeIdA, placeIdB);
        return new Progress(result.stakeCount(), result.connected());
    }

    public static int getSettlementTier(ServerLevel level, long placeId) {
        return Settlemynts.getSettlementTier(level, placeId);
    }

    public static int buybackUnusedStakes(ServerPlayer player, UUID instanceId) {
        return Settlemynts.buybackUnusedHighwayStakes(player, instanceId);
    }

    /** Despawns every placed Highway Stake for this instance, reverting its paved terrain first -- see {@code api.Settlemynts#despawnHighwayStakesForInstance}'s own doc. Used on quest failure/abandon, never on a normal completion (the stake network is meant to persist once connected). */
    public static void despawnStakesForInstance(ServerLevel level, UUID instanceId, BlockPos nearHint) {
        Settlemynts.despawnHighwayStakesForInstance(level, instanceId, nearHint);
    }
}
