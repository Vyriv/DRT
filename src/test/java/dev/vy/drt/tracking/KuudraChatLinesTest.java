package dev.vy.drt.tracking;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Locale;
import org.junit.jupiter.api.Test;

class KuudraChatLinesTest {
	private static String cleaned(String line) {
		return line.trim().toUpperCase(Locale.ROOT);
	}

	@Test
	void onlyTheHollowEntryLineStartsARun() {
		assertTrue(KuudraChatLines.isEntry(cleaned("[MVP+] IlikeGoths entered Kuudra's Hollow, Infernal Tier!")));
		assertTrue(KuudraChatLines.isEntry(cleaned("[MVP++] SoulJuq entered Kuudra's Hollow, Fiery Tier!")));
	}

	@Test
	void theRewardLineBeforeKuudraDownDoesNotRestartTheTimer() {
		assertFalse(KuudraChatLines.isEntry(cleaned("You gained 50 reputation for killing this tier of Kuudra!")),
			"K5 on 2026-10-14 and K4 on 2026-10-15 were saved as 1 ms and 2 ms runs because of this line");
		assertTrue(KuudraChatLines.isCompletion(cleaned("                               KUUDRA DOWN!")));
	}

	@Test
	void chatAboutKuudraIsNotAnEntry() {
		assertFalse(KuudraChatLines.isEntry(cleaned("Guild > D [MVP+] LFMoney [Meow]: anyone kuudra basic tier 1 run?")));
		assertFalse(KuudraChatLines.isEntry(cleaned("[174] D [VIP] Tomm_1: wtb Tier 5 kuudra carry /p me")));
		assertFalse(KuudraChatLines.isEntry(cleaned("Party > [MVP+] Someone: entered kuudra's hollow, infernal tier!")));
		assertFalse(KuudraChatLines.isEntry(cleaned("You bought Infernal Kuudra Key!")));
		assertFalse(KuudraChatLines.isEntry(cleaned("SoulJuq destroyed one of Kuudra's pods!")));
		assertFalse(KuudraChatLines.isEntry(null));
	}

	@Test
	void midRunLinesAreNotCompletions() {
		assertFalse(KuudraChatLines.isCompletion(cleaned("OrangeJosh was FINAL KILLED by Kuudra!")));
		assertFalse(KuudraChatLines.isCompletion(cleaned("You gained 50 reputation for killing this tier of Kuudra!")));
		assertFalse(KuudraChatLines.isCompletion(null));
	}
}
