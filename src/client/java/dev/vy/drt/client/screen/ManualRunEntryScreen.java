package dev.vy.drt.client.screen;

import dev.vy.drt.client.DrtClient;
import dev.vy.drt.config.DrtConfigManager;
import dev.vy.drt.config.DungeonFloor;
import dev.vy.drt.config.DungeonLootEntry;
import dev.vy.drt.config.DungeonRunRecord;
import dev.vy.drt.price.DungeonProfitPricing;
import dev.vy.drt.price.ManualLootSuggestions;
import dev.vy.drt.price.PriceCache;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.world.item.ItemStack;
import org.lwjgl.glfw.GLFW;

public final class ManualRunEntryScreen extends Screen {
	private static final int WIN_W = 460;
	private static final int SEARCH_POPUP_W = 272;
	private static final int MARGIN = 12;
	private static final int FIELD_H = 18;
	private static final int LABEL_H = 10;
	private static final int ROW_H = LABEL_H + FIELD_H + 8;
	private static final int COL_GAP = 10;
	private static final int LOOT_GAP = 8;
	private static final int BTN_H = 20;
	private static final int FOOTER_GAP = 8;
	private static final int PANEL_BG = 0xFF0E0E1E;
	private static final int PANEL_BORDER = 0xFF3A3A60;
	private static final int FIELD_BG = 0xFF141426;
	private static final int FIELD_BORDER = 0xFF303052;
	private static final int FIELD_BORDER_HOVER = 0xFF494978;
	private static final int TEXT_PRIMARY = 0xFFE5E7F0;
	private static final int TEXT_MUTED = 0xFF78829D;
	private static final int TEXT_SECONDARY = 0xFF9AA4BD;
	private static final int TITLE_H = 32;
	private static final int SEARCH_VISIBLE_ROWS = 7;
	private static final int SEARCH_ROW_H = 14;
	private static final int SEARCH_HEADER_H = 28;
	private static final int SEARCH_POPUP_GAP = 4;
	private static final int SEARCH_POPUP_PAD = 6;

	private static final DungeonFloor[] FLOOR_OPTIONS = {
		DungeonFloor.F1, DungeonFloor.F2, DungeonFloor.F3, DungeonFloor.F4, DungeonFloor.F5, DungeonFloor.F6, DungeonFloor.F7,
		DungeonFloor.M1, DungeonFloor.M2, DungeonFloor.M3, DungeonFloor.M4, DungeonFloor.M5, DungeonFloor.M6, DungeonFloor.M7,
		DungeonFloor.K1, DungeonFloor.K2, DungeonFloor.K3, DungeonFloor.K4, DungeonFloor.K5
	};
	private static final String[] SCORE_OPTIONS = {"S+", "S", "A"};

	private final Screen parent;
	private int ox, oy;
	private int winH;
	private int btnY;
	private int searchPopupX, searchPopupY, searchPopupW, searchPopupH;

	private DungeonFloor floor = DungeonFloor.F7;
	private String chestType = "Bedrock";
	private String score = "S+";
	private String timeStr = "10:00";
	private String costStr = "0";
	private String witherEssenceStr = "0";
	private String undeadEssenceStr = "0";

	private final List<ManualLootSlot> lootSlots = new ArrayList<>();
	private int selectedSlot = -1;
	private String itemSearchQuery = "";
	private final List<PriceCache.SearchResult> searchResults = new ArrayList<>();
	private int searchScroll = 0;

	private int activeInput = 0; // 0=none, 1=time, 2=cost, 3=search, 4=wither, 5=undead
	private OpenDropdown openDropdown = OpenDropdown.NONE;

	private int contentW;
	private int lootSlotW;
	private int floorFieldX, floorFieldY, floorFieldW;
	private int chestFieldX, chestFieldY, chestFieldW;
	private int scoreFieldX, scoreFieldY, scoreFieldW;
	private int timeFieldX, timeFieldY, timeFieldW;
	private int costFieldX, costFieldY, costFieldW;
	private int witherFieldX, witherFieldY, witherFieldW;
	private int undeadFieldX, undeadFieldY, undeadFieldW;
	private int lootRowY;

	public ManualRunEntryScreen(Screen parent) {
		super(Component.literal("Manual Run Entry"));
		this.parent = parent;
		for (int i = 0; i < 4; i++) {
			lootSlots.add(new ManualLootSlot());
		}
	}

	@Override
	protected void init() {
		winH = computeWinH();
		ox = (width - WIN_W) / 2;
		oy = (height - winH) / 2;
		layoutFields();
	}

	private int computeWinH() {
		int fieldRows = floor.isKuudra() ? 2 : 3;
		int yAfterFields = TITLE_H + fieldRows * ROW_H;
		int lootRowOffset = (floor.isKuudra() ? yAfterFields - ROW_H : yAfterFields) + ROW_H + 2;
		int dropdownH = SEARCH_HEADER_H + SEARCH_VISIBLE_ROWS * SEARCH_ROW_H + SEARCH_POPUP_PAD;
		return lootRowOffset + FIELD_H + SEARCH_POPUP_GAP + dropdownH + FOOTER_GAP + BTN_H + MARGIN;
	}

	private void layoutFields() {
		contentW = WIN_W - MARGIN * 2;
		lootSlotW = (contentW - LOOT_GAP * 3) / 4;

		int left = ox + MARGIN;
		int y = oy + TITLE_H;

		int floorW = 72;
		int scoreW = 52;
		int chestW = contentW - floorW - scoreW - COL_GAP * 2;

		floorFieldX = left;
		floorFieldY = y;
		floorFieldW = floorW;
		chestFieldX = left + floorW + COL_GAP;
		chestFieldY = y;
		chestFieldW = chestW;
		scoreFieldX = chestFieldX + chestW + COL_GAP;
		scoreFieldY = y;
		scoreFieldW = scoreW;

		y += ROW_H;
		timeFieldX = left;
		timeFieldY = y;
		timeFieldW = 84;
		costFieldX = left + timeFieldW + COL_GAP;
		costFieldY = y;
		costFieldW = 108;

		y += ROW_H;
		witherFieldX = left;
		witherFieldY = y;
		witherFieldW = (contentW - COL_GAP) / 2;
		undeadFieldX = witherFieldX + witherFieldW + COL_GAP;
		undeadFieldY = y;
		undeadFieldW = witherFieldW;

		lootRowY = (floor.isKuudra() ? y - ROW_H : y) + ROW_H + 2;
		btnY = oy + winH - MARGIN - BTN_H;

		searchPopupX = ox + MARGIN;
		searchPopupY = lootRowY + FIELD_H + SEARCH_POPUP_GAP;
		searchPopupW = SEARCH_POPUP_W;
		int desiredH = SEARCH_HEADER_H + SEARCH_VISIBLE_ROWS * SEARCH_ROW_H + SEARCH_POPUP_PAD;
		int maxH = btnY - FOOTER_GAP - searchPopupY;
		searchPopupH = Math.min(desiredH, Math.max(SEARCH_HEADER_H + SEARCH_ROW_H + SEARCH_POPUP_PAD, maxH));
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
		if (parent != null) {
			parent.extractRenderState(g, mouseX, mouseY, partialTick);
		}
		g.fill(0, 0, width, height, 0xAA000000);

		g.fill(ox, oy, ox + WIN_W, oy + winH, PANEL_BG);
		border(g, ox, oy, WIN_W, winH, PANEL_BORDER);

		g.text(font, "Manual Run Entry", ox + MARGIN, oy + 11, TEXT_PRIMARY);

		drawFieldLabel(g, "Floor", floorFieldX, floorFieldY - LABEL_H);
		drawDropdownField(g, floorFieldX, floorFieldY, floorFieldW, floor.name(),
			floor.isKuudra() ? 0xFFAA0000 : TEXT_PRIMARY, null, mouseX, mouseY,
			openDropdown == OpenDropdown.FLOOR);

		drawFieldLabel(g, "Chest", chestFieldX, chestFieldY - LABEL_H);
		drawChestDropdownField(g, chestFieldX, chestFieldY, chestFieldW, mouseX, mouseY);

		drawFieldLabel(g, "Score", scoreFieldX, scoreFieldY - LABEL_H);
		drawDropdownField(g, scoreFieldX, scoreFieldY, scoreFieldW, score, TEXT_PRIMARY, null, mouseX, mouseY, false);

		drawFieldLabel(g, "Time", timeFieldX, timeFieldY - LABEL_H);
		drawTextField(g, timeFieldX, timeFieldY, timeFieldW, timeStr, activeInput == 1, mouseX, mouseY);

		drawFieldLabel(g, "Cost", costFieldX, costFieldY - LABEL_H);
		drawTextField(g, costFieldX, costFieldY, costFieldW, costStr, activeInput == 2, mouseX, mouseY);

		if (!floor.isKuudra()) {
			drawFieldLabel(g, "Wither Essence", witherFieldX, witherFieldY - LABEL_H);
			drawTextField(g, witherFieldX, witherFieldY, witherFieldW, witherEssenceStr, activeInput == 4, mouseX, mouseY);
			drawFieldLabel(g, "Undead Essence", undeadFieldX, undeadFieldY - LABEL_H);
			drawTextField(g, undeadFieldX, undeadFieldY, undeadFieldW, undeadEssenceStr, activeInput == 5, mouseX, mouseY);
		}

		g.text(font, "Loot", ox + MARGIN, lootRowY - LABEL_H, TEXT_SECONDARY);
		for (int i = 0; i < lootSlots.size(); i++) {
			int sx = ox + MARGIN + i * (lootSlotW + LOOT_GAP);
			drawLootSlot(g, sx, lootRowY, lootSlots.get(i), i == selectedSlot, mouseX, mouseY);
		}

		if (openDropdown == OpenDropdown.FLOOR) {
			drawFloorDropdown(g, mouseX, mouseY);
		} else if (openDropdown == OpenDropdown.CHEST) {
			drawChestDropdown(g, mouseX, mouseY);
		}

		drawButton(g, ox + WIN_W - MARGIN - 70, btnY, 70, BTN_H, "Save", mouseX, mouseY);
		drawButton(g, ox + WIN_W - MARGIN - 152, btnY, 70, BTN_H, "Cancel", mouseX, mouseY);

		if (selectedSlot != -1) {
			drawSearchOverlay(g, mouseX, mouseY);
		}
	}

	private void drawFieldLabel(GuiGraphicsExtractor g, String label, int x, int y) {
		g.text(font, label, x, y, TEXT_MUTED);
	}

	private void drawDropdownField(GuiGraphicsExtractor g, int x, int y, int w, String value, int valueColor, ItemStack icon,
		int mx, int my, boolean open) {
		boolean hov = contains(x, y, w, FIELD_H, mx, my);
		g.fill(x, y, x + w, y + FIELD_H, open ? 0xFF202A52 : hov ? 0xFF17172A : FIELD_BG);
		border(g, x, y, w, FIELD_H, open ? 0xFF6A6AB0 : hov ? FIELD_BORDER_HOVER : FIELD_BORDER);
		int textX = x + 6;
		if (icon != null && !icon.isEmpty()) {
			g.item(icon, x + 4, y + 1);
			textX = x + 22;
		}
		g.text(font, font.plainSubstrByWidth(value, w - (textX - x) - 8), textX, y + 5, valueColor);
	}

	private void drawChestDropdownField(GuiGraphicsExtractor g, int x, int y, int w, int mx, int my) {
		String title = chestDisplayTitle();
		ItemStack icon = DungeonLootScreen.lootChestIcon(title);
		drawDropdownField(g, x, y, w, chestType, DungeonLootScreen.lootChestColor(title), icon, mx, my,
			openDropdown == OpenDropdown.CHEST);
	}

	private void drawTextField(GuiGraphicsExtractor g, int x, int y, int w, String value, boolean active, int mx, int my) {
		boolean hov = contains(x, y, w, FIELD_H, mx, my);
		g.fill(x, y, x + w, y + FIELD_H, active ? 0xFF101830 : hov ? 0xFF17172A : FIELD_BG);
		border(g, x, y, w, FIELD_H, active ? 0xFF6A6AB0 : hov ? FIELD_BORDER_HOVER : FIELD_BORDER);
		String disp = value + (active && (System.currentTimeMillis() / 500 % 2 == 0) ? "_" : "");
		g.text(font, font.plainSubstrByWidth(disp, w - 8), x + 4, y + 5, TEXT_PRIMARY);
	}

	private void drawLootSlot(GuiGraphicsExtractor g, int x, int y, ManualLootSlot slot, boolean selected, int mx, int my) {
		boolean hov = contains(x, y, lootSlotW, FIELD_H, mx, my);
		g.fill(x, y, x + lootSlotW, y + FIELD_H, selected ? 0x771F315D : hov ? 0x441A1A31 : FIELD_BG);
		border(g, x, y, lootSlotW, FIELD_H, selected ? 0xFF6A6AB0 : hov ? FIELD_BORDER_HOVER : FIELD_BORDER);
		if (slot.itemId.isEmpty()) {
			g.text(font, "(Empty)", x + 6, y + 5, TEXT_MUTED);
			return;
		}
		ItemStack icon = DungeonLootScreen.lootItemIcon(slot.itemId);
		if (!icon.isEmpty()) {
			g.item(icon, x + 3, y + 1);
		}
		String label = slot.displayName.isEmpty() ? formatItemId(slot.itemId) : slot.displayName;
		int maxLabelW = lootSlotW - 24;
		if (isUltimateItem(slot.itemId)) {
			Component comp = Component.literal(font.plainSubstrByWidth(label, maxLabelW))
				.withStyle(Style.EMPTY.withBold(true).withColor(TextColor.fromRgb(0xFF55FF)));
			g.text(font, comp, x + 20, y + 5, 0xFFFF55FF);
		} else {
			g.text(font, font.plainSubstrByWidth(label, maxLabelW), x + 20, y + 5, DungeonLootScreen.lootItemColor(slot.itemId));
		}
	}

	private void drawFloorDropdown(GuiGraphicsExtractor g, int mx, int my) {
		int rowH = 16;
		int h = FLOOR_OPTIONS.length * rowH + 4;
		int x = floorFieldX;
		int y = floorFieldY + FIELD_H + 2;
		g.fill(x, y, x + floorFieldW, y + h, 0xFF111120);
		border(g, x, y, floorFieldW, h, 0xFF6A6AB0);
		for (int i = 0; i < FLOOR_OPTIONS.length; i++) {
			DungeonFloor option = FLOOR_OPTIONS[i];
			int ry = y + 2 + i * rowH;
			boolean hov = contains(x + 1, ry, floorFieldW - 2, rowH, mx, my);
			if (hov || option == floor) {
				g.fill(x + 1, ry, x + floorFieldW - 1, ry + rowH, option == floor ? 0x771F315D : 0x441A1A31);
			}
			int color = option.isKuudra() ? 0xFFAA0000 : TEXT_PRIMARY;
			g.text(font, option.name(), x + 6, ry + 4, color);
		}
	}

	private void drawChestDropdown(GuiGraphicsExtractor g, int mx, int my) {
		String[] options = chestOptions();
		int rowH = 18;
		int h = options.length * rowH + 4;
		int x = chestFieldX;
		int y = chestFieldY + FIELD_H + 2;
		g.fill(x, y, x + chestFieldW, y + h, 0xFF111120);
		border(g, x, y, chestFieldW, h, 0xFF6A6AB0);
		for (int i = 0; i < options.length; i++) {
			String option = options[i];
			int ry = y + 2 + i * rowH;
			boolean hov = contains(x + 1, ry, chestFieldW - 2, rowH, mx, my);
			if (hov || option.equals(chestType)) {
				g.fill(x + 1, ry, x + chestFieldW - 1, ry + rowH, option.equals(chestType) ? 0x771F315D : 0x441A1A31);
			}
			String title = option + " Chest";
			ItemStack icon = DungeonLootScreen.lootChestIcon(title);
			if (!icon.isEmpty()) {
				g.item(icon, x + 4, ry + 1);
			}
			g.text(font, option, x + 22, ry + 5, DungeonLootScreen.lootChestColor(title));
		}
	}

	private void drawSearchOverlay(GuiGraphicsExtractor g, int mx, int my) {
		int sx = searchPopupX;
		int sy = searchPopupY;
		int sw = searchPopupW;
		int sh = searchPopupH;
		g.fill(sx, sy, sx + sw, sy + sh, 0xFF111120);
		border(g, sx, sy, sw, sh, 0xFF6A6AB0);

		g.text(font, "Search Item", sx + 8, sy + 8, TEXT_MUTED);
		drawTextField(g, sx + 78, sy + 4, sw - 86, itemSearchQuery, activeInput == 3, mx, my);

		int listY = sy + SEARCH_HEADER_H;
		int visibleRows = Math.max(1, (sh - SEARCH_HEADER_H - 4) / SEARCH_ROW_H);
		for (int i = 0; i < visibleRows; i++) {
			int idx = i + searchScroll;
			if (idx >= searchResults.size()) break;
			PriceCache.SearchResult option = searchResults.get(idx);
			int ry = listY + i * SEARCH_ROW_H;
			boolean hov = contains(sx + 4, ry, sw - 8, SEARCH_ROW_H - 1, mx, my);
			if (hov) g.fill(sx + 4, ry, sx + sw - 4, ry + SEARCH_ROW_H - 1, 0x441A1A31);
			ItemStack icon = DungeonLootScreen.lootItemIcon(option.itemId());
			if (!icon.isEmpty()) {
				g.item(icon, sx + 8, ry - 1);
			}
			int color = DungeonLootScreen.lootItemColor(option.itemId());
			g.text(font, font.plainSubstrByWidth(option.displayName(), sw - 40), sx + 26, ry + 2, hov ? TEXT_PRIMARY : color);
		}
	}

	private void drawButton(GuiGraphicsExtractor g, int x, int y, int w, int h, String label, int mx, int my) {
		boolean hov = contains(x, y, w, h, mx, my);
		g.fill(x, y, x + w, y + h, hov ? 0xFF232846 : 0xFF171A2E);
		border(g, x, y, w, h, hov ? 0xFF5B5F92 : 0xFF3F426D);
		g.centeredText(font, label, x + w / 2, y + (h - 8) / 2, TEXT_PRIMARY);
	}

	private void border(GuiGraphicsExtractor g, int x, int y, int w, int h, int color) {
		g.fill(x, y, x + w, y + 1, color);
		g.fill(x, y + h - 1, x + w, y + h, color);
		g.fill(x, y, x + 1, y + h, color);
		g.fill(x + w - 1, y, x + w, y + h, color);
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean isRepeat) {
		if (event.button() != 0) return super.mouseClicked(event, isRepeat);
		int mx = (int) event.x();
		int my = (int) event.y();

		if (selectedSlot != -1) {
			int sx = searchPopupX;
			int sy = searchPopupY;
			int sw = searchPopupW;
			int sh = searchPopupH;
			if (contains(sx + 78, sy + 4, sw - 86, FIELD_H, mx, my)) {
				activeInput = 3;
				return true;
			}
			int listY = sy + SEARCH_HEADER_H;
			int visibleRows = Math.max(1, (sh - SEARCH_HEADER_H - 4) / SEARCH_ROW_H);
			for (int i = 0; i < visibleRows; i++) {
				int idx = i + searchScroll;
				if (idx >= searchResults.size()) break;
				int ry = listY + i * SEARCH_ROW_H;
				if (contains(sx + 4, ry, sw - 8, SEARCH_ROW_H - 1, mx, my)) {
					PriceCache.SearchResult res = searchResults.get(idx);
					lootSlots.get(selectedSlot).itemId = res.itemId();
					lootSlots.get(selectedSlot).displayName = res.displayName();
					selectedSlot = -1;
					activeInput = 0;
					return true;
				}
			}
			if (!contains(sx, sy, sw, sh, mx, my)) {
				selectedSlot = -1;
				activeInput = 0;
			}
			return true;
		}

		if (openDropdown == OpenDropdown.FLOOR) {
			if (handleFloorDropdownClick(mx, my)) return true;
			if (!contains(floorFieldX, floorFieldY, floorFieldW, FIELD_H, mx, my)) {
				openDropdown = OpenDropdown.NONE;
			}
			return true;
		}
		if (openDropdown == OpenDropdown.CHEST) {
			if (handleChestDropdownClick(mx, my)) return true;
			if (!contains(chestFieldX, chestFieldY, chestFieldW, FIELD_H, mx, my)) {
				openDropdown = OpenDropdown.NONE;
			}
			return true;
		}

		if (contains(ox + WIN_W - MARGIN - 70, btnY, 70, BTN_H, mx, my)) {
			save();
			return true;
		}
		if (contains(ox + WIN_W - MARGIN - 152, btnY, 70, BTN_H, mx, my)) {
			onClose();
			return true;
		}

		if (contains(floorFieldX, floorFieldY, floorFieldW, FIELD_H, mx, my)) {
			openDropdown = OpenDropdown.FLOOR;
			activeInput = 0;
			return true;
		}
		if (contains(chestFieldX, chestFieldY, chestFieldW, FIELD_H, mx, my)) {
			openDropdown = OpenDropdown.CHEST;
			activeInput = 0;
			return true;
		}
		if (contains(scoreFieldX, scoreFieldY, scoreFieldW, FIELD_H, mx, my)) {
			score = nextScore(score);
			return true;
		}
		if (contains(timeFieldX, timeFieldY, timeFieldW, FIELD_H, mx, my)) {
			activeInput = 1;
			return true;
		}
		if (contains(costFieldX, costFieldY, costFieldW, FIELD_H, mx, my)) {
			activeInput = 2;
			return true;
		}
		if (!floor.isKuudra()) {
			if (contains(witherFieldX, witherFieldY, witherFieldW, FIELD_H, mx, my)) {
				activeInput = 4;
				return true;
			}
			if (contains(undeadFieldX, undeadFieldY, undeadFieldW, FIELD_H, mx, my)) {
				activeInput = 5;
				return true;
			}
		}

		for (int i = 0; i < lootSlots.size(); i++) {
			int sx = ox + MARGIN + i * (lootSlotW + LOOT_GAP);
			if (contains(sx, lootRowY, lootSlotW, FIELD_H, mx, my)) {
				selectedSlot = i;
				activeInput = 3;
				itemSearchQuery = "";
				searchScroll = 0;
				refreshSearchResults();
				openDropdown = OpenDropdown.NONE;
				return true;
			}
		}

		activeInput = 0;
		return super.mouseClicked(event, isRepeat);
	}

	private boolean handleFloorDropdownClick(int mx, int my) {
		int y = floorFieldY + FIELD_H + 2;
		for (int i = 0; i < FLOOR_OPTIONS.length; i++) {
			int ry = y + 2 + i * 16;
			if (contains(floorFieldX + 1, ry, floorFieldW - 2, 16, mx, my)) {
				DungeonFloor next = FLOOR_OPTIONS[i];
				if (next.isKuudra() != floor.isKuudra()) {
					chestType = next.isKuudra() ? "Free" : "Bedrock";
				}
				floor = next;
				winH = computeWinH();
				ox = (width - WIN_W) / 2;
				oy = (height - winH) / 2;
				layoutFields();
				openDropdown = OpenDropdown.NONE;
				if (selectedSlot != -1) {
					searchScroll = 0;
					refreshSearchResults();
				}
				return true;
			}
		}
		return false;
	}

	private boolean handleChestDropdownClick(int mx, int my) {
		String[] options = chestOptions();
		int y = chestFieldY + FIELD_H + 2;
		for (int i = 0; i < options.length; i++) {
			int ry = y + 2 + i * 18;
			if (contains(chestFieldX + 1, ry, chestFieldW - 2, 18, mx, my)) {
				chestType = options[i];
				openDropdown = OpenDropdown.NONE;
				return true;
			}
		}
		return false;
	}

	private void refreshSearchResults() {
		searchResults.clear();
		searchResults.addAll(ManualLootSuggestions.search(floor, itemSearchQuery));
		if (searchScroll >= searchResults.size()) {
			searchScroll = Math.max(0, searchResults.size() - SEARCH_VISIBLE_ROWS);
		}
	}

	private static long parseRunTimeMillis(String timeStr) {
		if (timeStr == null || timeStr.isBlank()) return 0L;
		String[] parts = timeStr.split(":");
		try {
			if (parts.length == 2) {
				long minutes = Long.parseLong(parts[0].trim());
				long seconds = Long.parseLong(parts[1].trim());
				return Math.max(0L, (minutes * 60L + seconds) * 1000L);
			}
		} catch (NumberFormatException ignored) {
		}
		return 0L;
	}

	@Override
	public boolean charTyped(CharacterEvent event) {
		String typed = event.codepointAsString();
		if (typed == null || typed.isEmpty()) return super.charTyped(event);

		if (activeInput == 1) {
			for (int i = 0; i < typed.length(); i++) {
				char c = typed.charAt(i);
				if (Character.isDigit(c) || c == ':') timeStr += c;
			}
			return true;
		}
		if (activeInput == 2) {
			for (int i = 0; i < typed.length(); i++) {
				char c = typed.charAt(i);
				if (Character.isDigit(c)) costStr += c;
			}
			return true;
		}
		if (activeInput == 3) {
			itemSearchQuery += typed;
			searchScroll = 0;
			refreshSearchResults();
			return true;
		}
		if (activeInput == 4) {
			for (int i = 0; i < typed.length(); i++) {
				char c = typed.charAt(i);
				if (Character.isDigit(c)) witherEssenceStr += c;
			}
			return true;
		}
		if (activeInput == 5) {
			for (int i = 0; i < typed.length(); i++) {
				char c = typed.charAt(i);
				if (Character.isDigit(c)) undeadEssenceStr += c;
			}
			return true;
		}
		return super.charTyped(event);
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		if (event.key() == GLFW.GLFW_KEY_BACKSPACE) {
			if (activeInput == 1 && !timeStr.isEmpty()) timeStr = timeStr.substring(0, timeStr.length() - 1);
			else if (activeInput == 2 && !costStr.isEmpty()) costStr = costStr.substring(0, costStr.length() - 1);
			else if (activeInput == 3 && !itemSearchQuery.isEmpty()) {
				itemSearchQuery = itemSearchQuery.substring(0, itemSearchQuery.length() - 1);
				refreshSearchResults();
			} else if (activeInput == 4 && !witherEssenceStr.isEmpty()) witherEssenceStr = witherEssenceStr.substring(0, witherEssenceStr.length() - 1);
			else if (activeInput == 5 && !undeadEssenceStr.isEmpty()) undeadEssenceStr = undeadEssenceStr.substring(0, undeadEssenceStr.length() - 1);
			return true;
		}
		if (event.key() == GLFW.GLFW_KEY_ESCAPE) {
			if (openDropdown != OpenDropdown.NONE) {
				openDropdown = OpenDropdown.NONE;
				return true;
			}
			if (selectedSlot != -1) {
				selectedSlot = -1;
				activeInput = 0;
				return true;
			}
		}
		return super.keyPressed(event);
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
		if (selectedSlot != -1) {
			if (scrollY > 0) searchScroll = Math.max(0, searchScroll - 1);
			else if (scrollY < 0) searchScroll = Math.min(Math.max(0, searchResults.size() - SEARCH_VISIBLE_ROWS), searchScroll + 1);
			return true;
		}
		return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
	}

	private void save() {
		long cost = parseLong(costStr);

		List<DungeonLootEntry> entries = new ArrayList<>();
		if (!floor.isKuudra()) {
			addEssenceEntry(entries, "Wither Essence", "ESSENCE_WITHER", witherEssenceStr);
			addEssenceEntry(entries, "Undead Essence", "ESSENCE_UNDEAD", undeadEssenceStr);
		}
		for (ManualLootSlot slot : lootSlots) {
			if (!slot.itemId.isEmpty()) {
				String name = slot.displayName.isEmpty() ? formatItemId(slot.itemId) : slot.displayName;
				entries.add(new DungeonLootEntry(name, slot.itemId, 1));
			}
		}

		long value = DungeonProfitPricing.calculateLootValue(entries, DrtConfigManager.getConfig());
		long profit = value - cost;

		DungeonRunRecord record = new DungeonRunRecord(
			System.currentTimeMillis(),
			DrtConfigManager.getRunHistory().size() + 1,
			floor.name(),
			score,
			chestDisplayTitle(),
			cost,
			value,
			profit,
			entries
		);

		DrtClient.getTracker().recordManualRun(record, parseRunTimeMillis(timeStr));
		onClose();
	}

	private void addEssenceEntry(List<DungeonLootEntry> entries, String name, String itemId, String qtyStr) {
		int qty = (int) parseLong(qtyStr);
		if (qty > 0) {
			entries.add(new DungeonLootEntry(name, itemId, qty));
		}
	}

	private static long parseLong(String value) {
		if (value == null || value.isBlank()) return 0L;
		try {
			return Long.parseLong(value);
		} catch (NumberFormatException ignored) {
			return 0L;
		}
	}

	private String[] chestOptions() {
		return floor.isKuudra()
			? new String[] {"Free", "Paid"}
			: new String[] {"Wood", "Gold", "Diamond", "Emerald", "Obsidian", "Bedrock"};
	}

	private String chestDisplayTitle() {
		return chestType + " Chest";
	}

	private static String nextScore(String current) {
		for (int i = 0; i < SCORE_OPTIONS.length; i++) {
			if (SCORE_OPTIONS[i].equals(current)) {
				return SCORE_OPTIONS[(i + 1) % SCORE_OPTIONS.length];
			}
		}
		return "S+";
	}

	private static String formatItemId(String itemId) {
		if (itemId == null || itemId.isBlank()) return "Unknown";
		String lower = itemId.toLowerCase(Locale.ROOT).replace('_', ' ');
		String[] parts = lower.split("\\s+");
		StringBuilder sb = new StringBuilder();
		for (String part : parts) {
			if (part.isEmpty()) continue;
			if (sb.length() > 0) sb.append(' ');
			sb.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
		}
		return sb.toString();
	}

	private static boolean isUltimateItem(String itemId) {
		return itemId != null && itemId.toUpperCase(Locale.ROOT).startsWith("ENCHANTMENT_ULTIMATE_");
	}

	private static boolean contains(int x, int y, int w, int h, int mx, int my) {
		return mx >= x && mx < x + w && my >= y && my < y + h;
	}

	@Override
	public void onClose() {
		if (minecraft != null) {
			minecraft.setScreen(parent);
		}
	}

	private enum OpenDropdown {
		NONE, FLOOR, CHEST
	}

	private static class ManualLootSlot {
		String itemId = "";
		String displayName = "";
	}
}
