package dev.vy.drt.tracking;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

public record DetectionEvent(
	long sequence,
	Instant wallTime,
	long monotonicNanos,
	DetectionEventType type,
	DetectionSource source,
	Map<String, Object> payload
) {
	public DetectionEvent {
		wallTime = wallTime == null ? Instant.EPOCH : wallTime;
		type = type == null ? DetectionEventType.DIAGNOSTIC : type;
		source = source == null ? DetectionSource.NONE : source;
		payload = payload == null ? Map.of() : Map.copyOf(payload);
	}

	public Map<String, Object> safePayload() {
		Map<String, Object> safe = new LinkedHashMap<>();
		for (Map.Entry<String, Object> entry : payload.entrySet()) {
			if (entry.getKey() == null || entry.getValue() == null) continue;
			String key = entry.getKey();
			if (key.equalsIgnoreCase("token") || key.equalsIgnoreCase("session") || key.equalsIgnoreCase("auth")) continue;
			safe.put(key, sanitizeScalar(entry.getValue()));
		}
		return safe;
	}

	private Object sanitizeScalar(Object value) {
		if (!(value instanceof String text)) return value;
		String normalized = text.replace('\r', '\n').trim();
		if (normalized.length() <= 512) return normalized;
		return normalized.substring(0, 512) + "...";
	}
}
