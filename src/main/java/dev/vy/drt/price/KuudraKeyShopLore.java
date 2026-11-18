package dev.vy.drt.price;

import dev.vy.drt.config.DungeonFloor;
import dev.vy.drt.config.KuudraKeyShopCost;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Mage and Barbarian emissary key offers. Hypixel prints materials as either
 * "20x Enchanted Mycelium" or "Enchanted Mycelium x20", and the coin line can sit
 * above or below those materials. Requirement lines are not part of the price.
 */
public final class KuudraKeyShopLore {
	private static final Pattern COIN_LINE = Pattern.compile(
		"([-+]?\\d[\\d,]*(?:\\.\\d+)?)\\s*([kmb])?\\s*coins?\\b",
		Pattern.CASE_INSENSITIVE
	);
	private static final Pattern MATERIAL_PREFIX = Pattern.compile(
		"^(\\d[\\d,]*)\\s*[xX×]\\s+(.+)$"
	);
	private static final Pattern MATERIAL_SUFFIX = Pattern.compile(
		"^(.+?)\\s+[xX×]\\s*(\\d[\\d,]*)$"
	);

	private KuudraKeyShopLore() {
	}

	/** Free and Paid chests exist only in Kuudra. Wood through Bedrock stay dungeon chests. */
	public static boolean isKuudraChestTitle(String title) {
		String normalized = title == null ? "" : title.trim().toUpperCase(Locale.ROOT);
		return normalized.equals("FREE CHEST") || normalized.equals("PAID CHEST");
	}

	/** Faction emissary screens. A generic chest or "Shop" title is not the key shop. */
	public static boolean isShopTitle(String title) {
		String normalized = title == null ? "" : title.trim().toUpperCase(Locale.ROOT);
		return normalized.equals("MAGE SHOP")
			|| normalized.equals("BARBARIAN SHOP")
			|| normalized.equals("MAGE EMISSARY")
			|| normalized.equals("BARBARIAN EMISSARY");
	}

	/**
	 * Longest key names first so a Burning key is not read as the bare Kuudra Key.
	 * A trailing suffix such as a stack name is still the same key.
	 */
	public static DungeonFloor tierFromItemName(String name) {
		String normalized = name == null ? "" : name.trim().toUpperCase(Locale.ROOT);
		if (normalized.isEmpty()) return DungeonFloor.UNKNOWN;
		String[] names = {
			"INFERNAL KUUDRA KEY",
			"FIERY KUUDRA KEY",
			"BURNING KUUDRA KEY",
			"HOT KUUDRA KEY",
			"BASIC KUUDRA KEY",
			"KUUDRA KEY"
		};
		DungeonFloor[] tiers = {
			DungeonFloor.K5,
			DungeonFloor.K4,
			DungeonFloor.K3,
			DungeonFloor.K2,
			DungeonFloor.K1,
			DungeonFloor.K1
		};
		for (int index = 0; index < names.length; index++) {
			String key = names[index];
			if (normalized.equals(key) || normalized.startsWith(key + " ")) return tiers[index];
		}
		return DungeonFloor.UNKNOWN;
	}

	public static KuudraKeyShopCost parse(List<String> loreLines) {
		if (loreLines == null || loreLines.isEmpty()) return null;
		int costIndex = -1;
		for (int index = 0; index < loreLines.size(); index++) {
			if (loreLines.get(index) != null && loreLines.get(index).trim().equalsIgnoreCase("Cost")) {
				costIndex = index;
				break;
			}
		}
		if (costIndex < 0) return null;

		KuudraKeyShopCost cost = new KuudraKeyShopCost();
		for (int index = costIndex + 1; index < loreLines.size(); index++) {
			String line = loreLines.get(index) == null ? "" : loreLines.get(index).trim();
			if (line.isEmpty()) continue;
			Long coins = coinAmount(line);
			if (coins != null) {
				if (coins <= 0L) return null;
				cost.coins = coins;
				continue;
			}
			MaterialAmount material = materialAmount(line);
			if (material == null) continue;
			String itemId = DungeonProfitPricing.kuudraKeyMaterialItemId(material.name());
			if (itemId.isEmpty() || material.amount() <= 0) continue;
			cost.materials.merge(itemId, material.amount(), Integer::sum);
		}
		return cost.coins > 0L ? cost : null;
	}

	private static Long coinAmount(String line) {
		Matcher matcher = COIN_LINE.matcher(line);
		if (!matcher.find()) return null;
		double amount = Double.parseDouble(matcher.group(1).replace(",", ""));
		String suffix = matcher.group(2);
		if (suffix != null && !suffix.isBlank()) {
			switch (Character.toLowerCase(suffix.charAt(0))) {
				case 'k' -> amount *= 1_000.0D;
				case 'm' -> amount *= 1_000_000.0D;
				case 'b' -> amount *= 1_000_000_000.0D;
				default -> {
				}
			}
		}
		return Math.round(Math.abs(amount));
	}

	private static MaterialAmount materialAmount(String line) {
		Matcher prefix = MATERIAL_PREFIX.matcher(line);
		if (prefix.matches()) {
			return new MaterialAmount(prefix.group(2).trim(), parseCount(prefix.group(1)));
		}
		Matcher suffix = MATERIAL_SUFFIX.matcher(line);
		if (suffix.matches()) {
			return new MaterialAmount(suffix.group(1).trim(), parseCount(suffix.group(2)));
		}
		return null;
	}

	private static int parseCount(String raw) {
		try {
			return Math.max(0, Integer.parseInt(raw.replace(",", "")));
		} catch (NumberFormatException ignored) {
			return 0;
		}
	}

	private record MaterialAmount(String name, int amount) {
	}
}
