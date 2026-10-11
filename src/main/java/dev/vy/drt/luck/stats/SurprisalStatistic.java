package dev.vy.drt.luck.stats;

import dev.vy.drt.luck.sim.RunContext;
import dev.vy.drt.luck.sim.RunOutcome;
import dev.vy.drt.luck.sim.RunRarity;
import dev.vy.drt.luck.sim.RunSimulator;
import java.util.List;
import java.util.Set;

/**
 * Luck statistic: sum over runs of -ln(p) for every rolled item that appeared, where p is the
 * item's per-run appearance probability without meter boost. Higher means rarer drops. The
 * statistic only orders histories; its null distribution must come from simulating the same
 * history, which accounts for the meter and the exact runs played.
 *
 * <p>The appearance form does not depend on which chests were bought. The purchased form does,
 * so it is only meaningful against a null simulated with the same selection policy.
 */
public final class SurprisalStatistic {
	private SurprisalStatistic() {
	}

	public static double of(List<RunOutcome> outcomes, RunSimulator simulator) {
		double total = 0.0;
		for (RunOutcome outcome : outcomes) total += ofKeys(outcome.run(), outcome.appearedKeys(), simulator);
		return total;
	}

	public static double ofPurchased(List<RunOutcome> outcomes, RunSimulator simulator) {
		double total = 0.0;
		for (RunOutcome outcome : outcomes) total += ofKeys(outcome.run(), outcome.purchasedKeys(), simulator);
		return total;
	}

	public static double ofObserved(List<ObservedRun> runs, RunSimulator simulator) {
		double total = 0.0;
		for (ObservedRun run : runs) {
			if (!run.offersComplete()) throw new IllegalArgumentException("Run with incomplete offers cannot be scored by appearance");
			total += ofKeys(run.run(), run.appearedKeys(), simulator);
		}
		return total;
	}

	private static double ofKeys(RunContext run, Set<String> keys, RunSimulator simulator) {
		RunRarity rarity = simulator.rarity(run.floor(), run.scoreTier());
		double total = 0.0;
		for (String key : keys) {
			double p = rarity.perRun(key);
			if (p > 0.0 && p < 1.0) total -= Math.log(p);
		}
		return total;
	}
}
