package dev.vy.drt.luck.rules;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * Named choices for mechanics that public sources do not settle. Defaults follow the best
 * available evidence; {@link #oneAtATimeAlternatives()} supports sensitivity testing.
 */
public record RuleVariants(
	SPlusQualityRounding sPlusRounding,
	QualityRounding qualityRounding,
	ModifierApplication modifierApplication,
	BoxBonusRounding boxRounding,
	GuaranteePlacement guaranteePlacement,
	MeterResetTrigger meterResetTrigger,
	MeterOverflow meterOverflow,
	SRunMeterGain sRunMeterGain,
	PityApplication pityApplication,
	KismetMeter kismetMeter,
	RequiredScoreRounding requiredScoreRounding,
	SelectionCarryOver selectionCarryOver,
	M7ChestEligibility m7ChestEligibility
) {
	/** S+ adds 5% to the base chest quality. Pigicial floors; the official wiki tables imply rounding half up. */
	public enum SPlusQualityRounding { FLOOR, HALF_UP }

	/** Rounding of each modifier pass. Pigicial uses half away from zero. */
	public enum QualityRounding { HALF_UP, HALF_EVEN }

	/** Pigicial applies Treasure, Boss Luck and Box twice (a known in-game bug); ONCE models the intended formula. */
	public enum ModifierApplication { TWICE, ONCE }

	/** Catacombs Box bonus after Echo scaling: kept fractional or floored. */
	public enum BoxBonusRounding { EXACT, FLOOR }

	/** Which chest receives a full-meter guaranteed item. */
	public enum GuaranteePlacement { LOWEST_TIER_CONTAINING, HIGHEST_ELIGIBLE_TIER_CONTAINING }

	/** Whether the meter resets when the selected item appears in any chest or only when it is bought. */
	public enum MeterResetTrigger { ON_APPEARANCE, ON_PURCHASE }

	/** Meter score left after a full-meter drop: overflow kept (community wiki) or cleared (Pigicial). */
	public enum MeterOverflow { SUBTRACT_REQUIRED, RESET_TO_ZERO }

	/**
	 * S runs add 70% of the score. TRUNCATE floors 70% then applies Pity; ROUND rounds 70% half up
	 * then applies Pity; COMBINED_MULTIPLIER floors score x 0.7 x Pity once. TRUNCATE is refuted by
	 * an observed F5 run; ROUND is the provisional default until an observed S score separates it
	 * from COMBINED_MULTIPLIER (see {@code SRunMeterGainEvidence}).
	 */
	public enum SRunMeterGain { TRUNCATE, ROUND, COMBINED_MULTIPLIER }

	/** S-run gain rules not refuted by recorded meter readings; only these enter sensitivity analysis. */
	public static final Set<SRunMeterGain> PLAUSIBLE_S_RUN_METER_GAINS =
		Collections.unmodifiableSet(EnumSet.of(SRunMeterGain.ROUND, SRunMeterGain.COMBINED_MULTIPLIER));

	/**
	 * RNG Meter Pity: +1% gain per level, or not applied. NOT_APPLIED is refuted by an observed F5 S+
	 * run at Pity 10 (+332, not +302) but stays available for diagnostics and conflict checks.
	 */
	public enum PityApplication { MULTIPLY_GAIN_TRUNCATE, NOT_APPLIED }

	/**
	 * Pity rules not refuted by recorded meter readings. Only applied where Pity is known and
	 * nonzero (see {@link RuleEvidence#pityApplicationsFor}); elsewhere every Pity rule stays.
	 */
	public static final Set<PityApplication> PLAUSIBLE_PITY_APPLICATIONS =
		Collections.unmodifiableSet(EnumSet.of(PityApplication.MULTIPLY_GAIN_TRUNCATE));

	/** Whether a Kismet reroll gets the meter weight boost and guarantee. */
	public enum KismetMeter { APPLIES, IGNORED }

	public enum RequiredScoreRounding { FLOOR, HALF_UP }

	/** Meter score carried over when selecting an item: capped at its requirement (Pigicial) or kept. */
	public enum SelectionCarryOver { CAP_AT_REQUIRED, UNCAPPED }

	/** M7: the archived official wiki says a full completion opens every chest regardless of score. */
	public enum M7ChestEligibility { ALL_CHESTS_ON_COMPLETION, SCORE_GATED }

	public static RuleVariants defaults() {
		return new RuleVariants(
			SPlusQualityRounding.FLOOR,
			QualityRounding.HALF_UP,
			ModifierApplication.TWICE,
			BoxBonusRounding.EXACT,
			GuaranteePlacement.LOWEST_TIER_CONTAINING,
			MeterResetTrigger.ON_APPEARANCE,
			MeterOverflow.SUBTRACT_REQUIRED,
			SRunMeterGain.ROUND,
			PityApplication.MULTIPLY_GAIN_TRUNCATE,
			KismetMeter.APPLIES,
			RequiredScoreRounding.FLOOR,
			SelectionCarryOver.CAP_AT_REQUIRED,
			M7ChestEligibility.ALL_CHESTS_ON_COMPLETION
		);
	}

	/**
	 * Every plausible variant that differs from {@code this} in exactly one rule. Without a
	 * configuration the Pity level is unknown, so every Pity rule is kept.
	 */
	public List<RuleVariants> oneAtATimeAlternatives() {
		return oneAtATimeAlternatives(PLAUSIBLE_S_RUN_METER_GAINS, EnumSet.allOf(PityApplication.class));
	}

	/** As {@link #oneAtATimeAlternatives()}, limited to the S-run gain and Pity rules the caller still considers plausible. */
	public List<RuleVariants> oneAtATimeAlternatives(Set<SRunMeterGain> plausibleSRunGains, Set<PityApplication> plausiblePity) {
		List<RuleVariants> out = new ArrayList<>();
		for (SPlusQualityRounding v : SPlusQualityRounding.values()) if (v != sPlusRounding) out.add(withSPlusRounding(v));
		for (QualityRounding v : QualityRounding.values()) if (v != qualityRounding) out.add(withQualityRounding(v));
		for (ModifierApplication v : ModifierApplication.values()) if (v != modifierApplication) out.add(withModifierApplication(v));
		for (BoxBonusRounding v : BoxBonusRounding.values()) if (v != boxRounding) out.add(withBoxRounding(v));
		for (GuaranteePlacement v : GuaranteePlacement.values()) if (v != guaranteePlacement) out.add(withGuaranteePlacement(v));
		for (MeterResetTrigger v : MeterResetTrigger.values()) if (v != meterResetTrigger) out.add(withMeterResetTrigger(v));
		for (MeterOverflow v : MeterOverflow.values()) if (v != meterOverflow) out.add(withMeterOverflow(v));
		for (SRunMeterGain v : SRunMeterGain.values()) if (v != sRunMeterGain && plausibleSRunGains.contains(v)) out.add(withSRunMeterGain(v));
		for (PityApplication v : PityApplication.values()) if (v != pityApplication && plausiblePity.contains(v)) out.add(withPityApplication(v));
		for (KismetMeter v : KismetMeter.values()) if (v != kismetMeter) out.add(withKismetMeter(v));
		for (RequiredScoreRounding v : RequiredScoreRounding.values()) if (v != requiredScoreRounding) out.add(withRequiredScoreRounding(v));
		for (SelectionCarryOver v : SelectionCarryOver.values()) if (v != selectionCarryOver) out.add(withSelectionCarryOver(v));
		for (M7ChestEligibility v : M7ChestEligibility.values()) if (v != m7ChestEligibility) out.add(withM7ChestEligibility(v));
		return out;
	}

	public RuleVariants withSPlusRounding(SPlusQualityRounding v) {
		return new RuleVariants(v, qualityRounding, modifierApplication, boxRounding, guaranteePlacement, meterResetTrigger, meterOverflow, sRunMeterGain, pityApplication, kismetMeter, requiredScoreRounding, selectionCarryOver, m7ChestEligibility);
	}

	public RuleVariants withQualityRounding(QualityRounding v) {
		return new RuleVariants(sPlusRounding, v, modifierApplication, boxRounding, guaranteePlacement, meterResetTrigger, meterOverflow, sRunMeterGain, pityApplication, kismetMeter, requiredScoreRounding, selectionCarryOver, m7ChestEligibility);
	}

	public RuleVariants withModifierApplication(ModifierApplication v) {
		return new RuleVariants(sPlusRounding, qualityRounding, v, boxRounding, guaranteePlacement, meterResetTrigger, meterOverflow, sRunMeterGain, pityApplication, kismetMeter, requiredScoreRounding, selectionCarryOver, m7ChestEligibility);
	}

	public RuleVariants withBoxRounding(BoxBonusRounding v) {
		return new RuleVariants(sPlusRounding, qualityRounding, modifierApplication, v, guaranteePlacement, meterResetTrigger, meterOverflow, sRunMeterGain, pityApplication, kismetMeter, requiredScoreRounding, selectionCarryOver, m7ChestEligibility);
	}

	public RuleVariants withGuaranteePlacement(GuaranteePlacement v) {
		return new RuleVariants(sPlusRounding, qualityRounding, modifierApplication, boxRounding, v, meterResetTrigger, meterOverflow, sRunMeterGain, pityApplication, kismetMeter, requiredScoreRounding, selectionCarryOver, m7ChestEligibility);
	}

	public RuleVariants withMeterResetTrigger(MeterResetTrigger v) {
		return new RuleVariants(sPlusRounding, qualityRounding, modifierApplication, boxRounding, guaranteePlacement, v, meterOverflow, sRunMeterGain, pityApplication, kismetMeter, requiredScoreRounding, selectionCarryOver, m7ChestEligibility);
	}

	public RuleVariants withMeterOverflow(MeterOverflow v) {
		return new RuleVariants(sPlusRounding, qualityRounding, modifierApplication, boxRounding, guaranteePlacement, meterResetTrigger, v, sRunMeterGain, pityApplication, kismetMeter, requiredScoreRounding, selectionCarryOver, m7ChestEligibility);
	}

	public RuleVariants withSRunMeterGain(SRunMeterGain v) {
		return new RuleVariants(sPlusRounding, qualityRounding, modifierApplication, boxRounding, guaranteePlacement, meterResetTrigger, meterOverflow, v, pityApplication, kismetMeter, requiredScoreRounding, selectionCarryOver, m7ChestEligibility);
	}

	public RuleVariants withPityApplication(PityApplication v) {
		return new RuleVariants(sPlusRounding, qualityRounding, modifierApplication, boxRounding, guaranteePlacement, meterResetTrigger, meterOverflow, sRunMeterGain, v, kismetMeter, requiredScoreRounding, selectionCarryOver, m7ChestEligibility);
	}

	public RuleVariants withKismetMeter(KismetMeter v) {
		return new RuleVariants(sPlusRounding, qualityRounding, modifierApplication, boxRounding, guaranteePlacement, meterResetTrigger, meterOverflow, sRunMeterGain, pityApplication, v, requiredScoreRounding, selectionCarryOver, m7ChestEligibility);
	}

	public RuleVariants withRequiredScoreRounding(RequiredScoreRounding v) {
		return new RuleVariants(sPlusRounding, qualityRounding, modifierApplication, boxRounding, guaranteePlacement, meterResetTrigger, meterOverflow, sRunMeterGain, pityApplication, kismetMeter, v, selectionCarryOver, m7ChestEligibility);
	}

	public RuleVariants withSelectionCarryOver(SelectionCarryOver v) {
		return new RuleVariants(sPlusRounding, qualityRounding, modifierApplication, boxRounding, guaranteePlacement, meterResetTrigger, meterOverflow, sRunMeterGain, pityApplication, kismetMeter, requiredScoreRounding, v, m7ChestEligibility);
	}

	public RuleVariants withM7ChestEligibility(M7ChestEligibility v) {
		return new RuleVariants(sPlusRounding, qualityRounding, modifierApplication, boxRounding, guaranteePlacement, meterResetTrigger, meterOverflow, sRunMeterGain, pityApplication, kismetMeter, requiredScoreRounding, selectionCarryOver, v);
	}
}
