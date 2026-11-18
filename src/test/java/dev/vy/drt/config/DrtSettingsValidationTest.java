package dev.vy.drt.config;

import com.google.gson.Gson;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DrtSettingsValidationTest {
	@Test
	void normalizesSettingsWithoutChangingOnboardingCompletion() {
		DrtConfig config = new DrtConfig();
		config.onboardingComplete = true;
		config.kuudraFaction = "invalid";
		config.kuudraPetRarity = "mythic";
		config.kuudraPetLevel = 900;
		config.coolForgedLevel = -2;
		config.crimsonEssenceBonusPercent = 130;
		config.bazaarPriceMode = "sell_offer";
		config.hudVisibilityMode = "dhub";
		config.selectedTheme = "  Amethyst  ";

		DrtSettingsValidation.normalize(config);

		assertTrue(config.onboardingComplete);
		assertEquals("MAGE", config.kuudraFaction);
		assertEquals("LEGENDARY", config.kuudraPetRarity);
		assertEquals(100, config.kuudraPetLevel);
		assertEquals(1, config.coolForgedLevel);
		assertEquals(100, config.crimsonEssenceBonusPercent);
		assertEquals("ORDER", config.bazaarPriceMode);
		assertEquals("DHUB", config.hudVisibilityMode);
		assertEquals("Amethyst", config.selectedTheme);
	}

	@Test
	void settingsPersistThroughJsonRoundTrip() {
		DrtConfig config = new DrtConfig();
		config.onboardingComplete = true;
		config.selectedTheme = "Midnight";
		config.forceSalvageEquipment = true;
		config.statsSyncEnabled = false;
		Gson gson = new Gson();

		DrtConfig restored = gson.fromJson(gson.toJson(config), DrtConfig.class);

		assertTrue(restored.onboardingComplete);
		assertEquals("Midnight", restored.selectedTheme);
		assertTrue(restored.forceSalvageEquipment);
		assertEquals(false, restored.statsSyncEnabled);
	}
}
