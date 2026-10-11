package dev.vy.drt.luck.stats;

import dev.vy.drt.luck.sim.RunContext;
import dev.vy.drt.luck.sim.RunOutcome;
import java.util.Set;

/**
 * A real run as DRT would record it.
 *
 * @param appearedKeys    rolled items seen in any offered chest, including a rerolled-away chest
 * @param purchasedKeys   rolled items in bought chests
 * @param offersComplete  whether every offered chest (and any pre-reroll roll) was captured; when
 *                        false, appearance is unknown and the run cannot enter a verified score
 */
public record ObservedRun(RunContext run, Set<String> appearedKeys, Set<String> purchasedKeys, boolean offersComplete) {
	public ObservedRun {
		appearedKeys = Set.copyOf(appearedKeys);
		purchasedKeys = Set.copyOf(purchasedKeys);
	}

	public static ObservedRun from(RunOutcome outcome) {
		return new ObservedRun(outcome.run(), outcome.appearedKeys(), outcome.purchasedKeys(), true);
	}
}
