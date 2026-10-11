package dev.vy.drt.luck;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.vy.drt.luck.meter.MeterGainObservation;
import dev.vy.drt.luck.meter.MeterRuleStatus;
import dev.vy.drt.luck.meter.SRunMeterGainEvidence;
import dev.vy.drt.luck.meter.SRunMeterGainEvidence.Assessment;
import dev.vy.drt.luck.rules.RuleVariants;
import dev.vy.drt.luck.rules.RuleVariants.SRunMeterGain;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** The S-run meter gain rule against the F5 readings recorded in game on 2026-10-11 (Pity 10). */
class SRunMeterGainEvidenceTest {
	private static final MeterGainObservation S_PLUS_302 = new MeterGainObservation("test", FloorId.F5, 302, 10, 3_224L, 3_556L);
	private static final MeterGainObservation S_298 = new MeterGainObservation("test", FloorId.F5, 298, 10, 3_556L, 3_785L);

	@Test
	void recordedReadingsAreTheTwoObservedF5Runs() {
		assertEquals(2, SRunMeterGainEvidence.RECORDED.size());
		assertTrue(SRunMeterGainEvidence.RECORDED.get(0).sameReadings(S_PLUS_302));
		assertTrue(SRunMeterGainEvidence.RECORDED.get(1).sameReadings(S_298));
		assertEquals(332L, S_PLUS_302.gain());
		assertEquals(229L, S_298.gain());
		assertEquals(ScoreTier.S_PLUS, S_PLUS_302.tier());
		assertEquals(ScoreTier.S, S_298.tier());
	}

	@Test
	void bothPlausibleRulesReproduceEveryObservedRun() {
		for (MeterGainObservation observation : List.of(S_PLUS_302, S_298)) {
			Map<SRunMeterGain, Long> predicted = SRunMeterGainEvidence.predictions(observation.score(), observation.pityLevel(), RuleVariants.defaults());
			assertEquals(observation.gain(), predicted.get(SRunMeterGain.ROUND), observation + " rounding-first");
			assertEquals(observation.gain(), predicted.get(SRunMeterGain.COMBINED_MULTIPLIER), observation + " combined multiplier");
		}
		assertEquals(228L, SRunMeterGainEvidence.predictions(298, 10, RuleVariants.defaults()).get(SRunMeterGain.TRUNCATE), "truncate-first is off by one");
	}

	@Test
	void roundingFirstIsTheDefaultButOnlyProvisional() {
		Assessment assessment = SRunMeterGainEvidence.recorded();
		assertEquals(MeterRuleStatus.PROVISIONAL, assessment.status());
		assertEquals(EnumSet.of(SRunMeterGain.ROUND, SRunMeterGain.COMBINED_MULTIPLIER), assessment.consistent());
		assertEquals(SRunMeterGain.ROUND, assessment.recommended());
		assertEquals(assessment.recommended(), RuleVariants.defaults().sRunMeterGain());
		assertEquals(assessment.consistent(), RuleVariants.PLAUSIBLE_S_RUN_METER_GAINS, "sensitivity analysis must cover exactly the unrefuted rules");
		assertEquals(List.of(), assessment.discriminating(), "no recorded score separates the two rules yet");
	}

	@Test
	void discriminatingScoresAreKnown() {
		assertTrue(SRunMeterGainEvidence.discriminates(285, 10));
		assertTrue(SRunMeterGainEvidence.discriminates(299, 10));
		assertFalse(SRunMeterGainEvidence.discriminates(298, 10));
		assertFalse(SRunMeterGainEvidence.discriminates(302, 10), "S+ gain does not depend on the S-run rule");
		Map<SRunMeterGain, Long> at285 = SRunMeterGainEvidence.predictions(285, 10, RuleVariants.defaults());
		assertEquals(220L, at285.get(SRunMeterGain.ROUND));
		assertEquals(219L, at285.get(SRunMeterGain.COMBINED_MULTIPLIER));
		Map<SRunMeterGain, Long> at299 = SRunMeterGainEvidence.predictions(299, 10, RuleVariants.defaults());
		assertEquals(229L, at299.get(SRunMeterGain.ROUND));
		assertEquals(230L, at299.get(SRunMeterGain.COMBINED_MULTIPLIER));
	}

	@Test
	void aDiscriminatingReadingSettlesTheDefault() {
		Assessment combined = withRecorded(new MeterGainObservation("test", FloorId.F5, 299, 10, 3_785L, 4_015L));
		assertEquals(MeterRuleStatus.CONFIRMED, combined.status());
		assertEquals(EnumSet.of(SRunMeterGain.COMBINED_MULTIPLIER), combined.consistent());
		assertEquals(SRunMeterGain.COMBINED_MULTIPLIER, combined.recommended());
		assertEquals(SRunMeterGain.COMBINED_MULTIPLIER, combined.apply(RuleVariants.defaults()).sRunMeterGain());
		assertEquals(1, combined.discriminating().size());

		Assessment round = withRecorded(new MeterGainObservation("test", FloorId.F5, 285, 10, 3_785L, 4_005L));
		assertEquals(MeterRuleStatus.CONFIRMED, round.status());
		assertEquals(SRunMeterGain.ROUND, round.recommended());
	}

	@Test
	void readingsNoRuleExplainsAreAConflictThatWidensSensitivity() {
		Assessment conflict = withRecorded(new MeterGainObservation("test", FloorId.F5, 299, 10, 3_785L, 4_016L));
		assertEquals(MeterRuleStatus.CONFLICT, conflict.status());
		assertEquals(SRunMeterGain.ROUND, conflict.recommended(), "a conflict keeps the default rather than picking a rule");
		assertEquals(EnumSet.allOf(SRunMeterGain.class), conflict.plausible());

		Assessment noPity = SRunMeterGainEvidence.assess(List.of(new MeterGainObservation("test", FloorId.F5, 302, 10, 3_224L, 3_526L)), RuleVariants.defaults());
		assertEquals(MeterRuleStatus.CONFLICT, noPity.status(), "an S+ gain without Pity contradicts the Pity rule");
	}

	@Test
	void theSameRunRecordedAndCapturedCountsOnce() {
		List<MeterGainObservation> all = new ArrayList<>(SRunMeterGainEvidence.RECORDED);
		all.add(new MeterGainObservation("luck-26478f2d", FloorId.F5, 298, 10, 3_556L, 3_785L));
		assertEquals(2, SRunMeterGainEvidence.assess(all, RuleVariants.defaults()).checks().size());
	}

	private static Assessment withRecorded(MeterGainObservation extra) {
		List<MeterGainObservation> all = new ArrayList<>(SRunMeterGainEvidence.RECORDED);
		all.add(extra);
		return SRunMeterGainEvidence.assess(all, RuleVariants.defaults());
	}
}
