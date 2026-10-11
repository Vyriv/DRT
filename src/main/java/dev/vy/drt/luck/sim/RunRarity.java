package dev.vy.drt.luck.sim;

import dev.vy.drt.luck.ChestTier;
import dev.vy.drt.luck.engine.ExactChestDistribution;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;

/**
 * Exact item appearance probabilities for one run configuration without meter boost. Chests roll
 * independently, so the per-run probability is 1 - product of (1 - per-chest probability).
 */
public final class RunRarity {
	private final Map<ChestTier, ExactChestDistribution.Result> chests;
	private final Map<String, Double> perRun;

	public RunRarity(Map<ChestTier, ExactChestDistribution.Result> chests) {
		this.chests = Collections.unmodifiableMap(new EnumMap<>(chests));
		Map<String, Double> missing = new HashMap<>();
		for (ExactChestDistribution.Result result : chests.values()) {
			for (Map.Entry<String, Double> entry : result.appearance().entrySet()) {
				missing.merge(entry.getKey(), 1.0 - entry.getValue(), (a, b) -> a * b);
			}
		}
		Map<String, Double> perRun = new HashMap<>();
		missing.forEach((key, absent) -> perRun.put(key, 1.0 - absent));
		this.perRun = Collections.unmodifiableMap(perRun);
	}

	/** Probability that {@code key} appears in at least one offered chest. */
	public double perRun(String key) {
		return perRun.getOrDefault(key, 0.0);
	}

	public double inChest(ChestTier tier, String key) {
		ExactChestDistribution.Result result = chests.get(tier);
		return result == null ? 0.0 : result.probability(key);
	}

	public Map<String, Double> perRunProbabilities() {
		return perRun;
	}

	public Map<ChestTier, ExactChestDistribution.Result> chests() {
		return chests;
	}
}
