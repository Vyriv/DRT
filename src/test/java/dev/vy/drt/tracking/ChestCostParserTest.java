package dev.vy.drt.tracking;

import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class ChestCostParserTest {
	@Test
	void distinguishesFreePaidAndUnknownCosts() {
		assertEquals(0L, ChestCostParser.parseLore(List.of("FREE")));
		assertEquals(0L, ChestCostParser.parseLore(List.of("Cost: Free")));
		assertEquals(400_000L, ChestCostParser.parseLore(List.of("Cost", "400,000 Coins")));
		assertEquals(2_500_000L, ChestCostParser.parseLore(List.of("Cost: 2.5m Coins")));
		assertNull(ChestCostParser.parseLore(List.of("Contents", "Rejuvenate I")));
	}
}
