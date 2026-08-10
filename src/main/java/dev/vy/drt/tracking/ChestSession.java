package dev.vy.drt.tracking;

import dev.vy.drt.config.ChestCostBreakdown;
import dev.vy.drt.config.DungeonFloor;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

public final class ChestSession {
	private final String id;
	private String ownerRunId = "";
	private ChestState state;
	private EvidenceValue<String> chestType = EvidenceValue.empty();
	private EvidenceValue<RunMode> contextMode = EvidenceValue.empty();
	private EvidenceValue<DungeonFloor> contextFloor = EvidenceValue.empty();
	private int containerId = -1;
	private long screenOpenSequence;
	private ChestCostBreakdown cost = new ChestCostBreakdown();
	private final List<LootObservation> observations = new ArrayList<>();
	private final Set<String> dedupKeys = new LinkedHashSet<>();
	private List<ResolvedLoot> resolvedLoot = List.of();
	private String commitFingerprint = "";

	public ChestSession(String id, String ownerRunId, ChestState state) {
		this.id = id == null || id.isBlank() ? "chest-unknown" : id;
		this.ownerRunId = ownerRunId == null ? "" : ownerRunId;
		this.state = state == null ? (this.ownerRunId.isBlank() ? ChestState.ORPHANED : ChestState.DISCOVERED) : state;
	}

	public String id() {
		return id;
	}

	public String ownerRunId() {
		return ownerRunId;
	}

	public ChestState state() {
		return state;
	}

	public EvidenceValue<String> chestType() {
		return chestType;
	}

	public EvidenceValue<RunMode> contextMode() {
		return contextMode;
	}

	public EvidenceValue<DungeonFloor> contextFloor() {
		return contextFloor;
	}

	public int containerId() {
		return containerId;
	}

	public long screenOpenSequence() {
		return screenOpenSequence;
	}

	public ChestCostBreakdown cost() {
		return cost.copy();
	}

	public List<LootObservation> observations() {
		return List.copyOf(observations);
	}

	public List<ResolvedLoot> resolvedLoot() {
		return resolvedLoot;
	}

	public String commitFingerprint() {
		return commitFingerprint;
	}

	public EvidenceDecision updateChestType(String value, EvidenceStrength strength, DetectionSource source, DetectionEvent event, DiagnosticRecorder diagnostics) {
		if (state == ChestState.COMMITTED) {
			return rejectCommittedMutation("chestType", value, chestType, event, diagnostics);
		}
		EvidenceUpdate<String> update = chestType.update(value, strength, source, event == null ? 0L : event.sequence(), event == null ? null : event.wallTime());
		chestType = update.value();
		if (update.decision() == EvidenceDecision.CONFLICT) state = ChestState.CONFLICTED;
		if (diagnostics != null && (update.decision() == EvidenceDecision.CONFLICT || update.decision() == EvidenceDecision.REJECTED_WEAKER)) {
			diagnostics.recordInvariantViolation(
				update.decision() == EvidenceDecision.REJECTED_WEAKER
					? TrackerInvariant.WEAKER_EVIDENCE_CANNOT_OVERWRITE_STRONGER_EVIDENCE
					: TrackerInvariant.CONTEXT_CONFLICT_BLOCKS_NORMAL_LOOT_GUARD,
				DiagnosticSeverity.WARN,
				event,
				"chest-type|" + id,
				"Chest type evidence conflicted with existing evidence. The existing value was preserved.",
				"ChestSession.updateChestType",
				"ChestSession.updateChestType",
				update.decision().name(),
				"chest_type_evidence_conflict",
				ownerRunId,
				id,
				Integer.toString(containerId),
				value,
				java.util.Map.of("chestId", id, "incoming", value == null ? "" : value)
			);
		}
		return update.decision();
	}

	public EvidenceDecision updateContextMode(RunMode value, EvidenceStrength strength, DetectionSource source, DetectionEvent event, DiagnosticRecorder diagnostics) {
		if (state == ChestState.COMMITTED) {
			return rejectCommittedMutation("contextMode", value, contextMode, event, diagnostics);
		}
		EvidenceUpdate<RunMode> update = contextMode.update(value, strength, source, event == null ? 0L : event.sequence(), event == null ? null : event.wallTime());
		contextMode = update.value();
		recordContextEvidenceDecision("ChestSession.updateContextMode", update.decision(), diagnostics, event, "mode", value == null ? "" : value.name());
		if (update.decision() == EvidenceDecision.CONFLICT) state = ChestState.CONFLICTED;
		return update.decision();
	}

	public EvidenceDecision updateContextFloor(DungeonFloor value, EvidenceStrength strength, DetectionSource source, DetectionEvent event, DiagnosticRecorder diagnostics) {
		if (state == ChestState.COMMITTED) {
			return rejectCommittedMutation("contextFloor", value, contextFloor, event, diagnostics);
		}
		EvidenceUpdate<DungeonFloor> update = contextFloor.update(value, strength, source, event == null ? 0L : event.sequence(), event == null ? null : event.wallTime());
		contextFloor = update.value();
		recordContextEvidenceDecision("ChestSession.updateContextFloor", update.decision(), diagnostics, event, "floor", value == null ? "" : value.name());
		if (update.decision() == EvidenceDecision.CONFLICT) state = ChestState.CONFLICTED;
		return update.decision();
	}

	private void recordContextEvidenceDecision(
		String handler,
		EvidenceDecision decision,
		DiagnosticRecorder diagnostics,
		DetectionEvent event,
		String field,
		String incoming
	) {
		if (diagnostics == null || (decision != EvidenceDecision.CONFLICT && decision != EvidenceDecision.REJECTED_WEAKER)) return;
		TrackerInvariant invariant = "mode".equals(field)
			? TrackerInvariant.DUNGEON_AND_KUUDRA_CONTEXTS_CANNOT_SHARE_RUN_STATE
			: decision == EvidenceDecision.REJECTED_WEAKER
				? TrackerInvariant.WEAKER_EVIDENCE_CANNOT_OVERWRITE_STRONGER_EVIDENCE
				: TrackerInvariant.CONTEXT_CONFLICT_BLOCKS_NORMAL_LOOT_GUARD;
		diagnostics.recordInvariantViolation(
			invariant,
			DiagnosticSeverity.WARN,
			event,
			"chest-context|" + field + "|" + id,
			"Chest context evidence conflicted with existing evidence. The existing value was preserved.",
			handler,
			handler,
			decision.name(),
			"chest_context_evidence_conflict",
			ownerRunId,
			id,
			Integer.toString(containerId),
			incoming,
			java.util.Map.of("chestId", id, "field", field, "incoming", incoming)
		);
	}

	private <T> EvidenceDecision rejectCommittedMutation(
		String field,
		T incoming,
		EvidenceValue<T> existing,
		DetectionEvent event,
		DiagnosticRecorder diagnostics
	) {
		if (existing != null && existing.isKnown() && Objects.equals(existing.value(), incoming)) {
			return EvidenceDecision.CORROBORATED;
		}
		if (diagnostics != null) {
			diagnostics.recordInvariantViolation(
				TrackerInvariant.COMMITTED_CHEST_IS_IMMUTABLE,
				DiagnosticSeverity.ERROR,
				event,
				"committed-chest|" + id + "|" + field,
				"Evidence arrived for an already committed ChestSession. The committed chest was not mutated.",
				"ChestSession." + field,
				"ChestSession.rejectCommittedMutation",
				"REJECT_AFTER_CHEST_COMMIT",
				"committed_chest_immutable",
				ownerRunId,
				id,
				Integer.toString(containerId),
				field,
				java.util.Map.of("chestId", id, "field", field, "incoming", incoming == null ? "" : incoming.toString())
			);
		}
		return EvidenceDecision.REJECTED_WEAKER;
	}

	public boolean assignOwner(String runId, DetectionEvent event, DiagnosticRecorder diagnostics) {
		String nextOwner = runId == null ? "" : runId;
		if (nextOwner.isBlank()) return false;
		if (ownerRunId.isBlank()) {
			ownerRunId = nextOwner;
			if (state == ChestState.ORPHANED) state = ChestState.DISCOVERED;
			return true;
		}
		if (ownerRunId.equals(nextOwner)) return true;
		state = ChestState.CONFLICTED;
		if (diagnostics != null) {
			diagnostics.recordInvariantViolation(
				TrackerInvariant.ONE_CHEST_HAS_AT_MOST_ONE_OWNER_RUN,
				DiagnosticSeverity.ERROR,
				event,
				"chest-owner|" + id,
				"A chest was offered a second owner RunSession. The original owner was preserved.",
				"ChestSession.assignOwner",
				"ChestSession.assignOwner",
				"REJECT_SECOND_OWNER",
				"owner_conflict",
				ownerRunId,
				id,
				Integer.toString(containerId),
				id,
				java.util.Map.of("existingOwner", ownerRunId, "rejectedOwner", nextOwner)
			);
		}
		return false;
	}

	public void open(int containerId, long screenOpenSequence) {
		this.containerId = containerId;
		this.screenOpenSequence = screenOpenSequence;
		state = ChestState.OPENED;
	}

	public boolean setCost(ChestCostBreakdown cost, DetectionEvent event, DiagnosticRecorder diagnostics) {
		if (state == ChestState.COMMITTED) {
			if (diagnostics != null) {
				diagnostics.recordInvariantViolation(
					TrackerInvariant.COMMITTED_CHEST_IS_IMMUTABLE,
					DiagnosticSeverity.ERROR,
					event,
					"committed-chest-cost|" + id,
					"Cost/modifier evidence arrived for an already committed ChestSession. The committed chest was not mutated.",
					"ChestSession.setCost",
					"ChestSession.setCost",
					"REJECT_COST_AFTER_COMMIT",
					"committed_chest_immutable",
					ownerRunId,
					id,
					Integer.toString(containerId),
					id,
					java.util.Map.of("chestId", id)
				);
			}
			return false;
		}
		this.cost = cost == null ? new ChestCostBreakdown() : cost.copy();
		return true;
	}

	public boolean observeLoot(LootObservation observation, DiagnosticRecorder diagnostics) {
		if (observation == null) return false;
		if (state == ChestState.COMMITTED) {
			if (diagnostics != null) {
				diagnostics.recordInvariantViolation(
					TrackerInvariant.COMMITTED_CHEST_IS_IMMUTABLE,
					DiagnosticSeverity.ERROR,
					null,
					"committed-chest|" + id,
					"Loot arrived for an already committed ChestSession. The committed chest was not mutated.",
					"ChestSession.observeLoot",
					"ChestSession.observeLoot",
					"REJECT_AFTER_COMMIT",
					"committed_chest_immutable",
					ownerRunId,
					id,
					Integer.toString(containerId),
					observation.dedupKey(),
					java.util.Map.of("observationId", observation.observationId())
				);
			}
			return false;
		}
		if (!dedupKeys.add(observation.dedupKey())) {
			if (diagnostics != null) {
				DetectionEvent event = diagnostics.recordEvent(
					DetectionEventType.LOOT_OBSERVED,
					observation.source(),
					java.util.Map.of(
						"chestId", id,
						"dedupKey", observation.dedupKey(),
						"rawName", observation.rawName()
					)
				);
				diagnostics.recordDecision(
					event,
					"ChestSession.observeLoot",
					"IGNORE_DUPLICATE_LOOT_OBSERVATION",
					"chest_lifetime_dedup",
					ownerRunId,
					id,
					Integer.toString(containerId),
					observation.dedupKey(),
					java.util.Map.of("observationId", observation.observationId())
				);
			}
			return false;
		}
		observations.add(observation);
		state = ChestState.RECONCILING;
		resolvedLoot = LootReconciler.reconcile(id, observations, diagnostics);
		return true;
	}

	public void commit(String fingerprint) {
		if (state == ChestState.COMMITTED) return;
		commitFingerprint = fingerprint == null ? "" : fingerprint;
		state = ChestState.COMMITTED;
	}
}
