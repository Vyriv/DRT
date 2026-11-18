package dev.vy.drt.client.screen;

/**
 * Vertical budget for the main DRT window in scaled GUI pixels. The height comes from what the tabs need (Runs party
 * details with the run graph below, and both Settings columns) instead of a share of the screen, so every tab shares
 * one height and switching tabs never resizes the window. Shorter screens cap it and each tab falls back to its own
 * compact sizing or scrolling.
 */
final class MainWindowLayout {
	static final int HEIGHT = 346;
	/** Nav bar padding, the bar itself and the gap below it. */
	static final int CONTENT_TOP = 33;
	static final int BOTTOM_MARGIN = 10;

	static final int OVERVIEW_ROW_STEP = 13;
	static final int OVERVIEW_ACTIVITY_HEADER = 20;
	static final int OVERVIEW_RECENT_ROWS = 5;
	static final int OVERVIEW_MIN_GRAPH_H = 60;
	static final int OVERVIEW_MIN_ACTIVITY_H = 36;
	static final double OVERVIEW_MAX_GRAPH_SHARE = 0.62D;

	record OverviewSplit(int graphH, int activityH) {
	}

	private MainWindowLayout() {
	}

	static int height(int availableH) {
		return Math.max(1, Math.min(availableH, HEIGHT));
	}

	static int contentHeight(int winH) {
		return Math.max(0, winH - CONTENT_TOP - BOTTOM_MARGIN);
	}

	/** Activity panel height that shows {@code rows} entries under its heading. */
	static int overviewActivityHeight(int rows) {
		return OVERVIEW_ACTIVITY_HEADER + Math.max(0, rows) * OVERVIEW_ROW_STEP + 3;
	}

	/** Entries that fit in an activity panel after {@code leadingRows} rows of text below its heading. */
	static int overviewActivityRows(int activityH, int leadingRows) {
		return Math.max(0, (activityH - OVERVIEW_ACTIVITY_HEADER - 3) / OVERVIEW_ROW_STEP - Math.max(0, leadingRows));
	}

	/**
	 * Splits the space below the Overview cards. The activity panels keep room for every recent run and chest and the
	 * graph takes the rest; when space is short the activity panels give way first, then the graph.
	 */
	static OverviewSplit overviewSplit(int available) {
		int preferredActivity = overviewActivityHeight(OVERVIEW_RECENT_ROWS);
		int graph = Math.max(OVERVIEW_MIN_GRAPH_H,
			Math.min(available - preferredActivity, (int) Math.round(available * OVERVIEW_MAX_GRAPH_SHARE)));
		int activity = Math.max(OVERVIEW_MIN_ACTIVITY_H, available - graph);
		if (graph + activity > available) graph = Math.max(OVERVIEW_MIN_GRAPH_H, available - activity);
		return new OverviewSplit(graph, activity);
	}
}
