package dev.vy.drt.client.screen;

final class LootSearchPolicy {
	record Mode(boolean committed, boolean includeUnopened) {}

	private LootSearchPolicy() {}

	static Mode edited(String query) {
		return new Mode(false, false);
	}

	static Mode committed(String query, boolean shiftDown) {
		boolean active = query != null && !query.isBlank();
		return new Mode(active, active && shiftDown);
	}
}
