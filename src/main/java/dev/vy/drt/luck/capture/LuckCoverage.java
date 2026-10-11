package dev.vy.drt.luck.capture;

import dev.vy.drt.luck.ChestTier;
import dev.vy.drt.luck.FloorId;
import dev.vy.drt.luck.ScoreTier;
import dev.vy.drt.luck.sim.ChestEligibility;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * What was and was not observed for one run. {@code blockers} prevent the run from being scored;
 * {@code caveats} are gaps the scoring engine handles by widening its range instead.
 */
public final class LuckCoverage {
	public static final int VERSION = 2;

	public enum ScoreStatus { OBSERVED, GRADE_ONLY, MISSING }

	public enum ModifierStatus { COMPLETE, PARTIAL, UNSET }

	public enum OfferStatus { NOT_OBSERVED, PARTIAL, COMPLETE }

	public enum MeterStatus { OBSERVED, DERIVED, UNKNOWN }

	/**
	 * Kismet handling is provisional until a real reroll has been captured: a change in a chest's
	 * contents is recorded as {@code CONTENTS_CHANGED_UNVERIFIED} and never read as a reroll.
	 */
	public enum KismetStatus { NOT_OBSERVED, NO_REROLL, REROLL_WITH_ORIGINAL, REROLL_WITHOUT_ORIGINAL, CONTENTS_CHANGED_UNVERIFIED }

	public int version = VERSION;
	public String scoreStatus = ScoreStatus.MISSING.name();
	public String modifierStatus = ModifierStatus.UNSET.name();
	public String offerStatus = OfferStatus.NOT_OBSERVED.name();
	public String meterStatus = MeterStatus.UNKNOWN.name();
	public String kismetStatus = KismetStatus.NOT_OBSERVED.name();
	public List<String> expectedChests = new ArrayList<>();
	public List<String> observedChests = new ArrayList<>();
	public boolean qualifiesForScore;
	public List<String> blockers = new ArrayList<>();
	public List<String> caveats = new ArrayList<>();

	public static LuckCoverage evaluate(LuckRunRecord run) {
		LuckCoverage coverage = new LuckCoverage();
		FloorId floor = FloorId.parse(run.floor);
		ScoreTier tier = scoreTier(run);

		if (run.score != null) {
			coverage.scoreStatus = ScoreStatus.OBSERVED.name();
		} else if (tier != null) {
			coverage.scoreStatus = ScoreStatus.GRADE_ONLY.name();
			coverage.caveats.add("NUMERIC_SCORE_MISSING");
		} else {
			coverage.scoreStatus = ScoreStatus.MISSING.name();
		}
		if (floor == null) coverage.blockers.add("FLOOR_UNKNOWN");
		if (tier == null) coverage.blockers.add("GRADE_UNKNOWN");

		int unknownModifiers = run.modifiers == null ? 6 : run.modifiers.unknownFieldCount();
		if (unknownModifiers == 0) {
			coverage.modifierStatus = ModifierStatus.COMPLETE.name();
		} else if (unknownModifiers < 6) {
			coverage.modifierStatus = ModifierStatus.PARTIAL.name();
			coverage.caveats.add("MODIFIERS_PARTIALLY_UNKNOWN");
		} else {
			coverage.modifierStatus = ModifierStatus.UNSET.name();
			coverage.caveats.add("MODIFIERS_UNKNOWN");
		}

		Set<ChestTier> expected = floor == null || tier == null ? Set.of() : expectedChests(floor, tier);
		Set<ChestTier> observed = EnumSet.noneOf(ChestTier.class);
		boolean rerollWithoutOriginal = false;
		boolean rerollWithOriginal = false;
		boolean anyOfferSeen = false;
		boolean unverifiedChange = false;
		for (LuckRunRecord.LuckChestRecord chest : run.chests) {
			ChestTier chestTier = parseTier(chest.tier);
			if (chestTier == null || chest.offers.isEmpty()) continue;
			anyOfferSeen = true;
			if (chest.offers.stream().anyMatch(offer -> offer.changedFromPrevious && !offer.afterKismetReroll)) unverifiedChange = true;
			boolean hasOriginal = chest.offers.stream().anyMatch(offer -> !offer.afterKismetReroll);
			boolean hasReroll = chest.offers.stream().anyMatch(offer -> offer.afterKismetReroll);
			if (hasReroll && hasOriginal) rerollWithOriginal = true;
			if (hasReroll && !hasOriginal) rerollWithoutOriginal = true;
			observed.add(chestTier);
		}
		for (ChestTier t : expected) coverage.expectedChests.add(t.name());
		for (ChestTier t : observed) coverage.observedChests.add(t.name());

		if (!anyOfferSeen) {
			coverage.offerStatus = OfferStatus.NOT_OBSERVED.name();
			coverage.blockers.add("OFFERS_NOT_OBSERVED");
		} else if (!expected.isEmpty() && observed.containsAll(expected) && !rerollWithoutOriginal) {
			coverage.offerStatus = OfferStatus.COMPLETE.name();
		} else {
			coverage.offerStatus = OfferStatus.PARTIAL.name();
			coverage.blockers.add("OFFERS_PARTIAL");
		}

		if (unverifiedChange) {
			coverage.kismetStatus = KismetStatus.CONTENTS_CHANGED_UNVERIFIED.name();
			coverage.blockers.add("OFFER_CHANGED_UNVERIFIED");
		} else if (rerollWithoutOriginal) {
			coverage.kismetStatus = KismetStatus.REROLL_WITHOUT_ORIGINAL.name();
			coverage.blockers.add("KISMET_ORIGINAL_OFFER_MISSING");
		} else if (rerollWithOriginal) {
			coverage.kismetStatus = KismetStatus.REROLL_WITH_ORIGINAL.name();
		} else if (anyOfferSeen) {
			coverage.kismetStatus = KismetStatus.NO_REROLL.name();
		}
		if (anyOfferSeen) coverage.caveats.add("KISMET_PROVISIONAL");

		coverage.meterStatus = meterStatus(run, floor).name();
		if (!MeterStatus.OBSERVED.name().equals(coverage.meterStatus)) {
			coverage.caveats.add(MeterStatus.DERIVED.name().equals(coverage.meterStatus) ? "METER_DERIVED" : "METER_UNKNOWN");
		}

		coverage.qualifiesForScore = coverage.blockers.isEmpty();
		return coverage;
	}

	/** Chests a completion at this floor and score tier generates, per the default M7 rule. */
	public static Set<ChestTier> expectedChests(FloorId floor, ScoreTier tier) {
		Set<ChestTier> out = EnumSet.noneOf(ChestTier.class);
		for (ChestTier chest : ChestTier.values()) {
			if (chest == ChestTier.BEDROCK && !floor.hasBedrockChest()) continue;
			if (floor == FloorId.M7 || tier.ordinal() >= ChestEligibility.minimumScore(chest).ordinal()) out.add(chest);
		}
		return out;
	}

	public static ScoreTier scoreTier(LuckRunRecord run) {
		if (run.score != null) return ScoreTier.fromScore(run.score);
		if (run.grade == null) return null;
		return switch (run.grade.trim().toUpperCase(Locale.ROOT)) {
			case "S+" -> ScoreTier.S_PLUS;
			case "S" -> ScoreTier.S;
			case "A" -> ScoreTier.A;
			case "B", "C", "D" -> ScoreTier.BELOW_A;
			default -> null;
		};
	}

	private static MeterStatus meterStatus(LuckRunRecord run, FloorId floor) {
		if (floor == null) return MeterStatus.UNKNOWN;
		MeterStatus best = MeterStatus.UNKNOWN;
		for (LuckRunRecord.LuckMeterSnapshot snapshot : run.meterSnapshots) {
			if (!floor.name().equalsIgnoreCase(snapshot.floor) || !LuckRunRecord.PHASE_BEFORE_RUN.equals(snapshot.phase)) continue;
			if (MeterStatus.OBSERVED.name().equals(snapshot.state)) return MeterStatus.OBSERVED;
			if (MeterStatus.DERIVED.name().equals(snapshot.state)) best = MeterStatus.DERIVED;
		}
		return best;
	}

	private static ChestTier parseTier(String value) {
		if (value == null || value.isBlank()) return null;
		try {
			return ChestTier.valueOf(value.trim().toUpperCase(Locale.ROOT));
		} catch (IllegalArgumentException e) {
			return null;
		}
	}
}
