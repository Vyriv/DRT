package dev.vy.drt.mixin;

import dev.vy.drt.client.sync.auth.DrtSessionAuth;
import net.minecraft.client.multiplayer.ClientHandshakePacketListenerImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Observe login start, including callers that bypass ConnectScreen. Never intercept vanilla I/O. */
@Mixin(ClientHandshakePacketListenerImpl.class)
public abstract class ClientHandshakeSessionAuthMixin {
	@Inject(method = "<init>", at = @At("RETURN"))
	private void drt$loginStarting(CallbackInfo ci) {
		DrtSessionAuth.onConnectionStarting();
	}
}
