package dev.vy.drt.tracking;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class RewardMenuTitlesTest {
	@Test
	void croesusProfitOverlayShowsOnCatacombsChestListsWhenEnabled() {
		assertTrue(RewardMenuTitles.shouldDrawProfitOverlay(true, "MASTER CATACOMBS - FLOOR V", 6));
		assertTrue(RewardMenuTitles.shouldDrawProfitOverlay(true, "MASTER MODE CATACOMBS - FLOOR V", 6));
		assertTrue(RewardMenuTitles.shouldDrawProfitOverlay(true, "THE CATACOMBS - FLOOR V", 6));
		assertFalse(RewardMenuTitles.shouldDrawProfitOverlay(false, "MASTER CATACOMBS - FLOOR V", 6));
		assertFalse(RewardMenuTitles.shouldDrawProfitOverlay(true, "MASTER CATACOMBS - FLOOR V", 0));
		assertFalse(RewardMenuTitles.shouldDrawProfitOverlay(true, "CROESUS", 6));
		assertFalse(RewardMenuTitles.shouldDrawProfitOverlay(true, "BEDROCK CHEST", 1));
	}
}
