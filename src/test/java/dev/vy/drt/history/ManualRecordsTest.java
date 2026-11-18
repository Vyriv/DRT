package dev.vy.drt.history;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.Gson;
import dev.vy.drt.config.DrtConfig;
import dev.vy.drt.config.DungeonLootEntry;
import dev.vy.drt.config.DungeonRunCompletionRecord;
import dev.vy.drt.config.DungeonRunRecord;
import dev.vy.drt.config.ManualRecord;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** The * marker for manual history entries, and that it never goes away once set. */
class ManualRecordsTest {
	private static final Gson GSON = new Gson();

	private static DungeonRunRecord chest(String id) {
		DungeonRunRecord record = new DungeonRunRecord(1_800_000_000_000L, 1, 190, "M5", "S+", "§6Gold Chest", 0L, 1_000L, 1_000L,
			List.of(new DungeonLootEntry("§fEnchanted Bone", "ENCHANTED_BONE", 1)));
		record.recordId = id;
		return record;
	}

	@Test
	void automaticRecordsHaveNoMarker() {
		DungeonRunRecord record = chest("record-auto");
		assertFalse(ManualRecords.isManual(record, null));
		assertEquals("#190 Gold", ManualRecords.mark("#190 Gold", ManualRecords.isManual(record, null)));
		assertNull(ManualRecords.tooltip(record, null));
	}

	@Test
	void createdAndEditedRecordsShowAnAsteriskWithAnExplanation() {
		DungeonRunRecord created = chest("record-created");
		created.manual = ManualRecord.CREATED;
		assertEquals("#191 Bedrock*", ManualRecords.mark("#191 Bedrock", ManualRecords.isManual(created, null)));
		assertEquals("* Manually created. Not included in online statistics.", ManualRecords.tooltip(created, null));

		DungeonRunRecord edited = chest("record-edited");
		edited.manual = ManualRecord.edited(edited.manual);
		assertEquals("#190 Gold*", ManualRecords.mark("#190 Gold", ManualRecords.isManual(edited, null)));
		assertEquals("* Manually edited. Not included in online statistics.", ManualRecords.tooltip(edited, null));
	}

	@Test
	void theMarkerSurvivesSavingReloadingCopyingAndRepricing() {
		DrtConfig config = new DrtConfig();
		DungeonRunRecord edited = chest("record-edited");
		edited.manual = ManualRecord.EDITED;
		DungeonRunCompletionRecord run = new DungeonRunCompletionRecord(1L, "DUNGEON", "M5", "S+", 100_000L, "s", "s|M5|S+|0");
		run.manual = ManualRecord.EDITED;
		config.runHistory.add(edited);
		config.runCompletions.add(run);

		DrtConfig restarted = GSON.fromJson(GSON.toJson(config), DrtConfig.class);
		DungeonRunRecord reloaded = restarted.runHistory.get(0);
		assertEquals(ManualRecord.EDITED, reloaded.manual);
		assertTrue(restarted.runCompletions.get(0).isManual());

		DungeonRunRecord repriced = reloaded.copy();
		repriced.chestValueCoins = 5_000L;
		assertEquals(ManualRecord.EDITED, repriced.manual, "automatic updates start from a copy and keep the marker");
		assertEquals(ManualRecord.EDITED, ManualRecord.merge(reloaded.manual, null), "an update without a marker never clears it");
		assertEquals(ManualRecord.CREATED, ManualRecord.edited(ManualRecord.CREATED), "editing a created record keeps it created");
		assertEquals(ManualRecord.EDITED, ManualRecord.merge(null, ManualRecord.EDITED));
	}

	@Test
	void manualRecordsSavedBeforeTheMarkerExistedAreMarkedOnLoad() {
		DungeonRunCompletionRecord legacyManualRun = new DungeonRunCompletionRecord(1L, "DUNGEON", "M5", "S", 0L, "", "manual|abc");
		DungeonRunCompletionRecord liveRun = new DungeonRunCompletionRecord(1L, "DUNGEON", "M5", "S", 100_000L, "live", "live|M5|S|0");
		DungeonRunRecord legacyManualChest = chest("record-legacy");
		legacyManualChest.commitFingerprint = "abc";
		DungeonRunRecord liveChest = chest("record-live");
		liveChest.commitFingerprint = "def";

		assertTrue(ManualRecords.markLegacy(List.of(legacyManualChest, liveChest), List.of(legacyManualRun, liveRun)));
		assertEquals(ManualRecord.CREATED, legacyManualRun.manual);
		assertEquals(ManualRecord.CREATED, legacyManualChest.manual);
		assertNull(liveRun.manual);
		assertNull(liveChest.manual);
		assertFalse(ManualRecords.markLegacy(List.of(legacyManualChest, liveChest), List.of(legacyManualRun, liveRun)), "only runs once");

		Map<DungeonRunRecord, RunHistoryLinks.Link> links = RunHistoryLinks.resolve(List.of(legacyManualChest), List.of(legacyManualRun));
		assertEquals(RunHistoryLinks.Source.MANUAL, links.get(legacyManualChest).source());
	}
}
