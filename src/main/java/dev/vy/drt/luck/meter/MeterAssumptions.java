package dev.vy.drt.luck.meter;

import dev.vy.drt.luck.FloorId;
import dev.vy.drt.luck.data.FloorTable;
import dev.vy.drt.luck.rules.RuleVariants;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Plausible starting meters when the true state is unknown. Unknown is never collapsed to an
 * empty meter: callers evaluate every returned state and report the range.
 */
public final class MeterAssumptions {
	/** Fractions of the required score used as plausible unknown fill levels. */
	public static final double[] PLAUSIBLE_FILL = {0.0, 0.5, 1.0};

	private MeterAssumptions() {
	}

	/** States for one floor with a known selected item but unknown stored score. */
	public static List<RngMeterState> unknownScore(FloorTable floor, String selectedKey, RuleVariants rules) {
		if (selectedKey == null) throw new IllegalArgumentException("Use unselected states for a meter with nothing selected");
		long required = RngMeterModel.requiredScore(floor, selectedKey, rules);
		List<RngMeterState> out = new ArrayList<>(PLAUSIBLE_FILL.length);
		for (double fill : PLAUSIBLE_FILL) out.add(new RngMeterState(floor.floor(), selectedKey, Math.round(required * fill)));
		return out;
	}

	/**
	 * Joint scenarios for several floors with known selections and unknown scores: every floor at the
	 * same fill level, one scenario per level. Floors absent from {@code selections} start empty.
	 */
	public static List<Map<FloorId, RngMeterState>> unknownScores(Map<FloorId, FloorTable> floors, Map<FloorId, String> selections, RuleVariants rules) {
		return unknownScores(floors, selections, Map.of(), rules);
	}

	/**
	 * As {@link #unknownScores(Map, Map, RuleVariants)}, with exactly read meters held fixed in every
	 * scenario. A read meter with nothing selected keeps its stored score, which only matters once a
	 * drop is selected later in the history.
	 */
	public static List<Map<FloorId, RngMeterState>> unknownScores(
		Map<FloorId, FloorTable> floors,
		Map<FloorId, String> selections,
		Map<FloorId, RngMeterState> observed,
		RuleVariants rules
	) {
		List<Map<FloorId, RngMeterState>> out = new ArrayList<>(PLAUSIBLE_FILL.length);
		for (double fill : PLAUSIBLE_FILL) {
			Map<FloorId, RngMeterState> scenario = new EnumMap<>(FloorId.class);
			selections.forEach((floor, key) -> {
				if (observed.containsKey(floor)) return;
				long required = RngMeterModel.requiredScore(floors.get(floor), key, rules);
				scenario.put(floor, new RngMeterState(floor, key, Math.round(required * fill)));
			});
			scenario.putAll(observed);
			out.add(scenario);
		}
		return out;
	}
}
