package dev.vy.drt.client.screen;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class CustomLayoutTextTest {
	private static final String LAYOUT = "DRT [{floor}]\nRuns: {runs.session}\n<gradient:#FF5555:#5555FF>Profit</gradient>";

	@Test
	void pastingOverAFullSelectionReplacesEverythingAndKeepsLines() {
		CustomLayoutText.Edit edit = CustomLayoutText.replace(LAYOUT, 0, LAYOUT.length(), "Line one\r\nLine two\rLine three");
		assertEquals("Line one\nLine two\nLine three", edit.text());
		assertEquals(edit.text().length(), edit.caret());
	}

	@Test
	void pastingOverAPartialSelectionReplacesOnlyThatRange() {
		int start = LAYOUT.indexOf("{runs.session}");
		int end = start + "{runs.session}".length();
		CustomLayoutText.Edit edit = CustomLayoutText.replace(LAYOUT, end, start, "{runs.total}");
		assertEquals(LAYOUT.replace("{runs.session}", "{runs.total}"), edit.text(), "selection direction does not matter");
		assertEquals(start + "{runs.total}".length(), edit.caret());
	}

	@Test
	void pastingWithoutASelectionInsertsAtTheCaret() {
		CustomLayoutText.Edit edit = CustomLayoutText.replace("AB", 1, 1, "\n<#55FFFF>x</#>\n");
		assertEquals("A\n<#55FFFF>x</#>\nB", edit.text());
		assertEquals(17, edit.caret());
	}

	@Test
	void deletingASelectionAcrossLinesJoinsThem() {
		int start = LAYOUT.indexOf("]") + 1;
		int end = LAYOUT.indexOf("Runs");
		CustomLayoutText.Edit edit = CustomLayoutText.replace(LAYOUT, start, end, "");
		assertEquals("DRT [{floor}]Runs: {runs.session}\n<gradient:#FF5555:#5555FF>Profit</gradient>", edit.text());
		assertEquals(start, edit.caret());
	}

	@Test
	void controlCharactersAreDroppedButTabsAndNewlinesStay() {
		CustomLayoutText.Edit edit = CustomLayoutText.replace("", 0, 0, "a\u0007b\tc\nd\u0000");
		assertEquals("ab\tc\nd", edit.text());
	}

	@Test
	void pastesAreTrimmedToTheLengthLimitAfterRemovingTheSelection() {
		String full = "x".repeat(CustomLayoutText.MAX_LENGTH);
		assertEquals(full, CustomLayoutText.replace(full, full.length(), full.length(), "more").text(), "a full layout accepts nothing more");

		CustomLayoutText.Edit replaced = CustomLayoutText.replace(full, 0, 10, "y".repeat(50));
		assertEquals(CustomLayoutText.MAX_LENGTH, replaced.text().length());
		assertEquals("y".repeat(10) + "x".repeat(CustomLayoutText.MAX_LENGTH - 10), replaced.text());
		assertEquals(10, replaced.caret());
	}

	@Test
	void outOfRangeSelectionsAreClamped() {
		CustomLayoutText.Edit edit = CustomLayoutText.replace("abc", -5, 99, "z");
		assertEquals("z", edit.text());
		assertEquals(1, edit.caret());
	}
}
