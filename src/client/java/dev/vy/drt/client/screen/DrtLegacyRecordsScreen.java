package dev.vy.drt.client.screen;

import dev.vy.drt.client.render.DrtUiText;
import dev.vy.drt.client.sync.DrtStatsSync;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

/**
 * The one-time question for records saved before DRT stored which account played them. Closing
 * without an answer keeps them local and asks again on a later launch.
 */
public final class DrtLegacyRecordsScreen extends Screen {
	private static final int PANEL_BG = 0xF00D0D18;
	private static final int BORDER = 0xFF35385D;
	private static final int TEXT = 0xFFE6E8F2;
	private static final int MUTED = 0xFF9AA4BD;
	private static final int DIM = 0xFF747E99;
	private static final int GREEN = 0xFF63D184;
	private static final int PANEL_W = 300;
	private static final int BUTTON_H = 18;

	private final String uuid;
	private final String name;
	private final int records;
	private int confirmX;
	private int keepX;
	private int buttonY;
	private int confirmW;
	private int keepW;

	public DrtLegacyRecordsScreen(String uuid, String name, int records) {
		super(Component.literal("DRT Statistics Sharing"));
		this.uuid = uuid;
		this.name = name;
		this.records = records;
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
		dev.vy.drt.config.DrtTheme.ensureLoaded();
		int panelW = Math.min(PANEL_W, width - 16);
		List<String> body = wrap(records + " older DRT runs and chests were saved before DRT recorded which Minecraft account played them. "
			+ "Statistics Sharing can upload them as " + name + ". Only choose this if they were all played on this account. "
			+ "Manually created or edited records are never uploaded.", panelW - 24);
		int panelH = 34 + body.size() * (font.lineHeight + 2) + 14 + BUTTON_H + 12;
		int ox = (width - panelW) / 2;
		int oy = (height - panelH) / 2;

		g.fill(0, 0, width, height, dev.vy.drt.config.DrtTheme.color("scrim", 0xA0000000));
		g.fill(ox, oy, ox + panelW, oy + panelH, dev.vy.drt.config.DrtTheme.color("window.background", PANEL_BG));
		border(g, ox, oy, panelW, panelH, dev.vy.drt.config.DrtTheme.color("window.border", BORDER));
		g.text(font, "DRT Statistics Sharing", ox + 12, oy + 10, dev.vy.drt.config.DrtTheme.color("text.pageTitle", TEXT));
		int y = oy + 30;
		for (String line : body) {
			g.text(font, line, ox + 12, y, dev.vy.drt.config.DrtTheme.color("text.description", MUTED));
			y += font.lineHeight + 2;
		}

		buttonY = oy + panelH - 12 - BUTTON_H;
		confirmW = Math.min(panelW / 2 - 18, font.width("Upload as " + name) + 16);
		keepW = font.width("Keep them local") + 16;
		keepX = ox + 12;
		confirmX = ox + panelW - 12 - confirmW;
		drawButton(g, mouseX, mouseY, keepX, keepW, "Keep them local", dev.vy.drt.config.DrtTheme.color("text.muted", DIM));
		drawButton(g, mouseX, mouseY, confirmX, confirmW, ellipsize("Upload as " + name, confirmW - 8), GREEN);
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean isRepeat) {
		int mx = (int) event.x();
		int my = (int) event.y();
		if (event.button() == 0 && contains(confirmX, buttonY, confirmW, BUTTON_H, mx, my)) {
			DrtStatsSync.claimLegacyRecords(uuid);
			super.onClose();
			return true;
		}
		if (event.button() == 0 && contains(keepX, buttonY, keepW, BUTTON_H, mx, my)) {
			DrtStatsSync.declineLegacyRecords();
			super.onClose();
			return true;
		}
		return super.mouseClicked(event, isRepeat);
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		if (event.key() == GLFW.GLFW_KEY_ESCAPE) {
			super.onClose();
			return true;
		}
		return super.keyPressed(event);
	}

	private void drawButton(GuiGraphicsExtractor g, int mouseX, int mouseY, int x, int w, String label, int color) {
		boolean hovered = contains(x, buttonY, w, BUTTON_H, mouseX, mouseY);
		g.fill(x, buttonY, x + w, buttonY + BUTTON_H, hovered ? dev.vy.drt.config.DrtTheme.color("control.hover", 0xFF222641) : dev.vy.drt.config.DrtTheme.color("control.background", 0xFF171B30));
		border(g, x, buttonY, w, BUTTON_H, hovered ? dev.vy.drt.config.DrtTheme.color("control.borderHover", 0xFF51557E) : dev.vy.drt.config.DrtTheme.color("control.border", BORDER));
		g.text(font, label, x + (w - font.width(label)) / 2, DrtUiText.centeredTextY(font, buttonY, BUTTON_H), color);
	}

	private List<String> wrap(String text, int maxWidth) {
		List<String> lines = new ArrayList<>();
		StringBuilder line = new StringBuilder();
		for (String word : text.split(" ")) {
			String candidate = line.isEmpty() ? word : line + " " + word;
			if (!line.isEmpty() && font.width(candidate) > maxWidth) {
				lines.add(line.toString());
				line = new StringBuilder(word);
			} else {
				line = new StringBuilder(candidate);
			}
		}
		if (!line.isEmpty()) lines.add(line.toString());
		return lines;
	}

	private String ellipsize(String value, int maxWidth) {
		if (font.width(value) <= maxWidth) return value;
		StringBuilder builder = new StringBuilder();
		for (int i = 0; i < value.length(); i++) {
			if (font.width(builder.toString() + value.charAt(i) + "...") > maxWidth) break;
			builder.append(value.charAt(i));
		}
		return builder.append("...").toString();
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
}
