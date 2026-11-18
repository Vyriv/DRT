package dev.vy.drt.client.screen;

/** Small, deterministic formatting helpers shared by the loot history UI and tests. */
public final class LootHistoryPresentation {
	private LootHistoryPresentation() {
	}

	public static String compactScore(String value) {
		if (value == null || value.isBlank()) return "N/R";
		String raw = value.trim();
		int slash = raw.indexOf('/');
		String number = slash >= 0 ? raw.substring(0, slash).trim() : raw;
		try {
			int score = Integer.parseInt(number);
			if (score < 0 || score > 100) return "N/R";
			return score + "/100";
		} catch (NumberFormatException ignored) {
			return "N/R";
		}
	}

	public static String compactChestName(String chestTitle) {
		if (chestTitle == null || chestTitle.isBlank()) return "";
		String name = chestTitle.trim();
		if (name.length() >= 6 && name.regionMatches(true, name.length() - 6, " Chest", 0, 6)) {
			name = name.substring(0, name.length() - 6).trim();
		}
		return name;
	}

	public static String normalizeHeaderPart(String value) {
		return value == null ? "" : value.trim().replaceAll("\\s+", " ");
	}
}
