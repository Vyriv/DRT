package dev.vy.drt.price;

import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.vy.drt.config.DrtConfig;
import dev.vy.drt.config.DungeonFloor;
import dev.vy.drt.config.DungeonLootEntry;
import dev.vy.drt.config.KuudraKeyShopCost;
import java.lang.reflect.Field;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class DungeonProfitPricingTest {
	@BeforeEach
	void seedCrimsonBazaarPrice() throws Exception {
		setStaticMap("itemIdToBazaarData", Map.of(
			"ESSENCE_CRIMSON",
			new PriceCache.BazaarPriceData("ESSENCE_CRIMSON", 1000.0D, 900.0D, 1100.0D, 1050.0D)
		));
		setStaticMap("itemIdToPrice", Map.of("ESSENCE_CRIMSON", 1000.0D));
		setStaticMap("itemIdToSource", Map.of("ESSENCE_CRIMSON", "bazaar"));
	}

	@AfterEach
	void clearSeededPrices() throws Exception {
		setStaticMap("itemIdToBazaarData", Map.of());
		setStaticMap("itemIdToPrice", Map.of());
		setStaticMap("itemIdToSource", Map.of());
		setStaticMap("itemIdToAuctionData", Map.of());
	}

	@Test
	void crimsonEssencePricesFromBazaarEvenWithBlankOrAlternateId() {
		DrtConfig config = new DrtConfig();
		config.essenceCountsTowardProfit = true;
		config.bazaarPriceMode = "INSTANT";

		long blankId = DungeonProfitPricing.resolveUnitPrice(
			new DungeonLootEntry("CRIMSON ESSENCE", "", 10),
			config
		);
		long altId = DungeonProfitPricing.resolveUnitPrice(
			new DungeonLootEntry("Crimson Essence", "CRIMSON_ESSENCE", 10),
			config
		);
		long canonical = DungeonProfitPricing.resolveUnitPrice(
			new DungeonLootEntry("CRIMSON ESSENCE", "ESSENCE_CRIMSON", 10),
			config
		);

		assertEquals(1000L, blankId);
		assertEquals(1000L, altId);
		assertEquals(1000L, canonical);

		long total = DungeonProfitPricing.resolveTotalPrice(
			new DungeonLootEntry("CRIMSON ESSENCE", "", 10),
			config
		);
		assertEquals(10_000L, total);
	}

	@Test
	void crimsonEssenceExcludedWhenEssenceToggleOff() {
		DrtConfig config = new DrtConfig();
		config.essenceCountsTowardProfit = false;
		long unit = DungeonProfitPricing.resolveUnitPrice(
			new DungeonLootEntry("CRIMSON ESSENCE", "ESSENCE_CRIMSON", 100),
			config
		);
		assertEquals(0L, unit);
	}

	@Test
	void witherStillUsesConfigDefault() {
		DrtConfig config = new DrtConfig();
		config.witherEssenceValuePer = 2600;
		long unit = DungeonProfitPricing.resolveUnitPrice(
			new DungeonLootEntry("WITHER ESSENCE", "", 1),
			config
		);
		assertEquals(2600L, unit);
	}

	@Test
	void shadowAssassinCloakPricesByNameForOlderBlankIds() throws Exception {
		setStaticMap("itemIdToAuctionData", Map.of(
			"SHADOW_ASSASSIN_CLOAK",
			new PriceCache.AuctionPriceData("SHADOW_ASSASSIN_CLOAK", 2_240_000.0D, 2_196_862.0D, 2_284_949.0D)
		));

		long unit = DungeonProfitPricing.resolveUnitPrice(
			new DungeonLootEntry("Shadow Assassin Cloak", "", 1),
			new DrtConfig()
		);

		assertEquals(2_196_862L, unit);
	}

	@Test
	void renamedKuudraVitalityBooksUseManaPrices() throws Exception {
		setStaticMap("itemIdToPrice", Map.of(
			"ENCHANTMENT_HARDENED_MANA_5", 189_000.0D,
			"ENCHANTMENT_MANA_VAMPIRE_5", 439_000.0D
		));
		setStaticMap("itemIdToSource", Map.of(
			"ENCHANTMENT_HARDENED_MANA_5", "auction",
			"ENCHANTMENT_MANA_VAMPIRE_5", "auction"
		));
		DrtConfig config = new DrtConfig();

		long hardened = DungeonProfitPricing.resolveUnitPrice(
			new DungeonLootEntry("Enchanted Book (Hardened Vitality V)", "ENCHANTMENT_HARDENED_VITALITY_5", 6),
			config
		);
		long vampiric = DungeonProfitPricing.resolveUnitPrice(
			new DungeonLootEntry("Enchanted Book (Vampiric Vitality V)", "ENCHANTMENT_VAMPIRIC_VITALITY_5", 2),
			config
		);

		assertEquals(189_000L, hardened);
		assertEquals(439_000L, vampiric);
	}

	@Test
	void auroraStaffUsesRunicStaffPrice() throws Exception {
		setStaticMap("itemIdToAuctionData", Map.of(
			"RUNIC_STAFF",
			new PriceCache.AuctionPriceData("RUNIC_STAFF", 1_000_000.0D, 883_531.0D, 916_641.0D)
		));
		DrtConfig config = new DrtConfig();
		config.forceSalvageWands = false;

		long unit = DungeonProfitPricing.resolveUnitPrice(
			new DungeonLootEntry("Aurora Staff", "AURORA_STAFF", 1),
			config
		);

		assertEquals(883_531L, unit);
	}

	@Test
	void shopReadKeyCostBeatsRecipeEstimate() throws Exception {
		setStaticMap("itemIdToPrice", Map.of("ENCHANTED_MYCELIUM", 1_500.0D, "CORRUPTED_NETHER_STAR", 12_000.0D));
		DrtConfig config = new DrtConfig();
		KuudraKeyShopCost basic = new KuudraKeyShopCost();
		basic.coins = 198_000L;
		basic.materials.put(DungeonProfitPricing.kuudraKeyMaterialItemId("Enchanted Mycelium"), 2);
		basic.materials.put(DungeonProfitPricing.kuudraKeyMaterialItemId("Nether Star"), 2);
		config.kuudraKeyShopCosts.put(DungeonFloor.K1.name(), basic);

		assertEquals(198_000L + 3_000L + 24_000L, DungeonProfitPricing.resolveKuudraKeyCost(DungeonFloor.K1, config));
		// No shop read for Hot yet: recipe estimate, 400k coins + 6 Mycelium + 2 stars.
		assertEquals(400_000L + 9_000L + 24_000L, DungeonProfitPricing.resolveKuudraKeyCost(DungeonFloor.K2, config));
	}

	@Test
	void petAndShardBonusStackOnCrimsonEssence() {
		DrtConfig config = new DrtConfig();
		config.essenceCountsTowardProfit = true;
		config.kuudraPetEnabled = true;
		config.kuudraPetRarity = "LEGENDARY";
		config.kuudraPetLevel = 100;
		config.crimsonEssenceBonusPercent = 1;

		long total = DungeonProfitPricing.resolveTotalPrice(
			new DungeonLootEntry("CRIMSON ESSENCE", "ESSENCE_CRIMSON", 2000),
			config
		);

		assertEquals(2_420_000L, total);
	}

	@SuppressWarnings("unchecked")
	private static void setStaticMap(String fieldName, Map<?, ?> value) throws Exception {
		Field field = PriceCache.class.getDeclaredField(fieldName);
		field.setAccessible(true);
		field.set(null, value);
	}
}
