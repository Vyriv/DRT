package dev.vy.drt.history;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.vy.drt.config.DungeonLootEntry;
import dev.vy.drt.config.DungeonRunRecord;
import dev.vy.drt.luck.ChestTier;
import dev.vy.drt.luck.capture.LuckRunRecord;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Chest tabs show what each offered chest held without ever inventing or counting rewards. */
class ChestOfferTabsTest {
	private static DungeonRunRecord purchase(String title, long cost, DungeonLootEntry... loot) {
		return new DungeonRunRecord(1L, 1, "F5", "S+", title, cost, 0L, 0L, new ArrayList<>(List.of(loot)));
	}

	private static LuckRunRecord.LuckChestRecord chest(String tier, Long cost, LuckRunRecord.LuckContentLine... lines) {
		LuckRunRecord.LuckChestRecord chest = new LuckRunRecord.LuckChestRecord();
		chest.tier = tier;
		chest.costCoins = cost;
		if (lines.length > 0) {
			LuckRunRecord.LuckOfferSnapshot offer = new LuckRunRecord.LuckOfferSnapshot();
			offer.contents.addAll(List.of(lines));
			chest.offers.add(offer);
		}
		return chest;
	}

	private static LuckRunRecord.LuckContentLine line(String text, String name, int quantity, String kind, String key) {
		LuckRunRecord.LuckContentLine line = new LuckRunRecord.LuckContentLine();
		line.text = text;
		line.name = name;
		line.quantity = quantity;
		line.kind = kind;
		line.key = key;
		return line;
	}

	@Test
	void legacyRecordWithoutARunShowsTheSimpleView() {
		List<ChestOfferTabs.Tab> tabs = ChestOfferTabs.build(null, List.of(purchase("Bedrock Chest", 2_000_000L)));
		assertEquals(1, tabs.size());
		assertFalse(ChestOfferTabs.showTabs(tabs));
	}

	@Test
	void tabsCoverOnlyOfferedChestsInChestOrder() {
		LuckRunRecord run = new LuckRunRecord();
		run.chests.add(chest("OBSIDIAN", 1_000_000L, line("Recombobulator 3000", "Recombobulator 3000", 1, "ITEM", "RECOMBOBULATOR_3000")));
		run.chests.add(chest("WOOD", 0L));
		List<ChestOfferTabs.Tab> tabs = ChestOfferTabs.build(run, List.of(purchase("Bedrock Chest", 2_000_000L, new DungeonLootEntry("Necron's Handle", "NECRON_HANDLE", 1))));
		assertEquals(List.of(ChestTier.WOOD, ChestTier.OBSIDIAN, ChestTier.BEDROCK), tabs.stream().map(ChestOfferTabs.Tab::tier).toList());
		assertTrue(ChestOfferTabs.showTabs(tabs));

		ChestOfferTabs.Tab wood = tabs.get(0);
		assertEquals(ChestOfferTabs.State.NOT_RECORDED, wood.state());
		assertTrue(wood.contents().isEmpty(), "an unseen chest is never shown as empty loot");
		assertFalse(wood.purchased());

		ChestOfferTabs.Tab obsidian = tabs.get(1);
		assertEquals(ChestOfferTabs.State.OBSERVED, obsidian.state());
		assertFalse(obsidian.purchased());
		assertEquals("RECOMBOBULATOR_3000", obsidian.contents().get(0).itemId);
		assertEquals(1_000_000L, obsidian.costCoins());

		ChestOfferTabs.Tab bedrock = tabs.get(2);
		assertEquals(ChestOfferTabs.State.PURCHASED_LOGGED, bedrock.state());
		assertTrue(bedrock.purchased());
		assertEquals("NECRON_HANDLE", bedrock.contents().get(0).itemId);
		assertEquals(2_000_000L, bedrock.costCoins());
		assertEquals(1, bedrock.purchases().size());
	}

	@Test
	void expectedChestsWithoutOffersAreNotRecorded() {
		LuckRunRecord run = new LuckRunRecord();
		run.coverage = new dev.vy.drt.luck.capture.LuckCoverage();
		run.coverage.expectedChests = new ArrayList<>(List.of("GOLD", "DIAMOND"));
		List<ChestOfferTabs.Tab> tabs = ChestOfferTabs.build(run, List.of(purchase("Diamond Chest", 100_000L)));
		assertEquals(ChestOfferTabs.State.NOT_RECORDED, tabs.get(0).state());
		assertNull(tabs.get(0).costCoins());
		assertEquals(ChestOfferTabs.State.PURCHASED_LOGGED, tabs.get(1).state());
	}

	@Test
	void purchasedInCroesusButNotLoggedIsMarkedPurchased() {
		LuckRunRecord run = new LuckRunRecord();
		LuckRunRecord.LuckChestRecord bought = chest("BEDROCK", 2_000_000L, line("Shadow Warp", "Shadow Warp", 1, "ENCHANTMENT", "ENCHANTMENT_ULTIMATE_SHADOW_WARP_1"));
		bought.purchased = true;
		run.chests.add(bought);
		run.chests.add(chest("OBSIDIAN", 1_000_000L));
		List<ChestOfferTabs.Tab> tabs = ChestOfferTabs.build(run, List.of());
		ChestOfferTabs.Tab bedrock = tabs.get(1);
		assertEquals(ChestOfferTabs.State.OBSERVED, bedrock.state());
		assertTrue(bedrock.purchased());
	}

	@Test
	void defaultTabIsTheSelectedChestThenAPurchasedOne() {
		LuckRunRecord run = new LuckRunRecord();
		run.chests.add(chest("WOOD", 0L));
		run.chests.add(chest("OBSIDIAN", 1L));
		List<ChestOfferTabs.Tab> tabs = ChestOfferTabs.build(run, List.of(purchase("Bedrock Chest", 2L)));
		assertEquals(2, ChestOfferTabs.defaultIndex(tabs, "Bedrock Chest"));
		assertEquals(1, ChestOfferTabs.defaultIndex(tabs, "Obsidian"));
		assertEquals(2, ChestOfferTabs.defaultIndex(tabs, "Paid Chest"));
		assertEquals(-1, ChestOfferTabs.defaultIndex(List.of(), "Bedrock Chest"));
	}

	@Test
	void stepStopsAtEitherEnd() {
		assertEquals(0, ChestOfferTabs.step(0, -1, 3));
		assertEquals(1, ChestOfferTabs.step(0, 1, 3));
		assertEquals(2, ChestOfferTabs.step(2, 1, 3));
		assertEquals(-1, ChestOfferTabs.step(0, 1, 0));
	}

	@Test
	void offerLinesPriceLikeLoggedLoot() {
		DungeonLootEntry essence = ChestOfferTabs.lootEntry(line("Undead Essence x19", "Undead Essence", 19, "ESSENCE", "ESSENCE_UNDEAD_19"));
		assertEquals("ESSENCE_UNDEAD", essence.itemId);
		assertEquals(19, essence.quantity);
		DungeonLootEntry unnamed = ChestOfferTabs.lootEntry(line("Mystery Item", "", 1, null, null));
		assertEquals("Mystery Item", unnamed.rawName);
		assertEquals("", unnamed.itemId);
	}
}
