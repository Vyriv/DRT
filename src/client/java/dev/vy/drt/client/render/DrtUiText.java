package dev.vy.drt.client.render;

import net.minecraft.client.gui.Font;

/** Vertical text alignment helpers shared by DRT screens and overlays. */
public final class DrtUiText {
	private DrtUiText() {
	}

	/**
	 * Visible glyph height of one line. Vanilla glyphs fill the top 7 rows of the 9px line; the
	 * rest is shadow, descenders and spacing, so centring the whole line box sits text ~1px high.
	 */
	public static int capHeight(int lineHeight) {
		return Math.max(1, lineHeight - 2);
	}

	/** Text Y that visually centres one line inside a box of height {@code h} starting at {@code top}. */
	public static int centeredTextY(Font font, int top, int h) {
		return top + (h - capHeight(font.lineHeight)) / 2;
	}
}
