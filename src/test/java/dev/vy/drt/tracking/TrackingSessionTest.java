package dev.vy.drt.tracking;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.vy.drt.config.ChestCostBreakdown;
import dev.vy.drt.config.DungeonFloor;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class TrackingSessionTest {
	@Test
	void playerInventoryCannotChangeActiveRunFloor() {
		FakeTrackerClock clock = new FakeTrackerClock(Instant.EPOCH);
		DiagnosticRecorder diagnostics = new DiagnosticRecorder(clock);
		TrackingSession session = new TrackingSession("test", "server", clock, diagnostics);
		session.startRun(RunMode.DUNGEON, DungeonFloor.M7, DetectionSource.CONFIRMED_SCOREBOARD, EvidenceStrength.CONFIRMED_SCOREBOARD);

		assertFalse(session.updateActiveRunFloor(DungeonFloor.K5, EvidenceStrength.FALLBACK_GUESS, DetectionSource.PLAYER_INVENTORY));

		TrackingSnapshot snapshot = session.snapshot();
		assertEquals(DungeonFloor.M7, snapshot.activeFloor());
		assertTrue(snapshot.invariants().contains(TrackerInvariant.PLAYER_INVENTORY_CANNOT_SET_RUN_FLOOR.name()));
	}

	@Test
	void runIdsAreUniqueAcrossTrackingSessionInstances() {
		FakeTrackerClock clock = new FakeTrackerClock(Instant.EPOCH);
		TrackingSession first = new TrackingSession("live", "server", clock, new DiagnosticRecorder(clock));
		TrackingSession second = new TrackingSession("live", "server", clock, new DiagnosticRecorder(clock));

		RunSession firstRun = first.startRun(RunMode.DUNGEON, DungeonFloor.M5, DetectionSource.STRUCTURED_CHAT, EvidenceStrength.STRUCTURED_CHAT);
		RunSession secondRun = second.startRun(RunMode.DUNGEON, DungeonFloor.M5, DetectionSource.STRUCTURED_CHAT, EvidenceStrength.STRUCTURED_CHAT);

		assertFalse(firstRun.id().equals(secondRun.id()));
	}

	@Test
	void oneRunCompletionCountsAtMostOncePerSession() {
		FakeTrackerClock clock = new FakeTrackerClock(Instant.EPOCH);
		DiagnosticRecorder diagnostics = new DiagnosticRecorder(clock);
		TrackingSession session = new TrackingSession("test", "server", clock, diagnostics);
		session.startRun(RunMode.DUNGEON, DungeonFloor.M7, DetectionSource.CONFIRMED_SCOREBOARD, EvidenceStrength.CONFIRMED_SCOREBOARD);

		assertTrue(session.completeActiveRun(DungeonFloor.M7, "S+", "run-1-score"));
		assertTrue(session.completeActiveRun(DungeonFloor.M7, "S+", "run-2-score"));

		TrackingSnapshot snapshot = session.snapshot();
		assertEquals(2, snapshot.completedRuns());
	}

	@Test
	void dungeonAndKuudraDoNotShareActiveRunState() {
		FakeTrackerClock clock = new FakeTrackerClock(Instant.EPOCH);
		DiagnosticRecorder diagnostics = new DiagnosticRecorder(clock);
		TrackingSession session = new TrackingSession("test", "server", clock, diagnostics);
		RunSession dungeon = session.startRun(RunMode.DUNGEON, DungeonFloor.M7, DetectionSource.CONFIRMED_SCOREBOARD, EvidenceStrength.CONFIRMED_SCOREBOARD);
		RunSession kuudra = session.startRun(RunMode.KUUDRA, DungeonFloor.K5, DetectionSource.CONFIRMED_SCOREBOARD, EvidenceStrength.CONFIRMED_SCOREBOARD);

		TrackingSnapshot snapshot = session.snapshot();
		assertEquals(RunState.ABANDONED, snapshot.runStates().get(dungeon.id()));
		assertEquals(RunState.ACTIVE, snapshot.runStates().get(kuudra.id()));
		assertEquals(RunMode.KUUDRA, snapshot.activeMode());
		assertEquals(DungeonFloor.K5, snapshot.activeFloor());
	}

	@Test
	void runModeConflictReportsDungeonKuudraInvariant() {
		FakeTrackerClock clock = new FakeTrackerClock(Instant.EPOCH);
		DiagnosticRecorder diagnostics = new DiagnosticRecorder(clock);
		TrackingSession session = new TrackingSession("test", "server", clock, diagnostics);
		RunSession run = session.startRun(RunMode.DUNGEON, DungeonFloor.M7, DetectionSource.CONFIRMED_SCOREBOARD, EvidenceStrength.CONFIRMED_SCOREBOARD);
		DetectionEvent event = diagnostics.recordEvent(DetectionEventType.RUN_EVIDENCE, DetectionSource.CONFIRMED_SCOREBOARD, java.util.Map.of());

		assertEquals(EvidenceDecision.CONFLICT, run.updateMode(RunMode.KUUDRA, EvidenceStrength.CONFIRMED_SCOREBOARD, DetectionSource.CONFIRMED_SCOREBOARD, event, diagnostics));
		assertTrue(diagnostics.incidents().stream()
			.anyMatch(incident -> incident.invariants().contains(TrackerInvariant.DUNGEON_AND_KUUDRA_CONTEXTS_CANNOT_SHARE_RUN_STATE)));
	}

	@Test
	void chestModeConflictReportsDungeonKuudraInvariant() {
		FakeTrackerClock clock = new FakeTrackerClock(Instant.EPOCH);
		DiagnosticRecorder diagnostics = new DiagnosticRecorder(clock);
		TrackingSession session = new TrackingSession("test", "server", clock, diagnostics);
		RunSession run = session.startRun(RunMode.DUNGEON, DungeonFloor.M7, DetectionSource.CONFIRMED_SCOREBOARD, EvidenceStrength.CONFIRMED_SCOREBOARD);
		ChestSession chest = session.openChest(run.id(), "Bedrock Chest", 42, DetectionSource.CONFIRMED_GUI_COMPONENT);

		assertFalse(session.updateChestContextMode(chest.id(), RunMode.KUUDRA, EvidenceStrength.CONFIRMED_GUI_COMPONENT, DetectionSource.CONFIRMED_GUI_COMPONENT));
		assertTrue(session.snapshot().invariants().contains(TrackerInvariant.DUNGEON_AND_KUUDRA_CONTEXTS_CANNOT_SHARE_RUN_STATE.name()));
	}

	@Test
	void sameRunSessionRejectsDelayedDuplicateCompletion() {
		FakeTrackerClock clock = new FakeTrackerClock(Instant.EPOCH);
		DiagnosticRecorder diagnostics = new DiagnosticRecorder(clock);
		DetectionEvent event = diagnostics.recordEvent(DetectionEventType.RUN_COMPLETED, DetectionSource.CONFIRMED_COMPLETION, java.util.Map.of());
		RunSession run = new RunSession("run-1", Instant.EPOCH);

		assertTrue(run.complete("fingerprint", event, diagnostics));
		assertFalse(run.complete("fingerprint", event, diagnostics));

		assertEquals(RunState.COMPLETED, run.state());
		assertTrue(diagnostics.incidents().stream()
			.anyMatch(incident -> incident.invariants().contains(TrackerInvariant.ONE_RUN_COMPLETION_COUNTS_AT_MOST_ONCE)));
	}

	@Test
	void completedRunRejectsLaterEvidenceMutation() {
		FakeTrackerClock clock = new FakeTrackerClock(Instant.EPOCH);
		DiagnosticRecorder diagnostics = new DiagnosticRecorder(clock);
		DetectionEvent event = diagnostics.recordEvent(DetectionEventType.RUN_COMPLETED, DetectionSource.CONFIRMED_COMPLETION, java.util.Map.of());
		RunSession run = new RunSession("run-1", Instant.EPOCH);
		run.updateFloor(DungeonFloor.M7, EvidenceStrength.CONFIRMED_SCOREBOARD, DetectionSource.CONFIRMED_SCOREBOARD, event, diagnostics);
		assertTrue(run.complete("fingerprint", event, diagnostics));

		assertEquals(EvidenceDecision.REJECTED_WEAKER, run.updateFloor(DungeonFloor.K5, EvidenceStrength.CONFIRMED_COMPLETION, DetectionSource.CONFIRMED_COMPLETION, event, diagnostics));
		assertEquals(DungeonFloor.M7, run.floor().value());
		assertTrue(diagnostics.incidents().stream()
			.anyMatch(incident -> incident.invariants().contains(TrackerInvariant.COMMITTED_RUN_IS_IMMUTABLE)));
	}

	@Test
	void orphanChestDuringActiveRunDoesNotMutateActiveRun() {
		FakeTrackerClock clock = new FakeTrackerClock(Instant.EPOCH);
		DiagnosticRecorder diagnostics = new DiagnosticRecorder(clock);
		TrackingSession session = new TrackingSession("test", "server", clock, diagnostics);
		session.startRun(RunMode.DUNGEON, DungeonFloor.M7, DetectionSource.CONFIRMED_SCOREBOARD, EvidenceStrength.CONFIRMED_SCOREBOARD);

		ChestSession chest = session.openChest("", "Bedrock Chest", 42, DetectionSource.CONFIRMED_GUI_COMPONENT);
		session.observeLoot(chest.id(), new LootObservation(
			"obs-1",
			1L,
			DetectionSource.CONFIRMED_GUI_COMPONENT,
			"Wither Essence",
			"WITHER ESSENCE",
			"ESSENCE_WITHER",
			LootIdentityStrength.EXACT_COMPONENT_ID,
			100,
			42,
			13,
			SlotOwner.SERVER_CONTAINER,
			"gui|42|13|ESSENCE_WITHER"
		));

		TrackingSnapshot snapshot = session.snapshot();
		assertEquals(DungeonFloor.M7, snapshot.activeFloor());
		assertEquals("", snapshot.chestOwners().get(chest.id()));
		assertTrue(snapshot.invariants().contains(TrackerInvariant.HISTORICAL_REWARD_CLAIM_CANNOT_MUTATE_ACTIVE_RUN.name()));
		assertTrue(snapshot.invariants().contains(TrackerInvariant.PHYSICAL_LOCATION_DOES_NOT_IMPLY_CHEST_OWNERSHIP.name()));
	}

	@Test
	void distinctGuiStacksWithSameQuantityAreNotCollapsed() {
		FakeTrackerClock clock = new FakeTrackerClock(Instant.EPOCH);
		DiagnosticRecorder diagnostics = new DiagnosticRecorder(clock);
		TrackingSession session = new TrackingSession("test", "server", clock, diagnostics);
		RunSession run = session.startRun(RunMode.DUNGEON, DungeonFloor.M5, DetectionSource.CONFIRMED_SCOREBOARD, EvidenceStrength.CONFIRMED_SCOREBOARD);
		ChestSession chest = session.openChest(run.id(), "Gold Chest", 77, DetectionSource.CONFIRMED_GUI_COMPONENT);

		// Regression: a bare "gui|1" dedup key used to drop every subsequent qty=1 GUI stack.
		assertTrue(session.observeLoot(chest.id(), new LootObservation(
			"wither", 1L, DetectionSource.CONFIRMED_GUI_COMPONENT,
			"WITHER ESSENCE", "WITHER ESSENCE", "ESSENCE_WITHER", LootIdentityStrength.STRICT_ALIAS,
			1, 77, 10, SlotOwner.SERVER_CONTAINER,
			"chest|CONFIRMED_GUI_COMPONENT|77|10|id:ESSENCE_WITHER|gui|1"
		)));
		assertTrue(session.observeLoot(chest.id(), new LootObservation(
			"undead", 2L, DetectionSource.CONFIRMED_GUI_COMPONENT,
			"UNDEAD ESSENCE", "UNDEAD ESSENCE", "ESSENCE_UNDEAD", LootIdentityStrength.STRICT_ALIAS,
			1, 77, 11, SlotOwner.SERVER_CONTAINER,
			"chest|CONFIRMED_GUI_COMPONENT|77|11|id:ESSENCE_UNDEAD|gui|1"
		)));
		assertTrue(session.observeLoot(chest.id(), new LootObservation(
			"book", 3L, DetectionSource.CONFIRMED_GUI_COMPONENT,
			"Enchanted Book (Rejuvenate I)", "ENCHANTED BOOK (REJUVENATE I)", "ENCHANTMENT_REJUVENATE_1", LootIdentityStrength.STRICT_ALIAS,
			1, 77, 12, SlotOwner.SERVER_CONTAINER,
			"chest|CONFIRMED_GUI_COMPONENT|77|12|id:ENCHANTMENT_REJUVENATE_1|gui|1"
		)));

		assertEquals(3, chest.resolvedLoot().size());
	}

	@Test
	void lootFromDifferentChestsDoesNotMerge() {
		FakeTrackerClock clock = new FakeTrackerClock(Instant.EPOCH);
		DiagnosticRecorder diagnostics = new DiagnosticRecorder(clock);
		TrackingSession session = new TrackingSession("test", "server", clock, diagnostics);
		RunSession run = session.startRun(RunMode.DUNGEON, DungeonFloor.M7, DetectionSource.CONFIRMED_SCOREBOARD, EvidenceStrength.CONFIRMED_SCOREBOARD);
		ChestSession first = session.openChest(run.id(), "Bedrock Chest", 101, DetectionSource.CONFIRMED_GUI_COMPONENT);
		ChestSession second = session.openChest(run.id(), "Bedrock Chest", 102, DetectionSource.CONFIRMED_GUI_COMPONENT);

		session.observeLoot(first.id(), new LootObservation("first", 1L, DetectionSource.CONFIRMED_GUI_COMPONENT, "Wither Essence", "WITHER ESSENCE", "ESSENCE_WITHER", LootIdentityStrength.EXACT_COMPONENT_ID, 100, 101, 13, SlotOwner.SERVER_CONTAINER, "gui|101|13|ESSENCE_WITHER"));
		session.observeLoot(second.id(), new LootObservation("second", 2L, DetectionSource.CONFIRMED_GUI_COMPONENT, "Wither Essence", "WITHER ESSENCE", "ESSENCE_WITHER", LootIdentityStrength.EXACT_COMPONENT_ID, 100, 102, 13, SlotOwner.SERVER_CONTAINER, "gui|102|13|ESSENCE_WITHER"));

		TrackingSnapshot snapshot = session.snapshot();
		assertEquals(2, snapshot.chestLoot().values().stream().filter(loot -> !loot.isEmpty()).count());
		for (List<ResolvedLoot> loot : snapshot.chestLoot().values()) {
			if (!loot.isEmpty()) assertEquals(100, loot.getFirst().quantity());
		}
	}

	@Test
	void playerInventoryCannotSetChestCost() {
		FakeTrackerClock clock = new FakeTrackerClock(Instant.EPOCH);
		DiagnosticRecorder diagnostics = new DiagnosticRecorder(clock);
		TrackingSession session = new TrackingSession("test", "server", clock, diagnostics);
		RunSession run = session.startRun(RunMode.DUNGEON, DungeonFloor.M7, DetectionSource.CONFIRMED_SCOREBOARD, EvidenceStrength.CONFIRMED_SCOREBOARD);
		ChestSession chest = session.openChest(run.id(), "Bedrock Chest", 42, DetectionSource.CONFIRMED_GUI_COMPONENT);
		ChestCostBreakdown cost = new ChestCostBreakdown(10_000_000L);

		assertFalse(session.updateChestCost(chest.id(), cost, DetectionSource.PLAYER_INVENTORY));
		assertEquals(0L, session.snapshot().chestCosts().get(chest.id()).totalCostCoins());
		assertTrue(session.snapshot().invariants().contains(TrackerInvariant.PLAYER_INVENTORY_CANNOT_SET_CHEST_COST.name()));
	}

	@Test
	void ownedChestInheritsRunModeAndFloorContext() {
		FakeTrackerClock clock = new FakeTrackerClock(Instant.EPOCH);
		DiagnosticRecorder diagnostics = new DiagnosticRecorder(clock);
		TrackingSession session = new TrackingSession("test", "server", clock, diagnostics);
		RunSession run = session.startRun(RunMode.DUNGEON, DungeonFloor.M7, DetectionSource.CONFIRMED_SCOREBOARD, EvidenceStrength.CONFIRMED_SCOREBOARD);
		ChestSession chest = session.openChest(run.id(), "Bedrock Chest", 42, DetectionSource.CONFIRMED_GUI_COMPONENT);

		TrackingSnapshot snapshot = session.snapshot();
		assertEquals(RunMode.DUNGEON, snapshot.chestModes().get(chest.id()));
		assertEquals(DungeonFloor.M7, snapshot.chestFloors().get(chest.id()));
	}

	@Test
	void playerInventoryCannotSetChestContext() {
		FakeTrackerClock clock = new FakeTrackerClock(Instant.EPOCH);
		DiagnosticRecorder diagnostics = new DiagnosticRecorder(clock);
		TrackingSession session = new TrackingSession("test", "server", clock, diagnostics);
		RunSession run = session.startRun(RunMode.DUNGEON, DungeonFloor.M7, DetectionSource.CONFIRMED_SCOREBOARD, EvidenceStrength.CONFIRMED_SCOREBOARD);
		ChestSession chest = session.openChest(run.id(), "Bedrock Chest", 42, DetectionSource.CONFIRMED_GUI_COMPONENT);

		assertFalse(session.updateChestContextFloor(chest.id(), DungeonFloor.K5, EvidenceStrength.FALLBACK_GUESS, DetectionSource.PLAYER_INVENTORY));
		assertFalse(session.updateChestContextMode(chest.id(), RunMode.KUUDRA, EvidenceStrength.FALLBACK_GUESS, DetectionSource.PLAYER_INVENTORY));
		TrackingSnapshot snapshot = session.snapshot();
		assertEquals(DungeonFloor.M7, snapshot.chestFloors().get(chest.id()));
		assertEquals(RunMode.DUNGEON, snapshot.chestModes().get(chest.id()));
		assertTrue(snapshot.invariants().contains(TrackerInvariant.PLAYER_INVENTORY_CANNOT_SET_RUN_FLOOR.name()));
		assertTrue(snapshot.invariants().contains(TrackerInvariant.PLAYER_INVENTORY_CANNOT_SET_RUN_MODE.name()));
	}

	@Test
	void committedChestCostIsImmutable() {
		FakeTrackerClock clock = new FakeTrackerClock(Instant.EPOCH);
		DiagnosticRecorder diagnostics = new DiagnosticRecorder(clock);
		TrackingSession session = new TrackingSession("test", "server", clock, diagnostics);
		RunSession run = session.startRun(RunMode.DUNGEON, DungeonFloor.M7, DetectionSource.CONFIRMED_SCOREBOARD, EvidenceStrength.CONFIRMED_SCOREBOARD);
		ChestSession chest = session.openChest(run.id(), "Bedrock Chest", 42, DetectionSource.CONFIRMED_GUI_COMPONENT);
		ChestCostBreakdown cost = new ChestCostBreakdown(10_000_000L);
		assertTrue(session.updateChestCost(chest.id(), cost));
		session.commitChest(chest.id(), "commit-1");

		assertFalse(session.updateChestCost(chest.id(), new ChestCostBreakdown(20_000_000L)));
		TrackingSnapshot snapshot = session.snapshot();
		assertEquals(10_000_000L, snapshot.chestCosts().get(chest.id()).totalCostCoins());
		assertTrue(snapshot.invariants().contains(TrackerInvariant.COMMITTED_CHEST_IS_IMMUTABLE.name()));
	}

	@Test
	void committedChestContextIsImmutable() {
		FakeTrackerClock clock = new FakeTrackerClock(Instant.EPOCH);
		DiagnosticRecorder diagnostics = new DiagnosticRecorder(clock);
		TrackingSession session = new TrackingSession("test", "server", clock, diagnostics);
		RunSession run = session.startRun(RunMode.DUNGEON, DungeonFloor.M7, DetectionSource.CONFIRMED_SCOREBOARD, EvidenceStrength.CONFIRMED_SCOREBOARD);
		ChestSession chest = session.openChest(run.id(), "Bedrock Chest", 42, DetectionSource.CONFIRMED_GUI_COMPONENT);
		session.commitChest(chest.id(), "commit-1");

		assertFalse(session.updateChestContextFloor(chest.id(), DungeonFloor.K5, EvidenceStrength.CONFIRMED_COMPLETION, DetectionSource.CONFIRMED_COMPLETION));
		TrackingSnapshot snapshot = session.snapshot();
		assertEquals(DungeonFloor.M7, snapshot.chestFloors().get(chest.id()));
		assertTrue(snapshot.invariants().contains(TrackerInvariant.COMMITTED_CHEST_IS_IMMUTABLE.name()));
	}
}
