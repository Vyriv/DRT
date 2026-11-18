package dev.vy.drt.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ThemeAccentTextTest {
	@Test void accentTextDraftOverridesPreviewAndPersists() throws Exception {
		ThemeEditSession session = new ThemeEditSession(null);
		session.set("text.accent", "#FF123456");
		assertEquals(0xFF123456, ThemeProperties.resolve(session.palette(), "text.accent", 0xFF9CA8FF));
		assertEquals("#FF123456", session.document().get("text.accent").getAsString());
	}
}
