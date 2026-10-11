package dev.vy.drt.client.luck;

import dev.vy.drt.DungeonRunTracker;
import dev.vy.drt.client.tracker.TabReader;
import dev.vy.drt.config.DungeonRunCompletionRecord;
import dev.vy.drt.luck.capture.DungeonPartyRoster;
import dev.vy.drt.luck.capture.DungeonScoreReading;
import dev.vy.drt.luck.capture.LuckModifierProfile;
import dev.vy.drt.luck.capture.LuckRunRecord;
import dev.vy.drt.luck.capture.LuckTrackingStore;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
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
