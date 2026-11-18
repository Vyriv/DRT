package dev.vy.drt.history;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.vy.drt.config.ChestCostBreakdown;
import dev.vy.drt.config.DungeonChestOffer;
import dev.vy.drt.config.DungeonLootEntry;
import dev.vy.drt.config.DungeonRunCompletionRecord;
import dev.vy.drt.config.DungeonRunRecord;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class LootHistoryProjectionTest {
	@Test
	void sameRunKeepsEveryOpenedChestAsItsOwnListRow() {
		DungeonRunCompletionRecord completion = new DungeonRunCompletionRecord(
			1L, "DUNGEON", "M5", "S+", 180_000L, "live-run-5", "run|live-run-5");
		completion.completionId = "completion-5";
		completion.runTimeSource = DungeonRunCompletionRecord.RUN_TIME_OFFICIAL;
		completion.chestOffers.add(new DungeonChestOffer("Bedrock Chest", new ChestCostBreakdown(6_000_000L), 9_000_000L, List.of(
			new DungeonLootEntry("Recombobulator 3000", "RECOMBOBULATOR_3000", 1),
			new DungeonLootEntry("Fuming Potato Book", "FUMING_POTATO_BOOK", 1),
			new DungeonLootEntry("Wither Essence", "ESSENCE_WITHER", 86),
			new DungeonLootEntry("Undead Essence", "ESSENCE_UNDEAD", 103)
		)));
		completion.chestOffers.getFirst().purchased = true;
		completion.chestOffers.add(new DungeonChestOffer("Wood Chest", new ChestCostBreakdown(0L), 86_000L, List.of(
			new DungeonLootEntry("Undead Essence", "ESSENCE_UNDEAD", 22),
			new DungeonLootEntry("Wither Essence", "ESSENCE_WITHER", 27),
			new DungeonLootEntry("Enchanted Book (Feather Falling VI)", "ENCHANTMENT_FEATHER_FALLING_6", 1)
		)));
		completion.chestOffers.get(1).purchased = true;
		completion.chestOffers.add(new DungeonChestOffer("Gold Chest", new ChestCostBreakdown(100_000L), 130_000L, List.of(
			new DungeonLootEntry("Enchanted Book (Wisdom I)", "ENCHANTMENT_ULTIMATE_WISDOM_1", 1)
		)));

		DungeonRunRecord firstBedrock = chest(212, "Bedrock Chest", List.of(
			new DungeonLootEntry("Enchanted Book (Rejuvenate III)", "ENCHANTMENT_REJUVENATE_3", 1),
			new DungeonLootEntry("Wither Essence", "ESSENCE_WITHER", 81)
		));
		DungeonRunRecord secondBedrock = chest(215, "Bedrock Chest", List.of(
			new DungeonLootEntry("Wither Essence", "ESSENCE_WITHER", 86),
			new DungeonLootEntry("Undead Essence", "ESSENCE_UNDEAD", 103)
		));
		DungeonRunRecord wood = chest(216, "Wood Chest", List.of(
			new DungeonLootEntry("Enchanted Book (Feather Falling VI)", "ENCHANTMENT_FEATHER_FALLING_6", 1),
			new DungeonLootEntry("Wither Essence", "ESSENCE_WITHER", 27),
			new DungeonLootEntry("Undead Essence", "ESSENCE_UNDEAD", 22)
		));

		List<LootHistoryProjection.RunEntry> rows = LootHistoryProjection.projectRuns(
			List.of(firstBedrock, secondBedrock, wood), List.of(completion));

		assertEquals(3, rows.size(), "each purchased chest is its own left-list row");
		assertTrue(rows.stream().allMatch(row -> "completion:completion-5".equals(row.runKey())));
		assertTrue(rows.stream().anyMatch(row -> row.representative().chestNumber == 212));
		assertTrue(rows.stream().anyMatch(row -> row.representative().chestNumber == 215));
		assertTrue(rows.stream().anyMatch(row -> row.representative().chestNumber == 216));
		LootHistoryProjection.RunEntry woodRow = rows.stream()
			.filter(row -> row.representative().chestNumber == 216).findFirst().orElseThrow();
		assertTrue(woodRow.chestOptions().stream().anyMatch(option -> option.chestTitle().equals("Gold Chest") && !option.purchased()));
		assertEquals(1, woodRow.realisedRecords().size());
		assertEquals(woodRow.chestOptions().getFirst().optionId(), woodRow.option(woodRow.chestOptions().getFirst().optionId()).optionId());
	}

	@Test
	void linkedChestsExposeTheStoredCompletionDuration() {
		DungeonRunCompletionRecord run1 = completion("live-4e9f64eb-run-1", "completion-run-1", 105_000L);
		DungeonRunCompletionRecord run2 = completion("live-4e9f64eb-run-2", "completion-run-2", 99_000L);
		DungeonRunCompletionRecord run3 = completion("live-4e9f64eb-run-3", "completion-run-3", 81_000L);
		DungeonRunRecord emerald221 = linkedChest(221, "Emerald Chest", "live-4e9f64eb-run-1");
		DungeonRunRecord obsidian222 = linkedChest(222, "Obsidian Chest", "live-4e9f64eb-run-1");
		DungeonRunRecord emerald220 = linkedChest(220, "Emerald Chest", "live-4e9f64eb-run-2");
		DungeonRunRecord emerald219 = linkedChest(219, "Emerald Chest", "live-4e9f64eb-run-3");

		List<LootHistoryProjection.RunEntry> rows = LootHistoryProjection.projectRuns(
			List.of(emerald219, emerald220, emerald221, obsidian222),
			List.of(run1, run2, run3));
		Map<DungeonRunRecord, RunHistoryLinks.Link> links = RunHistoryLinks.resolve(
			List.of(emerald219, emerald220, emerald221, obsidian222),
			List.of(run1, run2, run3));

		assertEquals(4, rows.size());
		LootHistoryProjection.RunEntry row221 = rows.stream().filter(row -> row.representative().chestNumber == 221).findFirst().orElseThrow();
		LootHistoryProjection.RunEntry row222 = rows.stream().filter(row -> row.representative().chestNumber == 222).findFirst().orElseThrow();
		assertEquals(row221.runKey(), row222.runKey());
		assertEquals(105_000L, RunHistoryLinks.runTime(row221.completion()).millis());
		assertEquals(105_000L, RunHistoryLinks.runTime(row222.completion()).millis());
		assertEquals(99_000L, RunHistoryLinks.runTime(rows.stream().filter(row -> row.representative().chestNumber == 220).findFirst().orElseThrow().completion()).millis());
		assertEquals(81_000L, RunHistoryLinks.runTime(rows.stream().filter(row -> row.representative().chestNumber == 219).findFirst().orElseThrow().completion()).millis());
		assertTrue(links.get(emerald221).linked());
		assertTrue(links.get(obsidian222).linked());
		assertEquals("live-4e9f64eb-run-1", links.get(emerald221).runId());
		assertEquals("live-4e9f64eb-run-1", links.get(obsidian222).runId());
	}

	@Test
	void unlinkedChestsKeepNullCompletionForTimeUnavailable() {
		DungeonRunRecord orphan = new DungeonRunRecord(1L, 217, "M1", "S", "Emerald Chest", 500_000L, 1L, 1L, List.of());
		orphan.recordId = "record-orphan";
		orphan.runLinkSource = DungeonRunRecord.LINK_NONE;
		orphan.linkedRunId = "";
		List<LootHistoryProjection.RunEntry> rows = LootHistoryProjection.projectRuns(List.of(orphan), List.of());
		assertEquals(1, rows.size());
		assertNull(rows.getFirst().completion());
		assertNull(RunHistoryLinks.runTime(rows.getFirst().completion()));
	}

	@Test
	void croesusStoredLinksSurviveMultiplePurchasesOnOneRun() {
		DungeonRunCompletionRecord completion = completion("live-run-1", "completion-1", 105_000L);
		DungeonRunRecord emerald = linkedChest(221, "Emerald Chest", "live-run-1");
		DungeonRunRecord obsidian = linkedChest(222, "Obsidian Chest", "live-run-1");
		DungeonRunRecord bedrock = linkedChest(223, "Bedrock Chest", "live-run-1");
		Map<DungeonRunRecord, RunHistoryLinks.Link> links = RunHistoryLinks.resolve(
			List.of(emerald, obsidian, bedrock), List.of(completion));
		assertNotNull(RunHistoryLinks.runTime(links.get(emerald).completion()));
		assertTrue(links.get(emerald).linked());
		assertTrue(links.get(obsidian).linked());
		assertTrue(links.get(bedrock).linked());
	}

	private static DungeonRunCompletionRecord completion(String session, String completionId, long runTimeMs) {
		DungeonRunCompletionRecord completion = new DungeonRunCompletionRecord(
			1L, "DUNGEON", "M1", "S+", runTimeMs, session, "run|" + session);
		completion.completionId = completionId;
		completion.runTimeSource = DungeonRunCompletionRecord.RUN_TIME_OFFICIAL;
		return completion;
	}

	private static DungeonRunRecord linkedChest(int number, String title, String runId) {
		DungeonRunRecord record = chest(number, title, List.of(new DungeonLootEntry("Bonzo Shard", "SHARD_BONZO", 1)));
		record.linkedRunId = runId;
		record.runSessionId = runId;
		record.runLinkSource = DungeonRunRecord.LINK_CROESUS_UNIQUE;
		return record;
	}

	private static DungeonRunRecord chest(int number, String title, List<DungeonLootEntry> loot) {
		DungeonRunRecord record = new DungeonRunRecord(1_000L + number, number, "M5", "S+", title, 0L, 1L, 1L, loot);
		record.runSessionId = "live-run-5";
		record.linkedRunId = "live-run-5";
		record.runLinkSource = DungeonRunRecord.LINK_CROESUS_UNIQUE;
		record.chestNumber = number;
		record.recordId = "record-" + number;
		return record;
	}
}
