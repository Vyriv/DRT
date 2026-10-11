package dev.vy.drt.luck.stats;

import dev.vy.drt.luck.FloorId;
import dev.vy.drt.luck.ScoreTier;
import dev.vy.drt.luck.data.CatacombsLootDatabase;
import dev.vy.drt.luck.data.ChestTable;
import dev.vy.drt.luck.data.FloorStatus;
import dev.vy.drt.luck.data.KnownGap;
import dev.vy.drt.luck.data.LootEntry;
import dev.vy.drt.luck.meter.MeterEvent;
import dev.vy.drt.luck.meter.RngMeterState;
import dev.vy.drt.luck.rules.ModifierPreset;
import dev.vy.drt.luck.rules.ResolvedModifiers;
import dev.vy.drt.luck.rules.RuleEvidence;
import dev.vy.drt.luck.rules.RuleVariants;
import dev.vy.drt.luck.sim.HistorySimulator;
import dev.vy.drt.luck.sim.KismetPolicy;
import dev.vy.drt.luck.sim.RunContext;
import dev.vy.drt.luck.sim.RunOutcome;
import dev.vy.drt.luck.sim.RunSimulator;
import dev.vy.drt.luck.sim.SelectionPolicy;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SplittableRandom;
import java.util.TreeSet;
import java.util.function.IntFunction;

/**
 * Scores an observed history against simulated alternatives under every plausible configuration.
 * A score is provisional only when all configurations, including simulation error, fall in the
 * same luck band; otherwise it is unresolved. No floor is VERIFIED yet, so no score is final.
 */
public final class LuckEvaluator {
	/** Percentile standard errors either side used when checking band stability. */
	public static final double BAND_ERROR_MULTIPLIER = 2.0;
	/** Share of the extreme-selection distribution left outside each missing-run limit. */
	public static final double MISSING_RUN_TAIL = 0.05;
	private static final double TIE_TOLERANCE = 1e-9;

	private LuckEvaluator() {
	}

	public enum Verdict {
		/** Too few rare drops expected for a meaningful percentile. */
		INSUFFICIENT_DATA,
		/** Some runs lack complete chest offers, so appearance cannot be scored. */
		INCOMPLETE_RECORDS,
		/** Every plausible configuration gives the same band. */
		PROVISIONAL,
		/** Plausible configurations or simulation error disagree on the band. */
		UNRESOLVED
	}

	public enum Band {
		VERY_UNLUCKY, UNLUCKY, TYPICAL, LUCKY, VERY_LUCKY;

		public static Band of(double percentile) {
			if (percentile < 0.05) return VERY_UNLUCKY;
			if (percentile < 0.25) return UNLUCKY;
			if (percentile <= 0.75) return TYPICAL;
			if (percentile <= 0.95) return LUCKY;
			return VERY_LUCKY;
		}
	}

	/**
	 * @param modifiers         modifiers for every run without its own entry in {@code runModifiers}
	 * @param runModifiers      per-run modifiers in history order, or empty when every run uses {@code modifiers}
	 * @param meterAssumptions  assumed meter states where the real state was not read, applied after
	 *                          the readings at the same run so they can replace a forward-simulated meter
	 */
	public record Configuration(
		String label,
		ResolvedModifiers modifiers,
		RuleVariants rules,
		Map<FloorId, RngMeterState> meterStart,
		SelectionPolicy selection,
		KismetPolicy kismet,
		List<ResolvedModifiers> runModifiers,
		List<MeterEvent> meterAssumptions
	) {
		public Configuration {
			meterStart = Map.copyOf(meterStart);
			runModifiers = List.copyOf(runModifiers);
			meterAssumptions = List.copyOf(meterAssumptions);
		}

		public Configuration(String label, ResolvedModifiers modifiers, RuleVariants rules, Map<FloorId, RngMeterState> meterStart,
			SelectionPolicy selection, KismetPolicy kismet) {
			this(label, modifiers, rules, meterStart, selection, kismet, List.of(), List.of());
		}

		public ResolvedModifiers modifiersFor(int runIndex) {
			return runModifiers.isEmpty() ? modifiers : runModifiers.get(runIndex);
		}

		/** One simulator per distinct modifier set, indexed by run. */
		IntFunction<RunSimulator> simulators(CatacombsLootDatabase database, int runCount) {
			if (!runModifiers.isEmpty() && runModifiers.size() != runCount) {
				throw new IllegalArgumentException(runModifiers.size() + " run modifiers for " + runCount + " runs");
			}
			Map<ResolvedModifiers, RunSimulator> byModifiers = new HashMap<>();
			RunSimulator[] perRun = new RunSimulator[runCount];
			for (int i = 0; i < runCount; i++) {
				perRun[i] = byModifiers.computeIfAbsent(modifiersFor(i), resolved -> new RunSimulator(database, resolved, rules));
			}
			return index -> perRun[index];
		}
	}

	public record ConfigurationResult(Configuration configuration, double observedStatistic, MidPPercentile.Result percentile) {
	}

	/**
	 * A run on a scored floor whose chests were not fully captured.
	 *
	 * @param seenKeys       rolled items seen in its original offers, a lower limit on what it dropped
	 * @param outcomeKnown   every eligible chest's original offer was seen, so {@code seenKeys} is exact
	 * @param contextAssumed grade or score unknown or contradicted: {@code run} is an S+ stand-in and the seen items set no lower limit
	 */
	public record MissingRun(String id, RunContext run, ResolvedModifiers modifiers, Set<String> seenKeys, boolean outcomeKnown, boolean contextAssumed) {
		public MissingRun {
			seenKeys = Set.copyOf(seenKeys);
		}
	}

	/**
	 * How far runs left out of the score could move the verdict. Unknown outcomes are bounded by the
	 * worst selection a capture pattern could make: on each floor, the missing runs being the luckiest
	 * (or unluckiest) of all that floor's runs, at the {@link #MISSING_RUN_TAIL} extreme of what that
	 * selection gives when nothing is lucky. Runs whose loot was fully seen count exactly.
	 *
	 * @param low           percentile of the whole history with the missing runs at their low limit
	 * @param high          percentile with them at their high limit
	 * @param bands         every band the two limits reach, including simulation error
	 * @param headlineBands every band the evaluation itself reaches
	 */
	public record MissingRunBounds(
		int missingRuns,
		int unknownOutcomes,
		double lowStatistic,
		double highStatistic,
		MidPPercentile.Result low,
		MidPPercentile.Result high,
		Set<Band> bands,
		Set<Band> headlineBands
	) {
		public MissingRunBounds {
			bands = Set.copyOf(bands);
			headlineBands = Set.copyOf(headlineBands);
		}

		/** True when the missing runs could put the history in a band the verdict does not already allow. */
		public boolean material() {
			return !headlineBands.containsAll(bands);
		}
	}

	public record Evaluation(
		Verdict verdict,
		ConfigurationResult baseline,
		List<ConfigurationResult> alternatives,
		double minPercentile,
		double maxPercentile,
		List<String> reasons
	) {
		public Evaluation {
			alternatives = List.copyOf(alternatives);
			reasons = List.copyOf(reasons);
		}

		public Band band() {
			return baseline == null ? null : Band.of(baseline.percentile().percentile());
		}
	}

	public static Evaluation evaluate(
		CatacombsLootDatabase database,
		List<ObservedRun> observed,
		Configuration baseline,
		List<Configuration> alternatives,
		int samples,
		long seed
	) {
		List<String> reasons = new ArrayList<>(dataCaveats(database, observed));
		String sRunCaveat = sRunGainCaveat(observed, baseline, alternatives);
		if (sRunCaveat != null) reasons.add(sRunCaveat);
		long incomplete = observed.stream().filter(run -> !run.offersComplete()).count();
		if (incomplete > 0) {
			reasons.add(incomplete + " of " + observed.size() + " runs lack complete chest offers");
			return new Evaluation(Verdict.INCOMPLETE_RECORDS, null, List.of(), Double.NaN, Double.NaN, reasons);
		}

		List<RunContext> runs = observed.stream().map(ObservedRun::run).toList();
		double expectedRare = RareOutcomeGate.expectedRareAppearances(runs, baseline.simulators(database, runs.size()), RareOutcomeGate.DEFAULT_RARE_THRESHOLD);
		if (expectedRare < RareOutcomeGate.DEFAULT_MINIMUM_EXPECTED) {
			reasons.add(String.format("only %.2f rare drops expected; need %.0f", expectedRare, RareOutcomeGate.DEFAULT_MINIMUM_EXPECTED));
			return new Evaluation(Verdict.INSUFFICIENT_DATA, null, List.of(), Double.NaN, Double.NaN, reasons);
		}

		SplittableRandom seeds = new SplittableRandom(seed);
		long nullSeed = seeds.nextLong();
		ConfigurationResult base = score(database, observed, runs, baseline, samples, nullSeed);
		List<ConfigurationResult> results = new ArrayList<>();
		for (Configuration configuration : alternatives) {
			// Common random numbers: every configuration uses the same seed so differences reflect the configuration.
			results.add(score(database, observed, runs, configuration, samples, nullSeed));
		}

		List<MidPPercentile.Result> all = new ArrayList<>();
		all.add(base.percentile());
		results.forEach(result -> all.add(result.percentile()));
		double min = all.stream().mapToDouble(MidPPercentile.Result::percentile).min().orElseThrow();
		double max = all.stream().mapToDouble(MidPPercentile.Result::percentile).max().orElseThrow();
		Verdict verdict = interpret(all);
		if (verdict == Verdict.UNRESOLVED) {
			reasons.add(String.format("plausible configurations span percentiles %.3f to %.3f (bands %s)", min, max, bandsCovered(all)));
		}
		return new Evaluation(verdict, base, results, min, max, reasons);
	}

	/** PROVISIONAL when every result's error interval sits inside one band, otherwise UNRESOLVED. */
	public static Verdict interpret(List<MidPPercentile.Result> results) {
		return bandsCovered(results).size() == 1 ? Verdict.PROVISIONAL : Verdict.UNRESOLVED;
	}

	/**
	 * Baseline uses known modifiers with unknown fields at their lowest; alternatives cover the other
	 * modifier extremes, every one-at-a-time rule variant and every meter scenario.
	 */
	public static List<Configuration> plausibleConfigurations(
		ModifierPreset preset,
		RuleVariants rules,
		List<Map<FloorId, RngMeterState>> meterScenarios,
		SelectionPolicy selection,
		KismetPolicy kismet
	) {
		return plausibleConfigurations(preset, rules, RuleEvidence.RECORDED, meterScenarios, selection, kismet);
	}

	/**
	 * As above, with the meter rules left plausible by the caller's readings ({@code MeterRuleEvidence}).
	 * A refuted Pity rule only drops out when the preset's Pity level is known and nonzero; unknown
	 * Pity still spans its modifier extremes and keeps every Pity rule.
	 */
	public static List<Configuration> plausibleConfigurations(
		ModifierPreset preset,
		RuleVariants rules,
		RuleEvidence evidence,
		List<Map<FloorId, RngMeterState>> meterScenarios,
		SelectionPolicy selection,
		KismetPolicy kismet
	) {
		if (meterScenarios.isEmpty()) throw new IllegalArgumentException("At least one meter scenario required");
		List<ResolvedModifiers> extremes = preset.plausibleExtremes();
		ResolvedModifiers lowest = extremes.get(0);
		Map<FloorId, RngMeterState> firstMeter = meterScenarios.get(0);
		List<Configuration> out = new ArrayList<>();
		out.add(new Configuration("baseline", lowest, rules, firstMeter, selection, kismet));
		for (int i = 1; i < extremes.size(); i++) {
			out.add(new Configuration("modifiers " + extremes.get(i), extremes.get(i), rules, firstMeter, selection, kismet));
		}
		for (RuleVariants variant : evidence.alternatives(rules, preset.pityLevel())) {
			out.add(new Configuration("rules " + variant, lowest, variant, firstMeter, selection, kismet));
		}
		for (int i = 1; i < meterScenarios.size(); i++) {
			out.add(new Configuration("meter scenario " + i, lowest, rules, meterScenarios.get(i), selection, kismet));
		}
		return out;
	}

	private static ConfigurationResult score(
		CatacombsLootDatabase database,
		List<ObservedRun> observed,
		List<RunContext> runs,
		Configuration configuration,
		int samples,
		long seed
	) {
		IntFunction<RunSimulator> simulators = configuration.simulators(database, runs.size());
		double statistic = SurprisalStatistic.ofObserved(observed, simulators);
		double[] nullSamples = NullDistribution.sample(simulators, runs, configuration.meterStart(), events(observed, configuration),
			configuration.selection(), configuration.kismet(), samples, seed);
		return new ConfigurationResult(configuration, statistic, MidPPercentile.compute(statistic, nullSamples, TIE_TOLERANCE));
	}

	/** Readings first, then the configuration's assumptions, which replace a simulated meter at the same run. */
	private static List<MeterEvent> events(List<ObservedRun> observed, Configuration configuration) {
		List<MeterEvent> events = new ArrayList<>(NullDistribution.observedMeterEvents(observed));
		events.addAll(configuration.meterAssumptions());
		events.sort(Comparator.comparingInt(MeterEvent::beforeRun));
		return events;
	}

	/**
	 * Bounds the whole history, scored runs plus {@code missing}, under the evaluation's baseline
	 * configuration. Missing runs are simulated without meter boost and independently of the scored
	 * runs' meter, whose gaps the configuration already covers with meter scenarios.
	 */
	public static MissingRunBounds missingRunBounds(
		CatacombsLootDatabase database,
		List<ObservedRun> observed,
		Evaluation evaluation,
		List<MissingRun> missing,
		int samples,
		long seed
	) {
		if (evaluation.baseline() == null) throw new IllegalArgumentException("Missing-run bounds need a scored evaluation");
		Configuration configuration = evaluation.baseline().configuration();
		List<RunContext> runs = observed.stream().map(ObservedRun::run).toList();
		IntFunction<RunSimulator> simulators = configuration.simulators(database, runs.size());
		List<MeterEvent> events = events(observed, configuration);

		Map<ResolvedModifiers, RunSimulator> missingSimulators = new HashMap<>();
		Map<FloorId, Integer> unknownByFloor = new EnumMap<>(FloorId.class);
		double exact = 0.0;
		double seenLower = 0.0;
		for (MissingRun run : missing) {
			RunSimulator simulator = missingSimulators.computeIfAbsent(run.modifiers(), modifiers -> new RunSimulator(database, modifiers, configuration.rules()));
			double seen = SurprisalStatistic.ofObserved(List.of(new ObservedRun(run.run(), run.seenKeys(), Set.of(), true)), simulator);
			if (run.outcomeKnown()) {
				exact += seen;
			} else {
				if (!run.contextAssumed()) seenLower += seen;
				unknownByFloor.merge(run.run().floor(), 1, Integer::sum);
			}
		}

		SplittableRandom seeds = new SplittableRandom(seed);
		double[] whole = new double[samples];
		double[] luckiest = new double[samples];
		double[] unluckiest = new double[samples];
		for (int i = 0; i < samples; i++) {
			List<RunOutcome> history = HistorySimulator.simulate(runs, simulators, configuration.meterStart(), events,
				configuration.selection(), configuration.kismet(), seeds.nextLong());
			SplittableRandom random = new SplittableRandom(seeds.nextLong());
			Map<FloorId, List<Double>> byFloor = new EnumMap<>(FloorId.class);
			double total = 0.0;
			for (int j = 0; j < history.size(); j++) {
				double value = SurprisalStatistic.of(List.of(history.get(j)), simulators.apply(j));
				total += value;
				if (unknownByFloor.containsKey(runs.get(j).floor())) byFloor.computeIfAbsent(runs.get(j).floor(), floor -> new ArrayList<>()).add(value);
			}
			for (MissingRun run : missing) {
				RunSimulator simulator = missingSimulators.get(run.modifiers());
				RunOutcome outcome = simulator.simulate(run.run(), RngMeterState.empty(run.run().floor()), configuration.selection(), KismetPolicy.never(), random);
				double value = SurprisalStatistic.of(List.of(outcome), simulator);
				total += value;
				if (unknownByFloor.containsKey(run.run().floor())) byFloor.computeIfAbsent(run.run().floor(), floor -> new ArrayList<>()).add(value);
			}
			whole[i] = total;
			for (Map.Entry<FloorId, List<Double>> floor : byFloor.entrySet()) {
				List<Double> values = floor.getValue();
				values.sort(null);
				int k = unknownByFloor.get(floor.getKey());
				for (int m = 0; m < k; m++) {
					unluckiest[i] += values.get(m);
					luckiest[i] += values.get(values.size() - 1 - m);
				}
			}
		}

		double observedStatistic = evaluation.baseline().observedStatistic() + exact;
		double low = observedStatistic + Math.max(quantile(unluckiest, MISSING_RUN_TAIL), seenLower);
		double high = observedStatistic + Math.max(quantile(luckiest, 1.0 - MISSING_RUN_TAIL), seenLower);
		MidPPercentile.Result lowResult = MidPPercentile.compute(low, whole, TIE_TOLERANCE);
		MidPPercentile.Result highResult = MidPPercentile.compute(high, whole, TIE_TOLERANCE);

		List<MidPPercentile.Result> headline = new ArrayList<>();
		headline.add(evaluation.baseline().percentile());
		evaluation.alternatives().forEach(result -> headline.add(result.percentile()));
		Band from = Band.of(Math.max(0.0, lowResult.percentile() - BAND_ERROR_MULTIPLIER * lowResult.monteCarloStandardError()));
		Band to = Band.of(Math.min(1.0, highResult.percentile() + BAND_ERROR_MULTIPLIER * highResult.monteCarloStandardError()));
		Set<Band> bands = EnumSet.noneOf(Band.class);
		for (Band band : Band.values()) {
			if (band.ordinal() >= from.ordinal() && band.ordinal() <= to.ordinal()) bands.add(band);
		}
		int unknown = unknownByFloor.values().stream().mapToInt(Integer::intValue).sum();
		return new MissingRunBounds(missing.size(), unknown, low, high, lowResult, highResult, bands, bandsCovered(headline));
	}

	private static double quantile(double[] values, double q) {
		double[] sorted = values.clone();
		Arrays.sort(sorted);
		int index = (int) Math.round(q * (sorted.length - 1));
		return sorted[Math.max(0, Math.min(sorted.length - 1, index))];
	}

	private static Set<Band> bandsCovered(List<MidPPercentile.Result> results) {
		Set<Band> bands = EnumSet.noneOf(Band.class);
		for (MidPPercentile.Result result : results) {
			double margin = BAND_ERROR_MULTIPLIER * result.monteCarloStandardError();
			Band low = Band.of(Math.max(0.0, result.percentile() - margin));
			Band high = Band.of(Math.min(1.0, result.percentile() + margin));
			for (Band band : Band.values()) {
				if (band.ordinal() >= low.ordinal() && band.ordinal() <= high.ordinal()) bands.add(band);
			}
		}
		return bands;
	}

	private static List<String> dataCaveats(CatacombsLootDatabase database, List<ObservedRun> observed) {
		Set<FloorId> floors = EnumSet.noneOf(FloorId.class);
		Set<String> unmodelled = new TreeSet<>();
		for (ObservedRun run : observed) {
			floors.add(run.run().floor());
			for (String key : run.appearedKeys()) {
				if (!rolledOnFloor(database, run.run().floor(), key)) unmodelled.add(run.run().floor() + " " + key);
			}
		}
		List<String> out = new ArrayList<>();
		for (FloorId floor : floors) {
			FloorStatus status = database.floor(floor).status();
			if (status != FloorStatus.VERIFIED) out.add(floor + " loot data is " + status);
		}
		for (KnownGap gap : database.knownGaps()) {
			if (gap.floors().stream().anyMatch(floors::contains)) out.add("not modelled: " + gap.id());
		}
		if (!unmodelled.isEmpty()) out.add("ignored items outside the loot tables: " + unmodelled);
		return out;
	}

	/** Names the unsettled S-run meter gain rule whenever S runs are scored under more than one candidate. */
	private static String sRunGainCaveat(List<ObservedRun> observed, Configuration baseline, List<Configuration> alternatives) {
		if (observed.stream().noneMatch(run -> run.run().scoreTier() == ScoreTier.S)) return null;
		Set<RuleVariants.SRunMeterGain> rules = EnumSet.of(baseline.rules().sRunMeterGain());
		alternatives.forEach(configuration -> rules.add(configuration.rules().sRunMeterGain()));
		if (rules.size() < 2) return null;
		return "S-run meter gain rule is PROVISIONAL: " + baseline.rules().sRunMeterGain() + " by default, scored against " + rules;
	}

	private static boolean rolledOnFloor(CatacombsLootDatabase database, FloorId floor, String key) {
		for (ChestTable chest : database.floor(floor).chests().values()) {
			LootEntry entry = chest.entry(key);
			if (entry != null && entry.role() == LootEntry.Role.WEIGHTED) return true;
		}
		return false;
	}
}
