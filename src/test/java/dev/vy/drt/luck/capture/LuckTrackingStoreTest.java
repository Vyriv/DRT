package dev.vy.drt.luck.capture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.vy.drt.luck.rules.ModifierPreset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LuckTrackingStoreTest {
	@TempDir
	Path dir;

	private Path file() {
		return dir.resolve("luck").resolve("drt-luck.json");
	}

	private static LuckRunRecord completion(String fingerprint, Integer score) {
		return LuckRunRecord.forCompletion("live-a-run-1", fingerprint, 1_000L, "M5", "S+",
			score == null ? null : new DungeonScoreReading(score, "S+"));
	}

	private static LuckModifierProfile maxed() {
		return LuckModifierProfile.of(new ModifierPreset(ModifierPreset.BossLuck.IV, ModifierPreset.TreasureAccessory.ARTIFACT, 10, 10, 10, 5));
	}

	@Test
	void newStoreWritesVersionedFileOnFirstRun() throws Exception {
		LuckTrackingStore store = new LuckTrackingStore(file());
		assertEquals(LuckTrackingStore.LoadState.NEW, store.load());
		assertEquals(LuckTrackingStore.AddResult.ADDED, store.addRun(completion("fp-1", 306)));
		JsonObject root = JsonParser.parseString(Files.readString(file(), StandardCharsets.UTF_8)).getAsJsonObject();
		assertEquals(LuckTrackingStore.SCHEMA, root.get("schema").getAsString());
		assertEquals(LuckTrackingStore.SCHEMA_VERSION, root.get("schemaVersion").getAsInt());
		assertEquals(1, root.getAsJsonArray("runs").size());
	}

	@Test
	void duplicateCompletionIsIgnoredAcrossRestarts() {
		LuckTrackingStore store = new LuckTrackingStore(file());
		store.load();
		assertEquals(LuckTrackingStore.AddResult.ADDED, store.addRun(completion("fp-1", 306)));
		assertEquals(LuckTrackingStore.AddResult.DUPLICATE, store.addRun(completion("fp-1", 306)));

		LuckTrackingStore restarted = new LuckTrackingStore(file());
		assertEquals(LuckTrackingStore.LoadState.LOADED, restarted.load());
		assertEquals(LuckTrackingStore.AddResult.DUPLICATE, restarted.addRun(completion("fp-1", 306)));
		assertEquals(LuckTrackingStore.AddResult.ADDED, restarted.addRun(completion("fp-2", 301)));
		assertEquals(2, restarted.runs().size());
		assertTrue(restarted.containsCompletion("fp-2"));
	}

	@Test
	void runSnapshotsTheProfileActiveAtCompletion() {
		LuckTrackingStore store = new LuckTrackingStore(file());
		store.load();
		assertTrue(store.updateProfile(maxed(), 5_000L));
		store.addRun(completion("fp-1", 306));
		assertTrue(store.updateProfile(LuckModifierProfile.unknown(), 6_000L));
		store.addRun(completion("fp-2", 306));

		LuckTrackingStore restarted = new LuckTrackingStore(file());
		restarted.load();
		LuckRunRecord first = restarted.runs().get(0);
		LuckRunRecord second = restarted.runs().get(1);
		assertEquals(1, first.modifiers.revision);
		assertEquals("IV", first.modifiers.bossLuck);
		assertEquals(2, second.modifiers.revision);
		assertNull(second.modifiers.bossLuck);
		assertEquals(6, second.modifiers.unknownFieldCount());
	}

	@Test
	void profileRevisionOnlyMovesOnRealChanges() {
		LuckTrackingStore store = new LuckTrackingStore(file());
		store.load();
		assertTrue(store.updateProfile(maxed(), 1L));
		assertTrue(store.updateProfile(maxed(), 2L));
		assertEquals(1, store.profile().revision);
		assertEquals(1L, store.profile().updatedAtEpochMillis);
	}

	@Test
	void numericScoreAndCoverageArePersisted() {
		LuckTrackingStore store = new LuckTrackingStore(file());
		store.load();
		store.addRun(completion("fp-1", 306));
		store.addRun(completion("fp-2", null));
		LuckTrackingStore restarted = new LuckTrackingStore(file());
		restarted.load();
		LuckRunRecord withScore = restarted.runs().get(0);
		LuckRunRecord gradeOnly = restarted.runs().get(1);
		assertEquals(306, withScore.score);
		assertEquals(LuckRunRecord.SCORE_SOURCE_COMPLETION_CHAT, withScore.scoreSource);
		assertEquals("OBSERVED", withScore.coverage.scoreStatus);
		assertNull(gradeOnly.score);
		assertEquals("GRADE_ONLY", gradeOnly.coverage.scoreStatus);
		assertFalse(gradeOnly.coverage.qualifiesForScore);
	}

	@Test
	void malformedFileIsPreservedBeforeStartingFresh() throws Exception {
		Files.createDirectories(file().getParent());
		Files.writeString(file(), "{ this is not json", StandardCharsets.UTF_8);
		LuckTrackingStore store = new LuckTrackingStore(file());
		assertEquals(LuckTrackingStore.LoadState.RECOVERED_FROM_MALFORMED, store.load());
		Path preserved = store.preservedMalformedFile();
		assertNotNull(preserved);
		assertEquals("{ this is not json", Files.readString(preserved, StandardCharsets.UTF_8));
		assertEquals(LuckTrackingStore.AddResult.ADDED, store.addRun(completion("fp-1", 306)));
	}

	@Test
	void newerSchemaIsNeverOverwritten() throws Exception {
		Files.createDirectories(file().getParent());
		String future = "{\"schema\":\"drt-luck\",\"schemaVersion\":99,\"runs\":[{\"futureField\":true}]}";
		Files.writeString(file(), future, StandardCharsets.UTF_8);
		LuckTrackingStore store = new LuckTrackingStore(file());
		assertEquals(LuckTrackingStore.LoadState.NEWER_SCHEMA_READ_ONLY, store.load());
		assertTrue(store.isReadOnly());
		assertEquals(LuckTrackingStore.AddResult.READ_ONLY, store.addRun(completion("fp-1", 306)));
		assertFalse(store.updateProfile(maxed(), 1L));
		assertEquals(future, Files.readString(file(), StandardCharsets.UTF_8));
	}

	@Test
	void unversionedFileIsMigratedWithoutLosingRuns() throws Exception {
		Files.createDirectories(file().getParent());
		String legacy = "{\"runs\":[{\"luckRunId\":\"luck-old\",\"drtCompletionFingerprint\":\"fp-old\",\"floor\":\"F7\",\"grade\":\"S\",\"score\":281}]}";
		Files.writeString(file(), legacy, StandardCharsets.UTF_8);
		LuckTrackingStore store = new LuckTrackingStore(file());
		assertEquals(LuckTrackingStore.LoadState.LOADED, store.load());
		assertEquals(1, store.runs().size());
		assertEquals(281, store.runs().get(0).score);
		assertNotNull(store.runs().get(0).coverage);
		assertEquals(6, store.profile().unknownFieldCount());
		assertEquals(LuckTrackingStore.AddResult.DUPLICATE, store.addRun(completion("fp-old", 281)));
	}

	@Test
	void outOfRangeValuesBecomeUnknownInsteadOfBeingClamped() throws Exception {
		Files.createDirectories(file().getParent());
		String json = "{\"schemaVersion\":1,\"profile\":{\"bossLuck\":\"V\",\"catacombsBoxLevel\":42,\"pityLevel\":3},"
			+ "\"runs\":[{\"drtCompletionFingerprint\":\"fp\",\"floor\":\"M5\",\"grade\":\"S+\",\"score\":5000}]}";
		Files.writeString(file(), json, StandardCharsets.UTF_8);
		LuckTrackingStore store = new LuckTrackingStore(file());
		store.load();
		LuckModifierProfile profile = store.profile();
		assertNull(profile.bossLuck);
		assertNull(profile.catacombsBoxLevel);
		assertEquals(3, profile.pityLevel);
		assertNull(store.runs().get(0).score);
	}

	@Test
	void storeOnlyTouchesItsOwnFiles() throws Exception {
		Path drtConfig = dir.resolve("drt.json");
		Files.writeString(drtConfig, "{\"runHistory\":[]}", StandardCharsets.UTF_8);
		LuckTrackingStore store = new LuckTrackingStore(file());
		store.load();
		store.addRun(completion("fp-1", 306));
		store.addRun(completion("fp-2", 306));
		assertEquals("{\"runHistory\":[]}", Files.readString(drtConfig, StandardCharsets.UTF_8));
		try (var files = Files.list(dir)) {
			assertEquals(2, files.count());
		}
	}
}
