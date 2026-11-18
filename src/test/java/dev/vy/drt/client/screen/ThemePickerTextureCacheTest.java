package dev.vy.drt.client.screen;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ThemePickerTextureCacheTest {
	@Test
	void cachedBackgroundsHaveABoundedPerFrameGuiElementCount() {
		assertEquals(10, ThemePickerTextureCache.CACHED_BACKGROUND_ELEMENTS_PER_FRAME);
		assertTrue(ThemePickerTextureCache.CACHED_BACKGROUND_ELEMENTS_PER_FRAME < 16);
	}

	@Test
	void sliderKeysIgnoreTheirOwnSelectedChannel() {
		int dark = 0xFF101020;
		int light = 0xFF202038;
		assertEquals(
			ThemePickerTextureCache.sliderKey(0, 20, 40, 60, dark, light),
			ThemePickerTextureCache.sliderKey(0, 220, 40, 60, dark, light));
		assertEquals(
			ThemePickerTextureCache.sliderKey(3, 20, 40, 60, dark, light),
			ThemePickerTextureCache.sliderKey(3, 20, 40, 60, dark, light));
		assertNotEquals(
			ThemePickerTextureCache.sliderKey(3, 20, 40, 60, dark, light),
			ThemePickerTextureCache.sliderKey(3, 21, 40, 60, dark, light));
	}
}
