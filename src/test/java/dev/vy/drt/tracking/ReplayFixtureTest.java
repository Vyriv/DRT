package dev.vy.drt.tracking;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

class ReplayFixtureTest {
	@Test
	void m7PlayerInventoryInfernalKeyReplay() throws Exception {
		runFixture("m7-player-inventory-infernal-key");
	}

	@Test
	void historicalRewardDuringActiveDungeonReplay() throws Exception {
		runFixture("historical-reward-during-active-dungeon");
	}

	@Test
	void expandedReliabilityReplayFixtures() throws Exception {
		for (String fixture : List.of(
			"m7-reward-during-active-m7",
			"m5-reward-during-active-f7",
			"dungeon-reward-during-active-kuudra",
			"kuudra-reward-during-active-dungeon",
			"late-identical-loot-line",
			"complete-gui-truncated-chat",
			"completion-two-paths",
			"rapid-distinct-completions",
			"late-duplicate-completion",
			"same-title-duplicate-header",
			"unknown-grade-orphan",
			"ambiguous-item-search",
			"same-title-two-croesus-runs",
			"modifier-preview-abandoned",
			"kismet-reroll",
			"croesus-reopen",
			"committed-run-immutable",
			"committed-chest-immutable",
			"committed-chest-context-immutable",
			"committed-chest-cost-immutable",
			"player-inventory-chest-cost",
			"player-inventory-chest-context",
			"m7-fifth-master-star-stale-f7",
			"context-conflict-k5-m7",
			"disconnect-reconnect",
			"warp-with-pending-chest",
			"delayed-chat-after-gui-close",
			"historical-reward-unknown-floor",
			"reordered-gui-chat-events",
			"dungeon-kuudra-transition",
			"kuudra-dungeon-transition",
			"open-then-abandon",
			"open-twice"
		)) {
			runFixture(fixture);
		}
	}

	private static void runFixture(String name) throws Exception {
		var resource = ReplayFixtureTest.class.getClassLoader().getResource("replay/" + name);
		if (resource == null) throw new IllegalStateException("Missing replay fixture " + name);
		Path directory = Path.of(resource.toURI());
		ReplayFixture fixture = ReplayFixture.load(directory);
		FakeTrackerClock clock = fixture.clock();
		DiagnosticRecorder diagnostics = new DiagnosticRecorder(clock);
		TrackingSession tracker = new TrackingSession(name, "test-server", clock, diagnostics);

		fixture.replayInto(tracker, clock);
		List<String> failures = fixture.assertExpected(tracker.snapshot());

		assertTrue(failures.isEmpty(), () -> name + " failures: " + failures);
	}
}
