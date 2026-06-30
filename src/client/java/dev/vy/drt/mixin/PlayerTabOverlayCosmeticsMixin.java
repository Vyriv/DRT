package dev.vy.drt.mixin;

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
}
