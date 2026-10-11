package dev.vy.drt.luck.data;

import java.util.Locale;
import java.util.Set;

/**
 * One line of a reward chest loot table.
 *
 * @param key canonical id shared with DRT loot records, for example {@code NECRON_HANDLE},
 *            {@code ENCHANTMENT_ULTIMATE_WISDOM_2} or {@code PET_SPIRIT_LEGENDARY}
 */
public record LootEntry(
	String key,
	Kind kind,
	String sourceId,
	int level,
	String tier,
	String displayName,
	int quality,
	int weight,
	long addedChestCost,
	Role role
) {
	public enum Kind { ITEM, PET, ENCHANTMENT, ESSENCE }

	public enum Role {
		/** Weighted pick, at most once per chest. */
		WEIGHTED,
		/** Weighted essence that can be picked repeatedly while quality remains. */
		REPEATABLE_ESSENCE,
		/** Zero-weight, one-quality essence that absorbs leftover quality. */
		FILLER_ESSENCE,
		/** Zero weight and zero quality: always added after the rolls. */
		GUARANTEED
	}

	// Hypixel ids for these ultimate enchantments carry an ULTIMATE_ prefix that the source data omits.
	private static final Set<String> UNPREFIXED_ULTIMATE_ENCHANTMENTS = Set.of(
		"BANK", "CHIMERA", "COMBO", "DUPLEX", "FATAL_TEMPO", "FLASH", "HABANERO_TACTICS", "INFERNO",
		"LAST_STAND", "LEGION", "NO_PAIN_NO_GAIN", "ONE_FOR_ALL", "REFRIGERATE", "REND", "SOUL_EATER",
		"SWARM", "THE_ONE", "WISDOM"
	);

	public boolean isRolled() {
		return role == Role.WEIGHTED || role == Role.REPEATABLE_ESSENCE;
	}

	public static String itemKey(String itemId) {
		return itemId.toUpperCase(Locale.ROOT);
	}

	public static String petKey(String pet, String tier) {
		return "PET_" + pet.toUpperCase(Locale.ROOT) + "_" + tier.toUpperCase(Locale.ROOT);
	}

	public static String enchantmentKey(String enchantment, int level) {
		String id = enchantment.toUpperCase(Locale.ROOT);
		if (UNPREFIXED_ULTIMATE_ENCHANTMENTS.contains(id)) id = "ULTIMATE_" + id;
		return "ENCHANTMENT_" + id + "_" + level;
	}

	public static String essenceKey(String type, int amount) {
		return "ESSENCE_" + type.toUpperCase(Locale.ROOT) + "_" + amount;
	}
}
