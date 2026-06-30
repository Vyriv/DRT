package dev.vy.drt.client.cosmetics;

import java.util.List;

final class NameStyleMatcher {
	private NameStyleMatcher() {
	}

	static boolean containsCandidate(String text, List<PlayerCustomizationRegistry.NameCandidate> candidates) {
		return text != null && !text.isEmpty() && !candidates.isEmpty() && findFirstNameMatch(text, candidates, 0) != null;
	}

	static MatchedCustomization findFirstNameMatch(String text, List<PlayerCustomizationRegistry.NameCandidate> candidates, int startIndex) {
		int bestIndex = Integer.MAX_VALUE;
		PlayerCustomizationRegistry.NameCandidate bestCandidate = null;

		for (PlayerCustomizationRegistry.NameCandidate candidate : candidates) {
			int searchIndex = startIndex;
			while (searchIndex < text.length()) {
				int candidateIndex = indexOfIgnoreCase(text, candidate.value(), searchIndex);
				if (candidateIndex == -1) break;
				if (candidateIndex > bestIndex) break;

				if (!candidate.requiresBoundary() || isNameBoundary(text, candidateIndex, candidateIndex + candidate.value().length())) {
					if (candidateIndex < bestIndex
						|| (candidateIndex == bestIndex && (bestCandidate == null || candidate.value().length() > bestCandidate.value().length()))) {
						bestIndex = candidateIndex;
						bestCandidate = candidate;
					}
					break;
				}

				searchIndex = candidateIndex + 1;
			}
		}

		if (bestCandidate == null) return null;
		return new MatchedCustomization(bestIndex, bestCandidate.value(), bestCandidate.customization());
	}

	private static int indexOfIgnoreCase(String text, String target, int fromIndex) {
		if (target == null || target.isEmpty()) return -1;
		int max = text.length() - target.length();
		for (int i = Math.max(0, fromIndex); i <= max; i++) {
			if (text.regionMatches(true, i, target, 0, target.length())) return i;
		}
		return -1;
	}

	private static boolean isNameBoundary(String text, int start, int endExclusive) {
		return isNameBoundaryCharacter(start > 0 ? text.charAt(start - 1) : null)
			&& isNameBoundaryCharacter(endExclusive < text.length() ? text.charAt(endExclusive) : null);
	}

	private static boolean isNameBoundaryCharacter(Character character) {
		return character == null || (!Character.isLetterOrDigit(character) && character != '_');
	}

	record MatchedCustomization(int index, String matchedName, PlayerCustomizationRegistry.PlayerCustomization customization) {
	}
}
