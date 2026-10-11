package dev.vy.drt.luck.stats;

import dev.vy.drt.luck.meter.RngMeterState;
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
 * @param meterBefore     the run floor's meter as read in game before this run, selected or not;
 *                        null when it was not read
 * @param meterAfter      the meter as read after this run and before the floor's next run; null
 *                        when it was not read
 */
public record ObservedRun(
	RunContext run,
	Set<String> appearedKeys,
	Set<String> purchasedKeys,
	boolean offersComplete,
	RngMeterState meterBefore,
	RngMeterState meterAfter
) {
	public ObservedRun {
		appearedKeys = Set.copyOf(appearedKeys);
		purchasedKeys = Set.copyOf(purchasedKeys);
		if (meterBefore != null && meterBefore.floor() != run.floor()) {
			throw new IllegalArgumentException("Meter before a " + run.floor() + " run is for " + meterBefore.floor());
		}
		if (meterAfter != null && meterAfter.floor() != run.floor()) {
			throw new IllegalArgumentException("Meter after a " + run.floor() + " run is for " + meterAfter.floor());
		}
	}

	public ObservedRun(RunContext run, Set<String> appearedKeys, Set<String> purchasedKeys, boolean offersComplete, RngMeterState meterBefore) {
		this(run, appearedKeys, purchasedKeys, offersComplete, meterBefore, null);
	}

	public ObservedRun(RunContext run, Set<String> appearedKeys, Set<String> purchasedKeys, boolean offersComplete) {
		this(run, appearedKeys, purchasedKeys, offersComplete, null, null);
	}

	public static ObservedRun from(RunOutcome outcome) {
		return new ObservedRun(outcome.run(), outcome.appearedKeys(), outcome.purchasedKeys(), true);
	}
}
