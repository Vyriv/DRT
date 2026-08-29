package dev.vy.drt.price;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.vy.drt.config.DungeonFloor;
import dev.vy.drt.config.DungeonLootEntry;
import java.util.List;
import org.junit.jupiter.api.Test;

class LootFloorGuardsTest {
	@Test
	void auroraChestplateIsKnownOnKuudra() {
		DungeonLootEntry entry = new DungeonLootEntry("Aurora Chestplate", "AURORA_CHESTPLATE", 1);
		assertTrue(ManualLootSuggestions.isKnownDrop(DungeonFloor.K3, entry.rawName, entry.itemId));
		assertTrue(LootFloorGuards.evaluate(DungeonFloor.K3, "Paid Chest", entry).isEmpty());
	}

	@Test
	void auroraChestplateFlagsOnWrongDungeonFloor() {
		DungeonLootEntry entry = new DungeonLootEntry("Aurora Chestplate", "AURORA_CHESTPLATE", 1);
		assertFalse(ManualLootSuggestions.isKnownDrop(DungeonFloor.M7, entry.rawName, entry.itemId));
		assertTrue(LootFloorGuards.evaluate(DungeonFloor.M7, "Bedrock Chest", entry).contains("not_in_known_drop_list"));
	}

	@Test
	void threeLineChestDoesNotWarnWhenFloorUnknown() {
		List<DungeonLootEntry> entries = List.of(
			new DungeonLootEntry("Wither Boots", "WITHER_BOOTS", 1),
			new DungeonLootEntry("Power Dragon Shard", "SHARD_POWER_DRAGON", 1),
			new DungeonLootEntry("UNDEAD ESSENCE", "ESSENCE_UNDEAD", 133)
		);
		assertTrue(LootFloorGuards.evaluateChest(DungeonFloor.UNKNOWN, "Bedrock Chest", entries).isEmpty());
	}

	@Test
	void threeLineChestDoesNotWarnWhenFloorKnown() {
		List<DungeonLootEntry> entries = List.of(
			new DungeonLootEntry("Wither Boots", "WITHER_BOOTS", 1),
			new DungeonLootEntry("Power Dragon Shard", "SHARD_POWER_DRAGON", 1),
			new DungeonLootEntry("UNDEAD ESSENCE", "ESSENCE_UNDEAD", 133)
		);
		assertFalse(LootFloorGuards.evaluateChest(DungeonFloor.M7, "Bedrock Chest", entries).contains("chest_drop_count_suspicious got=1"));
	}

	@Test
	void singleLineChestWarnsWhenFloorKnown() {
		List<DungeonLootEntry> entries = List.of(new DungeonLootEntry("UNDEAD ESSENCE", "ESSENCE_UNDEAD", 50));
		assertTrue(LootFloorGuards.evaluateChest(DungeonFloor.M7, "Bedrock Chest", entries).contains("chest_drop_count_suspicious got=1"));
	}
}
