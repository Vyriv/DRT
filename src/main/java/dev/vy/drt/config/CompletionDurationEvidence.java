package dev.vy.drt.config;

/** Applies stronger completion-time evidence without changing a run's persisted identity. */
public final class CompletionDurationEvidence {
	public record Update(boolean changed, long previousMillis) {
	}

	private CompletionDurationEvidence() {
	}

	public static Update apply(DungeonRunCompletionRecord completion, long runTimeMs, String evidence) {
		if (completion == null || !DungeonRunCompletionRecord.plausibleRunTime(runTimeMs)) return new Update(false, 0L);
		DungeonRunCompletionRecord incoming = new DungeonRunCompletionRecord();
		incoming.runTimeMs = runTimeMs;
		incoming.runTimeSource = DungeonRunCompletionRecord.RUN_TIME_OFFICIAL;
		incoming.runTimeEvidence = evidence;
		int incomingRank = incoming.runTimeEvidenceRank();
		int existingRank = completion.runTimeEvidenceRank();
		if (incomingRank < existingRank) return new Update(false, completion.runTimeMs);
		if (incomingRank == existingRank && completion.runTimeMs > 0L && completion.runTimeMs != runTimeMs) {
			return new Update(false, completion.runTimeMs);
		}
		if (completion.runTimeMs == runTimeMs
			&& DungeonRunCompletionRecord.RUN_TIME_OFFICIAL.equals(completion.runTimeSource)
			&& java.util.Objects.equals(completion.runTimeEvidence, evidence)) {
			return new Update(false, completion.runTimeMs);
		}
		long previous = completion.runTimeMs;
		completion.runTimeMs = runTimeMs;
		completion.runTimeSource = DungeonRunCompletionRecord.RUN_TIME_OFFICIAL;
		completion.runTimeEvidence = evidence;
		String prefix = completion.runSessionId + "|" + completion.floor + "|" + completion.grade + "|";
		if (completion.completionFingerprint != null && completion.completionFingerprint.startsWith(prefix)) {
			completion.completionFingerprint = prefix + runTimeMs;
		}
		return new Update(true, previous);
	}
}
