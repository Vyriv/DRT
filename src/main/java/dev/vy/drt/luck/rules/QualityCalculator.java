package dev.vy.drt.luck.rules;

import dev.vy.drt.luck.ScoreTier;
import dev.vy.drt.luck.data.ChestTable;

/** Chest roll budget ("quality") from base quality, score and player modifiers. */
public final class QualityCalculator {
	private QualityCalculator() {
	}

	public static int quality(ChestTable chest, ScoreTier scoreTier, ResolvedModifiers modifiers, RuleVariants rules) {
		return quality(chest.baseQuality(), scoreTier == ScoreTier.S_PLUS, modifiers, rules);
	}

	public static int quality(int baseQuality, boolean sPlus, ResolvedModifiers modifiers, RuleVariants rules) {
		double initial = baseQuality;
		if (sPlus) {
			double boosted = baseQuality * 1.05;
			initial = rules.sPlusRounding() == RuleVariants.SPlusQualityRounding.FLOOR ? Math.floor(boosted) : Math.floor(boosted + 0.5);
		}
		double treasure = modifiers.treasureAccessory().multiplier();
		double box = modifiers.catacombsBoxBonus();
		if (rules.boxRounding() == RuleVariants.BoxBonusRounding.FLOOR) box = Math.floor(box + 1e-9);
		double flat = modifiers.bossLuck().qualityBonus() + box;

		double quality = round(initial * treasure + flat, rules);
		if (rules.modifierApplication() == RuleVariants.ModifierApplication.TWICE) {
			quality = round(quality * treasure + flat, rules);
		}
		return (int) quality;
	}

	private static double round(double value, RuleVariants rules) {
		return rules.qualityRounding() == RuleVariants.QualityRounding.HALF_UP ? Math.floor(value + 0.5) : Math.rint(value);
	}
}
