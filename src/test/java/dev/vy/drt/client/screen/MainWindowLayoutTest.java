package dev.vy.drt.client.screen;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MainWindowLayoutTest {
	private static final int EDGE = 12;
	/** Title row, two rows of metric cards and the gap above the graph. */
	private static final int OVERVIEW_TOP = 86;
	private static final int OVERVIEW_GAP = 6;
	/** Runs tab title row, one row of floor filters and the metric cards. */
	private static final int RUNS_TOP = 80;
	/** Settings title and description above the two columns. */
	private static final int SETTINGS_TOP = 31;
	private static final int[] SETTINGS_LEFT = {5, 2, 2};
	private static final int[] SETTINGS_RIGHT = {6, 4};

	private static int windowFor(int scaledScreenH) {
		return MainWindowLayout.height(scaledScreenH - EDGE * 2);
	}

	@Test
	void guiScaleTwoWindowIsNoticeablyShorterAndDoesNotGrowWithTheScreen() {
		int oldFullscreen = (int) Math.round(540 * 0.77D);
		assertEquals(416, oldFullscreen);
		assertEquals(346, windowFor(540));
		assertEquals(346, windowFor(504), "windowed 1080p");
		assertEquals(346, windowFor(720), "1440p keeps the same height");
		assertTrue(oldFullscreen - windowFor(540) >= 60);
	}

	@Test
	void shortScreensCapTheWindowInsideTheScreenEdge() {
		assertEquals(336, windowFor(360), "GUI scale 3 at 1080p");
		assertEquals(246, windowFor(270), "GUI scale 4 at 1080p");
		for (int screenH = 40; screenH <= 1200; screenH += 7) {
			assertTrue(windowFor(screenH) <= Math.max(1, screenH - EDGE * 2));
		}
	}

	@Test
	void overviewGraphStaysReadableAndEveryRecentEntryFits() {
		int content = MainWindowLayout.contentHeight(346);
		assertEquals(303, content);
		MainWindowLayout.OverviewSplit split = MainWindowLayout.overviewSplit(content - OVERVIEW_TOP - OVERVIEW_GAP);
		assertEquals(123, split.graphH());
		assertTrue(split.graphH() - 49 >= 70, "plot area keeps room for axes and data");
		assertEquals(MainWindowLayout.OVERVIEW_RECENT_ROWS, MainWindowLayout.overviewActivityRows(split.activityH(), 0));
		assertEquals(4, MainWindowLayout.overviewActivityRows(split.activityH(), 1), "floor performance below its timing line");
		assertEquals(content, OVERVIEW_TOP + split.graphH() + OVERVIEW_GAP + split.activityH());
	}

	@Test
	void overviewNeverOverflowsWhenTheMinimumsFit() {
		for (int screenH = 200; screenH <= 1200; screenH += 5) {
			int available = MainWindowLayout.contentHeight(windowFor(screenH)) - OVERVIEW_TOP - OVERVIEW_GAP;
			MainWindowLayout.OverviewSplit split = MainWindowLayout.overviewSplit(available);
			assertTrue(split.graphH() >= MainWindowLayout.OVERVIEW_MIN_GRAPH_H);
			assertTrue(split.activityH() >= MainWindowLayout.OVERVIEW_MIN_ACTIVITY_H);
			if (available >= MainWindowLayout.OVERVIEW_MIN_GRAPH_H + MainWindowLayout.OVERVIEW_MIN_ACTIVITY_H) {
				assertTrue(split.graphH() + split.activityH() <= available, "screen height " + screenH);
			}
		}
	}

	@Test
	void runsPanelKeepsPartyDetailsAndTheRunGraphAtTheNewHeight() {
		int panel = MainWindowLayout.contentHeight(346) - RUNS_TOP;
		assertEquals(223, panel);
		int analytics = RunsPanelLayout.analyticsHeight(panel, 62);
		int party = RunsPanelLayout.partyHeight(panel, 62, analytics);
		assertEquals(60, analytics);
		assertEquals(90, party);
		assertEquals(13, RunsPanelLayout.partyRowStep(party, 5, 9), "five members keep full spacing");
		assertTrue(RunsPanelLayout.statsRowStep(party - 4, 7, 9) >= 12, "selected member statistics stay readable");
		assertTrue(RunsPanelLayout.statsRowStep(party - 4, 8, 9) >= 10, "wrapped statistics still fit");
	}

	@Test
	void bothSettingsColumnsFitWithoutCrowdingAtTheNewHeight() {
		int viewport = MainWindowLayout.contentHeight(346) - SETTINGS_TOP;
		assertEquals(272, viewport);
		int rowHeight = SettingsLayout.fitRowHeight(viewport, SettingsLayout.MAX_ROW_HEIGHT, SETTINGS_LEFT, SETTINGS_RIGHT);
		assertEquals(22, rowHeight);
		int tallest = SettingsLayout.tallestColumn(rowHeight, SETTINGS_LEFT, SETTINGS_RIGHT);
		assertTrue(tallest <= viewport);
		assertTrue(viewport - tallest < rowHeight, "no large unused area below the columns");
	}
}
