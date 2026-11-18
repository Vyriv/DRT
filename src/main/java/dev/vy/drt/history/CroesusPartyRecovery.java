package dev.vy.drt.history;

import dev.vy.drt.config.DungeonPartyMember;
import dev.vy.drt.config.DungeonRunCompletionRecord;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Matches the parties read from every visible Croesus entry to stored completions. Only exact,
 * unambiguous matches count: the time-of-day fallback used for a single clicked entry is never used
 * here, and two entries that resolve to the same run are both dropped.
 */
public final class CroesusPartyRecovery {
	public record Entry(CroesusRunSelection.Evidence evidence, List<DungeonPartyMember> party) {}

	private CroesusPartyRecovery() {
	}

	/** Party to merge per runSessionId. */
	public static Map<String, List<DungeonPartyMember>> plan(List<Entry> entries, List<DungeonRunCompletionRecord> completions) {
		Map<String, List<DungeonPartyMember>> matched = new LinkedHashMap<>();
		Set<String> conflicted = new HashSet<>();
		for (Entry entry : entries == null ? List.<Entry>of() : entries) {
			if (entry == null || entry.party() == null || entry.party().isEmpty()) continue;
			CroesusRunSelection.Match match = CroesusRunSelection.resolve(entry.evidence(), completions);
			if (match.status() != CroesusRunSelection.Status.MATCHED) continue;
			String runSessionId = match.runSessionId();
			if (conflicted.contains(runSessionId)) continue;
			if (matched.containsKey(runSessionId)) {
				matched.remove(runSessionId);
				conflicted.add(runSessionId);
				continue;
			}
			matched.put(runSessionId, List.copyOf(entry.party()));
		}
		return matched;
	}
}
