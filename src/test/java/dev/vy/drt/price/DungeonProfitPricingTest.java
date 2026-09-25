package dev.vy.drt.price;

import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.vy.drt.config.DrtConfig;
import dev.vy.drt.config.DungeonLootEntry;
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

	@SuppressWarnings("unchecked")
	private static void setStaticMap(String fieldName, Map<?, ?> value) throws Exception {
		Field field = PriceCache.class.getDeclaredField(fieldName);
		field.setAccessible(true);
		field.set(null, value);
	}
}
