package dev.vy.drt.client;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;

/**
 * One combined update notice per session for the Vyaddons mods (BetterPV, DRT). Both mods ship
 * an identical copy of this class; the first to initialize claims the check through a JVM system
 * property and reports on every installed Vyaddons mod, so the message is never printed twice.
 * Notification only: any network or parse failure is silently ignored.
 */
public final class VyaddonsUpdateChecker {
	private static final String CLAIM_PROPERTY = "vyaddons.updateChecker";
	private static final String SELF = "drt";
	private static final Duration TIMEOUT = Duration.ofSeconds(3);
	private static final String USER_AGENT = "Vyaddons-Update-Checker";
	private static final int HEADER_COLOR = 0xAAAAAA;
	private static final int UP_TO_DATE_COLOR = 0x55FF55;
	private static final List<Target> TARGETS = List.of(
		new Target("betterpv", "BetterPV", "Vyriv/BetterPV", "https://modrinth.com/mod/betterpv", 0xC9A7FF),
		new Target("drt", "DRT", "Vyriv/DRT", "https://modrinth.com/mod/drt", 0xFF8A8A)
	);
	private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(TIMEOUT).build();

	private static CompletableFuture<List<Result>> pending;
	private static boolean shown;

	private VyaddonsUpdateChecker() {
	}

	public static void initialize() {
		if (System.getProperties().putIfAbsent(CLAIM_PROPERTY, SELF) != null) {
			return;
		}
		List<CompletableFuture<Result>> checks = new ArrayList<>();
		for (Target target : TARGETS) {
			Optional<ModContainer> mod = FabricLoader.getInstance().getModContainer(target.modId());
			if (mod.isEmpty()) {
				continue;
			}
			String installed = mod.get().getMetadata().getVersion().getFriendlyString();
			checks.add(fetchLatest(target).thenApply(latest -> new Result(target, installed, latest)));
		}
		if (checks.isEmpty()) {
			return;
		}
		pending = CompletableFuture.allOf(checks.toArray(CompletableFuture[]::new))
			.thenApply(ignored -> checks.stream().map(CompletableFuture::join).toList());
		ClientTickEvents.END_CLIENT_TICK.register(VyaddonsUpdateChecker::tick);
	}

	private static void tick(Minecraft client) {
		if (shown || pending == null || !pending.isDone() || client.player == null || client.level == null || client.gui == null) {
			return;
		}
		shown = true;
		for (Component line : buildLines(pending.join())) {
			//? if >= 26.1 {
			client.player.sendSystemMessage(line);
			//? } else {
			/*client.player.displayClientMessage(line, false);
			*///?}
		}
	}

	private static CompletableFuture<String> fetchLatest(Target target) {
		try {
			HttpRequest request = HttpRequest.newBuilder(URI.create("https://api.github.com/repos/" + target.repo() + "/releases/latest"))
				.timeout(TIMEOUT)
				.header("User-Agent", USER_AGENT)
				.header("Accept", "application/vnd.github+json")
				.GET()
				.build();
			return HTTP.sendAsync(request, HttpResponse.BodyHandlers.ofString())
				.thenApply(VyaddonsUpdateChecker::parseTag)
				.exceptionally(error -> null);
		} catch (RuntimeException exception) {
			return CompletableFuture.completedFuture(null);
		}
	}

	// releases/latest already skips drafts and prereleases; the flags are checked anyway.
	private static String parseTag(HttpResponse<String> response) {
		if (response.statusCode() != 200 || response.body() == null) {
			return null;
		}
		JsonElement parsed = JsonParser.parseString(response.body());
		if (!parsed.isJsonObject()) {
			return null;
		}
		JsonObject release = parsed.getAsJsonObject();
		if (flag(release, "draft") || flag(release, "prerelease")) {
			return null;
		}
		JsonElement tag = release.get("tag_name");
		return tag != null && tag.isJsonPrimitive() ? tag.getAsString() : null;
	}

	private static boolean flag(JsonObject object, String key) {
		JsonElement value = object.get(key);
		return value != null && value.isJsonPrimitive() && value.getAsBoolean();
	}

	private static List<Component> buildLines(List<Result> results) {
		List<Result> outdated = new ArrayList<>();
		boolean unknown = false;
		for (Result result : results) {
			Integer cmp = result.latest() == null ? null : compareVersions(result.installed(), result.latest());
			if (cmp == null) {
				unknown = true;
			} else if (cmp < 0) {
				outdated.add(result);
			}
		}
		if (outdated.isEmpty() && unknown) {
			return List.of();
		}
		List<Component> lines = new ArrayList<>();
		lines.add(Component.literal("Vyaddons update checker:").withColor(HEADER_COLOR));
		if (outdated.isEmpty()) {
			lines.add(Component.literal("All up to date :)").withColor(UP_TO_DATE_COLOR));
			return lines;
		}
		for (Result result : outdated) {
			lines.add(outdatedLine(result));
		}
		return lines;
	}

	private static Component outdatedLine(Result result) {
		Target target = result.target();
		MutableComponent button = Component.literal("[Get new update]").setStyle(Style.EMPTY
			.withColor(target.color())
			.withBold(true)
			.withClickEvent(new ClickEvent.OpenUrl(URI.create(target.modrinthUrl())))
			.withHoverEvent(new HoverEvent.ShowText(Component.literal("Open " + target.label() + " on Modrinth"))));
		return Component.literal(target.label() + " new version available (Current " + displayVersion(result.installed())
				+ ", latest " + displayVersion(result.latest()) + ") ")
			.withColor(target.color())
			.append(button);
	}

	private static String displayVersion(String raw) {
		String normalized = normalizeVersion(raw);
		int plus = normalized.indexOf('+');
		return plus >= 0 ? normalized.substring(0, plus) : normalized;
	}

	// Strips whitespace and a leading "v"; installed versions also carry "+<mc version>" build metadata.
	static String normalizeVersion(String raw) {
		String value = raw == null ? "" : raw.trim();
		if (value.startsWith("v") || value.startsWith("V")) {
			value = value.substring(1).trim();
		}
		return value;
	}

	/** Numeric comparison ("1.0.10" > "1.0.9"). Null when either side isn't a plain dotted version. */
	static Integer compareVersions(String installedRaw, String latestRaw) {
		String installed = displayVersion(installedRaw);
		String latest = displayVersion(latestRaw);
		String installedPre = suffix(installed);
		String latestPre = suffix(latest);
		int[] a = numericParts(core(installed));
		int[] b = numericParts(core(latest));
		if (a == null || b == null) {
			return null;
		}
		for (int i = 0; i < Math.max(a.length, b.length); i++) {
			int x = i < a.length ? a[i] : 0;
			int y = i < b.length ? b[i] : 0;
			if (x != y) {
				return Integer.compare(x, y);
			}
		}
		// Same numbers: a prerelease ("1.0.3-beta") is older than the matching release.
		if (installedPre.isEmpty() == latestPre.isEmpty()) {
			return 0;
		}
		return installedPre.isEmpty() ? 1 : -1;
	}

	private static String core(String version) {
		int dash = version.indexOf('-');
		return dash >= 0 ? version.substring(0, dash) : version;
	}

	private static String suffix(String version) {
		int dash = version.indexOf('-');
		return dash >= 0 ? version.substring(dash + 1).toLowerCase(Locale.ROOT) : "";
	}

	private static int[] numericParts(String version) {
		if (version.isEmpty()) {
			return null;
		}
		String[] parts = version.split("\\.");
		int[] out = new int[parts.length];
		for (int i = 0; i < parts.length; i++) {
			if (!parts[i].matches("\\d{1,9}")) {
				return null;
			}
			out[i] = Integer.parseInt(parts[i]);
		}
		return out;
	}

	private record Target(String modId, String label, String repo, String modrinthUrl, int color) {
	}

	private record Result(Target target, String installed, String latest) {
	}
}
