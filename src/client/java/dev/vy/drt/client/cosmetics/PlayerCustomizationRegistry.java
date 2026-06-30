package dev.vy.drt.client.cosmetics;

import com.mojang.authlib.GameProfile;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class PlayerCustomizationRegistry {
	private static volatile List<PlayerCustomization> loadedEntries = List.of();
	private static volatile Snapshot snapshot = Snapshot.empty();

	private PlayerCustomizationRegistry() {
	}

	public static synchronized void initialize(List<CosmeticsContentManager.LoadedPlayerCustomization> entries) {
		Map<String, PlayerCustomization> merged = new LinkedHashMap<>();
		for (CosmeticsContentManager.LoadedPlayerCustomization entry : entries) {
			PlayerCustomization customization = toCustomization(entry);
			if (customization == null) continue;
			String key = customization.uuid != null
				? "uuid:" + customization.uuid.toString().toLowerCase(Locale.ROOT)
				: "name:" + normalizedNameKey(customization.username);
			merged.putIfAbsent(key, customization);
		}
		loadedEntries = List.copyOf(merged.values());
		rebuildSnapshot();
	}

	public static long version() {
		return snapshot.version;
	}

	public static List<PlayerCustomization> entries() {
		return snapshot.entries;
	}

	public static List<NameCandidate> nameplateDisplayCandidates() {
		return snapshot.nameplateDisplayCandidates;
	}

	public static List<NameCandidate> chatHeaderNameCandidates() {
		return snapshot.chatHeaderNameCandidates;
	}

	public static List<NameCandidate> styledNameCandidates() {
		return snapshot.styledNameCandidates;
	}

	public static List<NameCandidate> gradientNameCandidates() {
		return snapshot.gradientNameCandidates;
	}

	public static List<NameCandidate> scoreboardDisplayNameCandidates() {
		return snapshot.scoreboardDisplayNameCandidates;
	}

	public static List<NameCandidate> scoreboardStyledNameCandidates() {
		return snapshot.scoreboardStyledNameCandidates;
	}

	public static List<NameCandidate> scoreboardGradientNameCandidates() {
		return snapshot.scoreboardGradientNameCandidates;
	}

	public static PlayerCustomization find(GameProfile profile) {
		if (profile == null) return null;
		Snapshot current = snapshot;
		UUID id = profile.id();
		if (id != null) {
			PlayerCustomization byUuid = current.byUuid.get(id);
			if (byUuid != null) return byUuid;
		}
		return findByName(profile.name());
	}

	public static PlayerCustomization findByName(String name) {
		String key = normalizedNameKey(name);
		return key == null ? null : snapshot.byName.get(key);
	}

	public static PlayerCustomization findWithCape(GameProfile profile) {
		PlayerCustomization customization = find(profile);
		return customization != null && customization.hasCapeCustomization() ? customization : null;
	}

	public static boolean hasCapeCustomizations() {
		return snapshot.hasCapeCustomizations;
	}

	private static synchronized void rebuildSnapshot() {
		List<PlayerCustomization> effectiveEntries = effectiveEntries();
		Map<String, PlayerCustomization> byName = new LinkedHashMap<>();
		Map<UUID, PlayerCustomization> byUuid = new LinkedHashMap<>();
		List<NameCandidate> allNameCandidates = new ArrayList<>();
		List<NameCandidate> nameplateDisplayCandidates = new ArrayList<>();
		List<NameCandidate> chatHeaderNameCandidates = new ArrayList<>();
		List<NameCandidate> styledNameCandidates = new ArrayList<>();
		List<NameCandidate> gradientNameCandidates = new ArrayList<>();
		List<NameCandidate> scoreboardDisplayNameCandidates = new ArrayList<>();
		List<NameCandidate> scoreboardStyledNameCandidates = new ArrayList<>();
		List<NameCandidate> scoreboardGradientNameCandidates = new ArrayList<>();

		for (PlayerCustomization customization : effectiveEntries) {
			if (customization.uuid != null) byUuid.putIfAbsent(customization.uuid, customization);

			List<String> names = customization.matchNames();
			for (String name : names) {
				String key = normalizedNameKey(name);
				if (key != null) byName.putIfAbsent(key, customization);
			}

			List<NameCandidate> exactCandidates = exactCandidates(customization, names);
			allNameCandidates.addAll(exactCandidates);
			if (customization.hasChatDisplayOverride()) {
				nameplateDisplayCandidates.addAll(exactCandidates);
				chatHeaderNameCandidates.addAll(exactCandidates);
				scoreboardDisplayNameCandidates.addAll(scoreboardCandidates(customization, names));
			}
			if (customization.hasNameCustomization()) {
				styledNameCandidates.addAll(exactCandidates);
				scoreboardStyledNameCandidates.addAll(scoreboardCandidates(customization, names));
			}
			if (customization.hasExplicitNameColors()) {
				gradientNameCandidates.addAll(exactCandidates);
				scoreboardGradientNameCandidates.addAll(scoreboardCandidates(customization, names));
			}
		}

		ComparatorByCandidateLength.sort(allNameCandidates);
		ComparatorByCandidateLength.sort(nameplateDisplayCandidates);
		ComparatorByCandidateLength.sort(chatHeaderNameCandidates);
		ComparatorByCandidateLength.sort(styledNameCandidates);
		ComparatorByCandidateLength.sort(gradientNameCandidates);
		ComparatorByCandidateLength.sort(scoreboardDisplayNameCandidates);
		ComparatorByCandidateLength.sort(scoreboardStyledNameCandidates);
		ComparatorByCandidateLength.sort(scoreboardGradientNameCandidates);

		snapshot = new Snapshot(
			snapshot.version + 1L,
			List.copyOf(effectiveEntries),
			Map.copyOf(byName),
			Map.copyOf(byUuid),
			List.copyOf(allNameCandidates),
			List.copyOf(nameplateDisplayCandidates),
			List.copyOf(chatHeaderNameCandidates),
			List.copyOf(styledNameCandidates),
			List.copyOf(gradientNameCandidates),
			List.copyOf(scoreboardDisplayNameCandidates),
			List.copyOf(scoreboardStyledNameCandidates),
			List.copyOf(scoreboardGradientNameCandidates),
			effectiveEntries.stream().anyMatch(PlayerCustomization::hasCapeCustomization));
	}

	private static List<NameCandidate> exactCandidates(PlayerCustomization customization, List<String> names) {
		Map<String, NameCandidate> candidates = new LinkedHashMap<>();
		for (String raw : names) {
			String name = raw == null ? "" : raw.trim();
			if (name.isEmpty()) continue;
			candidates.putIfAbsent(name.toLowerCase(Locale.ROOT), new NameCandidate(customization, name, false));
		}
		return List.copyOf(candidates.values());
	}

	private static List<NameCandidate> scoreboardCandidates(PlayerCustomization customization, List<String> names) {
		Map<String, NameCandidate> candidates = new LinkedHashMap<>();
		for (String raw : names) {
			String name = raw == null ? "" : raw.trim();
			if (name.isEmpty()) continue;
			candidates.putIfAbsent("exact:" + name.toLowerCase(Locale.ROOT), new NameCandidate(customization, name, false));
			if (name.length() < 8) continue;

			int minimumLength = Math.max(6, Math.max(name.length() - 4, (int) (name.length() * 0.7F)));
			for (int length = name.length() - 1; length >= minimumLength; length--) {
				String prefix = name.substring(0, length);
				candidates.putIfAbsent("prefix:" + prefix.toLowerCase(Locale.ROOT), new NameCandidate(customization, prefix, true));
			}
		}
		return List.copyOf(candidates.values());
	}

	private static List<PlayerCustomization> effectiveEntries() {
		Map<String, PlayerCustomization> merged = new LinkedHashMap<>();
		for (PlayerCustomization customization : loadedEntries) {
			Set<String> identities = customization.identityKeys();
			if (identities.isEmpty()) continue;

			String existingKey = null;
			for (Map.Entry<String, PlayerCustomization> existing : merged.entrySet()) {
				Set<String> existingIdentities = existing.getValue().identityKeys();
				for (String identity : identities) {
					if (existingIdentities.contains(identity)) {
						existingKey = existing.getKey();
						break;
					}
				}
				if (existingKey != null) break;
			}

			String key = existingKey == null ? identities.iterator().next() : existingKey;
			PlayerCustomization existing = merged.get(key);
			merged.put(key, existing == null ? customization : existing.mergedWith(customization));
		}
		return List.copyOf(merged.values());
	}

	private static PlayerCustomization toCustomization(CosmeticsContentManager.LoadedPlayerCustomization entry) {
		if (entry == null) return null;
		UUID uuid = parseUuid(entry.uuid());
		String username = entry.username() == null ? "" : entry.username().trim();
		if (username.isEmpty() && uuid == null) return null;

		NameColors colors = null;
		List<Integer> letterColors = List.of();
		boolean animated = false;
		if (entry.style() != null) {
			switch (entry.style().mode()) {
				case SOLID -> colors = NameColors.solid(entry.style().leftColor());
				case GRADIENT -> colors = new NameColors(entry.style().leftColor(), entry.style().rightColor(), entry.style().gradientSpacing());
				case ANIMATED_GRADIENT -> {
					colors = new NameColors(entry.style().leftColor(), entry.style().rightColor(), entry.style().gradientSpacing());
					animated = true;
				}
				case MULTICOLOR -> letterColors = entry.style().letterColors();
				case INHERIT_RANK -> {
				}
			}
		}

		NameBadge badge = entry.badge() == null ? null : new NameBadge(entry.badge().label(), entry.badge().color(), entry.badge().bold());
		return new PlayerCustomization(
			username,
			blankToNull(entry.nickname()),
			uuid,
			entry.aliases(),
			colors,
			letterColors,
			animated,
			entry.style() == null ? null : entry.style().animationSpeed(),
			entry.style() == null ? null : entry.style().animationSteps(),
			entry.style() != null && entry.style().bold(),
			badge,
			blankToNull(entry.capeResourcePath()),
			blankToNull(entry.capeUrl()));
	}

	private static UUID parseUuid(String raw) {
		if (raw == null || raw.isBlank()) return null;
		try {
			return UUID.fromString(raw.trim());
		} catch (IllegalArgumentException ignored) {
			return null;
		}
	}

	static String normalizedNameKey(String name) {
		if (name == null) return null;
		String trimmed = name.trim();
		return trimmed.isEmpty() ? null : trimmed.toLowerCase(Locale.ROOT);
	}

	private static String blankToNull(String value) {
		if (value == null) return null;
		String trimmed = value.trim();
		return trimmed.isEmpty() ? null : trimmed;
	}

	public record NameCandidate(PlayerCustomization customization, String value, boolean requiresBoundary) {
	}

	public record NameBadge(String label, int color, boolean bold) {
	}

	public record NameColors(int left, int right, float spacing) {
		static NameColors solid(int color) {
			return new NameColors(color, color, 1.0F);
		}
	}

	public record PlayerCustomization(String username, String nickname, UUID uuid, List<String> aliases,
			NameColors nameColors, List<Integer> nameLetterColors, boolean nameAnimated, Float nameAnimationSpeed,
			Integer nameAnimationSteps, boolean nameBold, NameBadge nameBadge, String capeResourcePath, String capeUrl) {
		public PlayerCustomization {
			aliases = aliases == null ? List.of() : aliases.stream()
				.filter(alias -> alias != null && !alias.isBlank())
				.map(String::trim)
				.distinct()
				.toList();
			nameLetterColors = nameLetterColors == null ? List.of() : List.copyOf(nameLetterColors);
		}

		public boolean hasExplicitNameColors() {
			return nameColors != null || !nameLetterColors.isEmpty();
		}

		public boolean hasBadge() {
			return nameBadge != null;
		}

		public boolean hasNickname() {
			return nickname != null && !nickname.isBlank();
		}

		public boolean hasDecorations() {
			return hasExplicitNameColors() || nameBold || hasBadge();
		}

		public boolean hasNameCustomization() {
			return hasDecorations();
		}

		public boolean hasChatDisplayOverride() {
			return hasDecorations() || hasNickname();
		}

		public boolean hasCapeCustomization() {
			return (capeResourcePath != null && !capeResourcePath.isBlank()) || (capeUrl != null && !capeUrl.isBlank());
		}

		public boolean animatedGradient() {
			return nameAnimated && nameColors != null && nameColors.left != nameColors.right;
		}

		public String displayName(String matchedName) {
			return nickname == null || nickname.isBlank() ? matchedName : nickname;
		}

		public List<String> matchNames() {
			List<String> names = new ArrayList<>();
			addName(names, username);
			addName(names, nickname);
			for (String alias : aliases) addName(names, alias);
			return List.copyOf(names);
		}

		Set<String> identityKeys() {
			Set<String> keys = new LinkedHashSet<>();
			if (uuid != null) keys.add("uuid:" + uuid.toString().toLowerCase(Locale.ROOT));
			addIdentityKey(keys, username);
			for (String alias : aliases) addIdentityKey(keys, alias);
			return keys;
		}

		PlayerCustomization mergedWith(PlayerCustomization overlay) {
			String resolvedUsername = overlay.username != null && !overlay.username.isBlank() ? overlay.username : username;
			UUID resolvedUuid = overlay.uuid != null ? overlay.uuid : uuid;
			String resolvedNickname = overlay.nickname != null && !overlay.nickname.isBlank() ? overlay.nickname : nickname;
			List<String> mergedAliases = new ArrayList<>();
			mergedAliases.addAll(aliases);
			mergedAliases.addAll(overlay.aliases);
			addName(mergedAliases, username);
			addName(mergedAliases, overlay.username);
			mergedAliases = mergedAliases.stream()
				.map(String::trim)
				.filter(alias -> !alias.isEmpty() && !alias.equalsIgnoreCase(resolvedUsername))
				.distinct()
				.toList();

			return new PlayerCustomization(
				resolvedUsername,
				resolvedNickname,
				resolvedUuid,
				mergedAliases,
				overlay.nameColors != null ? overlay.nameColors : nameColors,
				!overlay.nameLetterColors.isEmpty() ? overlay.nameLetterColors : nameLetterColors,
				overlay.nameAnimated || nameAnimated,
				overlay.nameAnimationSpeed != null ? overlay.nameAnimationSpeed : nameAnimationSpeed,
				overlay.nameAnimationSteps != null ? overlay.nameAnimationSteps : nameAnimationSteps,
				overlay.nameBold || nameBold,
				overlay.nameBadge != null ? overlay.nameBadge : nameBadge,
				overlay.capeResourcePath != null ? overlay.capeResourcePath : capeResourcePath,
				overlay.capeUrl != null ? overlay.capeUrl : capeUrl);
		}

		private static void addName(List<String> names, String value) {
			if (value == null || value.isBlank()) return;
			String trimmed = value.trim();
			for (String existing : names) {
				if (existing.equalsIgnoreCase(trimmed)) return;
			}
			names.add(trimmed);
		}

		private static void addIdentityKey(Set<String> keys, String value) {
			String normalized = normalizedNameKey(value);
			if (normalized != null) keys.add("name:" + normalized);
		}
	}

	private record Snapshot(long version, List<PlayerCustomization> entries, Map<String, PlayerCustomization> byName,
			Map<UUID, PlayerCustomization> byUuid, List<NameCandidate> allNameCandidates,
			List<NameCandidate> nameplateDisplayCandidates, List<NameCandidate> chatHeaderNameCandidates,
			List<NameCandidate> styledNameCandidates, List<NameCandidate> gradientNameCandidates,
			List<NameCandidate> scoreboardDisplayNameCandidates, List<NameCandidate> scoreboardStyledNameCandidates,
			List<NameCandidate> scoreboardGradientNameCandidates, boolean hasCapeCustomizations) {
		static Snapshot empty() {
			return new Snapshot(0L, List.of(), Map.of(), Map.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), false);
		}
	}

	private static final class ComparatorByCandidateLength {
		static void sort(List<NameCandidate> candidates) {
			candidates.sort((left, right) -> Integer.compare(right.value().length(), left.value().length()));
		}
	}
}
