package dev.vy.drt.luck.sim;

import dev.vy.drt.luck.FloorId;
import dev.vy.drt.luck.meter.RngMeterState;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.SplittableRandom;

/**
 * Replays a sequence of run opportunities with simulated loot, carrying each floor's meter
 * forward from the simulated outcomes. One call is one alternative history.
 */
public final class HistorySimulator {
	private final RunSimulator simulator;

	public HistorySimulator(RunSimulator simulator) {
		this.simulator = simulator;
	}

	public List<RunOutcome> simulate(
		List<RunContext> runs,
		RngMeterState initialMeter,
		SelectionPolicy selection,
		KismetPolicy kismet,
		long seed
	) {
		for (RunContext run : runs) {
			if (run.floor() != initialMeter.floor()) throw new IllegalArgumentException("Run on " + run.floor() + " with a " + initialMeter.floor() + " meter");
		}
		return simulate(runs, Map.of(initialMeter.floor(), initialMeter), selection, kismet, seed);
	}

	/**
	 * @param initialMeters starting meter per floor; floors without an entry start with nothing selected
	 */
	public List<RunOutcome> simulate(
		List<RunContext> runs,
		Map<FloorId, RngMeterState> initialMeters,
		SelectionPolicy selection,
		KismetPolicy kismet,
		long seed
	) {
		SplittableRandom random = new SplittableRandom(seed);
		Map<FloorId, RngMeterState> meters = new EnumMap<>(FloorId.class);
		meters.putAll(initialMeters);
		List<RunOutcome> out = new ArrayList<>(runs.size());
		for (RunContext run : runs) {
			RngMeterState meter = meters.computeIfAbsent(run.floor(), RngMeterState::empty);
			RunOutcome outcome = simulator.simulate(run, meter, selection, kismet, random);
			out.add(outcome);
			meters.put(run.floor(), outcome.meterAfter());
		}
		return out;
	}
}
