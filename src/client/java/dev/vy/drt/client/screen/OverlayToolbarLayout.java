package dev.vy.drt.client.screen;

import java.util.ArrayList;
import java.util.List;

/**
 * Places the Overlay tab's HUD controls as a toolbar. Groups keep their order and wrap onto extra rows
 * only when the scaled GUI width runs out; leading groups pack left and trailing groups sit on the right.
 */
final class OverlayToolbarLayout {
	static final int ROW_HEIGHT = 26;
	static final int GROUP_GAP = 16;

	record Group(int width, boolean trailing) {
	}

	record Placement(int row, int x, int width) {
	}

	record Result(int rows, List<Placement> placements) {
		int height() {
			return rows * ROW_HEIGHT;
		}
	}

	private OverlayToolbarLayout() {
	}

	static Result arrange(List<Group> groups, int availableWidth) {
		int available = Math.max(1, availableWidth);
		List<List<Integer>> rows = new ArrayList<>();
		List<Integer> current = new ArrayList<>();
		int used = 0;
		for (int i = 0; i < groups.size(); i++) {
			int width = Math.min(available, Math.max(0, groups.get(i).width()));
			int needed = current.isEmpty() ? width : used + GROUP_GAP + width;
			if (!current.isEmpty() && needed > available) {
				rows.add(current);
				current = new ArrayList<>();
				needed = width;
			}
			current.add(i);
			used = needed;
		}
		if (!current.isEmpty()) rows.add(current);

		Placement[] placements = new Placement[groups.size()];
		for (int row = 0; row < rows.size(); row++) {
			int left = 0;
			int right = available;
			List<Integer> indices = rows.get(row);
			for (int index : indices) {
				if (groups.get(index).trailing()) continue;
				int width = Math.min(available, Math.max(0, groups.get(index).width()));
				placements[index] = new Placement(row, left, width);
				left += width + GROUP_GAP;
			}
			for (int i = indices.size() - 1; i >= 0; i--) {
				int index = indices.get(i);
				if (!groups.get(index).trailing()) continue;
				int width = Math.min(available, Math.max(0, groups.get(index).width()));
				right -= width;
				placements[index] = new Placement(row, Math.max(0, right), width);
				right -= GROUP_GAP;
			}
		}
		return new Result(Math.max(1, rows.size()), List.of(placements));
	}
}
