package dev.vy.drt.config;

public final class DungeonLootEntry {
	public String rawName = "";
	public String itemId = "";
	public int quantity = 1;

	public DungeonLootEntry() {
	}

	public DungeonLootEntry(String rawName, String itemId, int quantity) {
		this.rawName = rawName == null ? "" : rawName;
		this.itemId = itemId == null ? "" : itemId;
		this.quantity = Math.max(1, quantity);
	}

	public DungeonLootEntry copy() {
		return new DungeonLootEntry(rawName, itemId, quantity);
	}
}
