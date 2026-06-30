package dev.vy.drt.mixin;

import dev.vy.drt.client.cosmetics.DrtCosmetics;
import dev.vy.drt.client.cosmetics.NameStyler;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(EntityRenderer.class)
public abstract class EntityRendererCosmeticsMixin<T extends Entity, S extends EntityRenderState> {
	@Inject(method = "extract" + "RenderState", at = @At("RETURN"))
	private void drt$styleNameTag(T entity, S state, float tickProgress, CallbackInfo ci) {
		if (!(entity instanceof Player player) || state == null || state.nameTag == null) return;

		Component current = state.nameTag;
		Component styled = DrtCosmetics.styleDisplayName(NameStyler.applyNameplateDecorations(current), player.getGameProfile());
		if (styled != current) {
			state.nameTag = styled;
		}
	}
}
