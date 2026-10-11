package dev.vy.drt.luck.capture;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Party members from the dungeon tab list, for example {@code [302] Name (Mage XL)}. The tab
 * format has not been captured by the probe yet, so a roster is only kept when it is plausible:
 * one to five distinct names that include the local player. Otherwise the roster stays unknown.
 */
public final class DungeonPartyRoster {
	private static final Pattern MEMBER = Pattern.compile(
		"^\\[\\d+\\]\\s+(?:\\[[^\\]]+\\]\\s+)?([A-Za-z0-9_]{1,16})\\b.*\\((?:Healer|Mage|Berserk|Archer|Tank|EMPTY|DEAD)\\b[^)]*\\)\\s*$");

	private DungeonPartyRoster() {
	}

	public static List<String> fromTabLines(List<String> lines, String localPlayer) {
		if (lines == null || localPlayer == null || localPlayer.isBlank()) return null;
		List<String> names = new ArrayList<>();
		List<String> lower = new ArrayList<>();
		for (String raw : lines) {
			if (raw == null) continue;
			Matcher matcher = MEMBER.matcher(raw.trim());
			if (!matcher.matches()) continue;
			String name = matcher.group(1);
			String key = name.toLowerCase(Locale.ROOT);
			if (lower.contains(key)) continue;
			lower.add(key);
			names.add(name);
		}
		if (names.isEmpty() || names.size() > 5) return null;
		if (!lower.contains(localPlayer.toLowerCase(Locale.ROOT))) return null;
		return List.copyOf(names);
	}
}
