package dev.vy.drt.luck;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.vy.drt.luck.meter.RngMeterState;
import dev.vy.drt.luck.rules.ResolvedModifiers;
import dev.vy.drt.luck.rules.RuleVariants;
import dev.vy.drt.luck.sim.HistorySimulator;
import dev.vy.drt.luck.sim.KismetPolicy;
import dev.vy.drt.luck.sim.OfferView;
import dev.vy.drt.luck.sim.RunContext;
import dev.vy.drt.luck.sim.RunOutcome;
import dev.vy.drt.luck.sim.RunSimulator;
import dev.vy.drt.luck.sim.SelectionPolicy;
import dev.vy.drt.luck.stats.NullDistribution;
import dev.vy.drt.luck.stats.SurprisalStatistic;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.SplittableRandom;
import org.junit.jupiter.api.Test;

/** How buying-policy assumptions do and do not reach the score. */
class SelectionBiasTest {
	private static final SelectionPolicy TOP = SelectionPolicy.highestTier();
	private static final SelectionPolicy RAREST = SelectionPolicy.rarestItem(0.05, SelectionPolicy.Fallback.HIGHEST_TIER);
	/** Always the lowest chest (Wood), so the meter item is rarely bought. */
	private static final SelectionPolicy CHEAPEST = new SelectionPolicy() {
		@Override
		public String name() {
			return "CHEAPEST";
		}

		@Override
		public List<ChestTier> choose(OfferView view) {
			return List.of(view.offers().get(0).tier());
		}
	};

	private static RunSimulator simulator(RuleVariants rules) {
		return new RunSimulator(LuckTestData.defaults(), ResolvedModifiers.NONE, rules);
	}

	@Test
	void appearanceNullIgnoresBuyingPolicyWithoutKismetOrPurchaseResets() {
		RunSimulator sim = simulator(RuleVariants.defaults());
		List<RunContext> runs = Collections.nCopies(60, RunContext.of(FloorId.F1, 300));
		RngMeterState meter = new RngMeterState(FloorId.F1, "BONZO_STAFF", 0);
		double[] top = NullDistribution.sample(sim, runs, meter, TOP, KismetPolicy.never(), 200, 9L);
		double[] rarest = NullDistribution.sample(sim, runs, meter, RAREST, KismetPolicy.never(), 200, 9L);
		assertArrayEquals(top, rarest);
	}

	@Test
	void purchaseResetsMakeTheNullDependOnBuyingPolicy() {
		RunSimulator sim = simulator(RuleVariants.defaults().withMeterResetTrigger(RuleVariants.MeterResetTrigger.ON_PURCHASE));
		List<RunContext> runs = Collections.nCopies(300, RunContext.of(FloorId.F1, 300));
		RngMeterState meter = new RngMeterState(FloorId.F1, "BONZO_STAFF", 0);
		double[] top = NullDistribution.sample(sim, runs, meter, TOP, KismetPolicy.never(), 50, 9L);
		double[] wood = NullDistribution.sample(sim, runs, meter, CHEAPEST, KismetPolicy.never(), 50, 9L);
		assertFalse(Arrays.equals(top, wood));
	}

	@Test
	void kismetAddsOpportunitiesAndShiftsTheNull() {
		RunSimulator sim = simulator(RuleVariants.defaults());
		List<RunContext> runs = Collections.nCopies(60, new RunContext(FloorId.F7, 300, true, false));
		double[] never = NullDistribution.sample(sim, runs, RngMeterState.empty(FloorId.F7), TOP, KismetPolicy.never(), 300, 4L);
		double[] reroll = NullDistribution.sample(sim, runs, RngMeterState.empty(FloorId.F7), TOP, KismetPolicy.rerollTopUnlessRare(0.01), 300, 4L);
		assertTrue(mean(reroll) > mean(never), mean(reroll) + " vs " + mean(never));
	}

	@Test
	void purchasedRewardsDependOnPolicyWhileAppearanceDoesNot() {
		RunSimulator sim = simulator(RuleVariants.defaults());
		List<RunContext> runs = Collections.nCopies(300, RunContext.of(FloorId.F7, 300));
		HistorySimulator history = new HistorySimulator(sim);
		List<RunOutcome> top = history.simulate(runs, RngMeterState.empty(FloorId.F7), TOP, KismetPolicy.never(), 77L);
		List<RunOutcome> rarest = history.simulate(runs, RngMeterState.empty(FloorId.F7), RAREST, KismetPolicy.never(), 77L);
		assertEquals(SurprisalStatistic.of(top, sim), SurprisalStatistic.of(rarest, sim), 1e-9);
		assertTrue(SurprisalStatistic.ofPurchased(rarest, sim) != SurprisalStatistic.ofPurchased(top, sim));
		var rarity = sim.rarity(FloorId.F7, dev.vy.drt.luck.ScoreTier.S_PLUS);
		assertTrue(runsBuyingRare(rarest, rarity) > runsBuyingRare(top, rarity));
	}

	private static int runsBuyingRare(List<RunOutcome> outcomes, dev.vy.drt.luck.sim.RunRarity rarity) {
		int count = 0;
		for (RunOutcome outcome : outcomes) {
			if (outcome.purchasedKeys().stream().anyMatch(key -> rarity.perRun(key) <= 0.05)) count++;
		}
		return count;
	}

	@Test
	void floorsKeepSeparateMetersInOneHistory() {
		RunSimulator sim = simulator(RuleVariants.defaults());
		List<RunContext> runs = new ArrayList<>();
		for (int i = 0; i < 40; i++) runs.add(RunContext.of(i % 2 == 0 ? FloorId.F1 : FloorId.M1, 300));
		List<RunOutcome> outcomes = new HistorySimulator(sim).simulate(runs,
			Map.of(FloorId.M1, new RngMeterState(FloorId.M1, "BONZO_STAFF", 0)), TOP, KismetPolicy.never(), 1L);
		long f1Score = 0;
		for (RunOutcome outcome : outcomes) {
			assertEquals(outcome.run().floor(), outcome.meterBefore().floor());
			if (outcome.run().floor() == FloorId.F1) {
				assertEquals(f1Score, outcome.meterBefore().storedScore());
				f1Score += 300;
			} else {
				assertEquals("BONZO_STAFF", outcome.meterBefore().selectedKey());
			}
		}
	}

	@Test
	void sameSeedReplaysAcrossSamples() {
		RunSimulator sim = simulator(RuleVariants.defaults());
		List<RunContext> runs = Collections.nCopies(20, RunContext.of(FloorId.M3, 300));
		assertArrayEquals(
			NullDistribution.sample(sim, runs, RngMeterState.empty(FloorId.M3), TOP, KismetPolicy.never(), 30, new SplittableRandom(1).nextLong()),
			NullDistribution.sample(sim, runs, RngMeterState.empty(FloorId.M3), TOP, KismetPolicy.never(), 30, new SplittableRandom(1).nextLong()));
	}

	private static double mean(double[] values) {
		return Arrays.stream(values).average().orElseThrow();
	}
}
