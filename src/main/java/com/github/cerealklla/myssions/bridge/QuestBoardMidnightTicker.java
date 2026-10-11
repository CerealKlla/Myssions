package com.github.cerealklla.myssions.bridge;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.github.cerealklla.myssions.quest.IssuerQuestBoard;
import com.github.cerealklla.myssions.quest.IssuerQuestPools;
import com.github.cerealklla.settlemynts.api.Settlemynts;
import com.github.cerealklla.settlemynts.plotsign.PlotConfigSignIndex;

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
 * entry for any plot with a registered quest pool and an empty board slot.
 *
 * <p>Day-boundary detection mirrors Settlemynts' own {@code zone.ShopMidnightRestockTicker} exactly
 * (a local {@code lastKnownDay} compared against {@code TICKS_PER_DAY}), rather than depending on
 * that class directly -- it's private to Settlemynts, and this only needs the same cheap two-line
 * calculation, not a shared utility.
 */
public final class QuestBoardMidnightTicker {

    private static final long TICKS_PER_DAY = 24000L;
    private static final double GENERATE_CHANCE = 0.5;

    private long lastKnownDay = -1;

    @SubscribeEvent
    public void onServerTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        long currentDay = Math.floorDiv(server.overworld().getOverworldClockTime(), TICKS_PER_DAY);
        if (lastKnownDay < 0) {
            lastKnownDay = currentDay; // First tick since this listener was created -- establish the baseline only.
            return;
        }
        if (currentDay <= lastKnownDay) {
            return;
        }
        lastKnownDay = currentDay;

        IssuerQuestBoard board = IssuerQuestBoard.get(server);
        for (Map.Entry<UUID, GlobalPos> entry : PlotConfigSignIndex.get(server).all().entrySet()) {
            UUID plotId = entry.getKey();
            if (board.get(plotId).isPresent()) {
                continue; // Already has an unclaimed quest sitting on the board -- never overwritten.
            }
            GlobalPos pos = entry.getValue();
            ServerLevel level = server.getLevel(pos.dimension());
            if (level == null) {
                continue;
            }
            Settlemynts.findPlotRecord(level, plotId).ifPresent(plot -> {
                List<Identifier> pool = IssuerQuestPools.poolFor(plot.zoneTypeId().toString());
                if (pool.isEmpty() || level.getRandom().nextDouble() >= GENERATE_CHANCE) {
                    return;
                }
                Identifier chosen = pool.get(level.getRandom().nextInt(pool.size()));
                board.set(plotId, chosen);
            });
        }
    }
}
