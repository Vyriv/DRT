package dev.vy.drt.history;

import dev.vy.drt.config.DungeonChestOffer;
import dev.vy.drt.config.DungeonLootEntry;
import dev.vy.drt.config.DungeonRunCompletionRecord;
import dev.vy.drt.config.DungeonRunRecord;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Projects persisted history into Loot Log rows.
 * Each purchased chest gets its own list row so siblings from one run never hide each other.
 * Offer-only runs stay one row. Shared completion identity remains available via {@link #runKey()}.
 */
public final class LootHistoryProjection {
	public static final String OFFER_VIEW_PREFIX = "offer-view|";
	private static final List<String> CHEST_ORDER = List.of("WOOD", "GOLD", "DIAMOND", "EMERALD", "OBSIDIAN", "BEDROCK");

	public record ChestOption(DungeonRunRecord record, boolean purchased) {
		public String chestTitle() {
			return record == null || record.chestTitle == null ? "" : record.chestTitle;
		}

		public String optionId() {
			if (record == null) return "";
			if (record.recordId != null && !record.recordId.isBlank()) return record.recordId;
			if (record.commitFingerprint != null && !record.commitFingerprint.isBlank()) return record.commitFingerprint;
			return chestTitle() + "|" + record.timestampEpochMillis + "|" + record.chestNumber;
		}
	}

	public record RunEntry(String key, String runKey, DungeonRunRecord representative, DungeonRunCompletionRecord completion,
		List<ChestOption> chestOptions, List<DungeonRunRecord> realisedRecords) {
		public RunEntry {
			chestOptions = List.copyOf(chestOptions == null ? List.of() : chestOptions);
			realisedRecords = List.copyOf(realisedRecords == null ? List.of() : realisedRecords);
			runKey = runKey == null || runKey.isBlank() ? key : runKey;
		}

		/** Back-compat constructor used by older tests and callers. */
		public RunEntry(String key, DungeonRunRecord representative, DungeonRunCompletionRecord completion,
			List<ChestOption> chestOptions, List<DungeonRunRecord> realisedRecords) {
			this(key, key, representative, completion, chestOptions, realisedRecords);
		}

		public ChestOption defaultOption() {
			for (ChestOption option : chestOptions) {
				if (option.purchased() && sameRecord(option.record(), representative)) return option;
			}
			for (ChestOption option : chestOptions) if (option.purchased()) return option;
			return chestOptions.isEmpty() ? null : chestOptions.getFirst();
		}

		public ChestOption optionById(String key) {
			if (key == null || key.isBlank()) return null;
			for (ChestOption option : chestOptions) if (option.optionId().equals(key)) return option;
			for (ChestOption option : chestOptions) if (option.chestTitle().equalsIgnoreCase(key)) return option;
			return null;
		}

		public ChestOption option(String key) {
			ChestOption found = optionById(key);
			return found != null ? found : defaultOption();
		}

		public boolean hasPurchasedChest() {
			return !realisedRecords.isEmpty();
		}

		private static boolean sameRecord(DungeonRunRecord left, DungeonRunRecord right) {
			if (left == null || right == null) return false;
			if (left == right) return true;
			if (left.recordId != null && !left.recordId.isBlank() && left.recordId.equals(right.recordId)) return true;
			return left.chestNumber > 0 && left.chestNumber == right.chestNumber
				&& left.timestampEpochMillis == right.timestampEpochMillis
				&& normalizedTitle(left.chestTitle).equals(normalizedTitle(right.chestTitle));
		}

		private static String normalizedTitle(String title) {
			return title == null ? "" : title.trim().toUpperCase(Locale.ROOT);
		}
	}

	private LootHistoryProjection() {
	}

	public static List<RunEntry> projectRuns(List<DungeonRunRecord> purchasedHistory,
		List<DungeonRunCompletionRecord> completions) {
		List<DungeonRunRecord> purchased = purchasedHistory == null ? List.of() : purchasedHistory;
		List<DungeonRunCompletionRecord> completionList = completions == null ? List.of() : completions;
		Map<String, DungeonRunCompletionRecord> completionByRun = new LinkedHashMap<>();
		for (DungeonRunCompletionRecord completion : completionList) {
			if (completion != null && completion.runSessionId != null && !completion.runSessionId.isBlank()) {
				completionByRun.putIfAbsent(completion.runSessionId, completion);
			}
		}

		Map<String, Builder> builders = new LinkedHashMap<>();
		int unlinkedIndex = 0;
		for (DungeonRunRecord record : purchased) {
			if (record == null) continue;
			String runId = linkedRunId(record, completionByRun);
			DungeonRunCompletionRecord owner = completionByRun.get(runId);
			String runKey = runId.isBlank() ? "record:" + stableRecordId(record, unlinkedIndex++) : completionKey(owner);
			builders.computeIfAbsent(runKey, ignored -> new Builder(runKey, completionByRun.get(runId))).addRealised(record);
		}

		for (DungeonRunCompletionRecord completion : completionList) {
			if (completion == null || completion.runSessionId == null || completion.runSessionId.isBlank()
				|| completion.chestOffers == null) continue;
			String runKey = completionKey(completion);
			Builder builder = builders.get(runKey);
			for (DungeonChestOffer offer : completion.chestOffers) {
				if (offer == null || offer.lootEntries == null || offer.lootEntries.isEmpty()) continue;
				if (builder == null) {
					builder = new Builder(runKey, completion);
					builders.put(runKey, builder);
				}
				builder.addOffer(completion, offer);
			}
		}

		List<RunEntry> entries = new ArrayList<>();
		for (Builder builder : builders.values()) {
			entries.addAll(builder.buildRows());
		}
		entries.sort(Comparator.comparingInt((RunEntry entry) -> entry.representative().chestNumber).reversed()
			.thenComparing(Comparator.comparingLong((RunEntry entry) -> entry.representative().timestampEpochMillis).reversed()));
		return entries;
	}

	/** UI identity follows the persisted completion ID, with a session fallback only for unmigrated data. */
	public static String completionKey(DungeonRunCompletionRecord completion) {
		if (completion == null) return "";
		return completion.completionId != null && !completion.completionId.isBlank()
			? "completion:" + completion.completionId
			: "run:" + completion.runSessionId;
	}

	public static boolean isOfferRecord(DungeonRunRecord record) {
		return record != null && record.commitFingerprint != null && record.commitFingerprint.startsWith(OFFER_VIEW_PREFIX);
	}

	private static String linkedRunId(DungeonRunRecord record, Map<String, DungeonRunCompletionRecord> completions) {
		if (record.linkedRunId != null && completions.containsKey(record.linkedRunId)) return record.linkedRunId;
		if (record.runSessionId != null && completions.containsKey(record.runSessionId)) return record.runSessionId;
		return "";
	}

	private static String stableRecordId(DungeonRunRecord record, int index) {
		if (record.recordId != null && !record.recordId.isBlank()) return record.recordId;
		if (record.commitFingerprint != null && !record.commitFingerprint.isBlank()) return record.commitFingerprint;
		return index + ":" + record.timestampEpochMillis + ":" + record.chestTitle;
	}

	private static DungeonRunRecord offerViewRecord(DungeonRunCompletionRecord completion, DungeonChestOffer offer) {
		long observedAt = offer.observedAtEpochMillis > 0L ? offer.observedAtEpochMillis : completion.completedAtEpochMillis;
		long availableCost = offer.costBreakdown == null ? 0L : offer.costBreakdown.totalCostCoins();
		DungeonRunRecord record = new DungeonRunRecord(observedAt, 0, 0, completion.floor, completion.grade,
			offer.chestTitle, availableCost, offer.valueCoins, offer.valueCoins - availableCost, offer.lootEntries);
		record.runSessionId = completion.runSessionId;
		record.linkedRunId = completion.runSessionId;
		record.runLinkSource = "OBSERVED_OFFER";
		record.commitFingerprint = OFFER_VIEW_PREFIX + completion.completionId + "|" + offer.chestTitle;
		if (DungeonChestOffer.SOURCE_API_RECOVERED.equals(offer.captureSource)) record.financialsKnown = false;
		return record;
	}

	private static int chestRank(String title) {
		String normalized = title == null ? "" : title.toUpperCase(Locale.ROOT);
		for (int i = 0; i < CHEST_ORDER.size(); i++) if (normalized.contains(CHEST_ORDER.get(i))) return i;
		return CHEST_ORDER.size();
	}

	private static final class Builder {
		private final String runKey;
		private final DungeonRunCompletionRecord completion;
		private final List<DungeonRunRecord> realised = new ArrayList<>();
		private final Map<String, ChestOption> options = new LinkedHashMap<>();

		private Builder(String runKey, DungeonRunCompletionRecord completion) {
			this.runKey = runKey;
			this.completion = completion;
		}

		private void addRealised(DungeonRunRecord record) {
			realised.add(record);
			options.put("opened:" + realised.size() + ":" + normalizedTitle(record.chestTitle), new ChestOption(record, true));
		}

		private void addOffer(DungeonRunCompletionRecord owner, DungeonChestOffer offer) {
			String title = normalizedTitle(offer.chestTitle);
			for (DungeonRunRecord record : realised) {
				if (normalizedTitle(record.chestTitle).equals(title) && sameLoot(record, offer)) return;
			}
			String key = "offer:" + title;
			if (options.containsKey(key)) return;
			options.put(key, new ChestOption(offerViewRecord(owner, offer), offer.purchased));
		}

		private static boolean sameLoot(DungeonRunRecord record, DungeonChestOffer offer) {
			List<DungeonLootEntry> left = record == null || record.lootEntries == null ? List.of() : record.lootEntries;
			List<DungeonLootEntry> right = offer == null || offer.lootEntries == null ? List.of() : offer.lootEntries;
			if (left.size() != right.size()) return false;
			Map<String, Integer> counts = new LinkedHashMap<>();
			for (DungeonLootEntry entry : left) addLootCount(counts, entry, 1);
			for (DungeonLootEntry entry : right) addLootCount(counts, entry, -1);
			for (int count : counts.values()) if (count != 0) return false;
			return true;
		}

		private static void addLootCount(Map<String, Integer> counts, DungeonLootEntry entry, int sign) {
			if (entry == null) return;
			String id = entry.itemId == null ? "" : entry.itemId.trim().toUpperCase(Locale.ROOT);
			String key = (id.isBlank() ? String.valueOf(entry.rawName) : id) + "#" + entry.quantity;
			counts.put(key, counts.getOrDefault(key, 0) + sign);
		}

		private List<RunEntry> buildRows() {
			if (options.isEmpty()) return List.of();
			List<ChestOption> allOptions = new ArrayList<>(options.values());
			allOptions.sort(Comparator.comparingInt(option -> chestRank(option.chestTitle())));
			List<ChestOption> offerOnly = allOptions.stream().filter(option -> !option.purchased()).toList();
			if (realised.isEmpty()) {
				DungeonRunRecord representative = allOptions.getFirst().record();
				return representative == null ? List.of()
					: List.of(new RunEntry(runKey, runKey, representative, completion, allOptions, List.of()));
			}
			List<RunEntry> rows = new ArrayList<>();
			int index = 0;
			for (DungeonRunRecord record : realised) {
				if (record == null) continue;
				List<ChestOption> rowOptions = new ArrayList<>();
				rowOptions.add(new ChestOption(record, true));
				rowOptions.addAll(offerOnly);
				rowOptions.sort(Comparator.comparingInt(option -> chestRank(option.chestTitle())));
				String chestKey = "chest:" + stableRecordId(record, index++);
				rows.add(new RunEntry(chestKey, runKey, record, completion, rowOptions, List.of(record)));
			}
			return rows;
		}

		private static String normalizedTitle(String title) {
			return title == null ? "" : title.trim().toUpperCase(Locale.ROOT);
		}
	}
}
