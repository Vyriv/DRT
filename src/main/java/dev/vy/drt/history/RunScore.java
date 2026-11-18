package dev.vy.drt.history;

import dev.vy.drt.config.DungeonRunCompletionRecord;
import java.util.Locale;

/** Derived score provenance. It never mutates the persisted completion score. */
public final class RunScore {
	public enum Source { ACTUAL, INFERRED, UNKNOWN }
	public record Resolved(Integer value, Source source) {
		public boolean actual() { return source == Source.ACTUAL; }
		public boolean inferred() { return source == Source.INFERRED; }
		public boolean known() { return source != Source.UNKNOWN && value != null; }
		public String display() { return known() ? value + (inferred() ? "*" : "") : "Unknown"; }
	}

	private RunScore() {}

	public static Resolved resolve(DungeonRunCompletionRecord completion) {
		if (completion == null) return new Resolved(null, Source.UNKNOWN);
		if (completion.hasRecordedScore()) return new Resolved(Math.max(0, completion.score), Source.ACTUAL);
		Integer minimum = minimumForGrade(completion.grade);
		return minimum == null ? new Resolved(null, Source.UNKNOWN) : new Resolved(minimum, Source.INFERRED);
	}

	public static Integer minimumForGrade(String grade) {
		String normalized = grade == null ? "" : grade.trim().toUpperCase(Locale.ROOT);
		return switch (normalized) {
			case "S+" -> 300;
			case "S" -> 270;
			case "A" -> 230;
			case "B" -> 160;
			case "C" -> 100;
			case "D" -> 0;
			default -> null;
		};
	}
}
