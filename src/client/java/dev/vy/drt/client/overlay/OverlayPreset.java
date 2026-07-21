package dev.vy.drt.client.overlay;

import java.util.Locale;

public enum OverlayPreset {
	LEGACY("Legacy"),
	MODERN("Modern"),
	SESSION("Session"),
	DETAILED("Detailed"),
	CLASSIC("Classic"),
	CUSTOM("Custom");

	private final String displayName;

	OverlayPreset(String displayName) {
		this.displayName = displayName;
	}

	public String displayName() {
		return displayName;
	}

	public boolean isSelectablePreset() {
		return this != CUSTOM;
	}

	public static OverlayPreset fromConfig(String value) {
		if (value == null || value.isBlank()) return null;
		try {
			return OverlayPreset.valueOf(value.trim().toUpperCase(Locale.ROOT));
		} catch (IllegalArgumentException ignored) {
			return null;
		}
	}
}
