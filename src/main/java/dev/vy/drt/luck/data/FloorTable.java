package dev.vy.drt.luck.data;

import dev.vy.drt.luck.ChestTier;
import dev.vy.drt.luck.FloorId;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * @param meterTotalWeightDelta extra weight added to the top chest total when computing RNG Meter
 *                              required scores, from an evidence-backed override
 */
public record FloorTable(
	FloorId floor,
	Map<ChestTier, ChestTable> chests,
	FloorStatus status,
	int meterTotalWeightDelta,
	List<String> notes
) {
	public FloorTable {
		chests = Collections.unmodifiableMap(new EnumMap<>(chests));
		notes = List.copyOf(notes);
	}

	public ChestTable chest(ChestTier tier) {
		return chests.get(tier);
	}

	public ChestTable topChest() {
		ChestTable top = null;
		for (ChestTier tier : ChestTier.values()) {
			ChestTable chest = chests.get(tier);
			if (chest != null) top = chest;
		}
		return top;
	}
}
