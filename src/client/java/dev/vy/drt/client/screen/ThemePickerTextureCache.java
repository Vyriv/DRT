package dev.vy.drt.client.screen;

import com.mojang.blaze3d.platform.NativeImage;
import dev.vy.drt.DungeonRunTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;

import java.util.concurrent.atomic.AtomicInteger;

/** Retained picker gradients. Unchanged textures submit one GUI element and perform no upload. */
final class ThemePickerTextureCache implements AutoCloseable {
	static final int CACHED_BACKGROUND_ELEMENTS_PER_FRAME = 10;
	private static final AtomicInteger NEXT_ID = new AtomicInteger();
	private final Slot saturationValue;
	private final Slot hue;
	private final Slot[] sliders = new Slot[4];
	private final Slot checker;

	ThemePickerTextureCache() {
		int instance = NEXT_ID.incrementAndGet();
		saturationValue = new Slot(id(instance, "saturation_value"));
		hue = new Slot(id(instance, "hue"));
		for (int channel = 0; channel < sliders.length; channel++) sliders[channel] = new Slot(id(instance, "slider_" + channel));
		checker = new Slot(id(instance, "checker"));
	}

	void drawSaturationValue(GuiGraphicsExtractor graphics, ThemeEditorLayout.Rect rect, float selectedHue) {
		int hueKey = Float.floatToIntBits(selectedHue);
		saturationValue.ensure(rect.w(), rect.h(), hueKey, (x, y, width, height) -> {
			float saturation = x / (float) Math.max(1, width - 1);
			float brightness = 1F - y / (float) Math.max(1, height - 1);
			return java.awt.Color.HSBtoRGB(selectedHue, saturation, brightness);
		});
		saturationValue.draw(graphics, rect);
	}

	void drawHue(GuiGraphicsExtractor graphics, ThemeEditorLayout.Rect rect) {
		hue.ensure(rect.w(), rect.h(), 1, (x, y, width, height) ->
			java.awt.Color.HSBtoRGB(y / (float) Math.max(1, height - 1), 1F, 1F));
		hue.draw(graphics, rect);
	}

	void drawSlider(GuiGraphicsExtractor graphics, ThemeEditorLayout.Rect rect, int channel,
		int red, int green, int blue, int checkerDark, int checkerLight) {
		int key = sliderKey(channel, red, green, blue, checkerDark, checkerLight);
		sliders[channel].ensure(rect.w(), rect.h(), key, (x, y, width, height) -> {
			int value = Math.round(x * 255F / Math.max(1, width - 1));
			int r = channel == 0 ? value : red;
			int g = channel == 1 ? value : green;
			int b = channel == 2 ? value : blue;
			if (channel != 3) return 0xFF000000 | r << 16 | g << 8 | b;
			int base = checkerColor(x, y, checkerDark, checkerLight);
			return blendOpaque(base, 0xFF000000 | r << 16 | g << 8 | b, value);
		});
		sliders[channel].draw(graphics, rect);
	}

	void drawChecker(GuiGraphicsExtractor graphics, ThemeEditorLayout.Rect rect, int dark, int light) {
		int key = 31 * dark + light;
		checker.ensure(rect.w(), rect.h(), key, (x, y, width, height) -> checkerColor(x, y, dark, light));
		checker.draw(graphics, rect);
	}

	static int sliderKey(int channel, int red, int green, int blue, int checkerDark, int checkerLight) {
		int result = channel;
		if (channel != 0) result = 31 * result + red;
		if (channel != 1) result = 31 * result + green;
		if (channel != 2) result = 31 * result + blue;
		if (channel == 3) {
			result = 31 * result + checkerDark;
			result = 31 * result + checkerLight;
		}
		return result;
	}

	private static int checkerColor(int x, int y, int dark, int light) {
		return ((x / 4 + y / 4) & 1) == 0 ? dark | 0xFF000000 : light | 0xFF000000;
	}

	private static int blendOpaque(int background, int foreground, int alpha) {
		int inverse = 255 - alpha;
		int red = ((foreground >>> 16 & 255) * alpha + (background >>> 16 & 255) * inverse + 127) / 255;
		int green = ((foreground >>> 8 & 255) * alpha + (background >>> 8 & 255) * inverse + 127) / 255;
		int blue = ((foreground & 255) * alpha + (background & 255) * inverse + 127) / 255;
		return 0xFF000000 | red << 16 | green << 8 | blue;
	}

	private static Identifier id(int instance, String suffix) {
		return Identifier.fromNamespaceAndPath(DungeonRunTracker.MOD_ID, "theme_picker/" + instance + "/" + suffix);
	}

	@Override public void close() {
		saturationValue.close();
		hue.close();
		for (Slot slider : sliders) slider.close();
		checker.close();
	}

	@FunctionalInterface
	private interface PixelSource {
		int color(int x, int y, int width, int height);
	}

	private static final class Slot {
		private final Identifier id;
		private DynamicTexture texture;
		private int width;
		private int height;
		private int key;
		private boolean populated;

		private Slot(Identifier id) { this.id = id; }

		void ensure(int requestedWidth, int requestedHeight, int requestedKey, PixelSource source) {
			int safeWidth = Math.max(1, requestedWidth);
			int safeHeight = Math.max(1, requestedHeight);
			boolean resized = texture == null || width != safeWidth || height != safeHeight;
			if (!resized && populated && key == requestedKey) return;
			if (resized) recreate(safeWidth, safeHeight);
			NativeImage pixels = texture.getPixels();
			if (pixels == null) return;
			for (int y = 0; y < safeHeight; y++) {
				for (int x = 0; x < safeWidth; x++) pixels.setPixel(x, y, source.color(x, y, safeWidth, safeHeight));
			}
			texture.upload();
			key = requestedKey;
			populated = true;
		}

		void draw(GuiGraphicsExtractor graphics, ThemeEditorLayout.Rect rect) {
			graphics.blit(RenderPipelines.GUI_TEXTURED, id, rect.x(), rect.y(), 0F, 0F, rect.w(), rect.h(), width, height);
		}

		private void recreate(int newWidth, int newHeight) {
			close();
			width = newWidth;
			height = newHeight;
			NativeImage image = new NativeImage(newWidth, newHeight, false);
			texture = new DynamicTexture(() -> "DRT theme picker gradient", image);
			Minecraft.getInstance().getTextureManager().register(id, texture);
			populated = false;
		}

		void close() {
			if (texture == null) return;
			Minecraft client = Minecraft.getInstance();
			if (client != null) client.getTextureManager().release(id);
			else texture.close();
			texture = null;
			populated = false;
			width = 0;
			height = 0;
		}
	}
}
