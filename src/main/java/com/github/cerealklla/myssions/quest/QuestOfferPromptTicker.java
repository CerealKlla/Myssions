package com.github.cerealklla.myssions.quest;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import com.github.cerealklla.myssions.api.Myssions;
import com.github.cerealklla.myssions.bridge.AudybleConversationBridge;
import com.github.cerealklla.myssions.net.QuestOfferPromptPayload;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Periodic scan (request #3, 2026-11-06) showing/hiding the "Press &lt;key&gt; to review quest
 * offer" HUD prompt -- true exactly while a player has at least one available (offered, not yet
 * accepted/declined) quest AND is actively conversing with an NPC (via Audyble, an optional soft
 * dependency -- see {@link AudybleConversationBridge}'s own doc; the prompt simply never fires on
 * an Audyble-less server). Same stagger-offset shape as {@link QuestDeadlineTicker}, sent only on
 * change per player (never every scan) to match {@code PlotShopProximityTicker}'s own convention.
 */
public final class QuestOfferPromptTicker {

    private static final int SCAN_INTERVAL_TICKS = 10; // 0.5 seconds -- conversational, wants to feel responsive.
    private static final int TICK_OFFSET = 43;

    private final Map<UUID, Boolean> lastSent = new HashMap<>();

    @SubscribeEvent
    public void onServerTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        if ((server.getTickCount() + TICK_OFFSET) % SCAN_INTERVAL_TICKS != 0) {
            return;
        }
        if (!AudybleConversationBridge.isLoaded()) {
            return;
        }

        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            UUID playerId = player.getUUID();
            boolean shouldShow = Myssions.hasAvailableQuests(player)
                    && AudybleConversationBridge.isInConversation(playerId);
            Boolean previous = lastSent.get(playerId);
            if (previous != null && previous == shouldShow) {
                continue;
            }
            lastSent.put(playerId, shouldShow);
            PacketDistributor.sendToPlayer(player, new QuestOfferPromptPayload(shouldShow));
        }
    }
}
