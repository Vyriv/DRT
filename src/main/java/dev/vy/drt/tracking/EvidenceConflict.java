package dev.vy.drt.tracking;

import java.time.Instant;

public record EvidenceConflict<T>(
	T keptValue,
	EvidenceStrength keptStrength,
	DetectionSource keptSource,
	T rejectedValue,
	EvidenceStrength rejectedStrength,
	DetectionSource rejectedSource,
	long eventSequence,
	Instant observedAt,
	String reason
) {
}
