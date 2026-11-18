//? if >=26.3 {
/*package dev.vy.drt.client.compat;

import java.net.URI;
import org.lwjgl.sdl.SDLError;
import org.lwjgl.sdl.SDLMisc;

public final class SdlCompat {
	private SdlCompat() {}
	public static int mouseButton(int button) {
		return switch (button) { case 1 -> 0; case 2 -> 2; case 3 -> 1; default -> button - 1; };
	}
	public static void openUri(URI uri) {
		if (!SDLMisc.SDL_OpenURL(uri.toASCIIString())) {
			throw new IllegalStateException("Could not open " + uri + ": " + SDLError.SDL_GetError());
		}
	}
}
*///?}
