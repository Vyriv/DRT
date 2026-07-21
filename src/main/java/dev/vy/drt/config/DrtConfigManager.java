package dev.vy.drt.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.vy.drt.DungeonRunTracker;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Set;
import net.fabricmc.loader.api.FabricLoader;

public final class DrtConfigManager {
	private static final float MIN_HUD_SCALE = 0.5F;
	private static final float MAX_HUD_SCALE = 2.0F;
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final Path CONFIG_PATH = FabricLoader.getInstance().getConfigDir().resolve("drt.json");
	private static DrtConfig config = new DrtConfig();

	private DrtConfigManager() {
	}

	public static synchronized void load() {
		try {
			Files.createDirectories(CONFIG_PATH.getParent());
			if (Files.notExists(CONFIG_PATH)) {
				config = new DrtConfig();
				applyFreshOverlayDefaults(config);
				save();
				return;
			}
			try (Reader reader = Files.newBufferedReader(CONFIG_PATH, StandardCharsets.UTF_8)) {
				JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
				DrtConfig loaded = GSON.fromJson(root, DrtConfig.class);
				if (loaded == null) loaded = new DrtConfig();
				if (loaded.floorRunCounts == null) loaded.floorRunCounts = new LinkedHashMap<>();
				if (loaded.floorRunTimeMs == null) loaded.floorRunTimeMs = new LinkedHashMap<>();
				if (loaded.runHistory == null) loaded.runHistory = new ArrayList<>();
				loaded.hudScale = clampHudScale(loaded.hudScale);
				loaded.hudVisibilityMode = normalizeHudVisibilityMode(loaded.hudVisibilityMode);
				normalizeOnboardingSettings(loaded);
				if (loaded.legacyRunsCompleted > 0 && !loaded.floorRunCounts.containsKey("M5")) {
					loaded.floorRunCounts.put("M5", loaded.legacyRunsCompleted);
				}
				boolean migrated = normalizeRunHistory(loaded);
				if (migrateOverlayPreset(loaded, root)) migrated = true;
				if (normalizeCustomOverlayLayout(loaded)) migrated = true;
				config = loaded;
				if (migrated) save();
			} catch (com.google.gson.JsonSyntaxException e) {
				DungeonRunTracker.LOGGER.error("[DRT] Config file malformed, resetting to defaults", e);
				config = new DrtConfig();
				applyFreshOverlayDefaults(config);
				save();
			}
		} catch (IOException e) {
			DungeonRunTracker.LOGGER.error("[DRT] Failed to load config", e);
			config = new DrtConfig();
			applyFreshOverlayDefaults(config);
		}
	}

	public static synchronized void save() {
		try {
			Files.createDirectories(CONFIG_PATH.getParent());
			try (Writer writer = Files.newBufferedWriter(CONFIG_PATH, StandardCharsets.UTF_8)) {
				GSON.toJson(config, writer);
			}
		} catch (IOException e) {
			DungeonRunTracker.LOGGER.error("[DRT] Failed to save config", e);
		}
	}

	public static synchronized DrtConfig getConfig() {
		return config;
	}

	public static synchronized String getDungeonSelectedFloor() {
		return config.selectedFloor;
	}

	public static synchronized void updateSelectedFloor(String floor) {
		config.selectedFloor = (floor == null || floor.isBlank()) ? null : floor;
		save();
	}

	public static synchronized List<DungeonRunRecord> getRunHistory() {
		List<DungeonRunRecord> records = new ArrayList<>();
		for (DungeonRunRecord r : config.runHistory) {
			if (r != null) records.add(r.copy());
		}
		return records;
	}

	public static synchronized void addRunRecord(DungeonRunRecord record) {
		if (record == null || record.lootEntries == null || record.lootEntries.isEmpty()) return;
		if (config.runHistory == null) config.runHistory = new ArrayList<>();
		DungeonRunRecord stored = record.copy();
		if (stored.chestNumber <= 0) {
			stored.chestNumber = nextChestLogNumber();
		} else {
			config.nextChestLogNumber = Math.max(Math.max(1, config.nextChestLogNumber), stored.chestNumber + 1);
		}
		stored.normalizeCostBreakdown();
		removeDuplicateRunRecord(stored);
		config.runHistory.add(stored);
		save();
	}

	public static synchronized void updateFloorRunCount(String floor, int count) {
		if (config.floorRunCounts == null) config.floorRunCounts = new LinkedHashMap<>();
		config.floorRunCounts.put(floor, Math.max(0, count));
		save();
	}

	public static synchronized void updateFloorRunTimeMs(String floor, long totalMs) {
		if (config.floorRunTimeMs == null) config.floorRunTimeMs = new LinkedHashMap<>();
		config.floorRunTimeMs.put(floor, Math.max(0L, totalMs));
		save();
	}

	public static synchronized void addFloorRunTimeMs(String floor, long deltaMs) {
		if (deltaMs <= 0L || floor == null || floor.isBlank()) return;
		if (config.floorRunTimeMs == null) config.floorRunTimeMs = new LinkedHashMap<>();
		long next = Math.max(0L, config.floorRunTimeMs.getOrDefault(floor, 0L) + deltaMs);
		config.floorRunTimeMs.put(floor, next);
		save();
	}

	public static synchronized void updateHudPosition(int x, int y) {
		config.hudX = Math.max(0, x);
		config.hudY = Math.max(0, y);
		save();
	}

	public static synchronized void updateHudLayout(int x, int y, float scale) {
		config.hudX = Math.max(0, x);
		config.hudY = Math.max(0, y);
		config.hudScale = clampHudScale(scale);
		save();
	}

	public static synchronized void updateHudVisibilityMode(String mode) {
		config.hudVisibilityMode = normalizeHudVisibilityMode(mode);
		save();
	}

	public static synchronized void updateHudOverlayPreset(String preset) {
		config.hudOverlayPreset = normalizeOverlayPreset(preset, false);
		save();
	}

	public static synchronized void updateCustomOverlayLayout(String layout) {
		config.customOverlayLayout = layout == null ? "" : layout.replace("\r\n", "\n").replace('\r', '\n');
		save();
	}

	public static synchronized void updateOverlaySettings(String preset, String customLayout) {
		config.hudOverlayPreset = normalizeOverlayPreset(preset, false);
		if (customLayout != null) {
			config.customOverlayLayout = customLayout.replace("\r\n", "\n").replace('\r', '\n');
		}
		save();
	}

	public static synchronized void updateOnboardingSettings(
		boolean onboardingComplete,
		String kuudraFaction,
		boolean kuudraPetEnabled,
		String kuudraPetRarity,
		int kuudraPetLevel,
		boolean forceSalvageArmor,
		boolean forceSalvageWands,
		boolean forceSalvageEquipment,
		boolean coolForgedEnabled,
		int coolForgedLevel,
		String bazaarPriceMode
	) {
		config.onboardingComplete = onboardingComplete;
		config.kuudraFaction = normalizeKuudraFaction(kuudraFaction);
		config.kuudraPetEnabled = kuudraPetEnabled;
		config.kuudraPetRarity = normalizePetRarity(kuudraPetRarity);
		config.kuudraPetLevel = clamp(kuudraPetLevel, 1, 100);
		config.forceSalvageArmor = forceSalvageArmor;
		config.forceSalvageWands = forceSalvageWands;
		config.forceSalvageEquipment = forceSalvageEquipment;
		config.coolForgedEnabled = coolForgedEnabled;
		config.coolForgedLevel = clamp(coolForgedLevel, 1, 5);
		config.bazaarPriceMode = normalizeBazaarPriceMode(bazaarPriceMode);
		save();
	}

	public static synchronized void clearAllData() {
		if (config.floorRunCounts != null) config.floorRunCounts.clear();
		if (config.floorRunTimeMs != null) config.floorRunTimeMs.clear();
		config.legacyRunsCompleted = 0;
		config.runHistory = new ArrayList<>();
		config.nextChestLogNumber = 1;
		save();
	}

	public static synchronized void clearFloorData(String floor) {
		if (config.floorRunCounts != null) config.floorRunCounts.remove(floor);
		if (config.floorRunTimeMs != null) config.floorRunTimeMs.remove(floor);
		if (config.runHistory != null) config.runHistory.removeIf(r -> r != null && floor.equals(r.floor));
		save();
	}

	public static synchronized boolean removeRunRecord(DungeonRunRecord target) {
		if (target == null || config.runHistory == null) return false;
		boolean removed = config.runHistory.removeIf(r -> matchesRunRecord(r, target));
		if (removed) {
			decrementFloorRunCount(target.floor);
			save();
		}
		return removed;
	}

	/**
	 * Replaces the history entry matching {@code matchKey} with {@code replacement}.
	 * Identity fields (timestamp, chest number, floor, chest title) stay from the match key
	 * so the row remains addressable; loot/costs/profit come from the replacement.
	 */
	public static synchronized boolean updateRunRecord(DungeonRunRecord matchKey, DungeonRunRecord replacement) {
		if (matchKey == null || replacement == null || config.runHistory == null) return false;
		for (int i = 0; i < config.runHistory.size(); i++) {
			DungeonRunRecord existing = config.runHistory.get(i);
			if (!matchesRunRecord(existing, matchKey)) continue;
			DungeonRunRecord stored = replacement.copy();
			stored.timestampEpochMillis = existing.timestampEpochMillis;
			stored.chestNumber = existing.chestNumber;
			stored.runNumber = existing.runNumber;
			stored.normalizeCostBreakdown();
			config.runHistory.set(i, stored);
			String oldFloor = existing.floor == null ? "UNKNOWN" : existing.floor;
			String newFloor = stored.floor == null ? "UNKNOWN" : stored.floor;
			if (!oldFloor.equals(newFloor)) {
				decrementFloorRunCount(oldFloor);
				incrementFloorRunCount(newFloor);
			}
			save();
			return true;
		}
		return false;
	}

	private static void decrementFloorRunCount(String floor) {
		if (floor == null || floor.isBlank() || config.floorRunCounts == null) return;
		int current = config.floorRunCounts.getOrDefault(floor, 0);
		if (current <= 1) config.floorRunCounts.remove(floor);
		else config.floorRunCounts.put(floor, current - 1);
	}

	private static void incrementFloorRunCount(String floor) {
		if (floor == null || floor.isBlank()) return;
		if (config.floorRunCounts == null) config.floorRunCounts = new LinkedHashMap<>();
		config.floorRunCounts.merge(floor, 1, Integer::sum);
	}

	private static boolean matchesRunRecord(DungeonRunRecord left, DungeonRunRecord right) {
		if (left == null || right == null) return false;
		if (left.timestampEpochMillis != right.timestampEpochMillis) return false;
		if (left.chestNumber != right.chestNumber) return false;
		return sameText(left.floor, right.floor) && sameText(left.chestTitle, right.chestTitle);
	}

	private static float clampHudScale(float scale) {
		if (!Float.isFinite(scale) || scale <= 0.0F) return 1.0F;
		return Math.max(MIN_HUD_SCALE, Math.min(MAX_HUD_SCALE, scale));
	}

	private static String normalizeHudVisibilityMode(String mode) {
		if (mode == null || mode.isBlank()) return "DEFAULT";
		String normalized = mode.trim().toUpperCase(java.util.Locale.ROOT);
		return switch (normalized) {
			case "GLOBAL", "DEFAULT", "DHUB" -> normalized;
			default -> "DEFAULT";
		};
	}

	private static void normalizeOnboardingSettings(DrtConfig loaded) {
		loaded.kuudraFaction = normalizeKuudraFaction(loaded.kuudraFaction);
		loaded.kuudraPetRarity = normalizePetRarity(loaded.kuudraPetRarity);
		loaded.kuudraPetLevel = clamp(loaded.kuudraPetLevel, 1, 100);
		loaded.coolForgedLevel = clamp(loaded.coolForgedLevel, 1, 5);
		loaded.bazaarPriceMode = normalizeBazaarPriceMode(loaded.bazaarPriceMode);
	}

	private static void applyFreshOverlayDefaults(DrtConfig cfg) {
		cfg.hudOverlayPreset = "MODERN";
		if (cfg.customOverlayLayout == null || cfg.customOverlayLayout.isBlank()) {
			cfg.customOverlayLayout = defaultCustomOverlayLayout();
		}
	}

	private static boolean migrateOverlayPreset(DrtConfig loaded, JsonObject root) {
		boolean missingField = root == null || !root.has("hudOverlayPreset");
		String current = loaded.hudOverlayPreset;
		if (!missingField && current != null && !current.isBlank()) {
			String normalized = normalizeOverlayPreset(current, false);
			if (!normalized.equals(current)) {
				loaded.hudOverlayPreset = normalized;
				return true;
			}
			return false;
		}
		// Existing installs without the field keep Legacy; brand-new empty configs use Modern.
		boolean hasPriorData = loaded.onboardingComplete
			|| loaded.legacyRunsCompleted > 0
			|| (loaded.floorRunCounts != null && !loaded.floorRunCounts.isEmpty())
			|| (loaded.runHistory != null && !loaded.runHistory.isEmpty())
			|| loaded.hudX != 10
			|| loaded.hudY != 10
			|| Math.abs(loaded.hudScale - 1.0F) > 0.001F;
		loaded.hudOverlayPreset = hasPriorData ? "LEGACY" : "MODERN";
		return true;
	}

	private static boolean normalizeCustomOverlayLayout(DrtConfig loaded) {
		if (loaded.customOverlayLayout != null && !loaded.customOverlayLayout.isBlank()) {
			String normalized = loaded.customOverlayLayout.replace("\r\n", "\n").replace('\r', '\n');
			if (!normalized.equals(loaded.customOverlayLayout)) {
				loaded.customOverlayLayout = normalized;
				return true;
			}
			return false;
		}
		loaded.customOverlayLayout = defaultCustomOverlayLayout();
		return true;
	}

	private static String normalizeOverlayPreset(String preset, boolean freshDefault) {
		if (preset == null || preset.isBlank()) return freshDefault ? "MODERN" : "LEGACY";
		String normalized = preset.trim().toUpperCase(java.util.Locale.ROOT);
		return switch (normalized) {
			case "LEGACY", "MODERN", "SESSION", "DETAILED", "CLASSIC", "CUSTOM" -> normalized;
			default -> freshDefault ? "MODERN" : "LEGACY";
		};
	}

	private static String defaultCustomOverlayLayout() {
		return "DRT [{floor}]\n"
			+ "Session: {runs.session} runs | {runs.avg} avg | {runs.hour}/hr\n"
			+ "Profit: {profit.session} | {profit.run}/run | {profit.hour}/hr";
	}

	private static String normalizeKuudraFaction(String faction) {
		if (faction == null || faction.isBlank()) return "MAGE";
		String normalized = faction.trim().toUpperCase(java.util.Locale.ROOT);
		return switch (normalized) {
			case "MAGE", "BARBARIAN" -> normalized;
			default -> "MAGE";
		};
	}

	private static String normalizePetRarity(String rarity) {
		if (rarity == null || rarity.isBlank()) return "LEGENDARY";
		String normalized = rarity.trim().toUpperCase(java.util.Locale.ROOT);
		return switch (normalized) {
			case "COMMON", "UNCOMMON", "RARE", "EPIC", "LEGENDARY" -> normalized;
			default -> "LEGENDARY";
		};
	}

	private static String normalizeBazaarPriceMode(String mode) {
		if (mode == null || mode.isBlank()) return "INSTANT";
		String normalized = mode.trim().toUpperCase(java.util.Locale.ROOT);
		return switch (normalized) {
			case "INSTANT", "INSTANT_SELL", "INSTANT_BUY" -> "INSTANT";
			case "ORDER", "SELL_OFFER", "BUY_ORDER" -> "ORDER";
			default -> "INSTANT";
		};
	}

	private static int clamp(int value, int min, int max) {
		return Math.max(min, Math.min(max, value));
	}

	private static boolean normalizeRunHistory(DrtConfig loaded) {
		boolean changed = false;
		if (loaded.nextChestLogNumber <= 0) {
			loaded.nextChestLogNumber = 1;
			changed = true;
		}
		Set<Integer> used = new HashSet<>();
		int next = Math.max(1, loaded.nextChestLogNumber);
		for (DungeonRunRecord record : loaded.runHistory) {
			if (record == null) continue;
			if (record.normalizeCostBreakdown()) changed = true;
			if (record.chestNumber > 0 && used.add(record.chestNumber)) {
				next = Math.max(next, record.chestNumber + 1);
				continue;
			}
			while (used.contains(next)) next++;
			record.chestNumber = next++;
			used.add(record.chestNumber);
			changed = true;
		}
		if (loaded.nextChestLogNumber != next) {
			loaded.nextChestLogNumber = next;
			changed = true;
		}
		if (deduplicateRunHistory(loaded.runHistory)) {
			changed = true;
		}
		return changed;
	}

	private static boolean deduplicateRunHistory(List<DungeonRunRecord> records) {
		if (records == null || records.size() < 2) return false;
		boolean changed = false;
		for (int i = 0; i < records.size(); i++) {
			DungeonRunRecord left = records.get(i);
			if (left == null) continue;
			for (int j = records.size() - 1; j > i; j--) {
				DungeonRunRecord right = records.get(j);
				if (!areDuplicateLootRecords(left, right)) continue;
				DungeonRunRecord keep = preferredLootRecord(left, right);
				records.set(i, keep);
				records.remove(j);
				left = keep;
				changed = true;
			}
		}
		return changed;
	}

	private static void removeDuplicateRunRecord(DungeonRunRecord incoming) {
		if (incoming == null || config.runHistory == null || config.runHistory.isEmpty()) return;
		for (int i = config.runHistory.size() - 1; i >= 0; i--) {
			DungeonRunRecord existing = config.runHistory.get(i);
			if (!areDuplicateLootRecords(existing, incoming)) continue;
			DungeonRunRecord keep = preferredLootRecord(existing, incoming);
			if (keep == existing) return;
			config.runHistory.remove(i);
			return;
		}
	}

	private static DungeonRunRecord preferredLootRecord(DungeonRunRecord left, DungeonRunRecord right) {
		if (left == null) return right;
		if (right == null) return left;
		int leftQuantity = totalLootQuantity(left);
		int rightQuantity = totalLootQuantity(right);
		if (leftQuantity != rightQuantity) return leftQuantity < rightQuantity ? left : right;
		if (left.chestValueCoins != right.chestValueCoins) return left.chestValueCoins <= right.chestValueCoins ? left : right;
		return left.timestampEpochMillis <= right.timestampEpochMillis ? left : right;
	}

	private static boolean areDuplicateLootRecords(DungeonRunRecord left, DungeonRunRecord right) {
		if (left == null || right == null) return false;
		if (Math.abs(left.timestampEpochMillis - right.timestampEpochMillis) > 30_000L) return false;
		if (!sameText(left.floor, right.floor)) return false;
		if (!sameText(left.chestTitle, right.chestTitle)) return false;
		if (left.totalCostCoins() != right.totalCostCoins()) return false;
		return sameLootShape(left, right);
	}

	private static boolean sameLootShape(DungeonRunRecord left, DungeonRunRecord right) {
		if (left.lootEntries == null || right.lootEntries == null || left.lootEntries.isEmpty() || right.lootEntries.isEmpty()) return false;
		LinkedHashMap<String, Integer> leftCounts = lootCounts(left);
		LinkedHashMap<String, Integer> rightCounts = lootCounts(right);
		if (!leftCounts.keySet().equals(rightCounts.keySet())) return false;
		Integer multiplier = null;
		for (String key : leftCounts.keySet()) {
			int leftQuantity = Math.max(1, leftCounts.getOrDefault(key, 0));
			int rightQuantity = Math.max(1, rightCounts.getOrDefault(key, 0));
			if (leftQuantity == rightQuantity) continue;
			if (leftQuantity == rightQuantity * 2) {
				if (multiplier != null && multiplier != 2) return false;
				multiplier = 2;
				continue;
			}
			if (rightQuantity == leftQuantity * 2) {
				if (multiplier != null && multiplier != -2) return false;
				multiplier = -2;
				continue;
			}
			return false;
		}
		return true;
	}

	private static LinkedHashMap<String, Integer> lootCounts(DungeonRunRecord record) {
		LinkedHashMap<String, Integer> counts = new LinkedHashMap<>();
		if (record == null || record.lootEntries == null) return counts;
		for (DungeonLootEntry entry : record.lootEntries) {
			if (entry == null) continue;
			counts.merge(lootKey(entry), Math.max(1, entry.quantity), Integer::sum);
		}
		return counts;
	}

	private static int totalLootQuantity(DungeonRunRecord record) {
		int total = 0;
		if (record == null || record.lootEntries == null) return total;
		for (DungeonLootEntry entry : record.lootEntries) {
			if (entry != null) total += Math.max(1, entry.quantity);
		}
		return total;
	}

	private static String lootKey(DungeonLootEntry entry) {
		if (entry == null) return "";
		String itemId = entry.itemId == null ? "" : entry.itemId.trim().toUpperCase(java.util.Locale.ROOT);
		if (!itemId.isBlank()) return itemId;
		return entry.rawName == null ? "" : entry.rawName.trim().toUpperCase(java.util.Locale.ROOT);
	}

	private static boolean sameText(String left, String right) {
		String leftText = left == null ? "" : left.trim();
		String rightText = right == null ? "" : right.trim();
		return leftText.equalsIgnoreCase(rightText);
	}

	private static int nextChestLogNumber() {
		if (config.nextChestLogNumber <= 0) config.nextChestLogNumber = 1;
		return config.nextChestLogNumber++;
	}
}
