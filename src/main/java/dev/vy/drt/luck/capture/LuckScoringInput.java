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
import dev.vy.drt.luck.stats.ObservedRun;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;

/**
 * Turns stored live runs into Stage B scoring input using only what was observed. Coverage is
 * re-evaluated rather than trusted from the file; a run with any blocker is returned as rejected
 * with its reasons, and every gap the engine must widen over is returned as a caveat.
 */
public final class LuckScoringInput {
	public static final String KISMET_FEATHER = "Kismet Feather";
	public static final String DUNGEON_CHEST_KEY = "Dungeon Chest Key";

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

	/** Either a converted run or the reasons it cannot be scored. */
	public record Result(Converted converted, Rejected rejected) {
		public boolean accepted() {
			return converted != null;
		}
	}

	/**
	 * A history in completion order.
	 *
	 * @param modifiers fields shared by every converted run's snapshot; differing fields become unknown
	 * @param caveats   history-level gaps: differing snapshots, unknown meter starts, meter gaps after rejected runs, captured Kismet rerolls
	 */
	public record History(List<Converted> runs, List<Rejected> rejected, ModifierPreset modifiers, List<String> caveats) {
		public History {
			runs = List.copyOf(runs);
			rejected = List.copyOf(rejected);
			caveats = List.copyOf(caveats);
		}

		public List<ObservedRun> observed() {
			return runs.stream().map(Converted::observed).toList();
		}
	}

	public static Result convert(LuckRunRecord run, CatacombsLootDatabase database) {
		LuckCoverage coverage = LuckCoverage.evaluate(run);
		if (!coverage.qualifiesForScore) {
			return new Result(null, new Rejected(run.luckRunId, run.completedAtEpochMillis, run.floor, coverage.blockers));
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
			// The null only contains this reroll if the evaluation's Kismet policy rerolls the same chest.
			if (chest.offers.stream().anyMatch(offer -> offer.afterKismetReroll)) caveats.add("KISMET_REROLLED:" + chest.tier);
			if (chest.purchased == null) {
				purchasesUnknown = true;
			} else if (chest.purchased) {
				int index = chest.purchasedOfferIndex == null ? chest.offers.size() - 1 : chest.purchasedOfferIndex;
				purchased.addAll(luckKeys(chest.offers.get(Math.min(index, chest.offers.size() - 1))));
			}
		}
		if (purchasesUnknown) caveats.add("PURCHASES_UNKNOWN");

		RngMeterState meterBefore = meterBefore(run, floor, database, caveats);
		ObservedRun observed = new ObservedRun(context, appeared, purchased, true, meterBefore);
		ModifierPreset modifiers = run.modifiers == null ? ModifierPreset.UNKNOWN : run.modifiers.toPreset();
		return new Result(new Converted(run.luckRunId, run.completedAtEpochMillis, observed, modifiers, caveats), null);
	}

	public static History history(List<LuckRunRecord> runs, CatacombsLootDatabase database) {
		List<LuckRunRecord> ordered = new ArrayList<>(runs);
		ordered.sort(Comparator.comparingLong(run -> run.completedAtEpochMillis));
		List<Converted> converted = new ArrayList<>();
		List<Rejected> rejected = new ArrayList<>();
		List<String> caveats = new ArrayList<>();
		Set<FloorId> started = EnumSet.noneOf(FloorId.class);
		Set<FloorId> brokenSince = EnumSet.noneOf(FloorId.class);
		for (LuckRunRecord run : ordered) {
			Result result = convert(run, database);
			FloorId floor = FloorId.parse(run.floor);
			if (!result.accepted()) {
				rejected.add(result.rejected());
				if (floor != null) brokenSince.add(floor);
				continue;
			}
			Converted accepted = result.converted();
			converted.add(accepted);
			if (accepted.caveats().stream().anyMatch(caveat -> caveat.startsWith("KISMET_REROLLED:"))) caveats.add("KISMET_REROLLED:" + accepted.luckRunId());
			boolean meterRead = accepted.observed().meterBefore() != null;
			if (!started.contains(floor)) {
				if (!meterRead) caveats.add("METER_START_UNKNOWN:" + floor);
			} else if (brokenSince.contains(floor) && !meterRead) {
				// The rejected run's meter gain is not simulated, so this run's meter cannot be carried forward.
				caveats.add("METER_GAP:" + accepted.luckRunId());
			}
			started.add(floor);
			brokenSince.remove(floor);
		}
		ModifierPreset merged = merge(converted.stream().map(Converted::modifiers).toList(), caveats);
		return new History(converted, rejected, merged, caveats);
	}

	/** Rolled-item keys of one offer; essence totals are not single table entries and are left out. */
	private static List<String> luckKeys(LuckRunRecord.LuckOfferSnapshot offer) {
		List<String> out = new ArrayList<>();
		for (LuckRunRecord.LuckContentLine line : offer.contents) {
			if (line.key != null && !LootEntry.Kind.ESSENCE.name().equals(line.kind)) out.add(line.key);
		}
		return out;
	}

	private static RngMeterState meterBefore(LuckRunRecord run, FloorId floor, CatacombsLootDatabase database, List<String> caveats) {
		for (LuckRunRecord.LuckMeterSnapshot snapshot : run.meterSnapshots) {
			if (!LuckRunRecord.PHASE_BEFORE_RUN.equals(snapshot.phase) || !floor.name().equalsIgnoreCase(snapshot.floor)) continue;
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

	private static ModifierPreset merge(List<ModifierPreset> presets, List<String> caveats) {
		if (presets.isEmpty()) return ModifierPreset.UNKNOWN;
		List<String> differing = new ArrayList<>();
		ModifierPreset.BossLuck boss = same(presets, ModifierPreset::bossLuck, "bossLuck", differing);
		ModifierPreset.TreasureAccessory treasure = same(presets, ModifierPreset::treasureAccessory, "treasureAccessory", differing);
		Integer box = same(presets, ModifierPreset::catacombsBoxLevel, "catacombsBoxLevel", differing);
		Integer echoBoxes = same(presets, ModifierPreset::echoOfBoxesLevel, "echoOfBoxesLevel", differing);
		Integer echoEchoes = same(presets, ModifierPreset::echoOfEchoesLevel, "echoOfEchoesLevel", differing);
		Integer pity = same(presets, ModifierPreset::pityLevel, "pityLevel", differing);
		if (!differing.isEmpty()) caveats.add("MODIFIER_SNAPSHOTS_DIFFER:" + String.join(",", differing));
		return new ModifierPreset(boss, treasure, box, echoBoxes, echoEchoes, pity);
	}

	private static <T> T same(List<ModifierPreset> presets, Function<ModifierPreset, T> field, String name, List<String> differing) {
		T value = field.apply(presets.get(0));
		for (ModifierPreset preset : presets) {
			if (!Objects.equals(value, field.apply(preset))) {
				differing.add(name);
				return null;
			}
		}
		return value;
	}
}
