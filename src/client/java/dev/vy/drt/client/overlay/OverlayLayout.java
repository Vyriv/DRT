package dev.vy.drt.client.overlay;

import java.util.ArrayList;
import java.util.List;

public final class OverlayLayout {
	public final List<OverlayLine> lines;
	public final int width;
	public final int height;
	public final int lineHeight;

	public OverlayLayout(List<OverlayLine> lines, int width, int height, int lineHeight) {
		this.lines = List.copyOf(lines);
		this.width = Math.max(0, width);
		this.height = Math.max(0, height);
		this.lineHeight = Math.max(1, lineHeight);
	}

	public static final class Builder {
		private final List<OverlayLine> lines = new ArrayList<>();
		private final OverlayLine.FontMeasurer font;
		private final int lineHeight;
		private int y;

		public Builder(OverlayLine.FontMeasurer font, int fontLineHeight) {
			this.font = font;
			this.lineHeight = fontLineHeight + 2;
		}

		public Builder add(OverlayLine.Builder line) {
			OverlayLine built = line.build(font, y, lineHeight);
			lines.add(built);
			y += lineHeight;
			return this;
		}

		public OverlayLayout build() {
			int width = 0;
			for (OverlayLine line : lines) width = Math.max(width, line.width);
			int height = lines.isEmpty() ? 0 : lines.size() * lineHeight - 2;
			return new OverlayLayout(lines, width, height, lineHeight);
		}
	}

	public OverlayLine lineAtLocalY(double localY) {
		for (OverlayLine line : lines) {
			if (localY >= line.y - 2 && localY <= line.y + line.height) return line;
		}
		return null;
	}

	public HitResult hitTest(double localX, double localY) {
		OverlayLine line = lineAtLocalY(localY);
		if (line == null) return HitResult.miss();

		int x = 0;
		for (OverlaySegment segment : line.segments) {
			int segW = Math.max(0, measureApprox(segment.text));
			// width already baked into line; remeasure not available — use cumulative from plain? 
			// Caller should use hitTest with font. See hitTest(font,...).
			x += segW;
		}
		return hitTestWithWidths(line, localX, localY, null);
	}

	public HitResult hitTest(OverlayLine.FontMeasurer font, double localX, double localY) {
		OverlayLine line = lineAtLocalY(localY);
		if (line == null) return HitResult.miss();
		return hitTestWithWidths(line, localX, localY, font);
	}

	private HitResult hitTestWithWidths(OverlayLine line, double localX, double localY, OverlayLine.FontMeasurer font) {
		OverlaySegmentRole segmentRole = OverlaySegmentRole.TEXT;
		int cursor = 0;
		for (OverlaySegment segment : line.segments) {
			int segW = font == null ? segment.text.length() * 6 : font.width(segment.text);
			int segX = segment.positioned() ? segment.x : cursor;
			if (localX >= segX - 3 && localX <= segX + segW + 3) {
				if (segment.role == OverlaySegmentRole.MODE
					|| segment.role == OverlaySegmentRole.FLOOR
					|| segment.role == OverlaySegmentRole.RUNS_HR) {
					segmentRole = segment.role;
					break;
				}
			}
			cursor = segX + segW;
		}

		OverlayLineHover hover = line.hover;
		OverlayLineClick click = line.click;
		if (segmentRole == OverlaySegmentRole.MODE) {
			hover = OverlayLineHover.MODE;
			click = OverlayLineClick.CYCLE_MODE;
		} else if (segmentRole == OverlaySegmentRole.FLOOR) {
			hover = OverlayLineHover.FLOOR;
			click = OverlayLineClick.CYCLE_FLOOR;
		} else if (segmentRole == OverlaySegmentRole.RUNS_HR) {
			hover = OverlayLineHover.RUNS_HR;
			click = OverlayLineClick.TOGGLE_RUNS_HR;
		}
		return new HitResult(true, line, hover, click, segmentRole);
	}

	private static int measureApprox(String text) {
		return text == null ? 0 : text.length() * 6;
	}

	public record HitResult(
		boolean hit,
		OverlayLine line,
		OverlayLineHover hover,
		OverlayLineClick click,
		OverlaySegmentRole segmentRole
	) {
		public static HitResult miss() {
			return new HitResult(false, null, OverlayLineHover.NONE, OverlayLineClick.NONE, OverlaySegmentRole.TEXT);
		}
	}
}
