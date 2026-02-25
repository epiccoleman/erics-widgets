package com.epiccoleman.widgets.block.entity;

import com.epiccoleman.widgets.block.SplitterBlock;
import com.epiccoleman.widgets.registry.ModBlockEntities;
import net.fabricmc.fabric.api.transfer.v1.item.ItemStorage;
import net.fabricmc.fabric.api.transfer.v1.item.ItemVariant;
import net.fabricmc.fabric.api.transfer.v1.storage.Storage;
import net.fabricmc.fabric.api.transfer.v1.storage.StorageUtil;
import net.fabricmc.fabric.api.transfer.v1.storage.SlottedStorage;
import net.fabricmc.fabric.api.transfer.v1.storage.StorageView;
import net.fabricmc.fabric.api.transfer.v1.storage.base.SingleSlotStorage;
import net.fabricmc.fabric.api.transfer.v1.storage.base.SingleVariantStorage;
import net.fabricmc.fabric.api.transfer.v1.transaction.TransactionContext;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.DirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Collections;
import java.util.Iterator;
import java.util.List;

@SuppressWarnings("UnstableApiUsage")
public class SplitterBlockEntity extends BlockEntity {

    private final SingleVariantStorage<ItemVariant>[] outputSlots;
    private int roundRobinIndex = 0;

    @SuppressWarnings("unchecked")
    public SplitterBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.SPLITTER, pos, state);
        outputSlots = new SingleVariantStorage[4];
        for (int i = 0; i < 4; i++) {
            outputSlots[i] = new SingleVariantStorage<>() {
                @Override
                protected ItemVariant getBlankVariant() {
                    return ItemVariant.blank();
                }

                @Override
                protected long getCapacity(ItemVariant variant) {
                    return 1;
                }

                @Override
                protected void onFinalCommit() {
                    setChanged();
                }
            };
        }
    }

    /**
     * Returns the storage exposed on the given face of this block.
     * Input face: insert-only (round-robin distributes to output slots).
     * Output faces (4 perpendicular to input): extract-only.
     * Back face (opposite input): nothing.
     */
    public Storage<ItemVariant> getStorageForFace(Direction face) {
        Direction inputDir = getBlockState().getValue(DirectionalBlock.FACING);

        if (face == inputDir) {
            return new InputStorage();
        }

        if (face == inputDir.getOpposite()) {
            return null;
        }

        int slotIndex = getSlotIndexForDirection(face, inputDir);
        if (slotIndex >= 0 && slotIndex < 4) {
            return new ExtractOnlyStorage(outputSlots[slotIndex]);
        }

        return null;
    }

    /**
     * Returns the 4 output directions (perpendicular to input).
     * Order is stable for a given inputDir so slot mapping is consistent.
     */
    public static Direction[] getOutputDirections(Direction inputDir) {
        Direction[] outputs = new Direction[4];
        int idx = 0;
        for (Direction d : Direction.values()) {
            if (d != inputDir && d != inputDir.getOpposite()) {
                outputs[idx++] = d;
            }
        }
        return outputs;
    }

    private int getSlotIndexForDirection(Direction face, Direction inputDir) {
        Direction[] outputs = getOutputDirections(inputDir);
        for (int i = 0; i < outputs.length; i++) {
            if (outputs[i] == face) return i;
        }
        return -1;
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, SplitterBlockEntity be) {
        Direction inputDir = state.getValue(DirectionalBlock.FACING);
        Direction[] outputs = getOutputDirections(inputDir);

        for (int i = 0; i < 4; i++) {
            SingleVariantStorage<ItemVariant> slot = be.outputSlots[i];
            if (slot.getAmount() > 0) {
                Direction outDir = outputs[i];
                Storage<ItemVariant> target = ItemStorage.SIDED.find(
                        level, pos.relative(outDir), outDir.getOpposite()
                );
                if (target != null) {
                    StorageUtil.move(slot, target, v -> true, 1, null);
                }
            }
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        for (int i = 0; i < 4; i++) {
            SingleVariantStorage<ItemVariant> slot = outputSlots[i];
            if (slot.getAmount() > 0) {
                ItemStack stack = slot.getResource().toStack((int) slot.getAmount());
                tag.put("Slot" + i, stack.save(registries));
            }
        }
        tag.putInt("RoundRobinIndex", roundRobinIndex);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        for (int i = 0; i < 4; i++) {
            if (tag.contains("Slot" + i)) {
                ItemStack stack = ItemStack.parseOptional(registries, tag.getCompound("Slot" + i));
                outputSlots[i].variant = ItemVariant.of(stack);
                outputSlots[i].amount = stack.getCount();
            } else {
                outputSlots[i].variant = ItemVariant.blank();
                outputSlots[i].amount = 0;
            }
        }
        roundRobinIndex = tag.getInt("RoundRobinIndex");
    }

    /**
     * Insert-only storage for the input face.
     * Distributes items round-robin across the 4 output slots.
     */
    private class InputStorage implements Storage<ItemVariant> {
        @Override
        public long insert(ItemVariant resource, long maxAmount, TransactionContext transaction) {
            if (resource.isBlank()) return 0;

            long totalInserted = 0;
            int startIndex = roundRobinIndex;

            for (int i = 0; i < 4 && totalInserted < maxAmount; i++) {
                int idx = (startIndex + i) % 4;
                long inserted = outputSlots[idx].insert(resource, maxAmount - totalInserted, transaction);
                totalInserted += inserted;
                if (inserted > 0) {
                    roundRobinIndex = (idx + 1) % 4;
                }
            }

            return totalInserted;
        }

        @Override
        public long extract(ItemVariant resource, long maxAmount, TransactionContext transaction) {
            return 0;
        }

        @Override
        public Iterator<StorageView<ItemVariant>> iterator() {
            return Collections.emptyIterator();
        }
    }

    /**
     * Extract-only wrapper around an output slot.
     * Implements SlottedStorage so mods like Oritech can use getSlot() for extraction.
     */
    private static class ExtractOnlyStorage implements SlottedStorage<ItemVariant> {
        private final SingleVariantStorage<ItemVariant> slot;

        ExtractOnlyStorage(SingleVariantStorage<ItemVariant> slot) {
            this.slot = slot;
        }

        @Override
        public int getSlotCount() {
            return 1;
        }

        @Override
        public SingleSlotStorage<ItemVariant> getSlot(int index) {
            return slot;
        }

        @Override
        public long insert(ItemVariant resource, long maxAmount, TransactionContext transaction) {
            return 0;
        }

        @Override
        public long extract(ItemVariant resource, long maxAmount, TransactionContext transaction) {
            return slot.extract(resource, maxAmount, transaction);
        }

        @Override
        public Iterator<StorageView<ItemVariant>> iterator() {
            return List.<StorageView<ItemVariant>>of(slot).iterator();
        }
    }
}
