package dev.vy.drt.history;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class CroesusChestLoreTest {
	@Test
	void parsesGenuineContentsQuantitiesEssenceBooksAndCost() {
		CroesusChestLore.Offer offer = CroesusChestLore.parse(List.of(
			"Contents:",
			"Recombobulator 3000",
			"Enchanted Book (One For All I)",
			"Wither Essence x50",
			"Undead Essence: 120",
			"Cost: 1,500,000 Coins",
			"Click to open!"
		));

		assertEquals(1_500_000L, offer.costCoins());
		assertFalse(offer.alreadyOpened());
		assertEquals(List.of(
			new CroesusChestLore.Reward("Recombobulator 3000", 1),
			new CroesusChestLore.Reward("Enchanted Book (One For All I)", 1),
			new CroesusChestLore.Reward("Wither Essence", 50),
			new CroesusChestLore.Reward("Undead Essence", 120)
		), offer.rewards());
	}

	@Test
	void readsEveryChestIconInOneGuiWithoutHovering() {
		Map<String, List<String>> lore = new LinkedHashMap<>();
		lore.put("WOOD CHEST", List.of("Contents", "Wither Essence x10", "Cost: FREE"));
		lore.put("GOLD CHEST", List.of("Contents", "Hot Potato Book x2", "Cost: 100,000 Coins"));
		lore.put("DIAMOND CHEST", List.of("Contents", "Undead Essence x30", "Cost: 200,000 Coins"));
		lore.put("EMERALD CHEST", List.of("Contents", "Bonzo Shard", "Cost: 400,000 Coins"));
		lore.put("OBSIDIAN CHEST", List.of("Contents", "Recombobulator 3000", "Already Opened"));

		Map<String, CroesusChestLore.Offer> offers = CroesusChestLore.parseAll(lore);

		assertEquals(5, offers.size());
		assertEquals(10, offers.get("WOOD CHEST").rewards().getFirst().quantity());
		assertEquals(2, offers.get("GOLD CHEST").rewards().getFirst().quantity());
		assertTrue(offers.get("OBSIDIAN CHEST").alreadyOpened());
	}
}
