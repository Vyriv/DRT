package dev.vy.drt.luck.engine;

import dev.vy.drt.luck.data.ChestTable;
import dev.vy.drt.luck.data.LootEntry;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Exact appearance probabilities for one chest. States are (picked set, remaining quality);
 * every transition lowers the remaining quality, so processing states from the highest quality
 * down visits each state once, after all probability flowing into it has arrived.
 */
public final class ExactChestDistribution {
	private static final int MAX_WEIGHTED_ENTRIES = 63;

	private ExactChestDistribution() {
	}

	/**
	 * @param appearance                   probability that each entry is in the chest (guaranteed entries are 1)
	 * @param expectedRepeatableEssence    expected picks of the repeatable essence
	 * @param expectedFillerEssence        expected leftover quality converted to filler essence
	 */
	public record Result(Map<String, Double> appearance, double expectedRepeatableEssence, double expectedFillerEssence) {
		public double probability(String key) {
			return appearance.getOrDefault(key, 0.0);
		}
	}

	public static Result compute(ChestTable chest, int quality, MeterBoost boost) {
		List<LootEntry> weighted = new ArrayList<>();
		LootEntry repeatable = null;
		Map<String, Double> appearance = new LinkedHashMap<>();
		for (LootEntry entry : chest.entries()) {
			switch (entry.role()) {
				case WEIGHTED -> weighted.add(entry);
				case REPEATABLE_ESSENCE -> repeatable = entry;
				case GUARANTEED -> appearance.put(entry.key(), 1.0);
				case FILLER_ESSENCE -> { }
			}
		}
		if (weighted.size() > MAX_WEIGHTED_ENTRIES) throw new IllegalArgumentException("Too many weighted entries in " + chest.tier());

		int count = weighted.size();
		double[] weights = new double[count];
		int[] qualities = new int[count];
		for (int i = 0; i < count; i++) {
			LootEntry entry = weighted.get(i);
			weights[i] = boost.appliesTo(entry.key()) ? entry.weight() * boost.weightMultiplier() : entry.weight();
			qualities[i] = entry.quality();
		}
		double[] appeared = new double[count];

		long startMask = 0L;
		int start = quality;
		if (boost.forced()) {
			int index = -1;
			for (int i = 0; i < count; i++) {
				if (weighted.get(i).key().equals(boost.entryKey())) index = i;
			}
			if (index < 0) throw new IllegalArgumentException("Forced meter item " + boost.entryKey() + " is not rollable in " + chest.tier());
			startMask = 1L << index;
			start -= qualities[index];
			appeared[index] = 1.0;
		}

		double repeatableWeight = repeatable == null ? 0.0 : repeatable.weight();
		int repeatableQuality = repeatable == null ? Integer.MAX_VALUE : repeatable.quality();
		double expectedRepeatable = 0.0;
		double expectedFiller = 0.0;

		if (start > 0) {
			@SuppressWarnings("unchecked")
			HashMap<Long, Double>[] byQuality = new HashMap[start + 1];
			byQuality[start] = new HashMap<>();
			byQuality[start].put(startMask, 1.0);
			for (int q = start; q > 0; q--) {
				HashMap<Long, Double> states = byQuality[q];
				if (states == null) continue;
				byQuality[q] = null;
				for (Map.Entry<Long, Double> state : states.entrySet()) {
					long mask = state.getKey();
					double probability = state.getValue();
					double total = 0.0;
					for (int i = 0; i < count; i++) {
						if ((mask & (1L << i)) == 0 && qualities[i] <= q) total += weights[i];
					}
					boolean repeatableFits = repeatableQuality <= q;
					if (repeatableFits) total += repeatableWeight;
					if (total <= 0.0) {
						expectedFiller += probability * q;
						continue;
					}
					for (int i = 0; i < count; i++) {
						if ((mask & (1L << i)) != 0 || qualities[i] > q) continue;
						double share = probability * weights[i] / total;
						appeared[i] += share;
						int next = q - qualities[i];
						if (next > 0) addState(byQuality, next, mask | (1L << i), share);
					}
					if (repeatableFits && repeatableWeight > 0.0) {
						double share = probability * repeatableWeight / total;
						expectedRepeatable += share;
						int next = q - repeatableQuality;
						if (next > 0) addState(byQuality, next, mask, share);
					}
				}
			}
		}

		for (int i = 0; i < count; i++) appearance.put(weighted.get(i).key(), appeared[i]);
		return new Result(Collections.unmodifiableMap(appearance), expectedRepeatable, expectedFiller);
	}

	private static void addState(HashMap<Long, Double>[] byQuality, int quality, long mask, double probability) {
		HashMap<Long, Double> states = byQuality[quality];
		if (states == null) {
			states = new HashMap<>();
			byQuality[quality] = states;
		}
		states.merge(mask, probability, Double::sum);
	}
}
