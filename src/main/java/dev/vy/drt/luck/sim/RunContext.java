package dev.vy.drt.luck.sim;

import dev.vy.drt.luck.FloorId;
import dev.vy.drt.luck.ScoreTier;

/**
 * One completed run opportunity.
 *
 * @param score             final dungeon score, used for chest eligibility, S+ quality and meter gain
 * @param kismetAvailable   whether a Kismet Feather reroll may be used
 * @param chestKeyAvailable whether a Dungeon Chest Key allows a second purchase
 */
public record RunContext(FloorId floor, int score, boolean kismetAvailable, boolean chestKeyAvailable) {
	public RunContext {
		if (floor == null) throw new IllegalArgumentException("Floor required");
		if (score < 0) throw new IllegalArgumentException("Negative score");
	}

	public static RunContext of(FloorId floor, int score) {
		return new RunContext(floor, score, false, false);
	}

	public ScoreTier scoreTier() {
		return ScoreTier.fromScore(score);
	}

	public int maxPurchases() {
		return chestKeyAvailable ? 2 : 1;
	}
}
