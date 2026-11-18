//? if >=26.3 {
/*package dev.vy.drt.client.render;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.textures.GpuTexture;
import java.nio.ByteBuffer;
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GLCapabilities;
import org.lwjgl.system.MemoryUtil;
import static org.lwjgl.opengl.GL33C.*;
import static org.lwjgl.sdl.SDLVideo.*;
import static org.lwjgl.sdl.SDLError.SDL_GetError;

// NanoVG is OpenGL-only. Preserve its paths and antialiasing on Vulkan by
// rendering in an isolated hidden GL context, then uploading the RGBA result.
// Context, framebuffer and readback storage are reused until resize/shutdown.
final class DrtNvgOffscreenBridge implements AutoCloseable {
	private long window;
	private long context;
	private GLCapabilities capabilities;
	private int framebuffer;
	private int color;
	private int stencil;
	private int width;
	private int height;
	private ByteBuffer pixels;

	void drawFrame(GpuTexture destination, Runnable content) {
		long previousWindow = SDL_GL_GetCurrentWindow();
		long previousContext = SDL_GL_GetCurrentContext();
		GLCapabilities previousCapabilities = currentCapabilities();
		try {
			ensureContext();
			check(SDL_GL_MakeCurrent(window, context));
			GL.setCapabilities(capabilities);
			resize(destination.getWidth(0), destination.getHeight(0));
			glBindFramebuffer(GL_FRAMEBUFFER, framebuffer);
			glViewport(0, 0, width, height);
			glDisable(GL_SCISSOR_TEST);
			glColorMask(true, true, true, true);
			glStencilMask(0xFF);
			glClearColor(0, 0, 0, 0);
			glClearStencil(0);
			glClear(GL_COLOR_BUFFER_BIT | GL_STENCIL_BUFFER_BIT);
			DrtNvgRenderer.beginFrame(width, height);
			try { content.run(); } finally { DrtNvgRenderer.endFrame(); }
			pixels.clear();
			glPixelStorei(GL_PACK_ALIGNMENT, 1);
			glReadPixels(0, 0, width, height, GL_RGBA, GL_UNSIGNED_BYTE, pixels);
		} finally {
			SDL_GL_MakeCurrent(previousWindow, previousContext);
			GL.setCapabilities(previousCapabilities);
		}
		RenderSystem.getDevice().createCommandEncoder()
			.writeToTexture(destination, pixels, 0, 0, 0, 0, width, height);
	}

	private void ensureContext() {
		if (context != 0L) return;
		SDL_GL_ResetAttributes();
		check(SDL_GL_SetAttribute(SDL_GL_CONTEXT_MAJOR_VERSION, 3));
		check(SDL_GL_SetAttribute(SDL_GL_CONTEXT_MINOR_VERSION, 3));
		check(SDL_GL_SetAttribute(SDL_GL_CONTEXT_PROFILE_MASK, SDL_GL_CONTEXT_PROFILE_CORE));
		check(SDL_GL_SetAttribute(SDL_GL_CONTEXT_FLAGS, SDL_GL_CONTEXT_FORWARD_COMPATIBLE_FLAG));
		window = SDL_CreateWindow("DRT offscreen renderer", 1, 1, SDL_WINDOW_OPENGL | SDL_WINDOW_HIDDEN);
		if (window == 0L) throw new IllegalStateException("DRT offscreen window: " + SDL_GetError());
		try {
			context = SDL_GL_CreateContext(window);
			if (context == 0L) throw new IllegalStateException("DRT offscreen context: " + SDL_GetError());
			capabilities = GL.createCapabilities();
		} catch (RuntimeException error) {
			if (context != 0L) SDL_GL_DestroyContext(context);
			SDL_DestroyWindow(window);
			context = window = 0L;
			throw error;
		}
	}

	private void resize(int newWidth, int newHeight) {
		if (pixels != null && width == newWidth && height == newHeight) return;
		freeFramebuffer();
		width = newWidth;
		height = newHeight;
		pixels = MemoryUtil.memAlloc(Math.multiplyExact(Math.multiplyExact(width, height), 4));
		framebuffer = glGenFramebuffers();
		glBindFramebuffer(GL_FRAMEBUFFER, framebuffer);
		color = glGenTextures();
		glBindTexture(GL_TEXTURE_2D, color);
		glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA8, width, height, 0, GL_RGBA, GL_UNSIGNED_BYTE, 0L);
		glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
		glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
		glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D, color, 0);
		stencil = glGenRenderbuffers();
		glBindRenderbuffer(GL_RENDERBUFFER, stencil);
		glRenderbufferStorage(GL_RENDERBUFFER, GL_STENCIL_INDEX8, width, height);
		glFramebufferRenderbuffer(GL_FRAMEBUFFER, GL_STENCIL_ATTACHMENT, GL_RENDERBUFFER, stencil);
		if (glCheckFramebufferStatus(GL_FRAMEBUFFER) != GL_FRAMEBUFFER_COMPLETE)
			throw new IllegalStateException("DRT offscreen framebuffer is incomplete");
	}

	private void freeFramebuffer() {
		if (framebuffer != 0) glDeleteFramebuffers(framebuffer);
		if (color != 0) glDeleteTextures(color);
		if (stencil != 0) glDeleteRenderbuffers(stencil);
		MemoryUtil.memFree(pixels);
		pixels = null;
		framebuffer = color = stencil = 0;
	}

	@Override public void close() {
		if (context == 0L) return;
		long previousWindow = SDL_GL_GetCurrentWindow();
		long previousContext = SDL_GL_GetCurrentContext();
		GLCapabilities previousCapabilities = currentCapabilities();
		try {
			check(SDL_GL_MakeCurrent(window, context));
			GL.setCapabilities(capabilities);
			DrtNvgRenderer.closeContext();
			freeFramebuffer();
		} finally {
			SDL_GL_MakeCurrent(previousWindow, previousContext);
			GL.setCapabilities(previousCapabilities);
			SDL_GL_DestroyContext(context);
			SDL_DestroyWindow(window);
			context = window = 0L;
		}
	}

	private static GLCapabilities currentCapabilities() {
		try { return GL.getCapabilities(); } catch (IllegalStateException ignored) { return null; }
	}
	private static void check(boolean success) {
		if (!success) throw new IllegalStateException("DRT SDL renderer: " + SDL_GetError());
	}
}
*///?}
