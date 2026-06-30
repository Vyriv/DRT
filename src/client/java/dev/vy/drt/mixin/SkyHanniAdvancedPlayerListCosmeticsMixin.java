package dev.vy.drt.mixin;

import dev.vy.drt.client.cosmetics.NameStyler;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;

@Pseudo
@Mixin(targets = "at.hannibal2.skyhanni.features.misc.compacttablist.AdvancedPlayerList", remap = false)
public abstract class SkyHanniAdvancedPlayerListCosmeticsMixin {
	@ModifyArgs(
		method = "createTabLine",
		at = @At(
			value = "INVOKE",
			target = "Lat/hannibal2/skyhanni/features/misc/compacttablist/TabLine;<init>(Lnet/minecraft/class_2561;Lat/hannibal2/skyhanni/features/misc/compacttablist/TabStringType;Lnet/minecraft/class_2561;)V"
		),
		remap = false
	)
	private void drt$decorateCompactTabLine(Args args) {
		Object value = args.get(2);
		if (!(value instanceof Component current)) return;

		Component styled = NameStyler.applyNameplateDisplayDecorations(current);
		if (styled != current) {
			args.set(2, styled);
		}
	}
}
