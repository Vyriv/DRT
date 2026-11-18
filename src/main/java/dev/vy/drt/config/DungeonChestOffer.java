package dev.vy.drt.config;

import java.util.ArrayList;
import java.util.List;

public final class DungeonChestOffer {
	public static final String SOURCE_API_RECOVERED = "HYPIXEL_API";
	public String chestTitle = "";
	public DungeonFloor contextFloor = DungeonFloor.UNKNOWN;
	public ChestCostBreakdown costBreakdown = new ChestCostBreakdown();
	public long valueCoins;
	public long profitCoins;
	public boolean alreadyOpened;
	public long observedAtEpochMillis;
	public boolean purchased;
	/** Blank for legacy/local captures, or {@link #SOURCE_API_RECOVERED} for supplemental recovery. */
	public String captureSource = "";
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
		copy.contextFloor = contextFloor;
		copy.alreadyOpened = alreadyOpened;
		copy.observedAtEpochMillis = observedAtEpochMillis;
		copy.purchased = purchased;
		copy.captureSource = captureSource == null ? "" : captureSource;
		copy.normalize();
		return copy;
	}

	public void normalize() {
		if (chestTitle == null) chestTitle = "";
		if (contextFloor == null) contextFloor = DungeonFloor.UNKNOWN;
		if (costBreakdown == null) costBreakdown = new ChestCostBreakdown();
		costBreakdown.normalize();
		valueCoins = Math.max(0L, valueCoins);
		observedAtEpochMillis = Math.max(0L, observedAtEpochMillis);
		profitCoins = valueCoins - costBreakdown.totalCostCoins();
		if (captureSource == null) captureSource = "";
		if (lootEntries == null) lootEntries = new ArrayList<>();
	}
}
