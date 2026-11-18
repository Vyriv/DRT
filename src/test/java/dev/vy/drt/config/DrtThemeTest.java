package dev.vy.drt.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.nio.file.Files;
import static org.junit.jupiter.api.Assertions.*;

class DrtThemeTest {
    @TempDir Path directory;
    @AfterEach void restore() { DrtTheme.load(directory, "Default"); }
    @Test void switchesReloadsAndRestoresExactFallbacks() throws Exception {
        Path file = directory.resolve("Ocean.json");
        Files.writeString(file, "{\"panel.background\":\"#123456\"}");
        DrtTheme.load(directory, "Ocean");
        assertEquals(0xFF123456, DrtTheme.color("panel.background", 1));
        assertEquals(0xFF123456, DrtTheme.color("panel.background", 2));
        Files.writeString(file, "{\"panel.background\":\"#654321\"}");
        assertEquals(0xFF123456, DrtTheme.color("panel.background", 1));
        DrtTheme.load(directory, "Ocean");
        assertEquals(0xFF654321, DrtTheme.color("panel.background", 1));
        DrtTheme.load(directory, "Default");
        assertEquals(1, DrtTheme.color("panel.background", 1));
        assertEquals(2, DrtTheme.color("panel.background", 2));
        DrtTheme.load(directory, "Ocean");
        assertEquals(0xFF654321, DrtTheme.color("panel.background", 1));
        Files.writeString(file, "broken");
        DrtTheme.load(directory, "Ocean");
        assertEquals(1, DrtTheme.color("panel.background", 1));
        assertFalse(DrtTheme.diagnostic().isEmpty());
    }
    @Test void parsesSkylistFormatsAndFallsBackPerProperty() {
        var palette = DrtTheme.parse("# comment\n{\"a\":\"#123456\",\"b\":\"rgba(1,2,3,0.5)\",\"c\":null,\"d\":\"invalid\"}");
        assertEquals(0xFF123456, palette.get("a"));
        assertEquals(0x7F010203, palette.get("b"));
        assertFalse(palette.containsKey("c"));
        assertFalse(palette.containsKey("d"));
    }
    @Test void rejectsIndistinguishableProfitColours() {
        var palette = DrtTheme.parse("{\"profit.positive\":\"#888888\",\"profit.negative\":\"#888889\"}");
        assertTrue(palette.isEmpty());
    }

    @Test void newSemanticRolesPreferExplicitValuesAndInheritLegacyThemes() {
        var legacy = DrtTheme.parse("{\"text.primary\":\"#123456\",\"panel.border\":\"#654321\"}");
        assertEquals(0xFF123456, ThemeProperties.resolve(legacy, "text.pageTitle", 0));
        assertEquals(0xFF654321, ThemeProperties.resolve(legacy, "toggle.border", 0));

        var current = DrtTheme.parse("{\"text.primary\":\"#123456\",\"text.pageTitle\":\"#ABCDEF\"}");
        assertEquals(0xFFABCDEF, ThemeProperties.resolve(current, "text.pageTitle", 0));
        assertEquals(0xFF123456, ThemeProperties.resolve(current, "text.primary", 0));
    }
}
