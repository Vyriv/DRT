package dev.vy.drt.tracking;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public record EvidenceValue<T>(
	T value,
	EvidenceStrength strength,
	DetectionSource source,
	long eventSequence,
	Instant observedAt,
	List<EvidenceSample<T>> history,
	List<EvidenceConflict<T>> conflicts
) {
	public EvidenceValue {
		strength = strength == null ? EvidenceStrength.NONE : strength;
		source = source == null ? DetectionSource.NONE : source;
		observedAt = observedAt == null ? Instant.EPOCH : observedAt;
		history = history == null ? List.of() : List.copyOf(history);
		conflicts = conflicts == null ? List.of() : List.copyOf(conflicts);
	}

	public static <T> EvidenceValue<T> empty() {
		return new EvidenceValue<>(null, EvidenceStrength.NONE, DetectionSource.NONE, 0L, Instant.EPOCH, List.of(), List.of());
	}

	public boolean isKnown() {
		return value != null && strength != EvidenceStrength.NONE;
	}

	public EvidenceUpdate<T> update(
		T incoming,
		EvidenceStrength incomingStrength,
		DetectionSource incomingSource,
		long incomingSequence,
		Instant incomingObservedAt
	) {
		EvidenceStrength nextStrength = incomingStrength == null ? EvidenceStrength.NONE : incomingStrength;
		DetectionSource nextSource = incomingSource == null ? DetectionSource.NONE : incomingSource;
		Instant nextObservedAt = incomingObservedAt == null ? Instant.EPOCH : incomingObservedAt;
		if (incoming == null || nextStrength == EvidenceStrength.NONE) {
			return appendSample(incoming, nextStrength, nextSource, incomingSequence, nextObservedAt, EvidenceDecision.REJECTED_WEAKER, "empty_or_none_evidence");
		}
		if (!isKnown()) {
			return replace(incoming, nextStrength, nextSource, incomingSequence, nextObservedAt, EvidenceDecision.ACCEPTED, "first_evidence");
		}
		if (Objects.equals(value, incoming)) {
			return appendSample(incoming, nextStrength, nextSource, incomingSequence, nextObservedAt, EvidenceDecision.CORROBORATED, "same_value");
		}
		if (nextStrength.strongerThan(strength)) {
			return replace(incoming, nextStrength, nextSource, incomingSequence, nextObservedAt, EvidenceDecision.ACCEPTED, "stronger_evidence");
		}
		if (nextStrength.weakerThan(strength)) {
			return addConflict(incoming, nextStrength, nextSource, incomingSequence, nextObservedAt, EvidenceDecision.REJECTED_WEAKER, "weaker_evidence_cannot_overwrite");
		}
		return addConflict(incoming, nextStrength, nextSource, incomingSequence, nextObservedAt, EvidenceDecision.CONFLICT, "equal_strength_contradiction");
	}

	private EvidenceUpdate<T> replace(
		T incoming,
		EvidenceStrength incomingStrength,
		DetectionSource incomingSource,
		long incomingSequence,
		Instant incomingObservedAt,
		EvidenceDecision decision,
		String reason
	) {
		List<EvidenceSample<T>> nextHistory = append(history, new EvidenceSample<>(
			incoming,
			incomingStrength,
			incomingSource,
			incomingSequence,
			incomingObservedAt,
			decision,
			reason
		));
		return new EvidenceUpdate<>(
			new EvidenceValue<>(incoming, incomingStrength, incomingSource, incomingSequence, incomingObservedAt, nextHistory, conflicts),
			decision
		);
	}

	private EvidenceUpdate<T> appendSample(
		T incoming,
		EvidenceStrength incomingStrength,
		DetectionSource incomingSource,
		long incomingSequence,
		Instant incomingObservedAt,
		EvidenceDecision decision,
		String reason
	) {
		List<EvidenceSample<T>> nextHistory = append(history, new EvidenceSample<>(
			incoming,
			incomingStrength,
			incomingSource,
			incomingSequence,
			incomingObservedAt,
			decision,
			reason
		));
		return new EvidenceUpdate<>(
			new EvidenceValue<>(value, strength, source, eventSequence, observedAt, nextHistory, conflicts),
			decision
		);
	}

	private EvidenceUpdate<T> addConflict(
		T incoming,
		EvidenceStrength incomingStrength,
		DetectionSource incomingSource,
		long incomingSequence,
		Instant incomingObservedAt,
		EvidenceDecision decision,
		String reason
	) {
		List<EvidenceConflict<T>> nextConflicts = append(conflicts, new EvidenceConflict<>(
			value,
			strength,
			source,
			incoming,
			incomingStrength,
			incomingSource,
			incomingSequence,
			incomingObservedAt,
			reason
		));
		List<EvidenceSample<T>> nextHistory = append(history, new EvidenceSample<>(
			incoming,
			incomingStrength,
			incomingSource,
			incomingSequence,
			incomingObservedAt,
			decision,
			reason
		));
		return new EvidenceUpdate<>(
			new EvidenceValue<>(value, strength, source, eventSequence, observedAt, nextHistory, nextConflicts),
			decision
		);
	}

	private static <T> List<T> append(List<T> source, T value) {
		List<T> next = new ArrayList<>(source == null ? List.of() : source);
		next.add(value);
		return List.copyOf(next);
	}
}
