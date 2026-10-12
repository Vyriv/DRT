package dev.vy.drt.config;

public final class DungeonRunCompletionRecord {
	/** Hypixel's "Defeated <boss> in MMm SSs" completion line, second precision. */
	public static final String RUN_TIME_OFFICIAL = "OFFICIAL_CHAT";
	/** DRT's wall clock between a detected start and the completion, minus pauses. */
	public static final String RUN_TIME_MEASURED = "MEASURED";
	/** Entered by the player in the manual run screen. */
	public static final String RUN_TIME_MANUAL = "MANUAL";

	public String completionId = "";
	public String runSessionId = "";
	public String completionFingerprint = "";
	public long completedAtEpochMillis;
	public String mode = "UNKNOWN";
	public String floor = "UNKNOWN";
	public String grade = "?";
	public long runTimeMs;
	/** Where {@link #runTimeMs} came from; null for records written before sources were stored. */
	public String runTimeSource;

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
		copy.runTimeSource = runTimeSource;
		return copy;
	}

	public void normalize() {
		completedAtEpochMillis = Math.max(0L, completedAtEpochMillis);
		mode = normalize(mode, "UNKNOWN");
		floor = normalize(floor, "UNKNOWN");
		grade = normalize(grade, "?");
		runTimeMs = Math.max(0L, runTimeMs);
		runSessionId = runSessionId == null ? "" : runSessionId;
		completionFingerprint = completionFingerprint == null ? "" : completionFingerprint;
		completionId = completionId == null ? "" : completionId;
		if (runTimeSource != null && runTimeSource.isBlank()) runTimeSource = null;
	}

	/**
	 * The stored source, or for older records the source proven by the completion fingerprint:
	 * live fingerprints end in the official time ("session|floor|grade|millis"), 0 when the chat
	 * line was not seen. Returns null when nothing proves where the time came from.
	 */
	public String effectiveRunTimeSource() {
		if (runTimeMs <= 0L) return null;
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
