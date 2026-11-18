package dev.vy.drt.config;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

/** Pure, conservative merge of newly observed party details into persisted completion history. */
public final class PartyMemberReconciler {
	public record Result(List<DungeonPartyMember> members, boolean changed) {}

	private PartyMemberReconciler() {}

	public static Result reconcile(List<DungeonPartyMember> existingMembers, List<DungeonPartyMember> incomingMembers) {
		LinkedHashMap<String, DungeonPartyMember> merged = new LinkedHashMap<>();
		if (existingMembers != null) for (DungeonPartyMember member : existingMembers) {
			if (member != null && member.name != null && !member.name.isBlank()) merged.put(key(member), member.copy());
		}
		for (DungeonPartyMember incoming : incomingMembers == null ? List.<DungeonPartyMember>of() : incomingMembers) {
			if (incoming == null || incoming.name == null || incoming.name.isBlank()) continue;
			DungeonPartyMember copy = incoming.copy();
			copy.normalize();
			String incomingKey = key(copy);
			DungeonPartyMember existing = merged.get(incomingKey);
			String existingKey = incomingKey;
			if (existing == null) for (var entry : merged.entrySet()) {
				if (entry.getValue().name.equalsIgnoreCase(copy.name)
					|| !copy.uuid.isBlank() && entry.getValue().uuid.equalsIgnoreCase(copy.uuid)) {
					existing = entry.getValue();
					existingKey = entry.getKey();
					break;
				}
			}
			DungeonPartyMember combined = merge(existing, copy);
			if (!existingKey.equals(incomingKey)) merged.remove(existingKey);
			merged.put(key(combined), combined);
		}
		merged.entrySet().removeIf(entry -> isReplacedClassLevelArtifact(entry.getValue(), incomingMembers));
		List<DungeonPartyMember> result = new ArrayList<>(merged.values());
		return new Result(result, !sameLists(existingMembers, result));
	}

	private static boolean isReplacedClassLevelArtifact(DungeonPartyMember existing, List<DungeonPartyMember> incoming) {
		if (existing == null || !text(existing.uuid).isBlank() || !text(existing.dungeonClass).isBlank() || existing.classLevel != 0
			|| !text(existing.name).matches("\\d{1,3}")) return false;
		long matchingLevels = incoming == null ? 0 : incoming.stream().filter(member -> member != null
			&& !member.dungeonClass.isBlank() && member.classLevel > 0
			&& existing.name.equals(String.valueOf(member.classLevel))).count();
		return matchingLevels == 1;
	}

	// Fill only: a value already recorded for the run is kept; later sources just complete blanks.
	private static DungeonPartyMember merge(DungeonPartyMember existing, DungeonPartyMember incoming) {
		if (existing == null) return incoming;
		boolean classKnown = !text(existing.dungeonClass).isBlank();
		return new DungeonPartyMember(fill(existing.name, incoming.name),
			fill(existing.uuid, incoming.uuid),
			classKnown ? existing.dungeonClass : incoming.dungeonClass,
			existing.classLevel > 0 ? existing.classLevel
				: !classKnown || text(existing.dungeonClass).equalsIgnoreCase(text(incoming.dungeonClass)) ? incoming.classLevel : 0,
			fill(existing.kuudraRole, incoming.kuudraRole));
	}

	private static String fill(String existing, String incoming) {
		return text(existing).isBlank() ? text(incoming) : text(existing);
	}

	private static String text(String value) {
		return value == null ? "" : value.trim();
	}

	private static String key(DungeonPartyMember member) {
		return member.uuid != null && !member.uuid.isBlank() ? "uuid:" + member.uuid.toLowerCase(java.util.Locale.ROOT)
			: "name:" + member.name.toLowerCase(java.util.Locale.ROOT);
	}

	private static boolean sameLists(List<DungeonPartyMember> left, List<DungeonPartyMember> right) {
		List<DungeonPartyMember> a = left == null ? List.of() : left;
		List<DungeonPartyMember> b = right == null ? List.of() : right;
		if (a.size() != b.size()) return false;
		for (int i = 0; i < a.size(); i++) {
			if (a.get(i) == null || b.get(i) == null || !same(a.get(i), b.get(i))) return false;
		}
		return true;
	}

	private static boolean same(DungeonPartyMember left, DungeonPartyMember right) {
		return text(left.name).equalsIgnoreCase(text(right.name)) && text(left.uuid).equalsIgnoreCase(text(right.uuid))
			&& text(left.dungeonClass).equalsIgnoreCase(text(right.dungeonClass)) && left.classLevel == right.classLevel
			&& text(left.kuudraRole).equalsIgnoreCase(text(right.kuudraRole));
	}
}
