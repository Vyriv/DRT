package dev.vy.drt.client.screen;

final class LootContextMenuState {
	record Position(int x, int y) {}

	private int targetIndex = -1;
	private int x;
	private int y;
	private int width;
	private int height;
	private boolean deleteArmed;

	void open(int index, int cursorX, int cursorY, int menuWidth, int menuHeight,
		int boundX, int boundY, int boundWidth, int boundHeight) {
		targetIndex = index;
		width = menuWidth;
		height = menuHeight;
		x = clamp(cursorX, boundX, Math.max(boundX, boundX + boundWidth - menuWidth));
		y = clamp(cursorY, boundY, Math.max(boundY, boundY + boundHeight - menuHeight));
		deleteArmed = false;
	}

	boolean open() { return targetIndex >= 0; }
	int targetIndex() { return targetIndex; }
	int x() { return x; }
	int y() { return y; }
	int width() { return width; }
	int height() { return height; }
	Position position() { return new Position(x, y); }
	boolean contains(int mouseX, int mouseY) {
		return open() && mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
	}
	boolean deleteArmed() { return deleteArmed; }
	boolean requestDelete() {
		if (deleteArmed) return true;
		deleteArmed = true;
		return false;
	}
	void close() {
		targetIndex = -1;
		deleteArmed = false;
	}

	private static int clamp(int value, int min, int max) {
		return Math.max(min, Math.min(max, value));
	}
}
