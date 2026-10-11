package dev.vy.drt.luck.data;

import dev.vy.drt.luck.FloorId;
import java.util.List;

/** A mechanic or item that the data does not model, so it cannot be scored. */
public record KnownGap(String id, List<FloorId> floors, String summary, String evidence, String consequence) {
	public KnownGap {
		floors = List.copyOf(floors);
	}
}
