package dev.vy.drt.config;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.junit.jupiter.api.Test;

class OverlaySettingsTest {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final String CUSTOM = "DRT [{floor}]\n<#55FFFF>Runs: {runs.session}</#>";

	private static DrtConfig customised() {
		DrtConfig config = new DrtConfig();
		config.hudOverlayPreset = "DETAILED";
		config.customOverlayLayout = CUSTOM;
		config.hudX = 120;
		config.hudY = 48;
		config.hudScale = 1.25F;
		config.hudVisibilityMode = "DHUB";
		config.enabled = false;
		config.selectedTheme = "Midnight";
		return config;
	}

	@Test
	void choosingAStandardPresetKeepsTheCustomLayoutText() {
		DrtConfig config = customised();
		OverlaySettings.applyPreset(config, "classic");
		assertEquals("CLASSIC", config.hudOverlayPreset);
		assertEquals(CUSTOM, config.customOverlayLayout);
		OverlaySettings.applyPreset(config, "CUSTOM");
		assertEquals(CUSTOM, config.customOverlayLayout, "activating Custom uses the saved text unchanged");
	}

	@Test
	void editingTheCustomLayoutKeepsTheActiveStandardPreset() {
		DrtConfig config = customised();
		OverlaySettings.applyCustomLayout(config, "Line one\r\nLine two\rLine three");
		assertEquals("Line one\nLine two\nLine three", config.customOverlayLayout);
		assertEquals("DETAILED", config.hudOverlayPreset);

		OverlaySettings.applyPresetAndLayout(config, "CUSTOM", null);
		assertEquals("CUSTOM", config.hudOverlayPreset);
		assertEquals("Line one\nLine two\nLine three", config.customOverlayLayout, "a null layout leaves the text alone");
	}

	@Test
	void overlayEditsLeaveOtherOverlaySettingsAndTheThemeUntouched() {
		DrtConfig config = customised();
		OverlaySettings.applyPreset(config, "SESSION");
		OverlaySettings.applyCustomLayout(config, "Edited");
		assertEquals(120, config.hudX);
		assertEquals(48, config.hudY);
		assertEquals(1.25F, config.hudScale);
		assertEquals("DHUB", config.hudVisibilityMode);
		assertEquals(false, config.enabled);
		assertEquals("Midnight", config.selectedTheme);
	}

	@Test
	void changingTheUiThemeLeavesEveryOverlaySettingUntouched() {
		DrtConfig config = customised();
		config.selectedTheme = "  Sunset  ";
		DrtSettingsValidation.normalize(config);
		assertEquals("Sunset", config.selectedTheme);
		assertEquals("DETAILED", config.hudOverlayPreset);
		assertEquals(CUSTOM, config.customOverlayLayout);
		assertEquals(120, config.hudX);
		assertEquals(48, config.hudY);
		assertEquals(1.25F, config.hudScale);
		assertEquals("DHUB", config.hudVisibilityMode);
		assertEquals(false, config.enabled);
	}

	@Test
	void overlaySettingsSurviveASaveAndReload() {
		DrtConfig reloaded = GSON.fromJson(GSON.toJson(customised()), DrtConfig.class);
		assertEquals("DETAILED", reloaded.hudOverlayPreset);
		assertEquals(CUSTOM, reloaded.customOverlayLayout);
		assertEquals(120, reloaded.hudX);
		assertEquals(48, reloaded.hudY);
		assertEquals(1.25F, reloaded.hudScale);
		assertEquals("DHUB", reloaded.hudVisibilityMode);
		assertEquals(false, reloaded.enabled);
		assertEquals("Midnight", reloaded.selectedTheme);
	}

	@Test
	void anExistingConfigKeepsItsOverlayWhenUpgrading() {
		String saved = "{\"hudOverlayPreset\":\"CUSTOM\",\"customOverlayLayout\":\"My {runs.total}\",\"hudX\":300,\"hudY\":7,"
			+ "\"hudScale\":0.75,\"hudVisibilityMode\":\"GLOBAL\",\"enabled\":true,\"selectedTheme\":\"Default\"}";
		DrtConfig loaded = GSON.fromJson(saved, DrtConfig.class);
		assertEquals("CUSTOM", OverlaySettings.normalizePreset(loaded.hudOverlayPreset, false));
		assertEquals("My {runs.total}", loaded.customOverlayLayout);
		assertEquals(300, loaded.hudX);
		assertEquals(7, loaded.hudY);
		assertEquals(0.75F, loaded.hudScale);
		assertEquals("GLOBAL", loaded.hudVisibilityMode);
	}

	@Test
	void unknownPresetsFallBackWithoutTouchingTheLayout() {
		DrtConfig config = customised();
		OverlaySettings.applyPreset(config, "not-a-preset");
		assertEquals("LEGACY", config.hudOverlayPreset);
		assertEquals(CUSTOM, config.customOverlayLayout);
		assertEquals("MODERN", OverlaySettings.normalizePreset(null, true));
	}

	@Test
	void resetPlacementDefaultsMatchAFreshConfig() {
		DrtConfig fresh = new DrtConfig();
		assertEquals(fresh.hudX, OverlaySettings.DEFAULT_HUD_X);
		assertEquals(fresh.hudY, OverlaySettings.DEFAULT_HUD_Y);
		assertEquals(fresh.hudScale, OverlaySettings.DEFAULT_HUD_SCALE);
	}
}
