package dev.vy.drt.luck.capture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CaptureHelpersTest {
	@TempDir
	Path dir;

	@Test
	void partyRosterNeedsThePlausibleTabFormat() {
		List<String> tab = List.of(
			"Party (3)",
			"[302] LocalPlayer \u2672 (Mage XLI)",
			"[285] [MVP+] Teammate_A (Tank XXXVII)",
			"[190] Teammate_B (DEAD)",
			"Dungeon: Catacombs"
		);
		assertEquals(List.of("LocalPlayer", "Teammate_A", "Teammate_B"), DungeonPartyRoster.fromTabLines(tab, "localplayer"));
		assertNull(DungeonPartyRoster.fromTabLines(tab, "SomeoneElse"), "a roster without the local player is not trusted");
		assertNull(DungeonPartyRoster.fromTabLines(List.of("Area: Dungeon Hub"), "LocalPlayer"));
	}

	@Test
	void gateWaitsForTenStableTicksAndAgainAfterAChange() {
		StableScreenGate gate = new StableScreenGate();
		Object screen = new Object();
		LuckProbeRecorder.ScreenSample first = sample("A");
		for (int i = 1; i < LuckProbeRecorder.STABLE_TICKS; i++) assertNull(gate.observe(screen, first));
		assertNotNull(gate.observe(screen, first));
		assertNull(gate.observe(screen, first));
		LuckProbeRecorder.ScreenSample second = sample("B");
		for (int i = 1; i < LuckProbeRecorder.STABLE_TICKS; i++) assertNull(gate.observe(screen, second));
		assertNotNull(gate.observe(screen, second));
		Object reopened = new Object();
		for (int i = 1; i < LuckProbeRecorder.STABLE_TICKS; i++) assertNull(gate.observe(reopened, second));
		assertNotNull(gate.observe(reopened, second), "a reopened screen is handled again");
	}

	@Test
	void versionOneFileLoadsIntoVersionTwo() throws Exception {
		Path file = dir.resolve("drt-luck.json");
		String v1 = "{\"schema\":\"drt-luck\",\"schemaVersion\":1,\"profile\":{\"pityLevel\":10,\"revision\":3},"
			+ "\"runs\":[{\"luckRunId\":\"luck-1\",\"drtCompletionFingerprint\":\"fp\",\"completedAtEpochMillis\":5,\"floor\":\"F5\",\"grade\":\"S+\","
			+ "\"score\":302,\"chests\":[],\"meterSnapshots\":[]}]}";
		Files.writeString(file, v1, StandardCharsets.UTF_8);
		LuckTrackingStore store = new LuckTrackingStore(file);
		assertEquals(LuckTrackingStore.LoadState.LOADED, store.load());
		LuckRunRecord run = store.run("luck-1");
		assertEquals(302, run.score);
		assertNull(run.party);
		assertNull(run.croesus);
		assertEquals(10, store.profile().pityLevel);
		assertEquals(3, store.profile().revision);
		assertTrue(store.meterReadings().isEmpty());
		assertTrue(store.unmatchedCroesus().isEmpty());
		assertEquals(LuckCoverage.VERSION, run.coverage.version);
	}

	private static LuckProbeRecorder.ScreenSample sample(String name) {
		return new LuckProbeRecorder.ScreenSample("Croesus", 54,
			List.of(new LuckProbeRecorder.SlotSample(10, "minecraft:player_head", 1, name, List.of())));
	}
}
