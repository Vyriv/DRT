package dev.vy.drt.price;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ExpectedLootTablesTest {
	@Test
	void detectsKuudraRewardChestTitles() {
		assertTrue(ExpectedLootTables.isKuudraRewardChestTitle("Paid Chest"));
		assertTrue(ExpectedLootTables.isKuudraRewardChestTitle("Free Chest"));
		assertTrue(ExpectedLootTables.isKuudraRewardChestTitle("K3 Paid Chest"));
		assertFalse(ExpectedLootTables.isKuudraRewardChestTitle("Bedrock Chest"));
		assertFalse(ExpectedLootTables.isKuudraRewardChestTitle("Gold Chest"));
	}
}
