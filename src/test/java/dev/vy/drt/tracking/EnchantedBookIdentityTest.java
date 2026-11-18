package dev.vy.drt.tracking;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class EnchantedBookIdentityTest {
	@Test
	void parsesExactDungeonRewardBooks() {
		assertEquals("ENCHANTMENT_REJUVENATE_1", EnchantedBookIdentity.parse("Enchanted Book (Rejuvenate I)").itemId());
		assertEquals("ENCHANTMENT_FEATHER_FALLING_6", EnchantedBookIdentity.parse("Enchanted Book (Feather Falling VI)").itemId());
	}

	@Test
	void preservesUltimateBookIdentity() {
		assertEquals("ENCHANTMENT_ULTIMATE_WISE_5", EnchantedBookIdentity.parse("Enchanted Book (Ultimate Wise V)").itemId());
		assertEquals("Enchanted Book (Wise V)", EnchantedBookIdentity.parse("Enchanted Book (Ultimate Wise V)").displayName());
	}

	@Test
	void createsExactDisplayNameFromServerEnchantMetadata() {
		EnchantedBookIdentity.Book book = EnchantedBookIdentity.fromEnchantKey("feather_falling", 6);
		assertEquals("Enchanted Book (Feather Falling VI)", book.displayName());
		assertEquals("ENCHANTMENT_FEATHER_FALLING_6", book.itemId());
	}

	@Test
	void ultimateMetadataKeepsIdButOmitsUltimateFromPresentation() {
		EnchantedBookIdentity.Book bank = EnchantedBookIdentity.fromEnchantKey("ultimate_bank", 1);
		assertEquals("ENCHANTMENT_ULTIMATE_BANK_1", bank.itemId());
		assertEquals("Enchanted Book (Bank I)", bank.displayName());
		assertEquals("Enchanted Book (Bank I)", EnchantedBookIdentity.presentationName("Enchanted Book (Ultimate Bank I)"));
		assertEquals("Enchanted Book (No Pain No Gain I)",
			EnchantedBookIdentity.presentationName("Enchanted Book (Ultimate No Pain No Gain I)"));
	}

	@Test
	void rejectsGenericOrMalformedBooks() {
		assertNull(EnchantedBookIdentity.parse("Enchanted Book"));
		assertNull(EnchantedBookIdentity.parse("Enchanted Book (Rejuvenate 1)"));
	}
}
