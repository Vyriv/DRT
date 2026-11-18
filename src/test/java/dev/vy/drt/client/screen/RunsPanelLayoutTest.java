package dev.vy.drt.client.screen;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RunsPanelLayoutTest {
	@Test
	void shortSingleMemberLeavesRoomForStatistics() {
		int partyWidth = RunsPanelLayout.partyWidth(500, 82);
		assertEquals(150, partyWidth);
		assertTrue(500 - partyWidth - 16 >= 300);
	}

	@Test
	void longNamesRemainBoundedWithFiveMemberRows() {
		int partyWidth = RunsPanelLayout.partyWidth(500, 190);
		assertEquals(208, partyWidth);
		assertTrue(500 - partyWidth - 16 >= 270);
	}

	@Test
	void partyWidthAccountsForClassLevelAndSelectionIndicator() {
		int fullMemberWidth = 214;
		int partyWidth = RunsPanelLayout.partyWidth(620, fullMemberWidth);
		assertEquals(232, partyWidth);
		assertTrue(620 - partyWidth >= 210);
	}

	@Test
	void partyArrowUsesCleanRenderedTextWidthAndFixedFivePixelGap() {
		assertEquals("catgirlLivid | Mage 41", RunsPanelLayout.partyMemberText("catgirlLivid   ", "  Mage ", 41));
		assertEquals("Class unknown", RunsPanelLayout.partyClassText("", 0));
		assertEquals(152, RunsPanelLayout.partySelectionArrowX(7, 140));
		assertEquals(5, RunsPanelLayout.PARTY_SELECTION_GAP);
		assertEquals(13, RunsPanelLayout.PARTY_ROW_HEIGHT);
		assertEquals(80, RunsPanelLayout.partyListHeight(5));
	}

	@Test
	void runDetailsGiveThePartyPriorityThenDropTheGraphOnTinyPanels() {
		// Wrapped chest summary at the default window height.
		int wrapped = RunsPanelLayout.analyticsHeight(223, 71);
		assertEquals(RunsPanelLayout.ANALYTICS_MIN_HEIGHT, wrapped);
		assertTrue(RunsPanelLayout.partyHeight(223, 71, wrapped) >= RunsPanelLayout.partyListHeight(5));
		// Windowed GUI scale 3 keeps both, with tighter member rows that still show all five.
		int gui3 = RunsPanelLayout.analyticsHeight(189, 62);
		int gui3Party = RunsPanelLayout.partyHeight(189, 62, gui3);
		assertTrue(gui3 > 0);
		int step = RunsPanelLayout.partyRowStep(gui3Party, 5, 9);
		assertTrue(RunsPanelLayout.PARTY_LIST_TOP + step * 4 + 9 <= gui3Party);
		// GUI scale 4 drops the graph rather than overlapping it with the party.
		assertEquals(0, RunsPanelLayout.analyticsHeight(123, 62));
		assertEquals(55, RunsPanelLayout.partyHeight(123, 62, 0));
		// Tall panels cap the graph.
		assertEquals(RunsPanelLayout.ANALYTICS_MAX_HEIGHT, RunsPanelLayout.analyticsHeight(500, 62));
	}

	@Test
	void runListRowsAreCompactButHoldTwoLinesOfText() {
		assertEquals(30, RunsPanelLayout.RUN_ROW_HEIGHT);
		assertTrue(17 + 9 <= RunsPanelLayout.RUN_ROW_HEIGHT - 2, "second line ends inside the row highlight");
	}

	@Test
	void kuudraRunsShowCompletionInsteadOfDungeonScoreAndGrade() {
		assertEquals("Completed", RunsPanelLayout.gradeLabel("K1", "100%"));
		assertEquals("Completed", RunsPanelLayout.gradeLabel("k5", ""));
		assertFalse(RunsPanelLayout.showsScore("K3"));
		assertTrue(RunsPanelLayout.isKuudra("K4"));
	}

	@Test
	void catacombsGradesAndScoresAreUnchanged() {
		assertEquals("S+", RunsPanelLayout.gradeLabel("M7", "S+"));
		assertEquals("A", RunsPanelLayout.gradeLabel("F3", " A "));
		assertEquals("?", RunsPanelLayout.gradeLabel("M5", ""));
		assertTrue(RunsPanelLayout.showsScore("M5"));
		assertTrue(RunsPanelLayout.showsScore("F7"));
		assertFalse(RunsPanelLayout.isKuudra("M1"));
	}

	@Test
	void partyDetailsShowDungeonClassesOrKuudraRoles() {
		assertEquals("Berserk 46", RunsPanelLayout.partyDetailText("M7", "Berserk", 46, ""));
		assertEquals("Tank", RunsPanelLayout.partyDetailText("F1", "Tank", 0, ""));
		assertEquals("Class unknown", RunsPanelLayout.partyDetailText("M5", "", 0, ""));
		assertEquals("Crowd Control", RunsPanelLayout.partyDetailText("K1", "", 0, "Crowd Control"));
		assertEquals("", RunsPanelLayout.partyDetailText("K1", "", 0, ""), "Kuudra players never show Class unknown");
	}

	@Test
	void narrowPanelReservesEnoughWidthForCompleteClassStatistics() {
		int partyWidth = RunsPanelLayout.partyWidth(430, 260);
		assertEquals(189, partyWidth);
		assertEquals(225, 430 - partyWidth - 16);
	}

	@Test
	void filtersWrapWithoutShrinkingButtons() {
		assertEquals(2, RunsPanelLayout.wrappedRows(110, 3, 26, 24, 24, 24, 24));
		assertEquals(1, RunsPanelLayout.wrappedRows(130, 3, 26, 24, 24, 24));
	}

	@Test
	void sharedStatisticColumnsFillThePanelAndKeepStableStarts() {
		int[] widths = RunsPanelLayout.fitColumns(270, 8, 72, 78, 92);
		assertEquals(254, widths[0] + widths[1] + widths[2]);
		assertTrue(widths[2] > widths[0]);
	}

	@Test
	void classColumnsAllocateExtraWidthToClassAverage() {
		int[] widths = RunsPanelLayout.fitColumns(210, 6, 65, 48, 79);
		assertEquals(198, widths[0] + widths[1] + widths[2]);
		assertTrue(widths[2] >= 79);
		assertTrue(widths[2] > widths[1]);
	}

	@Test
	void classGridUsesMeasuredTwoColumnsAndFallsBackWithoutOverlap() {
		assertEquals(2, RunsPanelLayout.classGridColumns(220, 10, 92));
		assertEquals(3, RunsPanelLayout.classGridRows(6, 2));
		assertEquals(1, RunsPanelLayout.classGridColumns(180, 10, 92));
		assertEquals(6, RunsPanelLayout.classGridRows(6, 1));
	}
}
