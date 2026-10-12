package dev.vy.drt.luck.capture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Run matching against the recorded Croesus page, never forcing an ambiguous link. */
class CroesusRunMatcherTest {
	private static final long NOW = 1_800_000_000_000L;
	private static final long MINUTE = 60_000L;
	private static final long HOUR = 60 * MINUTE;
	private static final List<String> M5_PARTY = List.of("Teammate_A", "Teammate_B", "Teammate_C", "Teammate_D", "LocalPlayer");
	private static final int[] HOUR_AGO_SLOTS = {11, 12, 13, 14, 15, 16, 19, 20, 21, 22, 23};

	private static CroesusMenuParser.Menu menu() {
		return CroesusMenuParser.parse(ProbeFixtures.screen(ProbeFixtures.CROESUS_MENU));
	}

	private static LuckRunRecord run(String id, String floor, long ageMillis, List<String> party) {
		LuckRunRecord run = new LuckRunRecord();
		run.luckRunId = id;
		run.floor = floor;
		run.grade = "S+";
		run.completedAtEpochMillis = NOW - ageMillis;
		run.party = party;
		return run;
	}

	@Test
	void justCompletedSoloRunMatchesUniquely() {
		CroesusRunMatcher.Result result = CroesusRunMatcher.match(menu(), List.of(run("f5", "F5", 20_000L, List.of("LocalPlayer"))), NOW);
		CroesusRunMatcher.EntryMatch match = result.forSlot(10);
		assertEquals("f5", match.luckRunId());
		assertEquals(CroesusRunMatcher.METHOD_UNIQUE, match.method());
	}

	@Test
	void unknownRosterStillMatchesOnFloorAndTime() {
		CroesusRunMatcher.Result result = CroesusRunMatcher.match(menu(), List.of(run("f5", "F5", 20_000L, null)), NOW);
		assertEquals("f5", result.forSlot(10).luckRunId());
	}

	@Test
	void differentRosterNeverMatches() {
		CroesusRunMatcher.Result result = CroesusRunMatcher.match(menu(), List.of(run("f5", "F5", 20_000L, List.of("SomeoneElse"))), NOW);
		assertFalse(result.forSlot(10).matched());
		assertEquals(CroesusRunMatcher.REASON_NO_CANDIDATE, result.forSlot(10).reason());
	}

	@Test
	void wrongModeOrOutsideTheWindowNeverMatches() {
		assertFalse(CroesusRunMatcher.match(menu(), List.of(run("m5", "M5", 20_000L, null)), NOW).forSlot(10).matched());
		assertFalse(CroesusRunMatcher.match(menu(), List.of(run("f5", "F5", 5 * MINUTE, null)), NOW).forSlot(10).matched());
	}

	@Test
	void identicalEntriesStayUnmatchedUnderTheStrictPolicy() {
		List<LuckRunRecord> runs = new ArrayList<>();
		for (int i = 0; i < HOUR_AGO_SLOTS.length; i++) runs.add(run("m5-" + i, "M5", HOUR + (i + 1) * 4 * MINUTE, M5_PARTY));
		CroesusRunMatcher.Result result = CroesusRunMatcher.match(menu(), runs, NOW);
		for (int slot : HOUR_AGO_SLOTS) {
			assertNull(result.forSlot(slot).luckRunId(), "slot " + slot);
			assertEquals(CroesusRunMatcher.REASON_AMBIGUOUS, result.forSlot(slot).reason());
		}
	}

	@Test
	void identicalEntriesPairInOrderOnlyUnderTheListOrderPolicy() {
		List<LuckRunRecord> runs = new ArrayList<>();
		for (int i = 0; i < HOUR_AGO_SLOTS.length; i++) runs.add(run("m5-" + i, "M5", HOUR + (i + 1) * 4 * MINUTE, M5_PARTY));
		CroesusRunMatcher.Result result = CroesusRunMatcher.match(menu(), runs, NOW, CroesusRunMatcher.Policy.LIST_ORDER);
		for (int i = 0; i < HOUR_AGO_SLOTS.length; i++) {
			CroesusRunMatcher.EntryMatch match = result.forSlot(HOUR_AGO_SLOTS[i]);
			assertEquals("m5-" + i, match.luckRunId(), "slot " + HOUR_AGO_SLOTS[i]);
			assertEquals(CroesusRunMatcher.METHOD_ORDER_IN_BLOCK, match.method());
		}
		assertFalse(result.forSlot(24).matched(), "the 2 hours ago entry has no captured run");
	}

	@Test
	void blockWithAMissingRunStaysUnmatched() {
		List<LuckRunRecord> runs = new ArrayList<>();
		for (int i = 0; i < HOUR_AGO_SLOTS.length - 1; i++) runs.add(run("m5-" + i, "M5", HOUR + (i + 1) * 4 * MINUTE, M5_PARTY));
		CroesusRunMatcher.Result result = CroesusRunMatcher.match(menu(), runs, NOW);
		for (int slot : HOUR_AGO_SLOTS) {
			assertNull(result.forSlot(slot).luckRunId());
			assertEquals(CroesusRunMatcher.REASON_AMBIGUOUS, result.forSlot(slot).reason());
		}
	}

	@Test
	void runsThatCouldContinueOnTheNextPageStayUnmatched() {
		List<String> party = List.of("Teammate_E", "Teammate_F", "LocalPlayer", "Teammate_G", "Teammate_H");
		CroesusRunMatcher.Result result = CroesusRunMatcher.match(menu(), List.of(run("old", "M5", 63 * HOUR + 30 * MINUTE, party)), NOW);
		CroesusRunMatcher.EntryMatch match = result.forSlot(25);
		assertFalse(match.matched());
		assertEquals(CroesusRunMatcher.REASON_PAGE_BOUNDARY, match.reason());
	}

	@Test
	void crossedOrderIsRejected() {
		CroesusMenuParser.Menu menu = new CroesusMenuParser.Menu(1, 1, false, List.of(
			entry(10, 0, List.of("LocalPlayer", "Alpha")),
			entry(11, 1, List.of("LocalPlayer", "Beta"))
		));
		List<LuckRunRecord> runs = List.of(
			run("alpha", "F5", HOUR + 55 * MINUTE, List.of("LocalPlayer", "Alpha")),
			run("beta", "F5", HOUR + 5 * MINUTE, List.of("LocalPlayer", "Beta"))
		);
		CroesusRunMatcher.Result result = CroesusRunMatcher.match(menu, runs, NOW);
		assertEquals(CroesusRunMatcher.REASON_ORDER_CONFLICT, result.forSlot(10).reason());
		assertEquals(CroesusRunMatcher.REASON_ORDER_CONFLICT, result.forSlot(11).reason());
	}

	@Test
	void rosterSavedOnAnEarlierLinkMustAgree() {
		LuckRunRecord linked = run("f5", "F5", 20_000L, null);
		linked.croesus = new LuckRunRecord.LuckCroesusLink();
		linked.croesus.players = new ArrayList<>(List.of("LocalPlayer", "Other"));
		assertFalse(CroesusRunMatcher.match(menu(), List.of(linked), NOW).forSlot(10).matched());
	}

	@Test
	void chestsOpenedInTheDungeonSeparateConsecutiveRuns() {
		List<String> party = List.of("LocalPlayer", "Alpha");
		CroesusMenuParser.Menu menu = new CroesusMenuParser.Menu(1, 1, false, List.of(
			opened(10, 0, party, List.of("Bedrock")),
			opened(11, 1, party, List.of("Obsidian"))
		));
		List<LuckRunRecord> runs = List.of(
			withDungeonPurchase(run("first", "F5", HOUR + 50 * MINUTE, party), "OBSIDIAN"),
			withDungeonPurchase(run("second", "F5", HOUR + 10 * MINUTE, party), "BEDROCK")
		);
		CroesusRunMatcher.Result result = CroesusRunMatcher.match(menu, runs, NOW);
		assertEquals("second", result.forSlot(10).luckRunId());
		assertEquals("first", result.forSlot(11).luckRunId());
		assertEquals(CroesusRunMatcher.METHOD_UNIQUE, result.forSlot(10).method());
	}

	@Test
	void oneRunWithPurchaseEvidenceCanForceTheOtherPairing() {
		List<String> party = List.of("LocalPlayer", "Alpha");
		CroesusMenuParser.Menu menu = new CroesusMenuParser.Menu(1, 1, false, List.of(
			opened(10, 0, party, List.of("Bedrock")),
			opened(11, 1, party, List.of("Obsidian"))
		));
		List<LuckRunRecord> runs = List.of(
			run("first", "F5", HOUR + 50 * MINUTE, party),
			withDungeonPurchase(run("second", "F5", HOUR + 10 * MINUTE, party), "BEDROCK")
		);
		CroesusRunMatcher.Result result = CroesusRunMatcher.match(menu, runs, NOW);
		assertEquals("second", result.forSlot(10).luckRunId());
		assertEquals("first", result.forSlot(11).luckRunId());
		assertEquals(CroesusRunMatcher.METHOD_ELIMINATION, result.forSlot(10).method());
		assertEquals(CroesusRunMatcher.METHOD_ELIMINATION, result.forSlot(11).method());
	}

	@Test
	void sameOpenedChestsLeaveConsecutiveRunsUnmatched() {
		List<String> party = List.of("LocalPlayer", "Alpha");
		CroesusMenuParser.Menu menu = new CroesusMenuParser.Menu(1, 1, false, List.of(
			opened(10, 0, party, List.of("Bedrock")),
			opened(11, 1, party, List.of("Bedrock"))
		));
		List<LuckRunRecord> runs = List.of(
			withDungeonPurchase(run("first", "F5", HOUR + 50 * MINUTE, party), "BEDROCK"),
			withDungeonPurchase(run("second", "F5", HOUR + 10 * MINUTE, party), "BEDROCK")
		);
		CroesusRunMatcher.Result result = CroesusRunMatcher.match(menu, runs, NOW);
		assertEquals(CroesusRunMatcher.REASON_AMBIGUOUS, result.forSlot(10).reason());
		assertEquals(CroesusRunMatcher.REASON_AMBIGUOUS, result.forSlot(11).reason());
	}

	@Test
	void aChestOpenedInTheDungeonMustBeListedOnTheEntry() {
		List<String> party = List.of("LocalPlayer", "Alpha");
		CroesusMenuParser.Menu menu = new CroesusMenuParser.Menu(1, 1, false, List.of(entry(10, 0, party)));
		LuckRunRecord run = withDungeonPurchase(run("only", "F5", HOUR + 10 * MINUTE, party), "BEDROCK");
		CroesusRunMatcher.Result result = CroesusRunMatcher.match(menu, List.of(run), NOW);
		assertEquals(CroesusRunMatcher.REASON_NO_CANDIDATE, result.forSlot(10).reason());
	}

	@Test
	void chestsBoughtInCroesusAreNotEvidence() {
		List<String> party = List.of("LocalPlayer", "Alpha");
		CroesusMenuParser.Menu menu = new CroesusMenuParser.Menu(1, 1, false, List.of(entry(10, 0, party)));
		LuckRunRecord run = run("only", "F5", HOUR + 10 * MINUTE, party);
		LuckRunRecord.LuckChestRecord chest = new LuckRunRecord.LuckChestRecord();
		chest.tier = "BEDROCK";
		chest.purchased = true;
		chest.purchaseSource = LuckRunRecord.SOURCE_CROESUS_MENU;
		run.chests.add(chest);
		assertEquals("only", CroesusRunMatcher.match(menu, List.of(run), NOW).forSlot(10).luckRunId());
	}

	@Test
	void eliminationNeedsEveryPairingForced() {
		CroesusMenuParser.RunEntry a = entry(10, 0, List.of());
		CroesusMenuParser.RunEntry b = entry(11, 1, List.of());
		CroesusMenuParser.RunEntry c = entry(12, 2, List.of());
		java.util.Map<Integer, java.util.Set<String>> forced = java.util.Map.of(
			10, java.util.Set.of("x"), 11, java.util.Set.of("x", "y"), 12, java.util.Set.of("y", "z"));
		assertEquals(java.util.Map.of(10, "x", 11, "y", 12, "z"), CroesusRunMatcher.assignByElimination(List.of(a, b, c), forced));
		java.util.Map<Integer, java.util.Set<String>> open = java.util.Map.of(
			10, java.util.Set.of("x", "y"), 11, java.util.Set.of("x", "y"));
		assertNull(CroesusRunMatcher.assignByElimination(List.of(a, b), open));
	}

	private static LuckRunRecord withDungeonPurchase(LuckRunRecord run, String tier) {
		LuckRunRecord.LuckChestRecord chest = new LuckRunRecord.LuckChestRecord();
		chest.tier = tier;
		chest.purchased = true;
		chest.purchaseSource = LuckRunRecord.SOURCE_DUNGEON_CHEST;
		run.chests.add(chest);
		return run;
	}

	private static CroesusMenuParser.RunEntry opened(int slot, int position, List<String> players, List<String> openedChests) {
		return new CroesusMenuParser.RunEntry(slot, position, "The Catacombs", false, true, dev.vy.drt.luck.FloorId.F5, players,
			"1 hour ago", HOUR, 2 * HOUR, null, false, openedChests, List.of(), List.of());
	}

	private static CroesusMenuParser.RunEntry entry(int slot, int position, List<String> players) {
		return new CroesusMenuParser.RunEntry(slot, position, "The Catacombs", false, true, dev.vy.drt.luck.FloorId.F5, players,
			"1 hour ago", HOUR, 2 * HOUR, null, true, List.of(), List.of(), List.of());
	}
}
