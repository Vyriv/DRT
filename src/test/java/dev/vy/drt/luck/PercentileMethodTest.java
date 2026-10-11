package dev.vy.drt.luck;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.vy.drt.luck.meter.RngMeterState;
import dev.vy.drt.luck.rules.ResolvedModifiers;
import dev.vy.drt.luck.rules.RuleVariants;
import dev.vy.drt.luck.sim.HistorySimulator;
import dev.vy.drt.luck.sim.KismetPolicy;
import dev.vy.drt.luck.sim.RunContext;
import dev.vy.drt.luck.sim.RunSimulator;
import dev.vy.drt.luck.sim.SelectionPolicy;
import dev.vy.drt.luck.stats.MidPPercentile;
import dev.vy.drt.luck.stats.NullDistribution;
import dev.vy.drt.luck.stats.RareOutcomeGate;
import dev.vy.drt.luck.stats.SurprisalStatistic;
import java.util.Collections;
import java.util.List;
import java.util.SplittableRandom;
import org.junit.jupiter.api.Test;

class PercentileMethodTest {
	private static RunSimulator simulator() {
		return new RunSimulator(LuckTestData.defaults(), ResolvedModifiers.NONE, RuleVariants.defaults());
	}

	@Test
	void tiesCountAsHalf() {
		MidPPercentile.Result allTied = MidPPercentile.compute(5, new double[] {5, 5, 5, 5}, 1e-9);
		assertEquals(0.5, allTied.percentile());
		assertEquals(4, allTied.ties());

		assertEquals(0.5, MidPPercentile.compute(2.5, new double[] {1, 2, 3, 4}, 1e-9).percentile());
		assertEquals(0.625, MidPPercentile.compute(3, new double[] {1, 2, 3, 4}, 1e-9).percentile());

		MidPPercentile.Result top = MidPPercentile.compute(9, new double[] {1, 2, 3, 4}, 1e-9);
		assertEquals(1.0, top.percentile());
		assertTrue(top.atSampleLimit(), "a percentile beyond every sample is only a bound");
	}

	@Test
	void monteCarloErrorShrinksWithSamples() {
		double[] small = new double[100];
		double[] large = new double[10_000];
		for (int i = 0; i < small.length; i++) small[i] = i;
		for (int i = 0; i < large.length; i++) large[i] = i / 100.0;
		double se100 = MidPPercentile.compute(50, small, 1e-12).monteCarloStandardError();
		double se10k = MidPPercentile.compute(50, large, 1e-12).monteCarloStandardError();
		assertEquals(0.05, se100, 0.001);
		assertEquals(0.005, se10k, 0.0001);
	}

	@Test
	void percentileIsCalibratedUnderTheNull() {
		RunSimulator sim = simulator();
		List<RunContext> runs = Collections.nCopies(15, RunContext.of(FloorId.F1, 300));
		RngMeterState meter = new RngMeterState(FloorId.F1, "BONZO_STAFF", 0);
		SelectionPolicy policy = SelectionPolicy.highestTier();
		double[] nullSamples = NullDistribution.sample(sim, runs, meter, policy, KismetPolicy.never(), 3000, 1L);

		HistorySimulator history = new HistorySimulator(sim);
		SplittableRandom seeds = new SplittableRandom(2L);
		int observations = 600;
		double sum = 0.0;
		int lowTail = 0;
		int highTail = 0;
		for (int i = 0; i < observations; i++) {
			double observed = SurprisalStatistic.of(history.simulate(runs, meter, policy, KismetPolicy.never(), seeds.nextLong()), sim);
			double percentile = MidPPercentile.compute(observed, nullSamples, 1e-9).percentile();
			sum += percentile;
			if (percentile <= 0.1) lowTail++;
			if (percentile >= 0.9) highTail++;
		}
		assertEquals(0.5, sum / observations, 0.03, "mid-p mean under the null");
		assertTrue(lowTail <= observations * 0.15, "low tail " + lowTail);
		assertTrue(highTail <= observations * 0.15, "high tail " + highTail);
	}

	@Test
	void fewExpectedRareDropsAreNotScorable() {
		RunSimulator sim = simulator();
		assertFalse(RareOutcomeGate.scorable(List.of(RunContext.of(FloorId.F7, 300)), sim));
		assertTrue(RareOutcomeGate.scorable(Collections.nCopies(500, RunContext.of(FloorId.F7, 300)), sim));
		double perRun = RareOutcomeGate.expectedRareAppearances(List.of(RunContext.of(FloorId.F7, 300)), sim, 0.01);
		assertTrue(perRun > 0 && perRun < 1, "F7 S+ rare appearances per run " + perRun);
	}
}
