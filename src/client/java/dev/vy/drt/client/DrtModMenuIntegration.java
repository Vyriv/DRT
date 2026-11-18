package dev.vy.drt.client;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;
import dev.vy.drt.client.screen.DrtNavTab;
import dev.vy.drt.client.screen.DrtScreen;

public final class DrtModMenuIntegration implements ModMenuApi {
	@Override
	public ConfigScreenFactory<?> getModConfigScreenFactory() {
		return parent -> new DrtScreen(DrtNavTab.SETTINGS, null, null, parent);
	}
}
