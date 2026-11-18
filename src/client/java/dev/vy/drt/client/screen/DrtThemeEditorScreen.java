package dev.vy.drt.client.screen;

import com.google.gson.JsonObject;
import dev.vy.drt.config.DrtConfigManager;
import dev.vy.drt.config.DrtTheme;
import dev.vy.drt.config.ThemeEditSession;
import dev.vy.drt.config.ThemeProperties;
import dev.vy.drt.config.ThemePreview;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** All input is owned here. The embedded DRT screen only renders and never receives events. */
public final class DrtThemeEditorScreen extends Screen {
	private static final int DRAG_NONE = -1;
	private static final int DRAG_SATURATION_VALUE = 4;
	private static final int DRAG_HUE = 5;
	private static final int DRAG_PICKER = 6;
	private final Screen parent;
	private final DrtScreen preview = new DrtScreen();
	private final List<Action> actions = new ArrayList<>();
	private final ThemePickerTextureCache pickerTextures = new ThemePickerTextureCache();
	private ThemeEditSession draft;
	private ThemePreview.Element selected;
	private ThemePreview.Element hovered;
	private ThemeEditorLayout layout;
	private ThemeColorPickerModel colour;
	private Map<String, Integer> framePalette;
	private String name;
	private String status = "Click a preview element to edit its shared colours";
	private String input = "";
	private String editText = "";
	private boolean copy;
	private boolean picker;
	private boolean restore;
	private boolean replaceInput;
	private int propertyIndex;
	private int oldColor;
	private int drag = DRAG_NONE;
	private int layoutWidth = -1;
	private int layoutHeight = -1;
	private int pickerX = Integer.MIN_VALUE;
	private int pickerY = Integer.MIN_VALUE;
	private int pickerDragOffsetX;
	private int pickerDragOffsetY;

	private record Action(int x, int y, int w, int h, Runnable run) {
		boolean hit(double mx, double my) { return mx >= x && my >= y && mx < x + w && my < y + h; }
	}

	public DrtThemeEditorScreen(Screen parent) {
		super(Component.literal("DRT Theme Editor"));
		this.parent = parent;
		preview.themePreview = true;
		name = DrtConfigManager.getConfig().selectedTheme;
		try {
			Path source = null;
			if (!name.equalsIgnoreCase("Default")) {
				try (var paths = Files.list(DrtConfigManager.themesDirectory())) {
					source = paths.filter(path -> path.getFileName().toString().equalsIgnoreCase(name + ".json")
							|| path.getFileName().toString().equalsIgnoreCase(name + ".theme")
							|| Files.isDirectory(path) && path.getFileName().toString().equalsIgnoreCase(name))
						.findFirst().orElseThrow();
					if (Files.isDirectory(source)) source = source.resolve("theme.json");
				}
			}
			draft = new ThemeEditSession(source);
			copy = draft.readOnly();
			if (copy) name = "My Theme";
		} catch (Exception exception) {
			status = "Cannot edit theme: " + exception.getMessage();
		}
	}

	@Override protected void init() {
		refreshLayout();
		preview.init(width, height);
		preview.previewViewport(layout.previewTop, layout.actionTop);
	}
	@Override public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {}

	private int themed(String role, int fallback) {
		return draft == null ? DrtTheme.color(role, fallback) : ThemeProperties.resolve(framePalette, role, fallback);
	}
	private Map<String, Integer> palette() {
		Map<String, Integer> result = new HashMap<>(draft.palette());
		if (picker && selected != null && colour != null) result.put(property().role(), colour.argb());
		if (picker && selected != null && property().role().startsWith("profit.")) {
			JsonObject json = new JsonObject();
			result.forEach((key, value) -> json.addProperty(key, String.format("#%08X", value)));
			result = new HashMap<>(DrtTheme.parse(json.toString()));
		}
		return result;
	}

	@Override public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
		refreshLayout();
		preview.previewViewport(layout.previewTop, layout.actionTop);
		actions.clear();
		if (draft != null) {
			framePalette = palette();
			ThemePreview.begin(framePalette);
			try {
				preview.extractRenderState(graphics, mouseX, mouseY, delta);
				hovered = mouseY >= layout.previewTop && mouseY < layout.actionTop ? ThemePreview.pick(mouseX, mouseY) : null;
			} finally { ThemePreview.end(); }
		}
		if (!picker && hovered != null) outline(graphics, hovered);
		if (!picker && selected != null) outline(graphics, selected);
		drawNavigation(graphics);
		drawActionBar(graphics);
		if (picker) drawPicker(graphics);
	}

	private void refreshLayout() {
		if (layout != null && layoutWidth == width && layoutHeight == height) return;
		layout = pickerX == Integer.MIN_VALUE
			? ThemeEditorLayout.calculate(width, height)
			: ThemeEditorLayout.calculate(width, height, pickerX, pickerY);
		pickerX = layout.picker.x();
		pickerY = layout.picker.y();
		layoutWidth = width;
		layoutHeight = height;
	}

	private void drawNavigation(GuiGraphicsExtractor graphics) {
		graphics.fill(0, 0, width, layout.previewTop - 2, themed("window.background", 0xFF0E0E1E));
		int x = 4;
		for (DrtNavTab tab : DrtNavTab.values()) {
			int w = Math.max(32, (width - 8) / DrtNavTab.values().length - 2);
			button(graphics, x, 3, w, 17, tab.title(), () -> {
				preview.previewTab(tab); selected = null; closePicker();
			});
			x += w + 2;
		}
	}
	private void drawActionBar(GuiGraphicsExtractor graphics) {
		int y = layout.actionTop;
		graphics.fill(0, y, width, height, themed("window.background", 0xFF0E0E1E));
		int w = Math.max(45, (width - 12) / 4);
		button(graphics, 3, y + 3, w, 17, copy ? "Create Theme" : "Save", this::save);
		button(graphics, 5 + w, y + 3, w, 17, "Cancel", this::onClose);
		button(graphics, 7 + w * 2, y + 3, w, 17, "Reset Changes", () -> {
			if (draft != null) draft.reset(); selected = null; closePicker();
		});
		button(graphics, 9 + w * 3, y + 3, w - 3, 17, "Create Copy", () -> {
			copy = true; name = "My Theme"; beginInput("name", name);
		});
		String footer = (copy ? "Name: " + name + " | " : "") + status;
		graphics.text(font, font.plainSubstrByWidth(footer, width - 10), 5, y + 25, themed("text.description", 0xFF9AA4BD));
		if (copy) actions.add(new Action(0, y + 22, width, 18, () -> beginInput("name", name)));
	}

	private void outline(GuiGraphicsExtractor graphics, ThemePreview.Element element) {
		int color = themed("control.borderActive", 0xFF6268A8);
		graphics.fill(element.x() - 1, element.y() - 1, element.x() + element.w() + 1, element.y(), color);
		graphics.fill(element.x() - 1, element.y() + element.h(), element.x() + element.w() + 1, element.y() + element.h() + 1, color);
		graphics.fill(element.x() - 1, element.y(), element.x(), element.y() + element.h(), color);
		graphics.fill(element.x() + element.w(), element.y(), element.x() + element.w() + 1, element.y() + element.h(), color);
	}
	private ThemePreview.Ink property() { return selected.properties().get(propertyIndex); }
	private void select(ThemePreview.Element element) { selected = element; propertyIndex = 0; openProperty(); }
	private void openProperty() {
		oldColor = draft.palette().getOrDefault(property().role(), property().original());
		colour = new ThemeColorPickerModel(oldColor);
		picker = true; restore = false; drag = DRAG_NONE; input = "";
	}
	private void closePicker() { picker = false; drag = DRAG_NONE; input = ""; }

	private void drawPicker(GuiGraphicsExtractor graphics) {
		ThemeEditorLayout.Rect popup = layout.picker;
		int border = themed("popup.border", themed("control.borderActive", 0xFF6268A8));
		graphics.fill(popup.x() - 1, popup.y() - 1, popup.x() + popup.w() + 1, popup.y() + popup.h() + 1, border);
		graphics.fill(popup.x(), popup.y(), popup.x() + popup.w(), popup.y() + popup.h(), themed("popup.background", 0xFF111120));
		graphics.fill(popup.x(), popup.y() + 19, popup.x() + popup.w(), popup.y() + 20, themed("separator", 0xFF24243E));
		String friendlyName = ThemePropertyLabels.displayName(property().role());
		graphics.text(font, font.plainSubstrByWidth(friendlyName, popup.w() - 82), popup.x() + 7, popup.y() + 6, themed("text.sectionTitle", 0xFFE5E7F0));
		drawSwatch(graphics, popup.x() + popup.w() - 62, popup.y() + 5, 17, 12, oldColor);
		drawSwatch(graphics, popup.x() + popup.w() - 42, popup.y() + 5, 17, 12, colour.argb());
		flatButton(graphics, layout.closeButton, "X");
		drawSaturationValue(graphics);
		drawHue(graphics);
		for (int channel = 0; channel < 4; channel++) drawSlider(graphics, channel);
		drawValueFields(graphics);
		String propertyLabel = selected.properties().size() > 1 ? "< " + friendlyName + " >" : friendlyName;
		flatButton(graphics, layout.propertyButton, propertyLabel);
		flatButton(graphics, layout.applyButton, "Apply");
		flatButton(graphics, layout.cancelButton, "Cancel");
		flatButton(graphics, layout.defaultButton, "Default");
	}
	private void drawSaturationValue(GuiGraphicsExtractor graphics) {
		ThemeEditorLayout.Rect rect = layout.saturationValue;
		pickerTextures.drawSaturationValue(graphics, rect, colour.hue());
		int markerX = rect.x() + Math.round(colour.saturation() * (rect.w() - 1));
		int markerY = rect.y() + Math.round((1F - colour.brightness()) * (rect.h() - 1));
		drawMarker(graphics, markerX, markerY);
	}
	private void drawHue(GuiGraphicsExtractor graphics) {
		ThemeEditorLayout.Rect rect = layout.hue;
		pickerTextures.drawHue(graphics, rect);
		int markerY = rect.y() + Math.round(colour.hue() * (rect.h() - 1));
		graphics.fill(rect.x() - 2, markerY - 1, rect.x() + rect.w() + 2, markerY + 2, themed("input.caret", 0xFFE5E7F0));
	}
	private void drawSlider(GuiGraphicsExtractor graphics, int channel) {
		ThemeEditorLayout.Rect track = layout.sliderTracks[channel];
		ThemeEditorLayout.Rect field = layout.sliderFields[channel];
		String label = channel == 0 ? "R" : channel == 1 ? "G" : channel == 2 ? "B" : "A";
		graphics.text(font, label, track.x() - 12, track.y(), themed("text.metricLabel", 0xFF9AA4BD));
		int checkerDark = 0xFF141425;
		int checkerLight = 0xFF24243E;
		pickerTextures.drawSlider(graphics, track, channel, colour.red(), colour.green(), colour.blue(), checkerDark, checkerLight);
		int value = channelValue(channel);
		int handleX = track.x() + Math.round(value * (track.w() - 1) / 255F);
		graphics.fill(handleX - 1, track.y() - 2, handleX + 2, track.y() + track.h() + 2, themed("control.borderActive", 0xFF6268A8));
		graphics.fill(handleX, track.y() - 1, handleX + 1, track.y() + track.h() + 1, themed("input.caret", 0xFFE5E7F0));
		drawField(graphics, field, fieldText("channel" + channel, Integer.toString(value)));
	}
	private int channelValue(int channel) {
		return switch (channel) { case 0 -> colour.red(); case 1 -> colour.green(); case 2 -> colour.blue(); default -> colour.alpha(); };
	}
	private void drawValueFields(GuiGraphicsExtractor graphics) {
		ThemeEditorLayout.Rect hex = layout.hexField;
		graphics.text(font, "Hex", hex.x() - 25, hex.y() + 4, themed("text.metricLabel", 0xFF9AA4BD));
		drawField(graphics, hex, fieldText("hex", colour.rgbHex()), input.equals("hex") && replaceInput);
		ThemeEditorLayout.Rect alpha = layout.alphaField;
		graphics.text(font, "Alpha", alpha.x() - 31, alpha.y() + 4, themed("text.metricLabel", 0xFF9AA4BD));
		drawField(graphics, alpha, fieldText("alpha", Integer.toString(colour.alpha())));
		ThemeEditorLayout.Rect first = layout.rgbaFields[0];
		graphics.text(font, "RGBA", first.x() - 31, first.y() + 4, themed("text.metricLabel", 0xFF9AA4BD));
		for (int channel = 0; channel < 4; channel++) drawField(graphics, layout.rgbaFields[channel], fieldText("rgba" + channel, Integer.toString(channelValue(channel))));
	}
	private void drawField(GuiGraphicsExtractor graphics, ThemeEditorLayout.Rect rect, String value) {
		drawField(graphics, rect, value, false);
	}
	private void drawField(GuiGraphicsExtractor graphics, ThemeEditorLayout.Rect rect, String value, boolean selectedText) {
		graphics.fill(rect.x(), rect.y(), rect.x() + rect.w(), rect.y() + rect.h(), themed("control.border", 0xFF2D2D50));
		graphics.fill(rect.x() + 1, rect.y() + 1, rect.x() + rect.w() - 1, rect.y() + rect.h() - 1, themed("input.background", themed("control.background", 0xFF141425)));
		String shown = font.plainSubstrByWidth(value, Math.max(1, rect.w() - 4));
		if (selectedText && !shown.isEmpty()) {
			graphics.fill(rect.x() + 1, rect.y() + 3, rect.x() + 2 + font.width(shown), rect.y() + 4 + font.lineHeight, themed("control.borderActive", 0xFF6268A8));
		}
		graphics.text(font, shown, rect.x() + 2, rect.y() + 4, themed("input.text", 0xFFE5E7F0));
	}
	private void drawSwatch(GuiGraphicsExtractor graphics, int x, int y, int w, int h, int color) {
		ThemeEditorLayout.Rect rect = new ThemeEditorLayout.Rect(x, y, w, h);
		pickerTextures.drawChecker(graphics, rect, 0xFF141425, 0xFF24243E);
		graphics.fill(x, y, x + w, y + h, color);
		graphics.fill(x, y, x + w, y + 1, themed("control.border", 0xFF2D2D50));
		graphics.fill(x, y + h - 1, x + w, y + h, themed("control.border", 0xFF2D2D50));
	}
	private void drawMarker(GuiGraphicsExtractor graphics, int x, int y) {
		int outer = themed("control.border", 0xFF2D2D50), inner = themed("text.primary", 0xFFE5E7F0);
		graphics.fill(x - 3, y - 1, x + 4, y + 2, outer); graphics.fill(x - 1, y - 3, x + 2, y + 4, outer);
		graphics.fill(x - 2, y, x + 3, y + 1, inner); graphics.fill(x, y - 2, x + 1, y + 3, inner);
	}
	private void button(GuiGraphicsExtractor graphics, int x, int y, int w, int h, String text, Runnable run) {
		flatButton(graphics, new ThemeEditorLayout.Rect(x, y, w, h), text); actions.add(new Action(x, y, w, h, run));
	}
	private void flatButton(GuiGraphicsExtractor graphics, ThemeEditorLayout.Rect rect, String text) {
		graphics.fill(rect.x(), rect.y(), rect.x() + rect.w(), rect.y() + rect.h(), themed("control.border", 0xFF2D2D50));
		graphics.fill(rect.x() + 1, rect.y() + 1, rect.x() + rect.w() - 1, rect.y() + rect.h() - 1, themed("control.background", 0xFF141425));
		String shown = font.plainSubstrByWidth(text, Math.max(1, rect.w() - 6));
		graphics.text(font, shown, rect.x() + (rect.w() - font.width(shown)) / 2, rect.y() + 4, themed("control.text", 0xFFE5E7F0));
	}

	@Override public boolean mouseClicked(MouseButtonEvent event, boolean repeat) {
		if (event.button() != 0) return true;
		if (picker) { handlePickerClick(event.x(), event.y()); return true; }
		for (int i = actions.size() - 1; i >= 0; i--) {
			Action action = actions.get(i);
			if (action.hit(event.x(), event.y())) { action.run().run(); return true; }
		}
		if (hovered != null && event.y() >= layout.previewTop && event.y() < layout.actionTop) select(hovered);
		return true;
	}
	private void handlePickerClick(double x, double y) {
		if (!layout.picker.contains(x, y)) return;
		if (layout.closeButton.contains(x, y) || layout.cancelButton.contains(x, y)) { closePicker(); return; }
		if (layout.titleBar.contains(x, y)) {
			drag = DRAG_PICKER;
			pickerDragOffsetX = (int) Math.round(x - layout.picker.x());
			pickerDragOffsetY = (int) Math.round(y - layout.picker.y());
			return;
		}
		if (layout.applyButton.contains(x, y)) { applyColour(); return; }
		if (layout.defaultButton.contains(x, y)) { colour.setArgb(property().original()); restore = true; input = ""; return; }
		if (layout.propertyButton.contains(x, y)) {
			int direction = x < layout.propertyButton.x() + layout.propertyButton.w() / 2D ? -1 : 1;
			propertyIndex = ThemePropertyLabels.cycleIndex(propertyIndex, selected.properties().size(), direction);
			openProperty();
			return;
		}
		if (layout.saturationValue.contains(x, y)) drag = DRAG_SATURATION_VALUE;
		else if (layout.hue.contains(x, y)) drag = DRAG_HUE;
		else {
			for (int channel = 0; channel < 4; channel++) {
				if (layout.sliderTracks[channel].contains(x, y)) drag = channel;
				if (layout.sliderFields[channel].contains(x, y)) { beginInput("channel" + channel, Integer.toString(channelValue(channel))); return; }
				if (layout.rgbaFields[channel].contains(x, y)) { beginInput("rgba" + channel, Integer.toString(channelValue(channel))); return; }
			}
			if (layout.hexField.contains(x, y)) { beginInput("hex", colour.rgbHex()); return; }
			if (layout.alphaField.contains(x, y)) { beginInput("alpha", Integer.toString(colour.alpha())); return; }
		}
		adjustDrag(x, y);
	}
	private void applyColour() {
		try {
			if (restore) draft.restoreDefault(property().role());
			else {
				String argb = colour.argbHex(); DrtTheme.parseColor(argb);
				if (property().role().startsWith("profit.") && !palette().containsKey(property().role())) throw new IllegalArgumentException("Profit colours must differ");
				draft.set(property().role(), argb);
			}
			closePicker(); status = "Colour applied to preview";
		} catch (Exception exception) { status = exception.getMessage(); }
	}
	private void adjustDrag(double x, double y) {
		if (drag == DRAG_PICKER) {
			movePicker((int) Math.round(x) - pickerDragOffsetX, (int) Math.round(y) - pickerDragOffsetY);
			return;
		}
		if (drag == DRAG_SATURATION_VALUE) {
			ThemeEditorLayout.Rect rect = layout.saturationValue;
			colour.setSaturationBrightness((float) ((x - rect.x()) / Math.max(1D, rect.w() - 1D)), 1F - (float) ((y - rect.y()) / Math.max(1D, rect.h() - 1D)));
		} else if (drag == DRAG_HUE) {
			ThemeEditorLayout.Rect rect = layout.hue; colour.setHue((float) ((y - rect.y()) / Math.max(1D, rect.h() - 1D)));
		} else if (drag >= 0 && drag < 4) {
			ThemeEditorLayout.Rect rect = layout.sliderTracks[drag]; colour.setSliderFraction(drag, (x - rect.x()) / Math.max(1D, rect.w() - 1D));
		}
		if (drag != DRAG_NONE) { restore = false; input = ""; status = "Colour preview updated"; }
	}

	private void movePicker(int requestedX, int requestedY) {
		layout = ThemeEditorLayout.calculate(width, height, requestedX, requestedY);
		pickerX = layout.picker.x();
		pickerY = layout.picker.y();
		layoutWidth = width;
		layoutHeight = height;
	}
	@Override public boolean mouseDragged(MouseButtonEvent event, double deltaX, double deltaY) {
		if (picker && drag != DRAG_NONE) adjustDrag(event.x(), event.y()); return true;
	}
	@Override public boolean mouseReleased(MouseButtonEvent event) { drag = DRAG_NONE; return true; }

	private void beginInput(String target, String current) {
		input = target;
		editText = current;
		replaceInput = true;
		//? if >=26.3 {
		/*((dev.vy.drt.client.compat.SdlScreen) this).requestTextInput();*/
		//?}
	}
	private String fieldText(String target, String canonical) { return input.equals(target) ? editText : canonical; }
	@Override public boolean charTyped(CharacterEvent event) {
		if (input.isEmpty()) return true;
		if (replaceInput) { editText = ""; replaceInput = false; }
		String character = event.codepointAsString();
		if (input.equals("name")) { if (editText.length() < 64) editText += character; name = editText; return true; }
		if (input.equals("hex")) {
			if (editText.length() < 7) editText += character;
			if (colour.trySetRgbHex(editText)) { restore = false; status = "Colour preview updated"; } else status = "Enter #RRGGBB";
			return true;
		}
		if (character.length() == 1 && Character.isDigit(character.charAt(0)) && editText.length() < 3) { editText += character; updateNumericInput(); }
		return true;
	}
	private void updateNumericInput() {
		try {
			int value = Integer.parseInt(editText);
			if (value < 0 || value > 255) throw new NumberFormatException();
			int channel = input.equals("alpha") ? 3 : Integer.parseInt(input.substring(input.length() - 1));
			colour.setChannel(channel, value); restore = false; status = "Colour preview updated";
		} catch (NumberFormatException exception) { status = "Enter a value from 0 to 255"; }
	}
	@Override public boolean keyPressed(KeyEvent event) {
		if (event.key() == GLFW.GLFW_KEY_ESCAPE) { if (picker) closePicker(); else onClose(); return true; }
		if (event.key() == GLFW.GLFW_KEY_ENTER || event.key() == GLFW.GLFW_KEY_KP_ENTER) { input = ""; return true; }
		if (input.equals("hex")) {
			if (event.isSelectAll()) { replaceInput = true; return true; }
			if (event.isCopy()) { if (replaceInput && !editText.isEmpty()) minecraft.keyboardHandler.setClipboard(editText); return true; }
			if (event.isPaste()) { pasteHex(minecraft.keyboardHandler.getClipboard()); return true; }
		}
		if (event.key() == GLFW.GLFW_KEY_BACKSPACE && !input.isEmpty()) {
			if (replaceInput) { editText = ""; replaceInput = false; } else if (!editText.isEmpty()) editText = editText.substring(0, editText.length() - 1);
			if (input.equals("name")) name = editText;
			else if (input.equals("hex")) { if (colour.trySetRgbHex(editText)) restore = false; else status = "Enter #RRGGBB"; }
			else updateNumericInput();
			return true;
		}
		return true;
	}
	private void pasteHex(String clipboard) {
		if (colour.trySetArgbHex(clipboard)) editText = colour.rgbHex();
		else {
			String text = ThemeColorPickerModel.pastedHexText(replaceInput ? "" : editText, clipboard);
			if (text == null) { status = "Clipboard does not contain a hex colour"; return; }
			editText = text;
			replaceInput = false;
			if (!colour.trySetRgbHex(editText)) { status = "Enter #RRGGBB"; return; }
		}
		replaceInput = false; restore = false; status = "Colour preview updated";
	}

	private void save() {
		if (draft == null) return;
		try {
			if (picker) { status = "Apply or cancel the colour first"; return; }
			draft.save(DrtConfigManager.themesDirectory(), name, copy);
			DrtConfigManager.updateUserSettings(config -> config.selectedTheme = name);
			DrtTheme.reload(); minecraft.gui.setScreen(parent);
		} catch (Exception exception) { status = exception.getMessage(); }
	}
	@Override public void onClose() { ThemePreview.end(); pickerTextures.close(); minecraft.gui.setScreen(parent); }
	@Override public void removed() { pickerTextures.close(); super.removed(); }
	@Override public boolean isPauseScreen() { return false; }
}
