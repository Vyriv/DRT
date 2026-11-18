package dev.vy.drt.history;

import dev.vy.drt.config.DungeonRunCompletionRecord;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CroesusMenuOrderRegressionTest {
	@Test void twelveConsecutiveM5RowsMapToTwelveDistinctCompletions() {
		long now = 2_000_000_000_000L;
		List<DungeonRunCompletionRecord> completions = new ArrayList<>();
		for (int run = 1; run <= 12; run++) {
			DungeonRunCompletionRecord completion = completion("run-" + run, now - (13L - run) * 120_000L);
			completions.add(completion);
		}
		List<CroesusRunSelection.Evidence> rows = new ArrayList<>();
		for (int row = 0; row < 12; row++) rows.add(evidence());

		for (int row = 0; row < rows.size(); row++) {
			RunOwnership.Resolution result = RunOwnership.resolveCroesusMenuOrder(rows, row, completions, now, "aabb");
			assertTrue(result.matched());
			assertEquals("run-" + (12 - row), result.owner().runSessionId());
		}
	}

	@Test void conflictingEvidenceRemainsUnassigned() {
		long now = 2_000_000_000_000L;
		DungeonRunCompletionRecord newest = completion("newest", now - 10_000L);
		newest.score = 301;
		DungeonRunCompletionRecord older = completion("older", now - 130_000L);
		older.score = 302;
		CroesusRunSelection.Evidence conflict = new CroesusRunSelection.Evidence("M5", "S+", 999, 0L, List.of());
		RunOwnership.Resolution result = RunOwnership.resolveCroesusMenuOrder(
			List.of(conflict, evidence()), 0, List.of(newest, older), now, "aabb");
		assertFalse(result.matched());
	}

	@Test void mixedFloorsAndUntrackedOlderRowsDoNotBlockSameFloorSelection() {
		long now = 2_000_000_000_000L;
		DungeonRunCompletionRecord newestCompletion = completion("m1-newest", now - 60_000L, "M1", "S+", 301);
		DungeonRunCompletionRecord midCompletion = completion("m1-mid", now - 180_000L, "M1", "S+", 300);
		DungeonRunCompletionRecord oldCompletion = completion("m1-old", now - 300_000L, "M1", "S", 276);
		List<DungeonRunCompletionRecord> completions = List.of(
			newestCompletion, midCompletion, oldCompletion, completion("m5-other", now - 90_000L, "M5", "S+", 0)
		);
		List<CroesusRunSelection.Evidence> rows = List.of(
			new CroesusRunSelection.Evidence("M1", "S+", 301, 0L, List.of()),
			new CroesusRunSelection.Evidence("M5", "S+", 0, 0L, List.of()),
			new CroesusRunSelection.Evidence("M1", "S+", 300, 0L, List.of()),
			new CroesusRunSelection.Evidence("M1", "S", 276, 0L, List.of()),
			new CroesusRunSelection.Evidence("M1", "", 0, 0L, List.of())
		);

		RunOwnership.Resolution newest = RunOwnership.resolveCroesusMenuOrder(rows, 0, completions, now, "aabb");
		RunOwnership.Resolution mid = RunOwnership.resolveCroesusMenuOrder(rows, 2, completions, now, "aabb");
		RunOwnership.Resolution oldestTracked = RunOwnership.resolveCroesusMenuOrder(rows, 3, completions, now, "aabb");
		RunOwnership.Resolution untracked = RunOwnership.resolveCroesusMenuOrder(rows, 4, completions, now, "aabb");

		assertTrue(newest.matched());
		assertEquals("m1-newest", newest.owner().runSessionId());
		assertTrue(mid.matched());
		assertEquals("m1-mid", mid.owner().runSessionId());
		assertTrue(oldestTracked.matched());
		assertEquals("m1-old", oldestTracked.owner().runSessionId());
		assertFalse(untracked.matched());
	}

	private static DungeonRunCompletionRecord completion(String id, long time, String floor, String grade, int score) {
		DungeonRunCompletionRecord value = new DungeonRunCompletionRecord(time, "DUNGEON", floor, grade, 100_000L, id, id);
		value.completionId = "completion-" + id;
		value.playerUuid = "aabb";
		value.score = score;
		value.scoreRecorded = true;
		return value;
	}

	private static DungeonRunCompletionRecord completion(String id, long time) {
		return completion(id, time, "M5", "S+", 0);
	}

	private static CroesusRunSelection.Evidence evidence() {
		return new CroesusRunSelection.Evidence("M5", "S+", 0, 0L, List.of());
	}
}
