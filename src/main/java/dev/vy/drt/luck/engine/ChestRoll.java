package dev.vy.drt.luck.engine;

import dev.vy.drt.luck.ChestTier;
import dev.vy.drt.luck.data.LootEntry;
import java.util.List;

/**
 * Contents of one rolled reward chest.
 *
 * @param items        entries in roll order with their pick counts (repeatable and filler essence can exceed 1)
 * @param meterForced  whether the meter guarantee placed the selected item in this chest
 */
public record ChestRoll(ChestTier tier, int quality, long cost, List<Item> items, boolean meterForced) {
	public record Item(LootEntry entry, int count) {
	}

	public ChestRoll {
		items = List.copyOf(items);
	}

	public boolean contains(String key) {
		for (Item item : items) {
			if (item.entry().key().equals(key)) return true;
		}
		return false;
	}

	/** Entries that a luck score can consider: weighted, non-essence picks. */
	public List<LootEntry> rolledLuckItems() {
		return items.stream()
			.map(Item::entry)
			.filter(entry -> entry.role() == LootEntry.Role.WEIGHTED)
			.toList();
	}
}
