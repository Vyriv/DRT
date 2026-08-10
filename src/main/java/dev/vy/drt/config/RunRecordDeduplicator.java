package dev.vy.drt.config;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

public final class RunRecordDeduplicator {
	private RunRecordDeduplicator() {
	}

	public static DuplicateDecision decide(List<DungeonRunRecord> existingRecords, DungeonRunRecord incoming) {
		if (incoming == null || incoming.lootEntries == null || incoming.lootEntries.isEmpty()) {
			return new DuplicateDecision(RunRecordCommitDecision.KEEP_EXISTING, -1, "empty_incoming");
		}
		if (existingRecords == null || existingRecords.isEmpty()) {
			return new DuplicateDecision(RunRecordCommitDecision.ADD_INCOMING, -1, "no_existing_records");
		}
		for (int i = existingRecords.size() - 1; i >= 0; i--) {
			DungeonRunRecord existing = existingRecords.get(i);
			if (existing == null) continue;
			DuplicateKind duplicateKind = duplicateKind(existing, incoming);
			if (duplicateKind == DuplicateKind.NONE) continue;
			return switch (prefer(existing, incoming, duplicateKind)) {
				case EXISTING -> new DuplicateDecision(RunRecordCommitDecision.KEEP_EXISTING, i, duplicateKind.reason() + "|prefer_existing");
				case INCOMING -> new DuplicateDecision(RunRecordCommitDecision.REPLACE_EXISTING, i, duplicateKind.reason() + "|prefer_incoming");
				case CONFLICT -> new DuplicateDecision(RunRecordCommitDecision.CONFLICT, i, duplicateKind.reason() + "|conflict_keep_existing");
			};
		}
		return new DuplicateDecision(RunRecordCommitDecision.ADD_INCOMING, -1, "no_duplicate_match");
	}

	public static DungeonRunRecord preferredForMigration(DungeonRunRecord left, DungeonRunRecord right) {
		DuplicateKind duplicateKind = duplicateKind(left, right);
		return prefer(left, right, duplicateKind) == Preference.INCOMING ? right : left;
	}

	public static boolean areDuplicateLootRecords(DungeonRunRecord left, DungeonRunRecord right) {
		return duplicateKind(left, right) != DuplicateKind.NONE;
	}

	private static DuplicateKind duplicateKind(DungeonRunRecord left, DungeonRunRecord right) {
		if (left == null || right == null) return DuplicateKind.NONE;
		if (!sameStableId(left.commitFingerprint, right.commitFingerprint)
			&& !sameStableId(left.chestSessionId, right.chestSessionId)
			&& !sameLegacyShape(left, right)) {
			return DuplicateKind.NONE;
		}
		if (sameStableId(left.commitFingerprint, right.commitFingerprint)) return DuplicateKind.COMMIT_FINGERPRINT;
		if (sameStableId(left.chestSessionId, right.chestSessionId)) return DuplicateKind.CHEST_SESSION_ID;
		return DuplicateKind.LEGACY_SHAPE;
	}

	private static boolean sameLegacyShape(DungeonRunRecord left, DungeonRunRecord right) {
		if (Math.abs(left.timestampEpochMillis - right.timestampEpochMillis) > 30_000L) return false;
		if (!sameText(left.floor, right.floor)) return false;
		if (!sameText(left.chestTitle, right.chestTitle)) return false;
		if (left.totalCostCoins() != right.totalCostCoins()) return false;
		return sameLootShape(left, right);
	}

	private static Preference prefer(DungeonRunRecord existing, DungeonRunRecord incoming, DuplicateKind duplicateKind) {
		if (recordsEquivalent(existing, incoming)) return Preference.EXISTING;
		int existingScore = completenessScore(existing);
		int incomingScore = completenessScore(incoming);
		if (duplicateKind == DuplicateKind.CHEST_SESSION_ID
			&& lootCounts(existing).keySet().equals(lootCounts(incoming).keySet())
			&& !sameLootCounts(existing, incoming)
			&& !hasUniformDoubleQuantity(existing, incoming)) {
			return Preference.CONFLICT;
		}
		if (duplicateKind == DuplicateKind.LEGACY_SHAPE && hasUniformDoubleQuantity(existing, incoming)) {
			return totalLootQuantity(existing) <= totalLootQuantity(incoming) ? Preference.EXISTING : Preference.INCOMING;
		}
		if (incomingScore > existingScore) return Preference.INCOMING;
		if (existingScore > incomingScore) return Preference.EXISTING;
		if (sameLootCounts(existing, incoming) && existing.chestValueCoins == incoming.chestValueCoins) return Preference.EXISTING;
		return Preference.CONFLICT;
	}

	private static boolean recordsEquivalent(DungeonRunRecord left, DungeonRunRecord right) {
		return sameText(left.floor, right.floor)
			&& sameText(left.grade, right.grade)
			&& sameText(left.chestTitle, right.chestTitle)
			&& left.totalCostCoins() == right.totalCostCoins()
			&& left.chestValueCoins == right.chestValueCoins
			&& sameLootCounts(left, right);
	}

	private static int completenessScore(DungeonRunRecord record) {
		if (record == null) return 0;
		int score = 0;
		score += Math.max(0, distinctLootCount(record)) * 100;
		score += Math.min(99, totalLootQuantity(record));
		score += resolvedLootCount(record) * 10;
		if (record.chestValueCoins > 0L) score += 5;
		if (record.totalCostCoins() > 0L) score += 3;
		if (record.grade != null && !record.grade.isBlank() && !record.grade.equals("?")) score += 2;
		if (record.floor != null && !record.floor.isBlank() && !record.floor.equalsIgnoreCase("UNKNOWN")) score += 2;
		return score;
	}

	private static boolean sameLootShape(DungeonRunRecord left, DungeonRunRecord right) {
		if (left.lootEntries == null || right.lootEntries == null || left.lootEntries.isEmpty() || right.lootEntries.isEmpty()) return false;
		LinkedHashMap<String, Integer> leftCounts = lootCounts(left);
		LinkedHashMap<String, Integer> rightCounts = lootCounts(right);
		if (!leftCounts.keySet().equals(rightCounts.keySet())) return false;
		if (leftCounts.equals(rightCounts)) return true;
		return hasUniformDoubleQuantity(left, right);
	}

	private static boolean sameLootCounts(DungeonRunRecord left, DungeonRunRecord right) {
		return Objects.equals(lootCounts(left), lootCounts(right));
	}

	private static boolean hasUniformDoubleQuantity(DungeonRunRecord left, DungeonRunRecord right) {
		LinkedHashMap<String, Integer> leftCounts = lootCounts(left);
		LinkedHashMap<String, Integer> rightCounts = lootCounts(right);
		if (!leftCounts.keySet().equals(rightCounts.keySet())) return false;
		Integer multiplier = null;
		for (String key : leftCounts.keySet()) {
			int leftQuantity = Math.max(1, leftCounts.getOrDefault(key, 0));
			int rightQuantity = Math.max(1, rightCounts.getOrDefault(key, 0));
			if (leftQuantity == rightQuantity) continue;
			if (leftQuantity == rightQuantity * 2) {
				if (multiplier != null && multiplier != 2) return false;
				multiplier = 2;
				continue;
			}
			if (rightQuantity == leftQuantity * 2) {
				if (multiplier != null && multiplier != -2) return false;
				multiplier = -2;
				continue;
			}
			return false;
		}
		return multiplier != null;
	}

	private static LinkedHashMap<String, Integer> lootCounts(DungeonRunRecord record) {
		LinkedHashMap<String, Integer> counts = new LinkedHashMap<>();
		if (record == null || record.lootEntries == null) return counts;
		for (DungeonLootEntry entry : record.lootEntries) {
			if (entry == null) continue;
			counts.merge(lootKey(entry), Math.max(1, entry.quantity), Integer::sum);
		}
		return counts;
	}

	private static int distinctLootCount(DungeonRunRecord record) {
		return lootCounts(record).size();
	}

	private static int resolvedLootCount(DungeonRunRecord record) {
		int total = 0;
		if (record == null || record.lootEntries == null) return total;
		for (DungeonLootEntry entry : record.lootEntries) {
			if (entry != null && entry.itemId != null && !entry.itemId.isBlank()) total++;
		}
		return total;
	}

	private static int totalLootQuantity(DungeonRunRecord record) {
		int total = 0;
		if (record == null || record.lootEntries == null) return total;
		for (DungeonLootEntry entry : record.lootEntries) {
			if (entry != null) total += Math.max(1, entry.quantity);
		}
		return total;
	}

	private static String lootKey(DungeonLootEntry entry) {
		if (entry == null) return "";
		String itemId = entry.itemId == null ? "" : entry.itemId.trim().toUpperCase(Locale.ROOT);
		if (!itemId.isBlank()) return "id:" + itemId;
		return "raw:" + (entry.rawName == null ? "" : entry.rawName.trim().toUpperCase(Locale.ROOT));
	}

	private static boolean sameStableId(String left, String right) {
		return left != null && !left.isBlank() && right != null && !right.isBlank() && left.equals(right);
	}

	private static boolean sameText(String left, String right) {
		String leftText = left == null ? "" : left.trim();
		String rightText = right == null ? "" : right.trim();
		return leftText.equalsIgnoreCase(rightText);
	}

	private enum DuplicateKind {
		NONE("none"),
		COMMIT_FINGERPRINT("commit_fingerprint"),
		CHEST_SESSION_ID("chest_session_id"),
		LEGACY_SHAPE("legacy_shape");

		private final String reason;

		DuplicateKind(String reason) {
			this.reason = reason;
		}

		String reason() {
			return reason;
		}
	}

	private enum Preference {
		EXISTING,
		INCOMING,
		CONFLICT
	}

	public record DuplicateDecision(RunRecordCommitDecision action, int existingIndex, String reason) {
	}
}
