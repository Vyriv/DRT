package dev.vy.drt.client.cosmetics;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

final class NameStyleMatcher {
	private NameStyleMatcher() {
	}

	static Index compile(List<PlayerCustomizationRegistry.NameCandidate> candidates) {
		if (candidates == null || candidates.isEmpty()) return Index.EMPTY;

		List<BuilderNode> builders = new ArrayList<>();
		builders.add(new BuilderNode());
		for (int candidateIndex = 0; candidateIndex < candidates.size(); candidateIndex++) {
			PlayerCustomizationRegistry.NameCandidate candidate = candidates.get(candidateIndex);
			String value = candidate.value();
			if (value == null || value.isEmpty()) continue;

			int nodeIndex = 0;
			for (int offset = 0; offset < value.length(); offset++) {
				char character = foldCase(value.charAt(offset));
				BuilderNode node = builders.get(nodeIndex);
				Integer childIndex = node.children.get(character);
				if (childIndex == null) {
					childIndex = builders.size();
					node.children.put(character, childIndex);
					builders.add(new BuilderNode());
				}
				nodeIndex = childIndex;
			}
			builders.get(nodeIndex).candidateIndexes.add(candidateIndex);
		}

		Node[] nodes = new Node[builders.size()];
		for (int index = 0; index < builders.size(); index++) {
			BuilderNode builder = builders.get(index);
			List<Map.Entry<Character, Integer>> children = new ArrayList<>(builder.children.entrySet());
			children.sort(Map.Entry.comparingByKey());
			char[] childCharacters = new char[children.size()];
			int[] childIndexes = new int[children.size()];
			for (int child = 0; child < children.size(); child++) {
				childCharacters[child] = children.get(child).getKey();
				childIndexes[child] = children.get(child).getValue();
			}
			nodes[index] = new Node(childCharacters, childIndexes, builder.candidateIndexes.stream().mapToInt(Integer::intValue).toArray());
		}
		return new Index(candidates.toArray(PlayerCustomizationRegistry.NameCandidate[]::new), nodes);
	}

	static boolean containsCandidate(String text, Index index) {
		return text != null && !text.isEmpty() && !index.isEmpty() && findFirstNameMatch(text, index, 0) != null;
	}

	static MatchedCustomization findFirstNameMatch(String text, Index index, int startIndex) {
		if (text == null || text.isEmpty() || index == null || index.isEmpty()) return null;

		int firstStart = Math.max(0, startIndex);
		for (int matchStart = firstStart; matchStart < text.length(); matchStart++) {
			int nodeIndex = 0;
			PlayerCustomizationRegistry.NameCandidate bestCandidate = null;
			for (int cursor = matchStart; cursor < text.length(); cursor++) {
				nodeIndex = index.nodes[nodeIndex].child(foldCase(text.charAt(cursor)));
				if (nodeIndex < 0) break;

				Node node = index.nodes[nodeIndex];
				for (int candidateIndex : node.candidateIndexes) {
					PlayerCustomizationRegistry.NameCandidate candidate = index.candidates[candidateIndex];
					int end = matchStart + candidate.value().length();
					if (candidate.requiresBoundary() && !isNameBoundary(text, matchStart, end)) continue;
					if (bestCandidate == null || candidate.value().length() > bestCandidate.value().length()) {
						bestCandidate = candidate;
					}
				}
			}

			if (bestCandidate != null) {
				return new MatchedCustomization(matchStart, bestCandidate.value(), bestCandidate.customization());
			}
		}
		return null;
	}

	private static char foldCase(char character) {
		return Character.toLowerCase(Character.toUpperCase(character));
	}

	private static boolean isNameBoundary(String text, int start, int endExclusive) {
		return isNameBoundaryCharacter(start > 0 ? text.charAt(start - 1) : null)
			&& isNameBoundaryCharacter(endExclusive < text.length() ? text.charAt(endExclusive) : null);
	}

	private static boolean isNameBoundaryCharacter(Character character) {
		return character == null || (!Character.isLetterOrDigit(character) && character != '_');
	}

	static final class Index {
		private static final Index EMPTY = new Index(new PlayerCustomizationRegistry.NameCandidate[0], new Node[]{Node.EMPTY});

		private final PlayerCustomizationRegistry.NameCandidate[] candidates;
		private final Node[] nodes;

		private Index(PlayerCustomizationRegistry.NameCandidate[] candidates, Node[] nodes) {
			this.candidates = candidates;
			this.nodes = nodes;
		}

		boolean isEmpty() {
			return candidates.length == 0;
		}
	}

	private static final class BuilderNode {
		private final Map<Character, Integer> children = new HashMap<>();
		private final List<Integer> candidateIndexes = new ArrayList<>();
	}

	private record Node(char[] childCharacters, int[] childIndexes, int[] candidateIndexes) {
		private static final Node EMPTY = new Node(new char[0], new int[0], new int[0]);

		int child(char character) {
			int low = 0;
			int high = childCharacters.length - 1;
			while (low <= high) {
				int middle = (low + high) >>> 1;
				char candidate = childCharacters[middle];
				if (candidate < character) {
					low = middle + 1;
				} else if (candidate > character) {
					high = middle - 1;
				} else {
					return childIndexes[middle];
				}
			}
			return -1;
		}
	}

	record MatchedCustomization(int index, String matchedName, PlayerCustomizationRegistry.PlayerCustomization customization) {
	}
}
