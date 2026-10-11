package dev.vy.drt.luck.capture;

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
 * mode agree, the run's completion time falls inside the entry's rounded age window, and party
 * rosters agree wherever both are known. Entries and runs that share candidates form a block; a
 * block is assigned only when it has as many entries as runs, the newest-first pairing is
 * consistent, and no part of it could continue onto an unseen page. Anything else stays unmatched.
 */
public final class CroesusRunMatcher {
	/** Allowance for client and server clocks and for the menu text being a moment old. */
	public static final long TIME_SLACK_MILLIS = 45_000L;

	public static final String METHOD_UNIQUE = "UNIQUE";
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
			if (couldReachUnseenPage(blockRuns, runsById, entries, menu, observedAtEpochMillis)) reason = REASON_PAGE_BOUNDARY;
			else if (blockRuns.size() != block.size()) reason = REASON_AMBIGUOUS;
			List<LuckRunRecord> ordered = new ArrayList<>();
			if (reason == null) {
				for (String id : blockRuns) ordered.add(runsById.get(id));
				ordered.sort(Comparator.comparingLong((LuckRunRecord run) -> run.completedAtEpochMillis).reversed()
					.thenComparing(run -> run.luckRunId));
				for (int i = 0; i < block.size(); i++) {
					if (!candidates.get(block.get(i).slot()).contains(ordered.get(i).luckRunId)) {
						reason = REASON_ORDER_CONFLICT;
						break;
					}
				}
			}
			for (int i = 0; i < block.size(); i++) {
				CroesusMenuParser.RunEntry entry = block.get(i);
				int count = candidates.get(entry.slot()).size();
				if (reason != null) {
					out.put(entry.slot(), new EntryMatch(entry.slot(), null, null, reason, count));
				} else {
					String method = block.size() == 1 ? METHOD_UNIQUE : METHOD_ORDER_IN_BLOCK;
					out.put(entry.slot(), new EntryMatch(entry.slot(), ordered.get(i).luckRunId, method, null, count));
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
		return true;
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
