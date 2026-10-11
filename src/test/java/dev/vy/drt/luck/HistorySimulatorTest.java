package dev.vy.drt.luck;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.vy.drt.luck.meter.RngMeterModel;
import dev.vy.drt.luck.meter.RngMeterState;
import dev.vy.drt.luck.rules.ResolvedModifiers;
import dev.vy.drt.luck.rules.RuleVariants;
import dev.vy.drt.luck.sim.HistorySimulator;
import dev.vy.drt.luck.sim.KismetPolicy;
import dev.vy.drt.luck.sim.RunContext;
import dev.vy.drt.luck.sim.RunOutcome;
import dev.vy.drt.luck.sim.RunSimulator;
import dev.vy.drt.luck.sim.SelectionPolicy;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;

class HistorySimulatorTest {
	private static final String ITEM = "BONZO_STAFF";

	private static HistorySimulator history(RuleVariants rules) {
		return new HistorySimulator(new RunSimulator(LuckTestData.defaults(), ResolvedModifiers.NONE, rules));
	}

	private static List<RunContext> runs(int count, int score) {
		return Collections.nCopies(count, RunContext.of(FloorId.F1, score));
	}

	@Test
	void sameSeedReplaysIdentically() {
		RngMeterState meter = new RngMeterState(FloorId.F1, ITEM, 0);
		List<RunOutcome> a = history(RuleVariants.defaults()).simulate(runs(200, 300), meter, SelectionPolicy.highestTier(), KismetPolicy.never(), 99L);
		List<RunOutcome> b = history(RuleVariants.defaults()).simulate(runs(200, 300), meter, SelectionPolicy.highestTier(), KismetPolicy.never(), 99L);
		assertEquals(a, b);
	}

	@Test
	void meterBoundsTheGapBetweenAppearances() {
		RuleVariants rules = RuleVariants.defaults();
		long required = RngMeterModel.requiredScore(LuckTestData.defaults().floor(FloorId.F1), ITEM, rules);
		long runsToFill = (required + 299) / 300;
		List<RunOutcome> outcomes = history(rules).simulate(runs(3000, 300), new RngMeterState(FloorId.F1, ITEM, 0),
			SelectionPolicy.highestTier(), KismetPolicy.never(), 7L);
		int gap = 0;
		int maxGap = 0;
		int guarantees = 0;
		for (RunOutcome outcome : outcomes) {
			gap++;
			if (outcome.appeared(ITEM)) {
				maxGap = Math.max(maxGap, gap);
				gap = 0;
			}
			if (outcome.meterGuaranteeApplied()) guarantees++;
		}
		assertTrue(maxGap <= runsToFill + 1, "max gap " + maxGap + " runs to fill " + runsToFill);
		assertTrue(guarantees > 0);
	}

	@Test
	void meterCarriesBetweenRuns() {
		List<RunOutcome> outcomes = history(RuleVariants.defaults()).simulate(runs(50, 300), new RngMeterState(FloorId.F1, ITEM, 0),
			SelectionPolicy.highestTier(), KismetPolicy.never(), 3L);
		for (int i = 1; i < outcomes.size(); i++) {
			assertEquals(outcomes.get(i - 1).meterAfter(), outcomes.get(i).meterBefore());
		}
	}

	@Test
	void selectedMeterIncreasesAppearancesOverNoSelection() {
		RuleVariants rules = RuleVariants.defaults();
		int withMeter = count(history(rules).simulate(runs(3000, 300), new RngMeterState(FloorId.F1, ITEM, 0),
			SelectionPolicy.highestTier(), KismetPolicy.never(), 17L));
		int without = count(history(rules).simulate(runs(3000, 300), RngMeterState.empty(FloorId.F1),
			SelectionPolicy.highestTier(), KismetPolicy.never(), 17L));
		assertTrue(withMeter > without, withMeter + " vs " + without);
	}

	private static int count(List<RunOutcome> outcomes) {
		int count = 0;
		for (RunOutcome outcome : outcomes) if (outcome.appeared(ITEM)) count++;
		return count;
	}
}
