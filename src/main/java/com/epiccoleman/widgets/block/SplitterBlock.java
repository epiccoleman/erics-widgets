package com.epiccoleman.widgets.block;

import com.epiccoleman.widgets.block.entity.SplitterBlockEntity;
import com.epiccoleman.widgets.registry.ModBlockEntities;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.Containers;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.DirectionalBlock;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.DirectionProperty;

import java.util.ArrayList;
import java.util.List;

public class SplitterBlock extends DirectionalBlock implements EntityBlock {

    public static final DirectionProperty ORIENTATION = DirectionProperty.create("orientation");

    private final int outputCount;
    private final MapCodec<SplitterBlock> codec;

    @Override
    protected MapCodec<? extends DirectionalBlock> codec() {
        return codec;
    }

    public SplitterBlock(Properties properties, int outputCount) {
        super(properties);
        this.outputCount = outputCount;
        this.codec = simpleCodec(p -> new SplitterBlock(p, outputCount));
        registerDefaultState(stateDefinition.any()
                .setValue(FACING, Direction.UP)
                .setValue(ORIENTATION, Direction.NORTH));
    }

    public int getOutputCount() {
        return outputCount;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, ORIENTATION);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        Direction facing = ctx.getClickedFace();
        Direction orientation = Direction.NORTH; // default
        for (Direction dir : ctx.getNearestLookingDirections()) {
            if (dir.getAxis() != facing.getAxis()) {
                orientation = dir;
                break;
            }
        }
        return defaultBlockState()
                .setValue(FACING, facing)
                .setValue(ORIENTATION, orientation);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new SplitterBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide()) return null;
        if (type != ModBlockEntities.SPLITTER) return null;
        @SuppressWarnings("unchecked")
        BlockEntityTicker<T> ticker = (BlockEntityTicker<T>) (BlockEntityTicker<SplitterBlockEntity>) SplitterBlockEntity::serverTick;
        return ticker;
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!state.is(newState.getBlock())) {
            BlockEntity be = level.getBlockEntity(pos);
            if (be instanceof SplitterBlockEntity splitterBe) {
                splitterBe.dropItems(level, pos);
            }
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }

    /**
     * Returns the output directions for a splitter given its input direction, orientation, and output count.
     */
    public static List<Direction> getOutputDirections(Direction inputDir, Direction orientation, int outputCount) {
        List<Direction> all = new ArrayList<>();
        for (Direction d : Direction.values()) {
            if (d != inputDir && d != inputDir.getOpposite()) {
                all.add(d);
            }
        }
        // all now has the 4 perpendicular directions

        if (outputCount == 4) {
            return all;
        }

        if (outputCount == 3) {
            // Exclude opposite of orientation
            all.remove(orientation.getOpposite());
            return all;
        }

        if (outputCount == 2) {
            // Exclude both orientation and its opposite
            all.remove(orientation);
            all.remove(orientation.getOpposite());
            return all;
        }

        return all;
    }
}
