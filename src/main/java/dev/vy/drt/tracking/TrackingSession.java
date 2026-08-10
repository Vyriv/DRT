package dev.vy.drt.tracking;

import dev.vy.drt.config.DungeonFloor;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class TrackingSession {
	private static final int RECENT_COMPLETED_LIMIT = 16;

	private final String id;
	private final String serverInstanceId;
	private final TrackerClock clock;
	private final DiagnosticRecorder diagnostics;
	private final Map<String, RunSession> runs = new LinkedHashMap<>();
	private final Map<String, ChestSession> chests = new LinkedHashMap<>();
	private final ArrayDeque<String> recentlyCompletedRunIds = new ArrayDeque<>();
	private RunSession activeRun;
	private long runCounter;
	private long chestCounter;

	public TrackingSession(String id, String serverInstanceId, TrackerClock clock, DiagnosticRecorder diagnostics) {
		this.id = id == null || id.isBlank() ? "tracking-session" : id;
		this.serverInstanceId = serverInstanceId == null || serverInstanceId.isBlank() ? "server-unknown" : serverInstanceId;
		this.clock = clock == null ? new SystemTrackerClock() : clock;
		this.diagnostics = diagnostics == null ? new DiagnosticRecorder(this.clock) : diagnostics;
	}

	public String id() {
		return id;
	}

	public String serverInstanceId() {
		return serverInstanceId;
	}

	public DiagnosticRecorder diagnostics() {
		return diagnostics;
	}

	public RunSession activeRun() {
		return activeRun;
	}

	public ChestSession chest(String id) {
		return chests.get(id);
	}

	public RunSession run(String id) {
		return runs.get(id);
	}

	public RunSession startRun(RunMode mode, DungeonFloor floor, DetectionSource source, EvidenceStrength strength) {
		DetectionEvent event = diagnostics.recordEvent(
			DetectionEventType.RUN_STARTED,
			source,
			payload("mode", mode, "floor", floor)
		);
		if (activeRun != null && activeRun.state() == RunState.ACTIVE) {
			activeRun.abandon();
		}
		RunSession run = new RunSession(nextRunId(), clock.wallTime());
		run.updateMode(mode == null ? RunMode.UNKNOWN : mode, strength, source, event, diagnostics);
		if (floor != null && floor != DungeonFloor.UNKNOWN) {
			run.updateFloor(floor, strength, source, event, diagnostics);
		}
		runs.put(run.id(), run);
		activeRun = run;
		return run;
	}

	public boolean updateActiveRunFloor(DungeonFloor floor, EvidenceStrength strength, DetectionSource source) {
		if (source == DetectionSource.PLAYER_INVENTORY) {
			DetectionEvent event = diagnostics.recordEvent(
				DetectionEventType.RUN_EVIDENCE,
				source,
				payload("floor", floor)
			);
			diagnostics.recordInvariantViolation(
				TrackerInvariant.PLAYER_INVENTORY_CANNOT_SET_RUN_FLOOR,
				DiagnosticSeverity.ERROR,
				event,
				"player-inventory-floor|" + floor,
				"Player inventory evidence attempted to set a run floor. The evidence was rejected.",
				"TrackingSession.updateActiveRunFloor",
				"TrackingSession.updateActiveRunFloor",
				"REJECT_PLAYER_INVENTORY_FLOOR",
				"player_inventory_has_no_context_authority",
				activeRun == null ? "" : activeRun.id(),
				"",
				"",
				String.valueOf(floor),
				payload("floor", floor)
			);
			return false;
		}
		if (activeRun == null) return false;
		DetectionEvent event = diagnostics.recordEvent(
			DetectionEventType.RUN_EVIDENCE,
			source,
			payload("floor", floor)
		);
		EvidenceDecision decision = activeRun.updateFloor(floor, strength, source, event, diagnostics);
		return decision == EvidenceDecision.ACCEPTED || decision == EvidenceDecision.CORROBORATED;
	}

	public boolean updateActiveRunGrade(String grade, EvidenceStrength strength, DetectionSource source) {
		if (activeRun == null) return false;
		DetectionEvent event = diagnostics.recordEvent(
			DetectionEventType.RUN_EVIDENCE,
			source,
			payload("grade", grade)
		);
		EvidenceDecision decision = activeRun.updateGrade(grade, strength, source, event, diagnostics);
		return decision == EvidenceDecision.ACCEPTED || decision == EvidenceDecision.CORROBORATED;
	}

	public boolean completeActiveRun(DungeonFloor floor, String grade, String fingerprint) {
		if (activeRun == null) {
			activeRun = startRun(RunMode.UNKNOWN, DungeonFloor.UNKNOWN, DetectionSource.CONFIRMED_COMPLETION, EvidenceStrength.CONFIRMED_COMPLETION);
		}
		return completeRun(activeRun.id(), floor, grade, fingerprint);
	}

	public boolean completeRun(String runId, DungeonFloor floor, String grade, String fingerprint) {
		RunSession run = runs.get(runId);
		if (run == null) {
			DetectionEvent event = diagnostics.recordEvent(
				DetectionEventType.RUN_COMPLETED,
				DetectionSource.CONFIRMED_COMPLETION,
				payload("runId", runId, "floor", floor, "grade", grade, "fingerprint", fingerprint)
			);
			diagnostics.recordDecision(
				event,
				"TrackingSession.completeRun",
				"IGNORE_COMPLETION_WITHOUT_RUN",
				"missing_run_session",
				runId,
				"",
				"",
				fingerprint,
				payload("runId", runId)
			);
			return false;
		}
		DetectionEvent event = diagnostics.recordEvent(
			DetectionEventType.RUN_COMPLETED,
			DetectionSource.CONFIRMED_COMPLETION,
			payload("floor", floor, "grade", grade, "fingerprint", fingerprint)
		);
		if (floor != null && floor != DungeonFloor.UNKNOWN) {
			run.updateFloor(floor, EvidenceStrength.CONFIRMED_COMPLETION, DetectionSource.CONFIRMED_COMPLETION, event, diagnostics);
		}
		if (grade != null && !grade.isBlank()) {
			run.updateGrade(grade, EvidenceStrength.CONFIRMED_COMPLETION, DetectionSource.CONFIRMED_COMPLETION, event, diagnostics);
		}
		boolean completed = run.complete(fingerprint, event, diagnostics);
		if (completed) {
			recentlyCompletedRunIds.remove(run.id());
			recentlyCompletedRunIds.addFirst(run.id());
			while (recentlyCompletedRunIds.size() > RECENT_COMPLETED_LIMIT) recentlyCompletedRunIds.removeLast();
			if (activeRun == run) activeRun = null;
		}
		return completed;
	}

	public boolean abandonActiveRun(DetectionEventType type, DetectionSource source, String reason) {
		if (activeRun == null) return false;
		DetectionEvent event = diagnostics.recordEvent(
			type == null ? DetectionEventType.WORLD_CHANGED : type,
			source == null ? DetectionSource.SYSTEM : source,
			payload("runId", activeRun.id(), "reason", reason)
		);
		String runId = activeRun.id();
		activeRun.abandon();
		diagnostics.recordDecision(
			event,
			"TrackingSession.abandonActiveRun",
			"ABANDON_ACTIVE_RUN",
			reason == null || reason.isBlank() ? "runtime_transition" : reason,
			runId,
			"",
			"",
			runId,
			payload("runId", runId)
		);
		activeRun = null;
		return true;
	}

	public ChestSession openChest(String ownerRunId, String chestTitle, int containerId, DetectionSource source) {
		boolean orphan = ownerRunId == null || ownerRunId.isBlank();
		DetectionEvent event = diagnostics.recordEvent(
			DetectionEventType.CHEST_OPENED,
			source,
			payload("ownerRunId", ownerRunId, "chestTitle", chestTitle, "containerId", containerId)
		);
		ChestSession chest = new ChestSession(nextChestId(), orphan ? "" : ownerRunId, orphan ? ChestState.ORPHANED : ChestState.OPENED);
		chest.open(containerId, event.sequence());
		chest.updateChestType(chestTitle, EvidenceStrength.CONFIRMED_GUI_COMPONENT, source, event, diagnostics);
		chests.put(chest.id(), chest);
		if (orphan) {
			diagnostics.recordInvariantViolation(
				TrackerInvariant.ORPHAN_CHEST_CANNOT_SILENTLY_INVENT_RUN,
				DiagnosticSeverity.WARN,
				event,
				"orphan-chest|" + chest.id(),
				"A reward chest appeared without deterministic run ownership. It was kept as ORPHANED instead of inventing a run from location or recent context.",
				"TrackingSession.openChest",
				"TrackingSession.openChest",
				"CREATE_ORPHAN_CHEST",
				"missing_owner_evidence",
				"",
				chest.id(),
				Integer.toString(containerId),
				chest.id(),
				payload("chestTitle", chestTitle)
			);
			if (activeRun != null && activeRun.state() != RunState.COMPLETED && activeRun.state() != RunState.ABANDONED) {
				diagnostics.recordInvariantViolation(
					TrackerInvariant.HISTORICAL_REWARD_CLAIM_CANNOT_MUTATE_ACTIVE_RUN,
					DiagnosticSeverity.WARN,
					event,
					"historical-reward|" + activeRun.id() + "|" + chest.id(),
					"A reward chest was opened while another run was active. The reward was kept orphaned and the active RunSession was not mutated.",
					"TrackingSession.openChest",
					"TrackingSession.openChest",
					"KEEP_ACTIVE_RUN_UNCHANGED",
					"orphan_reward_during_active_run",
					activeRun.id(),
					chest.id(),
					Integer.toString(containerId),
					chest.id(),
					payload("activeRunId", activeRun.id(), "chestTitle", chestTitle)
				);
				diagnostics.recordInvariantViolation(
					TrackerInvariant.PHYSICAL_LOCATION_DOES_NOT_IMPLY_CHEST_OWNERSHIP,
					DiagnosticSeverity.WARN,
					event,
					"physical-location-ownership|" + activeRun.id() + "|" + chest.id(),
					"Current location or matching floor was not used as chest ownership evidence.",
					"TrackingSession.openChest",
					"TrackingSession.openChest",
					"REQUIRE_EXPLICIT_OWNER_EVIDENCE",
					"location_not_owner_evidence",
					activeRun.id(),
					chest.id(),
					Integer.toString(containerId),
					chest.id(),
					payload("activeRunId", activeRun.id(), "chestTitle", chestTitle)
				);
			}
			return chest;
		}
		RunSession owner = runs.get(ownerRunId);
		if (owner != null) {
			owner.addChest(chest.id());
			if (owner.mode().isKnown()) {
				chest.updateContextMode(owner.mode().value(), EvidenceStrength.AUTHORITATIVE_INTERNAL_IDENTITY, DetectionSource.AUTHORITATIVE_INTERNAL_IDENTITY, event, diagnostics);
			}
			if (owner.floor().isKnown()) {
				chest.updateContextFloor(owner.floor().value(), EvidenceStrength.AUTHORITATIVE_INTERNAL_IDENTITY, DetectionSource.AUTHORITATIVE_INTERNAL_IDENTITY, event, diagnostics);
			}
		}
		return chest;
	}

	public boolean updateChestContextFloor(String chestId, DungeonFloor floor, EvidenceStrength strength, DetectionSource source) {
		ChestSession chest = chests.get(chestId);
		if (chest == null) return false;
		DetectionEvent event = diagnostics.recordEvent(
			DetectionEventType.CHEST_OPENED,
			source == null ? DetectionSource.NONE : source,
			payload("chestId", chestId, "floor", floor)
		);
		if (source == DetectionSource.PLAYER_INVENTORY) {
			diagnostics.recordInvariantViolation(
				TrackerInvariant.PLAYER_INVENTORY_CANNOT_SET_RUN_FLOOR,
				DiagnosticSeverity.ERROR,
				event,
				"player-inventory-chest-floor|" + chestId + "|" + floor,
				"Player inventory evidence attempted to set reward chest floor context. The evidence was rejected.",
				"TrackingSession.updateChestContextFloor",
				"TrackingSession.updateChestContextFloor",
				"REJECT_PLAYER_INVENTORY_CHEST_FLOOR",
				"player_inventory_has_no_chest_authority",
				chest.ownerRunId(),
				chest.id(),
				Integer.toString(chest.containerId()),
				floor == null ? "" : floor.name(),
				payload("chestId", chestId, "floor", floor)
			);
			return false;
		}
		EvidenceDecision decision = chest.updateContextFloor(floor, strength, source, event, diagnostics);
		return decision == EvidenceDecision.ACCEPTED || decision == EvidenceDecision.CORROBORATED;
	}

	public boolean updateChestContextMode(String chestId, RunMode mode, EvidenceStrength strength, DetectionSource source) {
		ChestSession chest = chests.get(chestId);
		if (chest == null) return false;
		DetectionEvent event = diagnostics.recordEvent(
			DetectionEventType.CHEST_OPENED,
			source == null ? DetectionSource.NONE : source,
			payload("chestId", chestId, "mode", mode)
		);
		if (source == DetectionSource.PLAYER_INVENTORY) {
			diagnostics.recordInvariantViolation(
				TrackerInvariant.PLAYER_INVENTORY_CANNOT_SET_RUN_MODE,
				DiagnosticSeverity.ERROR,
				event,
				"player-inventory-chest-mode|" + chestId + "|" + mode,
				"Player inventory evidence attempted to set reward chest mode context. The evidence was rejected.",
				"TrackingSession.updateChestContextMode",
				"TrackingSession.updateChestContextMode",
				"REJECT_PLAYER_INVENTORY_CHEST_MODE",
				"player_inventory_has_no_chest_authority",
				chest.ownerRunId(),
				chest.id(),
				Integer.toString(chest.containerId()),
				mode == null ? "" : mode.name(),
				payload("chestId", chestId, "mode", mode)
			);
			return false;
		}
		EvidenceDecision decision = chest.updateContextMode(mode, strength, source, event, diagnostics);
		return decision == EvidenceDecision.ACCEPTED || decision == EvidenceDecision.CORROBORATED;
	}

	public boolean updateChestCost(String chestId, dev.vy.drt.config.ChestCostBreakdown cost) {
		return updateChestCost(chestId, cost, DetectionSource.CONFIRMED_GUI_COMPONENT);
	}

	public boolean updateChestCost(String chestId, dev.vy.drt.config.ChestCostBreakdown cost, DetectionSource source) {
		ChestSession chest = chests.get(chestId);
		if (chest == null) return false;
		DetectionEvent event = diagnostics.recordEvent(
			DetectionEventType.MODIFIER_OBSERVED,
			source == null ? DetectionSource.NONE : source,
			payload("chestId", chestId)
		);
		if (source == DetectionSource.PLAYER_INVENTORY) {
			diagnostics.recordInvariantViolation(
				TrackerInvariant.PLAYER_INVENTORY_CANNOT_SET_CHEST_COST,
				DiagnosticSeverity.ERROR,
				event,
				"player-inventory-cost|" + chestId,
				"Player inventory evidence attempted to set chest cost/modifier state. The evidence was rejected.",
				"TrackingSession.updateChestCost",
				"TrackingSession.updateChestCost",
				"REJECT_PLAYER_INVENTORY_COST",
				"player_inventory_has_no_chest_authority",
				chest.ownerRunId(),
				chest.id(),
				Integer.toString(chest.containerId()),
				chest.id(),
				payload("chestId", chestId)
			);
			return false;
		}
		boolean updated = chest.setCost(cost, event, diagnostics);
		if (!updated) return false;
		diagnostics.recordDecision(
			event,
			"TrackingSession.updateChestCost",
			"UPDATE_CHEST_COST",
			"cost_owned_by_chest_session",
			chest.ownerRunId(),
			chest.id(),
			Integer.toString(chest.containerId()),
			chest.id(),
			payload("hasCost", cost != null)
		);
		return true;
	}

	public boolean observeLoot(String chestId, LootObservation observation) {
		ChestSession chest = chests.get(chestId);
		if (chest == null) return false;
		if (observation.slotOwner() == SlotOwner.PLAYER_INVENTORY) {
			DetectionEvent event = diagnostics.recordEvent(
				DetectionEventType.LOOT_OBSERVED,
				DetectionSource.PLAYER_INVENTORY,
				payload("chestId", chestId, "rawName", observation.rawName(), "itemId", observation.itemId())
			);
			diagnostics.recordInvariantViolation(
				TrackerInvariant.PLAYER_INVENTORY_CANNOT_SET_RUN_FLOOR,
				DiagnosticSeverity.ERROR,
				event,
				"player-inventory-loot|" + chestId + "|" + observation.identityKey(),
				"Player inventory contents were seen during reward scanning and were rejected as chest/run context evidence.",
				"TrackingSession.observeLoot",
				"TrackingSession.observeLoot",
				"REJECT_PLAYER_INVENTORY_LOOT",
				"player_inventory_has_no_chest_authority",
				chest.ownerRunId(),
				chest.id(),
				Integer.toString(chest.containerId()),
				observation.dedupKey(),
				payload("rawName", observation.rawName(), "itemId", observation.itemId())
			);
			return false;
		}
		if (observation.identityStrength() == LootIdentityStrength.UNRESOLVED || observation.itemId().isBlank()) {
			DetectionEvent event = diagnostics.recordEvent(
				DetectionEventType.LOOT_OBSERVED,
				observation.source(),
				payload("chestId", chestId, "rawName", observation.rawName(), "normalizedName", observation.normalizedName())
			);
			diagnostics.recordInvariantViolation(
				TrackerInvariant.UNKNOWN_ITEM_CANNOT_USE_FIRST_SEARCH_RESULT,
				DiagnosticSeverity.WARN,
				event,
				"unresolved-loot|" + chestId + "|" + observation.normalizedName(),
				"Loot identity was not exact or strictly mapped. The observation remained unresolved instead of using a fuzzy first search result.",
				"TrackingSession.observeLoot",
				"TrackingSession.observeLoot",
				"KEEP_UNRESOLVED",
				"unresolved_identity",
				chest.ownerRunId(),
				chest.id(),
				Integer.toString(chest.containerId()),
				observation.identityKey(),
				payload("rawName", observation.rawName(), "normalizedName", observation.normalizedName())
			);
		}
		return chest.observeLoot(observation, diagnostics);
	}

	public boolean commitChest(String chestId, String fingerprint) {
		ChestSession chest = chests.get(chestId);
		if (chest == null) return false;
		DetectionEvent event = diagnostics.recordEvent(
			DetectionEventType.PERSISTENCE,
			DetectionSource.PERSISTENCE,
			payload("chestId", chestId, "fingerprint", fingerprint)
		);
		chest.commit(fingerprint);
		diagnostics.recordDecision(
			event,
			"TrackingSession.commitChest",
			"COMMIT_CHEST_SESSION",
			"chest_commit_fingerprint_recorded",
			chest.ownerRunId(),
			chest.id(),
			Integer.toString(chest.containerId()),
			fingerprint,
			payload("fingerprint", fingerprint)
		);
		return true;
	}

	public TrackingSnapshot snapshot() {
		Map<String, RunState> runStates = new LinkedHashMap<>();
		for (Map.Entry<String, RunSession> entry : runs.entrySet()) {
			runStates.put(entry.getKey(), entry.getValue().state());
		}
		Map<String, ChestState> chestStates = new LinkedHashMap<>();
		Map<String, String> chestOwners = new LinkedHashMap<>();
		Map<String, dev.vy.drt.config.ChestCostBreakdown> chestCosts = new LinkedHashMap<>();
		Map<String, RunMode> chestModes = new LinkedHashMap<>();
		Map<String, DungeonFloor> chestFloors = new LinkedHashMap<>();
		Map<String, List<ResolvedLoot>> chestLoot = new LinkedHashMap<>();
		for (Map.Entry<String, ChestSession> entry : chests.entrySet()) {
			chestStates.put(entry.getKey(), entry.getValue().state());
			chestOwners.put(entry.getKey(), entry.getValue().ownerRunId());
			chestCosts.put(entry.getKey(), entry.getValue().cost());
			chestModes.put(entry.getKey(), entry.getValue().contextMode().isKnown()
				? entry.getValue().contextMode().value()
				: RunMode.UNKNOWN);
			chestFloors.put(entry.getKey(), entry.getValue().contextFloor().isKnown()
				? entry.getValue().contextFloor().value()
				: DungeonFloor.UNKNOWN);
			chestLoot.put(entry.getKey(), entry.getValue().resolvedLoot());
		}
		int committedChests = (int) chestStates.values().stream()
			.filter(state -> state == ChestState.COMMITTED)
			.count();
		int abandonedRuns = (int) runStates.values().stream()
			.filter(state -> state == RunState.ABANDONED)
			.count();
		List<String> invariants = diagnostics.incidents().stream()
			.flatMap(incident -> incident.invariants().stream())
			.map(Enum::name)
			.distinct()
			.toList();
		return new TrackingSnapshot(
			activeRun == null ? "" : activeRun.id(),
			activeRun == null || !activeRun.mode().isKnown() ? RunMode.UNKNOWN : activeRun.mode().value(),
			activeRun == null || !activeRun.floor().isKnown() ? DungeonFloor.UNKNOWN : activeRun.floor().value(),
			activeRun == null || !activeRun.grade().isKnown() ? "" : activeRun.grade().value(),
			recentlyCompletedRunIds.size(),
			abandonedRuns,
			chests.size(),
			committedChests,
			Map.copyOf(runStates),
			Map.copyOf(chestStates),
			Map.copyOf(chestOwners),
			Map.copyOf(chestCosts),
			Map.copyOf(chestModes),
			Map.copyOf(chestFloors),
			Map.copyOf(chestLoot),
			invariants
		);
	}

	private String nextRunId() {
		return id + "-run-" + (++runCounter);
	}

	private String nextChestId() {
		return id + "-chest-" + (++chestCounter);
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
