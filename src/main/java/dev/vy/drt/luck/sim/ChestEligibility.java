package dev.vy.drt.luck.sim;

import dev.vy.drt.luck.ChestTier;
import dev.vy.drt.luck.FloorId;
import dev.vy.drt.luck.ScoreTier;
import dev.vy.drt.luck.data.FloorTable;
import dev.vy.drt.luck.rules.RuleVariants;
import java.util.ArrayList;
import java.util.List;

/** Chests offered after a boss kill: Wood, Gold, Diamond always; Emerald at A; Obsidian at S; Bedrock at S+. */
public final class ChestEligibility {
	private ChestEligibility() {
	}

	public static List<ChestTier> eligible(FloorTable floor, ScoreTier score, RuleVariants rules) {
		boolean allChests = floor.floor() == FloorId.M7
			&& rules.m7ChestEligibility() == RuleVariants.M7ChestEligibility.ALL_CHESTS_ON_COMPLETION;
		List<ChestTier> out = new ArrayList<>();
		for (ChestTier tier : ChestTier.values()) {
			if (floor.chest(tier) == null) continue;
			if (allChests || score.ordinal() >= minimumScore(tier).ordinal()) out.add(tier);
		}
		return out;
	}

	public static ScoreTier minimumScore(ChestTier tier) {
		return switch (tier) {
			case WOOD, GOLD, DIAMOND -> ScoreTier.BELOW_A;
			case EMERALD -> ScoreTier.A;
			case OBSIDIAN -> ScoreTier.S;
			case BEDROCK -> ScoreTier.S_PLUS;
		};
	}
}
