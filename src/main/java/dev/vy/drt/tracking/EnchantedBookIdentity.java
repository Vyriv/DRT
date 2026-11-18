package dev.vy.drt.tracking;

import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Exact SkyBlock enchanted-book display and item-id identity. */
public final class EnchantedBookIdentity {
	private static final Pattern DISPLAY = Pattern.compile("^Enchanted Book \\((.+) ([IVX]+)\\)$", Pattern.CASE_INSENSITIVE);
	private static final Set<String> ULTIMATE_ENCHANTS = Set.of(
		"LEGION", "WISE", "LAST_STAND", "SOUL_EATER", "SWARM", "COMBO", "REND",
		"NO_PAIN_NO_GAIN", "ONE_FOR_ALL", "CHIMERA", "BANK", "JERRY", "INFERNO",
		"FATAL_TEMPO", "DUPLEX", "FLASH", "HABANERO_TACTICS", "WISDOM"
	);

	private EnchantedBookIdentity() {
	}

	public static Book parse(String displayName) {
		if (displayName == null) return null;
		Matcher matcher = DISPLAY.matcher(displayName.trim().replaceAll("\\s+", " "));
		if (!matcher.matches()) return null;
		int level = romanToInt(matcher.group(2));
		if (level <= 0) return null;
		return fromEnchantName(matcher.group(1), level);
	}

	public static Book fromEnchantKey(String enchantKey, int level) {
		if (enchantKey == null || enchantKey.isBlank() || level <= 0) return null;
		String[] words = enchantKey.trim().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", " ").trim().split("\\s+");
		StringBuilder display = new StringBuilder();
		for (String word : words) {
			if (word.isBlank()) continue;
			if (display.length() > 0) display.append(' ');
			display.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
		}
		return display.isEmpty() ? null : fromEnchantName(display.toString(), level);
	}

	private static Book fromEnchantName(String enchantName, int level) {
		String cleanName = enchantName.trim().replaceAll("\\s+", " ");
		String normalized = cleanName.replaceFirst("(?i)^Ultimate\\s+", "")
			.replaceAll("[^A-Za-z0-9 ]", " ").trim().replaceAll("\\s+", "_").toUpperCase(Locale.ROOT);
		if (normalized.isBlank()) return null;
		boolean ultimate = cleanName.regionMatches(true, 0, "Ultimate ", 0, 9) || ULTIMATE_ENCHANTS.contains(normalized);
		String id = "ENCHANTMENT_" + (ultimate ? "ULTIMATE_" : "") + normalized + "_" + level;
		String presentation = cleanName.replaceFirst("(?i)^Ultimate\\s+", "").trim();
		String displayName = "Enchanted Book (" + presentation + " " + intToRoman(level) + ")";
		return new Book(displayName, id, presentation, level);
	}

	/** Presentation-only: Hypixel loot chat omits the Ultimate prefix for ultimate books. */
	public static String presentationName(String rawName) {
		if (rawName == null || rawName.isBlank()) return rawName == null ? "" : rawName;
		Matcher matcher = DISPLAY.matcher(rawName.trim().replaceAll("\\s+", " "));
		if (!matcher.matches()) return rawName;
		String enchant = matcher.group(1).replaceFirst("(?i)^Ultimate\\s+", "").trim();
		if (enchant.isBlank()) return rawName;
		return "Enchanted Book (" + enchant + " " + matcher.group(2).toUpperCase(Locale.ROOT) + ")";
	}

	private static int romanToInt(String roman) {
		if (roman == null) return -1;
		return switch (roman.toUpperCase(Locale.ROOT)) {
			case "I" -> 1; case "II" -> 2; case "III" -> 3; case "IV" -> 4; case "V" -> 5;
			case "VI" -> 6; case "VII" -> 7; case "VIII" -> 8; case "IX" -> 9; case "X" -> 10;
			default -> -1;
		};
	}

	private static String intToRoman(int level) {
		return switch (level) {
			case 1 -> "I"; case 2 -> "II"; case 3 -> "III"; case 4 -> "IV"; case 5 -> "V";
			case 6 -> "VI"; case 7 -> "VII"; case 8 -> "VIII"; case 9 -> "IX"; case 10 -> "X";
			default -> Integer.toString(level);
		};
	}

	public record Book(String displayName, String itemId, String enchantName, int level) {
	}
}
