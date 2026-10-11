package dev.vy.drt.luck.data;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.vy.drt.luck.ChestTier;
import dev.vy.drt.luck.FloorId;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Versioned Catacombs reward chest data: vendored base tables plus evidence-backed overrides.
 * Loading verifies every base file against the manifest hash, so silent edits fail fast.
 */
public final class CatacombsLootDatabase {
	public static final String MANIFEST_RESOURCE = "/assets/drt/luck/catacombs-data-v1.json";

	private final String dataVersion;
	private final String sourceCommit;
	private final Map<FloorId, FloorTable> floors;
	private final List<DataDispute> disputes;
	private final List<KnownGap> knownGaps;
	private final Map<String, String> activeOptions;

	private CatacombsLootDatabase(
		String dataVersion,
		String sourceCommit,
		Map<FloorId, FloorTable> floors,
		List<DataDispute> disputes,
		List<KnownGap> knownGaps,
		Map<String, String> activeOptions
	) {
		this.dataVersion = dataVersion;
		this.sourceCommit = sourceCommit;
		this.floors = Collections.unmodifiableMap(floors);
		this.disputes = List.copyOf(disputes);
		this.knownGaps = List.copyOf(knownGaps);
		this.activeOptions = Collections.unmodifiableMap(activeOptions);
	}

	/** Loads the bundled data with every dispute at its default option. */
	public static CatacombsLootDatabase loadBundled() {
		return loadBundled(Map.of());
	}

	/**
	 * @param optionSelections dispute id to option id; unlisted disputes use their default
	 */
	public static CatacombsLootDatabase loadBundled(Map<String, String> optionSelections) {
		JsonObject manifest = readJson(MANIFEST_RESOURCE).getAsJsonObject();
		String basePath = "/" + manifest.get("basePath").getAsString();
		JsonObject hashes = manifest.getAsJsonObject("files");
		JsonObject floorMeta = manifest.getAsJsonObject("floors");

		Map<FloorId, Map<ChestTier, MutableChest>> chests = new EnumMap<>(FloorId.class);
		for (Map.Entry<String, JsonElement> file : hashes.entrySet()) {
			String relative = file.getKey();
			byte[] bytes = readBytes(basePath + "/" + relative);
			String actual = sha256WithoutCarriageReturns(bytes);
			if (!actual.equals(file.getValue().getAsString())) {
				throw new IllegalStateException("Loot data hash mismatch for " + relative);
			}
			MutableChest chest = parseChest(JsonParser.parseString(new String(bytes, StandardCharsets.UTF_8)).getAsJsonObject());
			String expectedPath = chest.floor.dataDirectory() + "/" + chest.tier.dataFileName();
			if (!expectedPath.equals(relative)) {
				throw new IllegalStateException("Loot data file " + relative + " describes " + expectedPath);
			}
			chests.computeIfAbsent(chest.floor, ignored -> new EnumMap<>(ChestTier.class)).put(chest.tier, chest);
		}

		JsonObject overrides = readJson("/" + manifest.get("overridesPath").getAsString()).getAsJsonObject();
		if (!manifest.get("dataVersion").getAsString().equals(overrides.get("appliesTo").getAsString())) {
			throw new IllegalStateException("Overrides do not apply to " + manifest.get("dataVersion").getAsString());
		}
		List<DataDispute> disputes = parseDisputes(overrides.getAsJsonArray("disputes"));
		List<KnownGap> gaps = parseGaps(overrides.getAsJsonArray("knownGaps"));

		for (String selected : optionSelections.keySet()) {
			if (disputes.stream().noneMatch(dispute -> dispute.id().equals(selected))) {
				throw new IllegalArgumentException("Unknown dispute " + selected);
			}
		}

		Map<FloorId, Integer> meterDeltas = new EnumMap<>(FloorId.class);
		Map<String, String> active = new LinkedHashMap<>();
		for (DataDispute dispute : disputes) {
			String optionId = optionSelections.getOrDefault(dispute.id(), dispute.defaultOption());
			DataDispute.Option option = dispute.option(optionId);
			if (option == null) throw new IllegalArgumentException("Unknown option " + optionId + " for " + dispute.id());
			active.put(dispute.id(), optionId);
			for (DataDispute.Patch patch : option.patches()) {
				applyPatch(chests, meterDeltas, patch, dispute.id());
			}
		}

		Map<FloorId, FloorTable> floors = new EnumMap<>(FloorId.class);
		for (FloorId floor : FloorId.values()) {
			Map<ChestTier, MutableChest> floorChests = chests.get(floor);
			JsonObject meta = floorMeta.getAsJsonObject(floor.name());
			if (floorChests == null || meta == null) throw new IllegalStateException("Missing loot data for " + floor);
			Map<ChestTier, ChestTable> tables = new EnumMap<>(ChestTier.class);
			for (MutableChest chest : floorChests.values()) tables.put(chest.tier, chest.freeze());
			List<String> notes = new ArrayList<>();
			for (JsonElement note : meta.getAsJsonArray("notes")) notes.add(note.getAsString());
			floors.put(floor, new FloorTable(
				floor,
				tables,
				FloorStatus.valueOf(meta.get("status").getAsString()),
				meterDeltas.getOrDefault(floor, 0),
				notes
			));
		}

		return new CatacombsLootDatabase(
			manifest.get("dataVersion").getAsString(),
			manifest.getAsJsonObject("source").get("commit").getAsString(),
			floors,
			disputes,
			gaps,
			active
		);
	}

	public String dataVersion() {
		return dataVersion;
	}

	public String sourceCommit() {
		return sourceCommit;
	}

	public FloorTable floor(FloorId floor) {
		return floors.get(floor);
	}

	public Map<FloorId, FloorTable> floors() {
		return floors;
	}

	public List<DataDispute> disputes() {
		return disputes;
	}

	public List<KnownGap> knownGaps() {
		return knownGaps;
	}

	/** Dispute id to the option applied when this instance was loaded. */
	public Map<String, String> activeOptions() {
		return activeOptions;
	}

	public static String sha256WithoutCarriageReturns(byte[] bytes) {
		byte[] stripped = new byte[bytes.length];
		int length = 0;
		for (byte value : bytes) {
			if (value != '\r') stripped[length++] = value;
		}
		try {
			MessageDigest digest = MessageDigest.getInstance("SHA-256");
			digest.update(stripped, 0, length);
			return HexFormat.of().formatHex(digest.digest());
		} catch (NoSuchAlgorithmException exception) {
			throw new IllegalStateException(exception);
		}
	}

	private static void applyPatch(
		Map<FloorId, Map<ChestTier, MutableChest>> chests,
		Map<FloorId, Integer> meterDeltas,
		DataDispute.Patch patch,
		String disputeId
	) {
		switch (patch) {
			case DataDispute.MeterTotalWeightDelta delta -> meterDeltas.merge(delta.floor(), delta.value(), Integer::sum);
			case DataDispute.SetBaseCost cost -> requireChest(chests, cost.floor(), cost.chest(), disputeId).baseCost = cost.value();
			case DataDispute.SetWeight weight -> {
				MutableChest chest = requireChest(chests, weight.floor(), weight.chest(), disputeId);
				int index = requireEntry(chest, weight.entry(), disputeId);
				LootEntry old = chest.entries.get(index);
				if (old.role() != LootEntry.Role.WEIGHTED || weight.value() <= 0) {
					throw new IllegalStateException(disputeId + " can only reweight a weighted entry");
				}
				chest.entries.set(index, new LootEntry(old.key(), old.kind(), old.sourceId(), old.level(), old.tier(),
					old.displayName(), old.quality(), weight.value(), old.addedChestCost(), old.role()));
			}
			case DataDispute.RemoveEntry remove -> {
				MutableChest chest = requireChest(chests, remove.floor(), remove.chest(), disputeId);
				chest.entries.remove(requireEntry(chest, remove.entry(), disputeId));
			}
		}
	}

	private static MutableChest requireChest(Map<FloorId, Map<ChestTier, MutableChest>> chests, FloorId floor, ChestTier tier, String disputeId) {
		Map<ChestTier, MutableChest> floorChests = chests.get(floor);
		MutableChest chest = floorChests == null ? null : floorChests.get(tier);
		if (chest == null) throw new IllegalStateException(disputeId + " targets missing chest " + floor + " " + tier);
		return chest;
	}

	private static int requireEntry(MutableChest chest, String key, String disputeId) {
		for (int i = 0; i < chest.entries.size(); i++) {
			if (chest.entries.get(i).key().equals(key)) return i;
		}
		throw new IllegalStateException(disputeId + " targets missing entry " + key + " in " + chest.floor + " " + chest.tier);
	}

	private static MutableChest parseChest(JsonObject json) {
		int floorNumber = json.get("floor").getAsInt();
		boolean master = json.get("master_mode").getAsBoolean();
		FloorId floor = FloorId.parse((master ? "M" : "F") + floorNumber);
		ChestTier tier = ChestTier.parse(json.get("chest_type").getAsString());
		if (floor == null || tier == null) throw new IllegalStateException("Unknown chest " + json.get("chest_type"));
		MutableChest chest = new MutableChest(floor, tier, json.get("base_quality").getAsInt(), json.get("base_cost").getAsLong());
		for (JsonElement element : json.getAsJsonArray("loot")) {
			chest.entries.add(parseEntry(element.getAsJsonObject()));
		}
		return chest;
	}

	private static LootEntry parseEntry(JsonObject json) {
		int quality = json.get("quality").getAsInt();
		int weight = json.get("weight").getAsInt();
		long cost = json.has("extra_chest_cost") ? json.get("extra_chest_cost").getAsLong() : 0L;
		LootEntry.Kind kind;
		String key;
		String sourceId;
		int level = 0;
		String tier = null;
		String name;
		if (json.has("item")) {
			kind = LootEntry.Kind.ITEM;
			sourceId = json.get("item").getAsString();
			key = LootEntry.itemKey(sourceId);
			name = json.has("item_name") ? json.get("item_name").getAsString() : sourceId;
		} else if (json.has("pet")) {
			kind = LootEntry.Kind.PET;
			sourceId = json.get("pet").getAsString();
			tier = json.get("tier").getAsString();
			key = LootEntry.petKey(sourceId, tier);
			name = tier + " " + sourceId + " pet";
		} else if (json.has("enchantment")) {
			kind = LootEntry.Kind.ENCHANTMENT;
			sourceId = json.get("enchantment").getAsString();
			level = json.get("enchantment_level").getAsInt();
			key = LootEntry.enchantmentKey(sourceId, level);
			name = sourceId + " " + level;
		} else if (json.has("essence_type")) {
			kind = LootEntry.Kind.ESSENCE;
			sourceId = json.get("essence_type").getAsString();
			level = json.get("essence_amount").getAsInt();
			key = LootEntry.essenceKey(sourceId, level);
			name = level + " " + sourceId + " essence";
		} else {
			throw new IllegalStateException("Unrecognised loot entry " + json);
		}

		LootEntry.Role role;
		if (weight == 0 && quality == 0) {
			role = LootEntry.Role.GUARANTEED;
		} else if (kind == LootEntry.Kind.ESSENCE && weight > 0) {
			role = LootEntry.Role.REPEATABLE_ESSENCE;
		} else if (kind == LootEntry.Kind.ESSENCE && weight == 0 && quality == 1) {
			role = LootEntry.Role.FILLER_ESSENCE;
		} else if (weight > 0 && quality > 0) {
			role = LootEntry.Role.WEIGHTED;
		} else {
			throw new IllegalStateException("Loot entry with unsupported weight/quality " + json);
		}
		return new LootEntry(key, kind, sourceId, level, tier, name, quality, weight, cost, role);
	}

	private static List<DataDispute> parseDisputes(JsonArray array) {
		List<DataDispute> disputes = new ArrayList<>();
		for (JsonElement element : array) {
			JsonObject json = element.getAsJsonObject();
			List<DataDispute.Evidence> evidence = new ArrayList<>();
			for (JsonElement item : json.getAsJsonArray("evidence")) {
				JsonObject e = item.getAsJsonObject();
				evidence.add(new DataDispute.Evidence(string(e, "source"), string(e, "url"), string(e, "authority"), string(e, "finding")));
			}
			List<DataDispute.Option> options = new ArrayList<>();
			for (JsonElement item : json.getAsJsonArray("options")) {
				JsonObject o = item.getAsJsonObject();
				List<DataDispute.Patch> patches = new ArrayList<>();
				for (JsonElement patch : o.getAsJsonArray("patches")) patches.add(parsePatch(patch.getAsJsonObject()));
				options.add(new DataDispute.Option(string(o, "id"), string(o, "label"), patches));
			}
			DataDispute dispute = new DataDispute(
				string(json, "id"),
				floors(json.getAsJsonArray("floors")),
				DataDispute.Status.valueOf(string(json, "status")),
				string(json, "summary"),
				evidence,
				string(json, "defaultOption"),
				options
			);
			if (dispute.option(dispute.defaultOption()) == null) {
				throw new IllegalStateException(dispute.id() + " default option is missing");
			}
			disputes.add(dispute);
		}
		return disputes;
	}

	private static DataDispute.Patch parsePatch(JsonObject json) {
		FloorId floor = requireFloor(string(json, "floor"));
		String type = string(json, "type");
		return switch (type) {
			case "setWeight" -> new DataDispute.SetWeight(floor, requireTier(string(json, "chest")), string(json, "entry"), json.get("value").getAsInt());
			case "setBaseCost" -> new DataDispute.SetBaseCost(floor, requireTier(string(json, "chest")), json.get("value").getAsLong());
			case "removeEntry" -> new DataDispute.RemoveEntry(floor, requireTier(string(json, "chest")), string(json, "entry"));
			case "meterTotalWeightDelta" -> new DataDispute.MeterTotalWeightDelta(floor, json.get("value").getAsInt());
			default -> throw new IllegalStateException("Unknown patch type " + type);
		};
	}

	private static List<KnownGap> parseGaps(JsonArray array) {
		List<KnownGap> gaps = new ArrayList<>();
		for (JsonElement element : array) {
			JsonObject json = element.getAsJsonObject();
			gaps.add(new KnownGap(string(json, "id"), floors(json.getAsJsonArray("floors")), string(json, "summary"),
				string(json, "evidence"), string(json, "consequence")));
		}
		return gaps;
	}

	private static List<FloorId> floors(JsonArray array) {
		List<FloorId> floors = new ArrayList<>();
		for (JsonElement element : array) floors.add(requireFloor(element.getAsString()));
		return floors;
	}

	private static FloorId requireFloor(String value) {
		FloorId floor = FloorId.parse(value);
		if (floor == null) throw new IllegalStateException("Unknown floor " + value);
		return floor;
	}

	private static ChestTier requireTier(String value) {
		ChestTier tier = ChestTier.parse(value);
		if (tier == null) throw new IllegalStateException("Unknown chest " + value);
		return tier;
	}

	private static String string(JsonObject json, String key) {
		JsonElement value = json.get(key);
		return value == null || value.isJsonNull() ? null : value.getAsString();
	}

	private static JsonElement readJson(String resource) {
		return JsonParser.parseString(new String(readBytes(resource), StandardCharsets.UTF_8));
	}

	private static byte[] readBytes(String resource) {
		try (InputStream stream = CatacombsLootDatabase.class.getResourceAsStream(resource)) {
			if (stream == null) throw new IllegalStateException("Missing resource " + resource);
			return stream.readAllBytes();
		} catch (IOException exception) {
			throw new IllegalStateException("Could not read " + resource, exception);
		}
	}

	private static final class MutableChest {
		final FloorId floor;
		final ChestTier tier;
		final int baseQuality;
		long baseCost;
		final List<LootEntry> entries = new ArrayList<>();

		MutableChest(FloorId floor, ChestTier tier, int baseQuality, long baseCost) {
			this.floor = floor;
			this.tier = tier;
			this.baseQuality = baseQuality;
			this.baseCost = baseCost;
		}

		ChestTable freeze() {
			Map<String, Integer> seen = new HashMap<>();
			for (LootEntry entry : entries) {
				if (seen.put(entry.key(), 1) != null) {
					throw new IllegalStateException("Duplicate loot key " + entry.key() + " in " + floor + " " + tier);
				}
			}
			return new ChestTable(floor, tier, baseQuality, baseCost, entries);
		}
	}
}
