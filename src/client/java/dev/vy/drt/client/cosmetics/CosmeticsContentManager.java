package dev.vy.drt.client.cosmetics;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.vy.drt.DungeonRunTracker;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

public final class CosmeticsContentManager {
	private static final Gson GSON = new Gson();
	private static final String PEOPLE_RESOURCE_PATH = "drt/content/people.json";
	private static final String REMOTE_PEOPLE_URL = "https://api.vyriv.dev/v1/cosmetics";
	private static final HttpClient HTTP = HttpClient.newBuilder()
			.connectTimeout(Duration.ofSeconds(5))
			.build();
	private static final AtomicBoolean INITIALIZED = new AtomicBoolean();

	private static volatile PeopleContent bundledPeopleContent = new PeopleContent();
	private static volatile PeopleContent remotePeopleContent = new PeopleContent();

	private CosmeticsContentManager() {
	}

	public static void initialize() {
		if (!INITIALIZED.compareAndSet(false, true)) return;

		bundledPeopleContent = loadBundledPeople().normalized();
		PlayerCustomizationRegistry.initialize(playerCustomizations());

		CompletableFuture.runAsync(() -> {
			try {
				PeopleContent remote = fetchRemotePeopleContent();
				if (remote == null) return;
				remotePeopleContent = remote.normalized();
				PlayerCustomizationRegistry.initialize(playerCustomizations());
				NameStyler.clearCaches();
				CapeTextureManager.invalidateRemoteCapes();
				int totalEntries = remotePeopleContent.players.size() + remotePeopleContent.awesomePeople.size();
				DungeonRunTracker.LOGGER.info("[DRT] Loaded {} remote cosmetic player entr{} from startup refresh",
						totalEntries,
						totalEntries == 1 ? "y" : "ies");
			} catch (Exception e) {
				DungeonRunTracker.LOGGER.warn("[DRT] Failed to fetch startup cosmetic player data: {}", e.getMessage());
			}
		});
	}

	public static synchronized List<LoadedPlayerCustomization> playerCustomizations() {
		return mergedCustomizationEntries().stream()
				.map(CosmeticsContentManager::loadPlayerCustomization)
				.filter(customization -> customization != null)
				.toList();
	}

	private static PeopleContent loadBundledPeople() {
		var stream = CosmeticsContentManager.class.getClassLoader().getResourceAsStream(PEOPLE_RESOURCE_PATH);
		if (stream == null) return new PeopleContent();

		try (InputStreamReader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
			PeopleContent content = GSON.fromJson(reader, PeopleContent.class);
			return content == null ? new PeopleContent() : content;
		} catch (Exception e) {
			DungeonRunTracker.LOGGER.warn("[DRT] Failed to load bundled cosmetic player data: {}", e.getMessage());
			return new PeopleContent();
		}
	}

	private static PeopleContent fetchRemotePeopleContent() throws Exception {
		HttpRequest request = HttpRequest.newBuilder()
				.uri(cacheBustedUri())
				.timeout(Duration.ofSeconds(10))
				.header("accept", "application/json,*/*")
				.header("cache-control", "no-cache, no-store, max-age=0")
				.header("pragma", "no-cache")
				.GET()
				.build();
		HttpResponse<String> response = HTTP.send(request, HttpResponse.BodyHandlers.ofString());
		if (response.statusCode() != 200) {
			throw new IllegalStateException("unexpected HTTP " + response.statusCode());
		}
		return parsePeopleContent(response.body());
	}

	private static URI cacheBustedUri() {
		String separator = REMOTE_PEOPLE_URL.contains("?") ? "&" : "?";
		return URI.create(REMOTE_PEOPLE_URL + separator + "ts=" + System.currentTimeMillis());
	}

	private static PeopleContent parsePeopleContent(String rawBody) {
		String body = rawBody == null ? "" : rawBody.trim();
		if (body.isEmpty()) return new PeopleContent();

		JsonElement root = JsonParser.parseString(body);
		if (root.isJsonObject()) {
			JsonObject rootObject = root.getAsJsonObject();
			JsonElement content = rootObject.get("content");
			if (content != null && !content.isJsonNull()) {
				if (content.isJsonPrimitive() && content.getAsJsonPrimitive().isString()) {
					PeopleContent parsed = GSON.fromJson(content.getAsString(), PeopleContent.class);
					return parsed == null ? new PeopleContent() : parsed;
				}
				PeopleContent parsed = GSON.fromJson(content, PeopleContent.class);
				return parsed == null ? new PeopleContent() : parsed;
			}
		}
		PeopleContent parsed = GSON.fromJson(root, PeopleContent.class);
		return parsed == null ? new PeopleContent() : parsed;
	}

	private static LoadedPlayerCustomization loadPlayerCustomization(PlayerCustomizationFile entry) {
		if (entry == null) return null;
		LoadedNameStyle nameStyle = loadNameStyle(entry.style);
		LoadedBadge badge = entry.badge == null ? null : loadBadge(entry.badge);
		LoadedRankPrefix rankPrefix = entry.rankPrefix == null ? null : loadRankPrefix(entry.rankPrefix);
		return new LoadedPlayerCustomization(
			entry.username,
			blankToNull(entry.nickname),
			blankToNull(entry.uuid),
			entry.aliases == null ? List.of() : entry.aliases,
			nameStyle,
			badge,
			rankPrefix,
			blankToNull(entry.capeResourcePath),
			blankToNull(entry.capeUrl));
	}

	private static LoadedNameStyle loadNameStyle(NameStyleFile style) {
		if (style == null) return null;

		String normalizedMode = style.mode == null ? "inherit_rank" : style.mode.trim().toLowerCase(Locale.ROOT);
		Float animationSpeed = style.animationSpeed != null && style.animationSpeed > 0.0F ? style.animationSpeed : null;
		Integer animationSteps = style.animationSteps == null ? null : Math.max(2, style.animationSteps);
		float gradientSpacing = style.gradientFrequency != null ? style.gradientFrequency
				: style.gradientSpacing != null ? style.gradientSpacing : 1.0F;
		gradientSpacing = Math.max(1.0F, Math.min(10.0F, gradientSpacing));
		boolean animated = Boolean.TRUE.equals(style.animated);

		return switch (normalizedMode) {
			case "inherit_rank", "inherit-rank", "inherit" -> new LoadedNameStyle(LoadedNameStyle.Mode.INHERIT_RANK, 0xFFFFFF, 0xFFFFFF,
					style.bold, List.of(), animationSpeed, animationSteps, gradientSpacing);
			case "solid" -> {
				Integer color = parseColor(firstNonBlank(style.color, style.leftColor, style.rightColor));
				yield color == null ? null : new LoadedNameStyle(LoadedNameStyle.Mode.SOLID, color, color, style.bold, List.of(), animationSpeed,
						animationSteps, gradientSpacing);
			}
			case "gradient" -> {
				Integer left = parseColor(firstNonBlank(style.leftColor, style.color));
				Integer right = parseColor(firstNonBlank(style.rightColor, style.color));
				if (left == null || right == null) yield null;
				LoadedNameStyle.Mode mode = animated || animationSpeed != null ? LoadedNameStyle.Mode.ANIMATED_GRADIENT : LoadedNameStyle.Mode.GRADIENT;
				yield new LoadedNameStyle(mode, left, right, style.bold, List.of(), animationSpeed, animationSteps, gradientSpacing);
			}
			case "animated_gradient", "animated-gradient", "animatedgradient" -> {
				Integer left = parseColor(firstNonBlank(style.leftColor, style.color));
				Integer right = parseColor(firstNonBlank(style.rightColor, style.color));
				if (left == null || right == null) yield null;
				yield new LoadedNameStyle(LoadedNameStyle.Mode.ANIMATED_GRADIENT, left, right, style.bold, List.of(), animationSpeed, animationSteps,
						gradientSpacing);
			}
			case "multicolor" -> {
				List<Integer> colors = style.letterColors == null ? List.of() : style.letterColors.stream()
						.map(CosmeticsContentManager::parseColor)
						.filter(color -> color != null)
						.toList();
				yield colors.isEmpty() ? null : new LoadedNameStyle(LoadedNameStyle.Mode.MULTICOLOR, colors.get(0), colors.get(colors.size() - 1),
						style.bold, colors, animationSpeed, animationSteps, gradientSpacing);
			}
			default -> null;
		};
	}

	private static LoadedBadge loadBadge(BadgeFile badge) {
		String text = blankToNull(badge.text);
		Integer color = parseColor(badge.color);
		return text == null || color == null ? null : new LoadedBadge(text, color, badge.bold);
	}

	private static LoadedRankPrefix loadRankPrefix(RankPrefixFile rankPrefix) {
		String text = blankToNull(rankPrefix.text);
		if (text == null) return null;

		String normalizedMode = rankPrefix.mode == null ? "solid" : rankPrefix.mode.trim().toLowerCase(Locale.ROOT);
		Float animationSpeed = rankPrefix.animationSpeed != null && rankPrefix.animationSpeed > 0.0F ? rankPrefix.animationSpeed : null;
		Integer animationSteps = rankPrefix.animationSteps == null ? null : Math.max(2, rankPrefix.animationSteps);
		float gradientSpacing = rankPrefix.gradientFrequency != null ? rankPrefix.gradientFrequency
			: rankPrefix.gradientSpacing != null ? rankPrefix.gradientSpacing : 1.0F;
		gradientSpacing = Math.max(1.0F, Math.min(10.0F, gradientSpacing));
		boolean animated = Boolean.TRUE.equals(rankPrefix.animated);

		return switch (normalizedMode) {
			case "copy_name", "copy-name", "copyname" ->
				new LoadedRankPrefix(LoadedRankPrefix.Mode.COPY_NAME, text, 0xFFFFFF, 0xFFFFFF, rankPrefix.bold, animationSpeed, animationSteps, gradientSpacing);
			case "gradient" -> {
				Integer left = parseColor(firstNonBlank(rankPrefix.leftColor, rankPrefix.color));
				Integer right = parseColor(firstNonBlank(rankPrefix.rightColor, rankPrefix.color));
				if (left == null || right == null) yield null;
				LoadedRankPrefix.Mode mode = animated || animationSpeed != null
					? LoadedRankPrefix.Mode.ANIMATED_GRADIENT
					: LoadedRankPrefix.Mode.GRADIENT;
				yield new LoadedRankPrefix(mode, text, left, right, rankPrefix.bold, animationSpeed, animationSteps, gradientSpacing);
			}
			case "animated_gradient", "animated-gradient", "animatedgradient" -> {
				Integer left = parseColor(firstNonBlank(rankPrefix.leftColor, rankPrefix.color));
				Integer right = parseColor(firstNonBlank(rankPrefix.rightColor, rankPrefix.color));
				if (left == null || right == null) yield null;
				yield new LoadedRankPrefix(LoadedRankPrefix.Mode.ANIMATED_GRADIENT, text, left, right, rankPrefix.bold, animationSpeed, animationSteps, gradientSpacing);
			}
			case "solid" -> {
				Integer color = parseColor(firstNonBlank(rankPrefix.color, rankPrefix.leftColor, rankPrefix.rightColor));
				yield color == null ? null : new LoadedRankPrefix(LoadedRankPrefix.Mode.SOLID, text, color, color, rankPrefix.bold, animationSpeed, animationSteps, gradientSpacing);
			}
			default -> null;
		};
	}

	private static synchronized List<PlayerCustomizationFile> mergedCustomizationEntries() {
		List<String> orderedKeys = new ArrayList<>();
		Map<String, PlayerCustomizationFile> mergedEntries = new LinkedHashMap<>();
		List<List<PlayerCustomizationFile>> sections = List.of(
				bundledPeopleContent.players,
				bundledPeopleContent.awesomePeople,
				remotePeopleContent.players,
				remotePeopleContent.awesomePeople);

		for (List<PlayerCustomizationFile> section : sections) {
			for (PlayerCustomizationFile entry : section) {
				String key = existingCustomizationKey(entry, mergedEntries);
				if (key == null) key = identityKey(entry);
				if (key == null) continue;
				if (!orderedKeys.contains(key)) orderedKeys.add(key);
				PlayerCustomizationFile existing = mergedEntries.get(key);
				mergedEntries.put(key, existing == null ? entry : existing.mergedWith(entry));
			}
		}

		return orderedKeys.stream()
				.map(mergedEntries::get)
				.filter(entry -> entry != null)
				.toList();
	}

	private static String existingCustomizationKey(PlayerCustomizationFile entry, Map<String, PlayerCustomizationFile> mergedEntries) {
		String entryUuid = blankToNull(entry.uuid);
		String entryUsername = blankToNull(entry.username);
		for (Map.Entry<String, PlayerCustomizationFile> existingEntry : mergedEntries.entrySet()) {
			PlayerCustomizationFile existing = existingEntry.getValue();
			String existingUuid = blankToNull(existing.uuid);
			String existingUsername = blankToNull(existing.username);
			if (entryUuid != null && existingUuid != null && entryUuid.equalsIgnoreCase(existingUuid)) return existingEntry.getKey();
			if (entryUsername != null && existingUsername != null && entryUsername.equalsIgnoreCase(existingUsername)) return existingEntry.getKey();
		}
		return null;
	}

	private static String identityKey(PlayerCustomizationFile entry) {
		String uuid = blankToNull(entry.uuid);
		if (uuid != null) return uuid.toLowerCase(Locale.ROOT);
		String username = blankToNull(entry.username);
		return username == null ? null : username.toLowerCase(Locale.ROOT);
	}

	public static Integer parseColor(String raw) {
		if (raw == null || raw.isBlank()) return null;
		String normalized = raw.trim();
		if (normalized.startsWith("#")) normalized = normalized.substring(1);
		if (normalized.length() == 8) normalized = normalized.substring(2);
		if (normalized.length() != 6) return null;
		try {
			return Integer.parseInt(normalized, 16) & 0xFFFFFF;
		} catch (NumberFormatException ignored) {
			return null;
		}
	}

	private static String blankToNull(String value) {
		if (value == null) return null;
		String trimmed = value.trim();
		return trimmed.isEmpty() ? null : trimmed;
	}

	private static String firstNonBlank(String... values) {
		for (String value : values) {
			String normalized = blankToNull(value);
			if (normalized != null) return normalized;
		}
		return null;
	}

	public record LoadedPlayerCustomization(String username, String nickname, String uuid, List<String> aliases,
			LoadedNameStyle style, LoadedBadge badge, LoadedRankPrefix rankPrefix, String capeResourcePath, String capeUrl) {
	}

	public record LoadedNameStyle(Mode mode, int leftColor, int rightColor, boolean bold, List<Integer> letterColors,
	                              Float animationSpeed, Integer animationSteps, float gradientSpacing) {
		public enum Mode {
			INHERIT_RANK,
			SOLID,
			GRADIENT,
			ANIMATED_GRADIENT,
			MULTICOLOR
		}
	}

	public record LoadedBadge(String label, int color, boolean bold) {
	}

	public record LoadedRankPrefix(Mode mode, String label, int leftColor, int rightColor, boolean bold,
			Float animationSpeed, Integer animationSteps, float gradientSpacing) {
		public enum Mode {
			SOLID,
			GRADIENT,
			ANIMATED_GRADIENT,
			COPY_NAME
		}
	}

	private static final class PeopleContent {
		List<PlayerCustomizationFile> awesomePeople = new ArrayList<>();
		List<PlayerCustomizationFile> players = new ArrayList<>();

		PeopleContent normalized() {
			awesomePeople = normalizedEntries(awesomePeople);
			players = normalizedEntries(players);
			return this;
		}

		private static List<PlayerCustomizationFile> normalizedEntries(List<PlayerCustomizationFile> entries) {
			if (entries == null) return new ArrayList<>();
			return entries.stream()
					.map(PlayerCustomizationFile::normalized)
					.filter(entry -> entry != null)
					.toList();
		}
	}

	private static final class PlayerCustomizationFile {
		String username = "";
		String nickname;
		String uuid;
		List<String> aliases = new ArrayList<>();
		String creditLabel;
		String creditRole;
		NameStyleFile style;
		BadgeFile badge;
		RankPrefixFile rankPrefix;
		String capeResourcePath;
		String capeUrl;
		Float scale;
		Float scaleX;
		Float scaleY;
		Float scaleZ;
		Float widthBlocks;
		Float heightBlocks;
		Float depthBlocks;

		PlayerCustomizationFile normalized() {
			username = username == null ? "" : username.trim();
			uuid = blankToNull(uuid);
			if (username.isEmpty() && uuid == null) return null;
			aliases = aliases == null ? new ArrayList<>() : aliases.stream()
					.map(String::trim)
					.filter(alias -> !alias.isEmpty() && !alias.equalsIgnoreCase(username))
					.distinct()
					.toList();
			nickname = blankToNull(nickname);
			creditLabel = blankToNull(creditLabel);
			creditRole = blankToNull(creditRole);
			capeResourcePath = blankToNull(capeResourcePath);
			capeUrl = blankToNull(capeUrl);
			if (style != null) style.normalize();
			if (badge != null) badge.normalize();
			if (rankPrefix != null) rankPrefix.normalize();
			return this;
		}

		PlayerCustomizationFile mergedWith(PlayerCustomizationFile overlay) {
			PlayerCustomizationFile merged = new PlayerCustomizationFile();
			merged.username = blankToNull(overlay.username) != null ? overlay.username : username;
			merged.nickname = overlay.nickname != null ? overlay.nickname : nickname;
			merged.uuid = overlay.uuid != null ? overlay.uuid : uuid;
			merged.aliases = new ArrayList<>();
			merged.aliases.addAll(aliases == null ? List.of() : aliases);
			merged.aliases.addAll(overlay.aliases == null ? List.of() : overlay.aliases);
			merged.aliases = merged.aliases.stream()
					.map(String::trim)
					.filter(alias -> !alias.isEmpty() && !alias.equalsIgnoreCase(merged.username))
					.distinct()
					.toList();
			merged.creditLabel = overlay.creditLabel != null ? overlay.creditLabel : creditLabel;
			merged.creditRole = overlay.creditRole != null ? overlay.creditRole : creditRole;
			merged.style = overlay.style != null ? overlay.style : style;
			merged.badge = overlay.badge != null ? overlay.badge : badge;
			merged.rankPrefix = overlay.rankPrefix != null ? overlay.rankPrefix : rankPrefix;
			merged.capeResourcePath = overlay.capeResourcePath != null ? overlay.capeResourcePath : capeResourcePath;
			merged.capeUrl = overlay.capeUrl != null ? overlay.capeUrl : capeUrl;
			merged.scale = overlay.scale != null ? overlay.scale : scale;
			merged.scaleX = overlay.scaleX != null ? overlay.scaleX : scaleX;
			merged.scaleY = overlay.scaleY != null ? overlay.scaleY : scaleY;
			merged.scaleZ = overlay.scaleZ != null ? overlay.scaleZ : scaleZ;
			merged.widthBlocks = overlay.widthBlocks != null ? overlay.widthBlocks : widthBlocks;
			merged.heightBlocks = overlay.heightBlocks != null ? overlay.heightBlocks : heightBlocks;
			merged.depthBlocks = overlay.depthBlocks != null ? overlay.depthBlocks : depthBlocks;
			return merged;
		}
	}

	private static final class NameStyleFile {
		String mode = "inherit_rank";
		String color;
		String leftColor;
		String rightColor;
		List<String> letterColors;
		Boolean animated;
		Float animationSpeed;
		Integer animationSteps;
		Float gradientSpacing;
		Float gradientFrequency;
		boolean bold;

		void normalize() {
			mode = blankToNull(mode) == null ? "inherit_rank" : mode.trim();
			color = blankToNull(color);
			leftColor = blankToNull(leftColor);
			rightColor = blankToNull(rightColor);
			if (letterColors != null) {
				letterColors = letterColors.stream()
						.map(String::trim)
						.filter(value -> !value.isEmpty())
						.toList();
			}
		}
	}

	private static final class BadgeFile {
		String text;
		String color;
		boolean bold;

		void normalize() {
			text = blankToNull(text);
			color = blankToNull(color);
		}
	}

	private static final class RankPrefixFile {
		String text;
		String mode = "solid";
		String color;
		String leftColor;
		String rightColor;
		Boolean animated;
		Float animationSpeed;
		Integer animationSteps;
		Float gradientSpacing;
		Float gradientFrequency;
		boolean bold;

		void normalize() {
			text = blankToNull(text);
			mode = blankToNull(mode) == null ? "solid" : mode.trim();
			color = blankToNull(color);
			leftColor = blankToNull(leftColor);
			rightColor = blankToNull(rightColor);
		}
	}
}
