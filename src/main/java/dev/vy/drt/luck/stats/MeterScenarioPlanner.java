package dev.vy.drt.luck.stats;

import dev.vy.drt.luck.ChestTier;
import dev.vy.drt.luck.FloorId;
import dev.vy.drt.luck.data.CatacombsLootDatabase;
import dev.vy.drt.luck.data.FloorTable;
import dev.vy.drt.luck.data.LootEntry;
import dev.vy.drt.luck.meter.MeterEvent;
import dev.vy.drt.luck.meter.RngMeterModel;
import dev.vy.drt.luck.meter.RngMeterState;
import dev.vy.drt.luck.rules.ResolvedModifiers;
import dev.vy.drt.luck.rules.RuleVariants;
import dev.vy.drt.luck.sim.ChestEligibility;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.IntFunction;

/**
 * Meter states to assume wherever an observed history's real meter is unknown. Between readings
 * the meter is replayed exactly from the observed runs: score gains from each run's score and
 * modifiers, resets from the observed appearances or purchases. Where that chain is broken, a
 * window opens with candidate states, and only candidates that reproduce every later reading and
 * every observed guarantee survive. Selections are assumed unchanged between readings.
 */
public final class MeterScenarioPlanner {
	/** Fractions of the required score tried when a start cannot be derived from a reading. */
	static final double[] FILLS = {0.0, 0.5, 1.0};

	private MeterScenarioPlanner() {
	}

	public enum WindowKind {
		/** The floor's first run was not preceded by a reading. */
		UNKNOWN_START,
		/** A run on the floor is missing from the history (rejected or excluded), and no reading follows it. */
		GAP,
		/** A reading shows more progress than the captured runs since the previous reading explain. */
		UNEXPLAINED_GAIN
	}

	/**
	 * @param state     assumed state before {@code Window.startRun}, or null to keep the forward-simulated meter
	 * @param selection selected key the candidate assumes, null for nothing selected
	 * @param level     boost ordering used for joint scenarios: 0 is the weakest assumption
	 */
	public record Candidate(String label, RngMeterState state, String selection, int level) {
	}

	/**
	 * @param startRun first history index the assumption applies to
	 * @param anchored whether a later reading constrained the candidates
	 */
	public record Window(WindowKind kind, FloorId floor, int startRun, boolean anchored, List<Candidate> candidates) {
		public Window {
			candidates = List.copyOf(candidates);
		}

		public boolean selectionUnknown() {
			return candidates.stream().map(Candidate::selection).distinct().count() > 1;
		}
	}

	public record Scenario(String label, List<MeterEvent> events) {
		public Scenario {
			events = List.copyOf(events);
		}
	}

	/**
	 * @param contradictions observations the meter model cannot reproduce under these rules and modifiers
	 * @param notes          gaps that need no scenario, such as a selection change between readings
	 */
	public record Plan(List<Window> windows, List<String> contradictions, List<String> notes) {
		public Plan {
			windows = List.copyOf(windows);
			contradictions = List.copyOf(contradictions);
			notes = List.copyOf(notes);
		}

		public boolean contradicted() {
			return !contradictions.isEmpty();
		}

		public Scenario baseline() {
			return new Scenario("meter baseline", events(windows.stream().map(w -> w.candidates().get(0)).toList()));
		}

		/**
		 * Baseline first, then joint scenarios raising every window's fill together, then each other
		 * selection of a window whose selection is unknown, at its weakest and strongest fill.
		 */
		public List<Scenario> scenarios() {
			List<Candidate> base = windows.stream().map(w -> w.candidates().get(0)).toList();
			Map<List<Candidate>, String> out = new LinkedHashMap<>();
			out.put(base, "meter baseline");
			int maxLevel = windows.stream().flatMap(w -> w.candidates().stream()).mapToInt(Candidate::level).max().orElse(0);
			for (int level = 1; level <= maxLevel; level++) {
				List<Candidate> picks = new ArrayList<>();
				for (int i = 0; i < windows.size(); i++) picks.add(strongestAtOrBelow(windows.get(i), base.get(i).selection(), level));
				out.putIfAbsent(picks, "meter fill level " + level);
			}
			for (int i = 0; i < windows.size(); i++) {
				Window window = windows.get(i);
				Map<String, List<Candidate>> bySelection = new LinkedHashMap<>();
				for (Candidate candidate : window.candidates()) {
					if (Objects.equals(candidate.selection(), base.get(i).selection())) continue;
					bySelection.computeIfAbsent(String.valueOf(candidate.selection()), key -> new ArrayList<>()).add(candidate);
				}
				for (List<Candidate> group : bySelection.values()) {
					for (Candidate candidate : List.of(group.get(0), group.get(group.size() - 1))) {
						List<Candidate> picks = new ArrayList<>(base);
						picks.set(i, candidate);
						out.putIfAbsent(picks, "meter " + window.floor() + " from run " + window.startRun() + ": " + candidate.label());
					}
				}
			}
			List<Scenario> scenarios = new ArrayList<>();
			out.forEach((picks, label) -> scenarios.add(new Scenario(label, events(picks))));
			return scenarios;
		}

		private List<MeterEvent> events(List<Candidate> picks) {
			List<MeterEvent> events = new ArrayList<>();
			for (int i = 0; i < picks.size(); i++) {
				if (picks.get(i).state() != null) events.add(new MeterEvent.Observed(windows.get(i).startRun(), picks.get(i).state()));
			}
			return events;
		}

		private static Candidate strongestAtOrBelow(Window window, String selection, int level) {
			Candidate best = null;
			for (Candidate candidate : window.candidates()) {
				if (!Objects.equals(candidate.selection(), selection) || candidate.level() > level) continue;
				if (best == null || candidate.level() > best.level()) best = candidate;
			}
			return best;
		}
	}

	/**
	 * @param gapsBefore    history indices of runs preceded, since the previous run on the same floor, by a run missing from the history
	 * @param modifiersFor  the modifiers each run is simulated under
	 */
	public static Plan plan(List<ObservedRun> runs, Set<Integer> gapsBefore, CatacombsLootDatabase database, RuleVariants rules,
		IntFunction<ResolvedModifiers> modifiersFor) {
		Replay replay = new Replay(runs, database, rules, modifiersFor);
		Set<FloorId> floors = EnumSet.noneOf(FloorId.class);
		for (ObservedRun run : runs) floors.add(run.run().floor());
		for (FloorId floor : floors) replay.floor(floor, gapsBefore);
		return new Plan(replay.windows, replay.contradictions, replay.notes);
	}

	private static final class Replay {
		final List<ObservedRun> runs;
		final CatacombsLootDatabase database;
		final RuleVariants rules;
		final IntFunction<ResolvedModifiers> modifiersFor;
		final List<Window> windows = new ArrayList<>();
		final List<String> contradictions = new ArrayList<>();
		final List<String> notes = new ArrayList<>();

		Replay(List<ObservedRun> runs, CatacombsLootDatabase database, RuleVariants rules, IntFunction<ResolvedModifiers> modifiersFor) {
			this.runs = runs;
			this.database = database;
			this.rules = rules;
			this.modifiersFor = modifiersFor;
		}

		/** An open window: runs whose starting meter is unknown until the next reading. */
		private static final class Open {
			final WindowKind kind;
			final int startRun;
			final boolean selectionKnown;
			final String selection;
			final List<Integer> runs = new ArrayList<>();

			Open(WindowKind kind, int startRun, boolean selectionKnown, String selection) {
				this.kind = kind;
				this.startRun = startRun;
				this.selectionKnown = selectionKnown;
				this.selection = selection;
			}
		}

		void floor(FloorId floor, Set<Integer> gapsBefore) {
			RngMeterState state = null;
			Open open = null;
			// Runs replayed exactly since the last reading or reset, for placing an unexplained gain early.
			List<Integer> sinceAnchor = new ArrayList<>();
			boolean first = true;
			for (int index = 0; index < runs.size(); index++) {
				ObservedRun run = runs.get(index);
				if (run.run().floor() != floor) continue;
				RngMeterState before = run.meterBefore();
				if (first || gapsBefore.contains(index)) {
					if (open != null) close(open, null);
					open = null;
					sinceAnchor.clear();
					if (before != null) {
						state = before;
					} else {
						WindowKind kind = first ? WindowKind.UNKNOWN_START : WindowKind.GAP;
						open = state == null || first ? new Open(kind, index, false, null) : new Open(kind, index, true, state.selectedKey());
						state = null;
					}
					first = false;
				} else if (before != null) {
					if (open != null) {
						close(open, before);
						open = null;
					} else if (state != null) {
						compare(floor, state, before, sinceAnchor, index);
					}
					state = before;
					sinceAnchor.clear();
				}

				if (open != null) {
					open.runs.add(index);
				} else if (state != null) {
					Step step = step(state, index);
					if (step.contradiction() != null) contradictions.add(step.contradiction());
					state = step.after();
					if (step.reset()) sinceAnchor.clear();
					else sinceAnchor.add(index);
				}

				RngMeterState after = run.meterAfter();
				if (after != null) {
					if (open != null) {
						close(open, after);
						open = null;
					} else if (state != null) {
						compare(floor, state, after, sinceAnchor, -1);
					}
					state = after;
					sinceAnchor.clear();
				}
			}
			if (open != null) close(open, null);
		}

		/** A reading after exactly replayed runs: it must match, show an unexplained gain, or follow a selection change. */
		private void compare(FloorId floor, RngMeterState replayed, RngMeterState reading, List<Integer> sinceAnchor, int readingRun) {
			String where = readingRun >= 0 ? "before run " + readingRun : "after run " + (sinceAnchor.isEmpty() ? "?" : sinceAnchor.get(sinceAnchor.size() - 1));
			if (!Objects.equals(replayed.selectedKey(), reading.selectedKey())) {
				notes.add("METER_SELECTION_CHANGED:" + floor + " " + where);
				return;
			}
			long difference = reading.storedScore() - replayed.storedScore();
			if (difference == 0) return;
			if (difference < 0) {
				contradictions.add("METER_BELOW_CAPTURED_GAINS:" + floor + " " + where + " reads " + reading.storedScore() + ", captured runs give " + replayed.storedScore());
				return;
			}
			if (sinceAnchor.isEmpty()) {
				notes.add("METER_UNEXPLAINED_GAIN:" + floor + " " + where + " +" + difference + " with no captured run in between");
				return;
			}
			int start = sinceAnchor.get(0);
			long gains = 0L;
			for (int index : sinceAnchor) gains += gain(index);
			RngMeterState early = new RngMeterState(floor, reading.selectedKey(), reading.storedScore() - gains);
			if (!reproduces(early, sinceAnchor, reading)) {
				notes.add("METER_UNEXPLAINED_GAIN:" + floor + " " + where + " +" + difference + " cannot precede the runs without an unseen guarantee");
				return;
			}
			windows.add(new Window(WindowKind.UNEXPLAINED_GAIN, floor, start, true, List.of(
				new Candidate("missing progress after the runs", null, reading.selectedKey(), 0),
				new Candidate("missing progress before the runs", early, reading.selectedKey(), 1))));
		}

		private void close(Open open, RngMeterState anchor) {
			FloorId floor = runs.get(open.startRun).run().floor();
			FloorTable table = database.floor(floor);
			List<String> selections = new ArrayList<>();
			if (anchor != null) {
				selections.add(anchor.selectedKey());
			} else if (open.selectionKnown) {
				selections.add(open.selection);
			} else {
				selections.add(null);
				for (LootEntry entry : RngMeterModel.selectableItems(table)) selections.add(entry.key());
			}
			long windowGain = 0L;
			for (int index : open.runs) windowGain += gain(index);

			List<Candidate> candidates = new ArrayList<>();
			for (String selection : selections) {
				if (selection == null) {
					long stored = anchor == null ? 0L : anchor.storedScore() - windowGain;
					if (stored >= 0) candidates.add(new Candidate(anchor == null ? "nothing selected" : "derived from reading", new RngMeterState(floor, null, stored), null, 0));
					continue;
				}
				long required = RngMeterModel.requiredScore(table, selection, rules);
				boolean reset = open.runs.stream().anyMatch(index -> triggered(selection, index));
				if (anchor != null && !reset) {
					long stored = anchor.storedScore() - windowGain;
					if (stored >= 0 && reproduces(new RngMeterState(floor, selection, stored), open.runs, anchor)) {
						candidates.add(new Candidate("derived from reading", new RngMeterState(floor, selection, stored), selection, 0));
					}
					continue;
				}
				for (int level = 0; level < FILLS.length; level++) {
					RngMeterState start = new RngMeterState(floor, selection, Math.round(required * FILLS[level]));
					if (reproduces(start, open.runs, anchor)) {
						candidates.add(new Candidate(selection + " at " + Math.round(FILLS[level] * 100) + "%", start, selection, level));
					}
				}
			}
			if (candidates.isEmpty()) {
				contradictions.add("METER_" + open.kind + "_UNRESOLVABLE:" + floor + " from run " + open.startRun
					+ (anchor == null ? "" : ": no start reproduces the reading of " + anchor.storedScore()));
				return;
			}
			windows.add(new Window(open.kind, floor, open.startRun, anchor != null, candidates));
		}

		/** Replays a candidate through a window: no unseen guarantee, and the closing reading if there is one. */
		private boolean reproduces(RngMeterState start, List<Integer> window, RngMeterState anchor) {
			RngMeterState state = start;
			for (int index : window) {
				Step step = step(state, index);
				if (step.contradiction() != null) return false;
				state = step.after();
			}
			return anchor == null || state.storedScore() == anchor.storedScore();
		}

		private record Step(RngMeterState after, boolean reset, String contradiction) {
		}

		private Step step(RngMeterState state, int index) {
			ObservedRun run = runs.get(index);
			long gain = gain(index);
			if (!state.hasSelection()) return new Step(RngMeterModel.afterRun(state, 0L, false, false, gain, rules), false, null);
			FloorTable table = database.floor(run.run().floor());
			long required = RngMeterModel.requiredScore(table, state.selectedKey(), rules);
			boolean appeared = run.appearedKeys().contains(state.selectedKey());
			boolean purchased = run.purchasedKeys().contains(state.selectedKey());
			List<ChestTier> eligible = ChestEligibility.eligible(table, run.run().scoreTier(), rules);
			boolean forced = RngMeterModel.isFull(state.storedScore(), required) && RngMeterModel.guaranteeTier(table, state.selectedKey(), eligible, rules) != null;
			String contradiction = forced && !appeared
				? "METER_GUARANTEE_NOT_SEEN:" + run.run().floor() + " run " + index + " had a full meter for " + state.selectedKey() + " but it did not appear"
				: null;
			return new Step(RngMeterModel.afterRun(state, required, appeared, purchased, gain, rules), triggered(state.selectedKey(), index), contradiction);
		}

		private boolean triggered(String selection, int index) {
			ObservedRun run = runs.get(index);
			return rules.meterResetTrigger() == RuleVariants.MeterResetTrigger.ON_APPEARANCE
				? run.appearedKeys().contains(selection)
				: run.purchasedKeys().contains(selection);
		}

		private long gain(int index) {
			ObservedRun run = runs.get(index);
			return RngMeterModel.scoreGain(run.run().scoreTier(), run.run().score(), modifiersFor.apply(index), rules);
		}
	}
}
