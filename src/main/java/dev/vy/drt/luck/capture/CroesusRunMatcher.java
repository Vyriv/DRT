package dev.vy.drt.luck.capture;

import dev.vy.drt.luck.ChestTier;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * Links Croesus run entries to captured luck runs. An entry is a candidate for a run when floor and
 * mode agree, the run's completion time falls inside the entry's rounded age window, party rosters
 * agree wherever both are known, and every chest DRT saw opened in the dungeon is listed as opened.
 * Entries and runs that share candidates form a block, and a block is only linked when it has as
 * many runs as entries and could not continue onto an unseen page. Under the default
 * {@link Policy#STRICT} a block is linked only when the evidence allows a single pairing. Pairing by
 * the menu's newest-first order is only allowed under {@link Policy#LIST_ORDER}, which stays off
 * until that order is verified in game.
 */
public final class CroesusRunMatcher {
	/** Whether identical-looking entries may be paired with runs by the menu's list order. */
	public record Policy(boolean pairByListOrder) {
		public static final Policy STRICT = new Policy(false);
		public static final Policy LIST_ORDER = new Policy(true);
	}

	/** Allowance for client and server clocks and for the menu text being a moment old. */
	public static final long TIME_SLACK_MILLIS = 45_000L;

	public static final String METHOD_UNIQUE = "UNIQUE";
	/** A larger block where the evidence leaves exactly one possible pairing. */
	public static final String METHOD_ELIMINATION = "ELIMINATION";
	public static final String METHOD_ORDER_IN_BLOCK = "ORDER_IN_BLOCK";

	public static final String REASON_NOT_CATACOMBS = "NOT_CATACOMBS";
	public static final String REASON_UNREADABLE = "ENTRY_UNREADABLE";
	public static final String REASON_NO_CANDIDATE = "NO_CANDIDATE";
	public static final String REASON_AMBIGUOUS = "AMBIGUOUS";
	public static final String REASON_PAGE_BOUNDARY = "PAGE_BOUNDARY";
	public static final String REASON_ORDER_CONFLICT = "ORDER_CONFLICT";

	private CroesusRunMatcher() {
	}

	/** Outcome for one entry: a luck run id and method, or a reason it was left unmatched. */
	public record EntryMatch(int slot, String luckRunId, String method, String reason, int candidates) {
		public boolean matched() {
			return luckRunId != null;
		}
	}

	public record Result(Map<Integer, EntryMatch> bySlot) {
		public Result {
			bySlot = Map.copyOf(bySlot);
		}

		public EntryMatch forSlot(int slot) {
			return bySlot.get(slot);
		}
	}

	public static Result match(CroesusMenuParser.Menu menu, List<LuckRunRecord> runs, long observedAtEpochMillis) {
		return match(menu, runs, observedAtEpochMillis, Policy.STRICT);
	}

	public static Result match(CroesusMenuParser.Menu menu, List<LuckRunRecord> runs, long observedAtEpochMillis, Policy policy) {
		Policy rules = policy == null ? Policy.STRICT : policy;
		List<CroesusMenuParser.RunEntry> entries = new ArrayList<>(menu.entries());
		entries.sort(Comparator.comparingInt(CroesusMenuParser.RunEntry::position));
		Map<Integer, EntryMatch> out = new LinkedHashMap<>();
		Map<Integer, Set<String>> candidates = new HashMap<>();
		Map<String, LuckRunRecord> runsById = new HashMap<>();
		for (LuckRunRecord run : runs) runsById.put(run.luckRunId, run);

		for (CroesusMenuParser.RunEntry entry : entries) {
			if (!entry.catacombs()) {
				out.put(entry.slot(), new EntryMatch(entry.slot(), null, null, REASON_NOT_CATACOMBS, 0));
				continue;
			}
			if (entry.floor() == null || entry.completedAgoMin() == null || entry.completedAgoMax() == null) {
				out.put(entry.slot(), new EntryMatch(entry.slot(), null, null, REASON_UNREADABLE, 0));
				continue;
			}
			Set<String> ids = new TreeSet<>();
			for (LuckRunRecord run : runs) {
				if (isCandidate(entry, run, observedAtEpochMillis)) ids.add(run.luckRunId);
			}
			if (ids.isEmpty()) {
				out.put(entry.slot(), new EntryMatch(entry.slot(), null, null, REASON_NO_CANDIDATE, 0));
			} else {
				candidates.put(entry.slot(), ids);
			}
		}

		for (List<CroesusMenuParser.RunEntry> block : blocks(entries, candidates)) {
			Set<String> blockRuns = new HashSet<>();
			for (CroesusMenuParser.RunEntry entry : block) blockRuns.addAll(candidates.get(entry.slot()));
			String reason = null;
			String method = null;
			Map<Integer, String> assignment = null;
			if (couldReachUnseenPage(blockRuns, runsById, entries, menu, observedAtEpochMillis)) {
				reason = REASON_PAGE_BOUNDARY;
			} else if (blockRuns.size() != block.size()) {
				reason = REASON_AMBIGUOUS;
			} else if (block.size() == 1) {
				assignment = Map.of(block.get(0).slot(), blockRuns.iterator().next());
				method = METHOD_UNIQUE;
			} else if ((assignment = assignByElimination(block, candidates)) != null) {
				method = METHOD_ELIMINATION;
			} else if (rules.pairByListOrder()) {
				assignment = assignByListOrder(block, blockRuns, candidates, runsById);
				if (assignment == null) reason = REASON_ORDER_CONFLICT;
				else method = METHOD_ORDER_IN_BLOCK;
			} else {
				reason = REASON_AMBIGUOUS;
			}
			for (CroesusMenuParser.RunEntry entry : block) {
				int count = candidates.get(entry.slot()).size();
				if (reason != null) {
					out.put(entry.slot(), new EntryMatch(entry.slot(), null, null, reason, count));
				} else {
					out.put(entry.slot(), new EntryMatch(entry.slot(), assignment.get(entry.slot()), method, null, count));
				}
			}
		}

		dropOrderInversions(entries, out, runsById);
		return new Result(out);
	}

	static boolean isCandidate(CroesusMenuParser.RunEntry entry, LuckRunRecord run, long observedAt) {
		if (!entry.floor().name().equalsIgnoreCase(run.floor)) return false;
		long earliest = observedAt - entry.completedAgoMax() - TIME_SLACK_MILLIS;
		long latest = observedAt - entry.completedAgoMin() + TIME_SLACK_MILLIS;
		if (run.completedAtEpochMillis < earliest || run.completedAtEpochMillis > latest) return false;
		if (!sameRoster(entry.players(), run.party)) return false;
		if (run.croesus != null && !sameRoster(entry.players(), run.croesus.players)) return false;
		return purchasesAgree(entry, run);
	}

	/**
	 * A chest DRT saw opened in the dungeon must appear as opened on the entry. Chests opened later
	 * in Croesus prove nothing either way, so extra opened chests on the entry are allowed.
	 */
	static boolean purchasesAgree(CroesusMenuParser.RunEntry entry, LuckRunRecord run) {
		Set<String> opened = new HashSet<>();
		for (String name : entry.openedChests()) {
			ChestTier tier = LuckText.chestTier(name);
			if (tier != null) opened.add(tier.name());
		}
		for (LuckRunRecord.LuckChestRecord chest : run.chests) {
			if (!Boolean.TRUE.equals(chest.purchased) || !LuckRunRecord.SOURCE_DUNGEON_CHEST.equals(chest.purchaseSource)) continue;
			if (entry.noChestsOpened() || !opened.contains(chest.tier)) return false;
		}
		return true;
	}

	/**
	 * Pairs a block where every entry and run is pinned down by evidence alone: repeatedly takes an
	 * entry with one remaining candidate, or a run that only one remaining entry can be. Returns null
	 * as soon as a choice would be needed, so a result is the only pairing the evidence allows.
	 */
	static Map<Integer, String> assignByElimination(List<CroesusMenuParser.RunEntry> block, Map<Integer, Set<String>> candidates) {
		Map<Integer, Set<String>> open = new LinkedHashMap<>();
		for (CroesusMenuParser.RunEntry entry : block) open.put(entry.slot(), new HashSet<>(candidates.get(entry.slot())));
		Map<Integer, String> assigned = new HashMap<>();
		while (!open.isEmpty()) {
			Integer slot = null;
			String run = null;
			for (Map.Entry<Integer, Set<String>> entry : open.entrySet()) {
				if (entry.getValue().size() == 1) {
					slot = entry.getKey();
					run = entry.getValue().iterator().next();
					break;
				}
			}
			if (slot == null) {
				Map<String, List<Integer>> entriesByRun = new HashMap<>();
				for (Map.Entry<Integer, Set<String>> entry : open.entrySet()) {
					for (String id : entry.getValue()) entriesByRun.computeIfAbsent(id, key -> new ArrayList<>()).add(entry.getKey());
				}
				for (Map.Entry<String, List<Integer>> entry : entriesByRun.entrySet()) {
					if (entry.getValue().size() == 1) {
						slot = entry.getValue().get(0);
						run = entry.getKey();
						break;
					}
				}
			}
			if (slot == null) return null;
			assigned.put(slot, run);
			open.remove(slot);
			for (Set<String> remaining : open.values()) {
				remaining.remove(run);
				if (remaining.isEmpty()) return null;
			}
		}
		return assigned;
	}

	/** Newest entry to newest run, used only under {@link Policy#LIST_ORDER}. */
	private static Map<Integer, String> assignByListOrder(List<CroesusMenuParser.RunEntry> block, Set<String> blockRuns,
		Map<Integer, Set<String>> candidates, Map<String, LuckRunRecord> runsById) {
		List<LuckRunRecord> ordered = new ArrayList<>();
		for (String id : blockRuns) ordered.add(runsById.get(id));
		ordered.sort(Comparator.comparingLong((LuckRunRecord run) -> run.completedAtEpochMillis).reversed()
			.thenComparing(run -> run.luckRunId));
		Map<Integer, String> out = new HashMap<>();
		for (int i = 0; i < block.size(); i++) {
			int slot = block.get(i).slot();
			if (!candidates.get(slot).contains(ordered.get(i).luckRunId)) return null;
			out.put(slot, ordered.get(i).luckRunId);
		}
		return out;
	}

	/** Rosters agree when either is unknown or both name the same players, ignoring case. */
	static boolean sameRoster(List<String> a, List<String> b) {
		if (a == null || b == null || a.isEmpty() || b.isEmpty()) return true;
		return lowerSet(a).equals(lowerSet(b));
	}

	private static Set<String> lowerSet(List<String> names) {
		Set<String> out = new HashSet<>();
		for (String name : names) out.add(name.toLowerCase(Locale.ROOT));
		return out;
	}

	/** Connected groups of entries that share at least one candidate run, each in list order. */
	private static List<List<CroesusMenuParser.RunEntry>> blocks(List<CroesusMenuParser.RunEntry> entries, Map<Integer, Set<String>> candidates) {
		List<CroesusMenuParser.RunEntry> pending = new ArrayList<>();
		for (CroesusMenuParser.RunEntry entry : entries) if (candidates.containsKey(entry.slot())) pending.add(entry);
		List<List<CroesusMenuParser.RunEntry>> out = new ArrayList<>();
		Set<Integer> assigned = new HashSet<>();
		for (CroesusMenuParser.RunEntry seed : pending) {
			if (assigned.contains(seed.slot())) continue;
			Set<String> runs = new HashSet<>(candidates.get(seed.slot()));
			Set<Integer> members = new HashSet<>();
			members.add(seed.slot());
			boolean grew = true;
			while (grew) {
				grew = false;
				for (CroesusMenuParser.RunEntry other : pending) {
					if (members.contains(other.slot())) continue;
					Set<String> otherRuns = candidates.get(other.slot());
					if (otherRuns.stream().anyMatch(runs::contains)) {
						members.add(other.slot());
						runs.addAll(otherRuns);
						grew = true;
					}
				}
			}
			List<CroesusMenuParser.RunEntry> block = new ArrayList<>();
			for (CroesusMenuParser.RunEntry entry : pending) if (members.contains(entry.slot())) block.add(entry);
			assigned.addAll(members);
			out.add(block);
		}
		return out;
	}

	/**
	 * Entries on later pages are at least as old as the last visible entry, and entries on earlier
	 * pages at most as old as the first. A block whose runs could be that old (or that new) might
	 * belong to entries we cannot see, so its pairing is not trusted.
	 */
	private static boolean couldReachUnseenPage(Set<String> blockRuns, Map<String, LuckRunRecord> runsById,
		List<CroesusMenuParser.RunEntry> entries, CroesusMenuParser.Menu menu, long observedAt) {
		if (entries.isEmpty()) return menu.hasNextPage() || menu.hasPreviousPage();
		CroesusMenuParser.RunEntry first = entries.get(0);
		CroesusMenuParser.RunEntry last = entries.get(entries.size() - 1);
		for (String id : blockRuns) {
			long age = observedAt - runsById.get(id).completedAtEpochMillis;
			if (menu.hasNextPage() && (last.completedAgoMin() == null || age >= last.completedAgoMin() - TIME_SLACK_MILLIS)) return true;
			if (menu.hasPreviousPage() && (first.completedAgoMax() == null || age <= first.completedAgoMax() + TIME_SLACK_MILLIS)) return true;
		}
		return false;
	}

	/** Croesus lists newest first, so matched runs must not get newer further down the list. */
	private static void dropOrderInversions(List<CroesusMenuParser.RunEntry> entries, Map<Integer, EntryMatch> out, Map<String, LuckRunRecord> runsById) {
		List<EntryMatch> matched = new ArrayList<>();
		for (CroesusMenuParser.RunEntry entry : entries) {
			EntryMatch match = out.get(entry.slot());
			if (match != null && match.matched()) matched.add(match);
		}
		Set<Integer> conflicted = new HashSet<>();
		for (int i = 0; i < matched.size(); i++) {
			for (int j = i + 1; j < matched.size(); j++) {
				long newer = runsById.get(matched.get(i).luckRunId()).completedAtEpochMillis;
				long older = runsById.get(matched.get(j).luckRunId()).completedAtEpochMillis;
				if (older > newer) {
					conflicted.add(matched.get(i).slot());
					conflicted.add(matched.get(j).slot());
				}
			}
		}
		for (int slot : conflicted) {
			EntryMatch match = out.get(slot);
			out.put(slot, new EntryMatch(slot, null, null, REASON_ORDER_CONFLICT, match.candidates()));
		}
	}
}
