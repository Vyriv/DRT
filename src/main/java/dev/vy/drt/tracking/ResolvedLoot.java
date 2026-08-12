package dev.vy.drt.tracking;

import java.util.List;

public record ResolvedLoot(
	String identityKey,
	String rawName,
	String itemId,
	LootIdentityStrength identityStrength,
	int quantity,
	String resolutionReason,
	List<LootObservation> observations
) {
	public ResolvedLoot {
		identityKey = identityKey == null ? "" : identityKey;
		rawName = rawName == null ? "" : rawName;
		itemId = itemId == null ? "" : itemId;
		identityStrength = identityStrength == null ? LootIdentityStrength.UNRESOLVED : identityStrength;
		quantity = Math.max(1, quantity);
		resolutionReason = resolutionReason == null ? "" : resolutionReason;
		observations = observations == null ? List.of() : List.copyOf(observations);
	}

	public boolean resolved() {
		return identityStrength != LootIdentityStrength.UNRESOLVED && itemId != null && !itemId.isBlank();
	}
}
