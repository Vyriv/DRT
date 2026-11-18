package dev.vy.drt.client.screen;

final class SettingsLayout {
	static final int MIN_ROW_HEIGHT = 18;
	static final int MAX_ROW_HEIGHT = 32;
	static final int SECTION_HEADING_TOP_PADDING = 7;
	static final int SECTION_HEADING_TEXT_HEIGHT = 9;
	static final int SECTION_HEADING_BOTTOM_PADDING = 1;
	static final int SECTION_HEIGHT = SECTION_HEADING_TOP_PADDING + SECTION_HEADING_TEXT_HEIGHT + SECTION_HEADING_BOTTOM_PADDING;
	static final int SECTION_BOTTOM_PADDING = 2;
	static final int SECTION_GAP = 6;
	static final int OVERLAY_MAX_ROW_HEIGHT = 36;

	private SettingsLayout() {
	}

	static int columnHeight(int... rowsPerSection) {
		return columnHeightWithRowHeight(MIN_ROW_HEIGHT, rowsPerSection);
	}

	static int columnHeightWithRowHeight(int rowHeight, int... rowsPerSection) {
		if (rowsPerSection == null || rowsPerSection.length == 0) return 0;
		int height = (rowsPerSection.length - 1) * SECTION_GAP;
		for (int rows : rowsPerSection) {
			height += sectionHeight(rowHeight, rows);
		}
		return height;
	}

	static int sectionHeight(int rowHeight, int rows) {
		return SECTION_HEIGHT + Math.max(0, rows) * Math.max(1, rowHeight) + SECTION_BOTTOM_PADDING;
	}

	static int balancedRowHeight(int availableHeight) {
		return balancedRowHeight(availableHeight, 3, 10);
	}

	static int balancedRowHeight(int availableHeight, int sections, int rows) {
		int safeSections = Math.max(1, sections);
		int safeRows = Math.max(1, rows);
		int fixed = (SECTION_HEIGHT + SECTION_BOTTOM_PADDING) * safeSections
			+ (safeSections - 1) * SECTION_GAP;
		return Math.max(MIN_ROW_HEIGHT, Math.min(MAX_ROW_HEIGHT, (availableHeight - fixed) / safeRows));
	}

	/**
	 * Tallest shared row height at which every column, given as rows per section, fits {@code availableHeight}.
	 * Columns that cannot fit even at the minimum keep the minimum and the caller scrolls.
	 */
	static int fitRowHeight(int availableHeight, int maxRowHeight, int[]... columns) {
		for (int rowHeight = Math.max(MIN_ROW_HEIGHT, maxRowHeight); rowHeight > MIN_ROW_HEIGHT; rowHeight--) {
			if (tallestColumn(rowHeight, columns) <= availableHeight) return rowHeight;
		}
		return MIN_ROW_HEIGHT;
	}

	static int tallestColumn(int rowHeight, int[]... columns) {
		int tallest = 0;
		for (int[] column : columns) tallest = Math.max(tallest, columnHeightWithRowHeight(rowHeight, column));
		return tallest;
	}

	/**
	 * Row height for a single section that fills {@code availableHeight}. Overlay layout rows may grow a little past
	 * the shared settings maximum so the page stays balanced once the HUD controls collapse into a toolbar.
	 */
	static int fillRowHeight(int availableHeight, int rows, int maxRowHeight) {
		int safeRows = Math.max(1, rows);
		int fill = (availableHeight - SECTION_HEIGHT - SECTION_BOTTOM_PADDING) / safeRows;
		return Math.max(MIN_ROW_HEIGHT, Math.min(Math.max(MIN_ROW_HEIGHT, maxRowHeight), fill));
	}

	static int dropdownVisibleRows(int optionCount, int availableHeight, int rowHeight) {
		if (optionCount <= 0) return 0;
		int visibleCapacity = Math.max(1, (Math.max(0, availableHeight) - 2) / Math.max(1, rowHeight));
		return Math.min(optionCount, visibleCapacity);
	}
}
