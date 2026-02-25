package com.epiccoleman.widgets.registry;

import com.epiccoleman.widgets.EricsWidgets;
import com.epiccoleman.widgets.block.entity.SplitterBlockEntity;
import com.epiccoleman.widgets.block.entity.ValveBlockEntity;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidStorage;
import net.fabricmc.fabric.api.transfer.v1.item.ItemStorage;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.entity.BlockEntityType;

@SuppressWarnings("UnstableApiUsage")
public class ModBlockEntities {

    public static final BlockEntityType<SplitterBlockEntity> SPLITTER = Registry.register(
            BuiltInRegistries.BLOCK_ENTITY_TYPE,
            ResourceLocation.fromNamespaceAndPath(EricsWidgets.MOD_ID, "splitter"),
            BlockEntityType.Builder.of(SplitterBlockEntity::new, ModBlocks.SPLITTER).build(null)
    );

    public static final BlockEntityType<ValveBlockEntity> VALVE = Registry.register(
            BuiltInRegistries.BLOCK_ENTITY_TYPE,
            ResourceLocation.fromNamespaceAndPath(EricsWidgets.MOD_ID, "valve"),
            BlockEntityType.Builder.of(ValveBlockEntity::new, ModBlocks.VALVE).build(null)
    );

    public static void register() {
        EricsWidgets.LOGGER.info("Registering block entities");

        ItemStorage.SIDED.registerForBlockEntity(
                (be, direction) -> be.getStorageForFace(direction),
                SPLITTER
        );

        FluidStorage.SIDED.registerForBlockEntity(
                (be, direction) -> be.getStorageForFace(direction),
                VALVE
        );
    }
}
