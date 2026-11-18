package dev.vy.drt.client.screen;

final class ThemeEditorLayout {
	record Rect(int x, int y, int w, int h) {
		boolean contains(double px, double py) {
			return px >= x && py >= y && px < x + w && py < y + h;
		}
	}

	final int previewTop;
	final int actionTop;
	final Rect picker;
	final Rect titleBar;
	final Rect saturationValue;
	final Rect hue;
	final Rect[] sliderTracks;
	final Rect[] sliderFields;
	final Rect hexField;
	final Rect alphaField;
	final Rect[] rgbaFields;
	final Rect propertyButton;
	final Rect applyButton;
	final Rect cancelButton;
	final Rect defaultButton;
	final Rect closeButton;

	private ThemeEditorLayout(int screenW, int screenH, Integer requestedPickerX, Integer requestedPickerY) {
		previewTop = 25;
		actionTop = Math.max(previewTop + 80, screenH - 42);
		int pickerW = Math.min(Math.max(1, screenW - 8), Math.min(360, Math.max(270, screenW - 12)));
		int pickerH = Math.min(Math.max(1, screenH - 8), Math.min(202, Math.max(166, actionTop - previewTop - 8)));
		int centredX = Math.max(4, (screenW - pickerW) / 2);
		int usableTop = previewTop + 2;
		int usableBottom = Math.max(usableTop + pickerH, actionTop - 2);
		int centredY = Math.max(4, usableTop + (usableBottom - usableTop - pickerH) / 2);
		if (centredY + pickerH > screenH - 4) centredY = Math.max(4, screenH - pickerH - 4);
		int pickerX = clamp(requestedPickerX == null ? centredX : requestedPickerX, 4, Math.max(4, screenW - pickerW - 4));
		int pickerY = clamp(requestedPickerY == null ? centredY : requestedPickerY, 4, Math.max(4, screenH - pickerH - 4));
		picker = new Rect(pickerX, pickerY, pickerW, pickerH);

		int headerH = 20;
		titleBar = new Rect(pickerX, pickerY, pickerW, headerH);
		int bottomButtonH = 17;
		int bottomY = pickerY + pickerH - bottomButtonH - 4;
		int propertyY = bottomY - 19;
		boolean narrow = pickerW < 360;
		int valuesY = propertyY - (narrow ? 39 : 22);
		int contentTop = pickerY + headerH + 6;
		int availableBodyH = Math.max(54, valuesY - contentTop - 4);
		int leftW = Math.max(92, Math.min(150, pickerW * 39 / 100));
		int square = Math.max(52, Math.min(leftW - 24, availableBodyH));
		int leftX = pickerX + 7;
		saturationValue = new Rect(leftX, contentTop, square, square);
		hue = new Rect(leftX + square + 5, contentTop, 12, square);

		int controlsX = leftX + leftW + 8;
		int controlsRight = pickerX + pickerW - 7;
		int fieldW = pickerW < 340 ? 29 : 34;
		int trackX = controlsX + 13;
		int trackW = Math.max(35, controlsRight - fieldW - 6 - trackX);
		int rowStep = Math.max(12, Math.min(22, availableBodyH / 4));
		sliderTracks = new Rect[4];
		sliderFields = new Rect[4];
		for (int i = 0; i < 4; i++) {
			int rowY = contentTop + i * rowStep;
			sliderTracks[i] = new Rect(trackX, rowY + 4, trackW, 8);
			sliderFields[i] = new Rect(controlsRight - fieldW, rowY, fieldW, 16);
		}

		int labelW = 24;
		int hexW = Math.max(66, Math.min(88, pickerW / 4));
		hexField = new Rect(pickerX + 32, valuesY, hexW, 16);
		alphaField = new Rect(hexField.x + hexField.w + 35, valuesY, fieldW, 16);
		int rgbaY = narrow ? valuesY + 19 : valuesY;
		int rgbaStart = narrow ? pickerX + 42 : Math.max(alphaField.x + alphaField.w + 28, pickerX + pickerW - fieldW * 4 - 15);
		rgbaFields = new Rect[4];
		for (int i = 0; i < 4; i++) rgbaFields[i] = new Rect(rgbaStart + i * (fieldW + 2), rgbaY, fieldW, 16);
		if (rgbaFields[3].x + rgbaFields[3].w > pickerX + pickerW - 6) {
			int compactW = Math.max(22, (pickerX + pickerW - 6 - rgbaStart - 6) / 4);
			for (int i = 0; i < 4; i++) rgbaFields[i] = new Rect(rgbaStart + i * (compactW + 2), rgbaY, compactW, 16);
		}
		propertyButton = new Rect(pickerX + 8, propertyY, pickerW - 16, 16);
		int gap = 4;
		int buttonW = (pickerW - 18 - gap * 2) / 3;
		applyButton = new Rect(pickerX + 6, bottomY, buttonW, bottomButtonH);
		cancelButton = new Rect(applyButton.x + buttonW + gap, bottomY, buttonW, bottomButtonH);
		defaultButton = new Rect(cancelButton.x + buttonW + gap, bottomY, pickerX + pickerW - 6 - (cancelButton.x + buttonW + gap), bottomButtonH);
		closeButton = new Rect(pickerX + pickerW - 20, pickerY + 3, 16, 16);
	}

	static ThemeEditorLayout calculate(int width, int height) {
		return new ThemeEditorLayout(Math.max(1, width), Math.max(1, height), null, null);
	}

	static ThemeEditorLayout calculate(int width, int height, int pickerX, int pickerY) {
		return new ThemeEditorLayout(Math.max(1, width), Math.max(1, height), pickerX, pickerY);
	}

	private static int clamp(int value, int minimum, int maximum) {
		return Math.max(minimum, Math.min(maximum, value));
	}
}
