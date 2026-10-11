package dev.vy.drt.luck.meter;

import dev.vy.drt.luck.FloorId;

/**
 * Something known about a floor's meter between runs of a replayed history, applied before the run
 * at {@code beforeRun}. Events at the same index apply in list order.
 */
public sealed interface MeterEvent {
	int beforeRun();

	FloorId floor();

	/** The meter was read in game: the simulated state is replaced, selected or not. */
	record Observed(int beforeRun, RngMeterState state) implements MeterEvent {
		public Observed {
			if (beforeRun < 0) throw new IllegalArgumentException("Negative run index");
			if (state == null) throw new IllegalArgumentException("State required");
		}

		@Override
		public FloorId floor() {
			return state.floor();
		}
	}

	/**
	 * The selection changed but the meter was not read afterwards. Stored progress carries over
	 * under the active selection rule; a null key clears the selection and keeps the progress.
	 */
	record Selected(int beforeRun, FloorId floor, String selectedKey) implements MeterEvent {
		public Selected {
			if (beforeRun < 0) throw new IllegalArgumentException("Negative run index");
			if (floor == null) throw new IllegalArgumentException("Floor required");
		}
	}
}
