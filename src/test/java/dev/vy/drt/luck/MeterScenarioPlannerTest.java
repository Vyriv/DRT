package dev.vy.drt.luck;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.vy.drt.luck.data.FloorTable;
import dev.vy.drt.luck.data.LootEntry;
import dev.vy.drt.luck.meter.MeterEvent;
import dev.vy.drt.luck.meter.RngMeterModel;
import dev.vy.drt.luck.meter.RngMeterState;
import dev.vy.drt.luck.rules.ModifierPreset;
import dev.vy.drt.luck.rules.ResolvedModifiers;
import dev.vy.drt.luck.rules.RuleVariants;
import dev.vy.drt.luck.sim.ChestEligibility;
import dev.vy.drt.luck.sim.RunContext;
import dev.vy.drt.luck.stats.MeterScenarioPlanner;
import dev.vy.drt.luck.stats.MeterScenarioPlanner.Plan;
import dev.vy.drt.luck.stats.MeterScenarioPlanner.Window;
import dev.vy.drt.luck.stats.MeterScenarioPlanner.WindowKind;
import dev.vy.drt.luck.stats.ObservedRun;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** Meter windows for synthetic F5 S 298 runs at Pity 10: every run adds 229 under the default rules. */
class MeterScenarioPlannerTest {
	private static final RuleVariants RULES = RuleVariants.defaults();
	private static final ResolvedModifiers PITY_10 = new ResolvedModifiers(ModifierPreset.BossLuck.NONE, ModifierPreset.TreasureAccessory.NONE, 0, 0, 0, 10);
	private static final long GAIN = 229L;
	private static final FloorTable F5 = LuckTestData.defaults().floor(FloorId.F5);
	private static final List<ChestTier> S_CHESTS = ChestEligibility.eligible(F5, ScoreTier.S, RULES);

	/** A drop whose full-meter guarantee lands in a chest an S run opens. */
	private static final String DROP = RngMeterModel.selectableItems(F5).stream().map(LootEntry::key)
		.filter(key -> RngMeterModel.guaranteeTier(F5, key, S_CHESTS, RULES) != null).findFirst().orElseThrow();
	private static final long REQUIRED = RngMeterModel.requiredScore(F5, DROP, RULES);

	private static ObservedRun run(RngMeterState before, RngMeterState after, String... appeared) {
		return new ObservedRun(new RunContext(FloorId.F5, 298, false, false), Set.of(appeared), Set.of(), true, before, after);
	}

	private static RngMeterState meter(String selected, long stored) {
		return new RngMeterState(FloorId.F5, selected, stored);
	}

	private static Plan plan(List<ObservedRun> runs, Set<Integer> gaps) {
		return MeterScenarioPlanner.plan(runs, gaps, LuckTestData.defaults(), RULES, index -> PITY_10);
	}

	@Test
	void theTestRunsGainWhatTheLiveF5SRunGained() {
		assertEquals(GAIN, RngMeterModel.scoreGain(ScoreTier.S, 298, PITY_10, RULES));
	}

	@Test
	void readingsThatMatchTheCapturedRunsNeedNoScenario() {
		Plan plan = plan(List.of(run(meter(DROP, 100), meter(DROP, 100 + GAIN)), run(null, null), run(meter(DROP, 100 + 2 * GAIN), null)), Set.of());
		assertEquals(List.of(), plan.windows());
		assertEquals(List.of(), plan.contradictions());
		assertEquals(1, plan.scenarios().size());
		assertEquals(List.of(), plan.baseline().events());
	}

	@Test
	void anUnreadStartWithNoLaterReadingSpansNothingSelectedAndEveryDrop() {
		Plan plan = plan(List.of(run(null, null), run(null, null)), Set.of());
		assertEquals(1, plan.windows().size());
		Window window = plan.windows().get(0);
		assertEquals(WindowKind.UNKNOWN_START, window.kind());
		assertEquals(0, window.startRun());
		assertFalse(window.anchored());
		assertTrue(window.selectionUnknown());
		assertNull(window.candidates().get(0).selection(), "the weakest assumption comes first: nothing selected");
		assertEquals(meter(null, 0), window.candidates().get(0).state());
		for (LootEntry entry : RngMeterModel.selectableItems(F5)) {
			boolean guaranteed = RngMeterModel.guaranteeTier(F5, entry.key(), S_CHESTS, RULES) != null;
			long fills = window.candidates().stream().filter(c -> entry.key().equals(c.selection())).count();
			assertEquals(guaranteed ? 2 : 3, fills, entry.key() + ": a full meter whose guarantee was not seen is ruled out");
		}
		for (MeterScenarioPlanner.Scenario scenario : plan.scenarios()) {
			assertTrue(scenario.events().stream().anyMatch(e -> e.beforeRun() == 0 && e.floor() == FloorId.F5), scenario.label() + " must set the start, never default to empty");
		}
	}

	@Test
	void anUnreadStartIsDerivedFromTheNextReading() {
		Plan plan = plan(List.of(run(null, null), run(meter(DROP, 1_000), null)), Set.of());
		Window window = plan.windows().get(0);
		assertEquals(WindowKind.UNKNOWN_START, window.kind());
		assertTrue(window.anchored());
		assertEquals(1, window.candidates().size());
		assertEquals(meter(DROP, 1_000 - GAIN), window.candidates().get(0).state());
		assertEquals(List.of(new MeterEvent.Observed(0, meter(DROP, 1_000 - GAIN))), plan.baseline().events());
	}

	@Test
	void aLaterReadingAfterTheFloorsLastRunAlsoAnchorsTheStart() {
		Plan plan = plan(List.of(run(null, meter(null, 4_000))), Set.of());
		assertEquals(List.of(new MeterEvent.Observed(0, meter(null, 4_000 - GAIN))), plan.baseline().events());
	}

	@Test
	void aStartThatWouldNeedNegativeProgressIsAContradiction() {
		Plan plan = plan(List.of(run(null, null), run(meter(DROP, 100), null)), Set.of());
		assertTrue(plan.contradicted());
		assertTrue(plan.contradictions().get(0).startsWith("METER_UNKNOWN_START_UNRESOLVABLE:F5 from run 0"), plan.contradictions().toString());
	}

	@Test
	void aResetInsideTheWindowKeepsOnlyStartsThatReproduceTheReading() {
		Plan plan = plan(List.of(run(null, null, DROP), run(meter(DROP, GAIN), null)), Set.of());
		Window window = plan.windows().get(0);
		assertEquals(3, window.candidates().size(), "the drop appeared, so every fill resets to the same reading");
		assertEquals(List.of(0L, Math.round(REQUIRED * 0.5), REQUIRED), window.candidates().stream().map(c -> c.state().storedScore()).toList());

		assertTrue(plan(List.of(run(null, null, DROP), run(meter(DROP, GAIN + 1), null)), Set.of()).contradicted(),
			"no start explains a reading one point off the reset");
	}

	@Test
	void aGapKeepsTheKnownSelectionAndIsNotAnUnknownStart() {
		Plan plan = plan(List.of(run(meter(DROP, 0), null), run(null, null)), Set.of(1));
		Window window = plan.windows().get(0);
		assertEquals(WindowKind.GAP, window.kind());
		assertEquals(1, window.startRun());
		assertFalse(window.selectionUnknown());
		assertTrue(window.candidates().stream().allMatch(c -> DROP.equals(c.selection())));
		assertEquals(2, window.candidates().size(), "a full meter would have forced the drop into the next run");

		Plan read = plan(List.of(run(meter(DROP, 0), null), run(meter(DROP, 5_000), null)), Set.of(1));
		assertEquals(List.of(), read.windows(), "a reading after the missing run restores the meter");
		assertFalse(read.contradicted(), "the missing run explains the progress, so it is not compared");
	}

	@Test
	void unexplainedProgressBetweenReadingsIsPlacedEarlyOrLate() {
		Plan plan = plan(List.of(run(meter(DROP, 0), null), run(null, null), run(meter(DROP, 2 * GAIN + 500), null)), Set.of());
		Window window = plan.windows().get(0);
		assertEquals(WindowKind.UNEXPLAINED_GAIN, window.kind());
		assertEquals(0, window.startRun());
		assertNull(window.candidates().get(0).state(), "baseline keeps the forward-simulated meter");
		assertEquals(meter(DROP, 500), window.candidates().get(1).state());
		assertEquals(2, plan.scenarios().size());
		assertEquals(List.of(new MeterEvent.Observed(0, meter(DROP, 500))), plan.scenarios().get(1).events());
	}

	@Test
	void lessProgressThanTheCapturedRunsGaveIsAContradiction() {
		Plan plan = plan(List.of(run(meter(DROP, 1_000), null), run(meter(DROP, 1_000), null)), Set.of());
		assertTrue(plan.contradictions().get(0).startsWith("METER_BELOW_CAPTURED_GAINS:F5 before run 1"), plan.contradictions().toString());
	}

	@Test
	void aFullMeterWhoseGuaranteeNeverAppearedIsAContradiction() {
		Plan plan = plan(List.of(run(meter(DROP, REQUIRED), null)), Set.of());
		assertTrue(plan.contradictions().get(0).startsWith("METER_GUARANTEE_NOT_SEEN:F5 run 0"), plan.contradictions().toString());
		assertFalse(plan(List.of(run(meter(DROP, REQUIRED), null, DROP)), Set.of()).contradicted());
	}

	@Test
	void aSelectionChangeBetweenReadingsIsNotedRatherThanCompared() {
		Plan plan = plan(List.of(run(meter(DROP, 1_000), null), run(meter(null, 50), null)), Set.of());
		assertFalse(plan.contradicted());
		assertEquals(List.of("METER_SELECTION_CHANGED:F5 before run 1"), plan.notes());
	}
}
