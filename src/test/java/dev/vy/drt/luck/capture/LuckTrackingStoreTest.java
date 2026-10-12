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
import java.util.List;
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
	void revisionMovesOnEverySavedChangeAndSnapshotsAreDetached() {
		LuckTrackingStore store = new LuckTrackingStore(file());
		store.load();
		long loaded = store.revision();
		assertEquals(LuckTrackingStore.AddResult.ADDED, store.addRun(completion("fp-1", 306)));
		long added = store.revision();
		assertTrue(added > loaded);
		assertEquals(LuckTrackingStore.AddResult.DUPLICATE, store.addRun(completion("fp-1", 306)));
		assertEquals(added, store.revision(), "nothing saved, nothing moved");
		assertTrue(store.updateProfile(maxed(), 5_000L));
		assertTrue(store.revision() > added);

		List<LuckRunRecord> snapshot = store.runSnapshots();
		assertEquals(1, snapshot.size());
		snapshot.get(0).floor = "F1";
		snapshot.get(0).chests.clear();
		assertEquals("M5", store.runs().get(0).floor, "a snapshot never writes back");
		assertEquals("M5", store.runSnapshots().get(0).floor);
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

	private static LuckMeterReading reading(String floor, long at, long stored) {
		LuckMeterReading reading = new LuckMeterReading();
		reading.floor = floor;
		reading.observedAtEpochMillis = at;
		reading.source = LuckMeterReading.SOURCE_OVERVIEW;
		reading.storedScore = stored;
		reading.storedText = Long.toString(stored);
		return reading;
	}

	private static LuckRunRecord f5Run(String fingerprint, long completedAt) {
		return LuckRunRecord.forCompletion("session", fingerprint, completedAt, "F5", "S", new DungeonScoreReading(298, "S"));
	}

	private static String phases(LuckRunRecord run) {
		return run.meterSnapshots.stream().map(s -> s.phase + "=" + s.storedScore).toList().toString();
	}

	@Test
	void readingsOnlyAttachToTheRunsTheyBracket() {
		LuckTrackingStore store = new LuckTrackingStore(file());
		store.load();
		store.recordMeterReading(reading("F5", 100L, 1_000L));
		store.addRun(f5Run("r1", 200L));
		LuckRunRecord other = LuckRunRecord.forCompletion("session", "f6", 250L, "F6", "S", new DungeonScoreReading(298, "S"));
		store.addRun(other);
		store.addRun(f5Run("r2", 300L));
		List<LuckTrackingStore.MeterAttachment> attached = store.recordMeterReading(reading("F5", 400L, 1_500L));
		assertEquals(1, attached.size());
		assertNull(attached.get(0).before(), "a reading from before the first run is not the second run's start");
		assertEquals(1_500L, attached.get(0).after().storedScore);

		LuckTrackingStore restarted = new LuckTrackingStore(file());
		restarted.load();
		LuckRunRecord r1 = restarted.runs().get(0);
		LuckRunRecord f6 = restarted.runs().get(1);
		LuckRunRecord r2 = restarted.runs().get(2);
		assertEquals("[BEFORE_RUN=1000]", phases(r1), "the later reading spans two runs, so it is no one's exact after-state but the last");
		assertEquals("[]", phases(f6));
		assertEquals("[AFTER_RUN=1500]", phases(r2));
		assertNull(LuckMeterGains.of(r1));
		assertNull(LuckMeterGains.of(r2));
		assertEquals("OBSERVED", r1.coverage.meterStatus);
		assertEquals("UNKNOWN", r2.coverage.meterStatus);

		store.recordMeterReading(reading("F5", 500L, 1_500L));
		assertEquals(2, store.meterReadings().size(), "an unchanged reading with no run in between only moves the last-seen time");
		store.addRun(f5Run("r3", 600L));
		assertEquals("[BEFORE_RUN=1500]", phases(store.run(store.runs().get(3).luckRunId)), "the merged reading still counts as seen before r3");
	}

	@Test
	void olderSchemaKeepsObservationsAndReassessesCoverage() throws Exception {
		Files.createDirectories(file().getParent());
		String v1 = "{\"schema\":\"drt-luck\",\"schemaVersion\":1,"
			+ "\"profile\":{\"bossLuck\":\"IV\",\"treasureAccessory\":\"ARTIFACT\",\"catacombsBoxLevel\":10,\"echoOfBoxesLevel\":10,\"echoOfEchoesLevel\":10,\"pityLevel\":10,\"revision\":3},"
			+ "\"runs\":[{\"luckRunId\":\"luck-v1\",\"drtCompletionFingerprint\":\"fp-v1\",\"completedAtEpochMillis\":5000,\"floor\":\"F5\",\"grade\":\"S\",\"score\":298,"
			+ "\"modifiers\":{\"bossLuck\":\"III\",\"pityLevel\":4,\"revision\":2},"
			+ "\"chests\":[{\"tier\":\"WOOD\",\"source\":\"CROESUS_RUN_VIEW\",\"offers\":[{\"index\":0,\"contents\":[{\"text\":\"Mystery\",\"kind\":\"ITEM\"}]}]}],"
			+ "\"meterSnapshots\":[{\"floor\":\"F5\",\"phase\":\"BEFORE_RUN\",\"state\":\"OBSERVED\",\"storedScore\":3556}],"
			+ "\"coverage\":{\"version\":1,\"qualifiesForScore\":true,\"blockers\":[]}}]}";
		Files.writeString(file(), v1, StandardCharsets.UTF_8);
		LuckTrackingStore store = new LuckTrackingStore(file());
		assertEquals(LuckTrackingStore.LoadState.LOADED, store.load());
		assertEquals(3, store.profile().revision);

		LuckRunRecord run = store.run("luck-v1");
		assertEquals("III", run.modifiers.bossLuck, "the run keeps its own snapshot, not the current profile");
		assertEquals(4, run.modifiers.pityLevel);
		assertEquals(4, run.modifiers.unknownFieldCount());
		assertEquals("Mystery", run.chests.get(0).offers.get(0).contents.get(0).text);
		assertEquals(3_556L, run.meterSnapshots.get(0).storedScore);
		assertEquals(LuckCoverage.VERSION, run.coverage.version);
		assertFalse(run.coverage.qualifiesForScore, "a stale stored verdict is never trusted");
		assertTrue(run.coverage.blockers.containsAll(List.of("OFFERS_PARTIAL", "OFFER_CONTENT_UNRESOLVED")), run.coverage.blockers.toString());

		store.addRun(completion("fp-new", 306));
		LuckTrackingStore restarted = new LuckTrackingStore(file());
		restarted.load();
		assertEquals(LuckTrackingStore.SCHEMA_VERSION, JsonParser.parseString(Files.readString(file(), StandardCharsets.UTF_8)).getAsJsonObject().get("schemaVersion").getAsInt());
		assertEquals("III", restarted.run("luck-v1").modifiers.bossLuck);
		assertEquals("Mystery", restarted.run("luck-v1").chests.get(0).offers.get(0).contents.get(0).text);
	}

	private static CroesusRunViewParser.ChestOffer offer(dev.vy.drt.luck.ChestTier tier, String... keys) {
		List<LuckText.ContentLine> lines = new java.util.ArrayList<>();
		for (String key : keys) lines.add(new LuckText.ContentLine(key, key, 1, dev.vy.drt.luck.data.LootEntry.Kind.ITEM, key));
		return new CroesusRunViewParser.ChestOffer(tier, -1, lines, 2_000_000L, "2,000,000 Coins", List.of(keys));
	}

	@Test
	void dungeonChestOffersAreStoredUnderTheCompletedRun() {
		LuckTrackingStore store = new LuckTrackingStore(file());
		store.load();
		store.addRun(completion("fp-1", 306));
		assertEquals(LuckTrackingStore.OfferResult.ADDED,
			store.recordDungeonChestOffer("live-a-run-1", offer(dev.vy.drt.luck.ChestTier.BEDROCK, "A", "B"), 2_000L));
		assertEquals(LuckTrackingStore.OfferResult.UNCHANGED,
			store.recordDungeonChestOffer("live-a-run-1", offer(dev.vy.drt.luck.ChestTier.BEDROCK, "A", "B"), 3_000L));
		assertEquals(LuckTrackingStore.OfferResult.RUN_NOT_FOUND,
			store.recordDungeonChestOffer("some-other-run", offer(dev.vy.drt.luck.ChestTier.BEDROCK, "A"), 3_000L));

		LuckRunRecord run = store.runForSession("live-a-run-1");
		assertEquals(1, run.chests.size());
		LuckRunRecord.LuckChestRecord chest = run.chests.get(0);
		assertEquals("BEDROCK", chest.tier);
		assertEquals(LuckRunRecord.SOURCE_DUNGEON_CHEST, chest.source);
		assertEquals(1, chest.offers.size());
		assertNull(chest.purchased, "seeing a chest is not buying it");
		assertNull(store.runForSession("some-other-run"));
	}

	@Test
	void changedDungeonContentsAreFlaggedNotReplaced() {
		LuckTrackingStore store = new LuckTrackingStore(file());
		store.load();
		store.addRun(completion("fp-1", 306));
		store.recordDungeonChestOffer("live-a-run-1", offer(dev.vy.drt.luck.ChestTier.BEDROCK, "A", "B"), 2_000L);
		assertEquals(LuckTrackingStore.OfferResult.CHANGED_UNVERIFIED,
			store.recordDungeonChestOffer("live-a-run-1", offer(dev.vy.drt.luck.ChestTier.BEDROCK, "A", "C"), 3_000L));
		assertEquals(2, store.runForSession("live-a-run-1").chests.get(0).offers.size());
	}

	@Test
	void croesusViewOfTheSameDungeonChestIsNotStoredTwice() {
		LuckTrackingStore store = new LuckTrackingStore(file());
		store.load();
		store.addRun(completion("fp-1", 306));
		store.recordDungeonChestOffer("live-a-run-1", offer(dev.vy.drt.luck.ChestTier.BEDROCK, "A", "B"), 2_000L);
		String luckRunId = store.runForSession("live-a-run-1").luckRunId;
		CroesusRunViewParser.RunView view = new CroesusRunViewParser.RunView("Catacombs - Floor V", 5, false,
			List.of(offer(dev.vy.drt.luck.ChestTier.BEDROCK, "B", "A")), List.of());
		assertEquals(LuckTrackingStore.OfferResult.UNCHANGED, store.recordCroesusOffers(luckRunId, view, 5_000L));
		assertEquals(1, store.runForSession("live-a-run-1").chests.get(0).offers.size());
	}

	@Test
	void dungeonPurchaseMarksTheChestAndSurvivesARestart() {
		LuckTrackingStore store = new LuckTrackingStore(file());
		store.load();
		store.addRun(completion("fp-1", 306));
		store.recordDungeonChestOffer("live-a-run-1", offer(dev.vy.drt.luck.ChestTier.OBSIDIAN, "A"), 2_000L);
		assertTrue(store.recordDungeonPurchase("live-a-run-1", dev.vy.drt.luck.ChestTier.OBSIDIAN));
		assertFalse(store.recordDungeonPurchase("live-a-run-1", dev.vy.drt.luck.ChestTier.OBSIDIAN), "already recorded");

		LuckTrackingStore restarted = new LuckTrackingStore(file());
		restarted.load();
		LuckRunRecord.LuckChestRecord chest = restarted.runForSession("live-a-run-1").chests.get(0);
		assertEquals(Boolean.TRUE, chest.purchased);
		assertEquals(LuckRunRecord.SOURCE_DUNGEON_CHEST, chest.purchaseSource);
		assertEquals(Integer.valueOf(0), chest.purchasedOfferIndex);
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
