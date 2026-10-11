package dev.vy.drt.luck.stats;

import dev.vy.drt.luck.data.CatacombsLootDatabase;
import dev.vy.drt.luck.rules.ModifierPreset;
import dev.vy.drt.luck.rules.ResolvedModifiers;
import dev.vy.drt.luck.rules.RuleEvidence;
import dev.vy.drt.luck.rules.RuleVariants;
import dev.vy.drt.luck.sim.KismetPolicy;
import dev.vy.drt.luck.sim.SelectionPolicy;
import dev.vy.drt.luck.stats.LuckEvaluator.Configuration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Plausible configurations for an observed history in which every run keeps the modifier snapshot
 * recorded with it. Unknown fields span their extremes per snapshot; meter scenarios are planned
 * separately for each configuration because gains and resets depend on its rules and modifiers.
 */
public final class HistoryConfigurations {
	/** Beyond this the evaluation is too slow to run and too wide to mean anything. */
	public static final int MAX_CONFIGURATIONS = 64;

	private HistoryConfigurations() {
	}

	/**
	 * @param configurations baseline first; empty when withheld
	 * @param baselinePlan   the baseline configuration's meter windows, contradictions and notes
	 * @param dropped        alternatives left out because they contradict the meter readings
	 * @param withheld       why no configuration can be scored; empty when scorable
	 */
	public record Result(
		List<Configuration> configurations,
		MeterScenarioPlanner.Plan baselinePlan,
		List<String> dropped,
		List<String> withheld
	) {
		public Result {
			configurations = List.copyOf(configurations);
			dropped = List.copyOf(dropped);
			withheld = List.copyOf(withheld);
		}

		public boolean scorable() {
			return withheld.isEmpty();
		}
	}

	private record Assignment(String label, List<ResolvedModifiers> perRun) {
	}

	public static Result build(
		List<ObservedRun> runs,
		Set<Integer> gapsBefore,
		List<ModifierPreset> runPresets,
		CatacombsLootDatabase database,
		RuleVariants rules,
		RuleEvidence evidence,
		SelectionPolicy selection,
		KismetPolicy kismet
	) {
		if (runs.isEmpty()) return new Result(List.of(), null, List.of(), List.of("no runs to score"));
		if (runPresets.size() != runs.size()) throw new IllegalArgumentException(runPresets.size() + " modifier snapshots for " + runs.size() + " runs");

		Map<ModifierPreset, List<ResolvedModifiers>> extremes = new LinkedHashMap<>();
		for (ModifierPreset preset : runPresets) extremes.computeIfAbsent(preset, ModifierPreset::plausibleExtremes);
		List<ResolvedModifiers> lowest = runPresets.stream().map(preset -> extremes.get(preset).get(0)).toList();
		List<Assignment> modifierAlternatives = new ArrayList<>();
		List<ModifierPreset> uncertain = extremes.keySet().stream().filter(preset -> extremes.get(preset).size() > 1).toList();
		for (ModifierPreset preset : uncertain) {
			List<ResolvedModifiers> options = extremes.get(preset);
			for (int i = 1; i < options.size(); i++) {
				ResolvedModifiers option = options.get(i);
				String label = "modifiers " + option + (extremes.size() > 1 ? " for runs recorded with " + preset : "");
				modifierAlternatives.add(new Assignment(label, runPresets.stream().map(p -> p.equals(preset) ? option : extremes.get(p).get(0)).toList()));
			}
		}
		if (uncertain.size() > 1) {
			List<ResolvedModifiers> highest = runPresets.stream().map(p -> extremes.get(p).get(extremes.get(p).size() - 1)).toList();
			modifierAlternatives.add(new Assignment("modifiers at every snapshot's highest unknowns", highest));
		}

		MeterScenarioPlanner.Plan baselinePlan = MeterScenarioPlanner.plan(runs, gapsBefore, database, rules, lowest::get);
		if (baselinePlan.contradicted()) {
			List<String> withheld = new ArrayList<>();
			withheld.add("the meter readings contradict the captured runs under the default rules");
			withheld.addAll(baselinePlan.contradictions());
			return new Result(List.of(), baselinePlan, List.of(), withheld);
		}

		List<Configuration> out = new ArrayList<>();
		List<String> dropped = new ArrayList<>();
		List<MeterScenarioPlanner.Scenario> scenarios = baselinePlan.scenarios();
		out.add(configuration("baseline", lowest, rules, scenarios.get(0), selection, kismet));
		for (Assignment assignment : modifierAlternatives) {
			add(out, dropped, assignment.label(), assignment.perRun(), rules, runs, gapsBefore, database, selection, kismet);
		}
		for (RuleVariants variant : evidence.alternatives(rules, knownPity(runPresets))) {
			add(out, dropped, "rules " + variant, lowest, variant, runs, gapsBefore, database, selection, kismet);
		}
		for (int i = 1; i < scenarios.size(); i++) {
			out.add(configuration(scenarios.get(i).label(), lowest, rules, scenarios.get(i), selection, kismet));
		}
		if (out.size() > MAX_CONFIGURATIONS) {
			List<String> withheld = new ArrayList<>();
			withheld.add(out.size() + " plausible configurations exceed the limit of " + MAX_CONFIGURATIONS);
			for (MeterScenarioPlanner.Window window : baselinePlan.windows()) {
				if (window.selectionUnknown()) withheld.add("RNG Meter selection unknown on " + window.floor() + " from run " + window.startRun() + ": a reading would narrow it");
			}
			return new Result(List.of(), baselinePlan, dropped, withheld);
		}
		return new Result(out, baselinePlan, dropped, List.of());
	}

	private static void add(List<Configuration> out, List<String> dropped, String label, List<ResolvedModifiers> perRun, RuleVariants rules,
		List<ObservedRun> runs, Set<Integer> gapsBefore, CatacombsLootDatabase database, SelectionPolicy selection, KismetPolicy kismet) {
		MeterScenarioPlanner.Plan plan = MeterScenarioPlanner.plan(runs, gapsBefore, database, rules, perRun::get);
		if (plan.contradicted()) {
			dropped.add(label + " contradicts the meter readings: " + String.join("; ", plan.contradictions()));
			return;
		}
		out.add(configuration(label, perRun, rules, plan.baseline(), selection, kismet));
	}

	private static Configuration configuration(String label, List<ResolvedModifiers> perRun, RuleVariants rules,
		MeterScenarioPlanner.Scenario scenario, SelectionPolicy selection, KismetPolicy kismet) {
		return new Configuration(label, perRun.get(0), rules, Map.of(), selection, kismet, perRun, scenario.events());
	}

	/** The Pity level the rule evidence may rely on: positive only when every snapshot knows a nonzero level. */
	public static Integer knownPity(List<ModifierPreset> presets) {
		Integer lowest = null;
		for (ModifierPreset preset : presets) {
			if (preset.pityLevel() == null) return null;
			if (lowest == null || preset.pityLevel() < lowest) lowest = preset.pityLevel();
		}
		return lowest;
	}
}
