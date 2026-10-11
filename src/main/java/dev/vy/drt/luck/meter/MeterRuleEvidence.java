package dev.vy.drt.luck.meter;

import dev.vy.drt.luck.rules.RuleEvidence;
import dev.vy.drt.luck.rules.RuleVariants;
import java.util.List;

/** Plausible meter rule variants from a set of readings, such as the recorded ones plus live captures. */
public final class MeterRuleEvidence {
	private MeterRuleEvidence() {
	}

	/** A conflicting rule falls back to every variant, so disagreeing readings widen the estimate instead of hiding. */
	public static RuleEvidence from(List<MeterGainObservation> observations, RuleVariants baseline) {
		return new RuleEvidence(
			SRunMeterGainEvidence.assess(observations, baseline).plausible(),
			PityApplicationEvidence.assess(observations, baseline).plausible()
		);
	}
}
