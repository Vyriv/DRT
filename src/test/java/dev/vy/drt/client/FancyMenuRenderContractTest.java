package dev.vy.drt.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class FancyMenuRenderContractTest {
	@Test
	void fancyActiveRequiresDrawBeforeVanillaCancel() {
		assertTrue(FancyMenuRenderContract.mustDrawBeforeVanillaCancel(true));
		assertEquals(
			FancyMenuRenderContract.DrawSite.VANILLA_SUPPRESS_HOOK,
			FancyMenuRenderContract.drawSite(true)
		);
	}

	@Test
	void fancyInactiveUsesContentsTailForCroesusOverlay() {
		assertFalse(FancyMenuRenderContract.mustDrawBeforeVanillaCancel(false));
		assertEquals(
			FancyMenuRenderContract.DrawSite.CONTENTS_TAIL,
			FancyMenuRenderContract.drawSite(false)
		);
	}
}
