package dev.vy.drt.client.screen;

import dev.vy.drt.client.luck.LuckRatingService;
import dev.vy.drt.client.luck.LuckRunCapture;
import dev.vy.drt.luck.FloorId;
import dev.vy.drt.luck.capture.LuckHistoryEvaluation;
import dev.vy.drt.luck.capture.LuckHistoryEvaluation.FloorCoverage;
import dev.vy.drt.luck.capture.LuckHistoryEvaluation.Status;
import dev.vy.drt.luck.capture.LuckHistoryEvaluation.WithholdCause;
import dev.vy.drt.luck.capture.LuckMeterReading;
import dev.vy.drt.luck.capture.LuckRunRecord;
import dev.vy.drt.luck.capture.LuckScoringInput;
import dev.vy.drt.luck.stats.LuckEvaluator.Band;
import dev.vy.drt.luck.stats.RareOutcomeGate;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

/** Read-only view of the Luck Rating: verdict, setup, per-floor coverage, every recorded run and the reasons behind the result. */
public final class DrtLuckRatingScreen extends Screen {
	private static final int PANEL_BG = 0xF00D0D18;
	private static final int PANEL_ALT = 0xFF111222;
	private static final int PANEL_ROW = 0xFF151729;
	private static final int PANEL_ROW_HOVER = 0xFF1D2139;
	private static final int BORDER = 0xFF35385D;
	private static final int BORDER_ACTIVE = 0xFF6268A8;
	private static final int TEXT = 0xFFE6E8F2;
	private static final int MUTED = 0xFF9AA4BD;
	private static final int DIM = 0xFF747E99;
	private static final int GREEN = 0xFF63D184;
	private static final int RED = 0xFFFF6B6B;
	private static final int GOLD = 0xFFFFC857;
	private static final int GAP = 4;
	private static final int PAD = 12;
	private static final int PANEL_MIN_W = 360;
	private static final int PANEL_MAX_W = 480;
	private static final int PANEL_MAX_H = 350;
	private static final int VERDICT_TOP = 26;
	private static final int VERDICT_PAD_TOP = 6;
	private static final int VERDICT_PAD_BOTTOM = 4;
	private static final int VERDICT_BLOCK_GAP = 3;
	private static final int VERDICT_MAX_EXPLANATION_LINES = 3;
	private static final int VERDICT_MAX_NOTE_LINES = 2;
	private static final int SETUP_H = 18;
	private static final int PARAGRAPH_GAP = 3;
	private static final int SECTION_GAP = 7;
	private static final int TAB_H = 16;
	private static final int TAB_PAD = 8;
	private static final int FOOTER_H = 30;
	private static final int LIST_ROW_H = 18;
	private static final int TOOLTIP_MAX_W = 240;
	private static final DateTimeFormatter RUN_TIME = DateTimeFormatter.ofPattern("MMM d HH:mm", Locale.ROOT).withZone(ZoneId.systemDefault());
	private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("MMM d", Locale.ROOT).withZone(ZoneId.systemDefault());

	private enum Tab {
		FLOORS("Floors"),
		RUNS("Runs"),
		DETAILS("Details");

		private final String label;

		Tab(String label) {
			this.label = label;
		}
	}

	private record ClickTarget(int x, int y, int w, int h, Runnable action, List<String> tooltip) {
	}

	private record FloorRow(String floor, int nameColor, String captured, String rating, int ratingColor, List<String> tooltip) {
	}

	private record RunRow(String time, String floor, String grade, String reason, String status, int statusColor, List<String> tooltip) {
	}

	/** One Details row: text, a section heading with a trailing rule, or blank space when {@code text} is empty. */
	private record Line(String text, int color, int indent, int height, boolean heading) {
	}

	/** The verdict block, wrapped for the current width. */
	private record VerdictLayout(String headline, int headlineColor, List<String> explanation, List<String> note, int noteColor,
		double progress, String progressLabel, List<String> tooltip, int height) {
	}

	private final Screen parent;
	private final List<ClickTarget> clickTargets = new ArrayList<>();
	private Tab tab = Tab.FLOORS;
	private int scroll;

	private int ox;
	private int oy;
	private int winW;
	private int winH;
	private int contentX;
	private int contentW;
	private int listY;
	private int listH;
	private int verdictH = 46;
	private VerdictLayout verdictLayout;

	private LuckRatingService.Snapshot modelSnapshot;
	private long modelStoreRevision = Long.MIN_VALUE;
	private int modelWidth = -1;
	private List<FloorRow> floorRows = List.of();
	private List<RunRow> runRows = List.of();
	private List<Line> detailLines = List.of();

	public DrtLuckRatingScreen(Screen parent) {
		super(Component.literal("Luck Rating"));
		this.parent = parent;
	}

	@Override
	protected void init() {
		LuckRatingService.refresh(false);
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	@Override
	public void extractBackground(GuiGraphicsExtractor guiGraphics, int mouseX, int mouseY, float partialTick) {
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
		LuckRatingService.refresh(false);
		updateLayout();
		clickTargets.clear();
		LuckRatingService.Snapshot snapshot = LuckRatingService.current();
		rebuildModel(snapshot);
		updateLayout();

		g.fill(0, 0, width, height, 0xA0000000);
		g.fill(ox, oy, ox + winW, oy + winH, PANEL_BG);
		border(g, ox, oy, winW, winH, BORDER);

		g.text(font, "Luck Rating", contentX, oy + 10, TEXT);
		String chip = statusLabel(snapshot);
		g.text(font, chip, contentX + contentW - font.width(chip), oy + 10, statusColor(snapshot));

		drawVerdict(g, snapshot);
		drawSetup(g);
		drawTabs(g, mouseX, mouseY);
		drawList(g, mouseX, mouseY);
		drawFooter(g, snapshot);
		drawHoveredTooltip(g, mouseX, mouseY);
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean isRepeat) {
		if (event.button() == 0) {
			int mx = (int) event.x();
			int my = (int) event.y();
			for (int i = clickTargets.size() - 1; i >= 0; i--) {
				ClickTarget target = clickTargets.get(i);
				if (target.action != null && contains(target.x, target.y, target.w, target.h, mx, my)) {
					target.action.run();
					return true;
				}
			}
		}
		return super.mouseClicked(event, isRepeat);
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
		updateLayout();
		if (contains(contentX, listY, contentW, listH, (int) mouseX, (int) mouseY)) {
			int step = tab == Tab.DETAILS ? lineHeight() * 2 : LIST_ROW_H;
			scroll = clamp(scroll - (int) Math.copySign(step, scrollY), 0, maxScroll());
			return true;
		}
		return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		if (event.key() == GLFW.GLFW_KEY_ESCAPE) {
			onClose();
			return true;
		}
		return super.keyPressed(event);
	}

	@Override
	public void onClose() {
		if (minecraft != null && parent != null) {
			minecraft.setScreen(parent);
			return;
		}
		super.onClose();
	}

	private void updateLayout() {
		winW = Math.max(PANEL_MIN_W, Math.min(PANEL_MAX_W, width - 24));
		winH = Math.min(PANEL_MAX_H, height - 16);
		if (winW > width - 8) winW = Math.max(1, width - 8);
		if (winH < 1) winH = Math.max(1, height - 8);
		ox = (width - winW) / 2;
		oy = (height - winH) / 2;
		contentX = ox + PAD;
		contentW = winW - PAD * 2;
		int tabsY = oy + VERDICT_TOP + verdictH + GAP + SETUP_H + GAP;
		listY = tabsY + TAB_H;
		listH = Math.max(LIST_ROW_H, oy + winH - FOOTER_H - GAP - listY);
	}

	private int lineHeight() {
		return font.lineHeight + 2;
	}

	// Verdict

	private record Verdict(String headline, int headlineColor, String explanation, String note, int noteColor, double progress) {
	}

	private VerdictLayout layoutVerdict(LuckRatingService.Snapshot snapshot) {
		Verdict verdict = verdict(snapshot);
		int textW = contentW - 16;
		int lh = lineHeight();
		String headline = ellipsize(verdict.headline, textW);
		List<String> explanation = wrapLimited(verdict.explanation, textW, VERDICT_MAX_EXPLANATION_LINES);
		boolean progress = verdict.progress >= 0.0;
		List<String> note = progress ? List.of() : wrapLimited(verdict.note, textW, VERDICT_MAX_NOTE_LINES);
		int height = VERDICT_PAD_TOP + lh + 2 + explanation.size() * lh;
		if (progress) {
			height += VERDICT_BLOCK_GAP + lh;
		} else if (!note.isEmpty()) {
			height += VERDICT_BLOCK_GAP + note.size() * lh;
		}
		height += VERDICT_PAD_BOTTOM;
		boolean truncated = !headline.equals(verdict.headline)
			|| wrapNeat(verdict.explanation, textW).size() > explanation.size()
			|| (!progress && wrapNeat(verdict.note, textW).size() > note.size());
		List<String> tooltip = truncated ? List.of(verdict.headline, verdict.explanation, verdict.note, "Full text in Details.") : null;
		return new VerdictLayout(headline, verdict.headlineColor, explanation, note, verdict.noteColor, verdict.progress, verdict.note, tooltip, height);
	}

	private void drawVerdict(GuiGraphicsExtractor g, LuckRatingService.Snapshot snapshot) {
		VerdictLayout layout = verdictLayout;
		int x = contentX;
		int y = oy + VERDICT_TOP;
		g.fill(x, y, x + contentW, y + verdictH, PANEL_ALT);
		border(g, x, y, contentW, verdictH, BORDER);
		int textX = x + 8;
		int textW = contentW - 16;
		int lh = lineHeight();
		int lineY = y + VERDICT_PAD_TOP;
		g.text(font, layout.headline, textX, lineY, layout.headlineColor);
		lineY += lh + 2;
		for (String line : layout.explanation) {
			g.text(font, line, textX, lineY, MUTED);
			lineY += lh;
		}
		if (layout.progress >= 0.0) {
			lineY += VERDICT_BLOCK_GAP;
			String label = layout.progressLabel;
			int labelW = Math.min(font.width(label), textW / 2);
			int barW = textW - labelW - 8;
			drawProgress(g, textX, lineY + (font.lineHeight - 6) / 2, barW, 6, layout.progress);
			g.text(font, ellipsize(label, labelW), textX + barW + 8, lineY, layout.noteColor);
		} else if (!layout.note.isEmpty()) {
			lineY += VERDICT_BLOCK_GAP;
			for (String line : layout.note) {
				g.text(font, line, textX, lineY, layout.noteColor);
				lineY += lh;
			}
		}
		clickTargets.add(new ClickTarget(x, y, contentW, verdictH, null, layout.tooltip));
	}

	private Verdict verdict(LuckRatingService.Snapshot snapshot) {
		LuckHistoryEvaluation result = snapshot.evaluation();
		if (result == null) {
			if (snapshot.state() == LuckRatingService.State.FAILED) {
				return new Verdict("Could not calculate", RED, "Something went wrong while simulating your runs. Recalculate to try again.",
					"The error is in Details.", DIM, -1);
			}
			return new Verdict("Calculating...", MUTED, "Simulating your recorded runs. This can take a few seconds.", "", DIM, -1);
		}
		return switch (result.status()) {
			case NO_RECORDED_RUNS -> new Verdict("No runs recorded yet", TEXT,
				"DRT records every Catacombs run you finish while it is running. There is nothing else to set up.", "", DIM, -1);
			case NO_SCORABLE_RUNS -> new Verdict("No rating yet", TEXT,
				"DRT has recorded " + plural(result.recordedRuns(), "run") + " but has not seen their chest contents yet. It reads them when you open a run in Croesus.",
				"Viewing runs is optional. Unviewed runs stay listed.", DIM, -1);
			case INSUFFICIENT_DATA -> insufficient(result);
			case PROVISIONAL -> provisional(result);
			case UNRESOLVED -> unresolved(result);
			case WITHHELD -> withheld(result);
		};
	}

	private Verdict insufficient(LuckHistoryEvaluation result) {
		double expected = Double.isNaN(result.expectedRareDrops()) ? 0.0 : result.expectedRareDrops();
		int more = result.runsUntilSufficient();
		String note = more > 0 ? "About " + plural(more, "more run") : String.format(Locale.ROOT, "%.2f of %.0f", expected, RareOutcomeGate.DEFAULT_MINIMUM_EXPECTED);
		return new Verdict("Not enough runs yet", TEXT,
			String.format(Locale.ROOT, "Luck shows up in rare drops. Your runs on %s would expect only %.2f rare drops so far, and a rating needs at least %.0f.",
				joinFloors(result.ratedFloors()), expected, RareOutcomeGate.DEFAULT_MINIMUM_EXPECTED),
			note, MUTED, Math.min(1.0, expected / RareOutcomeGate.DEFAULT_MINIMUM_EXPECTED));
	}

	private Verdict provisional(LuckHistoryEvaluation result) {
		Band band = result.headlineBand();
		double percentile = result.evaluation().baseline().percentile().percentile();
		return new Verdict(bandLabel(band) + String.format(Locale.ROOT, ": luckier than %d%% of simulated players with the same runs", Math.round(percentile * 100)),
			bandColor(band),
			"Based on " + plural(result.scored().size() - unratedScored(result), "run") + " on " + joinFloors(result.ratedFloors())
				+ ". Every possible combination of your unknown settings gives the same result.",
			"Provisional: uses community loot data, not official Hypixel numbers.", DIM, -1);
	}

	private Verdict unresolved(LuckHistoryEvaluation result) {
		Band low = Band.of(result.evaluation().minPercentile());
		Band high = Band.of(result.evaluation().maxPercentile());
		String range = low == high ? bandLabel(low) : "Somewhere between " + bandLabel(low) + " and " + bandLabel(high);
		boolean modifiersKnown = LuckRunCapture.profile().unknownFieldCount() == 0;
		return new Verdict(range, GOLD,
			"DRT cannot narrow it down further, because some of your modifiers or the game's exact rules are unknown.",
			modifiersKnown ? "Confirmed game rules may settle it later." : "Filling in your modifiers may settle it for future runs.", DIM, -1);
	}

	private Verdict withheld(LuckHistoryEvaluation result) {
		WithholdCause first = result.withheldCauses().isEmpty() ? null : result.withheldCauses().get(0);
		String explanation = first == null ? "Some data is missing or contradictory, so any rating could mislead." : playerReason(first, result);
		if (result.withheldCauses().size() > 1) explanation += " (" + plural(result.withheldCauses().size() - 1, "more reason") + " in Details.)";
		String hint = first == WithholdCause.NO_ELIGIBLE_FLOOR ? " The Floors tab shows what each floor needs." : "";
		LuckHistoryEvaluation.Estimate estimate = result.estimate();
		if (estimate != null && estimate.informative()) {
			return new Verdict("No rating yet", TEXT, explanation,
				"Rough estimate, incomplete history: " + estimateRange(estimate) + ". Not verified.", GOLD, -1);
		}
		if (estimate != null) {
			return new Verdict("No rating yet", TEXT, explanation, "No useful estimate: the unseen runs could put you anywhere." + hint, DIM, -1);
		}
		return new Verdict("No rating yet", TEXT, explanation, playerNoEstimate(result) + hint, DIM, -1);
	}

	/** The main reason in player terms; the engine's own wording stays in Details. */
	private static String playerReason(WithholdCause cause, LuckHistoryEvaluation result) {
		return switch (cause) {
			case NO_ELIGIBLE_FLOOR -> {
				FloorCoverage closest = result.floorCoverage().stream()
					.filter(floor -> !floor.floor().equals(LuckHistoryEvaluation.UNKNOWN_FLOOR))
					.max(Comparator.comparingDouble(FloorCoverage::coverage).thenComparingInt(FloorCoverage::recorded))
					.orElse(null);
				String base = "A floor is rated once DRT has seen the chests of " + LuckHistoryEvaluation.MINIMUM_COVERAGE_PERCENT + "% of its runs.";
				yield closest == null ? base : base + " Closest: " + closest.floor() + ", " + closest.scorable() + " of " + closest.recorded() + " seen.";
			}
			case UNATTRIBUTED_RUNS -> "Some runs were recorded without a floor, so DRT cannot tell where they belong.";
			case KISMET_REROLLS -> "A Kismet reroll was used on a rated floor, and DRT cannot rate rerolled runs fairly yet.";
			case UNRECORDED_RUNS -> "Your RNG Meter moved more than the recorded runs explain, so DRT missed some runs.";
			case METER_CONTRADICTION -> "Your RNG Meter readings do not match the recorded runs.";
			case TOO_MANY_CONFIGURATIONS -> "Too many of your settings are unknown to work out a rating. Filling in your modifiers helps.";
			case MISSING_RUNS_COULD_CHANGE_VERDICT -> "Some runs' chests were never seen, and depending on what they held, the result could change.";
			case INCOMPLETE_RECORDS -> "Some run records are incomplete.";
		};
	}

	private static String playerNoEstimate(LuckHistoryEvaluation result) {
		for (WithholdCause cause : result.withheldCauses()) {
			switch (cause) {
				case KISMET_REROLLS -> {
					return "No estimate while a Kismet reroll is in the history.";
				}
				case UNRECORDED_RUNS -> {
					return "No estimate, because how many runs were missed is unknown.";
				}
				case UNATTRIBUTED_RUNS -> {
					return "No estimate until every run has a floor.";
				}
				case METER_CONTRADICTION, TOO_MANY_CONFIGURATIONS, INCOMPLETE_RECORDS -> {
					return "No estimate yet.";
				}
				default -> {
				}
			}
		}
		return "Too few runs for even a rough estimate yet.";
	}

	private static String estimateRange(LuckHistoryEvaluation.Estimate estimate) {
		return estimate.lowest() == estimate.highest() ? bandLabel(estimate.lowest()) : bandLabel(estimate.lowest()) + " to " + bandLabel(estimate.highest());
	}

	private static String joinFloors(List<String> floors) {
		if (floors.size() <= 1) return String.join("", floors);
		return String.join(", ", floors.subList(0, floors.size() - 1)) + " and " + floors.get(floors.size() - 1);
	}

	private static int unratedScored(LuckHistoryEvaluation result) {
		Set<String> rated = new HashSet<>(result.ratedFloors());
		return (int) result.scored().stream().filter(run -> !rated.contains(run.observed().run().floor().name())).count();
	}

	// Setup strip

	private void drawSetup(GuiGraphicsExtractor g) {
		int y = oy + VERDICT_TOP + verdictH + GAP;
		int textY = y + (SETUP_H - font.lineHeight) / 2;
		int floorsRead = 0;
		long lastOverview = 0L;
		Set<String> floors = new HashSet<>();
		for (LuckMeterReading reading : LuckRunCapture.store().meterReadings()) {
			if (!LuckMeterReading.SOURCE_OVERVIEW.equals(reading.source)) continue;
			if (floors.add(reading.floor)) floorsRead++;
			lastOverview = Math.max(lastOverview, reading.lastObservedAtEpochMillis);
		}
		String meter = floorsRead == 0 ? "RNG Meter: open the Catacombs RNG Meters menu once" : "RNG Meter: read " + DAY.format(Instant.ofEpochMilli(lastOverview));
		int meterColor = floorsRead == 0 ? MUTED : GREEN;

		int buttonW = 40;
		int buttonX = contentX + contentW - buttonW;
		int unknown = LuckRunCapture.profile().unknownFieldCount();
		String modifiers = unknown == 0 ? "Modifiers: all set" : "Modifiers: " + unknown + " unknown";
		int modifiersX = buttonX - 6 - font.width(modifiers);
		drawButton(g, buttonX, y + 1, buttonW, SETUP_H - 2, "Edit", () -> {
			if (minecraft != null) minecraft.setScreen(new DrtLuckModifiersScreen(this));
		}, List.of("Set the global modifier preset", "Applies to runs completed after saving"));
		g.text(font, modifiers, modifiersX, textY, unknown == 0 ? GREEN : MUTED);
		clickTargets.add(new ClickTarget(modifiersX, y, font.width(modifiers), SETUP_H, null,
			List.of("Unknown modifiers are rated as a range, never as zero.", "More known modifiers can narrow an unresolved rating.")));

		int meterW = modifiersX - 12 - contentX;
		g.text(font, ellipsize(meter, meterW), contentX, textY, meterColor);
		clickTargets.add(new ClickTarget(contentX, y, Math.max(0, meterW), SETUP_H, null, floorsRead == 0
			? List.of("Open the Catacombs RNG Meters overview once.", "DRT reads every floor's stored score from it and follows each meter from there.")
			: List.of("Stored scores read for " + plural(floorsRead, "floor") + ".", "DRT follows each meter from here. No repeat checks needed.")));
	}

	// Tabs

	private void drawTabs(GuiGraphicsExtractor g, int mouseX, int mouseY) {
		int y = listY - TAB_H;
		int x = contentX;
		for (Tab candidate : Tab.values()) {
			String label = candidate == Tab.RUNS ? "Runs (" + runRows.size() + ")" : candidate.label;
			int w = font.width(label) + TAB_PAD * 2;
			boolean active = candidate == tab;
			boolean hovered = contains(x, y, w, TAB_H, mouseX, mouseY);
			g.fill(x, y, x + w, y + TAB_H, active ? PANEL_ROW_HOVER : hovered ? PANEL_ROW : PANEL_ALT);
			g.fill(x, y, x + w, y + 1, active ? BORDER_ACTIVE : BORDER);
			g.fill(x, y, x + 1, y + TAB_H, BORDER);
			g.fill(x + w - 1, y, x + w, y + TAB_H, BORDER);
			g.text(font, label, x + TAB_PAD, y + (TAB_H - font.lineHeight) / 2 + 1, active ? TEXT : MUTED);
			Tab target = candidate;
			clickTargets.add(new ClickTarget(x, y, w, TAB_H, () -> {
				if (tab != target) {
					tab = target;
					scroll = 0;
				}
			}, null));
			x += w + 2;
		}
	}

	// Scrollable list

	private void drawList(GuiGraphicsExtractor g, int mouseX, int mouseY) {
		g.fill(contentX, listY, contentX + contentW, listY + listH, PANEL_ALT);
		scroll = clamp(scroll, 0, maxScroll());
		g.enableScissor(contentX + 1, listY + 1, contentX + contentW - 1, listY + listH - 1);
		switch (tab) {
			case FLOORS -> drawFloors(g, mouseX, mouseY);
			case RUNS -> drawRuns(g, mouseX, mouseY);
			case DETAILS -> drawDetails(g);
		}
		g.disableScissor();
		border(g, contentX, listY, contentW, listH, BORDER);
		drawScrollbar(g);
	}

	private int contentHeight() {
		return switch (tab) {
			case FLOORS -> (floorRows.size() + 1) * LIST_ROW_H + 4;
			case RUNS -> Math.max(1, runRows.size()) * LIST_ROW_H + 4;
			case DETAILS -> detailLines.stream().mapToInt(Line::height).sum() + 8;
		};
	}

	private int maxScroll() {
		return Math.max(0, contentHeight() - listH);
	}

	private void drawScrollbar(GuiGraphicsExtractor g) {
		int total = contentHeight();
		if (total <= listH) return;
		int trackX = contentX + contentW - 3;
		int thumbH = Math.max(12, listH * listH / total);
		int thumbY = listY + (listH - thumbH) * scroll / Math.max(1, total - listH);
		g.fill(trackX, listY + 1, trackX + 2, listY + listH - 1, PANEL_ROW);
		g.fill(trackX, thumbY, trackX + 2, thumbY + thumbH, BORDER_ACTIVE);
	}

	private boolean rowVisible(int rowY, int rowH) {
		return rowY + rowH > listY && rowY < listY + listH;
	}

	private void drawFloors(GuiGraphicsExtractor g, int mouseX, int mouseY) {
		int left = contentX + 8;
		int capturedX = left + Math.max(font.width("Floor"), font.width("M7")) + 24;
		int right = contentX + contentW - 10;
		int y = listY + 2 - scroll;
		if (rowVisible(y, LIST_ROW_H)) {
			int textY = y + (LIST_ROW_H - font.lineHeight) / 2;
			g.text(font, "Floor", left, textY, DIM);
			g.text(font, "Chests seen", capturedX, textY, DIM);
			g.text(font, "Rating", right - font.width("Rating"), textY, DIM);
		}
		y += LIST_ROW_H;
		if (floorRows.isEmpty()) {
			if (rowVisible(y, LIST_ROW_H)) g.text(font, modelSnapshot == null || modelSnapshot.evaluation() == null ? "Calculating..." : "No runs recorded yet", left, y + 5, DIM);
			return;
		}
		for (FloorRow row : floorRows) {
			if (rowVisible(y, LIST_ROW_H)) {
				boolean hovered = contains(contentX + 1, y, contentW - 2, LIST_ROW_H, mouseX, mouseY) && contains(contentX, listY, contentW, listH, mouseX, mouseY);
				g.fill(contentX + 2, y, contentX + contentW - 2, y + LIST_ROW_H - 1, hovered ? PANEL_ROW_HOVER : PANEL_ROW);
				int textY = y + (LIST_ROW_H - font.lineHeight) / 2;
				g.text(font, row.floor, left, textY, row.nameColor);
				g.text(font, row.captured, capturedX, textY, row.nameColor == DIM ? DIM : MUTED);
				int ratingW = right - (capturedX + font.width(row.captured) + 12);
				String rating = ellipsize(row.rating, Math.max(0, ratingW));
				g.text(font, rating, right - font.width(rating), textY, row.ratingColor);
				addListTooltip(y, LIST_ROW_H, row.tooltip);
			}
			y += LIST_ROW_H;
		}
	}

	private void drawRuns(GuiGraphicsExtractor g, int mouseX, int mouseY) {
		int left = contentX + 8;
		int right = contentX + contentW - 10;
		int floorX = left + font.width("Oct 30 23:59") + 8;
		int gradeX = floorX + font.width("M7") + 8;
		int reasonX = gradeX + font.width("S+ 300") + 8;
		int y = listY + 2 - scroll;
		if (runRows.isEmpty()) {
			g.text(font, modelSnapshot == null || modelSnapshot.evaluation() == null ? "Calculating..." : "No runs recorded yet", left, y + 5, DIM);
			return;
		}
		for (RunRow row : runRows) {
			if (rowVisible(y, LIST_ROW_H)) {
				boolean hovered = contains(contentX + 1, y, contentW - 2, LIST_ROW_H, mouseX, mouseY) && contains(contentX, listY, contentW, listH, mouseX, mouseY);
				g.fill(contentX + 2, y, contentX + contentW - 2, y + LIST_ROW_H - 1, hovered ? PANEL_ROW_HOVER : PANEL_ROW);
				int textY = y + (LIST_ROW_H - font.lineHeight) / 2;
				g.text(font, row.time, left, textY, DIM);
				g.text(font, row.floor, floorX, textY, TEXT);
				g.text(font, ellipsize(row.grade, reasonX - gradeX - 4), gradeX, textY, MUTED);
				String status = row.status;
				int statusX = right - font.width(status);
				g.text(font, status, statusX, textY, row.statusColor);
				g.text(font, ellipsize(row.reason, Math.max(0, statusX - 8 - reasonX)), reasonX, textY, DIM);
				addListTooltip(y, LIST_ROW_H, row.tooltip);
			}
			y += LIST_ROW_H;
		}
	}

	private void drawDetails(GuiGraphicsExtractor g) {
		int y = listY + 4 - scroll;
		int x = contentX + 8;
		for (Line line : detailLines) {
			if (!line.text.isEmpty() && rowVisible(y, line.height)) {
				g.text(font, line.text, x + line.indent, y + 1, line.color);
				if (line.heading) {
					int ruleX = x + font.width(line.text) + 4;
					int ruleY = y + 1 + font.lineHeight / 2;
					if (ruleX < contentX + contentW - 10) g.fill(ruleX, ruleY, contentX + contentW - 10, ruleY + 1, BORDER);
				}
			}
			y += line.height;
		}
	}

	private void addListTooltip(int y, int h, List<String> tooltip) {
		int top = Math.max(y, listY);
		int bottom = Math.min(y + h, listY + listH);
		if (bottom > top) clickTargets.add(new ClickTarget(contentX + 1, top, contentW - 2, bottom - top, null, tooltip));
	}

	// Footer

	private void drawFooter(GuiGraphicsExtractor g, LuckRatingService.Snapshot snapshot) {
		int y = oy + winH - FOOTER_H + 6;
		g.fill(ox + 10, y - 5, ox + winW - 10, y - 4, 0x5535385D);
		int recalcW = font.width("Recalculate") + 12;
		drawButton(g, contentX, y, recalcW, 18, "Recalculate", () -> LuckRatingService.refresh(true),
			List.of("Run the simulation again", LuckRatingService.SAMPLES + " simulated histories per plausible setting"));
		int doneW = 58;
		int doneX = contentX + contentW - doneW;
		drawButton(g, doneX, y, doneW, 18, "Done", this::onClose, null);
		String summary = footerSummary(snapshot);
		int left = contentX + recalcW + 8;
		int right = doneX - 8;
		String shown = ellipsize(summary, Math.max(0, right - left));
		g.text(font, shown, left + (right - left - font.width(shown)) / 2, y + (18 - font.lineHeight) / 2, DIM);
	}

	private static String footerSummary(LuckRatingService.Snapshot snapshot) {
		if (snapshot.state() == LuckRatingService.State.CALCULATING) return "Calculating...";
		if (snapshot.finishedAtMillis() == 0L) return "";
		long seconds = Math.max(0L, (System.currentTimeMillis() - snapshot.finishedAtMillis()) / 1000L);
		String age = seconds < 60 ? "just now" : seconds < 3600 ? (seconds / 60) + "m ago" : (seconds / 3600) + "h ago";
		return String.format(Locale.ROOT, "Updated %s in %.1fs", age, snapshot.elapsedMillis() / 1000.0);
	}

	// Model

	private void rebuildModel(LuckRatingService.Snapshot snapshot) {
		long storeRevision = LuckRunCapture.store().revision();
		if (snapshot == modelSnapshot && storeRevision == modelStoreRevision && contentW == modelWidth) return;
		modelSnapshot = snapshot;
		modelStoreRevision = storeRevision;
		modelWidth = contentW;
		verdictLayout = layoutVerdict(snapshot);
		verdictH = verdictLayout.height;
		LuckHistoryEvaluation result = snapshot.evaluation();
		floorRows = result == null ? List.of() : floorRows(result);
		runRows = runRows(result);
		detailLines = detailLines(snapshot, result);
	}

	/** Every floor in game order, played or not, then runs without a readable floor. */
	private static List<FloorRow> floorRows(LuckHistoryEvaluation result) {
		Map<String, FloorCoverage> byFloor = new HashMap<>();
		for (FloorCoverage floor : result.floorCoverage()) byFloor.put(floor.floor(), floor);
		List<FloorRow> out = new ArrayList<>();
		for (FloorId id : FloorId.values()) {
			FloorCoverage floor = byFloor.get(id.name());
			if (floor == null || floor.recorded() == 0) {
				out.add(new FloorRow(id.name(), DIM, "-", "Not started", DIM, List.of(id.name() + ": no runs recorded yet")));
			} else {
				out.add(floorRow(floor));
			}
		}
		FloorCoverage unknown = byFloor.get(LuckHistoryEvaluation.UNKNOWN_FLOOR);
		if (unknown != null) out.add(floorRow(unknown));
		return out;
	}

	private static FloorRow floorRow(FloorCoverage floor) {
		boolean unknown = floor.floor().equals(LuckHistoryEvaluation.UNKNOWN_FLOOR);
		String name = unknown ? "?" : floor.floor();
		String captured = floor.scorable() + "/" + floor.recorded() + "  " + Math.round(floor.coverage() * 100) + "%";
		String rating;
		int color;
		if (unknown) {
			rating = "Floor not read";
			color = RED;
		} else if (floor.eligible()) {
			rating = "Rated";
			color = GREEN;
		} else {
			rating = "Needs " + floor.capturedRunsToGate() + " more";
			color = MUTED;
		}
		List<String> tooltip = new ArrayList<>();
		tooltip.add(name + ": " + plural(floor.recorded(), "recorded run"));
		tooltip.add("Chests seen for " + floor.scorable() + ", not seen for " + floor.rejected() + ", held out for Kismet: " + floor.excluded() + ".");
		if (unknown) {
			tooltip.add("Runs without a readable floor cannot count towards any floor.");
		} else if (floor.eligible()) {
			tooltip.add("Chests seen for at least " + LuckHistoryEvaluation.MINIMUM_COVERAGE_PERCENT + "% of runs, so this floor counts towards the rating.");
		} else {
			tooltip.add("A floor counts once DRT has seen the chests of " + LuckHistoryEvaluation.MINIMUM_COVERAGE_PERCENT + "% of its runs.");
			tooltip.add("That takes " + plural(floor.capturedRunsToGate(), "more run") + " in a row with chests seen. DRT reads them when you open a run in Croesus.");
		}
		return new FloorRow(name, TEXT, captured, rating, color, tooltip);
	}

	private List<RunRow> runRows(LuckHistoryEvaluation result) {
		Set<String> rated = result == null ? Set.of() : new HashSet<>(result.ratedFloors());
		Map<String, LuckScoringInput.Converted> scored = new HashMap<>();
		Map<String, LuckScoringInput.Rejected> rejected = new HashMap<>();
		Map<String, LuckScoringInput.Excluded> excluded = new HashMap<>();
		if (result != null) {
			result.scored().forEach(run -> scored.put(run.luckRunId(), run));
			result.rejected().forEach(run -> rejected.put(run.luckRunId(), run));
			result.excluded().forEach(run -> excluded.put(run.luckRunId(), run));
		}
		List<LuckRunRecord> runs = new ArrayList<>(LuckRunCapture.store().runs());
		runs.sort(Comparator.comparingLong((LuckRunRecord run) -> run.completedAtEpochMillis).reversed());
		List<RunRow> out = new ArrayList<>(runs.size());
		for (LuckRunRecord run : runs) {
			String time = RUN_TIME.format(Instant.ofEpochMilli(run.completedAtEpochMillis));
			String floor = run.floor == null || run.floor.equals(LuckHistoryEvaluation.UNKNOWN_FLOOR) ? "?" : run.floor;
			String grade = (run.grade == null ? "?" : run.grade) + (run.score == null ? "" : " " + run.score);
			boolean floorRated = rated.contains(floorKey(run.floor));
			List<String> tooltip = new ArrayList<>();
			tooltip.add(floor + " " + grade + ", " + time);
			String status;
			String reason;
			int color;
			LuckScoringInput.Converted converted = scored.get(run.luckRunId);
			LuckScoringInput.Rejected missing = rejected.get(run.luckRunId);
			LuckScoringInput.Excluded held = excluded.get(run.luckRunId);
			if (converted != null) {
				status = floorRated ? "Scored" : "Captured";
				color = floorRated ? GREEN : MUTED;
				reason = floorRated ? "" : "floor not rated";
				tooltip.add(floorRated ? "Chests seen. Counted in the rating." : "Chests seen. Its floor does not have enough runs with chests seen yet.");
				if (!converted.caveats().isEmpty()) {
					tooltip.add(floorRated ? "Notes (the run still counts):" : "Notes:");
					for (String caveat : converted.caveats()) tooltip.add("- " + caveatLabel(caveat));
				}
			} else if (missing != null) {
				status = "Not captured";
				color = MUTED;
				reason = missing.reasons().isEmpty() ? "" : reasonLabel(missing.reasons().get(0))
					+ (missing.reasons().size() > 1 ? " +" + (missing.reasons().size() - 1) : "");
				tooltip.add(floorRated
					? "Kept as missing. The rating checks it could not change the verdict."
					: "Kept as missing. Its floor is not rated.");
				for (String code : missing.reasons()) tooltip.add("- " + reasonLabel(code));
			} else if (held != null) {
				status = "Held out";
				color = GOLD;
				reason = reasonLabel(held.reason());
				tooltip.add("Kismet rerolls are not simulated yet, so this run is held out and listed.");
				for (String evidence : held.evidence()) tooltip.add("- " + evidence);
			} else {
				status = "New";
				color = DIM;
				reason = "waiting for recalculation";
				tooltip.add("Recorded after the last calculation.");
			}
			out.add(new RunRow(time, floor, grade, reason, status, color, tooltip));
		}
		return out;
	}

	private List<Line> detailLines(LuckRatingService.Snapshot snapshot, LuckHistoryEvaluation result) {
		List<Line> out = new ArrayList<>();
		int width = contentW - 20;
		if (result == null) {
			paragraph(out, snapshot.state() == LuckRatingService.State.FAILED ? "The calculation failed: " + snapshot.error() : "Calculating...", DIM, width);
			return out;
		}
		Verdict verdict = verdict(snapshot);
		heading(out, "Verdict", width);
		paragraph(out, verdict.headline, verdict.headlineColor, width);
		paragraph(out, verdict.explanation, MUTED, width);
		if (!verdict.note.isEmpty()) paragraph(out, verdict.note, verdict.noteColor == GOLD ? GOLD : DIM, width);
		technicalLines(result).forEach(line -> bullet(out, line, DIM, width));

		if (result.status() == Status.WITHHELD && !result.withheldCauses().isEmpty()) {
			heading(out, "Why there is no rating", width);
			for (WithholdCause cause : result.withheldCauses()) {
				paragraph(out, causeTitle(cause), TEXT, width);
				paragraph(out, causeExplanation(cause), MUTED, width);
			}
			paragraph(out, "Engine detail:", DIM, width);
			for (String reason : result.withheldReasons()) bullet(out, reason, DIM, width);
		}

		if (result.status() == Status.WITHHELD) {
			heading(out, "Rough estimate, incomplete history", width);
			LuckHistoryEvaluation.Estimate estimate = result.estimate();
			if (estimate == null) {
				paragraph(out, playerNoEstimate(result), MUTED, width);
				if (result.estimateUnavailable() != null) bullet(out, "Engine detail: " + result.estimateUnavailable() + ".", DIM, width);
			} else {
				paragraph(out, estimate.informative() ? estimateRange(estimate) : "Any band: " + estimateRange(estimate), estimate.informative() ? GOLD : DIM, width);
				paragraph(out, String.format(Locale.ROOT,
					"Covers %s: %s with chests seen, plus %s without (%d with unseen loot). Each unseen run is counted as the luckiest, then the unluckiest, of its floor's runs.",
					joinFloors(estimate.floors()), plural(estimate.scoredRuns(), "run"), plural(estimate.missingRuns(), "run"), estimate.unknownOutcomes()), MUTED, width);
				paragraph(out, "It does not assume the unseen runs were average or random, and it is not a verified rating.", DIM, width);
				bullet(out, String.format(Locale.ROOT, "Percentile %.3f to %.3f.", estimate.lowPercentile(), estimate.highPercentile()), DIM, width);
			}
		}

		heading(out, "Data", width);
		paragraph(out, String.format(Locale.ROOT, "%s recorded. Chests seen for %d, not seen for %d, held out for Kismet: %d.",
			plural(result.recordedRuns(), "run"), result.scorableRuns(), result.rejectedRuns(), result.excludedRuns()), MUTED, width);
		if (!Double.isNaN(result.expectedRareDrops())) {
			paragraph(out, String.format(Locale.ROOT, "Runs on rated floors expect %.2f rare drops. A rating needs at least %.0f.",
				result.expectedRareDrops(), RareOutcomeGate.DEFAULT_MINIMUM_EXPECTED), MUTED, width);
		}
		if (result.missingRunBounds() != null && !result.missingRunBounds().material()) {
			paragraph(out, "Runs without chests seen on rated floors: " + result.missingRunBounds().missingRuns()
				+ ". Even at their luckiest or unluckiest, they could not change the verdict.", MUTED, width);
		}

		if (!result.runCaveatCounts().isEmpty()) {
			heading(out, "Notes on counted runs", width);
			paragraph(out, "None of these removes a run from the rating.", DIM, width);
			result.runCaveatCounts().entrySet().stream()
				.sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
				.forEach(entry -> bullet(out, capitalize(caveatLabel(entry.getKey())) + ": " + plural(entry.getValue(), "run"), MUTED, width));
		}

		heading(out, "Model", width);
		for (String caveat : result.historyCaveats()) bullet(out, historyCaveatLabel(caveat), MUTED, width);
		if (result.evaluation() != null && result.evaluation().baseline() != null) {
			bullet(out, "Scored under " + plural(result.evaluation().alternatives().size() + 1, "plausible combination")
				+ " of modifiers, RNG Meter rules and meter states. A rating needs every one to agree.", MUTED, width);
		}
		if (!result.meterWindows().isEmpty()) {
			bullet(out, "RNG Meter state assumed across " + plural(result.meterWindows().size(), "stretch", "stretches") + " DRT did not read.", MUTED, width);
		}
		if (result.modifierSnapshots() > 1) {
			bullet(out, "Runs use " + result.modifierSnapshots() + " different modifier presets, each from when the run finished.", MUTED, width);
		}
		for (String dropped : result.droppedConfigurations()) bullet(out, dropped, DIM, width);
		bullet(out, String.format(Locale.ROOT, "%d simulated histories per combination, calculated in %.1fs.",
			LuckRatingService.SAMPLES, snapshot.elapsedMillis() / 1000.0), DIM, width);
		return out;
	}

	/** Engine numbers behind the headline, for Details only. */
	private static List<String> technicalLines(LuckHistoryEvaluation result) {
		if (result.evaluation() == null || result.evaluation().baseline() == null) return List.of();
		double baseline = result.evaluation().baseline().percentile().percentile();
		double min = result.evaluation().minPercentile();
		double max = result.evaluation().maxPercentile();
		int combinations = result.evaluation().alternatives().size() + 1;
		if (result.status() == Status.PROVISIONAL) {
			return List.of(String.format(Locale.ROOT, "Percentile %.3f; every one of %d combinations falls between %.3f and %.3f.", baseline, combinations, min, max));
		}
		if (result.status() == Status.UNRESOLVED) {
			return List.of(String.format(Locale.ROOT, "Percentile %.3f to %.3f across %d combinations.", min, max, combinations));
		}
		return List.of(String.format(Locale.ROOT, "Runs with chests seen alone: percentile %.3f to %.3f across %d combinations. Not a rating.", min, max, combinations));
	}

	private void heading(List<Line> out, String text, int width) {
		if (!out.isEmpty()) space(out, SECTION_GAP);
		out.add(new Line(ellipsize(text, width), TEXT, 0, lineHeight() + 3, true));
	}

	private void paragraph(List<Line> out, String text, int color, int width) {
		for (String line : wrapNeat(text, width)) out.add(new Line(line, color, 0, lineHeight(), false));
		space(out, PARAGRAPH_GAP);
	}

	/** A list item whose wrapped lines hang under its text, not under the dash. */
	private void bullet(List<Line> out, String text, int color, int width) {
		String dash = "- ";
		int hang = font.width(dash);
		List<String> lines = wrapNeat(text, width - hang);
		for (int i = 0; i < lines.size(); i++) {
			out.add(i == 0 ? new Line(dash + lines.get(i), color, 0, lineHeight(), false) : new Line(lines.get(i), color, hang, lineHeight(), false));
		}
		space(out, 1);
	}

	private void space(List<Line> out, int height) {
		out.add(new Line("", DIM, 0, height, false));
	}

	private static String capitalize(String text) {
		return text.isEmpty() ? text : Character.toUpperCase(text.charAt(0)) + text.substring(1);
	}

	// Labels

	private static String statusLabel(LuckRatingService.Snapshot snapshot) {
		LuckHistoryEvaluation result = snapshot.evaluation();
		if (result == null) return snapshot.state() == LuckRatingService.State.FAILED ? "Failed" : "Calculating";
		return switch (result.status()) {
			case NO_RECORDED_RUNS -> "No runs";
			case NO_SCORABLE_RUNS -> "Not rated";
			case INSUFFICIENT_DATA -> "Not enough data";
			case PROVISIONAL -> "Provisional";
			case UNRESOLVED -> "Unresolved";
			case WITHHELD -> "Withheld";
		};
	}

	private static int statusColor(LuckRatingService.Snapshot snapshot) {
		LuckHistoryEvaluation result = snapshot.evaluation();
		if (result == null) return snapshot.state() == LuckRatingService.State.FAILED ? RED : DIM;
		return switch (result.status()) {
			case PROVISIONAL -> GREEN;
			case UNRESOLVED -> GOLD;
			case WITHHELD -> RED;
			default -> MUTED;
		};
	}

	private static String bandLabel(Band band) {
		if (band == null) return "?";
		return switch (band) {
			case VERY_UNLUCKY -> "Very unlucky";
			case UNLUCKY -> "Unlucky";
			case TYPICAL -> "Typical";
			case LUCKY -> "Lucky";
			case VERY_LUCKY -> "Very lucky";
		};
	}

	private static int bandColor(Band band) {
		if (band == null) return TEXT;
		return switch (band) {
			case VERY_UNLUCKY, UNLUCKY -> RED;
			case TYPICAL -> TEXT;
			case LUCKY -> GREEN;
			case VERY_LUCKY -> GOLD;
		};
	}

	private static String causeTitle(WithholdCause cause) {
		return switch (cause) {
			case NO_ELIGIBLE_FLOOR -> "No floor has " + LuckHistoryEvaluation.MINIMUM_COVERAGE_PERCENT + "% of its runs captured yet";
			case UNATTRIBUTED_RUNS -> "Some runs have no readable floor";
			case KISMET_REROLLS -> "Kismet rerolls on rated floors";
			case UNRECORDED_RUNS -> "The RNG Meter shows runs DRT did not record";
			case METER_CONTRADICTION -> "RNG Meter readings contradict the runs";
			case TOO_MANY_CONFIGURATIONS -> "Too many unknown settings to score";
			case MISSING_RUNS_COULD_CHANGE_VERDICT -> "Uncaptured runs could change the verdict";
			case INCOMPLETE_RECORDS -> "Some records are incomplete";
		};
	}

	private static String causeExplanation(WithholdCause cause) {
		return switch (cause) {
			case NO_ELIGIBLE_FLOOR -> "A floor counts once " + LuckHistoryEvaluation.MINIMUM_COVERAGE_PERCENT
				+ "% of its runs have their chest offers captured. The Floors tab shows how many more each floor needs.";
			case UNATTRIBUTED_RUNS -> "Without a floor these runs cannot be counted against any floor's coverage.";
			case KISMET_REROLLS -> "Rerolls usually follow poor offers, so leaving rerolled runs out would flatter the rest. They stay listed until rerolls are simulated.";
			case UNRECORDED_RUNS -> "Runs DRT missed have unknown loot and an unknown count, so no range can account for them.";
			case METER_CONTRADICTION -> "The meter readings do not fit the recorded runs under any plausible rule.";
			case TOO_MANY_CONFIGURATIONS -> "Setting known modifiers in the preset reduces the combinations to simulate.";
			case MISSING_RUNS_COULD_CHANGE_VERDICT -> "At their luckiest or unluckiest, the uncaptured runs on rated floors could move the result into another band.";
			case INCOMPLETE_RECORDS -> "A run without complete chest offers reached the engine.";
		};
	}

	private static String reasonLabel(String code) {
		return switch (code) {
			case "NUMERIC_SCORE_MISSING" -> "score not read";
			case "FLOOR_UNKNOWN" -> "floor not read";
			case "GRADE_UNKNOWN" -> "grade not read";
			case "OFFERS_NOT_OBSERVED" -> "chests not viewed";
			case "OFFERS_PARTIAL" -> "some chests not viewed";
			case "UNEXPECTED_CHEST" -> "chest does not fit the score";
			case "OFFER_CONTENT_UNRESOLVED" -> "unknown item in a chest";
			case "OFFER_CHANGED_UNVERIFIED" -> "chest changed, reroll unconfirmed";
			case "KISMET_ORIGINAL_OFFER_MISSING" -> "rerolled before DRT saw it";
			case LuckScoringInput.KISMET_REROLL_CONFIRMED -> "Kismet reroll";
			case LuckScoringInput.KISMET_REROLL_SUSPECTED -> "possible Kismet reroll";
			default -> humanize(code);
		};
	}

	private static String caveatLabel(String caveat) {
		int colon = caveat.indexOf(':');
		String code = colon < 0 ? caveat : caveat.substring(0, colon);
		String detail = colon < 0 ? "" : " (" + caveat.substring(colon + 1) + ")";
		return switch (code) {
			case "CROESUS_MODIFIERS_UNKNOWN" -> "Croesus modifiers not read";
			case "PURCHASES_UNKNOWN" -> "purchases unknown";
			case "METER_START_UNKNOWN" -> "RNG Meter start assumed" + detail;
			case "METER_GAP" -> "RNG Meter assumed before this run";
			case "METER_SELECTION_NOT_SELECTABLE", "METER_SELECTION_UNRESOLVED" -> "RNG Meter selection unclear";
			case "MODIFIERS_PARTIALLY_UNKNOWN" -> "some modifiers unknown";
			case "MODIFIERS_UNKNOWN" -> "modifiers unknown";
			case "KISMET_PROVISIONAL" -> "no Kismet reroll detected (detection not yet confirmed in game)";
			case "METER_DERIVED" -> "RNG Meter derived from earlier readings";
			case "METER_UNKNOWN" -> "RNG Meter not read";
			default -> humanize(code) + detail;
		};
	}

	private static String historyCaveatLabel(String caveat) {
		int colon = caveat.indexOf(':');
		if (colon < 0) return humanize(caveat);
		String code = caveat.substring(0, colon);
		String detail = caveat.substring(colon + 1);
		return switch (code) {
			case "KISMET_RUNS_EXCLUDED" -> "Held out for Kismet: " + detail;
			case "RUNS_NOT_SCORED" -> "Not captured: " + detail;
			case "FLOOR_NOT_RATED" -> "Floor not rated: " + detail;
			default -> caveatLabel(caveat);
		};
	}

	private static String humanize(String code) {
		String lower = code.toLowerCase(Locale.ROOT).replace('_', ' ');
		return lower.isEmpty() ? lower : Character.toUpperCase(lower.charAt(0)) + lower.substring(1);
	}

	private static String floorKey(String floor) {
		FloorId parsed = FloorId.parse(floor);
		return parsed == null ? LuckHistoryEvaluation.UNKNOWN_FLOOR : parsed.name();
	}

	private static String plural(int count, String noun) {
		return plural(count, noun, noun + "s");
	}

	private static String plural(int count, String singular, String pluralForm) {
		return count + " " + (count == 1 ? singular : pluralForm);
	}

	// Drawing helpers

	private void drawProgress(GuiGraphicsExtractor g, int x, int y, int w, int h, double progress) {
		double clamped = Math.max(0.0, Math.min(1.0, progress));
		g.fill(x, y, x + w, y + h, PANEL_ROW);
		border(g, x, y, w, h, BORDER);
		int fill = (int) Math.round((w - 2) * clamped);
		if (fill > 0) g.fill(x + 1, y + 1, x + 1 + fill, y + h - 1, clamped >= 1.0 ? GREEN : BORDER_ACTIVE);
	}

	private void drawButton(GuiGraphicsExtractor g, int x, int y, int w, int h, String label, Runnable onClick, List<String> tooltip) {
		g.fill(x, y, x + w, y + h, 0xFF171B30);
		border(g, x, y, w, h, BORDER);
		g.text(font, label, x + (w - font.width(label)) / 2, y + (h - font.lineHeight) / 2 + 1, TEXT);
		clickTargets.add(new ClickTarget(x, y, w, h, onClick, tooltip));
	}

	private void drawHoveredTooltip(GuiGraphicsExtractor g, int mouseX, int mouseY) {
		for (int i = clickTargets.size() - 1; i >= 0; i--) {
			ClickTarget target = clickTargets.get(i);
			if (!contains(target.x, target.y, target.w, target.h, mouseX, mouseY)) continue;
			if (target.tooltip == null || target.tooltip.isEmpty()) continue;
			drawTooltip(g, target.tooltip, mouseX, mouseY);
			return;
		}
	}

	private void drawTooltip(GuiGraphicsExtractor g, List<String> text, int mouseX, int mouseY) {
		List<String> lines = new ArrayList<>();
		for (String entry : text) {
			if (entry == null || entry.isEmpty()) continue;
			lines.addAll(wrap(entry, TOOLTIP_MAX_W));
		}
		if (lines.isEmpty()) return;
		int pad = 4;
		int lh = lineHeight();
		int tw = 0;
		for (String line : lines) tw = Math.max(tw, font.width(line));
		int th = lines.size() * lh - 2;
		int tx = mouseX + 10;
		int ty = mouseY - th - pad * 2;
		if (tx + tw + pad * 2 > width) tx = Math.max(pad, width - tw - pad * 2);
		if (ty < pad) ty = mouseY + 14;
		if (ty + th + pad > height) ty = Math.max(pad, height - th - pad);
		g.fill(tx - pad, ty - pad, tx + tw + pad, ty + th + pad, 0xDD000000);
		border(g, tx - pad, ty - pad, tw + pad * 2, th + pad * 2, BORDER_ACTIVE);
		for (int i = 0; i < lines.size(); i++) {
			g.text(font, lines.get(i), tx, ty + i * lh, i == 0 ? 0xFFCCCCFF : MUTED, true);
		}
	}

	private List<String> wrap(String text, int maxWidth) {
		List<String> out = new ArrayList<>();
		if (text == null || text.isEmpty()) return out;
		StringBuilder line = new StringBuilder();
		for (String word : text.split(" ")) {
			if (word.isEmpty()) continue;
			String candidate = line.isEmpty() ? word : line + " " + word;
			if (font.width(candidate) <= maxWidth) {
				line.setLength(0);
				line.append(candidate);
				continue;
			}
			if (!line.isEmpty()) out.add(line.toString());
			line.setLength(0);
			if (font.width(word) <= maxWidth) {
				line.append(word);
			} else {
				out.add(ellipsize(word, maxWidth));
			}
		}
		if (!line.isEmpty()) out.add(line.toString());
		return out;
	}

	/**
	 * Greedy wrapping, rebalanced when the last line would be a short orphan: the narrowest width
	 * that keeps the same number of lines spreads the words evenly instead.
	 */
	private List<String> wrapNeat(String text, int maxWidth) {
		List<String> greedy = wrap(text, maxWidth);
		if (greedy.size() < 2 || font.width(greedy.get(greedy.size() - 1)) >= maxWidth / 3) return greedy;
		int longestWord = 0;
		for (String word : text.split(" ")) longestWord = Math.max(longestWord, font.width(word));
		int low = Math.max(longestWord, maxWidth / 2);
		int high = maxWidth;
		while (low < high) {
			int mid = (low + high) / 2;
			if (wrap(text, mid).size() <= greedy.size()) {
				high = mid;
			} else {
				low = mid + 1;
			}
		}
		return wrap(text, high);
	}

	/** At most {@code maxLines} lines, the last ending in an ellipsis when text was cut. */
	private List<String> wrapLimited(String text, int maxWidth, int maxLines) {
		List<String> lines = wrapNeat(text, maxWidth);
		if (lines.size() <= maxLines) return lines;
		List<String> out = new ArrayList<>(lines.subList(0, maxLines - 1));
		String last = lines.get(maxLines - 1) + " " + lines.get(maxLines);
		String cut = ellipsize(last, maxWidth);
		out.add(cut.endsWith("...") ? cut : ellipsize(last + "...", maxWidth));
		return out;
	}

	private String ellipsize(String value, int maxWidth) {
		if (value == null) return "";
		if (font.width(value) <= maxWidth) return value;
		String ellipsis = "...";
		int ellipsisWidth = font.width(ellipsis);
		StringBuilder builder = new StringBuilder();
		for (int i = 0; i < value.length(); i++) {
			char c = value.charAt(i);
			if (font.width(builder.toString() + c) + ellipsisWidth > maxWidth) break;
			builder.append(c);
		}
		return builder.append(ellipsis).toString();
	}

	private static int clamp(int value, int min, int max) {
		return Math.max(min, Math.min(max, value));
	}

	private static boolean contains(int x, int y, int w, int h, int mx, int my) {
		return mx >= x && mx < x + w && my >= y && my < y + h;
	}

	private static void border(GuiGraphicsExtractor g, int x, int y, int w, int h, int color) {
		g.fill(x, y, x + w, y + 1, color);
		g.fill(x, y + h - 1, x + w, y + h, color);
		g.fill(x, y, x + 1, y + h, color);
		g.fill(x + w - 1, y, x + w, y + h, color);
	}
}
