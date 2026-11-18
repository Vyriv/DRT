package dev.vy.drt.client.sync.auth;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.authlib.exceptions.AuthenticationException;
import com.mojang.authlib.exceptions.InvalidCredentialsException;

import dev.vy.drt.DungeonRunTracker;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.User;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.Style;

/**
 * DRT → api.vyriv.dev session proof (JWT only).
 *
 * <p>Minecraft access tokens are used only for the official Mojang/authlib
 * Minecraft session joinServer call. They are never sent to
 * api.vyriv.dev / Vyriv / Cloudflare / logging.
 */
public final class DrtSessionAuth {
	private static final URI AUTH_CHALLENGE_URI = URI.create("https://api.vyriv.dev/v1/drt/auth/challenge");
	private static final URI AUTH_URI = URI.create("https://api.vyriv.dev/v1/drt/auth/exchange");
	private static final URI AUTH_REFRESH_URI = URI.create("https://api.vyriv.dev/v1/drt/auth/refresh");
	private static final Duration TIMEOUT = Duration.ofSeconds(12);
	private static final long REFRESH_SKEW_MILLIS = 60_000L;
	private static final long TOKEN_EXPIRY_SAFETY_MILLIS = 5_000L;
	private static final long MOJANG_COOLDOWN_MILLIS = 60_000L;
	private static final SessionAuthConnectionGate CONNECTION_GATE = new SessionAuthConnectionGate(System::nanoTime);
	private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
	private static final java.security.SecureRandom RANDOM = new java.security.SecureRandom();
	private static final Object LOCK = new Object();
	private static final ExecutorService AUTH_EXECUTOR = Executors.newSingleThreadExecutor(r -> {
		Thread t = new Thread(r, "DRT-SessionAuth");
		t.setDaemon(true);
		return t;
	});

	// Only limits Mojang re-proofs. Renewing through the API continues until the session deadline,
	// so /drt still works after the launcher's token expires mid-game.
	private static final long REFRESH_WHILE_USED_MILLIS = 30L * 60L * 1000L;
	private static final ScheduledExecutorService REFRESH_SCHEDULER = Executors.newSingleThreadScheduledExecutor(r -> {
		Thread t = new Thread(r, "DRT-SessionAuthRefresh");
		t.setDaemon(true);
		return t;
	});

	interface RenewTransport {
		RenewReply send(HttpRequest request) throws Exception;
	}

	interface RequestSigner {
		String sign(String transcript) throws Exception;
	}

	record RenewReply(int status, String body, String origin) {
		static final RenewReply NO_RESPONSE = new RenewReply(-1, "", "none");
	}

	static volatile RenewTransport renewTransport = request -> {
		HttpResponse<String> response = HTTP.send(request, HttpResponse.BodyHandlers.ofString());
		return new RenewReply(response.statusCode(), response.body() == null ? "" : response.body(), responseOrigin(response));
	};
	static volatile RequestSigner requestSigner = transcript -> DrtInstallKey.load().sign(transcript);

	private static volatile String cachedJwt;
	private static volatile long expiresAtMillis;
	private static volatile long usableUntilMillis;
	private static volatile UUID cachedProfileId;
	private static volatile long sessionDeadlineMillis;
	private static int renewFailures;
	private static final SessionAuthPolicy.CredentialBackoff CREDENTIAL_BACKOFF = new SessionAuthPolicy.CredentialBackoff();
	private static volatile long nextMojangAuthAttemptAtMillis;
	private static Failure cooldownFailure = Failure.NONE;
	private static String cooldownDetail = "";
	private static ScheduledFuture<?> pendingRefresh;
	private static long refreshGeneration;
	private static volatile long lastUsedAtMillis;
	private static CompletableFuture<Optional<String>> inFlight;
	private static volatile Failure lastFailure = Failure.NONE;
	private static volatile String lastFailureDetail = "";
	private static volatile long lastChatNoticeAtMillis;
	private static volatile String lastChatNotice;

	public enum Failure {
		NONE(""),
		MISSING_SESSION("DRT could not authenticate /drt (missing Minecraft session)"),
		OFFLINE_SESSION("DRT could not authenticate /drt (offline / Fabric session)"),
		JOIN_SERVER_FAILED("DRT could not authenticate /drt (Minecraft session service failed). Wait a minute before retrying."),
		INVALID_CREDENTIALS("DRT could not verify your Minecraft login because it expired during this session. Restart Minecraft from your launcher (sign in again if asked), then use /drt."),
		MOJANG_RATE_LIMITED("DRT authentication is rate-limited by Minecraft. Wait a minute before retrying /drt."),
		SERVER_CONNECTING("DRT authentication will start once your server connection is ready."),
		SERVER_AUTH_UNAVAILABLE("DRT could not authenticate /drt (auth service unavailable)"),
		AUTH_COOLDOWN("DRT authentication is paused. Wait a minute before retrying /drt."),
		AUTH_REJECTED("DRT could not authenticate /drt (session proof rejected)"),
		AUTH_HTTP("DRT could not authenticate /drt (auth HTTP error)"),
		MISSING_JWT("DRT could not authenticate /drt (missing JWT)");

		private final String userMessage;

		Failure(String userMessage) {
			this.userMessage = userMessage;
		}

		public String userMessage() {
			return userMessage;
		}
	}

	private DrtSessionAuth() {
	}

	public static void invalidate() {
		synchronized (LOCK) {
			invalidateLocked();
		}
	}

	private static void invalidateLocked() {
		cachedJwt = null;
		expiresAtMillis = 0L;
		usableUntilMillis = 0L;
		cachedProfileId = null;
		sessionDeadlineMillis = 0L;
		// Never detach an active attempt or clear its cooldown.
		cancelRefreshLocked();
	}

	/** Ignore late 401s for an older JWT; preserve the shared authentication attempt. */
	public static void invalidate(HttpRequest rejectedRequest) {
		handleUnauthorized(rejectedRequest, "{\"cause\":\"unauthorized\"}");
	}

	/**
	 * Handles a resource 401 without assuming every request-proof failure invalidates the JWT.
	 * Returns true only when the rejected request used and invalidated the current token.
	 */
	public static boolean handleUnauthorized(HttpRequest rejectedRequest, String responseBody) {
		return handleUnauthorized(rejectedRequest, responseBody, -1L);
	}

	public static boolean handleUnauthorized(
		HttpRequest rejectedRequest,
		String responseBody,
		long elapsedMillis
	) {
		String cause = unauthorizedCause(responseBody);
		boolean invalidatingCause = invalidatesSession(cause);
		boolean currentToken;
		boolean invalidated = false;
		long validForMillis;
		long cooldownMillis;
		synchronized (LOCK) {
			String authorization = rejectedRequest == null
				? ""
				: rejectedRequest.headers().firstValue("Authorization").orElse("");
			currentToken = cachedJwt != null && authorization.equals("Bearer " + cachedJwt);
			validForMillis = Math.max(0L, expiresAtMillis - System.currentTimeMillis());
			cooldownMillis = remainingAuthCooldownMillis();
			if (currentToken && invalidatingCause) {
				invalidateLocked();
				invalidated = true;
			} else if (currentToken) {
				setFailure(Failure.AUTH_REJECTED, "resource 401 cause=" + cause);
			}
		}
		String correlationId = rejectedRequest == null
			? "none"
			: rejectedRequest.headers().firstValue("X-BetterPV-Correlation-ID").orElse("none");
		DungeonRunTracker.LOGGER.warn(
			"DRT auth correlationId={} stage=resource_unauthorized elapsedMs={} httpStatus=401 cause={} currentToken={} action={} tokenValidForMs={} proofCooldownMs={}",
			correlationId,
			elapsedMillis < 0L ? "unknown" : elapsedMillis,
			cause,
			currentToken,
			invalidated ? "invalidate" : currentToken ? "preserve" : "ignore_stale",
			validForMillis,
			cooldownMillis
		);
		return invalidated;
	}

	static String unauthorizedCause(String responseBody) {
		if (responseBody == null || responseBody.isBlank()) {
			return "unknown";
		}
		try {
			JsonObject root = JsonParser.parseString(responseBody).getAsJsonObject();
			if (root.has("cause") && root.get("cause").isJsonPrimitive()) {
				String cause = root.get("cause").getAsString();
				if (cause != null && cause.matches("[A-Za-z0-9_]{1,80}")) {
					return cause.toLowerCase(Locale.ROOT);
				}
			}
		} catch (RuntimeException ignored) {
		}
		return "unknown";
	}

	static boolean invalidatesSession(String cause) {
		return "unauthorized".equals(cause)
			|| "session_revoked_or_expired".equals(cause)
			|| "auth_v1_disabled".equals(cause);
	}

	public static long remainingAuthCooldownMillis() {
		return Math.max(0L, nextMojangAuthAttemptAtMillis - System.currentTimeMillis());
	}

	/** True when an authenticated resource request can go out now without forcing a new Mojang proof. */
	public static boolean canFetchResources() {
		return SessionAuthPolicy.resourceFetchAllowed(isTokenValid(), remainingAuthCooldownMillis());
	}

	private static boolean cooldownActive() {
		synchronized (LOCK) {
			long remaining = remainingAuthCooldownMillis();
			if (remaining > 0L && cooldownFailure == Failure.INVALID_CREDENTIALS
				&& !CREDENTIAL_BACKOFF.appliesTo(currentCredentialKey())) {
				DungeonRunTracker.LOGGER.info("DRT Minecraft login changed; session proof may resume");
				nextMojangAuthAttemptAtMillis = 0L;
				remaining = 0L;
			}
			if (remaining > 0L) {
				// Keep a more useful exchange failure visible while still blocking another
				// Mojang proof. A successful joinServer followed by a backend 502 used to
				// be replaced with AUTH_COOLDOWN on the next caller.
				if (lastFailure == Failure.NONE || lastFailure == Failure.AUTH_COOLDOWN
					|| lastFailure == cooldownFailure) {
					setFailure(cooldownFailure, cooldownDetail);
				}
				DungeonRunTracker.LOGGER.debug("DRT session auth skipped; cooldown active for {}ms", remaining);
				return true;
			}
			if (nextMojangAuthAttemptAtMillis != 0L) {
				DungeonRunTracker.LOGGER.info("DRT session auth cooldown expired; authentication may resume");
				nextMojangAuthAttemptAtMillis = 0L;
			}
			return false;
		}
	}

	private static void pauseAuth(Failure failure, String detail) {
		pauseAuth(failure, detail, MOJANG_COOLDOWN_MILLIS);
	}

	private static void pauseAuth(Failure failure, String detail, long pauseMillis) {
		synchronized (LOCK) {
			nextMojangAuthAttemptAtMillis = System.currentTimeMillis() + pauseMillis;
			cooldownFailure = failure;
			cooldownDetail = detail;
			setFailure(failure, detail);
		}
		if (failure == Failure.MOJANG_RATE_LIMITED) {
			DungeonRunTracker.LOGGER.warn("DRT Minecraft session auth rate-limited; pausing Mojang auth for 60s");
		}
	}

	private static void setFailure(Failure failure, String detail) {
		lastFailure = failure == null ? Failure.NONE : failure;
		lastFailureDetail = detail == null ? "" : detail.trim();
	}

	public static Failure lastFailure() {
		return lastFailure == null ? Failure.NONE : lastFailure;
	}

	/** User-facing reason when JWT auth is unavailable; empty when OK. */
	public static Optional<String> userFacingFailure() {
		Failure failure = lastFailure();
		if (failure == Failure.NONE || failure.userMessage().isBlank()) {
			return Optional.empty();
		}
		return Optional.of(failure.userMessage());
	}

	/** Applies the v2 bearer token and proof of possession. */
	public static boolean applyAuthHeaders(HttpRequest.Builder builder, URI uri, String method) {
		Optional<String> bearer = ensureBearerToken();
		if (bearer.isPresent()) {
			try {
				signRequest(builder, method, uri, bearer.get(), UUID.randomUUID().toString());
				return true;
			} catch (Exception exception) {
				DungeonRunTracker.LOGGER.warn("DRT request proof failed", exception);
				setFailure(Failure.AUTH_HTTP, "request proof failed");
				return false;
			}
		}
		if (lastFailure == Failure.NONE) {
			lastFailure = Failure.MISSING_JWT;
		}
		return false;
	}

	private static void signRequest(HttpRequest.Builder builder, String method, URI uri, String token, String correlationId)
		throws Exception {
		String timestamp = Long.toString(Instant.now().getEpochSecond());
		byte[] nonceBytes = new byte[18];
		RANDOM.nextBytes(nonceBytes);
		String nonce = Base64.getUrlEncoder().withoutPadding().encodeToString(nonceBytes);
		String path = uri.getRawPath();
		if (uri.getRawQuery() != null && !uri.getRawQuery().isBlank()) path += "?" + uri.getRawQuery();
		String signature = requestSigner.sign(requestTranscript(method, path, timestamp, nonce, token));
		builder.header("Authorization", "Bearer " + token)
			.header("X-BetterPV-Timestamp", timestamp)
			.header("X-BetterPV-Nonce", nonce)
			.header("X-BetterPV-Signature", signature)
			.header("X-BetterPV-Correlation-ID", correlationId);
	}

	/** Posts a throttled in-game chat line for the current auth failure. */
	public static void notifyPlayerIfNeeded() {
		Failure failure = lastFailure();
		if (failure == Failure.NONE) {
			return;
		}
		Minecraft mc = Minecraft.getInstance();
		if (mc == null || mc.gui == null) {
			return;
		}
		long now = System.currentTimeMillis();
		String noticeKey = failure.name();
		if (noticeKey.equals(lastChatNotice) && now - lastChatNoticeAtMillis < 15_000L) {
			return;
		}
		lastChatNotice = noticeKey;
		lastChatNoticeAtMillis = now;
		String report = copyableReport(mc, failure);
		mc.execute(() -> {
			if (mc.gui == null) {
				return;
			}
			mc.gui.hud.getChat().addClientSystemMessage(authFailChat(report, failure));
		});
	}

	private static Component authFailChat(String report, Failure failure) {
		Component copyHint = Component.literal("Click to copy a report to DM Vyriv")
			.setStyle(Style.EMPTY
				.withColor(0xFFD36A)
				.withUnderlined(true)
				.withClickEvent(new ClickEvent.CopyToClipboard(report))
				.withHoverEvent(new HoverEvent.ShowText(
					Component.literal("Click to copy, then paste it to Vyriv on Discord")
				)));
		return Component.literal(failure.userMessage() + " ")
			.setStyle(Style.EMPTY.withColor(0xFFAAAAAA))
			.append(copyHint);
	}

	private static String copyableReport(Minecraft mc, Failure failure) {
		User user = mc.getUser();
		String name = user != null && user.getName() != null ? user.getName() : "?";
		String uuid = user != null && user.getProfileId() != null ? user.getProfileId().toString() : "?";
		String detail = lastFailureDetail == null || lastFailureDetail.isBlank() ? "" : "\ndetail: " + lastFailureDetail;
		return "DRT /drt auth failed"
			+ "\nname: " + name
			+ "\nuuid: " + uuid
			+ "\nreason: " + failure.name()
			+ detail
			+ "\nmod: " + modVersion();
	}

	private static String modVersion() {
		return FabricLoader.getInstance().getModContainer("drt")
			.map(container -> container.getMetadata().getVersion().getFriendlyString())
			.orElse("unknown");
	}

	/**
	 * Non-blocking warm of the session JWT once a real Minecraft login is present.
	 * Reuses in-flight auth; no-ops when a usable JWT is already cached.
	 */
	public static void prefetchAsync() {
		requestToken(false);
	}

	/** Non-blocking; concurrent consumers share one attempt. */
	public static CompletableFuture<Optional<String>> bearerTokenAsync() {
		lastUsedAtMillis = System.currentTimeMillis();
		return requestToken(false);
	}

	private static CompletableFuture<Optional<String>> requestToken(boolean refresh) {
		synchronized (LOCK) {
			dropTokenForOtherAccountLocked();
			SessionAuthPolicy.Action action = SessionAuthPolicy.nextAction(
				isTokenValid(), refresh, sessionDeadlineMillis - System.currentTimeMillis());
			if (action == SessionAuthPolicy.Action.USE_CACHED) {
				setFailure(Failure.NONE, "");
				return CompletableFuture.completedFuture(Optional.of(cachedJwt));
			}
			if (inFlight != null && !inFlight.isDone()) return inFlight;
			boolean renew = action == SessionAuthPolicy.Action.RENEW;
			if (!renew && cooldownActive()) return CompletableFuture.completedFuture(Optional.empty());
			inFlight = CompletableFuture.supplyAsync(
					renew ? DrtSessionAuth::renewOnce : DrtSessionAuth::authenticateOnce, AUTH_EXECUTOR)
				.exceptionally(error -> {
					DungeonRunTracker.LOGGER.warn("DRT session auth failed", error);
					setFailure(Failure.AUTH_HTTP, "Unexpected session authentication failure");
					return Optional.empty();
				});
			return inFlight;
		}
	}

	/** Blocking; call only from worker threads, never the extractRenderState thread. */
	public static Optional<String> ensureBearerToken() {
		return bearerTokenAsync().join();
	}

	private static void cancelRefreshLocked() {
		refreshGeneration++;
		if (pendingRefresh != null) pendingRefresh.cancel(false);
		pendingRefresh = null;
	}

	// Only one pending refresh, and only while DRT is in use.
	private static void scheduleRefresh() {
		synchronized (LOCK) {
			long delayMillis = Math.max(30_000L, usableUntilMillis - System.currentTimeMillis());
			scheduleRefreshLocked(delayMillis);
		}
	}

	private static void scheduleRefreshLocked(long delayMillis) {
		cancelRefreshLocked();
		long generation = refreshGeneration;
		pendingRefresh = REFRESH_SCHEDULER.schedule(() -> {
			synchronized (LOCK) {
				if (generation != refreshGeneration) return;
				pendingRefresh = null;
				boolean renewable = isTokenValid()
					&& sessionDeadlineMillis - System.currentTimeMillis() >= SessionAuthPolicy.MIN_RENEWABLE_MILLIS;
				if (!renewable && System.currentTimeMillis() - lastUsedAtMillis > REFRESH_WHILE_USED_MILLIS) return;
				long cooldownMillis = renewable ? 0L : remainingAuthCooldownMillis();
				if (cooldownMillis > 0L) {
					DungeonRunTracker.LOGGER.info(
						"DRT session refresh deferred proofCooldownMs={} tokenValidForMs={}",
						cooldownMillis,
						Math.max(0L, expiresAtMillis - System.currentTimeMillis())
					);
					scheduleRefreshLocked(cooldownMillis + 250L);
					return;
				}
				requestToken(true);
			}
		}, Math.max(1L, delayMillis), TimeUnit.MILLISECONDS);
	}

	private static boolean isTokenValid() {
		return cachedJwt != null && !cachedJwt.isBlank()
			&& System.currentTimeMillis() < expiresAtMillis - TOKEN_EXPIRY_SAFETY_MILLIS;
	}

	// Account switchers can replace the Minecraft user mid-game; never send one account's JWT for another.
	private static void dropTokenForOtherAccountLocked() {
		UUID owner = cachedProfileId;
		if (cachedJwt == null || owner == null) return;
		UUID current = currentProfileId();
		if (current == null || current.equals(owner)) return;
		DungeonRunTracker.LOGGER.info("DRT Minecraft account changed; dropping the previous account's session token");
		invalidateLocked();
	}

	private static UUID currentProfileId() {
		Minecraft mc = Minecraft.getInstance();
		User user = mc == null ? null : mc.getUser();
		return user == null ? null : user.getProfileId();
	}

	private static String currentCredentialKey() {
		Minecraft mc = Minecraft.getInstance();
		User user = mc == null ? null : mc.getUser();
		return user == null ? null : SessionAuthPolicy.credentialKey(user.getProfileId(), user.getAccessToken());
	}

	private static void installTokenLocked(String token, long expiresInSeconds, long sessionExpiresInSeconds, UUID profileId) {
		long now = System.currentTimeMillis();
		cachedJwt = token;
		expiresAtMillis = now + (expiresInSeconds * 1000L);
		usableUntilMillis = expiresAtMillis - Math.min(REFRESH_SKEW_MILLIS, expiresInSeconds * 1000L / 2L);
		cachedProfileId = profileId;
		sessionDeadlineMillis = sessionExpiresInSeconds > 0L ? now + (sessionExpiresInSeconds * 1000L) : 0L;
		renewFailures = 0;
		scheduleRefresh();
	}

	private static long positiveLong(JsonObject root, String key, long fallback) {
		return root.has(key) && root.get(key).isJsonPrimitive() ? Math.max(1L, root.get(key).getAsLong()) : fallback;
	}

	/** Swaps the current JWT for a new one through the API. Falls back to a Mojang proof only when it must. */
	private static Optional<String> renewOnce() {
		String source;
		UUID profileId;
		synchronized (LOCK) {
			source = isTokenValid() ? cachedJwt : null;
			profileId = cachedProfileId;
		}
		if (source == null) return authenticateOnce();
		String correlationId = UUID.randomUUID().toString();
		long startedAtMillis = System.currentTimeMillis();
		RenewReply reply;
		try {
			HttpRequest.Builder builder = HttpRequest.newBuilder(AUTH_REFRESH_URI)
				.timeout(TIMEOUT)
				.header("Content-Type", "application/json")
				.header("Accept", "application/json")
				.POST(HttpRequest.BodyPublishers.ofString("{}", StandardCharsets.UTF_8));
			signRequest(builder, "POST", AUTH_REFRESH_URI, source, correlationId);
			logAuthStage(correlationId, "refresh_request_started", startedAtMillis, null);
			reply = renewTransport.send(builder.build());
		} catch (InterruptedException exception) {
			Thread.currentThread().interrupt();
			reply = RenewReply.NO_RESPONSE;
		} catch (Exception exception) {
			DungeonRunTracker.LOGGER.warn("DRT session refresh request failed: {}", exception.toString());
			reply = RenewReply.NO_RESPONSE;
		}
		logAuthStage(correlationId, "refresh_response", startedAtMillis, reply.status() > 0 ? reply.status() : null, reply.origin());
		String cause = unauthorizedCause(reply.body());
		SessionAuthPolicy.RenewOutcome outcome = SessionAuthPolicy.classifyRenewal(reply.status(), cause);
		if (outcome == SessionAuthPolicy.RenewOutcome.RENEWED) {
			try {
				JsonObject root = JsonParser.parseString(reply.body()).getAsJsonObject();
				String token = requiredString(root, "token");
				long expiresInSeconds = positiveLong(root, "expiresIn", 300L);
				long sessionExpiresInSeconds = positiveLong(root, "sessionExpiresIn", 0L);
				synchronized (LOCK) {
					// A new proof or invalidation won the race; keep whatever replaced the source token.
					if (!source.equals(cachedJwt)) return Optional.ofNullable(isTokenValid() ? cachedJwt : null);
					installTokenLocked(token, expiresInSeconds, sessionExpiresInSeconds, profileId);
				}
				setFailure(Failure.NONE, "");
				logAuthStage(correlationId, "refresh_succeeded", startedAtMillis, reply.status());
				return Optional.of(token);
			} catch (RuntimeException exception) {
				outcome = SessionAuthPolicy.RenewOutcome.RETRY;
			}
		}
		DungeonRunTracker.LOGGER.info(
			"DRT auth correlationId={} stage=refresh_not_renewed outcome={} httpStatus={} cause={}",
			correlationId, outcome, reply.status() > 0 ? reply.status() : "none", cause
		);
		switch (outcome) {
			case NEEDS_PROOF -> {
				synchronized (LOCK) {
					if (source.equals(cachedJwt)) sessionDeadlineMillis = 0L;
				}
				return authenticateOnce();
			}
			case SESSION_DEAD -> {
				synchronized (LOCK) {
					if (source.equals(cachedJwt)) invalidateLocked();
				}
				return authenticateOnce();
			}
			case BLOCKED -> {
				synchronized (LOCK) {
					if (source.equals(cachedJwt)) sessionDeadlineMillis = 0L;
				}
				setFailure(Failure.AUTH_REJECTED, "refresh status=" + reply.status() + " cause=" + cause);
				return Optional.empty();
			}
			default -> {
				synchronized (LOCK) {
					if (!source.equals(cachedJwt)) return Optional.ofNullable(isTokenValid() ? cachedJwt : null);
					renewFailures++;
					long delay = SessionAuthPolicy.renewRetryDelayMillis(
						renewFailures, expiresAtMillis - System.currentTimeMillis());
					if (delay > 0L) {
						scheduleRefreshLocked(delay);
					} else {
						DungeonRunTracker.LOGGER.warn("DRT session refresh gave up after {} attempts; the next /drt needs a new session proof",
							renewFailures);
					}
					return isTokenValid() ? Optional.of(cachedJwt) : Optional.empty();
				}
			}
		}
	}

	private static Optional<String> authenticateOnce() {
		if (cooldownActive()) return Optional.empty();
		Minecraft mc = Minecraft.getInstance();
		if (mc == null) {
			setFailure(Failure.MISSING_SESSION, "minecraft instance null");
			return Optional.empty();
		}

		User user = mc.getUser();
		if (user == null) {
			setFailure(Failure.MISSING_SESSION, "user null");
			return Optional.empty();
		}

		String username = user.getName();
		UUID profileId = user.getProfileId();
		String accessToken = user.getAccessToken();
		if (username == null || username.isBlank() || profileId == null || accessToken == null || accessToken.isBlank()) {
			DungeonRunTracker.LOGGER.warn("DRT session auth skipped: missing Minecraft user session");
			setFailure(Failure.MISSING_SESSION, "blank username/uuid/token");
			return Optional.empty();
		}

		if (looksLikeOfflineDevSession(accessToken)) {
			DungeonRunTracker.LOGGER.warn(
				"DRT session JWT skipped: Fabric offline token user={}",
				username
			);
			setFailure(Failure.OFFLINE_SESSION, "FabricMC offline token");
			return Optional.empty();
		}

		long connectionGeneration = waitWhileConnecting(mc);
		if (connectionGeneration < 0L) return Optional.empty();
		String correlationId = UUID.randomUUID().toString();
		long authStartedAtMillis = System.currentTimeMillis();
		try {
			DrtInstallKey installKey = DrtInstallKey.load();
			String publicKey = installKey.publicKey();
			String challengeBody = "{\"username\":\"" + escapeJson(username)
				+ "\",\"publicKey\":\"" + publicKey + "\",\"protocol\":2}";
			HttpRequest challengeRequest = HttpRequest.newBuilder(AUTH_CHALLENGE_URI)
				.timeout(TIMEOUT)
				.header("Content-Type", "application/json")
				.header("Accept", "application/json")
				.header("X-BetterPV-Correlation-ID", correlationId)
				.POST(HttpRequest.BodyPublishers.ofString(challengeBody, StandardCharsets.UTF_8))
				.build();
			logAuthStage(correlationId, "challenge_request_started", authStartedAtMillis, null);
			HttpResponse<String> challengeResponse = HTTP.send(challengeRequest, HttpResponse.BodyHandlers.ofString());
			logAuthStage(
				correlationId,
				"challenge_response",
				authStartedAtMillis,
				challengeResponse.statusCode(),
				responseOrigin(challengeResponse)
			);
			if (challengeResponse.statusCode() < 200 || challengeResponse.statusCode() >= 300
				|| challengeResponse.body() == null || challengeResponse.body().isBlank()) {
				setFailure(
					Failure.AUTH_HTTP,
					"challenge status=" + challengeResponse.statusCode()
						+ " origin=" + responseOrigin(challengeResponse)
						+ " correlationId=" + correlationId
				);
				return Optional.empty();
			}
			JsonObject challenge = JsonParser.parseString(challengeResponse.body()).getAsJsonObject();
			String challengeId = requiredString(challenge, "challengeId");
			String serverId = requiredString(challenge, "serverId");

			// Check on the client thread again after the challenge round trip. No network I/O
			// holds the lifecycle monitor, and vanilla never waits for our proof.
			if (cooldownActive()) return Optional.empty();
			if (waitWhileConnecting(mc) != connectionGeneration || !CONNECTION_GATE.beginProof(connectionGeneration)) {
				setFailure(Failure.SERVER_CONNECTING, "Minecraft connection changed before session proof");
				return Optional.empty();
			}
			Optional<String> joinError;
			boolean sameConnection;
			try {
				// A reconnect after admission can still overlap an already-issued Mojang request.
				// Authlib offers no reliable cancellation; do not stall vanilla to serialize it.
				logAuthStage(correlationId, "mojang_join_started", authStartedAtMillis, null);
				joinError = joinMinecraftSession(mc, profileId, accessToken, serverId, username);
			} finally {
				sameConnection = CONNECTION_GATE.finishProof(connectionGeneration);
			}

			if (joinError.isPresent()) {
				logAuthStage(correlationId, "mojang_join_failed", authStartedAtMillis, null);
				Failure failure = classifyJoinFailure(joinError.get());
				if (failure == Failure.INVALID_CREDENTIALS) {
					long pauseMillis;
					int attempts;
					synchronized (LOCK) {
						pauseMillis = CREDENTIAL_BACKOFF.recordInvalid(SessionAuthPolicy.credentialKey(profileId, accessToken));
						attempts = CREDENTIAL_BACKOFF.failures();
					}
					DungeonRunTracker.LOGGER.warn(
						"DRT Minecraft login rejected by Mojang attempts={} nextProofInSeconds={}; the launcher must sign in again",
						attempts, pauseMillis / 1000L
					);
					pauseAuth(failure, joinError.get(), pauseMillis);
				} else {
					pauseAuth(failure, joinError.get());
				}
				return Optional.empty();
			}
			logAuthStage(correlationId, "mojang_join_succeeded", authStartedAtMillis, null);
			synchronized (LOCK) {
				CREDENTIAL_BACKOFF.reset();
			}
			// Also space successful proofs, including repeated backend 401 re-authentication.
			pauseAuth(Failure.AUTH_COOLDOWN, "Waiting before another Minecraft session proof");
			if (!sameConnection) {
				setFailure(Failure.SERVER_CONNECTING, "Minecraft connection changed during session proof");
				return Optional.empty();
			}

			String challengeTranscript = String.join("\n",
				"drt-auth-v2", challengeId, username, serverId, publicKey);
			String signature = installKey.sign(challengeTranscript);
			String body = "{\"challengeId\":\"" + challengeId
				+ "\",\"username\":\"" + escapeJson(username)
				+ "\",\"serverId\":\"" + serverId
				+ "\",\"publicKey\":\"" + publicKey
				+ "\",\"signature\":\"" + signature
				+ "\",\"protocol\":2}";
			HttpRequest request = HttpRequest.newBuilder(AUTH_URI)
				.timeout(TIMEOUT)
				.header("Content-Type", "application/json")
				.header("Accept", "application/json")
				.header("X-BetterPV-Correlation-ID", correlationId)
				.POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
				.build();
			logAuthStage(correlationId, "exchange_request_started", authStartedAtMillis, null);
			HttpResponse<String> response = HTTP.send(request, HttpResponse.BodyHandlers.ofString());
			int status = response.statusCode();
			logAuthStage(correlationId, "exchange_response", authStartedAtMillis, status, responseOrigin(response));
			if (status == 502 || status == 504) {
				// The API keeps the signed challenge available after transient Mojang
				// failures. Retry this exact exchange once without another joinServer call.
				logAuthStage(correlationId, "exchange_retry_started", authStartedAtMillis, status);
				response = HTTP.send(request, HttpResponse.BodyHandlers.ofString());
				status = response.statusCode();
				logAuthStage(
					correlationId,
					"exchange_retry_response",
					authStartedAtMillis,
					status,
					responseOrigin(response)
				);
			}
			String responseBody = response.body() == null ? "" : response.body();
			if (status == 503 || causeEquals(responseBody, "session_auth_unavailable")) {
				DungeonRunTracker.LOGGER.warn("DRT v2 auth unavailable status={}", status);
				setFailure(Failure.SERVER_AUTH_UNAVAILABLE, "status=" + status);
				return Optional.empty();
			}
			if (status < 200 || status >= 300 || responseBody.isBlank()) {
				DungeonRunTracker.LOGGER.warn("DRT v2 auth failed status={}", status);
				setFailure(
					status == 401 || status == 403 ? Failure.AUTH_REJECTED : Failure.AUTH_HTTP,
					"status=" + status + " origin=" + responseOrigin(response)
						+ " correlationId=" + correlationId
				);
				return Optional.empty();
			}

			JsonObject root = JsonParser.parseString(responseBody).getAsJsonObject();
			if (root.has("success") && root.get("success").isJsonPrimitive() && !root.get("success").getAsBoolean()) {
				setFailure(Failure.AUTH_REJECTED, "success=false");
				return Optional.empty();
			}
			if (!root.has("token") || !root.get("token").isJsonPrimitive()) {
				setFailure(Failure.AUTH_REJECTED, "missing token field");
				return Optional.empty();
			}

			String token = root.get("token").getAsString();
			long expiresInSeconds = positiveLong(root, "expiresIn", 300L);
			long sessionExpiresInSeconds = positiveLong(root, "sessionExpiresIn", 0L);
			synchronized (LOCK) {
				installTokenLocked(token, expiresInSeconds, sessionExpiresInSeconds, profileId);
			}
			setFailure(Failure.NONE, "");
			DungeonRunTracker.LOGGER.info(
				"DRT session token installed ttlSeconds={} refreshInMs={} renewableForSeconds={} proofCooldownMs={}",
				expiresInSeconds,
				Math.max(0L, usableUntilMillis - System.currentTimeMillis()),
				sessionExpiresInSeconds,
				remainingAuthCooldownMillis()
			);
			logAuthStage(correlationId, "authentication_succeeded", authStartedAtMillis, status);
			return Optional.of(token);
		} catch (Exception exception) {
			DungeonRunTracker.LOGGER.warn("DRT v2 auth request failed", exception);
			if (exception instanceof InterruptedException) {
				Thread.currentThread().interrupt();
			}
			setFailure(Failure.AUTH_HTTP, exception.toString());
			return Optional.empty();
		}
	}

	private static void logAuthStage(
		String correlationId,
		String stage,
		long startedAtMillis,
		Integer httpStatus
	) {
		logAuthStage(correlationId, stage, startedAtMillis, httpStatus, "none");
	}

	private static void logAuthStage(
		String correlationId,
		String stage,
		long startedAtMillis,
		Integer httpStatus,
		String origin
	) {
		DungeonRunTracker.LOGGER.info(
			"DRT auth correlationId={} stage={} elapsedMs={} httpStatus={} origin={}",
			correlationId,
			stage,
			Math.max(0L, System.currentTimeMillis() - startedAtMillis),
			httpStatus == null ? "none" : httpStatus,
			origin
		);
	}

	private static String responseOrigin(HttpResponse<?> response) {
		return response.headers().firstValue("X-BetterPV-Auth-Origin")
			.filter("application"::equalsIgnoreCase)
			.map(ignored -> "application")
			.orElse("proxy_or_edge");
	}

	/**
	 * Registers this client session with Mojang for {@code serverId}.
	 * Returns empty on success, or a short error tag on failure.
	 */
	private static Optional<String> joinMinecraftSession(
		Minecraft mc,
		UUID profileId,
		String accessToken,
		String serverId,
		String username
	) {
		try {
			// Access token is ONLY for official Minecraft session-service join; never sent to Vyriv.
			var sessionService = mc.services().sessionService();
			sessionService.joinServer(profileId, accessToken, serverId);
			return Optional.empty();
		} catch (InvalidCredentialsException exception) {
			DungeonRunTracker.LOGGER.warn(
				"Minecraft joinServer rejected credentials for DRT auth user={}",
				username
			);
			return Optional.of("InvalidCredentials: " + exception.toString());
		} catch (AuthenticationException exception) {
			DungeonRunTracker.LOGGER.warn("Minecraft joinServer failed for DRT auth: {}", exception.toString());
			return Optional.of(joinFailureDetail(exception));
		} catch (RuntimeException exception) {
			DungeonRunTracker.LOGGER.warn("Minecraft joinServer failed for DRT auth: {}", exception.toString());
			return Optional.of(joinFailureDetail(exception));
		}
	}

	private static String joinFailureDetail(Throwable failure) {
		StringBuilder detail = new StringBuilder();
		for (int depth = 0; failure != null && depth < 8; depth++, failure = failure.getCause()) {
			if (!detail.isEmpty()) detail.append("; caused by: ");
			detail.append(failure);
		}
		return detail.toString();
	}

	static Failure classifyJoinFailure(String detail) {
		String lower = detail.toLowerCase(Locale.ROOT);
		if (lower.contains("invalidcredentials")) return Failure.INVALID_CREDENTIALS;
		if (lower.contains("ratelimiter disallowed request") || lower.contains("rate limit")
			|| lower.contains("too many requests")) return Failure.MOJANG_RATE_LIMITED;
		return Failure.JOIN_SERVER_FAILED;
	}

	/** Called at connection start, before vanilla schedules authentication. Never waits for I/O. */
	public static void onConnectionStarting() {
		if (CONNECTION_GATE.connecting()) {
			DungeonRunTracker.LOGGER.debug("Minecraft connection started with a DRT proof already in flight; abandoning its result");
		}
	}

	public static void onPlayInit(Object listener) {
		CONNECTION_GATE.expectPlay(listener);
	}

	public static void onPlayReady(Object listener) {
		CONNECTION_GATE.playReady(listener);
	}

	public static void onPlayDisconnect(Object listener) {
		CONNECTION_GATE.disconnected(listener);
	}

	/** Client-thread snapshot, also used by prefetch so it does not consume its one shot too early. */
	public static boolean isReadyForSessionAuth(Minecraft client) {
		return connectionGeneration(client) >= 0L;
	}

	private static long connectionGeneration(Minecraft client) {
		if (client.gui.screen() instanceof ConnectScreen || client.player == null || client.level == null
			|| client.getConnection() == null || !client.getConnection().isAcceptingMessages()) return -1L;
		return CONNECTION_GATE.permit(client.getConnection());
	}

	// Read client-owned state on the client thread. Only DRT's worker waits, at most 2s.
	private static long waitWhileConnecting(Minecraft client) {
		try {
			long generation = client.submit(() -> connectionGeneration(client)).get(2L, TimeUnit.SECONDS);
			if (generation >= 0L) return generation;
		} catch (InterruptedException exception) {
			Thread.currentThread().interrupt();
		} catch (java.util.concurrent.ExecutionException | java.util.concurrent.TimeoutException exception) {
			DungeonRunTracker.LOGGER.debug("DRT session proof deferred: client connection state unavailable");
		}
		setFailure(Failure.SERVER_CONNECTING, "Minecraft play connection is not ready or is settling");
		return -1L;
	}

	/** Loom {@code runClient} default token. Real Microsoft sessions must still attempt joinServer. */
	static boolean looksLikeOfflineDevSession(String accessToken) {
		if (accessToken == null || accessToken.isBlank()) {
			return true;
		}
		return "FabricMC".equalsIgnoreCase(accessToken.trim());
	}

	private static boolean causeEquals(String body, String cause) {
		if (body == null || body.isBlank() || cause == null) {
			return false;
		}
		try {
			JsonObject root = JsonParser.parseString(body).getAsJsonObject();
			return root.has("cause")
				&& root.get("cause").isJsonPrimitive()
				&& cause.equalsIgnoreCase(root.get("cause").getAsString());
		} catch (RuntimeException ignored) {
			return body.contains(cause);
		}
	}

	private static String requiredString(JsonObject object, String key) {
		if (!object.has(key) || !object.get(key).isJsonPrimitive()) {
			throw new IllegalArgumentException("Missing auth response field: " + key);
		}
		String value = object.get(key).getAsString();
		if (value == null || value.isBlank()) {
			throw new IllegalArgumentException("Blank auth response field: " + key);
		}
		return value;
	}

	private static String requestTranscript(String method, String path, String timestamp, String nonce, String token)
		throws Exception {
		MessageDigest digest = MessageDigest.getInstance("SHA-256");
		byte[] tokenHash = digest.digest(token.getBytes(StandardCharsets.UTF_8));
		String tokenHashHex = java.util.HexFormat.of().formatHex(tokenHash);
		return String.join("\n", "drt-request-v2", method, path, timestamp, nonce, tokenHashHex);
	}

	private static String escapeJson(String value) {
		return value
			.replace("\\", "\\\\")
			.replace("\"", "\\\"")
			.replace("\n", "\\n")
			.replace("\r", "\\r")
			.replace("\t", "\\t");
	}
}
