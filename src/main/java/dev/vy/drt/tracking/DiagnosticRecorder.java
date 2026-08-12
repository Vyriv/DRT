package dev.vy.drt.tracking;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public final class DiagnosticRecorder {
	public static final String REPORT_SCHEMA = "drt-tracker-report-v1";
	public static final String REPLAY_SCHEMA = "drt-replay-v1";
	private static final int DEFAULT_EVENT_LIMIT = 512;
	private static final int REPORT_CHAR_LIMIT = 64 * 1024;
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final Gson JSONL_GSON = new Gson();

	private final TrackerClock clock;
	private final int eventLimit;
	private final Supplier<Map<String, Object>> environmentSupplier;
	private final ArrayDeque<DetectionEvent> events = new ArrayDeque<>();
	private final ArrayDeque<DiagnosticEntry> entries = new ArrayDeque<>();
	private final Map<String, DiagnosticIncident> incidentsByRoot = new LinkedHashMap<>();
	private long nextSequence;

	public DiagnosticRecorder() {
		this(new SystemTrackerClock(), DEFAULT_EVENT_LIMIT, Map::of);
	}

	public DiagnosticRecorder(TrackerClock clock) {
		this(clock, DEFAULT_EVENT_LIMIT, Map::of);
	}

	public DiagnosticRecorder(TrackerClock clock, int eventLimit, Supplier<Map<String, Object>> environmentSupplier) {
		this.clock = clock == null ? new SystemTrackerClock() : clock;
		this.eventLimit = Math.max(32, eventLimit);
		this.environmentSupplier = environmentSupplier == null ? Map::of : environmentSupplier;
	}

	public synchronized DetectionEvent recordEvent(DetectionEventType type, DetectionSource source, Map<String, Object> payload) {
		DetectionEvent event = new DetectionEvent(++nextSequence, clock.wallTime(), clock.monotonicNanos(), type, source, payload);
		events.addLast(event);
		while (events.size() > eventLimit) events.removeFirst();
		return event;
	}

	public synchronized DiagnosticEntry recordDecision(
		DetectionEvent event,
		String handler,
		String decision,
		String reason,
		String runId,
		String chestId,
		String containerId,
		String dedupKey,
		Map<String, Object> data
	) {
		DiagnosticEntry entry = new DiagnosticEntry(
			event == null ? ++nextSequence : event.sequence(),
			event == null ? clock.wallTime() : event.wallTime(),
			event == null ? DetectionEventType.DIAGNOSTIC : event.type(),
			event == null ? DetectionSource.SYSTEM : event.source(),
			handler,
			decision,
			reason,
			runId,
			chestId,
			containerId,
			dedupKey,
			data
		);
		entries.addLast(entry);
		while (entries.size() > eventLimit) entries.removeFirst();
		return entry;
	}

	public synchronized DiagnosticIncident recordInvariantViolation(
		TrackerInvariant invariant,
		DiagnosticSeverity severity,
		DetectionEvent event,
		String rootKey,
		String likelyCause,
		String investigationLocation,
		String handler,
		String decision,
		String reason,
		String runId,
		String chestId,
		String containerId,
		String dedupKey,
		Map<String, Object> data
	) {
		return recordInvariantViolation(
			"TRACKING_INVARIANT",
			invariant,
			severity,
			event,
			rootKey,
			likelyCause,
			investigationLocation,
			handler,
			decision,
			reason,
			runId,
			chestId,
			containerId,
			dedupKey,
			data
		);
	}

	public synchronized DiagnosticIncident recordInvariantViolation(
		String incidentType,
		TrackerInvariant invariant,
		DiagnosticSeverity severity,
		DetectionEvent event,
		String rootKey,
		String likelyCause,
		String investigationLocation,
		String handler,
		String decision,
		String reason,
		String runId,
		String chestId,
		String containerId,
		String dedupKey,
		Map<String, Object> data
	) {
		String key = rootKey == null || rootKey.isBlank()
			? (invariant == null ? "unknown" : invariant.name())
			: rootKey;
		DiagnosticEntry entry = recordDecision(event, handler, decision, reason, runId, chestId, containerId, dedupKey, data);
		DiagnosticIncident incident = incidentsByRoot.get(key);
		if (incident == null) {
			String id = shortId(key + "|" + entry.sequence() + "|" + clock.wallTime());
			incident = new DiagnosticIncident(
				id,
				incidentType,
				severity,
				key,
				likelyCause,
				investigationLocation,
				entry.wallTime(),
				entry.sequence(),
				invariant,
				entry
			);
			incidentsByRoot.put(key, incident);
		} else {
			incident.append(invariant, entry);
		}
		return incident;
	}

	public synchronized List<DetectionEvent> events() {
		return List.copyOf(events);
	}

	public synchronized List<DiagnosticEntry> entries() {
		return List.copyOf(entries);
	}

	public synchronized List<DiagnosticIncident> incidents() {
		return List.copyOf(incidentsByRoot.values());
	}

	public synchronized DiagnosticIncident incidentById(String reportId) {
		if (reportId == null || reportId.isBlank()) return null;
		for (DiagnosticIncident incident : incidentsByRoot.values()) {
			if (incident.id().equals(reportId)) return incident;
		}
		return null;
	}

	public synchronized String buildHumanReport(DiagnosticIncident incident, Map<String, Object> state) {
		Objects.requireNonNull(incident, "incident");
		StringBuilder sb = new StringBuilder(4096);
		sb.append("=== DRT DIAGNOSTIC REPORT ===\n");
		sb.append("schemaVersion=").append(REPORT_SCHEMA).append('\n');
		sb.append("reportId=").append(incident.id()).append('\n');
		sb.append("incidentType=").append(incident.incidentType()).append('\n');
		sb.append("severity=").append(incident.severity()).append('\n');
		sb.append("createdAt=").append(incident.createdAt()).append('\n');
		sb.append("firstInconsistentEvent=#").append(incident.firstSequence()).append('\n');
		sb.append("confidence=high\n");
		sb.append("likelyCause=").append(incident.likelyCause()).append('\n');
		sb.append("suggestedInvestigation=").append(incident.investigationLocation()).append('\n');
		sb.append("violatedInvariants=").append(incident.invariants()).append('\n');
		if (incident.replayPath() != null && !incident.replayPath().isBlank()) {
			sb.append("replayPath=").append(incident.replayPath()).append('\n');
		}
		sb.append('\n');
		sb.append("--- environment ---\n");
		appendJsonish(sb, safeMap(environmentSupplier.get()));
		sb.append('\n');
		sb.append("--- tracker state ---\n");
		appendJsonish(sb, safeMap(state));
		sb.append('\n');
		sb.append("--- incident entries ---\n");
		for (DiagnosticEntry entry : incident.entries()) {
			sb.append('#').append(entry.sequence())
				.append(' ').append(entry.wallTime())
				.append(" type=").append(entry.eventType())
				.append(" source=").append(entry.source())
				.append(" handler=").append(entry.handler())
				.append(" decision=").append(entry.decision())
				.append(" reason=").append(entry.reason())
				.append(" run=").append(entry.runId())
				.append(" chest=").append(entry.chestId())
				.append(" container=").append(entry.containerId())
				.append(" dedup=").append(entry.dedupKey())
				.append(" data=").append(entry.data())
				.append('\n');
			if (sb.length() > REPORT_CHAR_LIMIT) {
				sb.setLength(REPORT_CHAR_LIMIT);
				sb.append("\n--- truncated ---\n");
				break;
			}
		}
		return sb.toString();
	}

	public synchronized Path saveReplayBundle(Path parentDir, DiagnosticIncident incident, Map<String, Object> expected) throws IOException {
		return saveReplayBundle(parentDir, incident, expected, expected);
	}

	public synchronized Path saveReplayBundle(Path parentDir, DiagnosticIncident incident, Map<String, Object> expected, Map<String, Object> reportState) throws IOException {
		Objects.requireNonNull(parentDir, "parentDir");
		Objects.requireNonNull(incident, "incident");
		Files.createDirectories(parentDir);
		Path zipPath = parentDir.resolve("drt-report-" + incident.id() + ".zip");

		String previousReplayPath = incident.replayPath();
		incident.setReplayPath(zipPath.toAbsolutePath().toString());
		try {
			Map<String, Object> manifest = new LinkedHashMap<>();
			manifest.put("schema", REPLAY_SCHEMA);
			manifest.put("reportId", incident.id());
			manifest.put("incidentType", incident.incidentType());
			manifest.put("severity", incident.severity().name());
			manifest.put("rootKey", incident.rootKey());
			manifest.put("createdAt", Instant.now().toString());
			manifest.put("redactionVersion", 1);
			manifest.put("environment", safeMap(environmentSupplier.get()));
			manifest.put("firstInconsistentEvent", incident.firstSequence());
			manifest.put("likelyCause", incident.likelyCause());
			manifest.put("suggestedInvestigation", incident.investigationLocation());
			manifest.put("violatedInvariants", incident.invariants().stream().map(Enum::name).toList());
			manifest.put("incidentEntries", incident.entries().stream().map(this::entryMap).toList());

			StringBuilder jsonl = new StringBuilder();
			for (DetectionEvent event : events) {
				Map<String, Object> row = new LinkedHashMap<>();
				row.put("sequence", event.sequence());
				row.put("wallTime", event.wallTime().toString());
				row.put("monotonicNanos", event.monotonicNanos());
				row.put("type", event.type().name());
				row.put("source", event.source().name());
				row.put("payload", event.safePayload());
				jsonl.append(JSONL_GSON.toJson(row)).append('\n');
			}

			try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(
				zipPath,
				StandardOpenOption.CREATE,
				StandardOpenOption.TRUNCATE_EXISTING,
				StandardOpenOption.WRITE
			))) {
				writeZipEntry(zip, "manifest.json", GSON.toJson(manifest));
				writeZipEntry(zip, "events.jsonl", jsonl.toString());
				writeZipEntry(zip, "expected.json", GSON.toJson(expected == null ? Map.of() : safeMap(expected)));
				writeZipEntry(zip, "report.txt", buildHumanReport(incident, reportState));
			}
			return zipPath;
		} catch (IOException e) {
			incident.setReplayPath(previousReplayPath);
			Files.deleteIfExists(zipPath);
			throw e;
		}
	}

	private Map<String, Object> entryMap(DiagnosticEntry entry) {
		if (entry == null) return Map.of();
		Map<String, Object> row = new LinkedHashMap<>();
		row.put("sequence", entry.sequence());
		row.put("wallTime", entry.wallTime().toString());
		row.put("eventType", entry.eventType().name());
		row.put("source", entry.source().name());
		row.put("handler", entry.handler());
		row.put("decision", entry.decision());
		row.put("reason", entry.reason());
		row.put("runId", entry.runId());
		row.put("chestId", entry.chestId());
		row.put("containerId", entry.containerId());
		row.put("dedupKey", entry.dedupKey());
		row.put("data", safeMap(entry.data()));
		return row;
	}

	private static void writeZipEntry(ZipOutputStream zip, String name, String content) throws IOException {
		zip.putNextEntry(new ZipEntry(name));
		zip.write((content == null ? "" : content).getBytes(StandardCharsets.UTF_8));
		zip.closeEntry();
	}

	private static Map<String, Object> safeMap(Map<String, Object> input) {
		if (input == null || input.isEmpty()) return Map.of();
		Map<String, Object> safe = new LinkedHashMap<>();
		for (Map.Entry<String, Object> entry : input.entrySet()) {
			if (entry.getKey() == null || entry.getValue() == null) continue;
			String key = entry.getKey();
			if (key.equalsIgnoreCase("token") || key.equalsIgnoreCase("session") || key.equalsIgnoreCase("auth")) continue;
			Object value = entry.getValue();
			if (value instanceof String text) {
				String clean = text.replace('\r', '\n').trim();
				safe.put(key, clean.length() > 512 ? clean.substring(0, 512) + "..." : clean);
			} else {
				safe.put(key, value);
			}
		}
		return Map.copyOf(safe);
	}

	private static void appendJsonish(StringBuilder sb, Map<String, Object> values) {
		if (values == null || values.isEmpty()) {
			sb.append("(none)\n");
			return;
		}
		for (Map.Entry<String, Object> entry : values.entrySet()) {
			sb.append(entry.getKey()).append('=').append(entry.getValue()).append('\n');
		}
	}

	private static String shortId(String seed) {
		return UUID.nameUUIDFromBytes(seed.getBytes(StandardCharsets.UTF_8)).toString().substring(0, 8);
	}
}
