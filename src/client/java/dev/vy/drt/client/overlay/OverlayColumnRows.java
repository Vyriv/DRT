package dev.vy.drt.client.overlay;

import java.util.ArrayList;
import java.util.List;

/**
 * Builds multi-column overlay rows with shared measured column x-positions.
 * Used by Session / Detailed / Classic (and optionally Modern) so HUD and preview match.
 */
public final class OverlayColumnRows {
	private OverlayColumnRows() {
	}

	public record Cell(String text, int color, OverlaySegmentRole role) {
		public Cell(String text, int color) {
			this(text, color, OverlaySegmentRole.TEXT);
		}

		public static Cell of(String text, int color) {
			return new Cell(text, color, OverlaySegmentRole.TEXT);
		}

		public static Cell of(String text, int color, OverlaySegmentRole role) {
			return new Cell(text, color, role);
		}

		public int width(OverlayLine.FontMeasurer font) {
			return text == null || text.isEmpty() ? 0 : font.width(text);
		}
	}

	/** One logical column cell that may itself be multiple sequential segments. */
	public record Column(List<Cell> cells) {
		public Column(Cell... cells) {
			this(List.of(cells));
		}

		public int width(OverlayLine.FontMeasurer font) {
			int w = 0;
			for (Cell cell : cells) w += cell.width(font);
			return w;
		}

		public void appendAt(OverlayLine.Builder line, int x, OverlayLine.FontMeasurer font) {
			int cursor = x;
			for (Cell cell : cells) {
				if (cell.text == null || cell.text.isEmpty()) continue;
				line.addAt(cursor, cell.text, cell.color, cell.role);
				cursor += cell.width(font);
			}
		}
	}

	public record Row(
		List<Column> columns,
		OverlayLineHover hover,
		OverlayLineClick click
	) {
		public Row(List<Column> columns, OverlayLineHover hover) {
			this(columns, hover, OverlayLineClick.NONE);
		}
	}

	/**
	 * Places each column at a shared x derived from the widest column content across all rows,
	 * with a measured separator (" | ") between columns.
	 */
	public static void addAlignedRows(
		OverlayLayout.Builder builder,
		OverlayLine.FontMeasurer font,
		List<Row> rows
	) {
		if (rows.isEmpty()) return;
		int colCount = 0;
		for (Row row : rows) colCount = Math.max(colCount, row.columns.size());
		if (colCount == 0) return;

		int[] colWidths = new int[colCount];
		for (Row row : rows) {
			for (int c = 0; c < row.columns.size(); c++) {
				colWidths[c] = Math.max(colWidths[c], row.columns.get(c).width(font));
			}
		}

		String sepText = " | ";
		int sepW = font.width(sepText);
		int[] colX = new int[colCount];
		int x = 0;
		for (int c = 0; c < colCount; c++) {
			colX[c] = x;
			x += colWidths[c];
			if (c < colCount - 1) x += sepW;
		}

		for (Row row : rows) {
			OverlayLine.Builder line = new OverlayLine.Builder()
				.hover(row.hover)
				.click(row.click);
			for (int c = 0; c < row.columns.size(); c++) {
				row.columns.get(c).appendAt(line, colX[c], font);
				if (c < row.columns.size() - 1 && c < colCount - 1) {
					line.addAt(colX[c] + colWidths[c], sepText, OverlayColors.SEP);
				}
			}
			builder.add(line);
		}
	}

	/**
	 * Detailed-style: left label, left value, separator, right label, right value —
	 * each field column shares an x across every row.
	 */
	public static void addLabelValuePairRows(
		OverlayLayout.Builder builder,
		OverlayLine.FontMeasurer font,
		List<LabelValuePairRow> rows
	) {
		if (rows.isEmpty()) return;

		int leftLabelW = 0;
		int leftValueW = 0;
		int rightLabelW = 0;
		int rightValueW = 0;
		for (LabelValuePairRow row : rows) {
			leftLabelW = Math.max(leftLabelW, font.width(row.leftLabel));
			leftValueW = Math.max(leftValueW, font.width(row.leftValue));
			rightLabelW = Math.max(rightLabelW, font.width(row.rightLabel));
			rightValueW = Math.max(rightValueW, font.width(row.rightValue));
		}

		String sepText = " | ";
		int sepW = font.width(sepText);
		int gap = Math.max(2, font.width(" "));

		int leftLabelX = 0;
		int leftValueX = leftLabelX + leftLabelW;
		int sepX = leftValueX + leftValueW + gap;
		int rightLabelX = sepX + sepW + gap;
		int rightValueX = rightLabelX + rightLabelW;

		for (LabelValuePairRow row : rows) {
			OverlayLine.Builder line = new OverlayLine.Builder()
				.hover(row.hover)
				.click(row.click);
			line.addAt(leftLabelX, row.leftLabel, OverlayColors.LABEL);
			line.addAt(leftValueX, row.leftValue, row.leftValueColor);
			line.addAt(sepX, sepText, OverlayColors.SEP);
			line.addAt(rightLabelX, row.rightLabel, OverlayColors.LABEL);
			line.addAt(rightValueX, row.rightValue, row.rightValueColor, row.rightRole);
			builder.add(line);
		}
	}

	public record LabelValuePairRow(
		String leftLabel,
		String leftValue,
		int leftValueColor,
		String rightLabel,
		String rightValue,
		int rightValueColor,
		OverlaySegmentRole rightRole,
		OverlayLineHover hover,
		OverlayLineClick click
	) {
		public LabelValuePairRow(
			String leftLabel,
			String leftValue,
			int leftValueColor,
			String rightLabel,
			String rightValue,
			int rightValueColor,
			OverlayLineHover hover,
			OverlayLineClick click
		) {
			this(leftLabel, leftValue, leftValueColor, rightLabel, rightValue, rightValueColor,
				OverlaySegmentRole.TEXT, hover, click);
		}
	}
}
