package dev.vy.drt.client.screen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.vy.drt.client.overlay.OverlayPreset;
import java.util.List;
import org.junit.jupiter.api.Test;

class OverlayPageStateTest {
	@Test
	void everyExistingLayoutIsOffered() {
		assertEquals(List.of(OverlayPreset.values()), OverlayPageState.LAYOUTS);
	}

	@Test
	void previewingDoesNotApplyUntilApplied() {
		OverlayPageState state = new OverlayPageState();
		assertEquals(OverlayPreset.MODERN, state.previewed(OverlayPreset.MODERN), "the preview starts on the live layout");
		assertFalse(state.hasPendingChange(OverlayPreset.MODERN));

		state.preview(OverlayPreset.CLASSIC);
		assertEquals(OverlayPreset.CLASSIC, state.previewed(OverlayPreset.MODERN));
		assertTrue(state.hasPendingChange(OverlayPreset.MODERN));

		assertEquals(OverlayPreset.CLASSIC, state.apply(OverlayPreset.MODERN));
		assertEquals(OverlayPreset.CLASSIC, state.previewed(OverlayPreset.CLASSIC), "after applying, the preview follows the live layout");
		assertFalse(state.hasPendingChange(OverlayPreset.CLASSIC));
	}

	@Test
	void cancelReturnsToTheLiveLayout() {
		OverlayPageState state = new OverlayPageState();
		state.preview(OverlayPreset.CUSTOM);
		state.cancel();
		assertEquals(OverlayPreset.DETAILED, state.previewed(OverlayPreset.DETAILED));
		assertNull(state.apply(OverlayPreset.DETAILED), "nothing is applied after cancelling");
	}

	@Test
	void applyingTheLiveLayoutChangesNothing() {
		OverlayPageState state = new OverlayPageState();
		state.preview(OverlayPreset.SESSION);
		assertNull(state.apply(OverlayPreset.SESSION));
	}
}
