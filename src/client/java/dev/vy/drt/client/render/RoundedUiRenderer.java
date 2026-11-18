package dev.vy.drt.client.render;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/** Collects DRT vector UI commands into one framebuffer-resolution NanoVG pass. */
public final class RoundedUiRenderer {
	private static Frame currentFrame;

	private RoundedUiRenderer() {}

	public static void beginFrame(GuiGraphicsExtractor graphics, int guiWidth, int guiHeight, float openingScale) {
		Frame frame = new Frame(guiWidth, guiHeight, openingScale);
		currentFrame = frame;
		DrtNvgSpecialRenderer.draw(graphics, guiWidth, guiHeight, frame::submit);
	}

	public static void endFrame() {
		currentFrame = null;
	}

	public static void fill(GuiGraphicsExtractor ignored, int x, int y, int width, int height, int radius, int color) {
		dev.vy.drt.config.ThemePreview.record(x,y,width,height,color);
		color = dev.vy.drt.config.ThemePreview.decode(color);
		if (currentFrame != null && width > 0 && height > 0) currentFrame.commands.add(new RoundedCommand(x, y, width, height, radius, color, 0));
	}

	public static void fillBordered(GuiGraphicsExtractor ignored, int x, int y, int width, int height, int radius, int borderColor, int fillColor) {
		dev.vy.drt.config.ThemePreview.record(x,y,width,height,fillColor,borderColor);
		fillColor = dev.vy.drt.config.ThemePreview.decode(fillColor);
		borderColor = dev.vy.drt.config.ThemePreview.decode(borderColor);
		if (currentFrame != null && width > 0 && height > 0) currentFrame.commands.add(new RoundedCommand(x, y, width, height, radius, fillColor, borderColor));
	}

	public static void line(float x0, float y0, float x1, float y1, float width, int color) {
		dev.vy.drt.config.ThemePreview.record((int)Math.min(x0,x1)-2,(int)Math.min(y0,y1)-2,(int)Math.abs(x1-x0)+5,(int)Math.abs(y1-y0)+5,color);
		color = dev.vy.drt.config.ThemePreview.decode(color);
		if (currentFrame != null) currentFrame.commands.add(new LineCommand(x0, y0, x1, y1, width, color));
	}

	public static void graph(float[] xs, float[] ys, int count, float baseline, boolean stepped, int lineColor, int fillColor) {
		for(int i=1;i<count;i++) dev.vy.drt.config.ThemePreview.record((int)xs[i-1],(int)Math.min(ys[i-1],ys[i])-3,Math.max(4,(int)(xs[i]-xs[i-1])),(int)Math.abs(ys[i]-ys[i-1])+7,lineColor,fillColor);
		lineColor = dev.vy.drt.config.ThemePreview.decode(lineColor);
		fillColor = dev.vy.drt.config.ThemePreview.decode(fillColor);
		if (currentFrame != null && count > 0) currentFrame.commands.add(new GraphCommand(xs, ys, count, baseline, stepped, lineColor, fillColor));
	}

	private interface Command { void execute(); }

	private record RoundedCommand(float x, float y, float width, float height, float radius, int fill, int outline) implements Command {
		@Override public void execute() { DrtNvgRenderer.roundedRect(x, y, width, height, radius, fill, outline); }
	}

	private record LineCommand(float x0, float y0, float x1, float y1, float width, int color) implements Command {
		@Override public void execute() { DrtNvgRenderer.line(x0, y0, x1, y1, width, color); }
	}

	private record GraphCommand(float[] xs, float[] ys, int count, float baseline, boolean stepped, int lineColor, int fillColor) implements Command {
		@Override public void execute() { DrtNvgRenderer.graph(xs, ys, count, baseline, stepped, lineColor, fillColor); }
	}

	private static final class Frame {
		final int guiWidth;
		final int guiHeight;
		final float openingScale;
		final List<Command> commands = new ArrayList<>(64);

		Frame(int guiWidth, int guiHeight, float openingScale) {
			this.guiWidth = guiWidth;
			this.guiHeight = guiHeight;
			this.openingScale = openingScale;
		}

		void submit() {
			Minecraft minecraft = Minecraft.getInstance();
			float rawScaleX = minecraft.getWindow().getWidth() / (float) Math.max(1, guiWidth);
			float rawScaleY = minecraft.getWindow().getHeight() / (float) Math.max(1, guiHeight);
			DrtNvgRenderer.push();
			DrtNvgRenderer.scale(rawScaleX, rawScaleY);
			DrtNvgRenderer.translate(guiWidth / 2.0F, guiHeight / 2.0F);
			DrtNvgRenderer.scale(openingScale, openingScale);
			DrtNvgRenderer.translate(-guiWidth / 2.0F, -guiHeight / 2.0F);
			for (Command command : commands) command.execute();
			DrtNvgRenderer.pop();
		}
	}
}
