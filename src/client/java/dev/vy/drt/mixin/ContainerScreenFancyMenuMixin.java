package dev.vy.drt.mixin;

import dev.vy.drt.client.DrtClient;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.ContainerScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Skip the vanilla chest background blit when Fancy Menu is active.
 * extractBackground is declared on ContainerScreen (not ACS), so it needs its own target.
 */
@Mixin(ContainerScreen.class)
public abstract class ContainerScreenFancyMenuMixin {
	@Inject(
		method = "extractBackground(Lnet/minecraft/client/gui/GuiGraphicsExtractor;IIF)V",
		at = @At("HEAD"),
		cancellable = true
	)
	private void drt$suppressVanillaContainerBackground(
		GuiGraphicsExtractor graphics,
		int mouseX,
		int mouseY,
		float delta,
		CallbackInfo ci
	) {
		Minecraft client = Minecraft.getInstance();
		if (DrtClient.shouldSuppressVanillaContainerPresentation(client)) {
			ci.cancel();
		}
	}
}
