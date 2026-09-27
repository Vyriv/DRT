package dev.vy.drt.mixin;

import dev.vy.drt.client.cosmetics.NameStyler;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Team;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

// Hypixel's tab and sidebar lines are team prefix + name + suffix, so badges (e.g. :3) can
// only be added where vanilla formats a name for its team. Mirrors Skylist's TeamMixin.
@Mixin(PlayerTeam.class)
public abstract class PlayerTeamCosmeticsMixin {
	@Inject(method = "getFormattedName(Lnet/minecraft/network/chat/Component;)Lnet/minecraft/network/chat/MutableComponent;", at = @At("RETURN"), cancellable = true)
	private void drt$decorateTeamName(Component name, CallbackInfoReturnable<MutableComponent> cir) {
		MutableComponent styled = drt$decorate(cir.getReturnValue());
		if (styled != null) cir.setReturnValue(styled);
	}

	@Inject(method = "formatNameForTeam(Lnet/minecraft/world/scores/Team;Lnet/minecraft/network/chat/Component;)Lnet/minecraft/network/chat/MutableComponent;", at = @At("RETURN"), cancellable = true)
	private static void drt$decorateStaticTeamName(Team team, Component name, CallbackInfoReturnable<MutableComponent> cir) {
		MutableComponent styled = drt$decorate(cir.getReturnValue());
		if (styled != null) cir.setReturnValue(styled);
	}

	@Unique
	private static MutableComponent drt$decorate(MutableComponent current) {
		if (current == null) return null;
		Component styled = NameStyler.applyScoreboardDisplayDecorations(current);
		if (styled == current) return null;
		return styled instanceof MutableComponent mutable ? mutable : styled.copy();
	}
}
