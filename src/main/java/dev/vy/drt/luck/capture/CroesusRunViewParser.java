package dev.vy.drt.luck.capture;

import dev.vy.drt.luck.ChestTier;
import dev.vy.drt.luck.data.FloorTable;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses the Croesus chest list for one run ({@code Catacombs - Floor V}, or
 * {@code Master Catacombs - Floor V} for Master Mode). Each chest button lists its full contents,
 * including essence, and its cost. A title without a Master marker leaves the mode unknown rather
 * than assuming Normal.
 */
public final class CroesusRunViewParser {
	private static final Pattern TITLE = Pattern.compile("^(Master(?:\\s+Mode)?\\s+)?(?:The\\s+)?Catacombs\\s+-\\s+Floor\\s+([IVXL]+|\\d+)$",
		Pattern.CASE_INSENSITIVE);
	private static final Pattern COINS = Pattern.compile("^(\\d[\\d,]*)\\s+Coins$");

	private CroesusRunViewParser() {
	}

	/**
	 * @param costCoins exact coin cost, 0 for FREE, null when the cost line was not understood
	 */
	public record ChestOffer(ChestTier tier, int slot, List<LuckText.ContentLine> contents, Long costCoins, String costText, List<String> rawLines) {
		public ChestOffer {
			contents = List.copyOf(contents);
			rawLines = List.copyOf(rawLines);
		}

		public List<String> contentTexts() {
			List<String> out = new ArrayList<>(contents.size());
			for (LuckText.ContentLine line : contents) out.add(line.text());
			return out;
		}
	}

	/**
	 * @param masterMode true when the title says Master Mode, null when it does not say
	 */
	public record RunView(String title, int floorNumber, Boolean masterMode, List<ChestOffer> chests, List<String> availableModifiers) {
		public RunView {
			chests = List.copyOf(chests);
			availableModifiers = List.copyOf(availableModifiers);
		}
	}

	public static boolean isRunViewTitle(String title) {
		return title != null && TITLE.matcher(title.trim()).matches();
	}

	/**
	 * @param floorTables resolves item names for a floor once the mode is known; may return null
	 */
	public static RunView parse(LuckProbeRecorder.ScreenSample screen, Function<Integer, FloorTable> floorTables) {
		if (screen == null) return null;
		Matcher title = TITLE.matcher(screen.title().trim());
		if (!title.matches()) return null;
		String number = title.group(2);
		int floorNumber = Character.isDigit(number.charAt(0)) ? Integer.parseInt(number) : LuckText.roman(number);
		Boolean masterMode = title.group(1) != null ? Boolean.TRUE : null;
		FloorTable table = floorTables == null ? null : floorTables.apply(floorNumber);

		List<ChestOffer> chests = new ArrayList<>();
		List<String> modifiers = new ArrayList<>();
		for (LuckProbeRecorder.SlotSample slot : screen.slots()) {
			if ("Chest Modifiers".equals(slot.name())) {
				modifiers.addAll(modifierLines(slot.lore()));
				continue;
			}
			ChestOffer offer = parseChest(slot, table);
			if (offer != null) chests.add(offer);
		}
		if (chests.isEmpty()) return null;
		chests.sort((a, b) -> Integer.compare(a.tier().ordinal(), b.tier().ordinal()));
		return new RunView(screen.title().trim(), floorNumber, masterMode, chests, modifiers);
	}

	static ChestOffer parseChest(LuckProbeRecorder.SlotSample slot, FloorTable table) {
		ChestTier tier = LuckText.chestTier(slot.name());
		List<String> lore = slot.lore();
		if (tier == null || lore.isEmpty() || !"Contents".equals(lore.get(0).trim())) return null;
		List<LuckText.ContentLine> contents = new ArrayList<>();
		int i = 1;
		for (; i < lore.size(); i++) {
			String line = lore.get(i).trim();
			if (line.isEmpty()) break;
			contents.add(LuckText.contentLine(line, table));
		}
		Long cost = null;
		String costText = null;
		for (; i < lore.size(); i++) {
			if (!"Cost".equals(lore.get(i).trim())) continue;
			if (i + 1 < lore.size()) {
				costText = lore.get(i + 1).trim();
				cost = parseCost(costText);
			}
			break;
		}
		return new ChestOffer(tier, slot.slot(), contents, cost, costText, lore);
	}

	static Long parseCost(String text) {
		if (text == null) return null;
		if (text.trim().toUpperCase(Locale.ROOT).equals("FREE")) return 0L;
		Matcher coins = COINS.matcher(text.trim());
		return coins.matches() ? Long.parseLong(coins.group(1).replace(",", "")) : null;
	}

	private static List<String> modifierLines(List<String> lore) {
		List<String> out = new ArrayList<>();
		boolean inList = false;
		for (String raw : lore) {
			String line = raw.trim();
			if (line.equals("Available Modifiers:")) {
				inList = true;
				continue;
			}
			if (!inList) continue;
			if (line.isEmpty() || line.startsWith("Click to")) break;
			out.add(line);
		}
		return out;
	}
}
