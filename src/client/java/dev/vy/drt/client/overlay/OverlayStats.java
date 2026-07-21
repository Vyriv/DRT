package dev.vy.drt.client.overlay;

/**
 * Central overlay data model consumed by every preset and the custom layout renderer.
 */
public final class OverlayStats {
	public final String floorTag;
	public final int totalRuns;
	public final int sessionRuns;
	public final long avgRunTimeMs;
	public final long sessionRunTimeMs;
	public final long totalRunTimeMs;
	public final double runsPerHour;
	public final int sCount;
	public final int sPlusCount;
	public final long totalProfit;
	public final long sessionProfit;
	public final long profitPerRun;
	public final long profitPerHour;
	public final long lifetimeProfitPerRun;
	public final boolean runsPerHourPaused;
	public final String resetLabel;

	public OverlayStats(
		String floorTag,
		int totalRuns,
		int sessionRuns,
		long avgRunTimeMs,
		long sessionRunTimeMs,
		long totalRunTimeMs,
		double runsPerHour,
		int sCount,
		int sPlusCount,
		long totalProfit,
		long sessionProfit,
		long profitPerRun,
		long profitPerHour,
		boolean runsPerHourPaused,
		String resetLabel
	) {
		this.floorTag = floorTag == null || floorTag.isBlank() ? "All" : floorTag;
		this.totalRuns = Math.max(0, totalRuns);
		this.sessionRuns = Math.max(0, sessionRuns);
		this.avgRunTimeMs = Math.max(0L, avgRunTimeMs);
		this.sessionRunTimeMs = Math.max(0L, sessionRunTimeMs);
		this.totalRunTimeMs = Math.max(0L, totalRunTimeMs);
		this.runsPerHour = Math.max(0.0, runsPerHour);
		this.sCount = Math.max(0, sCount);
		this.sPlusCount = Math.max(0, sPlusCount);
		this.totalProfit = totalProfit;
		this.sessionProfit = sessionProfit;
		this.profitPerRun = profitPerRun;
		this.profitPerHour = profitPerHour;
		this.lifetimeProfitPerRun = this.totalRuns > 0 ? totalProfit / this.totalRuns : 0L;
		this.runsPerHourPaused = runsPerHourPaused;
		this.resetLabel = resetLabel == null ? "Reset" : resetLabel;
	}

	public static OverlayStats previewSample() {
		return new OverlayStats(
			"M6",
			26,
			4,
			169_000L,
			676_000L,
			4_394_000L,
			17.6,
			8,
			12,
			43_700_000L,
			6_200_000L,
			1_550_000L,
			36_500_000L,
			false,
			"Reset M6"
		);
	}
}
