package dev.vy.drt.luck.capture;

import dev.vy.drt.luck.ChestTier;
import dev.vy.drt.luck.FloorId;
import dev.vy.drt.luck.ScoreTier;
import dev.vy.drt.luck.data.CatacombsLootDatabase;
import dev.vy.drt.luck.data.LootEntry;
import dev.vy.drt.luck.meter.RngMeterModel;
import dev.vy.drt.luck.meter.RngMeterState;
import dev.vy.drt.luck.rules.ModifierPreset;
import dev.vy.drt.luck.sim.RunContext;
import dev.vy.drt.luck.stats.LuckEvaluator;
import dev.vy.drt.luck.stats.ObservedRun;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/**
 * Turns stored live runs into Stage B scoring input using only what was observed. Coverage is
 * re-evaluated rather than trusted from the file. A run with any blocker is returned as rejected
 * with its reasons; a run with a confirmed or suspected Kismet reroll is held out as excluded with
 * its evidence until rerolls can be simulated; every gap the engine must widen over is a caveat.
 */
public final class LuckScoringInput {
	public static final String KISMET_FEATHER = "Kismet Feather";
	public static final String DUNGEON_CHEST_KEY = "Dungeon Chest Key";
	public static final String KISMET_REROLL_CONFIRMED = "KISMET_REROLL_CONFIRMED";
	public static final String KISMET_REROLL_SUSPECTED = "KISMET_REROLL_SUSPECTED";
	private static final Set<String> KISMET_BLOCKERS = Set.of("OFFER_CHANGED_UNVERIFIED", "KISMET_ORIGINAL_OFFER_MISSING");

	private LuckScoringInput() {
	}

	/** One run in engine form, with the modifier snapshot taken at its completion. */
	public record Converted(String luckRunId, long completedAtEpochMillis, ObservedRun observed, ModifierPreset modifiers, List<String> caveats) {
		public Converted {
			caveats = List.copyOf(caveats);
		}
	}

	public record Rejected(String luckRunId, long completedAtEpochMillis, String floor, List<String> reasons) {
		public Rejected {
			reasons = List.copyOf(reasons);
		}
	}

	/**
	 * A run held out of the headline score because of a Kismet reroll. Nothing else blocks it, so it
	 * can be scored once a matching reroll policy exists.
	 *
	 * @param reason       {@link #KISMET_REROLL_CONFIRMED} or {@link #KISMET_REROLL_SUSPECTED}
	 * @param kismetStatus the run's {@link LuckCoverage.KismetStatus}
	 * @param evidence     per chest: which offers were rerolls or unexplained changes
	 */
	public record Excluded(String luckRunId, long completedAtEpochMillis, String floor, String reason, String kismetStatus, List<String> evidence) {
		public Excluded {
			evidence = List.copyOf(evidence);
		}
	}

	/** Exactly one of the three is set. */
	public record Result(Converted converted, Rejected rejected, Excluded excluded) {
		public boolean accepted() {
			return converted != null;
		}
	}

	/**
	 * A history in completion order.
	 *
	 * @param gapsBefore indices into {@code runs} of runs preceded, since the previous scored run on their floor, by a rejected or excluded run
	 * @param caveats    history-level gaps: unknown meter starts and meter gaps after missing runs
	 */
	public record History(List<Converted> runs, List<Rejected> rejected, List<Excluded> excluded, Set<Integer> gapsBefore, List<String> caveats) {
		public History {
			runs = List.copyOf(runs);
			rejected = List.copyOf(rejected);
			excluded = List.copyOf(excluded);
			gapsBefore = Set.copyOf(gapsBefore);
			caveats = List.copyOf(caveats);
		}

		public List<ObservedRun> observed() {
			return runs.stream().map(Converted::observed).toList();
		}

		/** Each scored run's own modifier snapshot, in history order. */
		public List<ModifierPreset> runModifiers() {
			return runs.stream().map(Converted::modifiers).toList();
		}

		public int recorded() {
			return runs.size() + rejected.size() + excluded.size();
		}
	}

	public static Result convert(LuckRunRecord run, CatacombsLootDatabase database) {
		LuckCoverage coverage = LuckCoverage.evaluate(run);
		boolean onlyKismetBlocks = !coverage.blockers.isEmpty() && KISMET_BLOCKERS.containsAll(coverage.blockers);
		boolean rerolled = LuckCoverage.KismetStatus.REROLL_WITH_ORIGINAL.name().equals(coverage.kismetStatus);
		if (!coverage.qualifiesForScore && !onlyKismetBlocks) {
			return new Result(null, new Rejected(run.luckRunId, run.completedAtEpochMillis, run.floor, coverage.blockers), null);
		}
		if (onlyKismetBlocks || rerolled) {
			String reason = rerolled && coverage.qualifiesForScore ? KISMET_REROLL_CONFIRMED : KISMET_REROLL_SUSPECTED;
			return new Result(null, null, new Excluded(run.luckRunId, run.completedAtEpochMillis, run.floor, reason, coverage.kismetStatus, kismetEvidence(run)));
		}
		FloorId floor = FloorId.parse(run.floor);
		Set<ChestTier> expected = LuckCoverage.expectedChests(floor, ScoreTier.fromScore(run.score));
		List<String> caveats = new ArrayList<>(coverage.caveats);

		List<String> listed = run.croesus == null ? List.of() : run.croesus.availableModifiers;
		if (run.croesus == null) caveats.add("CROESUS_MODIFIERS_UNKNOWN");
		RunContext context = new RunContext(floor, run.score, listed.contains(KISMET_FEATHER), listed.contains(DUNGEON_CHEST_KEY));

		Set<String> appeared = new LinkedHashSet<>();
		Set<String> purchased = new LinkedHashSet<>();
		boolean purchasesUnknown = false;
		for (LuckRunRecord.LuckChestRecord chest : run.chests) {
			if (chest.offers.isEmpty() || expected.stream().noneMatch(tier -> tier.name().equalsIgnoreCase(chest.tier.trim()))) continue;
			for (LuckRunRecord.LuckOfferSnapshot offer : chest.offers) appeared.addAll(luckKeys(offer));
			if (chest.purchased == null) {
				purchasesUnknown = true;
			} else if (chest.purchased) {
				int index = chest.purchasedOfferIndex == null ? chest.offers.size() - 1 : chest.purchasedOfferIndex;
				purchased.addAll(luckKeys(chest.offers.get(Math.min(index, chest.offers.size() - 1))));
			}
		}
		if (purchasesUnknown) caveats.add("PURCHASES_UNKNOWN");

		RngMeterState meterBefore = meterReading(run, floor, LuckRunRecord.PHASE_BEFORE_RUN, database, caveats);
		RngMeterState meterAfter = meterReading(run, floor, LuckRunRecord.PHASE_AFTER_RUN, database, new ArrayList<>());
		ObservedRun observed = new ObservedRun(context, appeared, purchased, true, meterBefore, meterAfter);
		ModifierPreset modifiers = run.modifiers == null ? ModifierPreset.UNKNOWN : run.modifiers.toPreset();
		return new Result(new Converted(run.luckRunId, run.completedAtEpochMillis, observed, modifiers, caveats), null, null);
	}

	public static History history(List<LuckRunRecord> runs, CatacombsLootDatabase database) {
		List<LuckRunRecord> ordered = new ArrayList<>(runs);
		ordered.sort(Comparator.comparingLong(run -> run.completedAtEpochMillis));
		List<Converted> converted = new ArrayList<>();
		List<Rejected> rejected = new ArrayList<>();
		List<Excluded> excluded = new ArrayList<>();
		Set<Integer> gapsBefore = new TreeSet<>();
		List<String> caveats = new ArrayList<>();
		Set<FloorId> started = EnumSet.noneOf(FloorId.class);
		Set<FloorId> brokenSince = EnumSet.noneOf(FloorId.class);
		for (LuckRunRecord run : ordered) {
			Result result = convert(run, database);
			FloorId floor = FloorId.parse(run.floor);
			if (!result.accepted()) {
				if (result.rejected() != null) rejected.add(result.rejected());
				else excluded.add(result.excluded());
				if (floor != null) brokenSince.add(floor);
				continue;
			}
			Converted accepted = result.converted();
			boolean meterRead = accepted.observed().meterBefore() != null;
			if (!started.contains(floor)) {
				if (!meterRead) caveats.add("METER_START_UNKNOWN:" + floor);
			} else if (brokenSince.contains(floor)) {
				gapsBefore.add(converted.size());
				// The missing run's meter gain and any reset are not simulated, so the meter cannot be carried past it.
				if (!meterRead) caveats.add("METER_GAP:" + accepted.luckRunId());
			}
			converted.add(accepted);
			started.add(floor);
			brokenSince.remove(floor);
		}
		return new History(converted, rejected, excluded, gapsBefore, caveats);
	}

	/**
	 * A rejected run as the evaluator's missing-run bounds see it, or null when its floor is unknown.
	 * Its context comes from the score, else the grade; without either, or when an unexpected chest
	 * shows the grade is wrong, it is an S+ stand-in. Its outcome is exact only when the score alone
	 * blocked it and every eligible chest's original offer was seen.
	 */
	public static LuckEvaluator.MissingRun missingRun(LuckRunRecord run, CatacombsLootDatabase database) {
		FloorId floor = FloorId.parse(run.floor);
		if (floor == null) return null;
		LuckCoverage coverage = LuckCoverage.evaluate(run);
		ScoreTier grade = run.score != null ? ScoreTier.fromScore(run.score) : LuckCoverage.scoreTier(run);
		boolean assumed = grade == null || coverage.blockers.contains("UNEXPECTED_CHEST");
		int score = assumed ? ScoreTier.S_PLUS.minimumScore() : run.score != null ? run.score : grade.minimumScore();

		Set<ChestTier> expected = LuckCoverage.expectedChests(floor, ScoreTier.fromScore(score));
		Set<String> seen = new LinkedHashSet<>();
		Set<ChestTier> seenChests = EnumSet.noneOf(ChestTier.class);
		for (LuckRunRecord.LuckChestRecord chest : run.chests) {
			if (chest.offers.isEmpty() || chest.offers.get(0).afterKismetReroll) continue;
			ChestTier tier = expected.stream().filter(t -> t.name().equalsIgnoreCase(chest.tier.trim())).findFirst().orElse(null);
			if (tier == null) continue;
			seen.addAll(luckKeys(chest.offers.get(0)));
			seenChests.add(tier);
		}
		boolean known = !assumed && seenChests.containsAll(expected) && Set.of("NUMERIC_SCORE_MISSING").containsAll(coverage.blockers);
		ModifierPreset preset = run.modifiers == null ? ModifierPreset.UNKNOWN : run.modifiers.toPreset();
		RunContext context = RunContext.of(floor, score);
		return new LuckEvaluator.MissingRun(run.luckRunId, context, preset.plausibleExtremes().get(0), seen, known, assumed);
	}

	/** Rolled-item keys of one offer; essence totals are not single table entries and are left out. */
	private static List<String> luckKeys(LuckRunRecord.LuckOfferSnapshot offer) {
		List<String> out = new ArrayList<>();
		for (LuckRunRecord.LuckContentLine line : offer.contents) {
			if (line.key != null && !LootEntry.Kind.ESSENCE.name().equals(line.kind)) out.add(line.key);
		}
		return out;
	}

	private static List<String> kismetEvidence(LuckRunRecord run) {
		List<String> out = new ArrayList<>();
		for (LuckRunRecord.LuckChestRecord chest : run.chests) {
			boolean hasOriginal = chest.offers.stream().anyMatch(offer -> !offer.afterKismetReroll);
			for (LuckRunRecord.LuckOfferSnapshot offer : chest.offers) {
				if (offer.afterKismetReroll) {
					out.add(chest.tier + ": offer " + offer.index + " after a Kismet reroll" + (hasOriginal ? "" : ", original not seen"));
				} else if (offer.changedFromPrevious) {
					out.add(chest.tier + ": contents changed at offer " + offer.index + " without a recorded reroll");
				}
			}
		}
		return out;
	}

	private static RngMeterState meterReading(LuckRunRecord run, FloorId floor, String phase, CatacombsLootDatabase database, List<String> caveats) {
		for (LuckRunRecord.LuckMeterSnapshot snapshot : run.meterSnapshots) {
			if (!phase.equals(snapshot.phase) || !floor.name().equalsIgnoreCase(snapshot.floor)) continue;
			if (!LuckCoverage.MeterStatus.OBSERVED.name().equals(snapshot.state) || snapshot.storedScore == null) continue;
			if (snapshot.selectedItem == null) return new RngMeterState(floor, null, snapshot.storedScore);
			if (snapshot.selectedKey == null) return null;
			boolean selectable = RngMeterModel.selectableItems(database.floor(floor)).stream().anyMatch(entry -> entry.key().equals(snapshot.selectedKey));
			if (!selectable) {
				caveats.add("METER_SELECTION_NOT_SELECTABLE");
				return null;
			}
			return new RngMeterState(floor, snapshot.selectedKey, snapshot.storedScore);
		}
		return null;
	}
}
