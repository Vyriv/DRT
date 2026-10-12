package dev.vy.drt.luck.capture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.vy.drt.luck.LuckTestData;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** The recorded F5 run, from Croesus page to chest offers, plus meter readings around it. */
class LuckCaptureCoordinatorTest {
	private static final long NOW = 1_800_000_000_000L;

	@TempDir
	Path dir;

	private LuckTrackingStore store;
	private LuckCaptureCoordinator coordinator;

	@BeforeEach
	void setUp() {
		store = new LuckTrackingStore(dir.resolve("drt-luck.json"));
		store.load();
		coordinator = new LuckCaptureCoordinator(store, floor -> LuckTestData.defaults().floor(floor));
	}

	private LuckRunRecord addF5Run(long completedAt) {
		LuckRunRecord run = LuckRunRecord.forCompletion("session", "fp-f5-" + completedAt, completedAt, "F5", "S+", new DungeonScoreReading(302, "S+"));
		run.party = List.of("LocalPlayer");
		run.partySource = LuckRunRecord.PARTY_SOURCE_TAB_LIST;
		assertEquals(LuckTrackingStore.AddResult.ADDED, store.addRun(run));
		return store.runs().get(store.runs().size() - 1);
	}

	@Test
	void clickedEntryLinksTheRunViewAndStoresEveryChest() throws Exception {
		LuckRunRecord run = addF5Run(NOW - 20_000L);
		CroesusRunMatcher.Result matches = coordinator.onCroesusMenu(ProbeFixtures.screen(ProbeFixtures.CROESUS_MENU), NOW);
		assertEquals(run.luckRunId, matches.forSlot(10).luckRunId());
		coordinator.onCroesusClick(10, NOW + 1_000L);
		LuckCaptureCoordinator.ViewOutcome outcome = coordinator.onCroesusRunView(ProbeFixtures.screen(ProbeFixtures.CROESUS_RUN_VIEW_F5), NOW + 2_000L);
		assertEquals(LuckTrackingStore.OfferResult.ADDED, outcome.offerResult());

		LuckRunRecord stored = store.run(run.luckRunId);
		assertEquals(6, stored.chests.size());
		LuckRunRecord.LuckChestRecord bedrock = stored.chests.stream().filter(c -> c.tier.equals("BEDROCK")).findFirst().orElseThrow();
		assertEquals(1_600_000L, bedrock.costCoins);
		assertEquals(1, bedrock.offers.size());
		LuckRunRecord.LuckOfferSnapshot offer = bedrock.offers.get(0);
		assertFalse(offer.changedFromPrevious);
		assertFalse(offer.afterKismetReroll);
		assertEquals(LuckRunRecord.SOURCE_CROESUS_RUN_VIEW, offer.source);
		assertEquals(List.of("ENCHANTMENT_ULTIMATE_WISE_2", "DARK_ORB", "ENCHANTMENT_ULTIMATE_NO_PAIN_NO_GAIN_2", "ESSENCE_WITHER_40", "ESSENCE_UNDEAD_54"), offer.itemKeys);
		assertEquals(54, offer.contents.get(4).quantity);
		assertEquals("Contents", offer.rawLines.get(0));

		assertEquals("COMPLETE", stored.coverage.offerStatus);
		assertEquals("NO_REROLL", stored.coverage.kismetStatus);
		assertTrue(stored.coverage.caveats.contains("KISMET_PROVISIONAL"));
		assertEquals(List.of(), stored.coverage.blockers);
		assertTrue(stored.coverage.caveats.contains("METER_UNKNOWN"));
		assertTrue(stored.coverage.qualifiesForScore);
		assertEquals(List.of("LocalPlayer"), stored.croesus.players);
		assertEquals(CroesusRunMatcher.METHOD_UNIQUE, stored.croesus.method);
		assertEquals(List.of("Kismet Feather", "Dungeon Chest Key"), stored.croesus.availableModifiers);
		assertTrue(stored.croesus.noChestsOpened);
		assertTrue(stored.chests.stream().allMatch(chest -> Boolean.FALSE.equals(chest.purchased)));

		JsonObject root = JsonParser.parseString(Files.readString(dir.resolve("drt-luck.json"), StandardCharsets.UTF_8)).getAsJsonObject();
		assertEquals(2, root.get("schemaVersion").getAsInt());
		LuckTrackingStore restarted = new LuckTrackingStore(dir.resolve("drt-luck.json"));
		assertEquals(LuckTrackingStore.LoadState.LOADED, restarted.load());
		assertEquals(6, restarted.run(run.luckRunId).chests.size());
	}

	@Test
	void sameContentsAreStoredOnceAndChangesAreFlaggedNotAssumedRerolls() {
		LuckRunRecord run = addF5Run(NOW - 20_000L);
		coordinator.onCroesusMenu(ProbeFixtures.screen(ProbeFixtures.CROESUS_MENU), NOW);
		coordinator.onCroesusClick(10, NOW + 1_000L);
		coordinator.onCroesusRunView(ProbeFixtures.screen(ProbeFixtures.CROESUS_RUN_VIEW_F5), NOW + 2_000L);
		LuckCaptureCoordinator.ViewOutcome again = coordinator.onCroesusRunView(ProbeFixtures.screen(ProbeFixtures.CROESUS_RUN_VIEW_F5), NOW + 3_000L);
		assertEquals(LuckTrackingStore.OfferResult.UNCHANGED, again.offerResult());

		LuckProbeRecorder.ScreenSample changed = ProbeFixtures.screen(ProbeFixtures.CROESUS_RUN_VIEW_F5,
			line -> line.equals("Dark Orb") ? "Enchanted Book (Bank III)" : line);
		LuckCaptureCoordinator.ViewOutcome outcome = coordinator.onCroesusRunView(changed, NOW + 4_000L);
		assertEquals(LuckTrackingStore.OfferResult.CHANGED_UNVERIFIED, outcome.offerResult());

		LuckRunRecord stored = store.run(run.luckRunId);
		LuckRunRecord.LuckChestRecord bedrock = stored.chests.stream().filter(c -> c.tier.equals("BEDROCK")).findFirst().orElseThrow();
		assertEquals(2, bedrock.offers.size());
		assertTrue(bedrock.offers.get(1).changedFromPrevious);
		assertFalse(bedrock.offers.get(1).afterKismetReroll);
		assertEquals("CONTENTS_CHANGED_UNVERIFIED", stored.coverage.kismetStatus);
		assertTrue(stored.coverage.blockers.contains("OFFER_CHANGED_UNVERIFIED"));
		assertFalse(stored.coverage.qualifiesForScore);
		LuckRunRecord.LuckChestRecord wood = stored.chests.stream().filter(c -> c.tier.equals("WOOD")).findFirst().orElseThrow();
		assertEquals(1, wood.offers.size());
	}

	@Test
	void openedChestFromTheCroesusPageMarksThePurchase() {
		LuckRunRecord run = addF5Run(NOW - 20_000L);
		coordinator.onCroesusMenu(ProbeFixtures.screen(ProbeFixtures.CROESUS_MENU), NOW);
		coordinator.onCroesusClick(10, NOW + 1_000L);
		coordinator.onCroesusRunView(ProbeFixtures.screen(ProbeFixtures.CROESUS_RUN_VIEW_F5), NOW + 2_000L);

		LuckProbeRecorder.ScreenSample opened = ProbeFixtures.screen(ProbeFixtures.CROESUS_MENU,
			line -> line.equals("No chests opened yet!") ? "Opened Chest: Bedrock" : line.equals("Completed: 15 secs ago") ? "Completed: 1 min ago" : line);
		CroesusMenuParser.Menu parsed = CroesusMenuParser.parse(opened);
		assertEquals(List.of("Bedrock"), parsed.entryAt(10).openedChests());
		coordinator.onCroesusMenu(opened, NOW + 60_000L);

		LuckRunRecord stored = store.run(run.luckRunId);
		for (LuckRunRecord.LuckChestRecord chest : stored.chests) {
			assertEquals(Boolean.valueOf(chest.tier.equals("BEDROCK")), chest.purchased, chest.tier);
		}
		LuckRunRecord.LuckChestRecord bedrock = stored.chests.stream().filter(c -> c.tier.equals("BEDROCK")).findFirst().orElseThrow();
		assertEquals(0, bedrock.purchasedOfferIndex);
		assertEquals(List.of("Bedrock"), stored.croesus.openedChests);
	}

	@Test
	void runViewWithoutAClickedEntryIsKeptUnmatched() {
		addF5Run(NOW - 20_000L);
		coordinator.onCroesusMenu(ProbeFixtures.screen(ProbeFixtures.CROESUS_MENU), NOW);
		LuckCaptureCoordinator.ViewOutcome outcome = coordinator.onCroesusRunView(ProbeFixtures.screen(ProbeFixtures.CROESUS_RUN_VIEW_F5), NOW + 2_000L);
		assertNull(outcome.luckRunId());
		assertEquals(LuckCaptureCoordinator.REASON_NO_CLICKED_ENTRY, outcome.unmatchedReason());
		assertEquals(1, store.unmatchedCroesus().size());
		assertEquals(6, store.unmatchedCroesus().get(0).chests.size());
		assertTrue(store.runs().get(0).chests.isEmpty());
	}

	@Test
	void clickedEntryForAnotherFloorOrAnAmbiguousRunIsKeptUnmatched() {
		addF5Run(NOW - 20_000L);
		coordinator.onCroesusMenu(ProbeFixtures.screen(ProbeFixtures.CROESUS_MENU), NOW);
		coordinator.onCroesusClick(11, NOW + 1_000L);
		LuckCaptureCoordinator.ViewOutcome mismatch = coordinator.onCroesusRunView(
			ProbeFixtures.retitled(ProbeFixtures.screen(ProbeFixtures.CROESUS_RUN_VIEW_F5), "Catacombs - Floor VI"), NOW + 2_000L);
		assertEquals(LuckCaptureCoordinator.REASON_VIEW_ENTRY_MISMATCH, mismatch.unmatchedReason());

		coordinator.onCroesusMenu(ProbeFixtures.screen(ProbeFixtures.CROESUS_MENU), NOW + 5_000L);
		coordinator.onCroesusClick(11, NOW + 6_000L);
		LuckCaptureCoordinator.ViewOutcome uncaptured = coordinator.onCroesusRunView(ProbeFixtures.screen(ProbeFixtures.CROESUS_RUN_VIEW_F5), NOW + 7_000L);
		assertEquals(CroesusRunMatcher.REASON_NO_CANDIDATE, uncaptured.unmatchedReason());
		UnmatchedCroesusRun kept = store.unmatchedCroesus().get(store.unmatchedCroesus().size() - 1);
		assertEquals("M5", kept.floor);
		assertEquals(5, kept.players.size());
		assertNotNull(kept.completedAfterEpochMillis);
	}

	@Test
	void staleClickDoesNotLinkALaterView() {
		addF5Run(NOW - 20_000L);
		coordinator.onCroesusMenu(ProbeFixtures.screen(ProbeFixtures.CROESUS_MENU), NOW);
		coordinator.onCroesusClick(10, NOW + 1_000L);
		LuckCaptureCoordinator.ViewOutcome outcome = coordinator.onCroesusRunView(ProbeFixtures.screen(ProbeFixtures.CROESUS_RUN_VIEW_F5),
			NOW + 1_000L + LuckCaptureCoordinator.CLICK_TO_VIEW_MILLIS + 1L);
		assertEquals(LuckCaptureCoordinator.REASON_NO_CLICKED_ENTRY, outcome.unmatchedReason());
	}

	private LuckRunRecord addF5Run(long completedAt, String session) {
		LuckRunRecord run = LuckRunRecord.forCompletion(session, "fp-f5-" + session, completedAt, "F5", "S", new DungeonScoreReading(296, "S"));
		run.party = List.of("LocalPlayer");
		run.partySource = LuckRunRecord.PARTY_SOURCE_TAB_LIST;
		assertEquals(LuckTrackingStore.AddResult.ADDED, store.addRun(run));
		return store.runs().get(store.runs().size() - 1);
	}

	@Test
	void quickClickReadsThePageAtTheClickAndLinksTheView() {
		LuckRunRecord run = addF5Run(NOW - 20_000L);
		CroesusRunMatcher.EntryMatch clicked = coordinator.onCroesusClick(ProbeFixtures.screen(ProbeFixtures.CROESUS_MENU), 10, NOW);
		assertNotNull(clicked, "no stable page was captured, the click itself carries the entry");
		assertTrue(clicked.matched());
		assertEquals(run.luckRunId, clicked.luckRunId());

		LuckCaptureCoordinator.ViewOutcome outcome = coordinator.onCroesusRunView(ProbeFixtures.screen(ProbeFixtures.CROESUS_RUN_VIEW_F5), NOW + 1_000L);
		assertEquals(run.luckRunId, outcome.luckRunId());
		assertEquals(LuckTrackingStore.OfferResult.ADDED, outcome.offerResult());
		assertFalse(outcome.live());
		assertEquals("session", coordinator.activeViewRunSession(NOW + 2_000L), "chests opened from this view belong to the run");
		assertTrue(store.unmatchedCroesus().isEmpty());

		LuckCaptureCoordinator.ViewOutcome reopened = coordinator.onCroesusRunView(ProbeFixtures.screen(ProbeFixtures.CROESUS_RUN_VIEW_F5), NOW + 60_000L);
		assertEquals(run.luckRunId, reopened.luckRunId(), "leaving for a chest and coming back keeps the link");
		assertEquals(LuckTrackingStore.OfferResult.UNCHANGED, reopened.offerResult());
	}

	@Test
	void clickOffAnEntryOfTheCurrentPageNeverFallsBackToAnOlderPage() {
		addF5Run(NOW - 20_000L);
		coordinator.onCroesusMenu(ProbeFixtures.screen(ProbeFixtures.CROESUS_MENU), NOW);
		LuckProbeRecorder.ScreenSample page = ProbeFixtures.screen(ProbeFixtures.CROESUS_MENU);
		LuckProbeRecorder.ScreenSample withoutSlot10 = new LuckProbeRecorder.ScreenSample(page.title(), page.containerSlots(),
			page.slots().stream().filter(slot -> slot.slot() != 10).toList());
		assertNull(coordinator.onCroesusClick(withoutSlot10, 10, NOW + 1_000L));
		LuckCaptureCoordinator.ViewOutcome outcome = coordinator.onCroesusRunView(ProbeFixtures.screen(ProbeFixtures.CROESUS_RUN_VIEW_F5), NOW + 2_000L);
		assertEquals(LuckCaptureCoordinator.REASON_NO_CLICKED_ENTRY, outcome.unmatchedReason());
	}

	@Test
	void quickClickOnAnAmbiguousEntryStaysUnmatched() {
		addF5Run(NOW - 20_000L, "live-a-run-1");
		addF5Run(NOW - 25_000L, "live-a-run-2");
		CroesusRunMatcher.EntryMatch clicked = coordinator.onCroesusClick(ProbeFixtures.screen(ProbeFixtures.CROESUS_MENU), 10, NOW);
		assertNotNull(clicked);
		assertFalse(clicked.matched(), "two captured runs fit the entry; strict matching refuses to pick one");
		LuckCaptureCoordinator.ViewOutcome outcome = coordinator.onCroesusRunView(ProbeFixtures.screen(ProbeFixtures.CROESUS_RUN_VIEW_F5), NOW + 1_000L);
		assertNull(outcome.luckRunId());
		assertTrue(store.runs().stream().allMatch(run -> run.chests.isEmpty()));
		assertNull(coordinator.activeViewRunSession(NOW + 2_000L));
	}

	@Test
	void chestClickedBeforeTheRunViewWasStableStillStoresOffersAndLinksTheChest() {
		LuckRunRecord run = addF5Run(NOW - 20_000L);
		LuckCaptureCoordinator.ClickOutcome page = coordinator.onCroesusScreenClick(ProbeFixtures.screen(ProbeFixtures.CROESUS_MENU), 10, NOW, null);
		assertNotNull(page);
		assertEquals(run.luckRunId, page.entry().luckRunId());
		assertNull(page.view());

		LuckCaptureCoordinator.ClickOutcome chestClick = coordinator.onCroesusScreenClick(ProbeFixtures.screen(ProbeFixtures.CROESUS_RUN_VIEW_F5), 13, NOW + 400L, null);
		assertNotNull(chestClick, "the view left within the stability window is read at the chest click");
		assertNull(chestClick.entry());
		assertEquals(run.luckRunId, chestClick.view().luckRunId());
		assertEquals(LuckTrackingStore.OfferResult.ADDED, chestClick.view().offerResult());
		assertEquals(6, store.run(run.luckRunId).chests.size());
		assertEquals("session", coordinator.activeViewRunSession(NOW + 3_000L), "the chest opened from the view belongs to the run");

		LuckCaptureCoordinator.ViewOutcome stable = coordinator.onCroesusRunView(ProbeFixtures.screen(ProbeFixtures.CROESUS_RUN_VIEW_F5), NOW + 900L);
		assertEquals(LuckTrackingStore.OfferResult.UNCHANGED, stable.offerResult(), "a later stable read of the same view stores nothing twice");
		assertEquals(6, store.run(run.luckRunId).chests.size());
	}

	@Test
	void runViewClickWithoutAClickedEntryOrOnAnotherScreenLinksNothing() {
		addF5Run(NOW - 20_000L);
		LuckCaptureCoordinator.ClickOutcome chestClick = coordinator.onCroesusScreenClick(ProbeFixtures.screen(ProbeFixtures.CROESUS_RUN_VIEW_F5), 13, NOW, null);
		assertEquals(LuckCaptureCoordinator.REASON_NO_CLICKED_ENTRY, chestClick.view().unmatchedReason());
		assertNull(coordinator.activeViewRunSession(NOW + 1_000L));
		assertTrue(store.runs().stream().allMatch(run -> run.chests.isEmpty()));

		LuckProbeRecorder.ScreenSample menu = ProbeFixtures.screen(ProbeFixtures.CROESUS_MENU);
		LuckProbeRecorder.ScreenSample other = new LuckProbeRecorder.ScreenSample("Large Chest", menu.containerSlots(), menu.slots());
		assertNull(coordinator.onCroesusScreenClick(other, 10, NOW + 2_000L, null));
		assertNull(coordinator.onCroesusScreenClick(null, 10, NOW + 2_000L, null));
	}

	@Test
	void runViewInTheCompletedRunsInstanceLinksWithoutAClick() {
		LuckRunRecord run = addF5Run(NOW - 20_000L, "live-a-run-1");
		LuckCaptureCoordinator.ViewOutcome outcome = coordinator.onCroesusRunView(ProbeFixtures.screen(ProbeFixtures.CROESUS_RUN_VIEW_F5), NOW,
			new LiveRewardSession("live-a-run-1", "F5"));
		assertEquals(run.luckRunId, outcome.luckRunId());
		assertTrue(outcome.live());
		assertEquals(LuckTrackingStore.OfferResult.ADDED, outcome.offerResult());
		assertEquals(6, store.run(run.luckRunId).chests.size());
		assertTrue(store.run(run.luckRunId).chests.stream().noneMatch(chest -> Boolean.TRUE.equals(chest.purchased)), "seeing offers is not buying");
		assertNull(coordinator.activeViewRunSession(NOW + 1_000L), "the live link is never reused for a later Croesus visit");
		assertTrue(store.unmatchedCroesus().isEmpty());
	}

	@Test
	void liveSessionForAnotherFloorKeepsTheViewUnmatched() {
		LuckRunRecord run = addF5Run(NOW - 20_000L, "live-a-run-1");
		LuckCaptureCoordinator.ViewOutcome outcome = coordinator.onCroesusRunView(ProbeFixtures.screen(ProbeFixtures.CROESUS_RUN_VIEW_F5), NOW,
			new LiveRewardSession("live-a-run-1", "F6"));
		assertNull(outcome.luckRunId());
		assertEquals(LuckCaptureCoordinator.REASON_LIVE_FLOOR_MISMATCH, outcome.unmatchedReason());
		assertTrue(store.run(run.luckRunId).chests.isEmpty());
		assertEquals(1, store.unmatchedCroesus().size());
	}

	@Test
	void delayedCroesusViewIsNeverGivenToTheMostRecentRun() {
		LuckRunRecord older = addF5Run(NOW - 30L * 60_000L, "live-a-run-1");
		LuckRunRecord newest = addF5Run(NOW - 20_000L, "live-a-run-2");
		LuckCaptureCoordinator.ViewOutcome outcome = coordinator.onCroesusRunView(ProbeFixtures.screen(ProbeFixtures.CROESUS_RUN_VIEW_F5), NOW + 5_000L);
		assertEquals(LuckCaptureCoordinator.REASON_NO_CLICKED_ENTRY, outcome.unmatchedReason());
		assertTrue(store.run(older.luckRunId).chests.isEmpty());
		assertTrue(store.run(newest.luckRunId).chests.isEmpty());
		assertEquals(1, store.unmatchedCroesus().size(), "kept, with every offer, for later review");
	}

	@Test
	void consecutiveRunsInTheirOwnInstancesDoNotCrossLink() {
		LuckRunRecord first = addF5Run(NOW - 10L * 60_000L, "live-a-run-1");
		coordinator.onCroesusRunView(ProbeFixtures.screen(ProbeFixtures.CROESUS_RUN_VIEW_F5), NOW - 9L * 60_000L,
			new LiveRewardSession("live-a-run-1", "F5"));
		LuckRunRecord second = addF5Run(NOW - 20_000L, "live-a-run-2");
		LuckProbeRecorder.ScreenSample secondView = ProbeFixtures.screen(ProbeFixtures.CROESUS_RUN_VIEW_F5,
			line -> line.equals("Dark Orb") ? "Enchanted Book (Bank III)" : line);
		coordinator.onCroesusRunView(secondView, NOW, new LiveRewardSession("live-a-run-2", "F5"));

		LuckRunRecord.LuckChestRecord firstBedrock = store.run(first.luckRunId).chests.stream().filter(c -> c.tier.equals("BEDROCK")).findFirst().orElseThrow();
		LuckRunRecord.LuckChestRecord secondBedrock = store.run(second.luckRunId).chests.stream().filter(c -> c.tier.equals("BEDROCK")).findFirst().orElseThrow();
		assertEquals(1, firstBedrock.offers.size(), "the second run's offers never land on the first");
		assertFalse(firstBedrock.offers.get(0).rawLines.contains("Enchanted Book (Bank III)"));
		assertEquals(1, secondBedrock.offers.size());
		assertTrue(secondBedrock.offers.get(0).rawLines.contains("Enchanted Book (Bank III)"));
	}

	@Test
	void meterReadingsBecomeBeforeAndAfterSnapshots() {
		coordinator.onMeterOverview(ProbeFixtures.screen(ProbeFixtures.RNG_OVERVIEW), NOW - 600_000L);
		LuckRunRecord run = addF5Run(NOW - 20_000L);
		LuckRunRecord.LuckMeterSnapshot before = run.meterSnapshots.get(0);
		assertEquals(LuckRunRecord.PHASE_BEFORE_RUN, before.phase);
		assertEquals("OBSERVED", before.state);
		assertNull(before.selectedItem);
		assertEquals(3_224L, before.storedScore);
		assertEquals("OBSERVED", run.coverage.meterStatus);
		assertFalse(run.coverage.caveats.contains("METER_UNKNOWN"));

		LuckProbeRecorder.ScreenSample after = ProbeFixtures.screen(ProbeFixtures.RNG_OVERVIEW,
			line -> line.equals("Stored Dungeon Score: 3,224") ? "Stored Dungeon Score: 3,556" : line);
		List<LuckTrackingStore.MeterAttachment> attachments = coordinator.onMeterOverview(after, NOW);
		assertEquals(1, attachments.size());
		assertEquals("F5", attachments.get(0).floor());
		assertEquals(3_224L, attachments.get(0).before().storedScore);
		assertEquals(3_556L, attachments.get(0).after().storedScore);
		LuckRunRecord stored = store.run(run.luckRunId);
		assertEquals(2, stored.meterSnapshots.size());
		assertEquals(LuckRunRecord.PHASE_AFTER_RUN, stored.meterSnapshots.get(1).phase);
	}

	@Test
	void oneSetupOverviewStartsEachFloorsMeterAtItsNextRunHoweverLate() {
		coordinator.onMeterOverview(ProbeFixtures.screen(ProbeFixtures.RNG_OVERVIEW), NOW - 10L * 86_400_000L);
		assertEquals(14, store.meterReadings().size(), "F1 to F7 and M1 to M7 from a single screen");

		LuckRunRecord first = addF5Run(NOW - 2L * 86_400_000L);
		LuckRunRecord second = addF5Run(NOW - 86_400_000L);
		assertEquals(1, first.meterSnapshots.size());
		assertEquals(LuckRunRecord.PHASE_BEFORE_RUN, first.meterSnapshots.get(0).phase);
		assertEquals(3_224L, first.meterSnapshots.get(0).storedScore, "eight days after the reading");
		assertEquals("OBSERVED", first.coverage.meterStatus);
		assertEquals(List.of(), second.meterSnapshots, "the reading is used once; later runs are replayed from it");
	}

	@Test
	void repeatedIdenticalReadingsWithoutARunAreMerged() {
		coordinator.onMeterOverview(ProbeFixtures.screen(ProbeFixtures.RNG_OVERVIEW), NOW);
		int first = store.meterReadings().size();
		assertEquals(14, first);
		coordinator.onMeterOverview(ProbeFixtures.screen(ProbeFixtures.RNG_OVERVIEW), NOW + 60_000L);
		assertEquals(first, store.meterReadings().size());
		LuckMeterReading f5 = store.meterReadings().stream().filter(r -> r.floor.equals("F5")).findFirst().orElseThrow();
		assertEquals(NOW, f5.observedAtEpochMillis);
		assertEquals(NOW + 60_000L, f5.lastObservedAtEpochMillis);
	}

	@Test
	void floorScreenSuppliesExactRequirementsAndGuaranteeChest() {
		coordinator.onMeterFloorMenu(ProbeFixtures.screen(ProbeFixtures.RNG_FLOOR_F5), NOW);
		LuckMeterRequirement rejuvenate = store.requirement("F5", "Enchanted Book (Rejuvenate III)");
		assertEquals(2_540L, rejuvenate.requiredScore);
		assertEquals("BEDROCK", rejuvenate.appearsIn);
		assertEquals("ENCHANTMENT_REJUVENATE_3", rejuvenate.itemKey);
		assertEquals("SHADOW_FURY", store.requirement("F5", "Shadow Fury").itemKey);

		LuckProbeRecorder.ScreenSample selected = ProbeFixtures.screen(ProbeFixtures.RNG_OVERVIEW, line -> switch (line) {
			case "You don't have an RNG drop" -> "Selected Drop";
			case "selected. Choose one to start" -> "Shadow Fury";
			case "progressing towards it!" -> "";
			case "Stored Dungeon Score: 3,224" -> "3,224/114.3k";
			default -> line;
		});
		coordinator.onMeterOverview(selected, NOW + 1_000L);
		LuckMeterReading f5 = store.meterReadings().stream().filter(r -> r.floor.equals("F5") && r.selectedItem != null).findFirst().orElseThrow();
		assertEquals("Shadow Fury", f5.selectedItem);
		assertEquals("SHADOW_FURY", f5.selectedKey);
		assertEquals(3_224L, f5.storedScore);
		assertEquals("114.3k", f5.requiredText);
		assertEquals(114_300L, f5.requiredScore);
		assertEquals(LuckMeterReading.SOURCE_FLOOR_MENU, f5.requiredSource);
		assertEquals("BEDROCK", f5.guaranteeChest);
	}
}
