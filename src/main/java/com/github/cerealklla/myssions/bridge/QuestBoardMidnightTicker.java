package com.github.cerealklla.myssions.bridge;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.github.cerealklla.myssions.quest.IssuerQuestBoard;
import com.github.cerealklla.myssions.quest.IssuerQuestPools;
import com.github.cerealklla.myssions.quest.QuestEligibility;
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
 * <p><b>Immediate first roll, 2026-11-09</b> -- explicit request: "spawn the 8 starter towns so
 * there is something for the player to interact with when talking to the NPCs immediately." Without
 * this, a brand-new world's quest boards stay empty until the first real in-game midnight actually
 * passes, which can be many real minutes away depending on spawn time -- not "immediately" by any
 * reasonable reading. {@link #initialRollDone} tracks this separately from the normal day-boundary
 * gate: every tick until at least one real plot is registered anywhere (a plain, cheap index
 * lookup), this keeps waiting rather than burning its one shot rolling against nothing -- the 8
 * starter settlements' own plots aren't necessarily registered the instant the server boots
 * (Settlemynts' own natural-village plot generation runs on its own separate ticker, independent of
 * this one). The moment at least one plot exists, this rolls once immediately and never repeats this
 * early path again, falling back to the normal once-per-real-day behavior from then on.
 */
public final class QuestBoardMidnightTicker {

    private static final long TICKS_PER_DAY = 24000L;
    private static final double GENERATE_CHANCE = 0.5;

    private long lastKnownDay = -1;
    private boolean initialRollDone = false;

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

        boolean initialRoll = false;
        if (!initialRollDone) {
            if (PlotConfigSignIndex.get(server).all().isEmpty()) {
                return; // Nothing registered anywhere yet -- keep waiting rather than rolling against nothing.
            }
            initialRollDone = true;
            initialRoll = true;
        }

        if (!dayBoundaryRoll && !initialRoll) {
            return;
        }

        IssuerQuestBoard board = IssuerQuestBoard.get(server);
        for (Map.Entry<UUID, GlobalPos> entry : PlotConfigSignIndex.get(server).all().entrySet()) {
            UUID plotId = entry.getKey();
            if (board.get(plotId).size() >= IssuerQuestBoard.MAX_BOARD_SLOTS) {
                continue; // Already at the cap -- "not roll any more when at that limit."
            }
            GlobalPos pos = entry.getValue();
            ServerLevel level = server.getLevel(pos.dimension());
            if (level == null) {
                continue;
            }
            Settlemynts.findPlotRecord(level, plotId).ifPresent(plot -> {
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
            });
        }
    }
}
