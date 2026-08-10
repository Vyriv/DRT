package dev.vy.drt.config;

public final class DungeonRunCompletionRecord {
	public String completionId = "";
	public String runSessionId = "";
	public String completionFingerprint = "";
	public long completedAtEpochMillis;
	public String mode = "UNKNOWN";
	public String floor = "UNKNOWN";
	public String grade = "?";
	public long runTimeMs;

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
