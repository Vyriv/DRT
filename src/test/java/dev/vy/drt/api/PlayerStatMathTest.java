package dev.vy.drt.api;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class PlayerStatMathTest {
	@Test
	void derivesFromUnroundedLifetimeTotals() {
		assertEquals(10_001D / 333D, PlayerStatMath.secretsPerRun(10_001L, 333L));
	}

	@Test
	void missingOrZeroCompletionsRemainUnavailable() {
		assertNull(PlayerStatMath.secretsPerRun(null, 4L));
		assertNull(PlayerStatMath.secretsPerRun(10L, null));
		assertNull(PlayerStatMath.secretsPerRun(10L, 0L));
	}
}
