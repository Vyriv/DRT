package dev.vy.drt.history;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.vy.drt.config.DungeonPartyMember;
import dev.vy.drt.config.DungeonRunCompletionRecord;
import java.util.List;
import java.util.LinkedHashSet;
import org.junit.jupiter.api.Test;

class RunsDashboardDataTest {
	@Test
	void ordersFloorFiltersByDungeonModeAndNumber() {
		var floors = new LinkedHashSet<>(List.of(
			"K5", "K4", "K3", "K2", "K1",
			"M7", "M6", "M5", "M4", "M3", "M2", "M1",
			"F7", "F6", "F5", "F4", "F3", "F2", "F1"));
		assertEquals(List.of(
			"F1", "F2", "F3", "F4", "F5", "F6", "F7",
			"M1", "M2", "M3", "M4", "M5", "M6", "M7",
			"K1", "K2", "K3", "K4", "K5"),
			RunsDashboardData.orderedFloors(floors));
	}

	@Test
	void ordersFiltersAndKeepsCompletionOnlyRuns() {
		var old = run("F7", 1_700_000_000_000L, 600_000, 300, "S+");
		var recent = run("M7", old.completedAtEpochMillis + 1_000, 0, 0, "?");
		var all = RunsDashboardData.build(List.of(old, recent), List.of(), "", "", RunsDashboardData.Sort.NEWEST);
		assertEquals(recent.completionId, all.entries().getFirst().completion().completionId);
		assertEquals(2, all.metrics().totalRuns());
		assertEquals(1, all.metrics().timedRuns());
		assertTrue(all.entries().stream().noneMatch(RunsDashboardData.Entry::hasPurchase));
		assertEquals(1, RunsDashboardData.build(List.of(old, recent), List.of(), "F7", "", RunsDashboardData.Sort.NEWEST).entries().size());
	}

	@Test
	void teammateAggregationShowsSamplesFastestSPlusAndFloorBreakdownWithoutDoubleCounting() {
		var first = run("F7", 1_700_000_000_000L, 600_000, 305, "S+");
		first.partyMembers = List.of(new DungeonPartyMember("Alice", "a".repeat(32), "Mage", 41),
			new DungeonPartyMember("AliceOld", "a".repeat(32), "Mage", 41));
		var second = run("F7", first.completedAtEpochMillis + 1_000, 540_000, 290, "S");
		second.partyMembers = List.of(new DungeonPartyMember("Alice", "a".repeat(32), "Archer", 42));
		var teammate = RunsDashboardData.build(List.of(first, second), List.of(), "", "", RunsDashboardData.Sort.NEWEST).teammates().getFirst();
		assertEquals(2, teammate.runsTogether());
		assertEquals(540_000, teammate.fastestDurationMs());
		assertEquals(1, teammate.sPlusRuns());
		assertEquals(50D, teammate.sPlusPercent());
		assertEquals(2, teammate.floors().get("F7").runs());
	}

	@Test
	void lootNavigationKeyUsesPersistedCompletionIdentity() {
		var completion = run("F7", 1_700_000_000_000L, 600_000, 300, "S+");
		assertEquals("completion:" + completion.completionId, LootHistoryProjection.completionKey(completion));
	}

	@Test
	void malformedNumericPartyArtifactIsNotPresentedAsATeammate() {
		var completion = run("M1", 1_700_000_000_000L, 600_000, 300, "S+");
		completion.partyMembers = List.of(new DungeonPartyMember("41", "", "", 0));
		assertTrue(RunsDashboardData.build(List.of(completion), List.of(), "", "", RunsDashboardData.Sort.NEWEST)
			.teammates().isEmpty());
	}

	@Test
	void ownAccountIsExcludedFromTeammates() {
		var completion = run("F7", 1_700_000_000_000L, 600_000, 300, "S+");
		completion.partyMembers = List.of(new DungeonPartyMember("Me", "a".repeat(32), "Mage", 40),
			new DungeonPartyMember("Friend", "b".repeat(32), "Tank", 35));
		var teammates = RunsDashboardData.build(List.of(completion), List.of(), "", "", RunsDashboardData.Sort.NEWEST,
			"a".repeat(32), "Me").teammates();
		assertEquals(1, teammates.size());
		assertEquals("Friend", teammates.getFirst().name());
	}

	private static DungeonRunCompletionRecord run(String floor, long at, long time, int score, String grade) {
		var run = new DungeonRunCompletionRecord(at, floor.startsWith("M") ? "MASTER" : "CATACOMBS", floor, grade, time,
			"session-" + at, "fingerprint-" + at);
		run.completionId = "completion-" + at;
		run.score = score;
		run.runTimeSource = time > 0 ? DungeonRunCompletionRecord.RUN_TIME_OFFICIAL : null;
		return run;
	}
}
