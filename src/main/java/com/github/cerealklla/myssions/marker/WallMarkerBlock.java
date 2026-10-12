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
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Wall-mounted sibling of {@link GroundMarkerBlock} (design request 2026-10-12: "one that can be
 * attached to a wall surface similar to a torch") -- same incorporeal/support-dependent/never-drops
 * mechanic, anchored to the solid block behind {@link #FACING} instead of the one below. See {@link
 * GroundMarkerBlock}'s own doc for the full shared design (trigger wiring lives in {@code
 * marker.MarkerBreakListener}/{@code marker.MarkerEvents}, not in either block class).
 */
public class WallMarkerBlock extends Block implements EntityBlock {

    public static final EnumProperty<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;

    private static final VoxelShape SHAPE = Shapes.box(0.0, 0.0, 0.0, 1.0, 1.0, 0.0625);

    public WallMarkerBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(FACING);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return Shapes.empty();
    }

    @Override
    protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        Direction facing = state.getValue(FACING);
        BlockPos supportPos = pos.relative(facing.getOpposite());
        return level.getBlockState(supportPos).isFaceSturdy(level, supportPos, facing);
    }

    @Override
    protected BlockState updateShape(BlockState state, LevelReader level, ScheduledTickAccess tickAccess,
                                      BlockPos pos, Direction direction, BlockPos neighborPos, BlockState neighborState, net.minecraft.util.RandomSource random) {
        if (direction == state.getValue(FACING).getOpposite() && !canSurvive(state, level, pos)) {
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
