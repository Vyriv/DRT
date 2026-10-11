package dev.vy.drt.luck.stats;

import dev.vy.drt.luck.sim.RunOutcome;
import dev.vy.drt.luck.sim.RunRarity;
import dev.vy.drt.luck.sim.RunSimulator;
import java.util.List;

/**
 * Luck statistic: sum over runs of -ln(p) for every rolled item that appeared, where p is the
 * item's per-run appearance probability without meter boost. Higher means rarer drops. The
 * statistic only orders histories; its null distribution must come from simulating the same
 * history, which accounts for the meter and the exact runs played.
 */
public final class SurprisalStatistic {
	private SurprisalStatistic() {
	}

	public static double of(List<RunOutcome> outcomes, RunSimulator simulator) {
		double total = 0.0;
		for (RunOutcome outcome : outcomes) {
			RunRarity rarity = simulator.rarity(outcome.run().floor(), outcome.run().scoreTier());
			for (String key : outcome.appearedKeys()) {
				double p = rarity.perRun(key);
				if (p > 0.0 && p < 1.0) total -= Math.log(p);
			}
		}
		return total;
	}
}
