package dev.vy.drt.config;

public enum DungeonFloor {
	F1, F2, F3, F4, F5, F6, F7,
	M1, M2, M3, M4, M5, M6, M7,
	UNKNOWN;

	public boolean isMasterMode() {
		return this != UNKNOWN && ordinal() >= 7;
	}

	public int floorNumber() {
		if (this == UNKNOWN) return -1;
		return isMasterMode() ? ordinal() - 6 : ordinal() + 1;
	}
}
