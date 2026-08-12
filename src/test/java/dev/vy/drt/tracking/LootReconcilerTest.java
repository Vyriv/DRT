package dev.vy.drt.tracking;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class LootReconcilerTest {
	@Test
	void guiAndChatQuantitiesAreNotBlindlyAdded() {
		DiagnosticRecorder diagnostics = new DiagnosticRecorder(new FakeTrackerClock(Instant.EPOCH));
		List<ResolvedLoot> resolved = LootReconciler.reconcile("chest-1", List.of(
			new LootObservation("gui", 1L, DetectionSource.CONFIRMED_GUI_COMPONENT, "Wither Essence", "WITHER ESSENCE", "ESSENCE_WITHER", LootIdentityStrength.EXACT_COMPONENT_ID, 100, 7, 13, SlotOwner.SERVER_CONTAINER, "gui|7|13|ESSENCE_WITHER"),
			new LootObservation("chat", 2L, DetectionSource.STRUCTURED_CHAT, "WITHER ESSENCE", "WITHER ESSENCE", "ESSENCE_WITHER", LootIdentityStrength.STRUCTURED_CHAT, 100, 7, -1, SlotOwner.SERVER_CONTAINER, "chat|7|WITHER ESSENCE")
		), diagnostics);

		assertEquals(1, resolved.size());
		assertEquals(100, resolved.getFirst().quantity());
		assertEquals(2, resolved.getFirst().observations().size());
	}

	@Test
	void sameSourceQuantityDifferencesAreNotAssumedSeparateGrants() {
		DiagnosticRecorder diagnostics = new DiagnosticRecorder(new FakeTrackerClock(Instant.EPOCH));
		List<ResolvedLoot> resolved = LootReconciler.reconcile("chest-1", List.of(
			new LootObservation("chat-1", 1L, DetectionSource.STRUCTURED_CHAT, "WITHER ESSENCE", "WITHER ESSENCE", "ESSENCE_WITHER", LootIdentityStrength.STRUCTURED_CHAT, 100, 7, -1, SlotOwner.SERVER_CONTAINER, "chat|first"),
			new LootObservation("chat-2", 2L, DetectionSource.STRUCTURED_CHAT, "WITHER ESSENCE", "WITHER ESSENCE", "ESSENCE_WITHER", LootIdentityStrength.STRUCTURED_CHAT, 120, 7, -1, SlotOwner.SERVER_CONTAINER, "chat|second")
		), diagnostics);

		assertEquals(1, resolved.size());
		assertEquals(120, resolved.getFirst().quantity());
		assertEquals("max_quantity_after_quantity_conflict", resolved.getFirst().resolutionReason());
		assertTrue(diagnostics.incidents().stream()
			.flatMap(incident -> incident.invariants().stream())
			.anyMatch(invariant -> invariant == TrackerInvariant.LOOT_QUANTITY_CONFLICT));
	}

	@Test
	void unknownIdentityRemainsUnresolved() {
		List<ResolvedLoot> resolved = LootReconciler.reconcile("chest-1", List.of(
			new LootObservation("chat", 1L, DetectionSource.STRUCTURED_CHAT, "Ambiguous Fancy Thing", "AMBIGUOUS FANCY THING", "", LootIdentityStrength.UNRESOLVED, 1, 7, -1, SlotOwner.SERVER_CONTAINER, "chat|ambiguous")
		), null);

		assertEquals(1, resolved.size());
		assertTrue(!resolved.getFirst().resolved());
		assertEquals("", resolved.getFirst().itemId());
	}
}
