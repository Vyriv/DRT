package dev.vy.drt.client.screen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.vy.drt.price.PriceCache;
import org.junit.jupiter.api.Test;

class ManualRunEntrySelectionTest {
	@Test
	void pickerSelectionAddsMultipleItemsWithQuantitiesAndSupportsRemoval() {
		ManualRunEntryScreen.ManualLootSlot first = new ManualRunEntryScreen.ManualLootSlot();
		ManualRunEntryScreen.ManualLootSlot second = new ManualRunEntryScreen.ManualLootSlot();

		assertTrue(ManualRunEntryScreen.applySelection(first, result("RECOMBOBULATOR_3000", "Recombobulator 3000"), "3"));
		assertTrue(ManualRunEntryScreen.applySelection(second, result("FIRST_MASTER_STAR", "First Master Star"), "2"));
		assertEquals("RECOMBOBULATOR_3000", first.itemId);
		assertEquals(3, first.quantity);
		assertEquals("FIRST_MASTER_STAR", second.itemId);
		assertEquals(2, second.quantity);

		first.clear();
		assertEquals("", first.itemId);
		assertEquals(1, first.quantity);
		assertFalse(ManualRunEntryScreen.applySelection(first, result("", "Missing"), "1"));
	}

	private static PriceCache.SearchResult result(String id, String name) {
		return new PriceCache.SearchResult(id, name, 0D, "test", 0);
	}
}
