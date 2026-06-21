package dev.vy.drt.config;

import java.util.ArrayList;
import java.util.List;

public final class DungeonRunRecord {
	public long timestampEpochMillis;
	public int runNumber;
	public String floor = "M5";
	public String grade = "S+";
	public String chestTitle = "";
	public long chestCostCoins;
	public long chestValueCoins;
	public long chestProfitCoins;
	public List<DungeonLootEntry> lootEntries = new ArrayList<>();

	public DungeonRunRecord() {
	}

	public DungeonRunRecord(long timestampEpochMillis, int runNumber, String floor, String grade, String chestTitle, long chestCostCoins, long chestValueCoins, long chestProfitCoins, List<DungeonLootEntry> lootEntries) {
		this.timestampEpochMillis = Math.max(0L, timestampEpochMillis);
		this.runNumber = Math.max(0, runNumber);
		this.floor = floor == null ? "UNKNOWN" : floor;
		this.grade = grade == null ? "?" : grade;
		this.chestTitle = chestTitle == null ? "" : chestTitle;
		this.chestCostCoins = Math.max(0L, chestCostCoins);
		this.chestValueCoins = Math.max(0L, chestValueCoins);
		this.chestProfitCoins = chestProfitCoins;
		if (lootEntries != null) {
			for (DungeonLootEntry entry : lootEntries) {
				if (entry != null) {
					this.lootEntries.add(entry.copy());
				}
			}
		}
	}

	public DungeonRunRecord copy() {
		return new DungeonRunRecord(timestampEpochMillis, runNumber, floor, grade, chestTitle, chestCostCoins, chestValueCoins, chestProfitCoins, lootEntries);
	}
}
