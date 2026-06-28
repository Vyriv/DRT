package dev.vy.drt.client.screen;

import dev.vy.drt.config.DrtConfigManager;
import dev.vy.drt.config.DungeonFloor;
import dev.vy.drt.config.DungeonLootEntry;
import dev.vy.drt.config.DungeonRunRecord;
import dev.vy.drt.client.tracker.DungeonRunTrackerFeature;
import dev.vy.drt.client.tracker.NeuItemResolver;
import dev.vy.drt.price.DungeonProfitPricing;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.DyedItemColor;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import org.lwjgl.glfw.GLFW;

public final class DungeonLootScreen extends Screen {
	private static final double PANEL_W_RATIO = 0.72D;
	private static final double PANEL_H_RATIO = 0.77D;
	private static final int MIN_PANEL_W = 620;
	private static final int MIN_PANEL_H = 360;
	private static final int MAX_PANEL_W = 1380;
	private static final int MAX_PANEL_H = 800;
	private static final int SCREEN_EDGE_MARGIN = 12;
	private static final int MARGIN = 10;
	private static final int TAB_H = 18;
	private static final int HEADER_H = 34;
	private static final int SECTION_H = 18;
	private static final int MIN_LIST_W = 184;
	private static final int MAX_LIST_W = 220;
	private static final int ROW_H = 20;
	private static final int FOOTER_H = 28;
	private static final int PANEL_BG = 0xFF111120;
	private static final int PANEL_BG_ALT = 0xFF141425;
	private static final int PANEL_BORDER = 0xFF2D2D50;
	private static final int PANEL_BORDER_HOVER = 0xFF494978;
	private static final int TEXT_PRIMARY = 0xFFE5E7F0;
	private static final int TEXT_SECONDARY = 0xFF9AA4BD;
	private static final int TEXT_MUTED = 0xFF78829D;

	// null = All
	private DungeonFloor selectedTab = null;
	private List<DungeonFloor> availableTabs = new ArrayList<>();
	private List<DungeonRunRecord> allHistory = List.of();
	private List<DungeonRunRecord> tabHistory = List.of();

	private int selectedRunIndex = -1;
	private String selectedLootKey = "";
	private String lootSearchBuffer = "";
	private long lootSearchLastTypedMillis;
	private int listScroll;
	private int tableScroll;
	private long clearConfirmUntilMillis;
	private LootSortColumn lootSortColumn;
	private boolean lootSortAscending;

	private final List<ClickTarget> clickTargets = new ArrayList<>();

	// Layout coords recomputed each frame
	private int ox, oy;
	private int winW, winH;
	private int listX, listY, listW, listH;
	private int tableX, tableY, tableW, tableH;
	private int tabBarY;

	public DungeonLootScreen() {
		super(Component.literal("Dungeon Loot History"));
	}

	@Override
	protected void init() {
		String saved = DrtConfigManager.getDungeonSelectedFloor();
		if (saved != null && !saved.isBlank()) {
			try { selectedTab = DungeonFloor.valueOf(saved); } catch (IllegalArgumentException ignored) { selectedTab = null; }
		}
		reloadHistory();
	}

	@Override
	public void onClose() {
		DrtConfigManager.updateSelectedFloor(selectedTab == null ? null : selectedTab.name());
		super.onClose();
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	@Override
	public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
		g.fill(0, 0, width, height, 0xAA000000);
		updateLayout();

		clickTargets.clear();
		drawWindow(g);
		drawTabs(g, mouseX, mouseY);
		drawHeader(g, headerContentY());
		drawSectionLabels(g, headerContentY() + HEADER_H);
		drawRunList(g, mouseX, mouseY);
		drawLootTable(g, mouseX, mouseY);
		drawFooter(g, mouseX, mouseY);
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean isRepeat) {
		for (int i = clickTargets.size() - 1; i >= 0; i--) {
			ClickTarget t = clickTargets.get(i);
			if (t.button == event.button() && contains(t.x, t.y, t.w, t.h, (int) event.x(), (int) event.y())) {
				t.action.run();
				return true;
			}
		}
		return super.mouseClicked(event, isRepeat);
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
		updateLayout();
		int delta = (int) Math.copySign(ROW_H, scrollY);
		if (contains(listX, listY, listW, listH, (int) mouseX, (int) mouseY)) {
			int maxScroll = Math.max(0, (tabHistory.size() + 1) * ROW_H - listH);
			listScroll = clamp(listScroll - delta, 0, maxScroll);
			return true;
		}
		if (contains(tableX, tableY, tableW, tableH, (int) mouseX, (int) mouseY)) {
			int maxScroll = Math.max(0, buildDisplayRows().size() * ROW_H - tableH + 6);
			tableScroll = clamp(tableScroll - delta, 0, maxScroll);
			return true;
		}
		return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		if (event.key() == GLFW.GLFW_KEY_ESCAPE) {
			onClose();
			return true;
		}
		if (!selectedLootKey.isBlank() && event.key() == GLFW.GLFW_KEY_BACKSPACE && !lootSearchBuffer.isEmpty()) {
			lootSearchBuffer = lootSearchBuffer.substring(0, lootSearchBuffer.length() - 1);
			searchLootRows();
			return true;
		}
		return super.keyPressed(event);
	}

	@Override
	public boolean charTyped(CharacterEvent event) {
		if (selectedLootKey.isBlank()) {
			return super.charTyped(event);
		}
		String typed = event.codepointAsString();
		if (typed == null || typed.isBlank()) {
			return super.charTyped(event);
		}
		int codePoint = typed.codePointAt(0);
		if (Character.isISOControl(codePoint)) {
			return super.charTyped(event);
		}

		long now = System.currentTimeMillis();
		if (now - lootSearchLastTypedMillis > 1000L) {
			lootSearchBuffer = "";
		}
		lootSearchLastTypedMillis = now;
		lootSearchBuffer += typed.toLowerCase(Locale.ROOT);
		searchLootRows();
		return true;
	}

	// ── Drawing ──────────────────────────────────────────────────────────────

	private void updateLayout() {
		int availableW = Math.max(1, width - SCREEN_EDGE_MARGIN * 2);
		int availableH = Math.max(1, height - SCREEN_EDGE_MARGIN * 2);
		winW = Math.min(availableW, clamp((int) Math.round(width * PANEL_W_RATIO), MIN_PANEL_W, MAX_PANEL_W));
		winH = Math.min(availableH, clamp((int) Math.round(height * PANEL_H_RATIO), MIN_PANEL_H, MAX_PANEL_H));
		ox = (width - winW) / 2;
		oy = (height - winH) / 2;

		tabBarY = oy + MARGIN;
		int contentY = headerContentY();
		listW = clamp((int) Math.round(winW * 0.29D), MIN_LIST_W, MAX_LIST_W);
		listX = ox + MARGIN;
		listY = contentY + HEADER_H + SECTION_H;
		listH = Math.max(ROW_H, winH - (listY - oy) - MARGIN - FOOTER_H);
		tableX = listX + listW + MARGIN;
		tableY = listY;
		tableW = Math.max(120, winW - listW - MARGIN * 3);
		tableH = listH;
	}

	private int headerContentY() {
		return tabBarY + TAB_H + 4;
	}

	private void drawWindow(GuiGraphicsExtractor g) {
		g.fill(ox, oy, ox + winW, oy + winH, 0xFF0E0E1E);
		border(g, ox, oy, winW, winH, 0xFF3A3A60);
	}

	private void drawTabs(GuiGraphicsExtractor g, int mouseX, int mouseY) {
		int tabX = ox + MARGIN;

		// "All" tab
		drawTab(g, mouseX, mouseY, tabX, tabBarY, "All", selectedTab == null, () -> selectTab(null));
		tabX += tabWidth("All") + 3;

		for (DungeonFloor floor : availableTabs) {
			String label = floor.name();
			drawTab(g, mouseX, mouseY, tabX, tabBarY, label, floor == selectedTab, () -> selectTab(floor));
			tabX += tabWidth(label) + 3;
		}
	}

	private int tabWidth(String label) {
		return font.width(label) + 12;
	}

	private void drawTab(GuiGraphicsExtractor g, int mouseX, int mouseY, int x, int y, String label, boolean active, Runnable onClick) {
		int w = tabWidth(label);
		boolean hovered = contains(x, y, w, TAB_H, mouseX, mouseY);
		int bg = active ? 0xFF202A52 : hovered ? 0xFF17172A : 0xFF0F0F1D;
		int border = active ? 0xFF6A6AB0 : hovered ? PANEL_BORDER_HOVER : 0xFF343458;
		g.fill(x, y, x + w, y + TAB_H, bg);
		border(g, x, y, w, TAB_H, border);
		int textColor = active ? TEXT_PRIMARY : hovered ? 0xFFC8CDDD : TEXT_MUTED;
		g.text(font, label, x + 6, y + (TAB_H - font.lineHeight) / 2, textColor);
		clickTargets.add(new ClickTarget(x, y, w, TAB_H, 0, onClick));
	}

	private void drawHeader(GuiGraphicsExtractor g, int contentY) {
		g.text(font, "Dungeon Loot", ox + MARGIN, contentY + 3, TEXT_PRIMARY);
		String subtitle = selectedTab == null ? "All Floors" : selectedTab.name();
		g.text(font, subtitle, ox + MARGIN, contentY + 17, TEXT_MUTED);

		RunSelection sel = currentSelection();
		String summaryPrefix = sel.records.size() + " runs | " + buildDisplayRows().size() + " items | ";
		String profitText = "Profit " + formatSigned(sel.totalProfitCoins);
		int totalWidth = font.width(summaryPrefix) + font.width(profitText);
		int sx = ox + winW - MARGIN - totalWidth;
		int sy = contentY + 10;
		g.text(font, summaryPrefix, sx, sy, TEXT_SECONDARY);
		g.text(font, profitText, sx + font.width(summaryPrefix), sy, profitColor(sel.totalProfitCoins));
	}

	private void drawSectionLabels(GuiGraphicsExtractor g, int y) {
		String runsLabel = "All Runs (" + tabHistory.size() + ")";
		g.text(font, runsLabel, listX + 2, y + 4, TEXT_SECONDARY);
		String tableLabel = selectedRunIndex >= 0 && selectedRunIndex < tabHistory.size()
			? "Loot Summary, Run #" + tabHistory.get(selectedRunIndex).runNumber
			: "Loot Summary, All Runs";
		g.text(font, tableLabel, tableX + 2, y + 4, TEXT_SECONDARY);
	}

	private void drawRunList(GuiGraphicsExtractor g, int mouseX, int mouseY) {
		g.fill(listX, listY, listX + listW, listY + listH, PANEL_BG);
		g.enableScissor(listX, listY, listX + listW, listY + listH);

		drawRunListRow(g, mouseX, mouseY, -1, ItemStack.EMPTY,
			"All Runs (" + tabHistory.size() + ")", null, 0,
			formatSigned(sumProfit(tabHistory)),
			listY - listScroll);

		for (int i = 0; i < tabHistory.size(); i++) {
			DungeonRunRecord r = tabHistory.get(i);
			String runLabel = "#" + r.runNumber;
			String chestLabel = (r.chestTitle == null || r.chestTitle.isBlank()) ? null : " " + r.chestTitle;
			ItemStack icon = resolveChestIcon(r.chestTitle);
			drawRunListRow(g, mouseX, mouseY, i, icon, runLabel, chestLabel, chestColor(r.chestTitle),
				formatSigned(r.chestProfitCoins), listY + (i + 1) * ROW_H - listScroll);
		}

		g.disableScissor();
		border(g, listX, listY, listW, listH, PANEL_BORDER);
	}

	private void drawRunListRow(GuiGraphicsExtractor g, int mouseX, int mouseY, int index, ItemStack icon, String runLabel, String chestLabel, int chestLabelColor, String sub, int rowY) {
		if (rowY + ROW_H <= listY || rowY >= listY + listH) return;
		boolean selected = selectedRunIndex == index;
		boolean hovered = contains(listX, rowY, listW, ROW_H, mouseX, mouseY);
		int visualIndex = Math.max(0, (rowY + listScroll - listY) / ROW_H);
		int bg = selected ? 0xFF1F315D : hovered ? 0xFF1A1A31 : (visualIndex % 2 == 0 ? 0xFF101020 : PANEL_BG_ALT);
		g.fill(listX + 1, rowY, listX + listW - 1, rowY + ROW_H, bg);
		if (selected) {
			g.fill(listX + 1, rowY, listX + 3, rowY + ROW_H, 0xFF6A6AB0);
		}
		g.fill(listX + 5, rowY + ROW_H - 1, listX + listW - 5, rowY + ROW_H, 0x223A3A60);

		int textX = listX + 8;
		if (!icon.isEmpty()) {
			g.item(icon, listX + 4, rowY + 2);
			textX = listX + 23;
		}
		int textY = rowY + 6;
		int baseColor = selected ? 0xFFFFFFFF : TEXT_PRIMARY;
		int profitX = listX + listW - 8 - font.width(sub);
		g.text(font, runLabel, textX, textY, baseColor);
		if (chestLabel != null && !chestLabel.isBlank()) {
			int labelX = textX + font.width(runLabel);
			String safeChestLabel = ellipsize(chestLabel, Math.max(0, profitX - labelX - 6));
			g.text(font, safeChestLabel, labelX, textY, selected ? chestLabelColor : blendColor(chestLabelColor, 0xAA));
		}
		int subColor = sub.startsWith("-") ? 0xFFFF7A7A : 0xFF8BCF9A;
		g.text(font, sub, profitX, textY, subColor);
		final int idx = index;
		clickTargets.add(new ClickTarget(listX, rowY, listW, ROW_H, 0, () -> {
			selectedRunIndex = idx;
			clearLootFocus();
			tableScroll = 0;
		}));
	}

	private void drawLootTable(GuiGraphicsExtractor g, int mouseX, int mouseY) {
		g.fill(tableX, tableY, tableX + tableW, tableY + tableH, PANEL_BG);
		border(g, tableX, tableY, tableW, tableH, PANEL_BORDER);

		int itemLeft = tableX + 8;
		int totalRight = tableX + tableW - 10;
		int eachRight = tableX + tableW - 88;
		int qtyRight = tableX + tableW - 152;
		int tableHeaderH = ROW_H;
		int headerY = tableY;
		int headerHeight = tableHeaderH;
		int headerTextY = headerY + (tableHeaderH - font.lineHeight) / 2;
		int qtyHeaderWidth = headerHitWidth("Qty", LootSortColumn.QUANTITY, 42);
		int eachHeaderWidth = headerHitWidth("Each", LootSortColumn.EACH, 52);
		int totalHeaderWidth = headerHitWidth("Total", LootSortColumn.TOTAL, 58);
		int qtyHeaderX = qtyRight - qtyHeaderWidth / 2;
		int eachHeaderX = qtyHeaderX + qtyHeaderWidth;
		if (eachRight < eachHeaderX + eachHeaderWidth / 2) {
			eachHeaderX = eachRight - eachHeaderWidth / 2;
			qtyHeaderX = eachHeaderX - qtyHeaderWidth;
		}
		int totalHeaderX = eachHeaderX + eachHeaderWidth;
		if (totalRight < totalHeaderX + totalHeaderWidth / 2) {
			totalHeaderX = totalRight - totalHeaderWidth / 2;
			eachHeaderX = totalHeaderX - eachHeaderWidth;
			qtyHeaderX = eachHeaderX - qtyHeaderWidth;
		}
		int itemHeaderX = itemLeft - 8;
		int itemHeaderWidth = Math.max(0, qtyHeaderX - itemHeaderX);

		drawLootHeaderSegment(g, mouseX, mouseY, itemHeaderX, headerY, itemHeaderWidth, headerHeight, itemLeft, headerTextY, "Item", LootSortColumn.ITEM, HeaderTextAlign.LEFT);
		drawLootHeaderSegment(g, mouseX, mouseY, qtyHeaderX, headerY, qtyHeaderWidth, headerHeight, qtyHeaderX + qtyHeaderWidth / 2, headerTextY, "Qty", LootSortColumn.QUANTITY, HeaderTextAlign.CENTER);
		drawLootHeaderSegment(g, mouseX, mouseY, eachHeaderX, headerY, eachHeaderWidth, headerHeight, eachHeaderX + eachHeaderWidth / 2, headerTextY, "Each", LootSortColumn.EACH, HeaderTextAlign.CENTER);
		drawLootHeaderSegment(g, mouseX, mouseY, totalHeaderX, headerY, totalHeaderWidth, headerHeight, totalHeaderX + totalHeaderWidth / 2, headerTextY, "Total", LootSortColumn.TOTAL, HeaderTextAlign.CENTER);
		g.fill(tableX + 5, tableY + tableHeaderH - 1, tableX + tableW - 5, tableY + tableHeaderH, 0x663A3A60);

		List<DisplayRow> rows = buildDisplayRows();
		g.enableScissor(tableX, tableY + tableHeaderH, tableX + tableW, tableY + tableH);
		int drawY = tableY + tableHeaderH - tableScroll;
		int rowIndex = Math.max(0, tableScroll / ROW_H);
		for (DisplayRow row : rows) {
			if (drawY + ROW_H > tableY + tableHeaderH && drawY < tableY + tableH) {
				ItemStack icon = resolveItemIcon(row.itemId);
				boolean selectedLoot = rowKey(row).equals(selectedLootKey);
				boolean hovered = contains(tableX, drawY, tableW, ROW_H, mouseX, mouseY);
				int bg = selectedLoot ? 0x771F315D : hovered ? 0x441A1A31 : rowIndex % 2 == 0 ? 0x00111120 : 0x66141425;
				if (bg != 0) {
					g.fill(tableX + 1, drawY, tableX + tableW - 1, drawY + ROW_H, bg);
				}
				if (selectedLoot) {
					g.fill(tableX + 1, drawY, tableX + 3, drawY + ROW_H, 0xFF6A6AB0);
				}
				g.fill(tableX + 6, drawY + ROW_H - 1, tableX + tableW - 6, drawY + ROW_H, 0x1F3A3A60);
				int nameX = itemLeft;
				if (!icon.isEmpty()) {
					g.item(icon, itemLeft, drawY + 2);
					nameX = itemLeft + 18;
				}
				String truncated = ellipsize(row.label, Math.max(0, qtyRight - nameX - 18));
				int textY = drawY + 6;
				if (isUltimateItem(row.itemId)) {
					Component comp = Component.literal(truncated).withStyle(Style.EMPTY.withBold(true).withColor(TextColor.fromRgb(0xFF55FF)));
					g.text(font, comp, nameX, textY, 0xFFFF55FF);
				} else {
					g.text(font, truncated, nameX, textY, itemColor(row.itemId));
				}
				drawRightAligned(g, Integer.toString(row.quantity), qtyRight, textY, 0xFFB6C2DF);
				drawRightAligned(g, formatCoins(row.unitPrice), eachRight, textY, 0xFF7FB98E);
				drawRightAligned(g, formatCoins(row.totalPrice), totalRight, textY, 0xFF9CE3AC);
				addLootRowTargets(row, drawY);
			}
			drawY += ROW_H;
			rowIndex++;
		}
		g.disableScissor();
	}

	private void drawLootHeaderSegment(GuiGraphicsExtractor g, int mouseX, int mouseY, int x, int y, int w, int h, int textX, int textY, String label, LootSortColumn column, HeaderTextAlign align) {
		boolean hovered = contains(x, y, w, h, mouseX, mouseY);
		boolean active = lootSortColumn == column;
		if (hovered) {
			g.fill(x, y, x + w, y + h, active ? 0x22304163 : 0x181A2238);
		}
		int color = active ? 0xFFFFFFFF : hovered ? 0xFFE0E0F0 : 0xFFCCCCDD;
		String displayLabel = sortHeaderLabel(label, column);
		drawHeaderText(g, displayLabel, textX, textY, color, align);
		clickTargets.add(new ClickTarget(x, y, w, h, 0, () -> applyLootSort(column)));
	}

	private int headerHitWidth(String label, LootSortColumn column, int minWidth) {
		String displayLabel = sortHeaderLabel(label, column);
		return Math.max(minWidth, font.width(displayLabel) + 20);
	}

	private void addLootRowTargets(DisplayRow row, int rowY) {
		clickTargets.add(new ClickTarget(tableX, rowY, tableW, ROW_H, GLFW.GLFW_MOUSE_BUTTON_LEFT, () -> focusLootRow(row)));
		clickTargets.add(new ClickTarget(tableX, rowY, tableW, ROW_H, GLFW.GLFW_MOUSE_BUTTON_RIGHT, () -> copyLootShareMessage(row)));
	}

	private void focusLootRow(DisplayRow row) {
		selectedLootKey = rowKey(row);
		lootSearchBuffer = "";
		lootSearchLastTypedMillis = 0L;
	}

	private void copyLootShareMessage(DisplayRow row) {
		focusLootRow(row);
		if (minecraft != null) {
			minecraft.keyboardHandler.setClipboard("[DRT] " + row.label() + "'s dropped: " + row.quantity() + " | Total: " + formatCoins(row.totalPrice()));
		}
	}

	private void drawFooter(GuiGraphicsExtractor g, int mouseX, int mouseY) {
		int footerY = oy + winH - MARGIN - FOOTER_H;
		int buttonY = footerY + 3;
		int buttonH = FOOTER_H - 6;
		int closeW = footerBlockWidth("Close");
		int clearW = footerBlockWidth("Clear History");
		int closeX = ox + winW - MARGIN - closeW;
		int clearX = closeX - 8 - clearW;

		RunSelection sel = currentSelection();
		int statX = ox + MARGIN;
		statX += drawStatBlock(g, statX, buttonY, "Open Cost", formatCoins(sel.totalCostCoins), TEXT_SECONDARY) + 6;
		if (sel.totalValueCoins > 0L) {
			statX += drawStatBlock(g, statX, buttonY, "Loot Value", formatCoins(sel.totalValueCoins), 0xFF9CE3AC) + 6;
		}
		drawStatBlock(g, statX, buttonY, "Profit", formatSigned(sel.totalProfitCoins), profitColor(sel.totalProfitCoins));

		boolean closeHov = contains(closeX, buttonY, closeW, buttonH, mouseX, mouseY);
		boolean clearHov = contains(clearX, buttonY, clearW, buttonH, mouseX, mouseY);
		boolean confirming = clearConfirmUntilMillis > System.currentTimeMillis();
		drawClearButton(g, clearX, buttonY, clearW, buttonH, clearHov, confirming);
		drawButton(g, closeX, buttonY, closeW, buttonH, closeHov, "Close");

		clickTargets.add(new ClickTarget(closeX, buttonY, closeW, buttonH, 0, this::onClose));
		clickTargets.add(new ClickTarget(clearX, buttonY, clearW, buttonH, 0, () -> {
			long now = System.currentTimeMillis();
			if (clearConfirmUntilMillis > now) {
				if (selectedTab == null) {
					DrtConfigManager.clearAllData();
				} else {
					DrtConfigManager.clearFloorData(selectedTab.name());
				}
				clearConfirmUntilMillis = 0L;
				clearLootFocus();
				reloadHistory();
			} else {
				clearConfirmUntilMillis = now + 3000L;
			}
		}));
	}

	private void drawButton(GuiGraphicsExtractor g, int x, int y, int w, int h, boolean hovered, String label) {
		g.fill(x, y, x + w, y + h, hovered ? 0xFF232846 : 0xFF171A2E);
		border(g, x, y, w, h, hovered ? 0xFF5B5F92 : 0xFF3F426D);
		drawCenteredButtonText(g, label, x, y, w, h, TEXT_PRIMARY);
	}

	private void drawClearButton(GuiGraphicsExtractor g, int x, int y, int w, int h, boolean hovered, boolean confirming) {
		if (confirming) {
			g.fill(x, y, x + w, y + h, hovered ? 0xFF3A3000 : 0xFF252000);
			border(g, x, y, w, h, 0xFFAA9000);
			String lbl = "Confirm?";
			drawCenteredButtonText(g, lbl, x, y, w, h, 0xFFFFCC00);
		} else {
			g.fill(x, y, x + w, y + h, hovered ? 0xFF351014 : 0xFF220A0D);
			border(g, x, y, w, h, hovered ? 0xFF8A2A32 : 0xFF5F1E25);
			String lbl = "Clear History";
			drawCenteredButtonText(g, lbl, x, y, w, h, 0xFFE78A8A);
		}
	}

	private int drawStatBlock(GuiGraphicsExtractor g, int x, int y, String label, String value, int valueColor) {
		int labelW = font.width(label);
		int valueW = font.width(value);
		int w = labelW + valueW + 18;
		int h = FOOTER_H - 6;
		g.fill(x, y, x + w, y + h, 0xFF141426);
		border(g, x, y, w, h, 0xFF303052);
		g.text(font, label, x + 6, y + 7, TEXT_MUTED);
		g.text(font, value, x + 12 + labelW, y + 7, valueColor);
		return w;
	}

	private int footerBlockWidth(String text) {
		return font.width(text) + 18;
	}

	// ── Data ─────────────────────────────────────────────────────────────────

	private void selectTab(DungeonFloor floor) {
		selectedTab = floor;
		selectedRunIndex = -1;
		clearLootFocus();
		listScroll = 0;
		tableScroll = 0;
		clearConfirmUntilMillis = 0L;
		DrtConfigManager.updateSelectedFloor(floor == null ? null : floor.name());
		rebuildTabHistory();
	}

	private void reloadHistory() {
		allHistory = new ArrayList<>(DrtConfigManager.getRunHistory());
		allHistory.sort(Comparator.comparingLong((DungeonRunRecord r) -> r.timestampEpochMillis).reversed());

		// Build available tabs from floors that have at least one run
		availableTabs = new ArrayList<>();
		for (DungeonFloor f : DungeonFloor.values()) {
			if (f == DungeonFloor.UNKNOWN) continue;
			boolean hasSome = false;
			for (DungeonRunRecord r : allHistory) {
				if (f.name().equals(r.floor)) { hasSome = true; break; }
			}
			if (hasSome) availableTabs.add(f);
		}

		// Keep selected tab if it still has data
		if (selectedTab != null && !availableTabs.contains(selectedTab)) {
			selectedTab = null;
		}

		rebuildTabHistory();
	}

	private void rebuildTabHistory() {
		if (selectedTab == null) {
			tabHistory = allHistory;
		} else {
			String key = selectedTab.name();
			tabHistory = allHistory.stream().filter(r -> key.equals(r.floor)).toList();
		}
		if (selectedRunIndex >= tabHistory.size()) selectedRunIndex = -1;
		if (!selectedLootKey.isBlank() && buildDisplayRows().stream().noneMatch(row -> rowKey(row).equals(selectedLootKey))) {
			clearLootFocus();
		}
	}

	private RunSelection currentSelection() {
		if (selectedRunIndex >= 0 && selectedRunIndex < tabHistory.size()) {
			DungeonRunRecord r = tabHistory.get(selectedRunIndex);
			return new RunSelection(List.of(r), r.chestCostCoins, r.chestValueCoins, r.chestProfitCoins);
		}
		return new RunSelection(tabHistory, sumCoins(tabHistory, false, true), sumCoins(tabHistory, false, false), sumProfit(tabHistory));
	}

	private List<DisplayRow> buildDisplayRows() {
		List<DungeonRunRecord> records = currentSelection().records;
		Map<String, MutableAgg> aggs = new LinkedHashMap<>();
		for (DungeonRunRecord r : records) {
			if (r == null || r.lootEntries == null) continue;
			for (DungeonLootEntry e : r.lootEntries) {
				if (e == null) continue;
				if (shouldHideLootEntry(e)) continue;
				String key = e.itemId != null && !e.itemId.isBlank() ? e.itemId : e.rawName.trim().toUpperCase(Locale.ROOT);
				MutableAgg agg = aggs.computeIfAbsent(key, k -> new MutableAgg(displayName(e), e.itemId == null ? "" : e.itemId));
				agg.quantity += Math.max(1, e.quantity);
			}
		}
		List<DisplayRow> rows = new ArrayList<>();
		for (MutableAgg agg : aggs.values()) {
			long unit = DungeonProfitPricing.resolveUnitPrice(new DungeonLootEntry(agg.label, agg.itemId, 1), DrtConfigManager.getConfig());
			rows.add(new DisplayRow(agg.label, agg.itemId, agg.quantity, unit, unit * agg.quantity));
		}
		Comparator<DisplayRow> comparator = displayRowComparator();
		if (comparator != null) {
			rows.sort(comparator);
		}
		return rows;
	}

	private Comparator<DisplayRow> displayRowComparator() {
		if (lootSortColumn == null) {
			return Comparator.comparingLong(DisplayRow::totalPrice)
				.reversed()
				.thenComparing(DisplayRow::label, String::compareToIgnoreCase);
		}
		return switch (lootSortColumn) {
			case ITEM -> Comparator.comparing((DisplayRow row) -> row.label().toLowerCase(Locale.ROOT))
				.thenComparing(DisplayRow::label)
				.thenComparing(DisplayRow::itemId);
			case QUANTITY -> (left, right) -> {
				int quantityCompare = Integer.compare(right.quantity(), left.quantity());
				if (quantityCompare != 0) return quantityCompare;
				int totalCompare = Long.compare(right.totalPrice(), left.totalPrice());
				if (totalCompare != 0) return totalCompare;
				return left.label().compareToIgnoreCase(right.label());
			};
			case EACH -> lootSortAscending
				? Comparator.comparingLong(DisplayRow::unitPrice).thenComparing(DisplayRow::label, String::compareToIgnoreCase)
				: Comparator.comparingLong(DisplayRow::unitPrice).reversed().thenComparing(DisplayRow::label, String::compareToIgnoreCase);
			case TOTAL -> lootSortAscending
				? Comparator.comparingLong(DisplayRow::totalPrice).thenComparing(DisplayRow::label, String::compareToIgnoreCase)
				: Comparator.comparingLong(DisplayRow::totalPrice).reversed().thenComparing(DisplayRow::label, String::compareToIgnoreCase);
		};
	}

	private void applyLootSort(LootSortColumn column) {
		if (lootSortColumn != column) {
			lootSortColumn = column;
			lootSortAscending = false;
		} else if (column == LootSortColumn.EACH || column == LootSortColumn.TOTAL) {
			if (!lootSortAscending) {
				lootSortAscending = true;
			} else {
				lootSortColumn = null;
				lootSortAscending = false;
			}
		} else {
			lootSortColumn = null;
			lootSortAscending = false;
		}
		tableScroll = 0;
	}

	private void searchLootRows() {
		if (lootSearchBuffer.isBlank()) return;
		List<DisplayRow> rows = buildDisplayRows();
		String needle = normalizeSearchText(lootSearchBuffer);
		for (int i = 0; i < rows.size(); i++) {
			DisplayRow row = rows.get(i);
			if (normalizeSearchText(row.label()).startsWith(needle)) {
				selectedLootKey = rowKey(row);
				scrollLootRowIntoView(i);
				return;
			}
		}
		for (int i = 0; i < rows.size(); i++) {
			DisplayRow row = rows.get(i);
			if (normalizeSearchText(row.label()).contains(needle)) {
				selectedLootKey = rowKey(row);
				scrollLootRowIntoView(i);
				return;
			}
		}
	}

	private void scrollLootRowIntoView(int rowIndex) {
		int tableHeaderH = ROW_H;
		int viewportH = Math.max(ROW_H, tableH - tableHeaderH);
		int rowTop = rowIndex * ROW_H;
		int rowBottom = rowTop + ROW_H;
		if (rowTop < tableScroll) {
			tableScroll = rowTop;
		} else if (rowBottom > tableScroll + viewportH) {
			tableScroll = rowBottom - viewportH;
		}
		int maxScroll = Math.max(0, buildDisplayRows().size() * ROW_H - viewportH);
		tableScroll = clamp(tableScroll, 0, maxScroll);
	}

	private void clearLootFocus() {
		selectedLootKey = "";
		lootSearchBuffer = "";
		lootSearchLastTypedMillis = 0L;
	}

	private String rowKey(DisplayRow row) {
		return row.itemId() + " " + row.label();
	}

	private static String normalizeSearchText(String text) {
		return text == null ? "" : text.toLowerCase(Locale.ROOT).replace("'", "").replace("_", " ").trim();
	}

	private String sortHeaderLabel(String label, LootSortColumn column) {
		return label;
	}

	private String displayName(DungeonLootEntry e) {
		if (e.rawName != null && !e.rawName.isBlank()) return e.rawName;
		if (e.itemId == null || e.itemId.isBlank()) return "Unknown";
		String lower = e.itemId.toLowerCase(Locale.ROOT).replace('_', ' ');
		String[] parts = lower.split("\\s+");
		StringBuilder sb = new StringBuilder();
		for (String p : parts) {
			if (p.isEmpty()) continue;
			if (sb.length() > 0) sb.append(' ');
			sb.append(Character.toUpperCase(p.charAt(0))).append(p.substring(1));
		}
		return sb.toString();
	}

	private long sumProfit(List<DungeonRunRecord> records) {
		long t = 0L;
		for (DungeonRunRecord r : records) if (r != null) t += r.chestProfitCoins;
		return t;
	}

	private long sumCoins(List<DungeonRunRecord> records, boolean cost, boolean value) {
		long t = 0L;
		for (DungeonRunRecord r : records) {
			if (r == null) continue;
			if (cost) t += r.chestCostCoins;
			else if (value) t += r.chestValueCoins;
		}
		return t;
	}

	// ── Formatting ───────────────────────────────────────────────────────────

	private String formatCoins(long v) {
		if (v >= 1_000_000_000L) return String.format("%.1fB", v / 1_000_000_000.0);
		if (v >= 1_000_000L) return String.format("%.1fM", v / 1_000_000.0);
		if (v >= 1_000L) return String.format("%.1fK", v / 1_000.0);
		return Long.toString(v);
	}
	private String formatSigned(long v) { return (v >= 0 ? "+" : "-") + formatCoins(Math.abs(v)); }
	private int profitColor(long v) { return v >= 0 ? 0xFF8BCF9A : 0xFFFF7A7A; }

	private static ItemStack resolveChestIcon(String chestTitle) {
		ItemStack cached = DungeonRunTrackerFeature.getChestIcon(chestTitle);
		if (!cached.isEmpty()) return cached;
		if (chestTitle == null) return new ItemStack(Items.CHEST);
		String t = chestTitle.toUpperCase(Locale.ROOT);
		if (t.contains("WOOD"))    return new ItemStack(Items.CHEST);
		if (t.contains("GOLD"))    return new ItemStack(Items.GOLD_BLOCK);
		if (t.contains("DIAMOND")) return new ItemStack(Items.DIAMOND_BLOCK);
		if (t.contains("EMERALD")) return new ItemStack(Items.EMERALD_BLOCK);
		if (t.contains("OBSIDIAN"))return new ItemStack(Items.OBSIDIAN);
		if (t.contains("BEDROCK")) return new ItemStack(Items.BEDROCK);
		return new ItemStack(Items.CHEST);
	}

	private static ItemStack dyedLeather(net.minecraft.world.item.Item item, int rgb) {
		ItemStack stack = new ItemStack(item);
		stack.set(DataComponents.DYED_COLOR, new DyedItemColor(rgb));
		return stack;
	}

	private static ItemStack resolveItemIcon(String itemId) {
		ItemStack cached = DungeonRunTrackerFeature.getItemIcon(itemId);
		if (!cached.isEmpty()) return cached;
		if (itemId == null || itemId.isBlank()) return ItemStack.EMPTY;
		String originalId = itemId.toUpperCase(Locale.ROOT);
		String id = normalizeResolverItemId(originalId);

		// Enqueue async NEU fetch up-front so skulls have their texture by the next render.
		NeuItemResolver.enqueue(id);

		// ── Deterministic non-skull rules ────────────────────────────────────
		if (id.startsWith("ENCHANTMENT_ULTIMATE_")) return new ItemStack(Items.ENCHANTED_BOOK);
		if (id.startsWith("ENCHANTMENT_"))          return new ItemStack(Items.ENCHANTED_BOOK);
		if (id.startsWith("ESSENCE_"))              return new ItemStack(Items.NETHER_STAR);
		// Shadow Assassin — black leather (helmet is skull, falls through to NEU)
		if (id.equals("SHADOW_ASSASSIN_CHESTPLATE")) return dyedLeather(Items.LEATHER_CHESTPLATE, 0x1A1A1A);
		if (id.equals("SHADOW_ASSASSIN_LEGGINGS"))   return dyedLeather(Items.LEATHER_LEGGINGS, 0x1A1A1A);
		if (id.equals("SHADOW_ASSASSIN_BOOTS"))      return dyedLeather(Items.LEATHER_BOOTS, 0x1A1A1A);
		// Necromancer Lord — skull helmet via NEU, black chest, purple legs/boots
		if (id.equals("NECROMANCER_LORD_CHESTPLATE")) return dyedLeather(Items.LEATHER_CHESTPLATE, 0x1A1A1A);
		if (id.equals("NECROMANCER_LORD_LEGGINGS"))  return dyedLeather(Items.LEATHER_LEGGINGS, 0x6600AA);
		if (id.equals("NECROMANCER_LORD_BOOTS"))     return dyedLeather(Items.LEATHER_BOOTS, 0x6600AA);
		// Wither — skull helmet via NEU/cache, black leather
		if (id.equals("WITHER_CHESTPLATE"))          return dyedLeather(Items.LEATHER_CHESTPLATE, 0x1A1A1A);
		if (id.equals("WITHER_LEGGINGS"))            return dyedLeather(Items.LEATHER_LEGGINGS, 0x1A1A1A);
		if (id.equals("WITHER_BOOTS"))               return dyedLeather(Items.LEATHER_BOOTS, 0x1A1A1A);
		// Adaptive — skull helmet via NEU, light leather
		if (id.equals("ADAPTIVE_CHESTPLATE"))        return dyedLeather(Items.LEATHER_CHESTPLATE, 0xBFBCB2);
		if (id.equals("ADAPTIVE_LEGGINGS"))          return dyedLeather(Items.LEATHER_LEGGINGS, 0xBFBCB2);
		if (id.equals("ADAPTIVE_BOOTS"))             return dyedLeather(Items.LEATHER_BOOTS, 0xBFBCB2);
		// Zombie Knight — gold armor
		if (id.equals("ZOMBIE_KNIGHT_HELMET"))       return new ItemStack(Items.GOLDEN_HELMET);
		if (id.equals("ZOMBIE_KNIGHT_CHESTPLATE"))   return new ItemStack(Items.GOLDEN_CHESTPLATE);
		if (id.equals("ZOMBIE_KNIGHT_LEGGINGS"))     return new ItemStack(Items.GOLDEN_LEGGINGS);
		if (id.equals("ZOMBIE_KNIGHT_BOOTS"))        return new ItemStack(Items.GOLDEN_BOOTS);
		if (id.equals("ZOMBIE_KNIGHT_SWORD"))        return new ItemStack(Items.IRON_SWORD);
		// Zombie Soldier — green leather
		if (id.equals("ZOMBIE_SOLDIER_HELMET"))      return dyedLeather(Items.LEATHER_HELMET, 0x1C5216);
		if (id.equals("ZOMBIE_SOLDIER_CHESTPLATE"))  return dyedLeather(Items.LEATHER_CHESTPLATE, 0x1C5216);
		if (id.equals("ZOMBIE_SOLDIER_LEGGINGS"))    return dyedLeather(Items.LEATHER_LEGGINGS, 0x1C5216);
		if (id.equals("ZOMBIE_SOLDIER_BOOTS"))       return dyedLeather(Items.LEATHER_BOOTS, 0x1C5216);
		if (id.equals("ZOMBIE_SOLDIER_CUTLASS"))     return new ItemStack(Items.IRON_SWORD);
		// Lapis Armor — sea lantern helmet, blue leather
		if (id.equals("LAPIS_ARMOR_HELMET"))         return new ItemStack(Items.SEA_LANTERN);
		if (id.equals("LAPIS_ARMOR_CHESTPLATE"))     return dyedLeather(Items.LEATHER_CHESTPLATE, 0x1D3C8B);
		if (id.equals("LAPIS_ARMOR_LEGGINGS"))       return dyedLeather(Items.LEATHER_LEGGINGS, 0x1D3C8B);
		if (id.equals("LAPIS_ARMOR_BOOTS"))          return dyedLeather(Items.LEATHER_BOOTS, 0x1D3C8B);
		// Spirit — non-skull variants resolved here; skull variants fall through to NEU
		if (id.equals("SPIRIT_LEAP"))                return new ItemStack(Items.ENDER_PEARL);
		if (id.equals("SPIRIT_SWORD"))               return new ItemStack(Items.IRON_SWORD);
		if (id.equals("WITHER_CATALYST"))            return new ItemStack(Items.WITHER_SKELETON_SKULL);
		// Weapons & tools
		if (id.equals("SHADOW_FURY"))               return new ItemStack(Items.DIAMOND_SWORD);
		if (id.equals("LIVID_DAGGER"))              return new ItemStack(Items.IRON_SWORD);
		if (id.equals("LAST_BREATH"))               return new ItemStack(Items.BOW);
		if (id.equals("GIANTS_SWORD"))              return new ItemStack(Items.IRON_SWORD);
		if (id.equals("STONE_BLADE"))               return new ItemStack(Items.STONE_SWORD);
		if (id.equals("NECRON_BLADE"))              return new ItemStack(Items.IRON_SWORD);
		if (id.equals("NECROMANCER_SWORD"))         return new ItemStack(Items.IRON_SWORD);
		if (id.equals("WITHER_CLOAK"))              return new ItemStack(Items.STONE_SWORD);
		if (id.equals("BONZO_STAFF"))               return new ItemStack(Items.BLAZE_ROD);
		if (id.equals("PHANTOM_ROD"))               return new ItemStack(Items.FISHING_ROD);
		if (id.equals("SCYTHE_BLADE"))              return new ItemStack(Items.DIAMOND);
		// Misc non-skull
		if (id.equals("FUMING_POTATO_BOOK"))        return new ItemStack(Items.BOOK);
		if (id.equals("DUNGEON_CHEST_KEY"))         return new ItemStack(Items.TRIPWIRE_HOOK);

		// ── NEU resolved stack (skull textures and unknown items) ─────────────
		ItemStack neuStack = NeuItemResolver.getResolvedStack(id);
		if (neuStack != null && !neuStack.isEmpty()) return neuStack;

		// Skull placeholder shown while the NEU fetch is still in-flight
		if (id.equals("WITHER_CATALYST")) return new ItemStack(Items.WITHER_SKELETON_SKULL);
		if (isKnownSkullFallback(originalId, id)) return new ItemStack(Items.PLAYER_HEAD);

		return ItemStack.EMPTY;
	}

	private static String normalizeResolverItemId(String id) {
		return switch (id) {
			case "NECRONS_HANDLE" -> "NECRON_HANDLE";
			case "SCARFS_STUDIES" -> "SCARF_STUDIES";
			case "SPIRIT_STONE" -> "SPIRIT_DECOY";
			case "SPIRIT_BOOTS" -> "THORNS_BOOTS";
			case "SPIRIT_PET" -> "SPIRIT;3";
			case "WARPED_STONE" -> "AOTE_STONE";
			case "ADAPTIVE_BLADE" -> "STONE_BLADE";
			case "WITHER_CLOAK_SWORD" -> "WITHER_CLOAK";
			default -> id;
		};
	}

	private static boolean isKnownSkullFallback(String originalId, String resolvedId) {
		return originalId.equals("SPIRIT_PET")
			|| originalId.equals("SPIRIT_STONE")
			|| originalId.equals("SCARFS_STUDIES")
			|| originalId.equals("WARPED_STONE")
			|| resolvedId.equals("SHADOW_ASSASSIN_HELMET")
			|| resolvedId.equals("NECROMANCER_LORD_HELMET")
			|| resolvedId.equals("WITHER_HELMET")
			|| resolvedId.equals("ADAPTIVE_HELMET")
			|| resolvedId.startsWith("SPIRIT_")
			|| resolvedId.startsWith("ITEM_SPIRIT_")
			|| resolvedId.startsWith("BOSS_SPIRIT_")
			|| resolvedId.startsWith("STARRED_ITEM_SPIRIT_")
			|| resolvedId.startsWith("MASTER_SKULL_")
			|| resolvedId.equals("RECOMBOBULATOR_3000")
			|| resolvedId.equals("AOTE_STONE")
			|| resolvedId.equals("TRAINING_WEIGHTS")
			|| resolvedId.equals("SCARF_STUDIES");
	}

	private static int chestColor(String chestTitle) {
		if (chestTitle == null) return 0xFFDDDDDD;
		String t = chestTitle.toUpperCase(Locale.ROOT);
		if (t.contains("WOOD")) return 0xFFAA8050;
		if (t.contains("GOLD")) return 0xFFFFAA00;
		if (t.contains("DIAMOND")) return 0xFF55CCFF;
		if (t.contains("EMERALD")) return 0xFF55FF55;
		if (t.contains("OBSIDIAN")) return 0xFFAA55FF;
		if (t.contains("BEDROCK")) return 0xFF555555;
		return 0xFFDDDDDD;
	}

	private static boolean isUltimateItem(String itemId) {
		return itemId != null && itemId.toUpperCase(Locale.ROOT).startsWith("ENCHANTMENT_ULTIMATE_");
	}

	private static int itemColor(String itemId) {
		if (itemId == null || itemId.isBlank()) return 0xFFAAAAAA;
		String id = normalizeResolverItemId(itemId.toUpperCase(Locale.ROOT));
		// NEU repo displayname colour is authoritative — checked before hardcoded rules.
		Integer neuColor = NeuItemResolver.getColor(id);
		if (neuColor != null) return neuColor;
		if (id.startsWith("ENCHANTMENT_ULTIMATE_")) return 0xFFFF55FF; // Mythic
		if (id.startsWith("ENCHANTMENT_"))          return 0xFF5555FF; // Rare
		if (id.startsWith("ESSENCE_"))              return 0xFFAAAAAA; // grey
		// Master Skull tier varies
		if (id.startsWith("MASTER_SKULL_TIER_")) {
			int tier = 0;
			try { tier = Integer.parseInt(id.substring("MASTER_SKULL_TIER_".length())); } catch (NumberFormatException ignored) {}
			if (tier >= 9) return 0xFFFFAA00;
			if (tier >= 6) return 0xFFAA00AA;
			if (tier >= 5) return 0xFF5555FF;
			return 0xFF55FF55;
		}
		// Legendary
		if (id.startsWith("NECROMANCER_LORD_") || id.startsWith("WITHER_") ||
			id.equals("SHADOW_FURY") || id.equals("LIVID_DAGGER") ||
			id.equals("LAST_BREATH") || id.equals("GIANTS_SWORD") ||
			id.equals("NECROMANCER_SWORD") || id.equals("NECRON_BLADE") ||
			id.equals("RECOMBOBULATOR_3000") || id.equals("PHANTOM_ROD") ||
			id.equals("SCYTHE_BLADE") || id.equals("ITEM_SPIRIT_BOW") ||
			id.equals("STARRED_ITEM_SPIRIT_BOW")) return 0xFFFFAA00;
		// Epic
		if (id.startsWith("SHADOW_ASSASSIN_") || id.startsWith("ADAPTIVE_") ||
			id.startsWith("ZOMBIE_KNIGHT_") || id.startsWith("ZOMBIE_SOLDIER_") ||
			id.equals("FUMING_POTATO_BOOK") || id.equals("BOSS_SPIRIT_BOW") ||
			id.equals("STORM_IN_A_BOTTLE") ||
			id.equals("SPIRIT_WING") || id.equals("SPIRIT_MASK") ||
			id.equals("SPIRIT_BONE") || id.equals("SPIRIT_SWORD")) return 0xFFAA00AA;
		// Rare
		if (id.equals("DUNGEON_CHEST_KEY") || id.equals("BONZO_STAFF") ||
			id.equals("SPIRIT_LEAP") || id.equals("SPIRIT_DECOY") ||
			id.equals("AOTE_STONE") || id.equals("WARPED_STONE") ||
			id.equals("ZOMBIE_SOLDIER_CUTLASS")) return 0xFF5555FF;
		// Uncommon
		if (id.startsWith("LAPIS_ARMOR_") || id.equals("TRAINING_WEIGHTS")) return 0xFF55FF55;
		return 0xFFAAAAAA;
	}

	private static boolean shouldHideLootEntry(DungeonLootEntry entry) {
		String itemId = entry.itemId == null ? "" : entry.itemId.trim().toUpperCase(Locale.ROOT);
		String rawName = entry.rawName == null ? "" : entry.rawName.trim().toUpperCase(Locale.ROOT);
		return itemId.equals("ANCIENT_ROSE") || rawName.equals("ANCIENT ROSE");
	}

	private static int blendColor(int argb, int alpha) {
		return (argb & 0x00FFFFFF) | (alpha << 24);
	}

	private String ellipsize(String s, int maxWidth) {
		if (font.width(s) <= maxWidth) return s;
		String ell = "...";
		int ellW = font.width(ell);
		StringBuilder sb = new StringBuilder();
		for (char c : s.toCharArray()) {
			if (font.width(sb.toString() + c) + ellW > maxWidth) break;
			sb.append(c);
		}
		return sb.append(ell).toString();
	}

	// ── Utilities ────────────────────────────────────────────────────────────

	private boolean contains(int x, int y, int w, int h, int mx, int my) {
		return mx >= x && mx < x + w && my >= y && my < y + h;
	}

	private void drawRightAligned(GuiGraphicsExtractor g, String text, int rightX, int y, int color) {
		g.text(font, text, rightX - font.width(text), y, color);
	}

	private void drawCenteredButtonText(GuiGraphicsExtractor g, String text, int x, int y, int w, int h, int color) {
		g.text(font, text, x + (w - font.width(text)) / 2, y + (h - font.lineHeight) / 2, color);
	}

	private void drawHeaderText(GuiGraphicsExtractor g, String text, int anchorX, int y, int color, HeaderTextAlign align) {
		int drawX = switch (align) {
			case LEFT -> anchorX;
			case CENTER -> anchorX - font.width(text) / 2;
		};
		g.text(font, text, drawX, y, color);
	}

	private int clamp(int v, int min, int max) { return Math.max(min, Math.min(max, v)); }

	private void border(GuiGraphicsExtractor g, int x, int y, int w, int h, int color) {
		g.fill(x, y, x + w, y + 1, color);
		g.fill(x, y + h - 1, x + w, y + h, color);
		g.fill(x, y, x + 1, y + h, color);
		g.fill(x + w - 1, y, x + w, y + h, color);
	}

	// ── Records / inner classes ───────────────────────────────────────────────

	private record ClickTarget(int x, int y, int w, int h, int button, Runnable action) {}
	private record DisplayRow(String label, String itemId, int quantity, long unitPrice, long totalPrice) {}
	private record RunSelection(List<DungeonRunRecord> records, long totalCostCoins, long totalValueCoins, long totalProfitCoins) {}
	private enum LootSortColumn { ITEM, QUANTITY, EACH, TOTAL }
	private enum HeaderTextAlign { LEFT, CENTER }

	private static final class MutableAgg {
		final String label;
		final String itemId;
		int quantity;
		MutableAgg(String label, String itemId) { this.label = label; this.itemId = itemId == null ? "" : itemId; }
	}
}
