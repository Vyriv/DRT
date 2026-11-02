package dev.vy.drt.client;

import java.net.URI;

public final class DrtPlatformLinks {
	private DrtPlatformLinks() {
	}

	public static void openUri(URI uri) {
		//? if >= 26.3 {
		/*com.mojang.blaze3d.Blaze3D.openUri(uri);
		*///?} else {
		net.minecraft.util.Util.getPlatform().openUri(uri);
		//?}
	}
}
