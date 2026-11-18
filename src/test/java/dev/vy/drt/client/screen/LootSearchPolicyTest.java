package dev.vy.drt.client.screen;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LootSearchPolicyTest {
	@Test
	void enterAndShiftEnterCommitDistinctScopes() {
		var ordinary = LootSearchPolicy.committed("Recombobulator", false);
		assertTrue(ordinary.committed());
		assertFalse(ordinary.includeUnopened());
		var expanded = LootSearchPolicy.committed("Recombobulator", true);
		assertTrue(expanded.committed());
		assertTrue(expanded.includeUnopened());
	}

	@Test
	void editingAndClearingResetExpandedMode() {
		assertFalse(LootSearchPolicy.edited("Recombo").includeUnopened());
		assertFalse(LootSearchPolicy.edited("").includeUnopened());
		assertFalse(LootSearchPolicy.committed("", true).committed());
	}
}
