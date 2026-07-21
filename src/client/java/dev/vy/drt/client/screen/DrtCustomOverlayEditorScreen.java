package dev.vy.drt.client.screen;

import dev.vy.drt.DungeonRunTracker;
import dev.vy.drt.client.overlay.CustomOverlayParser;
import dev.vy.drt.client.overlay.OverlayLayout;
import dev.vy.drt.client.overlay.OverlayLayouts;
import dev.vy.drt.client.overlay.OverlayLine;
import dev.vy.drt.client.overlay.OverlayPreset;
import dev.vy.drt.client.overlay.OverlaySegment;
import dev.vy.drt.client.tracker.DungeonRunTrackerFeature;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

public final class DrtCustomOverlayEditorScreen extends Screen {
	private static final int PANEL_BG = 0xF00D0D18;
	private static final int PANEL_ALT = 0xFF111222;
	private static final int BORDER = 0xFF35385D;
	private static final int BORDER_ACTIVE = 0xFF6268A8;
	private static final int TEXT = 0xFFE6E8F2;
	private static final int MUTED = 0xFF9AA4BD;
	private static final int DIM = 0xFF747E99;
	private static final int GREEN = 0xFF63D184;
	private static final int GOLD = 0xFFFFC857;
	private static final int WARNING = 0xFFFFAA55;
	private static final Pattern PLACEHOLDER = Pattern.compile("\\{([a-zA-Z0-9_.]+)}");
	private static final Pattern OPEN_COLOUR = Pattern.compile("<#[0-9A-Fa-f]{6}>");
	private static final Pattern BAD_OPEN_COLOUR = Pattern.compile("<#[^>]*>");

	private static final KeyRow[] VARIABLE_ROWS = {
		new KeyRow("{floor}", "Selected floor"),
		new KeyRow("{title}", "DRT title"),
		KeyRow.gap(),
		new KeyRow("{runs.total}", "Lifetime run count"),
		new KeyRow("{runs.session}", "Session run count"),
		new KeyRow("{runs.avg}", "Average run time"),
		new KeyRow("{runs.hour}", "Runs per hour"),
		new KeyRow("{runs.splus}", "S+ count"),
		new KeyRow("{runs.s}", "S count"),
		KeyRow.gap(),
		new KeyRow("{time.session}", "Session run time"),
		new KeyRow("{time.total}", "Lifetime run time"),
	};

	private static final KeyRow[] COLOUR_ROWS = {
		new KeyRow("<#RRGGBB>", "Begin hex colour"),
		new KeyRow("</#>", "End current colour"),
		KeyRow.gap(),
		new KeyRow("<gradient:#RRGGBB:#RRGGBB>", "Begin two-colour gradient"),
		new KeyRow("</gradient>", "End current gradient"),
	};

	private static final KeyRow[] PROFIT_ROWS = {
		new KeyRow("{profit.total}", "Lifetime profit"),
		new KeyRow("{profit.session}", "Session profit"),
		new KeyRow("{profit.run}", "Profit per run"),
		new KeyRow("{profit.hour}", "Profit per hour"),
	};

	private final DungeonRunTrackerFeature trackerFeature;
	private final Screen parent;
	private final List<ClickTarget> clickTargets = new ArrayList<>();

	private String draft;
	private int caret;
	private int preferredColumn = -1;
	private int scrollLine;
	private int scrollCol;
	private boolean editorFocused = true;
	private String statusMessage = "";
	private int statusColor = MUTED;
	private String lastDraftForStatus = "";

	private int ox;
	private int oy;
	private int winW;
	private int winH;
	private int editorX;
	private int editorY;
	private int editorW;
	private int editorH;
	private int previewX;
	private int previewY;
	private int previewW;
	private int previewH;
	private int lastMouseX;
	private int lastMouseY;

	public DrtCustomOverlayEditorScreen(DungeonRunTrackerFeature trackerFeature, Screen parent) {
		super(Component.literal("Custom Layout"));
		this.trackerFeature = trackerFeature;
		this.parent = parent;
		this.draft = trackerFeature.getCustomOverlayLayout();
		if (this.draft == null || this.draft.isBlank()) this.draft = OverlayLayouts.DEFAULT_CUSTOM_LAYOUT;
		this.draft = normalizeNewlines(this.draft);
		this.lastDraftForStatus = this.draft;
		this.caret = this.draft.length();
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	@Override
	public void extractBackground(GuiGraphicsExtractor guiGraphics, int mouseX, int mouseY, float partialTick) {
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
		lastMouseX = mouseX;
		lastMouseY = mouseY;
		updateLayout();
		clickTargets.clear();
		if (!draft.equals(lastDraftForStatus) && statusColor == GREEN) {
			statusMessage = "";
			statusColor = MUTED;
			lastDraftForStatus = draft;
		}

		g.fill(0, 0, width, height, 0xA0000000);
		g.fill(ox, oy, ox + winW, oy + winH, PANEL_BG);
		border(g, ox, oy, winW, winH, BORDER);

		g.text(font, "Custom Layout", ox + 12, oy + 10, TEXT);

		int y = oy + 28;
		int gap = 8;
		int contentW = winW - 24;
		// Give the editor most of the width; preview is a compact side sample.
		editorW = Math.max(220, (contentW * 2) / 3);
		previewW = Math.max(90, contentW - gap - editorW);
		editorH = 60;
		previewH = editorH;

		editorX = ox + 12;
		editorY = y + 12;
		previewX = editorX + editorW + gap;
		previewY = editorY;

		g.text(font, "Editor", editorX, y, MUTED);
		drawEditor(g, mouseX, mouseY);
		drawPreview(g, previewX, previewY, previewW, previewH);

		y = editorY + editorH + 10;
		y = drawVariableKey(g, y);

		if (!statusMessage.isBlank()) {
			g.text(font, statusMessage, ox + 12, oy + winH - 48, statusColor);
		}

		int btnY = oy + winH - 28;
		drawButton(g, ox + 12, btnY, 48, 18, "Back", false, this::goBack, "Return to overlay presets", MUTED);
		drawButton(g, ox + 68, btnY, 56, 18, "Reset", false, this::resetDraft, "Restore the default custom template");
		drawButton(g, ox + winW - 70, btnY, 58, 18, "Save", false, this::saveDraft, "Save and activate Custom overlay", GREEN);

		drawHoveredTooltip(g, mouseX, mouseY);
	}

	private int drawVariableKey(GuiGraphicsExtractor g, int y) {
		int rowH = font.lineHeight + 1;
		int headerH = rowH + 2;
		int colGap = 14;
		int leftX = ox + 14;
		int leftKeyW = measureKeyColumnWidth(VARIABLE_ROWS);
		int leftDescX = leftX + leftKeyW + 6;
		int leftColEnd = leftDescX + measureDescColumnWidth(VARIABLE_ROWS);

		int rightKeyW = measureKeyColumnWidth(COLOUR_ROWS, PROFIT_ROWS);
		int rightX = leftColEnd + colGap;
		int rightDescX = rightX + rightKeyW + 6;
		int rightColEnd = rightDescX + Math.max(measureDescColumnWidth(COLOUR_ROWS), measureDescColumnWidth(PROFIT_ROWS));

		g.text(font, "Variable Key", leftX, y, MUTED);
		g.text(font, "Colour Formatting", rightX, y, MUTED);

		int leftY = drawKeyRows(g, VARIABLE_ROWS, leftX, leftDescX, leftColEnd, y + headerH, rowH);
		int rightY = drawKeyRows(g, COLOUR_ROWS, rightX, rightDescX, rightColEnd, y + headerH, rowH);
		rightY += rowH / 2;
		g.text(font, "Profit", rightX, rightY, MUTED);
		rightY += headerH;
		rightY = drawKeyRows(g, PROFIT_ROWS, rightX, rightDescX, rightColEnd, rightY, rowH);

		return Math.max(leftY, rightY);
	}

	private int drawKeyRows(GuiGraphicsExtractor g, KeyRow[] rows, int keyX, int descX, int colEnd, int startY, int rowH) {
		int rowY = startY;
		for (KeyRow row : rows) {
			if (row.blank) {
				rowY += rowH / 2;
				continue;
			}
			boolean hovered = contains(keyX - 2, rowY - 1, Math.max(1, colEnd - keyX + 4), rowH, lastMouseX, lastMouseY);
			int keyColor = hovered ? GOLD : TEXT;
			g.text(font, row.key, keyX, rowY, keyColor);
			g.text(font, row.description, descX, rowY, DIM);
			String snippet = row.key;
			clickTargets.add(new ClickTarget(
				keyX - 2,
				rowY - 1,
				Math.max(1, colEnd - keyX + 4),
				rowH,
				0,
				() -> insertSnippet(snippet),
				"Insert " + snippet
			));
			rowY += rowH;
		}
		return rowY;
	}

	private int measureKeyBlockHeight() {
		int rowH = font.lineHeight + 1;
		int headerH = rowH + 2;
		int leftH = headerH + measureRowsHeight(VARIABLE_ROWS, rowH);
		int rightH = headerH
			+ measureRowsHeight(COLOUR_ROWS, rowH)
			+ rowH / 2
			+ headerH
			+ measureRowsHeight(PROFIT_ROWS, rowH);
		return Math.max(leftH, rightH);
	}

	private static int measureRowsHeight(KeyRow[] rows, int rowH) {
		int h = 0;
		for (KeyRow row : rows) {
			h += row.blank ? rowH / 2 : rowH;
		}
		return h;
	}

	private int measureKeyColumnWidth(KeyRow[]... groups) {
		int max = 0;
		for (KeyRow[] group : groups) {
			for (KeyRow row : group) {
				if (row.blank) continue;
				max = Math.max(max, font.width(row.key));
			}
		}
		return max;
	}

	private int measureDescColumnWidth(KeyRow[] rows) {
		int max = 0;
		for (KeyRow row : rows) {
			if (row.blank) continue;
			max = Math.max(max, font.width(row.description));
		}
		return max;
	}

	private void drawEditor(GuiGraphicsExtractor g, int mouseX, int mouseY) {
		clampCaret();
		ensureCaretVisible();

		boolean hovered = contains(editorX, editorY, editorW, editorH, mouseX, mouseY);
		g.fill(editorX, editorY, editorX + editorW, editorY + editorH, editorFocused ? 0xFF10172E : hovered ? 0xFF171B30 : PANEL_ALT);
		border(g, editorX, editorY, editorW, editorH, editorFocused ? BORDER_ACTIVE : BORDER);

		String[] lines = draftLines();
		int lineStep = font.lineHeight + 1;
		int maxLines = Math.max(1, (editorH - 8) / lineStep);
		int textLeft = editorX + 4;
		int textTop = editorY + 4;
		int textW = Math.max(1, editorW - 8);

		g.enableScissor(textLeft, textTop, textLeft + textW, editorY + editorH - 4);
		try {
			for (int visible = 0; visible < maxLines; visible++) {
				int lineIndex = scrollLine + visible;
				if (lineIndex >= lines.length) break;
				String line = lines[lineIndex];
				g.text(font, line.isEmpty() ? " " : line, textLeft - scrollCol, textTop + visible * lineStep, TEXT);
			}

			if (editorFocused && (System.currentTimeMillis() / 500L) % 2L == 0L) {
				CaretPos pos = caretPos();
				int visibleLine = pos.line - scrollLine;
				if (visibleLine >= 0 && visibleLine < maxLines) {
					String before = lines[pos.line].substring(0, Math.min(pos.column, lines[pos.line].length()));
					int caretX = textLeft - scrollCol + font.width(before);
					int caretY = textTop + visibleLine * lineStep;
					g.fill(caretX, caretY, caretX + 1, caretY + font.lineHeight, TEXT);
				}
			}
		} finally {
			g.disableScissor();
		}

		clickTargets.add(new ClickTarget(editorX, editorY, editorW, editorH, 0, () -> {
			editorFocused = true;
		}, null));
	}

	private void drawPreview(GuiGraphicsExtractor g, int x, int y, int w, int h) {
		Minecraft client = Minecraft.getInstance();
		OverlayLayout preview;
		try {
			preview = trackerFeature.buildPreviewLayout(client, OverlayPreset.CUSTOM, draft);
		} catch (RuntimeException ex) {
			DungeonRunTracker.LOGGER.warn("[DRT] Custom overlay preview failed while editing", ex);
			preview = null;
		}

		if (preview != null) {
			int maxW = Math.max(1, w);
			int maxH = Math.max(1, h);
			int boundsW = Math.max(1, preview.width + OverlayLayouts.SHADOW_PAD);

			float scale = Math.min(1.0F, (float) maxW / boundsW);
			if (!Float.isFinite(scale) || scale <= 0F) scale = 1.0F;
			scale = Math.max(0.7F, Math.min(1.0F, scale));

			g.enableScissor(x, y, x + maxW, y + maxH);
			var pose = g.pose();
			pose.pushMatrix();
			try {
				pose.translate(x, y);
				pose.scale(scale);
				for (OverlayLine line : preview.lines) {
					if (line == null || line.segments == null) continue;
					int drawXCursor = 0;
					for (OverlaySegment segment : line.segments) {
						if (segment == null || segment.text == null || segment.text.isEmpty()) continue;
						int drawX = segment.positioned() ? segment.x : drawXCursor;
						g.text(font, segment.text, drawX, line.y, segment.color, true);
						drawXCursor = drawX + font.width(segment.text);
					}
				}
			} finally {
				pose.popMatrix();
				g.disableScissor();
			}
		}

		updateValidationStatus();
	}

	private void updateValidationStatus() {
		if (statusColor == GREEN && statusMessage.startsWith("Saved")) return;
		List<String> unknown = findUnknownPlaceholders(draft);
		List<String> badColors = findMalformedColourTags(draft);
		List<String> markupWarnings = CustomOverlayParser.validateMarkup(draft);
		if (draft.isBlank()) {
			statusMessage = "Layout is empty";
			statusColor = WARNING;
		} else if (!unknown.isEmpty()) {
			statusMessage = "Unknown placeholders: " + String.join(", ", unknown);
			statusColor = WARNING;
		} else if (!badColors.isEmpty() || !markupWarnings.isEmpty()) {
			String detail = !markupWarnings.isEmpty() ? markupWarnings.get(0) : "Invalid colour tags are shown as plain text";
			statusMessage = detail;
			statusColor = WARNING;
		} else {
			statusMessage = "Valid layout";
			statusColor = MUTED;
		}
	}

	private void saveDraft() {
		if (draft == null || draft.isBlank()) {
			statusMessage = "Cannot save an empty layout";
			statusColor = WARNING;
			return;
		}
		trackerFeature.setCustomOverlayLayout(draft, true);
		lastDraftForStatus = draft;
		List<String> unknown = findUnknownPlaceholders(draft);
		statusMessage = unknown.isEmpty()
			? "Saved. Custom overlay active"
			: "Saved with unknown placeholders";
		statusColor = GREEN;
	}

	private void resetDraft() {
		draft = normalizeNewlines(OverlayLayouts.DEFAULT_CUSTOM_LAYOUT);
		caret = draft.length();
		preferredColumn = -1;
		scrollLine = 0;
		scrollCol = 0;
		statusMessage = "Reset to default template (not saved yet)";
		statusColor = GOLD;
		lastDraftForStatus = "";
	}

	private static String normalizeNewlines(String text) {
		return text == null ? "" : text.replace("\r\n", "\n").replace('\r', '\n');
	}

	private String[] draftLines() {
		return draft.split("\n", -1);
	}

	private void clampCaret() {
		if (caret < 0) caret = 0;
		if (caret > draft.length()) caret = draft.length();
	}

	private CaretPos caretPos() {
		clampCaret();
		String[] lines = draftLines();
		int remaining = caret;
		for (int i = 0; i < lines.length; i++) {
			int lineLen = lines[i].length();
			if (remaining <= lineLen) return new CaretPos(i, remaining);
			remaining -= lineLen + 1; // +1 for newline
		}
		int last = Math.max(0, lines.length - 1);
		return new CaretPos(last, lines[last].length());
	}

	private int indexAt(int line, int column) {
		String[] lines = draftLines();
		if (lines.length == 0) return 0;
		line = Math.max(0, Math.min(line, lines.length - 1));
		column = Math.max(0, Math.min(column, lines[line].length()));
		int index = 0;
		for (int i = 0; i < line; i++) index += lines[i].length() + 1;
		return index + column;
	}

	private void setCaret(int index, boolean rememberColumn) {
		caret = index;
		clampCaret();
		if (rememberColumn) preferredColumn = caretPos().column;
		ensureCaretVisible();
	}

	private void ensureCaretVisible() {
		if (draft == null) draft = "";
		int lineStep = Math.max(1, font.lineHeight + 1);
		int maxLines = Math.max(1, (editorH - 8) / lineStep);
		CaretPos pos = caretPos();
		String[] lines = draftLines();
		if (lines.length == 0 || pos.line < 0 || pos.line >= lines.length) return;
		if (pos.line < scrollLine) scrollLine = pos.line;
		if (pos.line >= scrollLine + maxLines) scrollLine = pos.line - maxLines + 1;
		if (scrollLine < 0) scrollLine = 0;

		String before = lines[pos.line].substring(0, Math.min(pos.column, lines[pos.line].length()));
		int caretPx = font.width(before);
		int viewW = Math.max(1, editorW - 8);
		if (caretPx < scrollCol) scrollCol = Math.max(0, caretPx - 16);
		if (caretPx > scrollCol + viewW - 4) scrollCol = Math.max(0, caretPx - viewW + 16);
	}

	private void insertSnippet(String snippet) {
		if (snippet == null || snippet.isBlank()) return;
		editorFocused = true;
		insertText(snippet);
	}

	private void insertText(String text) {
		if (text == null || text.isEmpty()) return;
		if (draft == null) draft = "";
		// Keep control characters out of the layout (except newline handled via Enter).
		StringBuilder cleaned = new StringBuilder(text.length());
		text.codePoints().forEach(cp -> {
			if (cp == '\n' || cp == '\t' || !Character.isISOControl(cp)) {
				cleaned.appendCodePoint(cp);
			}
		});
		text = cleaned.toString();
		if (text.isEmpty()) return;
		if (draft.length() + text.length() > 1200) {
			int room = 1200 - draft.length();
			if (room <= 0) return;
			text = text.substring(0, room);
		}
		clampCaret();
		draft = draft.substring(0, caret) + text + draft.substring(caret);
		setCaret(caret + text.length(), true);
	}

	private void deleteBefore() {
		clampCaret();
		if (caret <= 0) return;
		draft = draft.substring(0, caret - 1) + draft.substring(caret);
		setCaret(caret - 1, true);
	}

	private void deleteAfter() {
		clampCaret();
		if (caret >= draft.length()) return;
		draft = draft.substring(0, caret) + draft.substring(caret + 1);
		setCaret(caret, true);
	}

	private void moveLeft() {
		CaretPos pos = caretPos();
		if (pos.column <= 0) return;
		setCaret(caret - 1, true);
	}

	private void moveRight() {
		CaretPos pos = caretPos();
		String[] lines = draftLines();
		if (pos.column >= lines[pos.line].length()) return;
		setCaret(caret + 1, true);
	}

	private void moveUp() {
		CaretPos pos = caretPos();
		if (pos.line <= 0) {
			setCaret(0, false);
			return;
		}
		int col = preferredColumn >= 0 ? preferredColumn : pos.column;
		String[] lines = draftLines();
		int targetCol = Math.min(col, lines[pos.line - 1].length());
		caret = indexAt(pos.line - 1, targetCol);
		preferredColumn = col;
		ensureCaretVisible();
	}

	private void moveDown() {
		CaretPos pos = caretPos();
		String[] lines = draftLines();
		if (pos.line >= lines.length - 1) {
			setCaret(draft.length(), false);
			return;
		}
		int col = preferredColumn >= 0 ? preferredColumn : pos.column;
		int targetCol = Math.min(col, lines[pos.line + 1].length());
		caret = indexAt(pos.line + 1, targetCol);
		preferredColumn = col;
		ensureCaretVisible();
	}

	private void moveHome() {
		CaretPos pos = caretPos();
		setCaret(indexAt(pos.line, 0), true);
	}

	private void moveEnd() {
		CaretPos pos = caretPos();
		String[] lines = draftLines();
		setCaret(indexAt(pos.line, lines[pos.line].length()), true);
	}

	private void setCaretFromClick(int mouseX, int mouseY) {
		editorFocused = true;
		String[] lines = draftLines();
		int lineStep = font.lineHeight + 1;
		int maxLines = Math.max(1, (editorH - 8) / lineStep);
		int localY = mouseY - (editorY + 4);
		int visibleLine = Math.max(0, Math.min(maxLines - 1, localY / lineStep));
		int line = Math.min(lines.length - 1, scrollLine + visibleLine);
		if (line < 0) {
			setCaret(0, true);
			return;
		}

		int localX = mouseX - (editorX + 4) + scrollCol;
		String lineText = lines[line];
		int column = 0;
		int bestDist = Integer.MAX_VALUE;
		for (int i = 0; i <= lineText.length(); i++) {
			int width = font.width(lineText.substring(0, i));
			int dist = Math.abs(width - localX);
			if (dist < bestDist) {
				bestDist = dist;
				column = i;
			}
		}
		setCaret(indexAt(line, column), true);
	}

	private static List<String> findUnknownPlaceholders(String text) {
		List<String> unknown = new ArrayList<>();
		if (text == null) return unknown;
		Matcher matcher = PLACEHOLDER.matcher(text);
		while (matcher.find()) {
			String key = matcher.group(1).toLowerCase(Locale.ROOT);
			if (!CustomOverlayParser.isKnownPlaceholder(key) && !unknown.contains(key)) {
				unknown.add(key);
			}
		}
		return unknown;
	}

	private static List<String> findMalformedColourTags(String text) {
		List<String> bad = new ArrayList<>();
		if (text == null) return bad;
		Matcher matcher = BAD_OPEN_COLOUR.matcher(text);
		while (matcher.find()) {
			String tag = matcher.group();
			if (OPEN_COLOUR.matcher(tag).matches()) continue;
			if (!bad.contains(tag)) bad.add(tag);
		}
		return bad;
	}

	private void updateLayout() {
		winW = Math.max(520, Math.min(620, width - 24));
		// title(28) + editor label/box(~72) + gap(10) + keys + status(20) + buttons(28) + pad(8)
		int contentH = 28 + 12 + 60 + 10 + measureKeyBlockHeight() + 20 + 28 + 8;
		winH = Math.max(260, Math.min(height - 24, contentH));
		if (winW > width - 8) winW = Math.max(1, width - 8);
		if (winH > height - 8) winH = Math.max(1, height - 8);
		ox = (width - winW) / 2;
		oy = (height - winH) / 2;
	}

	private void goBack() {
		if (minecraft != null && parent != null) {
			minecraft.setScreen(parent);
			return;
		}
		super.onClose();
	}

	/** Closes the whole onboarding/editor stack instead of stepping back one screen. */
	private void closeFully() {
		if (parent instanceof DrtOverlayEditorScreen overlay) {
			overlay.closeFully();
			return;
		}
		if (parent instanceof DrtOnboardingScreen onboarding) {
			onboarding.onClose();
			return;
		}
		if (minecraft != null) {
			minecraft.setScreen(null);
			return;
		}
		super.onClose();
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean isRepeat) {
		int mx = (int) event.x();
		int my = (int) event.y();
		if (event.button() == 0 && contains(editorX, editorY, editorW, editorH, mx, my)) {
			setCaretFromClick(mx, my);
			return true;
		}

		boolean hit = false;
		for (int i = clickTargets.size() - 1; i >= 0; i--) {
			ClickTarget target = clickTargets.get(i);
			if ((target.button == -1 || target.button == event.button()) && contains(target.x, target.y, target.w, target.h, mx, my)) {
				// Skip the editor focus target — handled above with caret placement.
				if (target.x == editorX && target.y == editorY && target.w == editorW && target.h == editorH) {
					continue;
				}
				target.action.run();
				hit = true;
				break;
			}
		}
		// Keep typing focus when clicking keys/empty panel space; only buttons steal via their actions.
		return hit || super.mouseClicked(event, isRepeat);
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		if (editorFocused) {
			int key = event.key();
			if (key == GLFW.GLFW_KEY_BACKSPACE) {
				deleteBefore();
				return true;
			}
			if (key == GLFW.GLFW_KEY_DELETE) {
				deleteAfter();
				return true;
			}
			if (key == GLFW.GLFW_KEY_LEFT) {
				moveLeft();
				return true;
			}
			if (key == GLFW.GLFW_KEY_RIGHT) {
				moveRight();
				return true;
			}
			if (key == GLFW.GLFW_KEY_UP) {
				moveUp();
				return true;
			}
			if (key == GLFW.GLFW_KEY_DOWN) {
				moveDown();
				return true;
			}
			if (key == GLFW.GLFW_KEY_HOME) {
				moveHome();
				return true;
			}
			if (key == GLFW.GLFW_KEY_END) {
				moveEnd();
				return true;
			}
			if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) {
				insertText("\n");
				return true;
			}
			if (key == GLFW.GLFW_KEY_ESCAPE) {
				closeFully();
				return true;
			}
		}
		if (event.key() == GLFW.GLFW_KEY_ESCAPE) {
			closeFully();
			return true;
		}
		return super.keyPressed(event);
	}

	@Override
	public boolean charTyped(CharacterEvent event) {
		if (!editorFocused) return super.charTyped(event);
		try {
			String typed = event.codepointAsString();
			if (typed == null || typed.isEmpty()) return true;
			insertText(typed);
		} catch (RuntimeException ex) {
			DungeonRunTracker.LOGGER.warn("[DRT] Custom overlay editor charTyped failed", ex);
		}
		return true;
	}

	@Override
	public void onClose() {
		goBack();
	}

	private void drawButton(GuiGraphicsExtractor g, int x, int y, int w, int h, String label, boolean active, Runnable onClick, String tooltip) {
		drawButton(g, x, y, w, h, label, active, onClick, tooltip, TEXT);
	}

	private void drawButton(GuiGraphicsExtractor g, int x, int y, int w, int h, String label, boolean active, Runnable onClick, String tooltip, int textColor) {
		g.fill(x, y, x + w, y + h, active ? 0xFF23375B : 0xFF171B30);
		border(g, x, y, w, h, active ? BORDER_ACTIVE : BORDER);
		g.text(font, label, x + (w - font.width(label)) / 2, y + (h - font.lineHeight) / 2, textColor);
		clickTargets.add(new ClickTarget(x, y, w, h, 0, onClick, tooltip));
	}

	private void drawHoveredTooltip(GuiGraphicsExtractor g, int mouseX, int mouseY) {
		for (int i = clickTargets.size() - 1; i >= 0; i--) {
			ClickTarget target = clickTargets.get(i);
			if (target.tooltip == null || target.tooltip.isBlank()) continue;
			if (!contains(target.x, target.y, target.w, target.h, mouseX, mouseY)) continue;
			drawTooltip(g, target.tooltip, mouseX, mouseY);
			return;
		}
	}

	private void drawTooltip(GuiGraphicsExtractor g, String text, int mouseX, int mouseY) {
		int pad = 4;
		int tw = font.width(text);
		int tx = mouseX + 8;
		int ty = mouseY - font.lineHeight - pad * 2;
		if (tx + tw + pad * 2 > width) tx = width - tw - pad * 2;
		if (ty < 0) ty = mouseY + 12;
		g.fill(tx - pad, ty - pad, tx + tw + pad, ty + font.lineHeight + pad, 0xDD000000);
		border(g, tx - pad, ty - pad, tw + pad * 2, font.lineHeight + pad * 2, BORDER_ACTIVE);
		g.text(font, text, tx, ty, 0xFFCCCCFF, true);
	}

	private String ellipsize(String value, int maxWidth) {
		if (value == null) return "";
		if (font.width(value) <= maxWidth) return value;
		String ellipsis = "...";
		int budget = Math.max(0, maxWidth - font.width(ellipsis));
		StringBuilder sb = new StringBuilder();
		for (int i = 0; i < value.length(); i++) {
			char c = value.charAt(i);
			if (font.width(sb.toString() + c) > budget) break;
			sb.append(c);
		}
		return sb + ellipsis;
	}

	private static void border(GuiGraphicsExtractor g, int x, int y, int w, int h, int color) {
		g.fill(x, y, x + w, y + 1, color);
		g.fill(x, y + h - 1, x + w, y + h, color);
		g.fill(x, y, x + 1, y + h, color);
		g.fill(x + w - 1, y, x + w, y + h, color);
	}

	private static boolean contains(int x, int y, int w, int h, int mx, int my) {
		return mx >= x && mx < x + w && my >= y && my < y + h;
	}

	private record ClickTarget(int x, int y, int w, int h, int button, Runnable action, String tooltip) {}

	private record CaretPos(int line, int column) {}

	private record KeyRow(String key, String description, boolean blank) {
		KeyRow(String key, String description) {
			this(key, description, false);
		}

		static KeyRow gap() {
			return new KeyRow("", "", true);
		}
	}
}
