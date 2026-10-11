package dev.vy.drt.luck;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.vy.drt.luck.data.CatacombsLootDatabase;
import dev.vy.drt.luck.data.FloorTable;
import dev.vy.drt.luck.data.LootEntry;
import dev.vy.drt.luck.meter.RngMeterModel;
import dev.vy.drt.luck.meter.RngMeterState;
import dev.vy.drt.luck.rules.ModifierPreset.BossLuck;
import dev.vy.drt.luck.rules.ModifierPreset.TreasureAccessory;
import dev.vy.drt.luck.rules.ResolvedModifiers;
import dev.vy.drt.luck.rules.RuleVariants;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class RngMeterModelTest {
	private static final RuleVariants RULES = RuleVariants.defaults();
	private static final String[] PET_TIERS = {"COMMON", "UNCOMMON", "RARE", "EPIC", "LEGENDARY", "MYTHIC"};
	private static final Set<String> UNMODELLED_NEU_KEYS = Set.of("DYE_LIVID", "DYE_NECRON");

	@ParameterizedTest
	@EnumSource(FloorId.class)
	void requiredScoresMatchInGameMeterWithinOnePoint(FloorId floor) {
		JsonObject neu = LuckTestData.fixture("neu-rngscore-catacombs.json").getAsJsonObject("catacombs").getAsJsonObject(floor.name());
		FloorTable table = LuckTestData.defaults().floor(floor);
		// The NEU copy of F7 predates the Bedrock Wither Shard (weight 350).
		int excludedWeight = floor == FloorId.F7 ? table.topChest().entry("WITHER_SHARD").weight() : 0;
		List<String> failures = new ArrayList<>();
		int matched = 0;
		for (Map.Entry<String, JsonElement> entry : neu.entrySet()) {
			if (UNMODELLED_NEU_KEYS.contains(entry.getKey())) continue;
			String key = canonicalNeuKey(entry.getKey());
			LootEntry loot = table.topChest().entry(key);
			if (loot == null) {
				failures.add("NEU item not in top chest: " + entry.getKey() + " -> " + key);
				continue;
			}
			double total = table.topChest().totalWeight() + table.meterTotalWeightDelta() - excludedWeight;
			long expected = (long) Math.floor(300 * total / loot.weight() + 1e-9);
			long computed = floor == FloorId.F7 ? expected : RngMeterModel.requiredScore(table, key, RULES);
			long inGame = entry.getValue().getAsLong();
			if (Math.abs(computed - inGame) > 1) failures.add(key + " computed " + computed + " in-game " + inGame);
			matched++;
		}
		assertEquals(RngMeterModel.selectableItems(table).size() - (floor == FloorId.F7 ? 1 : 0), matched, floor + " selectable count");
		assertEquals(List.of(), failures);
	}

	@ParameterizedTest
	@EnumSource(FloorId.class)
	void requiredScoresMatchFandomMeterCopy(FloorId floor) {
		// Fandom M4 predates Spirit Mask; compare it against the base data without that entry.
		CatacombsLootDatabase db = floor == FloorId.M4 ? LuckTestData.baseData() : LuckTestData.defaults();
		FloorTable table = db.floor(floor);
		List<Long> ours = new ArrayList<>();
		for (LootEntry entry : RngMeterModel.selectableItems(table)) {
			if (floor == FloorId.M4 && entry.key().equals("SPIRIT_MASK")) continue;
			// Neither in-game copy lists the F7 Wither Shard as a meter item, though its weight is in the total.
			if (floor == FloorId.F7 && entry.key().equals("WITHER_SHARD")) continue;
			double total = table.topChest().totalWeight() + table.meterTotalWeightDelta()
				- (floor == FloorId.M4 ? table.topChest().entry("SPIRIT_MASK").weight() : 0);
			ours.add((long) Math.floor(300 * total / entry.weight() + 1e-9));
		}
		// Fandom abbreviates large scores ("10.6k"), so each value carries its own rounding tolerance.
		List<double[]> fandom = new ArrayList<>();
		for (JsonElement element : LuckTestData.fixture("fandom-rng-meter.json").getAsJsonObject("floors").getAsJsonArray(floor.name())) {
			double[] score = parseFandomScore(element.getAsJsonObject().get("score").getAsString());
			if (score[0] < 1_000_000) fandom.add(score);
		}
		ours.sort(null);
		fandom.sort((a, b) -> Double.compare(a[0], b[0]));
		assertEquals(fandom.size(), ours.size(), floor + " item count");
		for (int i = 0; i < ours.size(); i++) {
			double[] expected = fandom.get(i);
			assertTrue(Math.abs(ours.get(i) - expected[0]) <= expected[1] + 1, floor + " score " + ours.get(i) + " vs " + expected[0]);
		}
	}

	/** Returns {value, rounding tolerance}. */
	static double[] parseFandomScore(String text) {
		String value = text.replace(",", "").trim().toLowerCase(java.util.Locale.ROOT);
		if (value.endsWith("k")) {
			String digits = value.substring(0, value.length() - 1);
			int decimals = digits.contains(".") ? digits.length() - digits.indexOf('.') - 1 : 0;
			return new double[] {Double.parseDouble(digits) * 1000, 0.5 * Math.pow(10, 3 - decimals)};
		}
		return new double[] {Double.parseDouble(value), 0};
	}

	@Test
	void disputedTotalsChangeRequiredScores() {
		FloorTable m4 = LuckTestData.defaults().floor(FloorId.M4);
		assertEquals(22200, RngMeterModel.requiredScore(m4, "SPIRIT_MASK", RULES));
		assertEquals(87900, RngMeterModel.requiredScore(LuckTestData.baseData().floor(FloorId.M4), "SPIRIT_MASK", RULES));
		FloorTable m5 = LuckTestData.defaults().floor(FloorId.M5);
		LootEntry rarest = RngMeterModel.selectableItems(m5).stream().min((a, b) -> Integer.compare(a.weight(), b.weight())).orElseThrow();
		assertEquals(300L * 326 / rarest.weight(), RngMeterModel.requiredScore(m5, rarest.key(), RULES));
	}

	@Test
	void multiplierRisesLinearlyToThree() {
		assertEquals(1.0, RngMeterModel.weightMultiplier(0, 1000));
		assertEquals(2.0, RngMeterModel.weightMultiplier(500, 1000));
		assertEquals(3.0, RngMeterModel.weightMultiplier(1000, 1000));
		assertEquals(3.0, RngMeterModel.weightMultiplier(5000, 1000));
	}

	@Test
	void scoreGainByTierAndPity() {
		ResolvedModifiers none = ResolvedModifiers.NONE;
		ResolvedModifiers pity = new ResolvedModifiers(BossLuck.NONE, TreasureAccessory.NONE, 0, 0, 0, 10);
		RuleVariants truncate = RULES.withSRunMeterGain(RuleVariants.SRunMeterGain.TRUNCATE);
		RuleVariants combined = RULES.withSRunMeterGain(RuleVariants.SRunMeterGain.COMBINED_MULTIPLIER);
		assertEquals(RuleVariants.SRunMeterGain.ROUND, RULES.sRunMeterGain());
		assertEquals(317, RngMeterModel.scoreGain(ScoreTier.S_PLUS, 317, none, RULES));
		assertEquals(196, RngMeterModel.scoreGain(ScoreTier.S, 280, none, RULES));
		assertEquals(197, RngMeterModel.scoreGain(ScoreTier.S, 281, none, RULES));
		assertEquals(196, RngMeterModel.scoreGain(ScoreTier.S, 281, none, truncate));
		assertEquals(196, RngMeterModel.scoreGain(ScoreTier.S, 281, none, combined));
		// 275 x 0.7 is 192.49999999999997 as a double; half up must still give 193.
		assertEquals(193, RngMeterModel.scoreGain(ScoreTier.S, 275, none, RULES));
		assertEquals(192, RngMeterModel.scoreGain(ScoreTier.S, 275, none, truncate));
		assertEquals(0, RngMeterModel.scoreGain(ScoreTier.A, 250, none, RULES));
		assertEquals(229, RngMeterModel.scoreGain(ScoreTier.S, 298, pity, RULES));
		assertEquals(229, RngMeterModel.scoreGain(ScoreTier.S, 298, pity, combined));
		assertEquals(228, RngMeterModel.scoreGain(ScoreTier.S, 298, pity, truncate));
		assertEquals(208, RngMeterModel.scoreGain(ScoreTier.S, 298, pity, combined.withPityApplication(RuleVariants.PityApplication.NOT_APPLIED)));
		assertEquals(348, RngMeterModel.scoreGain(ScoreTier.S_PLUS, 317, pity, RULES));
		assertEquals(317, RngMeterModel.scoreGain(ScoreTier.S_PLUS, 317, pity, RULES.withPityApplication(RuleVariants.PityApplication.NOT_APPLIED)));
	}

	@Test
	void progressionResetAndOverflowVariants() {
		RngMeterState state = new RngMeterState(FloorId.F1, "BONZO_STAFF", 32000);
		assertEquals(200 + 300, RngMeterModel.afterRun(state, 31800, true, false, 300, RULES).storedScore());
		assertEquals(300, RngMeterModel.afterRun(state, 31800, true, false, 300, RULES.withMeterOverflow(RuleVariants.MeterOverflow.RESET_TO_ZERO)).storedScore());
		assertEquals(32300, RngMeterModel.afterRun(state, 31800, true, false, 300, RULES.withMeterResetTrigger(RuleVariants.MeterResetTrigger.ON_PURCHASE)).storedScore());
		assertEquals(200 + 300, RngMeterModel.afterRun(state, 31800, true, true, 300, RULES.withMeterResetTrigger(RuleVariants.MeterResetTrigger.ON_PURCHASE)).storedScore());

		RngMeterState partial = new RngMeterState(FloorId.F1, "BONZO_STAFF", 10000);
		assertEquals(300, RngMeterModel.afterRun(partial, 31800, true, false, 300, RULES).storedScore());
		assertEquals(10300, RngMeterModel.afterRun(partial, 31800, false, false, 300, RULES).storedScore());

		RngMeterState unselected = new RngMeterState(FloorId.F1, null, 50000);
		assertEquals(50300, RngMeterModel.afterRun(unselected, 0, true, true, 300, RULES).storedScore());
	}

	@Test
	void selectionCarryOverVariants() {
		FloorTable f1 = LuckTestData.defaults().floor(FloorId.F1);
		RngMeterState stored = new RngMeterState(FloorId.F1, null, 50000);
		assertEquals(31800, RngMeterModel.select(stored, f1, "BONZO_STAFF", RULES).storedScore());
		assertEquals(50000, RngMeterModel.select(stored, f1, "BONZO_STAFF", RULES.withSelectionCarryOver(RuleVariants.SelectionCarryOver.UNCAPPED)).storedScore());
	}

	@Test
	void guaranteePlacementVariants() {
		FloorTable f7 = LuckTestData.defaults().floor(FloorId.F7);
		List<ChestTier> sRun = List.of(ChestTier.WOOD, ChestTier.GOLD, ChestTier.DIAMOND, ChestTier.EMERALD, ChestTier.OBSIDIAN);
		List<ChestTier> sPlusRun = List.of(ChestTier.values());
		assertNull(RngMeterModel.guaranteeTier(f7, "NECRON_HANDLE", sRun, RULES));
		assertEquals(ChestTier.BEDROCK, RngMeterModel.guaranteeTier(f7, "NECRON_HANDLE", sPlusRun, RULES));

		String shared = null;
		ChestTier lowest = null;
		for (LootEntry entry : RngMeterModel.selectableItems(f7)) {
			for (ChestTier tier : ChestTier.values()) {
				LootEntry inChest = f7.chest(tier).entry(entry.key());
				if (inChest != null && inChest.role() == LootEntry.Role.WEIGHTED) {
					if (tier != ChestTier.BEDROCK && (lowest == null || tier.ordinal() < lowest.ordinal())) {
						lowest = tier;
						shared = entry.key();
					}
					break;
				}
			}
		}
		assertEquals(lowest, RngMeterModel.guaranteeTier(f7, shared, sPlusRun, RULES));
		assertEquals(ChestTier.BEDROCK, RngMeterModel.guaranteeTier(f7, shared, sPlusRun,
			RULES.withGuaranteePlacement(RuleVariants.GuaranteePlacement.HIGHEST_ELIGIBLE_TIER_CONTAINING)));
	}

	static String canonicalNeuKey(String neu) {
		int separator = neu.indexOf(';');
		if (separator < 0) return LootEntry.itemKey(neu);
		String id = neu.substring(0, separator);
		int level = Integer.parseInt(neu.substring(separator + 1));
		if (id.equals("SPIRIT")) return LootEntry.petKey(id, PET_TIERS[level]);
		return LootEntry.enchantmentKey(id, level);
	}
}
