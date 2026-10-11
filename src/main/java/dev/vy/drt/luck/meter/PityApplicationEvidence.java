package dev.vy.drt.luck.meter;

import dev.vy.drt.luck.ScoreTier;
import dev.vy.drt.luck.rules.ModifierPreset;
import dev.vy.drt.luck.rules.ResolvedModifiers;
import dev.vy.drt.luck.rules.RuleVariants;
import dev.vy.drt.luck.rules.RuleVariants.PityApplication;
import dev.vy.drt.luck.rules.RuleVariants.SRunMeterGain;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Checks every Pity rule against observed meter readings ({@link SRunMeterGainEvidence#RECORDED}).
 * Only readings taken at a nonzero Pity level can tell the rules apart. A Pity rule matches a
 * reading when any S-run rule reproduces it, so S-run uncertainty is never read as Pity evidence.
 */
public final class PityApplicationEvidence {
	private PityApplicationEvidence() {
	}

	/** Each rule's predicted gain under the baseline S-run rule, and the rules that fit under any S-run rule. */
	public record Check(MeterGainObservation observation, Map<PityApplication, Long> predictions, Set<PityApplication> matching) {
		public Check {
			predictions = Collections.unmodifiableMap(new EnumMap<>(predictions));
			matching = matching.isEmpty() ? Collections.emptySet() : Collections.unmodifiableSet(EnumSet.copyOf(matching));
		}

		public boolean discriminating() {
			return new HashSet<>(predictions.values()).size() > 1;
		}
	}

	public record Assessment(List<Check> checks, Set<PityApplication> consistent, MeterRuleStatus status) {
		public Assessment {
			checks = List.copyOf(checks);
			consistent = consistent.isEmpty() ? Collections.emptySet() : Collections.unmodifiableSet(EnumSet.copyOf(consistent));
		}

		/** Rules for sensitivity analysis; every rule when the readings conflict. */
		public Set<PityApplication> plausible() {
			return consistent.isEmpty() ? Collections.unmodifiableSet(EnumSet.allOf(PityApplication.class)) : consistent;
		}
	}

	public static Assessment recorded() {
		return assess(SRunMeterGainEvidence.RECORDED, RuleVariants.defaults());
	}

	public static Assessment assess(List<MeterGainObservation> observations, RuleVariants baseline) {
		Set<PityApplication> consistent = EnumSet.allOf(PityApplication.class);
		List<Check> checks = new ArrayList<>();
		for (MeterGainObservation observation : MeterGainObservation.distinct(observations)) {
			Set<PityApplication> matching = EnumSet.noneOf(PityApplication.class);
			for (PityApplication rule : PityApplication.values()) {
				for (SRunMeterGain sRun : SRunMeterGain.values()) {
					if (gain(observation.score(), observation.pityLevel(), baseline.withPityApplication(rule).withSRunMeterGain(sRun)) == observation.gain()) {
						matching.add(rule);
					}
				}
			}
			checks.add(new Check(observation, predictions(observation.score(), observation.pityLevel(), baseline), matching));
			consistent.retainAll(matching);
		}
		return new Assessment(checks, consistent, MeterRuleStatus.of(consistent.size()));
	}

	/** Gain predicted by each Pity rule, keeping every other rule of {@code baseline}. */
	public static Map<PityApplication, Long> predictions(int score, int pityLevel, RuleVariants baseline) {
		Map<PityApplication, Long> out = new EnumMap<>(PityApplication.class);
		for (PityApplication rule : PityApplication.values()) out.put(rule, gain(score, pityLevel, baseline.withPityApplication(rule)));
		return out;
	}

	private static long gain(int score, int pityLevel, RuleVariants rules) {
		ResolvedModifiers modifiers = new ResolvedModifiers(ModifierPreset.BossLuck.NONE, ModifierPreset.TreasureAccessory.NONE, 0, 0, 0, pityLevel);
		return RngMeterModel.scoreGain(ScoreTier.fromScore(score), score, modifiers, rules);
	}
}
