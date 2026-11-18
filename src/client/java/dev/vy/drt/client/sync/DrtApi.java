package dev.vy.drt.client.sync;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.time.Duration;

/** Shared HTTP plumbing for DRT's api.vyriv.dev calls. */
final class DrtApi {
	/** Overridable with -Ddrt.api.url=http://localhost:3000 for local testing. */
	static final String BASE_URL = System.getProperty("drt.api.url", "https://api.vyriv.dev").replaceAll("/+$", "");
	static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
	private static final Duration TIMEOUT = Duration.ofSeconds(15);

	private DrtApi() {
	}

	static HttpRequest post(URI uri, String json, String bearerToken) {
		return send(uri, "POST", json, bearerToken);
	}

	static HttpRequest send(URI uri, String method, String json, String bearerToken) {
		HttpRequest.Builder builder = HttpRequest.newBuilder(uri)
			.timeout(TIMEOUT)
			.header("Content-Type", "application/json")
			.header("Accept", "application/json")
			.header("User-Agent", "DRT/" + DrtAccount.modVersion());
		if (!dev.vy.drt.client.sync.auth.DrtSessionAuth.applyAuthHeaders(builder, uri, method)) {
            dev.vy.drt.client.sync.auth.DrtSessionAuth.prefetchAsync();
            throw new IllegalStateException("Minecraft account authentication is pending");
        }
		HttpRequest.BodyPublisher body = json == null
			? HttpRequest.BodyPublishers.noBody()
			: HttpRequest.BodyPublishers.ofString(json);
		return builder.method(method, body).build();
	}
}
