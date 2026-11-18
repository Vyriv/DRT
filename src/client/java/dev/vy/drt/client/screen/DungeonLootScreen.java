package dev.vy.drt.client.screen;

import dev.vy.drt.config.DungeonFloor;
import net.minecraft.client.gui.screens.Screen;

public class DungeonLootScreen extends DrtScreen {
	public DungeonLootScreen() {
		this(null, null, null);
	}

	public DungeonLootScreen(DungeonFloor floorFilter, String searchFilter) {
		this(floorFilter, searchFilter, null);
	}

	public DungeonLootScreen(DungeonFloor floorFilter, String searchFilter, Screen parent) {
		super(DrtNavTab.LOOT, floorFilter, searchFilter, parent);
	}
}
