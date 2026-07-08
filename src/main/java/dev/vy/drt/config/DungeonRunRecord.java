package dev.vy.drt.config;

import java.util.ArrayList;
import java.util.List;

public final class DungeonRunRecord {
	public long timestampEpochMillis;
	public int runNumber;
	public int chestNumber;
	public String floor = "M5";
	public String grade = "S+";
	public String chestTitle = "";
	public long chestCostCoins;
	public long baseChestCostCoins;
	public long dungeonChestKeyCostCoins;
	public long kismetFeatherCostCoins;
	public long wheelOfFateCostCoins;
	public long kuudraKeyCostCoins;
	public boolean usedDungeonChestKey;
	public boolean usedKismetFeather;
	public boolean kismetRerolledChestOpened;
	public boolean usedWheelOfFate;
	public boolean usedKuudraKey;
	public long chestValueCoins;
	public long chestProfitCoins;
	public List<DungeonLootEntry> lootEntries = new ArrayList<>();

	public DungeonRunRecord() {
	}

	public DungeonRunRecord(long timestampEpochMillis, int runNumber, String floor, String grade, String chestTitle, long chestCostCoins, long chestValueCoins, long chestProfitCoins, List<DungeonLootEntry> lootEntries) {
		this(timestampEpochMillis, runNumber, 0, floor, grade, chestTitle, chestCostCoins, chestValueCoins, chestProfitCoins, lootEntries);
	}

	public DungeonRunRecord(long timestampEpochMillis, int runNumber, int chestNumber, String floor, String grade, String chestTitle, long chestCostCoins, long chestValueCoins, long chestProfitCoins, List<DungeonLootEntry> lootEntries) {
		this.timestampEpochMillis = Math.max(0L, timestampEpochMillis);
		this.runNumber = Math.max(0, runNumber);
		this.chestNumber = Math.max(0, chestNumber);
		this.floor = floor == null ? "UNKNOWN" : floor;
		this.grade = grade == null ? "?" : grade;
		this.chestTitle = chestTitle == null ? "" : chestTitle;
		this.chestCostCoins = Math.max(0L, chestCostCoins);
		this.baseChestCostCoins = this.chestCostCoins;
		this.chestValueCoins = Math.max(0L, chestValueCoins);
		this.chestProfitCoins = chestProfitCoins;
		if (lootEntries != null) {
			for (DungeonLootEntry entry : lootEntries) {
				if (entry != null) {
					this.lootEntries.add(entry.copy());
				}
			}
		}
		normalizeCostBreakdown();
	}

	public DungeonRunRecord copy() {
		DungeonRunRecord copy = new DungeonRunRecord(timestampEpochMillis, runNumber, chestNumber, floor, grade, chestTitle, chestCostCoins, chestValueCoins, chestProfitCoins, lootEntries);
		copy.applyCostBreakdown(toCostBreakdown());
		copy.normalizeCostBreakdown();
		return copy;
	}

	public ChestCostBreakdown toCostBreakdown() {
		ChestCostBreakdown breakdown = new ChestCostBreakdown();
		breakdown.baseChestCostCoins = baseChestCostCoins;
		breakdown.dungeonChestKeyCostCoins = dungeonChestKeyCostCoins;
		breakdown.kismetFeatherCostCoins = kismetFeatherCostCoins;
		breakdown.wheelOfFateCostCoins = wheelOfFateCostCoins;
		breakdown.kuudraKeyCostCoins = kuudraKeyCostCoins;
		breakdown.usedDungeonChestKey = usedDungeonChestKey;
		breakdown.usedKismetFeather = usedKismetFeather;
		breakdown.kismetRerolledChestOpened = kismetRerolledChestOpened;
		breakdown.usedWheelOfFate = usedWheelOfFate;
		breakdown.usedKuudraKey = usedKuudraKey;
		breakdown.normalize();
		return breakdown;
	}

	public void applyCostBreakdown(ChestCostBreakdown breakdown) {
		ChestCostBreakdown normalized = breakdown == null ? new ChestCostBreakdown() : breakdown.copy();
		baseChestCostCoins = normalized.baseChestCostCoins;
		dungeonChestKeyCostCoins = normalized.dungeonChestKeyCostCoins;
		kismetFeatherCostCoins = normalized.kismetFeatherCostCoins;
		wheelOfFateCostCoins = normalized.wheelOfFateCostCoins;
		kuudraKeyCostCoins = normalized.kuudraKeyCostCoins;
		usedDungeonChestKey = normalized.usedDungeonChestKey;
		usedKismetFeather = normalized.usedKismetFeather;
		kismetRerolledChestOpened = normalized.kismetRerolledChestOpened;
		usedWheelOfFate = normalized.usedWheelOfFate;
		usedKuudraKey = normalized.usedKuudraKey;
		normalizeCostBreakdown();
	}

	public boolean normalizeCostBreakdown() {
		long oldChestCostCoins = chestCostCoins;
		long oldBaseChestCostCoins = baseChestCostCoins;
		long oldDungeonChestKeyCostCoins = dungeonChestKeyCostCoins;
		long oldKismetFeatherCostCoins = kismetFeatherCostCoins;
		long oldWheelOfFateCostCoins = wheelOfFateCostCoins;
		long oldKuudraKeyCostCoins = kuudraKeyCostCoins;
		long oldChestValueCoins = chestValueCoins;
		boolean oldUsedDungeonChestKey = usedDungeonChestKey;
		boolean oldUsedKismetFeather = usedKismetFeather;
		boolean oldKismetRerolledChestOpened = kismetRerolledChestOpened;
		boolean oldUsedWheelOfFate = usedWheelOfFate;
		boolean oldUsedKuudraKey = usedKuudraKey;

		chestCostCoins = Math.max(0L, chestCostCoins);
		baseChestCostCoins = Math.max(0L, baseChestCostCoins);
		dungeonChestKeyCostCoins = Math.max(0L, dungeonChestKeyCostCoins);
		kismetFeatherCostCoins = Math.max(0L, kismetFeatherCostCoins);
		wheelOfFateCostCoins = Math.max(0L, wheelOfFateCostCoins);
		kuudraKeyCostCoins = Math.max(0L, kuudraKeyCostCoins);
		chestValueCoins = Math.max(0L, chestValueCoins);

		if (!hasCostBreakdown() && chestCostCoins > 0L) {
			baseChestCostCoins = chestCostCoins;
		}

		if (dungeonChestKeyCostCoins > 0L) usedDungeonChestKey = true;
		if (kismetFeatherCostCoins > 0L) usedKismetFeather = true;
		if (wheelOfFateCostCoins > 0L) usedWheelOfFate = true;
		if (kuudraKeyCostCoins > 0L) usedKuudraKey = true;
		if (!usedKismetFeather) kismetRerolledChestOpened = false;

		chestCostCoins = totalCostCoins();
		return oldChestCostCoins != chestCostCoins
			|| oldBaseChestCostCoins != baseChestCostCoins
			|| oldDungeonChestKeyCostCoins != dungeonChestKeyCostCoins
			|| oldKismetFeatherCostCoins != kismetFeatherCostCoins
			|| oldWheelOfFateCostCoins != wheelOfFateCostCoins
			|| oldKuudraKeyCostCoins != kuudraKeyCostCoins
			|| oldChestValueCoins != chestValueCoins
			|| oldUsedDungeonChestKey != usedDungeonChestKey
			|| oldUsedKismetFeather != usedKismetFeather
			|| oldKismetRerolledChestOpened != kismetRerolledChestOpened
			|| oldUsedWheelOfFate != usedWheelOfFate
			|| oldUsedKuudraKey != usedKuudraKey;
	}

	public long totalCostCoins() {
		return Math.max(0L, baseChestCostCoins)
			+ Math.max(0L, dungeonChestKeyCostCoins)
			+ Math.max(0L, kismetFeatherCostCoins)
			+ Math.max(0L, wheelOfFateCostCoins)
			+ Math.max(0L, kuudraKeyCostCoins);
	}

	private boolean hasCostBreakdown() {
		return toCostBreakdown().hasAnyCostOrModifier();
	}
}
