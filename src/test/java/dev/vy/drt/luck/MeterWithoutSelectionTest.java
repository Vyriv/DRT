package dev.vy.drt.luck;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.vy.drt.luck.data.FloorTable;
import dev.vy.drt.luck.meter.MeterAssumptions;
import dev.vy.drt.luck.meter.MeterEvent;
import dev.vy.drt.luck.meter.RngMeterModel;
import dev.vy.drt.luck.meter.RngMeterState;
import dev.vy.drt.luck.rules.ModifierPreset;
import dev.vy.drt.luck.rules.ResolvedModifiers;
import dev.vy.drt.luck.rules.RuleVariants;
import dev.vy.drt.luck.sim.HistorySimulator;
import dev.vy.drt.luck.sim.KismetPolicy;
import dev.vy.drt.luck.sim.RunContext;
import dev.vy.drt.luck.sim.RunOutcome;
import dev.vy.drt.luck.sim.RunSimulator;
import dev.vy.drt.luck.sim.SelectionPolicy;
import dev.vy.drt.luck.stats.NullDistribution;
import dev.vy.drt.luck.stats.ObservedRun;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** Meter progress with no drop selected, as read from the live F5 meter (3,224 stored, nothing selected). */
class MeterWithoutSelectionTest {
	private static final String REJUVENATE_3 = "ENCHANTMENT_REJUVENATE_3";
	private static final long LIVE_F5_STORED = 3_224L;
	private static final ResolvedModifiers PITY_10 = new ResolvedModifiers(
		ModifierPreset.BossLuck.NONE, ModifierPreset.TreasureAccessory.NONE, 0, 0, 0, 10);

	private static HistorySimulator history(ResolvedModifiers modifiers, RuleVariants rules) {
		return new HistorySimulator(new RunSimulator(LuckTestData.defaults(), modifiers, rules));
	}

	private static List<RunContext> f5Runs(int count, int score) {
		return Collections.nCopies(count, RunContext.of(FloorId.F5, score));
	}

	@Test
	void unselectedMeterAccumulatesEveryRunWithoutResetting() {
		RuleVariants rules = RuleVariants.defaults();
		RngMeterState start = new RngMeterState(FloorId.F5, null, LIVE_F5_STORED);
		List<RunOutcome> outcomes = history(ResolvedModifiers.NONE, rules)
			.simulate(f5Runs(5, 302), start, SelectionPolicy.highestTier(), KismetPolicy.never(), 11L);
		for (int i = 0; i < outcomes.size(); i++) {
			assertNull(outcomes.get(i).meterAfter().selectedKey());
			assertEquals(LIVE_F5_STORED + 302L * (i + 1), outcomes.get(i).meterAfter().storedScore());
			assertFalse(outcomes.get(i).meterGuaranteeApplied());
		}
	}

	@Test
	void pityVariantsPredictTheNextLiveReading() {
		long withPity = RngMeterModel.scoreGain(ScoreTier.S_PLUS, 302, PITY_10, RuleVariants.defaults());
		long withoutPity = RngMeterModel.scoreGain(ScoreTier.S_PLUS, 302,
			PITY_10, RuleVariants.defaults().withPityApplication(RuleVariants.PityApplication.NOT_APPLIED));
		assertEquals(3_556L, LIVE_F5_STORED + withPity);
		assertEquals(3_526L, LIVE_F5_STORED + withoutPity);
	}

	@Test
	void storedScoreWithoutSelectionDoesNotChangeTheRolls() {
		RuleVariants rules = RuleVariants.defaults();
		List<RunOutcome> stored = history(ResolvedModifiers.NONE, rules).simulate(f5Runs(300, 302),
			new RngMeterState(FloorId.F5, null, 200_000L), SelectionPolicy.highestTier(), KismetPolicy.never(), 23L);
		List<RunOutcome> empty = history(ResolvedModifiers.NONE, rules).simulate(f5Runs(300, 302),
			RngMeterState.empty(FloorId.F5), SelectionPolicy.highestTier(), KismetPolicy.never(), 23L);
		for (int i = 0; i < stored.size(); i++) {
			assertEquals(empty.get(i).finalOffers(), stored.get(i).finalOffers());
		}
	}

	@Test
	void selectingAfterUnselectedProgressCarriesTheScoreIntoAGuarantee() {
		FloorTable f5 = LuckTestData.defaults().floor(FloorId.F5);
		for (RuleVariants.SelectionCarryOver carry : RuleVariants.SelectionCarryOver.values()) {
			RuleVariants rules = RuleVariants.defaults().withSelectionCarryOver(carry);
			long required = RngMeterModel.requiredScore(f5, REJUVENATE_3, rules);
			assertEquals(2_540L, required);
			List<MeterEvent> events = List.of(
				new MeterEvent.Observed(0, new RngMeterState(FloorId.F5, null, LIVE_F5_STORED)),
				new MeterEvent.Selected(0, FloorId.F5, REJUVENATE_3)
			);
			List<RunOutcome> outcomes = history(ResolvedModifiers.NONE, rules).simulate(f5Runs(1, 302),
				Map.of(), events, SelectionPolicy.highestTier(), KismetPolicy.never(), 5L);
			RunOutcome run = outcomes.get(0);
			long expectedBefore = carry == RuleVariants.SelectionCarryOver.CAP_AT_REQUIRED ? required : LIVE_F5_STORED;
			assertEquals(new RngMeterState(FloorId.F5, REJUVENATE_3, expectedBefore), run.meterBefore());
			assertTrue(run.meterGuaranteeApplied(), carry.name());
			assertTrue(run.appeared(REJUVENATE_3), carry.name());
		}
	}

	@Test
	void observedReadingReplacesTheSimulatedMeterMidHistory() {
		RuleVariants rules = RuleVariants.defaults();
		RngMeterState observed = new RngMeterState(FloorId.F5, null, 9_999L);
		List<RunOutcome> outcomes = history(ResolvedModifiers.NONE, rules).simulate(f5Runs(4, 302),
			Map.of(FloorId.F5, RngMeterState.empty(FloorId.F5)), List.of(new MeterEvent.Observed(2, observed)),
			SelectionPolicy.highestTier(), KismetPolicy.never(), 9L);
		assertEquals(604L, outcomes.get(1).meterAfter().storedScore());
		assertEquals(observed, outcomes.get(2).meterBefore());
		assertEquals(9_999L + 302L, outcomes.get(2).meterAfter().storedScore());
	}

	@Test
	void deselectingKeepsStoredScore() {
		FloorTable f5 = LuckTestData.defaults().floor(FloorId.F5);
		RngMeterState selected = new RngMeterState(FloorId.F5, REJUVENATE_3, 1_000L);
		assertEquals(new RngMeterState(FloorId.F5, null, 1_000L), RngMeterModel.select(selected, f5, null, RuleVariants.defaults()));
	}

	@Test
	void observedRunsBecomeMeterEventsAtTheirIndex() {
		RngMeterState before = new RngMeterState(FloorId.F5, null, LIVE_F5_STORED);
		List<ObservedRun> observed = List.of(
			new ObservedRun(RunContext.of(FloorId.F5, 302), Set.of(), Set.of(), true),
			new ObservedRun(RunContext.of(FloorId.F5, 302), Set.of(), Set.of(), true, before)
		);
		assertEquals(List.of(new MeterEvent.Observed(1, before)), NullDistribution.observedMeterEvents(observed));
		assertThrows(IllegalArgumentException.class,
			() -> new ObservedRun(RunContext.of(FloorId.F6, 302), Set.of(), Set.of(), true, before));
	}

	@Test
	void observedUnselectedMetersStayFixedAcrossUnknownScenarios() {
		RngMeterState f5 = new RngMeterState(FloorId.F5, null, LIVE_F5_STORED);
		List<Map<FloorId, RngMeterState>> scenarios = MeterAssumptions.unknownScores(
			LuckTestData.defaults().floors(), Map.of(FloorId.M5, "THIRD_MASTER_STAR"), Map.of(FloorId.F5, f5), RuleVariants.defaults());
		assertEquals(MeterAssumptions.PLAUSIBLE_FILL.length, scenarios.size());
		for (Map<FloorId, RngMeterState> scenario : scenarios) assertEquals(f5, scenario.get(FloorId.F5));
		assertThrows(IllegalArgumentException.class,
			() -> MeterAssumptions.unknownScore(LuckTestData.defaults().floor(FloorId.F5), null, RuleVariants.defaults()));
	}
}
