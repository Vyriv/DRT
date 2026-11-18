package dev.vy.drt.client.render;

import org.lwjgl.nanovg.NVGColor;
import org.lwjgl.nanovg.NanoVG;
import org.lwjgl.nanovg.NanoVGGL3;

final class DrtNvgRenderer {
	private static final NVGColor COLOR = NVGColor.malloc();
	private static long context;

	private DrtNvgRenderer() {}

	static void beginFrame(int width, int height) {
		ensureContext();
		NanoVG.nvgBeginFrame(context, width, height, 1.0F);
	}

	static void endFrame() {
		NanoVG.nvgEndFrame(context);
	}

	//? if >=26.3 {
	/*static void closeContext() {
		if (context != 0L) NanoVGGL3.nvgDelete(context);
		context = 0L;
	}
	*///?}

	static void push() { NanoVG.nvgSave(context); }
	static void pop() { NanoVG.nvgRestore(context); }
	static void translate(float x, float y) { NanoVG.nvgTranslate(context, x, y); }
	static void scale(float x, float y) { NanoVG.nvgScale(context, x, y); }

	static void roundedRect(float x, float y, float width, float height, float radius, int fillColor, int outlineColor) {
		NanoVG.nvgBeginPath(context);
		NanoVG.nvgRoundedRect(context, x, y, width, height, Math.max(0.0F, radius));
		color(fillColor);
		NanoVG.nvgFillColor(context, COLOR);
		NanoVG.nvgFill(context);
		if (((outlineColor >>> 24) & 0xFF) == 0 || width <= 1.0F || height <= 1.0F) return;
		NanoVG.nvgBeginPath(context);
		NanoVG.nvgRoundedRect(context, x + 0.5F, y + 0.5F, width - 1.0F, height - 1.0F, Math.max(0.0F, radius - 0.5F));
		NanoVG.nvgStrokeWidth(context, 1.0F);
		color(outlineColor);
		NanoVG.nvgStrokeColor(context, COLOR);
		NanoVG.nvgStroke(context);
	}

	static void line(float x0, float y0, float x1, float y1, float width, int color) {
		NanoVG.nvgBeginPath(context);
		NanoVG.nvgMoveTo(context, x0, y0);
		NanoVG.nvgLineTo(context, x1, y1);
		NanoVG.nvgStrokeWidth(context, width);
		NanoVG.nvgLineCap(context, NanoVG.NVG_BUTT);
		color(color);
		NanoVG.nvgStrokeColor(context, COLOR);
		NanoVG.nvgStroke(context);
	}

	static void graph(float[] xs, float[] ys, int count, float baseline, boolean stepped, int lineColor, int fillColor) {
		if (count <= 0) return;
		NanoVG.nvgBeginPath(context);
		NanoVG.nvgMoveTo(context, xs[0], baseline);
		NanoVG.nvgLineTo(context, xs[0], ys[0]);
		appendGraphPath(xs, ys, count, stepped);
		NanoVG.nvgLineTo(context, xs[count - 1], baseline);
		NanoVG.nvgClosePath(context);
		color(fillColor);
		NanoVG.nvgFillColor(context, COLOR);
		NanoVG.nvgFill(context);

		NanoVG.nvgBeginPath(context);
		NanoVG.nvgMoveTo(context, xs[0], ys[0]);
		appendGraphPath(xs, ys, count, stepped);
		NanoVG.nvgStrokeWidth(context, 1.2F);
		NanoVG.nvgLineCap(context, NanoVG.NVG_ROUND);
		NanoVG.nvgLineJoin(context, NanoVG.NVG_ROUND);
		color(lineColor);
		NanoVG.nvgStrokeColor(context, COLOR);
		NanoVG.nvgStroke(context);
	}

	private static void appendGraphPath(float[] xs, float[] ys, int count, boolean stepped) {
		for (int i = 1; i < count; i++) {
			if (stepped) NanoVG.nvgLineTo(context, xs[i], ys[i - 1]);
			NanoVG.nvgLineTo(context, xs[i], ys[i]);
		}
	}

	private static void ensureContext() {
		if (context != 0L) return;
		context = NanoVGGL3.nvgCreate(NanoVGGL3.NVG_ANTIALIAS | NanoVGGL3.NVG_STENCIL_STROKES);
		if (context == 0L) throw new IllegalStateException("Failed to initialize DRT NanoVG renderer");
	}

	private static void color(int argb) {
		NanoVG.nvgRGBA(
			(byte) ((argb >>> 16) & 0xFF),
			(byte) ((argb >>> 8) & 0xFF),
			(byte) (argb & 0xFF),
			(byte) ((argb >>> 24) & 0xFF),
			COLOR
		);
	}
}
