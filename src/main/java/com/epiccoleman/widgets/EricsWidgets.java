package com.epiccoleman.widgets;

import com.epiccoleman.widgets.registry.ModBlockEntities;
import com.epiccoleman.widgets.registry.ModBlocks;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.minecraft.world.item.CreativeModeTabs;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class EricsWidgets implements ModInitializer {
	public static final String MOD_ID = "erics-widgets";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		ModBlocks.register();
		ModBlockEntities.register();

		ItemGroupEvents.modifyEntriesEvent(CreativeModeTabs.REDSTONE_BLOCKS).register(content -> {
			content.accept(ModBlocks.SPLITTER_ITEM);
			content.accept(ModBlocks.VALVE_ITEM);
		});

		LOGGER.info("Eric's Widgets initialized!");
	}
}
