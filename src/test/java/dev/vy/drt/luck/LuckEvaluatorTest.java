package dev.vy.drt.luck;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.vy.drt.luck.meter.MeterAssumptions;
import dev.vy.drt.luck.meter.RngMeterModel;
import dev.vy.drt.luck.meter.RngMeterState;
import dev.vy.drt.luck.rules.ModifierPreset;
import dev.vy.drt.luck.rules.ModifierPreset.BossLuck;
import dev.vy.drt.luck.rules.ModifierPreset.TreasureAccessory;
import dev.vy.drt.luck.rules.RuleVariants;
import dev.vy.drt.luck.sim.KismetPolicy;
import dev.vy.drt.luck.sim.RunContext;
import dev.vy.drt.luck.sim.SelectionPolicy;
import dev.vy.drt.luck.stats.LuckEvaluator;
import dev.vy.drt.luck.stats.LuckEvaluator.Band;
import dev.vy.drt.luck.stats.LuckEvaluator.Configuration;
import dev.vy.drt.luck.stats.LuckEvaluator.Evaluation;
import dev.vy.drt.luck.stats.LuckEvaluator.Verdict;
import dev.vy.drt.luck.stats.MidPPercentile;
import dev.vy.drt.luck.stats.ObservedRun;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class LuckEvaluatorTest {
	private static final ModifierPreset NO_MODIFIERS = new ModifierPreset(BossLuck.NONE, TreasureAccessory.NONE, 0, 0, 0, 0);

	private static List<Configuration> configurations(FloorId floor) {
		return LuckEvaluator.plausibleConfigurations(NO_MODIFIERS, RuleVariants.defaults(),
			List.of(Map.of(floor, RngMeterState.empty(floor))), SelectionPolicy.highestTier(), KismetPolicy.never());
	}

	private static Evaluation evaluate(List<ObservedRun> observed, FloorId floor) {
		List<Configuration> configurations = configurations(floor);
		return LuckEvaluator.evaluate(LuckTestData.defaults(), observed, configurations.get(0),
			configurations.subList(1, configurations.size()), 150, 5L);
	}

	private static List<ObservedRun> history(int runs, Set<String> appeared) {
		List<ObservedRun> out = new ArrayList<>();
		for (int i = 0; i < runs; i++) out.add(new ObservedRun(RunContext.of(FloorId.F7, 300), appeared, Set.of(), true));
		return out;
	}

	@Test
	void implausiblyLuckyHistoryIsVeryLuckyUnderEveryConfiguration() {
		Evaluation evaluation = evaluate(history(200, Set.of("NECRON_HANDLE", "IMPLOSION_SCROLL", "SHADOW_WARP_SCROLL")), FloorId.F7);
		assertEquals(Verdict.PROVISIONAL, evaluation.verdict(), evaluation.reasons().toString());
		assertEquals(Band.VERY_LUCKY, evaluation.band());
		assertEquals(13, evaluation.alternatives().size());
		assertTrue(evaluation.reasons().stream().anyMatch(r -> r.startsWith("F7 loot data is")));
	}

	@Test
	void historyWithNothingRolledIsVeryUnlucky() {
		Evaluation evaluation = evaluate(history(200, Set.of()), FloorId.F7);
		assertEquals(Verdict.PROVISIONAL, evaluation.verdict());
		assertEquals(Band.VERY_UNLUCKY, evaluation.band());
		assertEquals(0.0, evaluation.maxPercentile(), 1e-12);
	}

	@Test
	void fewRunsAreInsufficientAndUnmodelledItemsAreReported() {
		List<ObservedRun> observed = List.of(new ObservedRun(RunContext.of(FloorId.M7, 300), Set.of("DYE_NECRON"), Set.of(), true));
		Evaluation evaluation = evaluate(observed, FloorId.M7);
		assertEquals(Verdict.INSUFFICIENT_DATA, evaluation.verdict());
		assertTrue(evaluation.reasons().contains("M7 loot data is PROVISIONAL"));
		assertTrue(evaluation.reasons().contains("not modelled: DYES_NOT_MODELLED"));
		assertTrue(evaluation.reasons().stream().anyMatch(r -> r.contains("M7 DYE_NECRON")));
	}

	@Test
	void incompleteRecordsAreNotScored() {
		List<ObservedRun> observed = new ArrayList<>(history(200, Set.of()));
		observed.set(10, new ObservedRun(RunContext.of(FloorId.F7, 300), Set.of(), Set.of("NECRON_HANDLE"), false));
		Evaluation evaluation = evaluate(observed, FloorId.F7);
		assertEquals(Verdict.INCOMPLETE_RECORDS, evaluation.verdict());
		assertTrue(evaluation.reasons().contains("1 of 200 runs lack complete chest offers"));
	}

	@Test
	void bandStabilityIncludesSimulationError() {
		assertEquals(Verdict.PROVISIONAL, LuckEvaluator.interpret(List.of(result(0.97, 0.005), result(0.99, 0.005))));
		assertEquals(Verdict.UNRESOLVED, LuckEvaluator.interpret(List.of(result(0.5, 0.01), result(0.97, 0.005))));
		assertEquals(Verdict.UNRESOLVED, LuckEvaluator.interpret(List.of(result(0.94, 0.01))), "error interval crosses 0.95");
		assertEquals(Verdict.PROVISIONAL, LuckEvaluator.interpret(List.of(result(0.5, 0.05))));
	}

	@Test
	void plausibleConfigurationsCoverModifiersRulesAndMeters() {
		ModifierPreset echoesUnknown = new ModifierPreset(BossLuck.IV, TreasureAccessory.ARTIFACT, 8, null, null, 10);
		var f1 = LuckTestData.defaults().floor(FloorId.F1);
		List<Map<FloorId, RngMeterState>> meters = MeterAssumptions.unknownScores(
			Map.of(FloorId.F1, f1), Map.of(FloorId.F1, "BONZO_STAFF"), RuleVariants.defaults());
		List<Configuration> configurations = LuckEvaluator.plausibleConfigurations(echoesUnknown, RuleVariants.defaults(), meters,
			SelectionPolicy.highestTier(), KismetPolicy.never());
		assertEquals(1 + 3 + 13 + 2, configurations.size());
	}

	@Test
	void unknownMeterScoreGivesEmptyHalfAndFullStates() {
		var f1 = LuckTestData.defaults().floor(FloorId.F1);
		long required = RngMeterModel.requiredScore(f1, "BONZO_STAFF", RuleVariants.defaults());
		List<RngMeterState> states = MeterAssumptions.unknownScore(f1, "BONZO_STAFF", RuleVariants.defaults());
		assertEquals(List.of(0L, Math.round(required * 0.5), required), states.stream().map(RngMeterState::storedScore).toList());
	}

	private static MidPPercentile.Result result(double percentile, double standardError) {
		return new MidPPercentile.Result(percentile, 0, 0, 0, 1000, standardError);
	}
}
