package dev.vy.drt.client.screen;

import dev.vy.drt.config.DungeonRunCompletionRecord;
import dev.vy.drt.config.DungeonRunRecord;
import dev.vy.drt.history.RunHistoryLinks;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

final class OverviewDashboardData {
	private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("d MMM yyyy, HH:mm", Locale.ROOT).withZone(ZoneId.systemDefault());

	enum Metric {
		PROFIT("Profit over time", "Profit"),
		PROFIT_DAY("Profit per day", "Daily"),
		RUNS("Runs completed over time", "Runs"),
		RUN_TIME("Run completion times", "Times"),
		RUN_PROFIT("Profit per recorded run", "Run Profit");

		final String label;
		final String compact;
		Metric(String label, String compact) { this.label = label; this.compact = compact; }
	}

	enum TimeRange {
		DAYS_7("7D", 7), DAYS_30("30D", 30), DAYS_90("90D", 90), ALL("All", 0);
		final String label;
		final int days;
		TimeRange(String label, int days) { this.label = label; this.days = days; }
	}

	record Point(long timestamp, long value, String tooltip) {}
	record RecentRun(long timestamp, String floor, String grade, Long runTimeMs) {}
	record RecentChest(long timestamp, String floor, String chest, long profit) {}
	record FloorPerformance(String floor, int runs, int timedRuns, long totalTimeMs, long profit) {}
	record Snapshot(
		int totalRuns,
		long totalProfit,
		Long averageLinkedRunProfit,
		int linkedProfitRuns,
		Long averageRunTimeMs,
		Long bestRunTimeMs,
		Long worstRunTimeMs,
		String mostProfitableFloor,
		long mostProfitableFloorProfit,
		List<Point> graphPoints,
		List<RecentRun> recentRuns,
		List<RecentChest> recentChests,
		List<FloorPerformance> floors,
		List<String> availableFloors
	) {}

	private OverviewDashboardData() {}

	static Snapshot build(
		List<DungeonRunCompletionRecord> completions,
		List<DungeonRunRecord> chests,
		Metric metric,
		TimeRange range,
		String floorFilter,
		long now
	) {
		List<DungeonRunCompletionRecord> allRuns = completions == null ? List.of() : completions.stream().filter(r -> r != null).toList();
		List<DungeonRunRecord> allChests = chests == null ? List.of() : chests.stream().filter(r -> r != null).toList();
		Set<String> floorNames = new LinkedHashSet<>();
		for (DungeonRunCompletionRecord run : allRuns) addFloor(floorNames, run.floor);
		for (DungeonRunRecord chest : allChests) addFloor(floorNames, chest.floor);
		List<String> availableFloors = floorNames.stream().sorted(OverviewDashboardData::compareFloors).toList();

		List<DungeonRunCompletionRecord> runs = allRuns.stream().filter(r -> matchesFloor(r.floor, floorFilter)).toList();
		List<DungeonRunRecord> filteredChests = allChests.stream().filter(r -> matchesFloor(r.floor, floorFilter)).toList();
		Map<DungeonRunRecord, RunHistoryLinks.Link> links = RunHistoryLinks.resolve(allChests, allRuns);

		long totalProfit = filteredChests.stream().mapToLong(r -> r.chestProfitCoins).sum();
		long totalTimedMs = 0L;
		int timedRuns = 0;
		Long bestTime = null;
		Long worstTime = null;
		for (DungeonRunCompletionRecord run : runs) {
			RunHistoryLinks.RunTime time = RunHistoryLinks.runTime(run);
			if (time == null) continue;
			totalTimedMs += time.millis();
			timedRuns++;
			bestTime = bestTime == null ? time.millis() : Math.min(bestTime, time.millis());
			worstTime = worstTime == null ? time.millis() : Math.max(worstTime, time.millis());
		}

		Map<String, Long> linkedProfit = new HashMap<>();
		for (DungeonRunRecord chest : filteredChests) {
			RunHistoryLinks.Link link = links.get(chest);
			if (link == null || !link.linked() || !matchesFloor(link.completion().floor, floorFilter)) continue;
			linkedProfit.merge(runKey(link.completion()), chest.chestProfitCoins, Long::sum);
		}
		Long averageLinkedProfit = linkedProfit.isEmpty() ? null : linkedProfit.values().stream().mapToLong(Long::longValue).sum() / linkedProfit.size();

		Map<String, Long> profitByFloor = new HashMap<>();
		for (DungeonRunRecord chest : filteredChests) profitByFloor.merge(normalizeFloor(chest.floor), chest.chestProfitCoins, Long::sum);
		Map.Entry<String, Long> mostProfitable = profitByFloor.entrySet().stream().max(Map.Entry.comparingByValue()).orElse(null);

		long cutoff = range.days == 0 ? Long.MIN_VALUE : now - range.days * 86_400_000L;
		List<Point> points = buildGraphPoints(metric, runs, filteredChests, links, linkedProfit, cutoff);
		List<RecentRun> recentRuns = runs.stream()
			.sorted(Comparator.comparingLong((DungeonRunCompletionRecord r) -> r.completedAtEpochMillis).reversed())
			.limit(5)
			.map(r -> {
				RunHistoryLinks.RunTime time = RunHistoryLinks.runTime(r);
				return new RecentRun(r.completedAtEpochMillis, normalizeFloor(r.floor), r.grade == null ? "?" : r.grade, time == null ? null : time.millis());
			}).toList();
		List<RecentChest> recentChests = filteredChests.stream()
			.sorted(Comparator.comparingLong((DungeonRunRecord r) -> r.timestampEpochMillis).reversed())
			.limit(5)
			.map(r -> new RecentChest(r.timestampEpochMillis, normalizeFloor(r.floor), r.chestTitle == null ? "Chest" : r.chestTitle, r.chestProfitCoins))
			.toList();

		Map<String, MutableFloor> floorStats = new LinkedHashMap<>();
		for (DungeonRunCompletionRecord run : runs) {
			MutableFloor stats = floorStats.computeIfAbsent(normalizeFloor(run.floor), MutableFloor::new);
			stats.runs++;
			RunHistoryLinks.RunTime time = RunHistoryLinks.runTime(run);
			if (time != null) { stats.timedRuns++; stats.totalTimeMs += time.millis(); }
		}
		for (DungeonRunRecord chest : filteredChests) floorStats.computeIfAbsent(normalizeFloor(chest.floor), MutableFloor::new).profit += chest.chestProfitCoins;
		List<FloorPerformance> floors = floorStats.values().stream()
			.sorted(Comparator.comparingInt((MutableFloor f) -> f.runs).reversed().thenComparing(f -> f.floor))
			.limit(6)
			.map(f -> new FloorPerformance(f.floor, f.runs, f.timedRuns, f.totalTimeMs, f.profit)).toList();

		return new Snapshot(
			runs.size(), totalProfit, averageLinkedProfit, linkedProfit.size(),
			timedRuns == 0 ? null : totalTimedMs / timedRuns, bestTime, worstTime,
			mostProfitable == null ? null : mostProfitable.getKey(), mostProfitable == null ? 0L : mostProfitable.getValue(),
			List.copyOf(points), recentRuns, recentChests, floors, availableFloors
		);
	}

	private static List<Point> buildGraphPoints(
		Metric metric,
		List<DungeonRunCompletionRecord> runs,
		List<DungeonRunRecord> chests,
		Map<DungeonRunRecord, RunHistoryLinks.Link> links,
		Map<String, Long> linkedProfit,
		long cutoff
	) {
		List<Point> points = new ArrayList<>();
		switch (metric) {
			case PROFIT -> {
				long cumulative = 0L;
				for (DungeonRunRecord chest : chests.stream().filter(r -> r.timestampEpochMillis > 0L && r.timestampEpochMillis >= cutoff).sorted(Comparator.comparingLong(r -> r.timestampEpochMillis)).toList()) {
					cumulative += chest.chestProfitCoins;
					points.add(new Point(chest.timestampEpochMillis, cumulative, date(chest.timestampEpochMillis) + " | Cumulative " + signedCoins(cumulative)));
				}
			}
			case PROFIT_DAY -> {
				Map<java.time.LocalDate, Long> profitByDay = new java.util.TreeMap<>();
				java.time.ZoneId zone = java.time.ZoneId.systemDefault();
				for (DungeonRunRecord chest : chests) {
					if (chest.timestampEpochMillis <= 0L || chest.timestampEpochMillis < cutoff) continue;
					java.time.LocalDate day = Instant.ofEpochMilli(chest.timestampEpochMillis).atZone(zone).toLocalDate();
					profitByDay.merge(day, chest.chestProfitCoins, Long::sum);
				}
				for (Map.Entry<java.time.LocalDate, Long> entry : profitByDay.entrySet()) {
					long timestamp = entry.getKey().atStartOfDay(zone).toInstant().toEpochMilli();
					points.add(new Point(timestamp, entry.getValue(), entry.getKey().format(DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ROOT)) + " | " + signedCoins(entry.getValue())));
				}
			}
			case RUNS -> {
				long count = 0L;
				for (DungeonRunCompletionRecord run : runs.stream().filter(r -> r.completedAtEpochMillis > 0L && r.completedAtEpochMillis >= cutoff).sorted(Comparator.comparingLong(r -> r.completedAtEpochMillis)).toList()) {
					count++;
					points.add(new Point(run.completedAtEpochMillis, count, date(run.completedAtEpochMillis) + " | " + count + " completed"));
				}
			}
			case RUN_TIME -> {
				for (DungeonRunCompletionRecord run : runs.stream().filter(r -> r.completedAtEpochMillis > 0L && r.completedAtEpochMillis >= cutoff).sorted(Comparator.comparingLong(r -> r.completedAtEpochMillis)).toList()) {
					RunHistoryLinks.RunTime time = RunHistoryLinks.runTime(run);
					if (time != null) points.add(new Point(run.completedAtEpochMillis, time.millis(), date(run.completedAtEpochMillis) + " | " + normalizeFloor(run.floor) + " " + RunHistoryLinks.formatRunTime(time.millis())));
				}
			}
			case RUN_PROFIT -> {
				Map<String, DungeonRunCompletionRecord> linkedRuns = new HashMap<>();
				for (DungeonRunRecord chest : chests) {
					RunHistoryLinks.Link link = links.get(chest);
					if (link != null && link.linked()) linkedRuns.putIfAbsent(runKey(link.completion()), link.completion());
				}
				for (Map.Entry<String, Long> entry : linkedProfit.entrySet().stream()
					.sorted(Comparator.comparingLong(e -> linkedRuns.get(e.getKey()).completedAtEpochMillis)).toList()) {
					DungeonRunCompletionRecord run = linkedRuns.get(entry.getKey());
					if (run == null || run.completedAtEpochMillis <= 0L || run.completedAtEpochMillis < cutoff) continue;
					points.add(new Point(run.completedAtEpochMillis, entry.getValue(), date(run.completedAtEpochMillis) + " | " + normalizeFloor(run.floor) + " " + signedCoins(entry.getValue())));
				}
			}
		}
		return points;
	}

	private static String runKey(DungeonRunCompletionRecord run) {
		if (run.runSessionId != null && !run.runSessionId.isBlank()) return "session:" + run.runSessionId;
		if (run.completionId != null && !run.completionId.isBlank()) return "completion:" + run.completionId;
		return "record:" + run.completedAtEpochMillis + '|' + normalizeFloor(run.floor) + '|' + (run.completionFingerprint == null ? "" : run.completionFingerprint);
	}

	private static boolean matchesFloor(String floor, String filter) {
		return filter == null || filter.isBlank() || normalizeFloor(floor).equalsIgnoreCase(filter);
	}

	private static void addFloor(Set<String> floors, String floor) {
		String normalized = normalizeFloor(floor);
		if (!normalized.equals("UNKNOWN")) floors.add(normalized);
	}

	private static String normalizeFloor(String floor) {
		return floor == null || floor.isBlank() ? "UNKNOWN" : floor.trim().toUpperCase(Locale.ROOT);
	}

	private static int compareFloors(String left, String right) {
		char leftType = left.isEmpty() ? 'Z' : left.charAt(0);
		char rightType = right.isEmpty() ? 'Z' : right.charAt(0);
		int typeCompare = Integer.compare(typeOrder(leftType), typeOrder(rightType));
		if (typeCompare != 0) return typeCompare;
		return left.compareTo(right);
	}

	private static int typeOrder(char type) {
		return switch (type) { case 'F' -> 0; case 'M' -> 1; case 'K' -> 2; default -> 3; };
	}

	private static String date(long timestamp) {
		return timestamp <= 0L ? "Unknown date" : DATE.format(Instant.ofEpochMilli(timestamp));
	}

	private static String signedCoins(long value) {
		return String.format(Locale.ROOT, "%s%,d", value >= 0 ? "+" : "-", Math.abs(value));
	}

	private static final class MutableFloor {
		final String floor;
		int runs;
		int timedRuns;
		long totalTimeMs;
		long profit;
		MutableFloor(String floor) { this.floor = floor; }
	}
}
