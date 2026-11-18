package dev.vy.drt.history;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.vy.drt.config.DungeonRunCompletionRecord;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;

class CroesusRunSelectionTest {
	private static final ZoneId UTC = ZoneId.of("UTC");

	@Test
	void relativeCompletionTimeSelectsOneHistoricalRun() {
		long now = 1_800_000_000_000L;
		DungeonRunCompletionRecord selected = completion("run-m1-old", now - 125_000L, "M1", "S", 178_000L);
		DungeonRunCompletionRecord newer = completion("run-m1-new", now - 25_000L, "M1", "S", 190_000L);
		CroesusRunSelection.Evidence evidence = CroesusRunSelection.parse(
			"Master Mode Catacombs - Floor I",
			List.of("Completed: 2 minutes ago", "Grade: S", "Time: 02m 58s"),
			now,
			UTC
		);

		CroesusRunSelection.Match match = CroesusRunSelection.resolve(evidence, List.of(newer, selected));

		assertEquals(CroesusRunSelection.Status.MATCHED, match.status());
		assertEquals("run-m1-old", match.runSessionId());
	}

	@Test
	void ambiguousHistoricalRunIsNeverGuessed() {
		long now = 1_800_000_000_000L;
		CroesusRunSelection.Evidence evidence = CroesusRunSelection.parse(
			"The Catacombs - Floor VII",
			List.of("Completed: 2 hours ago"),
			now,
			UTC
		);
		CroesusRunSelection.Match match = CroesusRunSelection.resolve(evidence, List.of(
			completion("run-a", now - 2L * 3_600_000L - 60_000L, "F7", "S+", 300_000L),
			completion("run-b", now - 2L * 3_600_000L - 120_000L, "F7", "S+", 310_000L)
		));

		assertEquals(CroesusRunSelection.Status.AMBIGUOUS, match.status());
		assertNull(match.completion());
	}

	@Test
	void exactHistoricalDateCanBackfillAnOlderRun() {
		long completed = ZonedDateTime.of(2026, 10, 18, 9, 32, 20, 0, UTC).toInstant().toEpochMilli();
		CroesusRunSelection.Evidence evidence = CroesusRunSelection.parse(
			"Master Mode Catacombs - Floor I",
			List.of("Completed: Oct 18, 2026 09:32", "Grade: S"),
			completed + 10_000_000L,
			UTC
		);
		CroesusRunSelection.Match match = CroesusRunSelection.resolve(evidence, List.of(
			completion("old-m1", completed, "M1", "S", 178_000L),
			completion("other-m1", completed + 3_600_000L, "M1", "S", 181_000L)
		));

		assertEquals(CroesusRunSelection.Status.MATCHED, match.status());
		assertEquals("old-m1", match.runSessionId());
	}

	@Test
	void relativeCompletionTimeAllowsHypixelMinuteRounding() {
		long now = 1_800_000_000_000L;
		long completedAt = now - (12L * 60_000L + 45_000L);
		DungeonRunCompletionRecord selected = completion("run-m1", completedAt, "M1", "S", 97_000L);
		CroesusRunSelection.Evidence evidence = CroesusRunSelection.parse(
			"Master Mode Catacombs - Floor I",
			List.of("Completed: 12 minutes ago", "Grade: S", "Time: 01m 37s"),
			now,
			UTC
		);

		assertEquals(1, evidence.completionWindows().size());
		assertTrue(evidence.completionWindows().getFirst().contains(completedAt));
		CroesusRunSelection.Match match = CroesusRunSelection.resolve(evidence, List.of(selected));

		assertEquals(CroesusRunSelection.Status.MATCHED, match.status());
		assertEquals("run-m1", match.runSessionId());
	}

	@Test
	void floorWithoutTimeOrDurationIsInsufficient() {
		CroesusRunSelection.Evidence evidence = CroesusRunSelection.parse(
			"Master Mode Catacombs - Floor I",
			List.of("Grade: S"),
			1_800_000_000_000L,
			UTC
		);
		CroesusRunSelection.Match match = CroesusRunSelection.resolve(evidence, List.of(
			completion("only-run", 1_799_000_000_000L, "M1", "S", 178_000L)
		));

		assertEquals(CroesusRunSelection.Status.INSUFFICIENT_EVIDENCE, match.status());
	}

	private static DungeonRunCompletionRecord completion(String session, long at, String floor, String grade, long duration) {
		return new DungeonRunCompletionRecord(at, "DUNGEON", floor, grade, duration, session, session + "|" + floor + "|" + grade + "|" + duration);
	}
}
