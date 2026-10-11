package dev.vy.drt.luck.rules;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Player modifiers as known to DRT. A null field means unknown; unknowns are never assumed to be
 * zero and instead resolve to every plausible extreme so callers can bound their effect.
 */
public record ModifierPreset(
	BossLuck bossLuck,
	TreasureAccessory treasureAccessory,
	Integer catacombsBoxLevel,
	Integer echoOfBoxesLevel,
	Integer echoOfEchoesLevel,
	Integer pityLevel
) {
	public static final int MAX_BOX_LEVEL = 10;
	public static final int MAX_ECHO_LEVEL = 10;
	public static final int MAX_PITY_LEVEL = 10;

	public static final ModifierPreset UNKNOWN = new ModifierPreset(null, null, null, null, null, null);

	/** Boss Luck perk from Mort: flat quality bonus. */
	public enum BossLuck {
		NONE(0), I(1), II(3), III(5), IV(10);

		private final int qualityBonus;

		BossLuck(int qualityBonus) {
			this.qualityBonus = qualityBonus;
		}

		public int qualityBonus() {
			return qualityBonus;
		}
	}

	/** Treasure accessory line: quality multiplier. */
	public enum TreasureAccessory {
		NONE(1.00), TALISMAN(1.01), RING(1.02), ARTIFACT(1.03);

		private final double multiplier;

		TreasureAccessory(double multiplier) {
			this.multiplier = multiplier;
		}

		public double multiplier() {
			return multiplier;
		}
	}

	public boolean isFullyKnown() {
		return bossLuck != null && treasureAccessory != null && catacombsBoxLevel != null
			&& echoOfBoxesLevel != null && echoOfEchoesLevel != null && pityLevel != null;
	}

	public ResolvedModifiers resolveKnown() {
		if (!isFullyKnown()) throw new IllegalStateException("Modifier preset has unknown fields");
		return new ResolvedModifiers(bossLuck, treasureAccessory, catacombsBoxLevel, echoOfBoxesLevel, echoOfEchoesLevel, pityLevel);
	}

	/** Lowest and highest value of each unknown field, in every combination (deduplicated). */
	public List<ResolvedModifiers> plausibleExtremes() {
		List<BossLuck> boss = bossLuck == null ? List.of(BossLuck.NONE, BossLuck.IV) : List.of(bossLuck);
		List<TreasureAccessory> treasure = treasureAccessory == null ? List.of(TreasureAccessory.NONE, TreasureAccessory.ARTIFACT) : List.of(treasureAccessory);
		List<Integer> box = range(catacombsBoxLevel, MAX_BOX_LEVEL);
		List<Integer> echoBoxes = range(echoOfBoxesLevel, MAX_ECHO_LEVEL);
		List<Integer> echoEchoes = range(echoOfEchoesLevel, MAX_ECHO_LEVEL);
		List<Integer> pity = range(pityLevel, MAX_PITY_LEVEL);
		Set<ResolvedModifiers> out = new LinkedHashSet<>();
		for (BossLuck b : boss) {
			for (TreasureAccessory t : treasure) {
				for (int c : box) {
					for (int eb : echoBoxes) {
						for (int ee : echoEchoes) {
							for (int p : pity) out.add(new ResolvedModifiers(b, t, c, eb, ee, p));
						}
					}
				}
			}
		}
		return new ArrayList<>(out);
	}

	private static List<Integer> range(Integer known, int max) {
		return known == null ? List.of(0, max) : List.of(known);
	}
}
