package dev.vy.drt.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.AfterEach;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class ThemePreviewTest {
    @AfterEach void end(){ThemePreview.end();}
    @Test void selectsSemanticDrawPropertiesWithoutRgbInference() {
        ThemePreview.begin(Map.of("panel.background",0xFF123456));
        int fill=DrtTheme.color("panel.background",0xFF111120);
        int border=DrtTheme.color("panel.border",0xFF2D2D50);
        ThemePreview.record(10,10,100,30,fill,border);
        var selected=ThemePreview.pick(20,20);
        assertEquals(2,selected.properties().size());
        assertEquals("panel.background",selected.properties().getFirst().role());
        assertEquals(0xFF123456,ThemePreview.decode(fill));
        assertEquals(0xFF123456,ThemePreview.decode(DrtTheme.color("panel.background",0xFF111222)));
        ThemePreview.record(15,15,16,16,0xFF123456);
        assertNull(ThemePreview.pick(20,20));
    }
    @Test void previewIsScopedAndCannotOverrideProtectedRoles() {
        int original=DrtTheme.color("panel.background",0xFF111120);
        ThemePreview.begin(Map.of("panel.background",0x80123456,"loot.essence.crimson",0));
        assertEquals(0x80123456,ThemePreview.decode(DrtTheme.color("panel.background",0xFF111120)));
        assertEquals(0xFFFF8D65,DrtTheme.color("loot.essence.crimson",0xFFFF8D65));
        ThemePreview.end();
        assertEquals(original,DrtTheme.color("panel.background",0xFF111120));
    }

    @Test void hoveredComponentOffersEveryApplicableStateProperty() {
        ThemePreview.begin(Map.of("tab.background",0xFF101010,"tab.hoverBackground",0xFF202020));
        int hoverBackground=DrtTheme.color("tab.hoverBackground",0xFF17172A);
        int hoverBorder=DrtTheme.color("tab.hoverBorder",0xFF51557E);
        ThemePreview.record(10,10,80,18,hoverBackground,hoverBorder);
        int hoverText=DrtTheme.color("tab.textHover",0xFFC2C8E0);
        ThemePreview.record(30,14,30,9,hoverText);

        var selected=ThemePreview.pick(40,16);
        assertNotNull(selected);
        assertEquals(10,selected.x());
        assertTrue(selected.properties().stream().map(ThemePreview.Ink::role).toList().containsAll(List.of(
            "tab.background","tab.hoverBackground","tab.activeBackground",
            "tab.border","tab.hoverBorder","tab.activeBorder",
            "tab.text","tab.textHover","tab.textActive")));
    }

    @Test void protectedElementStillBlocksUnderlyingThemeSelection() {
        ThemePreview.begin(Map.of("row.hover",0xFF202020));
        ThemePreview.record(10,10,100,20,DrtTheme.color("row.hover",0xFF1A1A2D));
        ThemePreview.record(12,12,16,16);
        assertNull(ThemePreview.pick(15,15));
    }

    @Test void interactiveStateFamiliesStayScopedToTheirComponentRole() {
        assertEquals(List.of("control.background","control.hover","control.active","control.disabled"),
            ThemeProperties.componentStateRoles("control.hover"));
        assertEquals(List.of("control.border","control.borderHover","control.borderActive","control.borderDisabled"),
            ThemeProperties.componentStateRoles("control.borderHover"));
        assertEquals(List.of("input.text","input.placeholder","input.caret"),
            ThemeProperties.componentStateRoles("input.caret"));
        assertEquals(List.of("row.background","row.hover","row.selected"),
            ThemeProperties.componentStateRoles("row.selected"));
        assertEquals(List.of("toggle.border","toggle.trackOff","toggle.trackOn","toggle.knobOff","toggle.knobOn"),
            ThemeProperties.componentStateRoles("toggle.knobOn"));
        assertEquals(List.of("panel.background"),ThemeProperties.componentStateRoles("panel.background"));
    }
}
