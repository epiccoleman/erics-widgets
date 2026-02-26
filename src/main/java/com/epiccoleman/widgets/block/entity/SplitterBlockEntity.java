package com.epiccoleman.widgets.block.entity;

import com.epiccoleman.widgets.block.SplitterBlock;
import com.epiccoleman.widgets.registry.ModBlockEntities;
import net.fabricmc.fabric.api.transfer.v1.item.ItemStorage;
import net.fabricmc.fabric.api.transfer.v1.item.ItemVariant;
import net.fabricmc.fabric.api.transfer.v1.storage.Storage;
import net.fabricmc.fabric.api.transfer.v1.storage.SlottedStorage;
import net.fabricmc.fabric.api.transfer.v1.storage.StorageView;
import net.fabricmc.fabric.api.transfer.v1.storage.base.SingleSlotStorage;
import net.fabricmc.fabric.api.transfer.v1.storage.base.SingleVariantStorage;
import net.fabricmc.fabric.api.transfer.v1.transaction.Transaction;
import net.fabricmc.fabric.api.transfer.v1.transaction.TransactionContext;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.Containers;
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

    private static final int PULL_COOLDOWN_TICKS = 8;
    private final int outputCount;
    private final SingleVariantStorage<ItemVariant>[] outputSlots;
    private int roundRobinIndex = 0;
    private int pullCooldown = 0;

    @SuppressWarnings("unchecked")
    public SplitterBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.SPLITTER, pos, state);
        if (state.getBlock() instanceof SplitterBlock splitterBlock) {
            this.outputCount = splitterBlock.getOutputCount();
        } else {
            this.outputCount = 4;
        }
        outputSlots = new SingleVariantStorage[outputCount];
        for (int i = 0; i < outputCount; i++) {
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
     * Output faces: extract-only.
     * All other faces: nothing.
     */
    public Storage<ItemVariant> getStorageForFace(Direction face) {
        BlockState state = getBlockState();
        Direction inputDir = state.getValue(DirectionalBlock.FACING);
        Direction orientation = state.getValue(SplitterBlock.ORIENTATION);

        if (face == inputDir) {
            return new InputStorage();
        }

        List<Direction> outputs = SplitterBlock.getOutputDirections(inputDir, orientation, outputCount);
        int slotIndex = outputs.indexOf(face);
        if (slotIndex >= 0) {
            return new ExtractOnlyStorage(outputSlots[slotIndex]);
        }

        return null;
    }

    private static void pushSlot(SingleVariantStorage<ItemVariant> slot, Storage<ItemVariant> target) {
        try (Transaction tx = Transaction.openOuter()) {
            ItemVariant variant = slot.getResource();
            long amount = slot.getAmount();
            long inserted = target.insert(variant, amount, tx);
            if (inserted > 0) {
                long extracted = slot.extract(variant, inserted, tx);
                if (extracted > 0) {
                    tx.commit();
                }
            }
        }
    }

    public void dropItems(Level level, BlockPos pos) {
        for (int i = 0; i < outputCount; i++) {
            SingleVariantStorage<ItemVariant> slot = outputSlots[i];
            if (!slot.isResourceBlank() && slot.getAmount() > 0) {
                ItemStack stack = slot.getResource().toStack((int) slot.getAmount());
                Containers.dropItemStack(level, pos.getX(), pos.getY(), pos.getZ(), stack);
            }
        }
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, SplitterBlockEntity be) {
        Direction inputDir = state.getValue(DirectionalBlock.FACING);
        Direction orientation = state.getValue(SplitterBlock.ORIENTATION);
        List<Direction> outputs = SplitterBlock.getOutputDirections(inputDir, orientation, be.outputCount);

        // Pull from input face (like a hopper, at hopper speed)
        if (be.pullCooldown > 0) {
            be.pullCooldown--;
        }
        BlockPos inputPos = pos.relative(inputDir);
        Storage<ItemVariant> inputSource = be.pullCooldown <= 0 ? ItemStorage.SIDED.find(
                level, inputPos, inputDir.getOpposite()
        ) : null;
        if (inputSource != null) {
            // Find how many output slots are available
            int availableSlots = 0;
            for (int i = 0; i < be.outputCount; i++) {
                if (be.outputSlots[i].getAmount() <= 0) {
                    availableSlots++;
                }
            }
            if (availableSlots > 0) {
                // Pull one item from the source
                try (Transaction tx = Transaction.openOuter()) {
                    ItemVariant extracted = null;
                    for (StorageView<ItemVariant> view : inputSource) {
                        if (view.isResourceBlank() || view.getAmount() <= 0) continue;
                        extracted = view.getResource();
                        break;
                    }
                    if (extracted != null) {
                        long pulled = inputSource.extract(extracted, 1, tx);
                        if (pulled > 0) {
                            // Insert into the next available output slot via round-robin
                            long distributed = 0;
                            int startIndex = be.roundRobinIndex;
                            for (int i = 0; i < be.outputCount; i++) {
                                int idx = (startIndex + i) % be.outputCount;
                                long inserted = be.outputSlots[idx].insert(extracted, pulled, tx);
                                if (inserted > 0) {
                                    distributed = inserted;
                                    be.roundRobinIndex = (idx + 1) % be.outputCount;
                                    break;
                                }
                            }
                            if (distributed > 0) {
                                tx.commit();
                                be.pullCooldown = PULL_COOLDOWN_TICKS;
                            }
                        }
                    }
                }
            }
        }

        for (int i = 0; i < be.outputCount; i++) {
            SingleVariantStorage<ItemVariant> slot = be.outputSlots[i];
            if (slot.isResourceBlank() || slot.getAmount() <= 0) continue;

            // Try the slot's own output face first
            Direction outDir = outputs.get(i);
            Storage<ItemVariant> target = ItemStorage.SIDED.find(
                    level, pos.relative(outDir), outDir.getOpposite()
            );
            if (target != null) {
                pushSlot(slot, target);
                continue;
            }

            // No target on this face - push to any other face that has one
            for (int j = 0; j < be.outputCount; j++) {
                if (j == i) continue;
                Direction altDir = outputs.get(j);
                Storage<ItemVariant> altTarget = ItemStorage.SIDED.find(
                        level, pos.relative(altDir), altDir.getOpposite()
                );
                if (altTarget != null) {
                    pushSlot(slot, altTarget);
                    break;
                }
            }
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        for (int i = 0; i < outputCount; i++) {
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
        for (int i = 0; i < outputCount; i++) {
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
        if (outputCount > 0) {
            roundRobinIndex %= outputCount;
        }
    }

    /**
     * Insert-only storage for the input face.
     * Distributes items round-robin across all output slots.
     */
    private class InputStorage implements Storage<ItemVariant> {
        @Override
        public long insert(ItemVariant resource, long maxAmount, TransactionContext transaction) {
            if (resource.isBlank()) return 0;

            long totalInserted = 0;
            int startIndex = roundRobinIndex;

            for (int i = 0; i < outputCount && totalInserted < maxAmount; i++) {
                int idx = (startIndex + i) % outputCount;
                long inserted = outputSlots[idx].insert(resource, maxAmount - totalInserted, transaction);
                totalInserted += inserted;
                if (inserted > 0) {
                    roundRobinIndex = (idx + 1) % outputCount;
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
