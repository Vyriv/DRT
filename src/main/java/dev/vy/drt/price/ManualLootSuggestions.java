package dev.vy.drt.price;

import dev.vy.drt.config.DungeonFloor;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class ManualLootSuggestions {
	private static final int SEARCH_LIMIT = 80;
	private static final Pattern ENCHANTED_BOOK_PATTERN = Pattern.compile("^Enchanted Book \\((.+) ([IVX]+)\\)$", Pattern.CASE_INSENSITIVE);
	private static final Set<String> ULTIMATE_ENCHANTS = Set.of(
		"LEGION", "ULTIMATE_WISE", "LAST_STAND", "SOUL_EATER", "SWARM", "COMBO", "REND",
		"NO_PAIN_NO_GAIN", "ONE_FOR_ALL", "CHIMERA", "BANK", "JERRY", "INFERNO",
		"FATAL_TEMPO", "DUPLEX", "FLASH", "HABANERO_TACTICS"
	);

	private static final List<String> COMMON_DUNGEON_CHEST_REWARDS = List.of(
		"Hot Potato Book",
		"Fuming Potato Book",
		"Recombobulator 3000",
		"Necromancer's Brooch",
		"Enchanted Book (Bank I)",
		"Enchanted Book (Bank II)",
		"Enchanted Book (Bank III)",
		"Enchanted Book (Ultimate Jerry I)",
		"Enchanted Book (Ultimate Jerry II)",
		"Enchanted Book (Ultimate Jerry III)",
		"Enchanted Book (Infinite Quiver VI)",
		"Enchanted Book (Infinite Quiver VII)",
		"Enchanted Book (Feather Falling VI)",
		"Enchanted Book (Feather Falling VII)",
		"Enchanted Book (Rejuvenate I)",
		"Enchanted Book (Rejuvenate II)",
		"Enchanted Book (Rejuvenate III)",
		"Enchanted Book (Combo I)",
		"Enchanted Book (Combo II)",
		"Enchanted Book (No Pain No Gain I)",
		"Enchanted Book (No Pain No Gain II)",
		"Enchanted Book (Ultimate Wise I)",
		"Enchanted Book (Ultimate Wise II)",
		"Enchanted Book (Wisdom I)",
		"Enchanted Book (Wisdom II)",
		"Enchanted Book (Last Stand I)",
		"Enchanted Book (Last Stand II)",
		"Enchanted Book (Rend I)",
		"Enchanted Book (Rend II)",
		"Enchanted Book (Overload I)",
		"Enchanted Book (Legion I)",
		"Enchanted Book (Lethality VI)",
		"Enchanted Book (Swarm I)",
		"Enchanted Book (Soul Eater I)",
		"Enchanted Book (One For All I)",
		"Enchanted Book (Thunderlord VII)"
	);

	private static final List<String> F1_DROPS = List.of(
		"Bonzo's Staff", "Bonzo's Mask", "Red Nose", "Balloon Snake"
	);
	private static final List<String> F2_DROPS = List.of(
		"Scarf's Studies", "Red Scarf", "Adaptive Blade", "Adaptive Belt"
	);
	private static final List<String> F3_DROPS = List.of(
		"Adaptive Helmet", "Adaptive Chestplate", "Adaptive Leggings", "Adaptive Boots",
		"Adaptive Blade", "Adaptive Belt", "Suspicious Vial"
	);
	private static final List<String> F4_DROPS = List.of(
		"Spirit Stone", "Spirit Pet", "Spirit Sword", "Spirit Shortbow",
		"Spirit Boots", "Spirit Wing", "Spirit Bone"
	);
	private static final List<String> F5_DROPS = List.of(
		"Dark Orb", "Shadow Assassin Helmet", "Shadow Assassin Chestplate", "Shadow Assassin Leggings",
		"Shadow Assassin Boots", "Shadow Assassin Cloak", "Livid Dagger", "Shadow Fury",
		"Last Breath", "Warped Stone"
	);
	private static final List<String> F6_DROPS = List.of(
		"Giant Tooth", "Sadan's Brooch", "Necromancer Lord Helmet", "Necromancer Lord Chestplate",
		"Necromancer Lord Leggings", "Necromancer Lord Boots", "Necromancer Sword", "Summoning Ring",
		"Fel Skull", "Soulweaver Gloves", "Precursor Eye", "Giant's Sword"
	);
	private static final List<String> F7_DROPS = List.of(
		"Wither Helmet", "Wither Chestplate", "Wither Leggings", "Wither Boots", "Wither Cloak Sword",
		"Wither Blood", "Wither Catalyst", "Precursor Gear", "Necron's Handle", "Shadow Warp",
		"Wither Shield", "Implosion", "Auto Recombobulator", "Storm the Fish", "Maxor the Fish",
		"Goldor the Fish", "Dungeon Disc", "Clown Disc", "Watcher Disc", "Necron Disc"
	);

	private static final List<String> KUUDRA_SHARED = List.of(
		"Aurora Helmet", "Aurora Chestplate", "Aurora Leggings", "Aurora Boots", "Aurora Staff",
		"Crimson Helmet", "Crimson Chestplate", "Crimson Leggings", "Crimson Boots",
		"Fervor Helmet", "Fervor Chestplate", "Fervor Leggings", "Fervor Boots",
		"Hollow Helmet", "Hollow Chestplate", "Hollow Leggings", "Hollow Boots", "Hollow Wand",
		"Terror Helmet", "Terror Chestplate", "Terror Leggings", "Terror Boots",
		"Crimson Essence", "Kuudra Teeth"
	);
	private static final List<String> K1_EXTRAS = List.of(
		"Enchanted Book (Ferocious Mana I)", "Enchanted Book (Hardened Mana I)",
		"Enchanted Book (Mana Vampire I)", "Enchanted Book (Strong Mana I)"
	);
	private static final List<String> K2_EXTRAS = List.of(
		"Molten Necklace", "Molten Cloak", "Molten Belt", "Molten Bracelet",
		"Heavy Pearl", "Mandraa",
		"Enchanted Book (Ferocious Mana II)", "Enchanted Book (Hardened Mana II)",
		"Enchanted Book (Mana Vampire II)", "Enchanted Book (Strong Mana II)"
	);
	private static final List<String> K3_EXTRAS = List.of(
		"Wheel of Fate", "Burning Kuudra Core", "Tentacle Dye", "Enchanted Book (Inferno I)",
		"Enchanted Book (Ferocious Mana III)", "Enchanted Book (Hardened Mana III)",
		"Enchanted Book (Mana Vampire III)", "Enchanted Book (Strong Mana III)"
	);
	private static final List<String> K4_EXTRAS = List.of(
		"Kuudra Mandible", "Ananke Feather",
		"Enchanted Book (Fatal Tempo I)",
		"Enchanted Book (Ferocious Mana IV)", "Enchanted Book (Hardened Mana IV)",
		"Enchanted Book (Mana Vampire IV)", "Enchanted Book (Strong Mana IV)"
	);
	private static final List<String> K5_EXTRAS = List.of(
		"Kraken Shard", "Hellstorm Wand", "Tormentor",
		"Enchanted Book (Ferocious Mana V)", "Enchanted Book (Hardened Mana V)",
		"Enchanted Book (Mana Vampire V)", "Enchanted Book (Strong Mana V)",
		"Bezal Shard", "Magma Slug Shard", "Kada Knight Shard", "Wither Specter Shard",
		"Matcho Shard", "Lava Flame Shard", "Fire Eel Shard", "Flare Shard",
		"Barbarian Duke X Shard", "Hellwisp Shard", "XYZ Shard", "Taurus Shard",
		"Lord Jawbus Shard", "Cinderbat Shard", "Daemon Shard", "Moltenfish Shard",
		"Ananke Shard"
	);

	private static final Map<String, String> DISPLAY_ALIASES = createDisplayAliases();

	private ManualLootSuggestions() {
	}

	public static List<PriceCache.SearchResult> search(DungeonFloor floor, String query) {
		String trimmed = query == null ? "" : query.trim();
		if (trimmed.isEmpty()) {
			return resolveDisplayNames(suggestionsForFloor(floor));
		}
		return PriceCache.searchIndexed(trimmed, SEARCH_LIMIT);
	}

	public static List<String> suggestionsForFloor(DungeonFloor floor) {
		if (floor == null || floor == DungeonFloor.UNKNOWN) return List.of();
		if (floor.isKuudra()) return kuudraSuggestions(floor.floorNumber());
		if (floor.isMasterMode()) return masterSuggestions(floor.floorNumber());
		if (floor.isCatacombs()) return catacombsSuggestions(floor);
		return List.of();
	}

	private static List<String> catacombsSuggestions(DungeonFloor floor) {
		List<String> out = new ArrayList<>();
		out.addAll(catacombsFloorDrops(floor));
		out.addAll(COMMON_DUNGEON_CHEST_REWARDS);
		return dedupe(out);
	}

	private static List<String> masterSuggestions(int tier) {
		List<String> out = new ArrayList<>();
		out.addAll(catacombsFloorDrops(DungeonFloor.valueOf("F" + tier)));
		out.addAll(COMMON_DUNGEON_CHEST_REWARDS);
		out.add("Master Skull - Tier " + tier);
		switch (tier) {
			case 3 -> out.add("First Master Star");
			case 4 -> out.add("Second Master Star");
			case 5 -> {
				out.add("Third Master Star");
				out.add("Livid Dye");
			}
			case 6 -> out.add("Fourth Master Star");
			case 7 -> {
				out.add("Fifth Master Star");
				out.add("Dark Claymore");
				out.add("Necron Dye");
			}
			default -> {}
		}
		return dedupe(out);
	}

	private static List<String> kuudraSuggestions(int tier) {
		List<String> out = new ArrayList<>(KUUDRA_SHARED);
		if (tier >= 1) out.addAll(K1_EXTRAS);
		if (tier >= 2) out.addAll(K2_EXTRAS);
		if (tier >= 3) out.addAll(K3_EXTRAS);
		if (tier >= 4) out.addAll(K4_EXTRAS);
		if (tier >= 5) out.addAll(K5_EXTRAS);
		return dedupe(out);
	}

	private static List<String> catacombsFloorDrops(DungeonFloor floor) {
		return switch (floor) {
			case F1, M1 -> F1_DROPS;
			case F2, M2 -> F2_DROPS;
			case F3, M3 -> F3_DROPS;
			case F4, M4 -> F4_DROPS;
			case F5, M5 -> F5_DROPS;
			case F6, M6 -> F6_DROPS;
			case F7, M7 -> F7_DROPS;
			default -> List.of();
		};
	}

	private static List<String> dedupe(List<String> names) {
		LinkedHashSet<String> seen = new LinkedHashSet<>();
		for (String name : names) {
			if (name != null && !name.isBlank()) seen.add(name);
		}
		return List.copyOf(seen);
	}

	private static List<PriceCache.SearchResult> resolveDisplayNames(List<String> displayNames) {
		List<PriceCache.SearchResult> results = new ArrayList<>(displayNames.size());
		for (String displayName : displayNames) {
			String itemId = resolveItemId(displayName);
			double price = 0.0D;
			String source = "suggestion";
			PriceCache.PriceLookup lookup = PriceCache.get(itemId);
			if (lookup != null) {
				price = lookup.price();
				source = lookup.source();
			}
			results.add(new PriceCache.SearchResult(itemId, displayName, price, source, 0));
		}
		return List.copyOf(results);
	}

	private static String resolveItemId(String displayName) {
		if (displayName == null || displayName.isBlank()) return "";
		String normalized = normalizeName(displayName);
		if (normalized.startsWith("SHINY ")) {
			normalized = normalized.substring("SHINY ".length()).trim();
		}
		String alias = DISPLAY_ALIASES.get(normalized);
		if (alias != null) return alias;
		String bookId = resolveEnchantedBookId(displayName);
		if (bookId != null) return bookId;
		String generated = generatedItemId(normalized);
		if (PriceCache.containsItemId(generated)) return generated;
		List<PriceCache.SearchResult> hits = PriceCache.searchIndexed(normalized, 1);
		if (!hits.isEmpty()) return hits.getFirst().itemId();
		return generated;
	}

	private static String resolveEnchantedBookId(String itemName) {
		Matcher matcher = ENCHANTED_BOOK_PATTERN.matcher(itemName.trim());
		if (!matcher.matches()) return null;
		String enchantName = matcher.group(1);
		String romanLevel = matcher.group(2);
		int numericLevel = romanToInt(romanLevel);
		if (numericLevel <= 0) return null;
		String normalizedEnchantName = normalizeEnchantName(enchantName);
		boolean isUltimate = ULTIMATE_ENCHANTS.contains(normalizedEnchantName);
		if (isUltimate) return "ENCHANTMENT_ULTIMATE_" + normalizedEnchantName + "_" + numericLevel;
		return "ENCHANTMENT_" + normalizedEnchantName + "_" + numericLevel;
	}

	private static String normalizeEnchantName(String enchantName) {
		String normalized = enchantName.replaceFirst("(?i)^Ultimate\\s+", "");
		return normalized
			.replaceAll("[^A-Za-z0-9 ]", " ")
			.trim()
			.replaceAll("\\s+", "_")
			.toUpperCase(Locale.ROOT);
	}

	private static int romanToInt(String roman) {
		return switch (roman.toUpperCase(Locale.ROOT)) {
			case "I" -> 1;
			case "II" -> 2;
			case "III" -> 3;
			case "IV" -> 4;
			case "V" -> 5;
			case "VI" -> 6;
			case "VII" -> 7;
			case "VIII" -> 8;
			case "IX" -> 9;
			case "X" -> 10;
			default -> -1;
		};
	}

	private static String normalizeName(String name) {
		return name.trim().toUpperCase(Locale.ROOT);
	}

	private static String generatedItemId(String name) {
		return name.trim()
			.replace("'", "")
			.replaceAll("[^A-Za-z0-9 ]", " ")
			.trim()
			.replaceAll("\\s+", "_")
			.toUpperCase(Locale.ROOT);
	}

	private static Map<String, String> createDisplayAliases() {
		Map<String, String> aliases = new HashMap<>();
		aliases.put("BONZO'S STAFF", "BONZO_STAFF");
		aliases.put("BONZO'S MASK", "BONZO_MASK");
		aliases.put("RED NOSE", "RED_NOSE");
		aliases.put("BALLOON SNAKE", "BALLOON_SNAKE");
		aliases.put("SCARF'S STUDIES", "SCARF_STUDIES");
		aliases.put("RED SCARF", "RED_SCARF");
		aliases.put("ADAPTIVE BLADE", "STONE_BLADE");
		aliases.put("ADAPTIVE BELT", "ADAPTIVE_BELT");
		aliases.put("SUSPICIOUS VIAL", "SUSPICIOUS_VIAL");
		aliases.put("SPIRIT SHORTBOW", "BOSS_SPIRIT_BOW");
		aliases.put("SPIRIT STONE", "SPIRIT_STONE");
		aliases.put("DARK ORB", "DARK_ORB");
		aliases.put("SHADOW ASSASSIN CLOAK", "SHADOW_ASSASSIN_CLOAK");
		aliases.put("GIANT TOOTH", "GIANT_TOOTH");
		aliases.put("SADAN'S BROOCH", "SADANS_BROOCH");
		aliases.put("SUMMONING RING", "SUMMONING_RING");
		aliases.put("FEL SKULL", "FEL_SKULL");
		aliases.put("SOULWEAVER GLOVES", "SOULWEAVER_GLOVES");
		aliases.put("PRECURSOR EYE", "PRECURSOR_EYE");
		aliases.put("WITHER CLOAK SWORD", "WITHER_CLOAK");
		aliases.put("WITHER BLOOD", "WITHER_BLOOD");
		aliases.put("PRECURSOR GEAR", "PRECURSOR_GEAR");
		aliases.put("NECRON'S HANDLE", "NECRON_HANDLE");
		aliases.put("SHADOW WARP", "SHADOW_WARP_SCROLL");
		aliases.put("WITHER SHIELD", "WITHER_SHIELD_SCROLL");
		aliases.put("IMPLOSION", "IMPLOSION_SCROLL");
		aliases.put("AUTO RECOMBOBULATOR", "AUTO_RECOMBOBULATOR");
		aliases.put("STORM THE FISH", "STORM_THE_FISH");
		aliases.put("MAXOR THE FISH", "MAXOR_THE_FISH");
		aliases.put("GOLDOR THE FISH", "GOLDOR_THE_FISH");
		aliases.put("DUNGEON DISC", "DUNGEON_DISC");
		aliases.put("CLOWN DISC", "CLOWN_DISC");
		aliases.put("WATCHER DISC", "WATCHER_DISC");
		aliases.put("NECRON DISC", "NECRON_DISC");
		aliases.put("NECROMANCER'S BROOCH", "NECROMANCERS_BROOCH");
		aliases.put("HOT POTATO BOOK", "HOT_POTATO_BOOK");
		aliases.put("FIRST MASTER STAR", "FIRST_MASTER_STAR");
		aliases.put("SECOND MASTER STAR", "SECOND_MASTER_STAR");
		aliases.put("THIRD MASTER STAR", "THIRD_MASTER_STAR");
		aliases.put("FOURTH MASTER STAR", "FOURTH_MASTER_STAR");
		aliases.put("FIFTH MASTER STAR", "FIFTH_MASTER_STAR");
		aliases.put("LIVID DYE", "LIVID_DYE");
		aliases.put("NECRON DYE", "NECRON_DYE");
		aliases.put("DARK CLAYMORE", "DARK_CLAYMORE");
		aliases.put("TENTACLE DYE", "TENTACLE_DYE");
		aliases.put("HEAVY PEARL", "HEAVY_PEARL");
		aliases.put("MANDRAA", "MANDRAA");
		aliases.put("ANANKE FEATHER", "ANANKE_FEATHER");
		aliases.put("HELLSTORM WAND", "HELLSTORM_WAND");
		aliases.put("CRIMSON ESSENCE", "ESSENCE_CRIMSON");
		aliases.put("KUUDRA TEETH", "KUUDRA_TEETH");
		aliases.put("BEZAL SHARD", "SHARD_BEZAL");
		aliases.put("KRAKEN SHARD", "SHARD_KRAKEN");
		aliases.put("APEX DRAGON SHARD", "SHARD_APEX_DRAGON");
		for (int tier = 1; tier <= 10; tier++) {
			aliases.put("MASTER SKULL - TIER " + tier, "MASTER_SKULL_TIER_" + tier);
		}
		String[] attributeShards = {
			"MAGMA SLUG", "KADA KNIGHT", "WITHER SPECTER", "MATCHO", "LAVA FLAME", "FIRE EEL", "FLARE",
			"BARBARIAN DUKE X", "HELLWISP", "XYZ", "TAURUS", "LORD JAWBUS", "CINDERBAT", "DAEMON",
			"MOLTENFISH", "ANANKE"
		};
		for (String shard : attributeShards) {
			aliases.put(shard + " SHARD", "ATTRIBUTE_SHARD_" + shard.replace(' ', '_'));
		}
		aliases.put("AURORA STAFF", "AURORA_STAFF");
		aliases.put("HOLLOW WAND", "HOLLOW_WAND");
		aliases.put("MOLTEN NECKLACE", "MOLTEN_NECKLACE");
		aliases.put("MOLTEN CLOAK", "MOLTEN_CLOAK");
		aliases.put("MOLTEN BELT", "MOLTEN_BELT");
		aliases.put("MOLTEN BRACELET", "MOLTEN_BRACELET");
		aliases.put("BURNING KUUDRA CORE", "BURNING_KUUDRA_CORE");
		aliases.put("KUUDRA MANDIBLE", "KUUDRA_MANDIBLE");
		aliases.put("WHEEL OF FATE", "WHEEL_OF_FATE");
		aliases.put("TORMENTOR", "TORMENTOR");
		String[] kuudraSets = {"AURORA", "CRIMSON", "FERVOR", "HOLLOW", "TERROR"};
		String[] pieces = {"HELMET", "CHESTPLATE", "LEGGINGS", "BOOTS"};
		for (String set : kuudraSets) {
			for (String piece : pieces) {
				aliases.put(set + " " + piece, set + "_" + piece);
			}
		}
		aliases.put("SHADOW ASSASSIN HELMET", "SHADOW_ASSASSIN_HELMET");
		aliases.put("SHADOW ASSASSIN CHESTPLATE", "SHADOW_ASSASSIN_CHESTPLATE");
		aliases.put("SHADOW ASSASSIN LEGGINGS", "SHADOW_ASSASSIN_LEGGINGS");
		aliases.put("SHADOW ASSASSIN BOOTS", "SHADOW_ASSASSIN_BOOTS");
		aliases.put("LIVID DAGGER", "LIVID_DAGGER");
		aliases.put("SHADOW FURY", "SHADOW_FURY");
		aliases.put("LAST BREATH", "LAST_BREATH");
		aliases.put("WARPED STONE", "WARPED_STONE");
		aliases.put("NECROMANCER LORD HELMET", "NECROMANCER_LORD_HELMET");
		aliases.put("NECROMANCER LORD CHESTPLATE", "NECROMANCER_LORD_CHESTPLATE");
		aliases.put("NECROMANCER LORD LEGGINGS", "NECROMANCER_LORD_LEGGINGS");
		aliases.put("NECROMANCER LORD BOOTS", "NECROMANCER_LORD_BOOTS");
		aliases.put("NECROMANCER SWORD", "NECROMANCER_SWORD");
		aliases.put("GIANT'S SWORD", "GIANTS_SWORD");
		aliases.put("WITHER HELMET", "WITHER_HELMET");
		aliases.put("WITHER CHESTPLATE", "WITHER_CHESTPLATE");
		aliases.put("WITHER LEGGINGS", "WITHER_LEGGINGS");
		aliases.put("WITHER BOOTS", "WITHER_BOOTS");
		aliases.put("WITHER CATALYST", "WITHER_CATALYST");
		aliases.put("ADAPTIVE HELMET", "ADAPTIVE_HELMET");
		aliases.put("ADAPTIVE CHESTPLATE", "ADAPTIVE_CHESTPLATE");
		aliases.put("ADAPTIVE LEGGINGS", "ADAPTIVE_LEGGINGS");
		aliases.put("ADAPTIVE BOOTS", "ADAPTIVE_BOOTS");
		aliases.put("SPIRIT PET", "SPIRIT_PET");
		aliases.put("SPIRIT SWORD", "SPIRIT_SWORD");
		aliases.put("SPIRIT BOOTS", "SPIRIT_BOOTS");
		aliases.put("SPIRIT WING", "SPIRIT_WING");
		aliases.put("SPIRIT BONE", "SPIRIT_BONE");
		aliases.put("FUMING POTATO BOOK", "FUMING_POTATO_BOOK");
		aliases.put("RECOMBOBULATOR 3000", "RECOMBOBULATOR_3000");
		return Map.copyOf(aliases);
	}
}
