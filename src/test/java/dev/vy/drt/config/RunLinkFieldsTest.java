package dev.vy.drt.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.google.gson.Gson;
import java.util.ArrayList;
import org.junit.jupiter.api.Test;

/** Run links and run time sources survive copies and saves, and older files load without them. */
class RunLinkFieldsTest {
	private static final Gson GSON = new Gson();

	@Test
	void chestLinkSurvivesCopyAndJson() {
		DungeonRunRecord record = new DungeonRunRecord(1L, 1, "F5", "S+", "Bedrock Chest", 1L, 2L, 1L, new ArrayList<>());
		record.runLinkSource = DungeonRunRecord.LINK_LIVE;
		record.linkedRunId = "s1";
		DungeonRunRecord copy = record.copy();
		assertEquals(DungeonRunRecord.LINK_LIVE, copy.runLinkSource);
		assertEquals("s1", copy.linkedRunId);
		DungeonRunRecord loaded = GSON.fromJson(GSON.toJson(record), DungeonRunRecord.class);
		assertEquals(DungeonRunRecord.LINK_LIVE, loaded.runLinkSource);
		assertEquals("s1", loaded.linkedRunId);
	}

	@Test
	void olderChestRecordsLoadAsLegacy() {
		DungeonRunRecord loaded = GSON.fromJson("{\"floor\":\"F5\",\"chestTitle\":\"Bedrock Chest\",\"chestCostCoins\":5}", DungeonRunRecord.class);
		assertNull(loaded.runLinkSource);
		assertEquals(5L, loaded.chestCostCoins);
	}

	@Test
	void runTimeSourceSurvivesCopyAndJson() {
		DungeonRunCompletionRecord completion = new DungeonRunCompletionRecord(1L, "DUNGEON", "F5", "S+", 220_000L, "s1", "s1|F5|S+|220000");
		completion.runTimeSource = DungeonRunCompletionRecord.RUN_TIME_OFFICIAL;
		assertEquals(DungeonRunCompletionRecord.RUN_TIME_OFFICIAL, completion.copy().runTimeSource);
		DungeonRunCompletionRecord loaded = GSON.fromJson(GSON.toJson(completion), DungeonRunCompletionRecord.class);
		assertEquals(DungeonRunCompletionRecord.RUN_TIME_OFFICIAL, loaded.runTimeSource);

		DungeonRunCompletionRecord legacy = GSON.fromJson("{\"runTimeMs\":250000,\"completionFingerprint\":\"s1|F5|S+|0\"}", DungeonRunCompletionRecord.class);
		assertNull(legacy.runTimeSource);
		assertEquals(DungeonRunCompletionRecord.RUN_TIME_MEASURED, legacy.effectiveRunTimeSource());
	}
}
