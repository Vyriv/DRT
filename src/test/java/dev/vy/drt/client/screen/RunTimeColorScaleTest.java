package dev.vy.drt.client.screen;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class RunTimeColorScaleTest {
	@Test
	void usesExactFastAndSlowBoundaries() {
		assertEquals(RunTimeColorScale.FAST, RunTimeColorScale.color("M7", 240_000L, 1));
		assertEquals(RunTimeColorScale.SLOW, RunTimeColorScale.color("M7", 360_000L, 1));
		assertEquals(RunTimeColorScale.FAST, RunTimeColorScale.color("F7", 210_000L, 1));
		assertEquals(RunTimeColorScale.SLOW, RunTimeColorScale.color("F7", 300_000L, 1));
	}

	@Test
	void interpolatesThroughYellowAndOrange() {
		assertEquals(RunTimeColorScale.MID, RunTimeColorScale.color("M5", 140_000L, 1));
		int orange = RunTimeColorScale.color("M5", 160_000L, 1);
		assertNotEquals(RunTimeColorScale.MID, orange);
		assertNotEquals(RunTimeColorScale.SLOW, orange);
	}

	@Test
	void leavesUnlistedFloorsUnchanged() {
		assertEquals(0xFF123456, RunTimeColorScale.color("F6", 1L, 0xFF123456));
		assertEquals(0xFF123456, RunTimeColorScale.color("UNKNOWN", 1L, 0xFF123456));
	}
}
