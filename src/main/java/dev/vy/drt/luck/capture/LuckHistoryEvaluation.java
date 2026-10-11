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
 * @param estimate          a labelled range for a withheld history, or null; never a verified rating
 * @param estimateUnavailable why a withheld history has no estimate, or null
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
	LuckEvaluator.MissingRunBounds missingRunBounds,
	Estimate estimate,
	String estimateUnavailable
) {
	/** Kismet rerolls are excluded until a matching reroll policy exists, so the null never rerolls. */
	public static final KismetPolicy KISMET = KismetPolicy.never();
	public static final int MINIMUM_COVERAGE_PERCENT = 90;
	public static final String UNKNOWN_FLOOR = "UNKNOWN";
	private static final Pattern RUN_INDEX = Pattern.compile("run (\\d+)");
	/** Causes the worst-selection bound can still put a range on; every other cause leaves the number or the loot of runs unknown. */
	private static final Set<WithholdCause> ESTIMABLE = EnumSet.of(WithholdCause.NO_ELIGIBLE_FLOOR, WithholdCause.MISSING_RUNS_COULD_CHANGE_VERDICT);
	private static final long ESTIMATE_SEED = 0x65737469L;

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

		/** Fully captured runs in a row that would lift the floor to the gate, 0 when it passes, -1 for {@link #UNKNOWN_FLOOR}. */
		public int capturedRunsToGate() {
			if (floor.equals(UNKNOWN_FLOOR)) return -1;
			if (meetsGate(scorable, recorded)) return 0;
			long shortfall = (long) MINIMUM_COVERAGE_PERCENT * recorded - 100L * scorable;
			long perRun = 100L - MINIMUM_COVERAGE_PERCENT;
			return (int) ((shortfall + perRun - 1) / perRun);
		}
	}

	/**
	 * A labelled range for a history whose headline is withheld, from the same worst-selection bound
	 * as the headline check: every rejected run on the estimate's floors is taken as the luckiest, then
	 * the unluckiest, of its floor's runs. It assumes nothing about why runs are missing, so it is wide,
	 * and it is never a verified rating.
	 *
	 * @param floors  floors the estimate covers; each has scorable runs, whether or not it passes the coverage gate
	 * @param lowest  lowest band the history could be in, including simulation error
	 * @param highest highest band the history could be in
	 */
	public record Estimate(
		List<String> floors,
		int scoredRuns,
		int missingRuns,
		int unknownOutcomes,
		LuckEvaluator.Band lowest,
		LuckEvaluator.Band highest,
		double lowPercentile,
		double highPercentile
	) {
		public Estimate {
			floors = List.copyOf(floors);
		}

		/** False when the missing runs could put the history in any band. */
		public boolean informative() {
			return lowest != LuckEvaluator.Band.VERY_UNLUCKY || highest != LuckEvaluator.Band.VERY_LUCKY;
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

	/**
	 * Roughly how many more scorable runs on the rated floors would expect one rare drop, at the
	 * current mix of floors and scores: 0 once sufficient, -1 when nothing is rated yet.
	 */
	public int runsUntilSufficient() {
		if (statisticallySufficient) return 0;
		int rated = floorCoverage.stream().filter(FloorCoverage::eligible).mapToInt(FloorCoverage::scorable).sum();
		if (rated == 0 || Double.isNaN(expectedRareDrops) || expectedRareDrops <= 0.0) return -1;
		double perRun = expectedRareDrops / rated;
		return (int) Math.ceil((RareOutcomeGate.DEFAULT_MINIMUM_EXPECTED - expectedRareDrops) / perRun);
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
			if (builder.onlyEstimableCauses()) {
				Set<FloorId> captured = EnumSet.noneOf(FloorId.class);
				for (FloorCoverage floor : coverage) {
					if (floor.scorable() > 0 && !floor.floor().equals(UNKNOWN_FLOOR)) captured.add(FloorId.parse(floor.floor()));
				}
				builder.estimate(score(recorded, captured, database, rules, evidence, selection, samples, seed ^ ESTIMATE_SEED, true));
			}
			return builder.status(Status.WITHHELD).build();
		}

		Scoped strict = score(recorded, rated, database, rules, evidence, selection, samples, seed, !builder.withheld());
		builder.expectedRareDrops(strict.expectedRareDrops());
		builder.configurations(strict.configurations());
		strict.blocks().forEach(block -> builder.withhold(block.cause(), block.reason()));
		if (builder.withheld()) return builder.status(Status.WITHHELD).build();

		LuckEvaluator.Evaluation evaluation = strict.evaluation();
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
		LuckEvaluator.MissingRunBounds bounds = strict.bounds();
		if (bounds == null) return builder.status(scored).build();
		builder.missingRunBounds(bounds);
		if (!bounds.material()) return builder.status(scored).build();
		builder.withhold(WithholdCause.MISSING_RUNS_COULD_CHANGE_VERDICT, String.format(
			"%d rejected runs on rated floors (%d with unseen loot) could put the history anywhere from percentile %.3f to %.3f (%s), beyond the verdict's %s",
			bounds.missingRuns(), bounds.unknownOutcomes(), bounds.low().percentile(), bounds.high().percentile(), ordered(bounds.bands()), ordered(bounds.headlineBands())));
		builder.estimate(strict);
		return builder.status(Status.WITHHELD).build();
	}

	private record Block(WithholdCause cause, String reason) {
	}

	/**
	 * One scope of floors scored with every pre-simulation check.
	 *
	 * @param evaluation null when a block stopped scoring
	 * @param bounds     null unless the evaluation scored and the scope has rejected runs
	 */
	private record Scoped(
		List<String> floors,
		LuckScoringInput.History scope,
		double expectedRareDrops,
		HistoryConfigurations.Result configurations,
		List<Block> blocks,
		LuckEvaluator.Evaluation evaluation,
		LuckEvaluator.MissingRunBounds bounds
	) {
	}

	private static Scoped score(
		List<LuckRunRecord> recorded,
		Set<FloorId> floors,
		CatacombsLootDatabase database,
		RuleVariants rules,
		RuleEvidence evidence,
		SelectionPolicy selection,
		int samples,
		long seed,
		boolean simulate
	) {
		List<String> floorNames = floors.stream().sorted().map(FloorId::name).toList();
		List<LuckRunRecord> inScope = recorded.stream().filter(run -> floors.contains(FloorId.parse(run.floor))).toList();
		LuckScoringInput.History scope = LuckScoringInput.history(inScope, database);
		double expected = expectedRareDrops(scope, database, rules);
		List<Block> blocks = new ArrayList<>();
		if (!scope.excluded().isEmpty()) {
			blocks.add(new Block(WithholdCause.KISMET_REROLLS, scope.excluded().size() + " runs on rated floors used or may have used a Kismet reroll;"
				+ " rerolls are not simulated yet and usually follow poor offers, so leaving them out would flatter the rest"));
		}

		HistoryConfigurations.Result configurations = HistoryConfigurations.build(scope.observed(), scope.gapsBefore(), scope.runModifiers(),
			database, rules, evidence, selection, KISMET);
		if (!configurations.scorable()) {
			boolean contradicted = configurations.baselinePlan() != null && configurations.baselinePlan().contradicted();
			WithholdCause cause = contradicted ? WithholdCause.METER_CONTRADICTION : WithholdCause.TOO_MANY_CONFIGURATIONS;
			configurations.withheld().forEach(reason -> blocks.add(new Block(cause, reason)));
		} else {
			for (MeterScenarioPlanner.Window window : configurations.baselinePlan().windows()) {
				if (window.kind() != MeterScenarioPlanner.WindowKind.UNEXPLAINED_GAIN) continue;
				blocks.add(new Block(WithholdCause.UNRECORDED_RUNS, "the " + window.floor() + " RNG Meter shows progress around "
					+ scope.runs().get(window.startRun()).luckRunId() + " that the recorded runs do not explain; how many runs DRT missed and what they dropped is unknown"));
			}
			for (String note : configurations.baselinePlan().notes()) {
				if (!note.startsWith(MeterScenarioPlanner.UNEXPLAINED_GAIN_NOTE)) continue;
				blocks.add(new Block(WithholdCause.UNRECORDED_RUNS, describeGain(note.substring(MeterScenarioPlanner.UNEXPLAINED_GAIN_NOTE.length()), scope)
					+ "; how many runs DRT missed and what they dropped is unknown"));
			}
		}
		if (!blocks.isEmpty() || !simulate) return new Scoped(floorNames, scope, expected, configurations, blocks, null, null);

		List<Configuration> all = configurations.configurations();
		LuckEvaluator.Evaluation evaluation = LuckEvaluator.evaluate(database, scope.observed(), all.get(0), all.subList(1, all.size()), samples, seed);
		boolean scored = evaluation.verdict() == LuckEvaluator.Verdict.PROVISIONAL || evaluation.verdict() == LuckEvaluator.Verdict.UNRESOLVED;
		if (!scored || scope.rejected().isEmpty()) return new Scoped(floorNames, scope, expected, configurations, blocks, evaluation, null);

		Map<String, LuckRunRecord> byId = new HashMap<>();
		for (LuckRunRecord run : inScope) byId.put(run.luckRunId, run);
		List<LuckEvaluator.MissingRun> missing = new ArrayList<>();
		for (LuckScoringInput.Rejected run : scope.rejected()) missing.add(LuckScoringInput.missingRun(byId.get(run.luckRunId()), database));
		LuckEvaluator.MissingRunBounds bounds = LuckEvaluator.missingRunBounds(database, scope.observed(), evaluation, missing, samples, seed ^ 0x6d697373L);
		return new Scoped(floorNames, scope, expected, configurations, blocks, evaluation, bounds);
	}

	/** Why a history withheld for {@code cause} gets no estimate. */
	private static String unavailableBecause(WithholdCause cause) {
		return switch (cause) {
			case UNATTRIBUTED_RUNS -> "some runs have no readable floor, so they cannot be placed on any floor's range";
			case KISMET_REROLLS -> "Kismet rerolls are not simulated yet, and a rerolled run's loot is not an ordinary roll";
			case UNRECORDED_RUNS -> "the RNG Meter shows runs DRT did not record, and how many there were is unknown";
			case METER_CONTRADICTION -> "the RNG Meter readings contradict the recorded runs";
			case TOO_MANY_CONFIGURATIONS -> "too many unknown modifier and meter combinations to score";
			case INCOMPLETE_RECORDS -> "a run without complete chest offers reached the engine";
			case NO_ELIGIBLE_FLOOR, MISSING_RUNS_COULD_CHANGE_VERDICT -> "no estimate could be made";
		};
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
		private Estimate estimate;
		private String estimateUnavailable;

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

		boolean onlyEstimableCauses() {
			return ESTIMABLE.containsAll(causes);
		}

		/** A range from a scope's worst-selection bound, or the reason there is none. */
		void estimate(Scoped scoped) {
			if (!scoped.blocks().isEmpty()) {
				estimateUnavailable = unavailableBecause(scoped.blocks().get(0).cause());
				return;
			}
			LuckEvaluator.Evaluation scope = scoped.evaluation();
			if (scope != null && scope.verdict() == LuckEvaluator.Verdict.INSUFFICIENT_DATA) {
				estimateUnavailable = String.format("the captured runs on %s expect only %.2f rare drops; even an estimate needs %.0f",
					String.join(", ", scoped.floors()), scoped.expectedRareDrops(), RareOutcomeGate.DEFAULT_MINIMUM_EXPECTED);
				return;
			}
			LuckEvaluator.MissingRunBounds limits = scoped.bounds();
			if (scope == null || limits == null) {
				estimateUnavailable = "no estimate could be made";
				return;
			}
			EnumSet<LuckEvaluator.Band> reached = EnumSet.copyOf(limits.bands());
			reached.addAll(limits.headlineBands());
			List<LuckEvaluator.Band> ordered = List.copyOf(reached);
			estimate = new Estimate(scoped.floors(), scoped.scope().runs().size(), limits.missingRuns(), limits.unknownOutcomes(),
				ordered.get(0), ordered.get(ordered.size() - 1),
				Math.min(limits.low().percentile(), scope.minPercentile()), Math.max(limits.high().percentile(), scope.maxPercentile()));
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
			if (status == Status.WITHHELD && estimate == null && estimateUnavailable == null) {
				estimateUnavailable = causes.stream().filter(cause -> !ESTIMABLE.contains(cause)).findFirst()
					.map(LuckHistoryEvaluation::unavailableBecause).orElse("no estimate could be made");
			}
			return new LuckHistoryEvaluation(status, history.recorded(), history.runs().size(), history.rejected().size(), history.excluded().size(),
				expectedRareDrops, sufficient, coverage,
				history.runs(), history.rejected(), history.excluded(), reasons, caveats, historyCaveats, snapshots,
				plan == null ? List.of() : plan.windows(),
				plan == null ? List.of() : plan.notes(),
				configurations == null ? List.of() : configurations.dropped(),
				List.copyOf(causes), withheld, evaluation, bounds, estimate, estimateUnavailable);
		}
	}
}
