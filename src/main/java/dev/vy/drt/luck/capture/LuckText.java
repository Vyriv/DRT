package dev.vy.drt.luck.capture;

import dev.vy.drt.luck.ChestTier;
import dev.vy.drt.luck.FloorId;
import dev.vy.drt.luck.data.ChestTable;
import dev.vy.drt.luck.data.FloorTable;
import dev.vy.drt.luck.data.LootEntry;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Shared parsing for Hypixel menu text as read by the luck capture. */
public final class LuckText {
	private static final Pattern EXACT_NUMBER = Pattern.compile("^\\d{1,3}(?:,\\d{3})*$|^\\d+$");
	private static final Pattern ABBREVIATED_NUMBER = Pattern.compile("^(\\d+(?:\\.\\d+)?)([kKmMbB])?$");
	private static final Pattern ENCHANTED_BOOK = Pattern.compile("^Enchanted Book \\((.+?)\\s+([IVXL]+)\\)$");
	private static final Pattern ESSENCE = Pattern.compile("^([A-Za-z]+) Essence x(\\d[\\d,]*)$");
	private static final Pattern QUANTITY_SUFFIX = Pattern.compile("^(.+?) x(\\d[\\d,]*)$");

	private LuckText() {
	}

	/** A number printed in full, such as {@code 3,224}; null for abbreviated or malformed text. */
	public static Long exactNumber(String text) {
		if (text == null) return null;
		String trimmed = text.trim();
		if (!EXACT_NUMBER.matcher(trimmed).matches()) return null;
		try {
			return Long.parseLong(trimmed.replace(",", ""));
		} catch (NumberFormatException e) {
			return null;
		}
	}

	/** Approximate value of an abbreviated number such as {@code 48.9k}; exact numbers parse too. */
	public static Double approximateNumber(String text) {
		if (text == null) return null;
		Long exact = exactNumber(text);
		if (exact != null) return exact.doubleValue();
		Matcher matcher = ABBREVIATED_NUMBER.matcher(text.trim());
		if (!matcher.matches()) return null;
		double value = Double.parseDouble(matcher.group(1));
		String unit = matcher.group(2);
		if (unit == null) return value;
		return switch (unit.toLowerCase(Locale.ROOT)) {
			case "k" -> value * 1_000d;
			case "m" -> value * 1_000_000d;
			case "b" -> value * 1_000_000_000d;
			default -> null;
		};
	}

	public static int roman(String numeral) {
		if (numeral == null || numeral.isBlank()) return -1;
		int total = 0;
		int previous = 0;
		String upper = numeral.trim().toUpperCase(Locale.ROOT);
		for (int i = upper.length() - 1; i >= 0; i--) {
			int value = switch (upper.charAt(i)) {
				case 'I' -> 1;
				case 'V' -> 5;
				case 'X' -> 10;
				case 'L' -> 50;
				default -> -1;
			};
			if (value < 0) return -1;
			total += value < previous ? -value : value;
			previous = Math.max(previous, value);
		}
		return total;
	}

	/** "Floor V" plus the Master Mode flag; null when the floor number is not a Catacombs floor. */
	public static FloorId floor(int number, boolean masterMode) {
		if (number < 1 || number > 7) return null;
		return FloorId.parse((masterMode ? "M" : "F") + number);
	}

	/** Chest tier from a Croesus chest button name such as {@code Bedrock}. */
	public static ChestTier chestTier(String name) {
		if (name == null) return null;
		String trimmed = name.trim();
		if (trimmed.toUpperCase(Locale.ROOT).endsWith(" CHEST")) trimmed = trimmed.substring(0, trimmed.length() - 6).trim();
		try {
			return ChestTier.valueOf(trimmed.toUpperCase(Locale.ROOT));
		} catch (IllegalArgumentException e) {
			return null;
		}
	}

	/** One line of a chest's Contents list, with the loot key when it can be named without guessing. */
	public record ContentLine(String text, String name, int quantity, LootEntry.Kind kind, String key) {
	}

	/**
	 * Parses a Contents line. Books and essence get canonical keys from their text; other items
	 * only get a key when exactly one entry in the floor's tables has the same display name.
	 */
	public static ContentLine contentLine(String text, FloorTable floor) {
		String trimmed = text == null ? "" : text.trim();
		Matcher book = ENCHANTED_BOOK.matcher(trimmed);
		if (book.matches()) {
			int level = roman(book.group(2));
			String id = book.group(1).toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]+", "_");
			if (id.startsWith("ULTIMATE_") && floor != null && !hasKey(floor, LootEntry.enchantmentKey(id, level))) {
				String unprefixed = LootEntry.enchantmentKey(id.substring("ULTIMATE_".length()), level);
				if (hasKey(floor, unprefixed)) return new ContentLine(trimmed, trimmed, 1, LootEntry.Kind.ENCHANTMENT, unprefixed);
			}
			String key = level > 0 ? LootEntry.enchantmentKey(id, level) : null;
			return new ContentLine(trimmed, trimmed, 1, LootEntry.Kind.ENCHANTMENT, key);
		}
		Matcher essence = ESSENCE.matcher(trimmed);
		if (essence.matches()) {
			int amount = Integer.parseInt(essence.group(2).replace(",", ""));
			return new ContentLine(trimmed, essence.group(1) + " Essence", amount, LootEntry.Kind.ESSENCE,
				LootEntry.essenceKey(essence.group(1), amount));
		}
		String name = trimmed;
		int quantity = 1;
		Matcher suffix = QUANTITY_SUFFIX.matcher(trimmed);
		if (suffix.matches()) {
			name = suffix.group(1).trim();
			quantity = Integer.parseInt(suffix.group(2).replace(",", ""));
		}
		LootEntry entry = floor == null ? null : uniqueByDisplayName(floor, name);
		return new ContentLine(trimmed, name, quantity, entry == null ? null : entry.kind(), entry == null ? null : entry.key());
	}

	public static String normalizedName(String name) {
		return (name == null ? "" : name).toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]", "");
	}

	private static boolean hasKey(FloorTable floor, String key) {
		for (ChestTable chest : floor.chests().values()) {
			if (chest.contains(key)) return true;
		}
		return false;
	}

	private static LootEntry uniqueByDisplayName(FloorTable floor, String name) {
		String wanted = normalizedName(name);
		if (wanted.isEmpty()) return null;
		LootEntry found = null;
		for (ChestTable chest : floor.chests().values()) {
			for (LootEntry entry : chest.entries()) {
				if (entry.kind() == LootEntry.Kind.ESSENCE) continue;
				if (!normalizedName(entry.displayName()).equals(wanted)) continue;
				if (found != null && !found.key().equals(entry.key())) return null;
				found = entry;
			}
		}
		return found;
	}
}
