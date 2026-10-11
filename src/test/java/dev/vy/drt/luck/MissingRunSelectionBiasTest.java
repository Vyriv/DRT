package dev.vy.drt.luck;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.vy.drt.luck.data.CatacombsLootDatabase;
import dev.vy.drt.luck.meter.RngMeterState;
import dev.vy.drt.luck.rules.ResolvedModifiers;
import dev.vy.drt.luck.rules.RuleVariants;
import dev.vy.drt.luck.sim.HistorySimulator;
import dev.vy.drt.luck.sim.KismetPolicy;
import dev.vy.drt.luck.sim.RunContext;
import dev.vy.drt.luck.sim.RunOutcome;
import dev.vy.drt.luck.sim.RunSimulator;
import dev.vy.drt.luck.sim.SelectionPolicy;
import dev.vy.drt.luck.stats.LuckEvaluator;
import dev.vy.drt.luck.stats.LuckEvaluator.Configuration;
import dev.vy.drt.luck.stats.MidPPercentile;
import dev.vy.drt.luck.stats.NullDistribution;
import dev.vy.drt.luck.stats.ObservedRun;
import dev.vy.drt.luck.stats.SurprisalStatistic;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SplittableRandom;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

/**
 * Why 90% coverage alone does not make a score trustworthy, and what the missing-run bounds do about
 * it. Every history here is drawn with no luck at all, so an honest percentile centres on 0.5.
 */
class MissingRunSelectionBiasTest {
	private static final CatacombsLootDatabase DATABASE = LuckTestData.defaults();
	private static final RunSimulator SIM = new RunSimulator(DATABASE, ResolvedModifiers.NONE, RuleVariants.defaults());
	private static final SelectionPolicy TOP = SelectionPolicy.highestTier();
	private static final RunContext RUN = RunContext.of(FloorId.F7, 300);
	private static final int RUNS = 60;
	private static final int MISSING = 6;
	private static final int HISTORIES = 24;
	private static final int SAMPLES = 300;

	private static List<RunOutcome> history(long seed) {
		return new HistorySimulator(SIM).simulate(Collections.nCopies(RUNS, RUN), RngMeterState.empty(FloorId.F7), TOP, KismetPolicy.never(), seed);
	}

	private static double surprisal(RunOutcome outcome) {
		return SurprisalStatistic.of(List.of(outcome), SIM);
	}

	/** Indices of the {@code count} runs with the rarest drops. */
	private static List<Integer> luckiest(List<RunOutcome> outcomes, int count) {
		return IntStream.range(0, outcomes.size()).boxed()
			.sorted(Comparator.comparingDouble((Integer i) -> surprisal(outcomes.get(i))).reversed())
			.limit(count).toList();
	}

	private static List<Integer> random(int count, long seed) {
		List<Integer> all = new ArrayList<>(IntStream.range(0, RUNS).boxed().toList());
		Collections.shuffle(all, new java.util.Random(seed));
		return all.subList(0, count);
	}

	private static List<RunOutcome> without(List<RunOutcome> outcomes, List<Integer> dropped) {
		List<RunOutcome> out = new ArrayList<>();
		for (int i = 0; i < outcomes.size(); i++) if (!dropped.contains(i)) out.add(outcomes.get(i));
		return out;
	}

	private static double[] nullFor(int runs) {
		return NullDistribution.sample(SIM, Collections.nCopies(runs, RUN), RngMeterState.empty(FloorId.F7), TOP, KismetPolicy.never(), 600, 99L);
	}

	private static double percentile(List<RunOutcome> outcomes, double[] nullSamples) {
		return MidPPercentile.compute(SurprisalStatistic.of(outcomes, SIM), nullSamples, 1e-9).percentile();
	}

	private static LuckEvaluator.MissingRun unseen(int index) {
		return new LuckEvaluator.MissingRun("missing-" + index, RUN, ResolvedModifiers.NONE, Set.of(), false, false);
	}

	private static LuckEvaluator.Evaluation evaluate(List<RunOutcome> scored, long seed) {
		List<ObservedRun> observed = scored.stream().map(ObservedRun::from).toList();
		Configuration baseline = new Configuration("baseline", ResolvedModifiers.NONE, RuleVariants.defaults(), Map.of(), TOP, KismetPolicy.never());
		return LuckEvaluator.evaluate(DATABASE, observed, baseline, List.of(), SAMPLES, seed);
	}

	@Test
	void lootDependentCaptureBiasesTheScoreAtTheSameCoverageAsRandomLoss() {
		double[] remainingNull = nullFor(RUNS - MISSING);
		double lootDependent = 0.0;
		double random = 0.0;
		for (int h = 0; h < HISTORIES; h++) {
			List<RunOutcome> outcomes = history(1_000L + h);
			lootDependent += percentile(without(outcomes, luckiest(outcomes, MISSING)), remainingNull);
			random += percentile(without(outcomes, random(MISSING, 2_000L + h)), remainingNull);
		}
		lootDependent /= HISTORIES;
		random /= HISTORIES;
		assertTrue(random > 0.35 && random < 0.65, "losing runs at random leaves an honest percentile: " + random);
		assertTrue(lootDependent < 0.2, "losing the luckiest 10% makes a player with no luck look unlucky: " + lootDependent);
	}

	@Test
	void theBoundsCoverTheWholeHistoryWhenTheLuckiestRunsAreTheOnesMissing() {
		double[] wholeNull = nullFor(RUNS);
		List<LuckEvaluator.MissingRun> missing = IntStream.range(0, MISSING).mapToObj(MissingRunSelectionBiasTest::unseen).toList();
		int covered = 0;
		int withheld = 0;
		int scored = 0;
		for (int h = 0; h < HISTORIES; h++) {
			List<RunOutcome> outcomes = history(1_000L + h);
			List<RunOutcome> captured = without(outcomes, luckiest(outcomes, MISSING));
			LuckEvaluator.Evaluation evaluation = evaluate(captured, 3_000L + h);
			if (evaluation.baseline() == null) continue;
			scored++;
			List<ObservedRun> observed = captured.stream().map(ObservedRun::from).toList();
			LuckEvaluator.MissingRunBounds bounds = LuckEvaluator.missingRunBounds(DATABASE, observed, evaluation, missing, SAMPLES, 4_000L + h);
			double truth = percentile(outcomes, wholeNull);
			double margin = 2 * bounds.high().monteCarloStandardError() + 0.02;
			if (truth <= bounds.high().percentile() + margin && truth >= bounds.low().percentile() - margin) covered++;
			if (bounds.material()) withheld++;
		}
		assertEquals(HISTORIES, scored, "60 S+ F7 runs expect enough rare drops to score");
		assertTrue(covered >= HISTORIES - 3, "the whole history's percentile lies inside the bounds in " + covered + " of " + HISTORIES);
		assertTrue(withheld >= HISTORIES * 3 / 4, "losing 10% of runs to their loot should usually withhold the headline: " + withheld + " of " + HISTORIES);
	}

	@Test
	void boundsWidenWithEachUnseenRunAndCollapseWhenTheLootWasSeen() {
		List<RunOutcome> outcomes = history(5_000L);
		List<RunOutcome> captured = outcomes.subList(0, RUNS - MISSING);
		List<ObservedRun> observed = captured.stream().map(ObservedRun::from).toList();
		LuckEvaluator.Evaluation evaluation = evaluate(captured, 6_000L);

		LuckEvaluator.MissingRunBounds one = LuckEvaluator.missingRunBounds(DATABASE, observed, evaluation, List.of(unseen(0)), SAMPLES, 7L);
		LuckEvaluator.MissingRunBounds six = LuckEvaluator.missingRunBounds(DATABASE, observed, evaluation,
			IntStream.range(0, MISSING).mapToObj(MissingRunSelectionBiasTest::unseen).toList(), SAMPLES, 7L);
		assertTrue(six.highStatistic() - six.lowStatistic() > one.highStatistic() - one.lowStatistic(), one + " vs " + six);
		assertTrue(six.high().percentile() - six.low().percentile() > one.high().percentile() - one.low().percentile());

		List<LuckEvaluator.MissingRun> seen = new ArrayList<>();
		for (int i = RUNS - MISSING; i < RUNS; i++) {
			seen.add(new LuckEvaluator.MissingRun("seen-" + i, RUN, ResolvedModifiers.NONE, outcomes.get(i).appearedKeys(), true, false));
		}
		LuckEvaluator.MissingRunBounds exact = LuckEvaluator.missingRunBounds(DATABASE, observed, evaluation, seen, SAMPLES, 7L);
		assertEquals(0, exact.unknownOutcomes());
		assertEquals(exact.lowStatistic(), exact.highStatistic(), 1e-9, "fully seen loot is counted, not bounded");
		assertEquals(SurprisalStatistic.of(outcomes, SIM), exact.lowStatistic(), 1e-9, "and adds exactly what those runs dropped");
	}

	@Test
	void seenLootIsALowerLimitForARunThatWasNotFullyCaptured() {
		List<RunOutcome> outcomes = history(8_000L);
		List<RunOutcome> captured = outcomes.subList(0, RUNS - 1);
		List<ObservedRun> observed = captured.stream().map(ObservedRun::from).toList();
		LuckEvaluator.Evaluation evaluation = evaluate(captured, 9_000L);
		RunOutcome partial = outcomes.get(RUNS - 1);
		double seen = surprisal(partial);

		LuckEvaluator.MissingRun lowerLimit = new LuckEvaluator.MissingRun("partial", RUN, ResolvedModifiers.NONE, partial.appearedKeys(), false, false);
		LuckEvaluator.MissingRunBounds bounds = LuckEvaluator.missingRunBounds(DATABASE, observed, evaluation, List.of(lowerLimit), SAMPLES, 10L);
		assertTrue(bounds.lowStatistic() >= evaluation.baseline().observedStatistic() + seen - 1e-9, "never below what was seen");

		LuckEvaluator.MissingRun assumed = new LuckEvaluator.MissingRun("assumed", RUN, ResolvedModifiers.NONE, partial.appearedKeys(), false, true);
		LuckEvaluator.MissingRunBounds stand = LuckEvaluator.missingRunBounds(DATABASE, observed, evaluation, List.of(assumed), SAMPLES, 10L);
		assertTrue(stand.lowStatistic() <= bounds.lowStatistic() + 1e-9, "a stand-in context does not trust the seen items as a lower limit");
	}

	@Test
	void theSameSeedGivesTheSameBounds() {
		List<RunOutcome> captured = history(11_000L).subList(0, RUNS - 2);
		List<ObservedRun> observed = captured.stream().map(ObservedRun::from).toList();
		LuckEvaluator.Evaluation evaluation = evaluate(captured, 12_000L);
		List<LuckEvaluator.MissingRun> missing = List.of(unseen(0), unseen(1));
		long seed = new SplittableRandom(3).nextLong();
		assertEquals(LuckEvaluator.missingRunBounds(DATABASE, observed, evaluation, missing, 100, seed),
			LuckEvaluator.missingRunBounds(DATABASE, observed, evaluation, missing, 100, seed));
	}
}
