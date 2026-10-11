package dev.vy.drt.luck;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.vy.drt.luck.data.CatacombsLootDatabase;
import dev.vy.drt.luck.data.ChestTable;
import dev.vy.drt.luck.data.FloorStatus;
import dev.vy.drt.luck.data.FloorTable;
import dev.vy.drt.luck.data.LootEntry;
import java.nio.charset.StandardCharsets;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class LootDatabaseTest {
	// Base quality / base cost per chest. Matches the archived official wiki and community wiki
	// chest tables (Stage A audit), except F2/M2 Emerald cost which is disputed (200k in data, 250k on wikis).
	private static final Map<FloorId, int[][]> BASE_TABLE = Map.ofEntries(
		Map.entry(FloorId.F1, new int[][] {{110, 0}, {160, 25000}, {200, 50000}, {240, 100000}, {300, 250000}}),
		Map.entry(FloorId.F2, new int[][] {{120, 0}, {170, 50000}, {210, 100000}, {250, 200000}, {320, 500000}}),
		Map.entry(FloorId.F3, new int[][] {{125, 0}, {180, 100000}, {220, 250000}, {260, 500000}, {330, 1000000}}),
		Map.entry(FloorId.F4, new int[][] {{125, 0}, {180, 100000}, {220, 250000}, {260, 500000}, {330, 1000000}}),
		Map.entry(FloorId.F5, new int[][] {{125, 0}, {180, 100000}, {220, 250000}, {260, 500000}, {330, 1000000}, {370, 2000000}}),
		Map.entry(FloorId.F6, new int[][] {{125, 0}, {180, 100000}, {220, 250000}, {260, 500000}, {330, 1000000}, {370, 2000000}}),
		Map.entry(FloorId.F7, new int[][] {{125, 0}, {180, 100000}, {220, 250000}, {260, 500000}, {330, 1000000}, {370, 2000000}}),
		Map.entry(FloorId.M1, new int[][] {{120, 0}, {170, 25000}, {215, 50000}, {255, 100000}, {320, 250000}}),
		Map.entry(FloorId.M2, new int[][] {{130, 0}, {180, 50000}, {225, 100000}, {265, 200000}, {340, 500000}}),
		Map.entry(FloorId.M3, new int[][] {{135, 0}, {195, 100000}, {235, 250000}, {280, 500000}, {350, 1000000}}),
		Map.entry(FloorId.M4, new int[][] {{135, 0}, {195, 100000}, {240, 250000}, {280, 500000}, {355, 1000000}}),
		Map.entry(FloorId.M5, new int[][] {{135, 0}, {195, 100000}, {240, 250000}, {280, 500000}, {355, 1000000}, {405, 2000000}}),
		Map.entry(FloorId.M6, new int[][] {{140, 0}, {200, 100000}, {245, 250000}, {290, 500000}, {365, 1000000}, {410, 2000000}}),
		Map.entry(FloorId.M7, new int[][] {{140, 0}, {200, 100000}, {245, 250000}, {290, 500000}, {370, 1000000}, {420, 2000000}})
	);

	@ParameterizedTest
	@EnumSource(FloorId.class)
	void everyFloorHasExpectedChestsAndBaseValues(FloorId floor) {
		FloorTable table = LuckTestData.baseData().floor(floor);
		Set<ChestTier> expected = floor.hasBedrockChest() ? EnumSet.allOf(ChestTier.class) : EnumSet.range(ChestTier.WOOD, ChestTier.OBSIDIAN);
		assertEquals(expected, table.chests().keySet());
		int[][] base = BASE_TABLE.get(floor);
		for (ChestTier tier : expected) {
			ChestTable chest = table.chest(tier);
			assertEquals(base[tier.ordinal()][0], chest.baseQuality(), floor + " " + tier + " base quality");
			assertEquals(base[tier.ordinal()][1], chest.baseCost(), floor + " " + tier + " base cost");
		}
	}

	@ParameterizedTest
	@EnumSource(FloorId.class)
	void everyChestHasOneRepeatableAndOneFillerEssence(FloorId floor) {
		for (ChestTable chest : LuckTestData.defaults().floor(floor).chests().values()) {
			long repeatable = chest.entries().stream().filter(e -> e.role() == LootEntry.Role.REPEATABLE_ESSENCE).count();
			long filler = chest.entries().stream().filter(e -> e.role() == LootEntry.Role.FILLER_ESSENCE).count();
			assertEquals(1, repeatable, floor + " " + chest.tier());
			assertEquals(1, filler, floor + " " + chest.tier());
			for (LootEntry entry : chest.entries()) {
				if (entry.role() == LootEntry.Role.REPEATABLE_ESSENCE) {
					// M4 Obsidian weights are all scaled by 4, essence included.
					int expectedWeight = floor == FloorId.M4 && chest.tier() == ChestTier.OBSIDIAN ? 4 : 1;
					assertEquals(expectedWeight, entry.weight(), floor + " " + chest.tier());
					assertEquals(10, entry.quality());
				}
				if (entry.role() == LootEntry.Role.WEIGHTED) {
					assertTrue(entry.quality() >= 40, entry.key() + " quality");
					assertFalse(entry.kind() == LootEntry.Kind.ESSENCE, entry.key());
				}
			}
			long weighted = chest.entries().stream().filter(e -> e.role() == LootEntry.Role.WEIGHTED).count();
			assertTrue(weighted > 0 && weighted <= 63, floor + " " + chest.tier() + " weighted count");
		}
	}

	@Test
	void floorStatusesMatchManifest() {
		Set<FloorId> provisional = EnumSet.of(FloorId.M4, FloorId.M5, FloorId.M7);
		for (FloorId floor : FloorId.values()) {
			FloorStatus expected = provisional.contains(floor) ? FloorStatus.PROVISIONAL : FloorStatus.SINGLE_SOURCE_TOP_CHEST_CONFIRMED;
			assertEquals(expected, LuckTestData.defaults().floor(floor).status(), floor.name());
		}
	}

	@Test
	void defaultOverridesAreApplied() {
		CatacombsLootDatabase db = LuckTestData.defaults();
		assertEquals(4, db.floor(FloorId.M4).chest(ChestTier.OBSIDIAN).entry("SPIRIT_MASK").weight());
		assertEquals(296, db.floor(FloorId.M4).topChest().totalWeight());
		assertEquals(1, db.floor(FloorId.M5).meterTotalWeightDelta());
		assertEquals(1, db.floor(FloorId.M7).meterTotalWeightDelta());
		assertEquals(0, db.floor(FloorId.F7).meterTotalWeightDelta());
		assertNotNull(db.floor(FloorId.F7).chest(ChestTier.OBSIDIAN).entry("WITHER_SHARD"));
		assertEquals(200000, db.floor(FloorId.F2).chest(ChestTier.EMERALD).baseCost());
		assertEquals("IN_GAME_WEIGHT_4", db.activeOptions().get("M4_SPIRIT_MASK_WEIGHT"));
	}

	@Test
	void baseDataOptionsLeaveVendoredDataUntouched() {
		CatacombsLootDatabase db = LuckTestData.baseData();
		assertEquals(1, db.floor(FloorId.M4).chest(ChestTier.OBSIDIAN).entry("SPIRIT_MASK").weight());
		assertEquals(293, db.floor(FloorId.M4).topChest().totalWeight());
		assertEquals(0, db.floor(FloorId.M5).meterTotalWeightDelta());
	}

	@Test
	void alternativeOptionsPatchTheData() {
		CatacombsLootDatabase db = CatacombsLootDatabase.loadBundled(Map.of(
			"F7_OBSIDIAN_WITHER_SHARD", "ABSENT",
			"F2_M2_EMERALD_BASE_COST", "WIKI_250K"
		));
		assertNull(db.floor(FloorId.F7).chest(ChestTier.OBSIDIAN).entry("WITHER_SHARD"));
		assertEquals(250000, db.floor(FloorId.F2).chest(ChestTier.EMERALD).baseCost());
		assertEquals(250000, db.floor(FloorId.M2).chest(ChestTier.EMERALD).baseCost());
	}

	@Test
	void unknownDisputeOrOptionIsRejected() {
		assertThrows(IllegalArgumentException.class, () -> CatacombsLootDatabase.loadBundled(Map.of("NOPE", "X")));
		assertThrows(IllegalArgumentException.class, () -> CatacombsLootDatabase.loadBundled(Map.of("M4_SPIRIT_MASK_WEIGHT", "X")));
	}

	@Test
	void everyDisputeAndGapIsDocumented() {
		CatacombsLootDatabase db = LuckTestData.defaults();
		assertEquals(Set.of("M4_SPIRIT_MASK_WEIGHT", "M5_M7_METER_TOTAL_WEIGHT", "F7_OBSIDIAN_WITHER_SHARD", "F2_M2_EMERALD_BASE_COST"),
			Set.copyOf(db.disputes().stream().map(d -> d.id()).toList()));
		assertEquals(Set.of("DYES_NOT_MODELLED", "M7_GOLD_ESSENCE", "M7_DRAGON_SHARD_CHESTS", "MUSIC_DISCS_NOT_MODELLED"),
			Set.copyOf(db.knownGaps().stream().map(g -> g.id()).toList()));
		db.disputes().forEach(d -> assertFalse(d.evidence().isEmpty(), d.id()));
		assertEquals("693819170104faff843ce339b397d41d0890e25b", db.sourceCommit());
		assertEquals("catacombs-loot-v1", db.dataVersion());
	}

	@Test
	void hashIgnoresCarriageReturns() {
		String lf = CatacombsLootDatabase.sha256WithoutCarriageReturns("a\nb\n".getBytes(StandardCharsets.UTF_8));
		String crlf = CatacombsLootDatabase.sha256WithoutCarriageReturns("a\r\nb\r\n".getBytes(StandardCharsets.UTF_8));
		assertEquals(lf, crlf);
	}

	@Test
	void canonicalKeysMatchDrtLootIds() {
		assertEquals("ENCHANTMENT_ULTIMATE_WISDOM_2", LootEntry.enchantmentKey("WISDOM", 2));
		assertEquals("ENCHANTMENT_ULTIMATE_JERRY_1", LootEntry.enchantmentKey("ULTIMATE_JERRY", 1));
		assertEquals("ENCHANTMENT_REJUVENATE_3", LootEntry.enchantmentKey("REJUVENATE", 3));
		assertEquals("PET_SPIRIT_LEGENDARY", LootEntry.petKey("SPIRIT", "LEGENDARY"));
		assertEquals("ESSENCE_WITHER_10", LootEntry.essenceKey("WITHER", 10));
	}
}
