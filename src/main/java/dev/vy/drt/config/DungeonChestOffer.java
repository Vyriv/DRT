package dev.vy.drt.config;

import java.util.ArrayList;
import java.util.List;

public final class DungeonChestOffer {
	public String chestTitle = "";
	public ChestCostBreakdown costBreakdown = new ChestCostBreakdown();
	public long valueCoins;
	public long profitCoins;
	public boolean alreadyOpened;
	public List<DungeonLootEntry> lootEntries = new ArrayList<>();

	public DungeonChestOffer() {
	}

	public DungeonChestOffer(String chestTitle, ChestCostBreakdown costBreakdown, long valueCoins, List<DungeonLootEntry> lootEntries) {
		this.chestTitle = chestTitle == null ? "" : chestTitle;
		this.costBreakdown = costBreakdown == null ? new ChestCostBreakdown() : costBreakdown.copy();
		this.valueCoins = Math.max(0L, valueCoins);
		if (lootEntries != null) {
			for (DungeonLootEntry entry : lootEntries) {
				if (entry != null) this.lootEntries.add(entry.copy());
			}
		}
		normalize();
	}

	public DungeonChestOffer copy() {
		DungeonChestOffer copy = new DungeonChestOffer(chestTitle, costBreakdown, valueCoins, lootEntries);
		copy.alreadyOpened = alreadyOpened;
		copy.normalize();
		return copy;
	}

	public void normalize() {
		if (chestTitle == null) chestTitle = "";
		if (costBreakdown == null) costBreakdown = new ChestCostBreakdown();
		costBreakdown.normalize();
		valueCoins = Math.max(0L, valueCoins);
		profitCoins = valueCoins - costBreakdown.totalCostCoins();
		if (lootEntries == null) lootEntries = new ArrayList<>();
	}
}
