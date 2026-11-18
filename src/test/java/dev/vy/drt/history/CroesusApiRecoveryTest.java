package dev.vy.drt.history;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.vy.drt.api.DungeonPlayerStats;
import dev.vy.drt.config.DungeonChestOffer;
import dev.vy.drt.config.DungeonRunCompletionRecord;
import java.util.List;
import org.junit.jupiter.api.Test;

class CroesusApiRecoveryTest {
	@Test
	void uniquelyMatchesExistingCompletionAndNeverCreatesRuns() {
		DungeonRunCompletionRecord completion = run("F7", 1_700_000_000_000L, "local");
		var api = apiRun("F7", completion.completedAtEpochMillis + 2_000L, "r1");
		var plan = CroesusApiRecovery.plan(List.of(completion), List.of(api));
		assertEquals(1, plan.matches().size());
		assertEquals("local", plan.matches().getFirst().completion().runSessionId);
		assertEquals(DungeonChestOffer.SOURCE_API_RECOVERED, plan.matches().getFirst().newOffers().getFirst().captureSource);
		completion.chestOffers.addAll(plan.matches().getFirst().newOffers());
		var projected = LootHistoryProjection.projectRuns(List.of(), List.of(completion)).getFirst();
		assertTrue(projected.chestOptions().stream().noneMatch(option -> option.record().hasKnownFinancials()));
	}

	@Test
	void ambiguousOrDistantRunsAreNotAttached() {
		long time = 1_700_000_000_000L;
		var ambiguous = CroesusApiRecovery.plan(List.of(run("F7", time, "a"), run("F7", time + 10_000, "b")),
			List.of(apiRun("F7", time + 5_000, "r")));
		assertEquals(1, ambiguous.ambiguous());
		assertTrue(ambiguous.matches().isEmpty());
		var distant = CroesusApiRecovery.plan(List.of(run("F7", time, "a")), List.of(apiRun("F7", time + 100_000, "r")));
		assertEquals(1, distant.unmatched());
	}

	@Test
	void locallyCapturedChestIsNeverOverwritten() {
		DungeonRunCompletionRecord completion = run("F7", 1_700_000_000_000L, "a");
		DungeonChestOffer local = new DungeonChestOffer();
		local.chestTitle = "Bedrock Chest";
		completion.chestOffers.add(local);
		var plan = CroesusApiRecovery.plan(List.of(completion), List.of(apiRun("F7", completion.completedAtEpochMillis, "r")));
		assertTrue(plan.matches().getFirst().newOffers().isEmpty());
		assertEquals(1, plan.alreadyCaptured());
	}

	@Test
	void classFormattedApiParticipantNeverBecomesNumericPlayerName() {
		DungeonRunCompletionRecord completion = run("M1", 1_700_000_000_000L, "a");
		var api = new DungeonPlayerStats.RecentRun("r", "M1", completion.completedAtEpochMillis,
			List.of("catgirlVivid: Mage (41)"), List.of());
		var party = CroesusApiRecovery.plan(List.of(completion), List.of(api)).matches().getFirst().party();
		assertEquals(1, party.size());
		assertEquals("catgirlVivid", party.getFirst().name);
		assertEquals("Mage", party.getFirst().dungeonClass);
		assertEquals(41, party.getFirst().classLevel);
	}

	@Test
	void realHypixelParticipantsKeepClassesAndLevels() {
		DungeonRunCompletionRecord completion = run("M5", 1_786_548_640_000L, "m5");
		var api = new DungeonPlayerStats.RecentRun("r", "M5", 1_786_548_640_599L, List.of(
			"§bJJGPld§f: §eTank§b (§e37§b)", "§asurume3600§f: §eBerserk§b (§e38§b)", "§bcatgirllivid§f: §eMage§b (§e39§b)",
			"§bFranzHelmutReis§f: §eHealer§b (§e41§b)", "§bB2Bhandles§f: §eArcher§b (§e49§b)"), List.of());
		var party = CroesusApiRecovery.plan(List.of(completion), List.of(api)).matches().getFirst().party();
		assertEquals(5, party.size());
		assertEquals(List.of("Tank", "Berserk", "Mage", "Healer", "Archer"), party.stream().map(member -> member.dungeonClass).toList());
		assertEquals(List.of(37, 38, 39, 41, 49), party.stream().map(member -> member.classLevel).toList());
	}

	@Test
	void unformattedParticipantsStillKeepTheirName() {
		DungeonRunCompletionRecord completion = run("F7", 1_700_000_000_000L, "f7");
		var party = CroesusApiRecovery.plan(List.of(completion),
			List.of(apiRun("F7", completion.completedAtEpochMillis, "r"))).matches().getFirst().party();
		assertEquals("Player", party.getFirst().name);
		assertEquals("", party.getFirst().dungeonClass);
	}

	private static DungeonRunCompletionRecord run(String floor, long at, String session) {
		return new DungeonRunCompletionRecord(at, "CATACOMBS", floor, "S+", 600_000, session, session);
	}

	private static DungeonPlayerStats.RecentRun apiRun(String floor, long at, String id) {
		return new DungeonPlayerStats.RecentRun(id, floor, at, List.of("[MVP+] Player"),
			List.of(new DungeonPlayerStats.Chest("bedrock", true, 0, List.of("recombobulator_3000"))));
	}
}
