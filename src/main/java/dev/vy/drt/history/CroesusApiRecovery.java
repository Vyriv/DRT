package dev.vy.drt.history;

import dev.vy.drt.api.DungeonPlayerStats;
import dev.vy.drt.config.ChestCostBreakdown;
import dev.vy.drt.config.DungeonChestOffer;
import dev.vy.drt.config.DungeonFloor;
import dev.vy.drt.config.DungeonLootEntry;
import dev.vy.drt.config.DungeonPartyMember;
import dev.vy.drt.config.DungeonRunCompletionRecord;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Conservative reconciliation of temporary Hypixel Croesus data into existing completion owners. */
public final class CroesusApiRecovery {
	public static final long MATCH_WINDOW_MS = 90_000L;
	private static final Pattern NAME = Pattern.compile("[A-Za-z0-9_]{1,16}");

	public record Match(DungeonRunCompletionRecord completion, DungeonPlayerStats.RecentRun run,
		List<DungeonPartyMember> party, List<DungeonChestOffer> newOffers) {}
	public record Plan(List<Match> matches, int ambiguous, int unmatched, int alreadyCaptured) {}

	private CroesusApiRecovery() {}

	public static Plan plan(List<DungeonRunCompletionRecord> completions, List<DungeonPlayerStats.RecentRun> runs) {
		List<Match> matches = new ArrayList<>();
		int ambiguous = 0;
		int unmatched = 0;
		int alreadyCaptured = 0;
		for (DungeonPlayerStats.RecentRun run : runs == null ? List.<DungeonPlayerStats.RecentRun>of() : runs) {
			List<DungeonRunCompletionRecord> candidates = new ArrayList<>();
			for (DungeonRunCompletionRecord completion : completions == null ? List.<DungeonRunCompletionRecord>of() : completions) {
				if (completion == null || !same(completion.floor, run.floor()) || run.completedAt() <= 0L) continue;
				if (Math.abs(completion.completedAtEpochMillis - run.completedAt()) <= MATCH_WINDOW_MS) candidates.add(completion);
			}
			if (candidates.size() > 1) { ambiguous++; continue; }
			if (candidates.isEmpty()) { unmatched++; continue; }
			DungeonRunCompletionRecord completion = candidates.getFirst();
			List<DungeonChestOffer> offers = new ArrayList<>();
			for (DungeonPlayerStats.Chest chest : run.chests()) {
				String title = chestTitle(chest.type());
				if (title.isBlank()) continue;
				if (hasOffer(completion, title)) { alreadyCaptured++; continue; }
				if (chest.rewards().isEmpty()) continue;
				List<DungeonLootEntry> loot = chest.rewards().stream().map(CroesusApiRecovery::loot).toList();
				DungeonChestOffer offer = new DungeonChestOffer(title, new ChestCostBreakdown(), 0L, loot);
				offer.contextFloor = floor(completion.floor);
				offer.purchased = chest.paid();
				offer.alreadyOpened = chest.paid();
				offer.observedAtEpochMillis = System.currentTimeMillis();
				offer.captureSource = DungeonChestOffer.SOURCE_API_RECOVERED;
				offers.add(offer);
			}
			List<DungeonPartyMember> party = run.participants().stream().map(CroesusApiRecovery::party)
				.filter(member -> !member.name.isBlank()).toList();
			matches.add(new Match(completion, run, party, List.copyOf(offers)));
		}
		return new Plan(List.copyOf(matches), ambiguous, unmatched, alreadyCaptured);
	}

	private static boolean hasOffer(DungeonRunCompletionRecord completion, String title) {
		if (completion.chestOffers == null) return false;
		return completion.chestOffers.stream().anyMatch(offer -> offer != null && same(offer.chestTitle, title));
	}

	private static DungeonPartyMember party(String display) {
		// Hypixel's display_name carries the class and level: "§bcatgirllivid§f: §eMage§b (§e39§b)".
		DungeonPartyMember classified = CroesusPartyParser.participant(display);
		if (classified != null) return classified;
		String plain = display == null ? "" : display.replaceAll("§.", "");
		int colon = plain.indexOf(':');
		if (colon > 0) {
			String beforeClass = plain.substring(0, colon);
			Matcher prefix = NAME.matcher(beforeClass);
			String candidate = "";
			while (prefix.find()) candidate = prefix.group();
			if (validName(candidate)) return new DungeonPartyMember(candidate, "", "", 0);
		}
		Matcher matcher = NAME.matcher(plain);
		String last = "";
		while (matcher.find()) if (validName(matcher.group())) last = matcher.group();
		return new DungeonPartyMember(last, "", "", 0);
	}

	private static boolean validName(String value) {
		return value != null && value.matches("(?i)(?=.*[A-Z_])[A-Z0-9_]{1,16}")
			&& !value.matches("(?i)HEALER|MAGE|BERSERK|ARCHER|TANK|MVP|VIP");
	}

	private static DungeonLootEntry loot(String reward) {
		String raw = reward == null ? "" : reward.trim();
		if (raw.startsWith("ESSENCE:")) {
			String[] parts = raw.split(":");
			int quantity = parts.length > 2 ? integer(parts[2], 1) : 1;
			String type = parts.length > 1 ? title(parts[1]) : "Dungeon";
			return new DungeonLootEntry(type + " Essence", "ESSENCE_" + type.toUpperCase(Locale.ROOT), quantity);
		}
		return new DungeonLootEntry(title(raw), raw.toUpperCase(Locale.ROOT), 1);
	}

	private static String chestTitle(String type) {
		String clean = title(type);
		return clean.isBlank() ? "" : clean + " Chest";
	}

	private static String title(String value) {
		String clean = value == null ? "" : value.trim().replace('_', ' ').toLowerCase(Locale.ROOT);
		StringBuilder out = new StringBuilder(clean.length());
		boolean upper = true;
		for (int i = 0; i < clean.length(); i++) {
			char c = clean.charAt(i);
			out.append(upper ? Character.toUpperCase(c) : c);
			upper = c == ' ';
		}
		return out.toString();
	}

	private static int integer(String value, int fallback) {
		try { return Math.max(1, Integer.parseInt(value)); }
		catch (NumberFormatException ignored) { return fallback; }
	}

	private static DungeonFloor floor(String value) {
		try { return DungeonFloor.valueOf(value); }
		catch (RuntimeException ignored) { return DungeonFloor.UNKNOWN; }
	}

	private static boolean same(String left, String right) {
		return (left == null ? "" : left).equalsIgnoreCase(right == null ? "" : right);
	}
}
