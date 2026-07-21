package dev.vy.drt.client.overlay;

public final class OverlayFormat {
	private OverlayFormat() {
	}

	public static String coins(long coins) {
		if (coins < 0) return "-" + coins(-coins);
		if (coins >= 1_000_000_000L) return String.format("%.1fB", coins / 1_000_000_000.0);
		if (coins >= 1_000_000L) return String.format("%.1fM", coins / 1_000_000.0);
		if (coins >= 1_000L) return String.format("%.1fk", coins / 1_000.0);
		return String.valueOf(coins);
	}

	public static String signedCoins(long coins) {
		if (coins > 0) return "+" + coins(coins);
		return coins(coins);
	}

	public static String rate(double rate) {
		if (rate >= 100) return String.format("%.1f", rate);
		return String.format("%.2f", rate);
	}

	public static String duration(long millis) {
		if (millis <= 0L) return "0:00";
		long totalSeconds = Math.max(0L, millis / 1000L);
		long minutes = totalSeconds / 60L;
		long seconds = totalSeconds % 60L;
		return minutes + ":" + String.format("%02d", seconds);
	}
}
