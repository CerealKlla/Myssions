package com.github.cerealklla.myssions.marker;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/**
 * Open, trust-based listener registry for "a player just broke the block supporting an incorporeal
 * marker" (design request 2026-10-12: a generic, reusable hook -- "nothing will leverage it yet").
 * Same lightweight shape every other cross-mod hook in this suite uses (e.g. Yconomics' {@code
 * bills.BillEvents}) rather than a full NeoForge custom-Event -- a listener here never needs to
 * reference anything beyond plain, common types.
 */
public final class MarkerEvents {

    private static final List<TriggerListener> LISTENERS = new CopyOnWriteArrayList<>();

    private MarkerEvents() {
    }

    public interface TriggerListener {
        /**
         * @param markerPos the marker's own position (not the supporting block the player actually broke)
         * @param tag whatever the placing code attached via {@code api.Myssions#placeGroundMarker}/{@code #placeWallMarker} -- entirely opaque to this mod
         */
        void onMarkerTriggered(ServerLevel level, BlockPos markerPos, ServerPlayer player, Optional<String> tag);
    }

    public static void register(TriggerListener listener) {
        LISTENERS.add(listener);
    }

    static void fire(ServerLevel level, BlockPos markerPos, ServerPlayer player, Optional<String> tag) {
        for (TriggerListener listener : LISTENERS) {
            listener.onMarkerTriggered(level, markerPos, player, tag);
        }
    }
}
