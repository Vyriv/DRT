package dev.vy.drt.client.tracker;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

class DungeonRunCompletionParsingTest {
	@Test
	void parsesOfficialExtraStatsDuration() {
		assertEquals(178_000L, DungeonRunTrackerFeature.parseOfficialDungeonRunTimeMillis("Defeated Bonzo in 02m 58s"));
	}

	@Test
	void rejectsNonCompletionAndInvalidSeconds() {
		assertNull(DungeonRunTrackerFeature.parseOfficialDungeonRunTimeMillis("Bonzo was defeated"));
		assertNull(DungeonRunTrackerFeature.parseOfficialDungeonRunTimeMillis("Defeated Bonzo in 02m 99s"));
	}
}
