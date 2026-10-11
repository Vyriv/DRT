package dev.vy.drt.luck.capture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import dev.vy.drt.luck.FloorId;
import dev.vy.drt.luck.LuckTestData;
import dev.vy.drt.luck.capture.LuckHistoryEvaluation.FloorCoverage;
import dev.vy.drt.luck.capture.LuckHistoryEvaluation.Status;
import dev.vy.drt.luck.capture.LuckHistoryEvaluation.WithholdCause;
import dev.vy.drt.luck.data.LootEntry;
import dev.vy.drt.luck.engine.ChestRoll;
import dev.vy.drt.luck.meter.RngMeterState;
import dev.vy.drt.luck.rules.RuleEvidence;
import dev.vy.drt.luck.rules.RuleVariants;
import dev.vy.drt.luck.sim.HistorySimulator;
import dev.vy.drt.luck.sim.KismetPolicy;
import dev.vy.drt.luck.sim.RunContext;
import dev.vy.drt.luck.sim.RunOutcome;
import dev.vy.drt.luck.sim.RunSimulator;
import dev.vy.drt.luck.sim.SelectionPolicy;
import dev.vy.drt.luck.stats.LuckEvaluator;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** The missing-data policy on stored runs: the per-floor coverage gate, Kismet, unrecorded runs and rejected runs that could matter. */
class LuckMissingDataPolicyTest {
	private static final String S_RUN = "luck-26478f2d-e379-4adb-8f58-96b04733526c";
	private static final String S_PLUS_RUN = "luck-5ff1bf45-ca65-4dd6-adc1-576f5fefea20";
	private static final Gson GSON = new Gson();

	@TempDir
	Path dir;

	private LuckRunRecord live(String id) throws Exception {
		JsonObject fixture = LuckTestData.fixture("live-capture-f5-s-2026-10-11.json");
		JsonObject root = new JsonObject();
		root.addProperty("schema", LuckTrackingStore.SCHEMA);
		root.add("schemaVersion", fixture.get("schemaVersion"));
		root.add("profile", fixture.get("profile"));
		root.add("runs", fixture.get("runs"));
		root.add("meterReadings", fixture.get("meterReadings"));
		Path file = dir.resolve("drt-luck.json");
		Files.writeString(file, root.toString(), StandardCharsets.UTF_8);
		LuckTrackingStore store = new LuckTrackingStore(file);
		assertEquals(LuckTrackingStore.LoadState.LOADED, store.load());
		return store.run(id);
	}

	private static LuckRunRecord copy(LuckRunRecord run, String id, long completedAt) {
		LuckRunRecord out = GSON.fromJson(GSON.toJson(run), LuckRunRecord.class);
		out.luckRunId = id;
		out.drtCompletionFingerprint = id;
		out.completedAtEpochMillis = completedAt;
		return out;
	}

	/** The live S run followed by {@code count - 1} copies that carry its meter forward. */
	private static List<LuckRunRecord> complete(LuckRunRecord live, int count) {
		List<LuckRunRecord> out = new ArrayList<>();
		out.add(live);
		for (int i = 1; i < count; i++) {
			LuckRunRecord next = copy(live, "complete-" + i, live.completedAtEpochMillis + i * 600_000L);
			next.meterSnapshots.clear();
			out.add(next);
		}
		return out;
	}

	/** Copies of the live S+ run, whose chests were never viewed, placed after every complete run. */
	private static List<LuckRunRecord> unseen(LuckRunRecord sPlus, String floor, int count, long after) {
		List<LuckRunRecord> out = new ArrayList<>();
		for (int i = 0; i < count; i++) {
			LuckRunRecord run = copy(sPlus, "unseen-" + floor + "-" + i, after + (i + 1) * 60_000L);
			run.floor = floor;
			run.meterSnapshots.clear();
			out.add(run);
		}
		return out;
	}

	private static long last(List<LuckRunRecord> runs) {
		return runs.get(runs.size() - 1).completedAtEpochMillis;
	}

	private static LuckHistoryEvaluation evaluate(List<LuckRunRecord> runs, int samples) {
		return LuckHistoryEvaluation.evaluate(runs, LuckTestData.defaults(), RuleVariants.defaults(), RuleEvidence.RECORDED,
			SelectionPolicy.highestTier(), samples, 21L);
	}

	@Test
	void theGateIsNinetyPercentOfRecordedRunsCountedExactly() {
		assertTrue(FloorCoverage.meetsGate(9, 10));
		assertTrue(FloorCoverage.meetsGate(18, 20));
		assertTrue(FloorCoverage.meetsGate(90, 100));
		assertTrue(FloorCoverage.meetsGate(1, 1));
		assertFalse(FloorCoverage.meetsGate(17, 19), "89.5% is below the gate");
		assertFalse(FloorCoverage.meetsGate(89, 100));
		assertFalse(FloorCoverage.meetsGate(8, 10));
		assertFalse(FloorCoverage.meetsGate(0, 1));
		assertFalse(FloorCoverage.meetsGate(0, 0), "a floor with no runs is not rated");
	}

	@Test
	void capturedRunsToGateCountsTheRunsInARowAFloorStillNeeds() {
		assertEquals(10, new FloorCoverage("F5", 10, 8, 2, 0, false).capturedRunsToGate(), "18 of 20 reaches 90%");
		assertFalse(FloorCoverage.meetsGate(8 + 9, 10 + 9), "one fewer does not");
		assertEquals(27, new FloorCoverage("F7", 3, 0, 3, 0, false).capturedRunsToGate());
		assertEquals(1, new FloorCoverage("M7", 19, 17, 2, 0, false).capturedRunsToGate());
		assertEquals(0, new FloorCoverage("F5", 10, 9, 1, 0, true).capturedRunsToGate());
		assertEquals(-1, new FloorCoverage(LuckHistoryEvaluation.UNKNOWN_FLOOR, 2, 0, 2, 0, false).capturedRunsToGate());
	}

	@Test
	void exactlyNinetyPercentCapturedIsRatedAndOneMoreMissingRunIsNot() throws Exception {
		LuckRunRecord live = live(S_RUN);
		LuckRunRecord sPlus = live(S_PLUS_RUN);

		List<LuckRunRecord> ninety = complete(live, 9);
		ninety.addAll(unseen(sPlus, "F5", 1, last(ninety)));
		LuckHistoryEvaluation rated = evaluate(ninety, 10);
		assertEquals(List.of(new FloorCoverage("F5", 10, 9, 1, 0, true)), rated.floorCoverage());
		assertEquals(List.of("F5"), rated.ratedFloors());
		assertEquals(Status.INSUFFICIENT_DATA, rated.status(), "rated, but nine F5 runs expect too few rare drops");
		assertFalse(rated.withheldCauses().contains(WithholdCause.NO_ELIGIBLE_FLOOR));
		double perRun = rated.expectedRareDrops() / 9;
		assertEquals((int) Math.ceil((1.0 - rated.expectedRareDrops()) / perRun), rated.runsUntilSufficient());
		assertTrue(rated.runsUntilSufficient() > 0);
		assertNull(rated.estimate(), "only a withheld history gets an estimate");
		assertNull(rated.estimateUnavailable());

		List<LuckRunRecord> eighty = complete(live, 8);
		eighty.addAll(unseen(sPlus, "F5", 2, last(eighty)));
		LuckHistoryEvaluation notRated = evaluate(eighty, 10);
		assertEquals(List.of(new FloorCoverage("F5", 10, 8, 2, 0, false)), notRated.floorCoverage());
		assertEquals(Status.WITHHELD, notRated.status());
		assertEquals(List.of(WithholdCause.NO_ELIGIBLE_FLOOR), notRated.withheldCauses());
		assertEquals(10, notRated.recordedRuns(), "every run stays in the result");
		assertEquals(8, notRated.scored().size());
		assertEquals(2, notRated.rejected().size());
		assertTrue(notRated.rejected().stream().allMatch(run -> run.reasons().contains("OFFERS_NOT_OBSERVED")));
		assertNull(notRated.evaluation());
		assertNull(notRated.estimate(), "eight runs are too few even for a range");
		assertTrue(notRated.estimateUnavailable().startsWith("the captured runs on F5 expect only"), notRated.estimateUnavailable());
		assertEquals(-1, notRated.runsUntilSufficient(), "nothing is rated yet");
	}

	@Test
	void aWellCoveredFloorDoesNotCarryAPoorlyCoveredOne() throws Exception {
		LuckRunRecord live = live(S_RUN);
		List<LuckRunRecord> runs = complete(live, 30);
		runs.addAll(unseen(live(S_PLUS_RUN), "F7", 3, last(runs)));

		LuckHistoryEvaluation result = evaluate(runs, 10);
		assertEquals(33, result.recordedRuns());
		assertTrue(FloorCoverage.meetsGate(30, 33), "the history as a whole would pass");
		assertEquals(List.of(new FloorCoverage("F5", 30, 30, 0, 0, true), new FloorCoverage("F7", 3, 0, 3, 0, false)), result.floorCoverage());
		assertEquals(List.of("F5"), result.ratedFloors());
		assertTrue(result.historyCaveats().contains("FLOOR_NOT_RATED:F7 0/3 (needs 90% captured)"), result.historyCaveats().toString());
		assertEquals(3, result.rejected().stream().filter(run -> run.floor().equals("F7")).count(), "the unrated floor's runs are still listed");
		assertNull(result.missingRunBounds(), "runs on an unrated floor are outside the rating, not missing from it");
		assertFalse(result.withheldCauses().contains(WithholdCause.MISSING_RUNS_COULD_CHANGE_VERDICT));
	}

	@Test
	void runsWithoutAReadableFloorWithholdBecauseNoFloorCanAccountForThem() throws Exception {
		LuckRunRecord live = live(S_RUN);
		List<LuckRunRecord> runs = complete(live, 20);
		LuckRunRecord unreadable = copy(live(S_PLUS_RUN), "unreadable", last(runs) + 60_000L);
		unreadable.floor = "UNKNOWN";
		runs.add(unreadable);

		LuckHistoryEvaluation result = evaluate(runs, 10);
		assertEquals(Status.WITHHELD, result.status());
		assertEquals(List.of(WithholdCause.UNATTRIBUTED_RUNS), result.withheldCauses());
		assertEquals(new FloorCoverage(LuckHistoryEvaluation.UNKNOWN_FLOOR, 1, 0, 1, 0, false), result.floorCoverage().get(1));
		assertEquals(List.of("F5"), result.ratedFloors());
		assertNull(result.estimate());
		assertTrue(result.estimateUnavailable().contains("no readable floor"), result.estimateUnavailable());
	}

	@Test
	void aKismetRerollOnARatedFloorWithholdsTheHeadlineAndKeepsItsEvidence() throws Exception {
		LuckRunRecord live = live(S_RUN);
		List<LuckRunRecord> runs = complete(live, 20);
		LuckRunRecord rerolled = copy(live, "rerolled", last(runs) + 60_000L);
		rerolled.meterSnapshots.clear();
		LuckRunRecord.LuckChestRecord obsidian = rerolled.chests.get(4);
		LuckRunRecord.LuckOfferSnapshot after = GSON.fromJson(GSON.toJson(obsidian.offers.get(0)), LuckRunRecord.LuckOfferSnapshot.class);
		after.index = 1;
		after.changedFromPrevious = true;
		after.afterKismetReroll = true;
		after.contents.removeIf(line -> "ENCHANTMENT_ULTIMATE_LEGION_1".equals(line.key));
		obsidian.offers.add(after);
		runs.add(rerolled);

		LuckHistoryEvaluation result = evaluate(runs, 10);
		assertEquals(List.of(new FloorCoverage("F5", 21, 20, 0, 1, true)), result.floorCoverage(), "20 of 21 captured passes the gate");
		assertEquals(Status.WITHHELD, result.status());
		assertEquals(List.of(WithholdCause.KISMET_REROLLS), result.withheldCauses());
		assertNull(result.evaluation(), "no percentile is computed while a reroll could bias it");
		assertEquals(1, result.excluded().size());
		assertEquals("rerolled", result.excluded().get(0).luckRunId());
		assertEquals(List.of("OBSIDIAN: offer 1 after a Kismet reroll"), result.excluded().get(0).evidence());
		assertTrue(result.expectedRareDrops() > 0.0, "sufficiency is still reported alongside the withholding");
		assertNull(result.estimate(), "a rerolled run is not an ordinary roll, so the bound cannot cover it");
		assertTrue(result.estimateUnavailable().startsWith("Kismet rerolls are not simulated yet"), result.estimateUnavailable());
	}

	@Test
	void runsTheMeterSaysDrtNeverRecordedWithholdTheHeadline() throws Exception {
		LuckRunRecord live = live(S_RUN);
		LuckRunRecord later = copy(live, "after-missed-runs", live.completedAtEpochMillis + 3_600_000L);
		later.meterSnapshots.removeIf(snapshot -> LuckRunRecord.PHASE_AFTER_RUN.equals(snapshot.phase));
		later.meterSnapshots.get(0).storedScore = 9_000L;

		LuckHistoryEvaluation result = evaluate(List.of(live, later), 10);
		assertEquals(Status.WITHHELD, result.status());
		assertEquals(List.of(WithholdCause.UNRECORDED_RUNS), result.withheldCauses());
		assertTrue(result.withheldReasons().get(0).contains("after-missed-runs"), result.withheldReasons().toString());
		assertEquals(List.of(new FloorCoverage("F5", 2, 2, 0, 0, true)), result.floorCoverage(), "every recorded run was captured");
		assertNull(result.estimate(), "an unknown number of missed runs has no range");
		assertTrue(result.estimateUnavailable().contains("did not record"), result.estimateUnavailable());
	}

	/**
	 * The live S run followed by copies whose chests hold what simulated runs rolled under the same
	 * snapshot, from the live meter reading: a history with no luck in it.
	 */
	private static List<LuckRunRecord> rolled(LuckRunRecord live, int count, long seed) {
		RunSimulator simulator = new RunSimulator(LuckTestData.defaults(), live.modifiers.toPreset().resolveKnown(), RuleVariants.defaults());
		List<RunOutcome> outcomes = new HistorySimulator(simulator).simulate(Collections.nCopies(count, RunContext.of(FloorId.F5, live.score)),
			new RngMeterState(FloorId.F5, null, 3_556L), SelectionPolicy.highestTier(), KismetPolicy.never(), seed);
		List<LuckRunRecord> out = new ArrayList<>();
		for (int i = 0; i < count; i++) {
			LuckRunRecord run = copy(live, "rolled-" + i, live.completedAtEpochMillis + i * 600_000L);
			if (i > 0) run.meterSnapshots.clear();
			for (ChestRoll roll : outcomes.get(i).initialOffers()) {
				LuckRunRecord.LuckOfferSnapshot offer = run.chests.stream().filter(chest -> chest.tier.trim().equalsIgnoreCase(roll.tier().name()))
					.findFirst().orElseThrow().offers.get(0);
				offer.contents.removeIf(line -> !LootEntry.Kind.ESSENCE.name().equals(line.kind));
				for (LootEntry entry : roll.rolledLuckItems()) {
					LuckRunRecord.LuckContentLine line = new LuckRunRecord.LuckContentLine();
					line.text = entry.displayName();
					line.name = entry.displayName();
					line.kind = entry.kind().name();
					line.key = entry.key();
					offer.contents.add(line);
				}
			}
			out.add(run);
		}
		return out;
	}

	@Test
	void rejectedRunsThatCouldChangeTheBandWithholdTheHeadline() throws Exception {
		LuckRunRecord live = live(S_RUN);
		List<LuckRunRecord> runs = rolled(live, 140, 31L);
		LuckHistoryEvaluation captured = evaluate(runs, 40);
		assertEquals(140, captured.scorableRuns(), "every rolled copy is a complete record");
		assertTrue(captured.status() == Status.PROVISIONAL || captured.status() == Status.UNRESOLVED, captured.status() + " " + captured.withheldReasons());

		runs.addAll(unseen(live(S_PLUS_RUN), "F5", 15, last(runs)));

		LuckHistoryEvaluation result = evaluate(runs, 40);
		assertEquals(List.of(new FloorCoverage("F5", 155, 140, 15, 0, true)), result.floorCoverage(), "90.3% captured passes the gate");
		assertTrue(result.statisticallySufficient());
		LuckEvaluator.MissingRunBounds bounds = result.missingRunBounds();
		assertNotNull(bounds);
		assertEquals(15, bounds.missingRuns());
		assertEquals(15, bounds.unknownOutcomes());
		assertTrue(bounds.material(), bounds.toString());
		assertEquals(Status.WITHHELD, result.status());
		assertEquals(List.of(WithholdCause.MISSING_RUNS_COULD_CHANGE_VERDICT), result.withheldCauses());
		assertTrue(result.withheldReasons().get(0).startsWith("15 rejected runs on rated floors (15 with unseen loot)"), result.withheldReasons().toString());
		assertNotNull(result.evaluation(), "the evaluation is kept for diagnostics");
		assertNull(result.headlineBand(), "but never headlined");

		LuckHistoryEvaluation.Estimate estimate = result.estimate();
		assertNotNull(estimate, "the same bound gives a labelled range");
		assertNull(result.estimateUnavailable());
		assertEquals(List.of("F5"), estimate.floors());
		assertEquals(140, estimate.scoredRuns());
		assertEquals(15, estimate.missingRuns());
		assertEquals(15, estimate.unknownOutcomes());
		for (LuckEvaluator.Band band : bounds.bands()) {
			assertTrue(band.compareTo(estimate.lowest()) >= 0 && band.compareTo(estimate.highest()) <= 0, band + " outside " + estimate);
		}
		LuckEvaluator.Band verdict = result.evaluation().band();
		assertTrue(verdict.compareTo(estimate.lowest()) >= 0 && verdict.compareTo(estimate.highest()) <= 0, "the range includes the captured runs' own band");
		assertTrue(estimate.lowPercentile() <= bounds.low().percentile() && estimate.highPercentile() >= bounds.high().percentile());
	}

	@Test
	void aHistoryBelowTheGateGetsAnEstimateFromTheWorstSelectionBoundAndNoHeadline() throws Exception {
		LuckRunRecord live = live(S_RUN);
		List<LuckRunRecord> runs = rolled(live, 140, 31L);
		runs.addAll(unseen(live(S_PLUS_RUN), "F5", 20, last(runs)));

		LuckHistoryEvaluation result = evaluate(runs, 40);
		assertEquals(List.of(new FloorCoverage("F5", 160, 140, 20, 0, false)), result.floorCoverage(), "87.5% captured is below the gate");
		assertEquals(Status.WITHHELD, result.status());
		assertEquals(List.of(WithholdCause.NO_ELIGIBLE_FLOOR), result.withheldCauses());
		assertNull(result.evaluation(), "nothing is scored for the headline");
		assertNull(result.missingRunBounds());
		assertNull(result.headlineBand());

		LuckHistoryEvaluation.Estimate estimate = result.estimate();
		assertNotNull(estimate, result.estimateUnavailable());
		assertEquals(List.of("F5"), estimate.floors());
		assertEquals(140, estimate.scoredRuns());
		assertEquals(20, estimate.missingRuns());
		assertEquals(20, estimate.unknownOutcomes());
		assertTrue(estimate.lowest().compareTo(estimate.highest()) <= 0);
		assertTrue(estimate.lowPercentile() <= estimate.highPercentile());
		assertEquals(40, result.floorCoverage().get(0).capturedRunsToGate(), "180 of 200 reaches 90%");

		LuckHistoryEvaluation again = evaluate(runs, 40);
		assertEquals(estimate, again.estimate(), "the same history and seed give the same range");
	}

	@Test
	void aRejectedRunIsBoundedFromWhatWasSeenOfIt() throws Exception {
		LuckRunRecord live = live(S_RUN);
		LuckRunRecord gradeOnly = copy(live, "grade-only", live.completedAtEpochMillis);
		gradeOnly.score = null;
		LuckEvaluator.MissingRun known = LuckScoringInput.missingRun(gradeOnly, LuckTestData.defaults());
		assertTrue(known.outcomeKnown(), "only the score was missing and every chest was seen");
		assertFalse(known.contextAssumed());
		assertEquals(FloorId.F5, known.run().floor());
		assertEquals(270, known.run().score(), "the S grade stands in for the score");
		assertEquals(7, known.seenKeys().size());

		LuckEvaluator.MissingRun unseen = LuckScoringInput.missingRun(live(S_PLUS_RUN), LuckTestData.defaults());
		assertFalse(unseen.outcomeKnown());
		assertFalse(unseen.contextAssumed());
		assertTrue(unseen.seenKeys().isEmpty());

		LuckRunRecord noGrade = copy(live(S_PLUS_RUN), "no-grade", live.completedAtEpochMillis);
		noGrade.score = null;
		noGrade.grade = "?";
		LuckEvaluator.MissingRun assumed = LuckScoringInput.missingRun(noGrade, LuckTestData.defaults());
		assertTrue(assumed.contextAssumed());
		assertEquals(300, assumed.run().score());

		LuckRunRecord noFloor = copy(live, "no-floor", live.completedAtEpochMillis);
		noFloor.floor = "UNKNOWN";
		assertNull(LuckScoringInput.missingRun(noFloor, LuckTestData.defaults()));
	}

	@Test
	void everyOutcomeStaysDistinct() throws Exception {
		LuckRunRecord live = live(S_RUN);
		assertEquals(Status.NO_RECORDED_RUNS, evaluate(List.of(), 10).status());
		assertEquals(Status.NO_SCORABLE_RUNS, evaluate(List.of(live(S_PLUS_RUN)), 10).status());
		assertEquals(Status.INSUFFICIENT_DATA, evaluate(List.of(live), 10).status());
		LuckHistoryEvaluation scored = evaluate(complete(live, 150), 40);
		assertTrue(scored.status() == Status.PROVISIONAL || scored.status() == Status.UNRESOLVED, scored.status() + " " + scored.withheldReasons());
		assertNull(scored.missingRunBounds(), "nothing was missing");
		assertEquals(scored.status() == Status.PROVISIONAL, scored.headlineBand() != null);
		assertNull(scored.estimate(), "a scored history is never shown as an estimate");
		assertNull(scored.estimateUnavailable());
		assertEquals(0, scored.runsUntilSufficient());

		LuckHistoryEvaluation again = evaluate(complete(live, 150), 40);
		assertEquals(scored.evaluation().baseline().percentile(), again.evaluation().baseline().percentile(), "parallel scoring stays deterministic");
		for (int i = 0; i < scored.evaluation().alternatives().size(); i++) {
			assertEquals(scored.evaluation().alternatives().get(i).percentile(), again.evaluation().alternatives().get(i).percentile());
		}
	}
}
