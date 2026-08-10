package dev.vy.drt.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.Gson;
import com.google.gson.JsonParser;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class AtomicJsonFileStoreTest {
	@TempDir
	Path tempDir;

	@Test
	void writeCreatesBackupAndVerifiedPrimary() throws Exception {
		Path path = tempDir.resolve("drt.json");
		Files.writeString(path, "{\"enabled\":true}", StandardCharsets.UTF_8);

		DrtConfig next = new DrtConfig();
		next.enabled = false;
		AtomicJsonFileStore.writeCrashSafe(path, new Gson(), next, false);

		assertTrue(Files.exists(AtomicJsonFileStore.backupPath(path)));
		assertEquals(false, JsonParser.parseString(Files.readString(path)).getAsJsonObject().get("enabled").getAsBoolean());
		assertEquals(true, JsonParser.parseString(Files.readString(AtomicJsonFileStore.backupPath(path))).getAsJsonObject().get("enabled").getAsBoolean());
	}

	@Test
	void malformedPrimaryCanBePreservedOutsideNormalBackup() throws Exception {
		Path path = tempDir.resolve("drt.json");
		Files.writeString(path, "{malformed", StandardCharsets.UTF_8);

		AtomicJsonFileStore.writeCrashSafe(path, new Gson(), new DrtConfig(), true);

		try (var files = Files.list(tempDir)) {
			assertTrue(files.anyMatch(file -> file.getFileName().toString().startsWith("drt.json.malformed-")));
		}
		JsonParser.parseString(Files.readString(path));
	}

	@Test
	void staleInterruptedTempDoesNotCorruptPrimary() throws Exception {
		Path path = tempDir.resolve("drt.json");
		Path temp = tempDir.resolve("drt.json.tmp");
		Files.writeString(path, "{\"enabled\":true}", StandardCharsets.UTF_8);
		Files.writeString(temp, "{interrupted", StandardCharsets.UTF_8);

		DrtConfig next = new DrtConfig();
		next.enabled = false;
		AtomicJsonFileStore.writeCrashSafe(path, new Gson(), next, false);

		assertEquals(false, JsonParser.parseString(Files.readString(path)).getAsJsonObject().get("enabled").getAsBoolean());
		assertEquals(true, JsonParser.parseString(Files.readString(AtomicJsonFileStore.backupPath(path))).getAsJsonObject().get("enabled").getAsBoolean());
	}
}
