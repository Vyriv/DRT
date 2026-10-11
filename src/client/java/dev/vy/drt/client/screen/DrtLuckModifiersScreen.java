package dev.vy.drt.client.screen;

import dev.vy.drt.client.luck.LuckRunCapture;
import dev.vy.drt.luck.capture.LuckModifierProfile;
import dev.vy.drt.luck.rules.ModifierPreset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

public final class DrtLuckModifiersScreen extends Screen {
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
	private static final int ROW_H = 22;
	private static final int GAP = 4;
	private static final int PANEL_MIN_W = 330;
	private static final int PANEL_MAX_W = 430;
	private static final int PANEL_H = 252;
	private static final int RIGHT_PAD = 18;
	private static final int STEPPER_W = 104;
	private static final int ROWS_TOP = 30;
	private static final String UNKNOWN = "Unknown";

	private final Screen parent;
	private final List<ClickTarget> clickTargets = new ArrayList<>();
	private final List<OptionRow<?>> rows;
	private String status = "";
	private int statusColor = DIM;

	private int ox;
	private int oy;
	private int winW;
	private int winH;

	public DrtLuckModifiersScreen(Screen parent) {
		super(Component.literal("Luck Rating Modifiers"));
		this.parent = parent;
		ModifierPreset preset = LuckRunCapture.profile().toPreset();
		List<Integer> levels10 = levels(10);
		rows = List.of(
			new OptionRow<>("Boss Luck", "Mort's Boss Luck perk. Adds flat chest quality",
				withUnknown(Arrays.asList(ModifierPreset.BossLuck.values())), preset.bossLuck(), DrtLuckModifiersScreen::bossLuckLabel),
			new OptionRow<>("Treasure accessory", "Highest Treasure accessory you own. Multiplies chest quality",
				withUnknown(Arrays.asList(ModifierPreset.TreasureAccessory.values())), preset.treasureAccessory(), DrtLuckModifiersScreen::treasureLabel),
			new OptionRow<>("Catacombs Box", "Catacombs Box attribute level. Adds chest quality",
				withUnknown(levels10), preset.catacombsBoxLevel(), String::valueOf),
			new OptionRow<>("Echo of Boxes", "Echo of Boxes attribute level. Boosts Catacombs Box",
				withUnknown(levels10), preset.echoOfBoxesLevel(), String::valueOf),
			new OptionRow<>("Echo of Echoes", "Echo of Echoes attribute level. Boosts Echo of Boxes",
				withUnknown(levels10), preset.echoOfEchoesLevel(), String::valueOf),
			new OptionRow<>("Pity", "Pity attribute level. Fills the RNG Meter faster",
				withUnknown(levels10), preset.pityLevel(), String::valueOf)
		);
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

		g.text(font, "Luck Rating modifiers", ox + 12, oy + 10, TEXT);
		String subtitle = "Global preset";
		g.text(font, subtitle, ox + winW - 12 - font.width(subtitle), oy + 10, DIM);

		int y = oy + ROWS_TOP;
		for (OptionRow<?> row : rows) {
			drawOptionRow(g, mouseX, mouseY, y, row);
			y += ROW_H + GAP;
		}

		int hintW = winW - 24;
		g.text(font, ellipsize("Applies to runs completed after saving.", hintW), ox + 12, y + 2, DIM);
		g.text(font, ellipsize("Unknown is rated as a range, never as zero.", hintW), ox + 12, y + 4 + font.lineHeight, DIM);

		drawFooter(g, mouseX, mouseY);
		drawHoveredTooltip(g, mouseX, mouseY);
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean isRepeat) {
		int mx = (int) event.x();
		int my = (int) event.y();
		for (int i = clickTargets.size() - 1; i >= 0; i--) {
			ClickTarget target = clickTargets.get(i);
			if (target.button == event.button() && contains(target.x, target.y, target.w, target.h, mx, my)) {
				target.action.run();
				return true;
			}
		}
		return super.mouseClicked(event, isRepeat);
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		if (event.key() == GLFW.GLFW_KEY_ESCAPE) {
			if (save()) closeFully();
			return true;
		}
		if (event.key() == GLFW.GLFW_KEY_ENTER || event.key() == GLFW.GLFW_KEY_KP_ENTER) {
			if (save()) goBack();
			return true;
		}
		return super.keyPressed(event);
	}

	@Override
	public void onClose() {
		if (save()) goBack();
	}

	private void goBack() {
		if (minecraft != null && parent != null) {
			minecraft.setScreen(parent);
			return;
		}
		super.onClose();
	}

	private void closeFully() {
		if (parent instanceof DrtOnboardingScreen onboarding) {
			onboarding.onClose();
			return;
		}
		super.onClose();
	}

	private boolean save() {
		LuckModifierProfile profile = new LuckModifierProfile();
		profile.bossLuck = rows.get(0).selected() instanceof ModifierPreset.BossLuck boss ? boss.name() : null;
		profile.treasureAccessory = rows.get(1).selected() instanceof ModifierPreset.TreasureAccessory t ? t.name() : null;
		profile.catacombsBoxLevel = (Integer) rows.get(2).selected();
		profile.echoOfBoxesLevel = (Integer) rows.get(3).selected();
		profile.echoOfEchoesLevel = (Integer) rows.get(4).selected();
		profile.pityLevel = (Integer) rows.get(5).selected();
		if (LuckRunCapture.updateProfile(profile)) return true;
		status = LuckRunCapture.store().isReadOnly() ? "Luck data is read-only" : "Could not save";
		statusColor = RED;
		return false;
	}

	private void updateLayout() {
		winW = Math.max(PANEL_MIN_W, Math.min(PANEL_MAX_W, width - 24));
		winH = PANEL_H;
		if (winW > width - 8) winW = Math.max(1, width - 8);
		if (winH > height - 8) winH = Math.max(1, height - 8);
		ox = (width - winW) / 2;
		oy = (height - winH) / 2;
	}

	private int controlRight() {
		return ox + winW - RIGHT_PAD;
	}

	private void drawOptionRow(GuiGraphicsExtractor g, int mouseX, int mouseY, int y, OptionRow<?> row) {
		boolean hovered = contains(ox + 12, y, winW - 24, ROW_H, mouseX, mouseY);
		g.fill(ox + 12, y, ox + winW - 12, y + ROW_H, hovered ? PANEL_ROW_HOVER : PANEL_ROW);
		g.fill(ox + 16, y + ROW_H - 1, ox + winW - 16, y + ROW_H, 0x3335385D);
		int stepperX = controlRight() - STEPPER_W;
		g.text(font, ellipsize(row.label, Math.max(0, stepperX - 8 - (ox + 18))), ox + 18, y + 7, TEXT);
		clickTargets.add(new ClickTarget(ox + 12, y, winW - 24, ROW_H, -2, () -> {}, row.tooltip));
		String value = row.selectedLabel();
		drawStepper(g, stepperX, y + 3, STEPPER_W, 16, value, row.isUnknown() ? MUTED : TEXT,
			() -> row.step(-1), () -> row.step(1), row.tooltip);
	}

	private void drawFooter(GuiGraphicsExtractor g, int mouseX, int mouseY) {
		int y = oy + winH - 30;
		g.fill(ox + 10, y - 5, ox + winW - 10, y - 4, 0x5535385D);
		drawSmallButton(g, ox + 18, y, 48, 18, "Reset", () -> {
			for (OptionRow<?> row : rows) row.reset();
			status = "";
		}, "Set every modifier back to Unknown", MUTED);
		int doneX = controlRight() - 58;
		drawSmallButton(g, doneX, y, 58, 18, "Done", () -> {
			if (save()) goBack();
		}, "Save and go back", TEXT);
		String summary = status.isEmpty() ? summaryText() : status;
		int summaryColor = status.isEmpty() ? (unknownCount() == 0 ? GREEN : DIM) : statusColor;
		int left = ox + 18 + 48 + 8;
		int right = doneX - 8;
		String shown = ellipsize(summary, Math.max(0, right - left));
		g.text(font, shown, left + (right - left - font.width(shown)) / 2, y + (18 - font.lineHeight) / 2, summaryColor);
	}

	private String summaryText() {
		int unknown = unknownCount();
		return unknown == 0 ? "All modifiers set" : unknown + " unknown";
	}

	private int unknownCount() {
		int unknown = 0;
		for (OptionRow<?> row : rows) if (row.isUnknown()) unknown++;
		return unknown;
	}

	private void drawStepper(GuiGraphicsExtractor g, int x, int y, int w, int h, String value, int valueColor, Runnable dec, Runnable inc, String tooltip) {
		int side = 16;
		drawSmallButton(g, x, y, side, h, "-", dec, tooltip, TEXT);
		g.fill(x + side, y, x + w - side, y + h, PANEL_ALT);
		g.fill(x + side, y, x + w - side, y + 1, BORDER);
		g.fill(x + side, y + h - 1, x + w - side, y + h, BORDER);
		String shown = ellipsize(value, w - side * 2 - 4);
		g.text(font, shown, x + side + (w - side * 2 - font.width(shown)) / 2, y + (h - font.lineHeight) / 2, valueColor);
		drawSmallButton(g, x + w - side, y, side, h, "+", inc, tooltip, TEXT);
	}

	private void drawSmallButton(GuiGraphicsExtractor g, int x, int y, int w, int h, String label, Runnable onClick, String tooltip, int textColor) {
		g.fill(x, y, x + w, y + h, 0xFF171B30);
		border(g, x, y, w, h, BORDER);
		g.text(font, label, x + (w - font.width(label)) / 2, y + (h - font.lineHeight) / 2, textColor);
		clickTargets.add(new ClickTarget(x, y, w, h, 0, onClick, tooltip));
	}

	private void drawHoveredTooltip(GuiGraphicsExtractor g, int mouseX, int mouseY) {
		for (int i = clickTargets.size() - 1; i >= 0; i--) {
			ClickTarget target = clickTargets.get(i);
			if (!contains(target.x, target.y, target.w, target.h, mouseX, mouseY)) continue;
			if (target.tooltip == null || target.tooltip.isBlank()) continue;
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
		int ellipsisWidth = font.width(ellipsis);
		StringBuilder builder = new StringBuilder();
		for (int i = 0; i < value.length(); i++) {
			char c = value.charAt(i);
			if (font.width(builder.toString() + c) + ellipsisWidth > maxWidth) break;
			builder.append(c);
		}
		return builder.append(ellipsis).toString();
	}

	private static String bossLuckLabel(ModifierPreset.BossLuck bossLuck) {
		return bossLuck == ModifierPreset.BossLuck.NONE ? "None" : bossLuck.name() + " (+" + bossLuck.qualityBonus() + ")";
	}

	private static String treasureLabel(ModifierPreset.TreasureAccessory accessory) {
		String lower = accessory.name().toLowerCase(java.util.Locale.ROOT);
		return Character.toUpperCase(lower.charAt(0)) + lower.substring(1);
	}

	private static List<Integer> levels(int max) {
		List<Integer> out = new ArrayList<>(max + 1);
		for (int i = 0; i <= max; i++) out.add(i);
		return out;
	}

	private static <T> List<T> withUnknown(List<T> values) {
		List<T> out = new ArrayList<>(values.size() + 1);
		out.add(null);
		out.addAll(values);
		return out;
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

	@FunctionalInterface
	private interface Labeler<T> {
		String label(T value);
	}

	/** One stepper row whose first option is null, meaning unknown. */
	private static final class OptionRow<T> {
		private final String label;
		private final String tooltip;
		private final List<T> options;
		private final Labeler<T> labeler;
		private int index;

		OptionRow(String label, String tooltip, List<T> options, T initial, Labeler<T> labeler) {
			this.label = label;
			this.tooltip = tooltip;
			this.options = options;
			this.labeler = labeler;
			this.index = Math.max(0, options.indexOf(initial));
		}

		T selected() {
			return options.get(index);
		}

		boolean isUnknown() {
			return selected() == null;
		}

		String selectedLabel() {
			T value = selected();
			return value == null ? UNKNOWN : labeler.label(value);
		}

		void step(int delta) {
			index = Math.max(0, Math.min(options.size() - 1, index + delta));
		}

		void reset() {
			index = 0;
		}
	}

	private record ClickTarget(int x, int y, int w, int h, int button, Runnable action, String tooltip) {
	}
}
