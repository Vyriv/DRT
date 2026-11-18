package dev.vy.drt.mixin;

import dev.vy.drt.client.DrtClient;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Skip vanilla container slot/item/tooltip extract when Fancy Menu is active.
 * Applied on ACS so subclasses (including ContainerScreen) inherit the cancel.
 * The underlying menu stays open for DRT scanning.
 *
 * Fancy itself must be drawn here: cancelling {@code extractRenderState} also
 * skips {@code extractContents}, which is where {@link AbstractContainerScreenCroesusHighlightMixin}
 * would otherwise paint the Fancy panel.
 */
@Mixin(AbstractContainerScreen.class)
public abstract class AbstractContainerScreenFancyMenuMixin {
	@Inject(
		method = "extractRenderState(Lnet/minecraft/client/gui/GuiGraphicsExtractor;IIF)V",
		at = @At("HEAD"),
		cancellable = true
	)
	private void drt$suppressVanillaContainerExtract(
		GuiGraphicsExtractor graphics,
		int mouseX,
		int mouseY,
		float delta,
		CallbackInfo ci
	) {
		Minecraft client = Minecraft.getInstance();
		if (!DrtClient.shouldSuppressVanillaContainerPresentation(client)) return;
		// Draw Fancy before cancel. extractContents will not run after this returns.
		DrtClient.extractChestOverlay(client, graphics, mouseX, mouseY);
		ci.cancel();
	}
}
