package dev.vy.drt.tracking;

public enum LootIdentityStrength {
	UNRESOLVED(0),
	STRICT_ALIAS(1),
	STRUCTURED_CHAT(2),
	EXACT_COMPONENT_ID(3);

	private final int rank;

	LootIdentityStrength(int rank) {
		this.rank = rank;
	}

	public int rank() {
		return rank;
	}
}
