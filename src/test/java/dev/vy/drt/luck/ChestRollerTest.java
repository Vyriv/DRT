package dev.vy.drt.luck;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.vy.drt.luck.data.ChestTable;
import dev.vy.drt.luck.data.LootEntry;
import dev.vy.drt.luck.engine.ChestRoll;
import dev.vy.drt.luck.engine.ChestRoller;
import dev.vy.drt.luck.engine.ExactChestDistribution;
import dev.vy.drt.luck.engine.MeterBoost;
import dev.vy.drt.luck.rules.QualityCalculator;
import dev.vy.drt.luck.rules.ResolvedModifiers;
import dev.vy.drt.luck.rules.RuleVariants;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.SplittableRandom;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class ChestRollerTest {
	private static final int ROLLS = 20_000;
	private static final double Z = 5.5;

	@ParameterizedTest
	@EnumSource(FloorId.class)
	void monteCarloMatchesExactDistribution(FloorId floor) {
		List<String> failures = new ArrayList<>();
		for (ChestTable chest : LuckTestData.defaults().floor(floor).chests().values()) {
			int quality = QualityCalculator.quality(chest, ScoreTier.S_PLUS, ResolvedModifiers.NONE, RuleVariants.defaults());
			compare(chest, quality, MeterBoost.NONE, 1000L + floor.ordinal() * 10 + chest.tier().ordinal(), failures);
		}
		assertEquals(List.of(), failures);
	}

	@Test
	void monteCarloMatchesExactWithMeterBoostAndGuarantee() {
		List<String> failures = new ArrayList<>();
		ChestTable f7 = LuckTestData.defaults().floor(FloorId.F7).chest(ChestTier.BEDROCK);
		compare(f7, 388, new MeterBoost("NECRON_HANDLE", 2.4, false), 7L, failures);
		compare(f7, 388, new MeterBoost("NECRON_HANDLE", 3.0, true), 8L, failures);
		ChestTable m5 = LuckTestData.defaults().floor(FloorId.M5).chest(ChestTier.OBSIDIAN);
		compare(m5, 373, new MeterBoost(m5.entries().get(0).key(), 3.0, true), 9L, failures);
		assertEquals(List.of(), failures);
	}

	@Test
	void forcedItemIsPlacedFirstAndSpendsQuality() {
		ChestTable f7 = LuckTestData.defaults().floor(FloorId.F7).chest(ChestTier.BEDROCK);
		ChestRoll roll = ChestRoller.roll(f7, 388, new MeterBoost("NECRON_HANDLE", 3.0, true), new SplittableRandom(1));
		assertTrue(roll.meterForced());
		assertEquals("NECRON_HANDLE", roll.items().get(0).entry().key());
		int spent = roll.items().stream()
			.filter(i -> i.entry().role() != LootEntry.Role.GUARANTEED)
			.mapToInt(i -> i.entry().quality() * i.count())
			.sum();
		assertEquals(388, spent);
	}

	@Test
	void sameSeedGivesSameRoll() {
		ChestTable chest = LuckTestData.defaults().floor(FloorId.M7).chest(ChestTier.BEDROCK);
		assertEquals(
			ChestRoller.roll(chest, 441, MeterBoost.NONE, new SplittableRandom(42)),
			ChestRoller.roll(chest, 441, MeterBoost.NONE, new SplittableRandom(42))
		);
	}

	private static void compare(ChestTable chest, int quality, MeterBoost boost, long seed, List<String> failures) {
		ExactChestDistribution.Result exact = ExactChestDistribution.compute(chest, quality, boost);
		SplittableRandom random = new SplittableRandom(seed);
		Map<String, Integer> seen = new HashMap<>();
		long essencePicks = 0;
		for (int i = 0; i < ROLLS; i++) {
			ChestRoll roll = ChestRoller.roll(chest, quality, boost, random);
			for (ChestRoll.Item item : roll.items()) {
				seen.merge(item.entry().key(), 1, Integer::sum);
				if (item.entry().role() == LootEntry.Role.REPEATABLE_ESSENCE) essencePicks += item.count();
			}
		}
		for (Map.Entry<String, Double> entry : exact.appearance().entrySet()) {
			double p = entry.getValue();
			double observed = seen.getOrDefault(entry.getKey(), 0) / (double) ROLLS;
			double tolerance = Z * Math.sqrt(Math.max(p * (1 - p), 1e-9) / ROLLS) + 1e-9;
			if (Math.abs(observed - p) > tolerance) {
				failures.add(chest.floor() + " " + chest.tier() + " q" + quality + " " + entry.getKey() + " exact " + p + " observed " + observed);
			}
		}
		double meanEssence = essencePicks / (double) ROLLS;
		if (Math.abs(meanEssence - exact.expectedRepeatableEssence()) > 0.1 + 0.05 * exact.expectedRepeatableEssence()) {
			failures.add(chest.floor() + " " + chest.tier() + " essence " + exact.expectedRepeatableEssence() + " observed " + meanEssence);
		}
	}
}
