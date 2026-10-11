package dev.vy.drt.luck.capture;

import java.util.Objects;

/**
 * One RNG Meter reading for a floor, as printed in game. A reading equal to the previous one for
 * the floor, with no captured run in between, only moves {@code lastObservedAtEpochMillis}.
 */
public final class LuckMeterReading {
	public static final String SOURCE_OVERVIEW = "RNG_METER_OVERVIEW";
	public static final String SOURCE_FLOOR_MENU = "RNG_METER_FLOOR_MENU";

	public String floor = "";
	public long observedAtEpochMillis;
	public long lastObservedAtEpochMillis;
	public String source = "";
	/** Display name of the selected drop; null when nothing is selected. */
	public String selectedItem;
	public String selectedKey;
	/** Exact stored score; null when the game printed it abbreviated. */
	public Long storedScore;
	public String storedText;
	/** Exact requirement, from this screen or a previously read floor screen; null otherwise. */
	public Long requiredScore;
	public String requiredText;
	public String requiredSource;
	public Double progressPercent;
	public String guaranteeChest;

	public boolean sameState(LuckMeterReading other) {
		return other != null
			&& floor.equals(other.floor)
			&& Objects.equals(selectedItem, other.selectedItem)
			&& Objects.equals(storedScore, other.storedScore)
			&& Objects.equals(storedText, other.storedText);
	}

	void normalize() {
		if (floor == null) floor = "";
		if (source == null) source = "";
		observedAtEpochMillis = Math.max(0L, observedAtEpochMillis);
		lastObservedAtEpochMillis = Math.max(observedAtEpochMillis, lastObservedAtEpochMillis);
		if (storedScore != null && storedScore < 0) storedScore = null;
		if (requiredScore != null && requiredScore <= 0) requiredScore = null;
	}
}
