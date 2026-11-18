package dev.vy.drt.tracking;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class RewardChestCaptureTest {
	@Test
	void unconfirmedPreviewCaptureIsDiscardedWhenTheOpenButtonAppears() {
		assertTrue(RewardChestCapture.shouldDiscardUnconfirmedCapture(true, false));
		assertFalse(RewardChestCapture.shouldDiscardUnconfirmedCapture(true, true));
		assertFalse(RewardChestCapture.shouldDiscardUnconfirmedCapture(false, false));
	}

	@Test
	void laterSnapshotIsReadOnlyWhileTheSameChestIsStillCollecting() {
		assertTrue(RewardChestCapture.shouldReadLaterSnapshot(true, true));
		assertFalse(RewardChestCapture.shouldReadLaterSnapshot(true, false));
		assertFalse(RewardChestCapture.shouldReadLaterSnapshot(false, true));
	}

	@Test
	void rareRewardInsideTheChestBlockIsKeptWhenItArrivesBeforeOtherLoot() {
		assertTrue(RewardChestCapture.includeRareRewardLine(true, true, false));
		assertTrue(RewardChestCapture.includeRareRewardLine(true, false, false));
	}

	@Test
	void strayRareRewardDoesNotSeedAnEmptyChest() {
		assertFalse(RewardChestCapture.includeRareRewardLine(false, true, false));
		assertTrue(RewardChestCapture.includeRareRewardLine(false, false, false));
		assertTrue(RewardChestCapture.includeRareRewardLine(false, true, true));
	}

	@Test
	void chatAndGuiObservationsOfTheSameRareRewardStayOneCopy() {
		List<ResolvedLoot> resolved = LootReconciler.reconcile("m5-bedrock", List.of(
			loot("gui-recomb", 1L, "Recombobulator 3000", "RECOMBOBULATOR_3000", 1),
			loot("chat-recomb", 2L, "Recombobulator 3000", "RECOMBOBULATOR_3000", 1),
			loot("chat-book", 3L, "Fuming Potato Book", "FUMING_POTATO_BOOK", 1),
			loot("essence", 4L, "Wither Essence", "ESSENCE_WITHER", 86)
		), null);

		assertEquals(3, resolved.size());
		assertEquals(1, quantity(resolved, "RECOMBOBULATOR_3000"));
		assertEquals(1, quantity(resolved, "FUMING_POTATO_BOOK"));
		assertEquals(86, quantity(resolved, "ESSENCE_WITHER"));
	}

	@Test
	void chatAuthoritativeScanAddsOnlyItemsChatMissed() {
		assertTrue(RewardChestCapture.shouldAddGuiItem(false, false));
		assertTrue(RewardChestCapture.shouldAddGuiItem(true, false));
		assertFalse(RewardChestCapture.shouldAddGuiItem(true, true));
		assertTrue(RewardChestCapture.shouldAddGuiItem(false, true));
	}

	@Test
	void delayedEssenceIsAddedWithoutDroppingOrDoublingEarlierRewards() {
		DiagnosticRecorder diagnostics = new DiagnosticRecorder(new FakeTrackerClock(Instant.EPOCH));
		List<ResolvedLoot> resolved = LootReconciler.reconcile("m7-bedrock", List.of(
			loot("item-1", 1L, "Wither Chestplate", "WITHER_CHESTPLATE", 1),
			loot("item-2", 2L, "Wither Leggings", "WITHER_LEGGINGS", 1),
			loot("item-3", 3L, "Wither Boots", "WITHER_BOOTS", 1),
			loot("item-4", 4L, "Recombobulator 3000", "RECOMBOBULATOR_3000", 1),
			loot("item-5", 5L, "Necron's Handle", "NECRON_HANDLE", 1),
			loot("essence-late", 6L, "Undead Essence", "ESSENCE_UNDEAD", 70),
			loot("essence-again", 7L, "Undead Essence", "ESSENCE_UNDEAD", 70)
		), diagnostics);

		assertEquals(6, resolved.size());
		assertEquals(1, quantity(resolved, "WITHER_CHESTPLATE"));
		assertEquals(1, quantity(resolved, "NECRON_HANDLE"));
		assertEquals(70, quantity(resolved, "ESSENCE_UNDEAD"));
	}

	private static LootObservation loot(String id, long sequence, String name, String itemId, int quantity) {
		return new LootObservation(
			id,
			sequence,
			DetectionSource.CONFIRMED_GUI_COMPONENT,
			name,
			name.toUpperCase(),
			itemId,
			LootIdentityStrength.EXACT_COMPONENT_ID,
			quantity,
			4,
			sequence > Integer.MAX_VALUE ? 0 : (int) sequence,
			SlotOwner.SERVER_CONTAINER,
			"gui|" + id
		);
	}

	private static int quantity(List<ResolvedLoot> resolved, String itemId) {
		return resolved.stream()
			.filter(entry -> itemId.equals(entry.itemId()))
			.mapToInt(ResolvedLoot::quantity)
			.findFirst()
			.orElse(-1);
	}
}
