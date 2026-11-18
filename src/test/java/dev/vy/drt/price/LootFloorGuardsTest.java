package dev.vy.drt.price;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.vy.drt.config.DungeonFloor;
import dev.vy.drt.config.DungeonLootEntry;
import dev.vy.drt.config.DrtConfig;
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

	@Test
	void kuudraFreeChestWithOnlyCrimsonIsNotSingleLineSuspicious() {
		List<DungeonLootEntry> entries = List.of(new DungeonLootEntry("CRIMSON ESSENCE", "ESSENCE_CRIMSON", 50));
		assertFalse(LootFloorGuards.evaluateChest(DungeonFloor.K3, "Free Chest", entries).contains("chest_drop_count_suspicious got=1"));
	}

	@Test
	void infernalFreeChestWithPetAdjusted170EssenceDoesNotWarn() {
		DrtConfig config = new DrtConfig();
		config.kuudraPetEnabled = true;
		config.kuudraPetRarity = "LEGENDARY";
		config.kuudraPetLevel = 100;
		DungeonLootEntry crimson = new DungeonLootEntry("CRIMSON ESSENCE", "ESSENCE_CRIMSON", 170);

		assertTrue(LootFloorGuards.evaluate(DungeonFloor.K5, "Free Chest", crimson, config).isEmpty());
		assertFalse(LootFloorGuards.evaluateChest(DungeonFloor.K5, "Free Chest", List.of(crimson), config)
			.stream().anyMatch(reason -> reason.startsWith("missing_free_crimson_essence")));
		assertTrue(LootFloorGuards.evaluateChest(
			DungeonFloor.K5,
			"Free Chest",
			List.of(new DungeonLootEntry("CRIMSON ESSENCE", "ESSENCE_CRIMSON", 166)),
			config
		).contains("missing_free_crimson_essence expected>=167 got=166"));
	}

	@Test
	void basicKuudraChestsDoNotWarnWhenJudgedAsK1() {
		List<DungeonLootEntry> free = List.of(new DungeonLootEntry("CRIMSON ESSENCE", "ESSENCE_CRIMSON", 22));
		assertTrue(LootFloorGuards.evaluateChest(DungeonFloor.K1, "Free Chest", free).isEmpty());

		DungeonLootEntry crimson = new DungeonLootEntry("CRIMSON ESSENCE", "ESSENCE_CRIMSON", 100);
		List<DungeonLootEntry> paid = List.of(
			new DungeonLootEntry("Aurora Leggings", "AURORA_LEGGINGS", 1),
			crimson,
			new DungeonLootEntry("Kuudra Teeth", "KUUDRA_TEETH", 1),
			new DungeonLootEntry("Kraken Shard", "SHARD_KRAKEN", 1)
		);
		assertTrue(LootFloorGuards.evaluateChest(DungeonFloor.K1, "Paid Chest", paid).isEmpty());
		assertTrue(LootFloorGuards.evaluate(DungeonFloor.K1, "Paid Chest", crimson).isEmpty());
	}

	@Test
	void infernalPaidChestFromReportDoesNotWarn() {
		for (int kraken : new int[] {1, 2}) {
			DungeonLootEntry crimson = new DungeonLootEntry("CRIMSON ESSENCE", "ESSENCE_CRIMSON", 2000);
			DungeonLootEntry shard = new DungeonLootEntry("Kraken Shard", "SHARD_KRAKEN", kraken);
			List<DungeonLootEntry> paid = List.of(
				new DungeonLootEntry("Burning Kuudra Core", "BURNING_KUUDRA_CORE", 1),
				new DungeonLootEntry("Enchanted Book (Hardened Vitality V)", "ENCHANTMENT_HARDENED_VITALITY_5", 1),
				crimson,
				new DungeonLootEntry("Kuudra Teeth", "KUUDRA_TEETH", 4),
				shard
			);
			assertTrue(LootFloorGuards.evaluate(DungeonFloor.K5, "Paid Chest", crimson).isEmpty());
			assertTrue(LootFloorGuards.evaluate(DungeonFloor.K5, "Paid Chest", shard).isEmpty());
			assertTrue(LootFloorGuards.evaluateChest(DungeonFloor.K5, "Paid Chest", paid).isEmpty());
		}
	}

	@Test
	void paidChestWithoutKrakenShardStillWarns() {
		List<DungeonLootEntry> paid = List.of(
			new DungeonLootEntry("Terror Leggings", "TERROR_LEGGINGS", 1),
			new DungeonLootEntry("CRIMSON ESSENCE", "ESSENCE_CRIMSON", 2000),
			new DungeonLootEntry("Kuudra Teeth", "KUUDRA_TEETH", 3)
		);
		assertTrue(LootFloorGuards.evaluateChest(DungeonFloor.K5, "Paid Chest", paid)
			.contains("missing_guaranteed_kraken_shards expected>=1 got=0"));
	}

	@Test
	void m7BedrockWithoutUndeadEssenceStillWarns() {
		List<DungeonLootEntry> rewards = List.of(
			new DungeonLootEntry("Wither Chestplate", "WITHER_CHESTPLATE", 1),
			new DungeonLootEntry("Wither Leggings", "WITHER_LEGGINGS", 1),
			new DungeonLootEntry("Wither Boots", "WITHER_BOOTS", 1),
			new DungeonLootEntry("Recombobulator 3000", "RECOMBOBULATOR_3000", 1),
			new DungeonLootEntry("Necron's Handle", "NECRON_HANDLE", 1)
		);
		assertTrue(LootFloorGuards.evaluateChest(DungeonFloor.M7, "Bedrock Chest", rewards)
			.contains("missing_guaranteed_undead_essence expected>=70 got=0"));
	}

	@Test
	void m7BedrockKeepsItsRewardsWhenEssenceArrives() {
		List<DungeonLootEntry> incomplete = List.of(
			new DungeonLootEntry("Wither Chestplate", "WITHER_CHESTPLATE", 1),
			new DungeonLootEntry("Wither Leggings", "WITHER_LEGGINGS", 1),
			new DungeonLootEntry("Wither Boots", "WITHER_BOOTS", 1),
			new DungeonLootEntry("Recombobulator 3000", "RECOMBOBULATOR_3000", 1),
			new DungeonLootEntry("Necron's Handle", "NECRON_HANDLE", 1)
		);
		List<DungeonLootEntry> complete = new java.util.ArrayList<>(incomplete);
		complete.add(new DungeonLootEntry("UNDEAD ESSENCE", "ESSENCE_UNDEAD", 70));

		assertFalse(LootFloorGuards.evaluateChest(DungeonFloor.M7, "Bedrock Chest", complete)
			.stream().anyMatch(reason -> reason.startsWith("missing_guaranteed_undead_essence")));
		assertEquals(6, complete.size());
		assertEquals(1, complete.getFirst().quantity);
		assertEquals(70, complete.get(5).quantity);
	}

	@Test
	void kuudraFloorWithCatacombsChestSkipsGuards() {
		DungeonLootEntry skull = new DungeonLootEntry("Master Skull - Tier 3", "MASTER_SKULL_TIER_3", 1);
		assertTrue(LootFloorGuards.evaluate(DungeonFloor.K3, "Obsidian Chest", skull).isEmpty());

		DungeonLootEntry star = new DungeonLootEntry("Third Master Star", "THIRD_MASTER_STAR", 1);
		assertTrue(LootFloorGuards.evaluate(DungeonFloor.K3, "Bedrock Chest", star).isEmpty());

		DungeonLootEntry essence = new DungeonLootEntry("UNDEAD ESSENCE", "ESSENCE_UNDEAD", 22);
		assertTrue(LootFloorGuards.evaluate(DungeonFloor.K3, "Wood Chest", essence).isEmpty());
	}
}
