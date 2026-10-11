package dev.vy.drt.luck.capture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.vy.drt.luck.ChestTier;
import dev.vy.drt.luck.FloorId;
import dev.vy.drt.luck.LuckTestData;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** RNG Meter screens as recorded in game on 2026-10-11. */
class RngMeterParserTest {
	@Test
	void overviewReadsEveryFloorWithExactStoredScores() {
		RngMeterParser.Overview overview = RngMeterParser.parseOverview(ProbeFixtures.screen(ProbeFixtures.RNG_OVERVIEW));
		assertNotNull(overview);
		Map<FloorId, RngMeterParser.Reading> byFloor = new EnumMap<>(FloorId.class);
		for (RngMeterParser.Reading reading : overview.readings()) byFloor.put(reading.floor(), reading);
		assertEquals(14, byFloor.size());

		Map<FloorId, Long> unselected = Map.of(FloorId.F1, 732L, FloorId.F2, 331L, FloorId.F3, 396L, FloorId.F4, 612L,
			FloorId.F5, 3_224L, FloorId.F6, 13_770L, FloorId.F7, 2_434L, FloorId.M7, 218L);
		unselected.forEach((floor, stored) -> {
			RngMeterParser.Reading reading = byFloor.get(floor);
			assertFalse(reading.hasSelection(), floor.name());
			assertEquals(stored, reading.storedScore(), floor.name());
			assertNull(reading.requiredText(), floor.name());
		});
	}

	@Test
	void overviewKeepsAbbreviatedRequirementsAsTextOnly() {
		RngMeterParser.Overview overview = RngMeterParser.parseOverview(ProbeFixtures.screen(ProbeFixtures.RNG_OVERVIEW));
		RngMeterParser.Reading m5 = overview.readings().stream().filter(r -> r.floor() == FloorId.M5).findFirst().orElseThrow();
		assertEquals("Third Master Star", m5.selectedItem());
		assertEquals(7_660L, m5.storedScore());
		assertEquals("48.9k", m5.requiredText());
		assertNull(m5.requiredScore());
		assertEquals(15.7, m5.progressPercent());

		RngMeterParser.Reading m6 = overview.readings().stream().filter(r -> r.floor() == FloorId.M6).findFirst().orElseThrow();
		assertEquals("Fourth Master Star", m6.selectedItem());
		assertEquals(25_325L, m6.storedScore());
		assertEquals("81.2k", m6.requiredText());

		RngMeterParser.Reading m1 = overview.readings().stream().filter(r -> r.floor() == FloorId.M1).findFirst().orElseThrow();
		assertEquals("Recombobulator 3000", m1.selectedItem());
		assertEquals(333L, m1.storedScore());
	}

	@Test
	void floorScreenReadsHeaderAndExactRequirements() {
		RngMeterParser.FloorMenu menu = RngMeterParser.parseFloorMenu(ProbeFixtures.screen(ProbeFixtures.RNG_FLOOR_F5));
		assertNotNull(menu);
		assertEquals(FloorId.F5, menu.floor());
		assertFalse(menu.header().hasSelection());
		assertEquals(3_224L, menu.header().storedScore());
		assertEquals(23, menu.items().size());

		RngMeterParser.ItemRequirement rejuvenate = menu.item("Enchanted Book (Rejuvenate III)");
		assertEquals(ChestTier.BEDROCK, rejuvenate.appearsIn());
		assertEquals("Occasional", rejuvenate.oddsLabel());
		assertEquals(11.811, rejuvenate.oddsPercent());
		assertEquals(2_540L, rejuvenate.requiredScore());
		assertEquals(3_224L, rejuvenate.storedScore());
		assertEquals("Click to select!", rejuvenate.actionLine());

		assertEquals(114_300L, menu.item("Shadow Fury").requiredScore());
		assertEquals("Pray RNGesus", menu.item("Shadow Fury").oddsLabel());
		assertEquals(ChestTier.GOLD, menu.item("Dark Orb").appearsIn());
		assertNull(menu.item("Reset RNG Drop"));
		assertNull(menu.item("Search"));
	}

	@Test
	void floorScreenAgreesWithTheHandTranscribedFixture() {
		RngMeterParser.FloorMenu menu = RngMeterParser.parseFloorMenu(ProbeFixtures.screen(ProbeFixtures.RNG_FLOOR_F5));
		JsonObject transcribed = LuckTestData.fixture("live-f5-rng-meter-2026-10-11.json");
		assertEquals(transcribed.get("storedScore").getAsLong(), menu.header().storedScore());
		int count = 0;
		for (JsonElement element : transcribed.getAsJsonArray("items")) {
			JsonObject item = element.getAsJsonObject();
			RngMeterParser.ItemRequirement parsed = menu.item(item.get("name").getAsString());
			assertNotNull(parsed, item.get("name").getAsString());
			assertEquals(item.get("required").getAsLong(), parsed.requiredScore());
			assertEquals(item.get("appearsIn").getAsString(), parsed.appearsIn().name());
			assertEquals(item.get("oddsPct").getAsDouble(), parsed.oddsPercent(), 1e-9);
			count++;
		}
		assertEquals(menu.items().size(), count);
	}

	@Test
	void topMeterMenuAndOtherScreensAreNotCatacombsMeters() {
		assertFalse(RngMeterParser.isOverviewTitle("RNG Meter"));
		assertTrue(RngMeterParser.isOverviewTitle("Catacombs RNG Meters"));
		assertEquals(FloorId.M7, RngMeterParser.floorMenuFloor("Catacombs (M7) RNG Meter"));
		assertNull(RngMeterParser.floorMenuFloor("Catacombs RNG Meters"));
		assertNull(RngMeterParser.parseOverview(ProbeFixtures.screen(ProbeFixtures.RNG_FLOOR_F5)));
	}

	@Test
	void numberFormats() {
		assertEquals(3_224L, LuckText.exactNumber("3,224"));
		assertEquals(114_300L, LuckText.exactNumber("114,300"));
		assertNull(LuckText.exactNumber("48.9k"));
		assertNull(LuckText.exactNumber("3,22"));
		assertEquals(48_900d, LuckText.approximateNumber("48.9k"), 1e-6);
		assertEquals(2_500_000d, LuckText.approximateNumber("2.5M"), 1e-6);
		assertEquals(List.of(1, 4, 6, 7), List.of(LuckText.roman("I"), LuckText.roman("IV"), LuckText.roman("VI"), LuckText.roman("VII")));
	}
}
