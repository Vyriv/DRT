package dev.vy.drt.client.cosmetics;

import com.mojang.authlib.GameProfile;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.PlayerSkin;

public final class DrtCosmetics {
	private DrtCosmetics() {
	}

	public static void initialize() {
		CosmeticsContentManager.initialize();
	}

	public static Component styleDisplayName(Component current, GameProfile profile) {
		if (current == null || !NameStyler.hasDisplayProfile(profile)) return current;
		return NameStyler.applyNameplateDisplayDecorations(current);
	}

	public static Component styleMatchingNames(Component current) {
		return NameStyler.applyNameplateDecorations(current);
	}

	public static PlayerSkin applyCape(PlayerSkin current, GameProfile profile) {
		return OwnerCape.applyCustomCape(profile, current);
	}
}
