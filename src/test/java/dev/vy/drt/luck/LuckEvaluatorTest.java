package dev.vy.drt.luck;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.vy.drt.luck.meter.MeterAssumptions;
import dev.vy.drt.luck.meter.MeterGainObservation;
import dev.vy.drt.luck.meter.MeterRuleEvidence;
import dev.vy.drt.luck.meter.RngMeterModel;
import dev.vy.drt.luck.meter.RngMeterState;
import dev.vy.drt.luck.meter.SRunMeterGainEvidence;
import dev.vy.drt.luck.rules.ModifierPreset;
import dev.vy.drt.luck.rules.ModifierPreset.BossLuck;
import dev.vy.drt.luck.rules.ModifierPreset.TreasureAccessory;
import dev.vy.drt.luck.rules.RuleEvidence;
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
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
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
		// Pity 10 is known, so the refuted no-Pity rule is not among the 12 rule alternatives.
		assertEquals(1 + 3 + 12 + 2, configurations.size());
		assertTrue(configurations.stream().anyMatch(c -> c.rules().sRunMeterGain() == RuleVariants.SRunMeterGain.COMBINED_MULTIPLIER));
		assertTrue(configurations.stream().noneMatch(c -> c.rules().sRunMeterGain() == RuleVariants.SRunMeterGain.TRUNCATE));

		List<Configuration> settled = LuckEvaluator.plausibleConfigurations(echoesUnknown, RuleVariants.defaults(),
			new RuleEvidence(EnumSet.of(RuleVariants.SRunMeterGain.ROUND), RuleVariants.PLAUSIBLE_PITY_APPLICATIONS),
			meters, SelectionPolicy.highestTier(), KismetPolicy.never());
		assertEquals(1 + 3 + 11 + 2, settled.size(), "a settled S-run rule drops out of the alternatives");
	}

	@Test
	void knownPityDropsTheRefutedNoPityRule() {
		ModifierPreset pityTen = new ModifierPreset(BossLuck.IV, TreasureAccessory.ARTIFACT, 10, 10, 0, 10);
		List<Configuration> configurations = pityConfigurations(pityTen, RuleEvidence.RECORDED);
		assertTrue(configurations.stream().noneMatch(c -> c.rules().pityApplication() == RuleVariants.PityApplication.NOT_APPLIED));
		assertTrue(configurations.stream().allMatch(c -> c.modifiers().pityLevel() == 10));
		assertEquals(1 + 12, configurations.size());

		List<Configuration> diagnostic = pityConfigurations(pityTen, RuleEvidence.UNFILTERED);
		assertTrue(diagnostic.stream().anyMatch(c -> c.rules().pityApplication() == RuleVariants.PityApplication.NOT_APPLIED),
			"the no-Pity rule stays available for diagnostics");
	}

	@Test
	void absentPityKeepsTheNoPityRule() {
		List<Configuration> configurations = pityConfigurations(new ModifierPreset(BossLuck.IV, TreasureAccessory.ARTIFACT, 10, 10, 0, 0), RuleEvidence.RECORDED);
		assertTrue(configurations.stream().anyMatch(c -> c.rules().pityApplication() == RuleVariants.PityApplication.NOT_APPLIED));
		assertTrue(configurations.stream().allMatch(c -> c.modifiers().pityLevel() == 0));
		assertEquals(1 + 13, configurations.size());
	}

	@Test
	void unknownPitySpansItsModifierExtremesWithoutAssumingPityTen() {
		List<Configuration> configurations = pityConfigurations(new ModifierPreset(BossLuck.IV, TreasureAccessory.ARTIFACT, 10, 10, 0, null), RuleEvidence.RECORDED);
		assertEquals(Set.of(0, 10), configurations.stream().map(c -> c.modifiers().pityLevel()).collect(Collectors.toSet()));
		assertEquals(0, configurations.get(0).modifiers().pityLevel(), "the baseline takes the lowest Pity, not the observed level");
		assertTrue(configurations.stream().anyMatch(c -> c.rules().pityApplication() == RuleVariants.PityApplication.NOT_APPLIED));
		assertEquals(2 + 13, configurations.size());
	}

	@Test
	void conflictingReadingsBringTheNoPityRuleBack() {
		List<MeterGainObservation> readings = new ArrayList<>(SRunMeterGainEvidence.RECORDED);
		readings.add(new MeterGainObservation("test", FloorId.F5, 302, 10, 3_785L, 4_087L));
		RuleEvidence conflicted = MeterRuleEvidence.from(readings, RuleVariants.defaults());
		assertEquals(EnumSet.allOf(RuleVariants.PityApplication.class), conflicted.pityApplications());
		ModifierPreset pityTen = new ModifierPreset(BossLuck.IV, TreasureAccessory.ARTIFACT, 10, 10, 0, 10);
		assertTrue(pityConfigurations(pityTen, conflicted).stream().anyMatch(c -> c.rules().pityApplication() == RuleVariants.PityApplication.NOT_APPLIED));
		assertEquals(RuleEvidence.RECORDED, MeterRuleEvidence.from(SRunMeterGainEvidence.RECORDED, RuleVariants.defaults()));
	}

	private static List<Configuration> pityConfigurations(ModifierPreset preset, RuleEvidence evidence) {
		return LuckEvaluator.plausibleConfigurations(preset, RuleVariants.defaults(), evidence,
			List.of(Map.of(FloorId.F5, RngMeterState.empty(FloorId.F5))), SelectionPolicy.highestTier(), KismetPolicy.never());
	}

	@Test
	void sRunsCarryTheProvisionalGainRuleCaveat() {
		Evaluation sRun = evaluate(List.of(new ObservedRun(RunContext.of(FloorId.F5, 298), Set.of(), Set.of(), true)), FloorId.F5);
		assertTrue(sRun.reasons().stream().anyMatch(r -> r.startsWith("S-run meter gain rule is PROVISIONAL: ROUND by default")), sRun.reasons().toString());
		Evaluation sPlusRun = evaluate(List.of(new ObservedRun(RunContext.of(FloorId.F5, 302), Set.of(), Set.of(), true)), FloorId.F5);
		assertTrue(sPlusRun.reasons().stream().noneMatch(r -> r.startsWith("S-run meter gain rule")), sPlusRun.reasons().toString());
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
