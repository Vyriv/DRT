package dev.vy.drt.luck;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.vy.drt.luck.data.ChestTable;
import dev.vy.drt.luck.rules.ModifierPreset.BossLuck;
import dev.vy.drt.luck.rules.ModifierPreset.TreasureAccessory;
import dev.vy.drt.luck.rules.QualityCalculator;
import dev.vy.drt.luck.rules.ResolvedModifiers;
import dev.vy.drt.luck.rules.RuleVariants;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class QualityCalculatorTest {
	/** Wiki "max bonus": S+, Treasure Artifact, Boss Luck IV, Catacombs Box 13 (Box X with maxed Echoes). */
	static final ResolvedModifiers MAX = new ResolvedModifiers(BossLuck.IV, TreasureAccessory.ARTIFACT, 10, 10, 10, 0);
	static final RuleVariants RULES = RuleVariants.defaults();

	@Test
	void knownChestQualities() {
		assertEquals(388, QualityCalculator.quality(370, true, ResolvedModifiers.NONE, RULES));
		assertEquals(459, QualityCalculator.quality(370, true, MAX, RULES));
		assertEquals(430, QualityCalculator.quality(410, true, ResolvedModifiers.NONE, RULES));
		assertEquals(370, QualityCalculator.quality(370, false, ResolvedModifiers.NONE, RULES));
	}

	@Test
	void maxedEchoesGiveBoxThirteen() {
		assertEquals(13.0, MAX.catacombsBoxBonus(), 1e-9);
		assertEquals(10.4, new ResolvedModifiers(BossLuck.NONE, TreasureAccessory.NONE, 8, 10, 10, 0).catacombsBoxBonus(), 1e-9);
		assertEquals(8.0, new ResolvedModifiers(BossLuck.NONE, TreasureAccessory.NONE, 8, 0, 10, 0).catacombsBoxBonus(), 1e-9);
	}

	@Test
	void ruleVariantsChangeQualityAsDocumented() {
		assertEquals(389, QualityCalculator.quality(370, true, ResolvedModifiers.NONE, RULES.withSPlusRounding(RuleVariants.SPlusQualityRounding.HALF_UP)));
		// Applying modifiers once instead of twice: round(388 x 1.03 + 23) = 423.
		assertEquals(423, QualityCalculator.quality(370, true, MAX, RULES.withModifierApplication(RuleVariants.ModifierApplication.ONCE)));
		// Box IX with maxed Echoes is 11.7: exact gives 370 -> 382 -> 394, floored (11) gives 381 -> 392.
		ResolvedModifiers fractionalBox = new ResolvedModifiers(BossLuck.NONE, TreasureAccessory.NONE, 9, 10, 10, 0);
		assertEquals(394, QualityCalculator.quality(370, false, fractionalBox, RULES));
		assertEquals(392, QualityCalculator.quality(370, false, fractionalBox, RULES.withBoxRounding(RuleVariants.BoxBonusRounding.FLOOR)));
	}

	@Test
	void reproducesEveryWikiTableHeader() {
		JsonObject floors = LuckTestData.fixture("wiki-average-chances.json").getAsJsonObject("floors");
		List<String> failures = new ArrayList<>();
		int checked = 0;
		for (Map.Entry<String, JsonElement> floorEntry : floors.entrySet()) {
			FloorId floor = FloorId.parse(floorEntry.getKey());
			for (Map.Entry<String, JsonElement> chestEntry : floorEntry.getValue().getAsJsonObject().entrySet()) {
				ChestTier tier = ChestTier.parse(chestEntry.getKey());
				JsonObject header = chestEntry.getValue().getAsJsonObject();
				ChestTable chest = LuckTestData.baseData().floor(floor).chest(tier);
				// Bedrock requires S+ (so its "no bonus" header includes S+) except on M7, where completion opens every chest.
				boolean bedrockSPlus = tier == ChestTier.BEDROCK && floor != FloorId.M7;
				int noBonus = QualityCalculator.quality(chest.baseQuality(), bedrockSPlus, ResolvedModifiers.NONE, RULES);
				int maxBonus = QualityCalculator.quality(chest.baseQuality(), true, MAX, RULES);
				if (noBonus != header.get("qualityNoBonus").getAsInt()) failures.add(floor + " " + tier + " no bonus " + noBonus + " vs " + header.get("qualityNoBonus"));
				if (maxBonus != header.get("qualityMaxBonus").getAsInt()) failures.add(floor + " " + tier + " max " + maxBonus + " vs " + header.get("qualityMaxBonus"));
				checked++;
			}
		}
		assertEquals(76, checked);
		assertEquals(List.of(), failures);
	}
}
