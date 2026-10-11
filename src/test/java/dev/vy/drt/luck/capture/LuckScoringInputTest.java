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
import dev.vy.drt.luck.rules.RuleVariants;
import dev.vy.drt.luck.sim.KismetPolicy;
import dev.vy.drt.luck.sim.RunRarity;
import dev.vy.drt.luck.sim.RunSimulator;
import dev.vy.drt.luck.sim.SelectionPolicy;
import dev.vy.drt.luck.stats.LuckEvaluator;
import dev.vy.drt.luck.stats.LuckEvaluator.Configuration;
import dev.vy.drt.luck.stats.LuckEvaluator.Evaluation;
import dev.vy.drt.luck.stats.LuckEvaluator.Verdict;
import dev.vy.drt.luck.stats.ObservedRun;
import dev.vy.drt.luck.stats.RareOutcomeGate;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** The live F5 captures, loaded from disk, converted for the Stage B engine and passed through its gates. */
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

	private static List<Configuration> configurations(ModifierPreset preset) {
		return LuckEvaluator.plausibleConfigurations(preset, RuleVariants.defaults(), List.of(Map.of()),
			SelectionPolicy.highestTier(), KismetPolicy.never());
	}

	private static Evaluation evaluate(LuckScoringInput.History history, int samples) {
		List<Configuration> configurations = configurations(history.modifiers());
		return LuckEvaluator.evaluate(LuckTestData.defaults(), history.observed(), configurations.get(0),
			configurations.subList(1, configurations.size()), samples, 11L);
	}

	@Test
	void theLiveRunReachesTheEngineWithNothingReconstructed() throws Exception {
		LuckScoringInput.History history = LuckScoringInput.history(loadedLiveRuns(), LuckTestData.defaults());

		assertEquals(List.of(S_PLUS_RUN), history.rejected().stream().map(LuckScoringInput.Rejected::luckRunId).toList());
		assertEquals(List.of("OFFERS_NOT_OBSERVED"), history.rejected().get(0).reasons());
		assertEquals(1, history.runs().size());

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

		assertEquals(10, history.modifiers().pityLevel());
		assertTrue(history.modifiers().isFullyKnown());
		assertEquals(List.of(), history.caveats());

		RunSimulator simulator = new RunSimulator(LuckTestData.defaults(), history.modifiers().resolveKnown(), RuleVariants.defaults());
		assertEquals(LuckCoverage.expectedChests(FloorId.F5, ScoreTier.S).stream().toList(), simulator.eligibleChests(FloorId.F5, ScoreTier.S),
			"capture coverage and the simulator agree on eligible chests");
		RunRarity rarity = simulator.rarity(FloorId.F5, ScoreTier.S);
		for (String key : observed.appearedKeys()) assertTrue(rarity.perRun(key) > 0.0, key + " must be a rollable item for an F5 S run");
	}

	@Test
	void aSingleLiveRunIsWithheldByTheMinimumDataGate() throws Exception {
		Evaluation evaluation = evaluate(LuckScoringInput.history(loadedLiveRuns(), LuckTestData.defaults()), 50);
		assertEquals(Verdict.INSUFFICIENT_DATA, evaluation.verdict());
		assertTrue(evaluation.reasons().stream().anyMatch(r -> r.startsWith("F5 loot data is")), evaluation.reasons().toString());
		assertTrue(evaluation.reasons().stream().anyMatch(r -> r.startsWith("S-run meter gain rule is PROVISIONAL")));
		assertTrue(evaluation.reasons().stream().anyMatch(r -> r.startsWith("only ") && r.endsWith("need 1")));
		assertTrue(evaluation.reasons().stream().noneMatch(r -> r.startsWith("ignored items")), "essence totals must not read as unmodelled items");
	}

	@Test
	void enoughLiveRunsPassTheGateAndAreScored() throws Exception {
		LuckRunRecord live = find(loadedLiveRuns(), S_RUN);
		List<LuckRunRecord> runs = new ArrayList<>();
		for (int i = 0; i < 150; i++) runs.add(copy(live, "copy-" + i, live.completedAtEpochMillis + i * 600_000L));
		LuckScoringInput.History history = LuckScoringInput.history(runs, LuckTestData.defaults());
		assertEquals(150, history.runs().size());
		RunSimulator simulator = new RunSimulator(LuckTestData.defaults(), history.modifiers().resolveKnown(), RuleVariants.defaults());
		assertTrue(RareOutcomeGate.scorable(history.observed().stream().map(ObservedRun::run).toList(), simulator));

		Evaluation evaluation = evaluate(history, 60);
		assertTrue(evaluation.verdict() == Verdict.PROVISIONAL || evaluation.verdict() == Verdict.UNRESOLVED, evaluation.verdict() + " " + evaluation.reasons());
		assertNotNull(evaluation.baseline());
		assertEquals(12, evaluation.alternatives().size(), "known Pity 10: twelve rule alternatives, no modifier extremes");
	}

	@Test
	void runsThatCannotBeScoredAreRejectedNotDropped() throws Exception {
		LuckRunRecord live = find(loadedLiveRuns(), S_RUN);
		LuckRunRecord gradeOnly = copy(live, "grade-only", live.completedAtEpochMillis + 1L);
		gradeOnly.score = null;
		LuckRunRecord rerolled = copy(live, "changed", live.completedAtEpochMillis + 2L);
		LuckRunRecord.LuckOfferSnapshot changed = GSON.fromJson(GSON.toJson(rerolled.chests.get(4).offers.get(0)), LuckRunRecord.LuckOfferSnapshot.class);
		changed.index = 1;
		changed.changedFromPrevious = true;
		changed.contents.get(0).text = "Enchanted Book (Legion II)";
		rerolled.chests.get(4).offers.add(changed);

		LuckScoringInput.History history = LuckScoringInput.history(List.of(live, gradeOnly, rerolled), LuckTestData.defaults());
		assertEquals(1, history.runs().size());
		assertEquals(List.of("NUMERIC_SCORE_MISSING"), history.rejected().get(0).reasons());
		assertEquals(List.of("OFFER_CHANGED_UNVERIFIED"), history.rejected().get(1).reasons());

		ObservedRun incomplete = new ObservedRun(history.observed().get(0).run(), Set.of(), Set.of(), false);
		List<ObservedRun> withIncomplete = new ArrayList<>(history.observed());
		withIncomplete.add(incomplete);
		List<Configuration> configurations = configurations(history.modifiers());
		assertEquals(Verdict.INCOMPLETE_RECORDS, LuckEvaluator.evaluate(LuckTestData.defaults(), withIncomplete, configurations.get(0),
			configurations.subList(1, configurations.size()), 10, 1L).verdict(), "the engine refuses an incomplete record if one is passed");
	}

	@Test
	void aCapturedRerollIsScoredWithItsOriginalAndFlagged() throws Exception {
		LuckRunRecord live = find(loadedLiveRuns(), S_RUN);
		LuckRunRecord rerolled = copy(live, "rerolled", live.completedAtEpochMillis);
		LuckRunRecord.LuckChestRecord obsidian = rerolled.chests.get(4);
		LuckRunRecord.LuckOfferSnapshot after = GSON.fromJson(GSON.toJson(obsidian.offers.get(0)), LuckRunRecord.LuckOfferSnapshot.class);
		after.index = 1;
		after.changedFromPrevious = true;
		after.afterKismetReroll = true;
		after.contents.removeIf(line -> "ENCHANTMENT_ULTIMATE_LEGION_1".equals(line.key));
		obsidian.offers.add(after);

		assertEquals("REROLL_WITH_ORIGINAL", LuckCoverage.evaluate(rerolled).kismetStatus);
		LuckScoringInput.History history = LuckScoringInput.history(List.of(rerolled), LuckTestData.defaults());
		LuckScoringInput.Converted converted = history.runs().get(0);
		assertTrue(converted.observed().appearedKeys().contains("ENCHANTMENT_ULTIMATE_LEGION_1"), "the original offer still appeared, as the simulator counts it");
		assertTrue(converted.caveats().contains("KISMET_REROLLED:OBSIDIAN"));
		assertEquals(List.of("KISMET_REROLLED:rerolled"), history.caveats());

		after.afterKismetReroll = false;
		assertEquals(List.of("OFFER_CHANGED_UNVERIFIED"), LuckScoringInput.convert(rerolled, LuckTestData.defaults()).rejected().reasons(),
			"a change not known to be a reroll is never scored");
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

		LuckScoringInput.History unknownStart = LuckScoringInput.history(List.of(noMeter), LuckTestData.defaults());
		assertEquals(List.of("METER_START_UNKNOWN:F5"), unknownStart.caveats());
		assertNull(unknownStart.observed().get(0).meterBefore());
		assertTrue(unknownStart.runs().get(0).caveats().contains("METER_UNKNOWN"));
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
	void purchasesAndModifierSnapshotsStayExplicit() throws Exception {
		LuckRunRecord live = find(loadedLiveRuns(), S_RUN);
		LuckRunRecord bought = copy(live, "bought", live.completedAtEpochMillis + 1L);
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
		assertEquals(List.of("MODIFIER_SNAPSHOTS_DIFFER:bossLuck,pityLevel"), mixed.caveats());
		assertNull(mixed.modifiers().pityLevel(), "differing Pity snapshots are evaluated as unknown, never as one of them");
		assertNull(mixed.modifiers().bossLuck());
		assertEquals(10, mixed.modifiers().catacombsBoxLevel());
		assertFalse(configurations(mixed.modifiers()).stream().allMatch(c -> c.modifiers().pityLevel() == 10));
	}
}
