package dev.vy.drt.mixin;

import dev.vy.drt.client.cosmetics.NameStyler;
import net.minecraft.network.chat.Component;
import net.minecraft.world.scores.ScoreHolder;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

// Mirrors Skylist's ScoreboardEntryMixin so scoreboard names get the same badge decoration.
@Mixin(ScoreHolder.class)
public interface ScoreHolderCosmeticsMixin {
	@Inject(method = "getDisplayName", at = @At("RETURN"), cancellable = true)
	private void drt$decorateScoreHolderName(CallbackInfoReturnable<Component> cir) {
		Component current = cir.getReturnValue();
		if (current == null) return;
		Component styled = NameStyler.applyScoreboardDisplayDecorations(current);
		if (styled != current) cir.setReturnValue(styled);
	}
}
