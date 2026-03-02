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

    private static final BlockBehaviour.Properties SPLITTER_PROPS = BlockBehaviour.Properties.of()
            .strength(3.0f, 4.8f)
            .noOcclusion()
            .sound(SoundType.METAL);

    // --- Blocks ---

    public static final Block SPLITTER = Registry.register(
            BuiltInRegistries.BLOCK,
            ResourceLocation.fromNamespaceAndPath(EricsWidgets.MOD_ID, "splitter"),
            new SplitterBlock(SPLITTER_PROPS, 4)
    );

    public static final Block SPLITTER_3WAY = Registry.register(
            BuiltInRegistries.BLOCK,
            ResourceLocation.fromNamespaceAndPath(EricsWidgets.MOD_ID, "splitter_3way"),
            new SplitterBlock(SPLITTER_PROPS, 3)
    );

    public static final Block SPLITTER_2WAY = Registry.register(
            BuiltInRegistries.BLOCK,
            ResourceLocation.fromNamespaceAndPath(EricsWidgets.MOD_ID, "splitter_2way"),
            new SplitterBlock(SPLITTER_PROPS, 2)
    );

    // --- Block Items ---

    public static final Item SPLITTER_ITEM = Registry.register(
            BuiltInRegistries.ITEM,
            ResourceLocation.fromNamespaceAndPath(EricsWidgets.MOD_ID, "splitter"),
            new BlockItem(SPLITTER, new Item.Properties())
    );

    public static final Item SPLITTER_3WAY_ITEM = Registry.register(
            BuiltInRegistries.ITEM,
            ResourceLocation.fromNamespaceAndPath(EricsWidgets.MOD_ID, "splitter_3way"),
            new BlockItem(SPLITTER_3WAY, new Item.Properties())
    );

    public static final Item SPLITTER_2WAY_ITEM = Registry.register(
            BuiltInRegistries.ITEM,
            ResourceLocation.fromNamespaceAndPath(EricsWidgets.MOD_ID, "splitter_2way"),
            new BlockItem(SPLITTER_2WAY, new Item.Properties())
    );

    // --- Component Items ---

    public static final Item SPLITTER_INPUT_ITEM = Registry.register(
            BuiltInRegistries.ITEM,
            ResourceLocation.fromNamespaceAndPath(EricsWidgets.MOD_ID, "splitter_input"),
            new Item(new Item.Properties())
    );

    public static final Item SPLITTER_OUTPUT_ITEM = Registry.register(
            BuiltInRegistries.ITEM,
            ResourceLocation.fromNamespaceAndPath(EricsWidgets.MOD_ID, "splitter_output"),
            new Item(new Item.Properties())
    );

    public static void register() {
        EricsWidgets.LOGGER.info("Registering blocks");
    }
}
