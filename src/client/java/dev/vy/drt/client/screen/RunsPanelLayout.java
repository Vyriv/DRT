package dev.vy.drt.client.screen;

final class RunsPanelLayout {
	static final int PARTY_SELECTION_GAP = 5;
	static final int PARTY_ROW_HEIGHT = 13;
	static final int MIN_TEXT_ROW_STEP = 9;
	static final int RUN_ROW_HEIGHT = 30;
	static final int PARTY_LIST_TOP = 15;
	/** Party list and selected member statistics, kept before the run graph grows. */
	static final int PARTY_MIN_HEIGHT = 90;
	/** Below this the run graph is dropped so the party list is not squeezed further. */
	static final int PARTY_FALLBACK_HEIGHT = 48;
	static final int ANALYTICS_MIN_HEIGHT = 56;
	static final int ANALYTICS_PREFERRED_HEIGHT = 60;
	static final int ANALYTICS_MAX_HEIGHT = 86;
	static final int ANALYTICS_GAP = 5;
	static final int PANEL_BOTTOM_PAD = 6;

	private RunsPanelLayout() {}

	/** Height of the run time and grade panel below the party, or 0 when the panel is too short to show both. */
	static int analyticsHeight(int panelH, int partyOffset) {
		int shared = panelH - partyOffset - ANALYTICS_GAP - PANEL_BOTTOM_PAD;
		int preferred = Math.max(ANALYTICS_PREFERRED_HEIGHT, Math.min(ANALYTICS_MAX_HEIGHT, panelH / 4));
		int height = Math.max(ANALYTICS_MIN_HEIGHT, Math.min(preferred, shared - PARTY_MIN_HEIGHT));
		return shared - height >= PARTY_FALLBACK_HEIGHT ? height : 0;
	}

	static int partyHeight(int panelH, int partyOffset, int analyticsH) {
		int gap = analyticsH > 0 ? ANALYTICS_GAP : 0;
		return Math.max(PARTY_LIST_TOP, panelH - partyOffset - analyticsH - gap - PANEL_BOTTOM_PAD);
	}

	/** Party member row spacing that keeps up to five members inside {@code partyH}. */
	static int partyRowStep(int partyH, int members, int lineHeight) {
		int gaps = Math.max(1, Math.min(5, members) - 1);
		return Math.max(MIN_TEXT_ROW_STEP, Math.min(PARTY_ROW_HEIGHT, (partyH - PARTY_LIST_TOP - lineHeight) / gaps));
	}

	/** Spacing for {@code rows} lines of member statistics, including the small gap above Classes, inside {@code h}. */
	static int statsRowStep(int h, int rows, int lineHeight) {
		int gaps = Math.max(1, rows - 1);
		int step = (h - lineHeight - 4) / gaps;
		return Math.max(MIN_TEXT_ROW_STEP, Math.min(PARTY_ROW_HEIGHT, step));
	}

	static String partyMemberText(String name, String dungeonClass, int classLevel) {
		String cleanName = name == null ? "" : name.trim();
		return cleanName + " | " + partyClassText(dungeonClass, classLevel);
	}

	static String partyClassText(String dungeonClass, int classLevel) {
		String cleanClass = dungeonClass == null ? "" : dungeonClass.trim();
		if (cleanClass.isBlank()) return "Class unknown";
		return classLevel > 0 ? cleanClass + " " + classLevel : cleanClass;
	}

	static boolean isKuudra(String floor) {
		return floor != null && floor.trim().toUpperCase(java.util.Locale.ROOT).matches("K[1-5]");
	}

	/** Kuudra has no Catacombs grade; its stored marker only means the completion was detected. */
	static String gradeLabel(String floor, String grade) {
		if (isKuudra(floor)) return "Completed";
		return grade == null || grade.isBlank() ? "?" : grade.trim();
	}

	static boolean showsScore(String floor) {
		return !isKuudra(floor);
	}

	/** Dungeon class and level, or the Kuudra role; Kuudra players never show a Dungeon class. */
	static String partyDetailText(String floor, String dungeonClass, int classLevel, String kuudraRole) {
		if (isKuudra(floor)) return kuudraRole == null ? "" : kuudraRole.trim();
		return partyClassText(dungeonClass, classLevel);
	}

	static int partySelectionArrowX(int textX, int renderedTextWidth) {
		return textX + Math.max(0, renderedTextWidth) + PARTY_SELECTION_GAP;
	}

	static int partyWidth(int availableWidth, int widestMemberText) {
		int maximum = Math.max(150, Math.min(320, availableWidth * 44 / 100));
		return Math.min(maximum, Math.max(150, widestMemberText + 18));
	}

	static int partyListHeight(int members) {
		return 15 + Math.max(0, Math.min(5, members)) * PARTY_ROW_HEIGHT;
	}

	static int classGridColumns(int availableWidth, int gap, int requiredCellWidth) {
		return availableWidth >= Math.max(1, requiredCellWidth) * 2 + Math.max(0, gap) ? 2 : 1;
	}

	static int classGridRows(int items, int columns) {
		int safeColumns = Math.max(1, columns);
		return (Math.max(0, items) + safeColumns - 1) / safeColumns;
	}

	static int wrappedRows(int availableWidth, int gap, int... widths) {
		if (availableWidth <= 0 || widths == null || widths.length == 0) return 0;
		int rows = 1;
		int used = 0;
		for (int width : widths) {
			int itemWidth = Math.max(1, width);
			if (used > 0 && used + gap + itemWidth > availableWidth) {
				rows++;
				used = itemWidth;
			} else {
				used += (used == 0 ? 0 : gap) + itemWidth;
			}
		}
		return rows;
	}

	static int[] fitColumns(int availableWidth, int gap, int... desiredWidths) {
		if (desiredWidths == null || desiredWidths.length == 0) return new int[0];
		int columns = desiredWidths.length;
		int usable = Math.max(columns, availableWidth - Math.max(0, gap) * (columns - 1));
		int[] result = new int[columns];
		int desiredTotal = 0;
		for (int desired : desiredWidths) desiredTotal += Math.max(1, desired);
		if (desiredTotal <= usable) {
			int extra = usable - desiredTotal;
			int assigned = 0;
			for (int i = 0; i < columns - 1; i++) {
				result[i] = Math.max(1, desiredWidths[i]) + extra / columns;
				assigned += result[i];
			}
			result[columns - 1] = usable - assigned;
			return result;
		}
		int assigned = 0;
		for (int i = 0; i < columns - 1; i++) {
			result[i] = Math.max(1, usable * Math.max(1, desiredWidths[i]) / desiredTotal);
			assigned += result[i];
		}
		result[columns - 1] = Math.max(1, usable - assigned);
		return result;
	}
}
