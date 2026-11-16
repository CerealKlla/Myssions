package com.github.cerealklla.myssions.bridge;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.github.cerealklla.myssions.quest.InitialQuestRollTracker;
import com.github.cerealklla.myssions.quest.IssuerQuestBoard;
import com.github.cerealklla.myssions.quest.IssuerQuestPools;
import com.github.cerealklla.myssions.quest.QuestEligibility;
import com.github.cerealklla.settlemynts.api.Settlemynts;
import com.github.cerealklla.settlemynts.plotsign.PlotConfigSignIndex;
import com.github.cerealklla.settlemynts.zone.NaturalSettlementPlotStore;
import com.github.cerealklla.settlemynts.zone.PlotRecord;

import net.minecraft.core.GlobalPos;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * "At midnight, NPCs who do not currently have a quest available will decide with a 50% chance to
 * generate a quest" -- explicit user spec, 2026-10-10. Optional soft dependency on Settlemynts
 * (only constructed/registered behind {@code ModList.get().isLoaded("settlemynts")} in {@code
 * MyssionsMod}'s constructor, same isolation convention as every other Settlemynts bridge here) --
 * enumerates every known plot via Settlemynts' own {@code plotsign.PlotConfigSignIndex} (the same
 * save-wide index {@code zone.ShopMidnightRestockTicker} already uses for the identical "every
 * plot, once a day" shape), resolves each one's Zone Type, and rolls a fresh {@link IssuerQuestBoard}
 * entry for any plot with a registered quest pool and at least one free board slot (widened
 * 2026-10-11 from a single slot per plot to {@link IssuerQuestBoard#MAX_BOARD_SLOTS} -- "NPCs should
 * be able to hold 5 available quests at one time, and not roll any more when at that limit").
 * Candidates are first filtered through {@link QuestEligibility} (e.g. the buy-item quest can't be
 * offered at all if the settlement has nothing resolvable to sell); if the chosen quest has its own
 * registered eligibility predicate at all, one more free slot is immediately filled too, per
 * explicit user spec ("if an NPC generates a local quest [like buying a specific item], it should
 * roll an additional quest too").
 *
 * <p>Day-boundary detection mirrors Settlemynts' own {@code zone.ShopMidnightRestockTicker} exactly
 * (a local {@code lastKnownDay} compared against {@code TICKS_PER_DAY}), rather than depending on
 * that class directly -- it's private to Settlemynts, and this only needs the same cheap two-line
 * calculation, not a shared utility.
 *
 * <p><b>Immediate first roll, per plot, 2026-11-09, generalized 2026-11-15</b> -- explicit request:
 * "force all the town NPCs to do their nightly quest roll the first time they get generated, so
 * people fresh from the tutorial as the very first visitors have a chance to pick up quests
 * immediately." Originally (2026-11-09) this was a single global {@code initialRollDone} boolean --
 * fine for "the 8 starter towns have quests the moment the world boots," but it only ever fires
 * once, so any settlement founded afterward got no immediate roll of its own, just whatever's left
 * of the current real day. Now tracked per plot via {@link InitialQuestRollTracker}: every plot not
 * yet marked rolled gets an immediate roll this very tick, regardless of the day-boundary gate below
 * -- a brand-new plot (starter town or player-founded, it's the same index either way) never has to
 * wait on a real in-game midnight for its first offer.
 *
 * <p><b>Real bug fixed the same day: natural (NPC) villages never got ANY roll, ever</b> -- a live
 * report, "I've been in this NPC town for 2 midnight cycles and no new quests have generated."
 * Confirmed directly against this world's own persisted save data: both {@link IssuerQuestBoard}
 * and {@link InitialQuestRollTracker} were completely empty, for every plot, anywhere -- not a
 * regression from the per-plot rework above, a pre-existing gap since this class was first built
 * (2026-10-10). The only plots this class ever scanned were {@link PlotConfigSignIndex}'s, which is
 * exclusively populated by player-founded settlements (a Plot Config Sign is placed at Finalize) --
 * a naturally-spawned village has no Town Hall Core, no Finalize step, and no Plot Config Sign at
 * all, so its plots live entirely in {@link NaturalSettlementPlotStore} instead (see that class's
 * own doc). Since a fresh world's starter towns, and most of what a brand-new player actually finds
 * near spawn, are natural villages rather than player-founded settlements, this made the "immediate
 * first roll" feature above functionally inert for the exact audience it was built for. Now scans
 * both stores; natural villages have no real Town Hall Core/dimension of their own to resolve a
 * level from, so {@link MinecraftServer#overworld()} is used directly -- vanilla village generation
 * (and this suite's own natural-settlement discovery) never happens outside the Overworld anyway.
 */
public final class QuestBoardMidnightTicker {

    private static final long TICKS_PER_DAY = 24000L;
    private static final double GENERATE_CHANCE = 0.5;

    private long lastKnownDay = -1;

    @SubscribeEvent
    public void onServerTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        long currentDay = Math.floorDiv(server.overworld().getOverworldClockTime(), TICKS_PER_DAY);
        boolean dayBoundaryRoll = false;
        if (lastKnownDay < 0) {
            lastKnownDay = currentDay; // First tick since this listener was created -- establish the baseline only.
        } else if (currentDay > lastKnownDay) {
            lastKnownDay = currentDay;
            dayBoundaryRoll = true;
        }

        IssuerQuestBoard board = IssuerQuestBoard.get(server);
        InitialQuestRollTracker rollTracker = InitialQuestRollTracker.get(server);

        for (Map.Entry<UUID, GlobalPos> entry : PlotConfigSignIndex.get(server).all().entrySet()) {
            UUID plotId = entry.getKey();
            if (!shouldRollNow(rollTracker, plotId, dayBoundaryRoll)) {
                continue;
            }
            GlobalPos pos = entry.getValue();
            ServerLevel level = server.getLevel(pos.dimension());
            if (level == null) {
                continue;
            }
            Settlemynts.findPlotRecord(level, plotId).ifPresent(plot -> rollForPlot(level, board, plot));
        }

        // Natural (NPC) villages' plots -- a completely separate store, never covered by the loop
        // above (see this class's own doc for the real bug this closes).
        ServerLevel overworld = server.overworld();
        for (List<PlotRecord> plots : NaturalSettlementPlotStore.get(server).all().values()) {
            for (PlotRecord plot : plots) {
                if (!shouldRollNow(rollTracker, plot.plotId(), dayBoundaryRoll)) {
                    continue;
                }
                rollForPlot(overworld, board, plot);
            }
        }
    }

    /** Whether {@code plotId} should be (re)rolled on this tick -- either it's never had its one-time immediate first roll yet, or a real day boundary just passed. Marks the first-roll flag as a side effect, same as before. */
    private static boolean shouldRollNow(InitialQuestRollTracker rollTracker, UUID plotId, boolean dayBoundaryRoll) {
        boolean isPlotsFirstRoll = !rollTracker.hasRolled(plotId);
        if (!dayBoundaryRoll && !isPlotsFirstRoll) {
            return false;
        }
        if (isPlotsFirstRoll) {
            rollTracker.markRolled(plotId);
        }
        return true;
    }

    private static void rollForPlot(ServerLevel level, IssuerQuestBoard board, PlotRecord plot) {
        UUID plotId = plot.plotId();
        if (board.get(plotId).size() >= IssuerQuestBoard.MAX_BOARD_SLOTS) {
            return; // Already at the cap -- "not roll any more when at that limit."
        }
        List<Identifier> pool = IssuerQuestPools.poolFor(plot.zoneTypeId().toString());
        if (pool.isEmpty()) {
            return;
        }
        List<Identifier> eligible = new ArrayList<>();
        for (Identifier candidate : pool) {
            if (QuestEligibility.isEligible(level, plotId, candidate)) {
                eligible.add(candidate);
            }
        }
        if (eligible.isEmpty() || level.getRandom().nextDouble() >= GENERATE_CHANCE) {
            return;
        }
        Identifier chosen = eligible.get(level.getRandom().nextInt(eligible.size()));
        board.add(plotId, chosen);

        // "If an NPC generates a local quest [with a resolved vendor/item requirement], it
        // should roll an additional quest too" -- explicit user spec, 2026-10-11. Today only
        // the buy-item quest type has a registered eligibility predicate at all, so this is
        // effectively "a buy-item quest grants one free extra roll," generalized by checking
        // for any registered predicate rather than a hardcoded quest id.
        if (QuestEligibility.hasPredicate(chosen) && board.get(plotId).size() < IssuerQuestBoard.MAX_BOARD_SLOTS) {
            List<Identifier> stillEligible = new ArrayList<>();
            for (Identifier candidate : pool) {
                if (!candidate.equals(chosen) && QuestEligibility.isEligible(level, plotId, candidate)) {
                    stillEligible.add(candidate);
                }
            }
            if (!stillEligible.isEmpty()) {
                board.add(plotId, stillEligible.get(level.getRandom().nextInt(stillEligible.size())));
            }
        }
    }
}
