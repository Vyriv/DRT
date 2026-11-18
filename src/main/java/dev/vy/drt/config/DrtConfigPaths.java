package dev.vy.drt.config;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

public final class DrtConfigPaths {
	private DrtConfigPaths() {
	}

	public static Path drtDirectory(Path configDirectory) {
		return configDirectory.resolve("drt");
	}

	public static Path configFile(Path configDirectory) {
		return drtDirectory(configDirectory).resolve("drt.json");
	}

	public static Path themesDirectory(Path configDirectory) {
		return drtDirectory(configDirectory).resolve("themes");
	}

	/** Copies the legacy config only when the new DRT-owned path has no config. */
	public static boolean migrateLegacyConfig(Path configDirectory) throws IOException {
		Path target = configFile(configDirectory);
		Files.createDirectories(target.getParent());
		if (Files.exists(target)) return false;
		Path legacy = configDirectory.resolve("drt.json");
		if (Files.notExists(legacy) || !Files.isRegularFile(legacy)) return false;
		Files.copy(legacy, target, StandardCopyOption.COPY_ATTRIBUTES);
		return true;
	}
}
