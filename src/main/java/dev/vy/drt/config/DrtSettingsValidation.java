package dev.vy.drt.config;

import java.util.Locale;
import java.util.Set;

public final class DrtSettingsValidation {
	private static final Set<String> PET_RARITIES = Set.of("COMMON", "UNCOMMON", "RARE", "EPIC", "LEGENDARY");

	private DrtSettingsValidation() {
	}

	public static void normalize(DrtConfig config) {
		if (config == null) return;
		config.kuudraFaction = normalized(config.kuudraFaction, "MAGE").equals("BARBARIAN") ? "BARBARIAN" : "MAGE";
		String rarity = normalized(config.kuudraPetRarity, "LEGENDARY");
		config.kuudraPetRarity = PET_RARITIES.contains(rarity) ? rarity : "LEGENDARY";
		config.kuudraPetLevel = clamp(config.kuudraPetLevel, 1, 100);
		config.coolForgedLevel = clamp(config.coolForgedLevel, 1, 5);
		config.crimsonEssenceBonusPercent = clamp(config.crimsonEssenceBonusPercent, 0, 100);
		config.witherEssenceValuePer = Math.max(0, config.witherEssenceValuePer);
		config.undeadEssenceValuePer = Math.max(0, config.undeadEssenceValuePer);
		String priceMode = normalized(config.bazaarPriceMode, "INSTANT");
		config.bazaarPriceMode = switch (priceMode) {
			case "ORDER", "SELL_OFFER", "BUY_ORDER" -> "ORDER";
			default -> "INSTANT";
		};
		String visibility = normalized(config.hudVisibilityMode, "DEFAULT");
		config.hudVisibilityMode = switch (visibility) {
			case "GLOBAL", "DHUB" -> visibility;
			default -> "DEFAULT";
		};
		if (config.selectedTheme == null || config.selectedTheme.isBlank()) config.selectedTheme = "Default";
		else config.selectedTheme = config.selectedTheme.trim();
	}

	private static String normalized(String value, String fallback) {
		return value == null || value.isBlank() ? fallback : value.trim().toUpperCase(Locale.ROOT);
	}

	private static int clamp(int value, int min, int max) {
		return Math.max(min, Math.min(max, value));
	}
}
