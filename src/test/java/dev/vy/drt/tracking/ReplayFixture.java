package dev.vy.drt.tracking;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.vy.drt.config.ChestCostBreakdown;
import dev.vy.drt.config.DungeonFloor;
import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

final class ReplayFixture {
	private static final Gson GSON = new Gson();

	private final Path directory;
	private final List<JsonObject> events;
	private final JsonObject expected;

	private ReplayFixture(Path directory, List<JsonObject> events, JsonObject expected) {
		this.directory = directory;
		this.events = events;
		this.expected = expected;
	}

	static ReplayFixture load(Path directory) throws IOException {
		List<JsonObject> events = new ArrayList<>();
		for (String line : Files.readAllLines(directory.resolve("events.jsonl"), StandardCharsets.UTF_8)) {
			if (line == null || line.isBlank()) continue;
			events.add(JsonParser.parseString(line).getAsJsonObject());
		}
		try (Reader reader = Files.newBufferedReader(directory.resolve("expected.json"), StandardCharsets.UTF_8)) {
			return new ReplayFixture(directory, events, JsonParser.parseReader(reader).getAsJsonObject());
		}
	}

	FakeTrackerClock clock() {
		return new FakeTrackerClock(Instant.parse(string(expected, "clockStart", "2024-01-01T00:00:00Z")));
	}

	void replayInto(TrackingSession tracker, FakeTrackerClock clock) {
		String activeRunId = "";
		String lastRunId = "";
		String lastChestId = "";
		for (JsonObject event : events) {
			clock.advanceMillis(longValue(event, "advanceMillis", 1L));
			String type = string(event, "type", "");
			switch (type) {
				case "startRun" -> {
					RunSession run = tracker.startRun(
						enumValue(RunMode.class, string(event, "mode", "UNKNOWN")),
						enumValue(DungeonFloor.class, string(event, "floor", "UNKNOWN")),
						enumValue(DetectionSource.class, string(event, "source", "CONFIRMED_SCOREBOARD")),
						enumValue(EvidenceStrength.class, string(event, "strength", "CONFIRMED_SCOREBOARD"))
					);
					activeRunId = run.id();
				}
				case "updateActiveFloor" -> tracker.updateActiveRunFloor(
					enumValue(DungeonFloor.class, string(event, "floor", "UNKNOWN")),
					enumValue(EvidenceStrength.class, string(event, "strength", "CONFIRMED_SCOREBOARD")),
					enumValue(DetectionSource.class, string(event, "source", "CONFIRMED_SCOREBOARD"))
				);
				case "playerInventoryFloorEvidence" -> tracker.updateActiveRunFloor(
					enumValue(DungeonFloor.class, string(event, "floor", "UNKNOWN")),
					EvidenceStrength.FALLBACK_GUESS,
					DetectionSource.PLAYER_INVENTORY
				);
				case "completeRun" -> {
					String runId = string(event, "run", "active");
					if ("active".equals(runId)) runId = activeRunId;
					else if ("last".equals(runId)) runId = lastRunId;
					if (runId == null || runId.isBlank() || runId.equals(activeRunId)) {
						tracker.completeActiveRun(
							enumValue(DungeonFloor.class, string(event, "floor", "UNKNOWN")),
							string(event, "grade", "?"),
							string(event, "fingerprint", "completion")
						);
						lastRunId = activeRunId;
						activeRunId = "";
					} else {
						tracker.completeRun(
							runId,
							enumValue(DungeonFloor.class, string(event, "floor", "UNKNOWN")),
							string(event, "grade", "?"),
							string(event, "fingerprint", "completion")
						);
						lastRunId = runId;
					}
				}
				case "completeKnownRun" -> {
					String runId = string(event, "run", "last");
					if ("active".equals(runId)) runId = activeRunId;
					else if ("last".equals(runId)) runId = lastRunId;
					tracker.completeRun(
						runId,
						enumValue(DungeonFloor.class, string(event, "floor", "UNKNOWN")),
						string(event, "grade", "?"),
						string(event, "fingerprint", "completion")
					);
				}
				case "abandonActiveRun" -> {
					tracker.abandonActiveRun(
						enumValue(DetectionEventType.class, string(event, "eventType", "WORLD_CHANGED")),
						enumValue(DetectionSource.class, string(event, "source", "SYSTEM")),
						string(event, "reason", "runtime_transition")
					);
					activeRunId = "";
				}
				case "openChest" -> {
					String owner = string(event, "owner", "");
					if ("active".equals(owner)) owner = activeRunId;
					else if ("last".equals(owner)) owner = lastRunId;
					ChestSession chest = tracker.openChest(
						owner,
						string(event, "chestTitle", ""),
						intValue(event, "containerId", -1),
						enumValue(DetectionSource.class, string(event, "source", "CONFIRMED_GUI_COMPONENT"))
					);
					lastChestId = chest.id();
				}
				case "commitChest" -> {
					String chestId = string(event, "chestId", "last");
					if ("last".equals(chestId)) chestId = lastChestId;
					tracker.commitChest(chestId, string(event, "fingerprint", "chest-commit"));
				}
				case "updateChestCost" -> {
					String chestId = string(event, "chestId", "last");
					if ("last".equals(chestId)) chestId = lastChestId;
					ChestCostBreakdown cost = new ChestCostBreakdown();
					cost.baseChestCostCoins = longValue(event, "baseChestCostCoins", 0L);
					cost.dungeonChestKeyCostCoins = longValue(event, "dungeonChestKeyCostCoins", 0L);
					cost.kismetFeatherCostCoins = longValue(event, "kismetFeatherCostCoins", 0L);
					cost.wheelOfFateCostCoins = longValue(event, "wheelOfFateCostCoins", 0L);
					cost.kuudraKeyCostCoins = longValue(event, "kuudraKeyCostCoins", 0L);
					cost.usedDungeonChestKey = booleanValue(event, "usedDungeonChestKey", false);
					cost.usedKismetFeather = booleanValue(event, "usedKismetFeather", false);
					cost.kismetRerolledChestOpened = booleanValue(event, "kismetRerolledChestOpened", false);
					cost.usedWheelOfFate = booleanValue(event, "usedWheelOfFate", false);
					cost.usedKuudraKey = booleanValue(event, "usedKuudraKey", false);
					cost.normalize();
					tracker.updateChestCost(
						chestId,
						cost,
						enumValue(DetectionSource.class, string(event, "source", "CONFIRMED_GUI_COMPONENT"))
					);
				}
				case "updateChestContextFloor" -> {
					String chestId = string(event, "chestId", "last");
					if ("last".equals(chestId)) chestId = lastChestId;
					tracker.updateChestContextFloor(
						chestId,
						enumValue(DungeonFloor.class, string(event, "floor", "UNKNOWN")),
						enumValue(EvidenceStrength.class, string(event, "strength", "GUI_TITLE_INFERENCE")),
						enumValue(DetectionSource.class, string(event, "source", "GUI_TITLE_INFERENCE"))
					);
				}
				case "updateChestContextMode" -> {
					String chestId = string(event, "chestId", "last");
					if ("last".equals(chestId)) chestId = lastChestId;
					tracker.updateChestContextMode(
						chestId,
						enumValue(RunMode.class, string(event, "mode", "UNKNOWN")),
						enumValue(EvidenceStrength.class, string(event, "strength", "GUI_TITLE_INFERENCE")),
						enumValue(DetectionSource.class, string(event, "source", "GUI_TITLE_INFERENCE"))
					);
				}
				case "observeLoot" -> {
					String chestId = string(event, "chestId", "last");
					if ("last".equals(chestId)) chestId = lastChestId;
					tracker.observeLoot(chestId, new LootObservation(
						string(event, "observationId", "obs-" + event.hashCode()),
						longValue(event, "eventSequence", 1L),
						enumValue(DetectionSource.class, string(event, "source", "STRUCTURED_CHAT")),
						string(event, "rawName", ""),
						string(event, "normalizedName", ""),
						string(event, "itemId", ""),
						enumValue(LootIdentityStrength.class, string(event, "identityStrength", "UNRESOLVED")),
						intValue(event, "quantity", 1),
						intValue(event, "containerId", -1),
						intValue(event, "slotIndex", -1),
						enumValue(SlotOwner.class, string(event, "slotOwner", "SERVER_CONTAINER")),
						string(event, "dedupKey", "")
					));
				}
				default -> throw new IllegalArgumentException("Unknown replay event type in " + directory + ": " + type);
			}
		}
	}

	List<String> assertExpected(TrackingSnapshot snapshot) {
		List<String> failures = new ArrayList<>();
		String floor = string(expected, "activeFloor", null);
		if (floor != null && snapshot.activeFloor() != enumValue(DungeonFloor.class, floor)) {
			failures.add("activeFloor expected " + floor + " got " + snapshot.activeFloor());
		}
		String mode = string(expected, "activeMode", null);
		if (mode != null && snapshot.activeMode() != enumValue(RunMode.class, mode)) {
			failures.add("activeMode expected " + mode + " got " + snapshot.activeMode());
		}
		if (expected.has("completedRuns") && snapshot.completedRuns() != expected.get("completedRuns").getAsInt()) {
			failures.add("completedRuns expected " + expected.get("completedRuns").getAsInt() + " got " + snapshot.completedRuns());
		}
		if (expected.has("abandonedRuns") && snapshot.abandonedRuns() != expected.get("abandonedRuns").getAsInt()) {
			failures.add("abandonedRuns expected " + expected.get("abandonedRuns").getAsInt() + " got " + snapshot.abandonedRuns());
		}
		if (expected.has("activeRun")) {
			boolean wanted = expected.get("activeRun").getAsBoolean();
			boolean actual = snapshot.activeRunId() != null && !snapshot.activeRunId().isBlank();
			if (actual != wanted) failures.add("activeRun expected " + wanted + " got " + actual);
		}
		if (expected.has("orphanChests")) {
			long actual = snapshot.chestOwners().values().stream().filter(String::isBlank).count();
			long wanted = expected.get("orphanChests").getAsLong();
			if (actual != wanted) failures.add("orphanChests expected " + wanted + " got " + actual);
		}
		if (expected.has("chests") && snapshot.chestCount() != expected.get("chests").getAsInt()) {
			failures.add("chests expected " + expected.get("chests").getAsInt() + " got " + snapshot.chestCount());
		}
		if (expected.has("committedChests") && snapshot.committedChests() != expected.get("committedChests").getAsInt()) {
			failures.add("committedChests expected " + expected.get("committedChests").getAsInt() + " got " + snapshot.committedChests());
		}
		if (expected.has("chestCostCoins")) {
			long wanted = expected.get("chestCostCoins").getAsLong();
			boolean found = snapshot.chestCosts().values().stream()
				.anyMatch(cost -> cost.totalCostCoins() == wanted);
			if (!found) failures.add("missing chestCostCoins " + wanted);
		}
		if (expected.has("usedKismetFeather")) {
			boolean wanted = expected.get("usedKismetFeather").getAsBoolean();
			boolean found = snapshot.chestCosts().values().stream()
				.anyMatch(cost -> cost.usedKismetFeather == wanted);
			if (!found) failures.add("missing usedKismetFeather " + wanted);
		}
		if (expected.has("usedWheelOfFate")) {
			boolean wanted = expected.get("usedWheelOfFate").getAsBoolean();
			boolean found = snapshot.chestCosts().values().stream()
				.anyMatch(cost -> cost.usedWheelOfFate == wanted);
			if (!found) failures.add("missing usedWheelOfFate " + wanted);
		}
		if (expected.has("chestFloor")) {
			DungeonFloor wanted = enumValue(DungeonFloor.class, expected.get("chestFloor").getAsString());
			boolean found = snapshot.chestFloors().values().stream().anyMatch(observedFloor -> observedFloor == wanted);
			if (!found) failures.add("missing chestFloor " + wanted);
		}
		if (expected.has("chestMode")) {
			RunMode wanted = enumValue(RunMode.class, expected.get("chestMode").getAsString());
			boolean found = snapshot.chestModes().values().stream().anyMatch(observedMode -> observedMode == wanted);
			if (!found) failures.add("missing chestMode " + wanted);
		}
		if (expected.has("invariants")) {
			for (JsonElement element : expected.getAsJsonArray("invariants")) {
				String invariant = element.getAsString();
				if (!snapshot.invariants().contains(invariant)) failures.add("missing invariant " + invariant);
			}
		}
		if (expected.has("loot")) {
			JsonObject loot = expected.getAsJsonObject("loot");
			String itemId = string(loot, "itemId", "");
			int quantity = intValue(loot, "quantity", 1);
			boolean found = snapshot.chestLoot().values().stream()
				.flatMap(List::stream)
				.anyMatch(entry -> itemId.equals(entry.itemId()) && entry.quantity() == quantity);
			if (!found) failures.add("missing loot " + itemId + " x" + quantity);
		}
		return failures;
	}

	private static <T extends Enum<T>> T enumValue(Class<T> type, String value) {
		return Enum.valueOf(type, value == null || value.isBlank() ? type.getEnumConstants()[0].name() : value);
	}

	private static String string(JsonObject object, String key, String fallback) {
		if (object == null || !object.has(key) || object.get(key).isJsonNull()) return fallback;
		return object.get(key).getAsString();
	}

	private static int intValue(JsonObject object, String key, int fallback) {
		if (object == null || !object.has(key) || object.get(key).isJsonNull()) return fallback;
		return object.get(key).getAsInt();
	}

	private static long longValue(JsonObject object, String key, long fallback) {
		if (object == null || !object.has(key) || object.get(key).isJsonNull()) return fallback;
		return object.get(key).getAsLong();
	}

	private static boolean booleanValue(JsonObject object, String key, boolean fallback) {
		if (object == null || !object.has(key) || object.get(key).isJsonNull()) return fallback;
		return object.get(key).getAsBoolean();
	}
}
