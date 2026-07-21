package dev.vy.drt.price;

import dev.vy.drt.config.DungeonFloor;
import java.util.Locale;

/**
 * Hardcoded post-run guaranteed loot expectations for soft detection guards.
 * Dungeon essence tables are wiki-confirmed for normal mode F1–F7.
 * Kuudra Free Chest Crimson and Kraken shard counts are June 2025 update-confirmed.
 * Kuudra Paid Chest Crimson/Teeth defaults are community estimates and intentionally
 * exposed as tunable constants (not permanent sacred values).
 */
public final class ExpectedLootTables {
	public enum ChestTier {
		WOOD, GOLD, DIAMOND, EMERALD, OBSIDIAN, BEDROCK,
		FREE, PAID,
		UNKNOWN
	}

	/**
	 * Extra essence above the guaranteed line that filler 1x rolls may add before we warn.
	 * Guaranteed amounts are a floor, not a ceiling.
	 */
	public static final int ESSENCE_FILLER_HEADROOM = 120;

	/** Absolute soft ceiling for any single essence line. */
	public static final int ESSENCE_ABSOLUTE_SOFT_CEILING = 1000;

	// Paid Chest Crimson Essence defaults (community page; rework-warned — tunable).
	public static final int[] KUUDRA_PAID_CRIMSON_DEFAULT = {0, 80, 200, 400, 1000, 2000};
	// Paid Chest Kuudra Teeth defaults (Infernal listed as 3–4; use 3 as baseline).
	public static final int[] KUUDRA_PAID_TEETH_DEFAULT = {0, 1, 1, 2, 2, 3};
	public static final int KUUDRA_PAID_TEETH_INFERNAL_MAX = 4;

	// floor index 1..7, chest: Wood..Bedrock
	// F5 Wood corrected to 10 Undead (not 15).
	private static final int[][] UNDEAD_GUARANTEED = {
		{0, 0, 0, 0, 0, 0, 0},
		{0, 3, 5, 8, 12, 15, 0},      // F1 — no Bedrock
		{0, 5, 8, 12, 15, 20, 0},     // F2
		{0, 5, 10, 15, 20, 25, 0},    // F3
		{0, 10, 15, 20, 25, 30, 0},   // F4
		{0, 10, 15, 20, 25, 35, 45},  // F5
		{0, 10, 15, 25, 35, 45, 55},  // F6
		{0, 10, 20, 30, 40, 55, 70}   // F7
	};

	private static final int[][] WITHER_GUARANTEED = {
		{0, 0, 0, 0, 0, 0, 0},
		{0, 4, 5, 6, 6, 6, 0},        // F1
		{0, 6, 7, 8, 10, 12, 0},      // F2
		{0, 8, 9, 10, 12, 15, 0},     // F3
		{0, 8, 10, 12, 15, 18, 0},    // F4
		{0, 10, 12, 14, 18, 24, 34},  // F5
		{0, 12, 15, 18, 21, 28, 40},  // F6
		{0, 15, 18, 21, 28, 35, 50}   // F7
	};

	// Free Chest Crimson Essence — June 10 2025 update
	private static final int[] KUUDRA_FREE_CRIMSON = {0, 10, 25, 50, 100, 200};
	// Guaranteed Kraken Shards on Paid Chest
	private static final int[] KUUDRA_KRAKEN_SHARDS = {0, 1, 1, 2, 2, 3};

	private ExpectedLootTables() {
	}

	public static ChestTier parseChestTier(String chestTitle) {
		if (chestTitle == null || chestTitle.isBlank()) return ChestTier.UNKNOWN;
		String upper = chestTitle.trim().toUpperCase(Locale.ROOT);
		if (upper.contains("BEDROCK")) return ChestTier.BEDROCK;
		if (upper.contains("OBSIDIAN")) return ChestTier.OBSIDIAN;
		if (upper.contains("EMERALD")) return ChestTier.EMERALD;
		if (upper.contains("DIAMOND")) return ChestTier.DIAMOND;
		if (upper.contains("GOLD")) return ChestTier.GOLD;
		if (upper.contains("WOOD")) return ChestTier.WOOD;
		if (upper.contains("FREE")) return ChestTier.FREE;
		if (upper.contains("PAID")) return ChestTier.PAID;
		return ChestTier.UNKNOWN;
	}

	private static int dungeonFloorIndex(DungeonFloor floor) {
		if (floor == null || floor == DungeonFloor.UNKNOWN) return -1;
		if (!floor.isCatacombs()) return -1;
		int n = floor.floorNumber();
		return n >= 1 && n <= 7 ? n : -1;
	}

	private static int chestIndex(ChestTier tier) {
		return switch (tier) {
			case WOOD -> 1;
			case GOLD -> 2;
			case DIAMOND -> 3;
			case EMERALD -> 4;
			case OBSIDIAN -> 5;
			case BEDROCK -> 6;
			default -> -1;
		};
	}

	public static int guaranteedUndeadEssence(DungeonFloor floor, ChestTier chest) {
		int f = dungeonFloorIndex(floor);
		int c = chestIndex(chest);
		if (f < 0 || c < 0) return -1;
		return UNDEAD_GUARANTEED[f][c];
	}

	public static int guaranteedWitherEssence(DungeonFloor floor, ChestTier chest) {
		int f = dungeonFloorIndex(floor);
		int c = chestIndex(chest);
		if (f < 0 || c < 0) return -1;
		return WITHER_GUARANTEED[f][c];
	}

	public static int freeCrimsonEssence(DungeonFloor floor) {
		if (floor == null || !floor.isKuudra()) return -1;
		int t = floor.floorNumber();
		if (t < 1 || t > 5) return -1;
		return KUUDRA_FREE_CRIMSON[t];
	}

	public static int paidCrimsonEssenceDefault(DungeonFloor floor) {
		if (floor == null || !floor.isKuudra()) return -1;
		int t = floor.floorNumber();
		if (t < 1 || t > 5) return -1;
		return KUUDRA_PAID_CRIMSON_DEFAULT[t];
	}

	public static int paidKuudraTeethMin(DungeonFloor floor) {
		if (floor == null || !floor.isKuudra()) return -1;
		int t = floor.floorNumber();
		if (t < 1 || t > 5) return -1;
		return KUUDRA_PAID_TEETH_DEFAULT[t];
	}

	public static int paidKuudraTeethMax(DungeonFloor floor) {
		if (floor == null || !floor.isKuudra()) return -1;
		int t = floor.floorNumber();
		if (t == 5) return KUUDRA_PAID_TEETH_INFERNAL_MAX;
		return paidKuudraTeethMin(floor);
	}

	public static int guaranteedKrakenShards(DungeonFloor floor) {
		if (floor == null || !floor.isKuudra()) return -1;
		int t = floor.floorNumber();
		if (t < 1 || t > 5) return -1;
		return KUUDRA_KRAKEN_SHARDS[t];
	}

	/** Soft max for a known guaranteed essence line (guaranteed + filler headroom). */
	public static int softMaxForGuaranteed(int guaranteed) {
		if (guaranteed < 0) return ESSENCE_ABSOLUTE_SOFT_CEILING;
		return Math.min(ESSENCE_ABSOLUTE_SOFT_CEILING, guaranteed + ESSENCE_FILLER_HEADROOM);
	}

	public static boolean isUndeadEssence(String rawName, String itemId) {
		String id = itemId == null ? "" : itemId.toUpperCase(Locale.ROOT);
		String name = rawName == null ? "" : rawName.toUpperCase(Locale.ROOT);
		return id.contains("ESSENCE_UNDEAD") || name.contains("UNDEAD ESSENCE");
	}

	public static boolean isWitherEssence(String rawName, String itemId) {
		String id = itemId == null ? "" : itemId.toUpperCase(Locale.ROOT);
		String name = rawName == null ? "" : rawName.toUpperCase(Locale.ROOT);
		return id.contains("ESSENCE_WITHER") || name.contains("WITHER ESSENCE");
	}

	public static boolean isCrimsonEssence(String rawName, String itemId) {
		String id = itemId == null ? "" : itemId.toUpperCase(Locale.ROOT);
		String name = rawName == null ? "" : rawName.toUpperCase(Locale.ROOT);
		return id.contains("ESSENCE_CRIMSON") || name.contains("CRIMSON ESSENCE");
	}

	public static boolean isKuudraTeeth(String rawName, String itemId) {
		String id = itemId == null ? "" : itemId.toUpperCase(Locale.ROOT);
		String name = rawName == null ? "" : rawName.toUpperCase(Locale.ROOT);
		return id.contains("KUUDRA_TEETH") || name.contains("KUUDRA TEETH") || name.equals("KUUDRA TOOTH");
	}

	public static boolean isKrakenShard(String rawName, String itemId) {
		String id = itemId == null ? "" : itemId.toUpperCase(Locale.ROOT);
		String name = rawName == null ? "" : rawName.toUpperCase(Locale.ROOT);
		return id.contains("KRAKEN") && id.contains("SHARD")
			|| name.contains("KRAKEN SHARD");
	}
}
