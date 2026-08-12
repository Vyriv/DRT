package dev.vy.drt.tracking;

import java.time.Instant;

public final class SystemTrackerClock implements TrackerClock {
	@Override
	public Instant wallTime() {
		return Instant.now();
	}

	@Override
	public long monotonicNanos() {
		return System.nanoTime();
	}
}
