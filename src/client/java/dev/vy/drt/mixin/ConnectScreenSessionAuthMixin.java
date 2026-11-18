package dev.vy.drt.mixin;

import dev.vy.drt.client.sync.auth.DrtSessionAuth;
import net.minecraft.client.gui.screens.ConnectScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Covers manual reconnects, quick play, and the vanilla server-transfer packet path. */
@Mixin(ConnectScreen.class)
public abstract class ConnectScreenSessionAuthMixin {
	@Inject(method = "startConnecting", at = @At("HEAD"))
	private static void drt$connectionStarting(CallbackInfo ci) {
		DrtSessionAuth.onConnectionStarting();
	}
}
