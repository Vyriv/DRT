package dev.vy.drt.history;

import dev.vy.drt.config.ChestCostBreakdown;
import dev.vy.drt.config.DungeonChestOffer;
import dev.vy.drt.config.DungeonRunCompletionRecord;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HistoricalChestAvailabilityTest {
	@Test
	void infersSupportedCatacombsTiersWithoutChangingHistory() {
		DungeonRunCompletionRecord s = run("M1", "S");
		assertEquals(List.of("Wood", "Gold", "Diamond", "Emerald", "Obsidian"), HistoricalChestAvailability.resolve(s).chestTypes());
		assertTrue(HistoricalChestAvailability.resolve(s).inferred());
		assertTrue(s.chestOffers.isEmpty());

		assertEquals(List.of("Wood", "Gold", "Diamond", "Emerald", "Obsidian", "Bedrock"),
			HistoricalChestAvailability.resolve(run("M1", "S+")).chestTypes());
		assertEquals(List.of("Wood", "Gold", "Diamond", "Emerald", "Obsidian"),
			HistoricalChestAvailability.resolve(run("F4", "S+")).chestTypes());
		assertEquals(List.of("Wood", "Gold", "Diamond", "Emerald", "Obsidian", "Bedrock"),
			HistoricalChestAvailability.resolve(run("F5", "S+")).chestTypes());
	}

	@Test
	void recordedOffersWinAndUnsupportedRunsRemainUnknown() {
		DungeonRunCompletionRecord recorded = run("M5", "S+");
		recorded.chestOffers.add(new DungeonChestOffer("Emerald Chest", new ChestCostBreakdown(), -1L, List.of()));
		var result = HistoricalChestAvailability.resolve(recorded);
		assertEquals(List.of("Emerald"), result.chestTypes());
		assertFalse(result.inferred());
		assertTrue(HistoricalChestAvailability.resolve(run("M5", "A")).chestTypes().isEmpty());
		DungeonRunCompletionRecord kuudra = run("K5", "S+");
		kuudra.mode = "KUUDRA";
		assertTrue(HistoricalChestAvailability.resolve(kuudra).chestTypes().isEmpty());
	}

	private static DungeonRunCompletionRecord run(String floor, String grade) {
		return new DungeonRunCompletionRecord(1L, floor.startsWith("M") ? "MASTER" : "CATACOMBS", floor, grade,
			100_000L, "run", "fingerprint");
	}
}
