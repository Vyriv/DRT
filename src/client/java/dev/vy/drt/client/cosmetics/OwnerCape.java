package dev.vy.drt.client.cosmetics;

import com.mojang.authlib.GameProfile;
import java.util.Locale;
import net.minecraft.core.ClientAsset;
import net.minecraft.world.entity.player.PlayerSkin;

final class OwnerCape {
	private static final NameStylerLruCache<CapeCacheKey, PlayerSkin> CAPE_CACHE = new NameStylerLruCache<>(128);
	private static volatile long observedRegistryVersion = Long.MIN_VALUE;

	private OwnerCape() {
	}

	static PlayerSkin applyCustomCape(GameProfile profile, PlayerSkin skin) {
		if (skin == null) return null;
		if (!PlayerCustomizationRegistry.hasCapeCustomizations()) return skin;

		long version = currentRegistryVersion();
		PlayerCustomizationRegistry.PlayerCustomization customization = PlayerCustomizationRegistry.findWithCape(profile);
		if (customization == null) return skin;

		ClientAsset.Texture capeTexture = CapeTextureManager.getCapeTexture(customization.capeResourcePath(), customization.capeUrl());
		if (capeTexture == null) return skin;

		CapeCacheKey cacheKey = new CapeCacheKey(
			version,
			profileKey(profile),
			skin,
			customization.capeResourcePath(),
			customization.capeUrl());
		PlayerSkin cached = CAPE_CACHE.getCached(cacheKey);
		if (cached != null) return cached;

		PlayerSkin overridden = new PlayerSkin(skin.body(), capeTexture, capeTexture, skin.model(), skin.secure());
		CAPE_CACHE.putCached(cacheKey, overridden);
		return overridden;
	}

	private static long currentRegistryVersion() {
		long version = PlayerCustomizationRegistry.version();
		if (observedRegistryVersion != version) {
			CAPE_CACHE.clearCache();
			observedRegistryVersion = version;
		}
		return version;
	}

	private static String profileKey(GameProfile profile) {
		if (profile == null) return "";
		if (profile.id() != null) return profile.id().toString();
		return profile.name() == null ? "" : profile.name().toLowerCase(Locale.ROOT);
	}

	private record CapeCacheKey(long version, String profileKey, PlayerSkin skin, String capeResourcePath, String capeUrl) {
	}
}
