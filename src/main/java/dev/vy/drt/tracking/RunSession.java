package dev.vy.drt.tracking;

import dev.vy.drt.config.DungeonFloor;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

public final class RunSession {
	private final String id;
	private RunState state = RunState.CREATED;
	private EvidenceValue<RunMode> mode = EvidenceValue.empty();
	private EvidenceValue<DungeonFloor> floor = EvidenceValue.empty();
	private EvidenceValue<String> grade = EvidenceValue.empty();
	private final Instant startedAt;
	private Instant completedAt;
	private String completionFingerprint = "";
	private final LinkedHashSet<String> ownedChestIds = new LinkedHashSet<>();

	public RunSession(String id, Instant startedAt) {
		this.id = id == null || id.isBlank() ? "run-unknown" : id;
		this.startedAt = startedAt == null ? Instant.EPOCH : startedAt;
		this.state = RunState.ACTIVE;
	}

	public String id() {
		return id;
	}

	public RunState state() {
		return state;
	}

	public EvidenceValue<RunMode> mode() {
		return mode;
	}

	public EvidenceValue<DungeonFloor> floor() {
		return floor;
	}

	public EvidenceValue<String> grade() {
		return grade;
	}

	public Instant startedAt() {
		return startedAt;
	}

	public Instant completedAt() {
		return completedAt;
	}

	public String completionFingerprint() {
		return completionFingerprint;
	}

	public Set<String> ownedChestIds() {
		return Set.copyOf(ownedChestIds);
	}

	public EvidenceDecision updateMode(RunMode value, EvidenceStrength strength, DetectionSource source, DetectionEvent event, DiagnosticRecorder diagnostics) {
		if (state == RunState.COMPLETED) {
			return rejectCommittedMutation("mode", value, mode, event, diagnostics);
		}
		EvidenceUpdate<RunMode> update = mode.update(value, strength, source, sequence(event), observedAt(event));
		mode = update.value();
		recordEvidenceDecision("RunSession.updateMode", update.decision(), diagnostics, event, "mode");
		if (update.decision() == EvidenceDecision.CONFLICT) state = RunState.CONFLICTED;
		return update.decision();
	}

	public EvidenceDecision updateFloor(DungeonFloor value, EvidenceStrength strength, DetectionSource source, DetectionEvent event, DiagnosticRecorder diagnostics) {
		if (state == RunState.COMPLETED) {
			return rejectCommittedMutation("floor", value, floor, event, diagnostics);
		}
		EvidenceUpdate<DungeonFloor> update = floor.update(value, strength, source, sequence(event), observedAt(event));
		floor = update.value();
		recordEvidenceDecision("RunSession.updateFloor", update.decision(), diagnostics, event, "floor");
		if (update.decision() == EvidenceDecision.CONFLICT) state = RunState.CONFLICTED;
		return update.decision();
	}

	public EvidenceDecision updateGrade(String value, EvidenceStrength strength, DetectionSource source, DetectionEvent event, DiagnosticRecorder diagnostics) {
		if (state == RunState.COMPLETED) {
			return rejectCommittedMutation("grade", value, grade, event, diagnostics);
		}
		EvidenceUpdate<String> update = grade.update(value, strength, source, sequence(event), observedAt(event));
		grade = update.value();
		recordEvidenceDecision("RunSession.updateGrade", update.decision(), diagnostics, event, "grade");
		if (update.decision() == EvidenceDecision.CONFLICT) state = RunState.CONFLICTED;
		return update.decision();
	}

	public boolean complete(String fingerprint, DetectionEvent event, DiagnosticRecorder diagnostics) {
		String nextFingerprint = fingerprint == null ? "" : fingerprint;
		if (state == RunState.COMPLETED) {
			if (completionFingerprint.equals(nextFingerprint)) {
				recordDuplicateCompletion(event, diagnostics, "duplicate_completion_same_fingerprint");
			} else {
				recordDuplicateCompletion(event, diagnostics, "completion_conflict_after_commit");
			}
			return false;
		}
		state = RunState.COMPLETED;
		completedAt = observedAt(event);
		completionFingerprint = nextFingerprint;
		return true;
	}

	public boolean addChest(String chestId) {
		if (chestId == null || chestId.isBlank()) return false;
		return ownedChestIds.add(chestId);
	}

	public void abandon() {
		if (state == RunState.COMPLETED) return;
		state = RunState.ABANDONED;
	}

	private void recordEvidenceDecision(String handler, EvidenceDecision decision, DiagnosticRecorder diagnostics, DetectionEvent event, String field) {
		if (diagnostics == null || decision == EvidenceDecision.ACCEPTED || decision == EvidenceDecision.CORROBORATED) return;
		TrackerInvariant invariant = "mode".equals(field)
			? TrackerInvariant.DUNGEON_AND_KUUDRA_CONTEXTS_CANNOT_SHARE_RUN_STATE
			: decision == EvidenceDecision.REJECTED_WEAKER
			? TrackerInvariant.WEAKER_EVIDENCE_CANNOT_OVERWRITE_STRONGER_EVIDENCE
			: TrackerInvariant.CONTEXT_CONFLICT_BLOCKS_NORMAL_LOOT_GUARD;
		diagnostics.recordInvariantViolation(
			invariant,
			DiagnosticSeverity.WARN,
			event,
			"run-evidence|" + id + "|" + field,
			"Run " + field + " evidence conflicted with existing stronger or equal evidence. The existing value was preserved.",
			handler,
			handler,
			decision.name(),
			field,
			id,
			"",
			"",
			field,
			java.util.Map.of("runId", id, "field", field)
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
				TrackerInvariant.COMMITTED_RUN_IS_IMMUTABLE,
				DiagnosticSeverity.ERROR,
				event,
				"committed-run|" + id + "|" + field,
				"Evidence arrived for an already completed RunSession. The committed run was not mutated.",
				"RunSession." + field,
				"RunSession.rejectCommittedMutation",
				"REJECT_AFTER_RUN_COMMIT",
				"committed_run_immutable",
				id,
				"",
				"",
				field,
				java.util.Map.of("runId", id, "field", field, "incoming", incoming == null ? "" : incoming.toString())
			);
		}
		return EvidenceDecision.REJECTED_WEAKER;
	}

	private void recordDuplicateCompletion(DetectionEvent event, DiagnosticRecorder diagnostics, String reason) {
		if (diagnostics == null) return;
		diagnostics.recordInvariantViolation(
			TrackerInvariant.ONE_RUN_COMPLETION_COUNTS_AT_MOST_ONCE,
			DiagnosticSeverity.WARN,
			event,
			"run-completion|" + id,
			"A run completion signal was received after this RunSession was already completed. It was ignored.",
			"RunSession.complete",
			"RunSession.complete",
			"IGNORE_DUPLICATE_COMPLETION",
			reason,
			id,
			"",
			"",
			completionFingerprint,
			java.util.Map.of("runId", id, "existingFingerprint", completionFingerprint)
		);
	}

	private static long sequence(DetectionEvent event) {
		return event == null ? 0L : event.sequence();
	}

	private static Instant observedAt(DetectionEvent event) {
		return event == null ? Instant.EPOCH : event.wallTime();
	}
}
