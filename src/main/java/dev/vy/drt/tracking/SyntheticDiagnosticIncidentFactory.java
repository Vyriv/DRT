package dev.vy.drt.tracking;

import java.util.LinkedHashMap;
import java.util.Map;

public final class SyntheticDiagnosticIncidentFactory {
	public static final String INCIDENT_TYPE = "SYNTHETIC_TEST";
	public static final String DEFAULT_ROOT_KEY = "synthetic-test|player-inventory-floor";
	public static final String RUN_ID = "synthetic-run-m7-active";
	public static final String CHEST_ID = "synthetic-chest-bedrock-conflicted";
	public static final String CONTAINER_ID = "synthetic-container-184";

	private SyntheticDiagnosticIncidentFactory() {
	}

	public static DiagnosticIncident record(DiagnosticRecorder diagnostics, String rootKey, int repeatIndex) {
		if (diagnostics == null) throw new IllegalArgumentException("diagnostics");
		String key = rootKey == null || rootKey.isBlank() ? DEFAULT_ROOT_KEY : rootKey;
		diagnostics.recordEvent(
			DetectionEventType.RUN_STARTED,
			DetectionSource.CONFIRMED_SCOREBOARD,
			payload(
				"incidentType", INCIDENT_TYPE,
				"synthetic", true,
				"runSessionId", RUN_ID,
				"runState", "ACTIVE",
				"runMode", "CATACOMBS",
				"runFloor", "M7",
				"repeatIndex", repeatIndex
			)
		);
		diagnostics.recordEvent(
			DetectionEventType.CHEST_OPENED,
			DetectionSource.CONFIRMED_GUI_COMPONENT,
			payload(
				"incidentType", INCIDENT_TYPE,
				"synthetic", true,
				"runSessionId", RUN_ID,
				"chestSessionId", CHEST_ID,
				"chestType", "BEDROCK",
				"chestState", "CONFLICTED",
				"containerId", CONTAINER_ID,
				"repeatIndex", repeatIndex
			)
		);
		diagnostics.recordEvent(
			DetectionEventType.RUN_EVIDENCE,
			DetectionSource.CONFIRMED_SCOREBOARD,
			payload(
				"incidentType", INCIDENT_TYPE,
				"synthetic", true,
				"runSessionId", RUN_ID,
				"evidenceField", "floor",
				"value", "M7",
				"source", "SCOREBOARD",
				"strength", "CONFIRMED_SCOREBOARD",
				"repeatIndex", repeatIndex
			)
		);
		DetectionEvent attempted = diagnostics.recordEvent(
			DetectionEventType.CONTAINER_SNAPSHOT,
			DetectionSource.PLAYER_INVENTORY,
			payload(
				"incidentType", INCIDENT_TYPE,
				"synthetic", true,
				"runSessionId", RUN_ID,
				"chestSessionId", CHEST_ID,
				"slotOwner", "PLAYER_INVENTORY",
				"itemName", "Infernal Kuudra Key",
				"attemptedField", "floor",
				"attemptedValue", "K5",
				"attemptedSource", "PLAYER_INVENTORY",
				"trustedValue", "M7",
				"trustedSource", "SCOREBOARD",
				"repeatIndex", repeatIndex
			)
		);
		return diagnostics.recordInvariantViolation(
			INCIDENT_TYPE,
			TrackerInvariant.PLAYER_INVENTORY_CANNOT_SET_RUN_FLOOR,
			DiagnosticSeverity.ERROR,
			attempted,
			key,
			"SYNTHETIC_TEST: Player inventory evidence attempted to change a synthetic active M7 Catacombs run to K5. The evidence was rejected.",
			"DungeonRunTrackerFeature.triggerSyntheticDiagnosticIncident",
			"triggerSyntheticDiagnosticIncident",
			"REJECT_PLAYER_INVENTORY_EVIDENCE",
			"SYNTHETIC_TEST_player_inventory_has_no_run_floor_authority",
			RUN_ID,
			CHEST_ID,
			CONTAINER_ID,
			"SYNTHETIC_TEST|M7|K5|PLAYER_INVENTORY",
			payload(
				"incidentType", INCIDENT_TYPE,
				"synthetic", true,
				"summary", "Synthetic M7/K5 player-inventory floor conflict.",
				"runSessionId", RUN_ID,
				"runState", "ACTIVE",
				"runMode", "CATACOMBS",
				"runFloor", "M7",
				"chestSessionId", CHEST_ID,
				"chestState", "CONFLICTED",
				"chestType", "BEDROCK",
				"trustedEvidence", "M7 / SCOREBOARD",
				"attemptedEvidence", "K5 / PLAYER_INVENTORY",
				"violatedInvariant", TrackerInvariant.PLAYER_INVENTORY_CANNOT_SET_RUN_FLOOR.name(),
				"likelyEffect", "Synthetic test only; no real tracking state was mutated.",
				"suggestedInvestigation", "DungeonRunTrackerFeature.triggerSyntheticDiagnosticIncident",
				"unrelatedChatIncluded", false,
				"repeatIndex", repeatIndex
			)
		);
	}

	public static Map<String, Object> expected(DiagnosticIncident incident) {
		return payload(
			"schema", DiagnosticRecorder.REPLAY_SCHEMA,
			"incidentType", INCIDENT_TYPE,
			"reportId", incident == null ? "" : incident.id(),
			"synthetic", true,
			"runsCreated", 1,
			"runSessionId", RUN_ID,
			"runMode", "CATACOMBS",
			"runFloor", "M7",
			"runState", "ACTIVE",
			"chestsCreated", 1,
			"chestSessionId", CHEST_ID,
			"chestType", "BEDROCK",
			"chestState", "CONFLICTED",
			"invariant", TrackerInvariant.PLAYER_INVENTORY_CANNOT_SET_RUN_FLOOR.name(),
			"trustedEvidence", "M7 / SCOREBOARD",
			"attemptedEvidence", "K5 / PLAYER_INVENTORY",
			"unrelatedChatIncluded", false
		);
	}

	private static Map<String, Object> payload(Object... keyValues) {
		Map<String, Object> payload = new LinkedHashMap<>();
		for (int i = 0; i + 1 < keyValues.length; i += 2) {
			Object key = keyValues[i];
			Object value = keyValues[i + 1];
			if (key != null && value != null) payload.put(String.valueOf(key), value);
		}
		return payload;
	}
}
