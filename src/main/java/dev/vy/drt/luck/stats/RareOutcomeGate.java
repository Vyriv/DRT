package dev.vy.drt.luck.stats;

import dev.vy.drt.luck.sim.RunContext;
import dev.vy.drt.luck.sim.RunRarity;
import dev.vy.drt.luck.sim.RunSimulator;
import java.util.List;
import java.util.Map;
import java.util.function.IntFunction;

/**
 * Small-sample gate. With fewer than about one rare appearance expected, almost every history
 * ties at "nothing rare", so a percentile carries little information and is withheld.
 */
public final class RareOutcomeGate {
	public static final double DEFAULT_RARE_THRESHOLD = 0.01;
	public static final double DEFAULT_MINIMUM_EXPECTED = 1.0;

	private RareOutcomeGate() {
	}

	/** Expected number of appearances of items at or below {@code rareThreshold} per-run probability, without meter boost. */
	public static double expectedRareAppearances(List<RunContext> runs, RunSimulator simulator, double rareThreshold) {
		return expectedRareAppearances(runs, index -> simulator, rareThreshold);
	}

	/** As above with each run's probabilities from the simulator for its own modifiers. */
	public static double expectedRareAppearances(List<RunContext> runs, IntFunction<RunSimulator> simulatorFor, double rareThreshold) {
		double expected = 0.0;
		for (int i = 0; i < runs.size(); i++) {
			RunContext run = runs.get(i);
			RunRarity rarity = simulatorFor.apply(i).rarity(run.floor(), run.scoreTier());
			for (Map.Entry<String, Double> entry : rarity.perRunProbabilities().entrySet()) {
				double p = entry.getValue();
				if (p > 0.0 && p <= rareThreshold) expected += p;
			}
		}
		return expected;
	}

	public static boolean scorable(List<RunContext> runs, RunSimulator simulator) {
		return expectedRareAppearances(runs, simulator, DEFAULT_RARE_THRESHOLD) >= DEFAULT_MINIMUM_EXPECTED;
	}
}
