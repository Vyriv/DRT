package dev.vy.drt.config;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class RunRecordDeduplicatorTest {
	@Test
	void duplicateHistoryKeepsExistingWithoutAppendDecision() {
		DungeonRunRecord existing = record(1_000L, "M7", "Bedrock Chest", List.of(
			new DungeonLootEntry("Wither Essence", "ESSENCE_WITHER", 100)
		));
		DungeonRunRecord incoming = existing.copy();

		RunRecordDeduplicator.DuplicateDecision decision = RunRecordDeduplicator.decide(new ArrayList<>(List.of(existing)), incoming);

		assertEquals(RunRecordCommitDecision.KEEP_EXISTING, decision.action());
	}

	@Test
	void partialRecordCanBeReplacedByMoreCompleteDuplicate() {
		DungeonRunRecord partial = record(1_000L, "M7", "Bedrock Chest", List.of(
			new DungeonLootEntry("Wither Essence", "ESSENCE_WITHER", 100)
		));
		DungeonRunRecord complete = record(1_000L, "M7", "Bedrock Chest", List.of(
			new DungeonLootEntry("Wither Essence", "ESSENCE_WITHER", 100),
			new DungeonLootEntry("Necron's Handle", "NECRON_HANDLE", 1)
		));
		partial.chestSessionId = "chest-a";
		complete.chestSessionId = "chest-a";

		RunRecordDeduplicator.DuplicateDecision decision = RunRecordDeduplicator.decide(new ArrayList<>(List.of(partial)), complete);

		assertEquals(RunRecordCommitDecision.REPLACE_EXISTING, decision.action());
	}

	@Test
	void uncertainSameSessionDifferenceReportsConflict() {
		DungeonRunRecord left = record(1_000L, "M7", "Bedrock Chest", List.of(
			new DungeonLootEntry("Wither Essence", "ESSENCE_WITHER", 100)
		));
		DungeonRunRecord right = record(1_000L, "M7", "Bedrock Chest", List.of(
			new DungeonLootEntry("Wither Essence", "ESSENCE_WITHER", 120)
		));
		left.chestSessionId = "chest-a";
		right.chestSessionId = "chest-a";

		RunRecordDeduplicator.DuplicateDecision decision = RunRecordDeduplicator.decide(new ArrayList<>(List.of(left)), right);

		assertEquals(RunRecordCommitDecision.CONFLICT, decision.action());
	}

	@Test
	void sameRunSameChestLootKeepsExistingWithoutAppend() {
		DungeonRunRecord existing = record(1_000L, "M7", "Bedrock Chest", List.of(
			new DungeonLootEntry("Necron's Handle", "NECRON_HANDLE", 1)
		));
		existing.runSessionId = "run-12";
		existing.runNumber = 12;
		DungeonRunRecord incoming = record(900_000L, "M7", "Bedrock Chest", List.of(
			new DungeonLootEntry("Necron's Handle", "NECRON_HANDLE", 1)
		));
		incoming.runSessionId = "run-12";
		incoming.runNumber = 12;
		incoming.chestSessionId = "run-12-chest-99";

		RunRecordDeduplicator.DuplicateDecision decision = RunRecordDeduplicator.decide(new ArrayList<>(List.of(existing)), incoming);

		assertEquals(RunRecordCommitDecision.KEEP_EXISTING, decision.action());
	}

	@Test
	void sameRunSameTierDifferentChestSessionsStillAddsSecondRecord() {
		DungeonRunRecord firstWood = record(1_000L, "F7", "Wood Chest", List.of(
			new DungeonLootEntry("Undead Essence", "ESSENCE_UNDEAD", 27),
			new DungeonLootEntry("Wither Essence", "ESSENCE_WITHER", 20),
			new DungeonLootEntry("Enchanted Book (Infinite Quiver VI)", "ENCHANTMENT_INFINITE_QUIVER_6", 1)
		));
		firstWood.runSessionId = "live-e8acc531-run-13";
		firstWood.runNumber = 18;
		firstWood.chestSessionId = "live-e8acc531-chest-9";

		DungeonRunRecord secondWood = record(60_000L, "F7", "Wood Chest", List.of(
			new DungeonLootEntry("Undead Essence", "ESSENCE_UNDEAD", 27),
			new DungeonLootEntry("Wither Essence", "ESSENCE_WITHER", 20),
			new DungeonLootEntry("Enchanted Book (Infinite Quiver VI)", "ENCHANTMENT_INFINITE_QUIVER_6", 1),
			new DungeonLootEntry("Maxor the Fish", "MAXOR_THE_FISH", 1)
		));
		secondWood.runSessionId = "live-e8acc531-run-13";
		secondWood.runNumber = 18;
		secondWood.chestSessionId = "live-e8acc531-chest-15";

		RunRecordDeduplicator.DuplicateDecision decision = RunRecordDeduplicator.decide(new ArrayList<>(List.of(firstWood)), secondWood);

		assertEquals(RunRecordCommitDecision.ADD_INCOMING, decision.action());
	}

	@Test
	void sameRunIdenticalLootDifferentChestSessionsStillAddsSecondRecord() {
		DungeonRunRecord firstWood = record(1_000L, "F7", "Wood Chest", List.of(
			new DungeonLootEntry("Undead Essence", "ESSENCE_UNDEAD", 27),
			new DungeonLootEntry("Wither Essence", "ESSENCE_WITHER", 20),
			new DungeonLootEntry("Enchanted Book (Infinite Quiver VI)", "ENCHANTMENT_INFINITE_QUIVER_6", 1)
		));
		firstWood.runSessionId = "live-e8acc531-run-13";
		firstWood.runNumber = 18;
		firstWood.chestSessionId = "live-e8acc531-chest-9";

		DungeonRunRecord secondWood = firstWood.copy();
		secondWood.timestampEpochMillis = 60_000L;
		secondWood.chestSessionId = "live-e8acc531-chest-15";

		RunRecordDeduplicator.DuplicateDecision decision = RunRecordDeduplicator.decide(new ArrayList<>(List.of(firstWood)), secondWood);

		assertEquals(RunRecordCommitDecision.ADD_INCOMING, decision.action());
	}

	@Test
	void immediateSameRunIdenticalLootWithAccidentallyNewChestSessionKeepsExisting() {
		DungeonRunRecord first = record(1_000L, "M7", "Bedrock Chest", List.of(
			new DungeonLootEntry("Wither Essence", "ESSENCE_WITHER", 100),
			new DungeonLootEntry("Necron's Handle", "NECRON_HANDLE", 1)
		));
		first.runSessionId = "run-12";
		first.runNumber = 12;
		first.chestSessionId = "run-12-chest-9";

		DungeonRunRecord duplicate = first.copy();
		duplicate.timestampEpochMillis = 2_000L;
		duplicate.chestSessionId = "run-12-chest-10";

		RunRecordDeduplicator.DuplicateDecision decision = RunRecordDeduplicator.decide(new ArrayList<>(List.of(first)), duplicate);

		assertEquals(RunRecordCommitDecision.KEEP_EXISTING, decision.action());
	}

	@Test
	void sameLootDifferentRunsStillAddsSecondRecord() {
		DungeonRunRecord firstRun = record(1_000L, "F7", "Bedrock Chest", List.of(
			new DungeonLootEntry("Wither Essence", "ESSENCE_WITHER", 100)
		));
		firstRun.runSessionId = "run-a";
		firstRun.runNumber = 1;
		DungeonRunRecord secondRun = record(60_000L, "F7", "Bedrock Chest", List.of(
			new DungeonLootEntry("Wither Essence", "ESSENCE_WITHER", 100)
		));
		secondRun.runSessionId = "run-b";
		secondRun.runNumber = 2;

		RunRecordDeduplicator.DuplicateDecision decision = RunRecordDeduplicator.decide(new ArrayList<>(List.of(firstRun)), secondRun);

		assertEquals(RunRecordCommitDecision.ADD_INCOMING, decision.action());
	}

	private static DungeonRunRecord record(long timestamp, String floor, String chest, List<DungeonLootEntry> loot) {
		DungeonRunRecord record = new DungeonRunRecord(timestamp, 1, floor, "S+", chest, 0L, 1L, 1L, loot);
		record.normalizeCostBreakdown();
		return record;
	}
}
