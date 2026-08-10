package dev.vy.drt.tracking;

import java.time.Instant;

public final class FakeTrackerClock implements TrackerClock {
	private Instant wallTime;
	private long monotonicNanos;

	public FakeTrackerClock(Instant wallTime) {
		this.wallTime = wallTime == null ? Instant.EPOCH : wallTime;
		this.monotonicNanos = 0L;
	}

	@Override
	public Instant wallTime() {
		return wallTime;
	}

	@Override
	public long monotonicNanos() {
		return monotonicNanos;
	}

	public void advanceMillis(long millis) {
		long safeMillis = Math.max(0L, millis);
		wallTime = wallTime.plusMillis(safeMillis);
		monotonicNanos += safeMillis * 1_000_000L;
	}
}
