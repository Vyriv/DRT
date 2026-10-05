package dev.vy.drt.config;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/** A Kuudra key price as shown in the Mage/Barbarian shop, including the player's own discounts. */
public final class KuudraKeyShopCost {
	public long coins;
	/** Price item id to amount, e.g. ENCHANTED_MYCELIUM=80, CORRUPTED_NETHER_STAR=2. */
	public Map<String, Integer> materials = new LinkedHashMap<>();
	public long seenAtMillis;

	public boolean sameCostAs(KuudraKeyShopCost other) {
		if (other == null) return false;
		Map<String, Integer> mine = materials == null ? Map.of() : materials;
		Map<String, Integer> theirs = other.materials == null ? Map.of() : other.materials;
		return coins == other.coins && Objects.equals(mine, theirs);
	}
}
