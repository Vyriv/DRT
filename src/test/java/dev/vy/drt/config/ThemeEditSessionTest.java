package dev.vy.drt.config;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class ThemeEditSessionTest {
    @TempDir Path directory;

    @Test void copyDefaultAndPreventOverwrite() throws Exception {
        var draft = new ThemeEditSession(null);
        assertTrue(draft.readOnly());
        draft.set("panel.background", "#80112233");
        assertEquals(0x80112233, draft.palette().get("panel.background"));
        assertThrows(IllegalStateException.class, () -> draft.save(directory, "Ocean", false));
        Path saved = draft.save(directory, "Ocean", true);
        assertEquals(draft.palette(), DrtTheme.parse(Files.readString(saved)));
        assertThrows(java.io.IOException.class, () -> draft.save(directory, "ocean", true));
        assertThrows(IllegalArgumentException.class, () -> draft.save(directory, "../escape", true));
    }

    @Test void preservesUnknownDataAndResetsDraft() throws Exception {
        Path source = directory.resolve("Ocean.json");
        Files.writeString(source, "{\"author\":\"Ryan\",\"extra\":{\"value\":4},\"panel.background\":\"#123456\"}");
        var draft = new ThemeEditSession(source);
        draft.set("panel.background", "#AABBCC");
        draft.reset();
        assertEquals(0xFF123456, draft.palette().get("panel.background"));
        draft.restoreDefault("panel.background");
        assertFalse(draft.palette().containsKey("panel.background"));
        draft.save(directory, "Ocean", false);
        String saved = Files.readString(source);
        assertTrue(saved.contains("Ryan"));
        assertTrue(saved.contains("extra"));
    }

    @Test void protectsExcludedColoursAndExternalEdits() throws Exception {
        Path source = directory.resolve("Ocean.json");
        Files.writeString(source, "{}");
        var draft = new ThemeEditSession(source);
        for (String role : new String[]{"loot.essence.crimson", "rarity.mythic", "hud.title", "dungeonClass.mage", "chat.error", "croesus.opened"}) {
            assertThrows(IllegalArgumentException.class, () -> draft.set(role, "#123456"));
            assertThrows(IllegalArgumentException.class, () -> draft.restoreDefault(role));
        }
        assertThrows(RuntimeException.class, () -> draft.set("panel.background", "invalid"));
        Files.writeString(source, "{\"author\":\"external edit\"}");
        assertThrows(java.io.IOException.class, () -> draft.save(directory, "Ocean", false));
    }
}
