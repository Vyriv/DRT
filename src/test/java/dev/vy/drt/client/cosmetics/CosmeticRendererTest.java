package dev.vy.drt.client.cosmetics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.Set;
import org.junit.jupiter.api.Test;

class CosmeticRendererTest {
	@Test
	void preservesSharedAutoPriority() {
		assertEquals("betterpv", CosmeticRenderer.resolveOwner("auto", Set.of("betterpv", "drt", "skylist")::contains));
		assertEquals("drt", CosmeticRenderer.resolveOwner("auto", Set.of("drt", "skylist")::contains));
		assertEquals("skylist", CosmeticRenderer.resolveOwner("auto", Set.of("skylist")::contains));
		assertNull(CosmeticRenderer.resolveOwner("auto", ignored -> false));
	}

	@Test
	void preservesOffAndExplicitOwnership() {
		assertNull(CosmeticRenderer.resolveOwner("off", ignored -> true));
		assertEquals("drt", CosmeticRenderer.resolveOwner("drt", ignored -> false));
		assertEquals("betterpv", CosmeticRenderer.resolveOwner("betterpv", ignored -> false));
	}

	@Test
	void preservesCommandAliases() {
		assertEquals("auto", CosmeticRenderer.normalize(" AUTO "));
		assertEquals("betterpv", CosmeticRenderer.normalize("pv"));
		assertEquals("off", CosmeticRenderer.normalize("none"));
		assertNull(CosmeticRenderer.normalize("unknown"));
	}
}
