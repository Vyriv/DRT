package dev.vy.drt.client.luck;

import dev.vy.drt.DungeonRunTracker;
import dev.vy.drt.client.DrtClient;
import dev.vy.drt.client.tracker.DungeonRunTrackerFeature;
import dev.vy.drt.client.tracker.TabReader;
import dev.vy.drt.config.DungeonLootEntry;
import dev.vy.drt.config.DungeonRunCompletionRecord;
import dev.vy.drt.luck.ChestTier;
import dev.vy.drt.luck.FloorId;
import dev.vy.drt.luck.capture.CroesusRunViewParser;
import dev.vy.drt.luck.capture.DungeonPartyRoster;
import dev.vy.drt.luck.capture.LiveRewardSession;
import dev.vy.drt.luck.capture.LuckText;
import dev.vy.drt.luck.data.CatacombsLootDatabase;
import dev.vy.drt.luck.data.FloorTable;
import dev.vy.drt.luck.capture.DungeonScoreReading;
import dev.vy.drt.luck.capture.LuckModifierProfile;
import dev.vy.drt.luck.capture.LuckRunRecord;
import dev.vy.drt.luck.capture.LuckTrackingStore;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;

public final class LuckRunCapture {
	private static LuckTrackingStore store;

	private LuckRunCapture() {
	}

	public static Path storePath() {
		return FabricLoader.getInstance().getConfigDir().resolve("drt").resolve("luck").resolve("drt-luck.json");
	}

	public static synchronized LuckTrackingStore store() {
		if (store == null) {
			store = new LuckTrackingStore(storePath());
			LuckTrackingStore.LoadState state = store.load();
			switch (state) {
				case RECOVERED_FROM_MALFORMED -> DungeonRunTracker.LOGGER.warn(
					"[DRT] Luck data file was malformed; preserved it as {} and started fresh", store.preservedMalformedFile());
				case NEWER_SCHEMA_READ_ONLY -> DungeonRunTracker.LOGGER.warn(
					"[DRT] Luck data file was written by a newer DRT; luck capture is read-only this session");
				case UNREADABLE_READ_ONLY -> DungeonRunTracker.LOGGER.warn(
					"[DRT] Luck data file could not be read or preserved; luck capture is read-only this session");
				default -> {
				}
			}
		}
		return store;
	}

	public static LuckModifierProfile profile() {
		return store().profile();
	}

	public static boolean updateProfile(LuckModifierProfile values) {
		try {
			return store().updateProfile(values, System.currentTimeMillis());
		} catch (RuntimeException e) {
			DungeonRunTracker.LOGGER.warn("[DRT] Could not save Luck Rating modifiers", e);
			return false;
		}
	}

	/** Called after DRT has committed a new completion record. Never throws into the tracker. */
	public static void onCompletionRecorded(DungeonRunCompletionRecord completion, DungeonScoreReading scoreReading) {
		if (completion == null || !"DUNGEON".equals(completion.mode)) return;
		try {
			LuckRunRecord run = LuckRunRecord.forCompletion(
				completion.runSessionId,
				completion.completionFingerprint,
				completion.completedAtEpochMillis,
				completion.floor,
				completion.grade,
				scoreReading
			);
			run.party = readParty();
			if (run.party != null) run.partySource = LuckRunRecord.PARTY_SOURCE_TAB_LIST;
			String timeSource = completion.effectiveRunTimeSource();
			if (timeSource != null) {
				run.durationMillis = completion.runTimeMs;
				run.durationSource = timeSource;
			}
			LuckTrackingStore.AddResult result = store().addRun(run);
			if (result == LuckTrackingStore.AddResult.SAVE_FAILED) {
				DungeonRunTracker.LOGGER.warn("[DRT] Could not save luck capture for {}", completion.completionFingerprint, store().lastSaveError());
			} else {
				DungeonRunTracker.LOGGER.info("[DRT] Luck capture {}: floor={} grade={} score={} party={}",
					result, run.floor, run.grade, run.score == null ? "unknown" : run.score,
					run.party == null ? "unread" : run.party.size());
			}
		} catch (RuntimeException e) {
			DungeonRunTracker.LOGGER.warn("[DRT] Luck capture failed for {}", completion.completionFingerprint, e);
		}
	}

	/**
	 * One reward chest screen seen in the dungeon instance for the run DRT just completed. Only what
	 * the screen showed is stored; an empty or unreadable screen stores nothing.
	 */
	public static void onDungeonChestOffer(String runSessionId, String floor, String chestTitle, List<DungeonLootEntry> entries, Long costCoins) {
		ChestTier tier = LuckText.chestTier(chestTitle);
		if (tier == null || entries == null || entries.isEmpty() || runSessionId == null || runSessionId.isBlank()) return;
		try {
			FloorId floorId = FloorId.parse(floor);
			CatacombsLootDatabase database = LuckScreenCapture.database();
			FloorTable table = floorId == null || database == null ? null : database.floor(floorId);
			List<LuckText.ContentLine> contents = new ArrayList<>();
			List<String> raw = new ArrayList<>();
			for (DungeonLootEntry entry : entries) {
				String text = contentText(entry);
				if (text.isBlank()) continue;
				contents.add(LuckText.contentLine(text, table));
				raw.add(text);
			}
			if (contents.isEmpty()) return;
			String costText = costCoins == null ? null : costCoins == 0L ? "FREE" : String.format(Locale.ROOT, "%,d Coins", costCoins);
			CroesusRunViewParser.ChestOffer offer = new CroesusRunViewParser.ChestOffer(tier, -1, contents, costCoins, costText, raw);
			LuckTrackingStore.OfferResult result = store().recordDungeonChestOffer(runSessionId, offer, System.currentTimeMillis());
			if (result != LuckTrackingStore.OfferResult.UNCHANGED) {
				DungeonRunTracker.LOGGER.info("[DRT] Dungeon chest offer {} for {}: {} ({} lines)", result, runSessionId, tier, contents.size());
			}
		} catch (RuntimeException e) {
			DungeonRunTracker.LOGGER.warn("[DRT] Could not store dungeon chest offer for {}", runSessionId, e);
		}
	}

	/** A chest opened in the dungeon instance for the run DRT just completed. */
	public static void onDungeonChestPurchased(String runSessionId, String chestTitle) {
		ChestTier tier = LuckText.chestTier(chestTitle);
		if (tier == null || runSessionId == null || runSessionId.isBlank()) return;
		try {
			store().recordDungeonPurchase(runSessionId, tier);
		} catch (RuntimeException e) {
			DungeonRunTracker.LOGGER.warn("[DRT] Could not store dungeon chest purchase for {}", runSessionId, e);
		}
	}

	/** A chest opened from a Croesus run view linked to this run, once its rewards were received. */
	public static void onCroesusChestPurchased(String runSessionId, String chestTitle) {
		ChestTier tier = LuckText.chestTier(chestTitle);
		if (tier == null || runSessionId == null || runSessionId.isBlank()) return;
		try {
			if (store().recordCroesusPurchase(runSessionId, tier)) {
				DungeonRunTracker.LOGGER.info("[DRT] Croesus chest purchase recorded for {}: {}", runSessionId, tier);
			}
		} catch (RuntimeException e) {
			DungeonRunTracker.LOGGER.warn("[DRT] Could not store Croesus chest purchase for {}", runSessionId, e);
		}
	}

	/** The run just completed in the dungeon instance the player is still in; null otherwise. */
	public static LiveRewardSession liveRewardSession() {
		try {
			DungeonRunTrackerFeature tracker = DrtClient.getTracker();
			return tracker == null ? null : tracker.liveRewardSession();
		} catch (RuntimeException e) {
			return null;
		}
	}

	/** The completed run whose Croesus run view is open, when its entry matched exactly one run. */
	public static String activeCroesusRunSession() {
		try {
			return LuckScreenCapture.activeViewRunSession(System.currentTimeMillis());
		} catch (RuntimeException e) {
			return null;
		}
	}

	/** Content text in the Croesus "Contents" wording, so both sources resolve the same keys. */
	static String contentText(DungeonLootEntry entry) {
		String name = entry.rawName == null ? "" : entry.rawName.trim();
		if (name.isEmpty()) return "";
		String id = entry.itemId == null ? "" : entry.itemId;
		if (id.startsWith("ENCHANTMENT_") && !name.toUpperCase(Locale.ROOT).startsWith("ENCHANTED BOOK")) {
			name = "Enchanted Book (" + name + ")";
		}
		int quantity = Math.max(1, entry.quantity);
		return quantity > 1 ? name + " x" + quantity : name;
	}

	private static List<String> readParty() {
		Minecraft client = Minecraft.getInstance();
		if (client == null || client.player == null) return null;
		List<String> lines = new ArrayList<>();
		for (String raw : TabReader.readRawLines(client)) {
			String stripped = ChatFormatting.stripFormatting(raw);
			if (stripped != null) lines.add(stripped.trim());
		}
		return DungeonPartyRoster.fromTabLines(lines, client.player.getGameProfile().name());
	}
}
