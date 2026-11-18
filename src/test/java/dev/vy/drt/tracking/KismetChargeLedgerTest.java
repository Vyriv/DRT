package dev.vy.drt.tracking;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.vy.drt.config.ChestCostBreakdown;
import org.junit.jupiter.api.Test;

class KismetChargeLedgerTest {
	private static final long TTL = 180_000L;

	@Test
	void singleUseChargesTheNextChestOnce() {
		KismetChargeLedger ledger = new KismetChargeLedger();
		assertEquals(
			KismetChargeLedger.FeatherUse.QUEUED,
			ledger.onFeatherUsed(false, false, "", 1_000L, TTL)
		);

		assertTrue(ledger.commitCharge("BEDROCK CHEST", false, 2_000L));
		assertFalse(ledger.commitCharge("BEDROCK CHEST", false, 2_100L));
		assertEquals(0, ledger.unassignedUses());
	}

	@Test
	void consecutiveUsesChargeConsecutiveChests() {
		KismetChargeLedger ledger = new KismetChargeLedger();
		ledger.onFeatherUsed(false, false, "", 1_000L, TTL);
		ledger.onFeatherUsed(false, false, "", 1_100L, TTL);

		assertTrue(ledger.commitCharge("BEDROCK CHEST", false, 2_000L));
		assertTrue(ledger.commitCharge("BEDROCK CHEST", false, 3_000L));
		assertFalse(ledger.commitCharge("BEDROCK CHEST", false, 4_000L));
	}

	@Test
	void sixUsesChargeSixDifferentChests() {
		KismetChargeLedger ledger = new KismetChargeLedger();
		for (int use = 0; use < 6; use++) {
			ledger.onFeatherUsed(false, false, "", 1_000L + use, TTL);
		}
		String[] chests = {
			"WOOD CHEST", "GOLD CHEST", "DIAMOND CHEST",
			"EMERALD CHEST", "OBSIDIAN CHEST", "BEDROCK CHEST"
		};
		for (int index = 0; index < chests.length; index++) {
			assertTrue(ledger.commitCharge(chests[index], false, 10_000L + index), chests[index]);
		}
		assertFalse(ledger.commitCharge("WOOD CHEST", false, 20_000L));
	}

	@Test
	void secondFeatherWhileFirstChestIsOpenWaitsForTheNextChest() {
		KismetChargeLedger ledger = new KismetChargeLedger();
		assertEquals(
			KismetChargeLedger.FeatherUse.APPLY_TO_OPEN_CHEST,
			ledger.onFeatherUsed(true, false, "Bedrock Chest", 16_15_44L, TTL)
		);
		assertEquals(
			KismetChargeLedger.FeatherUse.QUEUED,
			ledger.onFeatherUsed(true, true, "Bedrock Chest", 16_15_47L, TTL)
		);

		assertFalse(ledger.commitCharge("BEDROCK CHEST", true, 16_15_48L));
		assertEquals(1, ledger.unassignedUses());
		assertTrue(ledger.commitCharge("BEDROCK CHEST", false, 16_15_50L));
		assertEquals(0, ledger.unassignedUses());
	}

	@Test
	void specificChestIsNotStolenByADifferentChest() {
		KismetChargeLedger ledger = new KismetChargeLedger();
		ledger.onChestContentsChanged("BEDROCK CHEST", false, "", 1_000L, TTL);

		assertFalse(ledger.commitCharge("WOOD CHEST", false, 2_000L));
		assertTrue(ledger.wouldChargeSpecific("BEDROCK CHEST", 2_000L));
		assertFalse(ledger.wouldChargeSpecific("WOOD CHEST", 2_000L));
		assertTrue(ledger.commitCharge("BEDROCK CHEST", false, 3_000L));
	}

	@Test
	void chatAndFingerprintForTheSameChestCountOnce() {
		KismetChargeLedger ledger = new KismetChargeLedger();
		ledger.onFeatherUsed(false, false, "", 1_000L, TTL);
		ledger.onChestContentsChanged("OBSIDIAN CHEST", false, "", 1_050L, TTL);

		assertTrue(ledger.commitCharge("OBSIDIAN CHEST", false, 2_000L));
		assertFalse(ledger.commitCharge("GOLD CHEST", false, 3_000L));
		assertEquals(0, ledger.unassignedUses());
	}

	@Test
	void reopeningAChargedChestDoesNotTakeAnotherFeather() {
		KismetChargeLedger ledger = new KismetChargeLedger();
		ledger.onFeatherUsed(false, false, "", 1_000L, TTL);
		ledger.onFeatherUsed(false, false, "", 1_100L, TTL);
		assertTrue(ledger.commitCharge("GOLD CHEST", false, 2_000L));

		assertFalse(ledger.commitCharge("GOLD CHEST", true, 2_500L));
		assertEquals(1, ledger.unassignedUses());
		assertTrue(ledger.commitCharge("DIAMOND CHEST", false, 3_000L));
	}

	@Test
	void expiredUsesDoNotReviveWhenANewFeatherArrives() {
		KismetChargeLedger ledger = new KismetChargeLedger();
		ledger.onFeatherUsed(false, false, "", 1_000L, TTL);
		ledger.onFeatherUsed(false, false, "", 1_000L + TTL + 1L, TTL);

		assertEquals(1, ledger.unassignedUses());
		assertTrue(ledger.commitCharge("EMERALD CHEST", false, 1_000L + TTL + 2L));
		assertFalse(ledger.commitCharge("OBSIDIAN CHEST", false, 1_000L + TTL + 3L));
	}

	@Test
	void oneFeatherRaisesCostAndLowersProfitOnce() {
		long feather = 1_663_526L;
		long base = 2_000_000L;
		long value = 8_000_000L;
		ChestCostBreakdown charged = chest(base, feather);
		ChestCostBreakdown plain = chest(base, 0L);

		assertEquals(base + feather, charged.totalCostCoins());
		assertEquals(base, plain.totalCostCoins());
		assertEquals(value - (base + feather), value - charged.totalCostCoins());
		assertEquals(value - base, value - plain.totalCostCoins());
		assertTrue(charged.totalCostCoins() < base + feather + feather);
	}

	private static ChestCostBreakdown chest(long base, long feather) {
		ChestCostBreakdown breakdown = new ChestCostBreakdown();
		breakdown.baseChestCostCoins = base;
		breakdown.baseChestCostKnown = true;
		if (feather > 0L) {
			breakdown.usedKismetFeather = true;
			breakdown.kismetFeatherCostCoins = feather;
			breakdown.kismetRerolledChestOpened = true;
		}
		return breakdown;
	}
}
