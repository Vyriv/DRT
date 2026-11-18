package dev.vy.drt.api;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Slim, versioned DRT dungeon response. Raw SkyBlock profile data never reaches the UI. */
public record DungeonPlayerStats(
	String username,
	String uuid,
	String profileName,
	Level catacombs,
	Map<String, Level> classes,
	Double averageClassLevel,
	Long secretsFound,
	Long totalCompletions,
	List<Floor> floors,
	List<RecentRun> recentRuns,
	String cacheState
) {
	public record Level(int level, double fractionalLevel, long xp, double progress) {}
	public record Floor(String id, Long completions, Long bestScore, Long fastestTimeMs,
		Long fastestSTimeMs, Long fastestSPlusTimeMs) {}
	public record Chest(String type, boolean paid, int rerolls, List<String> rewards) {}
	public record RecentRun(String upstreamRunId, String floor, long completedAt,
		List<String> participants, List<Chest> chests) {}

	public static DungeonPlayerStats parse(JsonObject root) {
		if (root == null || !bool(root, "success")) return null;
		JsonObject player = object(root, "player");
		JsonObject profile = object(root, "profile");
		JsonObject dungeons = object(root, "dungeons");
		if (player == null || dungeons == null) return null;
		Map<String, Level> classes = new LinkedHashMap<>();
		JsonObject classRoot = object(dungeons, "classes");
		if (classRoot != null) for (var entry : classRoot.entrySet()) {
			Level level = level(entry.getValue());
			if (level != null) classes.put(entry.getKey(), level);
		}
		List<Floor> floors = new ArrayList<>();
		for (JsonElement element : array(dungeons, "floors")) {
			if (!element.isJsonObject()) continue;
			JsonObject floor = element.getAsJsonObject();
			floors.add(new Floor(string(floor, "id"), nullableLong(floor, "completions"),
				nullableLong(floor, "bestScore"), nullableLong(floor, "fastestTimeMs"),
				nullableLong(floor, "fastestSTimeMs"), nullableLong(floor, "fastestSPlusTimeMs")));
		}
		List<RecentRun> recent = new ArrayList<>();
		for (JsonElement element : array(root, "recentRuns")) {
			if (!element.isJsonObject()) continue;
			JsonObject run = element.getAsJsonObject();
			List<String> participants = new ArrayList<>();
			for (JsonElement name : array(run, "participants")) if (name.isJsonPrimitive()) participants.add(name.getAsString());
			List<Chest> chests = new ArrayList<>();
			for (JsonElement chestElement : array(run, "chests")) {
				if (!chestElement.isJsonObject()) continue;
				JsonObject chest = chestElement.getAsJsonObject();
				List<String> rewards = new ArrayList<>();
				for (JsonElement reward : array(chest, "rewards")) if (reward.isJsonPrimitive()) rewards.add(reward.getAsString());
				chests.add(new Chest(string(chest, "type"), bool(chest, "paid"),
					(int) number(chest, "rerolls", 0), List.copyOf(rewards)));
			}
			recent.add(new RecentRun(string(run, "upstreamRunId"), string(run, "floor"),
				number(run, "completedAt", 0), List.copyOf(participants), List.copyOf(chests)));
		}
		JsonObject cache = object(root, "cache");
		return new DungeonPlayerStats(string(player, "username"), string(player, "uuid"),
			profile == null ? "" : string(profile, "cuteName"), level(dungeons.get("catacombs")),
			Map.copyOf(classes), nullableDouble(dungeons, "averageClassLevel"), nullableLong(dungeons, "secretsFound"),
			nullableLong(dungeons, "totalCompletions"), List.copyOf(floors), List.copyOf(recent),
			cache == null ? "" : string(cache, "state"));
	}

	public Floor floor(String id) {
		for (Floor floor : floors) if (floor.id().equalsIgnoreCase(id)) return floor;
		return null;
	}

	private static Level level(JsonElement element) {
		if (element == null || !element.isJsonObject()) return null;
		JsonObject object = element.getAsJsonObject();
		return new Level((int) number(object, "level", 0), numberDouble(object, "fractionalLevel", 0),
			number(object, "xp", 0), numberDouble(object, "progress", 0));
	}

	private static JsonObject object(JsonObject root, String key) {
		JsonElement value = root == null ? null : root.get(key);
		return value != null && value.isJsonObject() ? value.getAsJsonObject() : null;
	}

	private static JsonArray array(JsonObject root, String key) {
		JsonElement value = root == null ? null : root.get(key);
		return value != null && value.isJsonArray() ? value.getAsJsonArray() : new JsonArray();
	}

	private static String string(JsonObject root, String key) {
		JsonElement value = root == null ? null : root.get(key);
		return value != null && value.isJsonPrimitive() ? value.getAsString() : "";
	}

	private static boolean bool(JsonObject root, String key) {
		JsonElement value = root == null ? null : root.get(key);
		return value != null && value.isJsonPrimitive() && value.getAsBoolean();
	}

	private static long number(JsonObject root, String key, long fallback) {
		Long value = nullableLong(root, key);
		return value == null ? fallback : value;
	}

	private static double numberDouble(JsonObject root, String key, double fallback) {
		Double value = nullableDouble(root, key);
		return value == null ? fallback : value;
	}

	private static Long nullableLong(JsonObject root, String key) {
		JsonElement value = root == null ? null : root.get(key);
		try { return value != null && value.isJsonPrimitive() && value.getAsJsonPrimitive().isNumber() ? value.getAsLong() : null; }
		catch (RuntimeException ignored) { return null; }
	}

	private static Double nullableDouble(JsonObject root, String key) {
		JsonElement value = root == null ? null : root.get(key);
		try { return value != null && value.isJsonPrimitive() && value.getAsJsonPrimitive().isNumber() ? value.getAsDouble() : null; }
		catch (RuntimeException ignored) { return null; }
	}
}
