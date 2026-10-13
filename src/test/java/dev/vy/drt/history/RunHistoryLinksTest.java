package dev.vy.drt.history;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.vy.drt.config.DungeonRunCompletionRecord;
import dev.vy.drt.config.DungeonRunRecord;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Chests are tied to runs only on proof, and stored loot is never changed by the decision. */
class RunHistoryLinksTest {
	private static final long T = 1_800_000_000_000L;

	private static DungeonRunCompletionRecord completion(String session, long at, long runTimeMs, long officialMs) {
		return new DungeonRunCompletionRecord(at, "DUNGEON", "F5", "S+", runTimeMs, session, session + "|F5|S+|" + officialMs);
	}

	private static DungeonRunRecord chest(String session, long at, String title) {
		DungeonRunRecord record = new DungeonRunRecord(at, 1, "F5", "S+", title, 1_000L, 5_000L, 4_000L, new ArrayList<>());
		record.runSessionId = session;
		record.commitFingerprint = session + "#" + title + "#" + at;
		return record;
	}

	@Test
	void croesusChestsOfConsecutiveRunsShowEachRunsOwnOfficialTimeOnlyWhenLinked() {
		DungeonRunCompletionRecord run1 = completion("live-x-run-1", T, 108_000L, 108_000L);
		DungeonRunCompletionRecord run2 = completion("live-x-run-2", T + 147_000L, 136_000L, 136_000L);
		DungeonRunCompletionRecord run3 = completion("live-x-run-3", T + 296_000L, 138_000L, 138_000L);
		List<DungeonRunCompletionRecord> runs = List.of(run1, run2, run3);
		long openedAt = T + 303_000L;
		DungeonRunRecord bedrock = chest("live-x-run-5", openedAt, "Bedrock Chest");
		DungeonRunRecord wood2 = chest("live-x-run-5", openedAt, "Wood Chest");
		DungeonRunRecord wood1 = chest("live-x-run-5", openedAt, "Wood Chest");
		wood1.commitFingerprint += "#2";
		List<DungeonRunRecord> history = List.of(bedrock, wood2, wood1);
		for (DungeonRunRecord record : history) record.runLinkSource = DungeonRunRecord.LINK_NONE;

		Map<DungeonRunRecord, RunHistoryLinks.Link> unlinked = RunHistoryLinks.resolve(history, runs);
		for (DungeonRunRecord record : history) {
			assertFalse(unlinked.get(record).linked(), "no time is borrowed from a nearby completion");
		}

		bedrock.runLinkSource = DungeonRunRecord.LINK_CROESUS_UNIQUE;
		bedrock.linkedRunId = "live-x-run-3";
		wood2.runLinkSource = DungeonRunRecord.LINK_CROESUS_UNIQUE;
		wood2.linkedRunId = "live-x-run-2";
		wood1.runLinkSource = DungeonRunRecord.LINK_CROESUS_UNIQUE;
		wood1.linkedRunId = "live-x-run-1";
		Map<DungeonRunRecord, RunHistoryLinks.Link> linked = RunHistoryLinks.resolve(history, runs);
		assertSame(run3, linked.get(bedrock).completion());
		assertSame(run2, linked.get(wood2).completion());
		assertSame(run1, linked.get(wood1).completion());
		assertEquals(138_000L, RunHistoryLinks.runTime(linked.get(bedrock).completion()).millis());
		assertEquals(136_000L, RunHistoryLinks.runTime(linked.get(wood2).completion()).millis());
		assertEquals(108_000L, RunHistoryLinks.runTime(linked.get(wood1).completion()).millis());
		assertEquals(RunHistoryLinks.TimeSource.OFFICIAL, RunHistoryLinks.runTime(linked.get(wood1).completion()).source());
	}

	@Test
	void legacyChestOpenedInTheDungeonLinksExactly() {
		DungeonRunCompletionRecord run = completion("s1", T, 220_000L, 220_000L);
		DungeonRunRecord bedrock = chest("s1", T, "Bedrock Chest");
		DungeonRunRecord obsidian = chest("s1", T, "Obsidian Chest");
		Map<DungeonRunRecord, RunHistoryLinks.Link> links = RunHistoryLinks.resolve(List.of(bedrock, obsidian), List.of(run));
		assertEquals(RunHistoryLinks.Source.LEGACY_EXACT, links.get(bedrock).source());
		assertSame(run, links.get(bedrock).completion());
		assertTrue(links.get(obsidian).linked());
	}

	@Test
	void croesusChestsPiledOntoTheLatestRunStayUnlinked() {
		DungeonRunCompletionRecord run = completion("s1", T, 220_000L, 220_000L);
		List<DungeonRunRecord> history = new ArrayList<>();
		history.add(chest("s1", T, "Bedrock Chest"));
		for (int i = 1; i <= 6; i++) history.add(chest("s1", T + i * 60_000L, "Bedrock Chest"));
		Map<DungeonRunRecord, RunHistoryLinks.Link> links = RunHistoryLinks.resolve(history, List.of(run));
		for (DungeonRunRecord record : history) {
			assertFalse(links.get(record).linked());
			assertNull(links.get(record).completion());
		}
	}

	@Test
	void legacyChestOpenedLaterIsNotTiedToTheRun() {
		DungeonRunCompletionRecord run = completion("s1", T, 220_000L, 220_000L);
		DungeonRunRecord late = chest("s1", T + 30 * 60_000L, "Bedrock Chest");
		assertFalse(RunHistoryLinks.resolve(List.of(late), List.of(run)).get(late).linked());
	}

	@Test
	void sameChestTwiceUnderOneRunIsRejected() {
		DungeonRunCompletionRecord run = completion("s1", T, 220_000L, 220_000L);
		DungeonRunRecord a = chest("s1", T, "Bedrock Chest");
		DungeonRunRecord b = chest("s1", T, "Bedrock Chest");
		Map<DungeonRunRecord, RunHistoryLinks.Link> links = RunHistoryLinks.resolve(List.of(a, b), List.of(run));
		assertFalse(links.get(a).linked());
		assertFalse(links.get(b).linked());
	}

	@Test
	void chestFromAnotherFloorIsRejected() {
		DungeonRunCompletionRecord run = completion("s1", T, 220_000L, 220_000L);
		DungeonRunRecord m5 = chest("s1", T, "Bedrock Chest");
		m5.floor = "M5";
		assertFalse(RunHistoryLinks.resolve(List.of(m5), List.of(run)).get(m5).linked());
	}

	@Test
	void storedLiveAndCroesusLinksAreUsed() {
		DungeonRunCompletionRecord first = completion("s1", T, 220_000L, 220_000L);
		DungeonRunCompletionRecord second = completion("s2", T + 600_000L, 230_000L, 230_000L);
		DungeonRunRecord live = chest("other-session", T + 5_000L, "Bedrock Chest");
		live.runLinkSource = DungeonRunRecord.LINK_LIVE;
		live.linkedRunId = "s2";
		DungeonRunRecord croesus = chest("whatever", T + 7_200_000L, "Obsidian Chest");
		croesus.runLinkSource = DungeonRunRecord.LINK_CROESUS_UNIQUE;
		croesus.linkedRunId = "s1";
		DungeonRunRecord none = chest("s1", T, "Diamond Chest");
		none.runLinkSource = DungeonRunRecord.LINK_NONE;
		Map<DungeonRunRecord, RunHistoryLinks.Link> links = RunHistoryLinks.resolve(List.of(live, croesus, none), List.of(first, second));
		assertEquals(RunHistoryLinks.Source.LIVE, links.get(live).source());
		assertSame(second, links.get(live).completion());
		assertEquals(RunHistoryLinks.Source.CROESUS_UNIQUE, links.get(croesus).source());
		assertSame(first, links.get(croesus).completion());
		assertFalse(links.get(none).linked(), "an explicit no-link is never upgraded by legacy rules");
	}

	@Test
	void linkToAMissingRunIsDropped() {
		DungeonRunRecord live = chest("x", T, "Bedrock Chest");
		live.runLinkSource = DungeonRunRecord.LINK_LIVE;
		live.linkedRunId = "gone";
		assertFalse(RunHistoryLinks.resolve(List.of(live), List.of()).get(live).linked());
	}

	@Test
	void moreThanTwoChestsPointingAtOneRunAreAllRejected() {
		DungeonRunCompletionRecord run = completion("s1", T, 220_000L, 220_000L);
		List<DungeonRunRecord> history = new ArrayList<>();
		for (String title : List.of("Bedrock Chest", "Obsidian Chest", "Emerald Chest")) {
			DungeonRunRecord record = chest("x", T, title);
			record.runLinkSource = DungeonRunRecord.LINK_CROESUS_UNIQUE;
			record.linkedRunId = "s1";
			history.add(record);
		}
		Map<DungeonRunRecord, RunHistoryLinks.Link> links = RunHistoryLinks.resolve(history, List.of(run));
		for (DungeonRunRecord record : history) assertFalse(links.get(record).linked());
	}

	@Test
	void duplicateRunSessionsAreNotTrusted() {
		DungeonRunCompletionRecord a = completion("s1", T, 220_000L, 220_000L);
		DungeonRunCompletionRecord b = completion("s1", T + 600_000L, 230_000L, 230_000L);
		DungeonRunRecord record = chest("s1", T, "Bedrock Chest");
		assertFalse(RunHistoryLinks.resolve(List.of(record), List.of(a, b)).get(record).linked());
	}

	@Test
	void manualEntryLinksToItsManualCompletion() {
		DungeonRunRecord record = chest("manual-1", T, "Bedrock Chest");
		DungeonRunCompletionRecord manual = new DungeonRunCompletionRecord(T, "DUNGEON", "F5", "S+", 300_000L, "manual-1", "manual|" + record.commitFingerprint);
		RunHistoryLinks.Link link = RunHistoryLinks.resolve(List.of(record), List.of(manual)).get(record);
		assertEquals(RunHistoryLinks.Source.MANUAL, link.source());
		assertEquals(RunHistoryLinks.TimeSource.MANUAL, RunHistoryLinks.runTime(link.completion()).source());
	}

	@Test
	void resolvingNeverChangesStoredRecords() {
		DungeonRunCompletionRecord run = completion("s1", T, 220_000L, 220_000L);
		DungeonRunRecord record = chest("s1", T + 60_000L, "Bedrock Chest");
		String before = record.runSessionId + record.chestCostCoins + record.chestProfitCoins + record.runLinkSource + record.linkedRunId;
		RunHistoryLinks.resolve(List.of(record), List.of(run));
		assertEquals(before, record.runSessionId + record.chestCostCoins + record.chestProfitCoins + record.runLinkSource + record.linkedRunId);
	}

	@Test
	void runTimeSourcesComeFromStoredValueOrFingerprint() {
		assertEquals(RunHistoryLinks.TimeSource.OFFICIAL, RunHistoryLinks.runTime(completion("s", T, 220_000L, 220_000L)).source());
		assertEquals(RunHistoryLinks.TimeSource.MEASURED, RunHistoryLinks.runTime(completion("s", T, 250_000L, 0L)).source());
		assertNull(RunHistoryLinks.runTime(completion("s", T, 250_000L, 220_000L)), "a time that matches neither source is not shown");
		assertNull(RunHistoryLinks.runTime(completion("s", T, 0L, 0L)));
		assertNull(RunHistoryLinks.runTime(null));

		DungeonRunCompletionRecord stored = new DungeonRunCompletionRecord(T, "DUNGEON", "F5", "S+", 250_000L, "s", "unparsable");
		assertNull(RunHistoryLinks.runTime(stored));
		stored.runTimeSource = DungeonRunCompletionRecord.RUN_TIME_MEASURED;
		assertEquals(RunHistoryLinks.TimeSource.MEASURED, RunHistoryLinks.runTime(stored).source());
	}

	@Test
	void runTimeFormatting() {
		assertEquals("3m 40s", RunHistoryLinks.formatRunTime(220_000L));
		assertEquals("0m 05s", RunHistoryLinks.formatRunTime(5_999L));
		assertEquals("1h 02m 05s", RunHistoryLinks.formatRunTime(3_725_000L));
	}
}
