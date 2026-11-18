package dev.vy.drt.client.screen;

public enum DrtNavTab {
	OVERVIEW("Overview", "Ovr"),
	LOOT("Loot", "Loot"),
	RUNS("Runs", "Runs"),
	OVERLAY("Overlay", "HUD"),
	SETTINGS("Settings", "Set");

	private final String title;
	private final String compactTitle;

	DrtNavTab(String title, String compactTitle) {
		this.title = title;
		this.compactTitle = compactTitle;
	}

	public String title() {
		return title;
	}

	public String compactTitle() {
		return compactTitle;
	}
}
