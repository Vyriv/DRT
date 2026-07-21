package dev.vy.drt.client.screen;

import dev.vy.drt.client.tracker.DungeonRunTrackerFeature;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

public final class DungeonTrackerPositionScreen extends Screen {
	private final DungeonRunTrackerFeature trackerFeature;
	private final Screen parent;
	private boolean dragging;
	private int dragOffsetX;
	private int dragOffsetY;

	public DungeonTrackerPositionScreen(DungeonRunTrackerFeature trackerFeature) {
		this(trackerFeature, null);
	}

	public DungeonTrackerPositionScreen(DungeonRunTrackerFeature trackerFeature, Screen parent) {
		super(Component.literal("Move DRT Tracker"));
		this.trackerFeature = trackerFeature;
		this.parent = parent;
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	@Override
	public void extractBackground(GuiGraphicsExtractor guiGraphics, int mouseX, int mouseY, float partialTick) {
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor guiGraphics, int mouseX, int mouseY, float partialTick) {
		guiGraphics.fill(0, 0, width, height, 0xE0000000);

		if (dragging) {
			Minecraft client = Minecraft.getInstance();
			trackerFeature.setHudPosition(client, mouseX - dragOffsetX, mouseY - dragOffsetY, false);
		}

		drawMoveArea(guiGraphics, Minecraft.getInstance());
		trackerFeature.extractRenderState(Minecraft.getInstance(), guiGraphics, mouseX, mouseY, true);

		String line1 = "Drag the DRT tracker to position it.";
		String line2 = "Scroll or press +/- to resize (" + trackerFeature.getHudScalePercent() + "%). Press 0 for 100%.";
		String line3 = "Press Enter or Esc to save and exit.";
		int centerX = width / 2;
		guiGraphics.centeredText(font, line1, centerX, 24, 0xFFFFFFFF);
		guiGraphics.centeredText(font, line2, centerX, 38, 0xFFB6C2DF);
		guiGraphics.centeredText(font, line3, centerX, 52, 0xFFB6C2DF);
	}

	private void drawMoveArea(GuiGraphicsExtractor guiGraphics, Minecraft client) {
		int left = trackerFeature.getHudX() - 4;
		int top = trackerFeature.getHudY() - 4;
		int right = trackerFeature.getHudX() + trackerFeature.getDisplayWidth(client) + 4;
		int bottom = trackerFeature.getHudY() + trackerFeature.getDisplayHeight(client) + 4;
		guiGraphics.fill(left, top, right, bottom, 0x22FFFFFF);
		guiGraphics.fill(left, top, right, top + 1, 0x66FFFFFF);
		guiGraphics.fill(left, bottom - 1, right, bottom, 0x66FFFFFF);
		guiGraphics.fill(left, top, left + 1, bottom, 0x66FFFFFF);
		guiGraphics.fill(right - 1, top, right, bottom, 0x66FFFFFF);
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean isRepeat) {
		if (event.button() != 0) {
			return super.mouseClicked(event, isRepeat);
		}

		Minecraft client = Minecraft.getInstance();
		int mouseX = (int) event.x();
		int mouseY = (int) event.y();
		int left = trackerFeature.getHudX() - 3;
		int top = trackerFeature.getHudY() - 2;
		int right = trackerFeature.getHudX() + trackerFeature.getDisplayWidth(client) + 3;
		int bottom = trackerFeature.getHudY() + trackerFeature.getDisplayHeight(client) + 2;
		if (mouseX < left || mouseX > right || mouseY < top || mouseY > bottom) {
			return super.mouseClicked(event, isRepeat);
		}

		dragging = true;
		dragOffsetX = mouseX - trackerFeature.getHudX();
		dragOffsetY = mouseY - trackerFeature.getHudY();
		return true;
	}

	@Override
	public boolean mouseReleased(MouseButtonEvent event) {
		if (event.button() == 0 && dragging) {
			dragging = false;
			trackerFeature.setHudPosition(Minecraft.getInstance(), trackerFeature.getHudX(), trackerFeature.getHudY(), true);
			return true;
		}
		return super.mouseReleased(event);
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
		Minecraft client = Minecraft.getInstance();
		if (scrollY > 0.0D) {
			trackerFeature.growHudScale(client, true, false);
			return true;
		}
		if (scrollY < 0.0D) {
			trackerFeature.shrinkHudScale(client, true, false);
			return true;
		}
		return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
	}

	@Override
	public void onClose() {
		trackerFeature.setHudPosition(Minecraft.getInstance(), trackerFeature.getHudX(), trackerFeature.getHudY(), true);
		if (minecraft != null && parent != null) {
			minecraft.setScreen(parent);
			return;
		}
		super.onClose();
	}

	private void closeFully() {
		trackerFeature.setHudPosition(Minecraft.getInstance(), trackerFeature.getHudX(), trackerFeature.getHudY(), true);
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
	public boolean keyPressed(KeyEvent event) {
		if (event.key() == GLFW.GLFW_KEY_EQUAL || event.key() == GLFW.GLFW_KEY_KP_ADD) {
			trackerFeature.growHudScale(Minecraft.getInstance(), true, false);
			return true;
		}
		if (event.key() == GLFW.GLFW_KEY_MINUS || event.key() == GLFW.GLFW_KEY_KP_SUBTRACT) {
			trackerFeature.shrinkHudScale(Minecraft.getInstance(), true, false);
			return true;
		}
		if (event.key() == GLFW.GLFW_KEY_0 || event.key() == GLFW.GLFW_KEY_KP_0) {
			trackerFeature.setHudScale(Minecraft.getInstance(), 1.0F, true, false);
			return true;
		}
		if (event.key() == GLFW.GLFW_KEY_ESCAPE) {
			closeFully();
			return true;
		}
		if (event.key() == GLFW.GLFW_KEY_ENTER || event.key() == GLFW.GLFW_KEY_KP_ENTER) {
			closeFully();
			return true;
		}
		return super.keyPressed(event);
	}
}
