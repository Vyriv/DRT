package dev.vy.drt.luck.data;

import dev.vy.drt.luck.ChestTier;
import dev.vy.drt.luck.FloorId;
import java.util.List;

/** A documented disagreement between sources, with selectable resolutions. */
public record DataDispute(
	String id,
	List<FloorId> floors,
	Status status,
	String summary,
	List<Evidence> evidence,
	String defaultOption,
	List<Option> options
) {
	public enum Status {
		/** Evidence favours one option; the default follows it. */
		EVIDENCE_BACKED,
		/** Sources disagree with no decisive evidence; the default keeps the base data. */
		DISPUTED
	}

	public record Evidence(String source, String url, String authority, String finding) {
	}

	public record Option(String id, String label, List<Patch> patches) {
		public Option {
			patches = List.copyOf(patches);
		}
	}

	public sealed interface Patch {
		FloorId floor();
	}

	public record SetWeight(FloorId floor, ChestTier chest, String entry, int value) implements Patch {
	}

	public record SetBaseCost(FloorId floor, ChestTier chest, long value) implements Patch {
	}

	public record RemoveEntry(FloorId floor, ChestTier chest, String entry) implements Patch {
	}

	public record MeterTotalWeightDelta(FloorId floor, int value) implements Patch {
	}

	public DataDispute {
		floors = List.copyOf(floors);
		evidence = List.copyOf(evidence);
		options = List.copyOf(options);
	}

	public Option option(String optionId) {
		for (Option option : options) {
			if (option.id().equals(optionId)) return option;
		}
		return null;
	}
}
