package com.worldstudio;

import com.worldstudio.item.StudioWandItem;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroups;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;

public final class StudioItems {
	public static final Identifier STUDIO_WAND_ID = WorldStudioMod.id("studio_wand");

	public static Item studioWand;

	private StudioItems() {
	}

	public static void register() {
		studioWand = Registry.register(Registries.ITEM, STUDIO_WAND_ID,
				new StudioWandItem(new Item.Settings().maxCount(1)));

		ItemGroupEvents.modifyEntriesEvent(ItemGroups.TOOLS).register(entries -> entries.add(studioWand));
	}
}
