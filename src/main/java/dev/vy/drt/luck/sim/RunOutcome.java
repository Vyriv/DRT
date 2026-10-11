package dev.vy.drt.luck.sim;

import dev.vy.drt.luck.ChestTier;
import dev.vy.drt.luck.engine.ChestRoll;
import dev.vy.drt.luck.meter.RngMeterState;
import java.util.List;
import java.util.Set;

/**
 * Everything one simulated run produced. Appearance and purchase are kept apart: a reroll adds a
 * chest opportunity, and the replaced roll still counts as having appeared.
 *
 * @param initialOffers  chests as first rolled, in tier order
 * @param rerolledTier   tier rerolled by Kismet, or null
 * @param rerollResult   the replacement roll, or null
 * @param finalOffers    offers after any reroll
 * @param appearedKeys   rolled luck items seen in any chest, including the replaced roll
 * @param purchasedKeys  rolled luck items in bought chests
 * @param meterGuaranteeApplied whether a full meter forced the selected item into a chest
 */
public record RunOutcome(
	RunContext run,
	List<ChestRoll> initialOffers,
	ChestTier rerolledTier,
	ChestRoll rerollResult,
	List<ChestRoll> finalOffers,
	List<ChestTier> purchased,
	Set<String> appearedKeys,
	Set<String> purchasedKeys,
	RngMeterState meterBefore,
	RngMeterState meterAfter,
	boolean meterGuaranteeApplied
) {
	public RunOutcome {
		initialOffers = List.copyOf(initialOffers);
		finalOffers = List.copyOf(finalOffers);
		purchased = List.copyOf(purchased);
		appearedKeys = Set.copyOf(appearedKeys);
		purchasedKeys = Set.copyOf(purchasedKeys);
	}

	public int chestOpportunities() {
		return initialOffers.size() + (rerollResult == null ? 0 : 1);
	}

	public boolean appeared(String key) {
		return appearedKeys.contains(key);
	}

	public boolean purchased(String key) {
		return purchasedKeys.contains(key);
	}
}
