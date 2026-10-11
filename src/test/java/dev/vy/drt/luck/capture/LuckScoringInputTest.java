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
import dev.vy.drt.luck.ScoreTier;
import dev.vy.drt.luck.meter.RngMeterModel;
import dev.vy.drt.luck.meter.RngMeterState;
import dev.vy.drt.luck.rules.ModifierPreset;
import dev.vy.drt.luck.rules.RuleEvidence;
import dev.vy.drt.luck.rules.RuleVariants;
import dev.vy.drt.luck.sim.KismetPolicy;
import dev.vy.drt.luck.sim.RunRarity;
import dev.vy.drt.luck.sim.RunSimulator;
import dev.vy.drt.luck.sim.SelectionPolicy;
import dev.vy.drt.luck.stats.HistoryConfigurations;
import dev.vy.drt.luck.stats.LuckEvaluator;
import dev.vy.drt.luck.stats.LuckEvaluator.Configuration;
import dev.vy.drt.luck.stats.LuckEvaluator.Verdict;
import dev.vy.drt.luck.stats.MeterScenarioPlanner;
import dev.vy.drt.luck.stats.ObservedRun;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** The live F5 captures, loaded from disk, converted for the Stage B engine and evaluated as a history. */
class LuckScoringInputTest {
	private static final String S_RUN = "luck-26478f2d-e379-4adb-8f58-96b04733526c";
	private static final String S_PLUS_RUN = "luck-5ff1bf45-ca65-4dd6-adc1-576f5fefea20";
	private static final Gson GSON = new Gson();

	@TempDir
	Path dir;

	/** The fixture runs as a restarted client would read them back from its luck file. */
	private List<LuckRunRecord> loadedLiveRuns() throws Exception {
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
		List<LuckRunRecord> runs = new ArrayList<>();
		for (LuckRunRecord run : store.runs()) runs.add(store.run(run.luckRunId));
		return runs;
	}

	private static LuckRunRecord copy(LuckRunRecord run, String id, long completedAt) {
		LuckRunRecord out = GSON.fromJson(GSON.toJson(run), LuckRunRecord.class);
		out.luckRunId = id;
		out.drtCompletionFingerprint = id;
		out.completedAtEpochMillis = completedAt;
		return out;
	}

	private static LuckRunRecord find(List<LuckRunRecord> runs, String id) {
		return runs.stream().filter(run -> run.luckRunId.equals(id)).findFirst().orElseThrow();
	}

	private static LuckHistoryEvaluation evaluate(List<LuckRunRecord> runs, int samples) {
		return LuckHistoryEvaluation.evaluate(runs, LuckTestData.defaults(), RuleVariants.defaults(), RuleEvidence.RECORDED,
			SelectionPolicy.highestTier(), samples, 11L);
	}

	/** The live S run with an Obsidian Kismet reroll that replaced the Legion book. */
	private static LuckRunRecord rerolled(LuckRunRecord live, String id, long completedAt) {
		LuckRunRecord rerolled = copy(live, id, completedAt);
		LuckRunRecord.LuckChestRecord obsidian = rerolled.chests.get(4);
		LuckRunRecord.LuckOfferSnapshot after = GSON.fromJson(GSON.toJson(obsidian.offers.get(0)), LuckRunRecord.LuckOfferSnapshot.class);
		after.index = 1;
		after.changedFromPrevious = true;
		after.afterKismetReroll = true;
		after.contents.removeIf(line -> "ENCHANTMENT_ULTIMATE_LEGION_1".equals(line.key));
		obsidian.offers.add(after);
		return rerolled;
	}

	@Test
	void theLiveRunReachesTheEngineWithNothingReconstructed() throws Exception {
		LuckScoringInput.History history = LuckScoringInput.history(loadedLiveRuns(), LuckTestData.defaults());

		assertEquals(List.of(S_PLUS_RUN), history.rejected().stream().map(LuckScoringInput.Rejected::luckRunId).toList());
		assertEquals(List.of("OFFERS_NOT_OBSERVED"), history.rejected().get(0).reasons());
		assertEquals(1, history.runs().size());
		assertEquals(2, history.recorded());

		LuckScoringInput.Converted converted = history.runs().get(0);
		assertEquals(S_RUN, converted.luckRunId());
		assertEquals(List.of("KISMET_PROVISIONAL"), converted.caveats());
		ObservedRun observed = converted.observed();
		assertEquals(FloorId.F5, observed.run().floor());
		assertEquals(298, observed.run().score());
		assertEquals(ScoreTier.S, observed.run().scoreTier());
		assertTrue(observed.run().kismetAvailable());
		assertTrue(observed.run().chestKeyAvailable());
		assertTrue(observed.offersComplete());
		assertEquals(Set.of("ENCHANTMENT_FEATHER_FALLING_6", "ENCHANTMENT_INFINITE_QUIVER_6", "ENCHANTMENT_ULTIMATE_WISDOM_1",
			"ENCHANTMENT_ULTIMATE_WISE_1", "HOT_POTATO_BOOK", "ENCHANTMENT_REJUVENATE_2", "ENCHANTMENT_ULTIMATE_LEGION_1"), observed.appearedKeys());
		assertEquals(Set.of(), observed.purchasedKeys(), "nothing was opened");
		assertEquals(new RngMeterState(FloorId.F5, null, 3_556L), observed.meterBefore());
		assertEquals(new RngMeterState(FloorId.F5, null, 3_785L), observed.meterAfter());

		ModifierPreset snapshot = history.runModifiers().get(0);
		assertEquals(10, snapshot.pityLevel());
		assertTrue(snapshot.isFullyKnown());
		assertEquals(List.of(), history.caveats());
		assertEquals(Set.of(), history.gapsBefore(), "the rejected S+ run came before the floor's first scored run");

		RunSimulator simulator = new RunSimulator(LuckTestData.defaults(), snapshot.resolveKnown(), RuleVariants.defaults());
		assertEquals(LuckCoverage.expectedChests(FloorId.F5, ScoreTier.S).stream().toList(), simulator.eligibleChests(FloorId.F5, ScoreTier.S),
			"capture coverage and the simulator agree on eligible chests");
		RunRarity rarity = simulator.rarity(FloorId.F5, ScoreTier.S);
		for (String key : observed.appearedKeys()) assertTrue(rarity.perRun(key) > 0.0, key + " must be a rollable item for an F5 S run");
	}

	@Test
	void aScorableRunIsNotASufficientHistory() throws Exception {
		LuckHistoryEvaluation result = evaluate(loadedLiveRuns(), 50);
		assertEquals(LuckHistoryEvaluation.Status.INSUFFICIENT_DATA, result.status());
		assertEquals(2, result.recordedRuns());
		assertEquals(1, result.scorableRuns());
		assertEquals(1, result.rejectedRuns());
		assertEquals(0, result.excludedRuns());
		assertFalse(result.statisticallySufficient());
		assertEquals(Map.of("OFFERS_NOT_OBSERVED", 1), result.rejectionReasonCounts());
		assertEquals(Map.of("KISMET_PROVISIONAL", 1), result.runCaveatCounts());
		assertEquals(List.of(S_PLUS_RUN), result.rejected().stream().map(LuckScoringInput.Rejected::luckRunId).toList());
		assertTrue(result.historyCaveats().stream().anyMatch(c -> c.startsWith("RUNS_NOT_SCORED:1")), result.historyCaveats().toString());
		assertEquals(List.of(), result.meterWindows(), "the S run's start was read");
		assertEquals(List.of(), result.withheldReasons());
		assertEquals(1, result.modifierSnapshots());

		LuckEvaluator.Evaluation evaluation = result.evaluation();
		assertNotNull(evaluation);
		assertEquals(Verdict.INSUFFICIENT_DATA, evaluation.verdict());
		assertTrue(evaluation.reasons().stream().anyMatch(r -> r.startsWith("F5 loot data is")), evaluation.reasons().toString());
		assertTrue(evaluation.reasons().stream().anyMatch(r -> r.startsWith("S-run meter gain rule is PROVISIONAL")));
		assertTrue(evaluation.reasons().stream().anyMatch(r -> r.startsWith("only ") && r.endsWith("need 1")));
		assertTrue(evaluation.reasons().stream().noneMatch(r -> r.startsWith("ignored items")), "essence totals must not read as unmodelled items");
	}

	@Test
	void enoughLiveRunsAreScoredWithEveryCountCarried() throws Exception {
		List<LuckRunRecord> loaded = loadedLiveRuns();
		LuckRunRecord live = find(loaded, S_RUN);
		List<LuckRunRecord> runs = new ArrayList<>(loaded);
		for (int i = 1; i < 150; i++) {
			LuckRunRecord next = copy(live, "copy-" + i, live.completedAtEpochMillis + i * 600_000L);
			next.meterSnapshots.clear();
			runs.add(next);
		}
		LuckHistoryEvaluation result = evaluate(runs, 60);
		assertTrue(result.status() == LuckHistoryEvaluation.Status.PROVISIONAL || result.status() == LuckHistoryEvaluation.Status.UNRESOLVED,
			result.status() + " " + result.withheldReasons());
		assertTrue(result.statisticallySufficient());
		assertEquals(151, result.recordedRuns());
		assertEquals(150, result.scorableRuns());
		assertEquals(1, result.rejectedRuns());
		assertEquals(List.of(), result.meterWindows(), "later runs carry the meter forward from the live reading");
		assertNotNull(result.evaluation().baseline());
		assertEquals(12, result.evaluation().alternatives().size(), "known Pity 10: twelve rule alternatives, no modifier extremes");
	}

	@Test
	void repeatedIdenticalReadingsContradictTheRunsAndWithholdTheScore() throws Exception {
		LuckRunRecord live = find(loadedLiveRuns(), S_RUN);
		List<LuckRunRecord> runs = new ArrayList<>();
		for (int i = 0; i < 3; i++) runs.add(copy(live, "same-reading-" + i, live.completedAtEpochMillis + i * 600_000L));
		LuckHistoryEvaluation result = evaluate(runs, 10);
		assertEquals(LuckHistoryEvaluation.Status.WITHHELD, result.status());
		assertEquals(3, result.scorableRuns(), "each run is complete on its own");
		assertNull(result.evaluation());
		assertTrue(result.withheldReasons().stream().anyMatch(r -> r.startsWith("METER_BELOW_CAPTURED_GAINS:F5")), result.withheldReasons().toString());
	}

	@Test
	void runsThatCannotBeScoredAreRejectedNotDropped() throws Exception {
		LuckRunRecord live = find(loadedLiveRuns(), S_RUN);
		LuckRunRecord gradeOnly = copy(live, "grade-only", live.completedAtEpochMillis + 1L);
		gradeOnly.score = null;
		LuckScoringInput.History history = LuckScoringInput.history(List.of(live, gradeOnly), LuckTestData.defaults());
		assertEquals(1, history.runs().size());
		assertEquals(List.of("NUMERIC_SCORE_MISSING"), history.rejected().get(0).reasons());

		ObservedRun incomplete = new ObservedRun(history.observed().get(0).run(), Set.of(), Set.of(), false);
		List<ObservedRun> withIncomplete = new ArrayList<>(history.observed());
		withIncomplete.add(incomplete);
		List<Configuration> configurations = LuckEvaluator.plausibleConfigurations(history.runModifiers().get(0), RuleVariants.defaults(),
			List.of(Map.of()), SelectionPolicy.highestTier(), KismetPolicy.never());
		assertEquals(Verdict.INCOMPLETE_RECORDS, LuckEvaluator.evaluate(LuckTestData.defaults(), withIncomplete, configurations.get(0),
			configurations.subList(1, configurations.size()), 10, 1L).verdict(), "the engine refuses an incomplete record if one is passed");
	}

	@Test
	void aConfirmedRerollIsHeldOutWithItsEvidence() throws Exception {
		LuckRunRecord live = find(loadedLiveRuns(), S_RUN);
		LuckRunRecord rerolled = rerolled(live, "rerolled", live.completedAtEpochMillis + 600_000L);
		assertEquals("REROLL_WITH_ORIGINAL", LuckCoverage.evaluate(rerolled).kismetStatus);

		LuckScoringInput.Result result = LuckScoringInput.convert(rerolled, LuckTestData.defaults());
		assertFalse(result.accepted());
		assertNull(result.rejected(), "nothing else is wrong with the run");
		assertEquals(LuckScoringInput.KISMET_REROLL_CONFIRMED, result.excluded().reason());
		assertEquals("REROLL_WITH_ORIGINAL", result.excluded().kismetStatus());
		assertEquals(List.of("OBSIDIAN: offer 1 after a Kismet reroll"), result.excluded().evidence());

		LuckHistoryEvaluation evaluation = evaluate(List.of(live, rerolled), 10);
		assertEquals(2, evaluation.recordedRuns());
		assertEquals(1, evaluation.scorableRuns());
		assertEquals(1, evaluation.excludedRuns());
		assertEquals(List.of("rerolled"), evaluation.excluded().stream().map(LuckScoringInput.Excluded::luckRunId).toList());
		assertEquals(Map.of(LuckScoringInput.KISMET_REROLL_CONFIRMED, 1), evaluation.rejectionReasonCounts());
		assertTrue(evaluation.historyCaveats().stream().anyMatch(c -> c.startsWith("KISMET_RUNS_EXCLUDED:1")), evaluation.historyCaveats().toString());
		assertTrue(evaluation.scored().stream().noneMatch(run -> run.luckRunId().equals("rerolled")), "never simulated with the no-reroll policy");
	}

	@Test
	void anUnexplainedChangeIsASuspectedRerollAndAlsoHeldOut() throws Exception {
		LuckRunRecord live = find(loadedLiveRuns(), S_RUN);
		LuckRunRecord changed = rerolled(live, "changed", live.completedAtEpochMillis);
		changed.chests.get(4).offers.get(1).afterKismetReroll = false;
		LuckScoringInput.Excluded excluded = LuckScoringInput.convert(changed, LuckTestData.defaults()).excluded();
		assertEquals(LuckScoringInput.KISMET_REROLL_SUSPECTED, excluded.reason());
		assertEquals("CONTENTS_CHANGED_UNVERIFIED", excluded.kismetStatus());
		assertEquals(List.of("OBSIDIAN: contents changed at offer 1 without a recorded reroll"), excluded.evidence());

		changed.score = null;
		LuckScoringInput.Rejected rejected = LuckScoringInput.convert(changed, LuckTestData.defaults()).rejected();
		assertEquals(List.of("NUMERIC_SCORE_MISSING", "OFFER_CHANGED_UNVERIFIED"), rejected.reasons(), "other blockers make it a rejection, with every reason kept");
	}

	@Test
	void anExcludedRunBreaksMeterContinuityLikeARejectedOne() throws Exception {
		LuckRunRecord live = find(loadedLiveRuns(), S_RUN);
		LuckRunRecord rerolled = rerolled(live, "rerolled", live.completedAtEpochMillis + 300_000L);
		LuckRunRecord later = copy(live, "later", live.completedAtEpochMillis + 600_000L);
		later.meterSnapshots.clear();
		LuckScoringInput.History history = LuckScoringInput.history(List.of(live, rerolled, later), LuckTestData.defaults());
		assertEquals(Set.of(1), history.gapsBefore());
		assertEquals(List.of("METER_GAP:later"), history.caveats());

		LuckHistoryEvaluation evaluation = evaluate(List.of(live, rerolled, later), 10);
		assertEquals(1, evaluation.meterWindows().size());
		MeterScenarioPlanner.Window window = evaluation.meterWindows().get(0);
		assertEquals(MeterScenarioPlanner.WindowKind.GAP, window.kind());
		assertEquals(1, window.startRun());
		assertFalse(window.selectionUnknown(), "the live reading showed nothing selected before the gap");
	}

	@Test
	void meterContinuityGapsAreNamed() throws Exception {
		List<LuckRunRecord> loaded = loadedLiveRuns();
		LuckRunRecord live = find(loaded, S_RUN);
		LuckRunRecord noMeter = copy(live, "no-meter", live.completedAtEpochMillis + 600_000L);
		noMeter.meterSnapshots.clear();

		LuckScoringInput.History afterRejected = LuckScoringInput.history(List.of(live, find(loaded, S_PLUS_RUN), copy(noMeter, "later", live.completedAtEpochMillis + 1L)),
			LuckTestData.defaults());
		assertTrue(afterRejected.caveats().isEmpty(), "the S+ run precedes the read S run, so the meter is carried from a reading: " + afterRejected.caveats());

		LuckRunRecord rejectedBetween = copy(find(loaded, S_PLUS_RUN), "rejected-between", live.completedAtEpochMillis + 300_000L);
		LuckScoringInput.History gap = LuckScoringInput.history(List.of(live, rejectedBetween, noMeter), LuckTestData.defaults());
		assertEquals(List.of("METER_GAP:no-meter"), gap.caveats());
		assertEquals(Set.of(1), gap.gapsBefore());

		LuckScoringInput.History unknownStart = LuckScoringInput.history(List.of(noMeter), LuckTestData.defaults());
		assertEquals(List.of("METER_START_UNKNOWN:F5"), unknownStart.caveats());
		assertNull(unknownStart.observed().get(0).meterBefore());
		assertTrue(unknownStart.runs().get(0).caveats().contains("METER_UNKNOWN"));

		LuckHistoryEvaluation evaluated = evaluate(List.of(noMeter), 10);
		assertEquals(MeterScenarioPlanner.WindowKind.UNKNOWN_START, evaluated.meterWindows().get(0).kind());
		assertTrue(evaluated.meterWindows().get(0).selectionUnknown(), "no reading at all: every selection stays plausible");
	}

	@Test
	void selectedDropsAreCarriedOnlyWhenTheEngineCanModelThem() throws Exception {
		LuckRunRecord live = find(loadedLiveRuns(), S_RUN);
		String drop = RngMeterModel.selectableItems(LuckTestData.defaults().floor(FloorId.F5)).get(0).key();
		LuckRunRecord selected = copy(live, "selected", live.completedAtEpochMillis);
		selected.meterSnapshots.get(0).selectedItem = "Selected Drop";
		selected.meterSnapshots.get(0).selectedKey = drop;
		assertEquals(new RngMeterState(FloorId.F5, drop, 3_556L), LuckScoringInput.convert(selected, LuckTestData.defaults()).converted().observed().meterBefore());

		LuckRunRecord notSelectable = copy(live, "not-selectable", live.completedAtEpochMillis);
		notSelectable.meterSnapshots.get(0).selectedItem = "Necron's Handle";
		notSelectable.meterSnapshots.get(0).selectedKey = "NECRON_HANDLE";
		LuckScoringInput.Converted converted = LuckScoringInput.convert(notSelectable, LuckTestData.defaults()).converted();
		assertNull(converted.observed().meterBefore());
		assertTrue(converted.caveats().contains("METER_SELECTION_NOT_SELECTABLE"));
	}

	@Test
	void differingSnapshotsKeepTheirKnownValuesPerRun() throws Exception {
		LuckRunRecord live = find(loadedLiveRuns(), S_RUN);
		LuckRunRecord bought = copy(live, "bought", live.completedAtEpochMillis + 600_000L);
		bought.meterSnapshots.clear();
		bought.chests.get(4).purchased = true;
		bought.chests.get(4).purchasedOfferIndex = 0;
		bought.chests.get(0).purchased = null;
		bought.modifiers.pityLevel = 5;
		bought.modifiers.bossLuck = null;

		LuckScoringInput.Converted converted = LuckScoringInput.convert(bought, LuckTestData.defaults()).converted();
		assertEquals(Set.of("ENCHANTMENT_ULTIMATE_LEGION_1", "HOT_POTATO_BOOK"), converted.observed().purchasedKeys());
		assertTrue(converted.caveats().contains("PURCHASES_UNKNOWN"));
		assertTrue(converted.caveats().contains("MODIFIERS_PARTIALLY_UNKNOWN"));

		LuckScoringInput.History mixed = LuckScoringInput.history(List.of(live, bought), LuckTestData.defaults());
		assertEquals(List.of(), mixed.caveats(), "a preset change is not a gap");
		ModifierPreset first = mixed.runModifiers().get(0);
		ModifierPreset second = mixed.runModifiers().get(1);
		assertEquals(10, first.pityLevel());
		assertEquals(5, second.pityLevel());
		assertNull(second.bossLuck());

		HistoryConfigurations.Result configurations = HistoryConfigurations.build(mixed.observed(), mixed.gapsBefore(), mixed.runModifiers(),
			LuckTestData.defaults(), RuleVariants.defaults(), RuleEvidence.RECORDED, SelectionPolicy.highestTier(), KismetPolicy.never());
		assertTrue(configurations.scorable(), configurations.withheld().toString());
		for (Configuration configuration : configurations.configurations()) {
			assertEquals(first.resolveKnown(), configuration.modifiersFor(0), "the first run's known snapshot is never widened: " + configuration.label());
			assertEquals(5, configuration.modifiersFor(1).pityLevel(), configuration.label());
		}
		assertTrue(configurations.configurations().stream().anyMatch(c -> c.modifiersFor(1).bossLuck() == ModifierPreset.BossLuck.IV),
			"only the unknown Boss Luck of the second run is widened");
		assertEquals(2, evaluate(List.of(live, bought), 10).modifierSnapshots());
	}
}
