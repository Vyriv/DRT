package dev.vy.drt.luck.meter;

import dev.vy.drt.luck.FloorId;
import dev.vy.drt.luck.ScoreTier;
import dev.vy.drt.luck.rules.ModifierPreset;
import dev.vy.drt.luck.rules.ResolvedModifiers;
import dev.vy.drt.luck.rules.RuleVariants;
import dev.vy.drt.luck.rules.RuleVariants.SRunMeterGain;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Checks every S-run meter gain rule against observed meter readings. A rule stays plausible while
 * it reproduces every reading; the default is only CONFIRMED once a single rule is left, which
 * needs an S score where ROUND and COMBINED_MULTIPLIER disagree (285 or 299 at Pity 10).
 */
public final class SRunMeterGainEvidence {
	/** Readings recorded in game. Add new ones here; the default and the plausible set must follow the assessment. */
	public static final List<MeterGainObservation> RECORDED = List.of(
		new MeterGainObservation("F5 S+ 302, Pity 10, 2026-10-11", FloorId.F5, 302, 10, 3_224L, 3_556L),
		new MeterGainObservation("F5 S 298, Pity 10, 2026-10-11", FloorId.F5, 298, 10, 3_556L, 3_785L)
	);

	private SRunMeterGainEvidence() {
	}

	public enum Status {
		/** Exactly one rule reproduces every reading. */
		CONFIRMED,
		/** Several rules reproduce every reading; the default is a choice, not a finding. */
		PROVISIONAL,
		/** No rule reproduces every reading: a reading is wrong (an uncaptured run, a misread score) or the model is. */
		CONFLICT
	}

	/** Each rule's predicted gain for one reading. */
	public record Check(MeterGainObservation observation, Map<SRunMeterGain, Long> predictions, Set<SRunMeterGain> matching) {
		public Check {
			predictions = Collections.unmodifiableMap(new EnumMap<>(predictions));
			matching = matching.isEmpty() ? Collections.emptySet() : Collections.unmodifiableSet(EnumSet.copyOf(matching));
		}

		/** True when the reading tells {@code a} and {@code b} apart. */
		public boolean separates(SRunMeterGain a, SRunMeterGain b) {
			return !predictions.get(a).equals(predictions.get(b));
		}
	}

	/**
	 * @param consistent  rules that reproduce every reading
	 * @param recommended rule to use: the baseline's when still consistent, otherwise the first consistent one
	 */
	public record Assessment(List<Check> checks, Set<SRunMeterGain> consistent, Status status, SRunMeterGain recommended) {
		public Assessment {
			checks = List.copyOf(checks);
			consistent = consistent.isEmpty() ? Collections.emptySet() : Collections.unmodifiableSet(EnumSet.copyOf(consistent));
		}

		/** Rules for sensitivity analysis; every rule when the readings conflict, so a conflict widens rather than hides uncertainty. */
		public Set<SRunMeterGain> plausible() {
			return consistent.isEmpty() ? Collections.unmodifiableSet(EnumSet.allOf(SRunMeterGain.class)) : consistent;
		}

		public RuleVariants apply(RuleVariants rules) {
			return rules.withSRunMeterGain(recommended);
		}

		/** Readings where ROUND and COMBINED_MULTIPLIER predict different gains. */
		public List<Check> discriminating() {
			return checks.stream().filter(check -> check.separates(SRunMeterGain.ROUND, SRunMeterGain.COMBINED_MULTIPLIER)).toList();
		}
	}

	public static Assessment recorded() {
		return assess(RECORDED, RuleVariants.defaults());
	}

	/** Duplicate readings (the same run recorded here and captured live) count once. */
	public static Assessment assess(List<MeterGainObservation> observations, RuleVariants baseline) {
		List<MeterGainObservation> unique = new ArrayList<>();
		for (MeterGainObservation observation : observations) {
			if (unique.stream().noneMatch(observation::sameReadings)) unique.add(observation);
		}
		Set<SRunMeterGain> consistent = EnumSet.allOf(SRunMeterGain.class);
		List<Check> checks = new ArrayList<>();
		for (MeterGainObservation observation : unique) {
			Map<SRunMeterGain, Long> predictions = predictions(observation.score(), observation.pityLevel(), baseline);
			Set<SRunMeterGain> matching = EnumSet.noneOf(SRunMeterGain.class);
			predictions.forEach((rule, gain) -> {
				if (gain == observation.gain()) matching.add(rule);
			});
			checks.add(new Check(observation, predictions, matching));
			consistent.retainAll(matching);
		}
		Status status = consistent.isEmpty() ? Status.CONFLICT : consistent.size() == 1 ? Status.CONFIRMED : Status.PROVISIONAL;
		SRunMeterGain recommended = consistent.isEmpty() || consistent.contains(baseline.sRunMeterGain())
			? baseline.sRunMeterGain()
			: consistent.iterator().next();
		return new Assessment(checks, consistent, status, recommended);
	}

	/** Gain predicted by each S-run rule, keeping every other rule of {@code baseline}. */
	public static Map<SRunMeterGain, Long> predictions(int score, int pityLevel, RuleVariants baseline) {
		ResolvedModifiers modifiers = new ResolvedModifiers(ModifierPreset.BossLuck.NONE, ModifierPreset.TreasureAccessory.NONE, 0, 0, 0, pityLevel);
		Map<SRunMeterGain, Long> out = new EnumMap<>(SRunMeterGain.class);
		for (SRunMeterGain rule : SRunMeterGain.values()) {
			out.put(rule, RngMeterModel.scoreGain(ScoreTier.fromScore(score), score, modifiers, baseline.withSRunMeterGain(rule)));
		}
		return out;
	}

	/** True when an S run with this score and Pity would tell ROUND and COMBINED_MULTIPLIER apart. */
	public static boolean discriminates(int score, int pityLevel) {
		Map<SRunMeterGain, Long> predicted = predictions(score, pityLevel, RuleVariants.defaults());
		return !predicted.get(SRunMeterGain.ROUND).equals(predicted.get(SRunMeterGain.COMBINED_MULTIPLIER));
	}
}
