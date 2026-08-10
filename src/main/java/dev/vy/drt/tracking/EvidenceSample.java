package dev.vy.drt.tracking;

import java.time.Instant;

public record EvidenceSample<T>(
	T value,
	EvidenceStrength strength,
	DetectionSource source,
	long eventSequence,
	Instant observedAt,
	EvidenceDecision decision,
	String reason
) {
}
