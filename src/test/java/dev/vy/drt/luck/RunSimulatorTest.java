package dev.vy.drt.luck;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.vy.drt.luck.data.LootEntry;
import dev.vy.drt.luck.meter.RngMeterModel;
import dev.vy.drt.luck.meter.RngMeterState;
import dev.vy.drt.luck.rules.ResolvedModifiers;
import dev.vy.drt.luck.rules.RuleVariants;
import dev.vy.drt.luck.sim.AppearanceEstimator;
import dev.vy.drt.luck.sim.KismetPolicy;
import dev.vy.drt.luck.sim.RunContext;
import dev.vy.drt.luck.sim.RunOutcome;
import dev.vy.drt.luck.sim.RunRarity;
import dev.vy.drt.luck.sim.RunSimulator;
import dev.vy.drt.luck.sim.SelectionPolicy;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SplittableRandom;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class RunSimulatorTest {
	private static final double Z = 5.5;

	private static RunSimulator simulator(RuleVariants rules) {
		return new RunSimulator(LuckTestData.defaults(), ResolvedModifiers.NONE, rules);
	}

	private static RunSimulator simulator() {
		return simulator(RuleVariants.defaults());
	}

	@Test
	void chestEligibilityByScore() {
		RunSimulator sim = simulator();
		assertEquals(List.of(ChestTier.WOOD, ChestTier.GOLD, ChestTier.DIAMOND), sim.eligibleChests(FloorId.F1, ScoreTier.BELOW_A));
		assertEquals(4, sim.eligibleChests(FloorId.F1, ScoreTier.A).size());
		assertEquals(5, sim.eligibleChests(FloorId.F1, ScoreTier.S).size());
		assertEquals(5, sim.eligibleChests(FloorId.F4, ScoreTier.S_PLUS).size());
		assertEquals(5, sim.eligibleChests(FloorId.F5, ScoreTier.S).size());
		assertEquals(6, sim.eligibleChests(FloorId.F5, ScoreTier.S_PLUS).size());
		assertEquals(6, sim.eligibleChests(FloorId.M7, ScoreTier.BELOW_A).size());
		assertEquals(3, simulator(RuleVariants.defaults().withM7ChestEligibility(RuleVariants.M7ChestEligibility.SCORE_GATED))
			.eligibleChests(FloorId.M7, ScoreTier.BELOW_A).size());
	}

	@ParameterizedTest
	@EnumSource(FloorId.class)
	void purchasesAreASubsetOfAppearances(FloorId floor) {
		RunSimulator sim = simulator();
		SplittableRandom random = new SplittableRandom(floor.ordinal());
		RunContext run = new RunContext(floor, 305, true, true);
		SelectionPolicy policy = SelectionPolicy.rarestItem(0.05, SelectionPolicy.Fallback.HIGHEST_TIER);
		for (int i = 0; i < 300; i++) {
			RunOutcome outcome = sim.simulate(run, RngMeterState.empty(floor), policy, KismetPolicy.rerollTopUnlessRare(0.05), random);
			assertTrue(outcome.appearedKeys().containsAll(outcome.purchasedKeys()));
			assertTrue(outcome.purchased().size() <= 2);
			assertEquals(sim.eligibleChests(floor, ScoreTier.S_PLUS).size(), outcome.initialOffers().size());
		}
	}

	@Test
	void chestKeyBuysTwoChests() {
		RunOutcome outcome = simulator().simulate(new RunContext(FloorId.F7, 300, false, true), RngMeterState.empty(FloorId.F7),
			SelectionPolicy.highestTier(), KismetPolicy.never(), new SplittableRandom(3));
		assertEquals(List.of(ChestTier.BEDROCK, ChestTier.OBSIDIAN), outcome.purchased());
	}

	@Test
	void kismetRerollIsAnExtraOpportunity() {
		RunSimulator sim = simulator();
		RunContext run = new RunContext(FloorId.F7, 300, true, false);
		RunOutcome outcome = sim.simulate(run, RngMeterState.empty(FloorId.F7), SelectionPolicy.highestTier(),
			KismetPolicy.rerollTopUnlessRare(0.0), new SplittableRandom(5));
		assertEquals(ChestTier.BEDROCK, outcome.rerolledTier());
		assertNotNull(outcome.rerollResult());
		assertEquals(7, outcome.chestOpportunities());
		assertEquals(outcome.rerollResult(), outcome.finalOffers().get(5));

		String handle = "NECRON_HANDLE";
		double single = sim.rarity(FloorId.F7, ScoreTier.S_PLUS).inChest(ChestTier.BEDROCK, handle);
		AppearanceEstimator.Result estimate = AppearanceEstimator.estimate(sim, run, RngMeterState.empty(FloorId.F7),
			SelectionPolicy.highestTier(), KismetPolicy.rerollTopUnlessRare(0.0), 40_000, 11L);
		double expected = 1 - (1 - single) * (1 - single);
		assertWithin(expected, estimate.item(handle).appearanceProbability(), 40_000, "rerolled appearance");
		assertEquals(7.0, estimate.meanChestOpportunities(), 1e-12);
	}

	@Test
	void highestTierPurchaseMatchesTopChestAppearance() {
		RunSimulator sim = simulator();
		RunContext run = RunContext.of(FloorId.M7, 300);
		AppearanceEstimator.Result estimate = AppearanceEstimator.estimate(sim, run, RngMeterState.empty(FloorId.M7),
			SelectionPolicy.highestTier(), KismetPolicy.never(), 30_000, 21L);
		RunRarity rarity = sim.rarity(FloorId.M7, ScoreTier.S_PLUS);
		for (String key : List.of("NECRON_HANDLE", "DARK_CLAYMORE", "FIFTH_MASTER_STAR", "AUTO_RECOMBOBULATOR")) {
			assertWithin(rarity.inChest(ChestTier.BEDROCK, key), estimate.item(key).purchaseProbability(), 30_000, key + " purchase");
			assertWithin(rarity.perRun(key), estimate.item(key).appearanceProbability(), 30_000, key + " appearance");
			assertTrue(estimate.item(key).purchased() <= estimate.item(key).appeared());
		}
	}

	@Test
	void selectivePolicyBuysRareItemsWheneverTheyAppearAlone() {
		RunSimulator sim = simulator();
		RunContext run = RunContext.of(FloorId.F7, 300);
		AppearanceEstimator.Result rarest = AppearanceEstimator.estimate(sim, run, RngMeterState.empty(FloorId.F7),
			SelectionPolicy.rarestItem(0.01, SelectionPolicy.Fallback.HIGHEST_TIER), KismetPolicy.never(), 20_000, 31L);
		AppearanceEstimator.Estimate handle = rarest.item("NECRON_HANDLE");
		assertTrue(handle.purchased() <= handle.appeared());
		assertTrue(handle.purchased() >= 0.9 * handle.appeared(), "rarest policy should buy most handles");
	}

	@Test
	void fullMeterGuaranteesSelectedItem() {
		RunSimulator sim = simulator();
		var floor = LuckTestData.defaults().floor(FloorId.F7);
		long required = RngMeterModel.requiredScore(floor, "NECRON_HANDLE", sim.rules());
		RngMeterState full = new RngMeterState(FloorId.F7, "NECRON_HANDLE", required + 50);
		SplittableRandom random = new SplittableRandom(13);
		for (int i = 0; i < 200; i++) {
			RunOutcome outcome = sim.simulate(RunContext.of(FloorId.F7, 310), full, SelectionPolicy.highestTier(), KismetPolicy.never(), random);
			assertTrue(outcome.meterGuaranteeApplied());
			assertTrue(outcome.appeared("NECRON_HANDLE"));
			assertEquals(50 + 310, outcome.meterAfter().storedScore());
		}
		RunOutcome sRun = sim.simulate(RunContext.of(FloorId.F7, 290), full, SelectionPolicy.highestTier(), KismetPolicy.never(), random);
		assertTrue(!sRun.meterGuaranteeApplied(), "Bedrock-only item cannot be guaranteed without S+");
	}

	@Test
	void meterBoostRaisesAppearanceOverBaseline() {
		RunSimulator sim = simulator();
		var floor = LuckTestData.defaults().floor(FloorId.F7);
		long required = RngMeterModel.requiredScore(floor, "NECRON_HANDLE", sim.rules());
		RngMeterState half = new RngMeterState(FloorId.F7, "NECRON_HANDLE", required / 2);
		AppearanceEstimator.Result boosted = AppearanceEstimator.estimate(sim, RunContext.of(FloorId.F7, 300), half,
			SelectionPolicy.highestTier(), KismetPolicy.never(), 30_000, 41L);
		double baseline = sim.rarity(FloorId.F7, ScoreTier.S_PLUS).perRun("NECRON_HANDLE");
		assertTrue(boosted.item("NECRON_HANDLE").appearanceProbability() > baseline * 1.5);
	}

	@Test
	void luckItemsExcludeEssenceAndGuaranteedEntries() {
		RunOutcome outcome = simulator().simulate(RunContext.of(FloorId.M7, 300), RngMeterState.empty(FloorId.M7),
			SelectionPolicy.highestTier(), KismetPolicy.never(), new SplittableRandom(1));
		Set<String> keys = outcome.appearedKeys();
		for (String key : keys) {
			LootEntry entry = LuckTestData.defaults().floor(FloorId.M7).chests().values().stream()
				.map(c -> c.entry(key)).filter(e -> e != null).findFirst().orElseThrow();
			assertEquals(LootEntry.Role.WEIGHTED, entry.role(), key);
		}
		Map<String, Double> perRun = simulator().rarity(FloorId.M7, ScoreTier.S_PLUS).perRunProbabilities();
		assertTrue(perRun.get("NECRON_HANDLE") > 0 && perRun.get("NECRON_HANDLE") < 0.1);
	}

	static void assertWithin(double expected, double observed, int trials, String label) {
		double tolerance = Z * Math.sqrt(Math.max(expected * (1 - expected), 1e-9) / trials) + 1e-9;
		assertTrue(Math.abs(expected - observed) <= tolerance, label + " expected " + expected + " observed " + observed);
	}
}
