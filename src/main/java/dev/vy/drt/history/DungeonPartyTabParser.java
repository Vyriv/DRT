package dev.vy.drt.history;

import dev.vy.drt.config.DungeonPartyMember;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Conservatively parses party evidence from the live tab list. Hypixel shows Dungeon classes with
 * Roman numeral levels, for example {@code [312] catgirlVivid (Mage XL)}.
 */
public final class DungeonPartyTabParser {
	private static final int MAX_PARTY = 5;
	private static final String BRACKETS = "(?:\\[[^\\]]{1,24}]\\s*)*";
	// Emblems, ironman and other tab decorations around the name; ASCII word characters never count.
	private static final String SYMBOLS = "(?:[^\\sA-Za-z0-9_\\[\\]()]+\\s*){0,3}";
	private static final String NAME = "([A-Za-z0-9_]{1,16})";
	private static final String CLASS = "(Healer|Mage|Berserk|Archer|Tank)";
	private static final String LEVEL = "([IVXLC]{1,7}|\\d{1,3})";
	private static final Pattern TAB = Pattern.compile("(?i)^\\s*" + BRACKETS + SYMBOLS + NAME + "\\s*" + SYMBOLS
		+ "\\(\\s*" + CLASS + "(?:\\s+" + LEVEL + ")?\\s*\\)\\s*$");
	private static final Pattern LABELED = Pattern.compile("(?i)^\\s*" + BRACKETS + NAME
		+ "\\s*:\\s*" + CLASS + "\\s*\\(\\s*(\\d{1,3})\\s*\\)\\s*$");
	private static final Pattern PARTY_HEADER = Pattern.compile("(?i)^\\s*Party\\s*\\(\\s*([1-5])\\s*\\)\\s*:?\\s*$");
	private static final Pattern SECTION_MEMBER = Pattern.compile("(?i)^\\s*" + BRACKETS + SYMBOLS + "([A-Za-z0-9_]{2,16})\\s*" + SYMBOLS
		+ "(?:\\([^()]{0,24}\\)\\s*)?$");

	private DungeonPartyTabParser() {}

	/** Dungeon members whose class is shown beside their name, plus names listed under the tab's Party header. */
	public static List<DungeonPartyMember> parse(List<String> lines) {
		Map<String, DungeonPartyMember> members = new LinkedHashMap<>();
		for (String raw : lines == null ? List.<String>of() : lines) {
			String line = clean(raw);
			Matcher matcher = TAB.matcher(line);
			if (!matcher.matches()) matcher = LABELED.matcher(line);
			if (!matcher.matches()) continue;
			String name = matcher.group(1);
			members.put(name.toLowerCase(Locale.ROOT),
				new DungeonPartyMember(name, "", title(matcher.group(2)), level(matcher.group(3))));
		}
		for (String name : partySectionNames(lines)) {
			members.putIfAbsent(name.toLowerCase(Locale.ROOT), new DungeonPartyMember(name, "", "", 0));
		}
		return new ArrayList<>(members.values());
	}

	/**
	 * Names listed directly under a {@code Party (N)} tab header, stopping at the first line that is
	 * not a single player entry. Without the header nothing is returned, so lobby players never count.
	 */
	public static List<String> partySectionNames(List<String> lines) {
		List<String> names = new ArrayList<>();
		if (lines == null) return names;
		for (int i = 0; i < lines.size(); i++) {
			Matcher header = PARTY_HEADER.matcher(clean(lines.get(i)));
			if (!header.matches()) continue;
			int expected = Integer.parseInt(header.group(1));
			for (int j = i + 1; j < lines.size() && names.size() < expected; j++) {
				Matcher member = SECTION_MEMBER.matcher(clean(lines.get(j)));
				if (!member.matches() || isSectionWord(member.group(1))) break;
				names.add(member.group(1));
			}
			return names;
		}
		return names;
	}

	/** Roman or Arabic class level; 0 when absent or not a plausible level. */
	static int level(String value) {
		if (value == null || value.isBlank()) return 0;
		String text = value.trim().toUpperCase(Locale.ROOT);
		if (text.chars().allMatch(Character::isDigit)) {
			int parsed = Integer.parseInt(text);
			return parsed <= 100 ? parsed : 0;
		}
		int total = 0;
		int previous = 0;
		for (int i = text.length() - 1; i >= 0; i--) {
			int digit = switch (text.charAt(i)) {
				case 'I' -> 1;
				case 'V' -> 5;
				case 'X' -> 10;
				case 'L' -> 50;
				case 'C' -> 100;
				default -> -1;
			};
			if (digit < 0) return 0;
			total += digit < previous ? -digit : digit;
			previous = Math.max(previous, digit);
		}
		return total > 0 && total <= 100 && toRoman(total).equals(text) ? total : 0;
	}

	private static String toRoman(int value) {
		int[] values = {100, 90, 50, 40, 10, 9, 5, 4, 1};
		String[] symbols = {"C", "XC", "L", "XL", "X", "IX", "V", "IV", "I"};
		StringBuilder out = new StringBuilder();
		for (int i = 0; i < values.length; i++) {
			while (value >= values[i]) {
				out.append(symbols[i]);
				value -= values[i];
			}
		}
		return out.toString();
	}

	private static boolean isSectionWord(String name) {
		return name.matches("(?i)PARTY|INFO|STATS|PLAYERS?|DUNGEON|ACCOUNT|AREA|SERVER|PROFILE|EMPTY|DEAD");
	}

	private static String clean(String raw) {
		return raw == null ? "" : raw.replaceAll("§.", "").trim();
	}

	private static String title(String value) {
		String lower = value.toLowerCase(Locale.ROOT);
		return Character.toUpperCase(lower.charAt(0)) + lower.substring(1);
	}
}
