package dev.vy.drt.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.List;

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

	@Test
	void observedChestOffersSurviveCopyJsonAndLegacyLoad() {
		DungeonRunCompletionRecord completion = new DungeonRunCompletionRecord(1L, "DUNGEON", "F7", "S+", 220_000L, "s1", "s1|F7|S+|220000");
		DungeonChestOffer offer = new DungeonChestOffer("Bedrock Chest", new ChestCostBreakdown(10_000_000L), 12_000_000L,
			List.of(new DungeonLootEntry("Recombobulator 3000", "RECOMBOBULATOR_3000", 2)));
		offer.observedAtEpochMillis = 123L;
		completion.chestOffers.add(offer);

		DungeonRunCompletionRecord copy = completion.copy();
		assertEquals("RECOMBOBULATOR_3000", copy.chestOffers.get(0).lootEntries.get(0).itemId);
		assertEquals(2, copy.chestOffers.get(0).lootEntries.get(0).quantity);
		DungeonRunCompletionRecord loaded = GSON.fromJson(GSON.toJson(completion), DungeonRunCompletionRecord.class);
		loaded.normalize();
		assertEquals(123L, loaded.chestOffers.get(0).observedAtEpochMillis);

		DungeonRunCompletionRecord legacy = GSON.fromJson("{\"runSessionId\":\"old\"}", DungeonRunCompletionRecord.class);
		legacy.normalize();
		assertEquals(0, legacy.chestOffers.size());
	}
}
