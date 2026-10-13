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

    /**
     * Applies the plot's real Tier raise once the Plot Upgrade quest's own delivery has collected the
     * full shortfall -- added 2026-10-13, see {@code quest.QuestCompletionHooks}' own doc for why this
     * is called from a completion hook rather than hardcoded into the quest engine, and {@code
     * api.Settlemynts#applyPlotUpgradeFromQuest}'s own doc for the real accounting (the plot's
     * Construction Box still gets drained of whatever on-hand stock already counted toward the
     * shortfall figure this quest used). {@code targetTier} is the specific Tier this quest instance's
     * own shortfall was sized for at acceptance time (see {@code quest.PlotUpgradeQuestTargets}' own
     * doc) -- guards against two instances for the same plot (two players, or a stale duplicate) both
     * applying a real upgrade; a mismatch (the plot's already moved on) safely no-ops. False if the
     * plot can't be resolved anymore, is already at its real Tier ceiling, or has already moved past
     * this instance's own target.
     */
    public static boolean applyUpgrade(ServerLevel level, UUID plotId, int targetTier) {
        return Settlemynts.applyPlotUpgradeFromQuest(level, plotId, targetTier);
    }
}
