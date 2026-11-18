package dev.vy.drt.history;

import dev.vy.drt.config.DungeonRunCompletionRecord;
import dev.vy.drt.config.DungeonRunRecord;
import dev.vy.drt.config.DrtConfig;
import dev.vy.drt.config.DungeonChestOffer;
import java.util.ArrayList;
import java.util.List;

/** Resolves every reward artifact to one persisted run completion identity. */
public final class RunOwnership {
	public static final long FRESH_CROESUS_WINDOW_MS = 3L * 60L * 1000L;
	public static final long CROESUS_HISTORY_WINDOW_MS = 48L * 60L * 60L * 1000L;

	public enum Source {
		NONE,
		LIVE_COMPLETION,
		RECOVERED_RECENT,
		CROESUS_FRESH,
		CROESUS_HISTORICAL
	}

	public record Owner(
		String runSessionId,
		String completionId,
		String floor,
		String grade,
		long completedAtEpochMillis,
		String playerUuid,
		Source source
	) {
		public Owner {
			runSessionId = text(runSessionId);
			completionId = text(completionId);
			floor = text(floor);
			grade = text(grade);
			playerUuid = normalizeUuid(playerUuid);
			source = source == null ? Source.NONE : source;
		}

		public static Owner none() {
			return new Owner("", "", "", "", 0L, "", Source.NONE);
		}

		public static Owner from(DungeonRunCompletionRecord completion, Source source) {
			if (completion == null) return none();
			return new Owner(completion.runSessionId, completion.completionId, completion.floor, completion.grade,
				completion.completedAtEpochMillis, completion.playerUuid, source);
		}

		public boolean valid() {
			return !runSessionId.isBlank() && !completionId.isBlank() && source != Source.NONE;
		}

		public boolean croesus() {
			return source == Source.CROESUS_FRESH || source == Source.CROESUS_HISTORICAL;
		}
	}

	public record Resolution(Owner owner, CroesusRunSelection.Status status, int candidateCount, String reason) {
		public boolean matched() {
			return owner != null && owner.valid();
		}
	}

	private RunOwnership() {
	}

	public static Resolution resolveCroesus(
		CroesusRunSelection.Evidence evidence,
		List<DungeonRunCompletionRecord> completions,
		long selectedAtMillis,
		String currentPlayerUuid
	) {
		CroesusRunSelection.Match strict = CroesusRunSelection.resolve(evidence, completions);
		if (strict.status() == CroesusRunSelection.Status.MATCHED) {
			return new Resolution(Owner.from(strict.completion(), Source.CROESUS_HISTORICAL), strict.status(), 1,
				"unique Croesus completion evidence");
		}
		if (strict.status() == CroesusRunSelection.Status.AMBIGUOUS) {
			return new Resolution(Owner.none(), strict.status(), strict.candidateCount(), "ambiguous historical Croesus evidence");
		}
		if (strict.status() == CroesusRunSelection.Status.NO_MATCH) {
			return new Resolution(Owner.none(), strict.status(), 0, "Croesus evidence did not match a recorded completion");
		}

		List<DungeonRunCompletionRecord> fresh = candidates(
			completions,
			evidence == null ? "" : evidence.floor(),
			selectedAtMillis,
			FRESH_CROESUS_WINDOW_MS,
			currentPlayerUuid
		);
		if (evidence != null) {
			fresh.removeIf(completion -> evidence.score() > 0 && completion.score > 0 && evidence.score() != completion.score);
			fresh.removeIf(completion -> !evidence.grade().isBlank() && completion.grade != null && !completion.grade.isBlank()
				&& !completion.grade.equals("?") && !evidence.grade().equalsIgnoreCase(completion.grade));
		}
		if (fresh.size() == 1) {
			return new Resolution(Owner.from(fresh.getFirst(), Source.CROESUS_FRESH), CroesusRunSelection.Status.MATCHED, 1,
				"unique fresh completion for the same floor and profile");
		}
		CroesusRunSelection.Status status = fresh.isEmpty() ? strict.status() : CroesusRunSelection.Status.AMBIGUOUS;
		return new Resolution(Owner.none(), status, fresh.size(), fresh.isEmpty()
			? "no trustworthy completion matched the Croesus entry"
			: "multiple fresh completions matched the Croesus entry");
	}

	/**
	 * Historical evidence wins. Menu order wins over a fresh-window guess. A fresh completion is
	 * accepted only for the newest visible Croesus row, and only when nothing above identified a run.
	 */
	public static Resolution finishCroesusClick(Resolution direct, Resolution ordered, boolean clickedNewestVisibleRow) {
		if (direct != null && direct.matched() && direct.owner().source() == Source.CROESUS_HISTORICAL) return direct;
		if (ordered != null && ordered.matched()) return ordered;
		if (direct != null && direct.matched() && direct.owner().source() == Source.CROESUS_FRESH && clickedNewestVisibleRow) {
			return direct;
		}
		Resolution basis = direct == null
			? new Resolution(Owner.none(), CroesusRunSelection.Status.INSUFFICIENT_EVIDENCE, 0, "no croesus selection")
			: direct;
		if (basis.matched()) {
			return new Resolution(Owner.none(), CroesusRunSelection.Status.AMBIGUOUS, basis.candidateCount(),
				"clicked Croesus row was not the fresh completion");
		}
		return new Resolution(Owner.none(), basis.status(), basis.candidateCount(), basis.reason());
	}

	public static Resolution resolveRecent(
		List<DungeonRunCompletionRecord> completions,
		String floor,
		long now,
		long maxAgeMillis,
		String currentPlayerUuid
	) {
		List<DungeonRunCompletionRecord> candidates = candidates(completions, floor, now, maxAgeMillis, currentPlayerUuid);
		if (candidates.size() == 1) {
			return new Resolution(Owner.from(candidates.getFirst(), Source.RECOVERED_RECENT), CroesusRunSelection.Status.MATCHED, 1,
				"unique recent persisted completion");
		}
		return new Resolution(Owner.none(), candidates.isEmpty() ? CroesusRunSelection.Status.NO_MATCH : CroesusRunSelection.Status.AMBIGUOUS,
			candidates.size(), candidates.isEmpty() ? "no recent persisted completion" : "multiple recent persisted completions");
	}

	/**
	 * Resolves a visible Croesus row from newest-first order among runs of the clicked floor.
	 * Other floors and older same-floor rows that DRT never recorded do not block the match.
	 * Conflicting score, grade, duration, or timestamp evidence still leaves the click unassigned.
	 */
	public static Resolution resolveCroesusMenuOrder(
		List<CroesusRunSelection.Evidence> visibleRows,
		int selectedRow,
		List<DungeonRunCompletionRecord> completions,
		long selectedAtMillis,
		String currentPlayerUuid
	) {
		if (visibleRows == null || selectedRow < 0 || selectedRow >= visibleRows.size()) {
			return new Resolution(Owner.none(), CroesusRunSelection.Status.INSUFFICIENT_EVIDENCE, 0, "Croesus menu ordering unavailable");
		}
		CroesusRunSelection.Evidence selectedEvidence = visibleRows.get(selectedRow);
		String selectedFloor = selectedEvidence == null ? "" : text(selectedEvidence.floor());
		if (selectedFloor.isBlank()) {
			return new Resolution(Owner.none(), CroesusRunSelection.Status.INSUFFICIENT_EVIDENCE, 0, "clicked Croesus row has no floor");
		}

		List<CroesusRunSelection.Evidence> sameFloorRows = new ArrayList<>();
		int selectedAmongFloor = -1;
		for (int index = 0; index < visibleRows.size(); index++) {
			CroesusRunSelection.Evidence evidence = visibleRows.get(index);
			if (evidence == null || !same(selectedFloor, evidence.floor())) continue;
			if (index == selectedRow) selectedAmongFloor = sameFloorRows.size();
			sameFloorRows.add(evidence);
		}
		if (selectedAmongFloor < 0 || sameFloorRows.isEmpty()) {
			return new Resolution(Owner.none(), CroesusRunSelection.Status.INSUFFICIENT_EVIDENCE, 0, "clicked Croesus row missing from floor ordering");
		}

		String account = normalizeUuid(currentPlayerUuid);
		List<DungeonRunCompletionRecord> floorCompletions = new ArrayList<>();
		for (DungeonRunCompletionRecord completion : completions == null ? List.<DungeonRunCompletionRecord>of() : completions) {
			if (completion == null || text(completion.runSessionId).isBlank() || !same(selectedFloor, completion.floor)) continue;
			long age = selectedAtMillis - completion.completedAtEpochMillis;
			if (age < 0L || age > CROESUS_HISTORY_WINDOW_MS) continue;
			String recordedAccount = normalizeUuid(completion.playerUuid);
			if (!account.isBlank() && !recordedAccount.isBlank() && !account.equals(recordedAccount)) continue;
			floorCompletions.add(completion);
		}
		floorCompletions.sort((left, right) -> Long.compare(right.completedAtEpochMillis, left.completedAtEpochMillis));
		for (int i = 1; i < floorCompletions.size(); i++) {
			if (floorCompletions.get(i - 1).completedAtEpochMillis == floorCompletions.get(i).completedAtEpochMillis) {
				return new Resolution(Owner.none(), CroesusRunSelection.Status.AMBIGUOUS, 2, "duplicate completion timestamps prevent ordered matching");
			}
		}
		if (floorCompletions.isEmpty()) {
			return new Resolution(Owner.none(), CroesusRunSelection.Status.NO_MATCH, 0, "no recorded completions for the clicked Croesus floor");
		}

		// Pair newest Croesus rows of this floor to the newest compatible recorded completions.
		List<DungeonRunCompletionRecord> remaining = new ArrayList<>(floorCompletions);
		List<DungeonRunCompletionRecord> matched = new ArrayList<>();
		for (CroesusRunSelection.Evidence evidence : sameFloorRows) {
			DungeonRunCompletionRecord candidate = null;
			for (DungeonRunCompletionRecord completion : remaining) {
				if (!compatible(evidence, completion)) continue;
				candidate = completion;
				break;
			}
			if (candidate == null) break;
			if (!matched.isEmpty() && candidate.completedAtEpochMillis >= matched.getLast().completedAtEpochMillis) {
				return new Resolution(Owner.none(), CroesusRunSelection.Status.AMBIGUOUS, 2, "Croesus row order conflicts with completion order");
			}
			remaining.remove(candidate);
			matched.add(candidate);
		}
		if (selectedAmongFloor >= matched.size()) {
			return new Resolution(Owner.none(), CroesusRunSelection.Status.NO_MATCH, matched.size(),
				"clicked Croesus row could not be paired with a recorded completion");
		}
		DungeonRunCompletionRecord selected = matched.get(selectedAmongFloor);
		return new Resolution(Owner.from(selected, Source.CROESUS_HISTORICAL), CroesusRunSelection.Status.MATCHED, 1,
			"unique newest-first Croesus menu ordering");
	}

	private static boolean compatible(CroesusRunSelection.Evidence evidence, DungeonRunCompletionRecord completion) {
		if (!text(evidence.grade()).isBlank() && !text(completion.grade).isBlank() && !"?".equals(completion.grade)
			&& !same(evidence.grade(), completion.grade)) return false;
		if (evidence.score() > 0 && completion.score > 0 && evidence.score() != completion.score) return false;
		if (evidence.durationMillis() > 0L && completion.runTimeMs > 0L && evidence.durationMillis() != completion.runTimeMs) return false;
		return evidence.completionWindows().isEmpty()
			|| evidence.completionWindows().stream().anyMatch(window -> window.contains(completion.completedAtEpochMillis));
	}

	/** Safe migration for chests whose runtime owner was lost but whose tracking-instance id survived. */
	public static Owner recoverChestOwner(DungeonRunRecord chest, List<DungeonRunCompletionRecord> completions, long maxAgeMillis) {
		if (chest == null || chest.timestampEpochMillis <= 0L) return Owner.none();
		java.util.Set<String> instances = new java.util.HashSet<>();
		String runInstance = trackingInstance(chest.runSessionId);
		String chestInstance = trackingInstance(chest.chestSessionId);
		if (!runInstance.isBlank() && !text(chest.runSessionId).startsWith("legacy-run-")) instances.add(runInstance);
		if (!chestInstance.isBlank()) instances.add(chestInstance);
		if (instances.isEmpty()) return Owner.none();
		DungeonRunCompletionRecord match = null;
		for (DungeonRunCompletionRecord completion : completions == null ? List.<DungeonRunCompletionRecord>of() : completions) {
			if (completion == null || !instances.contains(trackingInstance(completion.runSessionId))) continue;
			if (!same(chest.floor, completion.floor)) continue;
			long age = chest.timestampEpochMillis - completion.completedAtEpochMillis;
			if (age < 0L || age > maxAgeMillis) continue;
			if (match != null) return Owner.none();
			match = completion;
		}
		return Owner.from(match, Source.RECOVERED_RECENT);
	}

	/** Repairs persisted chest links only when surviving tracking ids identify one completion. */
	public static boolean recoverPersistedChestOwners(DrtConfig config, long maxAgeMillis) {
		if (config == null || config.runHistory == null || config.runCompletions == null) return false;
		boolean changed = false;
		for (DungeonRunRecord record : config.runHistory) {
			if (record == null) continue;
			boolean unlinked = record.linkedRunId == null || record.linkedRunId.isBlank()
				|| DungeonRunRecord.LINK_NONE.equals(record.runLinkSource);
			if (!unlinked) continue;
			Owner owner = recoverChestOwner(record, config.runCompletions, maxAgeMillis);
			if (!owner.valid()) continue;
			record.runSessionId = owner.runSessionId();
			record.linkedRunId = owner.runSessionId();
			record.runLinkSource = DungeonRunRecord.LINK_RECOVERED;
			for (DungeonRunCompletionRecord completion : config.runCompletions) {
				if (completion == null || !owner.runSessionId().equals(completion.runSessionId) || completion.chestOffers == null) continue;
				for (DungeonChestOffer offer : completion.chestOffers) {
					if (offer != null && record.chestTitle != null && record.chestTitle.equalsIgnoreCase(offer.chestTitle)) {
						offer.purchased = true;
					}
				}
			}
			changed = true;
		}
		return changed;
	}

	private static List<DungeonRunCompletionRecord> candidates(
		List<DungeonRunCompletionRecord> completions,
		String floor,
		long atMillis,
		long maxAgeMillis,
		String currentPlayerUuid
	) {
		List<DungeonRunCompletionRecord> candidates = new ArrayList<>();
		String account = normalizeUuid(currentPlayerUuid);
		for (DungeonRunCompletionRecord completion : completions == null ? List.<DungeonRunCompletionRecord>of() : completions) {
			if (completion == null || completion.runSessionId == null || completion.runSessionId.isBlank()) continue;
			if (floor == null || floor.isBlank() || !same(floor, completion.floor)) continue;
			long age = atMillis - completion.completedAtEpochMillis;
			if (age < 0L || age > maxAgeMillis) continue;
			String recordedAccount = normalizeUuid(completion.playerUuid);
			if (!account.isBlank() && !recordedAccount.isBlank() && !account.equals(recordedAccount)) continue;
			candidates.add(completion);
		}
		return candidates;
	}

	private static String trackingInstance(String value) {
		String id = text(value);
		int run = id.lastIndexOf("-run-");
		if (run > 0) return id.substring(0, run);
		int chest = id.lastIndexOf("-chest-");
		return chest > 0 ? id.substring(0, chest) : "";
	}

	private static boolean same(String left, String right) {
		return text(left).equalsIgnoreCase(text(right));
	}

	private static String normalizeUuid(String value) {
		return text(value).replace("-", "").toLowerCase(java.util.Locale.ROOT);
	}

	private static String text(String value) {
		return value == null ? "" : value.trim();
	}
}
