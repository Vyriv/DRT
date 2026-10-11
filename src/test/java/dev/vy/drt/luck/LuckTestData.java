package dev.vy.drt.luck;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.vy.drt.luck.data.CatacombsLootDatabase;
import dev.vy.drt.luck.data.LootEntry;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Map;

public final class LuckTestData {
	/** Every dispute at the option that leaves the vendored base data untouched. */
	public static final Map<String, String> BASE_DATA_OPTIONS = Map.of(
		"M4_SPIRIT_MASK_WEIGHT", "DATA_WEIGHT_1",
		"M5_M7_METER_TOTAL_WEIGHT", "DATA_TOTAL",
		"F7_OBSIDIAN_WITHER_SHARD", "DATA_GUARANTEED",
		"F2_M2_EMERALD_BASE_COST", "DATA_200K"
	);

	private static CatacombsLootDatabase defaults;
	private static CatacombsLootDatabase baseData;

	private LuckTestData() {
	}

	public static synchronized CatacombsLootDatabase defaults() {
		if (defaults == null) defaults = CatacombsLootDatabase.loadBundled();
		return defaults;
	}

	public static synchronized CatacombsLootDatabase baseData() {
		if (baseData == null) baseData = CatacombsLootDatabase.loadBundled(BASE_DATA_OPTIONS);
		return baseData;
	}

	public static JsonObject fixture(String name) {
		try (InputStream stream = LuckTestData.class.getResourceAsStream("/luck/" + name)) {
			if (stream == null) throw new IllegalStateException("Missing fixture " + name);
			return JsonParser.parseString(new String(stream.readAllBytes(), StandardCharsets.UTF_8)).getAsJsonObject();
		} catch (IOException exception) {
			throw new IllegalStateException(exception);
		}
	}

	/** Entry name as printed by the community wiki loot tables. */
	public static String wikiName(LootEntry entry) {
		return switch (entry.kind()) {
			case ITEM -> entry.displayName().equals(entry.sourceId()) ? titleCase(entry.sourceId()) : entry.displayName();
			case PET -> "$" + entry.tier().toLowerCase(Locale.ROOT) + "$ " + titleCase(entry.sourceId()) + " Pet";
			case ENCHANTMENT -> "Enchanted Book &" + titleCase(entry.sourceId()) + " " + entry.level() + "&";
			case ESSENCE -> entry.level() + " " + titleCase(entry.sourceId()) + " Essence";
		};
	}

	public static String titleCase(String id) {
		StringBuilder out = new StringBuilder();
		for (String word : id.split("_")) {
			if (word.isEmpty()) continue;
			if (!out.isEmpty()) out.append(' ');
			out.append(word.substring(0, 1).toUpperCase(Locale.ROOT)).append(word.substring(1).toLowerCase(Locale.ROOT));
		}
		return out.toString();
	}
}
