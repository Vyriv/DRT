package dev.vy.drt.luck.capture;

import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Numeric score and grade read together from one end-of-run summary line, such as
 * "Team Score: 305 (S+)" or "Score: 306 (S+)". A line that shows only a grade yields nothing:
 * a numeric score is never derived from a letter.
 */
public record DungeonScoreReading(int score, String grade) {
	public static final int MAX_PLAUSIBLE_SCORE = 400;

	private static final Pattern NUMERIC_SCORE = Pattern.compile(
		"(?:^|[^A-Z0-9])(?:TEAM\\s+)?SCORE:\\s*([0-9]{1,3})\\s*\\((S\\+|S|A|B|C|D)\\)",
		Pattern.CASE_INSENSITIVE
	);

	public static Optional<DungeonScoreReading> parse(String line) {
		if (line == null || line.isBlank()) return Optional.empty();
		Matcher matcher = NUMERIC_SCORE.matcher(line);
		if (!matcher.find()) return Optional.empty();
		int score;
		try {
			score = Integer.parseInt(matcher.group(1));
		} catch (NumberFormatException e) {
			return Optional.empty();
		}
		if (score < 0 || score > MAX_PLAUSIBLE_SCORE) return Optional.empty();
		return Optional.of(new DungeonScoreReading(score, matcher.group(2).toUpperCase(Locale.ROOT)));
	}
}
