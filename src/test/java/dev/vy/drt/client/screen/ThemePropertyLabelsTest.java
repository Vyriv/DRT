package dev.vy.drt.client.screen;

import dev.vy.drt.config.ThemeProperties;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ThemePropertyLabelsTest {
	@Test
	void providesFriendlyNamesForEveryEditableProperty() {
		assertEquals(ThemeProperties.SUPPORTED, ThemePropertyLabels.all().keySet());
		assertEquals("Main Background", ThemePropertyLabels.displayName("window.background"));
		assertEquals("Secondary Panel Background", ThemePropertyLabels.displayName("panel.alternate"));
		assertEquals("Selected Row", ThemePropertyLabels.displayName("row.selected"));
		assertEquals("Section Heading", ThemePropertyLabels.displayName("text.sectionTitle"));
		assertEquals("Toggle Border", ThemePropertyLabels.displayName("toggle.border"));
		assertEquals("Menu Border", ThemePropertyLabels.displayName("menu.border"));
	}

	@Test
	void cyclesPropertiesInBothDirectionsWithoutNumbering() {
		assertEquals(1, ThemePropertyLabels.cycleIndex(0, 3, 1));
		assertEquals(0, ThemePropertyLabels.cycleIndex(2, 3, 1));
		assertEquals(2, ThemePropertyLabels.cycleIndex(0, 3, -1));
		assertEquals(0, ThemePropertyLabels.cycleIndex(0, 1, 1));
	}
}
