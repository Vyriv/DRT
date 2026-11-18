package dev.vy.drt.client.screen;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SettingsLayoutTest {
	@Test
	void normalSupportedPanelUsesTwoColumnsWithoutScrolling() {
		assertEquals(231, SettingsLayout.columnHeight(5, 2, 2));
		assertEquals(206, SettingsLayout.columnHeight(6, 3));
		assertTrue(SettingsLayout.SECTION_HEADING_TOP_PADDING >= SettingsLayout.SECTION_HEADING_BOTTOM_PADDING);
		assertEquals(21, SettingsLayout.balancedRowHeight(260, 3, 9));
		assertEquals(24, SettingsLayout.balancedRowHeight(260, 2, 9));
		assertEquals(30, SettingsLayout.balancedRowHeight(340, 3, 9));
		int compactRowHeight = SettingsLayout.balancedRowHeight(231, 3, 9);
		assertTrue(SettingsLayout.columnHeightWithRowHeight(compactRowHeight, 5, 2, 2) <= 231);
	}

	@Test
	void headingsAndPanelsUseContentSizedGeometry() {
		assertEquals(7, SettingsLayout.SECTION_HEADING_TOP_PADDING);
		assertEquals(1, SettingsLayout.SECTION_HEADING_BOTTOM_PADDING);
		assertEquals(55, SettingsLayout.sectionHeight(18, 2));
		assertEquals(91, SettingsLayout.sectionHeight(18, 4));
		assertEquals(127, SettingsLayout.sectionHeight(18, 6));
	}

	@Test
	void overlayLayoutRowsFillTheSpaceTheToolbarFrees() {
		// GUI scale 2 at 1080p: 272 px viewport, 30 px toolbar, 6 px gap.
		int rows = SettingsLayout.fillRowHeight(272 - 30 - 6, 6, SettingsLayout.OVERLAY_MAX_ROW_HEIGHT);
		assertEquals(36, rows);
		int used = SettingsLayout.sectionHeight(rows, 6) + 30 + 6;
		assertTrue(used <= 272);
		assertTrue(272 - used < 6, "the layout list leaves no empty band above the toolbar");
		// GUI scale 3 at 1080p: 262 px viewport.
		assertEquals(34, SettingsLayout.fillRowHeight(262 - 30 - 6, 6, SettingsLayout.OVERLAY_MAX_ROW_HEIGHT));
		assertEquals(SettingsLayout.MIN_ROW_HEIGHT, SettingsLayout.fillRowHeight(40, 6, SettingsLayout.OVERLAY_MAX_ROW_HEIGHT));
	}

	@Test
	void settingsRowsFitTheTallerColumnAndFallBackToScrolling() {
		int[] left = {5, 2, 2};
		int[] right = {6, 4};
		// GUI scale 2 and 3 at 1080p, then windowed GUI scale 3.
		assertEquals(22, SettingsLayout.fitRowHeight(272, SettingsLayout.MAX_ROW_HEIGHT, left, right));
		assertEquals(21, SettingsLayout.fitRowHeight(262, SettingsLayout.MAX_ROW_HEIGHT, left, right));
		assertEquals(18, SettingsLayout.fitRowHeight(238, SettingsLayout.MAX_ROW_HEIGHT, left, right));
		assertTrue(SettingsLayout.tallestColumn(18, left, right) <= 238);
		// GUI scale 4: even the minimum row height overflows, so the tab scrolls instead of clipping.
		assertEquals(SettingsLayout.MIN_ROW_HEIGHT, SettingsLayout.fitRowHeight(172, SettingsLayout.MAX_ROW_HEIGHT, left, right));
		assertTrue(SettingsLayout.tallestColumn(SettingsLayout.MIN_ROW_HEIGHT, left, right) > 172);
		assertEquals(SettingsLayout.MAX_ROW_HEIGHT, SettingsLayout.fitRowHeight(2000, SettingsLayout.MAX_ROW_HEIGHT, left, right));
	}

	@Test
	void dropdownRowsStayInsideAvailableHeight() {
		assertEquals(5, SettingsLayout.dropdownVisibleRows(20, 100, 18));
		assertEquals(3, SettingsLayout.dropdownVisibleRows(3, 100, 18));
		assertEquals(1, SettingsLayout.dropdownVisibleRows(4, 10, 18));
		assertEquals(0, SettingsLayout.dropdownVisibleRows(0, 100, 18));
	}
}
