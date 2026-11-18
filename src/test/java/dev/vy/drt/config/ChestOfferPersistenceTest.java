package dev.vy.drt.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.Gson;
import java.util.List;
import org.junit.jupiter.api.Test;

class ChestOfferPersistenceTest {
	private static final Gson GSON = new Gson();

	@Test
	void offerBeforeCompletionIsRetainedAndAttached() {
		DrtConfig config = new DrtConfig();
		assertTrue(ChestOfferPersistence.record(config, "run-1", offer("Wood Chest", "HOT_POTATO_BOOK")));
		assertEquals(1, config.pendingChestOffers.get("run-1").size());

		DungeonRunCompletionRecord completion = completion("run-1");
		assertTrue(ChestOfferPersistence.attachPending(config, completion));
		config.runCompletions.add(completion);
		assertFalse(config.pendingChestOffers.containsKey("run-1"));
		assertEquals("HOT_POTATO_BOOK", completion.chestOffers.get(0).lootEntries.get(0).itemId);
	}

	@Test
	void completionBeforeOfferDeduplicatesAndMarksPurchase() {
		DrtConfig config = new DrtConfig();
		DungeonRunCompletionRecord completion = completion("run-1");
		config.runCompletions.add(completion);
		assertTrue(ChestOfferPersistence.record(config, "run-1", offer("Emerald Chest", "RECOMBOBULATOR_3000")));
		assertFalse(ChestOfferPersistence.record(config, "run-1", offer("Emerald Chest", "RECOMBOBULATOR_3000")));
		assertEquals(1, completion.chestOffers.size());
		assertTrue(ChestOfferPersistence.markPurchased(config, "run-1", "Emerald Chest"));
		assertTrue(completion.chestOffers.get(0).purchased);
	}

	@Test
	void pendingAndAttachedOffersSurviveJsonRoundTrips() {
		DrtConfig config = new DrtConfig();
		ChestOfferPersistence.record(config, "run-late", offer("Gold Chest", "FUMING_POTATO_BOOK"));
		DrtConfig pendingLoaded = GSON.fromJson(GSON.toJson(config), DrtConfig.class);
		assertEquals(1, pendingLoaded.pendingChestOffers.get("run-late").size());

		DungeonRunCompletionRecord completion = completion("run-late");
		ChestOfferPersistence.attachPending(pendingLoaded, completion);
		pendingLoaded.runCompletions.add(completion);
		DrtConfig attachedLoaded = GSON.fromJson(GSON.toJson(pendingLoaded), DrtConfig.class);
		attachedLoaded.runCompletions.get(0).normalize();
		assertEquals("FUMING_POTATO_BOOK", attachedLoaded.runCompletions.get(0).chestOffers.get(0).lootEntries.get(0).itemId);
	}

	@Test
	void observedOffersDoNotCreateRealisedHistoryOrAccounting() {
		DrtConfig config = new DrtConfig();
		DungeonRunCompletionRecord completion = completion("run-1");
		config.runCompletions.add(completion);
		config.runHistory.add(new DungeonRunRecord(1_000L, 1, "M1", "S", "Emerald Chest", 1_500L, 2_000L, 500L,
			List.of(new DungeonLootEntry("Recombobulator 3000", "RECOMBOBULATOR_3000", 1))));

		assertTrue(ChestOfferPersistence.record(config, "run-1", offer("Wood Chest", "ESSENCE_WITHER")));
		assertTrue(ChestOfferPersistence.record(config, "run-1", offer("Gold Chest", "ENCHANTMENT_ULTIMATE_ONE_FOR_ALL_1")));

		assertEquals(1, config.runHistory.size());
		assertEquals(1_500L, config.runHistory.getFirst().chestCostCoins);
		assertEquals(2_000L, config.runHistory.getFirst().chestValueCoins);
		assertEquals(500L, config.runHistory.getFirst().chestProfitCoins);
		assertFalse(completion.chestOffers.getFirst().purchased);
		assertTrue(ChestOfferPersistence.markPurchased(config, "run-1", "Wood Chest"));
		assertTrue(completion.chestOffers.getFirst().purchased);
		assertEquals(1, config.runHistory.size());
	}

	private static DungeonRunCompletionRecord completion(String session) {
		return new DungeonRunCompletionRecord(1_000L, "DUNGEON", "M1", "S", 178_000L, session, session + "|M1|S|178000");
	}

	private static DungeonChestOffer offer(String title, String itemId) {
		DungeonChestOffer offer = new DungeonChestOffer(title, new ChestCostBreakdown(1_000L), 2_000L,
			List.of(new DungeonLootEntry(itemId, itemId, 1)));
		offer.observedAtEpochMillis = 2_000L;
		return offer;
	}
}
