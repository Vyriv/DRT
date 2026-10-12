package dev.vy.drt.history;

import dev.vy.drt.config.DungeonRunCompletionRecord;
import dev.vy.drt.config.DungeonRunRecord;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Decides which completed run each loot history chest belongs to, without changing stored records.
 * A chest is only tied to a run on proof: a link stored when it was opened, or for older records the
 * exact traces an in-dungeon open left behind. Anything else stays unlinked, which only hides the
 * run's duration and offers; loot, cost and profit are never touched.
 */
public final class RunHistoryLinks {
	/** One normal open plus one with a Dungeon Chest Key. */
	public static final int MAX_CHESTS_PER_RUN = 2;

	public enum Source { LIVE, CROESUS_UNIQUE, LEGACY_EXACT, MANUAL, NONE }

	public enum TimeSource { OFFICIAL, MEASURED, MANUAL }

	public record Link(Source source, String runId, DungeonRunCompletionRecord completion, String reason) {
		public boolean linked() {
			return source != Source.NONE && completion != null;
		}

		static Link none(String reason) {
			return new Link(Source.NONE, "", null, reason);
		}
	}

	public record RunTime(long millis, TimeSource source) {
	}

	private RunHistoryLinks() {
	}

	public static Map<DungeonRunRecord, Link> resolve(List<DungeonRunRecord> history, List<DungeonRunCompletionRecord> completions) {
		Map<String, DungeonRunCompletionRecord> bySession = new HashMap<>();
		Set<String> duplicateSessions = new HashSet<>();
		Map<String, DungeonRunCompletionRecord> manualByFingerprint = new HashMap<>();
		for (DungeonRunCompletionRecord completion : completions == null ? List.<DungeonRunCompletionRecord>of() : completions) {
			if (completion == null) continue;
			String session = completion.runSessionId == null ? "" : completion.runSessionId;
			if (!session.isBlank() && bySession.putIfAbsent(session, completion) != null) duplicateSessions.add(session);
			String fingerprint = completion.completionFingerprint == null ? "" : completion.completionFingerprint;
			if (fingerprint.startsWith("manual|")) manualByFingerprint.putIfAbsent(fingerprint, completion);
		}
		duplicateSessions.forEach(bySession::remove);

		List<DungeonRunRecord> records = history == null ? List.of() : history;
		Map<String, List<DungeonRunRecord>> legacyBySession = new HashMap<>();
		for (DungeonRunRecord record : records) {
			if (record == null || record.runLinkSource != null) continue;
			legacyBySession.computeIfAbsent(nullToEmpty(record.runSessionId), key -> new ArrayList<>()).add(record);
		}

		Map<DungeonRunRecord, Link> out = new IdentityHashMap<>();
		for (DungeonRunRecord record : records) {
			if (record == null) continue;
			out.put(record, record.runLinkSource != null
				? storedLink(record, bySession)
				: legacyLink(record, bySession, manualByFingerprint, legacyBySession));
		}
		rejectOverfullRuns(out);
		return out;
	}

	private static Link storedLink(DungeonRunRecord record, Map<String, DungeonRunCompletionRecord> bySession) {
		Source source = switch (record.runLinkSource) {
			case DungeonRunRecord.LINK_LIVE -> Source.LIVE;
			case DungeonRunRecord.LINK_CROESUS_UNIQUE -> Source.CROESUS_UNIQUE;
			default -> Source.NONE;
		};
		if (source == Source.NONE) return Link.none("Recorded without a provable run");
		DungeonRunCompletionRecord completion = bySession.get(nullToEmpty(record.linkedRunId));
		if (completion == null) return Link.none("Linked run is no longer recorded");
		return new Link(source, completion.runSessionId, completion, null);
	}

	/**
	 * An in-dungeon open is stamped with the completion's own timestamp and run session, and a run
	 * can only have two chests. Croesus opens that were attached to the latest run instead carry a
	 * later timestamp or pile up under one session, so they fail these checks.
	 */
	private static Link legacyLink(
		DungeonRunRecord record,
		Map<String, DungeonRunCompletionRecord> bySession,
		Map<String, DungeonRunCompletionRecord> manualByFingerprint,
		Map<String, List<DungeonRunRecord>> legacyBySession
	) {
		DungeonRunCompletionRecord manual = manualByFingerprint.get("manual|" + nullToEmpty(record.commitFingerprint));
		if (manual != null) return new Link(Source.MANUAL, manual.runSessionId, manual, null);
		String session = nullToEmpty(record.runSessionId);
		DungeonRunCompletionRecord completion = bySession.get(session);
		if (completion == null) return Link.none("No recorded completion for this chest's run");
		List<DungeonRunRecord> group = legacyBySession.getOrDefault(session, List.of());
		if (group.size() > MAX_CHESTS_PER_RUN) return Link.none(group.size() + " chests were filed under one run");
		if (hasDuplicateTier(group)) return Link.none("The same chest was filed twice under one run");
		if (!sameFloor(record.floor, completion.floor)) return Link.none("Chest floor differs from the run");
		if (record.timestampEpochMillis != completion.completedAtEpochMillis) return Link.none("Opened outside the run's own chest window");
		return new Link(Source.LEGACY_EXACT, completion.runSessionId, completion, null);
	}

	private static void rejectOverfullRuns(Map<DungeonRunRecord, Link> links) {
		Map<String, List<DungeonRunRecord>> byRun = new HashMap<>();
		for (Map.Entry<DungeonRunRecord, Link> entry : links.entrySet()) {
			if (entry.getValue().linked()) byRun.computeIfAbsent(entry.getValue().runId(), key -> new ArrayList<>()).add(entry.getKey());
		}
		for (List<DungeonRunRecord> group : byRun.values()) {
			if (group.size() <= MAX_CHESTS_PER_RUN && !hasDuplicateTier(group)) continue;
			for (DungeonRunRecord record : group) links.put(record, Link.none("More chests point at this run than it can have"));
		}
	}

	/** The run's duration with its source, or null when it is unknown or its source is unproven. */
	public static RunTime runTime(DungeonRunCompletionRecord completion) {
		if (completion == null || completion.runTimeMs <= 0L) return null;
		String source = completion.effectiveRunTimeSource();
		if (source == null) return null;
		return switch (source) {
			case DungeonRunCompletionRecord.RUN_TIME_OFFICIAL -> new RunTime(completion.runTimeMs, TimeSource.OFFICIAL);
			case DungeonRunCompletionRecord.RUN_TIME_MEASURED -> new RunTime(completion.runTimeMs, TimeSource.MEASURED);
			case DungeonRunCompletionRecord.RUN_TIME_MANUAL -> new RunTime(completion.runTimeMs, TimeSource.MANUAL);
			default -> null;
		};
	}

	/** "3m 40s", or "1h 02m 05s" for long runs. */
	public static String formatRunTime(long millis) {
		long seconds = Math.max(0L, millis) / 1000L;
		long hours = seconds / 3600L;
		long minutes = (seconds % 3600L) / 60L;
		long secs = seconds % 60L;
		if (hours > 0L) return String.format(Locale.ROOT, "%dh %02dm %02ds", hours, minutes, secs);
		return String.format(Locale.ROOT, "%dm %02ds", minutes, secs);
	}

	private static boolean hasDuplicateTier(List<DungeonRunRecord> group) {
		Set<String> seen = new HashSet<>();
		for (DungeonRunRecord record : group) {
			String title = nullToEmpty(record.chestTitle).trim().toUpperCase(Locale.ROOT);
			if (!seen.add(title)) return true;
		}
		return false;
	}

	private static boolean sameFloor(String chestFloor, String runFloor) {
		String chest = nullToEmpty(chestFloor).trim();
		if (chest.isEmpty() || chest.equalsIgnoreCase("UNKNOWN")) return true;
		return chest.equalsIgnoreCase(nullToEmpty(runFloor).trim());
	}

	private static String nullToEmpty(String value) {
		return value == null ? "" : value;
	}
}
