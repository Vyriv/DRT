//? if >=26.3 {
/*package dev.vy.drt.client.compat;

import com.mojang.blaze3d.platform.InputConstants;
import org.lwjgl.sdl.SDLScancode;

// Version adapter for existing DRT input bindings after Minecraft moved to SDL.
public final class GLFW {
	private GLFW() {}
	public static final int GLFW_KEY_0 = InputConstants.KEY_0;
	public static final int GLFW_KEY_BACKSPACE = InputConstants.KEY_BACKSPACE;
	public static final int GLFW_KEY_DELETE = InputConstants.KEY_DELETE;
	public static final int GLFW_KEY_DOWN = InputConstants.KEY_DOWN;
	public static final int GLFW_KEY_END = InputConstants.KEY_END;
	public static final int GLFW_KEY_ENTER = InputConstants.KEY_RETURN;
	public static final int GLFW_KEY_EQUAL = InputConstants.KEY_EQUALS;
	public static final int GLFW_KEY_ESCAPE = InputConstants.KEY_ESCAPE;
	public static final int GLFW_KEY_HOME = InputConstants.KEY_HOME;
	public static final int GLFW_KEY_KP_0 = InputConstants.KEY_NUMPAD0;
	public static final int GLFW_KEY_KP_ADD = InputConstants.KEY_ADD;
	public static final int GLFW_KEY_KP_ENTER = InputConstants.KEY_NUMPADENTER;
	public static final int GLFW_KEY_KP_SUBTRACT = SDLScancode.SDL_SCANCODE_KP_MINUS;
	public static final int GLFW_KEY_LEFT = InputConstants.KEY_LEFT;
	public static final int GLFW_KEY_MINUS = InputConstants.KEY_MINUS;
	public static final int GLFW_KEY_RIGHT = InputConstants.KEY_RIGHT;
	public static final int GLFW_KEY_SPACE = InputConstants.KEY_SPACE;
	public static final int GLFW_KEY_UP = InputConstants.KEY_UP;
	public static final int GLFW_MOD_SHIFT = InputConstants.MOD_SHIFT;
	// DRT hit targets use the pre-SDL button convention, normalized at event entry.
	public static final int GLFW_MOUSE_BUTTON_LEFT = 0;
	public static final int GLFW_MOUSE_BUTTON_RIGHT = 1;
}
*///?}
