package dev.vy.drt.luck.capture;

import dev.vy.drt.luck.FloorId;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses the paged Croesus menu ({@code (1/2) Croesus}). Run entries are listed newest first; each
 * lore block names the floor, the party, a rounded completion age and the chest expiry.
 */
public final class CroesusMenuParser {
	public static final long CHEST_LIFETIME_MILLIS = 72L * 60L * 60L * 1000L;

	private static final Pattern TITLE = Pattern.compile("^(?:\\((\\d+)/(\\d+)\\)\\s+)?Croesus$");
	private static final Pattern FLOOR_LINE = Pattern.compile("^(?:Floor|Tier)\\s+([IVXL]+|\\d+)$");
	private static final Pattern PLAYER_LINE = Pattern.compile("^([A-Za-z0-9_]{1,16}):\\s+.+$");
	private static final Pattern COMPLETED = Pattern.compile("^Completed:\\s+(.+)$");
	private static final Pattern AGO = Pattern.compile("^(\\d+)\\s+(sec|secs|second|seconds|min|mins|minute|minutes|hour|hours|hr|hrs|day|days)\\s+ago$");
	private static final Pattern EXPIRES = Pattern.compile("^Chests expire in\\s+(.+?)!?$");
	private static final Pattern DURATION_PART = Pattern.compile("(\\d+)\\s*([dhms])");
	private static final Pattern OPENED = Pattern.compile("^Opened Chests?:\\s*(.+)$");

	private CroesusMenuParser() {
	}

	/**
	 * One run entry.
	 *
	 * @param position         index among run entries on this page, newest first
	 * @param floor            null for Kuudra or an unreadable floor
	 * @param completedAgoMin  earliest plausible age in milliseconds, null when unreadable
	 * @param completedAgoMax  latest plausible age in milliseconds, null when unreadable
	 * @param openedChests     chest names after "Opened Chest:", empty when none are listed
	 */
	public record RunEntry(
		int slot,
		int position,
		String name,
		boolean masterMode,
		boolean catacombs,
		FloorId floor,
		List<String> players,
		String completedText,
		Long completedAgoMin,
		Long completedAgoMax,
		String expiresText,
		boolean noChestsOpened,
		List<String> openedChests,
		List<String> availableModifiers,
		List<String> rawLines
	) {
		public RunEntry {
			players = List.copyOf(players);
			openedChests = List.copyOf(openedChests);
			availableModifiers = List.copyOf(availableModifiers);
			rawLines = List.copyOf(rawLines);
		}
	}

	/** One page of the Croesus menu. */
	public record Menu(int page, int pageCount, boolean hasNextPage, List<RunEntry> entries) {
		public Menu {
			entries = List.copyOf(entries);
		}

		public boolean hasPreviousPage() {
			return page > 1;
		}

		public RunEntry entryAt(int slot) {
			for (RunEntry entry : entries) if (entry.slot() == slot) return entry;
			return null;
		}
	}

	public static boolean isMenuTitle(String title) {
		return title != null && TITLE.matcher(title.trim()).matches();
	}

	public static Menu parse(LuckProbeRecorder.ScreenSample screen) {
		if (screen == null) return null;
		Matcher title = TITLE.matcher(screen.title().trim());
		if (!title.matches()) return null;
		int page = title.group(1) == null ? 1 : Integer.parseInt(title.group(1));
		int pageCount = title.group(2) == null ? 1 : Integer.parseInt(title.group(2));
		boolean hasNext = false;
		List<RunEntry> entries = new ArrayList<>();
		List<LuckProbeRecorder.SlotSample> slots = new ArrayList<>(screen.slots());
		slots.sort((a, b) -> Integer.compare(a.slot(), b.slot()));
		for (LuckProbeRecorder.SlotSample slot : slots) {
			if ("Next Page".equals(slot.name())) hasNext = true;
			RunEntry entry = parseEntry(slot, entries.size());
			if (entry != null) entries.add(entry);
		}
		return new Menu(page, pageCount, hasNext || page < pageCount, entries);
	}

	static RunEntry parseEntry(LuckProbeRecorder.SlotSample slot, int position) {
		List<String> lore = slot.lore();
		if (lore.isEmpty() || !lore.contains("Click to view chests!")) return null;
		String name = slot.name().trim();
		String upperName = name.toUpperCase(Locale.ROOT);
		boolean masterMode = upperName.startsWith("MASTER MODE ");
		boolean catacombs = upperName.endsWith("THE CATACOMBS");

		FloorId floor = null;
		List<String> players = new ArrayList<>();
		String completedText = null;
		Long agoMin = null;
		Long agoMax = null;
		String expiresText = null;
		boolean noChestsOpened = false;
		List<String> opened = new ArrayList<>();
		List<String> modifiers = new ArrayList<>();

		String section = "";
		for (String raw : lore) {
			String line = raw.trim();
			if (line.isEmpty()) {
				section = "";
				continue;
			}
			Matcher floorLine = FLOOR_LINE.matcher(line);
			if (floor == null && catacombs && floorLine.matches() && line.startsWith("Floor")) {
				String number = floorLine.group(1);
				int value = Character.isDigit(number.charAt(0)) ? Integer.parseInt(number) : LuckText.roman(number);
				floor = LuckText.floor(value, masterMode);
				continue;
			}
			if (line.equals("Players:")) {
				section = "players";
				continue;
			}
			if (line.equals("Available Modifiers:")) {
				section = "modifiers";
				continue;
			}
			if (section.equals("players")) {
				Matcher player = PLAYER_LINE.matcher(line);
				if (player.matches()) players.add(player.group(1));
				continue;
			}
			if (section.equals("modifiers")) {
				if (!line.startsWith("Click to")) modifiers.add(line);
				continue;
			}
			Matcher completed = COMPLETED.matcher(line);
			if (completed.matches()) {
				completedText = completed.group(1).trim();
				long[] bounds = ageBounds(completedText);
				if (bounds != null) {
					agoMin = bounds[0];
					agoMax = bounds[1];
				}
				continue;
			}
			if (line.equals("No chests opened yet!")) {
				noChestsOpened = true;
				continue;
			}
			Matcher openedLine = OPENED.matcher(line);
			if (openedLine.matches()) {
				for (String part : openedLine.group(1).split(",")) {
					String chest = part.trim();
					if (!chest.isEmpty()) opened.add(chest);
				}
				continue;
			}
			Matcher expires = EXPIRES.matcher(line);
			if (expires.matches()) expiresText = expires.group(1).trim();
		}

		long[] window = intersect(agoMin, agoMax, expiryAgeBounds(expiresText));
		if (window != null) {
			agoMin = window[0];
			agoMax = window[1];
		}
		return new RunEntry(slot.slot(), position, name, masterMode, catacombs, floor, players, completedText,
			agoMin, agoMax, expiresText, noChestsOpened, opened, modifiers, lore);
	}

	/**
	 * Age range for "15 secs ago" style text. The game truncates to the largest unit, so "1 hour ago"
	 * covers [1h, 2h).
	 */
	static long[] ageBounds(String text) {
		if (text == null) return null;
		String trimmed = text.trim().toLowerCase(Locale.ROOT);
		if (trimmed.equals("just now") || trimmed.equals("now")) return new long[] {0L, 60_000L};
		Matcher matcher = AGO.matcher(trimmed);
		if (!matcher.matches()) return null;
		long value = Long.parseLong(matcher.group(1));
		long unit = unitMillis(matcher.group(2).charAt(0));
		return new long[] {value * unit, (value + 1) * unit};
	}

	/**
	 * Age range implied by "Chests expire in 2d 23h", assuming the 72 hour lifetime and truncation
	 * to the smallest printed unit.
	 */
	static long[] expiryAgeBounds(String text) {
		if (text == null) return null;
		Matcher matcher = DURATION_PART.matcher(text.toLowerCase(Locale.ROOT));
		long remaining = 0L;
		long smallest = 0L;
		boolean any = false;
		while (matcher.find()) {
			any = true;
			long unit = unitMillis(matcher.group(2).charAt(0));
			remaining += Long.parseLong(matcher.group(1)) * unit;
			smallest = smallest == 0L ? unit : Math.min(smallest, unit);
		}
		if (!any) return null;
		long latestAge = CHEST_LIFETIME_MILLIS - remaining;
		long earliestAge = latestAge - smallest;
		return new long[] {Math.max(0L, earliestAge), Math.max(0L, latestAge)};
	}

	private static long[] intersect(Long min, Long max, long[] other) {
		if (min == null || max == null) return other;
		if (other == null) return new long[] {min, max};
		long low = Math.max(min, other[0]);
		long high = Math.min(max, other[1]);
		// Inconsistent texts: keep the completion age alone rather than invent a window.
		return low <= high ? new long[] {low, high} : new long[] {min, max};
	}

	private static long unitMillis(char unit) {
		return switch (unit) {
			case 's' -> 1_000L;
			case 'm' -> 60_000L;
			case 'h' -> 3_600_000L;
			case 'd' -> 86_400_000L;
			default -> throw new IllegalArgumentException("Unknown unit " + unit);
		};
	}
}
