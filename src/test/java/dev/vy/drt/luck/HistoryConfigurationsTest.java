package dev.vy.drt.luck;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.vy.drt.luck.meter.RngMeterState;
import dev.vy.drt.luck.rules.ModifierPreset;
import dev.vy.drt.luck.rules.ResolvedModifiers;
import dev.vy.drt.luck.rules.RuleEvidence;
import dev.vy.drt.luck.rules.RuleVariants;
import dev.vy.drt.luck.sim.KismetPolicy;
import dev.vy.drt.luck.sim.RunContext;
import dev.vy.drt.luck.sim.RunSimulator;
import dev.vy.drt.luck.sim.SelectionPolicy;
import dev.vy.drt.luck.stats.HistoryConfigurations;
import dev.vy.drt.luck.stats.LuckEvaluator;
import dev.vy.drt.luck.stats.LuckEvaluator.Configuration;
import dev.vy.drt.luck.stats.ObservedRun;
import dev.vy.drt.luck.stats.SurprisalStatistic;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class HistoryConfigurationsTest {
	private static final RuleVariants RULES = RuleVariants.defaults();
	private static final ModifierPreset MAXED = new ModifierPreset(ModifierPreset.BossLuck.IV, ModifierPreset.TreasureAccessory.ARTIFACT, 10, 10, 10, 10);
	private static final ModifierPreset PLAIN = new ModifierPreset(ModifierPreset.BossLuck.NONE, ModifierPreset.TreasureAccessory.NONE, 0, 0, 0, 10);

	private static ObservedRun run(FloorId floor, RngMeterState before, RngMeterState after) {
		return new ObservedRun(new RunContext(floor, 298, false, false), Set.of(), Set.of(), true, before, after);
	}

	private static ObservedRun f5(RngMeterState before, RngMeterState after) {
		return run(FloorId.F5, before, after);
	}

	private static RngMeterState f5Meter(long stored) {
		return new RngMeterState(FloorId.F5, null, stored);
	}

	private static HistoryConfigurations.Result build(List<ObservedRun> runs, Set<Integer> gaps, List<ModifierPreset> presets) {
		return HistoryConfigurations.build(runs, gaps, presets, LuckTestData.defaults(), RULES, RuleEvidence.RECORDED,
			SelectionPolicy.highestTier(), KismetPolicy.never());
	}

	@Test
	void eachRunKeepsTheSnapshotRecordedWithIt() {
		List<ObservedRun> runs = List.of(f5(f5Meter(0), null), f5(null, null));
		HistoryConfigurations.Result result = build(runs, Set.of(), List.of(MAXED, PLAIN));
		assertTrue(result.scorable(), result.withheld().toString());
		for (Configuration configuration : result.configurations()) {
			assertEquals(MAXED.resolveKnown(), configuration.modifiersFor(0), configuration.label());
			assertEquals(PLAIN.resolveKnown(), configuration.modifiersFor(1), configuration.label());
		}
		assertEquals(1 + 12, result.configurations().size(), "both snapshots fully known: rule alternatives only");
	}

	@Test
	void unknownFieldsWidenOnlyTheirOwnRuns() {
		ModifierPreset unknownBoss = new ModifierPreset(null, ModifierPreset.TreasureAccessory.NONE, 0, 0, 0, 10);
		List<ObservedRun> runs = List.of(f5(f5Meter(0), null), f5(null, null));
		HistoryConfigurations.Result result = build(runs, Set.of(), List.of(MAXED, unknownBoss));
		List<Configuration> widened = result.configurations().stream().filter(c -> c.label().startsWith("modifiers")).toList();
		assertEquals(1, widened.size());
		assertEquals(ModifierPreset.BossLuck.IV, widened.get(0).modifiersFor(1).bossLuck());
		assertTrue(result.configurations().stream().allMatch(c -> c.modifiersFor(0).equals(MAXED.resolveKnown())), "the maxed run's known values are never widened");
		assertEquals(ModifierPreset.BossLuck.NONE, result.configurations().get(0).modifiersFor(1).bossLuck());
	}

	@Test
	void theNoPityRuleStaysWhileAnySnapshotLacksPity() {
		assertEquals(10, HistoryConfigurations.knownPity(List.of(MAXED, PLAIN)));
		ModifierPreset noPity = new ModifierPreset(ModifierPreset.BossLuck.NONE, ModifierPreset.TreasureAccessory.NONE, 0, 0, 0, 0);
		assertEquals(0, HistoryConfigurations.knownPity(List.of(MAXED, noPity)));
		assertEquals(null, HistoryConfigurations.knownPity(List.of(MAXED, ModifierPreset.UNKNOWN)));
		HistoryConfigurations.Result mixed = build(List.of(f5(f5Meter(0), null), f5(null, null)), Set.of(), List.of(MAXED, noPity));
		assertTrue(mixed.configurations().stream().anyMatch(c -> c.rules().pityApplication() == RuleVariants.PityApplication.NOT_APPLIED));
	}

	@Test
	void perRunScoringUsesEachRunsOwnOdds() {
		List<ObservedRun> runs = List.of(f5(f5Meter(0), null), f5(null, null));
		ObservedRun withRareItems = new ObservedRun(runs.get(0).run(), Set.of("ENCHANTMENT_ULTIMATE_LEGION_1", "HOT_POTATO_BOOK"), Set.of(), true);
		List<ObservedRun> observed = List.of(withRareItems, withRareItems);
		RunSimulator maxed = new RunSimulator(LuckTestData.defaults(), MAXED.resolveKnown(), RULES);
		RunSimulator plain = new RunSimulator(LuckTestData.defaults(), PLAIN.resolveKnown(), RULES);
		double mixed = SurprisalStatistic.ofObserved(observed, index -> index == 0 ? maxed : plain);
		double expected = SurprisalStatistic.ofObserved(observed.subList(0, 1), maxed) + SurprisalStatistic.ofObserved(observed.subList(1, 2), plain);
		assertEquals(expected, mixed, 1e-12);
		assertNotEquals(SurprisalStatistic.ofObserved(observed, maxed), mixed, "a merged preset would score the plain run at maxed odds");
	}

	@Test
	void uniformPerRunModifiersMatchTheSingleModifierPath() {
		List<ObservedRun> runs = new ArrayList<>();
		for (int i = 0; i < 40; i++) runs.add(new ObservedRun(new RunContext(FloorId.F5, 305, false, false), Set.of("HOT_POTATO_BOOK"), Set.of(), true));
		ResolvedModifiers resolved = PLAIN.resolveKnown();
		Configuration single = new Configuration("single", resolved, RULES, Map.of(), SelectionPolicy.highestTier(), KismetPolicy.never());
		Configuration perRun = new Configuration("per run", resolved, RULES, Map.of(), SelectionPolicy.highestTier(), KismetPolicy.never(),
			Collections.nCopies(runs.size(), resolved), List.of());
		LuckEvaluator.Evaluation a = LuckEvaluator.evaluate(LuckTestData.defaults(), runs, single, List.of(), 80, 5L);
		LuckEvaluator.Evaluation b = LuckEvaluator.evaluate(LuckTestData.defaults(), runs, perRun, List.of(), 80, 5L);
		assertEquals(a.verdict(), b.verdict());
		if (a.baseline() != null) assertEquals(a.baseline().percentile().percentile(), b.baseline().percentile().percentile(), 0.0);
	}

	@Test
	void anAlternativeTheReadingsRefuteIsDroppedWithItsReason() {
		ModifierPreset unknownPity = new ModifierPreset(ModifierPreset.BossLuck.NONE, ModifierPreset.TreasureAccessory.NONE, 0, 0, 0, null);
		long noPityGain = 209L;
		HistoryConfigurations.Result result = build(List.of(f5(f5Meter(0), f5Meter(noPityGain))), Set.of(), List.of(unknownPity));
		assertTrue(result.scorable());
		assertTrue(result.dropped().stream().anyMatch(d -> d.startsWith("modifiers") && d.contains("pityLevel=10") && d.contains("METER_BELOW_CAPTURED_GAINS")),
			result.dropped().toString());
		assertTrue(result.configurations().stream().noneMatch(c -> c.modifiersFor(0).pityLevel() == 10));
	}

	@Test
	void readingsTheDefaultRulesCannotReproduceWithholdTheScore() {
		HistoryConfigurations.Result result = build(List.of(f5(f5Meter(1_000), null), f5(f5Meter(1_000), null)), Set.of(), List.of(PLAIN, PLAIN));
		assertFalse(result.scorable());
		assertTrue(result.configurations().isEmpty());
		assertEquals("the meter readings contradict the captured runs under the default rules", result.withheld().get(0));
		assertTrue(result.withheld().get(1).startsWith("METER_BELOW_CAPTURED_GAINS"));
	}

	@Test
	void tooManyUnknownSelectionsWithholdAndSayWhichFloorsToRead() {
		List<ObservedRun> runs = List.of(run(FloorId.F5, null, null), run(FloorId.F6, null, null), run(FloorId.F7, null, null));
		HistoryConfigurations.Result result = build(runs, Set.of(), List.of(PLAIN, PLAIN, PLAIN));
		assertFalse(result.scorable());
		assertTrue(result.withheld().get(0).contains("exceed the limit of " + HistoryConfigurations.MAX_CONFIGURATIONS), result.withheld().toString());
		assertTrue(result.withheld().contains("RNG Meter selection unknown on F5 from run 0: a reading would narrow it"));
	}

	@Test
	void everyConfigurationSetsAnUnreadFloorsStart() {
		HistoryConfigurations.Result result = build(List.of(f5(null, null), f5(null, f5Meter(5_000))), Set.of(), List.of(PLAIN, PLAIN));
		assertTrue(result.scorable(), result.withheld().toString());
		for (Configuration configuration : result.configurations()) {
			assertTrue(configuration.meterAssumptions().stream().anyMatch(e -> e.beforeRun() == 0 && e.floor() == FloorId.F5), configuration.label());
		}
		assertEquals(f5Meter(5_000 - 2 * 229L), ((dev.vy.drt.luck.meter.MeterEvent.Observed) result.configurations().get(0).meterAssumptions().get(0)).state());
	}
}
