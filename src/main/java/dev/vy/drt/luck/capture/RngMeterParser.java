package dev.vy.drt.luck.capture;

import dev.vy.drt.luck.ChestTier;
import dev.vy.drt.luck.FloorId;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses the Catacombs RNG Meter screens. Stored scores are printed in full; the overview
 * abbreviates the selected drop's requirement ({@code 48.9k}), so it is only kept as text there.
 * Exact requirements come from the per-floor screen ({@code Dungeon Score: 3,224/2,540}).
 */
public final class RngMeterParser {
	public static final String OVERVIEW_TITLE = "Catacombs RNG Meters";

	private static final Pattern FLOOR_METER_NAME = Pattern.compile("^Catacombs \\(([FM][1-7])\\) RNG Meter$");
	private static final Pattern PROGRESS = Pattern.compile("^Progress:\\s*([\\d.]+)%$");
	private static final Pattern SCORE_PAIR = Pattern.compile("^([\\d.,]+[kKmMbB]?)/([\\d.,]+[kKmMbB]?)$");
	private static final Pattern STORED = Pattern.compile("^Stored Dungeon Score:\\s*([\\d.,]+[kKmMbB]?)$");
	private static final Pattern APPEARS_IN = Pattern.compile("^Appears In:\\s*(.+)$");
	private static final Pattern ODDS = Pattern.compile("^Odds:\\s*(.+?)\\s*\\(([\\d.]+)%\\)$");
	private static final Pattern ITEM_SCORE = Pattern.compile("^Dungeon Score:\\s*([\\d.,]+[kKmMbB]?)/([\\d.,]+[kKmMbB]?)$");

	private RngMeterParser() {
	}

	/**
	 * One floor meter as shown on its button.
	 *
	 * @param selectedItem  display name of the selected drop, null when nothing is selected
	 * @param storedScore   exact stored score, null when printed abbreviated or unreadable
	 * @param requiredScore exact requirement when printed in full, otherwise null
	 */
	public record Reading(
		FloorId floor,
		String selectedItem,
		Long storedScore,
		String storedText,
		Long requiredScore,
		String requiredText,
		Double progressPercent
	) {
		public boolean hasSelection() {
			return selectedItem != null;
		}
	}

	/**
	 * One selectable drop on a floor screen.
	 *
	 * @param requiredScore exact requirement, null when abbreviated
	 * @param actionLine    last lore line, kept raw because the selected marker has not been seen yet
	 */
	public record ItemRequirement(String name, ChestTier appearsIn, String oddsLabel, Double oddsPercent, Long requiredScore, String requiredText, Long storedScore, String actionLine) {
	}

	public record Overview(List<Reading> readings) {
		public Overview {
			readings = List.copyOf(readings);
		}
	}

	public record FloorMenu(FloorId floor, Reading header, List<ItemRequirement> items) {
		public FloorMenu {
			items = List.copyOf(items);
		}

		public ItemRequirement item(String name) {
			for (ItemRequirement item : items) if (item.name().equals(name)) return item;
			return null;
		}
	}

	public static boolean isOverviewTitle(String title) {
		return OVERVIEW_TITLE.equals(title == null ? null : title.trim());
	}

	public static FloorId floorMenuFloor(String title) {
		if (title == null) return null;
		Matcher matcher = FLOOR_METER_NAME.matcher(title.trim());
		return matcher.matches() ? FloorId.parse(matcher.group(1)) : null;
	}

	public static Overview parseOverview(LuckProbeRecorder.ScreenSample screen) {
		if (screen == null || !isOverviewTitle(screen.title())) return null;
		List<Reading> readings = new ArrayList<>();
		for (LuckProbeRecorder.SlotSample slot : screen.slots()) {
			FloorId floor = floorMenuFloor(slot.name());
			if (floor == null) continue;
			Reading reading = parseReading(floor, slot.lore());
			if (reading != null) readings.add(reading);
		}
		return readings.isEmpty() ? null : new Overview(readings);
	}

	public static FloorMenu parseFloorMenu(LuckProbeRecorder.ScreenSample screen) {
		if (screen == null) return null;
		FloorId floor = floorMenuFloor(screen.title());
		if (floor == null) return null;
		Reading header = null;
		List<ItemRequirement> items = new ArrayList<>();
		for (LuckProbeRecorder.SlotSample slot : screen.slots()) {
			if (floor == floorMenuFloor(slot.name())) {
				header = parseReading(floor, slot.lore());
				continue;
			}
			ItemRequirement item = parseItem(slot);
			if (item != null) items.add(item);
		}
		if (header == null && items.isEmpty()) return null;
		return new FloorMenu(floor, header, items);
	}

	static Reading parseReading(FloorId floor, List<String> lore) {
		String selected = null;
		boolean unselected = false;
		Long stored = null;
		String storedText = null;
		Long required = null;
		String requiredText = null;
		Double progress = null;
		for (int i = 0; i < lore.size(); i++) {
			String line = lore.get(i).trim();
			if (line.equals("Selected Drop") && i + 1 < lore.size()) {
				String next = lore.get(i + 1).trim();
				if (!next.isEmpty()) selected = next;
				continue;
			}
			if (line.startsWith("You don't have an RNG drop")) {
				unselected = true;
				continue;
			}
			Matcher storedLine = STORED.matcher(line);
			if (storedLine.matches()) {
				storedText = storedLine.group(1);
				stored = LuckText.exactNumber(storedText);
				continue;
			}
			Matcher progressLine = PROGRESS.matcher(line);
			if (progressLine.matches()) {
				progress = Double.parseDouble(progressLine.group(1));
				continue;
			}
			Matcher pair = SCORE_PAIR.matcher(line);
			if (pair.matches() && selected != null) {
				storedText = pair.group(1);
				stored = LuckText.exactNumber(storedText);
				requiredText = pair.group(2);
				required = LuckText.exactNumber(requiredText);
			}
		}
		if (selected == null && !unselected) return null;
		if (selected != null && unselected) return null;
		return new Reading(floor, selected, stored, storedText, required, requiredText, progress);
	}

	static ItemRequirement parseItem(LuckProbeRecorder.SlotSample slot) {
		ChestTier appearsIn = null;
		boolean sawAppearsIn = false;
		String oddsLabel = null;
		Double oddsPercent = null;
		Long required = null;
		String requiredText = null;
		Long stored = null;
		String action = null;
		for (String raw : slot.lore()) {
			String line = raw.trim();
			if (line.isEmpty()) continue;
			action = line;
			Matcher appears = APPEARS_IN.matcher(line);
			if (appears.matches()) {
				sawAppearsIn = true;
				appearsIn = LuckText.chestTier(appears.group(1));
				continue;
			}
			Matcher odds = ODDS.matcher(line);
			if (odds.matches()) {
				oddsLabel = odds.group(1);
				oddsPercent = Double.parseDouble(odds.group(2));
				continue;
			}
			Matcher score = ITEM_SCORE.matcher(line);
			if (score.matches()) {
				stored = LuckText.exactNumber(score.group(1));
				requiredText = score.group(2);
				required = LuckText.exactNumber(requiredText);
			}
		}
		if (!sawAppearsIn || requiredText == null) return null;
		return new ItemRequirement(slot.name().trim(), appearsIn, oddsLabel, oddsPercent, required, requiredText, stored, action);
	}
}
