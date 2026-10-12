package dev.vy.drt.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ClearConfirmationTest {
	private static final long NOW = 1_800_000_000_000L;

	@Test
	void secondClickAfterAPauseConfirms() {
		ClearConfirmation confirm = new ClearConfirmation();
		assertEquals(ClearConfirmation.Result.ARMED, confirm.click(null, NOW));
		assertTrue(confirm.isArmed(null, NOW + 100L));
		assertEquals(ClearConfirmation.Result.CONFIRMED, confirm.click(null, NOW + 800L));
		assertFalse(confirm.isArmed(null, NOW + 900L), "a confirmed clear needs two new clicks");
	}

	@Test
	void fastDoubleClickNeverConfirms() {
		ClearConfirmation confirm = new ClearConfirmation();
		assertEquals(ClearConfirmation.Result.ARMED, confirm.click(null, NOW));
		assertEquals(ClearConfirmation.Result.TOO_SOON, confirm.click(null, NOW + 120L));
		assertEquals(ClearConfirmation.Result.TOO_SOON, confirm.click(null, NOW + 300L));
		assertEquals(ClearConfirmation.Result.CONFIRMED, confirm.click(null, NOW + ClearConfirmation.MIN_CONFIRM_DELAY_MILLIS),
			"ignored clicks neither confirm nor restart the pause");
	}

	@Test
	void expiredOrDifferentScopeArmsAgainInsteadOfConfirming() {
		ClearConfirmation confirm = new ClearConfirmation();
		confirm.click("F5", NOW);
		assertEquals(ClearConfirmation.Result.ARMED, confirm.click(null, NOW + 1_000L), "arming F5 never confirms clearing every floor");
		assertEquals(ClearConfirmation.Result.ARMED, confirm.click(null, NOW + 1_000L + ClearConfirmation.CONFIRM_WINDOW_MILLIS + 1L));
		assertFalse(confirm.isArmed("F5", NOW + 1_500L));
	}

	@Test
	void resetDisarms() {
		ClearConfirmation confirm = new ClearConfirmation();
		confirm.click("M5", NOW);
		confirm.reset();
		assertEquals(ClearConfirmation.Result.ARMED, confirm.click("M5", NOW + 1_000L));
	}
}
