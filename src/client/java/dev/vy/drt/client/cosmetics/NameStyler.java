package dev.vy.drt.client.cosmetics;

import com.mojang.authlib.GameProfile;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;

public final class NameStyler {
	private static final int TEXT_CACHE_LIMIT = 4096;
	private static final int STRING_CACHE_LIMIT = 2048;
	private static final int MATCH_CACHE_LIMIT = 4096;
	private static final int IDENTITY_CACHE_SIZE = 2048;
	private static final int ANIMATED_GRADIENT_STEPS = 120;
	private static final float ANIMATED_GRADIENT_SPEED = 0.04F;
	private static final int LARGE_LOBBY_ANIMATION_THRESHOLD = 40;
	private static final float THROTTLED_ANIMATION_FPS = 15.0F;
	private static final Pattern TRAILING_BRACKET_PREFIX = Pattern.compile("(\\[[^\\]]+])(\\s*)$");
	/** SkyBlock tab/chat level tags like [435] or [1,234] — never treat these as Hypixel ranks. */
	private static final Pattern SKYBLOCK_LEVEL_INNER = Pattern.compile("^\\d[\\d,]*$");

	private static final NameStylerLruCache<ColorizedCacheKey, Component> COLORIZED_TEXT_CACHE = new NameStylerLruCache<>(TEXT_CACHE_LIMIT);
	private static final NameStylerLruCache<AnimatedGradientCacheKey, AnimatedGradientStyle> ANIMATED_GRADIENT_CACHE = new NameStylerLruCache<>(512);
	private static final NameStylerLruCache<TextCacheKey, Component> TEXT_TRANSFORM_CACHE = new NameStylerLruCache<>(TEXT_CACHE_LIMIT);
	private static final NameStylerLruCache<StringCacheKey, String> STRING_TRANSFORM_CACHE = new NameStylerLruCache<>(STRING_CACHE_LIMIT);
	private static final NameStylerLruCache<OrderedTextPlanCacheKey, OrderedTextTransformPlan> ORDERED_TEXT_PLAN_CACHE = new NameStylerLruCache<>(MATCH_CACHE_LIMIT);
	private static final NameStylerLruCache<OrderedTextResultCacheKey, FormattedCharSequence> ORDERED_TEXT_STATIC_RESULT_CACHE = new NameStylerLruCache<>(TEXT_CACHE_LIMIT);
	private static final NameStylerLruCache<OrderedTextAnimatedFrameCacheKey, FormattedCharSequence> ORDERED_TEXT_ANIMATED_FRAME_CACHE = new NameStylerLruCache<>(MATCH_CACHE_LIMIT);

	private static final NameStylerIdentityCache<Component, Component> GRADIENT_TEXT_IDENTITY_CACHE = new NameStylerIdentityCache<>(IDENTITY_CACHE_SIZE);
	private static final NameStylerIdentityCache<Component, Component> NAMEPLATE_TEXT_IDENTITY_CACHE = new NameStylerIdentityCache<>(IDENTITY_CACHE_SIZE);
	private static final NameStylerIdentityCache<Component, Component> SCOREBOARD_TEXT_IDENTITY_CACHE = new NameStylerIdentityCache<>(IDENTITY_CACHE_SIZE);
	private static final NameStylerIdentityCache<Component, Component> CHAT_HEADER_TEXT_IDENTITY_CACHE = new NameStylerIdentityCache<>(IDENTITY_CACHE_SIZE);
	private static final NameStylerIdentityCache<FormattedCharSequence, FormattedCharSequence> GRADIENT_ORDERED_TEXT_IDENTITY_CACHE = new NameStylerIdentityCache<>(IDENTITY_CACHE_SIZE);
	private static final NameStylerIdentityCache<FormattedCharSequence, FormattedCharSequence> NAMEPLATE_ORDERED_TEXT_IDENTITY_CACHE = new NameStylerIdentityCache<>(IDENTITY_CACHE_SIZE);
	private static final NameStylerIdentityCache<FormattedCharSequence, FormattedCharSequence> SCOREBOARD_ORDERED_TEXT_IDENTITY_CACHE = new NameStylerIdentityCache<>(IDENTITY_CACHE_SIZE);
	private static final NameStylerIdentityCache<FormattedCharSequence, OrderedTextSourceData> ORDERED_TEXT_SOURCE_CACHE = new NameStylerIdentityCache<>(IDENTITY_CACHE_SIZE);

	private static volatile long observedRegistryVersion = Long.MIN_VALUE;

	private NameStyler() {
	}

	public static void clearCaches() {
		COLORIZED_TEXT_CACHE.clearCache();
		ANIMATED_GRADIENT_CACHE.clearCache();
		TEXT_TRANSFORM_CACHE.clearCache();
		STRING_TRANSFORM_CACHE.clearCache();
		ORDERED_TEXT_PLAN_CACHE.clearCache();
		ORDERED_TEXT_STATIC_RESULT_CACHE.clearCache();
		ORDERED_TEXT_ANIMATED_FRAME_CACHE.clearCache();
		GRADIENT_TEXT_IDENTITY_CACHE.clear();
		NAMEPLATE_TEXT_IDENTITY_CACHE.clear();
		SCOREBOARD_TEXT_IDENTITY_CACHE.clear();
		CHAT_HEADER_TEXT_IDENTITY_CACHE.clear();
		GRADIENT_ORDERED_TEXT_IDENTITY_CACHE.clear();
		NAMEPLATE_ORDERED_TEXT_IDENTITY_CACHE.clear();
		SCOREBOARD_ORDERED_TEXT_IDENTITY_CACHE.clear();
		ORDERED_TEXT_SOURCE_CACHE.clear();
	}

	public static boolean hasGradientStyles() {
		checkRegistryVersion();
		return !PlayerCustomizationRegistry.gradientNameCandidates().isEmpty();
	}

	public static boolean hasChatHeaderStyles() {
		checkRegistryVersion();
		return !PlayerCustomizationRegistry.chatHeaderNameCandidates().isEmpty();
	}

	public static boolean hasDisplayProfile(GameProfile profile) {
		PlayerCustomizationRegistry.PlayerCustomization customization = PlayerCustomizationRegistry.find(profile);
		return customization != null && customization.hasChatDisplayOverride();
	}

	public static boolean hasAnimatedStyledProfile(GameProfile profile) {
		PlayerCustomizationRegistry.PlayerCustomization customization = PlayerCustomizationRegistry.find(profile);
		return customization != null && customization.animatedGradient();
	}

	public static Component styleEntityName(Component current) {
		return current == null ? null : applyNameplateDecorations(current);
	}

	public static Component applyNameplateDecorations(Component message) {
		return applyCachedTextTransform(message, TransformKind.NAMEPLATE_TEXT, NAMEPLATE_TEXT_IDENTITY_CACHE);
	}

	public static Component applyNameplateDisplayDecorations(Component message) {
		return applyCachedTextTransform(message, TransformKind.NAMEPLATE_DISPLAY_TEXT, NAMEPLATE_TEXT_IDENTITY_CACHE);
	}

	public static String applyNameplateDisplayDecorationsToString(String raw) {
		return applyDecorationsToString(raw, TransformKind.NAMEPLATE_DISPLAY_TEXT);
	}

	public static Component applyScoreboardDecorations(Component message) {
		return applyCachedTextTransform(message, TransformKind.SCOREBOARD_TEXT, SCOREBOARD_TEXT_IDENTITY_CACHE);
	}

	public static Component applyScoreboardDisplayDecorations(Component message) {
		return applyCachedTextTransform(message, TransformKind.SCOREBOARD_DISPLAY_TEXT, SCOREBOARD_TEXT_IDENTITY_CACHE);
	}

	public static String applyScoreboardDisplayDecorationsToString(String raw) {
		return applyDecorationsToString(raw, TransformKind.SCOREBOARD_DISPLAY_TEXT);
	}

	public static Component applyGradientToName(Component message) {
		return applyCachedTextTransform(message, TransformKind.GRADIENT_TEXT, GRADIENT_TEXT_IDENTITY_CACHE);
	}

	public static Component applyGradientToChatHeader(Component message) {
		return applyCachedTextTransform(message, TransformKind.CHAT_HEADER_TEXT, CHAT_HEADER_TEXT_IDENTITY_CACHE);
	}

	public static FormattedText applyGradientToFormattedText(FormattedText message) {
		if (message == null) return null;
		if (message instanceof Component component) {
			return applyGradientToName(component);
		}
		return applyGradientToName(rebuildFormattedText(message));
	}

	public static FormattedText applyChatHeaderToFormattedText(FormattedText message) {
		if (message == null) return null;
		if (message instanceof Component component) {
			return applyGradientToChatHeader(component);
		}
		return applyGradientToChatHeader(rebuildFormattedText(message));
	}

	public static FormattedCharSequence applyGradientToOrderedText(FormattedCharSequence text) {
		return applyCachedOrderedTextTransform(text, TransformKind.GRADIENT_TEXT, GRADIENT_ORDERED_TEXT_IDENTITY_CACHE);
	}

	public static FormattedCharSequence applyChatHeaderToOrderedText(FormattedCharSequence text) {
		return applyCachedOrderedTextTransform(text, TransformKind.CHAT_HEADER_TEXT, GRADIENT_ORDERED_TEXT_IDENTITY_CACHE);
	}

	public static String applyChatHeaderToString(String raw) {
		return applyDecorationsToString(raw, TransformKind.CHAT_HEADER_TEXT);
	}

	public static FormattedCharSequence applyNameplateDecorations(FormattedCharSequence text) {
		return applyCachedOrderedTextTransform(text, TransformKind.NAMEPLATE_DISPLAY_TEXT, NAMEPLATE_ORDERED_TEXT_IDENTITY_CACHE);
	}

	public static FormattedCharSequence applyScoreboardDecorations(FormattedCharSequence text) {
		return applyCachedOrderedTextTransform(text, TransformKind.SCOREBOARD_DISPLAY_TEXT, SCOREBOARD_ORDERED_TEXT_IDENTITY_CACHE);
	}

	public static String applyGradientToString(String raw) {
		return applyDecorationsToString(raw, TransformKind.GRADIENT_TEXT);
	}

	public static boolean hasAnimatedGradientInOrderedText(FormattedCharSequence text) {
		if (text == null || !hasGradientStyles()) return false;
		OrderedTextSourceData source = orderedTextSource(text);
		OrderedTextTransformPlan plan = buildOrderedTextPlan(source, TransformKind.GRADIENT_TEXT);
		return plan != null && plan.hasAnimatedGradient;
	}

	public static long currentOrderedTextAnimationFrameIndex() {
		return currentAnimationFrameIndex(currentAnimationTime());
	}

	private static Component applyCachedTextTransform(Component message, TransformKind kind, NameStylerIdentityCache<Component, Component> identityCache) {
		if (message == null) return null;
		checkRegistryVersion();
		if (candidatesForKind(kind).isEmpty()) return message;

		Component identityCached = identityCache.get(message);
		if (identityCached != null) return identityCached;

		List<StyledRun> runs = collectRuns(message);
		String plain = runsToPlain(runs);
		if (plain.isEmpty()) {
			identityCache.put(message, message);
			return message;
		}

		long frame = hasAnimatedGradientMatch(plain, candidatesForKind(kind)) ? currentAnimationFrameIndex(currentAnimationTime()) : Long.MIN_VALUE;
		TextCacheKey key = new TextCacheKey(PlayerCustomizationRegistry.version(), kind, plain, styleHash(runs), frame);
		Component cached = TEXT_TRANSFORM_CACHE.getCached(key);
		if (cached != null) {
			identityCache.put(message, cached);
			return cached;
		}

		OrderedTextTransformPlan plan = buildTransformPlan(runs, plain, kind);
		Component transformed = plan == null ? message : rebuildComponentFromPlan(new OrderedTextSourceData(plain, runs, styleHash(runs)), plan, currentAnimationTime(), kind);
		TEXT_TRANSFORM_CACHE.putCached(key, transformed);
		identityCache.put(message, transformed);
		return transformed;
	}

	private static FormattedCharSequence applyCachedOrderedTextTransform(FormattedCharSequence text, TransformKind kind,
			NameStylerIdentityCache<FormattedCharSequence, FormattedCharSequence> identityCache) {
		if (text == null) return null;
		checkRegistryVersion();
		if (candidatesForKind(kind).isEmpty()) return text;

		FormattedCharSequence identityCached = identityCache.get(text);
		if (identityCached != null) return identityCached;

		OrderedTextSourceData source = orderedTextSource(text);
		if (source.plain.isEmpty()) {
			identityCache.put(text, text);
			return text;
		}

		OrderedTextTransformPlan plan = buildOrderedTextPlan(source, kind);
		if (plan == null) {
			identityCache.put(text, text);
			return text;
		}

		FormattedCharSequence transformed;
		double animationTime = plan.hasAnimatedGradient ? currentAnimationTime() : 0.0D;
		if (plan.hasAnimatedGradient) {
			long frame = currentAnimationFrameIndex(animationTime);
			OrderedTextAnimatedFrameCacheKey key = new OrderedTextAnimatedFrameCacheKey(PlayerCustomizationRegistry.version(), kind, source.plain,
				source.styleHash, frame);
			transformed = ORDERED_TEXT_ANIMATED_FRAME_CACHE.getCached(key);
			if (transformed == null) {
				transformed = rebuildComponentFromPlan(source, plan, animationTime, kind).getVisualOrderText();
				ORDERED_TEXT_ANIMATED_FRAME_CACHE.putCached(key, transformed);
			}
		} else {
			OrderedTextResultCacheKey key = new OrderedTextResultCacheKey(PlayerCustomizationRegistry.version(), kind, source.plain, source.styleHash);
			transformed = ORDERED_TEXT_STATIC_RESULT_CACHE.getCached(key);
			if (transformed == null) {
				transformed = rebuildComponentFromPlan(source, plan, 0.0D, kind).getVisualOrderText();
				ORDERED_TEXT_STATIC_RESULT_CACHE.putCached(key, transformed);
			}
		}

		identityCache.put(text, transformed);
		return transformed;
	}

	private static String applyDecorationsToString(String raw, TransformKind kind) {
		if (raw == null || raw.isEmpty()) return raw;
		checkRegistryVersion();
		List<PlayerCustomizationRegistry.NameCandidate> candidates = candidatesForKind(kind);
		if (candidates.isEmpty()) return raw;

		long frame = hasAnimatedGradientMatch(raw, candidates) ? currentAnimationFrameIndex(currentAnimationTime()) : Long.MIN_VALUE;
		StringCacheKey key = new StringCacheKey(PlayerCustomizationRegistry.version(), kind, raw, frame);
		String cached = STRING_TRANSFORM_CACHE.getCached(key);
		if (cached != null) return cached;

		String transformed = applyDecorationsToStringUncached(raw, kind, candidates);
		STRING_TRANSFORM_CACHE.putCached(key, transformed);
		return transformed;
	}

	private static String applyDecorationsToStringUncached(String raw, TransformKind kind, List<PlayerCustomizationRegistry.NameCandidate> candidates) {
		StringBuilder output = new StringBuilder(raw.length() + 32);
		int index = 0;
		boolean changed = false;
		int matchBoundary = kind == TransformKind.CHAT_HEADER_TEXT ? chatHeaderBoundary(raw) : raw.length();
		while (index < matchBoundary) {
			NameStyleMatcher.MatchedCustomization match = NameStyleMatcher.findFirstNameMatch(raw, candidates, index);
			if (match == null || match.index() >= matchBoundary) break;

			PlayerCustomizationRegistry.PlayerCustomization customization = match.customization();
			String matchedText = raw.substring(match.index(), match.index() + match.matchedName().length());
			String displayText = kind.replaceMatchedName ? customization.displayName(matchedText) : matchedText;
			RankPrefixReplacement rankReplacement = resolveRankPrefixReplacement(raw.substring(index, match.index()), customization);

			if (rankReplacement != null) {
				output.append(rankReplacement.before());
				if (rankReplacement.copyName()) {
					output.append(toLegacyStyledName(rankReplacement.replacement() + displayText, customization));
				} else {
					output.append(toLegacyStyledRank(rankReplacement.replacement(), customization));
					output.append(toLegacyStyledName(displayText, customization));
				}
				changed = true;
			} else {
				if (match.index() > index) output.append(raw, index, match.index());
				output.append(toLegacyStyledName(displayText, customization));
				changed = true;
			}

			if (kind.includeBadges && customization.hasBadge()
				&& !hasPlainBadgeImmediatelyAfter(raw, match.index() + match.matchedName().length(), customization.nameBadge().label())
				&& !(kind.terminalBadgesOnly && hasPlainVisibleContentAfter(raw, match.index() + match.matchedName().length()))) {
				output.append(' ');
				output.append(buildLegacyHexColorCode(customization.nameBadge().color()));
				if (customization.nameBadge().bold()) output.append('§').append('l');
				output.append(customization.nameBadge().label());
			}
			index = match.index() + match.matchedName().length();
		}

		if (!changed) return raw;
		output.append(raw.substring(index));
		return output.toString();
	}

	private static OrderedTextSourceData orderedTextSource(FormattedCharSequence text) {
		OrderedTextSourceData cached = ORDERED_TEXT_SOURCE_CACHE.get(text);
		if (cached != null) return cached;

		List<StyledRun> runs = collectRuns(text);
		OrderedTextSourceData source = new OrderedTextSourceData(runsToPlain(runs), runs, styleHash(runs));
		ORDERED_TEXT_SOURCE_CACHE.put(text, source);
		return source;
	}

	private static OrderedTextTransformPlan buildOrderedTextPlan(OrderedTextSourceData source, TransformKind kind) {
		OrderedTextPlanCacheKey key = new OrderedTextPlanCacheKey(PlayerCustomizationRegistry.version(), kind, source.plain, source.styleHash);
		OrderedTextTransformPlan cached = ORDERED_TEXT_PLAN_CACHE.getCached(key);
		if (cached != null) return cached == OrderedTextTransformPlan.EMPTY ? null : cached;

		OrderedTextTransformPlan plan = buildTransformPlan(source.runs, source.plain, kind);
		ORDERED_TEXT_PLAN_CACHE.putCached(key, plan == null ? OrderedTextTransformPlan.EMPTY : plan);
		return plan;
	}

	private static OrderedTextTransformPlan buildTransformPlan(List<StyledRun> runs, String plain, TransformKind kind) {
		List<PlayerCustomizationRegistry.NameCandidate> candidates = candidatesForKind(kind);
		if (plain.isEmpty() || candidates.isEmpty()) return null;

		int matchBoundary = kind == TransformKind.CHAT_HEADER_TEXT ? chatHeaderBoundary(plain) : plain.length();
		List<ResolvedOrderedMatch> matches = new ArrayList<>();
		boolean hasAnimatedGradient = false;
		int searchIndex = 0;
		while (searchIndex < matchBoundary) {
			NameStyleMatcher.MatchedCustomization match = NameStyleMatcher.findFirstNameMatch(plain, candidates, searchIndex);
			if (match == null) break;
			int start = match.index();
			int end = start + match.matchedName().length();
			if (start >= matchBoundary || end > matchBoundary) break;

			PlayerCustomizationRegistry.PlayerCustomization customization = match.customization();
			String content = plain.substring(start, end);
			boolean animated = customization.animatedGradient();
			String badgeText = customization.nameBadge() == null ? null : customization.nameBadge().label();
			matches.add(new ResolvedOrderedMatch(
				start,
				end,
				content,
				styleAt(runs, start),
				customization,
				animated,
				customization.hasBadge(),
				customization.hasDecorations(),
				customization.hasExplicitNameColors(),
				kind.includeBadges && badgeText != null && hasPlainBadgeImmediatelyAfter(plain, end, badgeText),
				kind.terminalBadgesOnly && hasPlainVisibleContentAfter(plain, end)));
			hasAnimatedGradient = hasAnimatedGradient || animated;
			searchIndex = end;
		}

		return matches.isEmpty() ? null : new OrderedTextTransformPlan(matches, hasAnimatedGradient);
	}

	private static Component rebuildComponentFromPlan(OrderedTextSourceData source, OrderedTextTransformPlan plan, double animationTime, TransformKind kind) {
		MutableComponent rebuilt = Component.empty();
		int currentIndex = 0;
		for (ResolvedOrderedMatch match : plan.matches) {
			RankPrefixReplacement rankReplacement = resolveRankPrefixReplacement(
				source.plain.substring(currentIndex, match.start),
				match.customization);

			if (rankReplacement != null) {
				if (!rankReplacement.before().isEmpty()) {
					int beforeEnd = currentIndex + rankReplacement.before().length();
					appendOriginalRange(rebuilt, source.runs, currentIndex, beforeEnd);
				}

				String displayName;
				if (kind.replaceMatchedName) {
					displayName = match.customization.displayName(match.content);
				} else {
					displayName = match.content;
				}

				if (rankReplacement.copyName()) {
					Style combinedBase = applyCustomNameStyle(match.baseStyle, match.customization);
					if (match.customization.nameRankPrefix().bold()) {
						combinedBase = combinedBase.withBold(true);
					}
					if (kind.replaceMatchedName || match.hasExplicitNameColors || match.customization.nameBold()) {
						rebuilt.append(cachedGradient(rankReplacement.replacement() + displayName, match.customization, combinedBase, animationTime));
					} else {
						rebuilt.append(Component.literal(rankReplacement.replacement()).setStyle(combinedBase));
						rebuilt.append(buildOriginalRangeComponent(source.runs, match.start, match.end));
					}
				} else {
					rebuilt.append(styledRankPrefix(rankReplacement.replacement(), match.customization, match.baseStyle, animationTime));
					Component styledName;
					if (kind.replaceMatchedName) {
						styledName = cachedGradient(displayName, match.customization, match.baseStyle, animationTime);
					} else if (match.hasExplicitNameColors) {
						styledName = cachedGradient(match.content, match.customization, match.baseStyle, animationTime);
					} else {
						styledName = buildOriginalRangeComponent(source.runs, match.start, match.end);
					}
					rebuilt.append(styledName);
				}
			} else {
				if (match.start > currentIndex) {
					appendOriginalRange(rebuilt, source.runs, currentIndex, match.start);
				}

				Component styledName;
				if (kind.replaceMatchedName) {
					styledName = cachedGradient(match.customization.displayName(match.content), match.customization, match.baseStyle, animationTime);
				} else if (match.hasExplicitNameColors) {
					styledName = cachedGradient(match.content, match.customization, match.baseStyle, animationTime);
				} else {
					styledName = buildOriginalRangeComponent(source.runs, match.start, match.end);
				}
				rebuilt.append(styledName);
			}

			if (kind.includeBadges && match.hasBadge && !match.hasBadgeAlready && !match.hasTrailingContent) {
				appendBadge(rebuilt, match.customization, match.baseStyle);
			}
			currentIndex = match.end;
		}

		if (currentIndex < source.plain.length()) {
			appendOriginalRange(rebuilt, source.runs, currentIndex, source.plain.length());
		}
		return rebuilt;
	}

	private static Component cachedGradient(String content, PlayerCustomizationRegistry.PlayerCustomization customization, Style baseStyle, double animationTime) {
		Style effectiveBaseStyle = applyCustomNameStyle(baseStyle, customization);
		if (customization.nameColors() != null) {
			if (!customization.animatedGradient()) {
				return staticGradientText(content, customization.nameColors(), effectiveBaseStyle);
			}
			AnimatedGradientStyle animatedStyle = resolveAnimatedGradientStyle(customization);
			return animatedStyle == null
				? Component.literal(content).setStyle(effectiveBaseStyle)
				: animatedGradientText(content, animatedStyle, effectiveBaseStyle, animationTime);
		}

		if (!customization.nameLetterColors().isEmpty()) {
			ColorizedCacheKey key = new ColorizedCacheKey(content.toLowerCase(Locale.ROOT), "multicolor", customization.nameLetterColors());
			Component cached = COLORIZED_TEXT_CACHE.getCached(key);
			if (cached == null) {
				cached = multiColorText(content, customization.nameLetterColors(), Style.EMPTY);
				COLORIZED_TEXT_CACHE.putCached(key, cached);
			}
			return withParent(cached, effectiveBaseStyle);
		}

		return Component.literal(content).setStyle(effectiveBaseStyle);
	}

	private static Component staticGradientText(String content, PlayerCustomizationRegistry.NameColors colors, Style baseStyle) {
		ColorizedCacheKey key = new ColorizedCacheKey(content.toLowerCase(Locale.ROOT),
			"gradient:" + colors.left() + ':' + colors.right() + ':' + colors.spacing(), List.of(colors.left(), colors.right()));
		Component cached = COLORIZED_TEXT_CACHE.getCached(key);
		if (cached == null) {
			MutableComponent gradientText = Component.empty();
			float spacing = clamp(colors.spacing(), 1.0F, 10.0F);
			int[] codePoints = content.codePoints().toArray();
			for (int i = 0; i < codePoints.length; i++) {
				int color = gradientLoopColor(colors.left(), colors.right(), gradientFrequencyProgress(i, codePoints.length, spacing));
				gradientText.append(Component.literal(new String(Character.toChars(codePoints[i]))).setStyle(Style.EMPTY.withColor(color)));
			}
			cached = gradientText;
			COLORIZED_TEXT_CACHE.putCached(key, cached);
		}
		return withParent(cached, baseStyle);
	}

	private static Component multiColorText(String content, List<Integer> colors, Style baseStyle) {
		MutableComponent output = Component.empty();
		int[] codePoints = content.codePoints().toArray();
		int fallback = colors.isEmpty() ? 0xFFFFFF : colors.get(colors.size() - 1);
		for (int i = 0; i < codePoints.length; i++) {
			int color = i < colors.size() ? colors.get(i) : fallback;
			output.append(Component.literal(new String(Character.toChars(codePoints[i]))).setStyle(baseStyle.withColor(color)));
		}
		return output;
	}

	private static Component animatedGradientText(String content, AnimatedGradientStyle style, Style baseStyle, double time) {
		MutableComponent output = Component.empty();
		int[] codePoints = content.codePoints().toArray();
		for (int i = 0; i < codePoints.length; i++) {
			int color = style.getColor(i, codePoints.length, time);
			output.append(Component.literal(new String(Character.toChars(codePoints[i]))).setStyle(baseStyle.withColor(color)));
		}
		return output;
	}

	private static Component withParent(Component component, Style parent) {
		if (parent == null || parent.isEmpty()) return component.copy();
		MutableComponent output = Component.empty();
		component.visit((style, segment) -> {
			output.append(Component.literal(segment).setStyle(style.applyTo(parent)));
			return Optional.empty();
		}, Style.EMPTY);
		return output;
	}

	private static void appendBadge(MutableComponent target, PlayerCustomizationRegistry.PlayerCustomization customization, Style baseStyle) {
		PlayerCustomizationRegistry.NameBadge badge = customization.nameBadge();
		if (badge == null) return;
		target.append(Component.literal(" ").setStyle(baseStyle));
		Style badgeStyle = baseStyle.withColor(badge.color());
		if (badge.bold()) badgeStyle = badgeStyle.withBold(true);
		target.append(Component.literal(badge.label()).setStyle(badgeStyle));
	}

	private static RankPrefixReplacement resolveRankPrefixReplacement(String prefixPlain, PlayerCustomizationRegistry.PlayerCustomization customization) {
		if (!customization.hasRankPrefix() || prefixPlain == null || prefixPlain.isEmpty()) return null;
		PlayerCustomizationRegistry.NameRankPrefix rankPrefix = customization.nameRankPrefix();
		if (rankPrefix == null) return null;

		String work = prefixPlain;
		StringBuilder levelSuffix = new StringBuilder();
		// Peel trailing SkyBlock level brackets so we keep them next to the name.
		while (true) {
			Matcher levelMatcher = TRAILING_BRACKET_PREFIX.matcher(work);
			if (!levelMatcher.find()) break;
			String bracket = levelMatcher.group(1);
			String inner = bracket.substring(1, bracket.length() - 1);
			if (!isSkyblockLevelBracketInner(inner)) break;
			levelSuffix.insert(0, levelMatcher.group(0));
			work = work.substring(0, levelMatcher.start());
		}

		Matcher rankMatcher = TRAILING_BRACKET_PREFIX.matcher(work);
		if (rankMatcher.find()) {
			String trailingWhitespace = rankMatcher.group(2);
			return new RankPrefixReplacement(
				work.substring(0, rankMatcher.start()),
				rankPrefix.label() + trailingWhitespace + levelSuffix,
				rankPrefix.copyName());
		}

		// No Hypixel rank tag — keep level(s) and insert the custom prefix before the name.
		if (!levelSuffix.isEmpty()) {
			String label = rankPrefix.label();
			String replacement = label.endsWith(" ") ? label : label + " ";
			return new RankPrefixReplacement(
				work + levelSuffix,
				replacement,
				rankPrefix.copyName());
		}
		return null;
	}

	private static boolean isSkyblockLevelBracketInner(String inner) {
		if (inner == null || inner.isBlank()) return false;
		return SKYBLOCK_LEVEL_INNER.matcher(inner.trim()).matches();
	}

	private static Component styledRankPrefix(String text, PlayerCustomizationRegistry.PlayerCustomization customization, Style baseStyle, double animationTime) {
		PlayerCustomizationRegistry.NameRankPrefix rankPrefix = customization.nameRankPrefix();
		if (rankPrefix == null) return Component.literal(text).setStyle(baseStyle);

		Style rankStyle = baseStyle;
		if (rankPrefix.bold()) rankStyle = rankStyle.withBold(true);

		PlayerCustomizationRegistry.NameColors colors = rankPrefix.colors();
		if (colors == null) {
			return Component.literal(text).setStyle(rankStyle);
		}

		if (rankPrefix.animatedGradient()) {
			AnimatedGradientStyle animatedStyle = resolveRankAnimatedGradientStyle(rankPrefix);
			return animatedStyle == null
				? Component.literal(text).setStyle(rankStyle.withColor(colors.left()))
				: animatedGradientText(text, animatedStyle, rankStyle, animationTime);
		}

		if (colors.left() == colors.right()) {
			return Component.literal(text).setStyle(rankStyle.withColor(colors.left()));
		}
		return staticGradientText(text, colors, rankStyle);
	}

	private static String toLegacyStyledRank(String content, PlayerCustomizationRegistry.PlayerCustomization customization) {
		PlayerCustomizationRegistry.NameRankPrefix rankPrefix = customization.nameRankPrefix();
		if (rankPrefix == null) return content;

		PlayerCustomizationRegistry.NameColors colors = rankPrefix.colors();
		if (colors == null) {
			StringBuilder output = new StringBuilder();
			if (rankPrefix.bold()) output.append('§').append('l');
			output.append(content);
			return output.toString();
		}

		if (colors.left() == colors.right() && !rankPrefix.animatedGradient()) {
			StringBuilder output = new StringBuilder();
			output.append(buildLegacyHexColorCode(colors.left()));
			if (rankPrefix.bold()) output.append('§').append('l');
			output.append(content);
			return output.toString();
		}

		StringBuilder output = new StringBuilder();
		int[] codePoints = content.codePoints().toArray();
		AnimatedGradientStyle animatedStyle = rankPrefix.animatedGradient() ? resolveRankAnimatedGradientStyle(rankPrefix) : null;
		double animationTime = animatedStyle == null ? 0.0D : currentAnimationTime();
		for (int i = 0; i < codePoints.length; i++) {
			int color;
			if (animatedStyle != null) {
				color = animatedStyle.getColor(i, codePoints.length, animationTime);
			} else {
				float progress = gradientFrequencyProgress(i, codePoints.length, clamp(colors.spacing(), 1.0F, 10.0F));
				color = gradientLoopColor(colors.left(), colors.right(), progress);
			}
			output.append(buildLegacyHexColorCode(color));
			if (rankPrefix.bold()) output.append('§').append('l');
			output.appendCodePoint(codePoints[i]);
		}
		return output.toString();
	}

	private static AnimatedGradientStyle resolveRankAnimatedGradientStyle(PlayerCustomizationRegistry.NameRankPrefix rankPrefix) {
		PlayerCustomizationRegistry.NameColors colors = rankPrefix.colors();
		if (colors == null || !rankPrefix.animatedGradient()) return null;

		int steps = rankPrefix.animationSteps() == null ? ANIMATED_GRADIENT_STEPS : Math.max(2, rankPrefix.animationSteps());
		float speed = rankPrefix.animationSpeed() == null ? ANIMATED_GRADIENT_SPEED : rankPrefix.animationSpeed();
		float spacing = clamp(colors.spacing(), 1.0F, 10.0F);
		AnimatedGradientCacheKey key = new AnimatedGradientCacheKey(colors.left(), colors.right(), steps, Float.floatToRawIntBits(speed), Float.floatToRawIntBits(spacing));
		AnimatedGradientStyle cached = ANIMATED_GRADIENT_CACHE.getCached(key);
		if (cached != null) return cached;

		AnimatedGradientStyle built = new AnimatedGradientStyle(buildLoopGradient(colors.left(), colors.right(), steps), speed, spacing);
		ANIMATED_GRADIENT_CACHE.putCached(key, built);
		return built;
	}

	private static Style applyCustomNameStyle(Style style, PlayerCustomizationRegistry.PlayerCustomization customization) {
		return customization.nameBold() ? style.withBold(true) : style;
	}

	private static AnimatedGradientStyle resolveAnimatedGradientStyle(PlayerCustomizationRegistry.PlayerCustomization customization) {
		PlayerCustomizationRegistry.NameColors colors = customization.nameColors();
		if (colors == null || !customization.animatedGradient()) return null;

		int steps = customization.nameAnimationSteps() == null ? ANIMATED_GRADIENT_STEPS : Math.max(2, customization.nameAnimationSteps());
		float speed = customization.nameAnimationSpeed() == null ? ANIMATED_GRADIENT_SPEED : customization.nameAnimationSpeed();
		float spacing = clamp(colors.spacing(), 1.0F, 10.0F);
		AnimatedGradientCacheKey key = new AnimatedGradientCacheKey(colors.left(), colors.right(), steps, Float.floatToRawIntBits(speed), Float.floatToRawIntBits(spacing));
		AnimatedGradientStyle cached = ANIMATED_GRADIENT_CACHE.getCached(key);
		if (cached != null) return cached;

		AnimatedGradientStyle built = new AnimatedGradientStyle(buildLoopGradient(colors.left(), colors.right(), steps), speed, spacing);
		ANIMATED_GRADIENT_CACHE.putCached(key, built);
		return built;
	}

	private static double currentAnimationTime() {
		Minecraft client = Minecraft.getInstance();
		if (client == null || client.level == null) return System.currentTimeMillis() / 50.0D;
		return client.level.getGameTime();
	}

	private static long currentAnimationFrameIndex(double animationTime) {
		Minecraft client = Minecraft.getInstance();
		int visiblePlayers = client == null || client.level == null ? 0 : client.level.players().size();
		if (visiblePlayers < LARGE_LOBBY_ANIMATION_THRESHOLD) {
			return (long) Math.floor(animationTime);
		}
		return (long) Math.floor(animationTime * THROTTLED_ANIMATION_FPS / 20.0D);
	}

	private static boolean hasAnimatedGradientMatch(String text, List<PlayerCustomizationRegistry.NameCandidate> candidates) {
		if (text == null || text.isEmpty() || candidates.isEmpty()) return false;
		int index = 0;
		while (index < text.length()) {
			NameStyleMatcher.MatchedCustomization match = NameStyleMatcher.findFirstNameMatch(text, candidates, index);
			if (match == null) return false;
			if (match.customization().animatedGradient()) return true;
			index = match.index() + match.matchedName().length();
		}
		return false;
	}

	private static List<PlayerCustomizationRegistry.NameCandidate> candidatesForKind(TransformKind kind) {
		return switch (kind) {
			case GRADIENT_TEXT -> PlayerCustomizationRegistry.gradientNameCandidates();
			case CHAT_HEADER_TEXT -> PlayerCustomizationRegistry.chatHeaderNameCandidates();
			case NAMEPLATE_TEXT, NAMEPLATE_DISPLAY_TEXT -> PlayerCustomizationRegistry.nameplateDisplayCandidates();
			case SCOREBOARD_TEXT -> PlayerCustomizationRegistry.scoreboardStyledNameCandidates();
			case SCOREBOARD_DISPLAY_TEXT -> PlayerCustomizationRegistry.scoreboardDisplayNameCandidates();
		};
	}

	private static List<StyledRun> collectRuns(Component message) {
		List<StyledRun> runs = new ArrayList<>();
		int[] startIndex = {0};
		message.visit((style, segment) -> {
			if (!segment.isEmpty()) {
				runs.add(new StyledRun(startIndex[0], startIndex[0] + segment.length(), segment, style));
				startIndex[0] += segment.length();
			}
			return Optional.empty();
		}, Style.EMPTY);
		return runs;
	}

	private static List<StyledRun> collectRuns(FormattedCharSequence message) {
		List<StyledRun> runs = new ArrayList<>();
		Style[] currentStyle = {null};
		StringBuilder currentText = new StringBuilder();
		int[] runStart = {0};
		int[] visibleIndex = {0};

		message.accept((index, style, codePoint) -> {
			if (currentStyle[0] != null && !currentStyle[0].equals(style)) {
				flushRun(runs, currentStyle[0], currentText, runStart[0]);
				runStart[0] += currentText.length();
				currentText.setLength(0);
			}
			if (currentStyle[0] == null) {
				runStart[0] = visibleIndex[0];
			}
			currentStyle[0] = style;
			currentText.appendCodePoint(codePoint);
			visibleIndex[0] += Character.charCount(codePoint);
			return true;
		});
		if (currentStyle[0] != null) {
			flushRun(runs, currentStyle[0], currentText, runStart[0]);
		}
		return runs;
	}

	private static Component rebuildFormattedText(FormattedText message) {
		MutableComponent output = Component.empty();
		message.visit((style, segment) -> {
			if (!segment.isEmpty()) {
				output.append(Component.literal(segment).setStyle(style));
			}
			return Optional.empty();
		}, Style.EMPTY);
		return output;
	}

	private static void flushRun(List<StyledRun> runs, Style style, StringBuilder text, int start) {
		if (text.isEmpty()) return;
		String runText = text.toString();
		runs.add(new StyledRun(start, start + runText.length(), runText, style));
	}

	private static String runsToPlain(List<StyledRun> runs) {
		StringBuilder output = new StringBuilder();
		for (StyledRun run : runs) output.append(run.content);
		return output.toString();
	}

	private static int styleHash(List<StyledRun> runs) {
		int hash = 1;
		for (StyledRun run : runs) {
			hash = 31 * hash + run.start;
			hash = 31 * hash + run.end;
			hash = 31 * hash + run.style.hashCode();
		}
		return hash;
	}

	private static void appendOriginalRange(MutableComponent target, List<StyledRun> runs, int start, int end) {
		if (start >= end) return;
		for (StyledRun run : runs) {
			if (run.end <= start || run.start >= end) continue;
			int localStart = Math.max(0, start - run.start);
			int localEnd = Math.min(run.content.length(), end - run.start);
			if (localStart < localEnd) {
				target.append(Component.literal(run.content.substring(localStart, localEnd)).setStyle(run.style));
			}
		}
	}

	private static Component buildOriginalRangeComponent(List<StyledRun> runs, int start, int end) {
		MutableComponent output = Component.empty();
		appendOriginalRange(output, runs, start, end);
		return output;
	}

	private static Style styleAt(List<StyledRun> runs, int index) {
		for (StyledRun run : runs) {
			if (index >= run.start && index < run.end) return run.style;
		}
		return Style.EMPTY;
	}

	private static int chatHeaderBoundary(String plain) {
		int delimiter = plain.indexOf(": ");
		return delimiter == -1 ? Integer.MAX_VALUE : delimiter;
	}

	private static boolean hasPlainBadgeImmediatelyAfter(String text, int startIndex, String badgeText) {
		int cursor = startIndex;
		while (cursor < text.length() && Character.isWhitespace(text.charAt(cursor))) cursor++;
		return cursor + badgeText.length() <= text.length() && text.regionMatches(cursor, badgeText, 0, badgeText.length());
	}

	private static boolean hasPlainVisibleContentAfter(String text, int startIndex) {
		int cursor = startIndex;
		while (cursor < text.length()) {
			if (!Character.isWhitespace(text.charAt(cursor))) return true;
			cursor++;
		}
		return false;
	}

	private static String toLegacyStyledName(String content, PlayerCustomizationRegistry.PlayerCustomization customization) {
		if (customization.nameColors() == null && customization.nameLetterColors().isEmpty()) {
			StringBuilder output = new StringBuilder();
			if (customization.nameBold()) output.append('§').append('l');
			output.append(content);
			return output.toString();
		}

		StringBuilder output = new StringBuilder();
		int[] codePoints = content.codePoints().toArray();
		AnimatedGradientStyle animatedStyle = customization.animatedGradient() ? resolveAnimatedGradientStyle(customization) : null;
		double animationTime = animatedStyle == null ? 0.0D : currentAnimationTime();
		int fallbackLetterColor = customization.nameLetterColors().isEmpty() ? 0xFFFFFF : customization.nameLetterColors().get(customization.nameLetterColors().size() - 1);
		for (int i = 0; i < codePoints.length; i++) {
			int color;
			if (!customization.nameLetterColors().isEmpty()) {
				color = i < customization.nameLetterColors().size() ? customization.nameLetterColors().get(i) : fallbackLetterColor;
			} else if (animatedStyle != null) {
				color = animatedStyle.getColor(i, codePoints.length, animationTime);
			} else {
				PlayerCustomizationRegistry.NameColors colors = customization.nameColors();
				float progress = gradientFrequencyProgress(i, codePoints.length, clamp(colors.spacing(), 1.0F, 10.0F));
				color = gradientLoopColor(colors.left(), colors.right(), progress);
			}
			output.append(buildLegacyHexColorCode(color));
			if (customization.nameBold()) output.append('§').append('l');
			output.appendCodePoint(codePoints[i]);
		}
		return output.toString();
	}

	private static String buildLegacyHexColorCode(int color) {
		String hex = String.format("%06X", color & 0xFFFFFF);
		StringBuilder output = new StringBuilder(14);
		output.append('§').append('x');
		for (int i = 0; i < hex.length(); i++) {
			output.append('§').append(hex.charAt(i));
		}
		return output.toString();
	}

	private static float gradientFrequencyProgress(int index, int count, float spacing) {
		float normalizedPosition = count <= 1 ? 0.0F : (float) index / (float) (count - 1);
		return positiveModulo(normalizedPosition * spacing, 1.0F);
	}

	private static int gradientLoopColor(int startColor, int endColor, float progress) {
		float loopProgress = progress <= 0.5F ? progress * 2.0F : (1.0F - progress) * 2.0F;
		return GradientColors.interpolateRgb(startColor, endColor, loopProgress);
	}

	private static int interpolateRgbColor(int startColor, int endColor, float progress) {
		return GradientColors.interpolateRgb(startColor, endColor, progress);
	}

	private static int[] buildLoopGradient(int primaryColor, int secondaryColor, int stepsCount) {
		int count = Math.max(2, stepsCount);
		int half = count / 2;
		int remaining = count - half;
		int[] steps = new int[count];
		for (int i = 0; i < half; i++) {
			float progress = half <= 1 ? 1.0F : (float) i / (float) (half - 1);
			steps[i] = interpolateRgbColor(primaryColor, secondaryColor, progress);
		}
		for (int i = 0; i < remaining; i++) {
			float progress = remaining <= 1 ? 1.0F : (float) i / (float) (remaining - 1);
			steps[half + i] = interpolateRgbColor(secondaryColor, primaryColor, progress);
		}
		return steps;
	}

	private static float positiveModulo(float value, float modulus) {
		float remainder = value % modulus;
		return remainder < 0.0F ? remainder + modulus : remainder;
	}

	private static double positiveModulo(double value, double modulus) {
		double remainder = value % modulus;
		return remainder < 0.0D ? remainder + modulus : remainder;
	}

	private static float clamp(float value, float min, float max) {
		return Math.max(min, Math.min(max, value));
	}

	private static void checkRegistryVersion() {
		long version = PlayerCustomizationRegistry.version();
		if (observedRegistryVersion != version) {
			clearCaches();
			observedRegistryVersion = version;
		}
	}

	private enum TransformKind {
		GRADIENT_TEXT(false, false, false),
		CHAT_HEADER_TEXT(true, false, false),
		NAMEPLATE_TEXT(true, true, false),
		NAMEPLATE_DISPLAY_TEXT(true, true, false),
		SCOREBOARD_TEXT(false, true, true),
		SCOREBOARD_DISPLAY_TEXT(true, true, true);

		final boolean replaceMatchedName;
		final boolean includeBadges;
		final boolean terminalBadgesOnly;

		TransformKind(boolean replaceMatchedName, boolean includeBadges, boolean terminalBadgesOnly) {
			this.replaceMatchedName = replaceMatchedName;
			this.includeBadges = includeBadges;
			this.terminalBadgesOnly = terminalBadgesOnly;
		}
	}

	private record StyledRun(int start, int end, String content, Style style) {
	}

	private record OrderedTextSourceData(String plain, List<StyledRun> runs, int styleHash) {
	}

	private record OrderedTextTransformPlan(List<ResolvedOrderedMatch> matches, boolean hasAnimatedGradient) {
		static final OrderedTextTransformPlan EMPTY = new OrderedTextTransformPlan(List.of(), false);
	}

	private record ResolvedOrderedMatch(int start, int end, String content, Style baseStyle,
			PlayerCustomizationRegistry.PlayerCustomization customization, boolean isAnimatedGradient,
			boolean hasBadge, boolean hasDecorations, boolean hasExplicitNameColors, boolean hasBadgeAlready,
			boolean hasTrailingContent) {
	}

	private record RankPrefixReplacement(String before, String replacement, boolean copyName) {
	}

	private record TextCacheKey(long version, TransformKind kind, String plain, int styleHash, long frame) {
	}

	private record StringCacheKey(long version, TransformKind kind, String raw, long frame) {
	}

	private record OrderedTextPlanCacheKey(long version, TransformKind kind, String plain, int styleHash) {
	}

	private record OrderedTextResultCacheKey(long version, TransformKind kind, String plain, int styleHash) {
	}

	private record OrderedTextAnimatedFrameCacheKey(long version, TransformKind kind, String plain, int styleHash, long frame) {
	}

	private record ColorizedCacheKey(String content, String kind, List<Integer> colors) {
	}

	private record AnimatedGradientCacheKey(int leftColor, int rightColor, int stepsCount, int speedBits, int spacingBits) {
	}

	private record AnimatedGradientStyle(int[] steps, float speed, float spacing) {
		float offsetAt(double time) {
			return steps.length == 0 ? 0.0F : (float) positiveModulo(time * speed, steps.length);
		}

		int getColor(int charIndex, int characterCount, double time) {
			if (steps.length == 0) return 0;
			double normalizedPosition = characterCount <= 1 ? 0.0D : (double) charIndex / (double) (characterCount - 1);
			double normalizedOffset = (double) offsetAt(time) / (double) steps.length;
			double normalized = positiveModulo((normalizedPosition * spacing) + normalizedOffset, 1.0D);
			double wrapped = normalized * steps.length;
			int baseIndex = (int) Math.floor(wrapped);
			int nextIndex = (baseIndex + 1) % steps.length;
			float blend = (float) (wrapped - baseIndex);
			return interpolateRgbColor(steps[baseIndex], steps[nextIndex], blend);
		}
	}
}
