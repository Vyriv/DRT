package dev.vy.drt.client.cosmetics;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class PlayerCustomizationRegistryTest {
	@Test
	void rebuildsMatcherIndexesWhenRegistryContentChanges() {
		PlayerCustomizationRegistry.initialize(List.of(loaded("FirstOwner", false)));
		long firstVersion = PlayerCustomizationRegistry.version();
		assertNotNull(NameStyleMatcher.findFirstNameMatch("FirstOwner", PlayerCustomizationRegistry.gradientNameMatcher(), 0));

		PlayerCustomizationRegistry.initialize(List.of(loaded("SecondOwner", false)));
		assertTrue(PlayerCustomizationRegistry.version() > firstVersion);
		assertNull(NameStyleMatcher.findFirstNameMatch("FirstOwner", PlayerCustomizationRegistry.gradientNameMatcher(), 0));
		assertNotNull(NameStyleMatcher.findFirstNameMatch("SecondOwner", PlayerCustomizationRegistry.gradientNameMatcher(), 0));
	}

	static CosmeticsContentManager.LoadedPlayerCustomization loaded(String name, boolean animated) {
		CosmeticsContentManager.LoadedNameStyle.Mode mode = animated
			? CosmeticsContentManager.LoadedNameStyle.Mode.ANIMATED_GRADIENT
			: CosmeticsContentManager.LoadedNameStyle.Mode.GRADIENT;
		return new CosmeticsContentManager.LoadedPlayerCustomization(
			name,
			null,
			null,
			List.of(),
			new CosmeticsContentManager.LoadedNameStyle(mode, 0xFF0000, 0x0000FF, false, List.of(), 5.0F, 120, 1.0F),
			null,
			null,
			null,
			null);
	}
}
