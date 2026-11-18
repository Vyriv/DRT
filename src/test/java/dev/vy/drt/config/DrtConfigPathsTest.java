package dev.vy.drt.config;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DrtConfigPathsTest {
	@TempDir
	Path temporaryDirectory;

	@Test
	void migratesLegacyConfigWithoutDeletingIt() throws Exception {
		Path legacy = temporaryDirectory.resolve("drt.json");
		String json = "{\"onboardingComplete\":true,\"bazaarPriceMode\":\"ORDER\"}";
		Files.writeString(legacy, json);

		assertTrue(DrtConfigPaths.migrateLegacyConfig(temporaryDirectory));
		assertEquals(json, Files.readString(DrtConfigPaths.configFile(temporaryDirectory)));
		assertEquals(json, Files.readString(legacy));
	}

	@Test
	void neverOverwritesNewerDrtOwnedConfig() throws Exception {
		Files.writeString(temporaryDirectory.resolve("drt.json"), "legacy");
		Path target = DrtConfigPaths.configFile(temporaryDirectory);
		Files.createDirectories(target.getParent());
		Files.writeString(target, "current");

		assertFalse(DrtConfigPaths.migrateLegacyConfig(temporaryDirectory));
		assertEquals("current", Files.readString(target));
	}

	@Test
	void historyJsonLivesInsideTheDrtDataDirectory() {
		Path dataDirectory = DrtConfigPaths.drtDirectory(temporaryDirectory);
		assertEquals(dataDirectory, DrtConfigPaths.configFile(temporaryDirectory).getParent());
	}
}
