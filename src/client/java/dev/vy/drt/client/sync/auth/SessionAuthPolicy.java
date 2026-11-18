package dev.vy.drt.client.sync.auth;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;
import java.util.UUID;

/** Session auth decisions, kept free of Minecraft state so long sessions can be simulated in tests. */
final class SessionAuthPolicy {
	// The API refuses refreshes this close to the Mojang proof deadline.
	static final long MIN_RENEWABLE_MILLIS = 30_000L;
	private static final long RENEW_RETRY_MARGIN_MILLIS = 7_000L;
	private static final long[] RENEW_RETRY_MILLIS = {10_000L, 15_000L, 20_000L};
	// A launcher token that expired mid-game stays dead until the launcher signs in again,
	// so repeated joinServer calls only add Mojang load.
	private static final long[] INVALID_CREDENTIALS_BACKOFF_MILLIS = {60_000L, 300_000L, 900_000L, 1_800_000L};

	enum Action { USE_CACHED, RENEW, PROOF }

	enum RenewOutcome {
		RENEWED,
		/** Session can't be renewed, but the current JWT still works until it expires. */
		NEEDS_PROOF,
		/** Current JWT is no longer accepted. */
		SESSION_DEAD,
		BLOCKED,
		RETRY
	}

	private SessionAuthPolicy() {
	}

	static Action nextAction(boolean tokenValid, boolean refreshDue, long renewableForMillis) {
		if (tokenValid && !refreshDue) return Action.USE_CACHED;
		if (tokenValid && renewableForMillis >= MIN_RENEWABLE_MILLIS) return Action.RENEW;
		return Action.PROOF;
	}

	/** The Mojang proof cooldown only matters when no live JWT exists to sign the request. */
	static boolean resourceFetchAllowed(boolean tokenValid, long proofCooldownMillis) {
		return tokenValid || proofCooldownMillis <= 0L;
	}

	static RenewOutcome classifyRenewal(int status, String cause) {
		if (status >= 200 && status < 300) return RenewOutcome.RENEWED;
		// An API without the refresh route behaves like the old proof-only flow.
		if (status == 404 || status == 405) return RenewOutcome.NEEDS_PROOF;
		if (status == 401 || status == 403) {
			return switch (cause == null ? "" : cause) {
				case "session_reauth_required", "session_refresh_rejected" -> RenewOutcome.NEEDS_PROOF;
				case "session_revoked_or_expired", "unauthorized" -> RenewOutcome.SESSION_DEAD;
				case "access_restricted" -> RenewOutcome.BLOCKED;
				default -> RenewOutcome.RETRY;
			};
		}
		return RenewOutcome.RETRY;
	}

	/** Delay before retry number {@code failures}, or -1 when the JWT would expire first. */
	static long renewRetryDelayMillis(int failures, long tokenValidForMillis) {
		if (failures < 1 || failures > RENEW_RETRY_MILLIS.length) return -1L;
		long delay = RENEW_RETRY_MILLIS[failures - 1];
		return tokenValidForMillis - delay >= RENEW_RETRY_MARGIN_MILLIS ? delay : -1L;
	}

	/** In-memory identity for one launcher login. Never logged or sent anywhere. */
	static String credentialKey(UUID profileId, String accessToken) {
		if (profileId == null || accessToken == null || accessToken.isBlank()) return null;
		try {
			MessageDigest digest = MessageDigest.getInstance("SHA-256");
			byte[] hash = digest.digest((profileId + "\n" + accessToken).getBytes(StandardCharsets.UTF_8));
			return HexFormat.of().formatHex(hash);
		} catch (NoSuchAlgorithmException exception) {
			throw new IllegalStateException(exception);
		}
	}

	/** Escalating pause after Mojang rejects a login, reset when the account or token changes. */
	static final class CredentialBackoff {
		private String key;
		private int failures;

		long recordInvalid(String credentialKey) {
			if (!Objects.equals(key, credentialKey)) {
				key = credentialKey;
				failures = 0;
			}
			long delay = INVALID_CREDENTIALS_BACKOFF_MILLIS[Math.min(failures, INVALID_CREDENTIALS_BACKOFF_MILLIS.length - 1)];
			failures++;
			return delay;
		}

		boolean appliesTo(String credentialKey) {
			return key != null && key.equals(credentialKey);
		}

		int failures() {
			return failures;
		}

		void reset() {
			key = null;
			failures = 0;
		}
	}
}
