package dev.vy.drt.mixin;

import dev.vy.drt.client.DrtClient;
import dev.vy.drt.client.tracker.DungeonRunTrackerFeature;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSystemChatPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPacketListener.class)
public abstract class ClientPacketListenerRunDetectionMixin {
	@Inject(method = "handleSystemChat", at = @At("HEAD"))
	private void drt$captureSystemChat(ClientboundSystemChatPacket packet, CallbackInfo ci) {
		if (packet.overlay()) return;
		Component content = packet.content();
		DungeonRunTrackerFeature tracker = DrtClient.getTracker();
		if (tracker != null) tracker.handleRawSystemMessage(content);
	}
}
