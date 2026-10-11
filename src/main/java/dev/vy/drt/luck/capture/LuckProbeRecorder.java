package dev.vy.drt.luck.capture;

import com.google.gson.Gson;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.regex.Pattern;

/**
 * Records raw, unparsed samples of the screens and chat lines that the Luck Rating capture
 * design depends on. Samples are written verbatim so the real Hypixel formats can be checked
 * before any parser is built on top of them.
 */
public final class LuckProbeRecorder {
	public static final String SCHEMA = "drt-luck-probe-v1";
	public static final int STABLE_TICKS = 10;

	private static final Gson GSON = new Gson();
	private static final List<String> SCREEN_KEYWORDS = List.of(
		"CROESUS", "CHEST", "RNG", "CATACOMBS", "FLOOR", "MASTER MODE", "KISMET", "REWARD"
	);
	private static final List<String> IGNORED_SCREEN_TITLES = List.of("CHEST", "LARGE CHEST", "ENDER CHEST");
	private static final List<String> CHAT_KEYWORDS = List.of(
		"SCORE", "RNG METER", "KISMET", "REROLL", "CROESUS", "DEFEATED", "EXTRA STATS",
		"CHEST", "MASTER MODE", "THE CATACOMBS"
	);
	private static final Pattern PLAYER_CHAT_PREFIX = Pattern.compile(
		"^(?:(?:PARTY|GUILD|OFFICER|CO-OP)\\s*>|(?:FROM|TO)\\s|\\[\\d+\\]\\s|\\[(?:VIP|MVP|YOUTUBE|ADMIN|MOD|HELPER|GM)[^\\]]*\\]\\s*\\S+:)"
	);

	public record SlotSample(int slot, String itemId, int count, String name, List<String> lore) {
		public SlotSample {
			itemId = itemId == null ? "" : itemId;
			name = name == null ? "" : name;
			lore = lore == null ? List.of() : List.copyOf(lore);
		}
	}

	public record ScreenSample(String title, int containerSlots, List<SlotSample> slots) {
		public ScreenSample {
			title = title == null ? "" : title;
			slots = slots == null ? List.of() : List.copyOf(slots);
		}

		public String fingerprint() {
			StringBuilder sb = new StringBuilder(title).append('\u0000').append(containerSlots);
			for (SlotSample slot : slots) {
				sb.append('\u0001').append(slot.slot()).append('|').append(slot.itemId()).append('|')
					.append(slot.count()).append('|').append(slot.name());
				for (String line : slot.lore()) sb.append('\u0002').append(line);
			}
			return sb.toString();
		}
	}

	private final Consumer<String> sink;
	private final Supplier<Instant> clock;
	private long sequence;
	private long screenSession;
	private Object currentScreenKey;
	private String pendingFingerprint;
	private ScreenSample pendingSample;
	private int pendingStableTicks;
	private String writtenFingerprint;
	private int snapshotsThisSession;

	public LuckProbeRecorder(Consumer<String> sink, Supplier<Instant> clock) {
		this.sink = Objects.requireNonNull(sink, "sink");
		this.clock = clock == null ? Instant::now : clock;
	}

	public static boolean isRelevantScreenTitle(String title) {
		if (title == null) return false;
		String upper = title.trim().toUpperCase(Locale.ROOT);
		if (upper.isEmpty() || IGNORED_SCREEN_TITLES.contains(upper)) return false;
		for (String keyword : SCREEN_KEYWORDS) {
			if (upper.contains(keyword)) return true;
		}
		return false;
	}

	public static boolean isRelevantChatLine(String cleanLine) {
		if (cleanLine == null) return false;
		String upper = cleanLine.trim().toUpperCase(Locale.ROOT);
		if (upper.isEmpty() || PLAYER_CHAT_PREFIX.matcher(upper).find()) return false;
		for (String keyword : CHAT_KEYWORDS) {
			if (upper.contains(keyword)) return true;
		}
		return false;
	}

	/**
	 * Feeds the currently open screen once per client tick. A snapshot is written once its
	 * contents have stayed identical for {@link #STABLE_TICKS} ticks, and again whenever the
	 * contents change and settle within the same screen session (for example after a reroll).
	 *
	 * @return true when a snapshot line was written on this call
	 */
	public boolean observeScreen(Object screenKey, ScreenSample sample) {
		if (screenKey == null || sample == null) {
			screenClosed();
			return false;
		}
		if (screenKey != currentScreenKey) {
			currentScreenKey = screenKey;
			screenSession++;
			snapshotsThisSession = 0;
			writtenFingerprint = null;
			pendingFingerprint = null;
			pendingSample = null;
			pendingStableTicks = 0;
		}
		String fingerprint = sample.fingerprint();
		if (!fingerprint.equals(pendingFingerprint)) {
			pendingFingerprint = fingerprint;
			pendingSample = sample;
			pendingStableTicks = 1;
			return false;
		}
		pendingStableTicks++;
		if (pendingStableTicks < STABLE_TICKS || fingerprint.equals(writtenFingerprint)) return false;
		writtenFingerprint = fingerprint;
		snapshotsThisSession++;
		writeScreen(pendingSample);
		return true;
	}

	public void screenClosed() {
		currentScreenKey = null;
		pendingFingerprint = null;
		pendingSample = null;
		pendingStableTicks = 0;
		writtenFingerprint = null;
	}

	/** @return true when the line was relevant and written */
	public boolean observeChat(String rawLine, String cleanLine, boolean overlay) {
		if (overlay || !isRelevantChatLine(cleanLine)) return false;
		Map<String, Object> line = header("chat");
		line.put("clean", cleanLine);
		line.put("raw", rawLine == null ? "" : rawLine);
		sink.accept(GSON.toJson(line));
		return true;
	}

	public void writeMarker(String note) {
		Map<String, Object> line = header("marker");
		line.put("note", note == null ? "" : note);
		sink.accept(GSON.toJson(line));
	}

	private void writeScreen(ScreenSample sample) {
		Map<String, Object> line = header("screen");
		line.put("screenSession", screenSession);
		line.put("snapshotInSession", snapshotsThisSession);
		line.put("title", sample.title());
		line.put("containerSlots", sample.containerSlots());
		List<Map<String, Object>> slots = new ArrayList<>(sample.slots().size());
		for (SlotSample slot : sample.slots()) {
			Map<String, Object> s = new LinkedHashMap<>();
			s.put("slot", slot.slot());
			s.put("item", slot.itemId());
			s.put("count", slot.count());
			s.put("name", slot.name());
			s.put("lore", slot.lore());
			slots.add(s);
		}
		line.put("slots", slots);
		sink.accept(GSON.toJson(line));
	}

	private Map<String, Object> header(String kind) {
		Map<String, Object> line = new LinkedHashMap<>();
		line.put("schema", SCHEMA);
		line.put("seq", ++sequence);
		line.put("time", clock.get().toString());
		line.put("kind", kind);
		return line;
	}
}
