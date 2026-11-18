package dev.vy.drt.client.screen;

import dev.vy.drt.config.DungeonLootEntry;
import dev.vy.drt.config.DungeonRunCompletionRecord;
import dev.vy.drt.config.DungeonRunRecord;
import java.time.ZoneOffset;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LootChestExportTest {
	@Test
	void confirmedFreeChestDisplaysFreeInsteadOfHidingCost() {
		DungeonRunRecord record = record();
		record.applyCostBreakdown(new dev.vy.drt.config.ChestCostBreakdown(0L));
		record.chestProfitCoins = record.chestValueCoins;
		assertTrue(LootChestExport.shortSummary(record).contains("Cost: Free"));
	}

	@Test
	void formatsShortAndDetailedClipboardText() {
		DungeonRunRecord record = record();
		DungeonRunCompletionRecord completion = completion();
		String shortText = LootChestExport.shortSummary(record, completion);
		assertEquals("[DRT] M1 | Emerald Chest | S+ | 1m42s | Cost: 400k | Value: 749.7k | Profit: +349.7k (+87.4%)", shortText);

		String detailed = LootChestExport.detailed(record, completion, ZoneOffset.UTC,
			item -> item.itemId.equals("FIRST") ? 500_000L : 249_700L);
		assertTrue(detailed.startsWith("[DRT] M1 | Emerald Chest (S+ / 1m42s)\n5 Nov 2026, 11:53"));
		assertTrue(detailed.contains("Profit: +349.7k (+87.4%)"));
		assertTrue(detailed.contains("- First Item x1: 500k"));
		assertTrue(detailed.contains("- Second Item x2: 249.7k"));
		assertFalse(detailed.codePoints().anyMatch(codePoint -> codePoint == 0x2014 || codePoint == 0x2013));
	}

	@Test
	void omitsUnavailableFinancialAndItemData() {
		DungeonRunRecord record = record();
		record.financialsKnown = false;
		record.lootEntries = new ArrayList<>(List.of(new DungeonLootEntry("Unknown Value", "UNKNOWN_VALUE", 3)));
		String detailed = LootChestExport.detailed(record, 0L, ZoneOffset.UTC, item -> -1L);
		assertFalse(detailed.contains("Cost:"));
		assertTrue(detailed.contains("Items:\n- Unknown Value x3"));
		assertFalse(detailed.contains("Unknown Value x3:"));
	}

	@Test
	void omitsDurationWhenTheLinkedCompletionHasNoReliableTime() {
		DungeonRunCompletionRecord completion = completion();
		completion.runTimeSource = null;
		completion.completionFingerprint = "legacy";
		String text = LootChestExport.shortSummary(record(), completion);
		assertEquals("[DRT] M1 | Emerald Chest | S+ | Cost: 400k | Value: 749.7k | Profit: +349.7k (+87.4%)", text);
	}

	private static DungeonRunRecord record() {
		DungeonRunRecord record = new DungeonRunRecord(1_762_339_980_000L, 1, "M1", "S", "Emerald Chest",
			400_000L, 749_700L, 349_700L, List.of(
				new DungeonLootEntry("First Item", "FIRST", 1),
				new DungeonLootEntry("Second Item", "SECOND", 2)));
		record.financialsKnown = true;
		return record;
	}

	private static DungeonRunCompletionRecord completion() {
		DungeonRunCompletionRecord completion = new DungeonRunCompletionRecord(
			Instant.parse("2026-11-05T11:53:00Z").toEpochMilli(), "DUNGEON", "M1", "S+", 102_000L,
			"run-1", "run|M1|S+|102000");
		completion.runTimeSource = DungeonRunCompletionRecord.RUN_TIME_OFFICIAL;
		return completion;
	}
}
