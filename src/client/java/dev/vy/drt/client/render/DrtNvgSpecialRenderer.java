package dev.vy.drt.client.render;

import com.mojang.blaze3d.opengl.DirectStateAccess;
import com.mojang.blaze3d.opengl.GlConst;
import com.mojang.blaze3d.opengl.GlStateManager;
import com.mojang.blaze3d.opengl.GlTexture;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.render.pip.PictureInPictureRenderer;
import net.minecraft.client.renderer.state.gui.pip.PictureInPictureRenderState;
//? if <26.2 {
/*import net.minecraft.client.renderer.MultiBufferSource;
*///?}
import org.joml.Matrix3x2f;
import org.lwjgl.opengl.GL33C;

public final class DrtNvgSpecialRenderer extends PictureInPictureRenderer<DrtNvgSpecialRenderer.NvgRenderState> {
	//? if >=26.2 {
	public DrtNvgSpecialRenderer() { super(); }
	//?} else {
	/*public DrtNvgSpecialRenderer(MultiBufferSource.BufferSource buffers) { super(buffers); }
	*///?}

	//? if >=26.2 {
	@Override
	protected void renderToTexture(NvgRenderState state, PoseStack poseStack, net.minecraft.client.renderer.SubmitNodeCollector collector) {
		renderNvgFrame(state);
	}
	//?} else {
	/*@Override
	protected void renderToTexture(NvgRenderState state, PoseStack poseStack) { renderNvgFrame(state); }
	*///?}

	private void renderNvgFrame(NvgRenderState state) {
		//? if >=26.3 {
		/*var colorTexture = pipTexture("textureView");
		var depthTexture = pipTexture("depthTextureView");
		if (!(colorTexture.texture() instanceof GlTexture)) {
			if (offscreen == null) offscreen = new DrtNvgOffscreenBridge();
			offscreen.drawFrame(colorTexture.texture(), state.content());
			return;
		}
		DirectStateAccess direct = directStateAccess();
		if (direct == null || colorTexture == null || !(colorTexture.texture() instanceof GlTexture color)
			|| depthTexture == null || !(depthTexture.texture() instanceof GlTexture depth)) {
			throw new IllegalStateException("Could not access DRT's OpenGL extractRenderState target");
		}
		*///?} else {
		var colorTexture = RenderSystem.outputColorTextureOverride;
		DirectStateAccess direct = directStateAccess();
		if (direct == null || colorTexture == null || !(colorTexture.texture() instanceof GlTexture color)) return;
		if (RenderSystem.outputDepthTextureOverride == null || !(RenderSystem.outputDepthTextureOverride.texture() instanceof GlTexture depth)) return;
		//?}
		int width = colorTexture.getWidth(0);
		int height = colorTexture.getHeight(0);
		//? if >=26.2 {
		int framebuffer = framebuffer(direct, color, depth);
		if (framebuffer == 0) return;
		//?} else {
		/*int framebuffer = color.getFbo(direct, depth);
		*///?}
		GlStateManager._glBindFramebuffer(GlConst.GL_FRAMEBUFFER, framebuffer);
		GlStateManager._viewport(0, 0, width, height);
		int sampler = GL33C.glGetInteger(GL33C.GL_SAMPLER_BINDING);
		GL33C.glBindSampler(0, 0);
		DrtNvgRenderer.beginFrame(width, height);
		try { state.content().run(); } finally { DrtNvgRenderer.endFrame(); }
		GlStateManager._disableCull();
		GL33C.glBindSampler(0, sampler);
	}

	//? if >=26.3 {
	/*private DrtNvgOffscreenBridge offscreen;
	@Override public void close() {
		try { if (offscreen != null) offscreen.close(); }
		finally { offscreen = null; super.close(); }
	}
	private static final Field PIP_COLOR = pipField("textureView");
	private static final Field PIP_DEPTH = pipField("depthTextureView");
	private static Field pipField(String name) {
		try {
			Field field = PictureInPictureRenderer.class.getDeclaredField(name);
			field.setAccessible(true);
			return field;
		} catch (ReflectiveOperationException error) { throw new ExceptionInInitializerError(error); }
	}
	private com.mojang.renderpearl.api.textures.GpuTextureView pipTexture(String name) {
		try {
			return (com.mojang.renderpearl.api.textures.GpuTextureView)
				(name.equals("textureView") ? PIP_COLOR : PIP_DEPTH).get(this);
		} catch (IllegalAccessException error) { throw new IllegalStateException(error); }
	}
	*///?}

	@Override protected float getTranslateY(int height, int scaleFactor) { return height / 2.0F; }
	@Override public Class<NvgRenderState> getRenderStateClass() { return NvgRenderState.class; }
	@Override protected String getTextureLabel() { return "drt_nvg_renderer"; }

	public static void draw(GuiGraphicsExtractor graphics, int width, int height, Runnable content) {
		ScreenRectangle scissor = graphics.scissorStack.peek();
		Matrix3x2f pose = new Matrix3x2f(graphics.pose());
		ScreenRectangle bounds = new ScreenRectangle(0, 0, width, height).transformMaxBounds(pose);
		if (scissor != null) bounds = scissor.intersection(bounds);
		graphics.guiRenderState.addPicturesInPictureState(new NvgRenderState(0, 0, width, height, pose, scissor, bounds, content));
	}

	private static DirectStateAccess directStateAccess() {
		Object backend = backend();
		if (backend == null) return null;
		try {
			Method method = backend.getClass().getDeclaredMethod("directStateAccess");
			method.setAccessible(true);
			return (DirectStateAccess) method.invoke(backend);
		} catch (ReflectiveOperationException | ClassCastException ignored) { return null; }
	}

	private static int framebuffer(DirectStateAccess direct, GlTexture color, GlTexture depth) {
		Object backend = backend();
		if (backend == null || !backend.getClass().getName().equals("com.mojang.blaze3d.opengl.GlDevice")) return 0;
		try {
			Method cacheMethod = backend.getClass().getDeclaredMethod("frameBufferCache");
			cacheMethod.setAccessible(true);
			Object cache = cacheMethod.invoke(backend);
			if (cache == null) return 0;
			for (Method method : cache.getClass().getDeclaredMethods()) {
				if (method.getName().equals("getFbo") && method.getParameterCount() == 3) {
					method.setAccessible(true);
					return (int) method.invoke(cache, direct, java.util.List.of(color), depth);
				}
			}
		} catch (ReflectiveOperationException | ClassCastException ignored) {}
		return 0;
	}

	private static Object backend() {
		Object device = RenderSystem.getDevice();
		if (device == null) return null;
		try {
			Field field = device.getClass().getDeclaredField("backend");
			field.setAccessible(true);
			return field.get(device);
		} catch (ReflectiveOperationException ignored) { return null; }
	}

	public record NvgRenderState(int x0, int y0, int x1, int y1, Matrix3x2f pose, ScreenRectangle scissorArea, ScreenRectangle bounds, Runnable content)
		implements PictureInPictureRenderState {
		@Override public float scale() { return 1.0F; }
	}
}
