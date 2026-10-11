package dev.vy.drt.luck;

import java.util.Locale;

public enum FloorId {
	F1(1, false), F2(2, false), F3(3, false), F4(4, false), F5(5, false), F6(6, false), F7(7, false),
	M1(1, true), M2(2, true), M3(3, true), M4(4, true), M5(5, true), M6(6, true), M7(7, true);

	private final int number;
	private final boolean masterMode;

	FloorId(int number, boolean masterMode) {
		this.number = number;
		this.masterMode = masterMode;
	}

	public int number() {
		return number;
	}

	public boolean masterMode() {
		return masterMode;
	}

	public boolean hasBedrockChest() {
		return number >= 5;
	}

	public String dataDirectory() {
		return name().toLowerCase(Locale.ROOT);
	}

	public static FloorId parse(String value) {
		if (value == null) return null;
		try {
			return valueOf(value.trim().toUpperCase(Locale.ROOT));
		} catch (IllegalArgumentException ignored) {
			return null;
		}
	}
}
