package dev.vy.drt.luck.stats;

import dev.vy.drt.luck.meter.RngMeterState;
import dev.vy.drt.luck.sim.HistorySimulator;
import dev.vy.drt.luck.sim.KismetPolicy;
import dev.vy.drt.luck.sim.RunContext;
import dev.vy.drt.luck.sim.RunSimulator;
import dev.vy.drt.luck.sim.SelectionPolicy;
import java.util.List;
import java.util.SplittableRandom;

/** Surprisal of many simulated alternative histories with the same runs, meter start and policies. */
public final class NullDistribution {
	private NullDistribution() {
	}

	public static double[] sample(
		RunSimulator simulator,
		List<RunContext> runs,
		RngMeterState initialMeter,
		SelectionPolicy selection,
		KismetPolicy kismet,
		int samples,
		long seed
	) {
		HistorySimulator history = new HistorySimulator(simulator);
		SplittableRandom seeds = new SplittableRandom(seed);
		double[] out = new double[samples];
		for (int i = 0; i < samples; i++) {
			out[i] = SurprisalStatistic.of(history.simulate(runs, initialMeter, selection, kismet, seeds.nextLong()), simulator);
		}
		return out;
	}
}
