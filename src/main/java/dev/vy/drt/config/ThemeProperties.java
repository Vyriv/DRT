package dev.vy.drt.config;

import java.util.Map;
import java.util.List;
import java.util.Set;

/** Explicit editor allowlist. Gameplay, loot, chat and overlay identities are deliberately absent. */
public final class ThemeProperties {
    private ThemeProperties() {}
    public static final Set<String> SUPPORTED = Set.of(
        "badge.background",
        "badge.border",
        "button.positiveBackground",
        "button.positiveBorder",
        "button.positiveBorderHover",
        "button.positiveHover",
        "button.positiveText",
        "button.positiveTextHover",
        "chart.axis",
        "chart.actual",
        "chart.bar",
        "chart.barHighlighted",
        "chart.barHover",
        "chart.barNegative",
        "chart.barPositive",
        "chart.fill",
        "chart.grid",
        "chart.hoverGuide",
        "chart.hoverPoint",
        "chart.line",
        "chart.inferred",
        "chart.scoreLine",
        "chart.zero",
        "control.active",
        "control.background",
        "control.border",
        "control.borderActive",
        "control.borderDisabled",
        "control.borderHover",
        "control.disabled",
        "control.hover",
        "control.text",
        "control.textActive",
        "control.textDisabled",
        "control.textHover",
        "floor.catacombs",
        "floor.kuudra",
        "floor.master",
        "grade.a",
        "grade.b",
        "grade.cOrD",
        "grade.s",
        "grade.sPlus",
        "input.background",
        "input.focus",
        "input.hover",
        "input.caret",
        "input.placeholder",
        "input.text",
        "menu.background",
        "menu.border",
        "menu.borderHover",
        "menu.highlight",
        "menu.hover",
        "panel.alternate",
        "panel.background",
        "panel.border",
        "popup.background",
        "popup.border",
        "profit.negative",
        "profit.positive",
        "row.hover",
        "row.background",
        "row.indicator",
        "row.selected",
        "scrim",
        "scrollbar.hover",
        "scrollbar.thumb",
        "scrollbar.track",
        "separator",
        "shadow",
        "status.success",
        "tab.background",
        "tab.border",
        "tab.activeBackground",
        "tab.activeBorder",
        "tab.hoverBackground",
        "tab.hoverBorder",
        "tab.text",
        "tab.textActive",
        "tab.textHover",
        "text.accent",
        "text.hover",
        "text.muted",
        "text.primary",
        "text.secondary",
        "text.description",
        "text.metadata",
        "text.metricLabel",
        "text.pageTitle",
        "text.sectionTitle",
        "text.tableHeading",
        "time.fast",
        "time.medium",
        "time.slow",
        "tooltip.background",
        "tooltip.border",
        "tooltip.text",
        "toggle.border",
        "toggle.knobOff",
        "toggle.knobOn",
        "toggle.trackOff",
        "toggle.trackOn",
        "window.background",
        "window.border"
    );

    private static final Map<String, String> LEGACY = Map.ofEntries(
        Map.entry("chart.actual", "grade.s"),
        Map.entry("chart.inferred", "grade.sPlus"),
        Map.entry("control.text", "text.secondary"),
        Map.entry("control.textActive", "text.primary"),
        Map.entry("control.textDisabled", "text.muted"),
        Map.entry("control.textHover", "text.primary"),
        Map.entry("input.caret", "text.accent"),
        Map.entry("input.placeholder", "text.muted"),
        Map.entry("input.text", "text.primary"),
        Map.entry("menu.border", "control.borderActive"),
        Map.entry("popup.background", "panel.background"),
        Map.entry("popup.border", "control.borderActive"),
        Map.entry("row.background", "control.background"),
        Map.entry("status.success", "profit.positive"),
        Map.entry("tab.activeBackground", "control.active"),
        Map.entry("tab.activeBorder", "control.borderActive"),
        Map.entry("tab.hoverBackground", "control.hover"),
        Map.entry("tab.hoverBorder", "control.borderHover"),
        Map.entry("tab.text", "text.secondary"),
        Map.entry("tab.textActive", "text.primary"),
        Map.entry("tab.textHover", "text.hover"),
        Map.entry("text.description", "text.secondary"),
        Map.entry("text.metadata", "text.muted"),
        Map.entry("text.metricLabel", "text.muted"),
        Map.entry("text.pageTitle", "text.primary"),
        Map.entry("text.sectionTitle", "text.accent"),
        Map.entry("text.tableHeading", "text.muted"),
        Map.entry("toggle.border", "panel.border"),
        Map.entry("toggle.knobOff", "text.secondary"),
        Map.entry("toggle.knobOn", "text.primary"),
        Map.entry("toggle.trackOff", "panel.border"),
        Map.entry("toggle.trackOn", "control.borderActive")
    );

    private static final List<List<String>> COMPONENT_STATE_GROUPS = List.of(
        List.of("button.positiveBackground", "button.positiveHover"),
        List.of("button.positiveBorder", "button.positiveBorderHover"),
        List.of("button.positiveText", "button.positiveTextHover"),
        List.of("control.background", "control.hover", "control.active", "control.disabled"),
        List.of("control.border", "control.borderHover", "control.borderActive", "control.borderDisabled"),
        List.of("control.text", "control.textHover", "control.textActive", "control.textDisabled"),
        List.of("input.background", "input.hover", "input.focus"),
        List.of("input.text", "input.placeholder", "input.caret"),
        List.of("menu.background", "menu.highlight", "menu.hover"),
        List.of("menu.border", "menu.borderHover"),
        List.of("row.background", "row.hover", "row.selected"),
        List.of("scrollbar.track", "scrollbar.thumb", "scrollbar.hover"),
        List.of("tab.background", "tab.hoverBackground", "tab.activeBackground"),
        List.of("tab.border", "tab.hoverBorder", "tab.activeBorder"),
        List.of("tab.text", "tab.textHover", "tab.textActive"),
        List.of("toggle.border", "toggle.trackOff", "toggle.trackOn", "toggle.knobOff", "toggle.knobOn"),
        List.of("chart.bar", "chart.barHighlighted", "chart.barHover", "chart.barNegative", "chart.barPositive")
    );

    public static int resolve(Map<String, Integer> palette, String role, int original) {
        Integer direct = palette.get(role);
        if (direct != null) return direct;
        String legacy = LEGACY.get(role);
        return legacy == null ? original : palette.getOrDefault(legacy, original);
    }

    public static String legacyRole(String role) {
        return LEGACY.get(role);
    }

    public static List<String> componentStateRoles(String role) {
        for (List<String> group : COMPONENT_STATE_GROUPS) if (group.contains(role)) return group;
        return List.of(role);
    }

    public static int defaultColor(String role, int fallback) {
        return switch (role) {
            case "button.positiveBackground" -> 0xFF112011;
            case "button.positiveHover" -> 0xFF1A2A1A;
            case "button.positiveBorder" -> 0xFF2D5A2D;
            case "button.positiveBorderHover" -> 0xFF4A8A4A;
            case "button.positiveText" -> 0xFF5AC05A;
            case "button.positiveTextHover" -> 0xFF8AE88A;
            case "control.background" -> 0xFF171B30;
            case "control.hover" -> 0xFF222641;
            case "control.active" -> 0xFF222642;
            case "control.disabled" -> 0xFF10121E;
            case "control.border" -> 0xFF3F426D;
            case "control.borderHover" -> 0xFF51557E;
            case "control.borderActive" -> 0xFF6268A8;
            case "control.borderDisabled" -> 0xFF252842;
            case "control.text" -> 0xFF9AA4BD;
            case "control.textHover", "control.textActive" -> 0xFFE6E8F2;
            case "control.textDisabled" -> 0xFF747E99;
            case "input.background" -> 0xFF10101F;
            case "input.hover" -> 0xFF20243D;
            case "input.focus" -> 0xFF10172E;
            case "input.text" -> 0xFFE6E8F2;
            case "input.placeholder" -> 0xFF747E99;
            case "input.caret" -> 0xFF9CA8FF;
            case "menu.background" -> 0xFF17172B;
            case "menu.highlight" -> 0x221F2040;
            case "menu.hover" -> 0xCC292A50;
            case "menu.border" -> 0xFF6268A8;
            case "menu.borderHover" -> 0xAA747CC2;
            case "row.background" -> 0xFF151729;
            case "row.hover" -> 0x991A1A2D;
            case "row.selected" -> 0xCC202542;
            case "scrollbar.track" -> 0x3823233C;
            case "scrollbar.thumb" -> 0x8C555B8A;
            case "scrollbar.hover" -> 0xB87880B6;
            case "tab.background" -> 0xFF0F0F1D;
            case "tab.hoverBackground" -> 0xFF17172A;
            case "tab.activeBackground" -> 0xFF202A52;
            case "tab.border" -> 0xFF343458;
            case "tab.hoverBorder" -> 0xFF51557E;
            case "tab.activeBorder" -> 0xFF6A6AB0;
            case "tab.text" -> 0xFF9AA4BD;
            case "tab.textHover" -> 0xFFC2C8E0;
            case "tab.textActive" -> 0xFFE6E8F2;
            case "toggle.border", "toggle.trackOff" -> 0xFF35385D;
            case "toggle.trackOn" -> 0xFF6268A8;
            case "toggle.knobOff" -> 0xFF9AA4BD;
            case "toggle.knobOn" -> 0xFFE6E8F2;
            case "chart.bar" -> 0xFF555D91;
            case "chart.barHighlighted" -> 0xFF8B6BB5;
            case "chart.barHover" -> 0xDD929FEA;
            case "chart.barNegative" -> 0xAAA85F76;
            case "chart.barPositive" -> 0xAA7483D1;
            default -> fallback;
        };
    }
}
