package dev.vy.drt.client.screen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.vy.drt.config.DungeonLootEntry;
import dev.vy.drt.config.DungeonRunRecord;
import dev.vy.drt.config.DungeonRunCompletionRecord;
import dev.vy.drt.config.DungeonChestOffer;
import dev.vy.drt.config.ChestCostBreakdown;
import dev.vy.drt.history.LootHistoryProjection;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class LootHistorySearchIndexTest {
	@Test
	void filtersRunsAndKeepsFloorOrderIndependent() {
		DungeonRunRecord m5 = run("M5", "Shadow Assassin Chestplate", "SHADOW_ASSASSIN_CHESTPLATE");
		DungeonRunRecord m1 = run("M1", "Bonzo's Staff", "BONZO_STAFF");
		LootHistorySearchIndex index = new LootHistorySearchIndex(List.of(m5, m1), Map.of());

		LootHistorySearchIndex.Result result = index.search("bonzo staff");

		assertFalse(result.matches(m5));
		assertTrue(result.matches(m1));
		assertEquals(Set.of("M1"), result.matchingFloors());
		assertEquals("M1", result.firstMatchingFloor(List.of("F1", "M1", "M5")));
		assertEquals(null, result.firstMatchingFloor(List.of("F1", "M5")));
	}

	@Test
	void searchesUnselectedObservedChestsAndMarksOnlyMatchingChest() {
		DungeonRunRecord run = run("M1", "Hot Potato Book", "HOT_POTATO_BOOK");
		Map<DungeonRunRecord, List<LootHistorySearchIndex.Chest>> chests = new IdentityHashMap<>();
		chests.put(run, List.of(
			new LootHistorySearchIndex.Chest("WOOD", run.lootEntries),
			new LootHistorySearchIndex.Chest("BEDROCK", List.of(new DungeonLootEntry("§6Bonzo Shard", "SHARD_BONZO", 1)))
		));
		LootHistorySearchIndex.Result result = new LootHistorySearchIndex(List.of(run), chests).search("BONZO");

		assertTrue(result.matches(run));
		assertFalse(result.matchesChest(run, "WOOD"));
		assertTrue(result.matchesChest(run, "BEDROCK"));
	}

	@Test
	void projectedRunSearchFindsAndSelectsAnUnpurchasedOffer() {
		String runId = "run-194";
		DungeonRunCompletionRecord completion = new DungeonRunCompletionRecord(1L, "DUNGEON", "M1", "S", 131_000L,
			runId, "run|" + runId);
		completion.completionId = "completion-194";
		DungeonChestOffer wood = new DungeonChestOffer("Wood Chest", new ChestCostBreakdown(), 1L,
			List.of(new DungeonLootEntry("Enchanted Book (Combo I)", "ENCHANTMENT_COMBO_1", 1),
				new DungeonLootEntry("Wither Essence", "ESSENCE_WITHER", 10)));
		DungeonChestOffer obsidian = new DungeonChestOffer("Obsidian Chest", new ChestCostBreakdown(800_000L), 1L,
			List.of(new DungeonLootEntry("Recombobulator 3000", "RECOMBOBULATOR_3000", 1),
				new DungeonLootEntry("Undead Essence", "ESSENCE_UNDEAD", 30)));
		completion.chestOffers.add(wood);
		completion.chestOffers.add(obsidian);
		DungeonRunRecord purchased = run("M1", "Bonzo Shard", "SHARD_BONZO");
		purchased.chestTitle = "Emerald Chest";
		purchased.chestNumber = 194;
		purchased.runSessionId = runId;
		purchased.linkedRunId = runId;
		purchased.runLinkSource = DungeonRunRecord.LINK_CROESUS_UNIQUE;

		LootHistoryProjection.RunEntry projected = LootHistoryProjection.projectRuns(List.of(purchased), List.of(completion)).getFirst();

		assertTrue(LootHistorySearchIndex.matchesRun(projected, "recombobulator"));
		assertEquals("Obsidian Chest", LootHistorySearchIndex.firstMatchingChest(projected, "recombobulator"));
		assertEquals("Wood Chest", LootHistorySearchIndex.firstMatchingChest(projected, "enchanted book"));
		assertEquals("Emerald Chest", LootHistorySearchIndex.firstMatchingChest(projected, "bonzo shard"));
		assertEquals("Wood Chest", LootHistorySearchIndex.firstMatchingChest(projected, "essence"));
		assertEquals(2, projected.chestOptions().stream()
			.filter(option -> LootHistorySearchIndex.matchesChestOption(option, "essence")).count());
		assertFalse(LootHistorySearchIndex.matchesRun(projected, "necron handle"));
		assertFalse(LootHistorySearchIndex.matchesRun(projected, ""));
		assertEquals(null, LootHistorySearchIndex.firstMatchingChest(projected, ""));
		assertEquals("Emerald Chest", projected.defaultOption().chestTitle());
	}

	@Test
	void ordinarySearchExcludesOfferOnlyItemsAndExpandedSearchIncludesThem() {
		DungeonRunCompletionRecord completion = completion("run-scope");
		completion.chestOffers.add(new DungeonChestOffer("Obsidian Chest", new ChestCostBreakdown(800_000L), 2_000_000L,
			List.of(new DungeonLootEntry("Recombobulator 3000", "RECOMBOBULATOR_3000", 1))));
		DungeonRunRecord purchased = run("M1", "Recombobulator 3000", "RECOMBOBULATOR_3000");
		purchased.chestTitle = "Emerald Chest";
		purchased.runSessionId = "run-scope";
		purchased.linkedRunId = "run-scope";
		LootHistoryProjection.RunEntry projected = LootHistoryProjection.projectRuns(List.of(purchased), List.of(completion)).getFirst();

		assertEquals("Emerald Chest", LootHistorySearchIndex.firstMatchingChest(projected, "Recombobulator", false));
		assertTrue(LootHistorySearchIndex.matchesRun(projected, "Recombobulator", false));
		assertTrue(LootHistorySearchIndex.matchesRun(projected, "Recombobulator", true));

		purchased.lootEntries.clear();
		projected = LootHistoryProjection.projectRuns(List.of(purchased), List.of(completion)).getFirst();
		assertFalse(LootHistorySearchIndex.matchesRun(projected, "Recombobulator", false));
		assertTrue(LootHistorySearchIndex.matchesRun(projected, "Recombobulator", true));
	}

	@Test
	void normalizesFormattingPunctuationWhitespaceAndEnchantedBooks() {
		assertTrue(LootHistorySearchIndex.matchesItem("§dEnchanted Book (Legion I)", "ENCHANTMENT_ULTIMATE_LEGION_1", "legion"));
		assertTrue(LootHistorySearchIndex.matchesItem("Bonzo's   Staff", "BONZO_STAFF", "bonzo staff"));
		assertTrue(LootHistorySearchIndex.matchesItem("Wither Spectre Shard", "SHARD_WITHER_SPECTRE", "spectre"));
	}

	@Test
	void rebuiltIndexReflectsUpdatesAndEmptyQueryRestoresEverything() {
		DungeonRunRecord run = run("M1", "Hot Potato Book", "HOT_POTATO_BOOK");
		LootHistorySearchIndex before = new LootHistorySearchIndex(List.of(run), Map.of());
		assertFalse(before.search("bonzo").matches(run));
		run.lootEntries.add(new DungeonLootEntry("Bonzo's Staff", "BONZO_STAFF", 1));
		LootHistorySearchIndex after = new LootHistorySearchIndex(List.of(run), Map.of());
		assertTrue(after.search("bonzo").matches(run));
		assertTrue(after.search("").matches(run));
	}

	@Test
	void largeHistorySearchKeepsRecordedOrderAndCompletesPromptly() {
		List<DungeonRunRecord> runs = new java.util.ArrayList<>();
		for (int i = 0; i < 5_000; i++) {
			runs.add(run(i % 2 == 0 ? "M1" : "M5", i % 100 == 0 ? "Bonzo's Staff" : "Hot Potato Book",
				i % 100 == 0 ? "BONZO_STAFF" : "HOT_POTATO_BOOK"));
		}
		long started = System.nanoTime();
		LootHistorySearchIndex.Result result = new LootHistorySearchIndex(runs, Map.of()).search("bonzo staff");
		long elapsedMillis = (System.nanoTime() - started) / 1_000_000L;
		List<DungeonRunRecord> filtered = runs.stream().filter(result::matches).toList();
		assertEquals(50, filtered.size());
		assertEquals(runs.get(0), filtered.get(0));
		assertTrue(elapsedMillis < 2_000L, "index and query took " + elapsedMillis + " ms");
	}

	private static DungeonRunRecord run(String floor, String name, String id) {
		return new DungeonRunRecord(1L, 1, floor, "S+", "Bedrock Chest", 0L, 0L, 0L,
			List.of(new DungeonLootEntry(name, id, 1)));
	}

	private static DungeonRunCompletionRecord completion(String runId) {
		DungeonRunCompletionRecord completion = new DungeonRunCompletionRecord(1L, "DUNGEON", "M1", "S+", 100_000L,
			runId, "run|" + runId);
		completion.completionId = "completion-" + runId;
		return completion;
	}
}
