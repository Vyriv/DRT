package dev.vy.drt.client.screen;

import dev.vy.drt.config.DungeonLootEntry;
import dev.vy.drt.config.DungeonRunRecord;
import dev.vy.drt.history.LootHistoryProjection;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/** Immutable search representation of observed loot. Built only when saved history changes. */
final class LootHistorySearchIndex {
	private static final Pattern FORMATTING = Pattern.compile("(?i)[§&][0-9A-FK-ORX]");
	private static final Pattern NON_WORD = Pattern.compile("[^\\p{L}\\p{N}]+");
	private static final Pattern SPACES = Pattern.compile("\\s+");

	record Chest(String key, List<DungeonLootEntry> entries) {
		Chest {
			key = key == null ? "" : key;
			entries = entries == null ? List.of() : List.copyOf(entries);
		}
	}

	record Result(String query, Map<DungeonRunRecord, Set<String>> matchingChests, Set<String> matchingFloors) {
		Result {
			matchingChests = Collections.unmodifiableMap(matchingChests);
			matchingFloors = Set.copyOf(matchingFloors);
		}

		boolean active() {
			return !query.isBlank();
		}

		boolean matches(DungeonRunRecord record) {
			return !active() || matchingChests.containsKey(record);
		}

		boolean matchesChest(DungeonRunRecord record, String chestKey) {
			return active() && matchingChests.getOrDefault(record, Set.of()).contains(chestKey);
		}

		boolean hasAnyMatch() {
			return !matchingChests.isEmpty();
		}

		String firstMatchingFloor(List<String> displayedOrder) {
			if (!active() || displayedOrder == null) return null;
			for (String floor : displayedOrder) if (matchingFloors.contains(floor)) return floor;
			return null;
		}
	}

	private record IndexedChest(String key, List<String> items) {
	}

	private final List<DungeonRunRecord> order;
	private final Map<DungeonRunRecord, List<IndexedChest>> byRecord;

	LootHistorySearchIndex(List<DungeonRunRecord> records, Map<DungeonRunRecord, List<Chest>> chestsByRecord) {
		order = records == null ? List.of() : List.copyOf(records);
		byRecord = new IdentityHashMap<>();
		for (DungeonRunRecord record : order) {
			List<Chest> chests = chestsByRecord == null ? null : chestsByRecord.get(record);
			if (chests == null || chests.isEmpty()) chests = List.of(new Chest("", record == null ? List.of() : record.lootEntries));
			List<IndexedChest> indexed = new ArrayList<>(chests.size());
			for (Chest chest : chests) {
				List<String> items = new ArrayList<>();
				for (DungeonLootEntry entry : chest.entries()) {
					String text = searchableText(entry);
					if (!text.isBlank()) items.add(text);
				}
				indexed.add(new IndexedChest(chest.key(), List.copyOf(items)));
			}
			byRecord.put(record, List.copyOf(indexed));
		}
	}

	Result search(String rawQuery) {
		String query = normalize(rawQuery);
		if (query.isBlank()) return new Result("", new IdentityHashMap<>(), Set.of());
		Map<DungeonRunRecord, Set<String>> records = new IdentityHashMap<>();
		Set<String> floors = new LinkedHashSet<>();
		for (DungeonRunRecord record : order) {
			Set<String> chests = new LinkedHashSet<>();
			for (IndexedChest chest : byRecord.getOrDefault(record, List.of())) {
				if (chest.items().stream().anyMatch(item -> item.contains(query))) chests.add(chest.key());
			}
			if (chests.isEmpty()) continue;
			records.put(record, Set.copyOf(chests));
			if (record != null && record.floor != null) floors.add(record.floor);
		}
		return new Result(query, records, floors);
	}

	static boolean matchesItem(String label, String itemId, String rawQuery) {
		String query = normalize(rawQuery);
		if (query.isBlank()) return false;
		return searchableText(new DungeonLootEntry(label, itemId, 1)).contains(query);
	}

	static boolean matchesRun(LootHistoryProjection.RunEntry run, String rawQuery) {
		return matchesRun(run, rawQuery, true);
	}

	static boolean matchesRun(LootHistoryProjection.RunEntry run, String rawQuery, boolean includeUnopened) {
		return firstMatchingChest(run, rawQuery, includeUnopened) != null;
	}

	static String firstMatchingChest(LootHistoryProjection.RunEntry run, String rawQuery) {
		return firstMatchingChest(run, rawQuery, true);
	}

	static String firstMatchingChest(LootHistoryProjection.RunEntry run, String rawQuery, boolean includeUnopened) {
		if (run == null || normalize(rawQuery).isBlank()) return null;
		for (LootHistoryProjection.ChestOption option : run.chestOptions()) {
			if ((option.purchased() || includeUnopened) && matchesChestOption(option, rawQuery)) return option.chestTitle();
		}
		return null;
	}

	static boolean matchesChestOption(LootHistoryProjection.ChestOption option, String rawQuery) {
		DungeonRunRecord record = option == null ? null : option.record();
		if (record == null || record.lootEntries == null || normalize(rawQuery).isBlank()) return false;
		for (DungeonLootEntry entry : record.lootEntries) {
			if (entry != null && matchesItem(entry.rawName, entry.itemId, rawQuery)) return true;
		}
		return false;
	}

	static String normalize(String text) {
		if (text == null || text.isBlank()) return "";
		String stripped = FORMATTING.matcher(text).replaceAll("");
		stripped = Normalizer.normalize(stripped, Normalizer.Form.NFKC).toLowerCase(Locale.ROOT);
		return SPACES.matcher(NON_WORD.matcher(stripped).replaceAll(" ")).replaceAll(" ").trim();
	}

	private static String searchableText(DungeonLootEntry entry) {
		if (entry == null) return "";
		String name = normalize(entry.rawName);
		String id = normalize(entry.itemId == null ? "" : entry.itemId.replace('_', ' '));
		String enchant = id.startsWith("enchantment ") ? " enchanted book " + id.substring("enchantment ".length()) : "";
		return SPACES.matcher((name + " " + id + enchant).trim()).replaceAll(" ");
	}
}
