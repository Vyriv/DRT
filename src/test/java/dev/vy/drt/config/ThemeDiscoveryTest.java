package dev.vy.drt.config;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ThemeDiscoveryTest {
	@TempDir
	Path temporaryDirectory;

	@Test
	void missingDirectoryIsCreatedAndReturnsDefault() throws Exception {
		Path themes = temporaryDirectory.resolve("themes");
		assertEquals(List.of("Default"), ThemeDiscovery.discover(themes));
		assertTrue(Files.isDirectory(themes));
	}

	@Test
	void discoversFoldersAndSupportedFilesWithoutDuplicates() throws Exception {
		Path themes = temporaryDirectory.resolve("themes");
		Files.createDirectories(themes.resolve("Amethyst"));
		Files.writeString(themes.resolve("Midnight.json"), "{}");
		Files.writeString(themes.resolve("midnight.theme"), "future");
		Files.writeString(themes.resolve("notes.txt"), "ignored");

		assertEquals(List.of("Default", "Amethyst", "Midnight"), ThemeDiscovery.discover(themes));
	}
}
