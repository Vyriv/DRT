package dev.vy.drt.luck.capture;

import dev.vy.drt.luck.FloorId;
import dev.vy.drt.luck.data.FloorTable;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/**
 * Turns stable Croesus and RNG Meter screens into luck data. A Croesus run view is only linked to
 * a run through the entry clicked on the Croesus page just before it, and only when that entry was
 * matched to exactly one captured run; everything else is kept as an unmatched observation. The one
 * exception is a run view seen inside the dungeon instance of a run DRT just completed, which can
 * only belong to that run.
 */
public final class LuckCaptureCoordinator {
	/** How long after clicking a Croesus entry its run view may open. */
	public static final long CLICK_TO_VIEW_MILLIS = 10_000L;
	/** How long a linked run view stays linked while moving between its chests and back. */
	public static final long VIEW_CONTEXT_MILLIS = 10L * 60L * 1000L;

	public static final String REASON_NO_CLICKED_ENTRY = "NO_CLICKED_ENTRY";
	public static final String REASON_VIEW_ENTRY_MISMATCH = "VIEW_ENTRY_MISMATCH";
	public static final String REASON_LIVE_FLOOR_MISMATCH = "LIVE_FLOOR_MISMATCH";
	public static final String REASON_LIVE_RUN_NOT_FOUND = "LIVE_RUN_NOT_FOUND";

	private final LuckTrackingStore store;
	private final Function<FloorId, FloorTable> floors;

	private CroesusMenuParser.Menu lastMenu;
	private CroesusRunMatcher.Result lastMatches;
	private long lastMenuAt;
	private ViewContext pendingClick;
	private ViewContext activeView;

	private record ViewContext(CroesusMenuParser.RunEntry entry, CroesusRunMatcher.EntryMatch match, long observedAt, long at) {
	}

	public LuckCaptureCoordinator(LuckTrackingStore store, Function<FloorId, FloorTable> floors) {
		this.store = store;
		this.floors = floors == null ? floor -> null : floors;
	}

	public CroesusRunMatcher.Result onCroesusMenu(LuckProbeRecorder.ScreenSample screen, long now) {
		CroesusMenuParser.Menu menu = CroesusMenuParser.parse(screen);
		if (menu == null) return null;
		CroesusRunMatcher.Result matches = CroesusRunMatcher.match(menu, store.runs(), now);
		store.applyCroesusMenu(menu, matches, now);
		lastMenu = menu;
		lastMatches = matches;
		lastMenuAt = now;
		activeView = null;
		return matches;
	}

	/** A click on a Croesus page slot; only run entries arm the next run view. */
	public void onCroesusClick(int slot, long now) {
		if (lastMenu == null) return;
		CroesusMenuParser.RunEntry entry = lastMenu.entryAt(slot);
		if (entry == null) return;
		pendingClick = new ViewContext(entry, lastMatches == null ? null : lastMatches.forSlot(slot), lastMenuAt, now);
	}

	/**
	 * A click on a Croesus page slot, read from the page as it is at the click. The page may not
	 * have stayed open long enough to be captured as stable, so the clicked entry and its matches
	 * come from this snapshot under the same strict rules. A click that is not on a run entry of
	 * this page arms nothing, even if an older page had an entry in that slot.
	 *
	 * @return the clicked entry's match, or null when the click armed nothing
	 */
	public CroesusRunMatcher.EntryMatch onCroesusClick(LuckProbeRecorder.ScreenSample pageAtClick, int slot, long now) {
		CroesusMenuParser.Menu menu = CroesusMenuParser.parse(pageAtClick);
		if (menu == null) return null;
		CroesusMenuParser.RunEntry entry = menu.entryAt(slot);
		if (entry == null) return null;
		CroesusRunMatcher.Result matches = CroesusRunMatcher.match(menu, store.runs(), now);
		store.applyCroesusMenu(menu, matches, now);
		lastMenu = menu;
		lastMatches = matches;
		lastMenuAt = now;
		activeView = null;
		CroesusRunMatcher.EntryMatch match = matches.forSlot(slot);
		pendingClick = new ViewContext(entry, match, now, now);
		return match;
	}

	/** Result of handling one run view snapshot. */
	public record ViewOutcome(String luckRunId, LuckTrackingStore.OfferResult offerResult, String unmatchedReason, int chests, boolean live) {
		public ViewOutcome(String luckRunId, LuckTrackingStore.OfferResult offerResult, String unmatchedReason, int chests) {
			this(luckRunId, offerResult, unmatchedReason, chests, false);
		}
	}

	/** Result of a click on a Croesus screen: the clicked page entry or the run view read at the click. */
	public record ClickOutcome(CroesusRunMatcher.EntryMatch entry, ViewOutcome view) {
	}

	/**
	 * A click on a Croesus page or run view, read from the screen as it is at the click. Either
	 * screen may be left before it stays open long enough to be captured as stable: a page click
	 * arms the clicked entry, and a run view click reads the view at once so its offers are stored
	 * and the chest opened from it is linked under the same strict rules as a stable view.
	 *
	 * @return what the click recorded, or null when it recorded nothing
	 */
	public ClickOutcome onCroesusScreenClick(LuckProbeRecorder.ScreenSample screenAtClick, int slot, long now, LiveRewardSession live) {
		if (screenAtClick == null) return null;
		String title = screenAtClick.title();
		if (CroesusMenuParser.isMenuTitle(title)) {
			CroesusRunMatcher.EntryMatch match = onCroesusClick(screenAtClick, slot, now);
			return match == null ? null : new ClickOutcome(match, null);
		}
		if (CroesusRunViewParser.isRunViewTitle(title)) {
			ViewOutcome view = onCroesusRunView(screenAtClick, now, live);
			return view == null ? null : new ClickOutcome(null, view);
		}
		return null;
	}

	public ViewOutcome onCroesusRunView(LuckProbeRecorder.ScreenSample screen, long now) {
		return onCroesusRunView(screen, now, null);
	}

	/**
	 * @param live the run just completed in the instance the player is still in, or null. When
	 *     present it is the only run this view can belong to, so no Croesus entry is needed; it
	 *     must never be passed for a view opened outside that instance.
	 */
	public ViewOutcome onCroesusRunView(LuckProbeRecorder.ScreenSample screen, long now, LiveRewardSession live) {
		if (screen == null || !CroesusRunViewParser.isRunViewTitle(screen.title())) return null;
		if (live != null && !live.drtRunSessionId().isBlank()) return onLiveRunView(screen, now, live);
		ViewContext context = currentContext(now);
		CroesusMenuParser.RunEntry entry = context == null ? null : context.entry();
		FloorId floor = entry == null ? null : entry.floor();
		CroesusRunViewParser.RunView view = CroesusRunViewParser.parse(screen,
			number -> floor != null && floor.number() == number ? floors.apply(floor) : null);
		if (view == null) return null;
		pendingClick = null;
		if (context == null) activeView = null;

		String reason = null;
		if (context == null) {
			reason = REASON_NO_CLICKED_ENTRY;
		} else if (!consistent(view, entry)) {
			reason = REASON_VIEW_ENTRY_MISMATCH;
		} else if (context.match() == null || !context.match().matched()) {
			reason = context.match() == null ? CroesusRunMatcher.REASON_UNREADABLE : context.match().reason();
		}
		if (context != null) activeView = context;
		if (reason == null) {
			LuckTrackingStore.OfferResult result = store.recordCroesusOffers(context.match().luckRunId(), view, now);
			return new ViewOutcome(context.match().luckRunId(), result, null, view.chests().size());
		}
		store.recordUnmatchedCroesus(unmatched(view, context, reason, now));
		return new ViewOutcome(null, null, reason, view.chests().size());
	}

	private ViewOutcome onLiveRunView(LuckProbeRecorder.ScreenSample screen, long now, LiveRewardSession live) {
		pendingClick = null;
		FloorId floor = FloorId.parse(live.floor());
		CroesusRunViewParser.RunView view = CroesusRunViewParser.parse(screen,
			number -> floor != null && floor.number() == number ? floors.apply(floor) : null);
		if (view == null) return null;
		LuckRunRecord run = store.runForSession(live.drtRunSessionId());
		String reason = null;
		if (floor == null || floor.number() != view.floorNumber()
			|| (view.masterMode() != null && view.masterMode() != floor.masterMode())) {
			reason = REASON_LIVE_FLOOR_MISMATCH;
		} else if (run == null) {
			reason = REASON_LIVE_RUN_NOT_FOUND;
		}
		if (reason == null) {
			LuckTrackingStore.OfferResult result = store.recordCroesusOffers(run.luckRunId, view, now);
			return new ViewOutcome(run.luckRunId, result, null, view.chests().size(), true);
		}
		store.recordUnmatchedCroesus(unmatched(view, null, reason, now));
		return new ViewOutcome(null, null, reason, view.chests().size(), true);
	}

	/**
	 * The DRT run session of the run whose Croesus run view is open or was just left for one of its
	 * chests, when the evidence linked that view's entry to exactly one recorded run. Null otherwise,
	 * including for links made by list order.
	 */
	public String activeViewRunSession(long now) {
		if (activeView == null || now - activeView.at() > VIEW_CONTEXT_MILLIS) return null;
		CroesusRunMatcher.EntryMatch match = activeView.match();
		if (match == null || !match.matched()) return null;
		if (!CroesusRunMatcher.METHOD_UNIQUE.equals(match.method()) && !CroesusRunMatcher.METHOD_ELIMINATION.equals(match.method())) return null;
		LuckRunRecord run = store.run(match.luckRunId());
		return run == null || run.drtRunSessionId.isBlank() ? null : run.drtRunSessionId;
	}

	/** Readings from the Catacombs RNG Meters overview. */
	public List<LuckTrackingStore.MeterAttachment> onMeterOverview(LuckProbeRecorder.ScreenSample screen, long now) {
		RngMeterParser.Overview overview = RngMeterParser.parseOverview(screen);
		if (overview == null) return List.of();
		List<LuckTrackingStore.MeterAttachment> out = new ArrayList<>();
		for (RngMeterParser.Reading reading : overview.readings()) {
			out.addAll(store.recordMeterReading(toReading(reading, LuckMeterReading.SOURCE_OVERVIEW, null, now)));
		}
		return out;
	}

	/** Exact requirements and the header reading from one floor's RNG Meter screen. */
	public List<LuckTrackingStore.MeterAttachment> onMeterFloorMenu(LuckProbeRecorder.ScreenSample screen, long now) {
		RngMeterParser.FloorMenu menu = RngMeterParser.parseFloorMenu(screen);
		if (menu == null) return List.of();
		FloorTable table = floors.apply(menu.floor());
		List<LuckMeterRequirement> items = new ArrayList<>();
		for (RngMeterParser.ItemRequirement item : menu.items()) {
			LuckMeterRequirement requirement = new LuckMeterRequirement();
			requirement.floor = menu.floor().name();
			requirement.itemName = item.name();
			requirement.itemKey = LuckText.contentLine(item.name(), table).key();
			requirement.requiredScore = item.requiredScore();
			requirement.requiredText = item.requiredText();
			requirement.appearsIn = item.appearsIn() == null ? null : item.appearsIn().name();
			requirement.oddsLabel = item.oddsLabel();
			requirement.oddsPercent = item.oddsPercent();
			requirement.actionLine = item.actionLine();
			requirement.observedAtEpochMillis = now;
			items.add(requirement);
		}
		store.recordMeterRequirements(menu.floor().name(), items);
		if (menu.header() == null) return List.of();
		RngMeterParser.ItemRequirement selected = menu.header().hasSelection() ? menu.item(menu.header().selectedItem()) : null;
		return store.recordMeterReading(toReading(menu.header(), LuckMeterReading.SOURCE_FLOOR_MENU, selected, now));
	}

	/**
	 * The clicked entry or open view a run view belongs to. Only read here: it is consumed once the
	 * view parses, so a view whose items have not arrived yet keeps the click for its next read.
	 */
	private ViewContext currentContext(long now) {
		if (pendingClick != null && now - pendingClick.at() <= CLICK_TO_VIEW_MILLIS) return pendingClick;
		if (activeView != null && now - activeView.at() <= VIEW_CONTEXT_MILLIS) return activeView;
		return null;
	}

	private static boolean consistent(CroesusRunViewParser.RunView view, CroesusMenuParser.RunEntry entry) {
		if (entry.floor() == null) return false;
		if (entry.floor().number() != view.floorNumber()) return false;
		return view.masterMode() == null || view.masterMode() == entry.masterMode();
	}

	private LuckMeterReading toReading(RngMeterParser.Reading parsed, String source, RngMeterParser.ItemRequirement selected, long now) {
		LuckMeterReading reading = new LuckMeterReading();
		reading.floor = parsed.floor().name();
		reading.observedAtEpochMillis = now;
		reading.lastObservedAtEpochMillis = now;
		reading.source = source;
		reading.selectedItem = parsed.selectedItem();
		reading.storedScore = parsed.storedScore();
		reading.storedText = parsed.storedText();
		reading.requiredScore = parsed.requiredScore();
		reading.requiredText = parsed.requiredText();
		reading.progressPercent = parsed.progressPercent();
		if (parsed.requiredScore() != null) reading.requiredSource = source;
		if (selected != null) {
			if (reading.requiredScore == null && selected.requiredScore() != null) {
				reading.requiredScore = selected.requiredScore();
				reading.requiredSource = source;
			}
			if (selected.appearsIn() != null) reading.guaranteeChest = selected.appearsIn().name();
		}
		if (parsed.selectedItem() != null) {
			reading.selectedKey = LuckText.contentLine(parsed.selectedItem(), floors.apply(parsed.floor())).key();
		}
		return reading;
	}

	private static UnmatchedCroesusRun unmatched(CroesusRunViewParser.RunView view, ViewContext context, String reason, long now) {
		UnmatchedCroesusRun observation = new UnmatchedCroesusRun();
		observation.firstObservedAtEpochMillis = now;
		observation.lastObservedAtEpochMillis = now;
		observation.reason = reason;
		observation.viewTitle = view.title();
		observation.floorNumber = view.floorNumber();
		observation.masterMode = view.masterMode();
		observation.availableModifiers = new ArrayList<>(view.availableModifiers());
		StringBuilder fingerprint = new StringBuilder(view.title());
		for (CroesusRunViewParser.ChestOffer offer : view.chests()) {
			LuckRunRecord.LuckChestRecord chest = new LuckRunRecord.LuckChestRecord();
			chest.tier = offer.tier().name();
			chest.source = LuckRunRecord.SOURCE_CROESUS_RUN_VIEW;
			LuckTrackingStore.appendOffer(chest, offer, now);
			observation.chests.add(chest);
			fingerprint.append('|').append(offer.tier().name()).append(':').append(String.join(";", offer.contentTexts()));
		}
		observation.fingerprint = Integer.toHexString(fingerprint.toString().hashCode()) + "-" + fingerprint.length();
		if (context != null) {
			CroesusMenuParser.RunEntry entry = context.entry();
			observation.entryName = entry.name();
			observation.floor = entry.floor() == null ? null : entry.floor().name();
			observation.masterMode = entry.masterMode();
			observation.players = new ArrayList<>(entry.players());
			observation.completedText = entry.completedText();
			if (entry.completedAgoMin() != null && entry.completedAgoMax() != null) {
				observation.completedAfterEpochMillis = context.observedAt() - entry.completedAgoMax();
				observation.completedBeforeEpochMillis = context.observedAt() - entry.completedAgoMin();
			}
		}
		return observation;
	}
}
