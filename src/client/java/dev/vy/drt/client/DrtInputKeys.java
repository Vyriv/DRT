package dev.vy.drt.client;

/**
 * Keyboard and mouse codes shared by DRT screens. GLFW constants on 26.2; SDL-backed
 * {@code InputConstants} on 26.3+.
 */
public final class DrtInputKeys {
	private DrtInputKeys() {
	}

	//? if >= 26.3 {
	/*public static final int KEY_ESCAPE = com.mojang.blaze3d.platform.InputConstants.KEY_ESCAPE;
	public static final int KEY_BACKSPACE = com.mojang.blaze3d.platform.InputConstants.KEY_BACKSPACE;
	public static final int KEY_DELETE = com.mojang.blaze3d.platform.InputConstants.KEY_DELETE;
	public static final int KEY_LEFT = com.mojang.blaze3d.platform.InputConstants.KEY_LEFT;
	public static final int KEY_RIGHT = com.mojang.blaze3d.platform.InputConstants.KEY_RIGHT;
	public static final int KEY_UP = com.mojang.blaze3d.platform.InputConstants.KEY_UP;
	public static final int KEY_DOWN = com.mojang.blaze3d.platform.InputConstants.KEY_DOWN;
	public static final int KEY_HOME = com.mojang.blaze3d.platform.InputConstants.KEY_HOME;
	public static final int KEY_END = com.mojang.blaze3d.platform.InputConstants.KEY_END;
	public static final int KEY_ENTER = com.mojang.blaze3d.platform.InputConstants.KEY_RETURN;
	public static final int KEY_KP_ENTER = com.mojang.blaze3d.platform.InputConstants.KEY_NUMPADENTER;
	public static final int KEY_EQUAL = com.mojang.blaze3d.platform.InputConstants.KEY_EQUALS;
	public static final int KEY_KP_ADD = com.mojang.blaze3d.platform.InputConstants.KEY_ADD;
	public static final int KEY_MINUS = com.mojang.blaze3d.platform.InputConstants.KEY_MINUS;
	public static final int KEY_KP_SUBTRACT = com.mojang.blaze3d.platform.InputConstants.KEY_MINUS;
	public static final int KEY_0 = com.mojang.blaze3d.platform.InputConstants.KEY_0;
	public static final int KEY_KP_0 = com.mojang.blaze3d.platform.InputConstants.KEY_NUMPAD0;
	public static final int MOUSE_BUTTON_LEFT = com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_LEFT;
	public static final int MOUSE_BUTTON_RIGHT = com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_RIGHT;
	*///?} else {
	public static final int KEY_ESCAPE = org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE;
	public static final int KEY_BACKSPACE = org.lwjgl.glfw.GLFW.GLFW_KEY_BACKSPACE;
	public static final int KEY_DELETE = org.lwjgl.glfw.GLFW.GLFW_KEY_DELETE;
	public static final int KEY_LEFT = org.lwjgl.glfw.GLFW.GLFW_KEY_LEFT;
	public static final int KEY_RIGHT = org.lwjgl.glfw.GLFW.GLFW_KEY_RIGHT;
	public static final int KEY_UP = org.lwjgl.glfw.GLFW.GLFW_KEY_UP;
	public static final int KEY_DOWN = org.lwjgl.glfw.GLFW.GLFW_KEY_DOWN;
	public static final int KEY_HOME = org.lwjgl.glfw.GLFW.GLFW_KEY_HOME;
	public static final int KEY_END = org.lwjgl.glfw.GLFW.GLFW_KEY_END;
	public static final int KEY_ENTER = org.lwjgl.glfw.GLFW.GLFW_KEY_ENTER;
	public static final int KEY_KP_ENTER = org.lwjgl.glfw.GLFW.GLFW_KEY_KP_ENTER;
	public static final int KEY_EQUAL = org.lwjgl.glfw.GLFW.GLFW_KEY_EQUAL;
	public static final int KEY_KP_ADD = org.lwjgl.glfw.GLFW.GLFW_KEY_KP_ADD;
	public static final int KEY_MINUS = org.lwjgl.glfw.GLFW.GLFW_KEY_MINUS;
	public static final int KEY_KP_SUBTRACT = org.lwjgl.glfw.GLFW.GLFW_KEY_KP_SUBTRACT;
	public static final int KEY_0 = org.lwjgl.glfw.GLFW.GLFW_KEY_0;
	public static final int KEY_KP_0 = org.lwjgl.glfw.GLFW.GLFW_KEY_KP_0;
	public static final int MOUSE_BUTTON_LEFT = org.lwjgl.glfw.GLFW.GLFW_MOUSE_BUTTON_LEFT;
	public static final int MOUSE_BUTTON_RIGHT = org.lwjgl.glfw.GLFW.GLFW_MOUSE_BUTTON_RIGHT;
	//?}
}
