package dev.vy.drt.history;

import dev.vy.drt.config.DungeonRunCompletionRecord;
import dev.vy.drt.config.DungeonRunRecord;
import dev.vy.drt.config.ManualRecord;
import java.util.List;
import java.util.Map;

/** Which history entries the player created or edited. They stay in local history but never go online. */
public final class ManualRecords {
	public static final String MARKER = "*";

	private ManualRecords() {
	}

	public static boolean isManual(DungeonRunRecord chest, RunHistoryLinks.Link link) {
		if (chest == null) return false;
		return chest.manual != null || (link != null && link.source() == RunHistoryLinks.Source.MANUAL);
	}

	/** "#190 Gold" becomes "#190 Gold*" for a manual entry. */
	public static String mark(String label, boolean manual) {
		String text = label == null ? "" : label;
		return manual ? text + MARKER : text;
	}

	/** Tooltip line explaining the marker, or null for a DRT-recorded entry. */
	public static String tooltip(DungeonRunRecord chest, RunHistoryLinks.Link link) {
		if (!isManual(chest, link)) return null;
		String what = ManualRecord.EDITED.equals(chest.manual) ? "Manually edited" : "Manually created";
		return MARKER + " " + what + ". Not included in online statistics.";
	}

	/**
	 * Stores the created marker on manual runs and chests saved before the marker existed, so it
	 * stays even if the link that proves them manual changes later.
	 *
	 * @return true when a record changed
	 */
	public static boolean markLegacy(List<DungeonRunRecord> history, List<DungeonRunCompletionRecord> completions) {
		boolean changed = false;
		for (DungeonRunCompletionRecord completion : completions == null ? List.<DungeonRunCompletionRecord>of() : completions) {
			if (completion == null || completion.manual != null || !completion.isManual()) continue;
			completion.manual = ManualRecord.CREATED;
			changed = true;
		}
		Map<DungeonRunRecord, RunHistoryLinks.Link> links = RunHistoryLinks.resolve(history, completions);
		for (DungeonRunRecord chest : history == null ? List.<DungeonRunRecord>of() : history) {
			if (chest == null || chest.manual != null || !isManual(chest, links.get(chest))) continue;
			chest.manual = ManualRecord.CREATED;
			changed = true;
		}
		return changed;
	}
}
