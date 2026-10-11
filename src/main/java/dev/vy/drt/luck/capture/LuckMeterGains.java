package dev.vy.drt.luck.capture;

import dev.vy.drt.luck.FloorId;
import dev.vy.drt.luck.ScoreTier;
import dev.vy.drt.luck.meter.MeterGainObservation;
import java.util.ArrayList;
import java.util.List;

/** Meter gains read around captured runs, for checking the S-run gain rule against live data. */
public final class LuckMeterGains {
	private LuckMeterGains() {
	}

	/**
	 * The run's gain when both snapshots were read in game with no drop selected, so nothing could
	 * have reset the meter; null otherwise.
	 */
	public static MeterGainObservation of(LuckRunRecord run) {
		if (run == null || run.score == null || run.modifiers == null || run.modifiers.pityLevel == null) return null;
		ScoreTier tier = ScoreTier.fromScore(run.score);
		if (tier != ScoreTier.S && tier != ScoreTier.S_PLUS) return null;
		FloorId floor;
		try {
			floor = FloorId.valueOf(run.floor);
		} catch (IllegalArgumentException | NullPointerException e) {
			return null;
		}
		LuckRunRecord.LuckMeterSnapshot before = observed(run, LuckRunRecord.PHASE_BEFORE_RUN);
		LuckRunRecord.LuckMeterSnapshot after = observed(run, LuckRunRecord.PHASE_AFTER_RUN);
		if (before == null || after == null || after.storedScore < before.storedScore) return null;
		return new MeterGainObservation(run.luckRunId, floor, run.score, run.modifiers.pityLevel, before.storedScore, after.storedScore);
	}

	public static List<MeterGainObservation> all(List<LuckRunRecord> runs) {
		List<MeterGainObservation> out = new ArrayList<>();
		for (LuckRunRecord run : runs) {
			MeterGainObservation observation = of(run);
			if (observation != null) out.add(observation);
		}
		return out;
	}

	private static LuckRunRecord.LuckMeterSnapshot observed(LuckRunRecord run, String phase) {
		for (LuckRunRecord.LuckMeterSnapshot snapshot : run.meterSnapshots) {
			if (phase.equals(snapshot.phase)
				&& LuckCoverage.MeterStatus.OBSERVED.name().equals(snapshot.state)
				&& snapshot.storedScore != null
				&& snapshot.selectedItem == null
				&& run.floor.equals(snapshot.floor)) {
				return snapshot;
			}
		}
		return null;
	}
}
