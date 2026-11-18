package dev.vy.drt.client.cosmetics;

import java.util.List;

final class CosmeticsTestFixtures {
	private CosmeticsTestFixtures() {
	}

	static PlayerCustomizationRegistry.PlayerCustomization customization(String name) {
		return customization(name, false);
	}

	static PlayerCustomizationRegistry.PlayerCustomization customization(String name, boolean animated) {
		return new PlayerCustomizationRegistry.PlayerCustomization(
			name,
			null,
			null,
			List.of(),
			new PlayerCustomizationRegistry.NameColors(0xFF0000, 0x0000FF, 1.0F),
			List.of(),
			animated,
			1.0F,
			120,
			false,
			null,
			null,
			null,
			null);
	}

	static PlayerCustomizationRegistry.NameCandidate candidate(String name, boolean boundary) {
		return new PlayerCustomizationRegistry.NameCandidate(customization(name), name, boundary);
	}
}
