package dev.vy.drt.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import dev.vy.drt.history.RunOwnership;
import org.junit.jupiter.api.Test;

class PersistedRunOwnershipMigrationTest {
	@Test
	void chest193StyleRecordLinksAndMarksOnlyPurchasedOffer() {
		long completedAt = 1_793_877_999_432L;
		String runId = "live-99d1bc28-run-1";
		DungeonRunCompletionRecord completion = new DungeonRunCompletionRecord(
			completedAt, "DUNGEON", "M1", "S", 198_000L, runId, "run|" + runId);
		completion.completionId = "completion-193";
		for (String title : List.of("Wood Chest", "Gold Chest", "Diamond Chest", "Emerald Chest", "Obsidian Chest")) {
			completion.chestOffers.add(new DungeonChestOffer(title, new ChestCostBreakdown(), 1L,
				List.of(new DungeonLootEntry("Reward", "REWARD", 1))));
		}

		DungeonRunRecord chest = new DungeonRunRecord(completedAt + 13_716L, 198, "M1", "S", "Obsidian Chest",
			800_000L, 1_212_540L, 412_540L, List.of(new DungeonLootEntry("Master Skull - Tier 1", "MASTER_SKULL_TIER_1", 1)));
		chest.runSessionId = "legacy-run-record-193";
		chest.chestSessionId = "live-99d1bc28-chest-1";
		chest.runLinkSource = DungeonRunRecord.LINK_NONE;
		chest.linkedRunId = "";

		DrtConfig config = new DrtConfig();
		config.runCompletions.add(completion);
		config.runHistory.add(chest);

		assertTrue(RunOwnership.recoverPersistedChestOwners(config, 30L * 60L * 1000L));
		assertEquals(runId, chest.runSessionId);
		assertEquals(runId, chest.linkedRunId);
		assertEquals(DungeonRunRecord.LINK_RECOVERED, chest.runLinkSource);
		assertTrue(completion.chestOffers.stream().filter(offer -> offer.chestTitle.equals("Obsidian Chest")).findFirst().orElseThrow().purchased);
		assertEquals(4, completion.chestOffers.stream().filter(offer -> !offer.purchased).count());
	}
}
