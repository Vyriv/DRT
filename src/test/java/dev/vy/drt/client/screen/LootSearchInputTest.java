package dev.vy.drt.client.screen;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class LootSearchInputTest {
	@Test
	void acceptsSpacesAndBuildsMultiwordQueries() {
		String query = "";
		for (String typed : new String[] {"B", "o", "n", "z", "o", " ", "S", "h", "a", "r", "d"}) {
			query = LootSearchInput.append(query, typed);
		}
		assertEquals("bonzo shard", query);
		assertEquals("recombobulator 3000", LootSearchInput.append("recombobulator ", "3000"));
		assertEquals("enchanted book", LootSearchInput.append("enchanted", " book"));
	}

	@Test
	void backspaceRemovesSpaceWithoutClearingTheQuery() {
		assertEquals("bonzo", LootSearchInput.backspace("bonzo "));
	}
}
