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
	private static final String TAB_LINE_CTOR_NAMED =
			"Lat/hannibal2/skyhanni/features/misc/compacttablist/TabLine;<init>("
					+ "Lnet/minecraft/network/chat/Component;"
					+ "Lat/hannibal2/skyhanni/features/misc/compacttablist/TabStringType;"
					+ "Lnet/minecraft/network/chat/Component;)V";
	private static final String TAB_LINE_CTOR_INTERMEDIARY =
			"Lat/hannibal2/skyhanni/features/misc/compacttablist/TabLine;<init>("
					+ "Lnet/minecraft/class_2561;"
					+ "Lat/hannibal2/skyhanni/features/misc/compacttablist/TabStringType;"
					+ "Lnet/minecraft/class_2561;)V";

	@ModifyArgs(
		method = "createTabLine",
		at = @At(
			value = "INVOKE",
			target = TAB_LINE_CTOR_NAMED
		),
		require = 0,
		remap = false
	)
	private void drt$decorateCompactTabLineNamed(Args args) {
		drt$decorateCompactTabLine(args);
	}

	@ModifyArgs(
		method = "createTabLine",
		at = @At(
			value = "INVOKE",
			target = TAB_LINE_CTOR_INTERMEDIARY
		),
		require = 0,
		remap = false
	)
	private void drt$decorateCompactTabLineIntermediary(Args args) {
		drt$decorateCompactTabLine(args);
	}

	private void drt$decorateCompactTabLine(Args args) {
		Object value = args.get(2);
		if (!(value instanceof Component current)) return;

		Component styled = NameStyler.applyNameplateDisplayDecorations(current);
		if (styled != current) {
			args.set(2, styled);
		}
	}
}
