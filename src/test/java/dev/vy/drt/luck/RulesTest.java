package dev.vy.drt.luck;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.vy.drt.luck.data.ChestTable;
import dev.vy.drt.luck.meter.RngMeterModel;
import dev.vy.drt.luck.rules.ModifierPreset;
import dev.vy.drt.luck.rules.ModifierPreset.BossLuck;
import dev.vy.drt.luck.rules.ModifierPreset.TreasureAccessory;
import dev.vy.drt.luck.rules.QualityCalculator;
import dev.vy.drt.luck.rules.ResolvedModifiers;
import dev.vy.drt.luck.rules.RuleVariants;
import dev.vy.drt.luck.rules.SensitivityAnalysis;
import dev.vy.drt.luck.sim.RunSimulator;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class RulesTest {
	@Test
	void unknownModifiersResolveToExtremesNotZero() {
		ModifierPreset preset = new ModifierPreset(BossLuck.IV, TreasureAccessory.ARTIFACT, 8, null, null, 10);
		assertFalse(preset.isFullyKnown());
		assertThrows(IllegalStateException.class, preset::resolveKnown);
		List<ResolvedModifiers> extremes = preset.plausibleExtremes();
		assertEquals(4, extremes.size());
		double minBox = extremes.stream().mapToDouble(ResolvedModifiers::catacombsBoxBonus).min().orElseThrow();
		double maxBox = extremes.stream().mapToDouble(ResolvedModifiers::catacombsBoxBonus).max().orElseThrow();
		assertEquals(8.0, minBox, 1e-9);
		assertEquals(10.4, maxBox, 1e-9);

		ChestTable bedrock = LuckTestData.defaults().floor(FloorId.F7).chest(ChestTier.BEDROCK);
		Set<Integer> qualities = new HashSet<>();
		for (ResolvedModifiers modifiers : extremes) {
			qualities.add(QualityCalculator.quality(bedrock, ScoreTier.S_PLUS, modifiers, RuleVariants.defaults()));
		}
		assertTrue(qualities.size() > 1, "unknown Echo levels must widen the quality range");

		assertEquals(64, ModifierPreset.UNKNOWN.plausibleExtremes().size());
	}

	@Test
	void modifierRangesAreValidated() {
		assertThrows(IllegalArgumentException.class, () -> new ResolvedModifiers(BossLuck.NONE, TreasureAccessory.NONE, 11, 0, 0, 0));
		assertThrows(IllegalArgumentException.class, () -> new ResolvedModifiers(BossLuck.NONE, TreasureAccessory.NONE, 0, 0, 0, -1));
	}

	@Test
	void everyRuleHasOneAlternative() {
		List<RuleVariants> alternatives = RuleVariants.defaults().oneAtATimeAlternatives();
		assertEquals(13, alternatives.size());
		assertEquals(13, new HashSet<>(alternatives).size());
	}

	@Test
	void sensitivityCoversTheCombinedMultiplierButNotTheRefutedTruncation() {
		List<RuleVariants.SRunMeterGain> sRunRules = RuleVariants.defaults().oneAtATimeAlternatives().stream()
			.map(RuleVariants::sRunMeterGain)
			.filter(rule -> rule != RuleVariants.defaults().sRunMeterGain())
			.toList();
		assertEquals(List.of(RuleVariants.SRunMeterGain.COMBINED_MULTIPLIER), sRunRules);
		assertEquals(14, RuleVariants.defaults().oneAtATimeAlternatives(EnumSet.allOf(RuleVariants.SRunMeterGain.class)).size());
		assertEquals(12, RuleVariants.defaults().oneAtATimeAlternatives(EnumSet.of(RuleVariants.SRunMeterGain.ROUND)).size());

		SensitivityAnalysis.Result gain = SensitivityAnalysis.oneAtATime(RuleVariants.defaults(),
			rules -> RngMeterModel.scoreGain(ScoreTier.S, 299, new ResolvedModifiers(BossLuck.NONE, TreasureAccessory.NONE, 0, 0, 0, 10), rules));
		assertEquals(229.0, gain.baseline());
		assertEquals(230.0, gain.max(), "the combined multiplier moves an S 299 gain by one point");
	}

	@Test
	void sensitivityFlagsMaterialRules() {
		ChestTable bedrock = LuckTestData.defaults().floor(FloorId.F7).chest(ChestTier.BEDROCK);
		ResolvedModifiers max = new ResolvedModifiers(BossLuck.IV, TreasureAccessory.ARTIFACT, 10, 10, 10, 0);
		SensitivityAnalysis.Result quality = SensitivityAnalysis.oneAtATime(RuleVariants.defaults(),
			rules -> QualityCalculator.quality(bedrock, ScoreTier.S_PLUS, max, rules));
		assertEquals(459, quality.baseline());
		assertEquals(423, quality.min());
		assertTrue(quality.isMaterial(1.0), "applying modifiers once is a material change");

		SensitivityAnalysis.Result handle = SensitivityAnalysis.oneAtATime(RuleVariants.defaults(),
			rules -> new RunSimulator(LuckTestData.defaults(), ResolvedModifiers.NONE, rules)
				.rarity(FloorId.F7, ScoreTier.S_PLUS).perRun("NECRON_HANDLE"));
		assertTrue(handle.max() >= handle.baseline());
		assertTrue(handle.maxDeviation() < handle.baseline(), "no single rule should more than double handle odds");
	}
}
