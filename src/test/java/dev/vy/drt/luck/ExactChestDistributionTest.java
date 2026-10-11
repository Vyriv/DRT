package dev.vy.drt.luck;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.vy.drt.luck.data.ChestTable;
import dev.vy.drt.luck.data.LootEntry;
import dev.vy.drt.luck.engine.ExactChestDistribution;
import dev.vy.drt.luck.engine.MeterBoost;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class ExactChestDistributionTest {
	/** Wiki percentages are printed to 4 decimals; allow that rounding plus the generator's pruning. */
	private static final double WIKI_TOLERANCE = 2e-6;
	/** Wiki rows for items the data does not model (see DYES_NOT_MODELLED). */
	private static final Set<String> UNMODELLED_WIKI_ROWS = Set.of("Livid Dye", "Necron Dye");

	static LootEntry item(String key, int quality, int weight) {
		return new LootEntry(key, LootEntry.Kind.ITEM, key, 0, null, key, quality, weight, 0, LootEntry.Role.WEIGHTED);
	}

	static LootEntry repeatable() {
		return new LootEntry("ESSENCE_WITHER_1", LootEntry.Kind.ESSENCE, "WITHER", 1, null, "", 10, 1, 0, LootEntry.Role.REPEATABLE_ESSENCE);
	}

	static LootEntry filler() {
		return new LootEntry("ESSENCE_UNDEAD_1", LootEntry.Kind.ESSENCE, "UNDEAD", 1, null, "", 1, 0, 0, LootEntry.Role.FILLER_ESSENCE);
	}

	static ChestTable chest(LootEntry... entries) {
		return new ChestTable(FloorId.F1, ChestTier.WOOD, 100, 0, List.of(entries));
	}

	@Test
	void handComputedSmallChests() {
		ChestTable pair = chest(item("A", 60, 1), item("B", 60, 1), filler());
		ExactChestDistribution.Result one = ExactChestDistribution.compute(pair, 100, MeterBoost.NONE);
		assertEquals(0.5, one.probability("A"), 1e-12);
		assertEquals(0.5, one.probability("B"), 1e-12);
		assertEquals(40.0, one.expectedFillerEssence(), 1e-12);

		ExactChestDistribution.Result both = ExactChestDistribution.compute(pair, 120, MeterBoost.NONE);
		assertEquals(1.0, both.probability("A"), 1e-12);
		assertEquals(1.0, both.probability("B"), 1e-12);
		assertEquals(0.0, both.expectedFillerEssence(), 1e-12);

		assertEquals(0.75, ExactChestDistribution.compute(pair, 100, new MeterBoost("A", 3.0, false)).probability("A"), 1e-12);

		ExactChestDistribution.Result forced = ExactChestDistribution.compute(pair, 100, new MeterBoost("A", 3.0, true));
		assertEquals(1.0, forced.probability("A"), 1e-12);
		assertEquals(0.0, forced.probability("B"), 1e-12);
		assertEquals(1.0, ExactChestDistribution.compute(pair, 120, new MeterBoost("A", 3.0, true)).probability("B"), 1e-12);

		// Quality 20: A (q20) or essence first, each 1/2. After essence only essence fits.
		ExactChestDistribution.Result essence = ExactChestDistribution.compute(chest(item("A", 20, 1), repeatable(), filler()), 20, MeterBoost.NONE);
		assertEquals(0.5, essence.probability("A"), 1e-12);
		assertEquals(1.0, essence.expectedRepeatableEssence(), 1e-12);
	}

	@Test
	void guaranteedEntriesAlwaysAppear() {
		LootEntry guaranteed = new LootEntry("ESSENCE_WITHER_5", LootEntry.Kind.ESSENCE, "WITHER", 5, null, "", 0, 0, 0, LootEntry.Role.GUARANTEED);
		ExactChestDistribution.Result result = ExactChestDistribution.compute(chest(item("A", 60, 1), guaranteed, filler()), 10, MeterBoost.NONE);
		assertEquals(1.0, result.probability("ESSENCE_WITHER_5"));
		assertEquals(0.0, result.probability("A"));
		assertEquals(10.0, result.expectedFillerEssence(), 1e-12);
	}

	@ParameterizedTest
	@EnumSource(FloorId.class)
	void matchesWikiAverageChancesAtBothQualities(FloorId floor) {
		JsonObject chests = LuckTestData.fixture("wiki-average-chances.json").getAsJsonObject("floors").getAsJsonObject(floor.name());
		List<String> failures = new ArrayList<>();
		int compared = 0;
		for (Map.Entry<String, JsonElement> chestEntry : chests.entrySet()) {
			ChestTier tier = ChestTier.parse(chestEntry.getKey());
			ChestTable chest = LuckTestData.baseData().floor(floor).chest(tier);
			JsonObject wiki = chestEntry.getValue().getAsJsonObject();
			Map<String, LootEntry> byName = new HashMap<>();
			for (LootEntry entry : chest.entries()) {
				if (byName.put(LuckTestData.wikiName(entry), entry) != null) failures.add("ambiguous wiki name " + LuckTestData.wikiName(entry));
			}
			ExactChestDistribution.Result low = ExactChestDistribution.compute(chest, wiki.get("qualityNoBonus").getAsInt(), MeterBoost.NONE);
			ExactChestDistribution.Result high = ExactChestDistribution.compute(chest, wiki.get("qualityMaxBonus").getAsInt(), MeterBoost.NONE);
			int wikiRolled = 0;
			for (JsonElement element : wiki.getAsJsonArray("entries")) {
				JsonObject row = element.getAsJsonObject();
				String name = row.get("name").getAsString();
				LootEntry entry = byName.get(name);
				if (entry == null) {
					if (!UNMODELLED_WIKI_ROWS.contains(name)) failures.add(tier + " unmatched wiki row " + name);
					continue;
				}
				if (entry.quality() != row.get("quality").getAsInt() || entry.weight() != row.get("weight").getAsInt()) {
					failures.add(tier + " " + name + " quality/weight differs");
				}
				if (entry.role() == LootEntry.Role.FILLER_ESSENCE) continue;
				double lowExpected = row.get("noBonusPct").getAsDouble() / 100.0;
				double highExpected = row.get("maxBonusPct").getAsDouble() / 100.0;
				double lowActual;
				double highActual;
				switch (entry.role()) {
					case WEIGHTED -> {
						lowActual = low.probability(entry.key());
						highActual = high.probability(entry.key());
						wikiRolled++;
					}
					case REPEATABLE_ESSENCE -> {
						lowActual = low.expectedRepeatableEssence();
						highActual = high.expectedRepeatableEssence();
						// Expected counts accumulate the generator's branch pruning, so allow a small relative error.
						if (Math.abs(lowActual - lowExpected) <= 1e-5 * lowExpected) lowActual = lowExpected;
						if (Math.abs(highActual - highExpected) <= 1e-5 * highExpected) highActual = highExpected;
					}
					default -> {
						continue;
					}
				}
				compared++;
				if (Math.abs(lowActual - lowExpected) > WIKI_TOLERANCE) failures.add(tier + " " + name + " no bonus " + lowActual + " vs " + lowExpected);
				if (Math.abs(highActual - highExpected) > WIKI_TOLERANCE) failures.add(tier + " " + name + " max " + highActual + " vs " + highExpected);
			}
			long rolled = chest.entries().stream().filter(e -> e.role() == LootEntry.Role.WEIGHTED).count();
			if (wikiRolled != rolled) failures.add(tier + " wiki lists " + wikiRolled + " of " + rolled + " weighted entries");
		}
		assertTrue(compared > 0);
		assertEquals(List.of(), failures);
	}

	@ParameterizedTest
	@EnumSource(FloorId.class)
	void probabilitiesAreBoundedAndBoostIsMonotonic(FloorId floor) {
		for (ChestTable chest : LuckTestData.defaults().floor(floor).chests().values()) {
			int quality = chest.baseQuality();
			ExactChestDistribution.Result base = ExactChestDistribution.compute(chest, quality, MeterBoost.NONE);
			LootEntry rarest = null;
			for (LootEntry entry : chest.entries()) {
				double p = base.probability(entry.key());
				assertTrue(p >= -1e-12 && p <= 1 + 1e-12, entry.key() + " " + p);
				if (entry.role() == LootEntry.Role.WEIGHTED && (rarest == null || entry.weight() < rarest.weight())) rarest = entry;
			}
			double boosted = ExactChestDistribution.compute(chest, quality, new MeterBoost(rarest.key(), 2.0, false)).probability(rarest.key());
			assertTrue(boosted > base.probability(rarest.key()), floor + " " + chest.tier() + " " + rarest.key());
		}
	}
}
