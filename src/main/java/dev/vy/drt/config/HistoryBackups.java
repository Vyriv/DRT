package dev.vy.drt.config;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

/**
 * Bounded history backups next to the config. Rolling backups copy the saved file at most once per
 * {@link #ROLLING_INTERVAL_MILLIS}; before-clear backups snapshot the history about to be cleared.
 * Each kind is pruned only against its own prefix, so rolling backups never push out a
 * before-clear backup.
 */
public final class HistoryBackups {
	public static final String ROLLING_PREFIX = "drt-rolling-";
	public static final String BEFORE_CLEAR_PREFIX = "drt-before-clear-";
	public static final int ROLLING_KEEP = 8;
	public static final int BEFORE_CLEAR_KEEP = 10;
	public static final long ROLLING_INTERVAL_MILLIS = 6L * 60L * 60L * 1000L;
	private static final String SUFFIX = ".json";
	private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss-SSS").withZone(ZoneId.systemDefault());

	private HistoryBackups() {
	}

	/** Writes {@code json} as a before-clear backup and returns its path. */
	public static Path writeBeforeClear(Path dir, String scope, String json, long now) throws IOException {
		Files.createDirectories(dir);
		Path target = uniqueTarget(dir, BEFORE_CLEAR_PREFIX + stamp(now) + "-" + safeScope(scope));
		Path temp = target.resolveSibling(target.getFileName() + ".tmp");
		Files.writeString(temp, json, StandardCharsets.UTF_8);
		moveReplacing(temp, target);
		Files.setLastModifiedTime(target, FileTime.fromMillis(now));
		prune(dir, BEFORE_CLEAR_PREFIX, BEFORE_CLEAR_KEEP);
		return target;
	}

	/**
	 * Copies {@code primary} as a rolling backup when the newest one is at least
	 * {@link #ROLLING_INTERVAL_MILLIS} old, or missing.
	 *
	 * @return when the next rolling backup is due, in epoch millis
	 */
	public static long rollIfDue(Path dir, Path primary, long now) throws IOException {
		long newest = newestModified(dir, ROLLING_PREFIX);
		if (newest >= 0L && newest <= now && now - newest < ROLLING_INTERVAL_MILLIS) return newest + ROLLING_INTERVAL_MILLIS;
		if (primary == null || Files.notExists(primary)) return now + ROLLING_INTERVAL_MILLIS;
		Files.createDirectories(dir);
		Path target = uniqueTarget(dir, ROLLING_PREFIX + stamp(now));
		Path temp = target.resolveSibling(target.getFileName() + ".tmp");
		Files.copy(primary, temp, StandardCopyOption.REPLACE_EXISTING);
		moveReplacing(temp, target);
		Files.setLastModifiedTime(target, FileTime.fromMillis(now));
		prune(dir, ROLLING_PREFIX, ROLLING_KEEP);
		return now + ROLLING_INTERVAL_MILLIS;
	}

	/** Backups of one kind, newest first. */
	public static List<Path> list(Path dir, String prefix) throws IOException {
		if (dir == null || Files.notExists(dir)) return List.of();
		List<Path> out = new ArrayList<>();
		try (Stream<Path> files = Files.list(dir)) {
			files.filter(path -> {
				String name = path.getFileName().toString();
				return name.startsWith(prefix) && name.endsWith(SUFFIX) && Files.isRegularFile(path);
			}).forEach(out::add);
		}
		out.sort(Comparator.comparingLong(HistoryBackups::modified).reversed()
			.thenComparing(Comparator.comparing((Path path) -> path.getFileName().toString()).reversed()));
		return out;
	}

	private static void prune(Path dir, String prefix, int keep) throws IOException {
		List<Path> backups = list(dir, prefix);
		for (int i = keep; i < backups.size(); i++) {
			Files.deleteIfExists(backups.get(i));
		}
	}

	private static long newestModified(Path dir, String prefix) throws IOException {
		List<Path> backups = list(dir, prefix);
		return backups.isEmpty() ? -1L : modified(backups.get(0));
	}

	private static long modified(Path path) {
		try {
			return Files.getLastModifiedTime(path).toMillis();
		} catch (IOException e) {
			return -1L;
		}
	}

	private static Path uniqueTarget(Path dir, String base) {
		Path target = dir.resolve(base + SUFFIX);
		for (int n = 2; Files.exists(target); n++) {
			target = dir.resolve(base + "-" + n + SUFFIX);
		}
		return target;
	}

	private static String stamp(long now) {
		return STAMP.format(Instant.ofEpochMilli(now));
	}

	private static String safeScope(String scope) {
		String cleaned = scope == null ? "" : scope.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-").replaceAll("^-+|-+$", "");
		return cleaned.isEmpty() ? "all" : cleaned;
	}

	private static void moveReplacing(Path source, Path target) throws IOException {
		try {
			Files.move(source, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
		} catch (AtomicMoveNotSupportedException ignored) {
			Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
		}
	}
}
