package dev.vy.drt.tracking;

import java.util.Locale;

public record LootObservation(
	String observationId,
	long eventSequence,
	DetectionSource source,
	String rawName,
	String normalizedName,
	String itemId,
	LootIdentityStrength identityStrength,
	int quantity,
	int containerId,
	int slotIndex,
	SlotOwner slotOwner,
	String dedupKey
) {
	public LootObservation {
		observationId = observationId == null ? "" : observationId;
		source = source == null ? DetectionSource.NONE : source;
		rawName = rawName == null ? "" : rawName;
		normalizedName = normalizedName == null || normalizedName.isBlank() ? normalize(rawName) : normalizedName;
		itemId = itemId == null ? "" : itemId.trim().toUpperCase(Locale.ROOT);
		identityStrength = identityStrength == null ? LootIdentityStrength.UNRESOLVED : identityStrength;
		quantity = Math.max(1, quantity);
		slotOwner = slotOwner == null ? SlotOwner.UNKNOWN : slotOwner;
		dedupKey = dedupKey == null || dedupKey.isBlank()
			? source + "|" + containerId + "|" + slotIndex + "|" + (itemId.isBlank() ? normalizedName : itemId) + "|" + quantity
			: dedupKey;
	}

	public String identityKey() {
		if (!itemId.isBlank() && identityStrength != LootIdentityStrength.UNRESOLVED) return "id:" + itemId;
		return "unresolved:" + normalizedName;
	}

	private static String normalize(String value) {
		return value == null ? "" : value.trim().replaceAll("\\s+", " ").toUpperCase(Locale.ROOT);
	}
}
