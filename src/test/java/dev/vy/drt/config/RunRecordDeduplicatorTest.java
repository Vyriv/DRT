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

	private static DungeonRunRecord record(long timestamp, String floor, String chest, List<DungeonLootEntry> loot) {
		DungeonRunRecord record = new DungeonRunRecord(timestamp, 1, floor, "S+", chest, 0L, 1L, 1L, loot);
		record.normalizeCostBreakdown();
		return record;
	}
}
