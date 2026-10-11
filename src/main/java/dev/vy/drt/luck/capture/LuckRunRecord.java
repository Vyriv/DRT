package dev.vy.drt.luck.capture;

import java.util.ArrayList;
import java.util.List;

/**
 * Luck Rating capture for one completed dungeon run, stored separately from DRT's loot logs and
 * linked to them through the DRT run session and completion fingerprint. Absent observations stay
 * absent: nothing in here is filled in from assumptions.
 */
public final class LuckRunRecord {
	public String luckRunId = "";
	public String drtRunSessionId = "";
	public String drtCompletionFingerprint = "";
	public long completedAtEpochMillis;
	public String floor = "UNKNOWN";
	public String grade = "?";
	public Integer score;
	public String scoreSource;
	public LuckModifierProfile modifiers;
	public List<LuckChestRecord> chests = new ArrayList<>();
	public List<LuckMeterSnapshot> meterSnapshots = new ArrayList<>();
	public LuckCoverage coverage;

	/** One reward chest generated for this run, with every offer DRT has seen for it. */
	public static final class LuckChestRecord {
		public String tier = "";
		public String source = "";
		public List<LuckOfferSnapshot> offers = new ArrayList<>();
		public Boolean purchased;
		public Integer purchasedOfferIndex;
	}

	/** Contents of a chest as observed once. Index 0 is the original offer, 1 the Kismet reroll. */
	public static final class LuckOfferSnapshot {
		public int index;
		public long observedAtEpochMillis;
		public boolean afterKismetReroll;
		public List<String> itemKeys = new ArrayList<>();
		public List<String> rawLines = new ArrayList<>();
	}

	/** RNG Meter state for one floor, tagged with how it was obtained. */
	public static final class LuckMeterSnapshot {
		public String floor = "";
		public String phase = "";
		public String state = LuckCoverage.MeterStatus.UNKNOWN.name();
		public String selectedItem;
		public Long storedScore;
		public Long requiredScore;
		public long observedAtEpochMillis;
	}

	public static final String SCORE_SOURCE_COMPLETION_CHAT = "COMPLETION_CHAT";

	/**
	 * Builds the record for a DRT dungeon completion. The numeric score is only kept when it was
	 * read from the same line as the grade DRT recorded.
	 */
	public static LuckRunRecord forCompletion(
		String drtRunSessionId,
		String drtCompletionFingerprint,
		long completedAtEpochMillis,
		String floor,
		String grade,
		DungeonScoreReading reading
	) {
		LuckRunRecord run = new LuckRunRecord();
		run.drtRunSessionId = drtRunSessionId;
		run.drtCompletionFingerprint = drtCompletionFingerprint;
		run.completedAtEpochMillis = completedAtEpochMillis;
		run.floor = floor;
		run.grade = grade;
		if (reading != null && grade != null && reading.grade().equalsIgnoreCase(grade.trim())) {
			run.score = reading.score();
			run.scoreSource = SCORE_SOURCE_COMPLETION_CHAT;
		}
		run.normalize();
		return run;
	}

	public void normalize() {
		luckRunId = nullToEmpty(luckRunId);
		drtRunSessionId = nullToEmpty(drtRunSessionId);
		drtCompletionFingerprint = nullToEmpty(drtCompletionFingerprint);
		completedAtEpochMillis = Math.max(0L, completedAtEpochMillis);
		floor = floor == null || floor.isBlank() ? "UNKNOWN" : floor.trim();
		grade = grade == null || grade.isBlank() ? "?" : grade.trim();
		if (score != null && (score < 0 || score > DungeonScoreReading.MAX_PLAUSIBLE_SCORE)) {
			score = null;
			scoreSource = null;
		}
		if (modifiers != null) modifiers.normalize();
		if (chests == null) chests = new ArrayList<>();
		chests.removeIf(chest -> chest == null);
		for (LuckChestRecord chest : chests) {
			if (chest.offers == null) chest.offers = new ArrayList<>();
			chest.offers.removeIf(offer -> offer == null);
		}
		if (meterSnapshots == null) meterSnapshots = new ArrayList<>();
		meterSnapshots.removeIf(snapshot -> snapshot == null);
	}

	private static String nullToEmpty(String value) {
		return value == null ? "" : value;
	}
}
