package dev.vy.drt.client.screen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import dev.vy.drt.config.DungeonRunCompletionRecord;
import dev.vy.drt.config.DungeonRunRecord;
import java.util.List;
import org.junit.jupiter.api.Test;

class OverviewDashboardDataTest {
	@Test
	void keepsRunsWithoutChestsAndOnlyAveragesProvenLinks() {
		DungeonRunCompletionRecord linkedRun = completion("run-1", 1_000_000L, "M7", 240_000L);
		DungeonRunCompletionRecord noChestRun = completion("run-2", 2_000_000L, "M7", 300_000L);

		DungeonRunRecord linkedChest = chest(1_000_000L, "M7", 5_000_000L);
		linkedChest.runLinkSource = DungeonRunRecord.LINK_LIVE;
		linkedChest.linkedRunId = "run-1";
		DungeonRunRecord unlinkedChest = chest(1_500_000L, "M7", 2_000_000L);
		unlinkedChest.runLinkSource = DungeonRunRecord.LINK_NONE;

		OverviewDashboardData.Snapshot snapshot = OverviewDashboardData.build(
			List.of(linkedRun, noChestRun),
			List.of(linkedChest, unlinkedChest),
			OverviewDashboardData.Metric.RUN_PROFIT,
			OverviewDashboardData.TimeRange.ALL,
			null,
			3_000_000L
		);

		assertEquals(2, snapshot.totalRuns());
		assertEquals(7_000_000L, snapshot.totalProfit());
		assertEquals(5_000_000L, snapshot.averageLinkedRunProfit());
		assertEquals(1, snapshot.linkedProfitRuns());
		assertEquals(270_000L, snapshot.averageRunTimeMs());
		assertEquals(240_000L, snapshot.bestRunTimeMs());
		assertEquals(300_000L, snapshot.worstRunTimeMs());
		assertEquals(1, snapshot.graphPoints().size());
	}

	@Test
	void unknownTimingStaysUnknown() {
		DungeonRunCompletionRecord run = completion("run-1", 1_000_000L, "F7", 240_000L);
		run.runTimeSource = null;
		run.completionFingerprint = "legacy-with-no-proven-time";

		OverviewDashboardData.Snapshot snapshot = OverviewDashboardData.build(
			List.of(run), List.of(), OverviewDashboardData.Metric.RUN_TIME,
			OverviewDashboardData.TimeRange.ALL, null, 2_000_000L
		);

		assertEquals(1, snapshot.totalRuns());
		assertNull(snapshot.averageRunTimeMs());
		assertNull(snapshot.bestRunTimeMs());
		assertEquals(0, snapshot.graphPoints().size());
	}

	private static DungeonRunCompletionRecord completion(String session, long timestamp, String floor, long duration) {
		DungeonRunCompletionRecord record = new DungeonRunCompletionRecord(timestamp, "DUNGEON", floor, "S+", duration, session, "session|" + floor + "|S+|" + duration);
		record.runTimeSource = DungeonRunCompletionRecord.RUN_TIME_OFFICIAL;
		return record;
	}

	private static DungeonRunRecord chest(long timestamp, String floor, long profit) {
		return new DungeonRunRecord(timestamp, 1, floor, "S+", "Bedrock Chest", 0L, profit, profit, List.of());
	}
}
