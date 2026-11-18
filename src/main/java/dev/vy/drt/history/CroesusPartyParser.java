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
 * Parses the party from Croesus run entry lore and Hypixel's participant display names. Dungeon
 * entries read {@code beganjava: Berserk (46)}; Kuudra entries read {@code KrauserNoborn: Crowd Control}.
 */
public final class CroesusPartyParser {
	private static final int MAX_PARTY = 5;
	private static final String RANKS = "(?:\\[[^\\]]{1,24}]\\s*)*";
	private static final Pattern MEMBER = Pattern.compile(
		"(?i)([A-Z0-9_]{1,16})\\s*:?\\s+(HEALER|MAGE|BERSERK|ARCHER|TANK)\\s*\\(\\s*(\\d{1,3})\\s*\\)");
	private static final Pattern DUNGEON_LINE = Pattern.compile(
		"(?i)^\\s*" + RANKS + "([A-Z0-9_]{1,16})\\s*:\\s*(HEALER|MAGE|BERSERK|ARCHER|TANK)\\s*\\(\\s*(\\d{1,3})\\s*\\)\\s*$");
	private static final Pattern KUUDRA_LINE = Pattern.compile(
		"(?i)^\\s*" + RANKS + "([A-Z0-9_]{2,16})\\s*:\\s*([A-Z][A-Z' -]{0,22}[A-Z])\\s*$");
	private static final Pattern DUNGEON_CLASS = Pattern.compile("(?i)HEALER|MAGE|BERSERK|ARCHER|TANK");
	private static final Pattern LORE_KEY = Pattern.compile(
		"(?i)COMPLETED|OPENED|CHESTS?|TIME|DURATION|SCORE|TIER|FLOOR|STATUS|REWARDS?|CLICK|COST|TYPE|MODE|RUN|PROFIT|PLAYERS?|PARTY");

	private CroesusPartyParser() {
	}

	public static List<DungeonPartyMember> parse(List<String> lore) {
		Map<String, DungeonPartyMember> members = new LinkedHashMap<>();
		boolean sawHeader = false;
		boolean partySection = false;
		for (String raw : lore == null ? List.<String>of() : lore) {
			String line = clean(raw);
			String upper = line.toUpperCase(Locale.ROOT);
			if (upper.startsWith("PLAYERS:")) {
				sawHeader = true;
				partySection = true;
				line = line.substring(line.indexOf(':') + 1).trim();
				if (line.isEmpty()) continue;
			} else if (upper.equals("PLAYERS")) {
				sawHeader = true;
				partySection = true;
				continue;
			}
			if (!partySection || line.isBlank()) continue;
			if (members.size() >= MAX_PARTY) break;
			if (addDungeonMembers(line, members)) continue;
			DungeonPartyMember kuudra = kuudraMember(line);
			if (kuudra == null) {
				partySection = false;
				continue;
			}
			members.putIfAbsent(kuudra.name.toLowerCase(Locale.ROOT), kuudra);
		}
		// Without a Players header only complete Dungeon member lines are trusted.
		if (!sawHeader) {
			for (String raw : lore == null ? List.<String>of() : lore) {
				if (members.size() >= MAX_PARTY) break;
				DungeonPartyMember member = dungeonMember(clean(raw));
				if (member != null) members.putIfAbsent(member.name.toLowerCase(Locale.ROOT), member);
			}
		}
		return new ArrayList<>(members.values());
	}

	/** One Hypixel participant {@code display_name}, for example {@code §bcatgirllivid§f: §eMage§b (§e39§b)}. */
	public static DungeonPartyMember participant(String displayName) {
		return dungeonMember(clean(displayName));
	}

	private static boolean addDungeonMembers(String line, Map<String, DungeonPartyMember> members) {
		Matcher matcher = MEMBER.matcher(line);
		boolean found = false;
		while (matcher.find()) {
			found = true;
			String name = matcher.group(1);
			members.putIfAbsent(name.toLowerCase(Locale.ROOT),
				new DungeonPartyMember(name, "", title(matcher.group(2)), Integer.parseInt(matcher.group(3))));
		}
		return found;
	}

	private static DungeonPartyMember dungeonMember(String line) {
		Matcher matcher = DUNGEON_LINE.matcher(line);
		if (!matcher.matches() || !matcher.group(1).matches("(?i).*[A-Z_].*")) return null;
		return new DungeonPartyMember(matcher.group(1), "", title(matcher.group(2)), Integer.parseInt(matcher.group(3)));
	}

	private static DungeonPartyMember kuudraMember(String line) {
		Matcher matcher = KUUDRA_LINE.matcher(line);
		if (!matcher.matches()) return null;
		String name = matcher.group(1);
		String role = matcher.group(2).trim().replaceAll("\\s+", " ");
		if (LORE_KEY.matcher(name).matches() || DUNGEON_CLASS.matcher(role).matches()) return null;
		return DungeonPartyMember.kuudra(name, role);
	}

	private static String clean(String raw) {
		return raw == null ? "" : raw.replaceAll("§.", "").trim();
	}

	private static String title(String value) {
		String lower = value.toLowerCase(Locale.ROOT);
		return Character.toUpperCase(lower.charAt(0)) + lower.substring(1);
	}
}
