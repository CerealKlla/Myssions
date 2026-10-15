package com.github.cerealklla.myssions.bridge;

import java.util.UUID;

import com.github.cerealklla.myssions.quest.EntranceQuestLink;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/** Periodically asks TinyWyrlds (via {@link TinyWyrldsEntranceBridge#pollForGuardian}) whether any
 *  still-guardian-less tracked entrance has produced a real dungeon yet -- see that bridge's own
 *  doc for why this replaced an earlier event-based design once Entrance entry became a walk-in
 *  proximity trigger instead of a right-click. Every 20 ticks (1 second) is plenty responsive for
 *  "did the player walk in yet" without scanning on every single tick. */
public final class TinyWyrldsEntranceGuardianTicker {

    private static final int INTERVAL_TICKS = 20;

    @SubscribeEvent
    public void onServerTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        if (server.getTickCount() % INTERVAL_TICKS != 0) {
            return;
        }
        ServerLevel overworld = server.overworld();
        for (UUID instanceId : EntranceQuestLink.get(server).idsWithoutGuardian()) {
            TinyWyrldsEntranceBridge.pollForGuardian(overworld, instanceId);
        }
    }
}
