package dev.vy.drt.luck;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.vy.drt.luck.meter.MeterGainObservation;
import dev.vy.drt.luck.meter.MeterRuleStatus;
import dev.vy.drt.luck.meter.PityApplicationEvidence;
import dev.vy.drt.luck.meter.PityApplicationEvidence.Assessment;
import dev.vy.drt.luck.meter.SRunMeterGainEvidence;
import dev.vy.drt.luck.rules.RuleEvidence;
import dev.vy.drt.luck.rules.RuleVariants;
import dev.vy.drt.luck.rules.RuleVariants.PityApplication;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** The Pity rule against the F5 readings recorded in game on 2026-10-11 (Pity 10). */
class PityApplicationEvidenceTest {
	@Test
	void theObservedS302RefutesTheNoPityGain() {
		Assessment recorded = PityApplicationEvidence.recorded();
		PityApplicationEvidence.Check sPlus = recorded.checks().get(0);
		assertEquals(302, sPlus.observation().score());
		assertEquals(332L, sPlus.observation().gain());
		assertEquals(332L, sPlus.predictions().get(PityApplication.MULTIPLY_GAIN_TRUNCATE));
		assertEquals(302L, sPlus.predictions().get(PityApplication.NOT_APPLIED));
		assertEquals(Set.of(PityApplication.MULTIPLY_GAIN_TRUNCATE), sPlus.matching());
		assertTrue(sPlus.discriminating());

		PityApplicationEvidence.Check s = recorded.checks().get(1);
		assertEquals(209L, s.predictions().get(PityApplication.NOT_APPLIED));
		assertEquals(Set.of(PityApplication.MULTIPLY_GAIN_TRUNCATE), s.matching(), "no S-run rule reaches +229 without Pity");

		assertEquals(MeterRuleStatus.CONFIRMED, recorded.status());
		assertEquals(Set.of(PityApplication.MULTIPLY_GAIN_TRUNCATE), recorded.consistent());
		assertEquals(recorded.consistent(), RuleVariants.PLAUSIBLE_PITY_APPLICATIONS, "sensitivity analysis must follow the readings");
		assertEquals(PityApplication.MULTIPLY_GAIN_TRUNCATE, RuleVariants.defaults().pityApplication());
	}

	@Test
	void readingsWithoutPitySettleNothing() {
		Assessment noPity = PityApplicationEvidence.assess(List.of(new MeterGainObservation("test", FloorId.F5, 302, 0, 1_000L, 1_302L)), RuleVariants.defaults());
		assertFalse(noPity.checks().get(0).discriminating());
		assertEquals(MeterRuleStatus.PROVISIONAL, noPity.status());
		assertEquals(EnumSet.allOf(PityApplication.class), noPity.consistent());
	}

	@Test
	void theNoPityRuleStaysAvailableToDetectConflicts() {
		MeterGainObservation noBoost = new MeterGainObservation("test", FloorId.F5, 302, 10, 3_785L, 4_087L);
		Assessment alone = PityApplicationEvidence.assess(List.of(noBoost), RuleVariants.defaults());
		assertEquals(Set.of(PityApplication.NOT_APPLIED), alone.consistent(), "a reading without the Pity boost is still recognised");

		List<MeterGainObservation> readings = new ArrayList<>(SRunMeterGainEvidence.RECORDED);
		readings.add(noBoost);
		Assessment conflict = PityApplicationEvidence.assess(readings, RuleVariants.defaults());
		assertEquals(MeterRuleStatus.CONFLICT, conflict.status());
		assertEquals(EnumSet.allOf(PityApplication.class), conflict.plausible(), "a conflict widens the estimate again");
	}

	@Test
	void pityRulesAreOnlyNarrowedForKnownNonzeroPity() {
		assertEquals(Set.of(PityApplication.MULTIPLY_GAIN_TRUNCATE), RuleEvidence.RECORDED.pityApplicationsFor(10));
		assertEquals(Set.of(PityApplication.MULTIPLY_GAIN_TRUNCATE), RuleEvidence.RECORDED.pityApplicationsFor(3));
		assertEquals(EnumSet.allOf(PityApplication.class), RuleEvidence.RECORDED.pityApplicationsFor(0), "absent Pity");
		assertEquals(EnumSet.allOf(PityApplication.class), RuleEvidence.RECORDED.pityApplicationsFor(null), "unknown Pity");
		assertEquals(12, RuleEvidence.RECORDED.alternatives(RuleVariants.defaults(), 10).size());
		assertEquals(13, RuleEvidence.RECORDED.alternatives(RuleVariants.defaults(), null).size());
		assertEquals(13, RuleVariants.defaults().oneAtATimeAlternatives().size(), "without a configuration every Pity rule stays");
	}
}
