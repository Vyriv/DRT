package dev.vy.drt.client.screen;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class LootHistoryPresentationTest {
	@Test
	void compactScoreAcceptsNumericAndAlreadyFormattedValues() {
		assertEquals("41/100", LootHistoryPresentation.compactScore("41"));
		assertEquals("41/100", LootHistoryPresentation.compactScore("41/100"));
		assertEquals("41/100", LootHistoryPresentation.compactScore("41/100/100"));
		assertEquals("N/R", LootHistoryPresentation.compactScore("Not Rated"));
	}

	@Test
	void compactChestNameRemovesOnlyTheTrailingChestWord() {
		assertEquals("Emerald", LootHistoryPresentation.compactChestName("Emerald Chest"));
		assertEquals("Obsidian", LootHistoryPresentation.compactChestName("Obsidian"));
	}
}
