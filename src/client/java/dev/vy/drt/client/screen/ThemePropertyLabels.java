package dev.vy.drt.client.screen;

import java.util.Map;

/** User-facing names for the stable JSON property identifiers used by themes. */
final class ThemePropertyLabels {
	private static final Map<String, String> LABELS = Map.ofEntries(
		Map.entry("badge.background", "Badge Background"),
		Map.entry("badge.border", "Badge Border"),
		Map.entry("button.positiveBackground", "Positive Button Background"),
		Map.entry("button.positiveBorder", "Positive Button Border"),
		Map.entry("button.positiveBorderHover", "Positive Button Hover Border"),
		Map.entry("button.positiveHover", "Positive Button Hover Background"),
		Map.entry("button.positiveText", "Positive Button Text"),
		Map.entry("button.positiveTextHover", "Positive Button Hover Text"),
		Map.entry("chart.axis", "Chart Axis"),
		Map.entry("chart.actual", "Recorded Chart Data"),
		Map.entry("chart.bar", "Chart Bar"),
		Map.entry("chart.barHighlighted", "Highlighted Chart Bar"),
		Map.entry("chart.barHover", "Chart Bar Hover"),
		Map.entry("chart.barNegative", "Negative Chart Bar"),
		Map.entry("chart.barPositive", "Positive Chart Bar"),
		Map.entry("chart.fill", "Chart Fill"),
		Map.entry("chart.grid", "Chart Grid"),
		Map.entry("chart.hoverGuide", "Chart Hover Guide"),
		Map.entry("chart.hoverPoint", "Chart Hover Point"),
		Map.entry("chart.line", "Chart Line"),
		Map.entry("chart.inferred", "Estimated Chart Data"),
		Map.entry("chart.scoreLine", "Score Chart Line"),
		Map.entry("chart.zero", "Chart Zero Line"),
		Map.entry("control.active", "Active Control Background"),
		Map.entry("control.background", "Control Background"),
		Map.entry("control.border", "Control Border"),
		Map.entry("control.borderActive", "Active Control Border"),
		Map.entry("control.borderDisabled", "Disabled Control Border"),
		Map.entry("control.borderHover", "Control Hover Border"),
		Map.entry("control.disabled", "Disabled Control Background"),
		Map.entry("control.hover", "Control Hover Background"),
		Map.entry("control.text", "Control Text"),
		Map.entry("control.textActive", "Active Control Text"),
		Map.entry("control.textDisabled", "Disabled Control Text"),
		Map.entry("control.textHover", "Control Hover Text"),
		Map.entry("floor.catacombs", "Catacombs Floor"),
		Map.entry("floor.kuudra", "Kuudra Tier"),
		Map.entry("floor.master", "Master Mode Floor"),
		Map.entry("grade.a", "Grade A"),
		Map.entry("grade.b", "Grade B"),
		Map.entry("grade.cOrD", "Grade C and D"),
		Map.entry("grade.s", "Grade S"),
		Map.entry("grade.sPlus", "Grade S Plus"),
		Map.entry("input.background", "Input Background"),
		Map.entry("input.focus", "Focused Input"),
		Map.entry("input.hover", "Input Hover Background"),
		Map.entry("input.caret", "Input Caret"),
		Map.entry("input.placeholder", "Input Placeholder"),
		Map.entry("input.text", "Input Text"),
		Map.entry("menu.background", "Menu Background"),
		Map.entry("menu.border", "Menu Border"),
		Map.entry("menu.borderHover", "Menu Hover Border"),
		Map.entry("menu.highlight", "Menu Highlight"),
		Map.entry("menu.hover", "Menu Hover Background"),
		Map.entry("panel.alternate", "Secondary Panel Background"),
		Map.entry("panel.background", "Panel Background"),
		Map.entry("panel.border", "Panel Border"),
		Map.entry("popup.background", "Popup Background"),
		Map.entry("popup.border", "Popup Border"),
		Map.entry("profit.negative", "Loss Value"),
		Map.entry("profit.positive", "Profit Value"),
		Map.entry("row.hover", "Row Hover Background"),
		Map.entry("row.background", "Row Background"),
		Map.entry("row.indicator", "Row Indicator"),
		Map.entry("row.selected", "Selected Row"),
		Map.entry("scrim", "Screen Dimmer"),
		Map.entry("scrollbar.hover", "Scrollbar Hover"),
		Map.entry("scrollbar.thumb", "Scrollbar Thumb"),
		Map.entry("scrollbar.track", "Scrollbar Track"),
		Map.entry("separator", "Separator"),
		Map.entry("shadow", "Window Shadow"),
		Map.entry("status.success", "Success Status"),
		Map.entry("tab.background", "Tab Background"),
		Map.entry("tab.border", "Tab Border"),
		Map.entry("tab.activeBackground", "Active Tab Background"),
		Map.entry("tab.activeBorder", "Active Tab Border"),
		Map.entry("tab.hoverBackground", "Tab Hover Background"),
		Map.entry("tab.hoverBorder", "Tab Hover Border"),
		Map.entry("tab.text", "Tab Text"),
		Map.entry("tab.textActive", "Active Tab Text"),
		Map.entry("tab.textHover", "Tab Hover Text"),
		Map.entry("text.accent", "Accent Text"),
		Map.entry("text.hover", "Hover Text"),
		Map.entry("text.muted", "Muted Text"),
		Map.entry("text.primary", "Primary Text"),
		Map.entry("text.secondary", "Secondary Text"),
		Map.entry("text.description", "Description Text"),
		Map.entry("text.metadata", "Metadata Text"),
		Map.entry("text.metricLabel", "Metric Label"),
		Map.entry("text.pageTitle", "Page Title"),
		Map.entry("text.sectionTitle", "Section Heading"),
		Map.entry("text.tableHeading", "Table Heading"),
		Map.entry("time.fast", "Fast Run Time"),
		Map.entry("time.medium", "Medium Run Time"),
		Map.entry("time.slow", "Slow Run Time"),
		Map.entry("tooltip.background", "Tooltip Background"),
		Map.entry("tooltip.border", "Tooltip Border"),
		Map.entry("tooltip.text", "Tooltip Text"),
		Map.entry("toggle.border", "Toggle Border"),
		Map.entry("toggle.knobOff", "Toggle Off Knob"),
		Map.entry("toggle.knobOn", "Toggle On Knob"),
		Map.entry("toggle.trackOff", "Toggle Off Track"),
		Map.entry("toggle.trackOn", "Toggle On Track"),
		Map.entry("window.background", "Main Background"),
		Map.entry("window.border", "Main Border")
	);

	private ThemePropertyLabels() {}

	static String displayName(String property) {
		return LABELS.getOrDefault(property, property);
	}

	static int cycleIndex(int current, int count, int direction) {
		if (count <= 1) return 0;
		return Math.floorMod(current + direction, count);
	}

	static Map<String, String> all() {
		return LABELS;
	}
}
