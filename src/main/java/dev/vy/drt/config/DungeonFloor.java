package dev.vy.drt.config;

public enum DungeonFloor {
	F1, F2, F3, F4, F5, F6, F7,
	M1, M2, M3, M4, M5, M6, M7,
	K1, K2, K3, K4, K5,
	UNKNOWN;

	public boolean isMasterMode() {
		return name().startsWith("M");
	}

	public boolean isKuudra() {
		return name().startsWith("K");
	}

	public boolean isCatacombs() {
		return name().startsWith("F") || name().startsWith("M");
	}

	public int floorNumber() {
		if (this == UNKNOWN) return -1;
		try {
			return Integer.parseInt(name().substring(1));
		} catch (RuntimeException ignored) {
			return -1;
		}
	}
}
