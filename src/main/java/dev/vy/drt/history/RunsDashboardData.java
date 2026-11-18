package dev.vy.drt.history;

import dev.vy.drt.config.DungeonPartyMember;
import dev.vy.drt.config.DungeonRunCompletionRecord;
import dev.vy.drt.config.DungeonRunRecord;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Pure Runs-tab projection and analysis over persisted completion identities. */
public final class RunsDashboardData {
	private static final List<String> FLOOR_ORDER = List.of(
		"F1", "F2", "F3", "F4", "F5", "F6", "F7",
		"M1", "M2", "M3", "M4", "M5", "M6", "M7",
		"K1", "K2", "K3", "K4", "K5"
	);
	public enum Sort { NEWEST, OLDEST, FASTEST, HIGHEST_SCORE }

	public record Entry(DungeonRunCompletionRecord completion, List<DungeonRunRecord> purchases,
		long realisedCost, long realisedValue, long realisedProfit, int offerCount) {
		public boolean hasPurchase() { return !purchases.isEmpty(); }
		public DungeonRunRecord primaryPurchase() { return purchases.isEmpty() ? null : purchases.getLast(); }
	}

	public record Metrics(int totalRuns, int timedRuns, long averageDurationMs, long fastestDurationMs,
		int scoredRuns, int inferredScoreRuns, double averageScore, int bestScore, int sPlusRuns, int gradedRuns,
		Map<String, Integer> gradeDistribution) {
		public double sPlusPercent() { return gradedRuns <= 0 ? 0D : sPlusRuns * 100D / gradedRuns; }
	}

	public record FloorPerformance(String floor, int runs, int timedRuns, long averageDurationMs,
		long fastestDurationMs, int scoredRuns, double averageScore, int sPlusRuns, int gradedRuns) {
		public double sPlusPercent() { return gradedRuns == 0 ? 0D : sPlusRuns * 100D / gradedRuns; }
	}

	public record Teammate(String identity, String name, String uuid, int runsTogether, int timedRuns,
		long averageDurationMs, long fastestDurationMs, int scoredRuns, double averageScore,
		int sPlusRuns, int gradedRuns, Map<String, FloorPerformance> floors, List<String> completionIds) {
		public double sPlusPercent() { return gradedRuns == 0 ? 0D : sPlusRuns * 100D / gradedRuns; }
	}

	public record Snapshot(List<Entry> entries, List<String> availableFloors, Metrics metrics, List<Teammate> teammates) {
	}

	private RunsDashboardData() {
	}

	public static Snapshot build(List<DungeonRunCompletionRecord> completions, List<DungeonRunRecord> history,
		String floor, String query, Sort sort) {
		return build(completions, history, floor, query, sort, "", "");
	}

	public static Snapshot build(List<DungeonRunCompletionRecord> completions, List<DungeonRunRecord> history,
		String floor, String query, Sort sort, String ownUuid, String ownName) {
		List<DungeonRunCompletionRecord> completionList = completions == null ? List.of() : completions;
		List<DungeonRunRecord> historyList = history == null ? List.of() : history;
		Map<DungeonRunRecord, RunHistoryLinks.Link> links = RunHistoryLinks.resolve(historyList, completionList);
		Map<String, List<DungeonRunRecord>> purchasesByRun = new LinkedHashMap<>();
		for (DungeonRunRecord record : historyList) {
			RunHistoryLinks.Link link = links.get(record);
			if (link != null && link.linked()) purchasesByRun.computeIfAbsent(link.runId(), ignored -> new ArrayList<>()).add(record);
		}

		Set<String> floors = new LinkedHashSet<>();
		List<Entry> entries = new ArrayList<>();
		List<Entry> allEntries = new ArrayList<>();
		String floorFilter = text(floor).toUpperCase(Locale.ROOT);
		String search = text(query).toLowerCase(Locale.ROOT);
		for (DungeonRunCompletionRecord completion : completionList) {
			if (completion == null) continue;
			String runFloor = text(completion.floor).toUpperCase(Locale.ROOT);
			if (!runFloor.isBlank() && !runFloor.equals("UNKNOWN")) floors.add(runFloor);
			List<DungeonRunRecord> purchases = List.copyOf(purchasesByRun.getOrDefault(completion.runSessionId, List.of()));
			Entry entry = entry(completion, purchases);
			allEntries.add(entry);
			if (!floorFilter.isBlank() && !floorFilter.equals(runFloor)) continue;
			if (!search.isBlank() && !matches(entry, search)) continue;
			entries.add(entry);
		}
		entries.sort(comparator(sort == null ? Sort.NEWEST : sort));
		return new Snapshot(List.copyOf(entries), orderedFloors(floors), metrics(entries), teammates(allEntries, ownUuid, ownName));
	}

	static List<String> orderedFloors(Set<String> floors) {
		if (floors == null || floors.isEmpty()) return List.of();
		return floors.stream().sorted(Comparator.<String>comparingInt(floor -> {
			int index = FLOOR_ORDER.indexOf(text(floor).toUpperCase(Locale.ROOT));
			return index < 0 ? Integer.MAX_VALUE : index;
		}).thenComparing(String.CASE_INSENSITIVE_ORDER)).toList();
	}

	private static Entry entry(DungeonRunCompletionRecord completion, List<DungeonRunRecord> purchases) {
		long cost = 0L;
		long value = 0L;
		long profit = 0L;
		for (DungeonRunRecord purchase : purchases) {
			if (purchase == null) continue;
			cost += purchase.totalCostCoins();
			value += purchase.chestValueCoins;
			profit += purchase.chestProfitCoins;
		}
		int offers = completion.chestOffers == null ? 0 : completion.chestOffers.size();
		return new Entry(completion, purchases, cost, value, profit, offers);
	}

	private static boolean matches(Entry entry, String query) {
		DungeonRunCompletionRecord completion = entry.completion();
		if (text(completion.floor).toLowerCase(Locale.ROOT).contains(query)
			|| text(completion.grade).toLowerCase(Locale.ROOT).contains(query)
			|| text(completion.mode).toLowerCase(Locale.ROOT).contains(query)) return true;
		for (DungeonRunRecord purchase : entry.purchases()) {
			if (text(purchase.chestTitle).toLowerCase(Locale.ROOT).contains(query)) return true;
		}
		if (completion.partyMembers != null) {
			for (DungeonPartyMember member : completion.partyMembers) {
				if (member != null && (text(member.name).toLowerCase(Locale.ROOT).contains(query)
					|| text(member.dungeonClass).toLowerCase(Locale.ROOT).contains(query)
					|| text(member.kuudraRole).toLowerCase(Locale.ROOT).contains(query))) return true;
			}
		}
		return false;
	}

	private static Comparator<Entry> comparator(Sort sort) {
		return switch (sort) {
			case OLDEST -> Comparator.comparingLong(entry -> entry.completion().completedAtEpochMillis);
			case FASTEST -> Comparator.comparingLong((Entry entry) -> {
				RunHistoryLinks.RunTime time = RunHistoryLinks.runTime(entry.completion());
				return time == null ? Long.MAX_VALUE : time.millis();
			}).thenComparing(Comparator.comparingLong((Entry entry) -> entry.completion().completedAtEpochMillis).reversed());
			case HIGHEST_SCORE -> Comparator.comparingInt((Entry entry) -> {
				RunScore.Resolved score = RunScore.resolve(entry.completion());
				return score.known() ? score.value() : Integer.MIN_VALUE;
			})
				.reversed().thenComparing(Comparator.comparingLong((Entry entry) -> entry.completion().completedAtEpochMillis).reversed());
			default -> Comparator.comparingLong((Entry entry) -> entry.completion().completedAtEpochMillis).reversed();
		};
	}

	private static Metrics metrics(List<Entry> entries) {
		long durationSum = 0L;
		long fastest = Long.MAX_VALUE;
		int timed = 0;
		long scoreSum = 0L;
		int scored = 0;
		int inferred = 0;
		int best = 0;
		int sPlus = 0;
		int graded = 0;
		Map<String, Integer> grades = new LinkedHashMap<>();
		for (Entry entry : entries) {
			DungeonRunCompletionRecord completion = entry.completion();
			RunHistoryLinks.RunTime time = RunHistoryLinks.runTime(completion);
			if (time != null) {
				durationSum += time.millis();
				fastest = Math.min(fastest, time.millis());
				timed++;
			}
			RunScore.Resolved resolvedScore = RunScore.resolve(completion);
			if (resolvedScore.actual()) {
				scoreSum += resolvedScore.value();
				best = Math.max(best, resolvedScore.value());
				scored++;
			}
			if (resolvedScore.inferred()) inferred++;
			String grade = text(completion.grade).toUpperCase(Locale.ROOT);
			if (!grade.isBlank() && !grade.equals("?")) {
				grades.merge(grade, 1, Integer::sum);
				graded++;
				if (grade.equals("S+")) sPlus++;
			}
		}
		return new Metrics(entries.size(), timed, timed == 0 ? 0L : durationSum / timed,
			fastest == Long.MAX_VALUE ? 0L : fastest, scored, inferred, scored == 0 ? 0D : scoreSum / (double) scored,
			best, sPlus, graded, Map.copyOf(grades));
	}

	private static List<Teammate> teammates(List<Entry> entries, String ownUuid, String ownName) {
		Map<String, MutableTeammate> aggregate = new LinkedHashMap<>();
		for (Entry entry : entries) {
			DungeonRunCompletionRecord completion = entry.completion();
			if (completion.partyMembers == null) continue;
			Set<String> seen = new LinkedHashSet<>();
			for (DungeonPartyMember member : completion.partyMembers) {
				if (!isValidHistoricalMember(member)) continue;
				if (!text(ownUuid).isBlank() && text(member.uuid).equalsIgnoreCase(text(ownUuid).replace("-", ""))) continue;
				if (!text(ownName).isBlank() && text(member.name).equalsIgnoreCase(text(ownName))) continue;
				String identity = text(member.uuid).isBlank() ? "name:" + member.name.toLowerCase(Locale.ROOT)
					: "uuid:" + member.uuid.toLowerCase(Locale.ROOT);
				if (!seen.add(identity)) continue;
				MutableTeammate item = aggregate.computeIfAbsent(identity,
					ignored -> new MutableTeammate(identity, member.name, text(member.uuid)));
				item.add(completion);
			}
		}
		List<Teammate> result = new ArrayList<>();
		for (MutableTeammate item : aggregate.values()) result.add(item.snapshot());
		result.sort(Comparator.comparingInt(Teammate::runsTogether).reversed().thenComparing(Teammate::name));
		return List.copyOf(result);
	}

	public static boolean isValidHistoricalMember(DungeonPartyMember member) {
		if (member == null) return false;
		if (text(member.uuid).matches("(?i)[0-9a-f]{32}")) return true;
		String name = text(member.name);
		if (!name.matches("(?i)[a-z0-9_]{1,16}")) return false;
		return !name.matches("\\d{1,3}") || !text(member.dungeonClass).isBlank() || member.classLevel > 0;
	}

	private static String text(String value) { return value == null ? "" : value.trim(); }

	private static final class MutableTeammate {
		private final String identity;
		private final String name;
		private final String uuid;
		private int runs;
		private long duration;
		private int timed;
		private long fastest = Long.MAX_VALUE;
		private long score;
		private int scored;
		private int sPlus;
		private int graded;
		private final Map<String, MutableFloor> floors = new LinkedHashMap<>();
		private final List<String> completionIds = new ArrayList<>();
		private MutableTeammate(String identity, String name, String uuid) { this.identity = identity; this.name = name; this.uuid = uuid; }
		private void add(DungeonRunCompletionRecord completion) {
			runs++;
			RunHistoryLinks.RunTime time = RunHistoryLinks.runTime(completion);
			if (time != null) { duration += time.millis(); timed++; fastest = Math.min(fastest, time.millis()); }
			RunScore.Resolved resolvedScore = RunScore.resolve(completion);
			if (resolvedScore.actual()) { score += resolvedScore.value(); scored++; }
			String grade = text(completion.grade).toUpperCase(Locale.ROOT);
			if (!grade.isBlank() && !grade.equals("?")) { graded++; if (grade.equals("S+")) sPlus++; }
			String floor = text(completion.floor).toUpperCase(Locale.ROOT);
			if (!floor.isBlank()) floors.computeIfAbsent(floor, MutableFloor::new).add(completion, time);
			String id = text(completion.completionId).isBlank() ? text(completion.runSessionId) : completion.completionId;
			if (!id.isBlank()) completionIds.add(id);
		}
		private Teammate snapshot() {
			Map<String, FloorPerformance> floorData = new LinkedHashMap<>();
			floors.forEach((key, value) -> floorData.put(key, value.snapshot()));
			return new Teammate(identity, name, uuid, runs, timed, timed == 0 ? 0 : duration / timed,
				fastest == Long.MAX_VALUE ? 0 : fastest, scored, scored == 0 ? 0 : score / (double) scored,
				sPlus, graded, Map.copyOf(floorData), List.copyOf(completionIds));
		}
	}

	private static final class MutableFloor {
		private final String floor;
		private int runs, timed, scored, sPlus, graded;
		private long duration, fastest = Long.MAX_VALUE, score;
		private MutableFloor(String floor) { this.floor = floor; }
		private void add(DungeonRunCompletionRecord completion, RunHistoryLinks.RunTime time) {
			runs++;
			if (time != null) { timed++; duration += time.millis(); fastest = Math.min(fastest, time.millis()); }
			RunScore.Resolved resolvedScore = RunScore.resolve(completion);
			if (resolvedScore.actual()) { scored++; score += resolvedScore.value(); }
			String grade = text(completion.grade).toUpperCase(Locale.ROOT);
			if (!grade.isBlank() && !grade.equals("?")) { graded++; if (grade.equals("S+")) sPlus++; }
		}
		private FloorPerformance snapshot() {
			return new FloorPerformance(floor, runs, timed, timed == 0 ? 0 : duration / timed,
				fastest == Long.MAX_VALUE ? 0 : fastest, scored, scored == 0 ? 0 : score / (double) scored,
				sPlus, graded);
		}
	}
}
