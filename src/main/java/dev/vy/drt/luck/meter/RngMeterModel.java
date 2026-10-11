package dev.vy.drt.luck.meter;

import dev.vy.drt.luck.ChestTier;
import dev.vy.drt.luck.ScoreTier;
import dev.vy.drt.luck.data.ChestTable;
import dev.vy.drt.luck.data.FloorTable;
import dev.vy.drt.luck.data.LootEntry;
import dev.vy.drt.luck.engine.MeterBoost;
import dev.vy.drt.luck.rules.ResolvedModifiers;
import dev.vy.drt.luck.rules.RuleVariants;
import java.util.ArrayList;
import java.util.List;

/**
 * Floor RNG Meter rules: required score from the top chest weights, weight boost, guarantee and
 * progression. Each disputed step follows {@link RuleVariants}.
 */
public final class RngMeterModel {
	public static final int REQUIRED_SCORE_FACTOR = 300;
	public static final double MAX_MULTIPLIER = 3.0;

	private RngMeterModel() {
	}

	/** Items selectable on the floor meter: weighted non-essence entries of the top chest. */
	public static List<LootEntry> selectableItems(FloorTable floor) {
		List<LootEntry> out = new ArrayList<>();
		for (LootEntry entry : floor.topChest().entries()) {
			if (entry.role() == LootEntry.Role.WEIGHTED) out.add(entry);
		}
		return out;
	}

	/** Meter score needed to fill the meter for {@code key}: 300 x top chest weight total / item weight. */
	public static long requiredScore(FloorTable floor, String key, RuleVariants rules) {
		ChestTable top = floor.topChest();
		LootEntry entry = top.entry(key);
		if (entry == null || entry.role() != LootEntry.Role.WEIGHTED) {
			throw new IllegalArgumentException(key + " is not selectable on " + floor.floor());
		}
		double total = top.totalWeight() + floor.meterTotalWeightDelta();
		double raw = REQUIRED_SCORE_FACTOR * total / entry.weight();
		return rules.requiredScoreRounding() == RuleVariants.RequiredScoreRounding.FLOOR
			? (long) Math.floor(raw + 1e-9)
			: (long) Math.floor(raw + 0.5);
	}

	public static double weightMultiplier(long storedScore, long requiredScore) {
		return 1.0 + Math.min(2.0 * storedScore / requiredScore, 2.0);
	}

	public static boolean isFull(long storedScore, long requiredScore) {
		return storedScore >= requiredScore;
	}

	/**
	 * Boost for one chest. The forced guarantee applies only in {@code guaranteeTier}; other chests
	 * containing the item still get the weight multiplier.
	 */
	public static MeterBoost boostFor(RngMeterState state, long requiredScore, ChestTier chestTier, ChestTier guaranteeTier) {
		if (!state.hasSelection()) return MeterBoost.NONE;
		double multiplier = weightMultiplier(state.storedScore(), requiredScore);
		boolean forced = isFull(state.storedScore(), requiredScore) && chestTier == guaranteeTier;
		return new MeterBoost(state.selectedKey(), multiplier, forced);
	}

	/**
	 * Chest receiving a full-meter guarantee in this run, or null when no eligible chest qualifies
	 * (the meter stays full until a run reaches that chest).
	 */
	public static ChestTier guaranteeTier(FloorTable floor, String key, List<ChestTier> eligible, RuleVariants rules) {
		if (rules.guaranteePlacement() == RuleVariants.GuaranteePlacement.LOWEST_TIER_CONTAINING) {
			for (ChestTier tier : ChestTier.values()) {
				ChestTable chest = floor.chest(tier);
				if (chest != null && rollable(chest, key)) return eligible.contains(tier) ? tier : null;
			}
			return null;
		}
		ChestTier best = null;
		for (ChestTier tier : eligible) {
			ChestTable chest = floor.chest(tier);
			if (chest != null && rollable(chest, key) && (best == null || tier.ordinal() > best.ordinal())) best = tier;
		}
		return best;
	}

	/** Meter score added by a completed run. */
	public static long scoreGain(ScoreTier tier, int score, ResolvedModifiers modifiers, RuleVariants rules) {
		double base = switch (tier) {
			case S_PLUS -> score;
			case S -> rules.sRunMeterGain() == RuleVariants.SRunMeterGain.TRUNCATE ? Math.floor(score * 0.7) : Math.floor(score * 0.7 + 0.5);
			default -> 0.0;
		};
		if (rules.pityApplication() == RuleVariants.PityApplication.MULTIPLY_GAIN_TRUNCATE && modifiers.pityLevel() > 0) {
			base = Math.floor(base * (1.0 + modifiers.pityLevel() / 100.0) + 1e-9);
		}
		return (long) base;
	}

	/**
	 * State after a run.
	 *
	 * @param selectedAppeared whether the selected item appeared in any offered chest, including rerolled-away chests
	 * @param selectedPurchased whether the selected item was bought
	 */
	public static RngMeterState afterRun(
		RngMeterState state,
		long requiredScore,
		boolean selectedAppeared,
		boolean selectedPurchased,
		long gain,
		RuleVariants rules
	) {
		long stored = state.storedScore();
		if (state.hasSelection()) {
			boolean triggered = rules.meterResetTrigger() == RuleVariants.MeterResetTrigger.ON_APPEARANCE ? selectedAppeared : selectedPurchased;
			if (triggered) {
				stored = isFull(stored, requiredScore) && rules.meterOverflow() == RuleVariants.MeterOverflow.SUBTRACT_REQUIRED
					? stored - requiredScore
					: 0L;
			}
		}
		return state.withStoredScore(stored + gain);
	}

	/** Changes the selected item, applying the carry-over rule. */
	public static RngMeterState select(RngMeterState state, FloorTable floor, String key, RuleVariants rules) {
		if (key == null) return new RngMeterState(state.floor(), null, state.storedScore());
		long required = requiredScore(floor, key, rules);
		long stored = rules.selectionCarryOver() == RuleVariants.SelectionCarryOver.CAP_AT_REQUIRED
			? Math.min(state.storedScore(), required)
			: state.storedScore();
		return new RngMeterState(state.floor(), key, stored);
	}

	private static boolean rollable(ChestTable chest, String key) {
		LootEntry entry = chest.entry(key);
		return entry != null && entry.role() == LootEntry.Role.WEIGHTED;
	}
}
