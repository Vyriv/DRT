package dev.vy.drt.client.screen;

import java.util.Locale;
import java.util.Map;

final class RunTimeColorScale {
	static final int FAST = 0xFF55FF55;
	static final int MID = 0xFFFFFF55;
	static final int SLOW = 0xFFFF5555;

	private static final Map<String, Threshold> THRESHOLDS = Map.of(
		"M7", new Threshold(240_000L, 360_000L),
		"M6", new Threshold(160_000L, 240_000L),
		"M5", new Threshold(100_000L, 180_000L),
		"M4", new Threshold(130_000L, 180_000L),
		"M3", new Threshold(120_000L, 210_000L),
		"M2", new Threshold(120_000L, 220_000L),
		"M1", new Threshold(80_000L, 180_000L),
		"F7", new Threshold(210_000L, 300_000L)
	);

	private RunTimeColorScale() {
	}

	static int color(String floor, Long millis, int fallback) {
		if (floor == null || millis == null || millis < 0L) return fallback;
		Threshold threshold = THRESHOLDS.get(floor.trim().toUpperCase(Locale.ROOT));
		if (threshold == null) return fallback;
		int fast = dev.vy.drt.config.ThemePreview.decode(dev.vy.drt.config.DrtTheme.color("time.fast", FAST));
		int mid = dev.vy.drt.config.ThemePreview.decode(dev.vy.drt.config.DrtTheme.color("time.medium", MID));
		int slow = dev.vy.drt.config.ThemePreview.decode(dev.vy.drt.config.DrtTheme.color("time.slow", SLOW));
		if (millis <= threshold.fastMillis) return dev.vy.drt.config.ThemePreview.tag("time.fast", FAST, fast);
		if (millis >= threshold.slowMillis) return dev.vy.drt.config.ThemePreview.tag("time.slow", SLOW, slow);
		double progress = (millis - threshold.fastMillis) / (double) (threshold.slowMillis - threshold.fastMillis);
		int result = progress <= 0.5D
			? interpolate(fast, mid, progress * 2D)
			: interpolate(mid, slow, (progress - 0.5D) * 2D);
		return dev.vy.drt.config.ThemePreview.tag("time.medium", MID, result);
	}

	private static int interpolate(int start, int end, double amount) {
		double clamped = Math.max(0D, Math.min(1D, amount));
		int red = channel(start, 16) + (int) Math.round((channel(end, 16) - channel(start, 16)) * clamped);
		int green = channel(start, 8) + (int) Math.round((channel(end, 8) - channel(start, 8)) * clamped);
		int blue = channel(start, 0) + (int) Math.round((channel(end, 0) - channel(start, 0)) * clamped);
		return 0xFF000000 | red << 16 | green << 8 | blue;
	}

	private static int channel(int color, int shift) {
		return color >>> shift & 0xFF;
	}

	private record Threshold(long fastMillis, long slowMillis) {
	}
}
