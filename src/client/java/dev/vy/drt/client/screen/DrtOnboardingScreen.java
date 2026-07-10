package dev.vy.drt.client.screen;

import dev.vy.drt.client.tracker.DungeonRunTrackerFeature;
import dev.vy.drt.config.DrtConfig;
import dev.vy.drt.config.DrtConfigManager;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;
import org.lwjgl.glfw.GLFW;

public final class DrtOnboardingScreen extends Screen {
	private static final int PANEL_BG = 0xF00D0D18;
	private static final int PANEL_ALT = 0xFF111222;
	private static final int PANEL_ROW = 0xFF151729;
	private static final int PANEL_ROW_HOVER = 0xFF1D2139;
	private static final int BORDER = 0xFF35385D;
	private static final int BORDER_ACTIVE = 0xFF6268A8;
	private static final int TEXT = 0xFFE6E8F2;
	private static final int MUTED = 0xFF9AA4BD;
	private static final int DIM = 0xFF747E99;
	private static final int GREEN = 0xFF63D184;
	private static final int RED = 0xFFFF6B6B;
	private static final int GOLD = 0xFFFFC857;
	private static final int ROW_H = 22;
	private static final int GAP = 4;
	private static final int DROPDOWN_ROW_H = 18;
	private static final int PANEL_MIN_W = 330;
	private static final int PANEL_MAX_W = 430;
	private static final int RIGHT_PAD = 18;
	private static final int CONTROL_W = 118;
	private static final int CONTROL_GAP = 8;
	private static final String DISCORD_INVITE_URL = "https://discord.com/invite/R5NdTVRDpb";

	private static final String[] PET_RARITIES = {"COMMON", "UNCOMMON", "RARE", "EPIC", "LEGENDARY"};

	private final DungeonRunTrackerFeature trackerFeature;
	private final List<ClickTarget> clickTargets = new ArrayList<>();

	private boolean kuudraPetEnabled;
	private String kuudraPetRarity;
	private int kuudraPetLevel;
	private String kuudraPetLevelText;
	private boolean petLevelFocused;
	private String kuudraFaction;
	private String bazaarPriceMode;
	private boolean forceSalvageArmor;
	private boolean forceSalvageWands;
	private boolean forceSalvageEquipment;
	private boolean coolForgedEnabled;
	private int coolForgedLevel;
	private Dropdown openDropdown = Dropdown.NONE;

	private int ox;
	private int oy;
	private int winW;
	private int winH;
	private int factionDropdownX;
	private int factionDropdownY;
	private int factionDropdownW;
	private int priceDropdownX;
	private int priceDropdownY;
	private int priceDropdownW;
	private int salvageDropdownX;
	private int salvageDropdownY;
	private int salvageDropdownW;
	private int petRarityDropdownX;
	private int petRarityDropdownY;
	private int petRarityDropdownW;
	private int petLevelInputX;
	private int petLevelInputY;
	private int petLevelInputW;

	public DrtOnboardingScreen(DungeonRunTrackerFeature trackerFeature) {
		super(Component.literal("DRT Setup"));
		this.trackerFeature = trackerFeature;
		DrtConfig config = DrtConfigManager.getConfig();
		kuudraPetEnabled = config.kuudraPetEnabled;
		kuudraPetRarity = normalizeRarity(config.kuudraPetRarity);
		kuudraPetLevel = clamp(config.kuudraPetLevel, 1, 100);
		kuudraPetLevelText = Integer.toString(kuudraPetLevel);
		kuudraFaction = normalizeFaction(config.kuudraFaction);
		bazaarPriceMode = normalizeBazaarMode(config.bazaarPriceMode);
		forceSalvageArmor = config.forceSalvageArmor;
		forceSalvageWands = config.forceSalvageWands;
		forceSalvageEquipment = config.forceSalvageEquipment;
		coolForgedEnabled = config.coolForgedEnabled;
		coolForgedLevel = clamp(config.coolForgedLevel, 1, 5);
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
		updateLayout();
		clickTargets.clear();

		g.fill(0, 0, width, height, 0xA0000000);
		g.fill(ox, oy, ox + winW, oy + winH, PANEL_BG);
		border(g, ox, oy, winW, winH, BORDER);

		g.text(font, "DRT Setup", ox + 12, oy + 10, TEXT);
		String subtitle = "Basic settings";
		g.text(font, subtitle, ox + winW - 12 - font.width(subtitle), oy + 10, DIM);

		int y = oy + 34;
		drawSection(g, "General", y);
		y += 13;
		drawPositionRow(g, mouseX, mouseY, y);
		y += ROW_H + GAP;
		drawPriceModeRow(g, mouseX, mouseY, y);
		y += ROW_H + GAP;

		drawSection(g, "Kuudra", y + 2);
		y += 16;
		drawFactionRow(g, mouseX, mouseY, y);
		y += ROW_H + GAP;
		drawPetRow(g, mouseX, mouseY, y);
		y += ROW_H + GAP;
		if (kuudraPetEnabled) {
			drawPetOptionsRow(g, mouseX, mouseY, y);
			y += ROW_H + GAP;
		}
		drawForceSalvageRow(g, mouseX, mouseY, y);
		y += ROW_H + GAP;
		drawCoolForgedRow(g, mouseX, mouseY, y);

		drawFooter(g, mouseX, mouseY);
		drawDropdownMenus(g, mouseX, mouseY);
		drawHoveredTooltip(g, mouseX, mouseY);
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean isRepeat) {
		int mx = (int) event.x();
		int my = (int) event.y();
		for (int i = clickTargets.size() - 1; i >= 0; i--) {
			ClickTarget target = clickTargets.get(i);
			if ((target.button == -1 || target.button == event.button()) && contains(target.x, target.y, target.w, target.h, mx, my)) {
				target.action.run();
				return true;
			}
		}
		if (petLevelFocused) {
			commitPetLevelInput();
			petLevelFocused = false;
		}
		if (openDropdown != Dropdown.NONE) {
			openDropdown = Dropdown.NONE;
			return true;
		}
		return super.mouseClicked(event, isRepeat);
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		if (petLevelFocused) {
			if (event.key() == GLFW.GLFW_KEY_BACKSPACE) {
				if (!kuudraPetLevelText.isEmpty()) {
					kuudraPetLevelText = kuudraPetLevelText.substring(0, kuudraPetLevelText.length() - 1);
				}
				return true;
			}
			if (event.key() == GLFW.GLFW_KEY_DELETE) {
				kuudraPetLevelText = "";
				return true;
			}
			if (event.key() == GLFW.GLFW_KEY_ENTER || event.key() == GLFW.GLFW_KEY_KP_ENTER) {
				commitPetLevelInput();
				petLevelFocused = false;
				return true;
			}
		}
		if (event.key() == GLFW.GLFW_KEY_ESCAPE) {
			saveSettings(false);
			super.onClose();
			return true;
		}
		if (event.key() == GLFW.GLFW_KEY_ENTER || event.key() == GLFW.GLFW_KEY_KP_ENTER) {
			saveSettings(true);
			super.onClose();
			return true;
		}
		return super.keyPressed(event);
	}

	@Override
	public boolean charTyped(CharacterEvent event) {
		if (!petLevelFocused) return super.charTyped(event);
		String typed = event.codepointAsString();
		if (typed == null || typed.isBlank()) return true;
		for (int i = 0; i < typed.length(); i++) {
			char c = typed.charAt(i);
			if (!Character.isDigit(c)) continue;
			if (kuudraPetLevelText.length() < 3) {
				kuudraPetLevelText += c;
			}
		}
		return true;
	}

	@Override
	public void onClose() {
		saveSettings(false);
		super.onClose();
	}

	private void updateLayout() {
		winW = Math.max(PANEL_MIN_W, Math.min(PANEL_MAX_W, width - 24));
		int kuudraRows = 4 + (kuudraPetEnabled ? 1 : 0);
		int desiredH = 34
			+ 13 + (ROW_H + GAP) * 2
			+ 16 + kuudraRows * (ROW_H + GAP) - GAP
			+ 44;
		winH = Math.max(252, Math.min(Math.max(1, height - 24), desiredH));
		if (winW > width - 8) winW = Math.max(1, width - 8);
		if (winH > height - 8) winH = Math.max(1, height - 8);
		ox = (width - winW) / 2;
		oy = (height - winH) / 2;
	}

	private int controlRight() {
		return ox + winW - RIGHT_PAD;
	}

	private int controlX(int controlWidth) {
		return controlRight() - controlWidth;
	}

	private void drawSection(GuiGraphicsExtractor g, String label, int y) {
		g.text(font, label.toUpperCase(Locale.ROOT), ox + 12, y, MUTED);
		g.fill(ox + 12 + font.width(label) + 8, y + 4, ox + winW - 12, y + 5, 0x5535385D);
	}

	private void drawPositionRow(GuiGraphicsExtractor g, int mouseX, int mouseY, int y) {
		drawRowBase(g, mouseX, mouseY, y);
		g.text(font, "Tracker position", ox + 18, y + 7, TEXT);
		String value = trackerFeature.getHudX() + ", " + trackerFeature.getHudY() + "  " + trackerFeature.getHudScalePercent() + "%";
		int buttonX = controlX(64);
		int valueX = buttonX - CONTROL_GAP - font.width(value);
		if (valueX > ox + 128) {
			g.text(font, value, valueX, y + 7, MUTED);
		}
		drawSmallButton(g, buttonX, y + 3, 64, 16, "Move", false, () -> {
			saveSettings(false);
			Minecraft client = Minecraft.getInstance();
			client.setScreen(new DungeonTrackerPositionScreen(trackerFeature, this));
		}, "Pick where the DRT HUD sits");
	}

	private void drawPriceModeRow(GuiGraphicsExtractor g, int mouseX, int mouseY, int y) {
		drawRowBase(g, mouseX, mouseY, y);
		g.text(font, "BZ pricing", ox + 18, y + 7, TEXT);
		priceDropdownW = CONTROL_W;
		priceDropdownX = controlX(priceDropdownW);
		priceDropdownY = y + 3;
		drawDropdownButton(g, mouseX, mouseY, priceDropdownX, priceDropdownY, priceDropdownW, 16,
			priceModeLabel(), openDropdown == Dropdown.PRICE, () -> toggleDropdown(Dropdown.PRICE), "Select Bazaar instant or order pricing");
	}

	private void drawFactionRow(GuiGraphicsExtractor g, int mouseX, int mouseY, int y) {
		drawRowBase(g, mouseX, mouseY, y);
		g.text(font, "Faction", ox + 18, y + 7, TEXT);
		factionDropdownW = CONTROL_W;
		factionDropdownX = controlX(factionDropdownW);
		factionDropdownY = y + 3;
		drawDropdownButton(g, mouseX, mouseY, factionDropdownX, factionDropdownY, factionDropdownW, 16,
			titleCase(kuudraFaction), openDropdown == Dropdown.FACTION, () -> toggleDropdown(Dropdown.FACTION), "Select Kuudra faction");
	}

	private void drawPetRow(GuiGraphicsExtractor g, int mouseX, int mouseY, int y) {
		drawRowBase(g, mouseX, mouseY, y);
		g.text(font, "Kuudra pet", ox + 18, y + 7, TEXT);
		drawToggle(g, controlX(46), y + 3, kuudraPetEnabled, () -> {
			kuudraPetEnabled = !kuudraPetEnabled;
			openDropdown = Dropdown.NONE;
			petLevelFocused = false;
		}, "Include Kuudra pet effects");
	}

	private void drawPetOptionsRow(GuiGraphicsExtractor g, int mouseX, int mouseY, int y) {
		drawRowBase(g, mouseX, mouseY, y);
		g.text(font, "Pet rarity / level", ox + 18, y + 7, TEXT);
		petLevelInputW = 44;
		petLevelInputX = controlX(petLevelInputW);
		petLevelInputY = y + 3;
		petRarityDropdownW = 88;
		petRarityDropdownX = petLevelInputX - CONTROL_GAP - petRarityDropdownW;
		petRarityDropdownY = y + 3;
		drawDropdownButton(g, mouseX, mouseY, petRarityDropdownX, petRarityDropdownY, petRarityDropdownW, 16,
			titleCase(kuudraPetRarity), openDropdown == Dropdown.PET_RARITY, () -> toggleDropdown(Dropdown.PET_RARITY), "Select Kuudra pet rarity");
		drawTextInput(g, mouseX, mouseY, petLevelInputX, petLevelInputY, petLevelInputW, 16,
			petLevelFocused ? kuudraPetLevelText : Integer.toString(kuudraPetLevel), petLevelFocused, () -> {
				openDropdown = Dropdown.NONE;
				petLevelFocused = true;
				kuudraPetLevelText = Integer.toString(kuudraPetLevel);
			}, "Type Kuudra pet level");
	}

	private void drawForceSalvageRow(GuiGraphicsExtractor g, int mouseX, int mouseY, int y) {
		drawRowBase(g, mouseX, mouseY, y);
		g.text(font, "Force salvage", ox + 18, y + 7, TEXT);
		salvageDropdownW = CONTROL_W;
		salvageDropdownX = controlX(salvageDropdownW);
		salvageDropdownY = y + 3;
		drawDropdownButton(g, mouseX, mouseY, salvageDropdownX, salvageDropdownY, salvageDropdownW, 16,
			salvageSummary(), openDropdown == Dropdown.SALVAGE, () -> toggleDropdown(Dropdown.SALVAGE), "Select forced salvage types");
	}

	private void drawCoolForgedRow(GuiGraphicsExtractor g, int mouseX, int mouseY, int y) {
		drawRowBase(g, mouseX, mouseY, y);
		g.text(font, "Cool Forged", ox + 18, y + 7, TEXT);
		int levelX = controlX(68);
		int toggleX = levelX - CONTROL_GAP - 46;
		drawToggle(g, toggleX, y + 3, coolForgedEnabled, () -> coolForgedEnabled = !coolForgedEnabled, "Increase salvage essence value");
		String label = "Lv " + coolForgedLevel;
		drawStepper(g, levelX, y + 3, 68, 16, label, !coolForgedEnabled,
			() -> coolForgedLevel = clamp(coolForgedLevel - 1, 1, 5),
			() -> coolForgedLevel = clamp(coolForgedLevel + 1, 1, 5),
			"Bonus +" + coolForgedBonus() + "% essence");
	}

	private void drawFooter(GuiGraphicsExtractor g, int mouseX, int mouseY) {
		int y = oy + winH - 30;
		g.fill(ox + 10, y - 5, ox + winW - 10, y - 4, 0x5535385D);
		int doneX = controlX(58);
		int discordW = 88;
		int discordX = ox + (winW - discordW) / 2;
		drawSmallButton(g, ox + 18, y, 48, 18, "Skip", false, () -> {
			saveSettings(true);
			super.onClose();
		}, "Use current settings and stop showing setup", DIM);
		drawSmallButton(g, discordX, y, discordW, 18, "Join Discord", false, this::openDiscordInvite, "Open the DRT Discord invite", GOLD);
		drawSmallButton(g, doneX, y, 58, 18, "Done", false, () -> {
			saveSettings(true);
			super.onClose();
		}, "Save and finish onboarding");
	}

	private void openDiscordInvite() {
		Util.getPlatform().openUri(URI.create(DISCORD_INVITE_URL));
	}

	private void drawDropdownMenus(GuiGraphicsExtractor g, int mouseX, int mouseY) {
		if (openDropdown == Dropdown.FACTION) {
			int y = factionDropdownY + 18;
			drawDropdownOption(g, mouseX, mouseY, factionDropdownX, y, factionDropdownW, "Mage", kuudraFaction.equals("MAGE"), () -> {
				kuudraFaction = "MAGE";
				openDropdown = Dropdown.NONE;
			}, "Use Mage Kuudra key costs");
			drawDropdownOption(g, mouseX, mouseY, factionDropdownX, y + DROPDOWN_ROW_H, factionDropdownW, "Barbarian", kuudraFaction.equals("BARBARIAN"), () -> {
				kuudraFaction = "BARBARIAN";
				openDropdown = Dropdown.NONE;
			}, "Use Barbarian Kuudra key costs");
			return;
		}

		if (openDropdown == Dropdown.PRICE) {
			int y = priceDropdownY + 18;
			drawDropdownOption(g, mouseX, mouseY, priceDropdownX, y, priceDropdownW, "Instant", bazaarPriceMode.equals("INSTANT"), () -> {
				bazaarPriceMode = "INSTANT";
				openDropdown = Dropdown.NONE;
			}, "Sell loot instantly and buy costs instantly");
			drawDropdownOption(g, mouseX, mouseY, priceDropdownX, y + DROPDOWN_ROW_H, priceDropdownW, "Order", bazaarPriceMode.equals("ORDER"), () -> {
				bazaarPriceMode = "ORDER";
				openDropdown = Dropdown.NONE;
			}, "Use sell offers for loot and buy orders for costs");
			return;
		}

		if (openDropdown == Dropdown.PET_RARITY) {
			int y = petRarityDropdownY + 18;
			for (int i = 0; i < PET_RARITIES.length; i++) {
				String option = PET_RARITIES[i];
				drawDropdownOption(g, mouseX, mouseY, petRarityDropdownX, y + DROPDOWN_ROW_H * i, petRarityDropdownW, titleCase(option), kuudraPetRarity.equals(option), () -> {
					kuudraPetRarity = option;
					openDropdown = Dropdown.NONE;
				}, "Use " + titleCase(option) + " Kuudra pet scaling");
			}
			return;
		}

		if (openDropdown == Dropdown.SALVAGE) {
			int y = salvageDropdownY + 18;
			drawDropdownOption(g, mouseX, mouseY, salvageDropdownX, y, salvageDropdownW, "Armor", forceSalvageArmor, () -> forceSalvageArmor = !forceSalvageArmor, "Always value dungeon armor as salvage");
			drawDropdownOption(g, mouseX, mouseY, salvageDropdownX, y + DROPDOWN_ROW_H, salvageDropdownW, "Wands", forceSalvageWands, () -> forceSalvageWands = !forceSalvageWands, "Always value dungeon wands as salvage");
			drawDropdownOption(g, mouseX, mouseY, salvageDropdownX, y + DROPDOWN_ROW_H * 2, salvageDropdownW, "Equipment", forceSalvageEquipment, () -> forceSalvageEquipment = !forceSalvageEquipment, "Always value dungeon equipment as salvage");
		}
	}

	private void drawRowBase(GuiGraphicsExtractor g, int mouseX, int mouseY, int y) {
		boolean hovered = contains(ox + 12, y, winW - 24, ROW_H, mouseX, mouseY);
		g.fill(ox + 12, y, ox + winW - 12, y + ROW_H, hovered ? PANEL_ROW_HOVER : PANEL_ROW);
		g.fill(ox + 16, y + ROW_H - 1, ox + winW - 16, y + ROW_H, 0x3335385D);
	}

	private void drawToggle(GuiGraphicsExtractor g, int x, int y, boolean enabled, Runnable onClick, String tooltip) {
		drawSmallButton(g, x, y, 46, 16, enabled ? "ON" : "OFF", false, onClick, tooltip, enabled ? GREEN : RED);
	}

	private void drawDropdownButton(GuiGraphicsExtractor g, int mouseX, int mouseY, int x, int y, int w, int h, String label, boolean open, Runnable onClick, String tooltip) {
		boolean hovered = contains(x, y, w, h, mouseX, mouseY);
		g.fill(x, y, x + w, y + h, open ? 0xFF23375B : hovered ? 0xFF222641 : 0xFF171B30);
		border(g, x, y, w, h, open ? BORDER_ACTIVE : hovered ? 0xFF51557E : BORDER);
		String arrow = open ? "▲" : "▼";
		String safeLabel = ellipsize(label, Math.max(0, w - 24));
		g.text(font, safeLabel, x + 6, y + (h - font.lineHeight) / 2, TEXT);
		int arrowBoxW = 18;
		int arrowX = x + w - arrowBoxW + (arrowBoxW - font.width(arrow)) / 2;
		g.text(font, arrow, arrowX, y + (h - font.lineHeight) / 2, MUTED);
		clickTargets.add(new ClickTarget(x, y, w, h, 0, onClick, tooltip));
	}

	private void drawDropdownOption(GuiGraphicsExtractor g, int mouseX, int mouseY, int x, int y, int w, String label, boolean active, Runnable onClick, String tooltip) {
		boolean hovered = contains(x, y, w, DROPDOWN_ROW_H, mouseX, mouseY);
		g.fill(x, y, x + w, y + DROPDOWN_ROW_H, active ? 0xFF23375B : hovered ? 0xFF222641 : 0xFF101322);
		border(g, x, y, w, DROPDOWN_ROW_H, active ? BORDER_ACTIVE : BORDER);
		g.text(font, label, x + 6, y + 5, active ? TEXT : MUTED);
		clickTargets.add(new ClickTarget(x, y, w, DROPDOWN_ROW_H, 0, onClick, tooltip));
	}

	private void toggleDropdown(Dropdown dropdown) {
		if (petLevelFocused) {
			commitPetLevelInput();
			petLevelFocused = false;
		}
		openDropdown = openDropdown == dropdown ? Dropdown.NONE : dropdown;
	}

	private void drawSegment(GuiGraphicsExtractor g, int mouseX, int mouseY, int x, int y, int w, int h, String label, boolean active, Runnable onClick, String tooltip) {
		boolean hovered = contains(x, y, w, h, mouseX, mouseY);
		int bg = active ? 0xFF23375B : hovered ? 0xFF222641 : 0xFF15182B;
		int border = active ? BORDER_ACTIVE : hovered ? 0xFF51557E : BORDER;
		g.fill(x, y, x + w, y + h, bg);
		border(g, x, y, w, h, border);
		g.text(font, label, x + (w - font.width(label)) / 2, y + 4, active ? TEXT : MUTED);
		clickTargets.add(new ClickTarget(x, y, w, h, 0, onClick, tooltip));
	}

	private void drawSmallButton(GuiGraphicsExtractor g, int x, int y, int w, int h, String label, boolean disabled, Runnable onClick, String tooltip) {
		drawSmallButton(g, x, y, w, h, label, disabled, onClick, tooltip, disabled ? DIM : TEXT);
	}

	private void drawSmallButton(GuiGraphicsExtractor g, int x, int y, int w, int h, String label, boolean disabled, Runnable onClick, String tooltip, int textColor) {
		g.fill(x, y, x + w, y + h, disabled ? 0xFF10121E : 0xFF171B30);
		border(g, x, y, w, h, disabled ? 0xFF252842 : BORDER);
		g.text(font, label, x + (w - font.width(label)) / 2, y + (h - font.lineHeight) / 2, textColor);
		if (!disabled) clickTargets.add(new ClickTarget(x, y, w, h, 0, onClick, tooltip));
	}

	private void drawTextInput(GuiGraphicsExtractor g, int mouseX, int mouseY, int x, int y, int w, int h, String value, boolean focused, Runnable onClick, String tooltip) {
		boolean hovered = contains(x, y, w, h, mouseX, mouseY);
		g.fill(x, y, x + w, y + h, focused ? 0xFF10172E : hovered ? 0xFF20243D : PANEL_ALT);
		border(g, x, y, w, h, focused ? BORDER_ACTIVE : hovered ? 0xFF51557E : BORDER);
		String safeValue = ellipsize(value == null ? "" : value, Math.max(0, w - 8));
		int textX = x + 4;
		g.text(font, safeValue, textX, y + (h - font.lineHeight) / 2, focused ? TEXT : MUTED);
		if (focused && (System.currentTimeMillis() / 500L) % 2L == 0L) {
			int caretX = Math.min(x + w - 4, textX + font.width(safeValue) + 1);
			g.fill(caretX, y + 3, caretX + 1, y + h - 3, TEXT);
		}
		clickTargets.add(new ClickTarget(x, y, w, h, 0, onClick, tooltip));
	}

	private void drawStepper(GuiGraphicsExtractor g, int x, int y, int w, int h, String value, boolean disabled, Runnable dec, Runnable inc, String tooltip) {
		int side = 16;
		drawSmallButton(g, x, y, side, h, "-", disabled, dec, tooltip);
		g.fill(x + side, y, x + w - side, y + h, disabled ? 0xFF10121E : PANEL_ALT);
		g.fill(x + side, y, x + w - side, y + 1, disabled ? 0xFF252842 : BORDER);
		g.fill(x + side, y + h - 1, x + w - side, y + h, disabled ? 0xFF252842 : BORDER);
		g.text(font, value, x + side + (w - side * 2 - font.width(value)) / 2, y + (h - font.lineHeight) / 2, disabled ? DIM : TEXT);
		drawSmallButton(g, x + w - side, y, side, h, "+", disabled, inc, tooltip);
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

	private String salvageSummary() {
		int selected = (forceSalvageArmor ? 1 : 0) + (forceSalvageWands ? 1 : 0) + (forceSalvageEquipment ? 1 : 0);
		if (selected == 0) return "None";
		if (selected == 3) return "All";
		List<String> labels = new ArrayList<>();
		if (forceSalvageArmor) labels.add("Armor");
		if (forceSalvageWands) labels.add("Wands");
		if (forceSalvageEquipment) labels.add("EQ");
		return String.join(" + ", labels);
	}

	private int coolForgedBonus() {
		return coolForgedLevel * 4;
	}

	private void saveSettings(boolean complete) {
		commitPetLevelInput();
		boolean completed = complete || DrtConfigManager.getConfig().onboardingComplete;
		DrtConfigManager.updateOnboardingSettings(
			completed,
			kuudraFaction,
			kuudraPetEnabled,
			kuudraPetRarity,
			kuudraPetLevel,
			forceSalvageArmor,
			forceSalvageWands,
			forceSalvageEquipment,
			coolForgedEnabled,
			coolForgedLevel,
			bazaarPriceMode
		);
	}

	private static String normalizeFaction(String faction) {
		if (faction == null || faction.isBlank()) return "MAGE";
		String normalized = faction.trim().toUpperCase(Locale.ROOT);
		return normalized.equals("BARBARIAN") ? "BARBARIAN" : "MAGE";
	}

	private static String normalizeRarity(String rarity) {
		if (rarity == null || rarity.isBlank()) return "LEGENDARY";
		String normalized = rarity.trim().toUpperCase(Locale.ROOT);
		for (String option : PET_RARITIES) {
			if (option.equals(normalized)) return normalized;
		}
		return "LEGENDARY";
	}

	private static String normalizeBazaarMode(String mode) {
		if (mode == null || mode.isBlank()) return "INSTANT";
		String normalized = mode.trim().toUpperCase(Locale.ROOT);
		return switch (normalized) {
			case "ORDER", "SELL_OFFER", "BUY_ORDER" -> "ORDER";
			default -> "INSTANT";
		};
	}

	private String priceModeLabel() {
		return bazaarPriceMode.equals("ORDER") ? "Order" : "Instant";
	}

	private static String titleCase(String value) {
		if (value == null || value.isBlank()) return "";
		String lower = value.toLowerCase(Locale.ROOT);
		return Character.toUpperCase(lower.charAt(0)) + lower.substring(1);
	}

	private String ellipsize(String value, int maxWidth) {
		if (value == null) return "";
		if (font.width(value) <= maxWidth) return value;
		String ellipsis = "...";
		int ellipsisWidth = font.width(ellipsis);
		StringBuilder builder = new StringBuilder();
		for (int i = 0; i < value.length(); i++) {
			char c = value.charAt(i);
			if (font.width(builder.toString() + c) + ellipsisWidth > maxWidth) break;
			builder.append(c);
		}
		return builder.append(ellipsis).toString();
	}

	private static int clamp(int value, int min, int max) {
		return Math.max(min, Math.min(max, value));
	}

	private void commitPetLevelInput() {
		kuudraPetLevel = clamp(parseInt(kuudraPetLevelText, kuudraPetLevel), 1, 100);
		kuudraPetLevelText = Integer.toString(kuudraPetLevel);
	}

	private static int parseInt(String value, int fallback) {
		if (value == null || value.isBlank()) return fallback;
		try {
			return Integer.parseInt(value.trim());
		} catch (NumberFormatException ignored) {
			return fallback;
		}
	}

	private static boolean contains(int x, int y, int w, int h, int mx, int my) {
		return mx >= x && mx < x + w && my >= y && my < y + h;
	}

	private static void border(GuiGraphicsExtractor g, int x, int y, int w, int h, int color) {
		g.fill(x, y, x + w, y + 1, color);
		g.fill(x, y + h - 1, x + w, y + h, color);
		g.fill(x, y, x + 1, y + h, color);
		g.fill(x + w - 1, y, x + w, y + h, color);
	}

	private enum Dropdown { NONE, FACTION, PRICE, PET_RARITY, SALVAGE }

	private record ClickTarget(int x, int y, int w, int h, int button, Runnable action, String tooltip) {
	}
}
