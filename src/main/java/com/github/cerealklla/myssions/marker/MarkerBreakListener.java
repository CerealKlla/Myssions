package com.github.cerealklla.myssions.marker;

import java.util.Optional;

import com.github.cerealklla.myssions.registration.ModBlocks;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.level.block.BreakBlockEvent;

/**
 * Fires {@link MarkerEvents} the instant a player breaks a marker's support -- a ground marker's
 * support is directly below it, a wall marker's is behind the face it's mounted on. Fires once,
 * right before the break actually happens (both the marker and its support still exist at this
 * point); the marker's own removal afterward is ordinary vanilla "lost my support" mechanics (see
 * {@link GroundMarkerBlock}/{@link WallMarkerBlock}'s own {@code updateShape}), not handled here.
 *
 * <p>{@link BreakBlockEvent} fires on both physical sides (its own doc says so explicitly) -- only
 * acts when the level is genuinely a {@link ServerLevel} and the breaking entity a real {@link
 * ServerPlayer}, same guard every other server-authoritative listener in this suite uses.
 */
public final class MarkerBreakListener {

    @SubscribeEvent
    public void onBreak(BreakBlockEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level) || !(event.getPlayer() instanceof ServerPlayer player)) {
            return;
        }
        BlockPos brokenPos = event.getPos();

        BlockPos abovePos = brokenPos.above();
        BlockState aboveState = level.getBlockState(abovePos);
        if (aboveState.is(ModBlocks.GROUND_MARKER.get())) {
            fireFor(level, abovePos, player);
        }

        for (Direction direction : Direction.Plane.HORIZONTAL) {
            BlockPos neighborPos = brokenPos.relative(direction);
            BlockState neighborState = level.getBlockState(neighborPos);
            if (neighborState.is(ModBlocks.WALL_MARKER.get()) && neighborState.getValue(WallMarkerBlock.FACING) == direction) {
                fireFor(level, neighborPos, player);
            }
        }
    }

    private static void fireFor(ServerLevel level, BlockPos markerPos, ServerPlayer player) {
        Optional<String> tag = level.getBlockEntity(markerPos) instanceof IncorporealMarkerBlockEntity entity
                ? entity.tag() : Optional.empty();
        MarkerEvents.fire(level, markerPos, player, tag);
    }
}
