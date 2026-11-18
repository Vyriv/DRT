package dev.vy.drt.client.sync;

import com.google.gson.Gson;
import com.google.gson.JsonParseException;
import dev.vy.drt.DungeonRunTracker;
import dev.vy.drt.config.AtomicJsonFileStore;
import dev.vy.drt.sync.StatsSyncResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import net.fabricmc.loader.api.FabricLoader;

/**
 * config/drt/sync.json: this installation's upload tokens and which record revisions each account
 * already uploaded. Kept apart from drt.json so history files never carry tokens.
 */
final class DrtSyncState {
	private static final Path PATH = FabricLoader.getInstance().getConfigDir().resolve("drt").resolve("sync.json");
	private static final Gson GSON = new Gson();
	private static Data data;

	private static final class Data {
		Map<String, String> tokens = new LinkedHashMap<>();
		Map<String, Map<String, String>> uploaded = new LinkedHashMap<>();
		/** Revisions the server rejected or has tombstoned. They are retried only if local data changes. */
		Map<String, Map<String, String>> rejected = new LinkedHashMap<>();
		/** Accounts whose older, account-safe DRT history the player explicitly chose to import. */
		Map<String, Boolean> historicalImports = new LinkedHashMap<>();
		/** The one account that claimed records saved before DRT stamped accounts on them. */
		String unstampedOwner;
		/** The player said those records are not all from one account, so they stay local. */
		boolean unstampedDeclined;
		long lastSyncAtMillis;
	}

	private DrtSyncState() {
	}

	static synchronized String token(String uuid) {
		return data().tokens.containsKey(uuid) ? uuid : null;
	}

	static synchronized void setToken(String uuid, String token) {
		data().tokens.put(uuid, uuid);
		save();
	}

	/** Drops the token after the account's uploads were deleted; uploaded ids stay so they are not sent again. */
	static synchronized void forgetUploads(String uuid) {
		data().tokens.remove(uuid);
		save();
	}

	static synchronized Map<String, String> uploaded(String uuid) {
		return new LinkedHashMap<>(data().uploaded.getOrDefault(uuid, Map.of()));
	}

	static synchronized Map<String, String> handled(String uuid) {
		Map<String, String> handled = uploaded(uuid);
		handled.putAll(data().rejected.getOrDefault(uuid, Map.of()));
		return handled;
	}

	static synchronized void markResult(String uuid, Map<String, String> revisions, StatsSyncResponse response) {
		Map<String, String> uploaded = data().uploaded.computeIfAbsent(uuid, key -> new LinkedHashMap<>());
		Map<String, String> rejected = data().rejected.computeIfAbsent(uuid, key -> new LinkedHashMap<>());
		for (Map.Entry<String, String> entry : revisions.entrySet()) {
			boolean refused = response.rejectedIds().contains(entry.getKey()) || response.deletedIds().contains(entry.getKey());
			if (refused) {
				uploaded.remove(entry.getKey());
				rejected.put(entry.getKey(), entry.getValue());
			} else {
				rejected.remove(entry.getKey());
				uploaded.put(entry.getKey(), entry.getValue());
			}
		}
		data().lastSyncAtMillis = System.currentTimeMillis();
		save();
	}

	/** The server deleted these retracted records. */
	static synchronized void markRetracted(String uuid, Collection<String> ids) {
		Map<String, String> uploaded = data().uploaded.get(uuid);
		if (uploaded == null || ids.isEmpty()) return;
		ids.forEach(uploaded::remove);
		save();
	}

	static synchronized boolean historicalImportEnabled(String uuid) {
		return uuid != null && Boolean.TRUE.equals(data().historicalImports.get(uuid));
	}

	static synchronized void enableHistoricalImport(String uuid) {
		if (uuid == null || uuid.isBlank()) return;
		data().historicalImports.put(uuid, true);
		save();
	}

	static synchronized boolean ownsUnstamped(String uuid) {
		return uuid != null && uuid.equals(data().unstampedOwner);
	}

	/** Whether the player already answered the one-time question about records saved without an account. */
	static synchronized boolean unstampedDecided() {
		return data().unstampedOwner != null || data().unstampedDeclined;
	}

	static synchronized void claimUnstamped(String uuid) {
		if (data().unstampedOwner != null) return;
		data().unstampedOwner = uuid;
		save();
	}

	static synchronized void declineUnstamped() {
		data().unstampedDeclined = true;
		save();
	}

	static synchronized boolean holdsToken(String uuid) {
		return uuid != null && data().tokens.containsKey(uuid);
	}

	private static Data data() {
		if (data == null) data = load();
		return data;
	}

	private static Data load() {
		if (!Files.exists(PATH)) return new Data();
		try {
			Data loaded = GSON.fromJson(Files.readString(PATH, StandardCharsets.UTF_8), Data.class);
			if (loaded == null) return new Data();
			if (loaded.tokens == null) loaded.tokens = new LinkedHashMap<>();
			if (loaded.uploaded == null) loaded.uploaded = new LinkedHashMap<>();
			if (loaded.rejected == null) loaded.rejected = new LinkedHashMap<>();
			if (loaded.historicalImports == null) loaded.historicalImports = new LinkedHashMap<>();
			return loaded;
		} catch (IOException | JsonParseException e) {
			DungeonRunTracker.LOGGER.warn("[DRT] Could not read stats sync state, starting fresh", e);
			return new Data();
		}
	}

	private static void save() {
		try {
			AtomicJsonFileStore.writeCrashSafe(PATH, GSON, data, false);
		} catch (IOException e) {
			DungeonRunTracker.LOGGER.error("[DRT] Failed to save stats sync state", e);
		}
	}

	static synchronized long lastSyncAtMillis() {
		return data().lastSyncAtMillis;
	}
}
