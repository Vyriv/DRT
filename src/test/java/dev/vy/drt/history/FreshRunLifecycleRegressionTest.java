package dev.vy.drt.history;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.Gson;
import dev.vy.drt.config.ChestCostBreakdown;
import dev.vy.drt.config.ChestOfferPersistence;
import dev.vy.drt.config.CompletionDurationEvidence;
import dev.vy.drt.config.DrtConfig;
import dev.vy.drt.config.DungeonChestOffer;
import dev.vy.drt.config.DungeonLootEntry;
import dev.vy.drt.config.DungeonRunCompletionRecord;
import dev.vy.drt.config.DungeonRunRecord;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class FreshRunLifecycleRegressionTest {
	private static final Gson GSON = new Gson();
	private static final long COMPLETED_AT = 1_793_877_999_432L;
	private static final String RUN_ID = "live-99d1bc28-run-1";
	private static final String PLAYER = "e8a20d35b48b4fa1bd924df9049ae76f";

	@Test
	void freshM1TransferCroesusFiveOffersAndObsidianPurchaseShareOneOwner() {
		DrtConfig config = new DrtConfig();
		DungeonRunCompletionRecord completion = completion(199_000L);
		completion.runTimeEvidence = DungeonRunCompletionRecord.TIME_EVIDENCE_SINGLE_LINE;
		config.runCompletions.add(completion);

		CompletionDurationEvidence.Update durationUpdate = CompletionDurationEvidence.apply(
			completion,
			198_000L,
			DungeonRunCompletionRecord.TIME_EVIDENCE_MULTILINE
		);
		assertTrue(durationUpdate.changed());
		assertEquals(198_000L, completion.runTimeMs);

		CroesusRunSelection.Evidence floorOnly = CroesusRunSelection.parse(
			"Master Mode Catacombs - Floor I",
			List.of("Score: 272 (S)", "Opened Chest: Obsidian"),
			COMPLETED_AT + 10_000L,
			ZoneId.of("UTC")
		);
		assertEquals(CroesusRunSelection.Status.INSUFFICIENT_EVIDENCE,
			CroesusRunSelection.resolve(floorOnly, config.runCompletions).status());

		RunOwnership.Resolution selected = RunOwnership.resolveCroesus(
			floorOnly,
			config.runCompletions,
			COMPLETED_AT + 10_000L,
			PLAYER
		);
		assertTrue(selected.matched());
		assertEquals(RUN_ID, selected.owner().runSessionId());
		assertEquals(RunOwnership.Source.CROESUS_FRESH, selected.owner().source());
		assertEquals(272, completion.score);

		for (String chest : List.of("Wood Chest", "Gold Chest", "Diamond Chest", "Emerald Chest", "Obsidian Chest")) {
			assertTrue(ChestOfferPersistence.record(config, selected.owner().runSessionId(), offer(chest)));
			assertFalse(ChestOfferPersistence.record(config, selected.owner().runSessionId(), offer(chest)));
		}
		assertEquals(5, completion.chestOffers.size());

		DungeonRunRecord purchase = purchase();
		config.runHistory.add(purchase);
		assertTrue(ChestOfferPersistence.markPurchased(config, RUN_ID, "Obsidian Chest"));
		assertEquals(4, completion.chestOffers.stream().filter(offer -> !offer.purchased).count());
		assertEquals(1, config.runHistory.size());
		assertEquals(800_000L, config.runHistory.getFirst().chestCostCoins);
		assertEquals(412_540L, config.runHistory.getFirst().chestProfitCoins);

		Map<DungeonRunRecord, RunHistoryLinks.Link> links = RunHistoryLinks.resolve(config.runHistory, config.runCompletions);
		assertEquals(RUN_ID, links.get(purchase).runId());
		assertEquals(198_000L, RunHistoryLinks.runTime(links.get(purchase).completion()).millis());

		DrtConfig restarted = GSON.fromJson(GSON.toJson(config), DrtConfig.class);
		restarted.runCompletions.getFirst().normalize();
		assertEquals(1, restarted.runCompletions.size());
		assertEquals(1, restarted.runHistory.size());
		assertEquals(5, restarted.runCompletions.getFirst().chestOffers.size());
		assertEquals(4, restarted.runCompletions.getFirst().chestOffers.stream().filter(offer -> !offer.purchased).count());
		assertEquals(198_000L, restarted.runCompletions.getFirst().runTimeMs);
		assertEquals(DungeonRunCompletionRecord.TIME_EVIDENCE_MULTILINE,
			restarted.runCompletions.getFirst().runTimeEvidence);
	}

	@Test
	void contradictingCroesusTimeDoesNotAttachToTheOnlyFreshRun() {
		DungeonRunCompletionRecord completion = completion(198_000L);
		CroesusRunSelection.Evidence older = CroesusRunSelection.parse(
			"Master Mode Catacombs - Floor I",
			List.of("Completed: 10 minutes ago", "Grade: S", "Score: 272 (S)"),
			COMPLETED_AT + 10_000L,
			ZoneId.of("UTC")
		);

		RunOwnership.Resolution selected = RunOwnership.resolveCroesus(
			older, List.of(completion), COMPLETED_AT + 10_000L, PLAYER);

		assertFalse(selected.matched());
		assertEquals(CroesusRunSelection.Status.NO_MATCH, selected.status());
	}

	@Test
	void freshGuessIsOnlyAcceptedForTheNewestVisibleRow() {
		DungeonRunCompletionRecord completion = completion(198_000L);
		CroesusRunSelection.Evidence floorOnly = CroesusRunSelection.parse(
			"Master Mode Catacombs - Floor I",
			List.of("Score: 272 (S)"),
			COMPLETED_AT + 10_000L,
			ZoneId.of("UTC")
		);
		RunOwnership.Resolution fresh = RunOwnership.resolveCroesus(
			floorOnly, List.of(completion), COMPLETED_AT + 10_000L, PLAYER);
		assertTrue(fresh.matched());

		RunOwnership.Resolution newest = RunOwnership.finishCroesusClick(fresh, null, true);
		RunOwnership.Resolution olderRow = RunOwnership.finishCroesusClick(fresh, null, false);

		assertTrue(newest.matched());
		assertEquals(RUN_ID, newest.owner().runSessionId());
		assertFalse(olderRow.matched());
	}

	@Test
	void ambiguousFreshRunsAndHistoricalRunsAreNeverGuessed() {
		DungeonRunCompletionRecord first = completion(198_000L);
		DungeonRunCompletionRecord second = completion(201_000L);
		second.runSessionId = "live-other-run-1";
		second.completionId = "completion-other";
		second.completedAtEpochMillis = COMPLETED_AT - 30_000L;
		CroesusRunSelection.Evidence floorOnly = CroesusRunSelection.parse(
			"Master Mode Catacombs - Floor I", List.of("Score: 272 (S)"), COMPLETED_AT + 10_000L, ZoneId.of("UTC"));

		RunOwnership.Resolution result = RunOwnership.resolveCroesus(
			floorOnly, List.of(first, second), COMPLETED_AT + 10_000L, PLAYER);

		assertFalse(result.matched());
		assertEquals(CroesusRunSelection.Status.AMBIGUOUS, result.status());
	}

	@Test
	void restartMigrationRecoversExactTrackingInstanceWithoutNewestRunGuess() {
		DungeonRunCompletionRecord completion = completion(198_000L);
		DungeonRunRecord unlinked = purchase();
		unlinked.runSessionId = "legacy-run-record-193";
		unlinked.chestSessionId = "live-99d1bc28-chest-1";
		unlinked.linkedRunId = "";
		unlinked.runLinkSource = DungeonRunRecord.LINK_NONE;

		RunOwnership.Owner owner = RunOwnership.recoverChestOwner(unlinked, List.of(completion), 30L * 60L * 1000L);

		assertTrue(owner.valid());
		assertEquals(RUN_ID, owner.runSessionId());
	}

	private static DungeonRunCompletionRecord completion(long duration) {
		DungeonRunCompletionRecord completion = new DungeonRunCompletionRecord(
			COMPLETED_AT, "DUNGEON", "M1", "S", duration, RUN_ID, "run|" + RUN_ID);
		completion.completionId = "completion-fresh-m1";
		completion.playerUuid = PLAYER;
		completion.score = 272;
		completion.runTimeSource = DungeonRunCompletionRecord.RUN_TIME_OFFICIAL;
		return completion;
	}

	private static DungeonChestOffer offer(String chestTitle) {
		long cost = chestTitle.equals("Obsidian Chest") ? 800_000L : 100_000L;
		DungeonChestOffer offer = new DungeonChestOffer(chestTitle, new ChestCostBreakdown(cost), 1_000_000L,
			List.of(new DungeonLootEntry(chestTitle + " Reward", "TEST_" + chestTitle.toUpperCase().replace(' ', '_'), 1)));
		offer.observedAtEpochMillis = COMPLETED_AT + 14_000L;
		return offer;
	}

	private static DungeonRunRecord purchase() {
		DungeonRunRecord record = new DungeonRunRecord(COMPLETED_AT + 14_000L, 198, "M1", "S", "Obsidian Chest",
			800_000L, 1_212_540L, 412_540L, List.of(new DungeonLootEntry("Master Skull - Tier 1", "MASTER_SKULL_TIER_1", 1)));
		record.runSessionId = RUN_ID;
		record.linkedRunId = RUN_ID;
		record.runLinkSource = DungeonRunRecord.LINK_CROESUS_UNIQUE;
		record.chestSessionId = "live-99d1bc28-chest-1";
		return record;
	}
}
