package dev.vy.drt.luck;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.vy.drt.luck.data.FloorTable;
import dev.vy.drt.luck.data.LootEntry;
import dev.vy.drt.luck.meter.RngMeterModel;
import dev.vy.drt.luck.rules.RuleVariants;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.junit.jupiter.api.Test;

/** Checks the engine against RNG Meter values read from the live game by the luck probe. */
class LiveRngMeterFixtureTest {
	private static final RuleVariants RULES = RuleVariants.defaults();

	@Test
	void f5RequiredScoresAndGuaranteeChestsMatchLiveGame() {
		JsonObject fixture = LuckTestData.fixture("live-f5-rng-meter-2026-10-11.json");
		FloorTable table = LuckTestData.defaults().floor(FloorId.F5);
		List<LootEntry> selectable = RngMeterModel.selectableItems(table);
		List<String> failures = new ArrayList<>();
		int matched = 0;
		for (JsonElement element : fixture.getAsJsonArray("items")) {
			JsonObject item = element.getAsJsonObject();
			String name = item.get("name").getAsString();
			LootEntry entry = findByName(selectable, name);
			if (entry == null) {
				failures.add("no selectable entry named " + name);
				continue;
			}
			matched++;
			long required = RngMeterModel.requiredScore(table, entry.key(), RULES);
			long live = item.get("required").getAsLong();
			if (required != live) failures.add(name + " required " + required + " live " + live);
			ChestTier tier = RngMeterModel.guaranteeTier(table, entry.key(), List.of(ChestTier.values()), RULES);
			String liveTier = item.get("appearsIn").getAsString();
			if (tier == null || !tier.name().equals(liveTier)) failures.add(name + " appears in " + tier + " live " + liveTier);
		}
		assertEquals(List.of(), failures);
		assertEquals(selectable.size(), matched, "every selectable F5 item is listed in game");
	}

	private static LootEntry findByName(List<LootEntry> entries, String liveName) {
		String bookKey = enchantedBookKey(liveName);
		String wanted = normalize(liveName);
		for (LootEntry entry : entries) {
			if (bookKey != null ? entry.key().equals(bookKey) : normalize(entry.displayName()).equals(wanted)) return entry;
		}
		return null;
	}

	private static String enchantedBookKey(String liveName) {
		String prefix = "Enchanted Book (";
		if (!liveName.startsWith(prefix) || !liveName.endsWith(")")) return null;
		String inner = liveName.substring(prefix.length(), liveName.length() - 1).trim();
		int space = inner.lastIndexOf(' ');
		int level = roman(inner.substring(space + 1));
		String id = inner.substring(0, space).toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]+", "_");
		return LootEntry.enchantmentKey(id, level);
	}

	private static int roman(String numeral) {
		return switch (numeral) {
			case "I" -> 1; case "II" -> 2; case "III" -> 3; case "IV" -> 4; case "V" -> 5;
			case "VI" -> 6; case "VII" -> 7; default -> throw new IllegalArgumentException(numeral);
		};
	}

	private static String normalize(String name) {
		return (name == null ? "" : name).toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]", "");
	}
}
