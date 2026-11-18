package dev.vy.drt.client.sync;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import net.minecraft.client.Minecraft;

/**
 * Activity ping: the account UUID and installed versions, sent on launch and every 10 minutes
 * while DRT is running. It counts DRT users and gives VyAddons players the DRT badge. It cannot
 * be turned off and is independent of stats sync.
 */
public final class DrtActivityPinger {
	private static final URI PRESENCE_URI = URI.create(DrtApi.BASE_URL + "/v1/drt/presence");
	private static final long DEFAULT_INTERVAL_MS = 10L * 60L * 1000L;
	private static final long MIN_INTERVAL_MS = 5L * 60L * 1000L;
	private static final long MAX_INTERVAL_MS = 60L * 60L * 1000L;
	private static final long[] RETRY_DELAYS_MS = {60_000L, 5L * 60_000L, 10L * 60_000L};

	private static volatile boolean inFlight;
	private static long nextPingAtMillis;
	private static int failures;
	private static String lastUuid;

	private DrtActivityPinger() {
	}

	public static void tick(Minecraft client) {
		if (inFlight) return;
		String uuid = DrtAccount.currentUuid();
		if (uuid == null) return;
		long now = System.currentTimeMillis();
		if (!uuid.equals(lastUuid)) {
			lastUuid = uuid;
			nextPingAtMillis = 0L;
		}
		if (now < nextPingAtMillis) return;
		send(uuid);
	}

	private static void send(String uuid) {
		JsonObject body = new JsonObject();
		body.addProperty("uuid", uuid);
		body.addProperty("modVersion", DrtAccount.modVersion());
		body.addProperty("minecraftVersion", DrtAccount.minecraftVersion());
		inFlight = true;
		dev.vy.drt.client.sync.auth.DrtSessionAuth.bearerTokenAsync().whenComplete((token, authError) -> {
			if (authError != null || token.isEmpty()) {
				inFlight = false;
				scheduleRetry();
				return;
			}
			HttpRequest request;
			try {
				request = DrtApi.post(PRESENCE_URI, body.toString(), null);
			} catch (RuntimeException exception) {
				inFlight = false;
				scheduleRetry();
				return;
			}
			DrtApi.HTTP.sendAsync(request, HttpResponse.BodyHandlers.ofString())
			.whenComplete((response, error) -> {
				inFlight = false;
				if (response != null && response.statusCode() == 401) {
					dev.vy.drt.client.sync.auth.DrtSessionAuth.handleUnauthorized(request, response.body());
				}
				if (error != null || response == null || response.statusCode() != 200) {
					scheduleRetry();
					return;
				}
				failures = 0;
				nextPingAtMillis = System.currentTimeMillis() + intervalFrom(response.body());
			});
		});
	}

	private static void scheduleRetry() {
		long delay = RETRY_DELAYS_MS[Math.min(failures, RETRY_DELAYS_MS.length - 1)];
		failures++;
		nextPingAtMillis = System.currentTimeMillis() + delay;
	}

	private static long intervalFrom(String body) {
		try {
			JsonElement parsed = JsonParser.parseString(body == null ? "" : body);
			if (parsed.isJsonObject() && parsed.getAsJsonObject().has("nextPingSeconds")) {
				long seconds = parsed.getAsJsonObject().get("nextPingSeconds").getAsLong();
				return Math.max(MIN_INTERVAL_MS, Math.min(MAX_INTERVAL_MS, seconds * 1000L));
			}
		} catch (RuntimeException ignored) {
			// A malformed body keeps the default interval.
		}
		return DEFAULT_INTERVAL_MS;
	}
}
