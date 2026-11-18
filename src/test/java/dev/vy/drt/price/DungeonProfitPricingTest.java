package dev.vy.drt.price;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.vy.drt.config.ChestCostBreakdown;
import dev.vy.drt.config.DrtConfig;
import dev.vy.drt.config.DungeonFloor;
import dev.vy.drt.config.DungeonLootEntry;
import dev.vy.drt.config.KuudraKeyShopCost;
import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class DungeonProfitPricingTest {
	@Test
	void witherSpectreShardUsesProviderSpecterSpelling() throws Exception {
		setStaticMap("itemIdToBazaarData", Map.of("SHARD_WITHER_SPECTER",
			new PriceCache.BazaarPriceData("SHARD_WITHER_SPECTER", 5292.7D, 464.1D, 10825.1D, 54997.3D)));
		DungeonLootEntry shard = new DungeonLootEntry("Wither Spectre Shard", "SHARD_WITHER_SPECTRE", 2);
		assertEquals(5292.7D, DungeonProfitPricing.resolveUnitPriceExact(shard, new DrtConfig()), 0.0001D);
		assertEquals(10585.4D, DungeonProfitPricing.resolveTotalPriceExact(shard, new DrtConfig()), 0.0001D);
		assertTrue(DungeonProfitPricing.hasKnownUnitPrice(shard, new DrtConfig()));
		DungeonLootEntry olderRecord = new DungeonLootEntry("Wither Spectre Shard", "", 2);
		assertEquals(5292.7D, DungeonProfitPricing.resolveUnitPriceExact(olderRecord, new DrtConfig()), 0.0001D);
	}
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
	void canonicalizesLegacyNecromancerBroochIdAndKeepsPriceKnown() throws Exception {
		setStaticMap("itemIdToBazaarData", Map.of(
			"NECROMANCER_BROOCH",
			new PriceCache.BazaarPriceData("NECROMANCER_BROOCH", 1300.0D, 1000.0D, 1200.0D, 1400.0D)
		));
		DrtConfig config = new DrtConfig();
		DungeonLootEntry legacy = new DungeonLootEntry("Necromancer's Brooch", "NECROMANCERS_BROOCH", 1);

		assertEquals(1300L, DungeonProfitPricing.resolveUnitPrice(legacy, config));
		org.junit.jupiter.api.Assertions.assertTrue(DungeonProfitPricing.hasKnownUnitPrice(legacy, config));
	}

	@Test
	void unavailablePriceIsNotReportedAsKnownZero() {
		DungeonLootEntry unknown = new DungeonLootEntry("Unsupported Dungeon Reward", "UNSUPPORTED_DUNGEON_REWARD", 1);
		assertEquals(0L, DungeonProfitPricing.resolveUnitPrice(unknown, new DrtConfig()));
		org.junit.jupiter.api.Assertions.assertFalse(DungeonProfitPricing.hasKnownUnitPrice(unknown, new DrtConfig()));
	}

	@Test
	void summaryKeepsKnownSubtotalWhenOneItemIsUnknown() throws Exception {
		setStaticMap("itemIdToAuctionData", Map.of(
			"KNOWN_REWARD", new PriceCache.AuctionPriceData("KNOWN_REWARD", 100.0D, 100.0D, 100.0D),
			"KNOWN_BOOK", new PriceCache.AuctionPriceData("KNOWN_BOOK", 50.0D, 50.0D, 50.0D)
		));
		setStaticMap("itemIdToPrice", Map.of("KNOWN_REWARD", 100.0D, "KNOWN_BOOK", 50.0D));
		DungeonProfitPricing.ValueSummary summary = DungeonProfitPricing.summarizeLootValue(List.of(
			new DungeonLootEntry("Known Reward", "KNOWN_REWARD", 2),
			new DungeonLootEntry("Unknown Reward", "UNKNOWN_REWARD", 1),
			new DungeonLootEntry("Known Book", "KNOWN_BOOK", 3)
		), new DrtConfig());

		assertEquals(350L, summary.knownValueCoins());
		assertEquals(2, summary.pricedEntries());
		assertEquals(1, summary.unpricedEntries());
		org.junit.jupiter.api.Assertions.assertFalse(summary.complete());
	}

	@Test
	void summaryCountsSeveralUnknownItemsWithoutDiscardingKnownValue() throws Exception {
		setStaticMap("itemIdToAuctionData", Map.of(
			"KNOWN_REWARD", new PriceCache.AuctionPriceData("KNOWN_REWARD", 125.0D, 125.0D, 125.0D)
		));
		setStaticMap("itemIdToPrice", Map.of("KNOWN_REWARD", 125.0D));
		DungeonProfitPricing.ValueSummary summary = DungeonProfitPricing.summarizeLootValue(List.of(
			new DungeonLootEntry("Known Reward", "KNOWN_REWARD", 1),
			new DungeonLootEntry("Unknown Book", "UNKNOWN_BOOK", 1),
			new DungeonLootEntry("Unknown Stone", "UNKNOWN_STONE", 4)
		), new DrtConfig());

		assertEquals(125L, summary.knownValueCoins());
		assertEquals(1, summary.pricedEntries());
		assertEquals(2, summary.unpricedEntries());
	}

	@Test
	void configuredZeroValueExclusionIsKnownAndDoesNotBecomeUnknown() {
		DrtConfig config = new DrtConfig();
		config.essenceCountsTowardProfit = false;
		DungeonProfitPricing.ValueSummary summary = DungeonProfitPricing.summarizeLootValue(List.of(
			new DungeonLootEntry("Wither Essence", "ESSENCE_WITHER", 10)
		), config);

		assertEquals(0L, summary.knownValueCoins());
		assertEquals(1, summary.pricedEntries());
		assertEquals(0, summary.unpricedEntries());
		org.junit.jupiter.api.Assertions.assertTrue(summary.complete());
	}

	@Test
	void priceSnapshotChangesAreReflectedInSubsequentSummaries() throws Exception {
		DungeonLootEntry entry = new DungeonLootEntry("Historical Reward", "HISTORICAL_REWARD", 1);
		DrtConfig config = new DrtConfig();
		setStaticMap("itemIdToAuctionData", Map.of());
		setStaticMap("itemIdToPrice", Map.of());
		assertEquals(1, DungeonProfitPricing.summarizeLootValue(List.of(entry), config).unpricedEntries());

		setStaticMap("itemIdToAuctionData", Map.of(
			"HISTORICAL_REWARD", new PriceCache.AuctionPriceData("HISTORICAL_REWARD", 777.0D, 777.0D, 777.0D)
		));
		setStaticMap("itemIdToPrice", Map.of("HISTORICAL_REWARD", 777.0D));
		DungeonProfitPricing.ValueSummary refreshed = DungeonProfitPricing.summarizeLootValue(List.of(entry), config);
		assertEquals(777L, refreshed.knownValueCoins());
		assertEquals(0, refreshed.unpricedEntries());
	}

	@Test
	void knownZeroSellPriceStillCompletesMixedChestWithExpensiveReward() throws Exception {
		setStaticMap("itemIdToBazaarData", Map.of(
			"RECOMBOBULATOR_3000",
			new PriceCache.BazaarPriceData("RECOMBOBULATOR_3000", 8_682_555.3D, 5_000_000.0D, 8_981_698.6D, 9_350_000.0D),
			"ENCHANTMENT_ULTIMATE_NO_PAIN_NO_GAIN_1",
			new PriceCache.BazaarPriceData("ENCHANTMENT_ULTIMATE_NO_PAIN_NO_GAIN_1", 0.0D, 0.0D, 103.7D, 149.2D)
		));
		DrtConfig config = new DrtConfig();
		config.bazaarPriceMode = "INSTANT";
		DungeonLootEntry recomb = new DungeonLootEntry("Recombobulator 3000", "RECOMBOBULATOR_3000", 1);
		DungeonLootEntry noPain = new DungeonLootEntry("Enchanted Book (No Pain No Gain I)", "ENCHANTMENT_ULTIMATE_NO_PAIN_NO_GAIN_1", 1);

		assertTrue(DungeonProfitPricing.hasKnownUnitPrice(recomb, config));
		assertTrue(DungeonProfitPricing.hasKnownUnitPrice(noPain, config));
		assertEquals(0.0D, DungeonProfitPricing.resolveUnitPriceExact(noPain, config));
		DungeonProfitPricing.ValueSummary summary = DungeonProfitPricing.summarizeLootValue(List.of(recomb, noPain), config);
		assertEquals(8_682_555L, summary.knownValueCoins());
		assertEquals(2, summary.pricedEntries());
		assertEquals(0, summary.unpricedEntries());
		assertTrue(summary.complete());
	}

	@Test
	void explicitZeroInstantSellIsKnownZeroNotBuySide() throws Exception {
		setStaticMap("itemIdToBazaarData", Map.of(
			"ENCHANTMENT_FEATHER_FALLING_6",
			new PriceCache.BazaarPriceData("ENCHANTMENT_FEATHER_FALLING_6", 0.0D, 0.0D, 31.5D, 32.0D),
			"ENCHANTMENT_ULTIMATE_BANK_1",
			new PriceCache.BazaarPriceData("ENCHANTMENT_ULTIMATE_BANK_1", 0.0D, 0.0D, 1.0D, 1.0D)
		));
		DrtConfig config = new DrtConfig();
		config.bazaarPriceMode = "INSTANT";
		DungeonLootEntry feather = new DungeonLootEntry("Enchanted Book (Feather Falling VI)", "ENCHANTMENT_FEATHER_FALLING_6", 1);
		DungeonLootEntry bank = new DungeonLootEntry("Enchanted Book (Bank I)", "ENCHANTMENT_ULTIMATE_BANK_1", 1);

		assertTrue(DungeonProfitPricing.hasKnownUnitPrice(feather, config));
		assertTrue(DungeonProfitPricing.hasKnownUnitPrice(bank, config));
		assertEquals(0.0D, DungeonProfitPricing.resolveUnitPriceExact(feather, config));
		assertEquals(0.0D, DungeonProfitPricing.resolveUnitPriceExact(bank, config));
		assertEquals(0, DungeonProfitPricing.summarizeLootValue(List.of(feather, bank), config).unpricedEntries());
	}

	@Test
	void positiveInstantSellStaysOnTheSellSide() throws Exception {
		setStaticMap("itemIdToBazaarData", Map.of(
			"ENCHANTMENT_LETHALITY_6",
			new PriceCache.BazaarPriceData("ENCHANTMENT_LETHALITY_6", 10.7D, 3.0D, 38999.1D, 49997.9D)
		));
		DrtConfig config = new DrtConfig();
		config.bazaarPriceMode = "INSTANT";
		DungeonLootEntry lethality = new DungeonLootEntry("Enchanted Book (Lethality VI)", "ENCHANTMENT_LETHALITY_6", 1);

		assertEquals(10.7D, DungeonProfitPricing.resolveUnitPriceExact(lethality, config), 0.0001D);
		assertTrue(DungeonProfitPricing.hasKnownUnitPrice(lethality, config));
		assertEquals(11L, DungeonProfitPricing.resolveTotalPrice(lethality, config));
	}

	@Test
	void fractionalBazaarSellQuoteIsPreservedAndNotRoundedToOne() throws Exception {
		setStaticMap("itemIdToBazaarData", Map.of(
			"NECROMANCER_BROOCH",
			new PriceCache.BazaarPriceData("NECROMANCER_BROOCH", 0.1D, 0.1D, 6999.9D, 11997.7D)
		));
		DrtConfig config = new DrtConfig();
		config.bazaarPriceMode = "INSTANT";

		assertEquals(0.1D, DungeonProfitPricing.resolveUnitPriceExact(
			new DungeonLootEntry("Necromancer's Brooch", "NECROMANCER_BROOCH", 1), config));
		assertEquals(0L, DungeonProfitPricing.resolveUnitPrice(
			new DungeonLootEntry("Necromancer's Brooch", "NECROMANCER_BROOCH", 1), config));
		org.junit.jupiter.api.Assertions.assertTrue(DungeonProfitPricing.hasKnownUnitPrice(
			new DungeonLootEntry("Necromancer's Brooch", "NECROMANCER_BROOCH", 1), config));
	}

	@Test
	void zeroBazaarSellQuoteIsKnownZeroAndDoesNotUseTheBuySide() throws Exception {
		Map<String, Double> prices = new HashMap<>();
		Map<String, String> sources = new HashMap<>();
		Map<String, PriceCache.BazaarPriceData> bazaar = new HashMap<>();
		PriceCache.loadBazaar(JsonParser.parseString("{\"ENCHANTMENT_INFINITE_QUIVER_6\":{\"ib\":22.6,\"is\":0,\"tb\":23.9,\"ts\":0}}").getAsJsonObject(), prices, sources, bazaar);
		setStaticMap("itemIdToPrice", prices);
		setStaticMap("itemIdToSource", sources);
		setStaticMap("itemIdToBazaarData", bazaar);
		DrtConfig config = new DrtConfig();
		DungeonLootEntry entry = new DungeonLootEntry("Enchanted Book (Infinite Quiver VI)", "ENCHANTMENT_INFINITE_QUIVER_6", 1);

		assertEquals(0.0D, DungeonProfitPricing.resolveUnitPriceExact(entry, config));
		assertTrue(DungeonProfitPricing.hasKnownUnitPrice(entry, config));
		assertEquals(0.0D, bazaar.get("ENCHANTMENT_INFINITE_QUIVER_6").instantSell());
		assertEquals(22.6D, bazaar.get("ENCHANTMENT_INFINITE_QUIVER_6").instantBuy());
	}

	@Test
	void allZeroVyrivEntryKeepsExplicitZeroSellWithoutInventingAPrice() throws Exception {
		Map<String, Double> prices = new HashMap<>();
		Map<String, String> sources = new HashMap<>();
		Map<String, PriceCache.BazaarPriceData> bazaar = new HashMap<>();
		PriceCache.loadBazaar(JsonParser.parseString("{\"ENCHANTMENT_INFINITE_QUIVER_4\":{\"ib\":0,\"is\":0,\"tb\":0,\"ts\":0}}").getAsJsonObject(), prices, sources, bazaar);
		setStaticMap("itemIdToPrice", prices);
		setStaticMap("itemIdToSource", sources);
		setStaticMap("itemIdToBazaarData", bazaar);
		DungeonLootEntry entry = new DungeonLootEntry("Enchanted Book (Infinite Quiver IV)", "ENCHANTMENT_INFINITE_QUIVER_4", 1);

		assertEquals(0.0D, DungeonProfitPricing.resolveUnitPriceExact(entry, new DrtConfig()));
		assertTrue(DungeonProfitPricing.hasKnownUnitPrice(entry, new DrtConfig()));
		assertEquals(0.0D, prices.get("ENCHANTMENT_INFINITE_QUIVER_4"));
	}

	@Test
	void missingBazaarItemStaysUnknownSeparatelyFromExplicitZero() throws Exception {
		setStaticMap("itemIdToBazaarData", Map.of());
		setStaticMap("itemIdToAuctionData", Map.of());
		setStaticMap("itemIdToPrice", Map.of());
		DungeonLootEntry entry = new DungeonLootEntry("Missing Book", "ENCHANTMENT_NOT_IN_API", 1);
		assertFalse(DungeonProfitPricing.hasKnownUnitPrice(entry, new DrtConfig()));
		assertTrue(Double.isNaN(DungeonProfitPricing.resolveUnitPriceExact(entry, new DrtConfig())));
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
	void legacyWisdomBookIdUsesUltimateWisdomPrice() throws Exception {
		setStaticMap("itemIdToBazaarData", Map.of(
			"ENCHANTMENT_ULTIMATE_WISDOM_1",
			new PriceCache.BazaarPriceData("ENCHANTMENT_ULTIMATE_WISDOM_1", 119_790.5D, 29_280.0D, 144_437.0D, 149_358.8D)
		));
		DrtConfig config = new DrtConfig();
		config.bazaarPriceMode = "INSTANT";

		long unit = DungeonProfitPricing.resolveUnitPrice(
			new DungeonLootEntry("Enchanted Book (Wisdom I)", "ENCHANTMENT_WISDOM_1", 1),
			config
		);

		assertEquals(119_791L, unit);
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
	void everyTierPricesMageAndBarbarianRecipes() throws Exception {
		setStaticMap("itemIdToPrice", Map.of(
			"ENCHANTED_MYCELIUM", 1_500.0D,
			"ENCHANTED_RED_SAND", 2_000.0D,
			"CORRUPTED_NETHER_STAR", 12_000.0D
		));
		DrtConfig mage = faction("MAGE", true, 0);
		assertEquals(227_000L, DungeonProfitPricing.resolveKuudraKeyCost(DungeonFloor.K1, mage));
		assertEquals(433_000L, DungeonProfitPricing.resolveKuudraKeyCost(DungeonFloor.K2, mage));
		assertEquals(804_000L, DungeonProfitPricing.resolveKuudraKeyCost(DungeonFloor.K3, mage));
		assertEquals(1_614_000L, DungeonProfitPricing.resolveKuudraKeyCost(DungeonFloor.K4, mage));
		assertEquals(3_204_000L, DungeonProfitPricing.resolveKuudraKeyCost(DungeonFloor.K5, mage));

		DrtConfig barbarian = faction("BARBARIAN", true, 0);
		assertEquals(228_000L, DungeonProfitPricing.resolveKuudraKeyCost(DungeonFloor.K1, barbarian));
		assertEquals(436_000L, DungeonProfitPricing.resolveKuudraKeyCost(DungeonFloor.K2, barbarian));
		assertEquals(814_000L, DungeonProfitPricing.resolveKuudraKeyCost(DungeonFloor.K3, barbarian));
		assertEquals(1_644_000L, DungeonProfitPricing.resolveKuudraKeyCost(DungeonFloor.K4, barbarian));
		assertEquals(3_264_000L, DungeonProfitPricing.resolveKuudraKeyCost(DungeonFloor.K5, barbarian));
	}

	@Test
	void unknownReputationKeepsTheFullCoinCost() throws Exception {
		setStaticMap("itemIdToPrice", Map.of(
			"ENCHANTED_MYCELIUM", 1_500.0D,
			"CORRUPTED_NETHER_STAR", 12_000.0D
		));
		DrtConfig unknown = faction("MAGE", false, 12_000);
		assertEquals(804_000L, DungeonProfitPricing.resolveKuudraKeyCost(DungeonFloor.K3, unknown));

		DrtConfig maxReputation = faction("MAGE", true, 12_000);
		assertEquals(654_000L, DungeonProfitPricing.resolveKuudraKeyCost(DungeonFloor.K3, maxReputation));
	}

	@Test
	void missingMaterialPriceIsNotTreatedAsFree() throws Exception {
		setStaticMap("itemIdToPrice", Map.of("CORRUPTED_NETHER_STAR", 12_000.0D));
		DrtConfig config = faction("MAGE", true, 0);
		DungeonProfitPricing.KuudraKeyCostQuote quote = DungeonProfitPricing.quoteKuudraKeyCost(DungeonFloor.K3, config);
		assertFalse(quote.known());

		ChestCostBreakdown pricedEarlier = new ChestCostBreakdown();
		pricedEarlier.kuudraKeyCostCoins = 500L;
		pricedEarlier.baseChestCostKnown = true;
		DungeonProfitPricing.applyPaidKuudraKey(pricedEarlier, DungeonFloor.K3, config);
		assertEquals(500L, pricedEarlier.kuudraKeyCostCoins);
		assertTrue(pricedEarlier.baseChestCostKnown);
		assertTrue(pricedEarlier.usedKuudraKey);

		ChestCostBreakdown fresh = new ChestCostBreakdown();
		DungeonProfitPricing.applyPaidKuudraKey(fresh, DungeonFloor.K3, config);
		assertEquals(0L, fresh.kuudraKeyCostCoins);
		assertFalse(fresh.baseChestCostKnown);
		assertTrue(fresh.usedKuudraKey);
		long lootValue = 1_000_000L;
		long profit = fresh.baseChestCostKnown ? lootValue - fresh.totalCostCoins() : 0L;
		assertEquals(0L, profit);
	}

	@Test
	void knownKeyIsChestCostOnceAndProfitSubtractsIt() throws Exception {
		setStaticMap("itemIdToPrice", Map.of(
			"ENCHANTED_MYCELIUM", 1_500.0D,
			"CORRUPTED_NETHER_STAR", 12_000.0D
		));
		DrtConfig config = faction("BARBARIAN", true, 0);
		assertFalse(DungeonProfitPricing.quoteKuudraKeyCost(DungeonFloor.K3, config).known());

		config.kuudraFaction = "MAGE";
		ChestCostBreakdown paid = new ChestCostBreakdown();
		DungeonProfitPricing.applyPaidKuudraKey(paid, DungeonFloor.K3, config);
		DungeonProfitPricing.applyPaidKuudraKey(paid, DungeonFloor.K3, config);
		assertEquals(0L, paid.baseChestCostCoins);
		assertTrue(paid.baseChestCostKnown);
		assertEquals(804_000L, paid.kuudraKeyCostCoins);
		assertEquals(804_000L, paid.totalCostCoins());
		assertEquals(4_196_000L, 5_000_000L - paid.totalCostCoins());
	}

	@Test
	void shopCoinsAreNotDiscountedAgainAndStayOnTheConfig() throws Exception {
		setStaticMap("itemIdToPrice", Map.of(
			"ENCHANTED_MYCELIUM", 1_500.0D,
			"CORRUPTED_NETHER_STAR", 12_000.0D
		));
		DrtConfig config = faction("MAGE", true, 12_000);
		KuudraKeyShopCost shop = new KuudraKeyShopCost();
		shop.coins = 712_500L;
		shop.materials.put("ENCHANTED_MYCELIUM", 20);
		shop.materials.put("CORRUPTED_NETHER_STAR", 2);
		config.kuudraKeyShopCosts.put(DungeonFloor.K3.name(), shop);

		assertEquals(766_500L, DungeonProfitPricing.resolveKuudraKeyCost(DungeonFloor.K3, config));

		DrtConfig afterWorldChange = new DrtConfig();
		afterWorldChange.kuudraFaction = config.kuudraFaction;
		afterWorldChange.kuudraReputation = config.kuudraReputation;
		afterWorldChange.kuudraReputationKnown = config.kuudraReputationKnown;
		afterWorldChange.kuudraKeyShopCosts.putAll(config.kuudraKeyShopCosts);
		assertEquals(766_500L, DungeonProfitPricing.resolveKuudraKeyCost(DungeonFloor.K3, afterWorldChange));
	}

	@Test
	void coinOnlyShopStillRequiresMaterialPrices() throws Exception {
		setStaticMap("itemIdToPrice", Map.of(
			"ENCHANTED_MYCELIUM", 1_500.0D,
			"CORRUPTED_NETHER_STAR", 12_000.0D
		));
		DrtConfig config = faction("MAGE", true, 12_000);
		KuudraKeyShopCost shop = new KuudraKeyShopCost();
		shop.coins = 198_000L;
		config.kuudraKeyShopCosts.put(DungeonFloor.K1.name(), shop);

		assertEquals(225_000L, DungeonProfitPricing.resolveKuudraKeyCost(DungeonFloor.K1, config));

		setStaticMap("itemIdToPrice", Map.of("CORRUPTED_NETHER_STAR", 12_000.0D));
		assertFalse(DungeonProfitPricing.quoteKuudraKeyCost(DungeonFloor.K1, config).known());
	}

	@Test
	void nonKuudraFloorHasNoKeyPrice() throws Exception {
		setStaticMap("itemIdToPrice", Map.of(
			"ENCHANTED_MYCELIUM", 1_500.0D,
			"CORRUPTED_NETHER_STAR", 12_000.0D
		));
		assertFalse(DungeonProfitPricing.quoteKuudraKeyCost(DungeonFloor.M7, faction("MAGE", true, 0)).known());
		assertEquals(0L, DungeonProfitPricing.resolveKuudraKeyCost(DungeonFloor.F7, faction("MAGE", true, 0)));
	}

	private static DrtConfig faction(String faction, boolean reputationKnown, int reputation) {
		DrtConfig config = new DrtConfig();
		config.kuudraFaction = faction;
		config.kuudraReputationKnown = reputationKnown;
		config.kuudraReputation = reputation;
		return config;
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
