package com.github.cerealklla.myssions.quest;

import java.util.Map;
import java.util.UUID;

import com.github.cerealklla.myssions.registration.ModAttachments;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * Periodic scan (every {@link #SCAN_INTERVAL_TICKS}, same cadence every other "check every known
 * X" scan in this suite uses) failing any online player's in-progress quest whose own {@link
 * QuestTimeLimit} has passed. Only ever scans online players' already-loaded attachment data --
 * no world scan, no offline-player handling (an offline player's deadline is simply checked the
 * next time they log in and this ticker next runs, a deliberately accepted small slop rather than
 * the cost of waking offline player data just to fail a quest a few seconds early).
 */
public final class QuestDeadlineTicker {

    private static final int SCAN_INTERVAL_TICKS = 100; // 5 seconds.
    // Staggered 2026-10-12 -- real report: every mob near the player stutter-moving in lockstep.
    // Root cause: this and seven other full-world-scanning tickers across Settlemynts/Lyfe all
    // independently picked "every 100 ticks" with no offset, so every scan landed on the exact same
    // tick and their costs compounded into one periodic spike every 5 seconds -- confirmed live via
    // Settlemynts' own [LagDiagnostics] slow-tick logger, firing almost exactly every 5.00s. See
    // Settlemynts' FarmerSpawnTicker for the full writeup; this offset just spreads this mod's own
    // share of that same cost onto a different tick within the window.
    private static final int TICK_OFFSET = 87;

    @SubscribeEvent
    public void onServerTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        if ((server.getTickCount() + TICK_OFFSET) % SCAN_INTERVAL_TICKS != 0) {
            return;
        }
        long nowGameTime = server.overworld().getGameTime();
        long nowEpochMillis = System.currentTimeMillis();

        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            Map<UUID, QuestInstance> quests = player.getData(ModAttachments.QUEST_DATA).quests();
            for (QuestInstance instance : quests.values()) {
                if (instance.status() != QuestStatus.IN_PROGRESS) {
                    continue;
                }
                QuestRegistry.get(instance.definitionId()).ifPresent(definition -> {
                    QuestTimeLimit limit = definition.timeLimit();
                    if (!limit.hasLimit() || !isExpired(limit, instance, nowGameTime, nowEpochMillis)) {
                        return;
                    }
                    QuestEngine.failQuest(player, instance.instanceId());
                });
            }
        }
    }

    private boolean isExpired(QuestTimeLimit limit, QuestInstance instance, long nowGameTime, long nowEpochMillis) {
        // GAME_TICKS_FROM_ASSIGNMENT/NEXT_GAME_DAY_BOUNDARY share QuestTimeLimit#deadlineGameTime with
        // client.ActiveQuestOverlay's own countdown bar (added 2026-10-13) -- one formula, so the two
        // can never silently drift apart.
        //
        // GAME_TICKS_FROM_ASSIGNMENT specifically now prefers a real assignedAtRealEpochMillis when
        // the instance has one (added 2026-10-13, real live report: the tick clock silently pauses
        // for every real minute the server process itself is down, e.g. a deploy restart) -- falls
        // back to the tick-based check for any instance assigned before this field existed.
        if (limit.kind() == QuestTimeLimit.Kind.GAME_TICKS_FROM_ASSIGNMENT && instance.assignedAtRealEpochMillis().isPresent()) {
            return nowEpochMillis >= limit.deadlineRealEpochMillis(instance.assignedAtRealEpochMillis().get());
        }
        return switch (limit.kind()) {
            case NONE -> false;
            case GAME_TICKS_FROM_ASSIGNMENT, NEXT_GAME_DAY_BOUNDARY -> nowGameTime >= limit.deadlineGameTime(instance.assignedAtGameTime());
            case REAL_WORLD_DATE -> nowEpochMillis >= limit.realWorldEpochMillis();
        };
    }
}
