package dev.vy.drt.client.overlay;

import dev.vy.drt.client.cosmetics.CosmeticsContentManager;
import dev.vy.drt.client.cosmetics.GradientColors;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses custom overlay templates into HUD lines.
 * Supports {@code {placeholders}}, stacked {@code <#RRGGBB>} / {@code </#>} colour tags,
 * and {@code <gradient:#RRGGBB:#RRGGBB>} / {@code </gradient>} two-colour gradients
 * using {@link CosmeticsContentManager#parseColor(String)} and {@link GradientColors}.
 *
 * <p>Limitations: solid colour tags and nested gradients inside an active gradient region are
 * not applied as formatting (the markup is treated as literal visible text, or ignored for
 * well-formed nested gradient open tags that would be unstable). Formatting never leaks across lines.
 */
public final class CustomOverlayParser {
	private static final Set<String> KNOWN = Set.of(
		"floor", "title",
		"runs.total", "runs.session", "runs.avg", "runs.hour", "runs.s", "runs.splus",
		"profit.total", "profit.session", "profit.run", "profit.hour",
		"time.session", "time.total"
	);

	private static final Pattern GRADIENT_OPEN = Pattern.compile(
		"<gradient\\s*:\\s*#?([0-9A-Fa-f]{6})\\s*:\\s*#?([0-9A-Fa-f]{6})\\s*>",
		Pattern.CASE_INSENSITIVE
	);
	private static final String GRADIENT_CLOSE = "</gradient>";
	private static final Pattern BAD_GRADIENT_OPEN = Pattern.compile("<gradient[^>]*>", Pattern.CASE_INSENSITIVE);

	private CustomOverlayParser() {
	}

	public static OverlayLayout parse(
		String template,
		OverlayStats stats,
		OverlayLine.FontMeasurer font,
		int fontLineHeight
	) {
		try {
			return parseUnsafe(template, stats, font, fontLineHeight);
		} catch (RuntimeException ex) {
			OverlayLayout.Builder fallback = new OverlayLayout.Builder(font, fontLineHeight);
			fallback.add(new OverlayLine.Builder().add("(layout parse error)", OverlayColors.WARNING));
			return fallback.build();
		}
	}

	private static OverlayLayout parseUnsafe(
		String template,
		OverlayStats stats,
		OverlayLine.FontMeasurer font,
		int fontLineHeight
	) {
		String safe = template == null ? "" : template.replace("\r\n", "\n").replace('\r', '\n');
		if (safe.isBlank()) {
			OverlayLayout.Builder empty = new OverlayLayout.Builder(font, fontLineHeight);
			empty.add(new OverlayLine.Builder().add("(empty custom layout)", OverlayColors.DIM));
			return empty.build();
		}

		OverlayLayout.Builder builder = new OverlayLayout.Builder(font, fontLineHeight);
		for (String rawLine : safe.split("\n", -1)) {
			builder.add(parseLine(rawLine, stats));
		}
		return builder.build();
	}

	public static boolean isKnownPlaceholder(String key) {
		return key != null && KNOWN.contains(key.toLowerCase(Locale.ROOT));
	}

	/** Lightweight markup warnings for the custom layout editor. Never throws. */
	public static List<String> validateMarkup(String template) {
		List<String> warnings = new ArrayList<>();
		if (template == null || template.isBlank()) return warnings;
		String safe = template.replace("\r\n", "\n").replace('\r', '\n');
		for (String rawLine : safe.split("\n", -1)) {
			validateLineMarkup(rawLine, warnings);
		}
		return warnings;
	}

	private static void validateLineMarkup(String text, List<String> warnings) {
		if (text == null || text.isEmpty()) return;
		int i = 0;
		int solidDepth = 0;
		int gradientDepth = 0;
		while (i < text.length()) {
			if (text.startsWith(GRADIENT_CLOSE, i)) {
				if (gradientDepth <= 0) addWarning(warnings, "Unexpected </gradient>");
				else gradientDepth--;
				i += GRADIENT_CLOSE.length();
				continue;
			}
			if (text.regionMatches(true, i, "<gradient", 0, 9)) {
				Matcher m = GRADIENT_OPEN.matcher(text);
				m.region(i, text.length());
				if (m.lookingAt()) {
					Integer left = CosmeticsContentManager.parseColor(m.group(1));
					Integer right = CosmeticsContentManager.parseColor(m.group(2));
					if (left == null || right == null) addWarning(warnings, "Invalid gradient colours");
					if (gradientDepth > 0) addWarning(warnings, "Nested gradients are not supported");
					else gradientDepth++;
					i = m.end();
					continue;
				}
				Matcher bad = BAD_GRADIENT_OPEN.matcher(text);
				bad.region(i, text.length());
				if (bad.lookingAt()) {
					addWarning(warnings, "Malformed gradient tag: " + bad.group());
					i = bad.end();
					continue;
				}
			}
			if (text.startsWith("</#>", i)) {
				if (solidDepth <= 0) addWarning(warnings, "Unexpected </#>");
				else solidDepth--;
				i += 4;
				continue;
			}
			if (i + 1 < text.length() && text.charAt(i) == '<' && text.charAt(i + 1) == '#') {
				TagParse open = tryParseOpenColour(text, i);
				if (open.valid) {
					solidDepth++;
					i = open.end;
					continue;
				}
				int gt = text.indexOf('>', i + 1);
				if (gt > i) {
					addWarning(warnings, "Malformed colour tag: " + text.substring(i, gt + 1));
					i = gt + 1;
					continue;
				}
			}
			i += 1;
		}
		if (gradientDepth > 0) addWarning(warnings, "Unclosed </gradient>");
		if (solidDepth > 0) addWarning(warnings, "Unclosed colour tag </#>");
	}

	private static void addWarning(List<String> warnings, String message) {
		if (!warnings.contains(message)) warnings.add(message);
	}

	private static OverlayLine.Builder parseLine(String rawLine, OverlayStats stats) {
		OverlayLine.Builder line = new OverlayLine.Builder();
		String text = rawLine == null ? "" : rawLine;
		if (text.isEmpty()) {
			return line.add(" ", OverlayColors.DIM);
		}

		Deque<Integer> colourStack = new ArrayDeque<>();
		List<GradPiece> gradientPieces = null;
		int gradLeft = 0;
		int gradRight = 0;
		int i = 0;
		while (i < text.length()) {
			char c = text.charAt(i);

			if (c == '<' && text.startsWith(GRADIENT_CLOSE, i)) {
				if (gradientPieces != null) {
					flushGradient(line, gradLeft, gradRight, gradientPieces);
					gradientPieces = null;
				}
				i += GRADIENT_CLOSE.length();
				continue;
			}

			if (c == '<' && text.regionMatches(true, i, "<gradient", 0, 9)) {
				Matcher m = GRADIENT_OPEN.matcher(text);
				m.region(i, text.length());
				if (m.lookingAt()) {
					Integer left = CosmeticsContentManager.parseColor(m.group(1));
					Integer right = CosmeticsContentManager.parseColor(m.group(2));
					if (left != null && right != null && gradientPieces == null) {
						gradientPieces = new ArrayList<>();
						gradLeft = left;
						gradRight = right;
						i = m.end();
						continue;
					}
					// Nested / invalid: emit as literal (or ignore nested open by emitting raw)
					appendContent(line, colourStack, gradientPieces, text.substring(i, m.end()), OverlaySegmentRole.TEXT);
					i = m.end();
					continue;
				}
			}

			if (c == '<' && text.startsWith("</#>", i)) {
				if (gradientPieces != null) {
					appendContent(line, colourStack, gradientPieces, "</#>", OverlaySegmentRole.TEXT);
				} else if (!colourStack.isEmpty()) {
					colourStack.pop();
				}
				i += 4;
				continue;
			}

			if (c == '<' && i + 1 < text.length() && text.charAt(i + 1) == '#') {
				TagParse open = tryParseOpenColour(text, i);
				if (open.valid) {
					if (gradientPieces != null) {
						appendContent(line, colourStack, gradientPieces, text.substring(i, open.end), OverlaySegmentRole.TEXT);
					} else {
						colourStack.push(open.argb);
					}
					i = open.end;
					continue;
				}
				appendContent(line, colourStack, gradientPieces, "<", OverlaySegmentRole.TEXT);
				i += 1;
				continue;
			}

			if (c == '{') {
				int close = text.indexOf('}', i + 1);
				if (close > i + 1) {
					String key = text.substring(i + 1, close).toLowerCase(Locale.ROOT);
					if (isValidKeyChars(key)) {
						Resolved resolved = resolve(key, stats);
						line.placeholder(key);
						if (resolved.known) {
							emitPlaceholder(line, colourStack, gradientPieces, resolved);
						} else {
							appendContent(line, colourStack, gradientPieces, text.substring(i, close + 1), OverlaySegmentRole.TEXT);
						}
						i = close + 1;
						continue;
					}
				}
			}

			// Unrecognized '<' / '{' must advance — searching from i would loop forever.
			int next = nextSpecialIndex(text, i + 1);
			appendContent(line, colourStack, gradientPieces, text.substring(i, next), OverlaySegmentRole.TEXT);
			i = next;
		}

		if (gradientPieces != null) {
			flushGradient(line, gradLeft, gradRight, gradientPieces);
		}
		return line;
	}

	private static void emitPlaceholder(
		OverlayLine.Builder line,
		Deque<Integer> colourStack,
		List<GradPiece> gradientPieces,
		Resolved resolved
	) {
		switch (resolved.kind) {
			case FLOOR -> line.markFloor();
			case RUNS -> line.markRuns();
			case PROFIT -> line.markProfit();
			case TITLE, NONE -> { }
		}

		if (resolved.protectedColour) {
			if (gradientPieces != null) {
				gradientPieces.add(new GradPiece.Protected(resolved.text, resolved.color, resolved.role));
			} else {
				line.add(resolved.text, resolved.color, resolved.role);
			}
			return;
		}

		if (gradientPieces != null) {
			gradientPieces.add(new GradPiece.Text(resolved.text, resolved.role));
			return;
		}

		int colour = colourStack.isEmpty() ? resolved.color : currentColour(colourStack);
		line.add(resolved.text, colour, resolved.role);
	}

	private static void appendContent(
		OverlayLine.Builder line,
		Deque<Integer> colourStack,
		List<GradPiece> gradientPieces,
		String text,
		OverlaySegmentRole role
	) {
		if (text == null || text.isEmpty()) return;
		if (gradientPieces != null) {
			if (role == OverlaySegmentRole.MODE) {
				gradientPieces.add(new GradPiece.Text(text, OverlaySegmentRole.MODE));
				return;
			}
			int idx = 0;
			while (idx < text.length()) {
				int drt = indexOfIgnoreCase(text, "DRT", idx);
				if (drt < 0) {
					gradientPieces.add(new GradPiece.Text(text.substring(idx), OverlaySegmentRole.TEXT));
					return;
				}
				if (drt > idx) gradientPieces.add(new GradPiece.Text(text.substring(idx, drt), OverlaySegmentRole.TEXT));
				gradientPieces.add(new GradPiece.Text(text.substring(drt, drt + 3), OverlaySegmentRole.MODE));
				idx = drt + 3;
			}
			return;
		}
		appendLiteral(line, text, currentColour(colourStack));
	}

	private static void flushGradient(OverlayLine.Builder line, int leftRgb, int rightRgb, List<GradPiece> pieces) {
		if (pieces == null || pieces.isEmpty()) return;
		int eligible = 0;
		for (GradPiece piece : pieces) {
			if (piece instanceof GradPiece.Text text) {
				eligible += text.value.codePointCount(0, text.value.length());
			}
		}
		int index = 0;
		for (GradPiece piece : pieces) {
			if (piece instanceof GradPiece.Protected protectedPiece) {
				line.add(protectedPiece.value, protectedPiece.color, protectedPiece.role);
				continue;
			}
			if (piece instanceof GradPiece.Text text) {
				int[] codePoints = text.value.codePoints().toArray();
				for (int codePoint : codePoints) {
					int colour = GradientColors.linearArgb(leftRgb, rightRgb, index, eligible);
					line.add(new String(Character.toChars(codePoint)), colour, text.role);
					index++;
				}
			}
		}
	}

	private static int nextSpecialIndex(String text, int from) {
		for (int i = from; i < text.length(); i++) {
			char c = text.charAt(i);
			if (c == '<' || c == '{') return i;
		}
		return text.length();
	}

	private static boolean isValidKeyChars(String key) {
		if (key.isEmpty()) return false;
		for (int i = 0; i < key.length(); i++) {
			char c = key.charAt(i);
			if (!(c >= 'a' && c <= 'z') && !(c >= '0' && c <= '9') && c != '_' && c != '.') return false;
		}
		return true;
	}

	private static int currentColour(Deque<Integer> stack) {
		return stack.isEmpty() ? OverlayColors.VALUE : stack.peek();
	}

	private static TagParse tryParseOpenColour(String text, int start) {
		if (start + 9 > text.length()) return TagParse.invalid();
		if (text.charAt(start) != '<' || text.charAt(start + 1) != '#') return TagParse.invalid();
		if (text.charAt(start + 8) != '>') return TagParse.invalid();
		String hex = text.substring(start + 2, start + 8);
		Integer rgb = CosmeticsContentManager.parseColor(hex);
		if (rgb == null) return TagParse.invalid();
		return new TagParse(true, rgb | 0xFF000000, start + 9);
	}

	private static void appendLiteral(OverlayLine.Builder line, String text, int colour) {
		if (text == null || text.isEmpty()) return;
		int idx = 0;
		while (idx < text.length()) {
			int drt = indexOfIgnoreCase(text, "DRT", idx);
			if (drt < 0) {
				line.add(text.substring(idx), colour);
				return;
			}
			if (drt > idx) line.add(text.substring(idx, drt), colour);
			line.add(text.substring(drt, drt + 3), colour, OverlaySegmentRole.MODE);
			idx = drt + 3;
		}
	}

	private static int indexOfIgnoreCase(String haystack, String needle, int from) {
		return haystack.toLowerCase(Locale.ROOT).indexOf(needle.toLowerCase(Locale.ROOT), from);
	}

	private static Resolved resolve(String key, OverlayStats stats) {
		return switch (key) {
			case "floor" -> new Resolved(true, true, Kind.FLOOR, stats.floorTag, OverlayColors.floorTagColor(stats.floorTag), OverlaySegmentRole.FLOOR);
			case "title" -> new Resolved(true, false, Kind.TITLE, "DRT", OverlayColors.TITLE, OverlaySegmentRole.MODE);
			case "runs.total" -> new Resolved(true, false, Kind.RUNS, String.valueOf(stats.totalRuns), OverlayColors.VALUE, OverlaySegmentRole.TEXT);
			case "runs.session" -> new Resolved(true, false, Kind.RUNS, String.valueOf(stats.sessionRuns), OverlayColors.VALUE, OverlaySegmentRole.TEXT);
			case "runs.avg" -> new Resolved(true, false, Kind.RUNS, OverlayFormat.duration(stats.avgRunTimeMs), OverlayColors.VALUE, OverlaySegmentRole.TEXT);
			case "runs.hour" -> new Resolved(true, false, Kind.RUNS, OverlayFormat.rate(stats.runsPerHour), OverlayColors.RATE, OverlaySegmentRole.RUNS_HR);
			case "runs.s" -> new Resolved(true, false, Kind.RUNS, String.valueOf(stats.sCount), OverlayColors.VALUE, OverlaySegmentRole.TEXT);
			case "runs.splus" -> new Resolved(true, false, Kind.RUNS, String.valueOf(stats.sPlusCount), OverlayColors.VALUE, OverlaySegmentRole.TEXT);
			case "profit.total" -> new Resolved(true, true, Kind.PROFIT, OverlayFormat.coins(stats.totalProfit), OverlayColors.profitColor(stats.totalProfit), OverlaySegmentRole.TEXT);
			case "profit.session" -> new Resolved(true, true, Kind.PROFIT, OverlayFormat.signedCoins(stats.sessionProfit), OverlayColors.profitColor(stats.sessionProfit), OverlaySegmentRole.TEXT);
			case "profit.run" -> new Resolved(true, true, Kind.PROFIT, OverlayFormat.signedCoins(stats.profitPerRun), OverlayColors.profitColor(stats.profitPerRun), OverlaySegmentRole.TEXT);
			case "profit.hour" -> new Resolved(true, true, Kind.PROFIT, OverlayFormat.signedCoins(stats.profitPerHour), OverlayColors.profitColor(stats.profitPerHour), OverlaySegmentRole.TEXT);
			case "time.session" -> new Resolved(true, false, Kind.RUNS, OverlayFormat.duration(stats.sessionRunTimeMs), OverlayColors.VALUE, OverlaySegmentRole.TEXT);
			case "time.total" -> new Resolved(true, false, Kind.RUNS, OverlayFormat.duration(stats.totalRunTimeMs), OverlayColors.VALUE, OverlaySegmentRole.TEXT);
			default -> new Resolved(false, false, Kind.NONE, "", OverlayColors.WARNING, OverlaySegmentRole.TEXT);
		};
	}

	private enum Kind { NONE, FLOOR, RUNS, PROFIT, TITLE }

	private record Resolved(boolean known, boolean protectedColour, Kind kind, String text, int color, OverlaySegmentRole role) {}

	private record TagParse(boolean valid, int argb, int end) {
		static TagParse invalid() {
			return new TagParse(false, 0, 0);
		}
	}

	private sealed interface GradPiece {
		record Text(String value, OverlaySegmentRole role) implements GradPiece {}
		record Protected(String value, int color, OverlaySegmentRole role) implements GradPiece {}
	}
}
