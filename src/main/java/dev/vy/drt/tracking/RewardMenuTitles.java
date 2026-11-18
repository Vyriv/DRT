package dev.vy.drt.tracking;

/** Screen titles for the Catacombs chest list where the Croesus profit overlay belongs. */
public final class RewardMenuTitles {
	private RewardMenuTitles() {
	}

	public static boolean isCatacombsRewardMenu(String normalizedTitle) {
		if (normalizedTitle == null || normalizedTitle.isBlank()) return false;
		return normalizedTitle.startsWith("CATACOMBS - FLOOR ")
			|| normalizedTitle.startsWith("THE CATACOMBS - FLOOR ")
			|| normalizedTitle.startsWith("MASTER CATACOMBS - FLOOR ")
			|| normalizedTitle.startsWith("MASTER MODE CATACOMBS - FLOOR ")
			|| normalizedTitle.startsWith("MASTER MODE THE CATACOMBS - FLOOR ");
	}

	public static boolean shouldDrawProfitOverlay(boolean enabled, String normalizedTitle, int chestRows) {
		return enabled && chestRows > 0 && isCatacombsRewardMenu(normalizedTitle);
	}
}
