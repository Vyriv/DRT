package dev.vy.drt.sync;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import dev.vy.drt.config.DungeonLootEntry;
import dev.vy.drt.config.DungeonRunCompletionRecord;
import dev.vy.drt.config.DungeonRunRecord;
import dev.vy.drt.config.ManualRecord;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Only the player's own DRT-recorded records are uploaded, never manual ones, and nothing twice. */
class StatsUploadPlannerTest {
	private static final String ME = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa";
	private static final String ALT = "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb";
	private static final long SINCE = 1_800_000_000_000L;

	private static DungeonRunCompletionRecord run(String id, String uuid, long at) {
		DungeonRunCompletionRecord record = new DungeonRunCompletionRecord(at, "DUNGEON", "M5", "S+", 215_000L, "session-" + id, "session-" + id + "|M5|S+|215000");
		record.completionId = id;
		record.playerUuid = uuid;
		return record;
	}

	private static DungeonRunRecord chest(String id, String uuid, long at, String session) {
		DungeonRunRecord record = new DungeonRunRecord(at, 1, "M5", "S+", "§6Bedrock Chest", 2_000_000L, 5_000_000L, 3_000_000L,
			List.of(new DungeonLootEntry("§dShadow Fury", "SHADOW_FURY", 1), new DungeonLootEntry("§dWither Essence x20", "", 20)));
		record.recordId = id;
		record.playerUuid = uuid;
		record.runLinkSource = DungeonRunRecord.LINK_LIVE;
		record.linkedRunId = session;
		return record;
	}

	private static List<String> ids(List<StatsUploadPlanner.Upload> uploads) {
		return uploads.stream().map(StatsUploadPlanner.Upload::id).toList();
	}

	@Test
	void onlyThisAccountsLiveRecordsAreSentUntilHistoryIsExplicitlyEnabled() {
		List<DungeonRunCompletionRecord> runs = List.of(
			run("completion-old", ME, SINCE - 1),
			run("completion-new", ME, SINCE + 10),
			run("completion-alt", ALT, SINCE + 20),
			run("completion-unstamped", null, SINCE + 30));
		List<DungeonRunRecord> chests = List.of(chest("record-new", ME, SINCE + 15, "session-completion-new"));
		StatsUploadPlanner.Scope scope = new StatsUploadPlanner.Scope(ME, SINCE, false, false);
		List<StatsUploadPlanner.Upload> pending = StatsUploadPlanner.pending(scope, runs, chests, Map.of());
		assertEquals(List.of("completion-new", "record-new"), ids(pending));
		assertFalse(pending.get(0).historical());
		List<StatsUploadPlanner.Upload> imported = StatsUploadPlanner.pending(new StatsUploadPlanner.Scope(ME, SINCE, false, true), runs, chests, Map.of());
		assertEquals(List.of("completion-new", "record-new", "completion-old"), ids(imported), "history follows live records after opt-in");
		assertTrue(imported.get(2).historical());
		assertTrue(imported.get(2).json().get("historical").getAsBoolean());
	}

	@Test
	void recordsSavedWithoutAnAccountOnlyGoToTheAccountThatClaimedThem() {
		List<DungeonRunCompletionRecord> runs = List.of(
			run("completion-old", ME, SINCE - 1),
			run("completion-alt", ALT, SINCE - 5),
			run("completion-unstamped", null, SINCE - 30));
		List<StatsUploadPlanner.Upload> owner = StatsUploadPlanner.pending(new StatsUploadPlanner.Scope(ME, SINCE, true, true), runs, List.of(), Map.of());
		assertEquals(List.of("completion-unstamped", "completion-old"), ids(owner));

		List<StatsUploadPlanner.Upload> other = StatsUploadPlanner.pending(new StatsUploadPlanner.Scope(ME, SINCE, false, true), runs, List.of(), Map.of());
		assertEquals(List.of("completion-old"), ids(other), "unclaimed records saved without an account stay local");

		List<StatsUploadPlanner.Upload> alt = StatsUploadPlanner.pending(new StatsUploadPlanner.Scope(ALT, SINCE, false, true), runs, List.of(), Map.of());
		assertEquals(List.of("completion-alt"), ids(alt), "switching accounts only uploads that account's own records");
	}

	@Test
	void manuallyCreatedRecordsAreNeverSentEvenAsHistory() {
		DungeonRunCompletionRecord created = run("completion-manual", ME, SINCE - 100);
		created.manual = ManualRecord.CREATED;
		DungeonRunCompletionRecord legacyManual = new DungeonRunCompletionRecord(SINCE - 50, "DUNGEON", "M5", "S", 0L, "", "manual|abc");
		legacyManual.completionId = "completion-legacy-manual";
		legacyManual.playerUuid = ME;
		DungeonRunRecord createdChest = chest("record-manual", ME, SINCE - 100, "none");
		createdChest.manual = ManualRecord.CREATED;
		DungeonRunRecord legacyManualChest = chest("record-legacy-manual", null, SINCE - 50, "none");
		legacyManualChest.runLinkSource = null;
		legacyManualChest.commitFingerprint = "abc";
		DungeonRunRecord genuine = chest("record-genuine", ME, SINCE - 10, "none");
		for (boolean ownsUnstamped : new boolean[] {false, true}) {
			List<StatsUploadPlanner.Upload> pending = StatsUploadPlanner.pending(new StatsUploadPlanner.Scope(ME, SINCE, ownsUnstamped, true),
				List.of(created, legacyManual), List.of(createdChest, legacyManualChest, genuine), Map.of());
			assertEquals(List.of("record-genuine"), ids(pending));
		}
	}

	@Test
	void editingARecordedRunMakesItIneligibleAndRetractsTheUploadedCopy() {
		DungeonRunCompletionRecord completion = run("completion-1", ME, SINCE + 1);
		DungeonRunRecord edited = chest("record-1", ME, SINCE + 1, "session-completion-1");
		DungeonRunRecord sibling = chest("record-2", ME, SINCE + 2, "session-completion-1");
		sibling.chestTitle = "§5Obsidian Chest";
		StatsUploadPlanner.Scope scope = new StatsUploadPlanner.Scope(ME, SINCE, false, true);
		List<StatsUploadPlanner.Upload> before = StatsUploadPlanner.pending(scope, List.of(completion), List.of(edited, sibling), Map.of());
		assertEquals(List.of("completion-1", "record-1", "record-2"), ids(before));
		Map<String, String> uploaded = new HashMap<>();
		before.forEach(upload -> uploaded.put(upload.id(), upload.revision()));
		assertTrue(StatsUploadPlanner.retractions(List.of(completion), List.of(edited, sibling), uploaded).isEmpty());

		edited.manual = ManualRecord.edited(edited.manual);
		completion.manual = ManualRecord.edited(completion.manual);
		edited.chestValueCoins = 99_000_000L;
		List<StatsUploadPlanner.Upload> after = StatsUploadPlanner.pending(scope, List.of(completion), List.of(edited, sibling), uploaded);
		assertEquals(List.of("record-2"), ids(after), "only the untouched sibling changes, losing its link to the edited run");
		assertFalse(after.get(0).json().has("runId"));
		assertEquals(List.of("completion-1", "record-1"), StatsUploadPlanner.retractions(List.of(completion), List.of(edited, sibling), uploaded));
		assertTrue(StatsUploadPlanner.retractions(List.of(completion), List.of(edited, sibling), Map.of()).isEmpty(),
			"records never uploaded need no retraction");
	}

	@Test
	void locallyDeletedRunsAndChestsAreRetractedWithoutTouchingRemainingRecords() {
		DungeonRunCompletionRecord keptRun = run("completion-kept", ME, SINCE + 1);
		DungeonRunRecord keptChest = chest("record-kept", ME, SINCE + 2, "session-completion-kept");
		Map<String, String> uploaded = Map.of(
			"completion-kept", "rev-1",
			"record-kept", "rev-2",
			"completion-deleted", "rev-3",
			"record-deleted", "rev-4");
		assertEquals(List.of("completion-deleted", "record-deleted"),
			StatsUploadPlanner.retractions(List.of(keptRun), List.of(keptChest), uploaded));
	}

	@Test
	void multipleLinkedChestsAndNegativeProfitRemainExact() {
		DungeonRunCompletionRecord completion = run("completion-1", ME, SINCE + 1);
		DungeonRunRecord first = chest("record-1", ME, SINCE + 2, "session-completion-1");
		DungeonRunRecord second = chest("record-2", ME, SINCE + 3, "session-completion-1");
		second.chestTitle = "§5Obsidian Chest";
		second.chestProfitCoins = -2_500_000L;
		List<StatsUploadPlanner.Upload> uploads = StatsUploadPlanner.pending(
			new StatsUploadPlanner.Scope(ME, SINCE, false, false), List.of(completion), List.of(first, second), Map.of());
		assertEquals(List.of("completion-1", "record-1", "record-2"), ids(uploads));
		assertEquals("completion-1", uploads.get(1).json().get("runId").getAsString());
		assertEquals("completion-1", uploads.get(2).json().get("runId").getAsString());
		assertEquals(-2_500_000L, uploads.get(2).json().get("profit").getAsLong());
	}

	@Test
	void malformedFinancialAndQuantityValuesAreNotSilentlyChanged() {
		DungeonRunRecord record = chest("record-1", ME, SINCE + 1, "none");
		record.baseChestCostCoins = -1L;
		record.chestValueCoins = -2L;
		record.chestProfitCoins = -3L;
		record.lootEntries.get(0).quantity = 0;
		JsonObject json = StatsUploadPlanner.chestJson(record, null);
		assertEquals(-1L, json.getAsJsonObject("costs").get("base").getAsLong());
		assertEquals(-2L, json.get("value").getAsLong());
		assertEquals(-3L, json.get("profit").getAsLong());
		assertEquals(0, json.getAsJsonArray("items").get(0).getAsJsonObject().get("qty").getAsInt());
	}

	@Test
	void uploadedRevisionsAreSkippedUntilTheRecordChanges() {
		DungeonRunRecord record = chest("record-1", ME, SINCE + 1, "none");
		StatsUploadPlanner.Scope scope = new StatsUploadPlanner.Scope(ME, SINCE, false, true);
		StatsUploadPlanner.Upload first = StatsUploadPlanner.pending(scope, List.of(), List.of(record), Map.of()).get(0);
		Map<String, String> uploaded = new HashMap<>(Map.of(first.id(), first.revision()));
		assertTrue(StatsUploadPlanner.pending(scope, List.of(), List.of(record), uploaded).isEmpty());
		record.chestValueCoins = 6_000_000L;
		StatsUploadPlanner.Upload repriced = StatsUploadPlanner.pending(scope, List.of(), List.of(record), uploaded).get(0);
		assertNotEquals(first.revision(), repriced.revision());
		assertEquals(16, repriced.revision().length());
	}

	@Test
	void payloadsCarryStatsButNoFormatting() {
		DungeonRunCompletionRecord completion = run("completion-1", ME, SINCE + 1);
		DungeonRunRecord record = chest("record-1", ME, SINCE + 2, "session-completion-1");
		List<StatsUploadPlanner.Upload> pending = StatsUploadPlanner.pending(new StatsUploadPlanner.Scope(ME, SINCE, false, true),
			List.of(completion), List.of(record), Map.of());
		JsonObject run = pending.get(0).json();
		assertEquals("M5", run.get("floor").getAsString());
		assertEquals("S+", run.get("grade").getAsString());
		assertEquals(215_000L, run.get("durationMs").getAsLong());
		assertEquals("official", run.get("timeSource").getAsString());
		JsonObject chest = pending.get(1).json();
		assertEquals("Bedrock Chest", chest.get("chest").getAsString());
		assertEquals("completion-1", chest.get("runId").getAsString(), "linked chests point at the completion id");
		assertEquals(2_000_000L, chest.getAsJsonObject("costs").get("base").getAsLong());
		assertEquals("SHADOW_FURY", chest.getAsJsonArray("items").get(0).getAsJsonObject().get("id").getAsString());
		assertEquals("Wither Essence x20", chest.getAsJsonArray("items").get(1).getAsJsonObject().get("name").getAsString());
		for (String key : List.of("deaths", "secrets", "rooms")) {
			assertFalse(run.has(key) || chest.has(key), key);
		}
	}

	@Test
	void implausibleTimesKeepTheRunButSendNoDuration() {
		DungeonRunCompletionRecord bugged = new DungeonRunCompletionRecord(SINCE + 1, "KUUDRA", "K5", "100%", 1L, "live-1", "live-1|K5|100%|0");
		bugged.completionId = "completion-k5";
		bugged.playerUuid = ME;
		bugged.runTimeSource = DungeonRunCompletionRecord.RUN_TIME_MEASURED;
		List<StatsUploadPlanner.Upload> pending = StatsUploadPlanner.pending(new StatsUploadPlanner.Scope(ME, SINCE, false, true),
			List.of(bugged), List.of(), Map.of());
		assertEquals(List.of("completion-k5"), ids(pending), "the run itself still uploads");
		assertFalse(pending.get(0).json().has("durationMs"));
		assertFalse(pending.get(0).json().has("timeSource"));
	}

	@Test
	void unknownFloorsAndBlankIdsAreNeverSent() {
		DungeonRunCompletionRecord unknown = run("completion-1", ME, SINCE + 1);
		unknown.floor = "UNKNOWN";
		DungeonRunCompletionRecord blank = run("", ME, SINCE + 1);
		assertTrue(StatsUploadPlanner.pending(new StatsUploadPlanner.Scope(ME, SINCE, false, true), List.of(unknown, blank), List.of(), Map.of()).isEmpty());
	}
}
