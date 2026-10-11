package dev.vy.drt.luck.sim;

import dev.vy.drt.luck.ChestTier;
import dev.vy.drt.luck.FloorId;
import dev.vy.drt.luck.ScoreTier;
import dev.vy.drt.luck.data.CatacombsLootDatabase;
import dev.vy.drt.luck.data.ChestTable;
import dev.vy.drt.luck.data.FloorTable;
import dev.vy.drt.luck.data.LootEntry;
import dev.vy.drt.luck.engine.ChestRoll;
import dev.vy.drt.luck.engine.ChestRoller;
import dev.vy.drt.luck.engine.ExactChestDistribution;
import dev.vy.drt.luck.engine.MeterBoost;
import dev.vy.drt.luck.meter.RngMeterModel;
import dev.vy.drt.luck.meter.RngMeterState;
import dev.vy.drt.luck.rules.QualityCalculator;
import dev.vy.drt.luck.rules.ResolvedModifiers;
import dev.vy.drt.luck.rules.RuleVariants;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.random.RandomGenerator;

/** Simulates complete runs: every eligible chest, optional Kismet reroll, purchases and meter progression. */
public final class RunSimulator {
	private final CatacombsLootDatabase database;
	private final ResolvedModifiers modifiers;
	private final RuleVariants rules;
	private final Map<String, RunRarity> rarityCache = new HashMap<>();

	public RunSimulator(CatacombsLootDatabase database, ResolvedModifiers modifiers, RuleVariants rules) {
		this.database = database;
		this.modifiers = modifiers;
		this.rules = rules;
	}

	public CatacombsLootDatabase database() {
		return database;
	}

	public ResolvedModifiers modifiers() {
		return modifiers;
	}

	public RuleVariants rules() {
		return rules;
	}

	public List<ChestTier> eligibleChests(FloorId floor, ScoreTier score) {
		return ChestEligibility.eligible(database.floor(floor), score, rules);
	}

	public int chestQuality(FloorId floor, ChestTier tier, ScoreTier score) {
		return QualityCalculator.quality(database.floor(floor).chest(tier), score, modifiers, rules);
	}

	/** Exact per-run appearance probabilities with no meter boost, cached per floor and score tier. */
	public RunRarity rarity(FloorId floor, ScoreTier score) {
		return rarityCache.computeIfAbsent(floor + ":" + score, ignored -> {
			FloorTable table = database.floor(floor);
			Map<ChestTier, ExactChestDistribution.Result> results = new EnumMap<>(ChestTier.class);
			for (ChestTier tier : eligibleChests(floor, score)) {
				results.put(tier, ExactChestDistribution.compute(table.chest(tier), chestQuality(floor, tier, score), MeterBoost.NONE));
			}
			return new RunRarity(results);
		});
	}

	public RunOutcome simulate(
		RunContext run,
		RngMeterState meter,
		SelectionPolicy selection,
		KismetPolicy kismet,
		RandomGenerator random
	) {
		if (meter.floor() != run.floor()) throw new IllegalArgumentException("Meter floor does not match run floor");
		FloorTable floor = database.floor(run.floor());
		ScoreTier score = run.scoreTier();
		List<ChestTier> eligible = eligibleChests(run.floor(), score);

		long required = meter.hasSelection() ? RngMeterModel.requiredScore(floor, meter.selectedKey(), rules) : 0L;
		ChestTier guaranteeTier = meter.hasSelection() && RngMeterModel.isFull(meter.storedScore(), required)
			? RngMeterModel.guaranteeTier(floor, meter.selectedKey(), eligible, rules)
			: null;

		List<ChestRoll> initial = new ArrayList<>(eligible.size());
		Map<ChestTier, MeterBoost> boosts = new EnumMap<>(ChestTier.class);
		boolean guaranteeApplied = false;
		for (ChestTier tier : eligible) {
			ChestTable chest = floor.chest(tier);
			MeterBoost boost = meter.hasSelection() ? RngMeterModel.boostFor(meter, required, tier, guaranteeTier) : MeterBoost.NONE;
			if (boost.entryKey() != null && !chest.contains(boost.entryKey())) boost = boost.withoutForce();
			boosts.put(tier, boost);
			ChestRoll roll = ChestRoller.roll(chest, chestQuality(run.floor(), tier, score), boost, random);
			guaranteeApplied |= roll.meterForced();
			initial.add(roll);
		}

		RunRarity rarity = rarity(run.floor(), score);
		List<ChestRoll> offers = new ArrayList<>(initial);
		ChestTier rerolled = null;
		ChestRoll reroll = null;
		if (run.kismetAvailable()) {
			ChestTier target = kismet.chooseReroll(new OfferView(floor, run, offers, rarity));
			if (target != null) {
				int index = eligible.indexOf(target);
				if (index < 0) throw new IllegalStateException("Kismet policy chose an unoffered chest " + target);
				MeterBoost boost = rules.kismetMeter() == RuleVariants.KismetMeter.APPLIES ? boosts.get(target) : MeterBoost.NONE;
				reroll = ChestRoller.roll(floor.chest(target), chestQuality(run.floor(), target, score), boost, random);
				guaranteeApplied |= reroll.meterForced();
				rerolled = target;
				offers.set(index, reroll);
			}
		}

		List<ChestTier> purchased = selection.choose(new OfferView(floor, run, offers, rarity));
		if (purchased.size() > run.maxPurchases() || new HashSet<>(purchased).size() != purchased.size()) {
			throw new IllegalStateException(selection.name() + " bought an invalid chest set " + purchased);
		}

		Set<String> appeared = new HashSet<>();
		for (ChestRoll roll : initial) addLuckKeys(roll, appeared);
		if (reroll != null) addLuckKeys(reroll, appeared);
		Set<String> bought = new HashSet<>();
		for (ChestTier tier : purchased) {
			int index = eligible.indexOf(tier);
			if (index < 0) throw new IllegalStateException(selection.name() + " bought an unoffered chest " + tier);
			addLuckKeys(offers.get(index), bought);
		}

		long gain = RngMeterModel.scoreGain(score, run.score(), modifiers, rules);
		RngMeterState after = RngMeterModel.afterRun(
			meter,
			required,
			meter.hasSelection() && appeared.contains(meter.selectedKey()),
			meter.hasSelection() && bought.contains(meter.selectedKey()),
			gain,
			rules
		);

		return new RunOutcome(run, initial, rerolled, reroll, offers, purchased, appeared, bought, meter, after, guaranteeApplied);
	}

	private static void addLuckKeys(ChestRoll roll, Set<String> keys) {
		for (LootEntry entry : roll.rolledLuckItems()) keys.add(entry.key());
	}
}
