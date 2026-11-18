package dev.vy.drt.history;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class CroesusRecoveryFeedbackTest {
	@Test
	void distinguishesEveryRecoveryOutcome() {
		assertTrue(message(0, 0, 0, 0, 0, 0, 0).startsWith("No recent"));
		assertTrue(message(2, 0, 0, 0, 0, 2, 0).startsWith("No matching"));
		assertTrue(message(1, 0, 0, 0, 1, 0, 0).contains("ambiguous"));
		assertTrue(message(1, 1, 0, 0, 0, 0, 1).contains("already captured"));
		assertTrue(message(1, 1, 1, 1, 0, 0, 0).startsWith("Recovered"));
		assertTrue(message(1, 1, 0, 0, 0, 0, 0).contains("no recoverable chest contents"));
	}

	private static String message(int recent, int matched, int offers, int parties, int ambiguous, int unmatched, int captured) {
		return CroesusRecoveryFeedback.summarize(recent, matched, offers, parties, ambiguous, unmatched, captured);
	}
}
