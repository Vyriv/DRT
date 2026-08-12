package dev.vy.drt.tracking;

import java.time.Instant;

public interface TrackerClock {
	Instant wallTime();

	long monotonicNanos();
}
