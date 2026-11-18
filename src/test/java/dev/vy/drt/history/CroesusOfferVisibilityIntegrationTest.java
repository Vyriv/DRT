package dev.vy.drt.history;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.Gson;
import dev.vy.drt.config.ChestCostBreakdown;
import dev.vy.drt.config.ChestOfferPersistence;
import dev.vy.drt.config.DrtConfig;
import dev.vy.drt.config.DungeonChestOffer;
import dev.vy.drt.config.DungeonLootEntry;
import dev.vy.drt.config.DungeonRunCompletionRecord;
import dev.vy.drt.config.DungeonRunRecord;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class CroesusOfferVisibilityIntegrationTest {
	private static final Gson GSON = new Gson();

	@Test
	void serverChestSlotsPersistRestartAndRenderBesideTheirPurchasedRun() {
		String runId = "live-f9f00cbf-run-1";
		long completedAt = 1_793_879_591_929L;
		DungeonRunCompletionRecord completion = new DungeonRunCompletionRecord(
			completedAt, "DUNGEON", "M1", "S", 131_000L, runId, "run|" + runId);
		completion.completionId = "completion-194";
		completion.playerUuid = "player-uuid";
		completion.score = 275;

		DrtConfig config = new DrtConfig();
		config.runCompletions.add(completion);
		CroesusRunSelection.Evidence selectedEntry = CroesusRunSelection.parse(
			"Master Mode Catacombs - Floor I", List.of("Score: 275 (S)", "Opened Chest: Emerald"),
			completedAt + 10_000L, ZoneId.of("UTC"));
		RunOwnership.Resolution owner = RunOwnership.resolveCroesus(
			selectedEntry, config.runCompletions, completedAt + 10_000L, "playeruuid");
		assertTrue(owner.matched());
		assertEquals(runId, owner.owner().runSessionId());
		Map<String, List<String>> serverChestSlots = new LinkedHashMap<>();
		serverChestSlots.put("WOOD CHEST", List.of("Contents", "Wither Essence x10", "Cost: FREE"));
		serverChestSlots.put("GOLD CHEST", List.of("Contents", "Hot Potato Book x2", "Cost: 80,000 Coins"));
		serverChestSlots.put("DIAMOND CHEST", List.of("Contents", "Undead Essence x30", "Cost: 200,000 Coins"));
		serverChestSlots.put("EMERALD CHEST", List.of("Contents", "Bonzo Shard", "Cost: 400,000 Coins", "Already Opened"));
		serverChestSlots.put("OBSIDIAN CHEST", List.of("Contents", "Recombobulator 3000", "Cost: 800,000 Coins"));

		for (Map.Entry<String, CroesusChestLore.Offer> parsed : CroesusChestLore.parseAll(serverChestSlots).entrySet()) {
			List<DungeonLootEntry> loot = parsed.getValue().rewards().stream()
				.map(reward -> new DungeonLootEntry(reward.name(), reward.name().toUpperCase().replace(' ', '_'), reward.quantity()))
				.toList();
			DungeonChestOffer offer = new DungeonChestOffer(title(parsed.getKey()),
				new ChestCostBreakdown(parsed.getValue().costCoins()), 1_000_000L, loot);
			offer.observedAtEpochMillis = completedAt + 11_000L;
			offer.purchased = parsed.getValue().alreadyOpened();
			assertTrue(ChestOfferPersistence.record(config, owner.owner().runSessionId(), offer));
		}

		DungeonRunRecord purchased = new DungeonRunRecord(completedAt + 11_109L, 146, 194, "M1", "S",
			"Emerald Chest", 400_000L, 749_733L, 349_733L,
			List.of(new DungeonLootEntry("Bonzo Shard", "BONZO_SHARD", 1)));
		purchased.runSessionId = runId;
		purchased.linkedRunId = runId;
		purchased.runLinkSource = DungeonRunRecord.LINK_CROESUS_UNIQUE;
		config.runHistory.add(purchased);
		assertFalse(ChestOfferPersistence.markPurchased(config, runId, "Emerald Chest"));

		DrtConfig restarted = GSON.fromJson(GSON.toJson(config), DrtConfig.class);
		List<DungeonRunRecord> older = new ArrayList<>();
		for (int number = 1; number < 194; number++) {
			older.add(new DungeonRunRecord(number, number, number, "M1", "S", "Wood Chest", 0L, 1L, 1L,
				List.of(new DungeonLootEntry("Wither Essence", "ESSENCE_WITHER", 1))));
		}
		older.add(restarted.runHistory.getFirst());
		List<LootHistoryProjection.RunEntry> visible = LootHistoryProjection.projectRuns(older, restarted.runCompletions);

		assertEquals(194, visible.size());
		LootHistoryProjection.RunEntry run194 = visible.getFirst();
		assertEquals(runId, run194.completion().runSessionId);
		assertEquals("Emerald Chest", run194.representative().chestTitle);
		assertEquals(5, run194.chestOptions().size());
		assertEquals("Emerald Chest", run194.defaultOption().chestTitle());
		assertEquals(1, run194.chestOptions().stream().filter(LootHistoryProjection.ChestOption::purchased).count());
		assertEquals(4, run194.chestOptions().stream().filter(option -> !option.purchased()).count());
	}

	@Test
	void chest193And194EachProjectToOneRunWithFiveTabs() {
		DungeonRunCompletionRecord run193 = capturedCompletion("run-193", "completion-193", 1_000L, "Obsidian Chest");
		DungeonRunCompletionRecord run194 = capturedCompletion("run-194", "completion-194", 2_000L, "Emerald Chest");
		DungeonRunRecord chest193 = purchasedRecord("run-193", 193, "Obsidian Chest", 1_100L);
		DungeonRunRecord chest194 = purchasedRecord("run-194", 194, "Emerald Chest", 2_100L);

		List<LootHistoryProjection.RunEntry> runs = LootHistoryProjection.projectRuns(
			List.of(chest193, chest194), List.of(run193, run194));

		assertEquals(2, runs.size());
		assertEquals(194, runs.get(0).representative().chestNumber);
		assertEquals(193, runs.get(1).representative().chestNumber);
		for (LootHistoryProjection.RunEntry run : runs) {
			assertEquals(1, run.realisedRecords().size());
			assertEquals(5, run.chestOptions().size());
			assertEquals(1, run.chestOptions().stream().filter(LootHistoryProjection.ChestOption::purchased).count());
			assertEquals(run.representative().chestTitle, run.defaultOption().chestTitle());
		}
	}

	private static DungeonRunCompletionRecord capturedCompletion(String runId, String completionId, long time, String purchasedTitle) {
		DungeonRunCompletionRecord completion = new DungeonRunCompletionRecord(time, "DUNGEON", "M1", "S", 180_000L,
			runId, "run|" + runId);
		completion.completionId = completionId;
		for (String chest : List.of("Wood Chest", "Gold Chest", "Diamond Chest", "Emerald Chest", "Obsidian Chest")) {
			DungeonChestOffer offer = new DungeonChestOffer(chest, new ChestCostBreakdown(100_000L), 200_000L,
				List.of(new DungeonLootEntry(chest + " Item", chest.toUpperCase().replace(' ', '_'), 1)));
			offer.purchased = chest.equals(purchasedTitle);
			completion.chestOffers.add(offer);
		}
		return completion;
	}

	private static DungeonRunRecord purchasedRecord(String runId, int number, String chest, long time) {
		DungeonRunRecord record = new DungeonRunRecord(time, number, number, "M1", "S", chest, 100_000L, 200_000L,
			100_000L, List.of(new DungeonLootEntry(chest + " Item", chest.toUpperCase().replace(' ', '_'), 1)));
		record.runSessionId = runId;
		record.linkedRunId = runId;
		record.runLinkSource = DungeonRunRecord.LINK_CROESUS_UNIQUE;
		return record;
	}

	private static String title(String canonical) {
		String lower = canonical.toLowerCase(java.util.Locale.ROOT);
		return Character.toUpperCase(lower.charAt(0)) + lower.substring(1);
	}
}
