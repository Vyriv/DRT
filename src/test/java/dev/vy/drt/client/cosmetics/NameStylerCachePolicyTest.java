package dev.vy.drt.client.cosmetics;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class NameStylerCachePolicyTest {
	@Test
	void admitsNormalTextAndRejectsOversizedText() {
		assertTrue(NameStyler.cacheable("x".repeat(4096)));
		assertFalse(NameStyler.cacheable("x".repeat(4097)));
	}
}
