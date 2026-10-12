package dev.vy.drt.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class HistoryBackupsTest {
	private static final long NOW = 1_800_000_000_000L;
	private static final long HOUR = 60L * 60L * 1000L;

	@TempDir
	Path dir;

	@Test
	void rollingBackupIsTakenAtMostOncePerInterval() throws Exception {
		Path primary = writePrimary("{\"runHistory\":[1]}");
		Path backups = dir.resolve("backups");
		long nextDue = HistoryBackups.rollIfDue(backups, primary, NOW);
		assertEquals(NOW + HistoryBackups.ROLLING_INTERVAL_MILLIS, nextDue);
		assertEquals(1, HistoryBackups.list(backups, HistoryBackups.ROLLING_PREFIX).size());

		HistoryBackups.rollIfDue(backups, primary, NOW + HOUR);
		HistoryBackups.rollIfDue(backups, primary, NOW + 5L * HOUR);
		assertEquals(1, HistoryBackups.list(backups, HistoryBackups.ROLLING_PREFIX).size(), "saves inside the interval add no backup");

		HistoryBackups.rollIfDue(backups, primary, NOW + HistoryBackups.ROLLING_INTERVAL_MILLIS);
		List<Path> rolling = HistoryBackups.list(backups, HistoryBackups.ROLLING_PREFIX);
		assertEquals(2, rolling.size());
		assertEquals("{\"runHistory\":[1]}", Files.readString(rolling.get(0), StandardCharsets.UTF_8));
	}

	@Test
	void rollingBackupsAreBoundedAndNeverPruneBeforeClearBackups() throws Exception {
		Path primary = writePrimary("{}");
		Path backups = dir.resolve("backups");
		Path beforeClear = HistoryBackups.writeBeforeClear(backups, "all", "{\"runHistory\":[1,2,3]}", NOW);
		for (int i = 0; i < HistoryBackups.ROLLING_KEEP + 5; i++) {
			HistoryBackups.rollIfDue(backups, primary, NOW + i * HistoryBackups.ROLLING_INTERVAL_MILLIS);
		}
		List<Path> rolling = HistoryBackups.list(backups, HistoryBackups.ROLLING_PREFIX);
		assertEquals(HistoryBackups.ROLLING_KEEP, rolling.size());
		assertTrue(Files.getLastModifiedTime(rolling.get(0)).toMillis() > Files.getLastModifiedTime(rolling.get(rolling.size() - 1)).toMillis(),
			"the newest backups are the ones kept");
		assertTrue(Files.exists(beforeClear));
	}

	@Test
	void beforeClearBackupsKeepTheClearedHistoryAndAreBounded() throws Exception {
		Path backups = dir.resolve("backups");
		Path first = HistoryBackups.writeBeforeClear(backups, "M5", "{\"runHistory\":[\"m5\"]}", NOW);
		assertTrue(first.getFileName().toString().startsWith(HistoryBackups.BEFORE_CLEAR_PREFIX));
		assertTrue(first.getFileName().toString().endsWith("-m5.json"));
		assertEquals("{\"runHistory\":[\"m5\"]}", Files.readString(first, StandardCharsets.UTF_8));

		for (int i = 1; i <= HistoryBackups.BEFORE_CLEAR_KEEP + 2; i++) {
			HistoryBackups.writeBeforeClear(backups, "all", "{}", NOW + i * 1_000L);
		}
		assertEquals(HistoryBackups.BEFORE_CLEAR_KEEP, HistoryBackups.list(backups, HistoryBackups.BEFORE_CLEAR_PREFIX).size());
	}

	@Test
	void sameMillisecondBackupsDoNotOverwriteEachOther() throws Exception {
		Path backups = dir.resolve("backups");
		Path a = HistoryBackups.writeBeforeClear(backups, "all", "{\"a\":1}", NOW);
		Path b = HistoryBackups.writeBeforeClear(backups, "all", "{\"b\":1}", NOW);
		assertTrue(!a.equals(b));
		assertEquals("{\"a\":1}", Files.readString(a, StandardCharsets.UTF_8));
	}

	@Test
	void missingPrimaryWritesNothingAndUnrelatedFilesAreUntouched() throws Exception {
		Path backups = dir.resolve("backups");
		Files.createDirectories(backups);
		Path unrelated = Files.writeString(backups.resolve("notes.json"), "keep");
		HistoryBackups.rollIfDue(backups, dir.resolve("missing.json"), NOW);
		assertTrue(HistoryBackups.list(backups, HistoryBackups.ROLLING_PREFIX).isEmpty());
		assertTrue(Files.exists(unrelated));
	}

	private Path writePrimary(String json) throws Exception {
		return Files.writeString(dir.resolve("drt.json"), json, StandardCharsets.UTF_8);
	}
}
