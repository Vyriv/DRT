package dev.vy.drt.luck.engine;

/**
 * RNG Meter effect on one chest roll.
 *
 * @param weightMultiplier multiplier applied to the selected entry's weight (1 to 3)
 * @param forced           whether the entry is placed first and its quality spent (full meter, guarantee chest)
 */
public record MeterBoost(String entryKey, double weightMultiplier, boolean forced) {
	public static final MeterBoost NONE = new MeterBoost(null, 1.0, false);

	public MeterBoost {
		if (weightMultiplier < 1.0 || weightMultiplier > 3.0) throw new IllegalArgumentException("Multiplier out of range: " + weightMultiplier);
		if (entryKey == null && (weightMultiplier != 1.0 || forced)) throw new IllegalArgumentException("Boost without an entry");
	}

	public boolean appliesTo(String key) {
		return entryKey != null && entryKey.equals(key);
	}

	public MeterBoost withoutForce() {
		return forced ? new MeterBoost(entryKey, weightMultiplier, false) : this;
	}
}
