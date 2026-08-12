package dev.vy.drt.tracking;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonParser;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipFile;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DiagnosticRecorderTest {
	@TempDir
	Path tempDir;

	@Test
	void syntheticIncidentReportContainsDeveloperFields() {
		FakeTrackerClock clock = new FakeTrackerClock(Instant.parse("2024-01-01T00:00:00Z"));
		DiagnosticRecorder recorder = recorder(clock);

		DiagnosticIncident incident = SyntheticDiagnosticIncidentFactory.record(recorder, SyntheticDiagnosticIncidentFactory.DEFAULT_ROOT_KEY, 0);
		String report = recorder.buildHumanReport(incident, SyntheticDiagnosticIncidentFactory.expected(incident));

		assertTrue(report.contains("DRT DIAGNOSTIC REPORT"));
		assertTrue(report.contains("schemaVersion=" + DiagnosticRecorder.REPORT_SCHEMA));
		assertTrue(report.contains("reportId=" + incident.id()));
		assertTrue(report.contains("incidentType=SYNTHETIC_TEST"));
		assertTrue(report.contains("severity=ERROR"));
		assertTrue(report.contains("firstInconsistentEvent=#"));
		assertTrue(report.contains("runSessionId=synthetic-run-m7-active"));
		assertTrue(report.contains("runState=ACTIVE"));
		assertTrue(report.contains("runMode=CATACOMBS"));
		assertTrue(report.contains("runFloor=M7"));
		assertTrue(report.contains("chestSessionId=synthetic-chest-bedrock-conflicted"));
		assertTrue(report.contains("chestState=CONFLICTED"));
		assertTrue(report.contains("chestType=BEDROCK"));
		assertTrue(report.contains("trustedEvidence=M7 / SCOREBOARD"));
		assertTrue(report.contains("attemptedEvidence=K5 / PLAYER_INVENTORY"));
		assertTrue(report.contains("PLAYER_INVENTORY_CANNOT_SET_RUN_FLOOR"));
		assertTrue(report.contains("suggestedInvestigation="));
		assertNoUnrelatedPrivateData(report);
	}

	@Test
	void replayBundleSerializesSyntheticIncidentAndCanBeParsed() throws Exception {
		FakeTrackerClock clock = new FakeTrackerClock(Instant.parse("2024-01-01T00:00:00Z"));
		DiagnosticRecorder recorder = recorder(clock);
		DiagnosticIncident incident = SyntheticDiagnosticIncidentFactory.record(recorder, SyntheticDiagnosticIncidentFactory.DEFAULT_ROOT_KEY, 0);

		Path replayZip = recorder.saveReplayBundle(tempDir.resolve("drt").resolve("replays"), incident, SyntheticDiagnosticIncidentFactory.expected(incident));

		assertTrue(Files.isRegularFile(replayZip));
		assertTrue(replayZip.getFileName().toString().contains(incident.id()));
		assertTrue(replayZip.getFileName().toString().endsWith(".zip"));
		String manifest;
		String events;
		String expected;
		String report;
		try (ZipFile zip = new ZipFile(replayZip.toFile())) {
			manifest = readZipEntry(zip, "manifest.json");
			events = readZipEntry(zip, "events.jsonl");
			expected = readZipEntry(zip, "expected.json");
			report = readZipEntry(zip, "report.txt");
		}
		JsonParser.parseString(manifest).getAsJsonObject();
		for (String line : events.split("\\R")) {
			if (!line.isBlank()) JsonParser.parseString(line).getAsJsonObject();
		}
		JsonParser.parseString(expected).getAsJsonObject();

		assertTrue(manifest.contains("\"incidentType\": \"SYNTHETIC_TEST\""));
		assertTrue(manifest.contains("\"reportId\": \"" + incident.id() + "\""));
		assertTrue(manifest.contains("PLAYER_INVENTORY_CANNOT_SET_RUN_FLOOR"));
		assertTrue(events.contains("SYNTHETIC_TEST"));
		assertTrue(events.contains("PLAYER_INVENTORY"));
		assertTrue(expected.contains("SYNTHETIC_TEST"));
		assertTrue(report.contains("DRT DIAGNOSTIC REPORT"));
		assertTrue(report.contains("reportId=" + incident.id()));
		assertTrue(report.contains("replayPath=" + replayZip.toAbsolutePath()));
		assertNoUnrelatedPrivateData(manifest + events + expected + report);
	}

	@Test
	void duplicateSyntheticIncidentCoalescesIntoOneIncident() {
		DiagnosticRecorder recorder = recorder(new FakeTrackerClock(Instant.EPOCH));

		DiagnosticIncident first = SyntheticDiagnosticIncidentFactory.record(recorder, SyntheticDiagnosticIncidentFactory.DEFAULT_ROOT_KEY, 0);
		DiagnosticIncident second = SyntheticDiagnosticIncidentFactory.record(recorder, SyntheticDiagnosticIncidentFactory.DEFAULT_ROOT_KEY, 1);
		DiagnosticIncident third = SyntheticDiagnosticIncidentFactory.record(recorder, SyntheticDiagnosticIncidentFactory.DEFAULT_ROOT_KEY, 2);

		assertEquals(first.id(), second.id());
		assertEquals(first.id(), third.id());
		assertEquals(1, recorder.incidents().size());
		assertEquals(2, first.updateCount());
		assertTrue(first.entries().size() >= 3);
	}

	@Test
	void separateSyntheticIncidentsReceiveDifferentIds() {
		DiagnosticRecorder recorder = recorder(new FakeTrackerClock(Instant.EPOCH));

		DiagnosticIncident first = SyntheticDiagnosticIncidentFactory.record(recorder, SyntheticDiagnosticIncidentFactory.DEFAULT_ROOT_KEY + "|new-a", 0);
		DiagnosticIncident second = SyntheticDiagnosticIncidentFactory.record(recorder, SyntheticDiagnosticIncidentFactory.DEFAULT_ROOT_KEY + "|new-b", 0);

		assertNotEquals(first.id(), second.id());
		assertEquals(2, recorder.incidents().size());
		assertNotNull(recorder.incidentById(first.id()));
		assertNotNull(recorder.incidentById(second.id()));
	}

	@Test
	void invalidReplayDestinationFailsWithoutDeletingIncident() throws Exception {
		DiagnosticRecorder recorder = recorder(new FakeTrackerClock(Instant.EPOCH));
		DiagnosticIncident incident = SyntheticDiagnosticIncidentFactory.record(recorder, SyntheticDiagnosticIncidentFactory.DEFAULT_ROOT_KEY, 0);
		Path fileInsteadOfDirectory = tempDir.resolve("not-a-directory");
		Files.writeString(fileInsteadOfDirectory, "occupied", StandardCharsets.UTF_8);

		assertThrows(Exception.class, () -> recorder.saveReplayBundle(fileInsteadOfDirectory, incident, SyntheticDiagnosticIncidentFactory.expected(incident)));
		assertEquals(1, recorder.incidents().size());
		assertEquals("", incident.replayPath());
	}

	private static DiagnosticRecorder recorder(FakeTrackerClock clock) {
		return new DiagnosticRecorder(clock, 512, () -> Map.of(
			"modVersion", "test",
			"minecraft", "test-minecraft",
			"configFingerprint", "synthetic-test-config"
		));
	}

	private static void assertNoUnrelatedPrivateData(String text) {
		String lower = text.toLowerCase();
		for (String forbidden : List.of("party chat", "guild chat", "private message", "sessiontoken", "auth token")) {
			assertTrue(!lower.contains(forbidden), "unexpected private data marker: " + forbidden);
		}
	}

	private static String readZipEntry(ZipFile zip, String name) throws Exception {
		var entry = zip.getEntry(name);
		assertNotNull(entry);
		try (var stream = zip.getInputStream(entry)) {
			return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
		}
	}
}
