package dev.vy.drt.tracking;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class RewardOpenRefusalTest {
	@Test
	void missingKeyForASecondChestRefusesTheOpen() {
		assertTrue(RewardOpenRefusal.refusesOpen("YOU NEED A DUNGEON CHEST KEY TO OPEN ANOTHER CHEST!"),
			"M5 Emerald on 2026-10-13: Hypixel refused it, yet it was saved with the key cost");
		assertTrue(RewardOpenRefusal.refusesOpen("YOU CANNOT AFFORD THIS!"));
		assertTrue(RewardOpenRefusal.refusesOpen("YOU NEED A DUNGEON CHEST KEY TO OPEN ANOTHER CHEST! (2)"), "a stacked repeat");
	}

	@Test
	void realOpensAndKeyUseAreNotRefusals() {
		assertFalse(RewardOpenRefusal.refusesOpen("EMERALD CHEST REWARDS"));
		assertFalse(RewardOpenRefusal.refusesOpen("YOU USED A DUNGEON CHEST KEY TO OPEN ANOTHER CHEST!"));
		assertFalse(RewardOpenRefusal.refusesOpen("THIS CHEST REQUIRES A DUNGEON CHEST KEY"));
		assertFalse(RewardOpenRefusal.refusesOpen(null));
	}
}
