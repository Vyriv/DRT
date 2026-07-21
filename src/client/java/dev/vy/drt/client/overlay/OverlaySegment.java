package dev.vy.drt.client.overlay;

/**
 * A rendered text piece. When {@code x >= 0}, the segment is drawn at that absolute
 * line-local x; otherwise it is laid out sequentially after the previous segment.
 */
public final class OverlaySegment {
	public static final int SEQUENTIAL = -1;

	public final String text;
	public final int color;
	public final OverlaySegmentRole role;
	public final int x;

	public OverlaySegment(String text, int color) {
		this(text, color, OverlaySegmentRole.TEXT, SEQUENTIAL);
	}

	public OverlaySegment(String text, int color, OverlaySegmentRole role) {
		this(text, color, role, SEQUENTIAL);
	}

	public OverlaySegment(String text, int color, OverlaySegmentRole role, int x) {
		this.text = text == null ? "" : text;
		this.color = color;
		this.role = role == null ? OverlaySegmentRole.TEXT : role;
		this.x = x;
	}

	public boolean positioned() {
		return x >= 0;
	}
}
