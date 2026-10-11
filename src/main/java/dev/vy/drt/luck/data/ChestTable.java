package dev.vy.drt.luck.data;

import dev.vy.drt.luck.ChestTier;
import dev.vy.drt.luck.FloorId;
import java.util.List;

public record ChestTable(
	FloorId floor,
	ChestTier tier,
	int baseQuality,
	long baseCost,
	List<LootEntry> entries
) {
	public ChestTable {
		entries = List.copyOf(entries);
	}

	public LootEntry entry(String key) {
		for (LootEntry entry : entries) {
			if (entry.key().equals(key)) return entry;
		}
		return null;
	}

	public boolean contains(String key) {
		return entry(key) != null;
	}

	public int totalWeight() {
		int total = 0;
		for (LootEntry entry : entries) total += entry.weight();
		return total;
	}
}
