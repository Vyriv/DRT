package dev.vy.drt.client.screen;

/**
 * Custom Layout dialog geometry in scaled GUI pixels. Width follows the screen within sensible bounds; height
 * is built from the content (header, a nine line editor, the reference block and the footer) instead of a share
 * of the screen, shrinking the editor and then scrolling the reference when the window is short.
 */
final class CustomLayoutEditorLayout {
	static final int SCREEN_MARGIN = 8;
	static final int MIN_W = 420;
	static final int MAX_W = 760;
	static final double WIDTH_RATIO = 0.70D;
	static final int PAD = 10;
	static final int TITLE_GAP = 6;
	static final int LABEL_GAP = 3;
	static final int KEY_GAP = 5;
	static final int FOOTER_GAP = 4;
	static final int BUTTON_H = 18;
	static final int BOX_GAP = 8;
	static final int EDITOR_PAD = 4;
	static final int PREFERRED_EDITOR_LINES = 9;
	static final int MIN_EDITOR_LINES = 4;
	/** Editor share of the content width, in percent; the preview gets the rest. */
	static final int EDITOR_SHARE = 55;

	record Height(int winH, int editorLines, int editorBoxH, int keyViewportH, boolean keyScrolls) {
	}

	private CustomLayoutEditorLayout() {
	}

	static int dialogWidth(int screenW) {
		int maxW = Math.max(1, screenW - SCREEN_MARGIN * 2);
		int target = Math.max(MIN_W, Math.min(MAX_W, (int) Math.round(screenW * WIDTH_RATIO)));
		return Math.min(maxW, target);
	}

	static int contentWidth(int winW) {
		return Math.max(1, winW - PAD * 2);
	}

	static int editorWidth(int contentW) {
		return Math.max(1, (contentW - BOX_GAP) * EDITOR_SHARE / 100);
	}

	static int previewWidth(int contentW) {
		return Math.max(1, contentW - BOX_GAP - editorWidth(contentW));
	}

	static int headerHeight(int lineHeight) {
		return PAD + lineHeight + TITLE_GAP + lineHeight + LABEL_GAP;
	}

	static int editorBoxHeight(int lineHeight, int lines) {
		return (lineHeight + 1) * Math.max(1, lines) + EDITOR_PAD * 2;
	}

	static int fixedHeight(int lineHeight) {
		return headerHeight(lineHeight) + KEY_GAP + FOOTER_GAP + BUTTON_H + PAD;
	}

	static Height height(int screenH, int lineHeight, int keyHeight) {
		int maxH = Math.max(1, screenH - SCREEN_MARGIN * 2);
		int fixed = fixedHeight(lineHeight);
		int key = Math.max(0, keyHeight);
		int lines = PREFERRED_EDITOR_LINES;
		while (lines > MIN_EDITOR_LINES && fixed + editorBoxHeight(lineHeight, lines) + key > maxH) lines--;
		int box = editorBoxHeight(lineHeight, lines);
		int minKeyView = Math.min(key, (lineHeight + 1) * 3);
		int keyView = Math.max(minKeyView, Math.min(key, maxH - fixed - box));
		return new Height(fixed + box + keyView, lines, box, keyView, keyView < key);
	}
}
