package dev.vy.drt.config;

import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChestCostCertaintyTest {
	@Test
	void confirmedFreeCostRemainsKnown() {
		ChestCostBreakdown cost = new ChestCostBreakdown(0L);
		DungeonRunRecord record = new DungeonRunRecord(1L, 1, "F1", "S", "Wood Chest", 0L, 10L, 10L, List.of());
		record.applyCostBreakdown(cost);
		assertTrue(record.hasKnownChestCost());
		assertTrue(record.hasKnownProfit());
	}

	@Test
	void missingCostRemainsUnknown() {
		DungeonRunRecord record = new DungeonRunRecord(1L, 1, "F1", "S", "Gold Chest", 0L, 10L, 0L, List.of());
		record.applyCostBreakdown(new ChestCostBreakdown());
		assertFalse(record.hasKnownChestCost());
		assertFalse(record.hasKnownProfit());
	}

	@Test
	void legacyRecordsKeepTheirExistingFinancialMeaning() {
		DungeonRunRecord record = new DungeonRunRecord();
		record.chestCostKnown = null;
		record.financialsKnown = null;
		assertTrue(record.hasKnownChestCost());
	}
}
