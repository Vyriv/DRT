package dev.vy.drt.client.screen;

import dev.vy.drt.config.DrtTheme;
import dev.vy.drt.config.DrtConfig;
import dev.vy.drt.client.overlay.OverlayColors;
import com.google.gson.Gson;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class ThemeApplicationTest {
    @TempDir Path directory;
    @Test void runtimeRendererUsesThemeButHudDoesNot() throws Exception {
        int hudProfit = OverlayColors.profitColor(1000000);
        int hudFloor = OverlayColors.floorTagColor("M7");
        Files.writeString(directory.resolve("Test.json"), "{\"time.fast\":\"#123456\",\"time.medium\":\"#789ABC\",\"time.slow\":\"#FEDCBA\",\"profit.positive\":\"#00FFFF\",\"floor.master\":\"#ABCDEF\"}");
        try {
            DrtTheme.load(directory, "Test");
            assertEquals(0xFF123456, RunTimeColorScale.color("M7", 240000L, 0));
            assertEquals(0xFF789ABC, RunTimeColorScale.color("M7", 300000L, 0));
            assertEquals(0xFFFEDCBA, RunTimeColorScale.color("M7", 360000L, 0));
            assertEquals(hudProfit, OverlayColors.profitColor(1000000));
            assertEquals(hudFloor, OverlayColors.floorTagColor("M7"));
            DrtConfig config = new DrtConfig();
            config.selectedTheme = "Test";
            Gson gson = new Gson();
            DrtConfig restarted = gson.fromJson(gson.toJson(config), DrtConfig.class);
            DrtTheme.load(directory, "Default");
            assertEquals(RunTimeColorScale.FAST, RunTimeColorScale.color("M7", 240000L, 0));
            DrtTheme.load(directory, restarted.selectedTheme);
            assertEquals(0xFF123456, RunTimeColorScale.color("M7", 240000L, 0));
        } finally { DrtTheme.load(directory, "Default"); }
    }

    @Test void protectedLootIdentityDoesNotInheritInterfaceText() throws Exception {
        int blank = DrtScreen.lootItemColor("");
        int unknown = DrtScreen.lootItemColor("UNKNOWN_REWARD_ID");
        Files.writeString(directory.resolve("Text.json"), "{\"text.primary\":\"#010203\",\"text.pageTitle\":\"#AABBCC\"}");
        try {
            DrtTheme.load(directory, "Text");
            assertEquals(0xFF010203, DrtTheme.color("text.primary", 0));
            assertEquals(0xFFAABBCC, DrtTheme.color("text.pageTitle", 0));
            assertEquals(blank, DrtScreen.lootItemColor(""));
            assertEquals(unknown, DrtScreen.lootItemColor("UNKNOWN_REWARD_ID"));
        } finally { DrtTheme.load(directory, "Default"); }
    }
}
