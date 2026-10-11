package dev.vy.drt.luck.stats;

import dev.vy.drt.luck.FloorId;
import dev.vy.drt.luck.meter.MeterEvent;
import dev.vy.drt.luck.meter.RngMeterState;
import dev.vy.drt.luck.sim.HistorySimulator;
import dev.vy.drt.luck.sim.KismetPolicy;
import dev.vy.drt.luck.sim.RunContext;
import dev.vy.drt.luck.sim.RunOutcome;
import dev.vy.drt.luck.sim.RunSimulator;
import dev.vy.drt.luck.sim.SelectionPolicy;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.SplittableRandom;
import java.util.function.IntFunction;

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
		return sample(simulator, runs, Map.of(initialMeter.floor(), initialMeter), selection, kismet, samples, seed);
	}

	public static double[] sample(
		RunSimulator simulator,
		List<RunContext> runs,
		Map<FloorId, RngMeterState> initialMeters,
		SelectionPolicy selection,
		KismetPolicy kismet,
		int samples,
		long seed
	) {
		return sample(simulator, runs, initialMeters, List.of(), selection, kismet, samples, seed);
	}

	/** @param events meter readings and selection changes, identical in every simulated history */
	public static double[] sample(
		RunSimulator simulator,
		List<RunContext> runs,
		Map<FloorId, RngMeterState> initialMeters,
		List<MeterEvent> events,
		SelectionPolicy selection,
		KismetPolicy kismet,
		int samples,
		long seed
	) {
		return sample(index -> simulator, runs, initialMeters, events, selection, kismet, samples, seed);
	}

	/** As above with each run simulated and scored under its own modifiers. */
	public static double[] sample(
		IntFunction<RunSimulator> simulatorFor,
		List<RunContext> runs,
		Map<FloorId, RngMeterState> initialMeters,
		List<MeterEvent> events,
		SelectionPolicy selection,
		KismetPolicy kismet,
		int samples,
		long seed
	) {
		SplittableRandom seeds = new SplittableRandom(seed);
		double[] out = new double[samples];
		for (int i = 0; i < samples; i++) {
			List<RunOutcome> history = HistorySimulator.simulate(runs, simulatorFor, initialMeters, events, selection, kismet, seeds.nextLong());
			out[i] = SurprisalStatistic.of(history, simulatorFor);
		}
		return out;
	}

	/** Meter readings attached to observed runs, as events before each run. */
	public static List<MeterEvent> observedMeterEvents(List<ObservedRun> observed) {
		List<MeterEvent> events = new ArrayList<>();
		for (int i = 0; i < observed.size(); i++) {
			RngMeterState before = observed.get(i).meterBefore();
			if (before != null) events.add(new MeterEvent.Observed(i, before));
		}
		return events;
	}
}
