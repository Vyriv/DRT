package dev.vy.drt.client.overlay;

import java.util.ArrayList;
import java.util.List;

public final class OverlayLayouts {
	public static final String DEFAULT_CUSTOM_LAYOUT = """
		DRT [{floor}]
		Session: {runs.session} runs | {runs.avg} avg | {runs.hour}/hr
		Profit: {profit.session} | {profit.run}/run | {profit.hour}/hr""";

	/** Extra pixels for drop-shadow when fitting previews. */
	public static final int SHADOW_PAD = 1;

	private OverlayLayouts() {
	}

	public static OverlayLayout build(
		OverlayPreset preset,
		OverlayStats stats,
		OverlayLine.FontMeasurer font,
		int fontLineHeight,
		String customTemplate,
		boolean includeResetLine
	) {
		OverlayLayout layout = switch (preset == null ? OverlayPreset.LEGACY : preset) {
			case LEGACY -> legacy(stats, font, fontLineHeight);
			case MODERN -> modern(stats, font, fontLineHeight);
			case SESSION -> session(stats, font, fontLineHeight);
			case DETAILED -> detailed(stats, font, fontLineHeight);
			case CLASSIC -> classic(stats, font, fontLineHeight);
			case CUSTOM -> CustomOverlayParser.parse(
				customTemplate == null || customTemplate.isBlank() ? DEFAULT_CUSTOM_LAYOUT : customTemplate,
				stats,
				font,
				fontLineHeight
			);
		};
		if (!includeResetLine) return layout;
		OverlayLayout.Builder builder = new OverlayLayout.Builder(font, fontLineHeight);
		for (OverlayLine line : layout.lines) {
			builder.add(copyLine(line));
		}
		builder.add(new OverlayLine.Builder()
			.add(stats.resetLabel, OverlayColors.RESET)
			.hover(OverlayLineHover.RESET)
			.click(OverlayLineClick.RESET));
		return builder.build();
	}

	private static OverlayLine.Builder copyLine(OverlayLine line) {
		OverlayLine.Builder copy = new OverlayLine.Builder()
			.hover(line.hover)
			.click(line.click);
		for (OverlaySegment segment : line.segments) {
			if (segment.positioned()) {
				copy.addAt(segment.x, segment.text, segment.color, segment.role);
			} else {
				copy.add(segment.text, segment.color, segment.role);
			}
		}
		for (String placeholder : line.placeholders) copy.placeholder(placeholder);
		return copy;
	}

	private static OverlayLayout legacy(OverlayStats stats, OverlayLine.FontMeasurer font, int fontLineHeight) {
		OverlayLayout.Builder b = new OverlayLayout.Builder(font, fontLineHeight);
		b.add(titleLine(stats));
		OverlayLine.Builder runs = new OverlayLine.Builder()
			.add("Runs ", OverlayColors.LABEL)
			.add(String.valueOf(stats.totalRuns), OverlayColors.VALUE)
			.add(" | ", OverlayColors.SEP)
			.add(String.valueOf(stats.sessionRuns), OverlayColors.VALUE)
			.add(" | ", OverlayColors.SEP)
			.add(OverlayFormat.duration(stats.avgRunTimeMs), OverlayColors.VALUE)
			.add(" | ", OverlayColors.SEP)
			.add(OverlayFormat.rate(stats.runsPerHour), OverlayColors.RATE, OverlaySegmentRole.RUNS_HR)
			.add("/hr", OverlayColors.DIM, OverlaySegmentRole.RUNS_HR)
			.hover(OverlayLineHover.RUNS);
		if (stats.runsPerHourPaused) {
			runs.add(" [paused]", OverlayColors.PAUSED, OverlaySegmentRole.RUNS_HR);
		}
		b.add(runs);
		b.add(new OverlayLine.Builder()
			.add("Profit ", OverlayColors.LABEL)
			.add(OverlayFormat.coins(stats.totalProfit), OverlayColors.profitColor(stats.totalProfit))
			.add(" | ", OverlayColors.SEP)
			.add(OverlayFormat.coins(stats.sessionProfit), OverlayColors.profitColor(stats.sessionProfit))
			.add(" | ", OverlayColors.SEP)
			.add(OverlayFormat.coins(stats.profitPerRun), OverlayColors.profitColor(stats.profitPerRun))
			.add("/run | ", OverlayColors.DIM)
			.add(OverlayFormat.coins(stats.profitPerHour), OverlayColors.profitColor(stats.profitPerHour))
			.add("/hr", OverlayColors.DIM)
			.hover(OverlayLineHover.PROFIT)
			.click(OverlayLineClick.OPEN_LOOT));
		return b.build();
	}

	private static OverlayLayout modern(OverlayStats stats, OverlayLine.FontMeasurer font, int fontLineHeight) {
		OverlayLayout.Builder b = new OverlayLayout.Builder(font, fontLineHeight);
		b.add(titleLine(stats));

		List<OverlayColumnRows.Row> rows = new ArrayList<>();
		List<OverlayColumnRows.Column> runCols = new ArrayList<>();
		runCols.add(new OverlayColumnRows.Column(
			OverlayColumnRows.Cell.of("Session: ", OverlayColors.LABEL),
			OverlayColumnRows.Cell.of(stats.sessionRuns + " runs", OverlayColors.VALUE)
		));
		if (stats.sessionRuns > 0 || stats.avgRunTimeMs > 0 || stats.runsPerHour > 0) {
			runCols.add(new OverlayColumnRows.Column(
				OverlayColumnRows.Cell.of(OverlayFormat.duration(stats.avgRunTimeMs) + " avg", OverlayColors.VALUE)
			));
			List<OverlayColumnRows.Cell> rateCells = new ArrayList<>();
			rateCells.add(OverlayColumnRows.Cell.of(OverlayFormat.rate(stats.runsPerHour), OverlayColors.RATE, OverlaySegmentRole.RUNS_HR));
			rateCells.add(OverlayColumnRows.Cell.of("/hr", OverlayColors.DIM, OverlaySegmentRole.RUNS_HR));
			if (stats.runsPerHourPaused) {
				rateCells.add(OverlayColumnRows.Cell.of(" [paused]", OverlayColors.PAUSED, OverlaySegmentRole.RUNS_HR));
			}
			runCols.add(new OverlayColumnRows.Column(rateCells));
		}
		rows.add(new OverlayColumnRows.Row(runCols, OverlayLineHover.RUNS));

		List<OverlayColumnRows.Column> profitCols = new ArrayList<>();
		profitCols.add(new OverlayColumnRows.Column(
			OverlayColumnRows.Cell.of("Profit: ", OverlayColors.LABEL),
			OverlayColumnRows.Cell.of(OverlayFormat.signedCoins(stats.sessionProfit), OverlayColors.profitColor(stats.sessionProfit))
		));
		if (stats.sessionRuns > 0) {
			profitCols.add(new OverlayColumnRows.Column(
				OverlayColumnRows.Cell.of(OverlayFormat.signedCoins(stats.profitPerRun), OverlayColors.profitColor(stats.profitPerRun)),
				OverlayColumnRows.Cell.of("/run", OverlayColors.DIM)
			));
			if (stats.profitPerHour != 0 || stats.runsPerHour > 0) {
				profitCols.add(new OverlayColumnRows.Column(
					OverlayColumnRows.Cell.of(OverlayFormat.signedCoins(stats.profitPerHour), OverlayColors.profitColor(stats.profitPerHour)),
					OverlayColumnRows.Cell.of("/hr", OverlayColors.DIM)
				));
			}
		}
		rows.add(new OverlayColumnRows.Row(profitCols, OverlayLineHover.PROFIT, OverlayLineClick.OPEN_LOOT));

		OverlayColumnRows.addAlignedRows(b, font, rows);
		return b.build();
	}

	private static OverlayLayout session(OverlayStats stats, OverlayLine.FontMeasurer font, int fontLineHeight) {
		OverlayLayout.Builder b = new OverlayLayout.Builder(font, fontLineHeight);
		OverlayLine.Builder title = titleLine(stats);
		title.add(" • Session", OverlayColors.MUTED);
		b.add(title);

		if (stats.sessionRuns <= 0) {
			b.add(new OverlayLine.Builder()
				.add("No active session", OverlayColors.DIM)
				.hover(OverlayLineHover.RUNS));
			return b.build();
		}

		List<OverlayColumnRows.Cell> rateCells = new ArrayList<>();
		rateCells.add(OverlayColumnRows.Cell.of(OverlayFormat.rate(stats.runsPerHour), OverlayColors.RATE, OverlaySegmentRole.RUNS_HR));
		rateCells.add(OverlayColumnRows.Cell.of("/hr", OverlayColors.DIM, OverlaySegmentRole.RUNS_HR));
		if (stats.runsPerHourPaused) {
			rateCells.add(OverlayColumnRows.Cell.of(" [paused]", OverlayColors.PAUSED, OverlaySegmentRole.RUNS_HR));
		}

		List<OverlayColumnRows.Column> profitCols = new ArrayList<>();
		profitCols.add(new OverlayColumnRows.Column(
			OverlayColumnRows.Cell.of(OverlayFormat.signedCoins(stats.sessionProfit), OverlayColors.profitColor(stats.sessionProfit))
		));
		profitCols.add(new OverlayColumnRows.Column(
			OverlayColumnRows.Cell.of(OverlayFormat.signedCoins(stats.profitPerRun), OverlayColors.profitColor(stats.profitPerRun)),
			OverlayColumnRows.Cell.of("/run", OverlayColors.DIM)
		));
		if (stats.profitPerHour != 0 || stats.runsPerHour > 0) {
			profitCols.add(new OverlayColumnRows.Column(
				OverlayColumnRows.Cell.of(OverlayFormat.signedCoins(stats.profitPerHour), OverlayColors.profitColor(stats.profitPerHour)),
				OverlayColumnRows.Cell.of("/hr", OverlayColors.DIM)
			));
		}

		OverlayColumnRows.addAlignedRows(b, font, List.of(
			new OverlayColumnRows.Row(List.of(
				new OverlayColumnRows.Column(OverlayColumnRows.Cell.of(stats.sessionRuns + " runs", OverlayColors.VALUE)),
				new OverlayColumnRows.Column(OverlayColumnRows.Cell.of(OverlayFormat.duration(stats.avgRunTimeMs) + " avg", OverlayColors.VALUE)),
				new OverlayColumnRows.Column(rateCells)
			), OverlayLineHover.RUNS),
			new OverlayColumnRows.Row(profitCols, OverlayLineHover.PROFIT, OverlayLineClick.OPEN_LOOT)
		));
		return b.build();
	}

	private static OverlayLayout detailed(OverlayStats stats, OverlayLine.FontMeasurer font, int fontLineHeight) {
		OverlayLayout.Builder b = new OverlayLayout.Builder(font, fontLineHeight);
		b.add(titleLine(stats));

		List<OverlayColumnRows.LabelValuePairRow> rows = new ArrayList<>();
		rows.add(new OverlayColumnRows.LabelValuePairRow(
			"Runs: ", String.valueOf(stats.totalRuns), OverlayColors.VALUE,
			"Session: ", String.valueOf(stats.sessionRuns), OverlayColors.VALUE,
			OverlayLineHover.RUNS, OverlayLineClick.NONE
		));
		rows.add(new OverlayColumnRows.LabelValuePairRow(
			"Avg: ", OverlayFormat.duration(stats.avgRunTimeMs), OverlayColors.VALUE,
			"Rate: ", OverlayFormat.rate(stats.runsPerHour) + "/hr", OverlayColors.RATE,
			OverlaySegmentRole.RUNS_HR,
			OverlayLineHover.RUNS, OverlayLineClick.NONE
		));
		rows.add(new OverlayColumnRows.LabelValuePairRow(
			"Profit: ", OverlayFormat.coins(stats.totalProfit), OverlayColors.profitColor(stats.totalProfit),
			"Session: ", OverlayFormat.signedCoins(stats.sessionProfit), OverlayColors.profitColor(stats.sessionProfit),
			OverlayLineHover.PROFIT, OverlayLineClick.OPEN_LOOT
		));
		rows.add(new OverlayColumnRows.LabelValuePairRow(
			"Per run: ", OverlayFormat.coins(stats.lifetimeProfitPerRun), OverlayColors.profitColor(stats.lifetimeProfitPerRun),
			"Per hour: ", OverlayFormat.signedCoins(stats.profitPerHour), OverlayColors.profitColor(stats.profitPerHour),
			OverlayLineHover.PROFIT, OverlayLineClick.OPEN_LOOT
		));
		OverlayColumnRows.addLabelValuePairRows(b, font, rows);
		return b.build();
	}

	private static OverlayLayout classic(OverlayStats stats, OverlayLine.FontMeasurer font, int fontLineHeight) {
		OverlayLayout.Builder b = new OverlayLayout.Builder(font, fontLineHeight);
		b.add(new OverlayLine.Builder()
			.add("=== ", OverlayColors.DIM)
			.add("DRT ", OverlayColors.TITLE, OverlaySegmentRole.MODE)
			.add("[", OverlayColors.BRACKET)
			.add(stats.floorTag, OverlayColors.floorTagColor(stats.floorTag), OverlaySegmentRole.FLOOR)
			.add("]", OverlayColors.BRACKET)
			.add(" ===", OverlayColors.DIM)
			.hover(OverlayLineHover.FLOOR));

		List<OverlayColumnRows.Row> rows = new ArrayList<>();
		rows.add(new OverlayColumnRows.Row(List.of(
			new OverlayColumnRows.Column(
				OverlayColumnRows.Cell.of("Runs: ", OverlayColors.LABEL),
				OverlayColumnRows.Cell.of(String.valueOf(stats.totalRuns), OverlayColors.VALUE),
				OverlayColumnRows.Cell.of(" Tot", OverlayColors.DIM)
			),
			new OverlayColumnRows.Column(
				OverlayColumnRows.Cell.of(String.valueOf(stats.sessionRuns), OverlayColors.VALUE),
				OverlayColumnRows.Cell.of(" Sess", OverlayColors.DIM)
			)
		), OverlayLineHover.RUNS));

		List<OverlayColumnRows.Cell> paceRight = new ArrayList<>();
		paceRight.add(OverlayColumnRows.Cell.of(OverlayFormat.rate(stats.runsPerHour), OverlayColors.RATE, OverlaySegmentRole.RUNS_HR));
		paceRight.add(OverlayColumnRows.Cell.of("/hr", OverlayColors.DIM, OverlaySegmentRole.RUNS_HR));
		if (stats.runsPerHourPaused) {
			paceRight.add(OverlayColumnRows.Cell.of(" [paused]", OverlayColors.PAUSED, OverlaySegmentRole.RUNS_HR));
		}
		rows.add(new OverlayColumnRows.Row(List.of(
			new OverlayColumnRows.Column(
				OverlayColumnRows.Cell.of("Pace: ", OverlayColors.LABEL),
				OverlayColumnRows.Cell.of(OverlayFormat.duration(stats.avgRunTimeMs), OverlayColors.VALUE),
				OverlayColumnRows.Cell.of(" Avg", OverlayColors.DIM)
			),
			new OverlayColumnRows.Column(paceRight)
		), OverlayLineHover.RUNS));

		boolean showSessionProfit = stats.sessionRuns > 0 || stats.sessionProfit != 0;
		List<OverlayColumnRows.Column> profitCols = new ArrayList<>();
		profitCols.add(new OverlayColumnRows.Column(
			OverlayColumnRows.Cell.of("Profit: ", OverlayColors.LABEL),
			OverlayColumnRows.Cell.of(OverlayFormat.coins(stats.totalProfit), OverlayColors.profitColor(stats.totalProfit)),
			OverlayColumnRows.Cell.of(" Tot", OverlayColors.DIM)
		));
		if (showSessionProfit) {
			profitCols.add(new OverlayColumnRows.Column(
				OverlayColumnRows.Cell.of(OverlayFormat.signedCoins(stats.sessionProfit), OverlayColors.profitColor(stats.sessionProfit)),
				OverlayColumnRows.Cell.of(" Sess", OverlayColors.DIM)
			));
		}
		rows.add(new OverlayColumnRows.Row(profitCols, OverlayLineHover.PROFIT, OverlayLineClick.OPEN_LOOT));

		boolean showYield = stats.totalRuns > 0 || stats.profitPerHour != 0 || stats.lifetimeProfitPerRun != 0;
		if (showYield) {
			List<OverlayColumnRows.Column> yieldCols = new ArrayList<>();
			yieldCols.add(new OverlayColumnRows.Column(
				OverlayColumnRows.Cell.of("Yield: ", OverlayColors.LABEL),
				OverlayColumnRows.Cell.of(OverlayFormat.coins(stats.lifetimeProfitPerRun), OverlayColors.profitColor(stats.lifetimeProfitPerRun)),
				OverlayColumnRows.Cell.of("/run", OverlayColors.DIM)
			));
			if (stats.profitPerHour != 0 || stats.runsPerHour > 0) {
				yieldCols.add(new OverlayColumnRows.Column(
					OverlayColumnRows.Cell.of(OverlayFormat.coins(stats.profitPerHour), OverlayColors.profitColor(stats.profitPerHour)),
					OverlayColumnRows.Cell.of("/hr", OverlayColors.DIM)
				));
			}
			rows.add(new OverlayColumnRows.Row(yieldCols, OverlayLineHover.PROFIT, OverlayLineClick.OPEN_LOOT));
		}

		OverlayColumnRows.addAlignedRows(b, font, rows);
		return b.build();
	}

	private static OverlayLine.Builder titleLine(OverlayStats stats) {
		return new OverlayLine.Builder()
			.add("DRT ", OverlayColors.TITLE, OverlaySegmentRole.MODE)
			.add("[", OverlayColors.BRACKET)
			.add(stats.floorTag, OverlayColors.floorTagColor(stats.floorTag), OverlaySegmentRole.FLOOR)
			.add("]", OverlayColors.BRACKET)
			.hover(OverlayLineHover.FLOOR);
	}
}
