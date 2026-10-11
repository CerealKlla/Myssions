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
    private static final long TICKS_PER_DAY = 24000L;

    @SubscribeEvent
    public void onServerTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        if (server.getTickCount() % SCAN_INTERVAL_TICKS != 0) {
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
        return switch (limit.kind()) {
            case NONE -> false;
            case GAME_TICKS_FROM_ASSIGNMENT -> nowGameTime >= instance.assignedAtGameTime() + limit.ticksFromAssignment();
            case NEXT_GAME_DAY_BOUNDARY -> {
                long nextBoundary = ((instance.assignedAtGameTime() / TICKS_PER_DAY) + 1) * TICKS_PER_DAY;
                yield nowGameTime >= nextBoundary;
            }
            case REAL_WORLD_DATE -> nowEpochMillis >= limit.realWorldEpochMillis();
        };
    }
}
