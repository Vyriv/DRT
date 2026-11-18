package dev.vy.drt.history;

import dev.vy.drt.config.DungeonChestOffer;
import dev.vy.drt.config.DungeonFloor;
import dev.vy.drt.config.DungeonRunCompletionRecord;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Read-only chest tier projection for the Runs UI. */
public final class HistoricalChestAvailability {
	public record Result(List<String> chestTypes, boolean inferred) {}

	private static final List<String> THROUGH_OBSIDIAN = List.of("Wood", "Gold", "Diamond", "Emerald", "Obsidian");
	private static final List<String> THROUGH_BEDROCK = List.of("Wood", "Gold", "Diamond", "Emerald", "Obsidian", "Bedrock");

	private HistoricalChestAvailability() {}

	public static Result resolve(DungeonRunCompletionRecord run) {
		if (run == null) return new Result(List.of(), false);
		Set<String> recorded = new LinkedHashSet<>();
		if (run.chestOffers != null) for (DungeonChestOffer offer : run.chestOffers) {
			if (offer == null || offer.chestTitle == null || offer.chestTitle.isBlank()) continue;
			recorded.add(compact(offer.chestTitle));
		}
		if (!recorded.isEmpty()) return new Result(List.copyOf(recorded), false);

		DungeonFloor floor = floor(run.floor);
		if (floor == null || !floor.isCatacombs()) return new Result(List.of(), false);
		String grade = run.grade == null ? "" : run.grade.trim().toUpperCase(Locale.ROOT);
		if (grade.equals("S")) return new Result(THROUGH_OBSIDIAN, true);
		if (!grade.equals("S+")) return new Result(List.of(), false);
		boolean supportsBedrock = floor.isMasterMode() || floor.floorNumber() >= 5;
		return new Result(supportsBedrock ? THROUGH_BEDROCK : THROUGH_OBSIDIAN, true);
	}

	private static DungeonFloor floor(String value) {
		try { return DungeonFloor.valueOf(value == null ? "" : value.trim().toUpperCase(Locale.ROOT)); }
		catch (IllegalArgumentException ignored) { return null; }
	}

	private static String compact(String value) {
		String cleaned = value.replaceAll("(?i)\\s+chest.*$", "").trim();
		return cleaned.isBlank() ? value.trim() : cleaned;
	}
}
