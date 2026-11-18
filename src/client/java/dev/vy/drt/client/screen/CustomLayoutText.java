package dev.vy.drt.client.screen;

/** Text edits for the custom overlay layout editor: typing, pasting and deleting over a selection. */
final class CustomLayoutText {
	static final int MAX_LENGTH = 1200;

	record Edit(String text, int caret) {
	}

	private CustomLayoutText() {
	}

	static String normalizeNewlines(String text) {
		return text == null ? "" : text.replace("\r\n", "\n").replace('\r', '\n');
	}

	/** Replaces {@code [start, end)} with {@code insert}, keeping newlines and tabs but no other control characters. */
	static Edit replace(String text, int start, int end, String insert) {
		String current = text == null ? "" : text;
		int from = Math.max(0, Math.min(Math.min(start, end), current.length()));
		int to = Math.max(from, Math.min(Math.max(start, end), current.length()));
		String cleaned = clean(normalizeNewlines(insert));
		String kept = current.substring(0, from) + current.substring(to);
		int room = Math.max(0, MAX_LENGTH - kept.length());
		if (cleaned.length() > room) cleaned = cleaned.substring(0, room);
		return new Edit(kept.substring(0, from) + cleaned + kept.substring(from), from + cleaned.length());
	}

	private static String clean(String text) {
		StringBuilder cleaned = new StringBuilder(text.length());
		text.codePoints().forEach(cp -> {
			if (cp == '\n' || cp == '\t' || !Character.isISOControl(cp)) cleaned.appendCodePoint(cp);
		});
		return cleaned.toString();
	}
}
