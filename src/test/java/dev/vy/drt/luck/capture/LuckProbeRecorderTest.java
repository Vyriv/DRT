package dev.vy.drt.luck.capture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class LuckProbeRecorderTest {
	private final List<String> lines = new ArrayList<>();
	private final LuckProbeRecorder recorder = new LuckProbeRecorder(lines::add, () -> Instant.parse("2026-10-11T12:00:00Z"));

	private static LuckProbeRecorder.ScreenSample bedrock(String contentsLine) {
		return new LuckProbeRecorder.ScreenSample("Master Mode The Catacombs - Floor V", 54, List.of(
			new LuckProbeRecorder.SlotSample(31, "minecraft:player_head", 1, "Bedrock Chest",
				List.of("Contents", contentsLine, "", "Cost", "4,000,000 Coins"))
		));
	}

	private void feed(Object key, LuckProbeRecorder.ScreenSample sample, int ticks) {
		for (int i = 0; i < ticks; i++) recorder.observeScreen(key, sample);
	}

	@Test
	void screenTitleFilterKeepsDungeonRewardScreensOnly() {
		assertTrue(LuckProbeRecorder.isRelevantScreenTitle("Croesus"));
		assertTrue(LuckProbeRecorder.isRelevantScreenTitle("(1/3) Croesus"));
		assertTrue(LuckProbeRecorder.isRelevantScreenTitle("Bedrock Chest"));
		assertTrue(LuckProbeRecorder.isRelevantScreenTitle("Catacombs RNG Meter"));
		assertTrue(LuckProbeRecorder.isRelevantScreenTitle("Master Mode The Catacombs - Floor V"));
		assertFalse(LuckProbeRecorder.isRelevantScreenTitle("Chest"));
		assertFalse(LuckProbeRecorder.isRelevantScreenTitle("Large Chest"));
		assertFalse(LuckProbeRecorder.isRelevantScreenTitle("SkyBlock Menu"));
		assertFalse(LuckProbeRecorder.isRelevantScreenTitle(null));
	}

	@Test
	void chatFilterKeepsServerLinesAndDropsPlayerChat() {
		assertTrue(LuckProbeRecorder.isRelevantChatLine("Team Score: 306 (S+)"));
		assertTrue(LuckProbeRecorder.isRelevantChatLine("RNG METER! Reselected the Third Master Star for Catacombs - Floor 5 (Master Mode)! CLICK HERE to select a new drop!"));
		assertFalse(LuckProbeRecorder.isRelevantChatLine("Party > [MVP+] someone: 300 Score!"));
		assertFalse(LuckProbeRecorder.isRelevantChatLine("Guild > [MVP+] someone: dont use meter on silk"));
		assertFalse(LuckProbeRecorder.isRelevantChatLine("[331] D [VIP] someone: is it worth to reroll bedrock chest"));
		assertFalse(LuckProbeRecorder.isRelevantChatLine("From [MVP+] someone: chest?"));
		assertFalse(LuckProbeRecorder.isRelevantChatLine("You are now AFK"));
	}

	@Test
	void writesOneSnapshotOnceContentsSettle() {
		Object screen = new Object();
		feed(screen, bedrock("Necron's Handle"), LuckProbeRecorder.STABLE_TICKS - 1);
		assertTrue(lines.isEmpty());
		feed(screen, bedrock("Necron's Handle"), 1);
		assertEquals(1, lines.size());
		feed(screen, bedrock("Necron's Handle"), 50);
		assertEquals(1, lines.size());

		JsonObject json = JsonParser.parseString(lines.get(0)).getAsJsonObject();
		assertEquals(LuckProbeRecorder.SCHEMA, json.get("schema").getAsString());
		assertEquals("screen", json.get("kind").getAsString());
		assertEquals("Master Mode The Catacombs - Floor V", json.get("title").getAsString());
		assertEquals("Necron's Handle", json.getAsJsonArray("slots").get(0).getAsJsonObject().getAsJsonArray("lore").get(1).getAsString());
	}

	@Test
	void changedContentsInSameScreenProduceSecondSnapshot() {
		Object screen = new Object();
		feed(screen, bedrock("Necron's Handle"), LuckProbeRecorder.STABLE_TICKS);
		feed(screen, bedrock("Implosion"), LuckProbeRecorder.STABLE_TICKS);
		assertEquals(2, lines.size());
		JsonObject second = JsonParser.parseString(lines.get(1)).getAsJsonObject();
		assertEquals(1, second.get("screenSession").getAsLong());
		assertEquals(2, second.get("snapshotInSession").getAsInt());
	}

	@Test
	void flickeringContentsAreNotWrittenUntilStable() {
		Object screen = new Object();
		for (int i = 0; i < 40; i++) {
			recorder.observeScreen(screen, bedrock(i % 2 == 0 ? "A" : "B"));
		}
		assertTrue(lines.isEmpty());
	}

	@Test
	void reopeningTheSameContentsStartsANewSession() {
		feed(new Object(), bedrock("Necron's Handle"), LuckProbeRecorder.STABLE_TICKS);
		recorder.screenClosed();
		feed(new Object(), bedrock("Necron's Handle"), LuckProbeRecorder.STABLE_TICKS);
		assertEquals(2, lines.size());
		assertEquals(2, JsonParser.parseString(lines.get(1)).getAsJsonObject().get("screenSession").getAsLong());
	}

	@Test
	void overlayAndIrrelevantChatAreSkipped() {
		assertFalse(recorder.observeChat("Score: 306", "Score: 306", true));
		assertFalse(recorder.observeChat("hello", "hello", false));
		assertTrue(recorder.observeChat("Score: 306 (S+)", "Score: 306 (S+)", false));
		assertEquals(1, lines.size());
	}
}
