package dev.vy.drt.price;

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
		if (entries == null || entries.isEmpty()) return 0L;
		long total = 0L;
		for (DungeonLootEntry entry : entries) {
			if (entry == null) continue;
			total += resolveTotalPrice(entry, config);
		}
		return total;
	}

	public static long resolveTotalPrice(DungeonLootEntry entry, DrtConfig config) {
		if (entry == null) return 0L;
		long unitPrice = resolveUnitPrice(entry, config);
		int quantity = Math.max(1, entry.quantity);
		if (isCrimsonEssenceEntry(entry)) {
			quantity = adjustedCrimsonEssenceAmount(quantity, config);
		}
		return unitPrice * quantity;
	}

	public static long resolveUnitPrice(DungeonLootEntry entry, DrtConfig config) {
		if (entry == null) return 0L;

		String rawUpper = entry.rawName == null ? "" : entry.rawName.trim().toUpperCase(Locale.ROOT);
		String itemIdUpper = entry.itemId == null ? "" : entry.itemId.trim().toUpperCase(Locale.ROOT);
		if (config != null && !config.essenceCountsTowardProfit && isEssenceItem(rawUpper, itemIdUpper)) {
			return 0L;
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
			long crimsonPrice = resolveSellValue(ITEM_CRIMSON_ESSENCE, config);
			if (crimsonPrice > 0L) return crimsonPrice;
		}

		if (itemIdUpper.isBlank()) {
			itemIdUpper = inferPriceItemIdFromRawName(rawUpper);
		}
		if (!itemIdUpper.isBlank()) {
			String resolvedItemId = normalizePriceItemId(itemIdUpper);
			Long forcedSalvageValue = resolveForcedKuudraSalvageValue(resolvedItemId, rawUpper, config);
			if (forcedSalvageValue != null) return forcedSalvageValue;

			PriceCache.AuctionPriceData auctionData = PriceCache.getAuctionHouse(resolvedItemId);
			if (auctionData != null) {
				Double p3d = auctionData.p3d();
				if (p3d != null && p3d > 0.0D) return roundPositive(p3d);
				Double lbin = auctionData.lbin();
				if (lbin != null && lbin > 0.0D) return roundPositive(lbin);
				Double p7d = auctionData.p7d();
				if (p7d != null && p7d > 0.0D) return roundPositive(p7d);
			}
			PriceCache.BazaarPriceData bazaarData = PriceCache.getBazaar(resolvedItemId);
			if (bazaarData != null) {
				Double selected = selectedBazaarPrice(bazaarData, config);
				if (selected != null && selected > 0.0D) return roundPositive(selected);
			}
			PriceCache.PriceLookup lookup = PriceCache.get(resolvedItemId);
			if (lookup != null) return roundPositive(lookup.price());
		}
		return 0L;
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

	public static long resolveKuudraKeyCost(DungeonFloor floor, DrtConfig config) {
		KuudraKeyShopCost shopCost = kuudraKeyShopCost(floor, config);
		if (shopCost != null) {
			long total = Math.max(0L, shopCost.coins);
			if (shopCost.materials != null) {
				for (Map.Entry<String, Integer> material : shopCost.materials.entrySet()) {
					total += resolveMaterialCost(material.getKey(), material.getValue() == null ? 0 : material.getValue(), config);
				}
			}
			return total;
		}

		KuudraKeyRecipe recipe = kuudraKeyRecipe(floor);
		if (recipe == null) return 0L;
		String factionMaterial = normalizedKuudraFaction(config).equals("BARBARIAN")
			? ITEM_ENCHANTED_RED_SAND
			: ITEM_ENCHANTED_MYCELIUM;
		long materialCost = resolveMaterialCost(factionMaterial, recipe.factionMaterialCount, config);
		long starCost = resolveMaterialCost(ITEM_CORRUPTED_NETHER_STAR, 2, config);
		long coinCost = applyKuudraKeyCoinDiscount(recipe.coinCost, reputationForKeyDiscount(config));
		return Math.max(0L, coinCost) + materialCost + starCost;
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
		if (mode.equals("ORDER")) {
			return firstPositive(data.sellOffer(), data.instantSell(), data.instantBuy(), data.buyOrder());
		}
		return firstPositive(data.instantSell(), data.sellOffer(), data.instantBuy(), data.buyOrder());
	}

	private static Double selectedBazaarCostPrice(PriceCache.BazaarPriceData data, DrtConfig config) {
		String mode = normalizedBazaarMode(config);
		if (mode.equals("ORDER")) {
			return firstPositive(data.buyOrder(), data.instantBuy(), data.sellOffer(), data.instantSell());
		}
		return firstPositive(data.instantBuy(), data.buyOrder(), data.sellOffer(), data.instantSell());
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
			case "CRIMSON ESSENCE", "ESSENCE CRIMSON" -> ITEM_CRIMSON_ESSENCE;
			case "WITHER ESSENCE", "ESSENCE WITHER" -> "ESSENCE_WITHER";
			case "UNDEAD ESSENCE", "ESSENCE UNDEAD" -> "ESSENCE_UNDEAD";
			case "SPIDER ESSENCE", "ESSENCE SPIDER" -> "ESSENCE_SPIDER";
			case "DRAGON ESSENCE", "ESSENCE DRAGON" -> "ESSENCE_DRAGON";
			case "ICE ESSENCE", "ESSENCE ICE" -> "ESSENCE_ICE";
			case "DIAMOND ESSENCE", "ESSENCE DIAMOND" -> "ESSENCE_DIAMOND";
			case "GOLD ESSENCE", "ESSENCE GOLD" -> "ESSENCE_GOLD";
			default -> "";
		};
	}

	private static int adjustedCrimsonEssenceAmount(int baseAmount, DrtConfig config) {
		return applyEssenceBonus(baseAmount, crimsonEssenceBonusPercent(config, false));
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
			case "COMMON" -> 10.0D;
			case "UNCOMMON", "RARE" -> 15.0D;
			case "EPIC", "LEGENDARY" -> 20.0D;
			default -> 20.0D;
		};
		return maxPercent * cappedLevel / 100.0D;
	}

	private static long resolveMaterialCost(String itemId, int quantity, DrtConfig config) {
		if (quantity <= 0 || itemId == null || itemId.isBlank()) return 0L;
		long unitCost = resolveModifierCost(itemId, config);
		return unitCost * (long) quantity;
	}

	private static long resolveSellValue(String itemId, DrtConfig config) {
		if (itemId == null || itemId.isBlank()) return 0L;
		PriceCache.BazaarPriceData bazaarData = PriceCache.getBazaar(itemId);
		if (bazaarData != null) {
			Double selected = selectedBazaarPrice(bazaarData, config);
			if (selected != null && selected > 0.0D) return roundPositive(selected);
		}
		PriceCache.PriceLookup lookup = PriceCache.get(itemId);
		if (lookup != null) return roundPositive(lookup.price());
		return 0L;
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

	private static Double firstPositive(Double... values) {
		for (Double value : values) {
			if (value != null && value > 0.0D) return value;
		}
		return null;
	}

	private static String normalizePriceItemId(String itemId) {
		return switch (itemId) {
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
		for (String[] rename : KUUDRA_BOOK_RENAMES) {
			if (itemId.startsWith(rename[0])) return rename[1] + itemId.substring(rename[0].length());
		}
		return itemId;
	}

	private static long roundPositive(double price) {
		return price > 0.0D ? Math.max(1L, Math.round(price)) : 0L;
	}

	private record KuudraKeyRecipe(long coinCost, int factionMaterialCount) {
	}

	private enum KuudraSalvageCategory {
		ARMOR,
		WAND,
		EQUIPMENT
	}
}
