package dev.vy.drt.tracking;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class LootReconciler {
	private LootReconciler() {
	}

	public static List<ResolvedLoot> reconcile(String chestId, List<LootObservation> observations, DiagnosticRecorder diagnostics) {
		if (observations == null || observations.isEmpty()) return List.of();
		Map<String, List<LootObservation>> grouped = new LinkedHashMap<>();
		for (LootObservation observation : observations) {
			if (observation == null) continue;
			grouped.computeIfAbsent(observation.identityKey(), ignored -> new ArrayList<>()).add(observation);
		}

		List<ResolvedLoot> resolved = new ArrayList<>();
		for (Map.Entry<String, List<LootObservation>> entry : grouped.entrySet()) {
			List<LootObservation> group = entry.getValue();
			group.sort(Comparator
				.comparingInt((LootObservation o) -> o.identityStrength().rank()).reversed()
				.thenComparingLong(LootObservation::eventSequence));
			LootObservation strongest = group.getFirst();
			int quantity = strongest.quantity();
			boolean quantityConflict = false;
			for (LootObservation observation : group) {
				if (observation.quantity() == quantity) continue;
				quantityConflict = true;
				quantity = Math.max(quantity, observation.quantity());
			}
			if (quantityConflict && diagnostics != null) {
				DetectionEvent event = diagnostics.recordEvent(
					DetectionEventType.LOOT_OBSERVED,
					strongest.source(),
					Map.of(
						"chestId", chestId == null ? "" : chestId,
						"identityKey", entry.getKey(),
						"reason", "quantity_conflict"
					)
				);
				diagnostics.recordInvariantViolation(
					TrackerInvariant.LOOT_QUANTITY_CONFLICT,
					DiagnosticSeverity.WARN,
					event,
					"quantity-conflict|" + (chestId == null ? "" : chestId) + "|" + entry.getKey(),
					"GUI and chat reported different quantities for the same logical chest item. The stronger observation was preserved and the resolved quantity used the maximum instead of adding blindly.",
					"LootReconciler.reconcile",
					"LootReconciler",
					"RESOLVE_WITH_MAX_QUANTITY",
					"quantity_conflict",
					"",
					chestId,
					Integer.toString(strongest.containerId()),
					entry.getKey(),
					Map.of("observations", group.size(), "resolvedQuantity", quantity)
				);
			}
			resolved.add(new ResolvedLoot(
				entry.getKey(),
				strongest.rawName(),
				strongest.itemId(),
				strongest.identityStrength(),
				quantity,
				quantityConflict ? "max_quantity_after_quantity_conflict" : "deduplicated_observations",
				group
			));
		}
		return List.copyOf(resolved);
	}
}
