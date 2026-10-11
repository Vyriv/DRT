package dev.vy.drt.luck.sim;

import dev.vy.drt.luck.FloorId;
import dev.vy.drt.luck.meter.MeterEvent;
import dev.vy.drt.luck.meter.RngMeterModel;
import dev.vy.drt.luck.meter.RngMeterState;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.SplittableRandom;

/**
 * Replays a sequence of run opportunities with simulated loot, carrying each floor's meter
 * forward from the simulated outcomes. One call is one alternative history. A meter with no drop
 * selected still accumulates score, which later carries into a selection.
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
		return simulate(runs, initialMeters, List.of(), selection, kismet, seed);
	}

	/**
	 * @param events meter observations and selection changes applied before their run index
	 */
	public List<RunOutcome> simulate(
		List<RunContext> runs,
		Map<FloorId, RngMeterState> initialMeters,
		List<MeterEvent> events,
		SelectionPolicy selection,
		KismetPolicy kismet,
		long seed
	) {
		for (MeterEvent event : events) {
			if (event.beforeRun() > runs.size()) throw new IllegalArgumentException("Meter event after the last run");
		}
		SplittableRandom random = new SplittableRandom(seed);
		Map<FloorId, RngMeterState> meters = new EnumMap<>(FloorId.class);
		meters.putAll(initialMeters);
		List<RunOutcome> out = new ArrayList<>(runs.size());
		for (int i = 0; i < runs.size(); i++) {
			applyEvents(events, i, meters);
			RunContext run = runs.get(i);
			RngMeterState meter = meters.computeIfAbsent(run.floor(), RngMeterState::empty);
			RunOutcome outcome = simulator.simulate(run, meter, selection, kismet, random);
			out.add(outcome);
			meters.put(run.floor(), outcome.meterAfter());
		}
		return out;
	}

	private void applyEvents(List<MeterEvent> events, int runIndex, Map<FloorId, RngMeterState> meters) {
		for (MeterEvent event : events) {
			if (event.beforeRun() != runIndex) continue;
			switch (event) {
				case MeterEvent.Observed observed -> meters.put(observed.floor(), observed.state());
				case MeterEvent.Selected selected -> {
					RngMeterState current = meters.computeIfAbsent(selected.floor(), RngMeterState::empty);
					meters.put(selected.floor(), RngMeterModel.select(
						current,
						simulator.database().floor(selected.floor()),
						selected.selectedKey(),
						simulator.rules()
					));
				}
			}
		}
	}
}
