package dev.vy.drt.mixin;

import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(AbstractContainerScreen.class)
public interface AbstractContainerScreenAccessor {
	@Accessor("leftPos")
	int drt$getLeftPos();

	@Accessor("topPos")
	int drt$getTopPos();

	@Accessor("imageWidth")
	int drt$getImageWidth();

	@Accessor("imageHeight")
	int drt$getImageHeight();

	@Accessor("hoveredSlot")
	Slot drt$getHoveredSlot();
}
