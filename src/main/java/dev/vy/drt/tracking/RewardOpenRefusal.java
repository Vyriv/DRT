package dev.vy.drt.tracking;

/**
 * Hypixel chat that refuses a reward chest open. The chest stays closed and nothing is paid, so
 * the preview contents already seen must not be saved as loot.
 */
public final class RewardOpenRefusal {
	private RewardOpenRefusal() {
	}

	/**
	 * @param cleaned the chat line, uppercased with formatting stripped; chat-stacking mods may
	 *     append " (2)" to a repeat
	 */
	public static boolean refusesOpen(String cleaned) {
		if (cleaned == null) return false;
		return cleaned.startsWith("YOU CANNOT AFFORD THIS!")
			|| cleaned.startsWith("YOU NEED A DUNGEON CHEST KEY TO OPEN ANOTHER CHEST");
	}
}
