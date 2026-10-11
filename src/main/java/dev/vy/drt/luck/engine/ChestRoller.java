package dev.vy.drt.luck.engine;

import dev.vy.drt.luck.data.ChestTable;
import dev.vy.drt.luck.data.LootEntry;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.random.RandomGenerator;

/**
 * Samples one chest using the quality-budget algorithm: weighted picks among entries whose
 * quality fits the remaining budget, without replacement except the repeatable essence, until
 * the budget is spent or nothing fits. Leftover budget becomes filler essence, then guaranteed
 * entries are added.
 */
public final class ChestRoller {
	private ChestRoller() {
	}

	public static ChestRoll roll(ChestTable chest, int quality, MeterBoost boost, RandomGenerator random) {
		List<LootEntry> weighted = new ArrayList<>();
		LootEntry repeatable = null;
		LootEntry filler = null;
		List<LootEntry> guaranteed = new ArrayList<>();
		for (LootEntry entry : chest.entries()) {
			switch (entry.role()) {
				case WEIGHTED -> weighted.add(entry);
				case REPEATABLE_ESSENCE -> repeatable = entry;
				case FILLER_ESSENCE -> filler = entry;
				case GUARANTEED -> guaranteed.add(entry);
			}
		}

		Map<LootEntry, Integer> picks = new LinkedHashMap<>();
		int remaining = quality;
		boolean forced = false;
		if (boost.forced()) {
			LootEntry target = chest.entry(boost.entryKey());
			if (target == null || target.role() != LootEntry.Role.WEIGHTED) {
				throw new IllegalArgumentException("Forced meter item " + boost.entryKey() + " is not rollable in " + chest.tier());
			}
			picks.put(target, 1);
			weighted.remove(target);
			remaining -= target.quality();
			forced = true;
		}

		double[] weights = new double[weighted.size()];
		for (int i = 0; i < weighted.size(); i++) {
			LootEntry entry = weighted.get(i);
			weights[i] = boost.appliesTo(entry.key()) ? entry.weight() * boost.weightMultiplier() : entry.weight();
		}
		boolean[] taken = new boolean[weighted.size()];

		while (remaining > 0) {
			double total = 0.0;
			for (int i = 0; i < weights.length; i++) {
				if (!taken[i] && weighted.get(i).quality() <= remaining) total += weights[i];
			}
			boolean repeatableFits = repeatable != null && repeatable.quality() <= remaining;
			if (repeatableFits) total += repeatable.weight();
			if (total <= 0.0) break;

			double pick = random.nextDouble() * total;
			int chosen = -1;
			for (int i = 0; i < weights.length; i++) {
				if (taken[i] || weighted.get(i).quality() > remaining) continue;
				pick -= weights[i];
				if (pick < 0.0) {
					chosen = i;
					break;
				}
			}
			if (chosen >= 0) {
				taken[chosen] = true;
				picks.put(weighted.get(chosen), 1);
				remaining -= weighted.get(chosen).quality();
			} else if (repeatableFits) {
				picks.merge(repeatable, 1, Integer::sum);
				remaining -= repeatable.quality();
			} else {
				break;
			}
		}

		if (remaining > 0 && filler != null) picks.merge(filler, remaining, Integer::sum);
		for (LootEntry entry : guaranteed) picks.put(entry, 1);

		long cost = chest.baseCost();
		List<ChestRoll.Item> items = new ArrayList<>(picks.size());
		for (Map.Entry<LootEntry, Integer> pick : picks.entrySet()) {
			items.add(new ChestRoll.Item(pick.getKey(), pick.getValue()));
			cost += pick.getKey().addedChestCost();
		}
		return new ChestRoll(chest.tier(), quality, cost, items, forced);
	}
}
