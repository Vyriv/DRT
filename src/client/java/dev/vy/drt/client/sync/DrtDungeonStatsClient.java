package dev.vy.drt.client.sync;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.vy.drt.api.DungeonPlayerStats;
import dev.vy.drt.config.DrtConfigManager;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/** On-demand, bounded, deduplicated current dungeon-stat lookups for the Runs tab. */
public final class DrtDungeonStatsClient {
	private static final long TTL_MS = TimeUnit.MINUTES.toMillis(5);
	private static final Map<String, CacheEntry> CACHE = new ConcurrentHashMap<>();
	private static final Map<String, CompletableFuture<Result>> IN_FLIGHT = new ConcurrentHashMap<>();
	private static final ExecutorService WORKERS = Executors.newFixedThreadPool(2, runnable -> {
		Thread thread = new Thread(runnable, "DRT dungeon lookup");
		thread.setDaemon(true);
		return thread;
	});

	public enum State { READY, UNAVAILABLE, RATE_LIMITED, SHARING_DISABLED }
	public record Result(State state, DungeonPlayerStats stats, String message) {}
	private record CacheEntry(Result result, long expiresAt) {}

	private DrtDungeonStatsClient() {}

	public static CompletableFuture<Result> fetch(String player) {
		String key = player == null ? "" : player.trim().toLowerCase(Locale.ROOT);
		if (key.isBlank()) return CompletableFuture.completedFuture(new Result(State.UNAVAILABLE, null, "Missing player"));
		if (!DrtConfigManager.getConfig().statsSyncEnabled) return CompletableFuture.completedFuture(
			new Result(State.SHARING_DISABLED, null, "Enable Statistics Sharing to use current player statistics."));
		CacheEntry cached = CACHE.get(key);
		if (cached != null && cached.expiresAt() > System.currentTimeMillis()) return CompletableFuture.completedFuture(cached.result());
		return IN_FLIGHT.computeIfAbsent(key, ignored -> DrtStatsSync.accessToken().thenCompose(token -> {
			if (token == null) return CompletableFuture.completedFuture(new Result(State.UNAVAILABLE, null,
				"DRT authentication is unavailable. Retry after reconnecting."));
			return CompletableFuture.supplyAsync(() -> request(player, token), WORKERS);
		}).whenComplete((result, error) -> {
			IN_FLIGHT.remove(key);
			if (error == null && result != null && result.state() == State.READY) {
				CACHE.put(key, new CacheEntry(result, System.currentTimeMillis() + TTL_MS));
			}
		}));
	}

	private static Result request(String player, String token) {
		try {
			String encoded = URLEncoder.encode(player.trim(), StandardCharsets.UTF_8).replace("+", "%20");
			URI uri = URI.create(DrtApi.BASE_URL + "/v1/drt/dungeons/player/" + encoded);
			HttpResponse<String> response = DrtApi.HTTP.send(DrtApi.send(uri, "GET", null, token), HttpResponse.BodyHandlers.ofString());
			if (response.statusCode() == 429) return new Result(State.RATE_LIMITED, null, "Rate limited. Try again shortly.");
			JsonObject root = JsonParser.parseString(response.body()).getAsJsonObject();
			if (response.statusCode() != 200) return new Result(State.UNAVAILABLE, null, message(root));
			DungeonPlayerStats stats = DungeonPlayerStats.parse(root);
			return stats == null ? new Result(State.UNAVAILABLE, null, "Invalid statistics response")
				: new Result(State.READY, stats, "");
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			return new Result(State.UNAVAILABLE, null, "Request interrupted");
		} catch (Exception e) {
			return new Result(State.UNAVAILABLE, null, "Current statistics unavailable");
		}
	}

	private static String message(JsonObject root) {
		if (root == null || !root.has("cause")) return "Current statistics unavailable";
		return switch (root.get("cause").getAsString()) {
			case "PLAYER_NOT_FOUND" -> "Player not found";
			case "NO_SKYBLOCK_PROFILES" -> "No SkyBlock profiles";
			case "DUNGEON_STATS_UNAVAILABLE" -> "Dungeon statistics unavailable";
			case "hypixel_timeout" -> "Hypixel timed out";
			case "invalid_token" -> "DRT authentication was rejected. Retry after reconnecting.";
			case "SERVER_NOT_CONFIGURED" -> "The DRT backend key is not configured.";
			case "hypixel_temporarily_unavailable" -> "Hypixel is temporarily unavailable.";
			case "rate_limited" -> "The backend rate limit was reached. Try again shortly.";
			default -> "Current statistics unavailable";
		};
	}
}
