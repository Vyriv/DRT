package dev.vy.drt.luck.capture;

import dev.vy.drt.luck.FloorId;
import dev.vy.drt.luck.data.CatacombsLootDatabase;
import dev.vy.drt.luck.rules.ResolvedModifiers;
import dev.vy.drt.luck.rules.RuleEvidence;
import dev.vy.drt.luck.rules.RuleVariants;
import dev.vy.drt.luck.sim.KismetPolicy;
import dev.vy.drt.luck.sim.RunContext;
import dev.vy.drt.luck.sim.RunSimulator;
import dev.vy.drt.luck.sim.SelectionPolicy;
import dev.vy.drt.luck.stats.HistoryConfigurations;
import dev.vy.drt.luck.stats.LuckEvaluator;
import dev.vy.drt.luck.stats.LuckEvaluator.Configuration;
import dev.vy.drt.luck.stats.MeterScenarioPlanner;
import dev.vy.drt.luck.stats.RareOutcomeGate;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Everything a Luck Rating needs to show about a recorded history: per-floor capture coverage, which
 * runs were scored, rejected or held out and why, the meter windows the engine had to assume, and
 * the verdict. A run is scorable when its own record is complete; the history is statistically
 * sufficient only when the rated floors' scorable runs expect at least one rare drop. Neither
 * implies the other, and neither implies that the runs left out are harmless.
 *
 * <p>Only floors with at least {@link #MINIMUM_COVERAGE_PERCENT}% of their recorded runs scorable
 * are rated. Passing that gate is not treated as proof the rest is unbiased: rejected runs on rated
 * floors must also be shown unable to change the verdict, and any Kismet-excluded run on a rated
 * floor withholds the headline until rerolls are simulated.
 *
 * @param expectedRareDrops rare drops the rated floors' scorable runs expect, NaN when none
 * @param evaluation        the engine's result, or null when the score was withheld before scoring;
 *                          kept for diagnostics when a later check withholds, never a headline then
 * @param missingRunBounds  how far rejected runs on rated floors could move the result, or null when none were checked
 */
public record LuckHistoryEvaluation(
	Status status,
	int recordedRuns,
	int scorableRuns,
	int rejectedRuns,
	int excludedRuns,
	double expectedRareDrops,
	boolean statisticallySufficient,
	List<FloorCoverage> floorCoverage,
	List<LuckScoringInput.Converted> scored,
	List<LuckScoringInput.Rejected> rejected,
	List<LuckScoringInput.Excluded> excluded,
	Map<String, Integer> rejectionReasonCounts,
	Map<String, Integer> runCaveatCounts,
	List<String> historyCaveats,
	int modifierSnapshots,
	List<MeterScenarioPlanner.Window> meterWindows,
	List<String> meterNotes,
	List<String> droppedConfigurations,
	List<WithholdCause> withheldCauses,
	List<String> withheldReasons,
	LuckEvaluator.Evaluation evaluation,
	LuckEvaluator.MissingRunBounds missingRunBounds
) {
	/** Kismet rerolls are excluded until a matching reroll policy exists, so the null never rerolls. */
	public static final KismetPolicy KISMET = KismetPolicy.never();
	public static final int MINIMUM_COVERAGE_PERCENT = 90;
	public static final String UNKNOWN_FLOOR = "UNKNOWN";
	private static final Pattern RUN_INDEX = Pattern.compile("run (\\d+)");

	public enum Status {
		NO_RECORDED_RUNS,
		/** Runs were recorded but none can be scored on its own. */
		NO_SCORABLE_RUNS,
		/** Scorable runs exist, but missing or contradictory data could make any percentile misleading; see {@link WithholdCause}. */
		WITHHELD,
		/** Scorable runs exist, but too few rare drops are expected for a percentile. */
		INSUFFICIENT_DATA,
		/** Every plausible configuration, and every plausible outcome of the runs left out, gives the same band. */
		PROVISIONAL,
		/** Plausible configurations disagree on the band; the runs left out stay within the bands they span. */
		UNRESOLVED
	}

	public enum WithholdCause {
		/** No floor has enough of its recorded runs fully captured. */
		NO_ELIGIBLE_FLOOR,
		/** Runs with no readable floor cannot be counted against any floor's coverage. */
		UNATTRIBUTED_RUNS,
		/** A rated floor has runs held out for a confirmed or suspected Kismet reroll. */
		KISMET_REROLLS,
		/** The RNG Meter shows progress from runs DRT never recorded; their number and loot are unknown. */
		UNRECORDED_RUNS,
		/** The meter readings contradict the captured runs. */
		METER_CONTRADICTION,
		/** Too many meter, modifier and rule configurations to score. */
		TOO_MANY_CONFIGURATIONS,
		/** Rejected runs on rated floors could move the result into another band. */
		MISSING_RUNS_COULD_CHANGE_VERDICT,
		/** The engine was given a run without complete offers. */
		INCOMPLETE_RECORDS
	}

	/**
	 * Capture coverage of one floor ({@link #UNKNOWN_FLOOR} for runs without a readable floor).
	 *
	 * @param eligible whether the floor counts towards the rating
	 */
	public record FloorCoverage(String floor, int recorded, int scorable, int rejected, int excluded, boolean eligible) {
		public double coverage() {
			return recorded == 0 ? 0.0 : (double) scorable / recorded;
		}

		public static boolean meetsGate(int scorable, int recorded) {
			return recorded > 0 && 100L * scorable >= (long) MINIMUM_COVERAGE_PERCENT * recorded;
		}

		public String describe() {
			return floor + " " + scorable + "/" + recorded;
		}
	}

	public LuckHistoryEvaluation {
		floorCoverage = List.copyOf(floorCoverage);
		scored = List.copyOf(scored);
		rejected = List.copyOf(rejected);
		excluded = List.copyOf(excluded);
		rejectionReasonCounts = Map.copyOf(rejectionReasonCounts);
		runCaveatCounts = Map.copyOf(runCaveatCounts);
		historyCaveats = List.copyOf(historyCaveats);
		meterWindows = List.copyOf(meterWindows);
		meterNotes = List.copyOf(meterNotes);
		droppedConfigurations = List.copyOf(droppedConfigurations);
		withheldCauses = List.copyOf(withheldCauses);
		withheldReasons = List.copyOf(withheldReasons);
	}

	/** The floors the rating covers. */
	public List<String> ratedFloors() {
		return floorCoverage.stream().filter(FloorCoverage::eligible).map(FloorCoverage::floor).toList();
	}

	/** The band to headline, or null unless the result is {@link Status#PROVISIONAL}. */
	public LuckEvaluator.Band headlineBand() {
		return status == Status.PROVISIONAL && evaluation != null ? evaluation.band() : null;
	}

	public static LuckHistoryEvaluation evaluate(
		List<LuckRunRecord> recorded,
		CatacombsLootDatabase database,
		RuleVariants rules,
		RuleEvidence evidence,
		SelectionPolicy selection,
		int samples,
		long seed
	) {
		LuckScoringInput.History history = LuckScoringInput.history(recorded, database);
		Map<String, Integer> reasons = new TreeMap<>();
		for (LuckScoringInput.Rejected run : history.rejected()) run.reasons().forEach(reason -> reasons.merge(reason, 1, Integer::sum));
		for (LuckScoringInput.Excluded run : history.excluded()) reasons.merge(run.reason(), 1, Integer::sum);
		Map<String, Integer> caveats = new TreeMap<>();
		for (LuckScoringInput.Converted run : history.runs()) run.caveats().forEach(caveat -> caveats.merge(caveat, 1, Integer::sum));

		List<FloorCoverage> coverage = floorCoverage(history);
		List<String> historyCaveats = new ArrayList<>(history.caveats());
		if (!history.excluded().isEmpty()) {
			historyCaveats.add("KISMET_RUNS_EXCLUDED:" + history.excluded().size()
				+ " (rerolls usually follow poor offers, so the remaining runs may look luckier)");
		}
		if (!history.rejected().isEmpty()) {
			historyCaveats.add("RUNS_NOT_SCORED:" + history.rejected().size()
				+ " (if whether a run was fully captured depends on its loot, the score is biased)");
		}
		for (FloorCoverage floor : coverage) {
			if (!floor.eligible()) historyCaveats.add("FLOOR_NOT_RATED:" + floor.describe() + " (needs " + MINIMUM_COVERAGE_PERCENT + "% captured)");
		}
		int snapshots = (int) history.runModifiers().stream().distinct().count();

		Builder builder = new Builder(history, coverage, reasons, caveats, historyCaveats, snapshots);
		if (history.recorded() == 0) return builder.status(Status.NO_RECORDED_RUNS).build();
		if (history.runs().isEmpty()) {
			return builder.status(Status.NO_SCORABLE_RUNS).withhold(null, "none of the " + history.recorded() + " recorded runs can be scored").build();
		}

		Set<FloorId> rated = EnumSet.noneOf(FloorId.class);
		for (FloorCoverage floor : coverage) {
			if (floor.eligible()) rated.add(FloorId.parse(floor.floor()));
		}
		for (FloorCoverage floor : coverage) {
			if (floor.floor().equals(UNKNOWN_FLOOR)) {
				builder.withhold(WithholdCause.UNATTRIBUTED_RUNS, floor.recorded() + " recorded runs have no readable floor, so no floor's coverage can account for them");
			}
		}
		if (rated.isEmpty()) {
			builder.withhold(WithholdCause.NO_ELIGIBLE_FLOOR, "no floor has " + MINIMUM_COVERAGE_PERCENT + "% of its recorded runs fully captured: "
				+ String.join(", ", coverage.stream().map(FloorCoverage::describe).toList()));
			return builder.status(Status.WITHHELD).build();
		}

		List<LuckRunRecord> inScope = recorded.stream().filter(run -> rated.contains(FloorId.parse(run.floor))).toList();
		LuckScoringInput.History scope = LuckScoringInput.history(inScope, database);
		builder.expectedRareDrops(expectedRareDrops(scope, database, rules));
		if (!scope.excluded().isEmpty()) {
			builder.withhold(WithholdCause.KISMET_REROLLS, scope.excluded().size() + " runs on rated floors used or may have used a Kismet reroll;"
				+ " rerolls are not simulated yet and usually follow poor offers, so leaving them out would flatter the rest");
		}

		HistoryConfigurations.Result configurations = HistoryConfigurations.build(scope.observed(), scope.gapsBefore(), scope.runModifiers(),
			database, rules, evidence, selection, KISMET);
		builder.configurations(configurations);
		if (!configurations.scorable()) {
			boolean contradicted = configurations.baselinePlan() != null && configurations.baselinePlan().contradicted();
			WithholdCause cause = contradicted ? WithholdCause.METER_CONTRADICTION : WithholdCause.TOO_MANY_CONFIGURATIONS;
			configurations.withheld().forEach(reason -> builder.withhold(cause, reason));
		} else {
			for (MeterScenarioPlanner.Window window : configurations.baselinePlan().windows()) {
				if (window.kind() != MeterScenarioPlanner.WindowKind.UNEXPLAINED_GAIN) continue;
				builder.withhold(WithholdCause.UNRECORDED_RUNS, "the " + window.floor() + " RNG Meter shows progress around "
					+ scope.runs().get(window.startRun()).luckRunId() + " that the recorded runs do not explain; how many runs DRT missed and what they dropped is unknown");
			}
			for (String note : configurations.baselinePlan().notes()) {
				if (!note.startsWith(MeterScenarioPlanner.UNEXPLAINED_GAIN_NOTE)) continue;
				builder.withhold(WithholdCause.UNRECORDED_RUNS, describeGain(note.substring(MeterScenarioPlanner.UNEXPLAINED_GAIN_NOTE.length()), scope)
					+ "; how many runs DRT missed and what they dropped is unknown");
			}
		}
		if (builder.withheld()) return builder.status(Status.WITHHELD).build();

		List<Configuration> all = configurations.configurations();
		LuckEvaluator.Evaluation evaluation = LuckEvaluator.evaluate(database, scope.observed(), all.get(0), all.subList(1, all.size()), samples, seed);
		builder.evaluation(evaluation);
		switch (evaluation.verdict()) {
			case INSUFFICIENT_DATA -> {
				return builder.status(Status.INSUFFICIENT_DATA).build();
			}
			case INCOMPLETE_RECORDS -> {
				evaluation.reasons().forEach(reason -> builder.withhold(WithholdCause.INCOMPLETE_RECORDS, reason));
				return builder.status(Status.WITHHELD).build();
			}
			default -> {
			}
		}

		Status scored = evaluation.verdict() == LuckEvaluator.Verdict.PROVISIONAL ? Status.PROVISIONAL : Status.UNRESOLVED;
		if (scope.rejected().isEmpty()) return builder.status(scored).build();
		Map<String, LuckRunRecord> byId = new HashMap<>();
		for (LuckRunRecord run : inScope) byId.put(run.luckRunId, run);
		List<LuckEvaluator.MissingRun> missing = new ArrayList<>();
		for (LuckScoringInput.Rejected run : scope.rejected()) missing.add(LuckScoringInput.missingRun(byId.get(run.luckRunId()), database));
		LuckEvaluator.MissingRunBounds bounds = LuckEvaluator.missingRunBounds(database, scope.observed(), evaluation, missing, samples, seed ^ 0x6d697373L);
		builder.missingRunBounds(bounds);
		if (!bounds.material()) return builder.status(scored).build();
		builder.withhold(WithholdCause.MISSING_RUNS_COULD_CHANGE_VERDICT, String.format(
			"%d rejected runs on rated floors (%d with unseen loot) could put the history anywhere from percentile %.3f to %.3f (%s), beyond the verdict's %s",
			bounds.missingRuns(), bounds.unknownOutcomes(), bounds.low().percentile(), bounds.high().percentile(), ordered(bounds.bands()), ordered(bounds.headlineBands())));
		return builder.status(Status.WITHHELD).build();
	}

	/** Coverage per floor in floor order, then runs without a readable floor. */
	static List<FloorCoverage> floorCoverage(LuckScoringInput.History history) {
		Map<String, int[]> counts = new TreeMap<>((a, b) -> {
			FloorId left = FloorId.parse(a);
			FloorId right = FloorId.parse(b);
			if (left == null || right == null) return left == right ? a.compareTo(b) : left == null ? 1 : -1;
			return left.compareTo(right);
		});
		for (LuckScoringInput.Converted run : history.runs()) counts.computeIfAbsent(run.observed().run().floor().name(), key -> new int[3])[0]++;
		for (LuckScoringInput.Rejected run : history.rejected()) counts.computeIfAbsent(floorKey(run.floor()), key -> new int[3])[1]++;
		for (LuckScoringInput.Excluded run : history.excluded()) counts.computeIfAbsent(floorKey(run.floor()), key -> new int[3])[2]++;
		List<FloorCoverage> out = new ArrayList<>();
		counts.forEach((floor, count) -> {
			int recorded = count[0] + count[1] + count[2];
			boolean eligible = !floor.equals(UNKNOWN_FLOOR) && FloorCoverage.meetsGate(count[0], recorded);
			out.add(new FloorCoverage(floor, recorded, count[0], count[1], count[2], eligible));
		});
		return out;
	}

	private static String floorKey(String floor) {
		FloorId parsed = FloorId.parse(floor);
		return parsed == null ? UNKNOWN_FLOOR : parsed.name();
	}

	/** Rare drops expected by the rated runs under each run's lowest plausible modifiers. */
	private static double expectedRareDrops(LuckScoringInput.History scope, CatacombsLootDatabase database, RuleVariants rules) {
		if (scope.runs().isEmpty()) return Double.NaN;
		Map<ResolvedModifiers, RunSimulator> simulators = new HashMap<>();
		List<RunSimulator> perRun = scope.runModifiers().stream()
			.map(preset -> simulators.computeIfAbsent(preset.plausibleExtremes().get(0), modifiers -> new RunSimulator(database, modifiers, rules)))
			.toList();
		List<RunContext> runs = scope.observed().stream().map(run -> run.run()).toList();
		return RareOutcomeGate.expectedRareAppearances(runs, perRun::get, RareOutcomeGate.DEFAULT_RARE_THRESHOLD);
	}

	/** A planner note with history indices replaced by run ids. */
	private static String describeGain(String note, LuckScoringInput.History scope) {
		Matcher matcher = RUN_INDEX.matcher(note);
		StringBuilder out = new StringBuilder("RNG Meter on ");
		while (matcher.find()) {
			int index = Integer.parseInt(matcher.group(1));
			String id = index < scope.runs().size() ? scope.runs().get(index).luckRunId() : matcher.group();
			matcher.appendReplacement(out, Matcher.quoteReplacement("run " + id));
		}
		matcher.appendTail(out);
		return out.toString();
	}

	private static String ordered(Set<LuckEvaluator.Band> bands) {
		return EnumSet.copyOf(bands).toString();
	}

	private static final class Builder {
		private final LuckScoringInput.History history;
		private final List<FloorCoverage> coverage;
		private final Map<String, Integer> reasons;
		private final Map<String, Integer> caveats;
		private final List<String> historyCaveats;
		private final int snapshots;
		private final Set<WithholdCause> causes = new LinkedHashSet<>();
		private final List<String> withheld = new ArrayList<>();
		private Status status;
		private double expectedRareDrops = Double.NaN;
		private HistoryConfigurations.Result configurations;
		private LuckEvaluator.Evaluation evaluation;
		private LuckEvaluator.MissingRunBounds bounds;

		Builder(LuckScoringInput.History history, List<FloorCoverage> coverage, Map<String, Integer> reasons, Map<String, Integer> caveats,
			List<String> historyCaveats, int snapshots) {
			this.history = history;
			this.coverage = coverage;
			this.reasons = reasons;
			this.caveats = caveats;
			this.historyCaveats = historyCaveats;
			this.snapshots = snapshots;
		}

		Builder status(Status value) {
			status = value;
			return this;
		}

		Builder withhold(WithholdCause cause, String reason) {
			if (cause != null) causes.add(cause);
			withheld.add(reason);
			return this;
		}

		boolean withheld() {
			return !causes.isEmpty();
		}

		void expectedRareDrops(double value) {
			expectedRareDrops = value;
		}

		void configurations(HistoryConfigurations.Result value) {
			configurations = value;
		}

		void evaluation(LuckEvaluator.Evaluation value) {
			evaluation = value;
		}

		void missingRunBounds(LuckEvaluator.MissingRunBounds value) {
			bounds = value;
		}

		LuckHistoryEvaluation build() {
			MeterScenarioPlanner.Plan plan = configurations == null ? null : configurations.baselinePlan();
			boolean sufficient = !Double.isNaN(expectedRareDrops) && expectedRareDrops >= RareOutcomeGate.DEFAULT_MINIMUM_EXPECTED;
			return new LuckHistoryEvaluation(status, history.recorded(), history.runs().size(), history.rejected().size(), history.excluded().size(),
				expectedRareDrops, sufficient, coverage,
				history.runs(), history.rejected(), history.excluded(), reasons, caveats, historyCaveats, snapshots,
				plan == null ? List.of() : plan.windows(),
				plan == null ? List.of() : plan.notes(),
				configurations == null ? List.of() : configurations.dropped(),
				List.copyOf(causes), withheld, evaluation, bounds);
		}
	}
}
