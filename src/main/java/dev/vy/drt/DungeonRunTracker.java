package dev.vy.drt;

import dev.vy.drt.config.DrtConfigManager;
import dev.vy.drt.price.PriceCache;
import net.fabricmc.api.ModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class DungeonRunTracker implements ModInitializer {
	public static final String MOD_ID = "drt";
	public static final Logger LOGGER = LoggerFactory.getLogger("DRT");

	@Override
	public void onInitialize() {
		DrtConfigManager.load();
		PriceCache.start();
		LOGGER.info("[DRT] DungeonRunTracker loaded");
	}
}
