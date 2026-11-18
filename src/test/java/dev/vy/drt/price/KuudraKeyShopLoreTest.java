package dev.vy.drt.price;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.vy.drt.config.DungeonFloor;
import dev.vy.drt.config.KuudraKeyShopCost;
import java.util.List;
import org.junit.jupiter.api.Test;

class KuudraKeyShopLoreTest {
	@Test
	void prefixMaterialsAndALaterCoinLineStillParse() {
		KuudraKeyShopCost cost = KuudraKeyShopLore.parse(List.of(
			"Right Click to buy!",
			"Cost",
			"20x Enchanted Mycelium",
			"2x Nether Star",
			"750,000 Coins",
			"You don't have the required items!"
		));

		assertEquals(750_000L, cost.coins);
		assertEquals(20, cost.materials.get("ENCHANTED_MYCELIUM"));
		assertEquals(2, cost.materials.get("CORRUPTED_NETHER_STAR"));
	}

	@Test
	void suffixMaterialsAndCoinSuffixStillParse() {
		KuudraKeyShopCost cost = KuudraKeyShopLore.parse(List.of(
			"Cost",
			"Enchanted Red Sand x60",
			"Nether Star x2",
			"1.5m Coins"
		));

		assertEquals(1_500_000L, cost.coins);
		assertEquals(60, cost.materials.get("ENCHANTED_RED_SAND"));
		assertEquals(2, cost.materials.get("CORRUPTED_NETHER_STAR"));
	}

	@Test
	void linesWithoutAPositiveCoinCostAreNotAPrice() {
		assertNull(KuudraKeyShopLore.parse(List.of("20x Enchanted Mycelium", "750,000 Coins")));
		assertNull(KuudraKeyShopLore.parse(List.of("Cost", "You don't have the required items!")));
		assertNull(KuudraKeyShopLore.parse(List.of("Cost", "0 Coins", "2x Nether Star")));
	}

	@Test
	void onlyFactionEmissaryTitlesAreTheKeyShop() {
		assertTrue(KuudraKeyShopLore.isShopTitle("Mage Shop"));
		assertTrue(KuudraKeyShopLore.isShopTitle("BARBARIAN SHOP"));
		assertTrue(KuudraKeyShopLore.isShopTitle("Mage Emissary"));
		assertTrue(KuudraKeyShopLore.isShopTitle("Barbarian Emissary"));
		assertFalse(KuudraKeyShopLore.isShopTitle("Shop"));
		assertFalse(KuudraKeyShopLore.isShopTitle("Paid Chest"));
	}

	@Test
	void freeAndPaidChestsAreKuudraTitles() {
		assertTrue(KuudraKeyShopLore.isKuudraChestTitle("Paid Chest"));
		assertTrue(KuudraKeyShopLore.isKuudraChestTitle("FREE CHEST"));
		assertFalse(KuudraKeyShopLore.isKuudraChestTitle("Wood Chest"));
		assertFalse(KuudraKeyShopLore.isKuudraChestTitle("Bedrock Chest"));
		assertFalse(KuudraKeyShopLore.isKuudraChestTitle("Croesus"));
	}

	@Test
	void keyItemNamesMapToEveryTier() {
		assertEquals(DungeonFloor.K1, KuudraKeyShopLore.tierFromItemName("Basic Kuudra Key"));
		assertEquals(DungeonFloor.K1, KuudraKeyShopLore.tierFromItemName("Kuudra Key"));
		assertEquals(DungeonFloor.K2, KuudraKeyShopLore.tierFromItemName("Hot Kuudra Key"));
		assertEquals(DungeonFloor.K3, KuudraKeyShopLore.tierFromItemName("Burning Kuudra Key"));
		assertEquals(DungeonFloor.K4, KuudraKeyShopLore.tierFromItemName("Fiery Kuudra Key"));
		assertEquals(DungeonFloor.K5, KuudraKeyShopLore.tierFromItemName("Infernal Kuudra Key"));
		assertEquals(DungeonFloor.K3, KuudraKeyShopLore.tierFromItemName("Burning Kuudra Key (Right Click)"));
		assertEquals(DungeonFloor.UNKNOWN, KuudraKeyShopLore.tierFromItemName("Magical Kuudra Relic"));
	}
}
