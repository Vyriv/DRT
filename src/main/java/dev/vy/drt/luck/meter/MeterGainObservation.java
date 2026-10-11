package dev.vy.drt.luck.meter;

import dev.vy.drt.luck.FloorId;
import dev.vy.drt.luck.ScoreTier;

/**
 * Stored meter score read before and after one completed run, with no selected drop that could
 * have reset it in between.
 *
 * @param source where the readings came from, for reports only
 */
public record MeterGainObservation(String source, FloorId floor, int score, int pityLevel, long before, long after) {
	public MeterGainObservation {
		if (floor == null) throw new IllegalArgumentException("floor required");
		if (pityLevel < 0) throw new IllegalArgumentException("pityLevel must be >= 0");
		if (after < before) throw new IllegalArgumentException("meter fell from " + before + " to " + after + " without a reset");
	}

	public ScoreTier tier() {
		return ScoreTier.fromScore(score);
	}

	public long gain() {
		return after - before;
	}

	/** Same readings regardless of where they were recorded. */
	public boolean sameReadings(MeterGainObservation other) {
		return floor == other.floor && score == other.score && pityLevel == other.pityLevel && before == other.before && after == other.after;
	}
}
