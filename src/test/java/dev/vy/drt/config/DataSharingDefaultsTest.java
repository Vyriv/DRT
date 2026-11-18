package dev.vy.drt.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.Gson;
import dev.vy.drt.sync.StatsUploadPlanner;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Statistics Sharing is on by default and a stored choice wins; activity pings have no switch at all. */
class DataSharingDefaultsTest {
	private static final String ME = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa";
	private static final long NOW = 1_800_000_000_000L;
	private static final Gson GSON = new Gson();

	private static DungeonRunCompletionRecord run(String id, long at) {
		DungeonRunCompletionRecord record = new DungeonRunCompletionRecord(at, "DUNGEON", "M5", "S+", 215_000L, "session-" + id, "session-" + id + "|M5|S+|215000");
		record.completionId = id;
		record.playerUuid = ME;
		return record;
	}

	private static List<StatsUploadPlanner.Upload> pending(DrtConfig config, List<DungeonRunCompletionRecord> runs) {
		if (!config.statsSyncEnabled) return List.of();
		StatsUploadPlanner.Scope scope = new StatsUploadPlanner.Scope(ME, config.statsSyncSinceEpochMillis, false, false);
		return StatsUploadPlanner.pending(scope, runs, List.of(), Map.of());
	}

	@Test
	void newInstallationsShareFromTheFirstLoad() {
		DrtConfig config = new DrtConfig();
		assertTrue(config.statsSyncEnabled);
		assertTrue(config.startStatsSyncBoundary(NOW));
		assertEquals(NOW, config.statsSyncSinceEpochMillis);
		assertFalse(config.startStatsSyncBoundary(NOW + 60_000L), "the boundary is set once");
		assertEquals(List.of("completion-new"), pending(config, List.of(run("completion-new", NOW + 10))).stream().map(StatsUploadPlanner.Upload::id).toList());
	}

	@Test
	void upgradesHoldEarlierDrtHistoryUntilThePlayerImportsIt() {
		DrtConfig config = GSON.fromJson("{\"trackingEnabled\":true,\"runHistory\":[],\"runCompletions\":[]}", DrtConfig.class);
		assertTrue(config.statsSyncEnabled, "a missing key gets the on default");
		assertTrue(config.startStatsSyncBoundary(NOW));
		List<StatsUploadPlanner.Upload> uploads = pending(config, List.of(run("completion-before-upgrade", NOW - 86_400_000L), run("completion-after-upgrade", NOW + 10)));
		assertEquals(List.of("completion-after-upgrade"), uploads.stream().map(StatsUploadPlanner.Upload::id).toList());
		assertFalse(uploads.get(0).historical());
		List<StatsUploadPlanner.Upload> imported = StatsUploadPlanner.pending(
			new StatsUploadPlanner.Scope(ME, config.statsSyncSinceEpochMillis, false, true),
			List.of(run("completion-before-upgrade", NOW - 86_400_000L), run("completion-after-upgrade", NOW + 10)), List.of(), Map.of());
		assertEquals(List.of("completion-after-upgrade", "completion-before-upgrade"), imported.stream().map(StatsUploadPlanner.Upload::id).toList());
		assertTrue(imported.get(1).historical());
	}

	@Test
	void anExplicitlyDisabledSettingStaysOffAcrossRestarts() {
		DrtConfig config = GSON.fromJson("{\"statsSyncEnabled\":false,\"statsSyncSinceEpochMillis\":0}", DrtConfig.class);
		assertFalse(config.statsSyncEnabled);
		assertFalse(config.startStatsSyncBoundary(NOW));
		assertTrue(pending(config, List.of(run("completion-new", NOW + 10))).isEmpty());

		DrtConfig reloaded = GSON.fromJson(GSON.toJson(config), DrtConfig.class);
		assertFalse(reloaded.statsSyncEnabled, "a saved OFF survives a save and reload");

		DrtConfig afterDelete = GSON.fromJson("{\"statsSyncEnabled\":false,\"statsSyncSinceEpochMillis\":" + (NOW - 5_000L) + "}", DrtConfig.class);
		assertFalse(afterDelete.startStatsSyncBoundary(NOW), "turning sharing off, including through a deletion, is never undone by the default");
		assertFalse(afterDelete.statsSyncEnabled);
	}

	@Test
	void anExistingSyncBoundaryIsKept() {
		DrtConfig config = GSON.fromJson("{\"statsSyncEnabled\":true,\"statsSyncSinceEpochMillis\":" + (NOW - 5_000L) + "}", DrtConfig.class);
		assertFalse(config.startStatsSyncBoundary(NOW));
		assertEquals(NOW - 5_000L, config.statsSyncSinceEpochMillis);
	}

	@Test
	void activityTrackingHasNoSettingToTurnItOff() {
		DrtConfig config = GSON.fromJson("{\"activityPingsEnabled\":false,\"activityNoticeShown\":true}", DrtConfig.class);
		assertThrows(NoSuchFieldException.class, () -> DrtConfig.class.getField("activityPingsEnabled"));
		assertFalse(GSON.toJson(config).contains("activityPingsEnabled"), "an old OFF value is dropped on the next save");
	}

	@Test
	void theRemovedSharingNoticeFlagIsDroppedOnTheNextSave() {
		DrtConfig config = GSON.fromJson("{\"dataSharingNoticeShown\":true,\"statsSyncEnabled\":false}", DrtConfig.class);
		assertThrows(NoSuchFieldException.class, () -> DrtConfig.class.getField("dataSharingNoticeShown"));
		assertFalse(GSON.toJson(config).contains("dataSharingNoticeShown"));
		assertFalse(config.statsSyncEnabled, "the stored sharing choice is unaffected");
	}
}
