package dev.vy.drt.client;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;
import dev.vy.drt.client.screen.DrtOnboardingScreen;

public final class DrtModMenuIntegration implements ModMenuApi {
	@Override
	public ConfigScreenFactory<?> getModConfigScreenFactory() {
		return parent -> new DrtOnboardingScreen(DrtClient.getTracker());
	}
}
