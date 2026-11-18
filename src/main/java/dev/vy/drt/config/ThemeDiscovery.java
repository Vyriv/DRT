package dev.vy.drt.config;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;

public final class ThemeDiscovery {
	public static final String DEFAULT_THEME = "Default";

	private ThemeDiscovery() {
	}

	/** Discovers immediate child folders and .json or .theme files. Theme contents are not parsed yet. */
	public static List<String> discover(Path themesDirectory) throws IOException {
		Files.createDirectories(themesDirectory);
		LinkedHashMap<String, String> names = new LinkedHashMap<>();
		names.put(DEFAULT_THEME.toLowerCase(Locale.ROOT), DEFAULT_THEME);
		try (var entries = Files.list(themesDirectory)) {
			entries.filter(path -> Files.isDirectory(path) || supportedFile(path))
				.map(ThemeDiscovery::displayName)
				.filter(name -> !name.isBlank())
				.sorted(String.CASE_INSENSITIVE_ORDER)
				.forEach(name -> names.putIfAbsent(name.toLowerCase(Locale.ROOT), name));
		}
		List<String> result = new ArrayList<>(names.values());
		result.sort(Comparator.comparing(name -> name.equals(DEFAULT_THEME) ? "" : name, String.CASE_INSENSITIVE_ORDER));
		return List.copyOf(result);
	}

	private static boolean supportedFile(Path path) {
		if (!Files.isRegularFile(path)) return false;
		String name = path.getFileName().toString().toLowerCase(Locale.ROOT);
		return name.endsWith(".json") || name.endsWith(".theme");
	}

	private static String displayName(Path path) {
		String name = path.getFileName() == null ? "" : path.getFileName().toString().trim();
		String lower = name.toLowerCase(Locale.ROOT);
		if (lower.endsWith(".theme")) return name.substring(0, name.length() - 6).trim();
		if (lower.endsWith(".json")) return name.substring(0, name.length() - 5).trim();
		return name;
	}
}
