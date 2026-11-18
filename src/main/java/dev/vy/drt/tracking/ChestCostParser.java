package dev.vy.drt.tracking;

import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Parses the explicit opening cost shown by Hypixel reward chest controls. */
public final class ChestCostParser {
	private static final Pattern COINS = Pattern.compile("([-+]?\\d[\\d,]*(?:\\.\\d+)?)\\s*([kmb])?(?:\\s*Coins)?", Pattern.CASE_INSENSITIVE);

	private ChestCostParser() {
	}

	public static Long parseLore(List<String> loreLines) {
		if (loreLines == null) return null;
		for (int index = 0; index < loreLines.size(); index++) {
			String line = clean(loreLines.get(index));
			if (line.equalsIgnoreCase("FREE") || line.equalsIgnoreCase("Cost: Free")) return 0L;
			if (line.equalsIgnoreCase("Cost") && index + 1 < loreLines.size()) {
				Long parsed = parseCoins(loreLines.get(index + 1));
				if (parsed != null) return parsed;
			}
			if (line.regionMatches(true, 0, "Cost:", 0, 5)) {
				Long parsed = parseCoins(line.substring(5));
				if (parsed != null) return parsed;
			}
		}
		return null;
	}

	private static Long parseCoins(String value) {
		Matcher matcher = COINS.matcher(clean(value));
		if (!matcher.find()) return null;
		double amount = Double.parseDouble(matcher.group(1).replace(",", ""));
		String suffix = matcher.group(2);
		if (suffix != null && !suffix.isBlank()) {
			switch (suffix.toLowerCase(Locale.ROOT).charAt(0)) {
				case 'k' -> amount *= 1_000D;
				case 'm' -> amount *= 1_000_000D;
				case 'b' -> amount *= 1_000_000_000D;
				default -> { }
			}
		}
		return Math.round(Math.abs(amount));
	}

	private static String clean(String value) {
		return value == null ? "" : value.trim().replaceAll("\\s+", " ");
	}
}
