package dev.vy.drt.history;

import dev.vy.drt.config.DungeonLootEntry;
import dev.vy.drt.config.DungeonRunRecord;
import dev.vy.drt.luck.ChestTier;
import dev.vy.drt.luck.capture.LuckRunRecord;
import dev.vy.drt.luck.capture.LuckText;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The chest tabs for one recorded run: every chest it offered, what DRT actually saw in each, and
 * which were purchased. Purchased chests show the logged loot. Other offers are informational only
 * and never count towards profit. Chests the run offered but DRT never saw stay "not recorded".
 */
public final class ChestOfferTabs {
	public enum State { PURCHASED_LOGGED, OBSERVED, NOT_RECORDED }

	public record Tab(ChestTier tier, State state, boolean purchased, List<DungeonLootEntry> contents, Long costCoins, List<DungeonRunRecord> purchases) {
		public Tab {
			contents = List.copyOf(contents);
			purchases = List.copyOf(purchases);
		}

		public String label() {
			String name = tier.name();
			return name.charAt(0) + name.substring(1).toLowerCase(Locale.ROOT);
		}
	}

	private ChestOfferTabs() {
	}

	/**
	 * Tabs in chest order. {@code run} may be null (legacy records), in which case only the logged
	 * purchases are known. {@code purchases} are the loot records linked to the same run.
	 */
	public static List<Tab> build(LuckRunRecord run, List<DungeonRunRecord> purchases) {
		Map<ChestTier, List<DungeonRunRecord>> logged = new EnumMap<>(ChestTier.class);
		for (DungeonRunRecord record : purchases == null ? List.<DungeonRunRecord>of() : purchases) {
			ChestTier tier = record == null ? null : LuckText.chestTier(record.chestTitle);
			if (tier != null) logged.computeIfAbsent(tier, key -> new ArrayList<>()).add(record);
		}
		Map<ChestTier, LuckRunRecord.LuckChestRecord> seen = new EnumMap<>(ChestTier.class);
		Map<ChestTier, Boolean> offered = new EnumMap<>(ChestTier.class);
		if (run != null) {
			for (LuckRunRecord.LuckChestRecord chest : run.chests) {
				ChestTier tier = ChestTier.parse(chest.tier);
				if (tier == null) continue;
				seen.put(tier, chest);
				offered.put(tier, true);
			}
			if (run.coverage != null && run.coverage.expectedChests != null) {
				for (String name : run.coverage.expectedChests) {
					ChestTier tier = ChestTier.parse(name);
					if (tier != null) offered.put(tier, true);
				}
			}
		}
		for (ChestTier tier : logged.keySet()) offered.put(tier, true);

		List<Tab> tabs = new ArrayList<>();
		for (ChestTier tier : ChestTier.values()) {
			if (!offered.containsKey(tier)) continue;
			List<DungeonRunRecord> bought = logged.getOrDefault(tier, List.of());
			LuckRunRecord.LuckChestRecord chest = seen.get(tier);
			boolean purchased = !bought.isEmpty() || (chest != null && Boolean.TRUE.equals(chest.purchased));
			if (!bought.isEmpty()) {
				List<DungeonLootEntry> loot = new ArrayList<>();
				long cost = 0L;
				for (DungeonRunRecord record : bought) {
					if (record.lootEntries != null) for (DungeonLootEntry entry : record.lootEntries) if (entry != null) loot.add(entry.copy());
					cost += Math.max(0L, record.chestCostCoins);
				}
				tabs.add(new Tab(tier, State.PURCHASED_LOGGED, true, loot, cost, bought));
				continue;
			}
			LuckRunRecord.LuckOfferSnapshot offer = chest == null || chest.offers.isEmpty() ? null : chest.offers.get(chest.offers.size() - 1);
			if (offer == null) {
				tabs.add(new Tab(tier, State.NOT_RECORDED, purchased, List.of(), chest == null ? null : chest.costCoins, List.of()));
				continue;
			}
			List<DungeonLootEntry> contents = new ArrayList<>();
			for (LuckRunRecord.LuckContentLine line : offer.contents) contents.add(lootEntry(line));
			tabs.add(new Tab(tier, State.OBSERVED, purchased, contents, chest.costCoins, List.of()));
		}
		return tabs;
	}

	/** Tabs are only worth showing when the run offered more than one chest. */
	public static boolean showTabs(List<Tab> tabs) {
		return tabs != null && tabs.size() >= 2;
	}

	/** The tab for the selected history chest, else the first purchased one, else the first. */
	public static int defaultIndex(List<Tab> tabs, String selectedChestTitle) {
		if (tabs == null || tabs.isEmpty()) return -1;
		ChestTier selected = LuckText.chestTier(selectedChestTitle);
		for (int i = 0; i < tabs.size(); i++) if (tabs.get(i).tier() == selected) return i;
		for (int i = 0; i < tabs.size(); i++) if (tabs.get(i).purchased()) return i;
		return 0;
	}

	/** Moves {@code delta} steps and stops at either end instead of wrapping. */
	public static int step(int index, int delta, int size) {
		if (size <= 0) return -1;
		return Math.max(0, Math.min(size - 1, index + delta));
	}

	/** Prices an offer line like a logged loot entry. Unnamed lines keep their text and price at 0. */
	public static DungeonLootEntry lootEntry(LuckRunRecord.LuckContentLine line) {
		String name = line.name == null || line.name.isBlank() ? line.text : line.name;
		int quantity = Math.max(1, line.quantity);
		String key = line.key == null ? "" : line.key;
		if ("ESSENCE".equals(line.kind) && !key.isEmpty()) key = key.replaceFirst("_\\d+$", "");
		return new DungeonLootEntry(name, key, quantity);
	}
}
