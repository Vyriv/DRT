package dev.vy.drt.luck.data;

public enum FloorStatus {
	/** Item list, weights, quality costs and essence confirmed by two independent sources. */
	VERIFIED,
	/** Single source; top chest weights confirmed by in-game RNG Meter copies. */
	SINGLE_SOURCE_TOP_CHEST_CONFIRMED,
	/** Single source with an active override or an unmodelled rare item. */
	PROVISIONAL,
	/** Not usable for scoring. */
	INCOMPLETE
}
