package dev.vy.drt.config;

import java.util.Locale;

/**
 * Saved HUD overlay settings. The standard preset and the custom layout text are separate fields:
 * choosing a preset never rewrites the custom text, and editing the text only changes the preset
 * when the caller asks to activate Custom. None of this touches the UI theme.
 */
public final class OverlaySettings {
	public static final int DEFAULT_HUD_X = 10;
	public static final int DEFAULT_HUD_Y = 10;
	public static final float DEFAULT_HUD_SCALE = 1.0F;

	private OverlaySettings() {
	}

	public static void applyPreset(DrtConfig config, String preset) {
		config.hudOverlayPreset = normalizePreset(preset, false);
	}

	public static void applyCustomLayout(DrtConfig config, String layout) {
		config.customOverlayLayout = normalizeNewlines(layout == null ? "" : layout);
	}

	public static void applyPresetAndLayout(DrtConfig config, String preset, String customLayout) {
		applyPreset(config, preset);
		if (customLayout != null) applyCustomLayout(config, customLayout);
	}

	static String normalizePreset(String preset, boolean freshDefault) {
		if (preset == null || preset.isBlank()) return freshDefault ? "MODERN" : "LEGACY";
		String normalized = preset.trim().toUpperCase(Locale.ROOT);
		return switch (normalized) {
			case "LEGACY", "MODERN", "SESSION", "DETAILED", "CLASSIC", "CUSTOM" -> normalized;
			default -> freshDefault ? "MODERN" : "LEGACY";
		};
	}

	private static String normalizeNewlines(String text) {
		return text.replace("\r\n", "\n").replace('\r', '\n');
	}
}
