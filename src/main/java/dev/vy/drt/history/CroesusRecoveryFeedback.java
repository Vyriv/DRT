package dev.vy.drt.history;

/** User-facing summary for one explicit recent-Croesus recovery attempt. */
public final class CroesusRecoveryFeedback {
	private CroesusRecoveryFeedback() {
	}

	public static String summarize(int recentRuns, int matchedRuns, int recoveredOffers, int partyUpdates,
		int ambiguous, int unmatched, int alreadyCaptured) {
		if (recentRuns <= 0) return "No recent Croesus API records are available for the selected profile.";
		if (recoveredOffers > 0 || partyUpdates > 0) {
			return "Recovered " + recoveredOffers + " chest offer" + (recoveredOffers == 1 ? "" : "s")
				+ " and refreshed " + partyUpdates + " part" + (partyUpdates == 1 ? "y" : "ies") + "."
				+ suffix(ambiguous, unmatched, alreadyCaptured);
		}
		if (matchedRuns <= 0) {
			if (ambiguous > 0 && unmatched > 0) return "No safe local matches: " + ambiguous + " ambiguous, " + unmatched + " unmatched.";
			if (ambiguous > 0) return "No recovery applied: " + ambiguous + " API run" + (ambiguous == 1 ? " has" : "s have") + " ambiguous local matches.";
			return "No matching local completions for " + unmatched + " recent API run" + (unmatched == 1 ? "." : "s.");
		}
		if (alreadyCaptured > 0) return "Matching API chest data was already captured locally." + suffix(ambiguous, unmatched, 0);
		return "Recent API runs matched, but no recoverable chest contents were exposed." + suffix(ambiguous, unmatched, 0);
	}

	private static String suffix(int ambiguous, int unmatched, int alreadyCaptured) {
		StringBuilder out = new StringBuilder();
		if (alreadyCaptured > 0) out.append(" ").append(alreadyCaptured).append(" chest record")
			.append(alreadyCaptured == 1 ? " was" : "s were").append(" already captured.");
		if (ambiguous > 0) out.append(" ").append(ambiguous).append(" ambiguous.");
		if (unmatched > 0) out.append(" ").append(unmatched).append(" unmatched.");
		return out.toString();
	}
}
