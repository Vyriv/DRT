package dev.vy.drt.client.screen;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ThemeColorPickerModelTest {
	@Test
	void convertsArgbAndKeepsRgbAndAlphaRepresentationsSynchronized() {
		ThemeColorPickerModel model = new ThemeColorPickerModel(0xB48040C8);

		assertEquals(128, model.red());
		assertEquals(64, model.green());
		assertEquals(200, model.blue());
		assertEquals(180, model.alpha());
		assertEquals("#8040C8", model.rgbHex());
		assertEquals("#B48040C8", model.argbHex());
	}

	@Test
	void rgbHexPreservesAlphaAndInvalidTextPreservesColour() {
		ThemeColorPickerModel model = new ThemeColorPickerModel(0x7F102030);

		assertTrue(model.trySetRgbHex("#ABCDEF"));
		assertEquals(0x7FABCDEF, model.argb());
		assertFalse(model.trySetRgbHex("#12345"));
		assertFalse(model.trySetRgbHex("not-a-colour"));
		assertTrue(model.trySetRgbHex("123456"));
		assertEquals("#123456", model.rgbHex());
		assertEquals(0x7F123456, model.argb());
	}

	@Test
	void pastedHexAcceptsFullColoursAndPartialInputOnly() {
		assertEquals("#ABCDEF", ThemeColorPickerModel.pastedHexText("#12", " abcdef\n"));
		assertEquals("#ABCDEF", ThemeColorPickerModel.pastedHexText("", "#abcdef"));
		assertEquals("#ABCDEF", ThemeColorPickerModel.pastedHexText("", "0xABCDEF"));
		assertEquals("#12AB", ThemeColorPickerModel.pastedHexText("#12", "AB"));
		assertEquals("#A1", ThemeColorPickerModel.pastedHexText("#1234", "#A1"));
		assertNull(ThemeColorPickerModel.pastedHexText("#12345", "AB"));
		assertNull(ThemeColorPickerModel.pastedHexText("", "not a colour"));
		assertNull(ThemeColorPickerModel.pastedHexText("", "   "));
		assertNull(ThemeColorPickerModel.pastedHexText("", null));
	}

	@Test
	void argbHexPasteSetsAlphaAndRejectsOtherLengths() {
		ThemeColorPickerModel model = new ThemeColorPickerModel(0xFF000000);

		assertTrue(model.trySetArgbHex("#80ABCDEF"));
		assertEquals(0x80ABCDEF, model.argb());
		assertFalse(model.trySetArgbHex("#ABCDEF"));
		assertFalse(model.trySetArgbHex("#80ABCDEFF"));
		assertFalse(model.trySetArgbHex(null));
		assertEquals(0x80ABCDEF, model.argb());
	}

	@Test
	void slidersClampAndUpdateAllRepresentations() {
		ThemeColorPickerModel model = new ThemeColorPickerModel(0xFF000000);

		model.setSliderFraction(0, 0.5);
		model.setSliderFraction(1, 2.0);
		model.setSliderFraction(2, -1.0);
		model.setSliderFraction(3, 0.25);

		assertEquals(128, model.red());
		assertEquals(255, model.green());
		assertEquals(0, model.blue());
		assertEquals(64, model.alpha());
		assertEquals("#40 80 FF 00".replace(" ", ""), model.argbHex());
	}
}
