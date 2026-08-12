package dev.vy.drt.tracking;

public record EvidenceUpdate<T>(
	EvidenceValue<T> value,
	EvidenceDecision decision
) {
}
