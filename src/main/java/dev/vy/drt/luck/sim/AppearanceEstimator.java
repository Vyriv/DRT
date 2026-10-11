package dev.vy.drt.luck.sim;

import dev.vy.drt.luck.meter.RngMeterState;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.SplittableRandom;

/**
 * Monte Carlo estimates of item appearance versus purchase for one run configuration. Appearance
 * is what luck is judged on; purchase depends on the selection policy.
 */
public final class AppearanceEstimator {
	private AppearanceEstimator() {
	}

	public record Estimate(long appeared, long purchased, long trials) {
		public double appearanceProbability() {
			return (double) appeared / trials;
		}

		public double purchaseProbability() {
			return (double) purchased / trials;
		}

		/** Binomial standard error of the appearance estimate. */
		public double appearanceStandardError() {
			double p = appearanceProbability();
			return Math.sqrt(p * (1.0 - p) / trials);
		}

		public double purchaseStandardError() {
			double p = purchaseProbability();
			return Math.sqrt(p * (1.0 - p) / trials);
		}
	}

	public record Result(Map<String, Estimate> items, long trials, double meanChestOpportunities) {
		public Estimate item(String key) {
			return items.getOrDefault(key, new Estimate(0, 0, trials));
		}
	}

	/** Each trial starts from {@code meter}; the meter does not carry between trials. */
	public static Result estimate(
		RunSimulator simulator,
		RunContext run,
		RngMeterState meter,
		SelectionPolicy selection,
		KismetPolicy kismet,
		int trials,
		long seed
	) {
		SplittableRandom random = new SplittableRandom(seed);
		Map<String, long[]> counts = new HashMap<>();
		long opportunities = 0;
		for (int i = 0; i < trials; i++) {
			RunOutcome outcome = simulator.simulate(run, meter, selection, kismet, random);
			opportunities += outcome.chestOpportunities();
			for (String key : outcome.appearedKeys()) counts.computeIfAbsent(key, ignored -> new long[2])[0]++;
			for (String key : outcome.purchasedKeys()) counts.computeIfAbsent(key, ignored -> new long[2])[1]++;
		}
		Map<String, Estimate> items = new HashMap<>();
		counts.forEach((key, value) -> items.put(key, new Estimate(value[0], value[1], trials)));
		return new Result(Collections.unmodifiableMap(items), trials, (double) opportunities / trials);
	}
}
