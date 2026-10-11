package dev.vy.drt.luck.stats;

import dev.vy.drt.luck.FloorId;
import dev.vy.drt.luck.ScoreTier;
import dev.vy.drt.luck.data.CatacombsLootDatabase;
import dev.vy.drt.luck.data.ChestTable;
import dev.vy.drt.luck.data.FloorStatus;
import dev.vy.drt.luck.data.KnownGap;
import dev.vy.drt.luck.data.LootEntry;
import dev.vy.drt.luck.meter.RngMeterState;
import dev.vy.drt.luck.rules.ModifierPreset;
import dev.vy.drt.luck.rules.ResolvedModifiers;
import dev.vy.drt.luck.rules.RuleVariants;
import dev.vy.drt.luck.sim.KismetPolicy;
import dev.vy.drt.luck.sim.RunContext;
import dev.vy.drt.luck.sim.RunSimulator;
import dev.vy.drt.luck.sim.SelectionPolicy;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SplittableRandom;
import java.util.TreeSet;

/**
 * Scores an observed history against simulated alternatives under every plausible configuration.
 * A score is provisional only when all configurations, including simulation error, fall in the
 * same luck band; otherwise it is unresolved. No floor is VERIFIED yet, so no score is final.
 */
public final class LuckEvaluator {
	/** Percentile standard errors either side used when checking band stability. */
	public static final double BAND_ERROR_MULTIPLIER = 2.0;
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

	public record Configuration(
		String label,
		ResolvedModifiers modifiers,
		RuleVariants rules,
		Map<FloorId, RngMeterState> meterStart,
		SelectionPolicy selection,
		KismetPolicy kismet
	) {
		public Configuration {
			meterStart = Map.copyOf(meterStart);
		}
	}

	public record ConfigurationResult(Configuration configuration, double observedStatistic, MidPPercentile.Result percentile) {
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
		RunSimulator baselineSimulator = new RunSimulator(database, baseline.modifiers(), baseline.rules());
		double expectedRare = RareOutcomeGate.expectedRareAppearances(runs, baselineSimulator, RareOutcomeGate.DEFAULT_RARE_THRESHOLD);
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
		return plausibleConfigurations(preset, rules, RuleVariants.PLAUSIBLE_S_RUN_METER_GAINS, meterScenarios, selection, kismet);
	}

	/** As above, with the S-run gain rules left plausible by the caller's meter readings ({@code SRunMeterGainEvidence}). */
	public static List<Configuration> plausibleConfigurations(
		ModifierPreset preset,
		RuleVariants rules,
		Set<RuleVariants.SRunMeterGain> plausibleSRunGains,
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
		for (RuleVariants variant : rules.oneAtATimeAlternatives(plausibleSRunGains)) {
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
		RunSimulator simulator = new RunSimulator(database, configuration.modifiers(), configuration.rules());
		double statistic = SurprisalStatistic.ofObserved(observed, simulator);
		double[] nullSamples = NullDistribution.sample(simulator, runs, configuration.meterStart(), NullDistribution.observedMeterEvents(observed),
			configuration.selection(), configuration.kismet(), samples, seed);
		return new ConfigurationResult(configuration, statistic, MidPPercentile.compute(statistic, nullSamples, TIE_TOLERANCE));
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
