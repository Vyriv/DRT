package dev.vy.drt.price;

import dev.vy.drt.config.DrtConfig;
import dev.vy.drt.config.DungeonFloor;
import dev.vy.drt.config.DungeonLootEntry;
import java.util.List;
import java.util.Locale;

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
		if (rawUpper.contains("WITHER ESSENCE") || itemIdUpper.equals("ESSENCE_WITHER")) {
			return Math.max(0, config.witherEssenceValuePer);
		}
		if (rawUpper.contains("UNDEAD ESSENCE") || itemIdUpper.equals("ESSENCE_UNDEAD")) {
			return Math.max(0, config.undeadEssenceValuePer);
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
		KuudraKeyRecipe recipe = kuudraKeyRecipe(floor);
		if (recipe == null) return 0L;
		String factionMaterial = normalizedKuudraFaction(config).equals("BARBARIAN")
			? ITEM_ENCHANTED_RED_SAND
			: ITEM_ENCHANTED_MYCELIUM;
		long materialCost = resolveMaterialCost(factionMaterial, recipe.factionMaterialCount, config);
		long starCost = resolveMaterialCost(ITEM_CORRUPTED_NETHER_STAR, 2, config);
		return Math.max(0L, recipe.coinCost) + materialCost + starCost;
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
		long crimsonEssencePrice = resolveSellValue(ITEM_CRIMSON_ESSENCE, config);
		if (crimsonEssencePrice <= 0L) return 0L;
		int essence = adjustedSalvageEssence(baseForcedSalvageEssence(category, rawName), config);
		return crimsonEssencePrice * Math.max(0, essence);
	}

	private static boolean isCrimsonEssenceEntry(DungeonLootEntry entry) {
		if (entry == null) return false;
		String rawUpper = entry.rawName == null ? "" : entry.rawName.trim().toUpperCase(Locale.ROOT);
		String itemIdUpper = entry.itemId == null ? "" : entry.itemId.trim().toUpperCase(Locale.ROOT);
		return rawUpper.contains("CRIMSON ESSENCE") || itemIdUpper.equals(ITEM_CRIMSON_ESSENCE);
	}

	private static int adjustedCrimsonEssenceAmount(int baseAmount, DrtConfig config) {
		if (config == null || !config.kuudraPetEnabled) return Math.max(0, baseAmount);
		double bonusPercent = kuudraPetCrimsonBonusPercent(config.kuudraPetRarity, config.kuudraPetLevel);
		return (int) Math.round(Math.max(0, baseAmount) * (100.0D + bonusPercent) / 100.0D);
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
		double bonusPercent = 0.0D;
		if (config != null && config.kuudraPetEnabled) {
			bonusPercent += kuudraPetCrimsonBonusPercent(config.kuudraPetRarity, config.kuudraPetLevel);
		}
		if (config != null && config.coolForgedEnabled) {
			bonusPercent += Math.max(1, Math.min(5, config.coolForgedLevel)) * 4;
		}
		return (int) Math.round(baseEssence * (100.0D + bonusPercent) / 100.0D);
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
			default -> itemId;
		};
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
