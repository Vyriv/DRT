package dev.vy.drt.luck.capture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.vy.drt.luck.ChestTier;
import dev.vy.drt.luck.FloorId;
import dev.vy.drt.luck.LuckTestData;
import dev.vy.drt.luck.data.FloorTable;
import dev.vy.drt.luck.meter.MeterGainObservation;
import dev.vy.drt.luck.meter.SRunMeterGainEvidence;
import dev.vy.drt.luck.rules.RuleVariants;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The first complete live capture (F5 S 298, Minecraft 26.2, 2026-10-11), kept exactly as the store
 * wrote it, and replayed through the coordinator from the same live lore and meter readings.
 */
class LiveCaptureF5Test {
	private static final String FIXTURE = "live-capture-f5-s-2026-10-11.json";
	private static final String S_RUN = "luck-26478f2d-e379-4adb-8f58-96b04733526c";
	private static final String S_PLUS_RUN = "luck-5ff1bf45-ca65-4dd6-adc1-576f5fefea20";
	private static final List<String> S_CHESTS = List.of("WOOD", "GOLD", "DIAMOND", "EMERALD", "OBSIDIAN");
	private static final Gson GSON = new Gson();

	@TempDir
	Path dir;

	private static JsonObject fixture() {
		return LuckTestData.fixture(FIXTURE);
	}

	private static LuckRunRecord liveRun(String id) {
		for (JsonElement element : fixture().getAsJsonArray("runs")) {
			LuckRunRecord run = GSON.fromJson(element, LuckRunRecord.class);
			if (run.luckRunId.equals(id)) return run;
		}
		throw new AssertionError("fixture lacks " + id);
	}

	private static List<LuckMeterReading> liveReadings() {
		List<LuckMeterReading> out = new ArrayList<>();
		for (JsonElement element : fixture().getAsJsonArray("meterReadings")) out.add(GSON.fromJson(element, LuckMeterReading.class));
		return out;
	}

	private static FloorTable f5() {
		return LuckTestData.defaults().floor(FloorId.F5);
	}

	@Test
	void storedLiveRunLoadsWithEveryObservation() throws Exception {
		JsonObject root = new JsonObject();
		root.addProperty("schema", LuckTrackingStore.SCHEMA);
		root.add("schemaVersion", fixture().get("schemaVersion"));
		root.add("profile", fixture().get("profile"));
		root.add("runs", fixture().get("runs"));
		root.add("meterReadings", fixture().get("meterReadings"));
		Path file = dir.resolve("drt-luck.json");
		Files.writeString(file, root.toString(), StandardCharsets.UTF_8);
		LuckTrackingStore store = new LuckTrackingStore(file);
		assertEquals(LuckTrackingStore.LoadState.LOADED, store.load());

		LuckRunRecord run = store.run(S_RUN);
		assertEquals("F5", run.floor);
		assertEquals("S", run.grade);
		assertEquals(298, run.score);
		assertEquals(10, run.modifiers.pityLevel);
		assertEquals(List.of("LocalPlayer", "Teammate_I", "Teammate_J", "Teammate_K", "Teammate_L"), run.party);
		assertEquals(LuckRunRecord.PARTY_SOURCE_TAB_LIST, run.partySource);

		assertEquals(CroesusRunMatcher.METHOD_UNIQUE, run.croesus.method);
		assertEquals("11 secs ago", run.croesus.completedText);
		assertTrue(CroesusRunMatcher.sameRoster(run.party, run.croesus.players), "tab roster and Croesus roster agree");
		assertTrue(run.croesus.noChestsOpened);
		assertEquals(List.of("Kismet Feather", "Dungeon Chest Key"), run.croesus.availableModifiers);

		assertEquals(S_CHESTS, run.chests.stream().map(c -> c.tier).toList());
		Map<String, Long> costs = run.chests.stream().collect(Collectors.toMap(c -> c.tier, c -> c.costCoins));
		assertEquals(Map.of("WOOD", 0L, "GOLD", 80_000L, "DIAMOND", 200_000L, "EMERALD", 800_000L, "OBSIDIAN", 800_000L), costs);
		Map<String, List<String>> keys = run.chests.stream().collect(Collectors.toMap(c -> c.tier, c -> c.offers.get(0).itemKeys));
		assertEquals(List.of("ENCHANTMENT_FEATHER_FALLING_6", "ENCHANTMENT_INFINITE_QUIVER_6", "ESSENCE_UNDEAD_19", "ESSENCE_WITHER_10"), keys.get("WOOD"));
		assertEquals(List.of("ENCHANTMENT_ULTIMATE_WISDOM_1", "ENCHANTMENT_ULTIMATE_WISE_1", "ESSENCE_UNDEAD_22", "ESSENCE_WITHER_14"), keys.get("GOLD"));
		assertEquals(List.of("ENCHANTMENT_ULTIMATE_LEGION_1", "HOT_POTATO_BOOK", "ESSENCE_UNDEAD_42", "ESSENCE_WITHER_29"), keys.get("OBSIDIAN"));
		for (LuckRunRecord.LuckChestRecord chest : run.chests) {
			assertEquals(1, chest.offers.size(), chest.tier);
			assertEquals(Boolean.FALSE, chest.purchased, chest.tier);
			LuckRunRecord.LuckOfferSnapshot offer = chest.offers.get(0);
			assertEquals(LuckRunRecord.SOURCE_CROESUS_RUN_VIEW, offer.source);
			assertEquals(false, offer.changedFromPrevious);
			assertEquals(false, offer.afterKismetReroll);
			for (LuckRunRecord.LuckContentLine line : offer.contents) {
				assertNotNull(line.key, chest.tier + " " + line.text);
				// Essence totals add up repeated picks and filler, so only named items map to one table entry.
				if (line.kind.equals("ESSENCE")) continue;
				assertNotNull(f5().chest(ChestTier.valueOf(chest.tier)).entry(line.key), chest.tier + " cannot roll " + line.key);
			}
		}

		assertEquals(2, run.meterSnapshots.size());
		assertSnapshot(run.meterSnapshots.get(0), LuckRunRecord.PHASE_BEFORE_RUN, 3_556L, "3,556");
		assertSnapshot(run.meterSnapshots.get(1), LuckRunRecord.PHASE_AFTER_RUN, 3_785L, "3,785");

		assertEquals("COMPLETE", run.coverage.offerStatus);
		assertEquals("OBSERVED", run.coverage.meterStatus);
		assertEquals("NO_REROLL", run.coverage.kismetStatus);
		assertEquals(S_CHESTS, run.coverage.expectedChests);
		assertEquals(S_CHESTS, run.coverage.observedChests);
		assertEquals(List.of(), run.coverage.blockers);
		assertEquals(List.of("KISMET_PROVISIONAL"), run.coverage.caveats);
		assertTrue(run.coverage.qualifiesForScore);

		MeterGainObservation gain = LuckMeterGains.of(run);
		assertNotNull(gain);
		assertTrue(gain.sameReadings(SRunMeterGainEvidence.RECORDED.get(1)));
		assertNull(LuckMeterGains.of(store.run(S_PLUS_RUN)), "the earlier build never read the S+ run's before-run meter");
	}

	@Test
	void replayingTheLiveScreensReproducesTheStoredRun() {
		LuckTrackingStore store = new LuckTrackingStore(dir.resolve("drt-luck.json"));
		store.load();
		LuckCaptureCoordinator coordinator = new LuckCaptureCoordinator(store, floor -> LuckTestData.defaults().floor(floor));
		LuckRunRecord liveS = liveRun(S_RUN);
		LuckRunRecord liveSPlus = liveRun(S_PLUS_RUN);
		List<LuckMeterReading> readings = liveReadings();
		long menuAt = fixture().get("croesusMenuObservedAtEpochMillis").getAsLong();
		long viewAt = fixture().get("runViewObservedAtEpochMillis").getAsLong();

		long overviewAt = Instant.parse(LuckTestData.fixture(ProbeFixtures.RNG_OVERVIEW).get("time").getAsString()).toEpochMilli();
		coordinator.onMeterOverview(ProbeFixtures.screen(ProbeFixtures.RNG_OVERVIEW), overviewAt);
		assertEquals(LuckTrackingStore.AddResult.ADDED, store.addRun(completion(liveSPlus)));
		List<LuckTrackingStore.MeterAttachment> afterSPlus = store.recordMeterReading(readings.get(0));
		assertEquals(List.of(S_PLUS_RUN), afterSPlus.stream().map(LuckTrackingStore.MeterAttachment::luckRunId).toList());

		assertEquals(LuckTrackingStore.AddResult.ADDED, store.addRun(completion(liveS)));
		CroesusRunMatcher.Result matches = coordinator.onCroesusMenu(croesusPage(liveS, liveSPlus), menuAt);
		assertEquals(S_RUN, matches.forSlot(10).luckRunId());
		assertEquals(CroesusRunMatcher.METHOD_UNIQUE, matches.forSlot(10).method());
		assertEquals(S_PLUS_RUN, matches.forSlot(11).luckRunId());
		assertEquals(2, matches.bySlot().values().stream().filter(CroesusRunMatcher.EntryMatch::matched).count());

		coordinator.onCroesusClick(10, viewAt - 1_000L);
		LuckCaptureCoordinator.ViewOutcome outcome = coordinator.onCroesusRunView(runView(liveS), viewAt);
		assertEquals(LuckTrackingStore.OfferResult.ADDED, outcome.offerResult());
		assertEquals(5, outcome.chests());

		List<LuckTrackingStore.MeterAttachment> afterS = store.recordMeterReading(readings.get(1));
		assertEquals(1, afterS.size());
		assertEquals(3_556L, afterS.get(0).before().storedScore);
		assertEquals(3_785L, afterS.get(0).after().storedScore);

		assertEquals(GSON.toJsonTree(liveS), GSON.toJsonTree(store.run(S_RUN)), "replay must reproduce the live record field for field");

		List<MeterGainObservation> gains = LuckMeterGains.all(store.runs());
		assertEquals(2, gains.size());
		assertTrue(gains.get(0).sameReadings(SRunMeterGainEvidence.RECORDED.get(0)), "S+ 302: 3,224 to 3,556");
		assertTrue(gains.get(1).sameReadings(SRunMeterGainEvidence.RECORDED.get(1)), "S 298: 3,556 to 3,785");
		assertEquals(SRunMeterGainEvidence.Status.PROVISIONAL, SRunMeterGainEvidence.assess(gains, RuleVariants.defaults()).status());
	}

	private static void assertSnapshot(LuckRunRecord.LuckMeterSnapshot snapshot, String phase, long stored, String text) {
		assertEquals(phase, snapshot.phase);
		assertEquals("OBSERVED", snapshot.state);
		assertEquals("F5", snapshot.floor);
		assertNull(snapshot.selectedItem);
		assertEquals(stored, snapshot.storedScore);
		assertEquals(text, snapshot.storedText);
		assertEquals(LuckMeterReading.SOURCE_OVERVIEW, snapshot.source);
	}

	/** The run as DRT recorded its completion, before any Croesus or meter observation. */
	private static LuckRunRecord completion(LuckRunRecord live) {
		LuckRunRecord run = LuckRunRecord.forCompletion(live.drtRunSessionId, live.drtCompletionFingerprint, live.completedAtEpochMillis,
			live.floor, live.grade, new DungeonScoreReading(live.score, live.grade));
		run.luckRunId = live.luckRunId;
		run.modifiers = live.modifiers;
		run.party = live.party;
		run.partySource = live.partySource;
		return run;
	}

	/**
	 * The recorded Croesus page with its first two entries replaced by these runs. The S+ entry is
	 * the probe's own lore for that run, aged to what the stored link showed. The store keeps names
	 * only, so the S entry's class labels are placeholders; the parser reads only the names.
	 */
	private static LuckProbeRecorder.ScreenSample croesusPage(LuckRunRecord s, LuckRunRecord sPlus) {
		LuckProbeRecorder.ScreenSample probe = ProbeFixtures.screen(ProbeFixtures.CROESUS_MENU);
		List<String> probeEntry = probe.slots().stream().filter(slot -> slot.slot() == 10).findFirst().orElseThrow().lore();
		List<String> sPlusLore = probeEntry.stream().map(line -> switch (line) {
			case "Completed: 15 secs ago" -> "Completed: " + sPlus.croesus.completedText;
			case "No chests opened yet!" -> "Opened Chest: " + String.join(", ", sPlus.croesus.openedChests);
			case "Chests expire in 2d 23h!" -> "Chests expire in " + sPlus.croesus.expiresText + "!";
			default -> line;
		}).toList();
		List<String> sLore = new ArrayList<>();
		for (String line : probeEntry) {
			if (line.equals("LocalPlayer: Mage (41)")) {
				for (String player : s.croesus.players) sLore.add(player.equals("LocalPlayer") ? line : player + ": (class not stored)");
			} else if (line.equals("Completed: 15 secs ago")) {
				sLore.add("Completed: " + s.croesus.completedText);
			} else if (line.equals("Chests expire in 2d 23h!")) {
				sLore.add("Chests expire in " + s.croesus.expiresText + "!");
			} else {
				sLore.add(line);
			}
		}
		List<LuckProbeRecorder.SlotSample> slots = new ArrayList<>();
		for (LuckProbeRecorder.SlotSample slot : probe.slots()) {
			if (slot.slot() == 10) slots.add(new LuckProbeRecorder.SlotSample(10, slot.itemId(), 1, s.croesus.entryName, sLore));
			else if (slot.slot() == 11) slots.add(new LuckProbeRecorder.SlotSample(11, slot.itemId(), 1, sPlus.croesus.entryName, sPlusLore));
			else slots.add(slot);
		}
		return new LuckProbeRecorder.ScreenSample(probe.title(), probe.containerSlots(), slots);
	}

	/** The recorded F5 run view layout carrying the live chest lore; an S run has no Bedrock chest. */
	private static LuckProbeRecorder.ScreenSample runView(LuckRunRecord live) {
		LuckProbeRecorder.ScreenSample probe = ProbeFixtures.screen(ProbeFixtures.CROESUS_RUN_VIEW_F5);
		Function<String, LuckRunRecord.LuckChestRecord> chest = name -> live.chests.stream()
			.filter(c -> c.tier.equalsIgnoreCase(name)).findFirst().orElse(null);
		List<LuckProbeRecorder.SlotSample> slots = new ArrayList<>();
		for (LuckProbeRecorder.SlotSample slot : probe.slots()) {
			if (slot.name().equals("Bedrock")) {
				slots.add(new LuckProbeRecorder.SlotSample(slot.slot(), "minecraft:black_stained_glass_pane", 1, "", List.of()));
			} else if (chest.apply(slot.name()) != null && slot.itemId().equals("minecraft:player_head")) {
				slots.add(new LuckProbeRecorder.SlotSample(slot.slot(), slot.itemId(), slot.count(), slot.name(), chest.apply(slot.name()).offers.get(0).rawLines));
			} else {
				slots.add(slot);
			}
		}
		return new LuckProbeRecorder.ScreenSample(probe.title(), probe.containerSlots(), slots);
	}
}
