package com.github.cerealklla.myssions.marker;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * "X marks the spot" ground marker (design request 2026-10-12) -- sits on top of another block the
 * same way a vanilla snow layer or flower does (requires a solid block below, destroyed the instant
 * that support is gone), but genuinely incorporeal: zero collision, a player walks straight through
 * it rather than standing on it. Never player-placed or obtainable -- no default {@code BlockItem}
 * exists for this block at all; the only way one ever appears in the world is {@code api.Myssions
 * #placeGroundMarker}, called by whatever code (a future quest/loot feature) wants to mark a spot.
 *
 * <p><b>The actual trigger</b> doesn't live here -- a block has no way to add behavior to an
 * *arbitrary* block placed underneath it. Instead, {@code marker.MarkerBreakListener} (a global
 * {@code BlockEvent.BreakEvent} subscriber) checks, on every block a player breaks, whether a marker
 * sits directly above (this block) or beside (see {@link WallMarkerBlock}) the broken position, and
 * fires {@code marker.MarkerEvents} before the break completes. This block's own destruction is then
 * just vanilla's ordinary "my support disappeared" mechanic (see {@link #updateShape}) -- same
 * shape every snow-layer-like block already uses, never a drop (no loot table at all).
 */
public class GroundMarkerBlock extends Block implements EntityBlock {

    private static final VoxelShape SHAPE = Shapes.box(0.0, 0.0, 0.0, 1.0, 0.0625, 1.0);

    public GroundMarkerBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    /** Genuinely incorporeal -- never collides, a player (or anything else) walks straight through. */
    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return Shapes.empty();
    }

    @Override
    protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        return level.getBlockState(pos.below()).isFaceSturdy(level, pos.below(), Direction.UP);
    }

    @Override
    protected BlockState updateShape(BlockState state, LevelReader level, ScheduledTickAccess tickAccess,
                                      BlockPos pos, Direction direction, BlockPos neighborPos, BlockState neighborState, net.minecraft.util.RandomSource random) {
        if (direction == Direction.DOWN && !canSurvive(state, level, pos)) {
            return Blocks.AIR.defaultBlockState();
        }
        return super.updateShape(state, level, tickAccess, pos, direction, neighborPos, neighborState, random);
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new IncorporealMarkerBlockEntity(pos, state);
    }
}
