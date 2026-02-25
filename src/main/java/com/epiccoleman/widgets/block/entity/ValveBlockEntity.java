package com.epiccoleman.widgets.block.entity;

import com.epiccoleman.widgets.registry.ModBlockEntities;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidConstants;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidStorage;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidVariant;
import net.fabricmc.fabric.api.transfer.v1.storage.SlottedStorage;
import net.fabricmc.fabric.api.transfer.v1.storage.Storage;
import net.fabricmc.fabric.api.transfer.v1.storage.StorageUtil;
import net.fabricmc.fabric.api.transfer.v1.storage.StorageView;
import net.fabricmc.fabric.api.transfer.v1.storage.base.SingleSlotStorage;
import net.fabricmc.fabric.api.transfer.v1.storage.base.SingleVariantStorage;
import net.fabricmc.fabric.api.transfer.v1.transaction.TransactionContext;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.DirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Iterator;
import java.util.List;

@SuppressWarnings("UnstableApiUsage")
public class ValveBlockEntity extends BlockEntity {

    private final SingleVariantStorage<FluidVariant> tank = new SingleVariantStorage<>() {
        @Override
        protected FluidVariant getBlankVariant() {
            return FluidVariant.blank();
        }

        @Override
        protected long getCapacity(FluidVariant variant) {
            return FluidConstants.BUCKET;
        }

        @Override
        protected void onFinalCommit() {
            setChanged();
        }
    };

    public ValveBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.VALVE, pos, state);
    }

    public Storage<FluidVariant> getStorageForFace(Direction face) {
        Direction inputDir = getBlockState().getValue(DirectionalBlock.FACING);

        if (face == inputDir) {
            return new InsertOnlyStorage();
        }

        if (face == inputDir.getOpposite()) {
            return new ExtractOnlyStorage(tank);
        }

        return null;
    }

    private boolean isPowered() {
        return level != null && level.hasNeighborSignal(worldPosition);
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, ValveBlockEntity be) {
        if (be.tank.getAmount() <= 0) return;
        if (be.isPowered()) return;

        Direction outputDir = state.getValue(DirectionalBlock.FACING).getOpposite();
        Storage<FluidVariant> target = FluidStorage.SIDED.find(
                level, pos.relative(outputDir), outputDir.getOpposite()
        );
        if (target != null) {
            StorageUtil.move(be.tank, target, v -> true, Long.MAX_VALUE, null);
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (tank.getAmount() > 0) {
            tag.putString("FluidId", BuiltInRegistries.FLUID.getKey(tank.getResource().getFluid()).toString());
            tag.putLong("FluidAmount", tank.getAmount());
        }
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.contains("FluidAmount")) {
            Fluid fluid = BuiltInRegistries.FLUID.get(ResourceLocation.parse(tag.getString("FluidId")));
            tank.variant = FluidVariant.of(fluid);
            tank.amount = tag.getLong("FluidAmount");
        } else {
            tank.variant = FluidVariant.blank();
            tank.amount = 0;
        }
    }

    private class InsertOnlyStorage implements SlottedStorage<FluidVariant> {
        @Override
        public int getSlotCount() {
            return 1;
        }

        @Override
        public SingleSlotStorage<FluidVariant> getSlot(int index) {
            return tank;
        }

        @Override
        public long insert(FluidVariant resource, long maxAmount, TransactionContext transaction) {
            if (isPowered()) return 0;
            return tank.insert(resource, maxAmount, transaction);
        }

        @Override
        public long extract(FluidVariant resource, long maxAmount, TransactionContext transaction) {
            return 0;
        }

        @Override
        public Iterator<StorageView<FluidVariant>> iterator() {
            return List.<StorageView<FluidVariant>>of(tank).iterator();
        }
    }

    private static class ExtractOnlyStorage implements SlottedStorage<FluidVariant> {
        private final SingleVariantStorage<FluidVariant> tank;

        ExtractOnlyStorage(SingleVariantStorage<FluidVariant> tank) {
            this.tank = tank;
        }

        @Override
        public int getSlotCount() {
            return 1;
        }

        @Override
        public SingleSlotStorage<FluidVariant> getSlot(int index) {
            return tank;
        }

        @Override
        public long insert(FluidVariant resource, long maxAmount, TransactionContext transaction) {
            return 0;
        }

        @Override
        public long extract(FluidVariant resource, long maxAmount, TransactionContext transaction) {
            return tank.extract(resource, maxAmount, transaction);
        }

        @Override
        public Iterator<StorageView<FluidVariant>> iterator() {
            return List.<StorageView<FluidVariant>>of(tank).iterator();
        }
    }
}
