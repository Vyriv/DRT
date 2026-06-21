package dev.vy.drt.price;

import dev.vy.drt.config.DrtConfig;
import dev.vy.drt.config.DungeonLootEntry;
import java.util.List;
import java.util.Locale;

public final class DungeonProfitPricing {
	private DungeonProfitPricing() {
	}

	public static long calculateLootValue(List<DungeonLootEntry> entries, DrtConfig config) {
		if (entries == null || entries.isEmpty()) return 0L;
		long total = 0L;
		for (DungeonLootEntry entry : entries) {
			if (entry == null) continue;
			long unitPrice = resolveUnitPrice(entry, config);
			total += unitPrice * Math.max(1, entry.quantity);
		}
		return total;
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

			PriceCache.AuctionPriceData auctionData = PriceCache.getAuctionHouse(resolvedItemId);
			if (auctionData != null) {
				Double p3d = auctionData.p3d();
				if (p3d != null && p3d > 0.0D) return roundPositive(p3d);
				Double lbin = auctionData.lbin();
				if (lbin != null && lbin > 0.0D) return roundPositive(lbin);
				Double p7d = auctionData.p7d();
				if (p7d != null && p7d > 0.0D) return roundPositive(p7d);
			}
			PriceCache.PriceLookup lookup = PriceCache.get(resolvedItemId);
			if (lookup != null) return roundPositive(lookup.price());
		}
		return 0L;
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
}
