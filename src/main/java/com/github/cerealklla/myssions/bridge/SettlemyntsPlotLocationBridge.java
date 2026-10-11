package com.github.cerealklla.myssions.bridge;

import java.util.Optional;
import java.util.UUID;

import com.github.cerealklla.settlemynts.api.Settlemynts;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.neoforged.fml.ModList;

/**
 * Resolves an on-site quest's issuing plot to a real, on-the-ground location -- added 2026-10-10,
 * explicit user feedback: a Farmer quest's milestone just said "near the fields," which means
 * nothing once the player has left and forgotten where that was, and since they were standing right
 * there when they accepted it, they should already "know" exactly where it is (a real waypoint, not
 * a vague circle). Optional soft dependency, same isolation convention as every other bridge in
 * this mod -- every call site must check {@link #isLoaded()} itself before touching this class.
 */
public final class SettlemyntsPlotLocationBridge {

    private SettlemyntsPlotLocationBridge() {
    }

    public static boolean isLoaded() {
        return ModList.get().isLoaded("settlemynts");
    }

    public record PlotLocation(BlockPos pos, ResourceKey<Level> dimension, String settlementName) {
    }

    public static Optional<PlotLocation> resolve(ServerLevel level, UUID plotId) {
        return Settlemynts.findPlotLocation(level, plotId)
                .map(loc -> new PlotLocation(loc.pos(), loc.dimension(), loc.settlementName()));
    }
}
