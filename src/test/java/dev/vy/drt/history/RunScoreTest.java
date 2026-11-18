package dev.vy.drt.history;

import dev.vy.drt.config.DungeonRunCompletionRecord;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RunScoreTest {
	@Test
	void infersEveryGradeMinimumWithoutMutatingHistory() {
		List<String> grades = List.of("S+", "S", "A", "B", "C", "D");
		List<Integer> expected = List.of(300, 270, 230, 160, 100, 0);
		for (int i = 0; i < grades.size(); i++) {
			DungeonRunCompletionRecord run = run(grades.get(i), 0, null);
			RunScore.Resolved score = RunScore.resolve(run);
			assertEquals(RunScore.Source.INFERRED, score.source());
			assertEquals(expected.get(i), score.value());
			assertEquals(expected.get(i) + "*", score.display());
			assertEquals(0, run.score);
			assertEquals(null, run.scoreRecorded);
		}
	}

	@Test
	void actualScoresAlwaysWinIncludingGenuineZero() {
		assertEquals("275", RunScore.resolve(run("S", 275, null)).display());
		assertEquals("312", RunScore.resolve(run("S+", 312, true)).display());
		assertEquals("0", RunScore.resolve(run("D", 0, true)).display());
	}

	@Test
	void missingScoreAndGradeRemainUnknown() {
		assertEquals(RunScore.Source.UNKNOWN, RunScore.resolve(run("?", 0, null)).source());
		assertEquals("Unknown", RunScore.resolve(run("", 0, null)).display());
	}

	@Test
	void analyticsUseOnlyActualScoresAndCountInferredSamples() {
		DungeonRunCompletionRecord inferred = run("S+", 0, null);
		DungeonRunCompletionRecord actual = run("S", 275, null);
		DungeonRunCompletionRecord zero = run("D", 0, true);
		RunsDashboardData.Metrics metrics = RunsDashboardData.build(List.of(inferred, actual, zero), List.of(), "", "",
			RunsDashboardData.Sort.NEWEST).metrics();
		assertEquals(2, metrics.scoredRuns());
		assertEquals(1, metrics.inferredScoreRuns());
		assertEquals(137.5, metrics.averageScore());
		assertEquals(275, metrics.bestScore());
	}

	private static DungeonRunCompletionRecord run(String grade, int score, Boolean recorded) {
		DungeonRunCompletionRecord run = new DungeonRunCompletionRecord(1_700_000_000_000L + score, "DUNGEON", "F7", grade,
			600_000L, "session-" + grade + score + recorded, "fingerprint-" + grade + score + recorded);
		run.completionId = "completion-" + grade + score + recorded;
		run.score = score;
		run.scoreRecorded = recorded;
		return run;
	}
}
