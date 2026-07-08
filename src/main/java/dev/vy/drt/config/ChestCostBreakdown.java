package dev.vy.drt.config;

public final class ChestCostBreakdown {
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

	public ChestCostBreakdown() {
	}

	public ChestCostBreakdown(long baseChestCostCoins) {
		this.baseChestCostCoins = Math.max(0L, baseChestCostCoins);
		normalize();
	}

	public ChestCostBreakdown copy() {
		ChestCostBreakdown copy = new ChestCostBreakdown();
		copy.baseChestCostCoins = baseChestCostCoins;
		copy.dungeonChestKeyCostCoins = dungeonChestKeyCostCoins;
		copy.kismetFeatherCostCoins = kismetFeatherCostCoins;
		copy.wheelOfFateCostCoins = wheelOfFateCostCoins;
		copy.kuudraKeyCostCoins = kuudraKeyCostCoins;
		copy.usedDungeonChestKey = usedDungeonChestKey;
		copy.usedKismetFeather = usedKismetFeather;
		copy.kismetRerolledChestOpened = kismetRerolledChestOpened;
		copy.usedWheelOfFate = usedWheelOfFate;
		copy.usedKuudraKey = usedKuudraKey;
		copy.normalize();
		return copy;
	}

	public void normalize() {
		baseChestCostCoins = Math.max(0L, baseChestCostCoins);
		dungeonChestKeyCostCoins = Math.max(0L, dungeonChestKeyCostCoins);
		kismetFeatherCostCoins = Math.max(0L, kismetFeatherCostCoins);
		wheelOfFateCostCoins = Math.max(0L, wheelOfFateCostCoins);
		kuudraKeyCostCoins = Math.max(0L, kuudraKeyCostCoins);
		if (dungeonChestKeyCostCoins > 0L) usedDungeonChestKey = true;
		if (kismetFeatherCostCoins > 0L) usedKismetFeather = true;
		if (wheelOfFateCostCoins > 0L) usedWheelOfFate = true;
		if (kuudraKeyCostCoins > 0L) usedKuudraKey = true;
		if (!usedKismetFeather) kismetRerolledChestOpened = false;
	}

	public long totalCostCoins() {
		return Math.max(0L, baseChestCostCoins)
			+ Math.max(0L, dungeonChestKeyCostCoins)
			+ Math.max(0L, kismetFeatherCostCoins)
			+ Math.max(0L, wheelOfFateCostCoins)
			+ Math.max(0L, kuudraKeyCostCoins);
	}

	public boolean hasAnyCostOrModifier() {
		return totalCostCoins() > 0L
			|| usedDungeonChestKey
			|| usedKismetFeather
			|| usedWheelOfFate
			|| usedKuudraKey;
	}
}
