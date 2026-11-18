package dev.vy.drt.history;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Parses the Hypixel-provided lore embedded in Croesus chest icons. */
public final class CroesusChestLore {
	private static final Pattern COST = Pattern.compile(
		"(?i)(?:CHEST\\s+)?(?:COST|PRICE)\\s*:?\\s*([0-9][0-9,.]*)\\s*(?:COINS?)?"
	);
	private static final Pattern SUFFIX_QUANTITY = Pattern.compile("(?i)^(.+?)\\s*[x×]\\s*([0-9][0-9,]*)$");
	private static final Pattern PREFIX_QUANTITY = Pattern.compile("(?i)^([0-9][0-9,]*)\\s*[x×]\\s*(.+)$");
	private static final Pattern ESSENCE_QUANTITY = Pattern.compile("(?i)^(.+?ESSENCE)\\s*:?\\s*([0-9][0-9,]*)$");

	public record Reward(String name, int quantity) {
	}

	public record Offer(Long costCoins, boolean alreadyOpened, List<Reward> rewards) {
		public Offer {
			rewards = rewards == null ? List.of() : List.copyOf(rewards);
		}
	}

	private CroesusChestLore() {
	}

	public static Offer parse(List<String> lore) {
		List<String> lines = lore == null ? List.of() : lore;
		Long cost = null;
		boolean opened = false;
		boolean contents = false;
		List<Reward> rewards = new ArrayList<>();
		for (String raw : lines) {
			String line = clean(raw);
			String upper = line.toUpperCase(java.util.Locale.ROOT);
			if (upper.contains("ALREADY OPENED") || upper.contains("CHEST OPENED")) opened = true;
			if ((upper.contains("COST") || upper.contains("PRICE")) && upper.contains("FREE")) cost = 0L;
			Matcher costMatcher = COST.matcher(line);
			if (costMatcher.find()) cost = parseLong(costMatcher.group(1));

			if (upper.equals("CONTENTS") || upper.equals("CONTENTS:")) {
				contents = true;
				continue;
			}
			if (!contents || line.isBlank()) continue;
			if (endsContents(upper)) {
				contents = false;
				continue;
			}
			if (isContentsMetadata(upper)) continue;
			Reward reward = parseReward(line);
			if (reward != null) rewards.add(reward);
		}
		return new Offer(cost, opened, rewards);
	}

	public static Map<String, Offer> parseAll(Map<String, List<String>> chestLoreByTitle) {
		Map<String, Offer> parsed = new LinkedHashMap<>();
		if (chestLoreByTitle == null) return parsed;
		chestLoreByTitle.forEach((title, lore) -> {
			if (title != null && !title.isBlank()) parsed.put(title, parse(lore));
		});
		return parsed;
	}

	private static Reward parseReward(String line) {
		Matcher suffix = SUFFIX_QUANTITY.matcher(line);
		if (suffix.matches()) return reward(suffix.group(1), suffix.group(2));
		Matcher prefix = PREFIX_QUANTITY.matcher(line);
		if (prefix.matches()) return reward(prefix.group(2), prefix.group(1));
		Matcher essence = ESSENCE_QUANTITY.matcher(line);
		if (essence.matches()) return reward(essence.group(1), essence.group(2));
		return new Reward(line, 1);
	}

	private static Reward reward(String name, String quantity) {
		long parsed = parseLong(quantity);
		return new Reward(clean(name), (int) Math.max(1L, Math.min(Integer.MAX_VALUE, parsed)));
	}

	private static boolean endsContents(String upper) {
		return upper.startsWith("COST")
			|| upper.startsWith("CHEST COST")
			|| upper.startsWith("PRICE")
			|| upper.startsWith("VALUE")
			|| upper.startsWith("PROFIT")
			|| upper.startsWith("CLICK TO")
			|| upper.startsWith("OPEN REWARD")
			|| upper.startsWith("REQUIRES")
			|| upper.startsWith("ALREADY OPENED");
	}

	private static boolean isContentsMetadata(String upper) {
		return upper.equals("NONE")
			|| upper.startsWith("DUNGEON CHEST KEY")
			|| upper.startsWith("KISMET FEATHER")
			|| upper.startsWith("YOU MAY REROLL");
	}

	private static long parseLong(String value) {
		if (value == null) return 0L;
		try {
			return Long.parseLong(value.replace(",", "").replace(".", "").trim());
		} catch (NumberFormatException ignored) {
			return 0L;
		}
	}

	private static String clean(String value) {
		return value == null ? "" : value.replace('\u00a0', ' ').trim();
	}
}
