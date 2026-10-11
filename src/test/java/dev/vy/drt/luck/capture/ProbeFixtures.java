package dev.vy.drt.luck.capture;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.vy.drt.luck.LuckTestData;
import java.util.ArrayList;
import java.util.List;
import java.util.function.UnaryOperator;

/** Screens recorded in game by the luck probe (Minecraft 26.2), with player names anonymized. */
final class ProbeFixtures {
	static final String CROESUS_MENU = "probe-croesus-menu-2026-10-11.json";
	static final String CROESUS_RUN_VIEW_F5 = "probe-croesus-run-view-f5-2026-10-11.json";
	static final String RNG_OVERVIEW = "probe-rng-overview-2026-10-11.json";
	static final String RNG_FLOOR_F5 = "probe-rng-floor-f5-2026-10-11.json";

	private ProbeFixtures() {
	}

	static LuckProbeRecorder.ScreenSample screen(String name) {
		return screen(name, UnaryOperator.identity());
	}

	/** Loads a screen, passing every lore line through {@code loreEdit} to build variants. */
	static LuckProbeRecorder.ScreenSample screen(String name, UnaryOperator<String> loreEdit) {
		JsonObject json = LuckTestData.fixture(name);
		List<LuckProbeRecorder.SlotSample> slots = new ArrayList<>();
		for (JsonElement element : json.getAsJsonArray("slots")) {
			JsonObject slot = element.getAsJsonObject();
			List<String> lore = new ArrayList<>();
			for (JsonElement line : slot.getAsJsonArray("lore")) lore.add(loreEdit.apply(line.getAsString()));
			slots.add(new LuckProbeRecorder.SlotSample(
				slot.get("slot").getAsInt(),
				slot.get("item").getAsString(),
				slot.get("count").getAsInt(),
				slot.get("name").getAsString(),
				lore
			));
		}
		return new LuckProbeRecorder.ScreenSample(json.get("title").getAsString(), json.get("containerSlots").getAsInt(), slots);
	}

	static LuckProbeRecorder.ScreenSample retitled(LuckProbeRecorder.ScreenSample screen, String title) {
		return new LuckProbeRecorder.ScreenSample(title, screen.containerSlots(), screen.slots());
	}
}
