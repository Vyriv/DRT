package dev.vy.drt.client.screen;

import dev.vy.drt.client.render.DrtUiText;
import dev.vy.drt.DungeonRunTracker;
import dev.vy.drt.client.overlay.CustomOverlayParser;
import dev.vy.drt.client.overlay.OverlayLayout;
import dev.vy.drt.client.overlay.OverlayLayouts;
import dev.vy.drt.client.overlay.OverlayPreset;
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
	};

	private static final KeyRow[] TIME_ROWS = {
		new KeyRow("{time.session}", "Session run time"),
		new KeyRow("{time.total}", "Lifetime run time"),
	};

	private static final KeyRow[] COLOUR_ROWS = {
		new KeyRow("<#RRGGBB>", "Begin hex colour"),
		new KeyRow("</#>", "End current colour"),
	};

	private static final KeyRow[] GRADIENT_ROWS = {
		new KeyRow("<gradient:#RRGGBB:#RRGGBB>", "Begin two-colour gradient"),
		new KeyRow("</gradient>", "End current gradient"),
	};

	private static final KeyRow[] PROFIT_ROWS = {
		new KeyRow("{profit.total}", "Lifetime profit"),
		new KeyRow("{profit.session}", "Session profit"),
		new KeyRow("{profit.run}", "Profit per run"),
		new KeyRow("{profit.hour}", "Profit per hour"),
	};

	private static final KeyGroup VARIABLES = new KeyGroup("Run & General", VARIABLE_ROWS);
	private static final KeyGroup TIME = new KeyGroup("Time", TIME_ROWS);
	private static final KeyGroup PROFIT = new KeyGroup("Profit", PROFIT_ROWS);
	private static final KeyGroup COLOURS = new KeyGroup("Colour", COLOUR_ROWS);
	private static final KeyGroup GRADIENTS = new KeyGroup("Gradient", GRADIENT_ROWS);
	private static final List<List<KeyGroup>> WIDE_KEY_COLUMNS = List.of(List.of(VARIABLES), List.of(TIME, PROFIT), List.of(COLOURS, GRADIENTS));
	private static final List<List<KeyGroup>> NARROW_KEY_COLUMNS = List.of(List.of(VARIABLES, TIME), List.of(PROFIT, COLOURS, GRADIENTS));
	private static final int KEY_COLUMN_GAP = 14;
	private static final int KEY_DESC_GAP = 6;
	private static final int STACK_INDENT = 8;
	private static final int PAD = CustomLayoutEditorLayout.PAD;
	private static final int BOX_GAP = CustomLayoutEditorLayout.BOX_GAP;
	private static final int SELECTION = 0xFF2B3E72;
	private static final int VALID = 0xFF8BCF9A;

	private final DungeonRunTrackerFeature trackerFeature;
	private final Screen parent;
	private final List<ClickTarget> clickTargets = new ArrayList<>();

	private String draft;
	private int caret;
	/** Other end of the selection, or -1 when nothing is selected. */
	private int selectionAnchor = -1;
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
	private int contentX;
	private int contentW;
	private int keyX;
	private int keyY;
	private int keyViewportH;
	private int keyContentH;
	private int keyScroll;
	private int footerY;
	private List<List<KeyGroup>> keyColumns = WIDE_KEY_COLUMNS;
	private int keyColumnW;
	private int revealedEditorW = -1;
	private int revealedEditorH = -1;
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

		int titleY = oy + PAD;
		g.text(font, "Custom Layout", contentX, titleY, TEXT);
		int labelY = titleY + font.lineHeight + CustomLayoutEditorLayout.TITLE_GAP;
		g.text(font, "Editor", editorX, labelY, MUTED);
		g.text(font, "Preview", previewX, labelY, MUTED);
		String scaleNote = "HUD scale " + trackerFeature.getHudScalePercent() + "%";
		g.text(font, scaleNote, previewX + previewW - font.width(scaleNote), labelY, DIM);
		drawEditor(g, mouseX, mouseY);
		drawPreview(g, previewX, previewY, previewW, previewH);

		g.enableScissor(ox + 1, keyY - 1, ox + winW - 1, keyY + keyViewportH);
		try {
			drawVariableKey(g, keyX, keyY - keyScroll, keyY, keyY + keyViewportH);
		} finally {
			g.disableScissor();
		}
		if (keyContentH > keyViewportH) drawScrollbar(g, ox + winW - 5, keyY, keyViewportH, keyScroll, keyContentH);

		drawFooter(g);
		drawHoveredTooltip(g, mouseX, mouseY);
	}

	private void drawFooter(GuiGraphicsExtractor g) {
		int btnH = CustomLayoutEditorLayout.BUTTON_H;
		int backW = Math.max(48, font.width("Back") + 20);
		int resetW = Math.max(52, font.width("Reset") + 20);
		int saveW = Math.max(58, font.width("Save") + 24);
		int saveX = contentX + contentW - saveW;
		drawButton(g, contentX, footerY, backW, btnH, "Back", false, this::goBack, "Return to overlay presets", MUTED);
		drawButton(g, contentX + backW + 4, footerY, resetW, btnH, "Reset", false, this::resetDraft, "Restore the default custom template");
		drawButton(g, saveX, footerY, saveW, btnH, "Save", false, this::saveDraft, "Save and activate Custom overlay", GREEN);
		if (statusMessage.isBlank()) return;
		int statusX = contentX + backW + 4 + resetW + 10;
		int statusRight = saveX - 10;
		int dotY = footerY + btnH / 2 - 2;
		g.fill(statusX, dotY, statusX + 4, dotY + 4, statusColor);
		int textX = statusX + 8;
		int textW = Math.max(8, statusRight - textX);
		String shown = ellipsize(statusMessage, textW);
		g.text(font, shown, textX, DrtUiText.centeredTextY(font, footerY, btnH), statusColor);
		if (!shown.equals(statusMessage)) {
			clickTargets.add(new ClickTarget(textX, footerY, Math.min(textW, font.width(shown)), btnH, -2, () -> {}, statusMessage));
		}
	}

	private static void drawScrollbar(GuiGraphicsExtractor g, int x, int y, int viewH, int scroll, int contentH) {
		if (contentH <= viewH || viewH <= 0) return;
		int thumbH = Math.max(10, viewH * viewH / contentH);
		int maxScroll = Math.max(1, contentH - viewH);
		int thumbY = y + (viewH - thumbH) * Math.min(scroll, maxScroll) / maxScroll;
		g.fill(x, y, x + 2, y + viewH, 0x3335385D);
		g.fill(x, thumbY, x + 2, thumbY + thumbH, BORDER_ACTIVE);
	}

	/** Three reference columns when every group fits a third of the width, otherwise two. */
	private List<List<KeyGroup>> keyColumnsFor(int availableW) {
		int wideW = (availableW - KEY_COLUMN_GAP * 2) / 3;
		for (List<KeyGroup> column : WIDE_KEY_COLUMNS) {
			for (KeyGroup group : column) {
				if (minGroupWidth(group) > wideW) return NARROW_KEY_COLUMNS;
			}
		}
		return WIDE_KEY_COLUMNS;
	}

	private int columnWidthFor(List<List<KeyGroup>> columns, int availableW) {
		int count = Math.max(1, columns.size());
		return Math.max(1, (availableW - KEY_COLUMN_GAP * (count - 1)) / count);
	}

	private int groupKeyWidth(KeyGroup group) {
		int max = 0;
		for (KeyRow row : group.rows()) if (!row.blank) max = Math.max(max, font.width(row.key));
		return max;
	}

	private int groupDescWidth(KeyGroup group) {
		int max = 0;
		for (KeyRow row : group.rows()) if (!row.blank) max = Math.max(max, font.width(row.description));
		return max;
	}

	private int inlineGroupWidth(KeyGroup group) {
		return Math.max(font.width(group.title()), groupKeyWidth(group) + KEY_DESC_GAP + groupDescWidth(group));
	}

	private int stackedGroupWidth(KeyGroup group) {
		return Math.max(font.width(group.title()), Math.max(groupKeyWidth(group), STACK_INDENT + groupDescWidth(group)));
	}

	private int minGroupWidth(KeyGroup group) {
		return Math.min(inlineGroupWidth(group), stackedGroupWidth(group));
	}

	/** Groups whose keys and descriptions do not fit side by side put each description under its key. */
	private boolean stacked(KeyGroup group, int columnW) {
		return inlineGroupWidth(group) > columnW;
	}

	private int groupHeight(KeyGroup group, int columnW) {
		int rowH = font.lineHeight + 1;
		boolean stack = stacked(group, columnW);
		int h = rowH + 2;
		for (KeyRow row : group.rows()) h += row.blank ? rowH / 2 : stack ? rowH * 2 : rowH;
		return h;
	}

	private int keyBlockHeight(List<List<KeyGroup>> columns, int columnW) {
		int rowH = font.lineHeight + 1;
		int max = 0;
		for (List<KeyGroup> column : columns) {
			int h = 0;
			for (int i = 0; i < column.size(); i++) {
				if (i > 0) h += rowH / 2;
				h += groupHeight(column.get(i), columnW);
			}
			max = Math.max(max, h);
		}
		return max;
	}

	private void drawVariableKey(GuiGraphicsExtractor g, int x, int y, int clipTop, int clipBottom) {
		int rowH = font.lineHeight + 1;
		int columnX = x;
		for (List<KeyGroup> column : keyColumns) {
			int rowY = y;
			for (int i = 0; i < column.size(); i++) {
				KeyGroup group = column.get(i);
				if (i > 0) rowY += rowH / 2;
				g.text(font, group.title(), columnX, rowY, MUTED);
				rowY = drawKeyRows(g, group, columnX, columnX + keyColumnW, rowY + rowH + 2, rowH, clipTop, clipBottom);
			}
			columnX += keyColumnW + KEY_COLUMN_GAP;
		}
	}

	private int drawKeyRows(GuiGraphicsExtractor g, KeyGroup group, int keyX, int colEnd, int startY, int rowH, int clipTop, int clipBottom) {
		boolean stack = stacked(group, colEnd - keyX);
		int descX = stack ? keyX + STACK_INDENT : keyX + groupKeyWidth(group) + KEY_DESC_GAP;
		int rowY = startY;
		for (KeyRow row : group.rows()) {
			if (row.blank) {
				rowY += rowH / 2;
				continue;
			}
			int entryH = stack ? rowH * 2 : rowH;
			int hitW = Math.max(1, colEnd - keyX + 4);
			boolean visible = rowY - 1 >= clipTop && rowY + entryH <= clipBottom;
			boolean hovered = visible && contains(keyX - 2, rowY - 1, hitW, entryH, lastMouseX, lastMouseY);
			g.text(font, row.key, keyX, rowY, hovered ? GOLD : TEXT);
			g.text(font, ellipsize(row.description, Math.max(8, colEnd - descX)), descX, stack ? rowY + rowH : rowY, DIM);
			String snippet = row.key;
			// Rows scrolled out of the reference area are not clickable.
			if (visible) {
				clickTargets.add(new ClickTarget(keyX - 2, rowY - 1, hitW, entryH, 0, () -> insertSnippet(snippet), "Insert " + snippet));
			}
			rowY += entryH;
		}
		return rowY;
	}

	private int editorVisibleLines() {
		return Math.max(1, (editorH - CustomLayoutEditorLayout.EDITOR_PAD * 2) / Math.max(1, font.lineHeight + 1));
	}

	private void clampEditorScroll() {
		int maxScroll = Math.max(0, draftLines().length - editorVisibleLines());
		scrollLine = Math.max(0, Math.min(scrollLine, maxScroll));
	}

	private void drawEditor(GuiGraphicsExtractor g, int mouseX, int mouseY) {
		clampCaret();
		if (editorW != revealedEditorW || editorH != revealedEditorH) {
			revealedEditorW = editorW;
			revealedEditorH = editorH;
			ensureCaretVisible();
		}
		clampEditorScroll();

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
			boolean selecting = hasSelection();
			int selStart = selectionStart();
			int selEnd = selectionEnd();
			int lineStart = indexAt(scrollLine, 0);
			for (int visible = 0; visible < maxLines; visible++) {
				int lineIndex = scrollLine + visible;
				if (lineIndex >= lines.length) break;
				String line = lines[lineIndex];
				int lineY = textTop + visible * lineStep;
				int lineEnd = lineStart + line.length();
				if (selecting && selStart <= lineEnd && selEnd > lineStart) {
					int from = Math.max(selStart, lineStart) - lineStart;
					int to = Math.min(selEnd, lineEnd) - lineStart;
					int x0 = textLeft - scrollCol + font.width(line.substring(0, from));
					int x1 = textLeft - scrollCol + font.width(line.substring(0, to));
					// A selected line break shows as a short block past the end of the line.
					if (selEnd > lineEnd && lineIndex < lines.length - 1) x1 += 3;
					if (x1 > x0) g.fill(x0, lineY - 1, x1, lineY + font.lineHeight, SELECTION);
				}
				g.text(font, line.isEmpty() ? " " : line, textLeft - scrollCol, lineY, TEXT);
				lineStart = lineEnd + 1;
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
		if (lines.length > maxLines) {
			drawScrollbar(g, editorX + editorW - 4, editorY + 2, editorH - 4, scrollLine * lineStep, lines.length * lineStep);
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

		g.fill(x, y, x + w, y + h, PANEL_ALT);
		border(g, x, y, w, h, BORDER);
		if (preview != null) {
			// Real HUD size, anchored top left like the HUD itself: drawn at the HUD scale and clipped rather than shrunk.
			g.enableScissor(x + 1, y + 1, x + w - 1, y + h - 1);
			try {
				DrtOverlayEditorScreen.renderPreviewAt(g, font, preview, x + 6, y + 6, Math.max(1, w - 12), Math.max(1, h - 12),
					trackerFeature.getHudScalePercent() / 100F, false);
			} finally {
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
			statusMessage = "Some labels are not available yet: " + String.join(", ", unknown);
			statusColor = WARNING;
		} else if (!badColors.isEmpty() || !markupWarnings.isEmpty()) {
			String detail = !markupWarnings.isEmpty() ? markupWarnings.get(0) : "Invalid colour tags are shown as plain text";
			statusMessage = detail;
			statusColor = WARNING;
		} else {
			statusMessage = "Valid layout";
			statusColor = VALID;
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
			: "Saved, but some labels are not available yet";
		statusColor = GREEN;
	}

	private void resetDraft() {
		draft = normalizeNewlines(OverlayLayouts.DEFAULT_CUSTOM_LAYOUT);
		caret = draft.length();
		selectionAnchor = -1;
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
		clampCaret();
		boolean selecting = hasSelection();
		CustomLayoutText.Edit edit = CustomLayoutText.replace(draft, selecting ? selectionStart() : caret, selecting ? selectionEnd() : caret, text);
		selectionAnchor = -1;
		draft = edit.text();
		setCaret(edit.caret(), true);
	}

	private boolean hasSelection() {
		return selectionAnchor >= 0 && Math.min(selectionAnchor, draft.length()) != caret;
	}

	private int selectionStart() {
		return hasSelection() ? Math.min(Math.min(selectionAnchor, draft.length()), caret) : caret;
	}

	private int selectionEnd() {
		return hasSelection() ? Math.max(Math.min(selectionAnchor, draft.length()), caret) : caret;
	}

	private void selectAll() {
		selectionAnchor = 0;
		setCaret(draft.length(), true);
	}

	private void deleteSelection() {
		CustomLayoutText.Edit edit = CustomLayoutText.replace(draft, selectionStart(), selectionEnd(), "");
		selectionAnchor = -1;
		draft = edit.text();
		setCaret(edit.caret(), true);
	}

	private void copySelection() {
		if (hasSelection() && minecraft != null) minecraft.keyboardHandler.setClipboard(draft.substring(selectionStart(), selectionEnd()));
	}

	private void pasteClipboard() {
		if (minecraft == null) return;
		String clipboard = minecraft.keyboardHandler.getClipboard();
		if (clipboard != null && !clipboard.isEmpty()) insertText(clipboard);
	}

	private void deleteBefore() {
		clampCaret();
		if (hasSelection()) {
			deleteSelection();
			return;
		}
		if (caret <= 0) return;
		draft = draft.substring(0, caret - 1) + draft.substring(caret);
		setCaret(caret - 1, true);
	}

	private void deleteAfter() {
		clampCaret();
		if (hasSelection()) {
			deleteSelection();
			return;
		}
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
		selectionAnchor = -1;
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
		winW = CustomLayoutEditorLayout.dialogWidth(width);
		contentW = CustomLayoutEditorLayout.contentWidth(winW);
		keyColumns = keyColumnsFor(contentW);
		keyColumnW = columnWidthFor(keyColumns, contentW);
		keyContentH = keyBlockHeight(keyColumns, keyColumnW);
		CustomLayoutEditorLayout.Height layout = CustomLayoutEditorLayout.height(height, font.lineHeight, keyContentH);
		winH = layout.winH();
		ox = (width - winW) / 2;
		oy = (height - winH) / 2;

		contentX = ox + PAD;
		editorX = contentX;
		editorY = oy + CustomLayoutEditorLayout.headerHeight(font.lineHeight);
		editorW = CustomLayoutEditorLayout.editorWidth(contentW);
		editorH = layout.editorBoxH();
		previewX = editorX + editorW + BOX_GAP;
		previewY = editorY;
		previewW = CustomLayoutEditorLayout.previewWidth(contentW);
		previewH = editorH;
		keyX = contentX;
		keyY = editorY + editorH + CustomLayoutEditorLayout.KEY_GAP;
		keyViewportH = layout.keyViewportH();
		keyScroll = Math.max(0, Math.min(keyScroll, keyContentH - keyViewportH));
		footerY = keyY + keyViewportH + CustomLayoutEditorLayout.FOOTER_GAP;
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
		int mx = (int) mouseX;
		int my = (int) mouseY;
		int steps = scrollY > 0 ? -1 : scrollY < 0 ? 1 : 0;
		if (steps == 0) return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
		if (contains(editorX, editorY, editorW, editorH, mx, my)) {
			scrollLine += steps;
			clampEditorScroll();
			return true;
		}
		if (keyContentH > keyViewportH && contains(ox, keyY, winW, keyViewportH, mx, my)) {
			keyScroll = Math.max(0, Math.min(keyContentH - keyViewportH, keyScroll + steps * (font.lineHeight + 1) * 2));
			return true;
		}
		return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
	}

	private void goBack() {
		if (minecraft != null && parent != null) {
			minecraft.gui.setScreen(parent);
			return;
		}
		super.onClose();
	}

	/** Closes the whole onboarding/editor stack instead of stepping back one screen. */
	private void closeFully() {
		if (parent instanceof DrtScreen && minecraft != null) {
			minecraft.gui.setScreen(parent);
			return;
		}
		if (parent instanceof DrtOverlayEditorScreen overlay) {
			overlay.closeFully();
			return;
		}
		if (parent instanceof DrtOnboardingScreen onboarding) {
			onboarding.onClose();
			return;
		}
		if (minecraft != null) {
			minecraft.gui.setScreen(null);
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
				// Skip the editor focus target - handled above with caret placement.
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
			if (event.isSelectAll()) {
				selectAll();
				return true;
			}
			if (event.isCopy()) {
				copySelection();
				return true;
			}
			if (event.isPaste()) {
				pasteClipboard();
				return true;
			}
			if (key == GLFW.GLFW_KEY_LEFT || key == GLFW.GLFW_KEY_RIGHT || key == GLFW.GLFW_KEY_UP
				|| key == GLFW.GLFW_KEY_DOWN || key == GLFW.GLFW_KEY_HOME || key == GLFW.GLFW_KEY_END) {
				selectionAnchor = -1;
			}
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
		g.text(font, label, x + (w - font.width(label)) / 2, DrtUiText.centeredTextY(font, y, h), textColor);
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

	private record KeyGroup(String title, KeyRow[] rows) {}

	private record KeyRow(String key, String description, boolean blank) {
		KeyRow(String key, String description) {
			this(key, description, false);
		}

		static KeyRow gap() {
			return new KeyRow("", "", true);
		}
	}
}
