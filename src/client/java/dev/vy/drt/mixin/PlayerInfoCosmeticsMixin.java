package dev.vy.drt.mixin;

import com.mojang.authlib.GameProfile;
import dev.vy.drt.client.cosmetics.DrtCosmetics;
import dev.vy.drt.client.cosmetics.NameStyler;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.PlayerSkin;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(PlayerInfo.class)
public abstract class PlayerInfoCosmeticsMixin {
	@Shadow
	@Final
	private GameProfile profile;

	@Shadow
	private Component tabListDisplayName;

	@Inject(method = "getTabListDisplayName", at = @At("RETURN"), cancellable = true)
	private void drt$styleDisplayName(CallbackInfoReturnable<Component> cir) {
		Component current = cir.getReturnValue();
		Component styled = DrtCosmetics.styleDisplayName(current, profile);
		if (styled != current) {
			cir.setReturnValue(styled);
		}
	}

	@Inject(method = "setTabListDisplayName", at = @At("HEAD"), cancellable = true)
	private void drt$styleIncomingDisplayName(Component text, CallbackInfo ci) {
		if (text == null || NameStyler.hasAnimatedStyledProfile(profile)) return;

		Component styled = DrtCosmetics.styleDisplayName(text, profile);
		if (styled == text) return;

		this.tabListDisplayName = styled;
		ci.cancel();
	}

	@Inject(method = "getSkin", at = @At("RETURN"), cancellable = true)
	private void drt$applyCustomCape(CallbackInfoReturnable<PlayerSkin> cir) {
		PlayerSkin current = cir.getReturnValue();
		PlayerSkin styled = DrtCosmetics.applyCape(current, profile);
		if (styled != current) {
			cir.setReturnValue(styled);
		}
	}
}
