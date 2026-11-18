package dev.vy.drt.price;

import dev.vy.drt.config.ChestCostBreakdown;
import dev.vy.drt.config.DrtConfig;
import dev.vy.drt.config.DungeonFloor;
import dev.vy.drt.config.DungeonLootEntry;
import dev.vy.drt.config.KuudraKeyShopCost;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class DungeonProfitPricing {
	private static final String ITEM_CRIMSON_ESSENCE = "ESSENCE_CRIMSON";
	private static final String ITEM_ENCHANTED_MYCELIUM = "ENCHANTED_MYCELIUM";
	private static final String ITEM_ENCHANTED_RED_SAND = "ENCHANTED_RED_SAND";
	private static final String ITEM_CORRUPTED_NETHER_STAR = "CORRUPTED_NETHER_STAR";
	private static final int KUUDRA_ARMOR_BASE_CRIMSON_ESSENCE = 108;
	private static final double KUUDRA_ARMOR_STAR_REFUND_MULTIPLIER = 0.63D;
	private static final int KUUDRA_TABLE_DROP_CRIMSON_ESSENCE = 500;

	private DungeonProfitPricing() {
	}

	public static long calculateLootValue(List<DungeonLootEntry> entries, DrtConfig config) {
		return summarizeLootValue(entries, config).knownValueCoins();
	}

	/**
	 * Calculates the value that can be supported by current price evidence without
	 * silently treating unavailable entries as zero-valued loot.
	 */
	public static ValueSummary summarizeLootValue(List<DungeonLootEntry> entries, DrtConfig config) {
		if (entries == null || entries.isEmpty()) return new ValueSummary(0L, 0, 0);
		double exactKnownValue = 0.0D;
		int pricedEntries = 0;
		int unpricedEntries = 0;
		for (DungeonLootEntry entry : entries) {
			if (entry == null) continue;
			if (hasKnownUnitPrice(entry, config)) {
				exactKnownValue += resolveTotalPriceExact(entry, config);
				pricedEntries++;
			} else {
				unpricedEntries++;
			}
		}
		return new ValueSummary(Math.round(exactKnownValue), pricedEntries, unpricedEntries, exactKnownValue);
	}

	public static long resolveTotalPrice(DungeonLootEntry entry, DrtConfig config) {
		if (entry == null) return 0L;
		return Math.round(resolveTotalPriceExact(entry, config));
	}

	/** Exact market value retained for fractional provider quotes. Unknown values return NaN. */
	public static double resolveTotalPriceExact(DungeonLootEntry entry, DrtConfig config) {
		if (entry == null) return Double.NaN;
		double unitPrice = resolveUnitPriceExact(entry, config);
		if (!Double.isFinite(unitPrice)) return Double.NaN;
		int quantity = Math.max(1, entry.quantity);
		if (isCrimsonEssenceEntry(entry)) {
			quantity = adjustedCrimsonEssenceAmount(quantity, config);
		}
		return unitPrice * quantity;
	}

	public static long resolveUnitPrice(DungeonLootEntry entry, DrtConfig config) {
		double exact = resolveUnitPriceExact(entry, config);
		return Double.isFinite(exact) ? Math.round(exact) : 0L;
	}

	/** Exact unit quote. A valid zero is returned as 0.0; an unresolved quote returns NaN. */
	public static double resolveUnitPriceExact(DungeonLootEntry entry, DrtConfig config) {
		if (entry == null) return Double.NaN;

		String rawUpper = entry.rawName == null ? "" : entry.rawName.trim().toUpperCase(Locale.ROOT);
		String itemIdUpper = entry.itemId == null ? "" : entry.itemId.trim().toUpperCase(Locale.ROOT);
		if (config != null && !config.essenceCountsTowardProfit && isEssenceItem(rawUpper, itemIdUpper)) {
			return 0.0D;
		}
		if (rawUpper.contains("WITHER ESSENCE") || itemIdUpper.equals("ESSENCE_WITHER")) {
			return Math.max(0, config.witherEssenceValuePer);
		}
		if (rawUpper.contains("UNDEAD ESSENCE") || itemIdUpper.equals("ESSENCE_UNDEAD")) {
			return Math.max(0, config.undeadEssenceValuePer);
		}
		// Crimson has no config default (bazaar-priced). Resolve by name/id so blank or
		// alternate ids (CRIMSON_ESSENCE) still get ESSENCE_CRIMSON bazaar value.
		if (isCrimsonEssenceEntry(entry)) {
			double crimsonPrice = resolveSellValueExact(ITEM_CRIMSON_ESSENCE, config);
			if (Double.isFinite(crimsonPrice)) return crimsonPrice;
		}

		if (itemIdUpper.isBlank()) {
			itemIdUpper = inferPriceItemIdFromRawName(rawUpper);
		}
		if (!itemIdUpper.isBlank()) {
			String resolvedItemId = normalizePriceItemId(itemIdUpper);
			Long forcedSalvageValue = resolveForcedKuudraSalvageValue(resolvedItemId, rawUpper, config);
			if (forcedSalvageValue != null) return forcedSalvageValue.doubleValue();

			PriceCache.AuctionPriceData auctionData = PriceCache.getAuctionHouse(resolvedItemId);
			if (auctionData != null) {
				Double selected = firstPresent(auctionData.p3d(), auctionData.lbin(), auctionData.p7d());
				if (selected != null) return selected;
			}
			PriceCache.BazaarPriceData bazaarData = PriceCache.getBazaar(resolvedItemId);
			if (bazaarData != null) {
				Double selected = selectedBazaarPrice(bazaarData, config);
				if (selected != null) return selected;
				return Double.NaN;
			}
			PriceCache.PriceLookup lookup = PriceCache.get(resolvedItemId);
			if (lookup != null) return lookup.price();
		}
		return Double.NaN;
	}

	/** Returns whether zero means a real configured exclusion/value rather than an unavailable market price. */
	public static boolean hasKnownUnitPrice(DungeonLootEntry entry, DrtConfig config) {
		if (entry == null) return false;
		String rawUpper = entry.rawName == null ? "" : entry.rawName.trim().toUpperCase(Locale.ROOT);
		String itemIdUpper = entry.itemId == null ? "" : entry.itemId.trim().toUpperCase(Locale.ROOT);
		if (config != null && !config.essenceCountsTowardProfit && isEssenceItem(rawUpper, itemIdUpper)) return true;
		if (rawUpper.contains("WITHER ESSENCE") || itemIdUpper.equals("ESSENCE_WITHER")) return config != null;
		if (rawUpper.contains("UNDEAD ESSENCE") || itemIdUpper.equals("ESSENCE_UNDEAD")) return config != null;
		if (isCrimsonEssenceEntry(entry) && Double.isFinite(resolveSellValueExact(ITEM_CRIMSON_ESSENCE, config))) return true;

		if (itemIdUpper.isBlank()) itemIdUpper = inferPriceItemIdFromRawName(rawUpper);
		if (itemIdUpper.isBlank()) return false;
		String resolvedItemId = normalizePriceItemId(itemIdUpper);
		if (resolveForcedKuudraSalvageValue(resolvedItemId, rawUpper, config) != null) return true;
		PriceCache.AuctionPriceData auctionData = PriceCache.getAuctionHouse(resolvedItemId);
		if (auctionData != null && (present(auctionData.p3d()) || present(auctionData.lbin()) || present(auctionData.p7d()))) return true;
		PriceCache.BazaarPriceData bazaarData = PriceCache.getBazaar(resolvedItemId);
		if (bazaarData != null) return selectedBazaarPrice(bazaarData, config) != null;
		PriceCache.PriceLookup lookup = PriceCache.get(resolvedItemId);
		return lookup != null && Double.isFinite(lookup.price()) && lookup.price() >= 0.0D;
	}

	public static boolean hasUnknownUnitPrice(List<DungeonLootEntry> entries, DrtConfig config) {
		if (entries == null) return false;
		for (DungeonLootEntry entry : entries) {
			if (entry != null && !hasKnownUnitPrice(entry, config)) return true;
		}
		return false;
	}

	public static boolean isForcedSalvageValued(DungeonLootEntry entry, DrtConfig config) {
		if (entry == null || config == null) return false;
		String itemIdUpper = entry.itemId == null ? "" : entry.itemId.trim().toUpperCase(Locale.ROOT);
		if (itemIdUpper.isBlank()) return false;
		KuudraSalvageCategory category = kuudraSalvageCategory(normalizePriceItemId(itemIdUpper));
		if (category == null) return false;
		return switch (category) {
			case ARMOR -> config.forceSalvageArmor;
			case WAND -> config.forceSalvageWands;
			case EQUIPMENT -> config.forceSalvageEquipment;
		};
	}

	/**
	 * A priced Kuudra key. {@code known} is false when a required material has no price yet.
	 * Callers must not store that case as a real cost of zero.
	 */
	public record KuudraKeyCostQuote(boolean known, long coins) {
		public static KuudraKeyCostQuote unknown() {
			return new KuudraKeyCostQuote(false, 0L);
		}

		public static KuudraKeyCostQuote known(long coins) {
			return new KuudraKeyCostQuote(true, Math.max(0L, coins));
		}
	}

	public static long resolveKuudraKeyCost(DungeonFloor floor, DrtConfig config) {
		KuudraKeyCostQuote quote = quoteKuudraKeyCost(floor, config);
		return quote.known() ? quote.coins() : 0L;
	}

	public static KuudraKeyCostQuote quoteKuudraKeyCost(DungeonFloor floor, DrtConfig config) {
		KuudraKeyShopCost shopCost = kuudraKeyShopCost(floor, config);
		KuudraKeyRecipe recipe = kuudraKeyRecipe(floor);
		if (shopCost != null && shopCost.coins > 0L) {
			long total = shopCost.coins;
			boolean shopListedMaterials = shopCost.materials != null && !shopCost.materials.isEmpty();
			if (!shopListedMaterials) {
				if (recipe == null) return KuudraKeyCostQuote.unknown();
				Long factionCost = pricedMaterial(factionMaterialId(config), recipe.factionMaterialCount, config);
				Long starCost = pricedMaterial(ITEM_CORRUPTED_NETHER_STAR, 2, config);
				if (factionCost == null || starCost == null) return KuudraKeyCostQuote.unknown();
				return KuudraKeyCostQuote.known(total + factionCost + starCost);
			}
			for (Map.Entry<String, Integer> material : shopCost.materials.entrySet()) {
				Long part = pricedMaterial(material.getKey(), material.getValue() == null ? 0 : material.getValue(), config);
				if (part == null) return KuudraKeyCostQuote.unknown();
				total += part;
			}
			return KuudraKeyCostQuote.known(total);
		}

		if (recipe == null) return KuudraKeyCostQuote.unknown();
		Long factionCost = pricedMaterial(factionMaterialId(config), recipe.factionMaterialCount, config);
		Long starCost = pricedMaterial(ITEM_CORRUPTED_NETHER_STAR, 2, config);
		if (factionCost == null || starCost == null) return KuudraKeyCostQuote.unknown();
		long coinCost = applyKuudraKeyCoinDiscount(recipe.coinCost, reputationForKeyDiscount(config));
		return KuudraKeyCostQuote.known(coinCost + factionCost + starCost);
	}

	/**
	 * The paid Kuudra chest costs the key. There is no second coin fee to add on top.
	 * An unpriced key stays unknown instead of being stored as a free open.
	 */
	public static void applyPaidKuudraKey(ChestCostBreakdown breakdown, DungeonFloor floor, DrtConfig config) {
		if (breakdown == null) return;
		breakdown.usedKuudraKey = true;
		KuudraKeyCostQuote quote = quoteKuudraKeyCost(floor, config);
		if (!quote.known()) return;
		breakdown.kuudraKeyCostCoins = quote.coins();
		breakdown.baseChestCostKnown = true;
	}

	private static String factionMaterialId(DrtConfig config) {
		return normalizedKuudraFaction(config).equals("BARBARIAN")
			? ITEM_ENCHANTED_RED_SAND
			: ITEM_ENCHANTED_MYCELIUM;
	}

	/** Null when the unit price is unavailable, so a missing price is not added as zero. */
	private static Long pricedMaterial(String itemId, int quantity, DrtConfig config) {
		if (quantity <= 0 || itemId == null || itemId.isBlank()) return null;
		long unitCost = resolveModifierCost(itemId, config);
		if (unitCost <= 0L) return null;
		return unitCost * (long) quantity;
	}

	public static KuudraKeyShopCost kuudraKeyShopCost(DungeonFloor floor, DrtConfig config) {
		if (floor == null || !floor.isKuudra() || config == null || config.kuudraKeyShopCosts == null) return null;
		KuudraKeyShopCost cost = config.kuudraKeyShopCosts.get(floor.name());
		return cost != null && cost.coins > 0L ? cost : null;
	}

	/** Shop lore names to price ids. The shop calls the star just "Nether Star". */
	public static String kuudraKeyMaterialItemId(String displayName) {
		if (displayName == null) return "";
		String upper = displayName.trim().toUpperCase(Locale.ROOT);
		if (upper.isEmpty()) return "";
		if (upper.equals("NETHER STAR") || upper.equals("CORRUPTED NETHER STAR")) return ITEM_CORRUPTED_NETHER_STAR;
		return upper.replaceAll("[^A-Z0-9]+", "_").replaceAll("^_+|_+$", "");
	}

	/**
	 * Fallback estimate when the shop has not been seen yet. Emissary coin discount by
	 * faction reputation: 0 → 0%, 1k → 5%, 3k → 10%, 7k → 15%, 12k → 20%.
	 */
	public static int kuudraKeyCoinDiscountPercent(int reputation) {
		if (reputation >= 12_000) return 20;
		if (reputation >= 7_000) return 15;
		if (reputation >= 3_000) return 10;
		if (reputation >= 1_000) return 5;
		return 0;
	}

	public static long applyKuudraKeyCoinDiscount(long coinCost, int reputation) {
		if (coinCost <= 0L) return 0L;
		int percent = kuudraKeyCoinDiscountPercent(reputation);
		if (percent <= 0) return coinCost;
		return coinCost * (100L - percent) / 100L;
	}

	private static int reputationForKeyDiscount(DrtConfig config) {
		if (config == null || !config.kuudraReputationKnown) return 0;
		return Math.max(0, config.kuudraReputation);
	}

	public static long resolveModifierCost(String itemId, DrtConfig config) {
		if (itemId == null || itemId.isBlank()) return 0L;
		String resolvedItemId = normalizePriceItemId(itemId.trim().toUpperCase(Locale.ROOT));

		PriceCache.BazaarPriceData bazaarData = PriceCache.getBazaar(resolvedItemId);
		if (bazaarData != null) {
			Double selected = selectedBazaarCostPrice(bazaarData, config);
			if (selected != null && selected > 0.0D) return roundPositive(selected);
		}
		PriceCache.PriceLookup lookup = PriceCache.get(resolvedItemId);
		if (lookup != null) return roundPositive(lookup.price());

		PriceCache.AuctionPriceData auctionData = PriceCache.getAuctionHouse(resolvedItemId);
		if (auctionData != null) {
			Double lbin = auctionData.lbin();
			if (lbin != null && lbin > 0.0D) return roundPositive(lbin);
			Double p3d = auctionData.p3d();
			if (p3d != null && p3d > 0.0D) return roundPositive(p3d);
			Double p7d = auctionData.p7d();
			if (p7d != null && p7d > 0.0D) return roundPositive(p7d);
		}
		return 0L;
	}

	private static Double selectedBazaarPrice(PriceCache.BazaarPriceData data, DrtConfig config) {
		String mode = normalizedBazaarMode(config);
		// Vyriv returns explicit zeros as valid sell quotes (0 coins). Missing fields stay null.
		if (mode.equals("ORDER")) {
			return firstPresent(data.sellOffer(), data.instantSell());
		}
		return firstPresent(data.instantSell(), data.sellOffer());
	}

	private static Double selectedBazaarCostPrice(PriceCache.BazaarPriceData data, DrtConfig config) {
		String mode = normalizedBazaarMode(config);
		if (mode.equals("ORDER")) {
			return firstPresent(data.buyOrder(), data.instantBuy());
		}
		return firstPresent(data.instantBuy(), data.buyOrder());
	}

	private static String normalizedBazaarMode(DrtConfig config) {
		String mode = config == null || config.bazaarPriceMode == null ? "INSTANT" : config.bazaarPriceMode.trim().toUpperCase(Locale.ROOT);
		return switch (mode) {
			case "ORDER", "SELL_OFFER", "BUY_ORDER" -> "ORDER";
			default -> "INSTANT";
		};
	}

	private static String normalizedKuudraFaction(DrtConfig config) {
		String faction = config == null || config.kuudraFaction == null ? "MAGE" : config.kuudraFaction.trim().toUpperCase(Locale.ROOT);
		return faction.equals("BARBARIAN") ? "BARBARIAN" : "MAGE";
	}

	private static Long resolveForcedKuudraSalvageValue(String itemId, String rawName, DrtConfig config) {
		if (config == null || itemId == null || itemId.isBlank()) return null;
		KuudraSalvageCategory category = kuudraSalvageCategory(itemId);
		if (category == null) return null;
		boolean enabled = switch (category) {
			case ARMOR -> config.forceSalvageArmor;
			case WAND -> config.forceSalvageWands;
			case EQUIPMENT -> config.forceSalvageEquipment;
		};
		if (!enabled) return null;
		if (!config.essenceCountsTowardProfit) return 0L;
		long crimsonEssencePrice = resolveSellValue(ITEM_CRIMSON_ESSENCE, config);
		if (crimsonEssencePrice <= 0L) return 0L;
		int essence = adjustedSalvageEssence(baseForcedSalvageEssence(category, rawName), config);
		return crimsonEssencePrice * Math.max(0, essence);
	}

	private static boolean isEssenceItem(String rawUpper, String itemIdUpper) {
		if (itemIdUpper != null && itemIdUpper.startsWith("ESSENCE_")) return true;
		return rawUpper != null && rawUpper.contains("ESSENCE");
	}

	private static boolean isCrimsonEssenceEntry(DungeonLootEntry entry) {
		if (entry == null) return false;
		String rawUpper = entry.rawName == null ? "" : entry.rawName.trim().toUpperCase(Locale.ROOT);
		String itemIdUpper = entry.itemId == null ? "" : entry.itemId.trim().toUpperCase(Locale.ROOT);
		if (itemIdUpper.equals(ITEM_CRIMSON_ESSENCE) || itemIdUpper.equals("CRIMSON_ESSENCE")) {
			return true;
		}
		String compact = rawUpper.replaceAll("[^A-Z0-9]+", " ").trim().replaceAll("\\s+", " ");
		return compact.contains("CRIMSON ESSENCE") || compact.equals("ESSENCE CRIMSON");
	}

	private static String inferPriceItemIdFromRawName(String rawUpper) {
		if (rawUpper == null || rawUpper.isBlank()) return "";
		String compact = rawUpper.replaceAll("[^A-Z0-9]+", " ").trim().replaceAll("\\s+", " ");
		return switch (compact) {
			case "SHADOW ASSASSIN CLOAK" -> "SHADOW_ASSASSIN_CLOAK";
			case "WITHER SPECTRE SHARD" -> "SHARD_WITHER_SPECTER";
			case "NECROMANCER BROOCH", "NECROMANCER S BROOCH", "NECROMANCERS BROOCH" -> "NECROMANCER_BROOCH";
			case "CRIMSON ESSENCE", "ESSENCE CRIMSON" -> ITEM_CRIMSON_ESSENCE;
			case "WITHER ESSENCE", "ESSENCE WITHER" -> "ESSENCE_WITHER";
			case "UNDEAD ESSENCE", "ESSENCE UNDEAD" -> "ESSENCE_UNDEAD";
			case "SPIDER ESSENCE", "ESSENCE SPIDER" -> "ESSENCE_SPIDER";
			case "DRAGON ESSENCE", "ESSENCE DRAGON" -> "ESSENCE_DRAGON";
			case "ICE ESSENCE", "ESSENCE ICE" -> "ESSENCE_ICE";
			case "DIAMOND ESSENCE", "ESSENCE DIAMOND" -> "ESSENCE_DIAMOND";
			case "GOLD ESSENCE", "ESSENCE GOLD" -> "ESSENCE_GOLD";
			default -> compact.endsWith(" SHARD")
				? "SHARD_" + compact.substring(0, compact.length() - " SHARD".length()).replace(' ', '_')
				: "";
		};
	}

	/** Applies the player-specific bonuses that Hypixel adds after the chest's base essence line. */
	public static int adjustedCrimsonEssenceAmount(int baseAmount, DrtConfig config) {
		return applyEssenceBonus(baseAmount, crimsonEssenceBonusPercent(config, false));
	}

	/** Smallest chest line whose rounded, bonus-adjusted amount reaches the requested reward floor. */
	public static int minimumCrimsonEssenceBaseAmount(int adjustedMinimum, DrtConfig config) {
		if (adjustedMinimum <= 0) return 0;
		int low = 1;
		int high = adjustedMinimum;
		while (low < high) {
			int mid = low + (high - low) / 2;
			if (adjustedCrimsonEssenceAmount(mid, config) >= adjustedMinimum) {
				high = mid;
			} else {
				low = mid + 1;
			}
		}
		return low;
	}

	private static double crimsonEssenceBonusPercent(DrtConfig config, boolean includeCoolForged) {
		if (config == null) return 0.0D;
		double bonus = 0.0D;
		if (config.kuudraPetEnabled) {
			bonus += kuudraPetCrimsonBonusPercent(config.kuudraPetRarity, config.kuudraPetLevel);
		}
		bonus += Math.max(0, Math.min(100, config.crimsonEssenceBonusPercent));
		if (includeCoolForged && config.coolForgedEnabled) {
			bonus += Math.max(1, Math.min(5, config.coolForgedLevel)) * 4;
		}
		return bonus;
	}

	private static int applyEssenceBonus(int baseAmount, double bonusPercent) {
		if (baseAmount <= 0) return 0;
		if (bonusPercent <= 0.0D) return baseAmount;
		return (int) Math.round(baseAmount * (100.0D + bonusPercent) / 100.0D);
	}

	private static int baseForcedSalvageEssence(KuudraSalvageCategory category, String rawName) {
		if (category == KuudraSalvageCategory.ARMOR) {
			return kuudraArmorBaseSalvageEssence(rawName);
		}
		return KUUDRA_TABLE_DROP_CRIMSON_ESSENCE;
	}

	private static int kuudraArmorBaseSalvageEssence(String rawName) {
		int stars = countKuudraStars(rawName);
		int starEssenceCost = 0;
		for (int star = 1; star <= stars; star++) {
			starEssenceCost += 20 + star * 5;
		}
		return KUUDRA_ARMOR_BASE_CRIMSON_ESSENCE
			+ (int) Math.floor(starEssenceCost * KUUDRA_ARMOR_STAR_REFUND_MULTIPLIER);
	}

	private static int countKuudraStars(String rawName) {
		if (rawName == null || rawName.isBlank()) return 0;
		int stars = 0;
		for (int i = 0; i < rawName.length(); i++) {
			if (rawName.charAt(i) == '✪') stars++;
		}
		return stars;
	}

	private static int adjustedSalvageEssence(int baseEssence, DrtConfig config) {
		return applyEssenceBonus(baseEssence, crimsonEssenceBonusPercent(config, true));
	}

	private static double kuudraPetCrimsonBonusPercent(String rarity, int level) {
		int cappedLevel = Math.max(1, Math.min(100, level));
		String normalized = rarity == null ? "LEGENDARY" : rarity.trim().toUpperCase(Locale.ROOT);
		double maxPercent = switch (normalized) {
			case "RARE" -> 15.0D;
			case "EPIC", "LEGENDARY" -> 20.0D;
			default -> 0.0D;
		};
		return maxPercent * cappedLevel / 100.0D;
	}

	private static long resolveSellValue(String itemId, DrtConfig config) {
		double exact = resolveSellValueExact(itemId, config);
		return Double.isFinite(exact) ? Math.round(exact) : 0L;
	}

	private static double resolveSellValueExact(String itemId, DrtConfig config) {
		if (itemId == null || itemId.isBlank()) return Double.NaN;
		PriceCache.BazaarPriceData bazaarData = PriceCache.getBazaar(itemId);
		if (bazaarData != null) {
			Double selected = selectedBazaarPrice(bazaarData, config);
			if (selected != null) return selected;
		}
		PriceCache.PriceLookup lookup = PriceCache.get(itemId);
		if (lookup != null) return lookup.price();
		return Double.NaN;
	}

	private static KuudraKeyRecipe kuudraKeyRecipe(DungeonFloor floor) {
		if (floor == null) return null;
		return switch (floor) {
			case K1 -> new KuudraKeyRecipe(200_000L, 2);
			case K2 -> new KuudraKeyRecipe(400_000L, 6);
			case K3 -> new KuudraKeyRecipe(750_000L, 20);
			case K4 -> new KuudraKeyRecipe(1_500_000L, 60);
			case K5 -> new KuudraKeyRecipe(3_000_000L, 120);
			default -> null;
		};
	}

	private static KuudraSalvageCategory kuudraSalvageCategory(String itemId) {
		if (itemId == null || itemId.isBlank()) return null;
		String id = itemId.toUpperCase(Locale.ROOT);
		if (isKuudraArmor(id)) return KuudraSalvageCategory.ARMOR;
		if (id.equals("AURORA_STAFF")
			|| id.equals("RUNIC_STAFF")
			|| id.equals("HOLLOW_WAND")
			|| id.equals("KUUDRA_MANDIBLE")
			|| id.equals("TORMENTOR")) {
			return KuudraSalvageCategory.WAND;
		}
		if (id.equals("MOLTEN_BELT")
			|| id.equals("MOLTEN_BRACELET")
			|| id.equals("MOLTEN_CLOAK")
			|| id.equals("MOLTEN_NECKLACE")) {
			return KuudraSalvageCategory.EQUIPMENT;
		}
		return null;
	}

	private static boolean isKuudraArmor(String itemId) {
		String id = itemId;
		for (String tier : List.of("HOT_", "BURNING_", "FIERY_", "INFERNAL_")) {
			if (id.startsWith(tier)) {
				id = id.substring(tier.length());
				break;
			}
		}
		boolean set = id.startsWith("CRIMSON_")
			|| id.startsWith("TERROR_")
			|| id.startsWith("AURORA_")
			|| id.startsWith("FERVOR_")
			|| id.startsWith("HOLLOW_");
		if (!set) return false;
		return id.endsWith("_HELMET")
			|| id.endsWith("_CHESTPLATE")
			|| id.endsWith("_LEGGINGS")
			|| id.endsWith("_BOOTS");
	}

	private static Double firstPresent(Double... values) {
		for (Double value : values) {
			if (value != null && Double.isFinite(value) && value >= 0.0D) return value;
		}
		return null;
	}

	private static String normalizePriceItemId(String itemId) {
		return switch (itemId) {
			case "SHARD_WITHER_SPECTRE" -> "SHARD_WITHER_SPECTER";
			case "NECROMANCERS_BROOCH", "NECROMANCER_S_BROOCH" -> "NECROMANCER_BROOCH";
			case "NECRONS_HANDLE" -> "NECRON_HANDLE";
			case "SCARFS_STUDIES" -> "SCARF_STUDIES";
			case "SPIRIT_STONE" -> "SPIRIT_DECOY";
			case "SPIRIT_BOOTS" -> "THORNS_BOOTS";
			case "SPIRIT_PET" -> "PET_SPIRIT";
			case "WARPED_STONE" -> "AOTE_STONE";
			case "ADAPTIVE_BLADE" -> "STONE_BLADE";
			case "WITHER_CLOAK_SWORD" -> "WITHER_CLOAK";
			case "AURORA_STAFF" -> "RUNIC_STAFF";
			case "CRIMSON_ESSENCE" -> ITEM_CRIMSON_ESSENCE;
			case "WITHER_ESSENCE" -> "ESSENCE_WITHER";
			case "UNDEAD_ESSENCE" -> "ESSENCE_UNDEAD";
			default -> renamedKuudraBookId(itemId);
		};
	}

	// Hypixel renamed the Kuudra mana books to "Vitality", but prices are still keyed by the old ids.
	private static final String[][] KUUDRA_BOOK_RENAMES = {
		{"ENCHANTMENT_HARDENED_VITALITY_", "ENCHANTMENT_HARDENED_MANA_"},
		{"ENCHANTMENT_STRONG_VITALITY_", "ENCHANTMENT_STRONG_MANA_"},
		{"ENCHANTMENT_VAMPIRIC_VITALITY_", "ENCHANTMENT_MANA_VAMPIRE_"},
		{"ENCHANTMENT_VIVACIOUS_VITALITY_", "ENCHANTMENT_FEROCIOUS_MANA_"}
	};

	private static String renamedKuudraBookId(String itemId) {
		if (!itemId.startsWith("ENCHANTMENT_")) return itemId;
		// Wisdom is an ultimate enchant even though its display name omits the word
		// "Ultimate". Older records therefore contain the non-existent regular id.
		if (itemId.startsWith("ENCHANTMENT_WISDOM_")) {
			return "ENCHANTMENT_ULTIMATE_WISDOM_" + itemId.substring("ENCHANTMENT_WISDOM_".length());
		}
		for (String[] rename : KUUDRA_BOOK_RENAMES) {
			if (itemId.startsWith(rename[0])) return rename[1] + itemId.substring(rename[0].length());
		}
		return itemId;
	}

	private static long roundPositive(double price) {
		return price > 0.0D ? Math.max(1L, Math.round(price)) : 0L;
	}

	private static boolean present(Double value) {
		return value != null && Double.isFinite(value) && value >= 0.0D;
	}

	private record KuudraKeyRecipe(long coinCost, int factionMaterialCount) {
	}

	public record ValueSummary(long knownValueCoins, int pricedEntries, int unpricedEntries, double exactKnownValueCoins) {
		public ValueSummary(long knownValueCoins, int pricedEntries, int unpricedEntries) {
			this(knownValueCoins, pricedEntries, unpricedEntries, knownValueCoins);
		}

		public boolean complete() {
			return unpricedEntries == 0;
		}
	}

	private enum KuudraSalvageCategory {
		ARMOR,
		WAND,
		EQUIPMENT
	}
}
