package dev.vy.drt.luck;

import java.util.Locale;

public enum ChestTier {
	WOOD, GOLD, DIAMOND, EMERALD, OBSIDIAN, BEDROCK;

	public String dataFileName() {
		return name().toLowerCase(Locale.ROOT) + ".json";
	}

	public static ChestTier parse(String value) {
		if (value == null) return null;
		try {
			return valueOf(value.trim().toUpperCase(Locale.ROOT));
		} catch (IllegalArgumentException ignored) {
			return null;
		}
	}
}
