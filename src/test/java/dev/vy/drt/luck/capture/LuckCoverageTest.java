package dev.vy.drt.luck.capture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.vy.drt.luck.ChestTier;
import dev.vy.drt.luck.FloorId;
import dev.vy.drt.luck.ScoreTier;
import dev.vy.drt.luck.rules.ModifierPreset;
import java.util.EnumSet;
import java.util.List;
import org.junit.jupiter.api.Test;

class LuckCoverageTest {
	private static final ModifierPreset FULL = new ModifierPreset(
		ModifierPreset.BossLuck.IV, ModifierPreset.TreasureAccessory.ARTIFACT, 10, 10, 10, 0);

	private static LuckRunRecord run(String floor, String grade, DungeonScoreReading reading) {
		LuckRunRecord run = LuckRunRecord.forCompletion("live-a-run-1", "live-a-run-1|" + floor + "|" + grade + "|1000",
			1_000L, floor, grade, reading);
		run.modifiers = LuckModifierProfile.of(FULL);
		return run;
	}

	private static void addChest(LuckRunRecord run, ChestTier tier, boolean... rerollFlags) {
		LuckRunRecord.LuckChestRecord chest = new LuckRunRecord.LuckChestRecord();
		chest.tier = tier.name();
		chest.source = "CROESUS_RUN_VIEW";
		for (int i = 0; i < rerollFlags.length; i++) {
			LuckRunRecord.LuckOfferSnapshot offer = new LuckRunRecord.LuckOfferSnapshot();
			offer.index = i;
			offer.afterKismetReroll = rerollFlags[i];
			chest.offers.add(offer);
		}
		run.chests.add(chest);
	}

	private static void addAllChests(LuckRunRecord run, FloorId floor, ScoreTier tier) {
		for (ChestTier chest : LuckCoverage.expectedChests(floor, tier)) addChest(run, chest, false);
	}

	@Test
	void expectedChestsFollowScoreAndFloor() {
		assertEquals(EnumSet.of(ChestTier.WOOD, ChestTier.GOLD, ChestTier.DIAMOND),
			LuckCoverage.expectedChests(FloorId.F5, ScoreTier.BELOW_A));
		assertEquals(EnumSet.of(ChestTier.WOOD, ChestTier.GOLD, ChestTier.DIAMOND, ChestTier.EMERALD, ChestTier.OBSIDIAN),
			LuckCoverage.expectedChests(FloorId.F3, ScoreTier.S_PLUS));
		assertEquals(EnumSet.allOf(ChestTier.class), LuckCoverage.expectedChests(FloorId.M5, ScoreTier.S_PLUS));
		assertEquals(EnumSet.allOf(ChestTier.class), LuckCoverage.expectedChests(FloorId.M7, ScoreTier.BELOW_A));
	}

	@Test
	void runWithNoOfferObservationsIsRecordedButDoesNotQualify() {
		LuckCoverage coverage = LuckCoverage.evaluate(run("M5", "S+", new DungeonScoreReading(306, "S+")));
		assertEquals("OBSERVED", coverage.scoreStatus);
		assertEquals("NOT_OBSERVED", coverage.offerStatus);
		assertEquals("NOT_OBSERVED", coverage.kismetStatus);
		assertFalse(coverage.qualifiesForScore);
		assertTrue(coverage.blockers.contains("OFFERS_NOT_OBSERVED"));
		assertEquals(6, coverage.expectedChests.size());
	}

	@Test
	void gradeOnlyRunKeepsScoreUnknownAsACaveat() {
		LuckRunRecord run = run("M5", "S+", null);
		assertNull(run.score);
		addAllChests(run, FloorId.M5, ScoreTier.S_PLUS);
		LuckCoverage coverage = LuckCoverage.evaluate(run);
		assertEquals("GRADE_ONLY", coverage.scoreStatus);
		assertTrue(coverage.caveats.contains("NUMERIC_SCORE_MISSING"));
		assertTrue(coverage.qualifiesForScore);
	}

	@Test
	void scoreFromADifferentGradeIsNotAttached() {
		LuckRunRecord run = run("M5", "S", new DungeonScoreReading(306, "S+"));
		assertNull(run.score);
		assertNull(run.scoreSource);
	}

	@Test
	void missingChestMakesOffersPartial() {
		LuckRunRecord run = run("M5", "S+", new DungeonScoreReading(306, "S+"));
		addChest(run, ChestTier.WOOD, false);
		addChest(run, ChestTier.BEDROCK, false);
		LuckCoverage coverage = LuckCoverage.evaluate(run);
		assertEquals("PARTIAL", coverage.offerStatus);
		assertEquals(List.of("WOOD", "BEDROCK"), coverage.observedChests);
		assertFalse(coverage.qualifiesForScore);
		assertTrue(coverage.blockers.contains("OFFERS_PARTIAL"));
	}

	@Test
	void completeOffersQualifyAndMeterUnknownIsOnlyACaveat() {
		LuckRunRecord run = run("M5", "S+", new DungeonScoreReading(306, "S+"));
		addAllChests(run, FloorId.M5, ScoreTier.S_PLUS);
		LuckCoverage coverage = LuckCoverage.evaluate(run);
		assertEquals("COMPLETE", coverage.offerStatus);
		assertEquals("NO_REROLL", coverage.kismetStatus);
		assertEquals("UNKNOWN", coverage.meterStatus);
		assertEquals("COMPLETE", coverage.modifierStatus);
		assertTrue(coverage.caveats.contains("METER_UNKNOWN"));
		assertTrue(coverage.qualifiesForScore);
	}

	@Test
	void rerollWithOriginalOfferIsComplete() {
		LuckRunRecord run = run("F7", "S+", new DungeonScoreReading(301, "S+"));
		for (ChestTier chest : LuckCoverage.expectedChests(FloorId.F7, ScoreTier.S_PLUS)) {
			if (chest == ChestTier.BEDROCK) addChest(run, chest, false, true);
			else addChest(run, chest, false);
		}
		LuckCoverage coverage = LuckCoverage.evaluate(run);
		assertEquals("REROLL_WITH_ORIGINAL", coverage.kismetStatus);
		assertTrue(coverage.qualifiesForScore);
	}

	@Test
	void rerollSeenWithoutOriginalBlocksScoring() {
		LuckRunRecord run = run("F7", "S+", new DungeonScoreReading(301, "S+"));
		for (ChestTier chest : LuckCoverage.expectedChests(FloorId.F7, ScoreTier.S_PLUS)) {
			if (chest == ChestTier.BEDROCK) addChest(run, chest, true);
			else addChest(run, chest, false);
		}
		LuckCoverage coverage = LuckCoverage.evaluate(run);
		assertEquals("REROLL_WITHOUT_ORIGINAL", coverage.kismetStatus);
		assertEquals("PARTIAL", coverage.offerStatus);
		assertTrue(coverage.blockers.contains("KISMET_ORIGINAL_OFFER_MISSING"));
		assertFalse(coverage.qualifiesForScore);
	}

	@Test
	void unknownFloorOrGradeBlocks() {
		LuckCoverage coverage = LuckCoverage.evaluate(run("UNKNOWN", "?", null));
		assertEquals("MISSING", coverage.scoreStatus);
		assertTrue(coverage.blockers.contains("FLOOR_UNKNOWN"));
		assertTrue(coverage.blockers.contains("GRADE_UNKNOWN"));
		assertFalse(coverage.qualifiesForScore);
	}

	@Test
	void modifierCoverageReflectsUnknownFields() {
		LuckRunRecord run = run("M5", "S+", new DungeonScoreReading(306, "S+"));
		run.modifiers = LuckModifierProfile.of(new ModifierPreset(ModifierPreset.BossLuck.IV, null, 10, null, null, 0));
		assertEquals("PARTIAL", LuckCoverage.evaluate(run).modifierStatus);
		run.modifiers = LuckModifierProfile.unknown();
		LuckCoverage coverage = LuckCoverage.evaluate(run);
		assertEquals("UNSET", coverage.modifierStatus);
		assertTrue(coverage.caveats.contains("MODIFIERS_UNKNOWN"));
	}

	@Test
	void observedMeterBeforeRunIsReported() {
		LuckRunRecord run = run("M5", "S+", new DungeonScoreReading(306, "S+"));
		LuckRunRecord.LuckMeterSnapshot snapshot = new LuckRunRecord.LuckMeterSnapshot();
		snapshot.floor = "M5";
		snapshot.phase = "BEFORE_RUN";
		snapshot.state = "OBSERVED";
		run.meterSnapshots.add(snapshot);
		assertEquals("OBSERVED", LuckCoverage.evaluate(run).meterStatus);
	}
}
