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
import java.util.LinkedHashMap;
import java.util.List;
import net.fabricmc.loader.api.FabricLoader;

public final class DrtConfigManager {
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
				save();
				return;
			}
			try (Reader reader = Files.newBufferedReader(CONFIG_PATH, StandardCharsets.UTF_8)) {
				JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
				DrtConfig loaded = GSON.fromJson(root, DrtConfig.class);
				if (loaded == null) loaded = new DrtConfig();
				if (loaded.floorRunCounts == null) loaded.floorRunCounts = new LinkedHashMap<>();
				if (loaded.runHistory == null) loaded.runHistory = new ArrayList<>();
				if (loaded.legacyRunsCompleted > 0 && !loaded.floorRunCounts.containsKey("M5")) {
					loaded.floorRunCounts.put("M5", loaded.legacyRunsCompleted);
				}
				config = loaded;
			} catch (com.google.gson.JsonSyntaxException e) {
				DungeonRunTracker.LOGGER.error("[DRT] Config file malformed, resetting to defaults", e);
				config = new DrtConfig();
				save();
			}
		} catch (IOException e) {
			DungeonRunTracker.LOGGER.error("[DRT] Failed to load config", e);
			config = new DrtConfig();
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
		config.runHistory.add(record.copy());
		int overflow = config.runHistory.size() - 500;
		if (overflow > 0) config.runHistory.subList(0, overflow).clear();
		save();
	}

	public static synchronized void updateFloorRunCount(String floor, int count) {
		if (config.floorRunCounts == null) config.floorRunCounts = new LinkedHashMap<>();
		config.floorRunCounts.put(floor, Math.max(0, count));
		save();
	}

	public static synchronized void updateHudPosition(int x, int y) {
		config.hudX = Math.max(0, x);
		config.hudY = Math.max(0, y);
		save();
	}

	public static synchronized void clearAllData() {
		if (config.floorRunCounts != null) config.floorRunCounts.clear();
		config.legacyRunsCompleted = 0;
		config.runHistory = new ArrayList<>();
		save();
	}

	public static synchronized void clearFloorData(String floor) {
		if (config.floorRunCounts != null) config.floorRunCounts.remove(floor);
		if (config.runHistory != null) config.runHistory.removeIf(r -> r != null && floor.equals(r.floor));
		save();
	}
}
