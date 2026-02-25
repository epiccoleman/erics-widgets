package com.epiccoleman.widgets.registry;

import com.epiccoleman.widgets.EricsWidgets;
import com.epiccoleman.widgets.block.SplitterBlock;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;

public class ModBlocks {

    public static final Block SPLITTER = Registry.register(
            BuiltInRegistries.BLOCK,
            ResourceLocation.fromNamespaceAndPath(EricsWidgets.MOD_ID, "splitter"),
            new SplitterBlock(BlockBehaviour.Properties.of()
                    .strength(3.5f)
                    .requiresCorrectToolForDrops()
                    .noOcclusion()
                    .sound(SoundType.METAL))
    );

    public static final Item SPLITTER_ITEM = Registry.register(
            BuiltInRegistries.ITEM,
            ResourceLocation.fromNamespaceAndPath(EricsWidgets.MOD_ID, "splitter"),
            new BlockItem(SPLITTER, new Item.Properties())
    );

    public static void register() {
        EricsWidgets.LOGGER.info("Registering blocks");
    }
}
