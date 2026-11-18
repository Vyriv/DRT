package dev.vy.drt.history;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.vy.drt.config.DungeonPartyMember;
import dev.vy.drt.config.DungeonRunCompletionRecord;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.Test;

class CroesusPartyRecoveryTest {
	private static final ZoneId UTC = ZoneId.of("UTC");
	private static final long NOW = 1_800_000_000_000L;

	@Test
	void kuudraEntriesResolveByTierAndCompletionTimeOnly() {
		DungeonRunCompletionRecord basic = kuudra("k1-run", NOW - 125_000L, "K1", 312_239L);
		DungeonRunCompletionRecord infernal = kuudra("k5-run", NOW - 125_000L, "K5", 136_578L);
		var evidence = CroesusRunSelection.parse("Kuudra - Basic Tier",
			List.of("Players:", "KanKraft: Routeless", "Completed: 2 minutes ago", "Time: 05m 10s"), NOW, UTC);

		assertEquals("K1", evidence.floor());
		var match = CroesusRunSelection.resolve(evidence, List.of(infernal, basic));
		assertEquals(CroesusRunSelection.Status.MATCHED, match.status(), "a measured duration that differs does not block Kuudra");
		assertEquals("k1-run", match.runSessionId());
	}

	@Test
	void kuudraWithoutACompletionTimeIsNeverGuessed() {
		var evidence = CroesusRunSelection.parse("Kuudra - Infernal Tier", List.of("Players:", "KanKraft: Routeless"), NOW, UTC);
		assertEquals("K5", evidence.floor());
		assertEquals(CroesusRunSelection.Status.INSUFFICIENT_EVIDENCE,
			CroesusRunSelection.resolve(evidence, List.of(kuudra("k5", NOW - 60_000L, "K5", 1L))).status());
	}

	@Test
	void visibleEntriesEnrichOnlyTheirOwnRuns() {
		DungeonRunCompletionRecord m7 = dungeon("m7-run", NOW - 4L * 60_000L - 30_000L, "M7", "S+", 302);
		DungeonRunCompletionRecord k1 = kuudra("k1-run", NOW - 2L * 60_000L - 30_000L, "K1", 312_239L);
		List<String> m7Lore = List.of("Players:", "beganjava: Berserk (46)", "Wzxh: Tank (50)", "Score: 302 (S+)", "Completed: 4 minutes ago");
		List<String> k1Lore = List.of("Players:", "KanKraft: Routeless", "catgirlVivid: Specialist", "Completed: 2 minutes ago");
		var plan = CroesusPartyRecovery.plan(List.of(
			entry("Master Mode Catacombs - Floor VII", m7Lore),
			entry("Kuudra - Basic Tier", k1Lore)
		), List.of(m7, k1));

		assertEquals(2, plan.size());
		assertEquals("Berserk", plan.get("m7-run").getFirst().dungeonClass);
		assertEquals("Specialist", plan.get("k1-run").get(1).kuudraRole);
		assertEquals("", plan.get("k1-run").get(1).dungeonClass);
	}

	@Test
	void ambiguousOrDuplicatedMatchesAreDropped() {
		DungeonRunCompletionRecord first = dungeon("a", NOW - 2L * 3_600_000L - 60_000L, "F7", "S+", 0);
		DungeonRunCompletionRecord second = dungeon("b", NOW - 2L * 3_600_000L - 120_000L, "F7", "S+", 0);
		List<String> lore = List.of("Players:", "PlayerOne: Mage (41)", "Completed: 2 hours ago");
		assertTrue(CroesusPartyRecovery.plan(List.of(entry("The Catacombs - Floor VII", lore)), List.of(first, second)).isEmpty(),
			"two runs fit the same entry");

		DungeonRunCompletionRecord only = dungeon("only", NOW - 2L * 60_000L - 30_000L, "F7", "S+", 0);
		List<String> sameEntry = List.of("Players:", "PlayerOne: Mage (41)", "Completed: 2 minutes ago");
		List<String> otherParty = List.of("Players:", "PlayerTwo: Tank (30)", "Completed: 2 minutes ago");
		assertTrue(CroesusPartyRecovery.plan(List.of(
			entry("The Catacombs - Floor VII", sameEntry), entry("The Catacombs - Floor VII", otherParty)), List.of(only)).isEmpty(),
			"two entries claiming one run attach to neither");
	}

	@Test
	void entriesWithoutAPartyAreIgnored() {
		DungeonRunCompletionRecord run = dungeon("run", NOW - 2L * 60_000L - 30_000L, "F7", "S", 0);
		var evidence = CroesusRunSelection.parse("The Catacombs - Floor VII", List.of("Completed: 2 minutes ago"), NOW, UTC);
		assertTrue(CroesusPartyRecovery.plan(List.of(new CroesusPartyRecovery.Entry(evidence, List.<DungeonPartyMember>of())), List.of(run)).isEmpty());
	}

	private static CroesusPartyRecovery.Entry entry(String name, List<String> lore) {
		return new CroesusPartyRecovery.Entry(CroesusRunSelection.parse(name, lore, NOW, UTC), CroesusPartyParser.parse(lore));
	}

	private static DungeonRunCompletionRecord dungeon(String session, long at, String floor, String grade, int score) {
		DungeonRunCompletionRecord record = new DungeonRunCompletionRecord(at, "DUNGEON", floor, grade, 0L, session, session);
		record.score = score;
		return record;
	}

	private static DungeonRunCompletionRecord kuudra(String session, long at, String tier, long runTimeMs) {
		return new DungeonRunCompletionRecord(at, "KUUDRA", tier, "100%", runTimeMs, session, session);
	}
}
