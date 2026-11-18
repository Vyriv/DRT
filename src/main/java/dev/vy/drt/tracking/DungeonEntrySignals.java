package dev.vy.drt.tracking;

import java.util.Locale;
import java.util.regex.Pattern;

/** Floor headings occur in completion summaries too; they are not entry evidence. */
public final class DungeonEntrySignals {
    private static final Pattern COUNTDOWN = Pattern.compile("STARTING IN \\d+ SECONDS?[.!]?");
    private static final Pattern ENTRY = Pattern.compile("(?:.* )?(?:ENTERED|ENTERING) (?:THE )?(?:MASTER MODE )?CATACOMBS(?: .*)?");
    private DungeonEntrySignals() {}
    public static boolean isEntry(String text, boolean dungeonContext) {
        if (text == null) return false;
        String line = text.trim().toUpperCase(Locale.ROOT);
        return dungeonContext && COUNTDOWN.matcher(line).matches() || ENTRY.matcher(line).matches();
    }
}
