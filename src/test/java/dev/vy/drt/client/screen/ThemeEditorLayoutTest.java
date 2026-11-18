package dev.vy.drt.client.screen;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ThemeEditorLayoutTest {
	@Test
	void centresPreviewBetweenNavigationAndActionBar() {
		ThemeEditorLayout layout = ThemeEditorLayout.calculate(854, 480);

		assertTrue(layout.previewTop > 20);
		assertTrue(layout.actionTop < 480);
		assertTrue(layout.actionTop - layout.previewTop > 350);
		assertTrue(layout.picker.w() <= 360);
		assertTrue(layout.picker.h() <= 202);
		assertInside(layout.picker, 854, 480);
	}

	@Test
	void clampsDraggedPickerToEveryWindowEdge() {
		ThemeEditorLayout topLeft = ThemeEditorLayout.calculate(640, 360, -500, -500);
		ThemeEditorLayout bottomRight = ThemeEditorLayout.calculate(640, 360, 5000, 5000);

		assertEquals(4, topLeft.picker.x());
		assertEquals(4, topLeft.picker.y());
		assertEquals(640 - bottomRight.picker.w() - 4, bottomRight.picker.x());
		assertEquals(360 - bottomRight.picker.h() - 4, bottomRight.picker.y());
		assertInside(topLeft.titleBar, topLeft.picker);
		assertInside(bottomRight.titleBar, bottomRight.picker);
	}

	@Test
	void keepsCompactPickerAndControlsInsideSmallViewport() {
		ThemeEditorLayout layout = ThemeEditorLayout.calculate(320, 240);

		assertInside(layout.picker, 320, 240);
		assertInside(layout.saturationValue, layout.picker);
		assertInside(layout.hue, layout.picker);
		assertInside(layout.hexField, layout.picker);
		assertInside(layout.alphaField, layout.picker);
		assertInside(layout.propertyButton, layout.picker);
		assertInside(layout.applyButton, layout.picker);
		assertInside(layout.cancelButton, layout.picker);
		assertInside(layout.defaultButton, layout.picker);
		for (ThemeEditorLayout.Rect rect : layout.sliderTracks) assertInside(rect, layout.picker);
		for (ThemeEditorLayout.Rect rect : layout.sliderFields) assertInside(rect, layout.picker);
		for (ThemeEditorLayout.Rect rect : layout.rgbaFields) assertInside(rect, layout.picker);
	}

	@Test
	void popupBoundsProvideACompleteInputCaptureRegion() {
		ThemeEditorLayout layout = ThemeEditorLayout.calculate(640, 360);

		assertTrue(layout.picker.contains(layout.hexField.x() + 1, layout.hexField.y() + 1));
		assertTrue(layout.picker.contains(layout.sliderTracks[3].x() + 1, layout.sliderTracks[3].y() + 1));
		assertTrue(layout.picker.contains(layout.cancelButton.x() + 1, layout.cancelButton.y() + 1));
	}

	private static void assertInside(ThemeEditorLayout.Rect rect, int width, int height) {
		assertTrue(rect.x() >= 0 && rect.y() >= 0);
		assertTrue(rect.x() + rect.w() <= width);
		assertTrue(rect.y() + rect.h() <= height);
	}

	private static void assertInside(ThemeEditorLayout.Rect child, ThemeEditorLayout.Rect parent) {
		assertTrue(child.x() >= parent.x() && child.y() >= parent.y());
		assertTrue(child.x() + child.w() <= parent.x() + parent.w());
		assertTrue(child.y() + child.h() <= parent.y() + parent.h());
	}
}
