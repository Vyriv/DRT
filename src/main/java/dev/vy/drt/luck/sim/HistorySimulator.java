package dev.vy.drt.luck.sim;

import dev.vy.drt.luck.meter.RngMeterState;
import java.util.ArrayList;
import java.util.List;
import java.util.SplittableRandom;

/**
 * Replays a sequence of run opportunities with simulated loot, carrying the meter forward from
 * each simulated outcome. One call is one alternative history.
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
		SplittableRandom random = new SplittableRandom(seed);
		List<RunOutcome> out = new ArrayList<>(runs.size());
		RngMeterState meter = initialMeter;
		for (RunContext run : runs) {
			if (run.floor() != meter.floor()) throw new IllegalArgumentException("History mixes floors; simulate each floor meter separately");
			RunOutcome outcome = simulator.simulate(run, meter, selection, kismet, random);
			out.add(outcome);
			meter = outcome.meterAfter();
		}
		return out;
	}
}
