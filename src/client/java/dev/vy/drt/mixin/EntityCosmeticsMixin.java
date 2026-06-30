package dev.vy.drt.mixin;

import dev.vy.drt.client.cosmetics.DrtCosmetics;
import dev.vy.drt.client.cosmetics.NameStyler;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Entity.class)
public abstract class EntityCosmeticsMixin {
	@Inject(method = "getDisplayName", at = @At("RETURN"), cancellable = true)
	private void drt$stylePlayerDisplayName(CallbackInfoReturnable<Component> cir) {
		if (!((Object) this instanceof Player player)) return;
		Component current = cir.getReturnValue();
		Component styled = DrtCosmetics.styleDisplayName(NameStyler.styleEntityName(current), player.getGameProfile());
		if (styled != current) {
			cir.setReturnValue(styled);
		}
	}
}
