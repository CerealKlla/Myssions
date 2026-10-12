package com.github.cerealklla.myssions.bridge;

import java.util.Optional;
import java.util.UUID;

import com.github.cerealklla.settlemynts.api.Settlemynts;

import net.minecraft.server.level.ServerLevel;
import net.neoforged.fml.ModList;

/**
 * Resolves a plot's real current "help upgrade this plot" shortfall -- added 2026-10-12 for the new
 * Plot Upgrade quest type (every profession's NPC can offer "bring me the missing components to
 * raise this plot's Tier"). Thin pass-through to Settlemynts' own {@code api.Settlemynts
 * #getPlotUpgradeShortfall}; optional soft dependency, same isolation convention as every other
 * bridge in this mod -- every call site must check {@link #isLoaded()} itself before touching this
 * class.
 */
public final class SettlemyntsPlotUpgradeBridge {

    private SettlemyntsPlotUpgradeBridge() {
    }

    public static boolean isLoaded() {
        return ModList.get().isLoaded("settlemynts");
    }

    public record Shortfall(int nextTier, int woodNeeded, int stoneNeeded) {
        public boolean anyShortfall() {
            return woodNeeded > 0 || stoneNeeded > 0;
        }
    }

    public static Optional<Shortfall> resolveShortfall(ServerLevel level, UUID plotId) {
        return Settlemynts.getPlotUpgradeShortfall(level, plotId)
                .map(s -> new Shortfall(s.nextTier(), s.woodNeeded(), s.stoneNeeded()));
    }
}
