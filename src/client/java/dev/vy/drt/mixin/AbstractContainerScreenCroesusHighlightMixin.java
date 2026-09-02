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
 * Draw Croesus slot borders after items, before deferred vanilla tooltips.
 * afterExtract is too late and covers Hypixel lore.
 */
@Mixin(AbstractContainerScreen.class)
public abstract class AbstractContainerScreenCroesusHighlightMixin {
	//? if >= 26.1 {
	@Inject(
		method = "extractContents(Lnet/minecraft/client/gui/GuiGraphicsExtractor;IIF)V",
		at = @At("TAIL")
	)
	private void drt$extractCroesusSlotHighlights(
		GuiGraphicsExtractor graphics,
		int mouseX,
		int mouseY,
		float delta,
		CallbackInfo ci
	) {
		DrtClient.extractCroesusSlotHighlights(Minecraft.getInstance(), graphics, mouseX, mouseY);
	}
	//? } else {
	/*@Inject(
		method = "renderContents(Lnet/minecraft/client/gui/GuiGraphics;IIF)V",
		at = @At("TAIL")
	)
	private void drt$extractCroesusSlotHighlights(
		GuiGraphics graphics,
		int mouseX,
		int mouseY,
		float delta,
		CallbackInfo ci
	) {
		DrtClient.extractCroesusSlotHighlights(Minecraft.getInstance(), graphics, mouseX, mouseY);
	}
	*///?}
}
