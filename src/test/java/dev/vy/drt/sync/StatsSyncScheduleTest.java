package dev.vy.drt.sync;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** Background sharing runs on its own: a minute between checks, quick batches for a backlog, backoff on failure. */
class StatsSyncScheduleTest {
	@Test
	void checksAgainEveryMinuteAndDrainsBacklogsInQuickBatches() {
		assertEquals(60_000L, StatsSyncSchedule.afterSuccess(false));
		assertEquals(2_000L, StatsSyncSchedule.afterSuccess(true));
		assertTrue(StatsSyncSchedule.BATCH_SIZE <= 100, "stays within the server's batch limit");
	}

	@Test
	void failuresBackOffToHalfAnHour() {
		assertEquals(60_000L, StatsSyncSchedule.afterFailure(0));
		assertEquals(300_000L, StatsSyncSchedule.afterFailure(1));
		assertEquals(900_000L, StatsSyncSchedule.afterFailure(2));
		assertEquals(1_800_000L, StatsSyncSchedule.afterFailure(3));
		assertEquals(1_800_000L, StatsSyncSchedule.afterFailure(40));
	}

	@Test
	void rateLimitsFollowRetryAfterWithinBounds() {
		assertEquals(60_000L, StatsSyncSchedule.afterRateLimit(null));
		assertEquals(10_000L, StatsSyncSchedule.afterRateLimit(1L));
		assertEquals(120_000L, StatsSyncSchedule.afterRateLimit(120L));
		assertEquals(3_600_000L, StatsSyncSchedule.afterRateLimit(999_999L));
	}
}
