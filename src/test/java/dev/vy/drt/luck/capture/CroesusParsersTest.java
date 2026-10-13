package dev.vy.drt.luck.capture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.vy.drt.luck.ChestTier;
import dev.vy.drt.luck.FloorId;
import dev.vy.drt.luck.LuckTestData;
import dev.vy.drt.luck.data.ChestTable;
import dev.vy.drt.luck.data.FloorTable;
import dev.vy.drt.luck.data.LootEntry;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Croesus page and run view formats as recorded in game on 2026-10-11. */
class CroesusParsersTest {
	private static final long SECOND = 1_000L;
	private static final long HOUR = 3_600_000L;

	@Test
	void menuPageListsTwentyEightRunsNewestFirst() {
		CroesusMenuParser.Menu menu = CroesusMenuParser.parse(ProbeFixtures.screen(ProbeFixtures.CROESUS_MENU));
		assertNotNull(menu);
		assertEquals(1, menu.page());
		assertEquals(2, menu.pageCount());
		assertTrue(menu.hasNextPage());
		assertFalse(menu.hasPreviousPage());
		assertEquals(28, menu.entries().size());
		for (int i = 0; i < menu.entries().size(); i++) assertEquals(i, menu.entries().get(i).position());
		assertEquals(10, menu.entries().get(0).slot());
		assertEquals(43, menu.entries().get(27).slot());
	}

	@Test
	void newestEntryIsTheJustCompletedNormalFloorFive() {
		CroesusMenuParser.RunEntry entry = CroesusMenuParser.parse(ProbeFixtures.screen(ProbeFixtures.CROESUS_MENU)).entryAt(10);
		assertEquals("The Catacombs", entry.name());
		assertFalse(entry.masterMode());
		assertTrue(entry.catacombs());
		assertEquals(FloorId.F5, entry.floor());
		assertEquals(List.of("LocalPlayer"), entry.players());
		assertEquals("15 secs ago", entry.completedText());
		assertEquals("2d 23h", entry.expiresText());
		assertTrue(entry.noChestsOpened());
		assertEquals(List.of(), entry.openedChests());
		assertEquals(List.of("Kismet Feather", "Dungeon Chest Key"), entry.availableModifiers());
		assertEquals(15 * SECOND, entry.completedAgoMin());
		assertEquals(16 * SECOND, entry.completedAgoMax());
	}

	@Test
	void masterModeEntriesCarryRosterOpenedChestAndNarrowedAge() {
		CroesusMenuParser.Menu menu = CroesusMenuParser.parse(ProbeFixtures.screen(ProbeFixtures.CROESUS_MENU));
		CroesusMenuParser.RunEntry hourAgo = menu.entryAt(11);
		assertEquals(FloorId.M5, hourAgo.floor());
		assertTrue(hourAgo.masterMode());
		assertEquals(List.of("Teammate_A", "Teammate_B", "Teammate_C", "Teammate_D", "LocalPlayer"), hourAgo.players());
		assertEquals(List.of("Bedrock"), hourAgo.openedChests());
		assertFalse(hourAgo.noChestsOpened());
		assertEquals(HOUR, hourAgo.completedAgoMin());
		assertEquals(2 * HOUR, hourAgo.completedAgoMax());

		CroesusMenuParser.RunEntry daysAgo = menu.entryAt(25);
		assertEquals("2 days ago", daysAgo.completedText());
		assertEquals("8h", daysAgo.expiresText());
		// "2 days ago" alone is [48h, 72h); 8h left of a 72h lifetime narrows it to [63h, 64h].
		assertEquals(63 * HOUR, daysAgo.completedAgoMin());
		assertEquals(64 * HOUR, daysAgo.completedAgoMax());
	}

	@Test
	void classLevelsDoNotEnterTheRoster() {
		CroesusMenuParser.Menu menu = CroesusMenuParser.parse(ProbeFixtures.screen(ProbeFixtures.CROESUS_MENU));
		assertEquals(menu.entryAt(34).players(), menu.entryAt(38).players());
	}

	@Test
	void ageTextVariants() {
		assertEquals(5 * 60_000L, CroesusMenuParser.ageBounds("5 mins ago")[0]);
		assertEquals(6 * 60_000L, CroesusMenuParser.ageBounds("5 mins ago")[1]);
		assertEquals(HOUR, CroesusMenuParser.ageBounds("1 hour ago")[0]);
		assertNull(CroesusMenuParser.ageBounds("yesterday"));
		long[] expiry = CroesusMenuParser.expiryAgeBounds("2d 23h");
		assertEquals(0L, expiry[0]);
		assertEquals(HOUR, expiry[1]);
	}

	@Test
	void nonRunSlotsAreIgnored() {
		CroesusMenuParser.Menu menu = CroesusMenuParser.parse(ProbeFixtures.screen(ProbeFixtures.CROESUS_MENU));
		assertNull(menu.entryAt(4));
		assertNull(menu.entryAt(50));
		assertNull(menu.entryAt(53));
		assertNull(CroesusMenuParser.parse(ProbeFixtures.screen(ProbeFixtures.CROESUS_RUN_VIEW_F5)));
	}

	@Test
	void runViewListsEveryChestWithContentsEssenceAndCost() {
		FloorTable f5 = LuckTestData.defaults().floor(FloorId.F5);
		CroesusRunViewParser.RunView view = CroesusRunViewParser.parse(ProbeFixtures.screen(ProbeFixtures.CROESUS_RUN_VIEW_F5), number -> f5);
		assertNotNull(view);
		assertEquals(5, view.floorNumber());
		assertNull(view.masterMode(), "a title without Master Mode leaves the mode unknown");
		assertEquals(List.of("Kismet Feather", "Dungeon Chest Key"), view.availableModifiers());
		assertEquals(List.of(ChestTier.WOOD, ChestTier.GOLD, ChestTier.DIAMOND, ChestTier.EMERALD, ChestTier.OBSIDIAN, ChestTier.BEDROCK),
			view.chests().stream().map(CroesusRunViewParser.ChestOffer::tier).toList());

		CroesusRunViewParser.ChestOffer wood = view.chests().get(0);
		assertEquals(0L, wood.costCoins());
		assertEquals("FREE", wood.costText());
		assertEquals(List.of("ENCHANTMENT_FEATHER_FALLING_6", "ENCHANTMENT_INFINITE_QUIVER_6", "ESSENCE_WITHER_11", "ESSENCE_UNDEAD_16"), keys(wood));
		assertEquals(11, wood.contents().get(2).quantity());

		CroesusRunViewParser.ChestOffer bedrock = view.chests().get(5);
		assertEquals(1_600_000L, bedrock.costCoins());
		assertEquals(List.of("ENCHANTMENT_ULTIMATE_WISE_2", "DARK_ORB", "ENCHANTMENT_ULTIMATE_NO_PAIN_NO_GAIN_2", "ESSENCE_WITHER_40", "ESSENCE_UNDEAD_54"), keys(bedrock));

		assertEquals(80_000L, view.chests().get(1).costCoins());
		assertEquals(200_000L, view.chests().get(2).costCoins());
		assertEquals(400_000L, view.chests().get(3).costCoins());
		assertEquals(800_000L, view.chests().get(4).costCoins());
	}

	@Test
	void everyLiveRolledItemExistsInThatChestsLootTable() {
		FloorTable f5 = LuckTestData.defaults().floor(FloorId.F5);
		CroesusRunViewParser.RunView view = CroesusRunViewParser.parse(ProbeFixtures.screen(ProbeFixtures.CROESUS_RUN_VIEW_F5), number -> f5);
		List<String> missing = new ArrayList<>();
		for (CroesusRunViewParser.ChestOffer offer : view.chests()) {
			ChestTable table = f5.chest(offer.tier());
			for (LuckText.ContentLine line : offer.contents()) {
				if (line.kind() == LootEntry.Kind.ESSENCE) continue;
				if (line.key() == null || !table.contains(line.key())) missing.add(offer.tier() + ": " + line.text() + " -> " + line.key());
			}
		}
		assertEquals(List.of(), missing);
	}

	@Test
	void masterModeTitleAndUnknownTitles() {
		assertTrue(CroesusRunViewParser.isRunViewTitle("Catacombs - Floor VII"));
		assertTrue(CroesusRunViewParser.isRunViewTitle("Master Mode Catacombs - Floor V"));
		assertFalse(CroesusRunViewParser.isRunViewTitle("Catacombs RNG Meters"));
		CroesusRunViewParser.RunView master = CroesusRunViewParser.parse(
			ProbeFixtures.retitled(ProbeFixtures.screen(ProbeFixtures.CROESUS_RUN_VIEW_F5), "Master Mode Catacombs - Floor V"), number -> null);
		assertEquals(Boolean.TRUE, master.masterMode());
		assertNull(master.chests().get(5).contents().get(1).key(), "item names stay unresolved without a floor table");
	}

	@Test
	void hypixelMasterCatacombsTitleIsAMasterModeRunView() {
		assertTrue(CroesusRunViewParser.isRunViewTitle("Master Catacombs - Floor V"));
		assertTrue(CroesusRunViewParser.isRunViewTitle("Master Catacombs - Floor VII"));
		assertFalse(CroesusRunViewParser.isRunViewTitle("Master Catacombs RNG Meter"));
		CroesusRunViewParser.RunView master = CroesusRunViewParser.parse(
			ProbeFixtures.retitled(ProbeFixtures.screen(ProbeFixtures.CROESUS_RUN_VIEW_F5), "Master Catacombs - Floor V"), number -> null);
		assertEquals(5, master.floorNumber());
		assertEquals(Boolean.TRUE, master.masterMode());
		assertEquals(6, master.chests().size(), "every chest offer is read, opened or not");
	}

	private static List<String> keys(CroesusRunViewParser.ChestOffer offer) {
		return offer.contents().stream().map(LuckText.ContentLine::key).toList();
	}
}
