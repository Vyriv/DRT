package dev.vy.drt.client.screen;

import dev.vy.drt.client.overlay.OverlayLayout;
import dev.vy.drt.client.overlay.OverlayLayouts;
import dev.vy.drt.client.overlay.OverlayLine;
import dev.vy.drt.client.overlay.OverlayPreset;
import dev.vy.drt.client.overlay.OverlaySegment;
import dev.vy.drt.client.tracker.DungeonRunTrackerFeature;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

public final class DrtOverlayEditorScreen extends Screen {
	private static final int PANEL_BG = 0xF00D0D18;
	private static final int PANEL_ALT = 0xFF111222;
	private static final int PANEL_ROW = 0xFF151729;
	private static final int PANEL_ROW_HOVER = 0xFF1D2139;
	private static final int PANEL_ROW_ACTIVE = 0xFF23375B;
	private static final int BORDER = 0xFF35385D;
	private static final int BORDER_ACTIVE = 0xFF6268A8;
	private static final int TEXT = 0xFFE6E8F2;
	private static final int MUTED = 0xFF9AA4BD;
	private static final int DIM = 0xFF747E99;
	private static final int GREEN = 0xFF63D184;

	private static final int PAD = 12;
	private static final int LIST_W = 108;
	private static final int LIST_ROW_H = 22;
	private static final int LIST_ROW_GAP = 2;
	private static final int LIST_INNER_PAD = 4;
	private static final int BODY_GAP = 8;
	private static final int BTN_H = 18;
	private static final int FOOTER_H = 28;
	private static final float MAXIMUM_PREVIEW_SCALE = 1.15F;

	private static final OverlayPreset[] PRESETS = {
		OverlayPreset.LEGACY,
		OverlayPreset.MODERN,
		OverlayPreset.SESSION,
		OverlayPreset.DETAILED,
		OverlayPreset.CLASSIC
	};

	private final DungeonRunTrackerFeature trackerFeature;
	private final Screen parent;
	private final List<ClickTarget> clickTargets = new ArrayList<>();

	/** Currently applied live HUD preset. */
	private OverlayPreset activePreset;
	/** Preset shown in the left-hand preview (may differ until Apply). */
	private OverlayPreset previewedPreset;

	private int ox;
	private int oy;
	private int winW;
	private int winH;

	public DrtOverlayEditorScreen(DungeonRunTrackerFeature trackerFeature, Screen parent) {
		super(Component.literal("Edit Overlay"));
		this.trackerFeature = trackerFeature;
		this.parent = parent;
		OverlayPreset current = trackerFeature.getOverlayPreset();
		if (current != null && current.isSelectablePreset()) {
			this.activePreset = current;
			this.previewedPreset = current;
		} else {
			this.activePreset = OverlayPreset.MODERN;
			this.previewedPreset = OverlayPreset.MODERN;
		}
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
		syncActiveFromTracker();
		updateLayout();
		clickTargets.clear();

		g.fill(0, 0, width, height, 0xA0000000);
		g.fill(ox, oy, ox + winW, oy + winH, PANEL_BG);
		border(g, ox, oy, winW, winH, BORDER);

		g.text(font, "Edit Overlay", ox + PAD, oy + 10, TEXT);

		int bodyTop = oy + 28;
		int bodyH = bodyHeight();
		int previewX = ox + PAD;
		int previewW = winW - PAD * 2 - LIST_W - BODY_GAP;
		int listX = previewX + previewW + BODY_GAP;

		drawPreviewPanel(g, mouseX, mouseY, previewX, bodyTop, previewW, bodyH);
		drawPresetList(g, mouseX, mouseY, listX, bodyTop, bodyH);
		drawFooter(g, mouseX, mouseY);

		drawHoveredTooltip(g, mouseX, mouseY);
	}

	private void syncActiveFromTracker() {
		OverlayPreset live = trackerFeature.getOverlayPreset();
		if (live != null && live.isSelectablePreset()) {
			activePreset = live;
		}
	}

	private static int bodyHeight() {
		return LIST_INNER_PAD * 2
			+ PRESETS.length * LIST_ROW_H
			+ Math.max(0, PRESETS.length - 1) * LIST_ROW_GAP;
	}

	private void drawPresetList(GuiGraphicsExtractor g, int mouseX, int mouseY, int x, int y, int h) {
		g.fill(x, y, x + LIST_W, y + h, PANEL_ALT);
		border(g, x, y, LIST_W, h, BORDER);

		int rowY = y + LIST_INNER_PAD;
		int rowW = LIST_W - LIST_INNER_PAD * 2;
		for (OverlayPreset preset : PRESETS) {
			boolean isActive = trackerFeature.getOverlayPreset() == preset;
			boolean isPreviewed = preset == previewedPreset;
			boolean hovered = contains(x + LIST_INNER_PAD, rowY, rowW, LIST_ROW_H, mouseX, mouseY);

			int bg;
			int borderColor;
			if (isPreviewed) {
				bg = PANEL_ROW_ACTIVE;
				borderColor = BORDER_ACTIVE;
			} else if (hovered) {
				bg = PANEL_ROW_HOVER;
				borderColor = 0xFF51557E;
			} else if (isActive) {
				bg = 0xFF1A2038;
				borderColor = BORDER;
			} else {
				bg = PANEL_ROW;
				borderColor = BORDER;
			}
			g.fill(x + LIST_INNER_PAD, rowY, x + LIST_INNER_PAD + rowW, rowY + LIST_ROW_H, bg);
			border(g, x + LIST_INNER_PAD, rowY, rowW, LIST_ROW_H, borderColor);

			String mark = isActive ? "✓ " : "  ";
			int nameColor = isPreviewed || isActive ? TEXT : MUTED;
			g.text(font, mark + preset.displayName(), x + LIST_INNER_PAD + 6, rowY + (LIST_ROW_H - font.lineHeight) / 2, nameColor);

			clickTargets.add(new ClickTarget(x + LIST_INNER_PAD, rowY, rowW, LIST_ROW_H, 0, () -> previewedPreset = preset,
				"Preview " + preset.displayName()));
			rowY += LIST_ROW_H + LIST_ROW_GAP;
		}
	}

	private void drawPreviewPanel(GuiGraphicsExtractor g, int mouseX, int mouseY, int x, int y, int w, int h) {
		g.fill(x, y, x + w, y + h, PANEL_ALT);
		border(g, x, y, w, h, BORDER);

		int inner = 6;
		g.text(font, previewedPreset.displayName(), x + inner, y + 4, TEXT);

		String description = descriptionFor(previewedPreset);
		int descY = y + h - inner - font.lineHeight;
		drawWrappedMuted(g, description, x + inner, descY, w - inner * 2);

		int previewTop = y + 4 + font.lineHeight + 4;
		int previewBottom = descY - 4;
		int areaX = x + inner;
		int areaY = previewTop;
		int areaW = w - inner * 2;
		int areaH = Math.max(12, previewBottom - previewTop);

		Minecraft client = Minecraft.getInstance();
		OverlayLayout layout = trackerFeature.buildPreviewLayout(client, previewedPreset, null);
		renderCenteredPreview(g, layout, areaX, areaY, areaW, areaH);
	}

	private void renderCenteredPreview(GuiGraphicsExtractor g, OverlayLayout layout, int areaX, int areaY, int areaW, int areaH) {
		int boundsW = Math.max(1, layout.width + OverlayLayouts.SHADOW_PAD);
		int boundsH = Math.max(1, layout.height + OverlayLayouts.SHADOW_PAD);

		float scale = Math.min(
			MAXIMUM_PREVIEW_SCALE,
			Math.min((float) areaW / boundsW, (float) areaH / boundsH)
		);
		if (!Float.isFinite(scale) || scale <= 0F) scale = 0.5F;

		int drawnW = Math.round(boundsW * scale);
		int drawnH = Math.round(boundsH * scale);
		int drawX = areaX + Math.max(0, (areaW - drawnW) / 2);
		int drawY = areaY + Math.max(0, (areaH - drawnH) / 2);

		var pose = g.pose();
		pose.pushMatrix();
		try {
			pose.translate(drawX, drawY);
			pose.scale(scale);
			for (OverlayLine line : layout.lines) {
				int x = 0;
				for (OverlaySegment segment : line.segments) {
					int segX = segment.positioned() ? segment.x : x;
					g.text(font, segment.text, segX, line.y, segment.color, true);
					x = segX + font.width(segment.text);
				}
			}
		} finally {
			pose.popMatrix();
		}
	}

	private void drawFooter(GuiGraphicsExtractor g, int mouseX, int mouseY) {
		int y = oy + winH - FOOTER_H;
		g.fill(ox + PAD, y - 6, ox + winW - PAD, y - 5, 0x5535385D);

		boolean previewIsActive = previewedPreset == trackerFeature.getOverlayPreset();
		String applyLabel = previewIsActive ? "Selected" : "Use " + previewedPreset.displayName();
		int applyW = Math.max(72, font.width(applyLabel) + 14);
		int backW = 48;
		int moveW = 78;
		int customW = 86;
		int gap = 6;
		int x = ox + PAD;

		drawButton(g, x, y, backW, BTN_H, "Back", false, this::goBack, "Return to settings", MUTED);
		x += backW + gap;
		drawButton(g, x, y, moveW, BTN_H, "Move Overlay", false, () -> {
			Minecraft client = Minecraft.getInstance();
			client.setScreen(new DungeonTrackerPositionScreen(trackerFeature, this));
		}, "Move the active overlay on screen");
		x += moveW + gap;
		drawButton(g, x, y, customW, BTN_H, "Custom Layout", trackerFeature.getOverlayPreset() == OverlayPreset.CUSTOM, () -> {
			Minecraft client = Minecraft.getInstance();
			client.setScreen(new DrtCustomOverlayEditorScreen(trackerFeature, this));
		}, "Open the custom overlay text editor");

		int applyX = ox + winW - PAD - applyW;
		if (previewIsActive) {
			drawButton(g, applyX, y, applyW, BTN_H, applyLabel, true, null, "This preset is already active", GREEN);
		} else {
			drawButton(g, applyX, y, applyW, BTN_H, applyLabel, false, this::applyPreviewedPreset,
				"Apply " + previewedPreset.displayName() + " to the live HUD", GREEN);
		}
	}

	private void applyPreviewedPreset() {
		trackerFeature.setOverlayPreset(previewedPreset);
		activePreset = previewedPreset;
	}

	private void drawWrappedMuted(GuiGraphicsExtractor g, String text, int x, int y, int maxW) {
		if (text == null || text.isBlank()) return;
		if (font.width(text) <= maxW) {
			g.text(font, text, x, y, DIM);
			return;
		}
		String ellipsis = "...";
		int budget = Math.max(0, maxW - font.width(ellipsis));
		StringBuilder sb = new StringBuilder();
		for (int i = 0; i < text.length(); i++) {
			char c = text.charAt(i);
			if (font.width(sb.toString() + c) > budget) break;
			sb.append(c);
		}
		g.text(font, sb + ellipsis, x, y, DIM);
	}

	private static String descriptionFor(OverlayPreset preset) {
		return switch (preset) {
			case LEGACY -> "Original DRT layout.";
			case MODERN -> "Clean layout focused on the current session.";
			case SESSION -> "Only displays current-session statistics.";
			case DETAILED -> "Shows lifetime and session statistics side by side.";
			case CLASSIC -> "Compact old-school text layout.";
			case CUSTOM -> "Custom text layout.";
		};
	}

	private void drawButton(GuiGraphicsExtractor g, int x, int y, int w, int h, String label, boolean active, Runnable onClick, String tooltip) {
		drawButton(g, x, y, w, h, label, active, onClick, tooltip, TEXT);
	}

	private void drawButton(GuiGraphicsExtractor g, int x, int y, int w, int h, String label, boolean active, Runnable onClick, String tooltip, int textColor) {
		g.fill(x, y, x + w, y + h, active ? 0xFF23375B : onClick == null ? 0xFF10121E : 0xFF171B30);
		border(g, x, y, w, h, active ? BORDER_ACTIVE : onClick == null ? 0xFF252842 : BORDER);
		g.text(font, label, x + (w - font.width(label)) / 2, y + (h - font.lineHeight) / 2, textColor);
		if (onClick != null) {
			clickTargets.add(new ClickTarget(x, y, w, h, 0, onClick, tooltip));
		} else if (tooltip != null && !tooltip.isBlank()) {
			clickTargets.add(new ClickTarget(x, y, w, h, 0, () -> {}, tooltip));
		}
	}

	private void updateLayout() {
		int bodyH = bodyHeight();
		winW = Math.max(340, Math.min(390, width - 24));
		winH = 28 + bodyH + 10 + FOOTER_H + 8;
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
	void closeFully() {
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
		for (int i = clickTargets.size() - 1; i >= 0; i--) {
			ClickTarget target = clickTargets.get(i);
			if ((target.button == -1 || target.button == event.button()) && contains(target.x, target.y, target.w, target.h, mx, my)) {
				target.action.run();
				return true;
			}
		}
		return super.mouseClicked(event, isRepeat);
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		if (event.key() == GLFW.GLFW_KEY_ESCAPE) {
			closeFully();
			return true;
		}
		return super.keyPressed(event);
	}

	@Override
	public void onClose() {
		goBack();
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
}
