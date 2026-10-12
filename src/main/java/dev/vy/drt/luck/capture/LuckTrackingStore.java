package dev.vy.drt.luck.capture;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import dev.vy.drt.config.AtomicJsonFileStore;
import dev.vy.drt.luck.ChestTier;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Versioned Luck Rating capture file. It lives beside DRT's own config and never reads from or
 * writes to the existing loot logs. A file from a newer schema is left untouched and the store
 * becomes read-only; a malformed file is preserved under a timestamped name before replacement.
 */
public final class LuckTrackingStore {
	public static final String SCHEMA = "drt-luck";
	/** 2 adds Croesus links, structured offers, meter readings, meter requirements and unmatched Croesus chests. */
	public static final int SCHEMA_VERSION = 2;
	static final int MAX_METER_READINGS = 2_000;
	static final int MAX_UNMATCHED_CROESUS = 300;

	public enum LoadState { NEW, LOADED, RECOVERED_FROM_MALFORMED, NEWER_SCHEMA_READ_ONLY, UNREADABLE_READ_ONLY }

	public enum AddResult { ADDED, DUPLICATE, READ_ONLY, SAVE_FAILED }

	public enum OfferResult { ADDED, UNCHANGED, CHANGED_UNVERIFIED, RUN_NOT_FOUND, READ_ONLY, SAVE_FAILED }

	/** A reading attached to a run as its after-run state, with the before-run reading when there is one. */
	public record MeterAttachment(String luckRunId, String floor, LuckRunRecord.LuckMeterSnapshot before, LuckRunRecord.LuckMeterSnapshot after) {
	}

	static final class FileData {
		String schema = SCHEMA;
		int schemaVersion = SCHEMA_VERSION;
		LuckModifierProfile profile = LuckModifierProfile.unknown();
		List<LuckRunRecord> runs = new ArrayList<>();
		List<LuckMeterReading> meterReadings = new ArrayList<>();
		List<LuckMeterRequirement> meterRequirements = new ArrayList<>();
		List<UnmatchedCroesusRun> unmatchedCroesus = new ArrayList<>();
	}

	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().serializeNulls().create();

	private final Path path;
	private FileData data = new FileData();
	private LoadState loadState = LoadState.NEW;
	private Path preservedMalformedFile;
	private IOException lastSaveError;
	private long revision;

	public LuckTrackingStore(Path path) {
		this.path = Objects.requireNonNull(path, "path");
	}

	public synchronized LoadState load() {
		revision++;
		data = new FileData();
		preservedMalformedFile = null;
		if (!Files.exists(path)) {
			loadState = LoadState.NEW;
			return loadState;
		}
		String json;
		try {
			json = Files.readString(path, StandardCharsets.UTF_8);
		} catch (IOException e) {
			return recoverFromMalformed();
		}
		JsonObject root;
		try {
			JsonElement element = JsonParser.parseString(json);
			if (!element.isJsonObject()) return recoverFromMalformed();
			root = element.getAsJsonObject();
		} catch (JsonParseException e) {
			return recoverFromMalformed();
		}
		int version = root.has("schemaVersion") && root.get("schemaVersion").isJsonPrimitive()
			? root.get("schemaVersion").getAsInt()
			: 0;
		if (version > SCHEMA_VERSION) {
			loadState = LoadState.NEWER_SCHEMA_READ_ONLY;
			return loadState;
		}
		FileData parsed;
		try {
			parsed = GSON.fromJson(root, FileData.class);
		} catch (JsonParseException | IllegalStateException | NumberFormatException e) {
			return recoverFromMalformed();
		}
		data = migrate(parsed == null ? new FileData() : parsed);
		loadState = LoadState.LOADED;
		return loadState;
	}

	public synchronized LoadState loadState() {
		return loadState;
	}

	public synchronized boolean isReadOnly() {
		return loadState == LoadState.NEWER_SCHEMA_READ_ONLY || loadState == LoadState.UNREADABLE_READ_ONLY;
	}

	public synchronized Path preservedMalformedFile() {
		return preservedMalformedFile;
	}

	public synchronized IOException lastSaveError() {
		return lastSaveError;
	}

	public synchronized LuckModifierProfile profile() {
		return data.profile.copy();
	}

	/** Saves new modifier values. The revision only moves when a value actually changes. */
	public synchronized boolean updateProfile(LuckModifierProfile values, long nowEpochMillis) {
		if (isReadOnly() || values == null) return false;
		LuckModifierProfile next = values.copy();
		next.normalize();
		if (next.sameValues(data.profile)) return true;
		LuckModifierProfile previous = data.profile;
		next.revision = previous.revision + 1;
		next.updatedAtEpochMillis = Math.max(0L, nowEpochMillis);
		data.profile = next;
		if (save()) return true;
		data.profile = previous;
		return false;
	}

	public synchronized List<LuckRunRecord> runs() {
		return List.copyOf(data.runs);
	}

	/** Moves on every load and successful save, so a reader can tell its copy is stale. */
	public synchronized long revision() {
		return revision;
	}

	/** Deep copies of every run, safe to read while capture keeps changing the stored ones. */
	public synchronized List<LuckRunRecord> runSnapshots() {
		List<LuckRunRecord> out = new ArrayList<>(data.runs.size());
		for (LuckRunRecord run : data.runs) out.add(GSON.fromJson(GSON.toJson(run), LuckRunRecord.class));
		return out;
	}

	public synchronized boolean containsCompletion(String completionFingerprint) {
		if (completionFingerprint == null || completionFingerprint.isBlank()) return false;
		for (LuckRunRecord run : data.runs) {
			if (completionFingerprint.equals(run.drtCompletionFingerprint)) return true;
		}
		return false;
	}

	/**
	 * Adds a completed run with the current modifier preset snapshotted onto it. A run whose DRT
	 * completion fingerprint or luck id is already stored is ignored.
	 */
	public synchronized AddResult addRun(LuckRunRecord run) {
		if (run == null) return AddResult.DUPLICATE;
		if (isReadOnly()) return AddResult.READ_ONLY;
		run.normalize();
		if (run.luckRunId.isBlank()) run.luckRunId = "luck-" + UUID.randomUUID();
		for (LuckRunRecord existing : data.runs) {
			boolean sameCompletion = !run.drtCompletionFingerprint.isBlank()
				&& run.drtCompletionFingerprint.equals(existing.drtCompletionFingerprint);
			if (sameCompletion || run.luckRunId.equals(existing.luckRunId)) return AddResult.DUPLICATE;
		}
		if (run.modifiers == null) run.modifiers = data.profile.copy();
		LuckMeterReading before = readingBefore(run);
		if (before != null) run.meterSnapshots.add(snapshot(before, LuckRunRecord.PHASE_BEFORE_RUN));
		run.coverage = LuckCoverage.evaluate(run);
		data.runs.add(run);
		if (save()) return AddResult.ADDED;
		data.runs.remove(run);
		return AddResult.SAVE_FAILED;
	}

	public synchronized LuckRunRecord run(String luckRunId) {
		LuckRunRecord run = find(luckRunId);
		return run == null ? null : GSON.fromJson(GSON.toJson(run), LuckRunRecord.class);
	}

	public synchronized List<LuckMeterReading> meterReadings() {
		return List.copyOf(data.meterReadings);
	}

	public synchronized List<UnmatchedCroesusRun> unmatchedCroesus() {
		return List.copyOf(data.unmatchedCroesus);
	}

	public synchronized LuckMeterRequirement requirement(String floor, String itemName) {
		for (LuckMeterRequirement requirement : data.meterRequirements) {
			if (requirement.floor.equals(floor) && requirement.itemName.equals(itemName)) return requirement;
		}
		return null;
	}

	/**
	 * Updates runs matched to entries of a Croesus page: link details, listed modifiers and which
	 * chests were opened. Returns how many runs changed.
	 */
	public synchronized int applyCroesusMenu(CroesusMenuParser.Menu menu, CroesusRunMatcher.Result matches, long now) {
		if (isReadOnly() || menu == null || matches == null) return 0;
		int changed = 0;
		for (CroesusMenuParser.RunEntry entry : menu.entries()) {
			CroesusRunMatcher.EntryMatch match = matches.forSlot(entry.slot());
			if (match == null || !match.matched()) continue;
			LuckRunRecord run = find(match.luckRunId());
			if (run == null) continue;
			if (updateLink(run, entry, match.method(), now) | updatePurchases(run, entry.noChestsOpened(), entry.openedChests())) {
				run.coverage = LuckCoverage.evaluate(run);
				changed++;
			}
		}
		if (changed > 0 && !save()) return 0;
		return changed;
	}

	/**
	 * Stores every chest shown for a run. Identical contents are not stored twice; different
	 * contents for a chest already seen are appended and flagged, never treated as a reroll.
	 */
	public synchronized OfferResult recordCroesusOffers(String luckRunId, CroesusRunViewParser.RunView view, long now) {
		if (isReadOnly()) return OfferResult.READ_ONLY;
		LuckRunRecord run = find(luckRunId);
		if (run == null || view == null) return OfferResult.RUN_NOT_FOUND;
		boolean added = false;
		boolean changed = false;
		for (CroesusRunViewParser.ChestOffer offer : view.chests()) {
			LuckRunRecord.LuckChestRecord chest = chestRecord(run.chests, offer.tier().name(), LuckRunRecord.SOURCE_CROESUS_RUN_VIEW);
			int result = appendOffer(chest, offer, now);
			if (result > 0) added = true;
			if (result > 1) changed = true;
		}
		boolean dirty = added;
		if (run.croesus != null) {
			if (!view.availableModifiers().isEmpty()) {
				dirty |= noteModifiers(run.croesus, view.availableModifiers(), LuckRunRecord.SOURCE_CROESUS_RUN_VIEW, now);
			}
			dirty |= updatePurchases(run, run.croesus.noChestsOpened, run.croesus.openedChests);
		}
		if (!dirty) return OfferResult.UNCHANGED;
		run.coverage = LuckCoverage.evaluate(run);
		if (!save()) return OfferResult.SAVE_FAILED;
		if (!added) return OfferResult.UNCHANGED;
		return changed ? OfferResult.CHANGED_UNVERIFIED : OfferResult.ADDED;
	}

	/** The stored run for a DRT completion's run session id, as a copy; null when none. */
	public synchronized LuckRunRecord runForSession(String drtRunSessionId) {
		LuckRunRecord run = findBySession(drtRunSessionId);
		return run == null ? null : GSON.fromJson(GSON.toJson(run), LuckRunRecord.class);
	}

	/**
	 * Stores one chest previewed or opened in the dungeon instance for the run DRT just completed.
	 * Merges with Croesus observations of the same chest by the same rules as the run view.
	 */
	public synchronized OfferResult recordDungeonChestOffer(String drtRunSessionId, CroesusRunViewParser.ChestOffer offer, long now) {
		if (isReadOnly()) return OfferResult.READ_ONLY;
		LuckRunRecord run = findBySession(drtRunSessionId);
		if (run == null || offer == null || offer.contents().isEmpty()) return OfferResult.RUN_NOT_FOUND;
		LuckRunRecord.LuckChestRecord chest = chestRecord(run.chests, offer.tier().name(), LuckRunRecord.SOURCE_DUNGEON_CHEST);
		int result = appendOffer(chest, offer, now, LuckRunRecord.SOURCE_DUNGEON_CHEST);
		if (result == 0) return OfferResult.UNCHANGED;
		run.coverage = LuckCoverage.evaluate(run);
		if (!save()) return OfferResult.SAVE_FAILED;
		return result > 1 ? OfferResult.CHANGED_UNVERIFIED : OfferResult.ADDED;
	}

	/** Marks a chest opened in the dungeon instance as purchased. Returns whether anything changed. */
	public synchronized boolean recordDungeonPurchase(String drtRunSessionId, ChestTier tier) {
		if (isReadOnly() || tier == null) return false;
		LuckRunRecord run = findBySession(drtRunSessionId);
		if (run == null) return false;
		LuckRunRecord.LuckChestRecord chest = chestRecord(run.chests, tier.name(), LuckRunRecord.SOURCE_DUNGEON_CHEST);
		if (Boolean.TRUE.equals(chest.purchased) && LuckRunRecord.SOURCE_DUNGEON_CHEST.equals(chest.purchaseSource)) return false;
		chest.purchased = true;
		chest.purchaseSource = LuckRunRecord.SOURCE_DUNGEON_CHEST;
		if (!chest.offers.isEmpty()) chest.purchasedOfferIndex = chest.offers.size() - 1;
		run.coverage = LuckCoverage.evaluate(run);
		return save();
	}

	/**
	 * Marks a chest opened from a Croesus run view that was linked to this run, after Hypixel
	 * delivered its rewards. A purchase already proven by the Croesus page or in the instance is
	 * left as it is. Returns whether anything changed.
	 */
	public synchronized boolean recordCroesusPurchase(String drtRunSessionId, ChestTier tier) {
		if (isReadOnly() || tier == null) return false;
		LuckRunRecord run = findBySession(drtRunSessionId);
		if (run == null) return false;
		LuckRunRecord.LuckChestRecord chest = chestRecord(run.chests, tier.name(), LuckRunRecord.SOURCE_CROESUS_RUN_VIEW);
		if (Boolean.TRUE.equals(chest.purchased)) return false;
		chest.purchased = true;
		chest.purchaseSource = LuckRunRecord.SOURCE_CROESUS_CHEST;
		if (!chest.offers.isEmpty()) chest.purchasedOfferIndex = chest.offers.size() - 1;
		run.coverage = LuckCoverage.evaluate(run);
		return save();
	}

	/** Keeps a Croesus observation that matched no run, merging repeats of the same chests. */
	public synchronized boolean recordUnmatchedCroesus(UnmatchedCroesusRun observation) {
		if (isReadOnly() || observation == null || observation.fingerprint.isBlank()) return false;
		for (UnmatchedCroesusRun existing : data.unmatchedCroesus) {
			if (!existing.fingerprint.equals(observation.fingerprint)) continue;
			existing.lastObservedAtEpochMillis = observation.lastObservedAtEpochMillis;
			if (observation.entryName != null || existing.entryName == null) existing.reason = observation.reason;
			if (existing.entryName == null && observation.entryName != null) {
				existing.entryName = observation.entryName;
				existing.players = observation.players;
				existing.completedText = observation.completedText;
				existing.completedAfterEpochMillis = observation.completedAfterEpochMillis;
				existing.completedBeforeEpochMillis = observation.completedBeforeEpochMillis;
				existing.floor = observation.floor;
				existing.masterMode = observation.masterMode;
			}
			return save();
		}
		data.unmatchedCroesus.add(observation);
		while (data.unmatchedCroesus.size() > MAX_UNMATCHED_CROESUS) data.unmatchedCroesus.remove(0);
		return save();
	}

	/** Replaces the stored requirements for a floor with the latest floor screen. */
	public synchronized boolean recordMeterRequirements(String floor, List<LuckMeterRequirement> items) {
		if (isReadOnly() || floor == null || items == null || items.isEmpty()) return false;
		data.meterRequirements.removeIf(requirement -> requirement.floor.equals(floor));
		data.meterRequirements.addAll(items);
		return save();
	}

	/**
	 * Stores a meter reading and, when it is the first reading after a captured run on that floor,
	 * attaches it as the run's after-run state.
	 */
	public synchronized List<MeterAttachment> recordMeterReading(LuckMeterReading reading) {
		if (isReadOnly() || reading == null || reading.floor.isBlank()) return List.of();
		reading.normalize();
		LuckMeterRequirement requirement = reading.selectedItem == null ? null : requirement(reading.floor, reading.selectedItem);
		if (requirement != null) {
			if (reading.requiredScore == null && requirement.requiredScore != null) {
				reading.requiredScore = requirement.requiredScore;
				reading.requiredSource = LuckMeterReading.SOURCE_FLOOR_MENU;
			}
			if (reading.guaranteeChest == null) reading.guaranteeChest = requirement.appearsIn;
			if (reading.selectedKey == null) reading.selectedKey = requirement.itemKey;
		}

		LuckMeterReading latest = latestReading(reading.floor);
		long lastRun = latestCompletion(reading.floor, Long.MAX_VALUE);
		if (latest != null && latest.sameState(reading) && lastRun < latest.observedAtEpochMillis) {
			latest.lastObservedAtEpochMillis = Math.max(latest.lastObservedAtEpochMillis, reading.observedAtEpochMillis);
			if (latest.requiredScore == null && reading.requiredScore != null) {
				latest.requiredScore = reading.requiredScore;
				latest.requiredSource = reading.requiredSource;
			}
			save();
			return List.of();
		}
		data.meterReadings.add(reading);
		while (data.meterReadings.size() > MAX_METER_READINGS) data.meterReadings.remove(0);

		List<MeterAttachment> attachments = new ArrayList<>();
		LuckRunRecord run = latestRunBefore(reading.floor, reading.observedAtEpochMillis);
		if (run != null && snapshotFor(run, LuckRunRecord.PHASE_AFTER_RUN) == null) {
			LuckRunRecord.LuckMeterSnapshot after = snapshot(reading, LuckRunRecord.PHASE_AFTER_RUN);
			run.meterSnapshots.add(after);
			run.coverage = LuckCoverage.evaluate(run);
			attachments.add(new MeterAttachment(run.luckRunId, run.floor, snapshotFor(run, LuckRunRecord.PHASE_BEFORE_RUN), after));
		}
		save();
		return attachments;
	}

	private LuckRunRecord find(String luckRunId) {
		if (luckRunId == null) return null;
		for (LuckRunRecord run : data.runs) if (luckRunId.equals(run.luckRunId)) return run;
		return null;
	}

	private LuckRunRecord findBySession(String drtRunSessionId) {
		if (drtRunSessionId == null || drtRunSessionId.isBlank()) return null;
		for (LuckRunRecord run : data.runs) if (drtRunSessionId.equals(run.drtRunSessionId)) return run;
		return null;
	}

	private LuckMeterReading latestReading(String floor) {
		for (int i = data.meterReadings.size() - 1; i >= 0; i--) {
			if (data.meterReadings.get(i).floor.equals(floor)) return data.meterReadings.get(i);
		}
		return null;
	}

	/** Latest captured completion on a floor strictly before {@code before}, or {@link Long#MIN_VALUE}. */
	private long latestCompletion(String floor, long before) {
		long latest = Long.MIN_VALUE;
		for (LuckRunRecord run : data.runs) {
			if (run.floor.equalsIgnoreCase(floor) && run.completedAtEpochMillis < before) latest = Math.max(latest, run.completedAtEpochMillis);
		}
		return latest;
	}

	private LuckRunRecord latestRunBefore(String floor, long time) {
		LuckRunRecord latest = null;
		for (LuckRunRecord run : data.runs) {
			if (!run.floor.equalsIgnoreCase(floor) || run.completedAtEpochMillis >= time) continue;
			if (latest == null || run.completedAtEpochMillis > latest.completedAtEpochMillis) latest = run;
		}
		return latest;
	}

	/** The newest reading taken after the floor's previous captured run and before this one. */
	private LuckMeterReading readingBefore(LuckRunRecord run) {
		long previous = latestCompletion(run.floor, run.completedAtEpochMillis);
		for (int i = data.meterReadings.size() - 1; i >= 0; i--) {
			LuckMeterReading reading = data.meterReadings.get(i);
			if (!reading.floor.equalsIgnoreCase(run.floor)) continue;
			if (reading.lastObservedAtEpochMillis >= run.completedAtEpochMillis) continue;
			return reading.observedAtEpochMillis > previous ? reading : null;
		}
		return null;
	}

	private static LuckRunRecord.LuckMeterSnapshot snapshotFor(LuckRunRecord run, String phase) {
		for (LuckRunRecord.LuckMeterSnapshot snapshot : run.meterSnapshots) {
			if (phase.equals(snapshot.phase) && run.floor.equalsIgnoreCase(snapshot.floor)) return snapshot;
		}
		return null;
	}

	private static LuckRunRecord.LuckMeterSnapshot snapshot(LuckMeterReading reading, String phase) {
		LuckRunRecord.LuckMeterSnapshot snapshot = new LuckRunRecord.LuckMeterSnapshot();
		snapshot.floor = reading.floor;
		snapshot.phase = phase;
		snapshot.state = reading.storedScore == null ? LuckCoverage.MeterStatus.UNKNOWN.name() : LuckCoverage.MeterStatus.OBSERVED.name();
		snapshot.selectedItem = reading.selectedItem;
		snapshot.selectedKey = reading.selectedKey;
		snapshot.storedScore = reading.storedScore;
		snapshot.storedText = reading.storedText;
		snapshot.requiredScore = reading.requiredScore;
		snapshot.requiredText = reading.requiredText;
		snapshot.guaranteeChest = reading.guaranteeChest;
		snapshot.source = reading.source;
		snapshot.observedAtEpochMillis = reading.observedAtEpochMillis;
		return snapshot;
	}

	private static LuckRunRecord.LuckChestRecord chestRecord(List<LuckRunRecord.LuckChestRecord> chests, String tier, String source) {
		for (LuckRunRecord.LuckChestRecord chest : chests) if (tier.equals(chest.tier)) return chest;
		LuckRunRecord.LuckChestRecord chest = new LuckRunRecord.LuckChestRecord();
		chest.tier = tier;
		chest.source = source;
		chests.add(chest);
		return chest;
	}

	/** 0 when unchanged, 1 for a first offer, 2 for different contents after an earlier offer. */
	static int appendOffer(LuckRunRecord.LuckChestRecord chest, CroesusRunViewParser.ChestOffer offer, long now) {
		return appendOffer(chest, offer, now, LuckRunRecord.SOURCE_CROESUS_RUN_VIEW);
	}

	static int appendOffer(LuckRunRecord.LuckChestRecord chest, CroesusRunViewParser.ChestOffer offer, long now, String source) {
		if (offer.costCoins() != null) chest.costCoins = offer.costCoins();
		if (offer.costText() != null) chest.costText = offer.costText();
		List<String> texts = offer.contentTexts();
		LuckRunRecord.LuckOfferSnapshot last = chest.offers.isEmpty() ? null : chest.offers.get(chest.offers.size() - 1);
		if (last != null && contentTexts(last).equals(texts)) return 0;
		if (last != null && !Objects.equals(last.source, source) && sameResolvedContents(last, offer)) return 0;
		LuckRunRecord.LuckOfferSnapshot snapshot = new LuckRunRecord.LuckOfferSnapshot();
		snapshot.index = chest.offers.size();
		snapshot.observedAtEpochMillis = now;
		snapshot.changedFromPrevious = last != null;
		snapshot.source = source;
		for (LuckText.ContentLine line : offer.contents()) {
			LuckRunRecord.LuckContentLine content = new LuckRunRecord.LuckContentLine();
			content.text = line.text();
			content.name = line.name();
			content.quantity = line.quantity();
			content.kind = line.kind() == null ? null : line.kind().name();
			content.key = line.key();
			snapshot.contents.add(content);
			if (line.key() != null) snapshot.itemKeys.add(line.key());
		}
		snapshot.rawLines.addAll(offer.rawLines());
		chest.offers.add(snapshot);
		return last == null ? 1 : 2;
	}

	/**
	 * Chest screens and the Croesus run view word the same contents differently, so an offer seen
	 * in both is compared by resolved item keys and quantities. Any unresolved line on either side
	 * leaves the texts to decide.
	 */
	private static boolean sameResolvedContents(LuckRunRecord.LuckOfferSnapshot last, CroesusRunViewParser.ChestOffer offer) {
		if (last.contents.isEmpty() || offer.contents().isEmpty()) return false;
		Map<String, Integer> before = new HashMap<>();
		for (LuckRunRecord.LuckContentLine line : last.contents) {
			if (line.key == null) return false;
			before.merge(line.key, Math.max(1, line.quantity), Integer::sum);
		}
		Map<String, Integer> after = new HashMap<>();
		for (LuckText.ContentLine line : offer.contents()) {
			if (line.key() == null) return false;
			after.merge(line.key(), Math.max(1, line.quantity()), Integer::sum);
		}
		return before.equals(after);
	}

	private static List<String> contentTexts(LuckRunRecord.LuckOfferSnapshot offer) {
		List<String> out = new ArrayList<>(offer.contents.size());
		for (LuckRunRecord.LuckContentLine line : offer.contents) out.add(line.text);
		return out;
	}

	private static boolean updateLink(LuckRunRecord run, CroesusMenuParser.RunEntry entry, String method, long now) {
		boolean changed = false;
		LuckRunRecord.LuckCroesusLink link = run.croesus;
		if (link == null) {
			link = new LuckRunRecord.LuckCroesusLink();
			link.firstMatchedAtEpochMillis = now;
			link.method = method;
			run.croesus = link;
			changed = true;
		}
		link.lastSeenAtEpochMillis = now;
		if (!entry.name().equals(link.entryName)) {
			link.entryName = entry.name();
			changed = true;
		}
		if (!entry.players().equals(link.players)) {
			link.players = new ArrayList<>(entry.players());
			changed = true;
		}
		if (!Objects.equals(entry.completedText(), link.completedText) || !Objects.equals(entry.expiresText(), link.expiresText)) {
			link.completedText = entry.completedText();
			link.expiresText = entry.expiresText();
			changed = true;
		}
		if (entry.noChestsOpened() != link.noChestsOpened || !entry.openedChests().equals(link.openedChests)) {
			link.noChestsOpened = entry.noChestsOpened();
			link.openedChests = new ArrayList<>(entry.openedChests());
			changed = true;
		}
		changed |= noteModifiers(link, entry.availableModifiers(), LuckRunRecord.SOURCE_CROESUS_MENU, now);
		return changed;
	}

	private static boolean noteModifiers(LuckRunRecord.LuckCroesusLink link, List<String> modifiers, String source, long now) {
		List<LuckRunRecord.LuckModifierObservation> history = link.modifierHistory;
		LuckRunRecord.LuckModifierObservation last = history.isEmpty() ? null : history.get(history.size() - 1);
		link.availableModifiers = new ArrayList<>(modifiers);
		if (last != null && last.modifiers.equals(modifiers)) return false;
		LuckRunRecord.LuckModifierObservation observation = new LuckRunRecord.LuckModifierObservation();
		observation.observedAtEpochMillis = now;
		observation.source = source;
		observation.modifiers = new ArrayList<>(modifiers);
		history.add(observation);
		return true;
	}

	/** Marks chests opened or unopened from the entry's "Opened Chest:" line. */
	private static boolean updatePurchases(LuckRunRecord run, boolean noChestsOpened, List<String> openedChests) {
		List<String> opened = new ArrayList<>();
		for (String name : openedChests) {
			ChestTier tier = LuckText.chestTier(name);
			if (tier == null) return false;
			opened.add(tier.name());
		}
		if (!noChestsOpened && opened.isEmpty()) return false;
		boolean changed = false;
		for (String tier : opened) {
			LuckRunRecord.LuckChestRecord chest = chestRecord(run.chests, tier, LuckRunRecord.SOURCE_CROESUS_MENU);
			if (!Boolean.TRUE.equals(chest.purchased)) {
				chest.purchased = true;
				chest.purchaseSource = LuckRunRecord.SOURCE_CROESUS_MENU;
				if (!chest.offers.isEmpty()) chest.purchasedOfferIndex = chest.offers.size() - 1;
				changed = true;
			}
		}
		for (LuckRunRecord.LuckChestRecord chest : run.chests) {
			if (opened.contains(chest.tier) || Boolean.FALSE.equals(chest.purchased)) continue;
			if (Boolean.TRUE.equals(chest.purchased)) continue;
			chest.purchased = false;
			changed = true;
		}
		return changed;
	}

	private boolean save() {
		try {
			AtomicJsonFileStore.writeCrashSafe(path, GSON, data, false);
			revision++;
			lastSaveError = null;
			if (loadState == LoadState.NEW) loadState = LoadState.LOADED;
			return true;
		} catch (IOException e) {
			lastSaveError = e;
			return false;
		}
	}

	private LoadState recoverFromMalformed() {
		data = new FileData();
		try {
			Path target = path.resolveSibling(path.getFileName() + ".malformed-"
				+ DateTimeFormatter.ISO_INSTANT.format(Instant.now()).replace(':', '-'));
			Files.copy(path, target, StandardCopyOption.REPLACE_EXISTING);
			preservedMalformedFile = target;
			loadState = LoadState.RECOVERED_FROM_MALFORMED;
		} catch (IOException e) {
			loadState = LoadState.UNREADABLE_READ_ONLY;
		}
		return loadState;
	}

	private static FileData migrate(FileData parsed) {
		parsed.schema = SCHEMA;
		parsed.schemaVersion = SCHEMA_VERSION;
		if (parsed.profile == null) parsed.profile = LuckModifierProfile.unknown();
		parsed.profile.normalize();
		if (parsed.runs == null) parsed.runs = new ArrayList<>();
		parsed.runs.removeIf(Objects::isNull);
		for (LuckRunRecord run : parsed.runs) {
			run.normalize();
			run.coverage = LuckCoverage.evaluate(run);
		}
		if (parsed.meterReadings == null) parsed.meterReadings = new ArrayList<>();
		parsed.meterReadings.removeIf(Objects::isNull);
		parsed.meterReadings.forEach(LuckMeterReading::normalize);
		if (parsed.meterRequirements == null) parsed.meterRequirements = new ArrayList<>();
		parsed.meterRequirements.removeIf(requirement -> requirement == null || requirement.floor == null || requirement.itemName == null);
		if (parsed.unmatchedCroesus == null) parsed.unmatchedCroesus = new ArrayList<>();
		parsed.unmatchedCroesus.removeIf(observation -> observation == null || observation.fingerprint == null);
		return parsed;
	}
}
