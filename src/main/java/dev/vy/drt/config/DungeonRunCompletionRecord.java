package dev.vy.drt.config;

import java.util.ArrayList;
import java.util.List;

public final class DungeonRunCompletionRecord {
	/** Hypixel's "Defeated <boss> in MMm SSs" completion line, second precision. */
	public static final String RUN_TIME_OFFICIAL = "OFFICIAL_CHAT";
	/** DRT's wall clock between a detected start and the completion, minus pauses. */
	public static final String RUN_TIME_MEASURED = "MEASURED";
	/** Entered by the player in the manual run screen. */
	public static final String RUN_TIME_MANUAL = "MANUAL";
	public static final String TIME_EVIDENCE_SINGLE_LINE = "SINGLE_LINE_SUMMARY";
	public static final String TIME_EVIDENCE_MULTILINE = "MULTILINE_SUMMARY";
	public static final String TIME_EVIDENCE_MEASURED = "MEASURED";
	public static final String TIME_EVIDENCE_MANUAL = "MANUAL";
	/** No floor or Kuudra tier finishes faster; shorter times are timing faults, not runs. */
	public static final long MIN_PLAUSIBLE_RUN_TIME_MS = 20_000L;
	public static final long MAX_PLAUSIBLE_RUN_TIME_MS = 3L * 60L * 60L * 1000L;

	public String completionId = "";
	public String runSessionId = "";
	public String completionFingerprint = "";
	public long completedAtEpochMillis;
	public String mode = "UNKNOWN";
	public String floor = "UNKNOWN";
	public String grade = "?";
	/** Hypixel team score. Legacy zero values are unavailable unless {@link #scoreRecorded} is true. */
	public int score;
	/** True proves that {@link #score} was captured, including a genuine zero. Null preserves legacy files. */
	public Boolean scoreRecorded;
	public long runTimeMs;
	/** Where {@link #runTimeMs} came from; null for records written before sources were stored. */
	public String runTimeSource;
	/** Specific evidence quality used when two official completion messages disagree. */
	public String runTimeEvidence;
	/** Undashed Minecraft UUID of the account that played the run; null for records written before accounts were stored. */
	public String playerUuid;
	/** {@link ManualRecord#CREATED} or {@link ManualRecord#EDITED} once the player made or changed this run; never cleared. */
	public String manual;
	/** Reward chest offers genuinely observed for this completed run. */
	public List<DungeonChestOffer> chestOffers = new ArrayList<>();
	/** Historical party and class levels genuinely observed from the live dungeon tab or Croesus entry. */
	public List<DungeonPartyMember> partyMembers = new ArrayList<>();

	public DungeonRunCompletionRecord() {
	}

	public DungeonRunCompletionRecord(
		long completedAtEpochMillis,
		String mode,
		String floor,
		String grade,
		long runTimeMs,
		String runSessionId,
		String completionFingerprint
	) {
		this.completedAtEpochMillis = Math.max(0L, completedAtEpochMillis);
		this.mode = normalize(mode, "UNKNOWN");
		this.floor = normalize(floor, "UNKNOWN");
		this.grade = normalize(grade, "?");
		this.runTimeMs = Math.max(0L, runTimeMs);
		this.runSessionId = runSessionId == null ? "" : runSessionId;
		this.completionFingerprint = completionFingerprint == null ? "" : completionFingerprint;
	}

	public DungeonRunCompletionRecord copy() {
		DungeonRunCompletionRecord copy = new DungeonRunCompletionRecord(
			completedAtEpochMillis,
			mode,
			floor,
			grade,
			runTimeMs,
			runSessionId,
			completionFingerprint
		);
		copy.completionId = completionId == null ? "" : completionId;
		copy.score = score;
		copy.scoreRecorded = scoreRecorded;
		copy.runTimeSource = runTimeSource;
		copy.runTimeEvidence = runTimeEvidence;
		copy.playerUuid = playerUuid;
		copy.manual = ManualRecord.normalize(manual);
		if (chestOffers != null) {
			for (DungeonChestOffer offer : chestOffers) {
				if (offer != null) copy.chestOffers.add(offer.copy());
			}
		}
		if (partyMembers != null) for (DungeonPartyMember member : partyMembers) if (member != null) copy.partyMembers.add(member.copy());
		return copy;
	}

	/** Created in the manual run screen or changed by the player, including runs saved before the marker existed. */
	public boolean isManual() {
		return manual != null
			|| RUN_TIME_MANUAL.equals(runTimeSource)
			|| (completionFingerprint != null && completionFingerprint.startsWith("manual|"));
	}

	public boolean hasRecordedScore() {
		return score > 0 || Boolean.TRUE.equals(scoreRecorded);
	}

	public void normalize() {
		completedAtEpochMillis = Math.max(0L, completedAtEpochMillis);
		mode = normalize(mode, "UNKNOWN");
		floor = normalize(floor, "UNKNOWN");
		grade = normalize(grade, "?");
		score = Math.max(0, score);
		runTimeMs = Math.max(0L, runTimeMs);
		runSessionId = runSessionId == null ? "" : runSessionId;
		completionFingerprint = completionFingerprint == null ? "" : completionFingerprint;
		completionId = completionId == null ? "" : completionId;
		if (runTimeSource != null && runTimeSource.isBlank()) runTimeSource = null;
		if (runTimeEvidence != null && runTimeEvidence.isBlank()) runTimeEvidence = null;
		if (playerUuid != null && playerUuid.isBlank()) playerUuid = null;
		manual = ManualRecord.normalize(manual);
		if (chestOffers == null) chestOffers = new ArrayList<>();
		chestOffers.removeIf(offer -> offer == null);
		for (DungeonChestOffer offer : chestOffers) offer.normalize();
		if (partyMembers == null) partyMembers = new ArrayList<>();
		partyMembers.removeIf(member -> member == null);
		for (DungeonPartyMember member : partyMembers) member.normalize();
	}

	/**
	 * The stored source, or for older records the source proven by the completion fingerprint:
	 * live fingerprints end in the official time ("session|floor|grade|millis"), 0 when the chat
	 * line was not seen. Returns null when nothing proves where the time came from, or when the
	 * time is implausible, so such a run keeps its record but never counts as timed.
	 */
	public String effectiveRunTimeSource() {
		if (!plausibleRunTime(runTimeMs)) return null;
		if (runTimeSource != null) return runTimeSource;
		String fingerprint = completionFingerprint == null ? "" : completionFingerprint;
		if (fingerprint.startsWith("manual|")) return RUN_TIME_MANUAL;
		String[] parts = fingerprint.split("\\|");
		if (parts.length != 4) return null;
		long official;
		try {
			official = Long.parseLong(parts[3].trim());
		} catch (NumberFormatException e) {
			return null;
		}
		if (official > 0L) return official == runTimeMs ? RUN_TIME_OFFICIAL : null;
		return RUN_TIME_MEASURED;
	}

	public static boolean plausibleRunTime(long runTimeMs) {
		return runTimeMs >= MIN_PLAUSIBLE_RUN_TIME_MS && runTimeMs <= MAX_PLAUSIBLE_RUN_TIME_MS;
	}

	public int runTimeEvidenceRank() {
		if (TIME_EVIDENCE_MULTILINE.equals(runTimeEvidence)) return 40;
		if (TIME_EVIDENCE_SINGLE_LINE.equals(runTimeEvidence)) return 30;
		if (TIME_EVIDENCE_MANUAL.equals(runTimeEvidence) || RUN_TIME_MANUAL.equals(runTimeSource)) return 20;
		if (TIME_EVIDENCE_MEASURED.equals(runTimeEvidence) || RUN_TIME_MEASURED.equals(runTimeSource)) return 10;
		if (RUN_TIME_OFFICIAL.equals(runTimeSource)) return 30;
		return 0;
	}

	public boolean equivalentTo(DungeonRunCompletionRecord other) {
		if (other == null) return false;
		return completedAtEpochMillis == other.completedAtEpochMillis
			&& runTimeMs == other.runTimeMs
			&& same(mode, other.mode)
			&& same(floor, other.floor)
			&& same(grade, other.grade)
			&& same(runSessionId, other.runSessionId)
			&& same(completionFingerprint, other.completionFingerprint);
	}

	private static String normalize(String value, String fallback) {
		String normalized = value == null ? "" : value.trim();
		return normalized.isBlank() ? fallback : normalized;
	}

	private static boolean same(String left, String right) {
		String leftText = left == null ? "" : left.trim();
		String rightText = right == null ? "" : right.trim();
		return leftText.equalsIgnoreCase(rightText);
	}
}
