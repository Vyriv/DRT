package dev.vy.drt.client.screen;

import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OverlayToolbarLayoutTest {
	// Widths measured with the vanilla font: HUD Overlay + switch, HUD Display + dropdown, Scale + stepper, Move + Reset.
	private static final List<OverlayToolbarLayout.Group> GROUPS = List.of(
		new OverlayToolbarLayout.Group(97, false),
		new OverlayToolbarLayout.Group(141, false),
		new OverlayToolbarLayout.Group(113, false),
		new OverlayToolbarLayout.Group(177, true));

	@Test
	void wideScaledGuiFitsEveryControlOnOneRowWithActionsOnTheRight() {
		// 1920x1080 at GUI scale 2 gives the Overlay tab about 657 scaled pixels inside its panel.
		OverlayToolbarLayout.Result result = OverlayToolbarLayout.arrange(GROUPS, 657);
		assertEquals(1, result.rows());
		assertEquals(OverlayToolbarLayout.ROW_HEIGHT, result.height());
		assertEquals(0, result.placements().get(0).x());
		assertEquals(97 + OverlayToolbarLayout.GROUP_GAP, result.placements().get(1).x());
		assertEquals(657 - 177, result.placements().get(3).x(), "Move and Reset stay grouped at the right edge");
		assertNoOverlap(result, 657);
	}

	@Test
	void largerGuiScalesWrapIntoTwoCompactRows() {
		// GUI scale 4 at 1080p leaves about 422 scaled pixels.
		OverlayToolbarLayout.Result gui4 = OverlayToolbarLayout.arrange(GROUPS, 422);
		assertEquals(2, gui4.rows());
		assertEquals(0, gui4.placements().get(2).row(), "the three settings stay together");
		assertEquals(1, gui4.placements().get(3).row());
		assertNoOverlap(gui4, 422);

		// An 854x480 window at GUI scale 2 leaves about 369.
		OverlayToolbarLayout.Result small = OverlayToolbarLayout.arrange(GROUPS, 369);
		assertEquals(2, small.rows());
		assertEquals(1, small.placements().get(2).row());
		assertEquals(1, small.placements().get(3).row());
		assertNoOverlap(small, 369);
	}

	@Test
	void controlsNeverOverlapOrLeaveThePanelAtAnyWidth() {
		for (int width = 180; width <= 1400; width += 7) {
			OverlayToolbarLayout.Result result = OverlayToolbarLayout.arrange(GROUPS, width);
			assertNoOverlap(result, width);
			assertTrue(result.rows() <= GROUPS.size());
		}
	}

	@Test
	void hudToolbarIsFarShorterThanTheOldThreeRowSection() {
		int oldSection = SettingsLayout.sectionHeight(32, 3);
		int toolbar = SettingsLayout.SECTION_BOTTOM_PADDING * 2 + OverlayToolbarLayout.arrange(GROUPS, 657).height();
		assertEquals(115, oldSection);
		assertEquals(30, toolbar);
	}

	private static void assertNoOverlap(OverlayToolbarLayout.Result result, int width) {
		List<OverlayToolbarLayout.Placement> placements = result.placements();
		for (int i = 0; i < placements.size(); i++) {
			OverlayToolbarLayout.Placement a = placements.get(i);
			assertTrue(a.x() >= 0 && a.x() + a.width() <= width, "group " + i + " stays inside " + width);
			for (int j = i + 1; j < placements.size(); j++) {
				OverlayToolbarLayout.Placement b = placements.get(j);
				if (a.row() != b.row()) continue;
				boolean separate = a.x() + a.width() + OverlayToolbarLayout.GROUP_GAP <= b.x()
					|| b.x() + b.width() + OverlayToolbarLayout.GROUP_GAP <= a.x();
				assertTrue(separate || width < GROUPS.get(i).width() + GROUPS.get(j).width(), "groups " + i + " and " + j + " overlap at " + width);
			}
		}
	}
}
