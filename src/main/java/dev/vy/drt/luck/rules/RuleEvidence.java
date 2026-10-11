package dev.vy.drt.luck.rules;

import dev.vy.drt.luck.rules.RuleVariants.PityApplication;
import dev.vy.drt.luck.rules.RuleVariants.SRunMeterGain;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * Meter rule variants still consistent with observed readings. A contradicted variant stops
 * widening estimates for configurations the readings cover, but stays in its enum so later
 * readings can still be checked against it.
 */
public record RuleEvidence(Set<SRunMeterGain> sRunMeterGains, Set<PityApplication> pityApplications) {
	/** Plausible sets from the readings recorded in code. */
	public static final RuleEvidence RECORDED = new RuleEvidence(RuleVariants.PLAUSIBLE_S_RUN_METER_GAINS, RuleVariants.PLAUSIBLE_PITY_APPLICATIONS);
	/** Every variant, for diagnostics. */
	public static final RuleEvidence UNFILTERED = new RuleEvidence(EnumSet.allOf(SRunMeterGain.class), EnumSet.allOf(PityApplication.class));

	public RuleEvidence {
		if (sRunMeterGains.isEmpty() || pityApplications.isEmpty()) throw new IllegalArgumentException("each rule needs a plausible variant");
		sRunMeterGains = Collections.unmodifiableSet(EnumSet.copyOf(sRunMeterGains));
		pityApplications = Collections.unmodifiableSet(EnumSet.copyOf(pityApplications));
	}

	/**
	 * Pity rules to evaluate for a configuration. The readings only settle the rule where Pity is
	 * known and nonzero; absent Pity (level 0) and unknown Pity keep every rule.
	 */
	public Set<PityApplication> pityApplicationsFor(Integer knownPityLevel) {
		return knownPityLevel != null && knownPityLevel > 0 ? pityApplications : Collections.unmodifiableSet(EnumSet.allOf(PityApplication.class));
	}

	public List<RuleVariants> alternatives(RuleVariants baseline, Integer knownPityLevel) {
		return baseline.oneAtATimeAlternatives(sRunMeterGains, pityApplicationsFor(knownPityLevel));
	}
}
