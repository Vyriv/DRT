package dev.vy.drt.tracking;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** The F5 Wood chest rewards as Hypixel printed them on 2026-10-12, formatting codes stripped. */
class RewardChatBlockTest {
	@Test
	void indentedLinesUnderTheHeaderAreRewardsUntilTheBlankLine() {
		RewardChatBlock block = new RewardChatBlock();
		assertFalse(block.acceptLine("  WOOD CHEST REWARDS"), "nothing is open before the tracker assigns the header");
		block.open();
		assertTrue(block.acceptLine("    Enchanted Book (Rejuvenate I)"));
		assertTrue(block.acceptLine("    Enchanted Book (Feather Falling VI)"));
		assertTrue(block.acceptLine("    Undead Essence x19"));
		assertTrue(block.acceptLine("    Wither Essence x10"));
		assertFalse(block.acceptLine(""));
		assertFalse(block.isOpen());
		assertFalse(block.acceptLine("      Click HERE to re-queue into The Catacombs!"), "indented chat after the block is not a reward");
	}

	@Test
	void unindentedChatEndsTheBlockAndIsNeverAReward() {
		RewardChatBlock block = new RewardChatBlock();
		block.open();
		assertFalse(block.acceptLine("From [MVP++] Someone: scroll"));
		assertFalse(block.acceptLine("    Enchanted Book (Rejuvenate I)"), "the block stays closed once broken");
	}

	@Test
	void partyAndGuildChatInterruptTheBlock() {
		RewardChatBlock block = new RewardChatBlock();
		block.open();
		assertTrue(block.acceptLine("    Hot Potato Book"));
		assertFalse(block.acceptLine("Party > [VIP] Friend: nice"));
		assertFalse(block.acceptLine("    Enchanted Book (Legion I)"));
	}

	@Test
	void whitespaceOnlyOrSingleSpaceLinesDoNotCount() {
		RewardChatBlock block = new RewardChatBlock();
		block.open();
		assertFalse(block.acceptLine("     "));
		block.open();
		assertFalse(block.acceptLine(" Enchanted Book (Rejuvenate I)"));
		block.open();
		assertFalse(block.acceptLine(null));
	}
}
