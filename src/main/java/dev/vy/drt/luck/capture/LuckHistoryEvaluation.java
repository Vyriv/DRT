package dev.vy.drt.luck.capture;

import dev.vy.drt.luck.data.CatacombsLootDatabase;
import dev.vy.drt.luck.rules.RuleEvidence;
import dev.vy.drt.luck.rules.RuleVariants;
import dev.vy.drt.luck.sim.KismetPolicy;
import dev.vy.drt.luck.sim.SelectionPolicy;
import dev.vy.drt.luck.stats.HistoryConfigurations;
import dev.vy.drt.luck.stats.LuckEvaluator;
import dev.vy.drt.luck.stats.LuckEvaluator.Configuration;
import dev.vy.drt.luck.stats.MeterScenarioPlanner;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Everything a Luck Rating needs to show about a recorded history: which runs were scored, which
 * were rejected or held out and why, the meter windows the engine had to assume, and the verdict.
 * A run is scorable when its own record is complete; the history is statistically sufficient only
 * when the scorable runs together expect at least one rare drop. Neither implies the other.
 *
 * @param evaluation the engine's result, or null when the score was withheld before scoring
 */
public record LuckHistoryEvaluation(
	Status status,
	int recordedRuns,
	int scorableRuns,
	int rejectedRuns,
	int excludedRuns,
	boolean statisticallySufficient,
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
	List<String> withheldReasons,
	LuckEvaluator.Evaluation evaluation
) {
	/** Kismet rerolls are excluded until a matching reroll policy exists, so the null never rerolls. */
	public static final KismetPolicy KISMET = KismetPolicy.never();

	public enum Status {
		NO_RECORDED_RUNS,
		/** Runs were recorded but none can be scored on its own. */
		NO_SCORABLE_RUNS,
		/** Scorable runs exist, but the meter data contradicts them or leaves too many scenarios. */
		WITHHELD,
		/** Scorable runs exist, but too few rare drops are expected for a percentile. */
		INSUFFICIENT_DATA,
		PROVISIONAL,
		UNRESOLVED
	}

	public LuckHistoryEvaluation {
		scored = List.copyOf(scored);
		rejected = List.copyOf(rejected);
		excluded = List.copyOf(excluded);
		rejectionReasonCounts = Map.copyOf(rejectionReasonCounts);
		runCaveatCounts = Map.copyOf(runCaveatCounts);
		historyCaveats = List.copyOf(historyCaveats);
		meterWindows = List.copyOf(meterWindows);
		meterNotes = List.copyOf(meterNotes);
		droppedConfigurations = List.copyOf(droppedConfigurations);
		withheldReasons = List.copyOf(withheldReasons);
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

		List<String> historyCaveats = new ArrayList<>(history.caveats());
		if (!history.excluded().isEmpty()) {
			historyCaveats.add("KISMET_RUNS_EXCLUDED:" + history.excluded().size()
				+ " (rerolls usually follow poor offers, so the remaining runs may look luckier)");
		}
		if (!history.rejected().isEmpty()) {
			historyCaveats.add("RUNS_NOT_SCORED:" + history.rejected().size()
				+ " (if whether a run was fully captured depends on its loot, the score is biased)");
		}
		int snapshots = (int) history.runModifiers().stream().distinct().count();

		Builder builder = new Builder(history, reasons, caveats, historyCaveats, snapshots);
		if (history.recorded() == 0) return builder.build(Status.NO_RECORDED_RUNS, false, null, null, List.of());
		if (history.runs().isEmpty()) {
			return builder.build(Status.NO_SCORABLE_RUNS, false, null, null, List.of("none of the " + history.recorded() + " recorded runs can be scored"));
		}

		HistoryConfigurations.Result configurations = HistoryConfigurations.build(history.observed(), history.gapsBefore(), history.runModifiers(),
			database, rules, evidence, selection, KISMET);
		if (!configurations.scorable()) return builder.build(Status.WITHHELD, false, null, configurations, configurations.withheld());

		List<Configuration> all = configurations.configurations();
		LuckEvaluator.Evaluation evaluation = LuckEvaluator.evaluate(database, history.observed(), all.get(0), all.subList(1, all.size()), samples, seed);
		return switch (evaluation.verdict()) {
			case INSUFFICIENT_DATA -> builder.build(Status.INSUFFICIENT_DATA, false, evaluation, configurations, List.of());
			case PROVISIONAL -> builder.build(Status.PROVISIONAL, true, evaluation, configurations, List.of());
			case UNRESOLVED -> builder.build(Status.UNRESOLVED, true, evaluation, configurations, List.of());
			case INCOMPLETE_RECORDS -> builder.build(Status.WITHHELD, false, evaluation, configurations, evaluation.reasons());
		};
	}

	private record Builder(LuckScoringInput.History history, Map<String, Integer> reasons, Map<String, Integer> caveats, List<String> historyCaveats, int snapshots) {
		LuckHistoryEvaluation build(Status status, boolean sufficient, LuckEvaluator.Evaluation evaluation, HistoryConfigurations.Result configurations, List<String> withheld) {
			MeterScenarioPlanner.Plan plan = configurations == null ? null : configurations.baselinePlan();
			return new LuckHistoryEvaluation(status, history.recorded(), history.runs().size(), history.rejected().size(), history.excluded().size(), sufficient,
				history.runs(), history.rejected(), history.excluded(), reasons, caveats, historyCaveats, snapshots,
				plan == null ? List.of() : plan.windows(),
				plan == null ? List.of() : plan.notes(),
				configurations == null ? List.of() : configurations.dropped(),
				withheld, evaluation);
		}
	}
}
