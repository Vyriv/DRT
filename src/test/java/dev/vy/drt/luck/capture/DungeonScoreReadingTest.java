package dev.vy.drt.luck.capture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class DungeonScoreReadingTest {
	@ParameterizedTest
	@CsvSource(delimiter = '|', value = {
		"Team Score: 305 (S+)|305|S+",
		"TEAM SCORE: 305 (S+)|305|S+",
		"Score: 306 (S+)|306|S+",
		"Team Score: 271 (S)|271|S",
		"Team Score: 230 (A)|230|A",
		"Team Score: 112 (D)|112|D",
		"   Team Score: 288 (s)  |288|S"
	})
	void readsNumericScoreTogetherWithGrade(String line, int score, String grade) {
		DungeonScoreReading reading = DungeonScoreReading.parse(line).orElseThrow();
		assertEquals(score, reading.score());
		assertEquals(grade, reading.grade());
	}

	@ParameterizedTest
	@ValueSource(strings = {
		"Team Score: (S+)",
		"Team Score: S+",
		"300 Score Reached!",
		"Party > [MVP+] someone: 300 Score!",
		"Odin > 300 score reached in 01m 29s || M5.",
		"A Bat has been slain. +1 Bonus Score",
		"Team Score: 999 (S+)",
		"Highscore: 305 (S+)",
		""
	})
	void neverInventsANumberFromAGradeOrUnrelatedLine(String line) {
		assertTrue(DungeonScoreReading.parse(line).isEmpty());
	}

	@org.junit.jupiter.api.Test
	void nullLineIsEmpty() {
		assertTrue(DungeonScoreReading.parse(null).isEmpty());
	}
}
