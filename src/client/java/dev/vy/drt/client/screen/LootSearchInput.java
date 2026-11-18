package dev.vy.drt.client.screen;

/** Small text-input policy for the Loot Log search field. */
final class LootSearchInput {
	private LootSearchInput() {
	}

	static String append(String current, String typed) {
		String value = current == null ? "" : current;
		if (typed == null || typed.isEmpty()) return value;
		int codePoint = typed.codePointAt(0);
		if (Character.isISOControl(codePoint)) return value;
		return value + typed.toLowerCase(java.util.Locale.ROOT);
	}

	static String backspace(String current) {
		if (current == null || current.isEmpty()) return "";
		int end = current.offsetByCodePoints(current.length(), -1);
		return current.substring(0, end);
	}
}
