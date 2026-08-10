package dev.vy.drt.tracking;

import java.time.Instant;
import java.util.Map;

public record DiagnosticEntry(
	long sequence,
	Instant wallTime,
	DetectionEventType eventType,
	DetectionSource source,
	String handler,
	String decision,
	String reason,
	String runId,
	String chestId,
	String containerId,
	String dedupKey,
	Map<String, Object> data
) {
	public DiagnosticEntry {
		wallTime = wallTime == null ? Instant.EPOCH : wallTime;
		eventType = eventType == null ? DetectionEventType.DIAGNOSTIC : eventType;
		source = source == null ? DetectionSource.NONE : source;
		handler = handler == null ? "" : handler;
		decision = decision == null ? "" : decision;
		reason = reason == null ? "" : reason;
		runId = runId == null ? "" : runId;
		chestId = chestId == null ? "" : chestId;
		containerId = containerId == null ? "" : containerId;
		dedupKey = dedupKey == null ? "" : dedupKey;
		data = data == null ? Map.of() : Map.copyOf(data);
	}
}
