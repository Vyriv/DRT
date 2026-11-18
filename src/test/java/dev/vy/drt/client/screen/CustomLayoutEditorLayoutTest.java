package dev.vy.drt.client.screen;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CustomLayoutEditorLayoutTest {
	private static final int LINE = 9;
	private static final int WIDE_KEY_H = 97;
	private static final int NARROW_KEY_H = 134;

	@Test
	void widescreenDialogUsesAboutSeventyPercentWidthAndContentHeight() {
		// 1920x1080 at GUI scale 2.
		assertEquals(672, CustomLayoutEditorLayout.dialogWidth(960));
		CustomLayoutEditorLayout.Height height = CustomLayoutEditorLayout.height(540, LINE, WIDE_KEY_H);
		assertEquals(9, height.editorLines());
		assertFalse(height.keyScrolls());
		assertEquals(269, height.winH());
		assertTrue(height.winH() < 540 * 0.6, "a few lines of text no longer fill the screen");
	}

	@Test
	void dialogHeightDoesNotGrowWithTheScreen() {
		assertEquals(CustomLayoutEditorLayout.height(540, LINE, WIDE_KEY_H).winH(), CustomLayoutEditorLayout.height(1080, LINE, WIDE_KEY_H).winH());
		assertEquals(CustomLayoutEditorLayout.MAX_W, CustomLayoutEditorLayout.dialogWidth(1920));
	}

	@Test
	void editorAndPreviewSplitRoughlyFiftyFiveToFortyFive() {
		int content = CustomLayoutEditorLayout.contentWidth(672);
		int editor = CustomLayoutEditorLayout.editorWidth(content);
		int preview = CustomLayoutEditorLayout.previewWidth(content);
		assertEquals(content, editor + preview + CustomLayoutEditorLayout.BOX_GAP);
		assertTrue(editor > preview);
		double share = editor / (double) (editor + preview);
		assertTrue(share > 0.53 && share < 0.57, "editor share " + share);
	}

	@Test
	void shortWindowsShrinkTheEditorThenScrollTheReference() {
		// GUI scale 4 at 1080p: 480x270 scaled, two reference columns.
		assertEquals(420, CustomLayoutEditorLayout.dialogWidth(480));
		CustomLayoutEditorLayout.Height gui4 = CustomLayoutEditorLayout.height(270, LINE, NARROW_KEY_H);
		assertEquals(CustomLayoutEditorLayout.MIN_EDITOR_LINES, gui4.editorLines());
		assertTrue(gui4.keyScrolls());
		assertEquals(270 - CustomLayoutEditorLayout.SCREEN_MARGIN * 2, gui4.winH());

		// 854x480 window at GUI scale 2.
		assertEquals(411, CustomLayoutEditorLayout.dialogWidth(427));
		CustomLayoutEditorLayout.Height small = CustomLayoutEditorLayout.height(240, LINE, NARROW_KEY_H);
		assertTrue(small.winH() <= 240 - CustomLayoutEditorLayout.SCREEN_MARGIN * 2);
		assertTrue(small.keyViewportH() >= (LINE + 1) * 3, "some reference rows always stay reachable");
	}

	@Test
	void largeGuiScaleKeepsTheFullEditorWhenItFits() {
		// GUI scale 3 at 1080p: 640x360.
		assertEquals(448, CustomLayoutEditorLayout.dialogWidth(640));
		CustomLayoutEditorLayout.Height gui3 = CustomLayoutEditorLayout.height(360, LINE, NARROW_KEY_H);
		assertEquals(9, gui3.editorLines());
		assertFalse(gui3.keyScrolls());
		assertTrue(gui3.winH() <= 360 - CustomLayoutEditorLayout.SCREEN_MARGIN * 2);
	}

	@Test
	void veryNarrowScreensNeverExceedTheAvailableWidth() {
		for (int width = 120; width <= 2000; width += 13) {
			int dialog = CustomLayoutEditorLayout.dialogWidth(width);
			assertTrue(dialog <= Math.max(1, width - CustomLayoutEditorLayout.SCREEN_MARGIN * 2));
			assertTrue(dialog <= CustomLayoutEditorLayout.MAX_W);
		}
	}
}
