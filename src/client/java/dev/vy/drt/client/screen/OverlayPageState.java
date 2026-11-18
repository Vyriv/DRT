package dev.vy.drt.client.screen;

import dev.vy.drt.client.overlay.OverlayPreset;
import java.util.List;

/** Overlay tab selection: previewing a layout changes nothing until it is applied. */
final class OverlayPageState {
	static final List<OverlayPreset> LAYOUTS = List.of(
		OverlayPreset.LEGACY,
		OverlayPreset.MODERN,
		OverlayPreset.SESSION,
		OverlayPreset.DETAILED,
		OverlayPreset.CLASSIC,
		OverlayPreset.CUSTOM
	);

	private OverlayPreset previewed;

	OverlayPreset previewed(OverlayPreset active) {
		return previewed == null ? active : previewed;
	}

	void preview(OverlayPreset preset) {
		previewed = preset;
	}

	boolean hasPendingChange(OverlayPreset active) {
		return previewed != null && previewed != active;
	}

	/** Returns the preset to apply, or null when the preview already matches the live HUD. */
	OverlayPreset apply(OverlayPreset active) {
		OverlayPreset target = hasPendingChange(active) ? previewed : null;
		previewed = null;
		return target;
	}

	void cancel() {
		previewed = null;
	}
}
