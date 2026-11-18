package dev.vy.drt.client.screen;

import java.awt.Color;
import java.util.Locale;

final class ThemeColorPickerModel {
	private int red;
	private int green;
	private int blue;
	private int alpha;
	private float hue;
	private float saturation;
	private float brightness;

	ThemeColorPickerModel(int argb) {
		setArgb(argb);
	}

	int argb() { return alpha << 24 | red << 16 | green << 8 | blue; }
	int red() { return red; }
	int green() { return green; }
	int blue() { return blue; }
	int alpha() { return alpha; }
	float hue() { return hue; }
	float saturation() { return saturation; }
	float brightness() { return brightness; }
	String rgbHex() { return String.format("#%02X%02X%02X", red, green, blue); }
	String argbHex() { return String.format("#%02X%02X%02X%02X", alpha, red, green, blue); }

	void setArgb(int argb) {
		alpha = argb >>> 24;
		red = argb >>> 16 & 255;
		green = argb >>> 8 & 255;
		blue = argb & 255;
		syncHsv();
	}

	void setSaturationBrightness(float saturation, float brightness) {
		this.saturation = clampUnit(saturation);
		this.brightness = clampUnit(brightness);
		syncRgb();
	}

	void setHue(float hue) {
		this.hue = clampUnit(hue);
		syncRgb();
	}

	void setChannel(int channel, int value) {
		int safe = Math.clamp(value, 0, 255);
		switch (channel) {
			case 0 -> red = safe;
			case 1 -> green = safe;
			case 2 -> blue = safe;
			case 3 -> alpha = safe;
			default -> throw new IllegalArgumentException("Unknown colour channel");
		}
		if (channel < 3) syncHsv();
	}

	void setSliderFraction(int channel, double fraction) {
		setChannel(channel, (int) Math.round(clampUnit((float) fraction) * 255));
	}

	boolean trySetRgbHex(String text) {
		if (text == null) return false;
		String clean = text.trim();
		if (clean.startsWith("#")) clean = clean.substring(1);
		if (!clean.matches("[0-9a-fA-F]{6}")) return false;
		int rgb = Integer.parseInt(clean, 16);
		red = rgb >>> 16 & 255;
		green = rgb >>> 8 & 255;
		blue = rgb & 255;
		syncHsv();
		return true;
	}

	boolean trySetArgbHex(String text) {
		String clean = stripHexPrefix(text);
		if (clean == null || !clean.matches("[0-9a-fA-F]{8}")) return false;
		setArgb(Integer.parseUnsignedInt(clean, 16));
		return true;
	}

	/** Hex field text after pasting {@code clipboard} onto {@code current}, or null when the paste is not hex colour input. */
	static String pastedHexText(String current, String clipboard) {
		String clean = stripHexPrefix(clipboard);
		if (clean == null || clean.isEmpty()) return null;
		if (clean.matches("[0-9a-fA-F]{6}")) return "#" + clean.toUpperCase(Locale.ROOT);
		String text = clipboard.strip().startsWith("#") ? "#" + clean : current + clean;
		return text.matches("#?[0-9a-fA-F]{0,6}") ? text : null;
	}

	private static String stripHexPrefix(String text) {
		if (text == null) return null;
		String clean = text.strip();
		if (clean.startsWith("#")) return clean.substring(1);
		if (clean.regionMatches(true, 0, "0x", 0, 2)) return clean.substring(2);
		return clean;
	}

	private void syncHsv() {
		float[] hsv = Color.RGBtoHSB(red, green, blue, null);
		hue = hsv[0];
		saturation = hsv[1];
		brightness = hsv[2];
	}

	private void syncRgb() {
		int rgb = Color.HSBtoRGB(hue, saturation, brightness);
		red = rgb >>> 16 & 255;
		green = rgb >>> 8 & 255;
		blue = rgb & 255;
	}

	private static float clampUnit(float value) {
		return Math.max(0F, Math.min(1F, value));
	}
}
