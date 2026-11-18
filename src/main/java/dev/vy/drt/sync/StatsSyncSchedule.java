package dev.vy.drt.sync;

/**
 * When the background stats sync acts next. History is checked locally once a minute and a
 * request is only made when something is waiting; backlogs go out in short-spaced batches and
 * failures back off up to half an hour.
 */
public final class StatsSyncSchedule {
	/** Records per kind (runs, chests, retractions) in one request; the server accepts up to 100. */
	public static final int BATCH_SIZE = 50;
	public static final long SCAN_INTERVAL_MS = 60_000L;
	public static final long NEXT_BATCH_DELAY_MS = 2_000L;
	private static final long[] RETRY_DELAYS_MS = {60_000L, 5L * 60_000L, 15L * 60_000L, 30L * 60_000L};

	private StatsSyncSchedule() {
	}

	public static long afterSuccess(boolean morePending) {
		return morePending ? NEXT_BATCH_DELAY_MS : SCAN_INTERVAL_MS;
	}

	/** @param previousFailures failures in a row before this one */
	public static long afterFailure(int previousFailures) {
		return RETRY_DELAYS_MS[Math.max(0, Math.min(previousFailures, RETRY_DELAYS_MS.length - 1))];
	}

	/** Honours the server's Retry-After, kept between 10 seconds and an hour; a minute when it is missing. */
	public static long afterRateLimit(Long retryAfterSeconds) {
		long seconds = retryAfterSeconds == null ? 60L : retryAfterSeconds;
		return Math.max(10L, Math.min(3600L, seconds)) * 1000L;
	}
}
