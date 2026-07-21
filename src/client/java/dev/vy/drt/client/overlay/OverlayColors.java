package dev.vy.drt.client.overlay;

public final class OverlayColors {
	public static final int TITLE = 0xFF7A7A8A;
	public static final int BRACKET = 0xFF555555;
	public static final int FLOOR = 0xFFFFAA00;
	public static final int LABEL = 0xFFAAAAAA;
	public static final int VALUE = 0xFFFFFFFF;
	public static final int SEP = 0xFF555555;
	public static final int DIM = 0xFF888888;
	public static final int RATE = 0xFF55FFFF;
	public static final int PAUSED = 0xFFFFAA00;
	public static final int RESET = 0xFFFF5555;
	public static final int WARNING = 0xFFFFAA55;
	public static final int MUTED = 0xFF7A7A8A;

	private static final int[] PROFIT_GRADIENT = {
		0xFF0000, 0xF70909, 0xEF1212, 0xE71B1B, 0xDF2424, 0xD72D2D,
		0xCF3636, 0xC73F3F, 0xBF4848, 0xB75151, 0xAF5A5A, 0xA76363,
		0x9F6C6C, 0x977575, 0x8F7E7E, 0x888888, 0x7E8F7E, 0x759775,
		0x6C9F6C, 0x63A763, 0x5AAF5A, 0x51B751, 0x48BF48, 0x3FC73F,
		0x36CF36, 0x2DD72D, 0x24DF24, 0x1BE71B, 0x12EF12, 0x09F709, 0x00FF00
	};
	private static final long PROFIT_GRADIENT_MAX = 100_000_000L;

	private OverlayColors() {
	}

	public static int floorTagColor(String tag) {
		if (tag == null) return FLOOR;
		if (tag.startsWith("F")) return 0xFF55CC66;
		if (tag.startsWith("M")) return 0xFFFF5555;
		if (tag.startsWith("K")) return 0xFFAA0000;
		return FLOOR;
	}

	public static int profitColor(long coins) {
		float t = (float) Math.max(-1.0, Math.min(1.0, (double) coins / PROFIT_GRADIENT_MAX));
		float idx = (t + 1f) / 2f * (PROFIT_GRADIENT.length - 1);
		int lo = (int) idx;
		int hi = Math.min(lo + 1, PROFIT_GRADIENT.length - 1);
		float frac = idx - lo;
		return lerpRgb(PROFIT_GRADIENT[lo], PROFIT_GRADIENT[hi], frac) | 0xFF000000;
	}

	private static int lerpRgb(int from, int to, float t) {
		int r = (int) ((((from >> 16) & 0xFF) * (1f - t)) + (((to >> 16) & 0xFF) * t));
		int g = (int) ((((from >> 8) & 0xFF) * (1f - t)) + (((to >> 8) & 0xFF) * t));
		int b = (int) (((from & 0xFF) * (1f - t)) + ((to & 0xFF) * t));
		return (r << 16) | (g << 8) | b;
	}
}
