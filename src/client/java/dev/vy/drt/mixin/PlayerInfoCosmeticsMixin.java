package dev.vy.drt.mixin;

import com.mojang.authlib.GameProfile;
import dev.vy.drt.client.cosmetics.CosmeticRenderer;
import dev.vy.drt.client.cosmetics.DrtCosmetics;
import dev.vy.drt.client.cosmetics.NameStyler;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.PlayerSkin;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
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

	@Unique
	private Component drt$unstyledDisplayName;

	@Unique
	private Component drt$bakedDisplayName;

	// A tab name baked while DRT owned cosmetic names must not outlive that ownership,
	// otherwise the next owner (or Off) sees stale styling until the server resends it.
	@Inject(method = "getTabListDisplayName", at = @At("HEAD"))
	private void drt$restoreUnstyledDisplayName(CallbackInfoReturnable<Component> cir) {
		if (drt$bakedDisplayName == null || CosmeticRenderer.active()) return;
		if (tabListDisplayName == drt$bakedDisplayName) {
			tabListDisplayName = drt$unstyledDisplayName;
		}
		drt$bakedDisplayName = null;
		drt$unstyledDisplayName = null;
	}

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
		drt$bakedDisplayName = null;
		drt$unstyledDisplayName = null;
		if (text == null || NameStyler.hasAnimatedStyledProfile(profile)) return;

		Component styled = DrtCosmetics.styleDisplayName(text, profile);
		if (styled == text) return;

		drt$unstyledDisplayName = text;
		drt$bakedDisplayName = styled;
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
