package dev.vy.drt.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.vy.drt.client.cosmetics.DrtCosmetics;
import net.minecraft.client.gui.components.PlayerTabOverlay;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(PlayerTabOverlay.class)
public abstract class PlayerTabOverlayCosmeticsMixin {
	@Inject(method = "getNameForDisplay", at = @At("RETURN"), cancellable = true)
	private void drt$styleTabName(PlayerInfo playerInfo, CallbackInfoReturnable<Component> cir) {
		if (playerInfo == null) return;
		Component current = cir.getReturnValue();
		Component styled = DrtCosmetics.styleDisplayName(current, playerInfo.getProfile());
		if (styled != current) {
			cir.setReturnValue(styled);
		}
	}

	// Styled at the draw call, not in getNameForDisplay: SkyHanni and TabReader parse tab lines
	// through that method, and SkyHanni decorates its compact tab separately.
	@WrapOperation(
		method = "extractRenderState",
		at = @At(
			value = "INVOKE",
			target = "Lnet/minecraft/client/gui/components/PlayerTabOverlay;getNameForDisplay(Lnet/minecraft/client/multiplayer/PlayerInfo;)Lnet/minecraft/network/chat/Component;"
		)
	)
	private Component drt$styleRenderedTabName(PlayerTabOverlay overlay, PlayerInfo playerInfo, Operation<Component> original) {
		Component current = original.call(overlay, playerInfo);
		return DrtCosmetics.styleRenderedTabName(current, playerInfo == null ? null : playerInfo.getProfile());
	}
}
