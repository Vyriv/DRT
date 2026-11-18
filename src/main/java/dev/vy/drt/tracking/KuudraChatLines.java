package dev.vy.drt.tracking;

/**
 * Hypixel chat that starts or ends a Kuudra run. Each line takes the chat text uppercased with
 * formatting stripped.
 */
public final class KuudraChatLines {
	private KuudraChatLines() {
	}

	/**
	 * "[MVP+] Name entered Kuudra's Hollow, Infernal Tier!". Nothing else counts: an entry restarts
	 * the run timer, and player chat ("anyone kuudra basic tier?"), key purchases and the reward line
	 * "You gained 50 reputation for killing this tier of Kuudra!" sent just before "KUUDRA DOWN!"
	 * all mention Kuudra and a tier.
	 */
	public static boolean isEntry(String cleaned) {
		if (cleaned == null || cleaned.contains(":")) return false;
		return cleaned.contains("ENTERED KUUDRA'S HOLLOW") || cleaned.contains("ENTERED KUUDRA HOLLOW");
	}

	public static boolean isCompletion(String cleaned) {
		if (cleaned == null) return false;
		return cleaned.contains("KUUDRA DOWN")
			|| cleaned.contains("KUUDRA DEFEATED")
			|| cleaned.contains("DEFEATED KUUDRA")
			|| cleaned.contains("KUUDRA RUN ENDED: COMPLETED");
	}
}
