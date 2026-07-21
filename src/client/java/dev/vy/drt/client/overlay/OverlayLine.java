package dev.vy.drt.client.overlay;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

public final class OverlayLine {
	public final List<OverlaySegment> segments;
	public final OverlayLineHover hover;
	public final OverlayLineClick click;
	public final Set<String> placeholders;
	public final int y;
	public final int width;
	public final int height;

	public OverlayLine(
		List<OverlaySegment> segments,
		OverlayLineHover hover,
		OverlayLineClick click,
		Set<String> placeholders,
		int y,
		int width,
		int height
	) {
		this.segments = List.copyOf(segments);
		this.hover = hover == null ? OverlayLineHover.NONE : hover;
		this.click = click == null ? OverlayLineClick.NONE : click;
		this.placeholders = placeholders == null || placeholders.isEmpty()
			? Set.of()
			: Set.copyOf(placeholders);
		this.y = y;
		this.width = Math.max(0, width);
		this.height = Math.max(0, height);
	}

	public static final class Builder {
		private final List<OverlaySegment> segments = new ArrayList<>();
		private OverlayLineHover hover = OverlayLineHover.NONE;
		private OverlayLineClick click = OverlayLineClick.NONE;
		private final EnumSet<PlaceholderFlag> flags = EnumSet.noneOf(PlaceholderFlag.class);
		private final Set<String> placeholders = new java.util.LinkedHashSet<>();

		public Builder add(String text, int color) {
			return add(text, color, OverlaySegmentRole.TEXT);
		}

		public Builder add(String text, int color, OverlaySegmentRole role) {
			if (text != null && !text.isEmpty()) {
				segments.add(new OverlaySegment(text, color, role, OverlaySegment.SEQUENTIAL));
			}
			return this;
		}

		public Builder addAt(int x, String text, int color) {
			return addAt(x, text, color, OverlaySegmentRole.TEXT);
		}

		public Builder addAt(int x, String text, int color, OverlaySegmentRole role) {
			if (text != null && !text.isEmpty()) {
				segments.add(new OverlaySegment(text, color, role, Math.max(0, x)));
			}
			return this;
		}

		public Builder hover(OverlayLineHover hover) {
			this.hover = hover;
			return this;
		}

		public Builder click(OverlayLineClick click) {
			this.click = click;
			return this;
		}

		public Builder placeholder(String name) {
			if (name != null && !name.isBlank()) placeholders.add(name);
			return this;
		}

		public Builder markRuns() {
			flags.add(PlaceholderFlag.RUNS);
			return this;
		}

		public Builder markProfit() {
			flags.add(PlaceholderFlag.PROFIT);
			return this;
		}

		public Builder markFloor() {
			flags.add(PlaceholderFlag.FLOOR);
			return this;
		}

		public Builder applyFlags() {
			if (flags.contains(PlaceholderFlag.FLOOR) && hover == OverlayLineHover.NONE) {
				hover = OverlayLineHover.FLOOR;
			}
			if (flags.contains(PlaceholderFlag.RUNS)) {
				if (hover == OverlayLineHover.NONE) hover = OverlayLineHover.RUNS;
			}
			if (flags.contains(PlaceholderFlag.PROFIT)) {
				if (hover == OverlayLineHover.NONE) hover = OverlayLineHover.PROFIT;
				if (click == OverlayLineClick.NONE) click = OverlayLineClick.OPEN_LOOT;
			}
			return this;
		}

		public OverlayLine build(FontMeasurer font, int y, int lineHeight) {
			applyFlags();
			int cursor = 0;
			int width = 0;
			boolean anyPositioned = false;
			for (OverlaySegment segment : segments) {
				if (segment.positioned()) anyPositioned = true;
			}
			if (!anyPositioned) {
				for (OverlaySegment segment : segments) {
					width += font.width(segment.text);
				}
			} else {
				List<OverlaySegment> resolved = new ArrayList<>(segments.size());
				for (OverlaySegment segment : segments) {
					int x = segment.positioned() ? segment.x : cursor;
					resolved.add(new OverlaySegment(segment.text, segment.color, segment.role, x));
					int end = x + font.width(segment.text);
					cursor = end;
					width = Math.max(width, end);
				}
				segments.clear();
				segments.addAll(resolved);
			}
			return new OverlayLine(segments, hover, click, placeholders, y, width, lineHeight);
		}
	}

	private enum PlaceholderFlag {
		FLOOR, RUNS, PROFIT
	}

	@FunctionalInterface
	public interface FontMeasurer {
		int width(String text);
	}
}
