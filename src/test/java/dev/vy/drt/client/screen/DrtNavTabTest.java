package dev.vy.drt.client.screen;

import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DrtNavTabTest {
	@Test
	void navigationShowsTheFiveTabsInOrder() {
		assertEquals(List.of("Overview", "Loot", "Runs", "Overlay", "Settings"),
			Arrays.stream(DrtNavTab.values()).map(DrtNavTab::title).toList());
		assertEquals(List.of("Ovr", "Loot", "Runs", "HUD", "Set"),
			Arrays.stream(DrtNavTab.values()).map(DrtNavTab::compactTitle).toList());
	}

	@Test
	void sessionPageIsNoLongerATab() {
		assertFalse(Arrays.stream(DrtNavTab.values()).anyMatch(tab -> tab.title().equalsIgnoreCase("Session")));
		assertThrows(IllegalArgumentException.class, () -> DrtNavTab.valueOf("SESSION"));
	}
}
