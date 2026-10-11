package dev.vy.drt.luck.meter;

import dev.vy.drt.luck.FloorId;

/**
 * Meter for one floor. Unknown state is represented by callers as a range of states, never as zero.
 *
 * @param selectedKey canonical key of the selected item, or null when nothing is selected
 * @param storedScore accumulated meter score
 */
public record RngMeterState(FloorId floor, String selectedKey, long storedScore) {
	public RngMeterState {
		if (floor == null) throw new IllegalArgumentException("Floor required");
		if (storedScore < 0) throw new IllegalArgumentException("Negative meter score");
	}

	public static RngMeterState empty(FloorId floor) {
		return new RngMeterState(floor, null, 0L);
	}

	public boolean hasSelection() {
		return selectedKey != null;
	}

	public RngMeterState withStoredScore(long score) {
		return new RngMeterState(floor, selectedKey, score);
	}
}
