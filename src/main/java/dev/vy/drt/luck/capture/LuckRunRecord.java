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
	/** Dungeon duration from the completion; null when unknown. Independent of the completion time. */
	public Long durationMillis;
	/** A {@code DungeonRunCompletionRecord.RUN_TIME_} value, set together with {@link #durationMillis}. */
	public String durationSource;
	public LuckModifierProfile modifiers;
	/** Party members read from the tab list at completion; null when not read. */
	public List<String> party;
	public String partySource;
	public LuckCroesusLink croesus;
	public List<LuckChestRecord> chests = new ArrayList<>();
	public List<LuckMeterSnapshot> meterSnapshots = new ArrayList<>();
	public LuckCoverage coverage;

	/** One reward chest generated for this run, with every offer DRT has seen for it. */
	public static final class LuckChestRecord {
		public String tier = "";
		public String source = "";
		public List<LuckOfferSnapshot> offers = new ArrayList<>();
		public Boolean purchased;
		/** Where {@link #purchased} was last proven; {@link #SOURCE_DUNGEON_CHEST} for an open DRT saw in the instance. */
		public String purchaseSource;
		public Integer purchasedOfferIndex;
		/** Coin cost as listed, 0 for FREE; null when not observed. */
		public Long costCoins;
		public String costText;
	}

	/**
	 * Contents of a chest as observed once. Offers are appended in the order seen. A later offer
	 * with different contents is flagged {@code changedFromPrevious}; {@code afterKismetReroll} is
	 * only set once a reroll has been confirmed in game.
	 */
	public static final class LuckOfferSnapshot {
		public int index;
		public long observedAtEpochMillis;
		public boolean afterKismetReroll;
		public boolean changedFromPrevious;
		public String source;
		public List<String> itemKeys = new ArrayList<>();
		public List<LuckContentLine> contents = new ArrayList<>();
		public List<String> rawLines = new ArrayList<>();
	}

	/** One Contents line; {@code key} is null when the line could not be named without guessing. */
	public static final class LuckContentLine {
		public String text = "";
		public String name = "";
		public int quantity = 1;
		public String kind;
		public String key;
	}

	/** How this run was linked to a Croesus entry, and what that entry last showed. */
	public static final class LuckCroesusLink {
		public long firstMatchedAtEpochMillis;
		public long lastSeenAtEpochMillis;
		public String method = "";
		public String entryName = "";
		public List<String> players = new ArrayList<>();
		public String completedText;
		public String expiresText;
		public boolean noChestsOpened;
		public List<String> openedChests = new ArrayList<>();
		public List<String> availableModifiers = new ArrayList<>();
		/** Each change in the listed modifiers, oldest first. */
		public List<LuckModifierObservation> modifierHistory = new ArrayList<>();
	}

	public static final class LuckModifierObservation {
		public long observedAtEpochMillis;
		public String source = "";
		public List<String> modifiers = new ArrayList<>();
	}

	/** RNG Meter state for one floor, tagged with how it was obtained. */
	public static final class LuckMeterSnapshot {
		public String floor = "";
		public String phase = "";
		public String state = LuckCoverage.MeterStatus.UNKNOWN.name();
		public String selectedItem;
		public String selectedKey;
		public Long storedScore;
		public String storedText;
		public Long requiredScore;
		public String requiredText;
		public String guaranteeChest;
		public String source;
		public long observedAtEpochMillis;
	}

	public static final String SCORE_SOURCE_COMPLETION_CHAT = "COMPLETION_CHAT";
	public static final String PHASE_BEFORE_RUN = "BEFORE_RUN";
	public static final String PHASE_AFTER_RUN = "AFTER_RUN";
	public static final String SOURCE_CROESUS_RUN_VIEW = "CROESUS_RUN_VIEW";
	public static final String SOURCE_CROESUS_MENU = "CROESUS_MENU";
	/** A reward chest screen in the dungeon instance, right after the run's completion. */
	public static final String SOURCE_DUNGEON_CHEST = "DUNGEON_CHEST";
	/** A chest opened from a Croesus run view linked to this run, once its rewards were received. */
	public static final String SOURCE_CROESUS_CHEST = "CROESUS_CHEST";
	public static final String PARTY_SOURCE_TAB_LIST = "TAB_LIST";

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
		if (durationMillis != null && durationMillis <= 0L) durationMillis = null;
		if (durationMillis == null) durationSource = null;
		if (modifiers != null) modifiers.normalize();
		if (chests == null) chests = new ArrayList<>();
		chests.removeIf(chest -> chest == null);
		for (LuckChestRecord chest : chests) {
			if (chest.offers == null) chest.offers = new ArrayList<>();
			chest.offers.removeIf(offer -> offer == null);
			for (LuckOfferSnapshot offer : chest.offers) {
				if (offer.itemKeys == null) offer.itemKeys = new ArrayList<>();
				if (offer.contents == null) offer.contents = new ArrayList<>();
				offer.contents.removeIf(line -> line == null);
				if (offer.rawLines == null) offer.rawLines = new ArrayList<>();
			}
			if (chest.costCoins != null && chest.costCoins < 0) chest.costCoins = null;
		}
		if (party != null) {
			party = new ArrayList<>(party);
			party.removeIf(name -> name == null || name.isBlank());
			if (party.isEmpty()) {
				party = null;
				partySource = null;
			}
		}
		if (croesus != null) {
			if (croesus.players == null) croesus.players = new ArrayList<>();
			if (croesus.openedChests == null) croesus.openedChests = new ArrayList<>();
			if (croesus.availableModifiers == null) croesus.availableModifiers = new ArrayList<>();
			if (croesus.modifierHistory == null) croesus.modifierHistory = new ArrayList<>();
			croesus.modifierHistory.removeIf(observation -> observation == null);
		}
		if (meterSnapshots == null) meterSnapshots = new ArrayList<>();
		meterSnapshots.removeIf(snapshot -> snapshot == null);
	}

	private static String nullToEmpty(String value) {
		return value == null ? "" : value;
	}
}
