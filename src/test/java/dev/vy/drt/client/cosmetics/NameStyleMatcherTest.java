package dev.vy.drt.client.cosmetics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.Test;

class NameStyleMatcherTest {
	@Test
	void handlesVeryLongRepeatedTextWithoutAMatch() {
		NameStyleMatcher.Index index = index("Vyriv", "DungeonRunner", "CosmeticOwner");
		assertNull(NameStyleMatcher.findFirstNameMatch("meow".repeat(50_000), index, 0));
	}

	@Test
	void findsALegitimateNameNearTheEndOfLongText() {
		NameStyleMatcher.Index index = index("Vyriv", "DungeonRunner");
		String text = "meow".repeat(50_000) + " VYRIV";
		NameStyleMatcher.MatchedCustomization match = NameStyleMatcher.findFirstNameMatch(text, index, 0);
		assertEquals(text.length() - 5, match.index());
		assertEquals("Vyriv", match.matchedName());
	}

	@Test
	void enumeratesSeveralNamesWithoutMissingOverlaps() {
		NameStyleMatcher.Index index = index("Ann", "Anna", "Vyriv");
		String text = "Anna met ann and VYRIV";
		NameStyleMatcher.MatchedCustomization first = NameStyleMatcher.findFirstNameMatch(text, index, 0);
		NameStyleMatcher.MatchedCustomization second = NameStyleMatcher.findFirstNameMatch(text, index,
			first.index() + first.matchedName().length());
		NameStyleMatcher.MatchedCustomization third = NameStyleMatcher.findFirstNameMatch(text, index,
			second.index() + second.matchedName().length());
		assertEquals("Anna", first.matchedName());
		assertEquals("Ann", second.matchedName());
		assertEquals("Vyriv", third.matchedName());
	}

	@Test
	void matchesCaseInsensitively() {
		NameStyleMatcher.MatchedCustomization match = NameStyleMatcher.findFirstNameMatch("hello vYrIv", index("Vyriv"), 0);
		assertEquals(6, match.index());
	}

	@Test
	void choosesEarliestThenLongestMatch() {
		PlayerCustomizationRegistry.NameCandidate shortName = CosmeticsTestFixtures.candidate("Ann", false);
		PlayerCustomizationRegistry.NameCandidate longName = CosmeticsTestFixtures.candidate("Anna", false);
		NameStyleMatcher.Index index = NameStyleMatcher.compile(List.of(shortName, longName));
		NameStyleMatcher.MatchedCustomization match = NameStyleMatcher.findFirstNameMatch("x Anna Ann", index, 0);
		assertEquals(2, match.index());
		assertEquals("Anna", match.matchedName());
		assertSame(longName.customization(), match.customization());
	}

	@Test
	void enforcesBoundariesAndContinuesAfterAnInvalidOccurrence() {
		PlayerCustomizationRegistry.NameCandidate candidate = CosmeticsTestFixtures.candidate("Runner", true);
		NameStyleMatcher.Index index = NameStyleMatcher.compile(List.of(candidate));
		assertEquals(9, NameStyleMatcher.findFirstNameMatch("xRunner_ Runner!", index, 0).index());
		assertNull(NameStyleMatcher.findFirstNameMatch("xRunner_", index, 0));
	}

	@Test
	void agreesWithThePreviousMatcherAcrossRandomizedInputs() {
		List<PlayerCustomizationRegistry.NameCandidate> candidates = List.of(
			CosmeticsTestFixtures.candidate("Ann", false),
			CosmeticsTestFixtures.candidate("Anna", false),
			CosmeticsTestFixtures.candidate("Runner", true),
			CosmeticsTestFixtures.candidate("Vyriv", false),
			CosmeticsTestFixtures.candidate("MEOWS", true));
		NameStyleMatcher.Index index = NameStyleMatcher.compile(candidates);
		Random random = new Random(0xD47C05L);
		String alphabet = " aAnNrRuUnNeErRvVyYiImMeEoOwWsS_!";
		for (int iteration = 0; iteration < 2_000; iteration++) {
			int length = random.nextInt(100);
			StringBuilder text = new StringBuilder(length);
			for (int character = 0; character < length; character++) {
				text.append(alphabet.charAt(random.nextInt(alphabet.length())));
			}
			int start = length == 0 ? 0 : random.nextInt(length + 1);
			NameStyleMatcher.MatchedCustomization expected = referenceMatch(text.toString(), candidates, start);
			NameStyleMatcher.MatchedCustomization actual = NameStyleMatcher.findFirstNameMatch(text.toString(), index, start);
			assertEquals(expected == null ? null : expected.index(), actual == null ? null : actual.index());
			assertEquals(expected == null ? null : expected.matchedName(), actual == null ? null : actual.matchedName());
		}
	}

	private static NameStyleMatcher.MatchedCustomization referenceMatch(String text,
	                                                                    List<PlayerCustomizationRegistry.NameCandidate> candidates,
	                                                                    int startIndex) {
		int bestIndex = Integer.MAX_VALUE;
		PlayerCustomizationRegistry.NameCandidate best = null;
		for (PlayerCustomizationRegistry.NameCandidate candidate : candidates) {
			int search = startIndex;
			while (search < text.length()) {
				int index = -1;
				for (int offset = Math.max(0, search); offset <= text.length() - candidate.value().length(); offset++) {
					if (text.regionMatches(true, offset, candidate.value(), 0, candidate.value().length())) {
						index = offset;
						break;
					}
				}
				if (index == -1 || index > bestIndex) break;
				int end = index + candidate.value().length();
				boolean boundary = !candidate.requiresBoundary()
					|| (boundary(text, index - 1) && boundary(text, end));
				if (boundary) {
					if (index < bestIndex || (index == bestIndex && (best == null || candidate.value().length() > best.value().length()))) {
						bestIndex = index;
						best = candidate;
					}
					break;
				}
				search = index + 1;
			}
		}
		return best == null ? null : new NameStyleMatcher.MatchedCustomization(bestIndex, best.value(), best.customization());
	}

	private static boolean boundary(String text, int index) {
		return index < 0 || index >= text.length()
			|| (!Character.isLetterOrDigit(text.charAt(index)) && text.charAt(index) != '_');
	}

	private static NameStyleMatcher.Index index(String... names) {
		return NameStyleMatcher.compile(List.of(names).stream()
			.map(name -> CosmeticsTestFixtures.candidate(name, false))
			.toList());
	}
}
