package dev.vy.drt.client.sync;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.vy.drt.DungeonRunTracker;
import dev.vy.drt.client.screen.DrtLegacyRecordsScreen;
import dev.vy.drt.config.DrtConfig;
import dev.vy.drt.config.DrtConfigManager;
import dev.vy.drt.sync.StatsSyncSchedule;
import dev.vy.drt.sync.StatsSyncResponse;
import dev.vy.drt.sync.StatsUploadPlanner;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalLong;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.CompletableFuture;
import net.minecraft.client.Minecraft;

/**
 * Background stats sharing, on by default. While it is on, new DRT-recorded runs and chests for
 * the logged-in account are sent in batches. Older history stays local until the player explicitly
 * imports it. Every server result is remembered by revision and never resent unless it changes.
 * Records the player made or edited are never sent, and uploaded records later edited or deleted
 * are retracted. A revoked installation turns sharing off instead of silently registering again.
 */
public final class DrtStatsSync {
	public static final String PRIVACY_URL = "https://vyriv.dev/privacy#drt";
	private static final URI REGISTER_URI = URI.create(DrtApi.BASE_URL + "/v1/drt/stats/register");
	private static final URI SYNC_URI = URI.create(DrtApi.BASE_URL + "/v1/drt/stats/sync");
	private static final URI ME_URI = URI.create(DrtApi.BASE_URL + "/v1/drt/stats/me");
	private static final ExecutorService WORKER = Executors.newSingleThreadExecutor(runnable -> {
		Thread thread = new Thread(runnable, "DRT stats sync");
		thread.setDaemon(true);
		return thread;
	});

	private static volatile boolean busy;
	private static volatile long nextAttemptAtMillis;
	private static volatile String lastUuid;
	private static int failures;
	private static volatile String lastStatus = "Waiting for new records.";

	private DrtStatsSync() {
	}

	public static void tick(Minecraft client) {
		DrtConfig config = DrtConfigManager.getConfig();
		if (!config.statsSyncEnabled || busy) return;
		String uuid = DrtAccount.currentUuid();
		if (uuid == null) return;
		if (!uuid.equals(lastUuid)) {
			lastUuid = uuid;
			nextAttemptAtMillis = 0L;
		}
		if (System.currentTimeMillis() < nextAttemptAtMillis) return;
		long since = config.statsSyncSinceEpochMillis;
		busy = true;
		WORKER.execute(() -> {
			long delay;
			try {
				delay = syncOnce(client, uuid, since);
			} catch (IOException | RuntimeException e) {
				DungeonRunTracker.LOGGER.warn("[DRT] Stats sync failed: {}", e.toString());
				lastStatus = "Could not reach api.vyriv.dev. Retrying automatically.";
				delay = retryDelay();
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
				lastStatus = "Statistics sync was interrupted. Retrying automatically.";
				delay = retryDelay();
			}
			nextAttemptAtMillis = System.currentTimeMillis() + delay;
			busy = false;
		});
	}

	/**
	 * The Statistics Sharing setting. Turning it on starts a new boundary. Older records remain
	 * local until the player explicitly imports them.
	 */
	public static void setEnabled(boolean enabled) {
		DrtConfig config = DrtConfigManager.getConfig();
		if (config.statsSyncEnabled == enabled) return;
		config.statsSyncEnabled = enabled;
		if (enabled) config.statsSyncSinceEpochMillis = System.currentTimeMillis();
		DrtConfigManager.save();
		if (enabled) nextAttemptAtMillis = 0L;
	}

	static StatsUploadPlanner.Scope scope(String uuid, long since) {
		return new StatsUploadPlanner.Scope(uuid, since, DrtSyncState.ownsUnstamped(uuid), DrtSyncState.historicalImportEnabled(uuid));
	}

	static List<StatsUploadPlanner.Upload> pending(String uuid, long since) {
		return StatsUploadPlanner.pending(scope(uuid, since), DrtConfigManager.getRunCompletions(),
			DrtConfigManager.getRunHistory(), DrtSyncState.handled(uuid));
	}

	/** One upload step; returns how long to wait before the next one. */
	private static long syncOnce(Minecraft client, String uuid, long since) throws IOException, InterruptedException {
		List<StatsUploadPlanner.Upload> pending = pending(uuid, since);
		List<String> retract = StatsUploadPlanner.retractions(DrtConfigManager.getRunCompletions(),
			DrtConfigManager.getRunHistory(), DrtSyncState.uploaded(uuid));
		if (pending.isEmpty() && retract.isEmpty()) {
			failures = 0;
			lastStatus = DrtSyncState.historicalImportEnabled(uuid) ? "Statistics and selected history are up to date." : "Statistics are up to date.";
			return StatsSyncSchedule.SCAN_INTERVAL_MS;
		}
		String token = DrtSyncState.token(uuid);
		if (token == null) {
			token = register(uuid);
			if (token == null) return retryDelay();
		}

		JsonArray runs = new JsonArray();
		JsonArray chests = new JsonArray();
		Map<String, String> sent = new LinkedHashMap<>();
		for (StatsUploadPlanner.Upload upload : pending) {
			JsonArray target = upload.run() ? runs : chests;
			if (target.size() >= StatsSyncSchedule.BATCH_SIZE) continue;
			target.add(upload.json());
			sent.put(upload.id(), upload.revision());
		}
		List<String> retractBatch = retract.subList(0, Math.min(retract.size(), StatsSyncSchedule.BATCH_SIZE));
		JsonObject body = new JsonObject();
		body.add("runs", runs);
		body.add("chests", chests);
		if (!retractBatch.isEmpty()) {
			JsonArray ids = new JsonArray();
			retractBatch.forEach(ids::add);
			body.add("retract", ids);
		}
		HttpResponse<String> response = DrtApi.HTTP.send(DrtApi.post(SYNC_URI, body.toString(), token), HttpResponse.BodyHandlers.ofString());
		return switch (response.statusCode()) {
			case 200 -> {
				StatsSyncResponse result = StatsSyncResponse.parse(response.body());
				if (result == null || (!retractBatch.isEmpty() && !result.hasRetractionResult())) {
					lastStatus = "The statistics server returned an invalid acknowledgement. Retrying automatically.";
					yield retryDelay();
				}
				DrtSyncState.markResult(uuid, sent, result);
				boolean retracted = result.hasRetractionResult();
				if (retracted) DrtSyncState.markRetracted(uuid, retractBatch);
				failures = 0;
				int refused = result.rejectedIds().size() + result.deletedIds().size();
				int remaining = Math.max(0, pending.size() - sent.size()) + Math.max(0, retract.size() - retractBatch.size());
				lastStatus = refused > 0
					? refused + " record" + (refused == 1 ? " was" : "s were") + " rejected or already deleted. They will retry only if their local data changes."
					: remaining > 0
						? "Last batch synced successfully. " + remaining + " record" + (remaining == 1 ? " remains" : "s remain") + " queued."
						: DrtSyncState.historicalImportEnabled(uuid) ? "Statistics and selected history are up to date." : "Statistics are up to date.";
				yield StatsSyncSchedule.afterSuccess(remaining > 0);
			}
			case 401 -> {
                dev.vy.drt.client.sync.auth.DrtSessionAuth.invalidate();
                lastStatus = "Account authentication expired. Retrying automatically.";
                yield retryDelay();
			}
			case 429 -> {
				OptionalLong retryAfter = response.headers().firstValueAsLong("Retry-After");
				lastStatus = "The statistics server rate limited this installation. Retrying automatically.";
				yield StatsSyncSchedule.afterRateLimit(retryAfter.isPresent() ? retryAfter.getAsLong() : null);
			}
			default -> {
				lastStatus = "Statistics sync failed with HTTP " + response.statusCode() + ". Retrying automatically.";
				yield retryDelay();
			}
		};
	}

	private static String register(String uuid) throws IOException, InterruptedException {
		JsonObject body = new JsonObject();
		body.addProperty("uuid", uuid);
		HttpResponse<String> response = DrtApi.HTTP.send(DrtApi.post(REGISTER_URI, body.toString(), null), HttpResponse.BodyHandlers.ofString());
		if (response.statusCode() != 200) {
			lastStatus = "Statistics registration failed with HTTP " + response.statusCode() + ". Retrying automatically.";
			return null;
		}
		String token = stringField(response.body(), "uuid");
		if (token == null || token.isBlank()) {
			lastStatus = "The statistics server returned an invalid registration response. Retrying automatically.";
			return null;
		}
		DrtSyncState.setToken(uuid, token);
		return token;
	}

	/** Returns a per-install access token, registering only after an explicit Runs-tab lookup. */
	public static CompletableFuture<String> accessToken() {
		String uuid = DrtAccount.currentUuid();
		if (uuid == null || !DrtConfigManager.getConfig().statsSyncEnabled) {
			return CompletableFuture.completedFuture(null);
		}
		String existing = DrtSyncState.token(uuid);
		if (existing != null) return CompletableFuture.completedFuture(existing);
		return CompletableFuture.supplyAsync(() -> {
			try {
				String installed = DrtSyncState.token(uuid);
				if (installed != null) return installed;
				return register(uuid);
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
				return null;
			} catch (IOException | RuntimeException e) {
				return null;
			}
		}, WORKER);
	}

	private static void revoked(Minecraft client, String uuid) {
		DrtSyncState.forgetUploads(uuid);
		client.execute(() -> {
			DrtConfigManager.getConfig().statsSyncEnabled = false;
			DrtConfigManager.save();
			DrtChat.send(client, "§e[DRT] Statistics Sharing is now OFF. §7The uploads from this installation were deleted on "
				+ "api.vyriv.dev, so DRT will not upload again unless you turn Statistics Sharing back on in /drt config.");
		});
	}

	/**
	 * How many DRT-recorded runs and chests saved before DRT stored accounts would upload if this
	 * account claimed them; 0 once the one-time question was answered.
	 */
	public static int unclaimedLegacyRecords(String uuid) {
		if (uuid == null || DrtSyncState.unstampedDecided()) return 0;
		long since = DrtConfigManager.getConfig().statsSyncSinceEpochMillis;
		var completions = DrtConfigManager.getRunCompletions();
		var history = DrtConfigManager.getRunHistory();
		int withLegacy = StatsUploadPlanner.eligible(new StatsUploadPlanner.Scope(uuid, since, true, true), completions, history).size();
		int without = StatsUploadPlanner.eligible(new StatsUploadPlanner.Scope(uuid, since, false, true), completions, history).size();
		return Math.max(0, withLegacy - without);
	}

	/** Starts the one-time, resumable import of records saved before Statistics Sharing began. */
	public static void beginHistoricalImport(Minecraft client) {
		String uuid = DrtAccount.currentUuid();
		if (client == null || uuid == null) return;
		if (!DrtConfigManager.getConfig().statsSyncEnabled) {
			DrtChat.send(client, "§e[DRT] Turn on Statistics Sharing before importing history.");
			return;
		}
		if (DrtSyncState.historicalImportEnabled(uuid)) {
			DrtChat.send(client, "§a[DRT] Historical import is already enabled. §7It resumes automatically after failures or restarts.");
			return;
		}
		int unstamped = unclaimedLegacyRecords(uuid);
		if (unstamped > 0) {
			client.gui.setScreen(new DrtLegacyRecordsScreen(uuid, DrtAccount.currentName(), unstamped));
			return;
		}
		enableHistoricalImport(client, uuid);
	}

	private static void enableHistoricalImport(Minecraft client, String uuid) {
		DrtSyncState.enableHistoricalImport(uuid);
		nextAttemptAtMillis = 0L;
		int records = historicalRecords(uuid);
		DrtChat.send(client, records == 0
			? "§a[DRT] No eligible historical records were found."
			: "§a[DRT] Historical import started for " + records + " run and chest record" + (records == 1 ? "" : "s")
				+ ". §7It runs in the background and resumes automatically after failures or restarts.");
	}

	private static int historicalRecords(String uuid) {
		long since = DrtConfigManager.getConfig().statsSyncSinceEpochMillis;
		var completions = DrtConfigManager.getRunCompletions();
		var history = DrtConfigManager.getRunHistory();
		int withHistory = StatsUploadPlanner.eligible(new StatsUploadPlanner.Scope(uuid, since, DrtSyncState.ownsUnstamped(uuid), true), completions, history).size();
		int liveOnly = StatsUploadPlanner.eligible(new StatsUploadPlanner.Scope(uuid, since, DrtSyncState.ownsUnstamped(uuid), false), completions, history).size();
		return Math.max(0, withHistory - liveOnly);
	}

	public static boolean historicalImportEnabled() {
		return DrtSyncState.historicalImportEnabled(DrtAccount.currentUuid());
	}

	public static String status() {
		return lastStatus;
	}

	/** The player confirmed the records saved without an account are all from this account. */
	public static void claimLegacyRecords(String uuid) {
		DrtSyncState.claimUnstamped(uuid);
		enableHistoricalImport(Minecraft.getInstance(), uuid);
	}

	/** The player keeps records saved without an account local; they are never uploaded. */
	public static void declineLegacyRecords() {
		DrtSyncState.declineUnstamped();
	}

	/** Whether this installation holds uploads for the logged-in account that could be deleted. */
	public static boolean holdsUploads() {
		return DrtSyncState.holdsToken(DrtAccount.currentUuid());
	}

	/**
	 * Turns sharing off, then deletes everything this installation uploaded for the logged-in
	 * account. Sharing goes off first so no queued step can register again after the deletion.
	 */
	public static void deleteUploads(Minecraft client) {
		String uuid = DrtAccount.currentUuid();
		if (uuid == null) return;
		String name = DrtAccount.currentName();
		DrtConfigManager.getConfig().statsSyncEnabled = false;
		DrtConfigManager.save();
		WORKER.execute(() -> {
			String message = deleteMessage(uuid, name);
			if (message != null) client.execute(() -> DrtChat.send(client, message));
		});
	}

	private static String deleteMessage(String uuid, String name) {
		String token = DrtSyncState.token(uuid);
		if (token == null) return "§a[DRT] Statistics Sharing is OFF. §7This installation holds no uploads for " + name + ".";
		try {
			HttpRequest request = DrtApi.send(ME_URI, "DELETE", null, token);
			HttpResponse<String> response = DrtApi.HTTP.send(request, HttpResponse.BodyHandlers.ofString());
			if (response.statusCode() == 401) {
                dev.vy.drt.client.sync.auth.DrtSessionAuth.invalidate();
                return "§c[DRT] Account authentication expired. No deletion was confirmed. Try again shortly.";
			}
			if (response.statusCode() != 200) {
				return "§c[DRT] Statistics Sharing is OFF, but the uploads could not be deleted right now (HTTP "
					+ response.statusCode() + "). Try again later from /drt config.";
			}
			DrtSyncState.forgetUploads(uuid);
			return "§a[DRT] Deleted " + longField(response.body(), "deletedRuns") + " runs and "
				+ longField(response.body(), "deletedChests") + " chests uploaded from this installation for " + name
				+ ". §7Statistics Sharing is OFF and deleted records are never uploaded again.";
		} catch (IOException | RuntimeException e) {
			return "§c[DRT] Statistics Sharing is OFF, but api.vyriv.dev could not be reached. Try again later from /drt config.";
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			return null;
		}
	}

	private static long retryDelay() {
		return StatsSyncSchedule.afterFailure(failures++);
	}

	private static String stringField(String body, String field) {
		JsonElement value = field(body, field);
		return value != null && value.isJsonPrimitive() ? value.getAsString() : null;
	}

	private static long longField(String body, String field) {
		JsonElement value = field(body, field);
		try {
			return value != null && value.isJsonPrimitive() ? value.getAsLong() : 0L;
		} catch (RuntimeException e) {
			return 0L;
		}
	}

	private static JsonElement field(String body, String field) {
		try {
			JsonElement parsed = JsonParser.parseString(body == null ? "" : body);
			return parsed.isJsonObject() ? parsed.getAsJsonObject().get(field) : null;
		} catch (RuntimeException e) {
			return null;
		}
	}
}
