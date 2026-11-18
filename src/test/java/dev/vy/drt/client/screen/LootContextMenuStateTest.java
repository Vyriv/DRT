package dev.vy.drt.client.screen;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LootContextMenuStateTest {
	@Test
	void positionsInsideBoundsAndKeepsExactTarget() {
		LootContextMenuState state = new LootContextMenuState();
		state.open(37, 490, 390, 120, 80, 100, 100, 400, 300);
		assertEquals(37, state.targetIndex());
		assertEquals(new LootContextMenuState.Position(380, 320), state.position());
		assertTrue(state.contains(400, 340));
		assertFalse(state.contains(99, 99));
	}

	@Test
	void deleteRequiresSecondRequestAndDismissalClearsTarget() {
		LootContextMenuState state = new LootContextMenuState();
		state.open(4, 1, 1, 120, 80, 0, 0, 500, 500);
		assertFalse(state.requestDelete());
		assertTrue(state.deleteArmed());
		assertTrue(state.requestDelete());
		state.close();
		assertFalse(state.open());
		assertEquals(-1, state.targetIndex());
	}
}
