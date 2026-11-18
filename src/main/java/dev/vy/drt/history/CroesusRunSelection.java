package dev.vy.drt.history;

import dev.vy.drt.config.DungeonRunCompletionRecord;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Month;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Identifies a clicked Croesus run without falling back to the newest completion. */
public final class CroesusRunSelection {
	private static final Pattern EXPLICIT_FLOOR = Pattern.compile("(?i)(?:^|[^A-Z0-9])([FM])\\s*([1-7])(?:$|[^A-Z0-9])");
	private static final Pattern NAMED_FLOOR = Pattern.compile("(?i)FLOOR\\s+(VII|VI|IV|V|III|II|I|[1-7])\\b");
	private static final Pattern KUUDRA_TIER_NAME = Pattern.compile("(?i)\\b(BASIC|HOT|BURNING|FIERY|INFERNAL)\\s+TIER\\b");
	private static final Pattern KUUDRA_TIER_NUMBER = Pattern.compile("(?i)KUUDRA[^|]*?\\b(?:TIER|T)\\s*([1-5])\\b");
	private static final Pattern GRADE = Pattern.compile("(?i)(?:GRADE|RANK)\\s*:?\\s*(S\\+|S|A|B|C|D)\\b");
	private static final Pattern SCORE = Pattern.compile("(?i)SCORE\\s*:?\\s*(\\d{1,3})(?:\\s*\\((S\\+|S|A|B|C|D)\\))?");
	private static final Pattern DURATION = Pattern.compile("(?i)(?:TIME|DURATION)\\s*:?\\s*(\\d{1,3})M\\s*(\\d{1,2})S");
	private static final Pattern RELATIVE = Pattern.compile("(?i)(\\d+|A|AN|ONE)\\s*(SECONDS?|MINUTES?|HOURS?|DAYS?)\\s+AGO");
	private static final Pattern COMPACT_RELATIVE = Pattern.compile("(?i)(\\d+)\\s*([SMHD])\\s+AGO");
	private static final Pattern NUMERIC_DATE = Pattern.compile("(?i)(\\d{1,2})/(\\d{1,2})/(\\d{2,4})(?:\\s+(\\d{1,2}):(\\d{2})(?:\\s*(AM|PM))?)?");
	private static final Pattern TEXT_DATE = Pattern.compile("(?i)(JAN(?:UARY)?|FEB(?:RUARY)?|MAR(?:CH)?|APR(?:IL)?|MAY|JUN(?:E)?|JUL(?:Y)?|AUG(?:UST)?|SEP(?:TEMBER)?|OCT(?:OBER)?|NOV(?:EMBER)?|DEC(?:EMBER)?)\\s+(\\d{1,2})(?:ST|ND|RD|TH)?,?\\s+(\\d{4})(?:\\s+(\\d{1,2}):(\\d{2})(?:\\s*(AM|PM))?)?");

	public record TimeWindow(long earliestMillis, long latestMillis) {
		public boolean contains(long timestamp) {
			return timestamp >= earliestMillis && timestamp <= latestMillis;
		}
	}

	public record Evidence(String floor, String grade, int score, long durationMillis, List<TimeWindow> completionWindows) {
		public Evidence {
			floor = floor == null ? "" : floor;
			grade = grade == null ? "" : grade;
			completionWindows = completionWindows == null ? List.of() : List.copyOf(completionWindows);
		}

		public boolean hasStableIdentity() {
			return !completionWindows.isEmpty() || durationMillis > 0L;
		}
	}

	public enum Status { MATCHED, NO_MATCH, AMBIGUOUS, INSUFFICIENT_EVIDENCE }

	public record Match(Status status, DungeonRunCompletionRecord completion, int candidateCount) {
		public String runSessionId() {
			return completion == null || completion.runSessionId == null ? "" : completion.runSessionId;
		}
	}

	private CroesusRunSelection() {
	}

	public static Evidence parse(String displayName, List<String> lore, long observedAtMillis, ZoneId zone) {
		List<String> lines = new ArrayList<>();
		if (displayName != null && !displayName.isBlank()) lines.add(displayName);
		if (lore != null) lines.addAll(lore);
		String joined = String.join(" | ", lines).toUpperCase(Locale.ROOT);
		String floor = parseFloor(joined);
		String grade = matchGroup(GRADE, joined, 1).toUpperCase(Locale.ROOT);
		Matcher scoreMatcher = SCORE.matcher(joined);
		int score = 0;
		if (scoreMatcher.find()) {
			score = Integer.parseInt(scoreMatcher.group(1));
			if (grade.isBlank() && scoreMatcher.group(2) != null) grade = scoreMatcher.group(2).toUpperCase(Locale.ROOT);
		}
		long duration = parseDuration(joined);
		List<TimeWindow> windows = new ArrayList<>();
		for (String line : lines) {
			String upper = line == null ? "" : line.toUpperCase(Locale.ROOT);
			TimeWindow relative = relativeWindow(upper, observedAtMillis);
			if (relative != null) windows.add(relative);
			windows.addAll(absoluteWindows(upper, zone == null ? ZoneId.systemDefault() : zone));
		}
		return new Evidence(floor, grade, score, duration, windows);
	}

	public static Match resolve(Evidence evidence, List<DungeonRunCompletionRecord> completions) {
		if (evidence == null || evidence.floor().isBlank() || !evidence.hasStableIdentity()) {
			return new Match(Status.INSUFFICIENT_EVIDENCE, null, 0);
		}
		// Kuudra has no score or grade and DRT measures its duration locally, so only the completion time identifies it.
		boolean kuudra = evidence.floor().toUpperCase(Locale.ROOT).startsWith("K");
		if (kuudra && evidence.completionWindows().isEmpty()) return new Match(Status.INSUFFICIENT_EVIDENCE, null, 0);
		List<DungeonRunCompletionRecord> candidates = new ArrayList<>();
		for (DungeonRunCompletionRecord completion : completions == null ? List.<DungeonRunCompletionRecord>of() : completions) {
			if (completion == null || completion.runSessionId == null || completion.runSessionId.isBlank()) continue;
			if (!evidence.floor().equalsIgnoreCase(completion.floor)) continue;
			if (!kuudra && !evidence.grade().isBlank() && completion.grade != null && !completion.grade.isBlank() && !completion.grade.equals("?")
				&& !evidence.grade().equalsIgnoreCase(completion.grade)) continue;
			if (!kuudra && evidence.score() > 0 && completion.score > 0 && evidence.score() != completion.score) continue;
			if (!kuudra && evidence.durationMillis() > 0L && completion.runTimeMs > 0L && evidence.durationMillis() != completion.runTimeMs) continue;
			if (!kuudra && evidence.completionWindows().isEmpty() && evidence.durationMillis() > 0L && completion.runTimeMs <= 0L) continue;
			if (!evidence.completionWindows().isEmpty() && evidence.completionWindows().stream().noneMatch(w -> w.contains(completion.completedAtEpochMillis))) continue;
			candidates.add(completion);
		}
		if (candidates.size() == 1) return new Match(Status.MATCHED, candidates.get(0), 1);
		if (candidates.isEmpty()) return new Match(Status.NO_MATCH, null, 0);
		return new Match(Status.AMBIGUOUS, null, candidates.size());
	}

	private static String parseFloor(String joined) {
		Matcher kuudraName = KUUDRA_TIER_NAME.matcher(joined);
		if (kuudraName.find()) return "K" + kuudraTier(kuudraName.group(1));
		Matcher kuudraNumber = KUUDRA_TIER_NUMBER.matcher(joined);
		if (kuudraNumber.find()) return "K" + kuudraNumber.group(1);
		Matcher explicit = EXPLICIT_FLOOR.matcher(joined);
		if (explicit.find()) return explicit.group(1).toUpperCase(Locale.ROOT) + explicit.group(2);
		Matcher named = NAMED_FLOOR.matcher(joined);
		if (!named.find()) return "";
		int number = roman(named.group(1));
		if (number < 1 || number > 7) return "";
		return (joined.contains("MASTER MODE") || joined.contains("MASTER CATACOMBS") ? "M" : "F") + number;
	}

	private static int kuudraTier(String name) {
		return switch (name.toUpperCase(Locale.ROOT)) {
			case "BASIC" -> 1;
			case "HOT" -> 2;
			case "BURNING" -> 3;
			case "FIERY" -> 4;
			default -> 5;
		};
	}

	private static long parseDuration(String joined) {
		Matcher matcher = DURATION.matcher(joined);
		if (!matcher.find()) return 0L;
		int minutes = Integer.parseInt(matcher.group(1));
		int seconds = Integer.parseInt(matcher.group(2));
		return seconds < 60 ? (minutes * 60L + seconds) * 1000L : 0L;
	}

	private static TimeWindow relativeWindow(String line, long now) {
		if (line.contains("JUST NOW") || line.contains("MOMENTS AGO") || line.contains("LESS THAN A MINUTE AGO")) {
			return new TimeWindow(now - 60_000L, now + 2_000L);
		}
		Matcher matcher = RELATIVE.matcher(line);
		long amount;
		long unit;
		if (matcher.find()) {
			amount = wordNumber(matcher.group(1));
			String name = matcher.group(2).toUpperCase(Locale.ROOT);
			unit = name.startsWith("SECOND") ? 1_000L : name.startsWith("MINUTE") ? 60_000L : name.startsWith("HOUR") ? 3_600_000L : 86_400_000L;
		} else {
			Matcher compact = COMPACT_RELATIVE.matcher(line);
			if (!compact.find()) return null;
			amount = Long.parseLong(compact.group(1));
			unit = switch (compact.group(2).toUpperCase(Locale.ROOT)) {
				case "S" -> 1_000L;
				case "M" -> 60_000L;
				case "H" -> 3_600_000L;
				default -> 86_400_000L;
			};
		}
		if (amount < 0L || amount >= Long.MAX_VALUE / unit) return null;
		// Hypixel rounds relative times, so keep one full unit of slack on each side of the bucket.
		long earliest = now - (amount + 2L) * unit - 2_000L;
		long latest = now - Math.max(0L, amount - 1L) * unit + 2_000L;
		return new TimeWindow(earliest, latest);
	}

	private static List<TimeWindow> absoluteWindows(String line, ZoneId zone) {
		List<TimeWindow> windows = new ArrayList<>();
		Matcher text = TEXT_DATE.matcher(line);
		if (text.find()) {
			Month month = Month.valueOf(monthName(text.group(1)));
			addWindow(windows, Integer.parseInt(text.group(3)), month.getValue(), Integer.parseInt(text.group(2)), text.group(4), text.group(5), text.group(6), zone);
			return windows;
		}
		Matcher numeric = NUMERIC_DATE.matcher(line);
		if (!numeric.find()) return windows;
		int first = Integer.parseInt(numeric.group(1));
		int second = Integer.parseInt(numeric.group(2));
		int year = normalizeYear(Integer.parseInt(numeric.group(3)));
		if (first > 12) {
			addWindow(windows, year, second, first, numeric.group(4), numeric.group(5), numeric.group(6), zone);
		} else if (second > 12) {
			addWindow(windows, year, first, second, numeric.group(4), numeric.group(5), numeric.group(6), zone);
		} else {
			addWindow(windows, year, second, first, numeric.group(4), numeric.group(5), numeric.group(6), zone);
			addWindow(windows, year, first, second, numeric.group(4), numeric.group(5), numeric.group(6), zone);
		}
		return windows;
	}

	private static void addWindow(List<TimeWindow> windows, int year, int month, int day, String hourText, String minuteText, String amPm, ZoneId zone) {
		try {
			if (hourText == null) {
				ZonedDateTime start = LocalDate.of(year, month, day).atStartOfDay(zone);
				windows.add(new TimeWindow(start.toInstant().toEpochMilli(), start.plusDays(1).toInstant().toEpochMilli() - 1L));
				return;
			}
			int hour = Integer.parseInt(hourText);
			int minute = Integer.parseInt(minuteText);
			if (amPm != null) {
				hour %= 12;
				if (amPm.equalsIgnoreCase("PM")) hour += 12;
			}
			ZonedDateTime start = LocalDateTime.of(year, month, day, hour, minute).atZone(zone);
			windows.add(new TimeWindow(start.minusSeconds(2).toInstant().toEpochMilli(), start.plusMinutes(1).plusSeconds(2).toInstant().toEpochMilli()));
		} catch (DateTimeException | NumberFormatException ignored) {
			// Invalid lore dates provide no association evidence.
		}
	}

	private static String monthName(String value) {
		String upper = value.toUpperCase(Locale.ROOT);
		return switch (upper.substring(0, 3)) {
			case "JAN" -> "JANUARY";
			case "FEB" -> "FEBRUARY";
			case "MAR" -> "MARCH";
			case "APR" -> "APRIL";
			case "MAY" -> "MAY";
			case "JUN" -> "JUNE";
			case "JUL" -> "JULY";
			case "AUG" -> "AUGUST";
			case "SEP" -> "SEPTEMBER";
			case "OCT" -> "OCTOBER";
			case "NOV" -> "NOVEMBER";
			default -> "DECEMBER";
		};
	}

	private static int normalizeYear(int year) {
		return year < 100 ? 2000 + year : year;
	}

	private static long wordNumber(String value) {
		if (value.equalsIgnoreCase("A") || value.equalsIgnoreCase("AN") || value.equalsIgnoreCase("ONE")) return 1L;
		return Long.parseLong(value);
	}

	private static String matchGroup(Pattern pattern, String value, int group) {
		Matcher matcher = pattern.matcher(value);
		return matcher.find() ? matcher.group(group) : "";
	}

	private static int roman(String value) {
		try {
			return Integer.parseInt(value);
		} catch (NumberFormatException ignored) {
			return switch (value.toUpperCase(Locale.ROOT)) {
				case "I" -> 1;
				case "II" -> 2;
				case "III" -> 3;
				case "IV" -> 4;
				case "V" -> 5;
				case "VI" -> 6;
				case "VII" -> 7;
				default -> 0;
			};
		}
	}
}
