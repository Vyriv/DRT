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
	private boolean dragging;
	private int dragOffsetX;
	private int dragOffsetY;

	public DungeonTrackerPositionScreen(DungeonRunTrackerFeature trackerFeature) {
		super(Component.literal("Move DRT Tracker"));
		this.trackerFeature = trackerFeature;
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

		trackerFeature.extractRenderState(Minecraft.getInstance(), guiGraphics, mouseX, mouseY);

		String line1 = "Drag the DRT tracker to position it.";
		String line2 = "Press Enter or Esc to save and close.";
		int centerX = width / 2;
		guiGraphics.centeredText(font, line1, centerX, 24, 0xFFFFFFFF);
		guiGraphics.centeredText(font, line2, centerX, 38, 0xFFB6C2DF);
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
	public boolean keyPressed(KeyEvent event) {
		if (event.key() == GLFW.GLFW_KEY_ESCAPE || event.key() == GLFW.GLFW_KEY_ENTER || event.key() == GLFW.GLFW_KEY_KP_ENTER) {
			trackerFeature.setHudPosition(Minecraft.getInstance(), trackerFeature.getHudX(), trackerFeature.getHudY(), true);
			onClose();
			return true;
		}
		return super.keyPressed(event);
	}
}
