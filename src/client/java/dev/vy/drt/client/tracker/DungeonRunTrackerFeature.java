package dev.vy.drt.client.tracker;

import dev.vy.drt.DungeonRunTracker;
import dev.vy.drt.config.ChestCostBreakdown;
import dev.vy.drt.config.DrtConfig;
import dev.vy.drt.config.DrtConfigManager;
import dev.vy.drt.config.DungeonChestOffer;
import dev.vy.drt.config.DungeonFloor;
import dev.vy.drt.config.DungeonLootEntry;
import dev.vy.drt.config.DungeonRunRecord;
import dev.vy.drt.mixin.AbstractContainerScreenAccessor;
import dev.vy.drt.price.DungeonProfitPricing;
import dev.vy.drt.price.PriceCache;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.item.component.ResolvableProfile;
import net.minecraft.world.scores.DisplaySlot;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.PlayerScoreEntry;
import net.minecraft.world.scores.Scoreboard;
import org.joml.Matrix3x2fStack;

public final class DungeonRunTrackerFeature {
	private static final int REFRESH_INTERVAL_TICKS = 10;
	private static final long EXTRA_STATS_TIMEOUT_MS = 8_000L;
	private static final long DUNGEON_SIGNAL_GRACE_MS = 15_000L;
	private static final long MESSAGE_DEDUP_WINDOW_MS = 2_000L;
	private static final long RUN_COMPLETION_DEDUP_WINDOW_MS = 20_000L;
	private static final long LOOT_WINDOW_MS = 180_000L;
	private static final long LOOT_COLLECTION_MS = 3_000L;
	private static final long REWARD_MODIFIER_SCAN_INTERVAL_MS = 300L;
	private static final Pattern ESSENCE_PATTERN = Pattern.compile("^(?:\\+\\s*)?(WITHER|UNDEAD|SPIDER|DRAGON|ICE|DIAMOND|GOLD|CRIMSON) ESSENCE(?:\\s*[xX]\\s*(\\d+))?$");
	private static final Pattern RECEIVED_PATTERN = Pattern.compile("^YOU RECEIVED\\s+(.+?)(?:\\s*[xX]\\s*(\\d+))?!?$");
	private static final Pattern PLUS_PATTERN = Pattern.compile("^\\+\\s*(.+?)(?:\\s*[xX]\\s*(\\d+))?$");
	private static final Pattern TRAILING_QUANTITY_PATTERN = Pattern.compile("^(.+?)\\s*[xX]\\s*(\\d+)$");
	private static final Pattern COIN_PATTERN = Pattern.compile("([-+]?\\d[\\d,]*(?:\\.\\d+)?)\\s*([kmb])?(?:\\s*Coins)?", Pattern.CASE_INSENSITIVE);
	private static final Pattern QUANTITY_PREFIX_PATTERN = Pattern.compile("^(\\d+)x?\\s+(.+)$");
	private static final Pattern QUANTITY_SUFFIX_PATTERN = Pattern.compile("^(.+?)\\s+x(\\d+)$", Pattern.CASE_INSENSITIVE);
	private static final Pattern ENCHANTED_BOOK_PATTERN = Pattern.compile("^Enchanted Book \\((.+) ([IVX]+)\\)$", Pattern.CASE_INSENSITIVE);
	private static final Pattern BOSS_TIME_PATTERN = Pattern.compile("Defeated .+ in (\\d+)m\\s+(\\d+)s", Pattern.CASE_INSENSITIVE);
	private static final Pattern SHORT_FLOOR_PATTERN = Pattern.compile("(?:^|[^A-Z0-9])([FM])\\s*([1-7])(?:$|[^A-Z0-9])");
	private static final Pattern KUUDRA_SHORT_TIER_PATTERN = Pattern.compile("(?:^|[^A-Z0-9])(?:K|T)\\s*([1-5])(?:$|[^A-Z0-9])", Pattern.CASE_INSENSITIVE);
	private static final Pattern KUUDRA_TIER_WORD_PATTERN = Pattern.compile("\\bTIER\\s*(?:-|:)?\\s*(I{1,3}|IV|V|[1-5])\\b", Pattern.CASE_INSENSITIVE);
	private static final Pattern SCORE_GRADE_PATTERN = Pattern.compile("\\bSCORE\\b.*(?:\\((S\\+|S|A|B|C|D)\\)|(?:^|\\s)(S\\+|S|A|B|C|D)\\s*$)", Pattern.CASE_INSENSITIVE);
	private static final Set<String> REWARD_CHEST_TITLES = Set.of(
		"WOOD CHEST", "GOLD CHEST", "DIAMOND CHEST", "EMERALD CHEST", "OBSIDIAN CHEST", "BEDROCK CHEST",
		"FREE CHEST", "PAID CHEST"
	);
	private static final Map<String, String> TIER_NAME_TO_CHEST_TITLE = Map.of(
		"WOOD", "WOOD CHEST", "GOLD", "GOLD CHEST", "DIAMOND", "DIAMOND CHEST",
		"EMERALD", "EMERALD CHEST", "OBSIDIAN", "OBSIDIAN CHEST", "BEDROCK", "BEDROCK CHEST",
		"FREE", "FREE CHEST", "PAID", "PAID CHEST"
	);
	private static final Map<String, DungeonFloor> KUUDRA_TIER_NAMES = Map.of(
		"BASIC", DungeonFloor.K1,
		"HOT", DungeonFloor.K2,
		"BURNING", DungeonFloor.K3,
		"FIERY", DungeonFloor.K4,
		"INFERNAL", DungeonFloor.K5
	);
	private static final Set<String> ULTIMATE_ENCHANTS = Set.of(
		"LEGION", "ULTIMATE_WISE", "LAST_STAND", "SOUL_EATER", "SWARM", "COMBO", "REND",
		"NO_PAIN_NO_GAIN", "ONE_FOR_ALL", "CHIMERA", "BANK", "JERRY", "INFERNO",
		"FATAL_TEMPO", "DUPLEX", "FLASH", "HABANERO_TACTICS"
	);
	private static final Map<String, String> ITEM_ID_ALIASES = createItemIdAliases();
	private static final String ITEM_DUNGEON_CHEST_KEY = "DUNGEON_CHEST_KEY";
	private static final String ITEM_KISMET_FEATHER = "KISMET_FEATHER";
	private static final String ITEM_WHEEL_OF_FATE = "WHEEL_OF_FATE";

	private static final Map<String, ItemStack> CHEST_ICON_CACHE = new LinkedHashMap<>();
	private static final Map<String, ItemStack> ITEM_ICON_CACHE = new LinkedHashMap<>();
	private static final long ICON_CACHE_RETRY_MS = 2_000L;
	private static long nextIconCacheRetryMillis;
	private static boolean iconCacheLoadWarningLogged;
	private static final float MIN_HUD_SCALE = 0.5F;
	private static final float MAX_HUD_SCALE = 2.0F;
	private static final float HUD_SCALE_STEP = 0.1F;

	private enum HudVisibilityMode {
		GLOBAL("Global"),
		DEFAULT("Default"),
		DHUB("DHub");

		private final String displayName;

		HudVisibilityMode(String displayName) {
			this.displayName = displayName;
		}

		HudVisibilityMode next() {
			return switch (this) {
				case DEFAULT -> GLOBAL;
				case GLOBAL -> DHUB;
				case DHUB -> DEFAULT;
			};
		}

		static HudVisibilityMode fromConfig(String value) {
			if (value == null || value.isBlank()) return DEFAULT;
			try {
				return HudVisibilityMode.valueOf(value.trim().toUpperCase(Locale.ROOT));
			} catch (IllegalArgumentException ignored) {
				return DEFAULT;
			}
		}
	}

	private record PacketCapturedMessage(String text, long atMillis) {}
	private record OverlayChestData(String chestTitle, List<DungeonLootEntry> entries, ChestCostBreakdown breakdown, long valueCoins, long profitCoins) {}
	private record CroesusChestRow(String canonicalTitle, String displayName, ItemStack icon, int menuSlotIndex, int slotX, int slotY, long normalProfitCoins, long keyProfitCoins, boolean alreadyOpened, boolean kismetRerolled) {}
	private record CroesusRunSlot(int slotX, int slotY) {}
	private record ScreenBounds(int left, int top, int width, int height) {
		int right() { return left + width; }
	}
	private static final class OverlayItemDisplay {
		final String name;
		final boolean bold;
		final int color;

		OverlayItemDisplay(String name, boolean bold, int color) {
			this.name = name;
			this.bold = bold;
			this.color = color;
		}
	}

	public static ItemStack getChestIcon(String chestTitle) {
		if (chestTitle == null) return ItemStack.EMPTY;
		return CHEST_ICON_CACHE.getOrDefault(chestTitle.toUpperCase(Locale.ROOT), ItemStack.EMPTY);
	}

	public static ItemStack getItemIcon(String itemId) {
		if (itemId == null || itemId.isBlank()) return ItemStack.EMPTY;
		return ITEM_ICON_CACHE.getOrDefault(itemId.toUpperCase(Locale.ROOT), ItemStack.EMPTY);
	}

	private static final String TEX_WOOD     = "ewogICJ0aW1lc3RhbXAiIDogMTU5Njg1ODQzNjE3MiwKICAicHJvZmlsZUlkIiA6ICI4ODQ0ZDJiMWM2YjY0MmVkYTYxNTNjNGEyY2I3YTU5NiIsCiAgInByb2ZpbGVOYW1lIiA6ICJUYW52dmVyIiwKICAic2lnbmF0dXJlUmVxdWlyZWQiIDogdHJ1ZSwKICAidGV4dHVyZXMiIDogewogICAgIlNLSU4iIDogewogICAgICAidXJsIiA6ICJodHRwOi8vdGV4dHVyZXMubWluZWNyYWZ0Lm5ldC90ZXh0dXJlL2E0YmM2YjU4MDBlNTdkOGMyMzk2NmE1MGU5YjU1YWFkOGQwODI5MDk5ZTk4N2M5NmEwMWVlNWJlMThkYzgyZDAiCiAgICB9CiAgfQp9";
	private static final String TEX_GOLD     = "eyJ0aW1lc3RhbXAiOjE1NjE3MzA3MDE5MTUsInByb2ZpbGVJZCI6IjNmYzdmZGY5Mzk2MzRjNDE5MTE5OWJhM2Y3Y2MzZmVkIiwicHJvZmlsZU5hbWUiOiJZZWxlaGEiLCJzaWduYXR1cmVSZXF1aXJlZCI6dHJ1ZSwidGV4dHVyZXMiOnsiU0tJTiI6eyJ1cmwiOiJodHRwOi8vdGV4dHVyZXMubWluZWNyYWZ0Lm5ldC90ZXh0dXJlLzg2MWJhZDYzOTNhMDhlMjU4MjY4ZWU4NGY2NDFlNGRhNjEyNjAzZmVjMzVkOTQzOGE2YmFjYzVjNjYwY2NhNDMifX19";
	private static final String TEX_DIAMOND  = "eyJ0aW1lc3RhbXAiOjE1NzE2NDQ1NTk0ODIsInByb2ZpbGVJZCI6IjkxOGEwMjk1NTlkZDRjZTZiMTZmN2E1ZDUzZWZiNDEyIiwicHJvZmlsZU5hbWUiOiJCZWV2ZWxvcGVyIiwic2lnbmF0dXJlUmVxdWlyZWQiOnRydWUsInRleHR1cmVzIjp7IlNLSU4iOnsidXJsIjoiaHR0cDovL3RleHR1cmVzLm1pbmVjcmFmdC5uZXQvdGV4dHVyZS9mN2JmMDIzOTBkM2YzZjRjY2U0YmZlZGMzYzE5MDQ4NDEzOGEzMTc0ZDg1NDFhOGZkOTEyZWJiMjE0N2ZjYzBlIn19fQ==";
	private static final String TEX_EMERALD  = "ewogICJ0aW1lc3RhbXAiIDogMTYxNTM4OTI3MjQ5OCwKICAicHJvZmlsZUlkIiA6ICJmMjU5MTFiOTZkZDU0MjJhYTcwNzNiOTBmOGI4MTUyMyIsCiAgInByb2ZpbGVOYW1lIiA6ICJmYXJsb3VjaDEwMCIsCiAgInNpZ25hdHVyZVJlcXVpcmVkIiA6IHRydWUsCiAgInRleHR1cmVzIiA6IHsKICAgICJTS0lOIiA6IHsKICAgICAgInVybCIgOiAiaHR0cDovL3RleHR1cmVzLm1pbmVjcmFmdC5uZXQvdGV4dHVyZS9hZWQyMGZhZGRkM2Y5NjMyZjc0MmY2NDM1MGMzOGVmNzZjNWUxYjQ5MTY0ZmNlMGQyM2E5YTY0YjRlMTIyNTQwIgogICAgfQogIH0KfQ==";
	private static final String TEX_OBSIDIAN = "eyJ0aW1lc3RhbXAiOjE1MTc0MTU2MDkxMDQsInByb2ZpbGVJZCI6IjdjZjc2MTFkYmY2YjQxOWRiNjlkMmQzY2Q4NzUxZjRjIiwicHJvZmlsZU5hbWUiOiJrYXJldGg5OTkiLCJzaWduYXR1cmVSZXF1aXJlZCI6dHJ1ZSwidGV4dHVyZXMiOnsiU0tJTiI6eyJ1cmwiOiJodHRwOi8vdGV4dHVyZXMubWluZWNyYWZ0Lm5ldC90ZXh0dXJlLzliNGNhNDM1NmUzMzhiOGIyY2FiZmVjZDJkY2NkZjM2YzlkZTllZmU3N2U4ODUxNTcxMWFhNGFiMWNjIn19fQ==";
	private static final String TEX_BEDROCK  = "ewogICJ0aW1lc3RhbXAiIDogMTcxOTg2NjUxMzg0MywKICAicHJvZmlsZUlkIiA6ICIzOTg5OGFiODFmMjU0NmQxOGIyY2ExMTE1MDRkZGU1MCIsCiAgInByb2ZpbGVOYW1lIiA6ICI4YjJjYTExMTUwNGRkZTUwIiwKICAic2lnbmF0dXJlUmVxdWlyZWQiIDogdHJ1ZSwKICAidGV4dHVyZXMiIDogewogICAgIlNLSU4iIDogewogICAgICAidXJsIiA6ICJodHRwOi8vdGV4dHVyZXMubWluZWNyYWZ0Lm5ldC90ZXh0dXJlLzZlNjM5ZTQxYmUzNjg4MzBhMmM3YmEzZmJkNjllMmViYWY4YzM5MWQzZjcyNWMyODQ0ZThhNTJiNDU2YWY0MjEiLAogICAgICAibWV0YWRhdGEiIDogewogICAgICAgICJtb2RlbCIgOiAic2xpbSIKICAgICAgfQogICAgfQogIH0KfQ==";
	private static final String TEX_ESSENCE_WITHER  = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvYzRkYjRhZGZhOWJmNDhmZjVkNDE3MDdhZTM0ZWE3OGJkMjM3MTY1OWZjZDhjZDg5MzQ3NDlhZjRjY2U5YiJ9fX0=";
	private static final String TEX_ESSENCE_UNDEAD  = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvNzFkN2M4MTZmYzhjNjM2ZDdmNTBhOTNhMGJhN2FhZWZmMDZjOTZhNTYxNjQ1ZTllYjFiZWYzOTE2NTVjNTMxIn19fQ==";
	private static final String TEX_ESSENCE_CRIMSON = "ewogICJ0aW1lc3RhbXAiIDogMTY0NDc4ODQzMzE5NSwKICAicHJvZmlsZUlkIiA6ICIxZjEyNTNhYTVkYTQ0ZjU5YWU1YWI1NmFhZjRlNTYxNyIsCiAgInByb2ZpbGVOYW1lIiA6ICJOb3RNaUt5IiwKICAic2lnbmF0dXJlUmVxdWlyZWQiIDogdHJ1ZSwKICAidGV4dHVyZXMiIDogewogICAgIlNLSU4iIDogewogICAgICAidXJsIiA6ICJodHRwOi8vdGV4dHVyZXMubWluZWNyYWZ0Lm5ldC90ZXh0dXJlLzY3YzQxOTMwZjhmZjBmMmIwNDMwZTE2OWFlNWYzOGU5ODRkZjEyNDQyMTU3MDVjNmYxNzM4NjI4NDQ1NDNlOWQiLAogICAgICAibWV0YWRhdGEiIDogewogICAgICAgICJtb2RlbCIgOiAic2xpbSIKICAgICAgfQogICAgfQogIH0KfQ==";
	private static final String TEX_SHADOW_ASSASSIN_HELMET = "ewogICJ0aW1lc3RhbXAiIDogMTYxOTM0NjY3MzMxNCwKICAicHJvZmlsZUlkIiA6ICJiMGQ3MzJmZTAwZjc0MDdlOWU3Zjc0NjMwMWNkOThjYSIsCiAgInByb2ZpbGVOYW1lIiA6ICJPUHBscyIsCiAgInNpZ25hdHVyZVJlcXVpcmVkIiA6IHRydWUsCiAgInRleHR1cmVzIiA6IHsKICAgICJTS0lOIiA6IHsKICAgICAgInVybCIgOiAiaHR0cDovL3RleHR1cmVzLm1pbmVjcmFmdC5uZXQvdGV4dHVyZS9hMWFhMTRkZTljMTU4ZDM1MTM5NDljODVjYWJiOWUzNzg0OGI5ODRlYzNmYjViNDRhMzIxOWY0ZmI1OGEwMGRhIgogICAgfQogIH0KfQ==";
	private static final String TEX_NECROMANCER_LORD_HELMET = "ewogICJ0aW1lc3RhbXAiIDogMTcyMDA0NDAxNDg2MiwKICAicHJvZmlsZUlkIiA6ICJlY2Q0ZTI4NjdkMmE0MTE2OTljYzlkMjMzYmM1YmEyMyIsCiAgInByb2ZpbGVOYW1lIiA6ICJSYXRlZEtub3QiLAogICJzaWduYXR1cmVSZXF1aXJlZCIgOiB0cnVlLAogICJ0ZXh0dXJlcyIgOiB7CiAgICAiU0tJTiIgOiB7CiAgICAgICJ1cmwiIDogImh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvN2RhZGU4YmFlNTY3YTA2NzBmYmViMDI1ZTYzNTA2YTA2ZTQwMTBlZjY2NDRmZjdiNTU2YmRmOTNkMjYwODk1NCIKICAgIH0KICB9Cn0=";
	private static final String TEX_WITHER_HELMET = "ewogICJ0aW1lc3RhbXAiIDogMTY4OTY4MzY3MDM4NywKICAicHJvZmlsZUlkIiA6ICJkNzU2OTc4MWUyYjY0OWIyYjVlMjVlYTJhNDZkOGQxOSIsCiAgInByb2ZpbGVOYW1lIiA6ICJEckthcGRvciIsCiAgInNpZ25hdHVyZVJlcXVpcmVkIiA6IHRydWUsCiAgInRleHR1cmVzIiA6IHsKICAgICJTS0lOIiA6IHsKICAgICAgInVybCIgOiAiaHR0cDovL3RleHR1cmVzLm1pbmVjcmFmdC5uZXQvdGV4dHVyZS82OTExMjk0MDk3ODk1ODlhZTFiNDNlMWQ4MjhjMDZmYzlhMjEzYjc4MzQ4YTY4YjczMTNiZGQ0MDFkNzQ4NWNjIiwKICAgICAgIm1ldGFkYXRhIiA6IHsKICAgICAgICAibW9kZWwiIDogInNsaW0iCiAgICAgIH0KICAgIH0KICB9Cn0=";

	public static boolean loadIconCachesFromConfig() {
		long now = System.currentTimeMillis();
		if (now < nextIconCacheRetryMillis) return false;

		boolean loaded = true;
		loaded &= cacheSkull(CHEST_ICON_CACHE, "WOOD CHEST", TEX_WOOD);
		loaded &= cacheSkull(CHEST_ICON_CACHE, "GOLD CHEST", TEX_GOLD);
		loaded &= cacheSkull(CHEST_ICON_CACHE, "DIAMOND CHEST", TEX_DIAMOND);
		loaded &= cacheSkull(CHEST_ICON_CACHE, "EMERALD CHEST", TEX_EMERALD);
		loaded &= cacheSkull(CHEST_ICON_CACHE, "OBSIDIAN CHEST", TEX_OBSIDIAN);
		loaded &= cacheSkull(CHEST_ICON_CACHE, "BEDROCK CHEST", TEX_BEDROCK);
		loaded &= cacheSkull(ITEM_ICON_CACHE, "ESSENCE_WITHER", TEX_ESSENCE_WITHER);
		loaded &= cacheSkull(ITEM_ICON_CACHE, "ESSENCE_UNDEAD", TEX_ESSENCE_UNDEAD);
		loaded &= cacheSkull(ITEM_ICON_CACHE, "ESSENCE_CRIMSON", TEX_ESSENCE_CRIMSON);
		loaded &= cacheSkull(ITEM_ICON_CACHE, "SHADOW_ASSASSIN_HELMET", TEX_SHADOW_ASSASSIN_HELMET);
		loaded &= cacheSkull(ITEM_ICON_CACHE, "NECROMANCER_LORD_HELMET", TEX_NECROMANCER_LORD_HELMET);
		loaded &= cacheSkull(ITEM_ICON_CACHE, "WITHER_HELMET", TEX_WITHER_HELMET);
		if (!loaded) {
			nextIconCacheRetryMillis = now + ICON_CACHE_RETRY_MS;
			return false;
		}
		NeuItemResolver.preload();
		return true;
	}

	private static boolean cacheSkull(Map<String, ItemStack> cache, String key, String texture) {
		if (cache.containsKey(key)) return true;
		ItemStack stack = skullFromTexture(texture);
		if (stack.isEmpty()) return false;
		cache.put(key, stack);
		return true;
	}

	private static ItemStack skullFromTexture(String base64) {
		try {
			CompoundTag propTag = new CompoundTag();
			propTag.putString("name", "textures");
			propTag.putString("value", base64);
			ListTag propsList = new ListTag();
			propsList.add(propTag);
			CompoundTag profileTag = new CompoundTag();
			profileTag.putIntArray("id", new int[]{0, 0, 0, 1});
			profileTag.put("properties", propsList);
			ItemStack skull = new ItemStack(Items.PLAYER_HEAD);
			ResolvableProfile.CODEC.parse(NbtOps.INSTANCE, profileTag)
				.result()
				.ifPresent(profile -> skull.set(DataComponents.PROFILE, profile));
			return skull;
		} catch (Exception e) {
			if (!iconCacheLoadWarningLogged) {
				iconCacheLoadWarningLogged = true;
				DungeonRunTracker.LOGGER.warn("[DRT] Skull icon cache is not ready yet: {}", e.getMessage());
			} else {
				DungeonRunTracker.LOGGER.debug("[DRT] Skull icon cache retry failed: {}", e.getMessage());
			}
			return ItemStack.EMPTY;
		}
	}

	private static String getSkyblockId(ItemStack stack) {
		CustomData cd = stack.get(DataComponents.CUSTOM_DATA);
		if (cd == null) return null;
		return cd.copyTag()
			.getCompound("ExtraAttributes")
			.flatMap(ea -> ea.getString("id"))
			.filter(s -> !s.isEmpty())
			.orElse(null);
	}

	private final Map<String, Integer> floorRunCounts = new LinkedHashMap<>();
	private final Map<String, Long> floorProfitTotals = new LinkedHashMap<>();
	private DungeonFloor selectedFloor = null;

	private boolean enabled;
	private HudVisibilityMode hudVisibilityMode = HudVisibilityMode.DEFAULT;
	private int hudX = 10;
	private int hudY = 10;
	private float hudScale = 1.0F;

	private int refreshCountdown;
	private boolean insideDungeon;
	private boolean insideKuudra;
	private boolean inDungeonHub;
	private boolean inCrimsonIsle;
	private DungeonFloor currentFloor = DungeonFloor.UNKNOWN;
	private DungeonFloor lastKnownKuudraFloor = DungeonFloor.UNKNOWN;
	private long dungeonSignalUntilMillis;
	private long kuudraSignalUntilMillis;
	private long awaitingExtraStatsUntilMillis;
	private boolean awaitingExtraStatsScore;
	private DungeonFloor awaitingExtraStatsFloor = DungeonFloor.UNKNOWN;
	private String pendingScoreGrade = null;
	private String lastRecordedGrade = "?";
	private DungeonFloor pendingSPlusFloor = DungeonFloor.UNKNOWN;
	private long pendingSPlusUntilMillis;
	private boolean runCountedThisDungeon;
	private long lastRunRecordMillis;
	private DungeonFloor lastRunRecordFloor = DungeonFloor.UNKNOWN;
	private String lastRunRecordGrade = "?";
	private Object lastLevelIdentity;

	private long lootWindowUntilMillis;
	private long lootCollectionUntilMillis;
	private int pendingLootRunNumber;
	private long pendingLootRunTimestamp;
	private String pendingLootChestTitle = "";
	private ChestCostBreakdown pendingLootCostBreakdown = new ChestCostBreakdown();
	private DungeonFloor pendingLootFloor = DungeonFloor.UNKNOWN;
	private boolean pendingLootSeededFromGui;
	private boolean pendingLootChestAssigned;
	private int openedRewardChestsInLootWindow;
	private boolean nextOpenedChestUsesDungeonChestKey;
	private boolean nextOpenedChestUsesKismetFeather;
	private boolean nextOpenedChestUsesWheelOfFate;
	private boolean rewardMenuKismetRerollPending;
	private String rewardMenuKismetRerolledChestTitle = "";
	private final List<DungeonLootEntry> pendingLootEntries = new ArrayList<>();
	private final Map<String, DungeonChestOffer> cachedChestOffersByTitle = new HashMap<>();
	private final Map<String, Integer> cachedChestOfferFingerprintsByTitle = new HashMap<>();
	private final Set<String> scannedRewardScreens = new HashSet<>();
	private String lastRewardModifierScanKey = "";
	private long lastRewardModifierScanMillis;
	private boolean lastRewardModifierScanHadKeyRequirement;
	private boolean lastRewardModifierScanHadKismetMarker;
	private String lastViewedOpenedRewardChestTitle = "";

	private boolean sessionActive;
	private long sessionStartMillis;
	private int sessionRuns;
	private long sessionTotalProfit;
	private long totalLifetimeProfit;
	private long sessionInRunMillis;
	private long sessionTotalRunTimeMs;
	private final Map<String, Integer> sessionFloorRuns = new LinkedHashMap<>();
	private final Map<String, Long> sessionFloorProfitTotals = new LinkedHashMap<>();
	private final Map<String, Long> sessionFloorRunTimeTotals = new LinkedHashMap<>();
	private long currentRunStartMillis;
	private long currentRunPausedMillis;
	private long currentRunPauseStartMillis;
	private long currentRunUnavailablePauseStartMillis;
	private long currentRunBossTimeMs;
	private long lastFinishedRunTimeMs;
	private boolean currentRunActive;

	private final Map<String, Integer> gradeRunCounts = new LinkedHashMap<>();
	private final Map<String, Integer> sessionGradeRuns = new LinkedHashMap<>();
	private static final String[] ALL_GRADES = {"S+", "S", "A", "B", "C", "D"};

	private boolean runsPerHrPaused;

	private boolean dragging;
	private boolean leftMouseDownLastTick;
	private final Deque<PacketCapturedMessage> recentSystemMessages = new ArrayDeque<>();
	private final Deque<PacketCapturedMessage> recentLootMessages = new ArrayDeque<>();
	private boolean positionDirty;
	private int dragOffsetX;
	private int dragOffsetY;

	public void applyConfig(DrtConfig config) {
		enabled = config.enabled;
		hudVisibilityMode = HudVisibilityMode.fromConfig(config.hudVisibilityMode);
		floorRunCounts.clear();
		if (config.floorRunCounts != null) {
			config.floorRunCounts.forEach((k, v) -> {
				if (k != null && v != null) floorRunCounts.put(k, Math.max(0, v));
			});
		}
		if (config.legacyRunsCompleted > 0 && !floorRunCounts.containsKey("M5")) {
			floorRunCounts.put("M5", config.legacyRunsCompleted);
		}
		hudX = Math.max(0, config.hudX);
		hudY = Math.max(0, config.hudY);
		hudScale = clampHudScale(config.hudScale);
		String savedFloor = config.selectedFloor;
		if (savedFloor != null && !savedFloor.isBlank()) {
			try { selectedFloor = DungeonFloor.valueOf(savedFloor); } catch (IllegalArgumentException ignored) { selectedFloor = null; }
		} else {
			selectedFloor = null;
		}
		floorProfitTotals.clear();
		gradeRunCounts.clear();
		totalLifetimeProfit = 0L;
		if (config.runHistory != null) {
			for (DungeonRunRecord r : config.runHistory) {
				if (r != null) {
					totalLifetimeProfit += r.chestProfitCoins;
					String key = r.floor == null ? "UNKNOWN" : r.floor;
					floorProfitTotals.merge(key, r.chestProfitCoins, Long::sum);
					String g = r.grade == null || r.grade.isBlank() ? "S+" : r.grade;
					gradeRunCounts.merge(g, 1, Integer::sum);
				}
			}
		}
	}

	public void tick(Minecraft client) {
		if (client.player == null || client.level == null) {
			pauseCurrentRunForUnavailable(System.currentTimeMillis());
			clearRuntimeState();
			leftMouseDownLastTick = false;
			return;
		}

		long now = System.currentTimeMillis();
		resumeCurrentRunAfterUnavailable(now);
		captureOpenedRewardChestLootIfViewing(client, now);
		if (lootCollectionUntilMillis > 0L && now > lootCollectionUntilMillis) {
			flushPendingLootRecord(lootWindowUntilMillis > 0L && now <= lootWindowUntilMillis);
		}
		if (lootWindowUntilMillis > 0L && now > lootWindowUntilMillis) {
			clearLootWindow();
		}

		updateDungeonContext(client);
		captureRewardChestCosts(client);
		tryConsumeAutoOpenRewardChest(client);
		handleDragging(client);
	}

	public void handleChatMessage(Component message) {
		if (message == null) return;
		if (!markMessageForProcessing(message)) return;
		submitMessage(message, false);
	}

	public void handleRawSystemMessage(Component message) {
		if (message == null) return;
		if (!markMessageForProcessing(message)) return;
		submitMessage(message, true);
	}

	public void handleGameMessage(Component message, boolean overlay) {
		if (overlay) return;
		if (message == null) return;
		if (!markMessageForProcessing(message)) return;
		submitMessage(message, true);
	}

	private static final int C_BG       = 0xCC0D0D14;
	private static final int C_ACCENT   = 0xFF1A2A4A;
	private static final int C_TITLE    = 0xFF7A7A8A;
	private static final int C_BRACKET  = 0xFF445566;
	private static final int C_FLOOR    = 0xFF55CCFF;
	private static final int C_LABEL    = 0xFFAAAAAA;
	private static final int C_VALUE    = 0xFFFFFFFF;
	private static final int C_SEP      = 0xFF4A5060;
	private static final int C_DIM      = 0xFF777788;
	private static final int C_RATE     = 0xFFDDDDEE;
	private static final int C_PAUSED   = 0xFFFFAA00;
	private static final int C_RESET    = 0xFFFF4455;
	private static final int OVERLAY_BG = 0xDD0B0C14;
	private static final int OVERLAY_BG_ALT = 0xEE111320;
	private static final int OVERLAY_BORDER = 0xFF323858;
	private static final int OVERLAY_TEXT = 0xFFE5E7F0;
	private static final int OVERLAY_MUTED = 0xFF8993AE;
	private static final int OVERLAY_VALUE = 0xFF8FE39F;
	private static final int OVERLAY_COST = 0xFFFF7373;
	private static final int OVERLAY_BOOK = 0xFF55AAFF;
	private static final int OVERLAY_ULTIMATE = 0xFFFF55FF;
	private static final int OVERLAY_ESSENCE = 0xFFFF55FF;
	private static final int OVERLAY_PROFIT = 0xFF55FF55;
	private static final int OVERLAY_LOSS = 0xFFFF5555;
	private static final int CHEST_OVERLAY_W = 178;
	private static final int CHEST_OVERLAY_MAX_ITEMS = 8;

	public void extractRenderState(Minecraft client, GuiGraphicsExtractor guiGraphics, int mouseX, int mouseY) {
		extractRenderState(client, guiGraphics, mouseX, mouseY, false);
	}

	public void extractRenderState(Minecraft client, GuiGraphicsExtractor guiGraphics, int mouseX, int mouseY, boolean moveMode) {
		if (!isTrackerVisible(client, moveMode)) return;
		boolean showResetLine = shouldShowResetLine(client, moveMode);

		String floorTag = selectedFloor == null ? "All" : selectedFloor.name();
		long lifetimeProfit = selectedFloor == null
			? totalLifetimeProfit
			: floorProfitTotals.getOrDefault(selectedFloor.name(), 0L);
		int sessionRunCount = displaySessionRuns();
		long sessionProfit = displaySessionProfit();
		long sessionRunTimeMs = displaySessionRunTimeMs();
		long inRunElapsedMs = activeInRunElapsedMs();
		double hoursElapsed = inRunElapsedMs / 3_600_000.0;
		long profitPerHr = hoursElapsed > 0.001 ? (long) (sessionProfit / hoursElapsed) : 0L;
		double runsPerHr = hoursElapsed > 0.001 ? sessionRunCount / hoursElapsed : 0.0;
		long avgRunTimeMs = sessionRunCount > 0 ? sessionRunTimeMs / sessionRunCount : 0L;
		long avgProfitPerRun = sessionRunCount > 0 ? sessionProfit / sessionRunCount : 0L;
		int totalRuns = selectedFloor == null
			? totalRunsCompleted()
			: floorRunCounts.getOrDefault(selectedFloor.name(), 0);

		int lineH = client.font.lineHeight + 2;

		Matrix3x2fStack pose = guiGraphics.pose();
		pose.pushMatrix();
		try {
			pose.translate(hudX, hudY);
			pose.scale(hudScale);
			extractRenderStateHudTitleLine(client, guiGraphics, 0, 0, floorTag);
			extractRenderStateHudRunsLine(client, guiGraphics, 0, lineH, totalRuns, sessionRunCount, runsPerHr, avgRunTimeMs);
			extractRenderStateHudProfitLine(client, guiGraphics, 0, lineH * 2, lifetimeProfit, sessionProfit, profitPerHr, avgProfitPerRun);
			if (showResetLine) {
				seg(client, guiGraphics, "Reset " + floorTag, 0, lineH * 3, C_RESET);
			}
		} finally {
			pose.popMatrix();
		}

		if (client.screen != null && !moveMode) {
			if (isHoveringModeLabel(client, mouseX, mouseY)) {
				drawTooltip(client, guiGraphics, "Mode: " + hudVisibilityMode.displayName, mouseX, mouseY);
			} else if (isHoveringFloorLabel(client, mouseX, mouseY)) {
				drawTooltip(client, guiGraphics, "Left/right click to cycle floors", mouseX, mouseY);
			} else if (isHoveringRunsHrPart(client, mouseX, mouseY)) {
				drawTooltip(client, guiGraphics, runsPerHrPaused ? "Click to resume /hr timer" : "Click to pause /hr timer", mouseX, mouseY);
			} else if (isHoveringRunsLine(client, mouseX, mouseY)) {
				drawTooltip(client, guiGraphics, buildRunsTooltip(), mouseX, mouseY);
			} else if (isHoveringProfitLine(client, mouseX, mouseY)) {
				drawTooltip(client, guiGraphics, "Click to view " + (selectedFloor == null ? "all" : selectedFloor.name()) + " loot history", mouseX, mouseY);
			} else if (isHoveringResetButton(client, mouseX, mouseY, showResetLine)) {
				drawTooltip(client, guiGraphics, "Click to reset " + floorTag + " tracker", mouseX, mouseY);
			}
		}

	}

	public void extractChestOverlayRenderState(Minecraft client, GuiGraphicsExtractor guiGraphics, int mouseX, int mouseY) {
		if (extractRenderStateCroesusChestOverlay(client, guiGraphics, mouseX, mouseY)) return;
		extractRenderStateCroesusMainMenuHighlights(client, guiGraphics);
		extractRenderStateChestBreakdownOverlay(client, guiGraphics, mouseX, mouseY);
	}

	private void extractRenderStateHudTitleLine(Minecraft client, GuiGraphicsExtractor g, int x, int y, String floorTag) {
		int floorColor = floorTagColor(floorTag);
		x = seg(client, g, "DRT ", x, y, C_TITLE);
		x = seg(client, g, "[", x, y, C_BRACKET);
		x = seg(client, g, floorTag, x, y, floorColor);
		seg(client, g, "]", x, y, C_BRACKET);
	}

	private static int floorTagColor(String tag) {
		if (tag == null) return C_FLOOR;
		if (tag.startsWith("F")) return 0xFF55CC66;
		if (tag.startsWith("M")) return 0xFFFF5555;
		if (tag.startsWith("K")) return 0xFFAA0000;
		return C_FLOOR;
	}

	private void extractRenderStateHudRunsLine(Minecraft client, GuiGraphicsExtractor g, int x, int y, int totalRuns, int sessionRunCount, double runsPerHr, long avgRunTimeMs) {
		x = seg(client, g, "Runs ", x, y, C_LABEL);
		x = seg(client, g, String.valueOf(totalRuns), x, y, C_VALUE);
		x = seg(client, g, " | ", x, y, C_SEP);
		x = seg(client, g, String.valueOf(sessionRunCount), x, y, C_VALUE);
		x = seg(client, g, " | ", x, y, C_SEP);
		x = seg(client, g, formatDuration(avgRunTimeMs), x, y, C_VALUE);
		x = seg(client, g, " | ", x, y, C_SEP);
		x = seg(client, g, formatRate(runsPerHr), x, y, C_RATE);
		x = seg(client, g, "/hr", x, y, C_DIM);
		if (runsPerHrPaused) seg(client, g, " [paused]", x, y, C_PAUSED);
	}

	private void extractRenderStateHudProfitLine(Minecraft client, GuiGraphicsExtractor g, int x, int y,
			long lifetime, long session, long perHr, long avgProfitPerRun) {
		x = seg(client, g, "Profit ", x, y, C_LABEL);
		x = seg(client, g, formatCoins(lifetime), x, y, profitColor(lifetime));
		x = seg(client, g, " | ", x, y, C_SEP);
		x = seg(client, g, formatCoins(session), x, y, profitColor(session));
		x = seg(client, g, " | ", x, y, C_SEP);
		x = seg(client, g, formatCoins(avgProfitPerRun), x, y, profitColor(avgProfitPerRun));
		x = seg(client, g, "/run | ", x, y, C_DIM);
		x = seg(client, g, formatCoins(perHr), x, y, profitColor(perHr));
		seg(client, g, "/hr", x, y, C_DIM);
	}

	private int seg(Minecraft client, GuiGraphicsExtractor g, String text, int x, int y, int color) {
		g.text(client.font, text, x, y, color, true);
		return x + client.font.width(text);
	}

	private void drawTooltip(Minecraft client, GuiGraphicsExtractor guiGraphics, String text, int mouseX, int mouseY) {
		drawTooltip(client, guiGraphics, List.of(text), mouseX, mouseY);
	}

	private void drawTooltip(Minecraft client, GuiGraphicsExtractor guiGraphics, List<String> lines, int mouseX, int mouseY) {
		if (lines.isEmpty()) return;
		int pad = 4;
		int lineH = client.font.lineHeight + 1;
		int tw = lines.stream().mapToInt(l -> client.font.width(l)).max().orElse(0);
		int th = lines.size() * lineH - 1;
		int tx = mouseX + 8;
		int ty = mouseY - th - pad * 2;
		int screenW = client.getWindow().getGuiScaledWidth();
		if (tx + tw + pad * 2 > screenW) tx = screenW - tw - pad * 2;
		if (ty < 0) ty = mouseY + 12;
		guiGraphics.fill(tx - pad, ty - pad, tx + tw + pad, ty + th + pad, 0xCC000000);
		guiGraphics.fill(tx - pad, ty - pad, tx + tw + pad, ty - pad + 1, 0xFF6666AA);
		for (int i = 0; i < lines.size(); i++) {
			guiGraphics.text(client.font, lines.get(i), tx, ty + i * lineH, 0xFFCCCCFF, true);
		}
	}

	private List<String> buildRunsTooltip() {
		List<String> lines = new ArrayList<>();
		for (String g : ALL_GRADES) {
			int total = gradeRunCounts.getOrDefault(g, 0);
			int ses = sessionGradeRuns.getOrDefault(g, 0);
			if (total > 0 || ses > 0) {
				lines.add(g + ": " + total + " | " + ses + " ses");
			}
		}
		if (lines.isEmpty()) lines.add("No runs recorded");
		return lines;
	}

	private static final int[] PROFIT_GRADIENT = {
		0xFF0000, 0xF70909, 0xEF1212, 0xE71B1B, 0xDF2424, 0xD72D2D,
		0xCF3636, 0xC73F3F, 0xBF4848, 0xB75151, 0xAF5A5A, 0xA76363,
		0x9F6C6C, 0x977575, 0x8F7E7E, 0x888888, 0x7E8F7E, 0x759775,
		0x6C9F6C, 0x63A763, 0x5AAF5A, 0x51B751, 0x48BF48, 0x3FC73F,
		0x36CF36, 0x2DD72D, 0x24DF24, 0x1BE71B, 0x12EF12, 0x09F709, 0x00FF00
	};
	private static final long PROFIT_GRADIENT_MAX = 100_000_000L;

	private int profitColor(long coins) {
		float t = (float) Math.max(-1.0, Math.min(1.0, (double) coins / PROFIT_GRADIENT_MAX));
		float idx = (t + 1f) / 2f * (PROFIT_GRADIENT.length - 1);
		int lo = (int) idx;
		int hi = Math.min(lo + 1, PROFIT_GRADIENT.length - 1);
		float frac = idx - lo;
		return lerpRgb(PROFIT_GRADIENT[lo], PROFIT_GRADIENT[hi], frac) | 0xFF000000;
	}

	private int lerpRgb(int from, int to, float t) {
		int r = (int) ((((from >> 16) & 0xFF) * (1f - t)) + (((to >> 16) & 0xFF) * t));
		int g = (int) ((((from >> 8) & 0xFF) * (1f - t)) + (((to >> 8) & 0xFF) * t));
		int b = (int) (((from & 0xFF) * (1f - t)) + ((to & 0xFF) * t));
		return (r << 16) | (g << 8) | b;
	}

	public boolean isEnabled() {
		return enabled;
	}

	public void setEnabled(boolean enabled) {
		this.enabled = enabled;
		if (!enabled) dragging = false;
		DrtConfigManager.getConfig().enabled = enabled;
		DrtConfigManager.save();
	}

	public boolean toggleHud() {
		setEnabled(!enabled);
		return enabled;
	}

	private List<String> getDisplayLines(boolean includeResetLine) {
		String floorTag = selectedFloor == null ? "All" : selectedFloor.name();
		String resetLine = "Reset " + floorTag;

		int totalRuns = selectedFloor == null
			? totalRunsCompleted()
			: floorRunCounts.getOrDefault(selectedFloor.name(), 0);
		long lifetimeProfit = selectedFloor == null
			? totalLifetimeProfit
			: floorProfitTotals.getOrDefault(selectedFloor.name(), 0L);
		int sessionRunCount = displaySessionRuns();
		long sessionProfit = displaySessionProfit();
		long sessionRunTimeMs = displaySessionRunTimeMs();

		long inRunElapsedMs = activeInRunElapsedMs();
		double hoursElapsed = inRunElapsedMs / 3_600_000.0;
		double runsPerHr = hoursElapsed > 0.001 ? sessionRunCount / hoursElapsed : 0.0;
		double profitPerHr = hoursElapsed > 0.001 ? sessionProfit / hoursElapsed : 0.0;
		long avgRunTimeMs = sessionRunCount > 0 ? sessionRunTimeMs / sessionRunCount : 0L;
		long avgProfitPerRun = sessionRunCount > 0 ? sessionProfit / sessionRunCount : 0L;

		String runsLine = "Runs " + totalRuns + " | " + sessionRunCount + " | " + formatDuration(avgRunTimeMs) + " | " + formatRate(runsPerHr) + "/hr"
			+ (runsPerHrPaused ? " [paused]" : "");
		String profitLine = "Profit " + formatCoins(lifetimeProfit) + " | " + formatCoins(sessionProfit) + " | " + formatCoins(avgProfitPerRun) + "/run | " + formatCoins((long) profitPerHr) + "/hr";
		if (includeResetLine) return List.of("DRT [" + floorTag + "]", runsLine, profitLine, resetLine);
		return List.of("DRT [" + floorTag + "]", runsLine, profitLine);
	}

	private int displaySessionRuns() {
		return selectedFloor == null ? sessionRuns : sessionFloorRuns.getOrDefault(selectedFloor.name(), 0);
	}

	private long displaySessionProfit() {
		return selectedFloor == null ? sessionTotalProfit : sessionFloorProfitTotals.getOrDefault(selectedFloor.name(), 0L);
	}

	private long displaySessionRunTimeMs() {
		return selectedFloor == null ? sessionTotalRunTimeMs : sessionFloorRunTimeTotals.getOrDefault(selectedFloor.name(), 0L);
	}

	private void resetSelectedFloor() {
		if (selectedFloor == null) {
			floorRunCounts.clear();
			totalLifetimeProfit = 0L;
			floorProfitTotals.clear();
			gradeRunCounts.clear();
			clearSession();
			DrtConfigManager.clearAllData();
		} else {
			String key = selectedFloor.name();
			totalLifetimeProfit -= floorProfitTotals.getOrDefault(key, 0L);
			floorRunCounts.remove(key);
			floorProfitTotals.remove(key);
			DrtConfigManager.clearFloorData(key);
			selectedFloor = null;
			DrtConfigManager.updateSelectedFloor(null);
		}
	}

	private void cycleSelectedFloor() {
		cycleSelectedFloor(1);
	}

	private void cycleSelectedFloorBackward() {
		cycleSelectedFloor(-1);
	}

	private void cycleSelectedFloor(int direction) {
		List<DungeonFloor> available = new ArrayList<>();
		available.add(null);
		for (DungeonFloor f : DungeonFloor.values()) {
			if (f != DungeonFloor.UNKNOWN) available.add(f);
		}
		int idx = available.indexOf(selectedFloor);
		if (idx < 0) idx = 0;
		int next = Math.floorMod(idx + direction, available.size());
		selectedFloor = available.get(next);
		DrtConfigManager.updateSelectedFloor(selectedFloor == null ? null : selectedFloor.name());
	}

	private void cycleHudVisibilityMode() {
		hudVisibilityMode = hudVisibilityMode.next();
		DrtConfigManager.updateHudVisibilityMode(hudVisibilityMode.name());
	}

	private int totalRunsCompleted() {
		int total = 0;
		for (int v : floorRunCounts.values()) total += v;
		return total;
	}

	private String formatCoins(long coins) {
		if (coins < 0) return "-" + formatCoins(-coins);
		if (coins >= 1_000_000_000L) return String.format("%.1fB", coins / 1_000_000_000.0);
		if (coins >= 1_000_000L) return String.format("%.1fM", coins / 1_000_000.0);
		if (coins >= 1_000L) return String.format("%.1fk", coins / 1_000.0);
		return String.valueOf(coins);
	}

	private String formatRate(double rate) {
		if (rate >= 100) return String.format("%.1f", rate);
		return String.format("%.2f", rate);
	}

	private String formatDuration(long millis) {
		if (millis <= 0L) return "0:00";
		long totalSeconds = Math.max(0L, millis / 1000L);
		long minutes = totalSeconds / 60L;
		long seconds = totalSeconds % 60L;
		return minutes + ":" + String.format("%02d", seconds);
	}

	public int getHudX() { return hudX; }
	public int getHudY() { return hudY; }

	public int getHudScalePercent() {
		return Math.round(hudScale * 100.0F);
	}

	public int getDisplayWidth(Minecraft client) {
		return getDisplayWidth(client, false);
	}

	public int getDisplayWidth(Minecraft client, boolean includeResetLine) {
		if (client == null) return 0;
		return scaledHudSize(getBaseDisplayWidth(client, includeResetLine));
	}

	public int getDisplayHeight(Minecraft client) {
		return getDisplayHeight(client, false);
	}

	public int getDisplayHeight(Minecraft client, boolean includeResetLine) {
		if (client == null) return 0;
		return scaledHudSize(getBaseDisplayHeight(client, includeResetLine));
	}

	private int getBaseDisplayWidth(Minecraft client, boolean includeResetLine) {
		return getDisplayLines(includeResetLine).stream().mapToInt(l -> client.font.width(l)).max().orElse(0);
	}

	private int getBaseDisplayHeight(Minecraft client, boolean includeResetLine) {
		List<String> lines = getDisplayLines(includeResetLine);
		return lines.size() * (client.font.lineHeight + 2) - 2;
	}

	private int scaledHudSize(int baseSize) {
		if (baseSize <= 0) return 0;
		return (int) Math.ceil(baseSize * hudScale);
	}

	public void setHudPosition(Minecraft client, int x, int y, boolean save) {
		setHudPosition(client, x, y, save, false);
	}

	public void setHudPosition(Minecraft client, int x, int y, boolean save, boolean includeResetLine) {
		if (client == null) return;
		int maxX = Math.max(0, client.getWindow().getGuiScaledWidth() - getDisplayWidth(client, includeResetLine));
		int maxY = Math.max(0, client.getWindow().getGuiScaledHeight() - getDisplayHeight(client, includeResetLine));
		hudX = clamp(x, 0, maxX);
		hudY = clamp(y, 0, maxY);
		if (save) saveHudPosition();
	}

	public boolean handleScreenMouseClick(Minecraft client, double mouseX, double mouseY, int button) {
		return handleScreenMouseClick(client, mouseX, mouseY, button, false);
	}

	public boolean handleScreenMouseClick(Minecraft client, double mouseX, double mouseY, int button, boolean moveMode) {
		int mx = (int) mouseX;
		int my = (int) mouseY;
		if (button == 0 && !moveMode && handleChestTitleClick(client, mx, my)) return true;
		if (button == 0 && !moveMode && handleChestKeyModifierClick(client, mx, my)) return true;
		if (!moveMode && (button == 0 || button == 1) && handleCroesusOverlayClick(client, mx, my, button)) return true;
		if (!isTrackerVisible(client, moveMode) || (button != 0 && button != 1)) return false;
		if (moveMode) return false;
		boolean showResetLine = shouldShowResetLine(client, false);
		if (button == 1) {
			if (isHoveringFloorLabel(client, mx, my)) { cycleSelectedFloorBackward(); return true; }
			return false;
		}
		if (isHoveringModeLabel(client, mx, my)) { cycleHudVisibilityMode(); return true; }
		if (isHoveringFloorLabel(client, mx, my)) { cycleSelectedFloor(); return true; }
		if (isHoveringRunsHrPart(client, mx, my)) { toggleRunsPerHrPause(); return true; }
		if (isHoveringProfitLine(client, mx, my)) {
			requestOpenLootScreen(selectedFloor, null);
			return true;
		}
		if (isHoveringResetButton(client, mx, my, showResetLine)) { resetSelectedFloor(); return true; }
		return false;
	}

	private boolean handleChestKeyModifierClick(Minecraft client, int mouseX, int mouseY) {
		if (client == null || client.player == null) return false;
		if (!isHoveringChestKeyModifier(client, mouseX, mouseY)) return false;
		sendClientCommand(client, "bz dungeon chest key");
		return true;
	}

	private void sendClientCommand(Minecraft client, String command) {
		if (client == null || client.player == null || client.player.connection == null) return;
		client.player.connection.sendCommand(command);
	}

	private boolean isHoveringChestKeyModifier(Minecraft client, int mouseX, int mouseY) {
		OverlayChestData data = currentOverlayChestData(client);
		if (data == null || data.breakdown == null) return false;
		if (!data.breakdown.usedDungeonChestKey && data.breakdown.dungeonChestKeyCostCoins <= 0L) return false;

		int rowH = 14;
		int lineH = client.font.lineHeight + 2;
		int itemRows = 0;
		int essenceRows = 0;
		for (DungeonLootEntry entry : data.entries) {
			if (isEssenceEntry(entry)) essenceRows++;
			else itemRows++;
		}
		int shownItemRows = Math.min(4, itemRows);
		int hiddenRows = Math.max(0, itemRows - shownItemRows);
		int screenW = client.getWindow().getGuiScaledWidth();
		int screenH = client.getWindow().getGuiScaledHeight();
		ScreenBounds bounds = currentContainerBounds(client);
		int x = Math.min(screenW - CHEST_OVERLAY_W - 4, bounds.right() + 8);
		if (x < bounds.right() + 2) x = Math.max(4, bounds.right() + 2);
		int y = Math.max(6, Math.min(screenH - 12, bounds.top() + 2));

		int keyY = y
			+ lineH + 8
			+ shownItemRows * rowH
			+ essenceRows * rowH
			+ (hiddenRows > 0 ? rowH : 0)
			+ 8
			+ lineH + 8
			+ lineH;
		return pointInRect(mouseX, mouseY, x, keyY - 4, CHEST_OVERLAY_W, lineH + 2);
	}

	private long lastChestTitleClickMillis;
	private String lastChestTitleClicked = "";
	private long autoOpenRewardChestAtMillis;
	private long autoOpenRewardChestDeadlineMillis;
	private static final long AUTO_OPEN_REWARD_CHEST_DELAY_MS = 500L;
	private static final long AUTO_OPEN_REWARD_CHEST_RETRY_MS = 2000L;

	private boolean handleChestTitleClick(Minecraft client, int mouseX, int mouseY) {
		OverlayChestData data = currentOverlayChestData(client);
		if (data == null) return false;

		int screenW = client.getWindow().getGuiScaledWidth();
		ScreenBounds bounds = currentContainerBounds(client);
		int x = Math.min(screenW - CHEST_OVERLAY_W - 4, bounds.right() + 8);
		if (x < bounds.right() + 2) x = Math.max(4, bounds.right() + 2);
		int y = Math.max(6, Math.min(client.getWindow().getGuiScaledHeight() - 12, bounds.top() + 2));

		int lineH = client.font.lineHeight + 2;
		if (pointInRect(mouseX, mouseY, x, y - 4, CHEST_OVERLAY_W, lineH + 4)) {
			long now = System.currentTimeMillis();
			if (now - lastChestTitleClickMillis < 500 && data.chestTitle.equals(lastChestTitleClicked)) {
				// Double click: Open chest
				if (client.screen instanceof AbstractContainerScreen<?> screen) {
					String normalizedTitle = normalize(screen.getTitle().getString());
					if (isCroesusChestListTitle(normalizedTitle)) {
						List<CroesusChestRow> rows = currentCroesusChestRows(client);
						for (CroesusChestRow row : rows) {
							if (row.canonicalTitle.equals(canonicalRewardChestTitle(normalize(data.chestTitle)))) {
								invokeInventoryPickupClick(client, row.menuSlotIndex);
								lastChestTitleClickMillis = 0;
								lootScreenPendingMillis = 0; // Cancel delayed loot history opening
								return true;
							}
						}
					}
				}
			}

			// Single click: View history (or first part of double click)
			lastChestTitleClickMillis = now;
			lastChestTitleClicked = data.chestTitle;
			pendingLootScreenFloorFilter = selectedFloor;
			pendingLootScreenSearchFilter = shortChestName(data.chestTitle);
			lootScreenPendingMillis = now + 500;
			return true;
		}
		return false;
	}

	private boolean handleCroesusOverlayClick(Minecraft client, int mouseX, int mouseY, int button) {
		if (client == null || client.player == null || client.gameMode == null) return false;
		CroesusChestRow row = croesusOverlayRowAt(client, mouseX, mouseY);
		if (row == null) return false;

		CroesusChestRow mapped = resolveCroesusChestRow(client, row.canonicalTitle);
		if (mapped == null) {
			DungeonRunTracker.LOGGER.debug("[DRT] Croesus overlay click: unmapped chest {}", row.canonicalTitle);
			return true;
		}

		lootScreenPendingMillis = 0L;
		if (button == 1) {
			scheduleAutoOpenRewardChest();
		} else {
			cancelAutoOpenRewardChest();
		}

		if (!invokeInventoryPickupClick(client, mapped.menuSlotIndex)) {
			cancelAutoOpenRewardChest();
		}
		return true;
	}

	private CroesusChestRow resolveCroesusChestRow(Minecraft client, String canonicalTitle) {
		for (CroesusChestRow candidate : currentCroesusChestRows(client)) {
			if (candidate.canonicalTitle.equals(canonicalTitle)) return candidate;
		}
		return null;
	}

	private void scheduleAutoOpenRewardChest() {
		long now = System.currentTimeMillis();
		autoOpenRewardChestAtMillis = now + AUTO_OPEN_REWARD_CHEST_DELAY_MS;
		autoOpenRewardChestDeadlineMillis = autoOpenRewardChestAtMillis + AUTO_OPEN_REWARD_CHEST_RETRY_MS;
	}

	private void cancelAutoOpenRewardChest() {
		autoOpenRewardChestAtMillis = 0L;
		autoOpenRewardChestDeadlineMillis = 0L;
	}

	private void tryConsumeAutoOpenRewardChest(Minecraft client) {
		if (autoOpenRewardChestAtMillis <= 0L) return;
		long now = System.currentTimeMillis();
		if (now < autoOpenRewardChestAtMillis) return;
		if (now > autoOpenRewardChestDeadlineMillis) {
			cancelAutoOpenRewardChest();
			return;
		}
		if (!(client.screen instanceof AbstractContainerScreen<?> screen) || client.player == null || client.gameMode == null) return;
		if (canonicalRewardChestTitle(normalize(screen.getTitle().getString())) == null) return;

		int slotIndex = findOpenRewardChestSlotIndex(client.player.containerMenu);
		if (slotIndex < 0) return;
		if (invokeInventoryPickupClick(client, slotIndex)) {
			cancelAutoOpenRewardChest();
		}
	}

	private int findOpenRewardChestSlotIndex(AbstractContainerMenu menu) {
		if (menu == null) return -1;
		for (int slotIndex = 0; slotIndex < menu.slots.size(); slotIndex++) {
			if (stackIndicatesOpenRewardChest(menu.getSlot(slotIndex).getItem())) return slotIndex;
		}
		return -1;
	}

	private boolean stackIndicatesOpenRewardChest(ItemStack stack) {
		if (stack == null || stack.isEmpty()) return false;
		String name = normalize(cleanText(stack.getHoverName().getString()));
		if (name.contains("OPEN REWARD CHEST") || name.regionMatches(true, 0, "Click to open", 0, 13)) return true;
		for (String line : cleanLoreLines(stack)) {
			String normalized = normalize(line);
			if (normalized.contains("OPEN REWARD CHEST") || normalized.regionMatches(true, 0, "Click to open", 0, 13)) return true;
		}
		return false;
	}

	private boolean invokeInventoryPickupClick(Minecraft client, int menuSlotIndex) {
		if (client == null || client.player == null || client.gameMode == null || client.player.containerMenu == null) return false;
		Object gameMode = client.gameMode;
		Class<?> playerType = client.player.getClass();
		for (java.lang.reflect.Method method : gameMode.getClass().getDeclaredMethods()) {
			Class<?>[] params = method.getParameterTypes();
			if (params.length != 5) continue;
			if (params[0] != int.class || params[1] != int.class || params[2] != int.class) continue;
			if (!params[3].isEnum() || !params[4].isAssignableFrom(playerType)) continue;
			Object pickup = enumConstant(params[3], "PICKUP");
			if (pickup == null) continue;
			try {
				method.setAccessible(true);
				method.invoke(gameMode, client.player.containerMenu.containerId, menuSlotIndex, 0, pickup, client.player);
				return true;
			} catch (ReflectiveOperationException | RuntimeException e) {
				DungeonRunTracker.LOGGER.debug("[DRT] Croesus overlay click failed: {}", e.getMessage());
				return false;
			}
		}
		return false;
	}

	private Object enumConstant(Class<?> enumType, String name) {
		Object[] constants = enumType.getEnumConstants();
		if (constants == null) return null;
		for (Object constant : constants) {
			if (constant instanceof Enum<?> e && e.name().equals(name)) return constant;
		}
		return null;
	}

	private CroesusChestRow croesusOverlayRowAt(Minecraft client, int mouseX, int mouseY) {
		if (!(client.screen instanceof AbstractContainerScreen<?> screen)) return null;
		String normalizedTitle = normalize(screen.getTitle().getString());
		if (!isCroesusChestListTitle(normalizedTitle)) return null;

		List<CroesusChestRow> rows = currentCroesusChestRows(client);
		if (rows.isEmpty()) return null;

		int screenW = client.getWindow().getGuiScaledWidth();
		ScreenBounds bounds = currentContainerBounds(client);
		int x = Math.min(screenW - CHEST_OVERLAY_W - 4, bounds.right() + 8);
		if (x < bounds.right() + 2) x = Math.max(4, bounds.right() + 2);
		int y = Math.max(6, bounds.top() + 2);
		int lineH = client.font.lineHeight + 2;
		int rowH = 14;
		int cursorY = y + lineH + 8;
		for (CroesusChestRow row : rows) {
			if (pointInRect(mouseX, mouseY, x, cursorY - 4, CHEST_OVERLAY_W, rowH)) return row;
			cursorY += rowH;
		}

		CroesusChestRow bestNormal = bestNormalChest(rows);
		CroesusChestRow bestKey = bestKeyChest(rows, bestNormal);
		cursorY += 8 + lineH + 2;
		if (bestNormal != null) {
			if (pointInRect(mouseX, mouseY, x, cursorY - 4, CHEST_OVERLAY_W, rowH)) return bestNormal;
			cursorY += rowH;
		}
		if (bestKey != null && pointInRect(mouseX, mouseY, x, cursorY - 4, CHEST_OVERLAY_W, rowH)) return bestKey;
		return null;
	}

	public void growHudScale(Minecraft client, boolean save, boolean includeResetLine) {
		setHudScale(client, hudScale + HUD_SCALE_STEP, save, includeResetLine);
	}

	public void shrinkHudScale(Minecraft client, boolean save, boolean includeResetLine) {
		setHudScale(client, hudScale - HUD_SCALE_STEP, save, includeResetLine);
	}

	public void setHudScale(Minecraft client, float scale, boolean save) {
		setHudScale(client, scale, save, false);
	}

	public void setHudScale(Minecraft client, float scale, boolean save, boolean includeResetLine) {
		if (client == null) return;
		hudScale = clampHudScale(scale);
		setHudPosition(client, hudX, hudY, false, includeResetLine);
		if (save) saveHudLayout();
	}

	private long lootScreenPendingMillis = 0;
	private DungeonFloor pendingLootScreenFloorFilter = null;
	private String pendingLootScreenSearchFilter = null;
	private boolean openLootScreenRequested;

	public boolean consumeLootScreenPending() {
		if (lootScreenPendingMillis > 0 && System.currentTimeMillis() > lootScreenPendingMillis) {
			lootScreenPendingMillis = 0;
			return true;
		}
		return false;
	}

	public void requestOpenLootScreen() {
		requestOpenLootScreen(selectedFloor, null);
	}

	public void requestOpenLootScreen(DungeonFloor floorFilter, String searchFilter) {
		pendingLootScreenFloorFilter = floorFilter;
		pendingLootScreenSearchFilter = searchFilter;
		openLootScreenRequested = true;
	}

	public boolean consumeOpenLootScreenRequest() {
		if (!openLootScreenRequested) return false;
		openLootScreenRequested = false;
		return true;
	}

	public DungeonFloor getPendingLootScreenFloorFilter() {
		DungeonFloor f = pendingLootScreenFloorFilter;
		pendingLootScreenFloorFilter = null;
		return f;
	}

	public String getPendingLootScreenSearchFilter() {
		String s = pendingLootScreenSearchFilter;
		pendingLootScreenSearchFilter = null;
		return s;
	}

	public void clearSession() {
		sessionActive = false;
		sessionRuns = 0;
		sessionTotalProfit = 0L;
		sessionStartMillis = 0L;
		sessionInRunMillis = 0L;
		sessionTotalRunTimeMs = 0L;
		currentRunStartMillis = 0L;
		currentRunPausedMillis = 0L;
		currentRunPauseStartMillis = 0L;
		currentRunUnavailablePauseStartMillis = 0L;
		currentRunBossTimeMs = 0L;
		lastFinishedRunTimeMs = 0L;
		currentRunActive = false;
		runsPerHrPaused = false;
		sessionGradeRuns.clear();
		sessionFloorRuns.clear();
		sessionFloorProfitTotals.clear();
		sessionFloorRunTimeTotals.clear();
	}

	private long activeInRunElapsedMs() {
		long elapsed = sessionInRunMillis;
		if (!currentRunActive || currentRunStartMillis <= 0L) return Math.max(0L, elapsed);
		long paused = currentRunPausedMillis;
		if (runsPerHrPaused && currentRunPauseStartMillis > 0L) {
			paused += System.currentTimeMillis() - currentRunPauseStartMillis;
		}
		if (currentRunUnavailablePauseStartMillis > 0L) {
			paused += System.currentTimeMillis() - currentRunUnavailablePauseStartMillis;
		}
		long currentElapsed = Math.max(0L, System.currentTimeMillis() - currentRunStartMillis - paused);
		return Math.max(0L, elapsed + currentElapsed);
	}

	private long completedRunElapsedMs() {
		return Math.max(0L, sessionTotalRunTimeMs);
	}

	private void startCurrentRunTiming(long now) {
		if (currentRunActive) finishCurrentRunTiming(now);
		if (!sessionActive) {
			sessionActive = true;
			sessionStartMillis = now;
		}
		currentRunActive = true;
		currentRunStartMillis = now;
		currentRunPausedMillis = 0L;
		currentRunPauseStartMillis = 0L;
		currentRunUnavailablePauseStartMillis = 0L;
		currentRunBossTimeMs = 0L;
		lastFinishedRunTimeMs = 0L;
	}

	private long finishCurrentRunTiming(long now) {
		if (!currentRunActive || currentRunStartMillis <= 0L) return 0L;
		long paused = currentRunPausedMillis;
		if (runsPerHrPaused && currentRunPauseStartMillis > 0L) {
			paused += now - currentRunPauseStartMillis;
		}
		if (currentRunUnavailablePauseStartMillis > 0L) {
			paused += now - currentRunUnavailablePauseStartMillis;
		}
		long elapsed = Math.max(0L, now - currentRunStartMillis - paused);
		sessionInRunMillis += elapsed;
		currentRunActive = false;
		currentRunStartMillis = 0L;
		currentRunPausedMillis = 0L;
		currentRunPauseStartMillis = 0L;
		currentRunUnavailablePauseStartMillis = 0L;
		lastFinishedRunTimeMs = elapsed;
		return elapsed;
	}

	private void pauseCurrentRunForUnavailable(long now) {
		if (!currentRunActive || runsPerHrPaused || currentRunUnavailablePauseStartMillis > 0L) return;
		currentRunUnavailablePauseStartMillis = now;
	}

	private void resumeCurrentRunAfterUnavailable(long now) {
		if (currentRunUnavailablePauseStartMillis <= 0L) return;
		currentRunPausedMillis += Math.max(0L, now - currentRunUnavailablePauseStartMillis);
		currentRunUnavailablePauseStartMillis = 0L;
	}

	private void toggleRunsPerHrPause() {
		long now = System.currentTimeMillis();
		if (runsPerHrPaused) {
			if (currentRunActive && currentRunPauseStartMillis > 0L) {
				currentRunPausedMillis += now - currentRunPauseStartMillis;
				currentRunPauseStartMillis = 0L;
			}
			runsPerHrPaused = false;
		} else {
			runsPerHrPaused = true;
			if (currentRunActive) currentRunPauseStartMillis = now;
		}
	}

	private void handleMessage(Component message, boolean fromGameMessage) {
		if (message == null) return;

		long now = System.currentTimeMillis();
		if (lootCollectionUntilMillis > 0L && now > lootCollectionUntilMillis) {
			flushPendingLootRecord(lootWindowUntilMillis > 0L && now <= lootWindowUntilMillis);
		}
		if (lootWindowUntilMillis > 0L && now > lootWindowUntilMillis) clearLootWindow();

		String rawText = ChatFormatting.stripFormatting(message.getString());
		String cleaned = normalize(rawText);
		if (cleaned.isEmpty()) return;

		expirePendingCompletionState(now);
		List<String> rawLines = splitMessageLines(rawText);
		for (String rawLine : rawLines) {
			String cleanedLine = normalize(rawLine);
			if (!cleanedLine.isEmpty()) handleCompletionLine(rawLine.trim(), cleanedLine, now);
		}

		if (rawLines.size() > 1) {
			for (String rawLine : rawLines) {
				String cleanedLine = normalize(rawLine);
				if (!cleanedLine.isEmpty()) handleLootMessage(rawLine.trim(), cleanedLine, now);
			}
		} else {
			handleLootMessage(rawText == null ? cleaned : rawText.trim(), cleaned, now);
		}
	}

	private void submitMessage(Component message, boolean fromGameMessage) {
		Minecraft.getInstance().execute(() -> handleMessage(message, fromGameMessage));
	}

	private synchronized boolean markMessageForProcessing(Component message) {
		String text = message.getString();
		if (text == null || text.isEmpty()) return true;
		text = text
			.replace("\r\n", "\n")
			.replace('\r', '\n')
			.trim();
		if (text.isEmpty()) return true;
		long now = System.currentTimeMillis();
		for (var iterator = recentSystemMessages.iterator(); iterator.hasNext();) {
			PacketCapturedMessage captured = iterator.next();
			if (now - captured.atMillis > MESSAGE_DEDUP_WINDOW_MS) {
				iterator.remove();
				continue;
			}
			if (captured.text.equals(text)) return false;
		}
		recentSystemMessages.addLast(new PacketCapturedMessage(text, now));
		while (recentSystemMessages.size() > 64) recentSystemMessages.removeFirst();
		return true;
	}

	private void handleCompletionLine(String rawLine, String cleaned, long now) {
		if (handleKuudraLine(cleaned, now)) return;

		if (isDungeonEntryMessage(cleaned)) {
			beginNewDungeonRun(now);
			insideDungeon = true;
		}

		Matcher bossTimeMatcher = BOSS_TIME_PATTERN.matcher(rawLine == null ? cleaned : rawLine);
		if (bossTimeMatcher.find()) {
			long minutes = parsePositiveInt(bossTimeMatcher.group(1), 0);
			long seconds = parsePositiveInt(bossTimeMatcher.group(2), 0);
			currentRunBossTimeMs = (minutes * 60L + seconds) * 1000L;
		}

		DungeonFloor lineFloor = detectFloorFromLine(cleaned);
		if (lineFloor != DungeonFloor.UNKNOWN) currentFloor = lineFloor;

		String preGrade = extractScoreGrade(cleaned);
		if (preGrade != null && pendingScoreGrade == null) {
			pendingScoreGrade = preGrade;
			pendingSPlusFloor = lineFloor != DungeonFloor.UNKNOWN ? lineFloor : currentFloor;
			pendingSPlusUntilMillis = now + EXTRA_STATS_TIMEOUT_MS;
		}

		if (pendingScoreGrade != null && currentRunBossTimeMs > 0L && !runCountedThisDungeon) {
			DungeonFloor scoreFloor = bestCompletionFloor(lineFloor);
			recordCompletedRun(now, scoreFloor, pendingScoreGrade);
			clearPendingCompletionScore();
			return;
		}

		if (isExtraStatsHeader(cleaned)) {
			if (pendingScoreGrade != null && !runCountedThisDungeon) {
				DungeonFloor f = bestCompletionFloor(lineFloor);
				recordCompletedRun(now, f, pendingScoreGrade);
				clearPendingCompletionScore();
			} else {
				DungeonFloor contextFloor = currentFloor != DungeonFloor.UNKNOWN ? currentFloor : pendingSPlusFloor;
				awaitingExtraStatsScore = true;
				awaitingExtraStatsFloor = contextFloor;
				awaitingExtraStatsUntilMillis = now + EXTRA_STATS_TIMEOUT_MS;
			}
			return;
		}

		if (awaitingExtraStatsScore) {
			if (lineFloor != DungeonFloor.UNKNOWN) {
				awaitingExtraStatsFloor = lineFloor;
				pendingSPlusFloor = lineFloor;
			}
			String postGrade = preGrade;
			if (postGrade != null) {
				if (!runCountedThisDungeon) {
					DungeonFloor scoreFloor = awaitingExtraStatsFloor != DungeonFloor.UNKNOWN ? awaitingExtraStatsFloor : currentFloor;
					recordCompletedRun(now, scoreFloor, postGrade);
				}
				awaitingExtraStatsScore = false;
				awaitingExtraStatsFloor = DungeonFloor.UNKNOWN;
				awaitingExtraStatsUntilMillis = 0L;
				clearPendingCompletionScore();
			}
		}

	}

	private boolean extractRenderStateCroesusChestOverlay(Minecraft client, GuiGraphicsExtractor g, int mouseX, int mouseY) {
		if (!(client.screen instanceof AbstractContainerScreen<?> screen)) return false;
		String normalizedTitle = normalize(screen.getTitle().getString());
		if (!isCroesusChestListTitle(normalizedTitle)) return false;

		List<CroesusChestRow> rows = currentCroesusChestRows(client);
		if (rows.isEmpty()) return false;

		int screenW = client.getWindow().getGuiScaledWidth();
		ScreenBounds bounds = currentContainerBounds(client);
		int x = Math.min(screenW - CHEST_OVERLAY_W - 4, bounds.right() + 8);
		if (x < bounds.right() + 2) x = Math.max(4, bounds.right() + 2);
		int y = Math.max(6, bounds.top() + 2);
		int lineH = client.font.lineHeight + 2;
		int rowH = 14;
		int cursorY = y;

		CroesusChestRow bestNormal = bestNormalChest(rows);
		CroesusChestRow bestKey = bestKeyChest(rows, bestNormal);
		CroesusChestRow hoverRow = hoveredChestSlot(rows, mouseX, mouseY);
		CroesusChestRow tooltipRow = null;
		boolean itemTooltipActive = hoverRow != null;
		int hoverColor = hoverRow == null ? 0 : chestTitleColor(hoverRow.displayName);

		if (!itemTooltipActive) {
			drawOverlayText(client, g, "Chests", x, cursorY, OVERLAY_TEXT, true);
			cursorY += lineH + 8;
			for (CroesusChestRow row : rows) {
				if (pointInRect(mouseX, mouseY, x, cursorY - 4, CHEST_OVERLAY_W, rowH)) {
					hoverRow = row;
					tooltipRow = row;
					hoverColor = chestTitleColor(row.displayName);
				}
				drawCroesusChestRow(client, g, row, x, cursorY, row.normalProfitCoins, false);
				cursorY += rowH;
			}

			cursorY += 8;
			drawOverlayText(client, g, "Open", x, cursorY, OVERLAY_TEXT, true);
			cursorY += lineH + 2;
			if (bestNormal != null) {
				if (pointInRect(mouseX, mouseY, x, cursorY - 4, CHEST_OVERLAY_W, rowH)) {
					hoverRow = bestNormal;
					tooltipRow = bestNormal;
					hoverColor = OVERLAY_PROFIT;
				}
				drawCroesusOpenRow(client, g, bestNormal, x, cursorY, 1, bestNormal.normalProfitCoins, new ItemStack(Items.AIR));
				cursorY += rowH;
			}
			if (bestKey != null) {
				if (pointInRect(mouseX, mouseY, x, cursorY - 4, CHEST_OVERLAY_W, rowH)) {
					hoverRow = bestKey;
					tooltipRow = bestKey;
					hoverColor = 0xFFFFDD55;
				}
				drawCroesusOpenRow(client, g, bestKey, x, cursorY, 2, bestKey.keyProfitCoins, new ItemStack(Items.TRIPWIRE_HOOK));
			}
		}

		if (bestNormal != null) drawSlotHighlight(g, bestNormal, OVERLAY_PROFIT, false);
		if (bestKey != null) drawSlotHighlight(g, bestKey, 0xFFFFDD55, false);
		if (hoverRow != null) drawSlotHighlight(g, hoverRow, hoverColor, true);
		if (tooltipRow != null) {
			drawTooltip(client, g, List.of(
				"Click to open " + tooltipRow.displayName + " chest",
				"Right-click to open and confirm reward"
			), mouseX, mouseY);
		}
		return true;
	}

	private void extractRenderStateCroesusMainMenuHighlights(Minecraft client, GuiGraphicsExtractor g) {
		for (CroesusRunSlot slot : currentCroesusMainMenuUnopenedSlots(client)) {
			drawSlotHighlight(g, slot.slotX, slot.slotY, OVERLAY_PROFIT, false);
		}
	}

	private void drawCroesusChestRow(Minecraft client, GuiGraphicsExtractor g, CroesusChestRow row, int x, int y, long profitCoins, boolean bold) {
		ItemStack icon = row.icon.isEmpty() ? new ItemStack(Items.CHEST) : row.icon;
		g.item(icon, x, y - 4);
		String value = row.alreadyOpened ? "Opened" : signedCoins(profitCoins);
		int valueW = client.font.width(value);
		int nameMaxW = CHEST_OVERLAY_W - 20 - valueW - (row.kismetRerolled ? 22 : 6);
		String visibleName = ellipsize(client, row.displayName, Math.max(16, nameMaxW));
		drawOverlayText(client, g, visibleName, x + 18, y, chestTitleColor(row.displayName), bold);
		if (row.kismetRerolled) {
			g.item(new ItemStack(Items.FEATHER), x + 20 + client.font.width(visibleName), y - 4);
		}
		drawOverlayText(client, g, value, x + CHEST_OVERLAY_W - valueW, y, row.alreadyOpened ? OVERLAY_ESSENCE : (profitCoins >= 0L ? OVERLAY_PROFIT : OVERLAY_LOSS), false);
	}

	private void drawCroesusOpenRow(Minecraft client, GuiGraphicsExtractor g, CroesusChestRow row, int x, int y, int rank, long profitCoins, ItemStack modifierIcon) {
		drawOverlayText(client, g, rank + ".", x, y, OVERLAY_MUTED, false);
		int iconX = x + 16;
		if (!modifierIcon.isEmpty()) {
			g.item(modifierIcon, iconX, y - 4);
			iconX += 18;
		}
		ItemStack chestIcon = row.icon.isEmpty() ? new ItemStack(Items.CHEST) : row.icon;
		g.item(chestIcon, iconX, y - 4);
		String label = row.displayName;
		String value = signedCoins(profitCoins);
		int valueW = client.font.width(value);
		int nameMaxW = CHEST_OVERLAY_W - (iconX - x) - 20 - valueW - (row.kismetRerolled ? 20 : 0);
		String visibleLabel = ellipsize(client, label, Math.max(16, nameMaxW));
		drawOverlayText(client, g, visibleLabel, iconX + 18, y, chestTitleColor(row.displayName), true);
		if (row.kismetRerolled) {
			g.item(new ItemStack(Items.FEATHER), iconX + 20 + client.font.width(visibleLabel), y - 4);
		}
		drawOverlayText(client, g, value, x + CHEST_OVERLAY_W - valueW, y, profitCoins >= 0L ? OVERLAY_PROFIT : OVERLAY_LOSS, false);
	}

	private List<CroesusChestRow> currentCroesusChestRows(Minecraft client) {
		if (!(client.screen instanceof AbstractContainerScreen<?> screen) || client.player == null) return List.of();
		AbstractContainerMenu menu = client.player.containerMenu;
		if (menu == null) return List.of();

		String normalizedTitle = normalize(screen.getTitle().getString());
		boolean kuudraContext = isKuudraRewardContext(normalizedTitle);
		Map<String, CroesusChestRow> byTitle = new HashMap<>();
		ScreenBounds bounds = currentContainerBounds(client);
		for (int slotIndex = 0; slotIndex < menu.slots.size(); slotIndex++) {
			Slot slot = menu.slots.get(slotIndex);
			ItemStack stack = slot.getItem();
			if (stack.isEmpty()) continue;
			String canonicalKey = canonicalChestTitleFromStack(stack);
			if (canonicalKey == null || !isTrackedRewardChest(canonicalKey, kuudraContext)) continue;

			CHEST_ICON_CACHE.putIfAbsent(canonicalKey, stack.copy());
			DungeonChestOffer offer = cachedChestOffersByTitle.get(canonicalKey);
			if (offer == null) continue;
			List<DungeonLootEntry> entries = offer.lootEntries == null ? List.of() : offer.lootEntries;
			long value = offerValueCoins(canonicalKey, entries, offer.valueCoins);
			ChestCostBreakdown breakdown = offer.costBreakdown == null ? new ChestCostBreakdown() : offer.costBreakdown.copy();
			applyKuudraChestCostHint(canonicalKey, breakdown);
			populateKnownModifierCosts(breakdown);
			suppressDungeonChestKeyForKuudra(canonicalKey, breakdown);
			long keyCost = isCatacombsRewardChest(canonicalKey) ? resolveModifierItemCost(ITEM_DUNGEON_CHEST_KEY) : 0L;
			boolean kismetRerolled = breakdown.usedKismetFeather;
			String display = shortChestName(toDisplayChestTitle(canonicalKey));
			long normalProfit = value - breakdown.totalCostCoins();
			byTitle.put(canonicalKey, new CroesusChestRow(canonicalKey, display, stack.copy(), slotIndex, bounds.left + slot.x, bounds.top + slot.y, normalProfit, keyCost > 0L ? normalProfit - keyCost : Long.MIN_VALUE, offer.alreadyOpened, kismetRerolled));
		}

		List<CroesusChestRow> rows = new ArrayList<>();
		List<String> orderedTitles = kuudraContext
			? List.of("FREE CHEST", "PAID CHEST")
			: List.of("BEDROCK CHEST", "OBSIDIAN CHEST", "EMERALD CHEST", "DIAMOND CHEST", "GOLD CHEST", "WOOD CHEST");
		for (String title : orderedTitles) {
			CroesusChestRow row = byTitle.get(title);
			if (row != null) rows.add(row);
		}
		return rows;
	}

	private CroesusChestRow bestNormalChest(List<CroesusChestRow> rows) {
		CroesusChestRow best = null;
		for (CroesusChestRow row : rows) {
			if (row.alreadyOpened) continue;
			if (best == null || row.normalProfitCoins > best.normalProfitCoins) best = row;
		}
		return best;
	}

	private CroesusChestRow bestKeyChest(List<CroesusChestRow> rows, CroesusChestRow bestNormal) {
		CroesusChestRow best = null;
		for (CroesusChestRow row : rows) {
			if (row.alreadyOpened) continue;
			if (bestNormal != null && row.canonicalTitle.equals(bestNormal.canonicalTitle)) continue;
			if (row.keyProfitCoins <= 0L || row.keyProfitCoins == Long.MIN_VALUE) continue;
			if (best == null || row.keyProfitCoins > best.keyProfitCoins) best = row;
		}
		return best;
	}

	private CroesusChestRow hoveredChestSlot(List<CroesusChestRow> rows, int mouseX, int mouseY) {
		for (CroesusChestRow row : rows) {
			if (pointInRect(mouseX, mouseY, row.slotX, row.slotY, 16, 16)) return row;
		}
		return null;
	}

	private void drawSlotHighlight(GuiGraphicsExtractor g, CroesusChestRow row, int color, boolean hovered) {
		drawSlotHighlight(g, row.slotX, row.slotY, color, hovered);
	}

	private void drawSlotHighlight(GuiGraphicsExtractor g, int slotX, int slotY, int color, boolean hovered) {
		int fillColor = withAlpha(color, hovered ? 0x88 : 0x24);
		int borderColor = withAlpha(color, hovered ? 0xFF : 0xCC);
		int left = slotX - 1;
		int top = slotY - 1;
		int right = slotX + 17;
		int bottom = slotY + 17;
		g.fill(left, top, right, bottom, fillColor);
		g.fill(left, top, right, top + 1, borderColor);
		g.fill(left, bottom - 1, right, bottom, borderColor);
		g.fill(left, top, left + 1, bottom, borderColor);
		g.fill(right - 1, top, right, bottom, borderColor);
	}

	private boolean pointInRect(int mouseX, int mouseY, int x, int y, int width, int height) {
		return mouseX >= x && mouseY >= y && mouseX < x + width && mouseY < y + height;
	}

	private int withAlpha(int color, int alpha) {
		return (color & 0x00FFFFFF) | ((alpha & 0xFF) << 24);
	}

	private String signedCoins(long coins) {
		return coins >= 0L ? "+" + formatCoins(coins) : formatCoins(coins);
	}

	private String shortChestName(String title) {
		if (title == null) return "Chest";
		return title.replace(" Chest", "").trim();
	}

	private void extractRenderStateChestBreakdownOverlay(Minecraft client, GuiGraphicsExtractor g, int mouseX, int mouseY) {
		OverlayChestData data = currentOverlayChestData(client);
		if (data == null) return;

		int rowH = 14;
		int lineH = client.font.lineHeight + 2;
		List<DungeonLootEntry> itemEntries = new ArrayList<>();
		List<DungeonLootEntry> essenceEntries = new ArrayList<>();
		for (DungeonLootEntry entry : data.entries) {
			if (isEssenceEntry(entry)) essenceEntries.add(entry);
			else itemEntries.add(entry);
		}
		int itemRows = Math.min(4, itemEntries.size());
		int hiddenRows = Math.max(0, itemEntries.size() - itemRows);
		int screenW = client.getWindow().getGuiScaledWidth();
		int screenH = client.getWindow().getGuiScaledHeight();
		ScreenBounds bounds = currentContainerBounds(client);
		int x = Math.min(screenW - CHEST_OVERLAY_W - 4, bounds.right() + 8);
		if (x < bounds.right() + 2) x = Math.max(4, bounds.right() + 2);
		int y = Math.max(6, Math.min(screenH - 12, bounds.top() + 2));

		if (pointInRect(mouseX, mouseY, x, y - 4, CHEST_OVERLAY_W, lineH + 4)) {
			drawTooltip(client, g, List.of("Click to view " + data.chestTitle + " in loot history", "Double-click to open chest in Croesus"), mouseX, mouseY);
		}

		int cursorY = y;
		ItemStack chestIcon = getChestIcon(data.chestTitle);
		if (chestIcon.isEmpty()) chestIcon = new ItemStack(Items.CHEST);
		g.item(chestIcon, x, cursorY - 4);
		drawOverlayText(client, g, data.chestTitle, x + 18, cursorY, chestTitleColor(data.chestTitle), true);

		cursorY += lineH + 8;
		for (int i = 0; i < itemRows; i++) {
			cursorY = drawOverlayEntry(client, g, x, cursorY, itemEntries.get(i), rowH);
		}
		for (DungeonLootEntry essenceEntry : essenceEntries) {
			cursorY = drawOverlayEntry(client, g, x, cursorY, essenceEntry, rowH);
		}
		if (hiddenRows > 0) {
			String more = "+" + hiddenRows + " more";
			drawOverlayText(client, g, more, x + 18, cursorY, OVERLAY_MUTED, false);
			cursorY += rowH;
		}

		cursorY += 8;
		drawOverlayMetric(client, g, x, cursorY, "Value", data.valueCoins, OVERLAY_VALUE, false);
		cursorY += lineH + 8;
		drawOverlayMetric(client, g, x, cursorY, "Open Cost", data.breakdown.totalCostCoins(), OVERLAY_COST, true);
		cursorY += lineH;
		if (data.breakdown.usedDungeonChestKey || data.breakdown.dungeonChestKeyCostCoins > 0L) {
			drawOverlayCostWithIcon(client, g, x, cursorY, "Chest Key", data.breakdown.dungeonChestKeyCostCoins, new ItemStack(Items.TRIPWIRE_HOOK));
			if (pointInRect(mouseX, mouseY, x, cursorY - 4, CHEST_OVERLAY_W, lineH + 2)) {
				drawTooltip(client, g, "Click to buy a dungeon chest key", mouseX, mouseY);
			}
			cursorY += lineH;
		}
		if (data.breakdown.usedKismetFeather || data.breakdown.kismetFeatherCostCoins > 0L) {
			drawOverlayCostWithIcon(client, g, x, cursorY, "Kismet", data.breakdown.kismetFeatherCostCoins, new ItemStack(Items.FEATHER));
			cursorY += lineH;
		}
		if (data.breakdown.usedWheelOfFate || data.breakdown.wheelOfFateCostCoins > 0L) {
			drawOverlayCostWithIcon(client, g, x, cursorY, "Wheel", data.breakdown.wheelOfFateCostCoins, new ItemStack(Items.CLOCK));
			cursorY += lineH;
		}
		if (data.breakdown.usedKuudraKey || data.breakdown.kuudraKeyCostCoins > 0L) {
			drawOverlayCostWithIcon(client, g, x, cursorY, "Kuudra Key", data.breakdown.kuudraKeyCostCoins, new ItemStack(Items.TRIPWIRE_HOOK));
			cursorY += lineH;
		}
		cursorY += 8;
		drawOverlayMetric(client, g, x, cursorY, "Profit", data.profitCoins, data.profitCoins >= 0L ? OVERLAY_PROFIT : OVERLAY_LOSS, false);
	}

	private int drawOverlayEntry(Minecraft client, GuiGraphicsExtractor g, int x, int y, DungeonLootEntry entry, int rowH) {
		DrtConfig config = DrtConfigManager.getConfig();
		long totalPrice = DungeonProfitPricing.resolveTotalPrice(entry, config);
		boolean forceSalvage = DungeonProfitPricing.isForcedSalvageValued(entry, config);
		ItemStack icon = overlayItemIcon(entry.itemId);
		if (icon.isEmpty()) icon = new ItemStack(Items.PAPER);
		g.item(icon, x, y - 4);
		OverlayItemDisplay display = overlayItemDisplay(entry);
		String label = display.name;
		String quantityText = entry.quantity > 1 ? " x" + entry.quantity : "";
		String priceText = formatCoins(totalPrice);
		int priceW = client.font.width(priceText);
		int quantityW = client.font.width(quantityText);
		int salvageIconW = forceSalvage ? 18 : 0;
		String visibleLabel = ellipsize(client, label, CHEST_OVERLAY_W - 28 - priceW - quantityW - salvageIconW);
		drawOverlayText(client, g, visibleLabel, x + 18, y, display.color, display.bold);
		int afterNameX = x + 18 + client.font.width(visibleLabel);
		if (forceSalvage) {
			g.item(forcedSalvageEssenceIcon(), afterNameX + 2, y - 4);
			afterNameX += salvageIconW;
		}
		if (!quantityText.isEmpty()) {
			drawOverlayText(client, g, quantityText, afterNameX, y, OVERLAY_MUTED, false);
		}
		int priceX = x + CHEST_OVERLAY_W - priceW;
		drawOverlayText(client, g, priceText, priceX, y, OVERLAY_VALUE, false);
		return y + rowH;
	}

	private ItemStack forcedSalvageEssenceIcon() {
		ItemStack icon = overlayItemIcon("ESSENCE_CRIMSON");
		return icon.isEmpty() ? new ItemStack(Items.NETHER_STAR) : icon;
	}

	private void drawOverlayMetric(Minecraft client, GuiGraphicsExtractor g, int x, int y, String label, long coins, int valueColor, boolean negative) {
		String value = (negative && coins > 0L ? "-" : "") + formatCoins(coins);
		drawOverlayText(client, g, label, x, y, OVERLAY_MUTED, false);
		drawOverlayText(client, g, value, x + CHEST_OVERLAY_W - client.font.width(value), y, valueColor, false);
	}

	private void drawOverlayCostWithIcon(Minecraft client, GuiGraphicsExtractor g, int x, int y, String label, long coins, ItemStack icon) {
		g.item(icon, x, y - 4);
		String value = coins > 0L ? "-" + formatCoins(coins) : "-?";
		drawOverlayText(client, g, label, x + 18, y, OVERLAY_MUTED, false);
		drawOverlayText(client, g, value, x + CHEST_OVERLAY_W - client.font.width(value), y, OVERLAY_COST, false);
	}

	private void drawOverlayText(Minecraft client, GuiGraphicsExtractor g, String text, int x, int y, int color, boolean bold) {
		if (!bold) {
			g.text(client.font, text, x, y, color, true);
			return;
		}
		g.text(client.font, Component.literal(text).withStyle(ChatFormatting.BOLD), x, y, color, true);
	}

	private ScreenBounds currentContainerBounds(Minecraft client) {
		if (!(client.screen instanceof AbstractContainerScreen<?> screen)) {
			int width = 176;
			int height = 166;
			return new ScreenBounds((client.getWindow().getGuiScaledWidth() - width) / 2, (client.getWindow().getGuiScaledHeight() - height) / 2, width, height);
		}
		if (screen instanceof AbstractContainerScreenAccessor accessor) {
			return new ScreenBounds(accessor.drt$getLeftPos(), accessor.drt$getTopPos(), accessor.drt$getImageWidth(), accessor.drt$getImageHeight());
		}
		int width = reflectedScreenInt(screen, 176, "imageWidth", "backgroundWidth");
		int height = reflectedScreenInt(screen, 166, "imageHeight", "backgroundHeight");
		int left = reflectedScreenInt(screen, (client.getWindow().getGuiScaledWidth() - width) / 2, "leftPos", "x");
		int top = reflectedScreenInt(screen, (client.getWindow().getGuiScaledHeight() - height) / 2, "topPos", "y");
		return new ScreenBounds(left, top, width, height);
	}

	private int reflectedScreenInt(Object target, int fallback, String... names) {
		Class<?> type = target.getClass();
		while (type != null) {
			for (String name : names) {
				try {
					java.lang.reflect.Field field = type.getDeclaredField(name);
					field.setAccessible(true);
					return field.getInt(target);
				} catch (ReflectiveOperationException | RuntimeException ignored) {
				}
			}
			type = type.getSuperclass();
		}
		return fallback;
	}

	private List<CroesusRunSlot> currentCroesusMainMenuUnopenedSlots(Minecraft client) {
		if (!(client.screen instanceof AbstractContainerScreen<?> screen) || client.player == null) return List.of();
		String normalizedTitle = normalize(screen.getTitle().getString());
		if (!isCroesusMainMenuTitle(normalizedTitle)) return List.of();

		AbstractContainerMenu menu = client.player.containerMenu;
		if (menu == null) return List.of();
		ScreenBounds bounds = currentContainerBounds(client);
		List<CroesusRunSlot> slots = new ArrayList<>();
		for (int slotIndex = 0; slotIndex < menu.slots.size(); slotIndex++) {
			Slot slot = menu.slots.get(slotIndex);
			ItemStack stack = slot.getItem();
			if (stack.isEmpty()) continue;
			if (!croesusRunHasUnopenedChest(stack)) continue;
			slots.add(new CroesusRunSlot(bounds.left + slot.x, bounds.top + slot.y));
		}
		return slots;
	}

	private boolean isCroesusMainMenuTitle(String normalizedTitle) {
		if (normalizedTitle == null || normalizedTitle.isBlank()) return false;
		return normalizedTitle.equals("CROESUS")
			|| normalizedTitle.equals("CROESUS CHEST")
			|| normalizedTitle.equals("CROESUS MENU")
			|| normalizedTitle.equals("VESUVIUS")
			|| normalizedTitle.contains("VESUVIUS");
	}

	private boolean isCroesusChestListTitle(String normalizedTitle) {
		if (normalizedTitle == null || normalizedTitle.isBlank()) return false;
		if (canonicalRewardChestTitle(normalizedTitle) != null) return false;
		return isRewardsMenuTitle(normalizedTitle)
			|| normalizedTitle.contains("CROESUS")
			|| normalizedTitle.contains("VESUVIUS");
	}

	private boolean croesusRunHasUnopenedChest(ItemStack stack) {
		for (String line : cleanLoreLines(stack)) {
			String normalized = normalize(line);
			if (lineClearlyIndicatesUnopenedChest(normalized)) return true;
		}
		return false;
	}

	private boolean lineClearlyIndicatesUnopenedChest(String normalized) {
		if (normalized == null || !normalized.contains("CHEST")) return false;
		if (normalized.contains("OPENED CHEST:")) return false;
		if (normalized.contains("NO CHESTS OPENED YET")) return true;
		if (normalized.contains("NO UNOPENED") || normalized.contains("NO UNCLAIMED") || normalized.contains("0 UNOPENED") || normalized.contains("0 UNCLAIMED")) return false;
		return normalized.contains("UNOPENED")
			|| normalized.contains("UNCLAIMED")
			|| normalized.contains("AVAILABLE")
			|| normalized.contains("OPENABLE")
			|| normalized.contains("NOT OPENED");
	}

	private OverlayChestData currentOverlayChestData(Minecraft client) {
		if (!(client.screen instanceof AbstractContainerScreen<?> screen)) return null;
		String normalizedTitle = normalize(screen.getTitle().getString());
		String canonicalTitle = canonicalRewardChestTitle(normalizedTitle);
		if (canonicalTitle == null) {
			if (!pendingLootChestAssigned || pendingLootChestTitle.isBlank()) return null;
			canonicalTitle = pendingLootChestTitle.toUpperCase(Locale.ROOT);
		}
		DungeonChestOffer offer = cachedChestOffersByTitle.get(canonicalTitle);
		if (offer == null && pendingLootChestAssigned && toDisplayChestTitle(canonicalTitle).equalsIgnoreCase(pendingLootChestTitle)) {
			offer = new DungeonChestOffer(pendingLootChestTitle, pendingLootCostBreakdown, 0L, pendingLootEntries);
		}
		if (offer == null || offer.lootEntries == null || offer.lootEntries.isEmpty()) return null;

		ChestCostBreakdown breakdown = offer.costBreakdown == null ? new ChestCostBreakdown() : offer.costBreakdown.copy();
		if (pendingLootChestAssigned && toDisplayChestTitle(canonicalTitle).equalsIgnoreCase(pendingLootChestTitle)) {
			ChestCostBreakdown pending = pendingLootCostBreakdown == null ? new ChestCostBreakdown() : pendingLootCostBreakdown.copy();
			if (breakdown.baseChestCostCoins <= 0L) breakdown.baseChestCostCoins = pending.baseChestCostCoins;
			if (pending.usedDungeonChestKey) {
				breakdown.usedDungeonChestKey = true;
				breakdown.dungeonChestKeyCostCoins = pending.dungeonChestKeyCostCoins;
			}
			if (pending.usedKismetFeather) {
				breakdown.usedKismetFeather = true;
				breakdown.kismetRerolledChestOpened = pending.kismetRerolledChestOpened;
				breakdown.kismetFeatherCostCoins = pending.kismetFeatherCostCoins;
			}
			if (pending.usedWheelOfFate) {
				breakdown.usedWheelOfFate = true;
				breakdown.wheelOfFateCostCoins = pending.wheelOfFateCostCoins;
			}
			if (pending.usedKuudraKey) {
				breakdown.usedKuudraKey = true;
				breakdown.kuudraKeyCostCoins = pending.kuudraKeyCostCoins;
			}
		} else if (isCatacombsRewardChest(canonicalTitle) && shouldPreviewArmedKismetForChest(canonicalTitle)) {
			applyArmedRewardModifiersForPreview(breakdown);
		}
		applyKuudraChestCostHint(canonicalTitle, breakdown);
		populateKnownModifierCosts(breakdown);
		suppressDungeonChestKeyForKuudra(canonicalTitle, breakdown);
		List<DungeonLootEntry> entries = new ArrayList<>();
		for (DungeonLootEntry entry : offer.lootEntries) {
			if (entry != null) entries.add(entry.copy());
		}
		long value = offerValueCoins(canonicalTitle, entries, offer.valueCoins);
		return new OverlayChestData(toDisplayChestTitle(canonicalTitle), entries, breakdown, value, value - breakdown.totalCostCoins());
	}

	private long offerValueCoins(String canonicalTitle, List<DungeonLootEntry> entries, long loreValueCoins) {
		long calculated = DungeonProfitPricing.calculateLootValue(entries == null ? List.of() : entries, DrtConfigManager.getConfig());
		if (isKuudraRewardChest(canonicalTitle) && calculated > 0L) return calculated;
		return loreValueCoins > 0L ? loreValueCoins : calculated;
	}

	private void applyArmedRewardModifiersForPreview(ChestCostBreakdown breakdown) {
		if (breakdown == null) return;
		if (nextOpenedChestUsesDungeonChestKey) breakdown.usedDungeonChestKey = true;
		if (nextOpenedChestUsesKismetFeather || rewardMenuKismetRerollPending) {
			breakdown.usedKismetFeather = true;
		}
		if (nextOpenedChestUsesWheelOfFate) breakdown.usedWheelOfFate = true;
	}

	private boolean shouldPreviewArmedKismetForChest(String canonicalTitle) {
		if (!nextOpenedChestUsesKismetFeather && !rewardMenuKismetRerollPending) return false;
		return rewardMenuKismetRerolledChestTitle != null
			&& !rewardMenuKismetRerolledChestTitle.isBlank()
			&& rewardMenuKismetRerolledChestTitle.equals(canonicalTitle);
	}

	private String canonicalRewardChestTitle(String normalizedTitle) {
		if (normalizedTitle == null || normalizedTitle.isBlank()) return null;
		if (REWARD_CHEST_TITLES.contains(normalizedTitle)) return normalizedTitle;
		String mapped = TIER_NAME_TO_CHEST_TITLE.get(normalizedTitle);
		if (mapped != null) return mapped;
		for (String title : REWARD_CHEST_TITLES) {
			if (normalizedTitle.startsWith(title)) return title;
			String shortTitle = title.replace(" CHEST", "");
			if (normalizedTitle.equals(shortTitle) || normalizedTitle.startsWith(shortTitle + " ")) return title;
		}
		return null;
	}

	private ItemStack overlayItemIcon(String itemId) {
		if (itemId == null || itemId.isBlank()) return ItemStack.EMPTY;
		String id = normalizeOverlayItemId(itemId.toUpperCase(Locale.ROOT));
		String visualId = visualOverlayItemId(id);
		ItemStack cached = getItemIcon(visualId);
		if (!cached.isEmpty()) return cached;
		if (visualId.equals(ITEM_DUNGEON_CHEST_KEY)) return new ItemStack(Items.TRIPWIRE_HOOK);
		if (visualId.equals(ITEM_KISMET_FEATHER)) return new ItemStack(Items.FEATHER);
		if (visualId.equals(ITEM_WHEEL_OF_FATE)) return new ItemStack(Items.CLOCK);
		if (visualId.startsWith("ENCHANTMENT_")) return new ItemStack(Items.ENCHANTED_BOOK);
		if (visualId.equals("FUMING_POTATO_BOOK")) return new ItemStack(Items.BOOK);
		if (visualId.equals("RECOMBOBULATOR_3000")) return new ItemStack(Items.GOLD_INGOT);
		NeuItemResolver.enqueue(visualId);
		ItemStack resolved = NeuItemResolver.getResolvedStack(visualId);
		if (resolved != null && !resolved.isEmpty() && !resolved.is(Items.BARRIER)) return resolved;
		if (visualId.startsWith("ESSENCE_")) return new ItemStack(Items.NETHER_STAR);
		if (id.startsWith("SHARD_")) return new ItemStack(Items.AMETHYST_SHARD);
		if (visualId.contains("SWORD") || visualId.contains("DAGGER") || visualId.contains("BLADE")) return new ItemStack(Items.IRON_SWORD);
		if (visualId.contains("BOW")) return new ItemStack(Items.BOW);
		if (visualId.contains("HELMET")) return new ItemStack(Items.LEATHER_HELMET);
		if (visualId.contains("CHESTPLATE")) return new ItemStack(Items.LEATHER_CHESTPLATE);
		if (visualId.contains("LEGGINGS")) return new ItemStack(Items.LEATHER_LEGGINGS);
		if (visualId.contains("BOOTS")) return new ItemStack(Items.LEATHER_BOOTS);
		return ItemStack.EMPTY;
	}

	private String visualOverlayItemId(String id) {
		if (id == null || id.isBlank()) return "";
		if (id.startsWith("SHARD_")) {
			String shardInternalName = NeuItemResolver.getAttributeShardInternalName(id);
			if (shardInternalName != null && !shardInternalName.isBlank()) return shardInternalName.toUpperCase(Locale.ROOT);
		}
		return id;
	}

	private String normalizeOverlayItemId(String id) {
		return switch (id) {
			case "NECRONS_HANDLE" -> "NECRON_HANDLE";
			case "SCARFS_STUDIES" -> "SCARF_STUDIES";
			case "SPIRIT_STONE" -> "SPIRIT_DECOY";
			case "SPIRIT_BOOTS" -> "THORNS_BOOTS";
			case "WARPED_STONE" -> "AOTE_STONE";
			case "ADAPTIVE_BLADE" -> "STONE_BLADE";
			case "WITHER_CLOAK_SWORD" -> "WITHER_CLOAK";
			default -> id;
		};
	}

	private OverlayItemDisplay overlayItemDisplay(DungeonLootEntry entry) {
		String rawName = entry == null || entry.rawName == null ? "Unknown Item" : entry.rawName.trim();
		String itemId = entry == null || entry.itemId == null ? "" : entry.itemId.trim().toUpperCase(Locale.ROOT);
		String visualItemId = visualOverlayItemId(normalizeOverlayItemId(itemId));
		Matcher bookMatcher = ENCHANTED_BOOK_PATTERN.matcher(rawName);
		if (bookMatcher.matches()) {
			String enchantName = bookMatcher.group(1).trim();
			String displayName = enchantName + " " + romanToArabic(bookMatcher.group(2));
			return new OverlayItemDisplay(displayName, isUltimateEnchantName(enchantName, itemId), isUltimateEnchantName(enchantName, itemId) ? OVERLAY_ULTIMATE : OVERLAY_BOOK);
		}
		if (itemId.startsWith("ENCHANTMENT_")) {
			String displayName = enchantDisplayFromItemId(itemId);
			return new OverlayItemDisplay(displayName, isUltimateEnchantName(displayName, itemId), isUltimateEnchantName(displayName, itemId) ? OVERLAY_ULTIMATE : OVERLAY_BOOK);
		}
		String upper = rawName.toUpperCase(Locale.ROOT);
		if (upper.contains("ESSENCE") || itemId.startsWith("ESSENCE_")) {
			return new OverlayItemDisplay(rawName, false, OVERLAY_ESSENCE);
		}
		NeuItemResolver.enqueue(visualItemId);
		Integer resolvedColor = NeuItemResolver.getColor(visualItemId);
		if (resolvedColor == null) resolvedColor = NeuItemResolver.getColor(itemId);
		if (resolvedColor != null) {
			return new OverlayItemDisplay(rawName, false, resolvedColor | 0xFF000000);
		}
		if (isKuudraItemId(itemId)) {
			return new OverlayItemDisplay(rawName, false, 0xFFFF55FF);
		}
		return new OverlayItemDisplay(rawName, false, OVERLAY_TEXT);
	}

	private boolean isKuudraItemId(String itemId) {
		if (itemId == null || itemId.isBlank()) return false;
		String id = itemId.toUpperCase(Locale.ROOT);
		return id.startsWith("SHARD_")
			|| id.contains("KUUDRA")
			|| id.startsWith("CRIMSON_")
			|| id.startsWith("TERROR_")
			|| id.startsWith("AURORA_")
			|| id.startsWith("FERVOR_")
			|| id.startsWith("HOLLOW_")
			|| id.startsWith("HOT_")
			|| id.startsWith("BURNING_")
			|| id.startsWith("FIERY_")
			|| id.startsWith("INFERNAL_")
			|| id.startsWith("MOLTEN_")
			|| id.equals("TORMENTOR");
	}

	private boolean isEssenceEntry(DungeonLootEntry entry) {
		if (entry == null) return false;
		String rawName = entry.rawName == null ? "" : entry.rawName.toUpperCase(Locale.ROOT);
		String itemId = entry.itemId == null ? "" : entry.itemId.toUpperCase(Locale.ROOT);
		return rawName.contains("ESSENCE") || itemId.startsWith("ESSENCE_");
	}

	private boolean isUltimateEnchantName(String displayName, String itemId) {
		if (itemId != null && itemId.startsWith("ENCHANTMENT_ULTIMATE_")) return true;
		String normalized = toItemIdPart(displayName);
		return ULTIMATE_ENCHANTS.contains(normalized);
	}

	private String enchantDisplayFromItemId(String itemId) {
		String id = itemId;
		if (id.startsWith("ENCHANTMENT_ULTIMATE_")) id = id.substring("ENCHANTMENT_ULTIMATE_".length());
		else if (id.startsWith("ENCHANTMENT_")) id = id.substring("ENCHANTMENT_".length());
		String[] parts = id.split("_");
		if (parts.length == 0) return itemId;
		String level = parts[parts.length - 1];
		StringBuilder name = new StringBuilder();
		for (int i = 0; i < parts.length - 1; i++) {
			if (parts[i].isBlank()) continue;
			if (name.length() > 0) name.append(' ');
			String part = parts[i].toLowerCase(Locale.ROOT);
			name.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
		}
		if (name.length() == 0) return itemId;
		return name + " " + level;
	}

	private String romanToArabic(String roman) {
		int value = parseRomanNumeral(roman);
		return value > 0 ? Integer.toString(value) : roman;
	}

	private int parseRomanNumeral(String roman) {
		if (roman == null || roman.isBlank()) return -1;
		String normalized = roman.trim().toUpperCase(Locale.ROOT);
		int total = 0;
		int previous = 0;
		for (int i = normalized.length() - 1; i >= 0; i--) {
			int value = switch (normalized.charAt(i)) {
				case 'I' -> 1;
				case 'V' -> 5;
				case 'X' -> 10;
				default -> -1;
			};
			if (value <= 0) return -1;
			if (value < previous) total -= value;
			else {
				total += value;
				previous = value;
			}
		}
		return total;
	}

	private String ellipsize(Minecraft client, String text, int maxWidth) {
		if (client.font.width(text) <= maxWidth) return text;
		String suffix = "...";
		int suffixW = client.font.width(suffix);
		String trimmed = text;
		while (!trimmed.isEmpty() && client.font.width(trimmed) + suffixW > maxWidth) {
			trimmed = trimmed.substring(0, trimmed.length() - 1);
		}
		return trimmed.isEmpty() ? suffix : trimmed + suffix;
	}

	private int chestTitleColor(String title) {
		String upper = title == null ? "" : title.toUpperCase(Locale.ROOT);
		if (upper.contains("WOOD") || upper.contains("FREE")) return 0xFFB88554;
		if (upper.contains("GOLD")) return 0xFFFFD85A;
		if (upper.contains("DIAMOND")) return 0xFF6FE7FF;
		if (upper.contains("EMERALD")) return 0xFF55FF88;
		if (upper.contains("OBSIDIAN")) return 0xFFAA74FF;
		if (upper.contains("BEDROCK")) return 0xFFFF74D4;
		return OVERLAY_TEXT;
	}

	private boolean handleKuudraLine(String cleaned, long now) {
		if (cleaned == null || cleaned.isBlank()) return false;

		DungeonFloor lineTier = detectKuudraTierFromLine(cleaned, insideKuudra || isCurrentFloorKuudra());
		rememberKuudraTier(lineTier);

		if (isKuudraEntryMessage(cleaned)) {
			beginNewKuudraRun(now, bestKuudraTier(lineTier));
			return false;
		}

		if (!isKuudraCompletionMessage(cleaned)) return false;

		DungeonFloor tier = bestKuudraTier(lineTier);
		if (!currentRunActive && !runCountedThisDungeon) {
			beginNewKuudraRun(now, tier);
		}
		insideKuudra = true;
		kuudraSignalUntilMillis = now + DUNGEON_SIGNAL_GRACE_MS;
		recordCompletedRun(now, tier, "100%");
		return true;
	}

	private List<String> splitMessageLines(String rawText) {
		if (rawText == null || rawText.isBlank()) return List.of();
		String expanded = rawText
			.replace("\\r\\n", "\n")
			.replace("\\n", "\n")
			.replace("\\r", "\n");
		String[] pieces = expanded.split("\\R");
		List<String> lines = new ArrayList<>(pieces.length);
		for (String piece : pieces) {
			String line = piece.trim();
			if (!line.isEmpty()) lines.add(line);
		}
		return lines.isEmpty() ? List.of(rawText) : lines;
	}

	private void expirePendingCompletionState(long now) {
		if (awaitingExtraStatsScore && now > awaitingExtraStatsUntilMillis) {
			awaitingExtraStatsScore = false;
			awaitingExtraStatsFloor = DungeonFloor.UNKNOWN;
			awaitingExtraStatsUntilMillis = 0L;
		}
		if (pendingScoreGrade != null && now > pendingSPlusUntilMillis) {
			clearPendingCompletionScore();
		}
	}

	private void clearPendingCompletionScore() {
		pendingScoreGrade = null;
		pendingSPlusFloor = DungeonFloor.UNKNOWN;
		pendingSPlusUntilMillis = 0L;
	}

	private DungeonFloor bestCompletionFloor(DungeonFloor lineFloor) {
		if (lineFloor != DungeonFloor.UNKNOWN) return lineFloor;
		if (awaitingExtraStatsFloor != DungeonFloor.UNKNOWN) return awaitingExtraStatsFloor;
		if (pendingSPlusFloor != DungeonFloor.UNKNOWN) return pendingSPlusFloor;
		return currentFloor;
	}

	private void updateDungeonContext(Minecraft client) {
		long now = System.currentTimeMillis();
		Object currentLevel = client.level;
		if (lastLevelIdentity != currentLevel) {
			lastLevelIdentity = currentLevel;
			resetDungeonRuntimeState();
			inDungeonHub = false;
			inCrimsonIsle = false;
		}

		if (refreshCountdown > 0) {
			refreshCountdown--;
			return;
		}

		refreshCountdown = REFRESH_INTERVAL_TICKS;
		List<String> scoreboardLines = readScoreboardLines(client);
		List<String> tabLines = TabReader.readNormalizedLines(client);
		boolean wasKuudra = insideKuudra;
		inDungeonHub = isDungeonHub(scoreboardLines) || isDungeonHub(tabLines);
		inCrimsonIsle = isCrimsonIsle(scoreboardLines) || isCrimsonIsle(tabLines);
		DungeonFloor detectedKuudraTier = detectKuudraTierFromLines(scoreboardLines);
		if (detectedKuudraTier == DungeonFloor.UNKNOWN) detectedKuudraTier = detectKuudraTierFromLines(tabLines);
		boolean tierImpliesKuudra = detectedKuudraTier != DungeonFloor.UNKNOWN
			&& (wasKuudra || currentRunActive || inCrimsonIsle || isKuudra(scoreboardLines) || isKuudra(tabLines));
		insideDungeon = isDungeon(scoreboardLines) || isDungeonTab(tabLines) || now <= dungeonSignalUntilMillis;
		insideKuudra = tierImpliesKuudra || isKuudra(scoreboardLines) || isKuudra(tabLines) || now <= kuudraSignalUntilMillis;

		if (!insideDungeon && !insideKuudra) {
			finishCurrentRunTiming(now);
			resetDungeonRuntimeState();
			return;
		}

		if (insideDungeon) {
			DungeonFloor detected = detectFloorFromLines(scoreboardLines);
			if (detected == DungeonFloor.UNKNOWN) detected = detectFloorFromLines(tabLines);
			if (detected != DungeonFloor.UNKNOWN && detected != currentFloor) currentFloor = detected;
		}

		if (insideKuudra) {
			rememberKuudraTier(detectedKuudraTier);
			if (!wasKuudra && !currentRunActive && !runCountedThisDungeon) {
				beginNewKuudraRun(now, bestKuudraTier(detectedKuudraTier));
			}
		}
	}

	private void captureRewardChestCosts(Minecraft client) {
		if (!(client.screen instanceof AbstractContainerScreen<?> screen) || client.player == null) return;

		String normalizedTitle = normalize(screen.getTitle().getString());
		String canonicalRewardTitle = canonicalRewardChestTitle(normalizedTitle);
		long now = System.currentTimeMillis();
		rememberRunContextFromMenuTitle(normalizedTitle, now);

		if (canonicalRewardTitle != null) {
			if (lootWindowUntilMillis <= 0L || now > lootWindowUntilMillis) {
				startAdHocLootWindow(now);
			}
			if (lootWindowUntilMillis <= 0L || now > lootWindowUntilMillis) return;

			AbstractContainerMenu menu = client.player.containerMenu;
			if (isRewardChestPreviewScreen(menu)) {
				String previewKey = "preview#" + canonicalRewardTitle + "#" + menu.containerId;
				refreshRewardModifierScan(menu, previewKey, now);
				updateCachedRewardChestOffers(menu);
				if (pendingLootChestAssigned && pendingLootEntries.isEmpty()) {
					resetPendingChestState();
				}
				// Keep key-requirement from this preview so the subsequent open can bill a key.
				lastViewedOpenedRewardChestTitle = "";
				return;
			}

			DungeonChestOffer cached = cachedChestOffersByTitle.get(canonicalRewardTitle);
			if (cached != null && cached.alreadyOpened) {
				updateCachedRewardChestOffers(menu);
				lastViewedOpenedRewardChestTitle = "";
				return;
			}

			String screenKey = "opened#" + canonicalRewardTitle + "#" + menu.containerId;
			boolean firstOpenScan = scannedRewardScreens.add(screenKey);
			// Carry forward key requirement observed on the preview "Open Reward Chest" button.
			boolean paidWithKey = nextOpenedChestUsesDungeonChestKey || lastRewardModifierScanHadKeyRequirement;
			refreshRewardModifierScan(menu, screenKey, now);
			if (lastRewardModifierScanHadKismetMarker) markKismetFeatherUsed();
			if (firstOpenScan) {
				assignPendingOpenedChest(canonicalRewardTitle, cached, paidWithKey);
			}
			captureOpenedRewardChestLoot(client, menu, now);
			lastViewedOpenedRewardChestTitle = canonicalRewardTitle;
			return;
		}

		// Leaving an opened chest GUI: do NOT flush yet. Chat CHEST REWARDS often arrives next;
		// flushing here was splitting one open into a partial GUI record + a second chat record.
		if (!lastViewedOpenedRewardChestTitle.isBlank()) {
			lastViewedOpenedRewardChestTitle = "";
		}

		if (!isRewardsMenuTitle(normalizedTitle) && !isCroesusChestListTitle(normalizedTitle)) return;

		AbstractContainerMenu menu = client.player.containerMenu;
		if (menu == null) return;

		String screenKey = normalizedTitle + "#" + menu.containerId;
		boolean firstScan = scannedRewardScreens.add(screenKey);
		boolean dueScan = firstScan || !screenKey.equals(lastRewardModifierScanKey) || now - lastRewardModifierScanMillis >= REWARD_MODIFIER_SCAN_INTERVAL_MS;
		if (!dueScan) return;
		refreshRewardModifierScan(menu, screenKey, now);
		if (lastRewardModifierScanHadKismetMarker) armNextOpenedKismetReroll();

		updateCachedRewardChestOffers(menu);

		if (lootWindowUntilMillis <= 0L || now > lootWindowUntilMillis) startAdHocLootWindow(now);
	}

	private void rememberRunContextFromMenuTitle(String normalizedTitle, long now) {
		if (normalizedTitle == null || normalizedTitle.isBlank()) return;
		DungeonFloor kuudraTier = detectKuudraTierFromLine(normalizedTitle, normalizedTitle.contains("KUUDRA") || insideKuudra || isCurrentFloorKuudra());
		if (kuudraTier != DungeonFloor.UNKNOWN) {
			rememberKuudraTier(kuudraTier);
			insideKuudra = true;
			kuudraSignalUntilMillis = Math.max(kuudraSignalUntilMillis, now + DUNGEON_SIGNAL_GRACE_MS);
			if (pendingLootFloor == DungeonFloor.UNKNOWN && lootWindowUntilMillis > 0L && now <= lootWindowUntilMillis) {
				pendingLootFloor = kuudraTier;
			}
			return;
		}

		DungeonFloor titleFloor = detectFloorFromLine(normalizedTitle);
		if (titleFloor != DungeonFloor.UNKNOWN && titleFloor != currentFloor) {
			currentFloor = titleFloor;
			if (pendingLootFloor == DungeonFloor.UNKNOWN && lootWindowUntilMillis > 0L && now <= lootWindowUntilMillis) {
				pendingLootFloor = titleFloor;
			}
		}
	}

	private void refreshRewardModifierScan(AbstractContainerMenu menu, String screenKey, long now) {
		if (menu == null) return;
		if (screenKey.equals(lastRewardModifierScanKey) && now - lastRewardModifierScanMillis < REWARD_MODIFIER_SCAN_INTERVAL_MS) return;
		lastRewardModifierScanKey = screenKey;
		lastRewardModifierScanMillis = now;
		lastRewardModifierScanHadKeyRequirement = screenHasDungeonChestKeyRequirement(menu);
		lastRewardModifierScanHadKismetMarker = screenHasKismetRerollMarker(menu);
	}

	private void updateCachedRewardChestOffers(AbstractContainerMenu menu) {
		List<String> changedExistingOffers = new ArrayList<>();
		for (int slotIndex = 0; slotIndex < menu.slots.size(); slotIndex++) {
			ItemStack stack = menu.getSlot(slotIndex).getItem();
			if (stack.isEmpty()) continue;
			String canonicalKey = canonicalChestTitleFromStack(stack);
			if (canonicalKey == null) continue;
			int fingerprint = chestOfferFingerprint(stack);
			Integer previous = cachedChestOfferFingerprintsByTitle.get(canonicalKey);
			if (previous != null && previous == fingerprint && cachedChestOffersByTitle.containsKey(canonicalKey)) continue;
			if (previous != null && previous != fingerprint) changedExistingOffers.add(canonicalKey);
			CHEST_ICON_CACHE.putIfAbsent(canonicalKey, stack.copy());
			DungeonChestOffer offer = parseChestOffer(canonicalKey, stack);
			cachedChestOffersByTitle.put(canonicalKey, offer);
			cachedChestOfferFingerprintsByTitle.put(canonicalKey, fingerprint);
		}
		if ((nextOpenedChestUsesKismetFeather || rewardMenuKismetRerollPending)
			&& rewardMenuKismetRerolledChestTitle.isBlank()
			&& changedExistingOffers.size() == 1) {
			rewardMenuKismetRerolledChestTitle = changedExistingOffers.get(0);
		}
	}

	private String canonicalChestTitleFromStack(ItemStack stack) {
		if (stack == null || stack.isEmpty()) return null;
		String chestTitle = normalize(cleanText(stack.getHoverName().getString()));
		String canonicalRewardTitle = canonicalRewardChestTitle(chestTitle);
		if (canonicalRewardTitle != null) return canonicalRewardTitle;
		String canonicalKey = TIER_NAME_TO_CHEST_TITLE.get(chestTitle);
		if (canonicalKey != null) return canonicalKey;
		for (String title : REWARD_CHEST_TITLES) {
			if (chestTitle.startsWith(title)) return title;
		}
		return null;
	}

	private int chestOfferFingerprint(ItemStack stack) {
		int hash = cleanText(stack.getHoverName().getString()).hashCode();
		for (String line : cleanLoreLines(stack)) {
			if (lineIndicatesKismetRerollState(normalize(line))) continue;
			hash = 31 * hash + line.hashCode();
		}
		return hash;
	}

	private DungeonChestOffer parseChestOffer(String normalizedTitle, ItemStack stack) {
		List<String> lore = cleanLoreLines(stack);
		long costCoins = 0L;
		long valueCoins = 0L;
		boolean alreadyOpened = false;
		List<DungeonLootEntry> entries = new ArrayList<>();

		Long parsedCost = parseChestCost(stack);
		if (parsedCost != null) costCoins = parsedCost;
		for (int i = 0; i < lore.size(); i++) {
			String line = lore.get(i);
			if (lineIndicatesAlreadyOpened(line)) alreadyOpened = true;
			if (line.regionMatches(true, 0, "Value:", 0, 6)) {
				Long v = parseCoins(line.substring(6).trim());
				if (v == null && i + 1 < lore.size()) v = parseCoins(lore.get(i + 1));
				if (v != null) valueCoins = v;
			}
		}

		boolean inContents = false;
		for (String line : lore) {
			if (line.equalsIgnoreCase("Contents") || line.equalsIgnoreCase("Contents:")) {
				inContents = true;
				continue;
			}
			if (!inContents) continue;
			if (isContentsSectionEnd(line)) break;
			if (isContentsMetaLine(line)) continue;

			String itemName = line;
			int quantity = 1;
			Matcher pre = QUANTITY_PREFIX_PATTERN.matcher(line);
			Matcher suf = QUANTITY_SUFFIX_PATTERN.matcher(line);
			if (pre.matches()) {
				quantity = Integer.parseInt(pre.group(1));
				itemName = pre.group(2).trim();
			} else if (suf.matches()) {
				quantity = Integer.parseInt(suf.group(2));
				itemName = suf.group(1).trim();
			}
			if (shouldIgnoreLootName(itemName)) continue;

			String itemId = resolveItemId(itemName);
			if (!itemId.isEmpty() || looksReasonableLootName(itemName)) {
				entries.add(new DungeonLootEntry(itemName, itemId, quantity));
			}
		}
		ChestCostBreakdown breakdown = new ChestCostBreakdown(costCoins);
		applyModifierLoreHints(lore, breakdown);
		applyKuudraChestCostHint(normalizedTitle, breakdown);
		populateKnownModifierCosts(breakdown);
		suppressDungeonChestKeyForKuudra(normalizedTitle, breakdown);
		DungeonChestOffer offer = new DungeonChestOffer(toDisplayChestTitle(normalizedTitle), breakdown, valueCoins, entries);
		offer.alreadyOpened = alreadyOpened;
		offer.normalize();
		return offer;
	}

	private void assignPendingOpenedChest(String normalizedTitle, DungeonChestOffer offer) {
		assignPendingOpenedChest(normalizedTitle, offer, false);
	}

	private void assignPendingOpenedChest(String normalizedTitle, DungeonChestOffer offer, boolean paidWithDungeonChestKey) {
		String displayTitle = toDisplayChestTitle(normalizedTitle);
		boolean samePendingChest = pendingLootChestAssigned && displayTitle.equalsIgnoreCase(pendingLootChestTitle);
		boolean hadPendingEntries = !pendingLootEntries.isEmpty();
		boolean alreadyChargedKey = samePendingChest && pendingLootCostBreakdown != null && pendingLootCostBreakdown.usedDungeonChestKey;
		ChestCostBreakdown previousBreakdown = samePendingChest && pendingLootCostBreakdown != null
			? pendingLootCostBreakdown.copy()
			: null;
		if (pendingLootChestAssigned && !samePendingChest) {
			if (hadPendingEntries) {
				flushPendingLootRecord(true);
			} else {
				resetPendingChestState();
			}
			samePendingChest = false;
			alreadyChargedKey = false;
			previousBreakdown = null;
		}
		if (!samePendingChest) {
			openedRewardChestsInLootWindow++;
		}

		pendingLootChestAssigned = true;
		pendingLootChestTitle = displayTitle;
		pendingLootCostBreakdown = offer == null ? new ChestCostBreakdown() : offer.costBreakdown.copy();
		// Never inherit key flags from croesus/preview offer lore; only charge when we have evidence.
		pendingLootCostBreakdown.usedDungeonChestKey = false;
		pendingLootCostBreakdown.dungeonChestKeyCostCoins = 0L;
		if (previousBreakdown != null) {
			if (previousBreakdown.baseChestCostCoins > 0L && pendingLootCostBreakdown.baseChestCostCoins <= 0L) {
				pendingLootCostBreakdown.baseChestCostCoins = previousBreakdown.baseChestCostCoins;
			}
			if (previousBreakdown.usedKismetFeather) {
				pendingLootCostBreakdown.usedKismetFeather = true;
				pendingLootCostBreakdown.kismetRerolledChestOpened = previousBreakdown.kismetRerolledChestOpened;
			}
			if (previousBreakdown.usedWheelOfFate) pendingLootCostBreakdown.usedWheelOfFate = true;
			if (previousBreakdown.usedKuudraKey) pendingLootCostBreakdown.usedKuudraKey = true;
		}
		DungeonFloor kuudraFloor = currentKuudraFloorForPricing();
		if (pendingLootFloor == DungeonFloor.UNKNOWN && kuudraFloor != null && kuudraFloor.isKuudra()) {
			pendingLootFloor = kuudraFloor;
		}
		// Key only when chat/marker said so, or preview open-button required a key for this open.
		// Do NOT bill a key just because this is the Nth chest in the loot window (breaks Croesus).
		if ((alreadyChargedKey || paidWithDungeonChestKey || nextOpenedChestUsesDungeonChestKey)
			&& isCatacombsRewardChest(normalizedTitle)) {
			pendingLootCostBreakdown.usedDungeonChestKey = true;
			nextOpenedChestUsesDungeonChestKey = false;
			lastRewardModifierScanHadKeyRequirement = false;
		}
		if (shouldAssignArmedKismetToOpenedChest(normalizedTitle)) {
			pendingLootCostBreakdown.usedKismetFeather = true;
			pendingLootCostBreakdown.kismetRerolledChestOpened = true;
			nextOpenedChestUsesKismetFeather = false;
			rewardMenuKismetRerollPending = false;
			rewardMenuKismetRerolledChestTitle = "";
		}
		if (nextOpenedChestUsesWheelOfFate) {
			pendingLootCostBreakdown.usedWheelOfFate = true;
			nextOpenedChestUsesWheelOfFate = false;
		}
		applyKuudraChestCostHint(normalizedTitle, pendingLootCostBreakdown);
		populateKnownModifierCosts(pendingLootCostBreakdown);
		suppressDungeonChestKeyForKuudra(normalizedTitle, pendingLootCostBreakdown);
		lootCollectionUntilMillis = System.currentTimeMillis() + LOOT_COLLECTION_MS;
	}

	private boolean isRewardChestPreviewScreen(AbstractContainerMenu menu) {
		return findOpenRewardChestSlotIndex(menu) >= 0;
	}

	private void captureOpenedRewardChestLootIfViewing(Minecraft client, long now) {
		if (!(client.screen instanceof AbstractContainerScreen<?>) || client.player == null) return;
		String canonical = canonicalRewardChestTitle(normalize(client.screen.getTitle().getString()));
		if (canonical == null || !pendingLootChestAssigned) return;
		if (isRewardChestPreviewScreen(client.player.containerMenu)) return;
		captureOpenedRewardChestLoot(client, client.player.containerMenu, now);
		lastViewedOpenedRewardChestTitle = canonical;
	}

	private void captureOpenedRewardChestLoot(Minecraft client, AbstractContainerMenu menu, long now) {
		if (client == null || client.player == null || menu == null || !pendingLootChestAssigned) return;
		// Chat CHEST REWARDS is authoritative once it starts; keep GUI scan from fighting it.
		if (!pendingLootSeededFromGui && !pendingLootEntries.isEmpty() && lootCollectionUntilMillis > now) {
			return;
		}
		var playerInventory = client.player.getInventory();
		boolean found = false;
		for (Slot slot : menu.slots) {
			if (slot == null || slot.container == playerInventory) continue;
			ItemStack stack = slot.getItem();
			if (stack.isEmpty() || stackIndicatesOpenRewardChest(stack) || isRewardChestUiStack(stack)) continue;
			if (canonicalChestTitleFromStack(stack) != null) continue;

			String rawName = cleanText(stack.getHoverName().getString());
			String cleaned = normalize(rawName);
			if (rawName.isBlank() || shouldIgnoreLootName(rawName) || looksLikeNonLootLine(cleaned)) continue;

			int quantity = Math.max(1, stack.getCount());
			String itemId = resolveItemId(rawName);
			if (itemId.isEmpty() && !looksReasonableLootName(rawName)) continue;
			mergePendingLootEntry(new DungeonLootEntry(rawName, itemId, quantity));
			found = true;
		}
		if (found) {
			pendingLootSeededFromGui = true;
			lootCollectionUntilMillis = now + LOOT_COLLECTION_MS;
		}
	}

	private boolean isRewardChestUiStack(ItemStack stack) {
		if (stack == null || stack.isEmpty()) return true;
		if (stack.is(Items.LIGHT_GRAY_STAINED_GLASS_PANE) || stack.is(Items.GRAY_STAINED_GLASS_PANE)
			|| stack.is(Items.BLACK_STAINED_GLASS_PANE) || stack.is(Items.BARRIER)
			|| stack.is(Items.AIR)) return true;
		String name = normalize(cleanText(stack.getHoverName().getString()));
		return name.contains("STAINED GLASS");
	}

	private void seedPendingLootEntriesFromGuiOffer(DungeonChestOffer offer) {
		if (offer == null || offer.lootEntries == null || offer.lootEntries.isEmpty() || !pendingLootEntries.isEmpty()) return;
		for (DungeonLootEntry entry : offer.lootEntries) {
			if (entry != null) pendingLootEntries.add(entry.copy());
		}
		pendingLootSeededFromGui = !pendingLootEntries.isEmpty();
	}

	private boolean shouldAssignArmedKismetToOpenedChest(String normalizedTitle) {
		if (!nextOpenedChestUsesKismetFeather && !rewardMenuKismetRerollPending) return false;
		if (rewardMenuKismetRerolledChestTitle == null || rewardMenuKismetRerolledChestTitle.isBlank()) return true;
		return rewardMenuKismetRerolledChestTitle.equals(normalizedTitle);
	}

	private boolean isCatacombsRewardChest(String normalizedTitle) {
		if (normalizedTitle == null || normalizedTitle.isBlank()) return false;
		if (!REWARD_CHEST_TITLES.contains(normalizedTitle)) return false;
		return !normalizedTitle.equals("FREE CHEST") && !normalizedTitle.equals("PAID CHEST") && !isCurrentFloorKuudra();
	}

	private boolean isKuudraPaidRewardChest(String normalizedTitle) {
		if (normalizedTitle == null || normalizedTitle.isBlank()) return false;
		return isKuudraRewardContext(normalizedTitle)
			&& (normalizedTitle.equals("PAID CHEST") || normalizedTitle.contains("PAID"));
	}

	private boolean isKuudraRewardChest(String normalizedTitle) {
		if (normalizedTitle == null || normalizedTitle.isBlank()) return false;
		return isKuudraRewardContext(normalizedTitle)
			&& (normalizedTitle.equals("FREE CHEST") || normalizedTitle.equals("PAID CHEST"));
	}

	private boolean isTrackedRewardChest(String canonicalTitle, boolean kuudraContext) {
		if (canonicalTitle == null || canonicalTitle.isBlank()) return false;
		if (kuudraContext) return canonicalTitle.equals("FREE CHEST") || canonicalTitle.equals("PAID CHEST");
		return isCatacombsRewardChest(canonicalTitle);
	}

	private boolean shouldApplyDungeonChestKeyModifier(String normalizedTitle) {
		return !isKuudraRewardContext(normalizedTitle);
	}

	private void suppressDungeonChestKeyForKuudra(String normalizedTitle, ChestCostBreakdown breakdown) {
		if (breakdown == null || !isKuudraRewardChest(normalizedTitle)) return;
		breakdown.usedDungeonChestKey = false;
		breakdown.dungeonChestKeyCostCoins = 0L;
	}

	private boolean isKuudraRewardContext(String normalizedTitle) {
		if (normalizedTitle != null && (normalizedTitle.contains("VESUVIUS") || normalizedTitle.contains("KUUDRA"))) return true;
		if (insideKuudra) return true;
		DungeonFloor pricingFloor = currentKuudraFloorForPricing();
		return pricingFloor != null && pricingFloor.isKuudra();
	}

	private void applyKuudraChestCostHint(String canonicalTitle, ChestCostBreakdown breakdown) {
		if (breakdown == null || !isKuudraPaidRewardChest(canonicalTitle)) return;
		breakdown.usedKuudraKey = true;
	}

	private void applyModifierLoreHints(List<String> lore, ChestCostBreakdown breakdown) {
		if (lore == null || lore.isEmpty() || breakdown == null) return;
		for (String line : lore) {
			String normalized = normalize(line);
			if (lineIndicatesKismetRerollState(normalized)) continue;
			if (lineIndicatesDungeonChestKeyUsed(normalized)) {
				breakdown.usedDungeonChestKey = true;
			}
			if (lineIndicatesKismetUsed(normalized)) {
				breakdown.usedKismetFeather = true;
				breakdown.kismetRerolledChestOpened = true;
			}
			if (lineIndicatesWheelOfFateUsed(normalized)) {
				breakdown.usedWheelOfFate = true;
			}
		}
	}

	private boolean handleModifierMessage(String cleaned) {
		if (lineIndicatesDungeonChestKeyUsed(cleaned)) {
			markDungeonChestKeyUsed();
			return true;
		}
		if (lineIndicatesKismetUsed(cleaned)) {
			markKismetFeatherUsed();
			return true;
		}
		if (lineIndicatesKismetRerollState(cleaned)) {
			markKismetFeatherUsed();
			return true;
		}
		if (lineIndicatesWheelOfFateUsed(cleaned)) {
			markWheelOfFateUsed();
			return true;
		}
		return false;
	}

	private boolean lineIndicatesDungeonChestKeyUsed(String normalized) {
		if (normalized == null || !normalized.contains("DUNGEON CHEST KEY")) return false;
		if (normalized.contains("ALREADY OPENED")) return false;
		if (normalized.contains("REQUIRES") || normalized.contains("REQUIRE") || normalized.contains("NEEDS") || normalized.contains("NEED")) return false;
		return normalized.contains("USED")
			|| normalized.contains("CONSUMED")
			|| normalized.contains("UNLOCKED")
			|| normalized.contains("OPEN ANOTHER")
			|| normalized.contains("EXTRA CHEST");
	}

	private boolean lineIndicatesDungeonChestKeyRequirement(String normalized) {
		if (normalized == null || !normalized.contains("DUNGEON CHEST KEY")) return false;
		return normalized.contains("REQUIRES")
			|| normalized.contains("REQUIRE")
			|| normalized.contains("NEEDS")
			|| normalized.contains("NEED");
	}

	private boolean lineIndicatesAlreadyOpened(String line) {
		return normalize(line).contains("ALREADY OPENED");
	}

	private boolean lineIndicatesKismetUsed(String normalized) {
		if (normalized == null || !normalized.contains("KISMET")) return false;
		return normalized.contains("USED")
			|| normalized.contains("CONSUMED")
			|| normalized.contains("REROLLED")
			|| normalized.contains("RE-ROLLED")
			|| normalized.contains("REROLL USED");
	}

	private boolean lineIndicatesKismetRerollState(String normalized) {
		if (normalized == null || normalized.isBlank()) return false;
		return normalized.contains("YOU ALREADY REROLLED A CHEST")
			|| normalized.contains("ALREADY REROLLED A CHEST")
			|| (normalized.contains("REROLL USED") && normalized.contains("KISMET"));
	}

	private boolean screenHasKismetRerollMarker(AbstractContainerMenu menu) {
		if (menu == null) return false;
		for (int slotIndex = 0; slotIndex < menu.slots.size(); slotIndex++) {
			ItemStack stack = menu.getSlot(slotIndex).getItem();
			if (stack.isEmpty()) continue;
			String name = normalize(cleanText(stack.getHoverName().getString()));
			if (lineIndicatesKismetRerollState(name)) return true;
			for (String line : cleanLoreLines(stack)) {
				String normalized = normalize(line);
				if (lineIndicatesKismetRerollState(normalized)) return true;
			}
		}
		return false;
	}

	private boolean screenHasDungeonChestKeyRequirement(AbstractContainerMenu menu) {
		if (menu == null) return false;
		for (int slotIndex = 0; slotIndex < menu.slots.size(); slotIndex++) {
			ItemStack stack = menu.getSlot(slotIndex).getItem();
			if (stack.isEmpty()) continue;
			String name = normalize(cleanText(stack.getHoverName().getString()));
			if (lineIndicatesDungeonChestKeyRequirement(name)) return true;
			for (String line : cleanLoreLines(stack)) {
				String normalized = normalize(line);
				if (lineIndicatesDungeonChestKeyRequirement(normalized)) return true;
			}
		}
		return false;
	}

	private boolean lineIndicatesWheelOfFateUsed(String normalized) {
		if (normalized == null || !normalized.contains("WHEEL OF FATE")) return false;
		return normalized.contains("USED")
			|| normalized.contains("CONSUMED")
			|| normalized.contains("APPLIED")
			|| normalized.contains("ACTIVE")
			|| normalized.contains("REROLLED")
			|| normalized.contains("RE-ROLLED");
	}

	private void markDungeonChestKeyUsed() {
		if (pendingLootChestAssigned) {
			pendingLootCostBreakdown.usedDungeonChestKey = true;
			populateKnownModifierCosts(pendingLootCostBreakdown);
		} else {
			nextOpenedChestUsesDungeonChestKey = true;
		}
	}

	private void markKismetFeatherUsed() {
		invalidateRewardMenuScans();
		if (pendingLootChestAssigned) {
			pendingLootCostBreakdown.usedKismetFeather = true;
			pendingLootCostBreakdown.kismetRerolledChestOpened = true;
			nextOpenedChestUsesKismetFeather = false;
			rewardMenuKismetRerollPending = false;
			rewardMenuKismetRerolledChestTitle = "";
			populateKnownModifierCosts(pendingLootCostBreakdown);
		} else {
			armNextOpenedKismetReroll();
		}
	}

	private void armNextOpenedKismetReroll() {
		rewardMenuKismetRerollPending = true;
		nextOpenedChestUsesKismetFeather = true;
	}

	private void invalidateRewardMenuScans() {
		scannedRewardScreens.removeIf(key -> !key.startsWith("opened#"));
	}

	private void markWheelOfFateUsed() {
		invalidateRewardMenuScans();
		if (pendingLootChestAssigned) {
			pendingLootCostBreakdown.usedWheelOfFate = true;
			populateKnownModifierCosts(pendingLootCostBreakdown);
		} else {
			nextOpenedChestUsesWheelOfFate = true;
		}
	}

	private void populateKnownModifierCosts(ChestCostBreakdown breakdown) {
		if (breakdown == null) return;
		if (breakdown.usedDungeonChestKey && breakdown.dungeonChestKeyCostCoins <= 0L) {
			breakdown.dungeonChestKeyCostCoins = resolveModifierItemCost(ITEM_DUNGEON_CHEST_KEY);
		}
		if (breakdown.usedKismetFeather && breakdown.kismetFeatherCostCoins <= 0L) {
			breakdown.kismetFeatherCostCoins = resolveModifierItemCost(ITEM_KISMET_FEATHER);
		}
		if (breakdown.usedWheelOfFate && breakdown.wheelOfFateCostCoins <= 0L) {
			breakdown.wheelOfFateCostCoins = resolveModifierItemCost(ITEM_WHEEL_OF_FATE);
		}
		if (breakdown.usedKuudraKey && breakdown.kuudraKeyCostCoins <= 0L) {
			breakdown.kuudraKeyCostCoins = DungeonProfitPricing.resolveKuudraKeyCost(currentKuudraFloorForPricing(), DrtConfigManager.getConfig());
		}
		breakdown.normalize();
	}

	private DungeonFloor currentKuudraFloorForPricing() {
		if (pendingLootFloor != null && pendingLootFloor.isKuudra()) return pendingLootFloor;
		if (currentFloor != null && currentFloor.isKuudra()) return currentFloor;
		if (lastKnownKuudraFloor != null && lastKnownKuudraFloor.isKuudra()) return lastKnownKuudraFloor;
		return DungeonFloor.UNKNOWN;
	}

	private long resolveModifierItemCost(String itemId) {
		long cost = DungeonProfitPricing.resolveModifierCost(itemId, DrtConfigManager.getConfig());
		return Math.max(0L, cost);
	}

	private boolean isContentsSectionEnd(String line) {
		return line.equalsIgnoreCase("Cost")
			|| line.regionMatches(true, 0, "Cost:", 0, 5)
			|| line.regionMatches(true, 0, "Open Reward Chest", 0, 17)
			|| line.regionMatches(true, 0, "Click to open", 0, 13)
			|| line.regionMatches(true, 0, "Available Modifiers", 0, 19)
			|| line.regionMatches(true, 0, "Modifiers", 0, 9)
			|| line.regionMatches(true, 0, "Requires", 0, 8);
	}

	private boolean isContentsMetaLine(String line) {
		return line.equalsIgnoreCase("FREE")
			|| line.regionMatches(true, 0, "Value:", 0, 6)
			|| line.regionMatches(true, 0, "Profit:", 0, 7);
	}

	private void handleDragging(Minecraft client) {
		boolean leftMouseDown = client.mouseHandler.isLeftPressed();

		double guiScaleX = (double) client.getWindow().getGuiScaledWidth() / Math.max(1, client.getWindow().getWidth());
		double guiScaleY = (double) client.getWindow().getGuiScaledHeight() / Math.max(1, client.getWindow().getHeight());
		int mouseX = (int) Math.round(client.mouseHandler.xpos() * guiScaleX);
		int mouseY = (int) Math.round(client.mouseHandler.ypos() * guiScaleY);

		if (!isTrackerVisible(client)) {
			if (!leftMouseDown && dragging && positionDirty) saveHudPosition();
			dragging = false;
			positionDirty = false;
			leftMouseDownLastTick = leftMouseDown;
			return;
		}

		if (leftMouseDown && !leftMouseDownLastTick && client.screen != null) {
			if (isHoveringModeLabel(client, mouseX, mouseY)) { cycleHudVisibilityMode(); leftMouseDownLastTick = true; return; }
			if (isHoveringFloorLabel(client, mouseX, mouseY)) { cycleSelectedFloor(); leftMouseDownLastTick = true; return; }
			if (isHoveringRunsHrPart(client, mouseX, mouseY)) { toggleRunsPerHrPause(); leftMouseDownLastTick = true; return; }
			if (isHoveringProfitLine(client, mouseX, mouseY)) {
				requestOpenLootScreen(selectedFloor, null);
				leftMouseDownLastTick = true;
				return;
			}
		}

		if (!(client.screen instanceof AbstractContainerScreen<?>)) {
			if (!leftMouseDown && dragging && positionDirty) saveHudPosition();
			dragging = false;
			positionDirty = false;
			leftMouseDownLastTick = leftMouseDown;
			return;
		}

		if (leftMouseDown && !leftMouseDownLastTick && isHoveringTracker(client, mouseX, mouseY)) {
			dragging = true;
			dragOffsetX = mouseX - hudX;
			dragOffsetY = mouseY - hudY;
		}

		if (dragging && leftMouseDown) {
			int maxX = Math.max(0, client.getWindow().getGuiScaledWidth() - getDisplayWidth(client));
			int maxY = Math.max(0, client.getWindow().getGuiScaledHeight() - getDisplayHeight(client));
			hudX = clamp(mouseX - dragOffsetX, 0, maxX);
			hudY = clamp(mouseY - dragOffsetY, 0, maxY);
			positionDirty = true;
		}

		if (!leftMouseDown && dragging) {
			dragging = false;
			if (positionDirty) { saveHudPosition(); positionDirty = false; }
		}

		leftMouseDownLastTick = leftMouseDown;
	}

	private void saveHudPosition() {
		saveHudLayout();
	}

	private void saveHudLayout() {
		DrtConfigManager.updateHudLayout(hudX, hudY, hudScale);
	}

	private boolean isHoveringTracker(Minecraft client, int mouseX, int mouseY) {
		return isHoveringTracker(client, mouseX, mouseY, false);
	}

	private boolean isHoveringTracker(Minecraft client, int mouseX, int mouseY, boolean includeResetLine) {
		double localX = toHudLocalX(mouseX);
		double localY = toHudLocalY(mouseY);
		int width = getBaseDisplayWidth(client, includeResetLine);
		int height = getBaseDisplayHeight(client, includeResetLine);
		return localX >= -3 && localX <= width + 3 && localY >= -2 && localY <= height + 2;
	}

	private boolean isTrackerVisible(Minecraft client) {
		return isTrackerVisible(client, false);
	}

	private boolean isTrackerVisible(Minecraft client, boolean moveMode) {
		if (client == null || client.player == null) return false;
		if (moveMode) return true;
		if (!enabled) return false;
		return switch (hudVisibilityMode) {
			case GLOBAL -> true;
			case DEFAULT -> insideDungeon || insideKuudra || inDungeonHub || inCrimsonIsle;
			case DHUB -> inDungeonHub;
		};
	}

	private boolean shouldShowResetLine(Minecraft client, boolean moveMode) {
		return !moveMode && client != null && client.screen instanceof InventoryScreen;
	}

	private boolean isHoveringRunsLine(Minecraft client, int mouseX, int mouseY) {
		double localX = toHudLocalX(mouseX);
		double localY = toHudLocalY(mouseY);
		int lineH = client.font.lineHeight + 2;
		int lineW = client.font.width(getDisplayLines(false).get(1));
		return localX >= -3 && localX <= lineW + 3
			&& localY >= lineH - 2 && localY <= lineH + client.font.lineHeight + 2;
	}

	private boolean isHoveringRunsHrPart(Minecraft client, int mouseX, int mouseY) {
		if (!isHoveringRunsLine(client, mouseX, mouseY)) return false;
		double localX = toHudLocalX(mouseX);
		String runsLine = getDisplayLines(false).get(1);
		int firstSep = runsLine.indexOf(" | ");
		if (firstSep < 0) return false;
		int secondSep = runsLine.indexOf(" | ", firstSep + 3);
		if (secondSep < 0) return false;
		int thirdSep = runsLine.indexOf(" | ", secondSep + 3);
		if (thirdSep < 0) return false;
		String visibleRunsLine = runsLine.replace(" [paused]", "");
		int startX = client.font.width(runsLine.substring(0, thirdSep + 3));
		int endX = client.font.width(visibleRunsLine);
		return localX >= startX && localX <= endX;
	}

	private boolean isHoveringModeLabel(Minecraft client, int mouseX, int mouseY) {
		double localX = toHudLocalX(mouseX);
		double localY = toHudLocalY(mouseY);
		int drtWidth = client.font.width("DRT");
		int lineH = client.font.lineHeight + 2;
		return localX >= -3 && localX <= drtWidth + 3
			&& localY >= -2 && localY <= lineH;
	}

	private boolean isHoveringFloorLabel(Minecraft client, int mouseX, int mouseY) {
		double localX = toHudLocalX(mouseX);
		double localY = toHudLocalY(mouseY);
		List<String> lines = getDisplayLines(false);
		int labelStart = client.font.width("DRT ");
		int labelWidth = client.font.width(lines.get(0));
		int lineH = client.font.lineHeight + 2;
		return localX >= labelStart - 3 && localX <= labelWidth + 3
			&& localY >= -2 && localY <= lineH;
	}

	private boolean isHoveringProfitLine(Minecraft client, int mouseX, int mouseY) {
		double localX = toHudLocalX(mouseX);
		double localY = toHudLocalY(mouseY);
		int lineH = client.font.lineHeight + 2;
		int lineW = client.font.width(getDisplayLines(false).get(2));
		return localX >= -3 && localX <= lineW + 3
			&& localY >= lineH * 2 - 2 && localY <= lineH * 2 + client.font.lineHeight + 2;
	}

	private boolean isHoveringResetButton(Minecraft client, int mouseX, int mouseY, boolean includeResetLine) {
		if (!includeResetLine) return false;
		double localX = toHudLocalX(mouseX);
		double localY = toHudLocalY(mouseY);
		List<String> lines = getDisplayLines(true);
		int lastIdx = lines.size() - 1;
		int lineH = client.font.lineHeight + 2;
		int resetY = lastIdx * lineH;
		int resetWidth = client.font.width(lines.get(lastIdx));
		return localX >= -3 && localX <= resetWidth + 3
			&& localY >= resetY - 2 && localY <= resetY + client.font.lineHeight + 2;
	}

	private double toHudLocalX(double mouseX) {
		return (mouseX - hudX) / hudScale;
	}

	private double toHudLocalY(double mouseY) {
		return (mouseY - hudY) / hudScale;
	}

	private static float clampHudScale(float scale) {
		if (!Float.isFinite(scale) || scale <= 0.0F) return 1.0F;
		float rounded = Math.round(scale * 10.0F) / 10.0F;
		return Math.max(MIN_HUD_SCALE, Math.min(MAX_HUD_SCALE, rounded));
	}

	private boolean isDungeon(List<String> lines) {
		for (String line : lines) {
			if (line.equals("THE CATACOMBS") || line.equals("MASTER MODE")) return true;
		}
		return false;
	}

	private boolean isDungeonHub(List<String> lines) {
		for (String line : lines) {
			if (isDungeonHubLine(line)) return true;
		}
		return false;
	}

	private boolean isDungeonHubLine(String line) {
		return line.contains("DUNGEON HUB") || line.contains("DUNGEON_HUB");
	}

	private boolean isCrimsonIsle(List<String> lines) {
		for (String line : lines) {
			if (line.contains("CRIMSON ISLE") || line.contains("CRIMSON ISLES") || line.contains("CRIMSON_ISLE")) return true;
		}
		return false;
	}

	private boolean isDungeonTab(List<String> lines) {
		for (String line : lines) {
			if (line.startsWith("AREA:") && !isDungeonHubLine(line) && (line.contains("DUNGEON") || line.contains("CATACOMBS"))) return true;
			if (line.contains("DUNGEON STATS") || line.contains("PUZZLES:")
				|| line.contains("SECRETS FOUND") || line.contains("COMPLETED ROOMS:")
				|| line.contains("OPENED ROOMS:") || line.contains("TEAM DEATHS:")
				|| line.contains("YOUR MILESTONE:")) return true;
		}
		return false;
	}

	private boolean isKuudra(List<String> lines) {
		for (String line : lines) {
			if (line.contains("KUUDRA'S HOLLOW") || line.contains("KUUDRA HOLLOW")) return true;
			if (line.startsWith("AREA:") && line.contains("KUUDRA")) return true;
			if (line.contains("KUUDRA DOWN") || line.contains("KUUDRA DEFEATED")) return true;
			if (line.contains("KUUDRA") && (line.contains("TIER") || line.contains("BASIC") || line.contains("HOT")
				|| line.contains("BURNING") || line.contains("FIERY") || line.contains("INFERNAL"))) return true;
		}
		return false;
	}

	private DungeonFloor detectKuudraTierFromLines(List<String> lines) {
		boolean kuudraContext = lines.stream().anyMatch(line -> line.contains("KUUDRA"));
		for (String line : lines) {
			DungeonFloor tier = detectKuudraTierFromLine(line, kuudraContext);
			if (tier != DungeonFloor.UNKNOWN) return tier;
		}
		return DungeonFloor.UNKNOWN;
	}

	private DungeonFloor detectKuudraTierFromLine(String line, boolean kuudraContext) {
		if (line == null || line.isBlank()) return DungeonFloor.UNKNOWN;
		boolean hasKuudra = line.contains("KUUDRA");

		Matcher shortMatcher = KUUDRA_SHORT_TIER_PATTERN.matcher(line);
		if ((hasKuudra || kuudraContext) && shortMatcher.find()) {
			return kuudraTier(parsePositiveInt(shortMatcher.group(1), -1));
		}

		Matcher wordMatcher = KUUDRA_TIER_WORD_PATTERN.matcher(line);
		if ((hasKuudra || kuudraContext || line.startsWith("TIER")) && wordMatcher.find()) {
			return kuudraTier(parseTierNumber(wordMatcher.group(1)));
		}

		boolean tierNameContext = line.startsWith("TIER:")
			|| line.startsWith("TIER ")
			|| line.contains("KUUDRA'S HOLLOW")
			|| line.contains("KUUDRA HOLLOW")
			|| (hasKuudra && !line.contains("CORE"))
			|| kuudraContext;
		if (tierNameContext) {
			DungeonFloor namedTier = kuudraTierNameFromLine(line);
			if (namedTier != DungeonFloor.UNKNOWN) return namedTier;
		}
		return DungeonFloor.UNKNOWN;
	}

	private DungeonFloor kuudraTierNameFromLine(String line) {
		if (line == null || line.isBlank()) return DungeonFloor.UNKNOWN;
		String padded = " " + line.replaceAll("[^A-Z0-9]+", " ").trim() + " ";
		for (Map.Entry<String, DungeonFloor> entry : KUUDRA_TIER_NAMES.entrySet()) {
			if (padded.contains(" " + entry.getKey() + " ")) return entry.getValue();
		}
		return DungeonFloor.UNKNOWN;
	}

	private DungeonFloor detectFloorFromLines(List<String> lines) {
		boolean masterContext = lines.stream().anyMatch(l -> l.contains("MASTER"));
		for (String line : lines) {
			DungeonFloor f = detectFloorFromLine(line, masterContext);
			if (f != DungeonFloor.UNKNOWN) return f;
		}
		return DungeonFloor.UNKNOWN;
	}

	private DungeonFloor detectFloorFromLine(String line) {
		return detectFloorFromLine(line, false);
	}

	private DungeonFloor detectFloorFromLine(String line, boolean masterContext) {
		for (DungeonFloor f : DungeonFloor.values()) {
			if (f == DungeonFloor.UNKNOWN || !f.isCatacombs()) continue;
			String name = f.name();
			if (line.contains("(" + name + ")") || line.contains("DUNGEON: " + name)) return f;
		}
		if (line.contains("CATACOMBS") || line.contains("DUNGEON") || line.contains("FLOOR")) {
			Matcher shortFloorMatcher = SHORT_FLOOR_PATTERN.matcher(line);
			if (shortFloorMatcher.find()) {
				DungeonFloor shortFloor = floorFromShortTag(shortFloorMatcher.group(1), shortFloorMatcher.group(2));
				if (shortFloor != DungeonFloor.UNKNOWN) return shortFloor;
			}
		}
		boolean isMaster = masterContext || line.contains("MASTER");
		if ((isMaster || line.contains("CATACOMBS")) && line.contains("FLOOR")) {
			int n = parseFloorNumber(line);
			if (n >= 1 && n <= 7) return DungeonFloor.values()[isMaster ? 7 + n - 1 : n - 1];
		}
		return DungeonFloor.UNKNOWN;
	}

	private DungeonFloor floorFromShortTag(String mode, String number) {
		if (mode == null || number == null) return DungeonFloor.UNKNOWN;
		int n = parsePositiveInt(number, -1);
		if (n < 1 || n > 7) return DungeonFloor.UNKNOWN;
		boolean master = mode.equalsIgnoreCase("M");
		return DungeonFloor.values()[master ? 7 + n - 1 : n - 1];
	}

	private DungeonFloor kuudraTier(int number) {
		return switch (number) {
			case 1 -> DungeonFloor.K1;
			case 2 -> DungeonFloor.K2;
			case 3 -> DungeonFloor.K3;
			case 4 -> DungeonFloor.K4;
			case 5 -> DungeonFloor.K5;
			default -> DungeonFloor.UNKNOWN;
		};
	}

	private int parseTierNumber(String value) {
		if (value == null || value.isBlank()) return -1;
		String normalized = value.trim().toUpperCase(Locale.ROOT);
		return switch (normalized) {
			case "I" -> 1;
			case "II" -> 2;
			case "III" -> 3;
			case "IV" -> 4;
			case "V" -> 5;
			default -> parsePositiveInt(normalized, -1);
		};
	}

	private int parseFloorNumber(String line) {
		String[][] table = {{"VII", "7"}, {"VI", "6"}, {"V", "5"}, {"IV", "4"}, {"III", "3"}, {"II", "2"}, {"I", "1"}};
		for (String[] pair : table) {
			String roman = pair[0];
			String num = pair[1];
			if (line.endsWith(" " + roman) || line.contains(" " + roman + " ")
				|| line.contains("FLOOR " + num) || line.contains("FLOOR: " + num)
				|| line.endsWith(" " + num)) {
				return Integer.parseInt(num);
			}
		}
		return -1;
	}

	private String extractScoreGrade(String line) {
		Matcher matcher = SCORE_GRADE_PATTERN.matcher(line);
		if (!matcher.find()) return null;
		String grade = matcher.group(1) != null ? matcher.group(1) : matcher.group(2);
		return grade == null ? null : grade.toUpperCase(Locale.ROOT);
	}

	private boolean isExtraStatsHeader(String line) {
		return line.contains("EXTRA STATS");
	}

	private boolean isDungeonEntryMessage(String cleaned) {
		return (cleaned.startsWith("STARTING IN ") && (insideDungeon || inDungeonHub))
			|| cleaned.contains(" THE CATACOMBS ")
			|| cleaned.contains(" CATACOMBS (F")
			|| cleaned.contains(" CATACOMBS (M")
			|| cleaned.startsWith("MASTER MODE CATACOMBS - FLOOR ");
	}

	private boolean isKuudraEntryMessage(String cleaned) {
		return cleaned.contains("KUUDRA'S HOLLOW")
			|| cleaned.contains("KUUDRA HOLLOW")
			|| (cleaned.contains("KUUDRA") && !cleaned.contains("CORE") && (cleaned.contains("STARTING") || cleaned.contains("TIER") || cleaned.contains("BASIC")
				|| cleaned.contains("HOT") || cleaned.contains("BURNING") || cleaned.contains("FIERY") || cleaned.contains("INFERNAL")));
	}

	private boolean isKuudraCompletionMessage(String cleaned) {
		return cleaned.contains("KUUDRA DOWN")
			|| cleaned.contains("KUUDRA DEFEATED")
			|| cleaned.contains("DEFEATED KUUDRA")
			|| cleaned.contains("KUUDRA RUN ENDED: COMPLETED");
	}

	private boolean isCurrentFloorKuudra() {
		return currentFloor != null && currentFloor.isKuudra();
	}

	private void rememberKuudraTier(DungeonFloor tier) {
		if (tier == null || !tier.isKuudra()) return;
		currentFloor = tier;
		lastKnownKuudraFloor = tier;
	}

	private DungeonFloor bestKuudraTier(DungeonFloor candidate) {
		if (candidate != null && candidate.isKuudra()) return candidate;
		if (currentFloor != null && currentFloor.isKuudra()) return currentFloor;
		if (lastKnownKuudraFloor != null && lastKnownKuudraFloor.isKuudra()) return lastKnownKuudraFloor;
		return DungeonFloor.UNKNOWN;
	}

	private List<String> readScoreboardLines(Minecraft client) {
		Scoreboard scoreboard = client.level.getScoreboard();
		Objective sidebar = scoreboard.getDisplayObjective(DisplaySlot.SIDEBAR);
		if (sidebar == null) return List.of();

		List<String> lines = new ArrayList<>();
		lines.add(normalize(sidebar.getDisplayName().getString()));

		List<PlayerScoreEntry> entries = new ArrayList<>(scoreboard.listPlayerScores(sidebar));
		entries.removeIf(PlayerScoreEntry::isHidden);
		entries.sort(Comparator.comparingInt(PlayerScoreEntry::value).reversed());
		for (PlayerScoreEntry entry : entries) {
			if (entry.display() != null) lines.add(normalize(entry.display().getString()));
			lines.add(normalize(entry.ownerName().getString()));
		}
		return lines;
	}

	private String normalize(String value) {
		String stripped = ChatFormatting.stripFormatting(value);
		return stripped == null ? "" : stripped.trim().toUpperCase(Locale.ROOT);
	}

	private void clearRuntimeState() {
		refreshCountdown = 0;
		flushPendingLootRecord();
		resetDungeonRuntimeState();
		lastKnownKuudraFloor = DungeonFloor.UNKNOWN;
		inDungeonHub = false;
		inCrimsonIsle = false;
		dungeonSignalUntilMillis = 0L;
		kuudraSignalUntilMillis = 0L;
		lastLevelIdentity = null;
		clearLootWindow();
		cancelAutoOpenRewardChest();
		dragging = false;
		positionDirty = false;
	}

	private void resetDungeonRuntimeState() {
		insideDungeon = false;
		insideKuudra = false;
	}

	private void beginNewDungeonRun(long now) {
		flushPendingLootRecord();
		startCurrentRunTiming(now);
		currentFloor = DungeonFloor.UNKNOWN;
		lastKnownKuudraFloor = DungeonFloor.UNKNOWN;
		insideKuudra = false;
		kuudraSignalUntilMillis = 0L;
		awaitingExtraStatsScore = false;
		awaitingExtraStatsFloor = DungeonFloor.UNKNOWN;
		awaitingExtraStatsUntilMillis = 0L;
		pendingScoreGrade = null;
		pendingSPlusFloor = DungeonFloor.UNKNOWN;
		pendingSPlusUntilMillis = 0L;
		runCountedThisDungeon = false;
		dungeonSignalUntilMillis = now + DUNGEON_SIGNAL_GRACE_MS;
		cachedChestOffersByTitle.clear();
		scannedRewardScreens.clear();
	}

	private void beginNewKuudraRun(long now, DungeonFloor tier) {
		flushPendingLootRecord();
		startCurrentRunTiming(now);
		DungeonFloor runTier = tier != null && tier.isKuudra() ? tier : lastKnownKuudraFloor;
		currentFloor = runTier != null && runTier.isKuudra() ? runTier : DungeonFloor.UNKNOWN;
		rememberKuudraTier(currentFloor);
		insideDungeon = false;
		insideKuudra = true;
		awaitingExtraStatsScore = false;
		awaitingExtraStatsFloor = DungeonFloor.UNKNOWN;
		awaitingExtraStatsUntilMillis = 0L;
		pendingScoreGrade = null;
		pendingSPlusFloor = DungeonFloor.UNKNOWN;
		pendingSPlusUntilMillis = 0L;
		runCountedThisDungeon = false;
		kuudraSignalUntilMillis = now + DUNGEON_SIGNAL_GRACE_MS;
		cachedChestOffersByTitle.clear();
		scannedRewardScreens.clear();
	}

	private void startLootWindow(long now, int runNumber, DungeonFloor floor) {
		if (pendingLootChestAssigned && !pendingLootEntries.isEmpty() && pendingLootRunNumber > 0) {
			flushPendingLootRecord(true);
		}
		lootWindowUntilMillis = now + LOOT_WINDOW_MS;
		lootCollectionUntilMillis = 0L;
		pendingLootRunNumber = runNumber;
		pendingLootRunTimestamp = now;
		pendingLootFloor = floor;
		pendingLootChestTitle = "";
		pendingLootCostBreakdown = new ChestCostBreakdown();
		pendingLootSeededFromGui = false;
		pendingLootChestAssigned = false;
		openedRewardChestsInLootWindow = 0;
		nextOpenedChestUsesDungeonChestKey = false;
		nextOpenedChestUsesKismetFeather = false;
		nextOpenedChestUsesWheelOfFate = false;
		rewardMenuKismetRerollPending = false;
		rewardMenuKismetRerolledChestTitle = "";
		pendingLootEntries.clear();
	}

	public synchronized void recordManualRun(DungeonRunRecord record, long runTimeMs) {
		if (record == null) return;
		long now = System.currentTimeMillis();
		String key = record.floor;
		String g = record.grade;

		DrtConfigManager.addRunRecord(record);

		int newCount = floorRunCounts.merge(key, 1, Integer::sum);
		DrtConfigManager.updateFloorRunCount(key, newCount);
		gradeRunCounts.merge(g, 1, Integer::sum);
		sessionGradeRuns.merge(g, 1, Integer::sum);
		lastRecordedGrade = g;

		if (!sessionActive) {
			sessionActive = true;
			sessionStartMillis = now;
		}

		sessionRuns++;
		sessionFloorRuns.merge(key, 1, Integer::sum);

		long profit = record.chestProfitCoins;
		sessionTotalProfit += profit;
		sessionFloorProfitTotals.merge(key, profit, Long::sum);
		totalLifetimeProfit += profit;
		floorProfitTotals.merge(key, profit, Long::sum);

		if (runTimeMs > 0L) {
			sessionTotalRunTimeMs += runTimeMs;
			sessionFloorRunTimeTotals.merge(key, runTimeMs, Long::sum);
			sessionInRunMillis += runTimeMs;
		}

		DungeonRunTracker.LOGGER.info("[DRT] *** MANUAL RUN RECORDED: floor={} grade={} totalForFloor={} profit={} runTimeMs={}", key, g, newCount, profit, runTimeMs);
	}

	private synchronized void recordCompletedRun(long now, DungeonFloor floor, String grade) {
		String key = floor != null && floor != DungeonFloor.UNKNOWN ? floor.name() : "UNKNOWN";
		String g = grade == null || grade.isBlank() ? "?" : grade;
		if (lastRunRecordMillis > 0L && now - lastRunRecordMillis <= RUN_COMPLETION_DEDUP_WINDOW_MS) {
			DungeonRunTracker.LOGGER.info(
				"[DRT] Ignored duplicate completion signal: floor={} grade={} previousFloor={} previousGrade={} ageMs={}",
				key,
				g,
				lastRunRecordFloor != null ? lastRunRecordFloor.name() : "UNKNOWN",
				lastRunRecordGrade,
				now - lastRunRecordMillis
			);
			runCountedThisDungeon = true;
			return;
		}
		lastRunRecordMillis = now;
		lastRunRecordFloor = floor != null ? floor : DungeonFloor.UNKNOWN;
		lastRunRecordGrade = g;
		int newCount = floorRunCounts.merge(key, 1, Integer::sum);
		DrtConfigManager.updateFloorRunCount(key, newCount);
		gradeRunCounts.merge(g, 1, Integer::sum);
		sessionGradeRuns.merge(g, 1, Integer::sum);
		lastRecordedGrade = g;
		runCountedThisDungeon = true;
		DungeonRunTracker.LOGGER.info("[DRT] *** RUN RECORDED: floor={} grade={} totalForFloor={} allFloors={}", key, g, newCount, floorRunCounts);

		if (!sessionActive) {
			sessionActive = true;
			sessionStartMillis = now;
		}
		long wallRunTimeMs = finishCurrentRunTiming(now);
		if (wallRunTimeMs <= 0L) wallRunTimeMs = lastFinishedRunTimeMs;
		long runTimeMs = currentRunBossTimeMs > 0L ? currentRunBossTimeMs : wallRunTimeMs;
		sessionTotalRunTimeMs += runTimeMs;
		sessionFloorRunTimeTotals.merge(key, runTimeMs, Long::sum);
		currentRunBossTimeMs = 0L;
		lastFinishedRunTimeMs = 0L;
		sessionRuns++;
		sessionFloorRuns.merge(key, 1, Integer::sum);

		startLootWindow(now, totalRunsCompleted(), floor != null ? floor : DungeonFloor.UNKNOWN);
	}

	private void startAdHocLootWindow(long now) {
		int nextRunNumber = Math.max(totalRunsCompleted(), DrtConfigManager.getRunHistory().size()) + 1;
		startLootWindow(now, nextRunNumber, bestAdHocLootFloor());
	}

	private DungeonFloor bestAdHocLootFloor() {
		DungeonFloor kuudraFloor = currentKuudraFloorForPricing();
		if (kuudraFloor != null && kuudraFloor.isKuudra() && (insideKuudra || isCurrentFloorKuudra())) return kuudraFloor;
		return currentFloor != null ? currentFloor : DungeonFloor.UNKNOWN;
	}

	private void clearLootWindow() {
		lootWindowUntilMillis = 0L;
		lootCollectionUntilMillis = 0L;
		pendingLootRunNumber = 0;
		pendingLootRunTimestamp = 0L;
		pendingLootFloor = DungeonFloor.UNKNOWN;
		pendingLootChestTitle = "";
		pendingLootCostBreakdown = new ChestCostBreakdown();
		pendingLootSeededFromGui = false;
		pendingLootChestAssigned = false;
		openedRewardChestsInLootWindow = 0;
		nextOpenedChestUsesDungeonChestKey = false;
		nextOpenedChestUsesKismetFeather = false;
		nextOpenedChestUsesWheelOfFate = false;
		rewardMenuKismetRerollPending = false;
		rewardMenuKismetRerolledChestTitle = "";
		pendingLootEntries.clear();
		cachedChestOffersByTitle.clear();
		cachedChestOfferFingerprintsByTitle.clear();
		scannedRewardScreens.clear();
		lastRewardModifierScanKey = "";
		lastRewardModifierScanMillis = 0L;
		lastRewardModifierScanHadKeyRequirement = false;
		lastRewardModifierScanHadKismetMarker = false;
		lastViewedOpenedRewardChestTitle = "";
		recentLootMessages.clear();
	}

	private void handleLootMessage(String rawText, String cleaned, long now) {
		if ((lootWindowUntilMillis <= 0L || now > lootWindowUntilMillis) && isLootHeader(cleaned)) {
			startAdHocLootWindow(now);
		}
		if (lootWindowUntilMillis <= 0L || now > lootWindowUntilMillis) return;
		if (handleModifierMessage(cleaned)) return;
		if (cleaned.startsWith("YOU RECEIVED ")) {
			lootCollectionUntilMillis = now + LOOT_COLLECTION_MS;
		} else if (isLootHeader(cleaned)) {
			String chatChestTitle = chestTitleFromLootHeader(cleaned);
			boolean sameChest = pendingLootChestAssigned
				&& chatChestTitle != null
				&& toDisplayChestTitle(chatChestTitle).equalsIgnoreCase(pendingLootChestTitle);
			if (sameChest) {
				// Same open as the GUI capture — chat wins. Replace GUI loot, do not create a second record.
				pendingLootEntries.clear();
				pendingLootSeededFromGui = false;
				recentLootMessages.clear();
			} else if (!pendingLootEntries.isEmpty()) {
				flushPendingLootRecord(true);
				assignOpenedChest(cleaned);
			} else {
				assignOpenedChest(cleaned);
			}
			lootCollectionUntilMillis = now + LOOT_COLLECTION_MS;
			return;
		}
		if (lootCollectionUntilMillis <= 0L || now > lootCollectionUntilMillis) return;
		if (!markLootLineForProcessing(cleaned, now)) return;
		DungeonLootEntry parsed = parseLootEntry(rawText, cleaned);
		if (parsed == null) return;
		mergePendingLootEntry(parsed);
		pendingLootSeededFromGui = false;
		lootCollectionUntilMillis = now + LOOT_COLLECTION_MS;
	}

	private String chestTitleFromLootHeader(String cleaned) {
		if (cleaned == null || cleaned.isBlank()) return null;
		for (String title : REWARD_CHEST_TITLES) {
			if (cleaned.contains(title)) return title;
		}
		return null;
	}

	private boolean isLootHeader(String cleaned) {
		return cleaned.contains("DUNGEON CHEST")
			|| cleaned.contains("CHEST REWARDS")
			|| cleaned.contains("REWARD CHEST");
	}

	private void assignOpenedChest(String cleaned) {
		for (String title : REWARD_CHEST_TITLES) {
			if (!cleaned.contains(title)) continue;
			DungeonChestOffer cached = cachedChestOffersByTitle.get(title);
			assignPendingOpenedChest(title, cached);
			return;
		}
	}

	private String toDisplayChestTitle(String normalizedTitle) {
		String lower = normalizedTitle.toLowerCase(Locale.ROOT);
		String[] parts = lower.split("\\s+");
		StringBuilder builder = new StringBuilder();
		for (String part : parts) {
			if (part.isEmpty()) continue;
			if (builder.length() > 0) builder.append(' ');
			builder.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
		}
		return builder.toString();
	}

	private DungeonLootEntry parseLootEntry(String rawText, String cleaned) {
		if (rawText == null || rawText.isBlank()) return null;
		String trimmedRaw = rawText.trim();
		if (trimmedRaw.isEmpty()) return null;

		Matcher essenceMatcher = ESSENCE_PATTERN.matcher(cleaned);
		if (essenceMatcher.matches()) {
			String essenceName = essenceMatcher.group(1) + " ESSENCE";
			int quantity = parsePositiveInt(essenceMatcher.group(2), 1);
			return new DungeonLootEntry(essenceName, resolveItemId(essenceName), quantity);
		}

		String candidateName = null;
		int quantity = 1;

		Matcher receivedMatcher = RECEIVED_PATTERN.matcher(trimmedRaw);
		if (receivedMatcher.matches()) {
			candidateName = receivedMatcher.group(1);
			quantity = parsePositiveInt(receivedMatcher.group(2), 1);
		} else {
			Matcher plusMatcher = PLUS_PATTERN.matcher(trimmedRaw);
			if (plusMatcher.matches()) {
				candidateName = plusMatcher.group(1);
				quantity = parsePositiveInt(plusMatcher.group(2), 1);
			}
		}

		if (candidateName == null) {
			Matcher trailingQuantityMatcher = TRAILING_QUANTITY_PATTERN.matcher(trimmedRaw);
			if (trailingQuantityMatcher.matches()) {
				candidateName = trailingQuantityMatcher.group(1);
				quantity = parsePositiveInt(trailingQuantityMatcher.group(2), 1);
			}
		}

		if (candidateName == null) candidateName = trimmedRaw;

		candidateName = sanitizeLootName(candidateName);
		if (candidateName.isEmpty() || candidateName.length() > 80 || looksLikeNonLootLine(candidateName) || shouldIgnoreLootName(candidateName)) {
			return null;
		}

		String itemId = resolveItemId(candidateName);
		if (itemId.isEmpty() && !looksReasonableLootName(candidateName)) return null;
		return new DungeonLootEntry(candidateName, itemId, quantity);
	}

	private boolean isRewardsMenuTitle(String normalizedTitle) {
		return normalizedTitle.startsWith("CATACOMBS - FLOOR ")
			|| normalizedTitle.startsWith("MASTER CATACOMBS - FLOOR ")
			|| normalizedTitle.contains("KUUDRA")
			|| ((insideKuudra || isCurrentFloorKuudra()) && (normalizedTitle.contains("REWARD") || normalizedTitle.contains("CHEST")));
	}

	private Long parseChestCost(ItemStack stack) {
		List<String> loreLines = cleanLoreLines(stack);
		for (int index = 0; index < loreLines.size(); index++) {
			String line = loreLines.get(index);
			if (line.equalsIgnoreCase("FREE")) return 0L;
			if (line.equalsIgnoreCase("Cost") && index + 1 < loreLines.size()) {
				Long parsed = parseCoins(loreLines.get(index + 1));
				if (parsed != null) return parsed;
			}
			if (line.regionMatches(true, 0, "Cost:", 0, 5)) return parseCoins(line);
		}
		return null;
	}

	private List<String> cleanLoreLines(ItemStack stack) {
		ItemLore lore = stack.get(DataComponents.LORE);
		if (lore == null) return List.of();
		List<String> cleaned = new ArrayList<>();
		lore.styledLines().forEach(line -> {
			String cleanedLine = cleanText(line.getString());
			if (!cleanedLine.isEmpty()) cleaned.add(cleanedLine);
		});
		return cleaned;
	}

	private String cleanText(String text) {
		String stripped = ChatFormatting.stripFormatting(text);
		return stripped == null ? "" : stripped.trim();
	}

	private Long parseCoins(String line) {
		Matcher matcher = COIN_PATTERN.matcher(cleanText(line));
		if (!matcher.find()) return null;
		double amount = Double.parseDouble(matcher.group(1).replace(",", ""));
		String suffix = matcher.group(2);
		if (suffix != null && !suffix.isBlank()) {
			switch (Character.toLowerCase(suffix.charAt(0))) {
				case 'k' -> amount *= 1_000.0D;
				case 'm' -> amount *= 1_000_000.0D;
				case 'b' -> amount *= 1_000_000_000.0D;
				default -> {}
			}
		}
		return Math.round(Math.abs(amount));
	}

	private void mergePendingLootEntry(DungeonLootEntry incoming) {
		String incomingKey = lootKey(incoming);
		for (DungeonLootEntry existing : pendingLootEntries) {
			if (lootKey(existing).equals(incomingKey)) {
				if (pendingLootSeededFromGui) {
					existing.quantity = Math.max(existing.quantity, incoming.quantity);
					return;
				}
				existing.quantity += incoming.quantity;
				return;
			}
		}
		pendingLootEntries.add(incoming);
	}

	private void flushPendingLootRecord() {
		flushPendingLootRecord(false);
	}

	private void flushPendingLootRecord(boolean keepWindow) {
		if (pendingLootEntries.isEmpty() || pendingLootRunNumber <= 0) {
			if (keepWindow) resetPendingChestState();
			else clearLootWindow();
			return;
		}
		DrtConfig config = DrtConfigManager.getConfig();
		long chestValueCoins = DungeonProfitPricing.calculateLootValue(pendingLootEntries, config);
		ChestCostBreakdown costBreakdown = pendingLootCostBreakdown == null ? new ChestCostBreakdown() : pendingLootCostBreakdown.copy();
		if (costBreakdown.usedKismetFeather) costBreakdown.kismetRerolledChestOpened = true;
		DungeonFloor kuudraFloor = currentKuudraFloorForPricing();
		if (pendingLootFloor == DungeonFloor.UNKNOWN && kuudraFloor != null && kuudraFloor.isKuudra()) {
			pendingLootFloor = kuudraFloor;
		}
		String pendingChestTitle = pendingLootChestTitle == null ? "" : pendingLootChestTitle.toUpperCase(Locale.ROOT);
		applyKuudraChestCostHint(pendingChestTitle, costBreakdown);
		populateKnownModifierCosts(costBreakdown);
		suppressDungeonChestKeyForKuudra(pendingChestTitle, costBreakdown);
		long chestCostCoins = costBreakdown.totalCostCoins();
		long chestProfitCoins = chestValueCoins - chestCostCoins;
		DungeonRunTracker.LOGGER.info(
			"[DRT][FLUSH] entries={} value={} cost={} base={} key={} kismet={} wheel={} kuudraKey={} profit={} floor={} chest='{}'",
			pendingLootEntries.size(),
			chestValueCoins,
			chestCostCoins,
			costBreakdown.baseChestCostCoins,
			costBreakdown.dungeonChestKeyCostCoins,
			costBreakdown.kismetFeatherCostCoins,
			costBreakdown.wheelOfFateCostCoins,
			costBreakdown.kuudraKeyCostCoins,
			chestProfitCoins,
			pendingLootFloor,
			pendingLootChestTitle
		);
		String floorName = pendingLootFloor != DungeonFloor.UNKNOWN ? pendingLootFloor.name() : "UNKNOWN";
		String runGrade = pendingScoreGrade != null ? pendingScoreGrade : lastRecordedGrade;
		DungeonRunRecord record = new DungeonRunRecord(
			pendingLootRunTimestamp,
			pendingLootRunNumber,
			floorName,
			runGrade,
			pendingLootChestTitle,
			chestCostCoins,
			chestValueCoins,
			chestProfitCoins,
			pendingLootEntries
		);
		record.applyCostBreakdown(costBreakdown);
		DrtConfigManager.addRunRecord(record);
		totalLifetimeProfit += chestProfitCoins;
		sessionTotalProfit += chestProfitCoins;
		floorProfitTotals.merge(floorName, chestProfitCoins, Long::sum);
		sessionFloorProfitTotals.merge(floorName, chestProfitCoins, Long::sum);
		if (keepWindow) resetPendingChestState();
		else clearLootWindow();
	}

	private void resetPendingChestState() {
		lootCollectionUntilMillis = 0L;
		pendingLootChestTitle = "";
		pendingLootCostBreakdown = new ChestCostBreakdown();
		pendingLootSeededFromGui = false;
		pendingLootChestAssigned = false;
		pendingLootEntries.clear();
		recentLootMessages.clear();
	}

	private boolean markLootLineForProcessing(String cleaned, long now) {
		if (cleaned == null || cleaned.isBlank()) return true;
		String key = pendingLootRunNumber + "|" + cleaned.trim();
		for (var iterator = recentLootMessages.iterator(); iterator.hasNext();) {
			PacketCapturedMessage captured = iterator.next();
			if (now - captured.atMillis > MESSAGE_DEDUP_WINDOW_MS) {
				iterator.remove();
				continue;
			}
			if (captured.text.equals(key)) return false;
		}
		recentLootMessages.addLast(new PacketCapturedMessage(key, now));
		while (recentLootMessages.size() > 64) recentLootMessages.removeFirst();
		return true;
	}

	private String resolveItemId(String rawName) {
		String enchantedBookId = resolveEnchantedBookId(rawName);
		if (enchantedBookId != null) return enchantedBookId;

		String cleanedName = sanitizeLootName(rawName).toUpperCase(Locale.ROOT);
		// Shiny dungeon drops share the base item id/price (e.g. "Shiny Necron's Handle").
		if (cleanedName.startsWith("SHINY ")) {
			cleanedName = cleanedName.substring("SHINY ".length()).trim();
		}
		String alias = ITEM_ID_ALIASES.get(cleanedName);
		if (alias != null) return alias;

		String generatedKuudraId = generatedKuudraItemId(cleanedName);
		if (generatedKuudraId != null) return generatedKuudraId;

		List<PriceCache.SearchResult> searchResults = PriceCache.search(cleanedName, 5);
		if (!searchResults.isEmpty()) return searchResults.getFirst().itemId();
		return "";
	}

	private String generatedKuudraItemId(String cleanedName) {
		if (cleanedName == null || cleanedName.isBlank()) return null;
		if (cleanedName.endsWith(" SHARD")) {
			String shardName = cleanedName.substring(0, cleanedName.length() - " SHARD".length()).trim();
			if (!shardName.isBlank()) return "SHARD_" + toItemIdPart(shardName);
		}
		return null;
	}

	private String toItemIdPart(String name) {
		return sanitizeLootName(name)
			.toUpperCase(Locale.ROOT)
			.replaceAll("[^A-Z0-9]+", "_")
			.replaceAll("^_+|_+$", "");
	}

	private String sanitizeLootName(String name) {
		String sanitized = ChatFormatting.stripFormatting(name);
		if (sanitized == null) return "";
		sanitized = sanitized.replaceFirst("^(?i)[A-Z ]+ REWARD!\\s*", "");
		sanitized = sanitized.replaceAll("[✪★☆]", "");
		sanitized = sanitized.replaceAll("\\s+", " ").trim();
		sanitized = sanitized.replaceAll("[!,:;]+$", "").trim();
		return sanitized;
	}

	private String resolveEnchantedBookId(String itemName) {
		String cleaned = sanitizeLootName(itemName);
		Matcher matcher = ENCHANTED_BOOK_PATTERN.matcher(cleaned);
		if (!matcher.matches()) return null;

		String enchantName = matcher.group(1);
		String romanLevel = matcher.group(2);
		int numericLevel = romanToInt(romanLevel);
		if (numericLevel <= 0) return null;

		String normalizedEnchantName = normalizeEnchantName(enchantName);
		boolean isUltimate = ULTIMATE_ENCHANTS.contains(normalizedEnchantName);
		if (isUltimate) return "ENCHANTMENT_ULTIMATE_" + normalizedEnchantName + "_" + numericLevel;
		return "ENCHANTMENT_" + normalizedEnchantName + "_" + numericLevel;
	}

	private String normalizeEnchantName(String enchantName) {
		String normalized = sanitizeLootName(enchantName).replaceFirst("^(?i)Ultimate\\s+", "");
		return normalized
			.replaceAll("[^A-Za-z0-9 ]", " ")
			.trim()
			.replaceAll("\\s+", "_")
			.toUpperCase(Locale.ROOT);
	}

	private int romanToInt(String roman) {
		return switch (roman.toUpperCase(Locale.ROOT)) {
			case "I" -> 1; case "II" -> 2; case "III" -> 3; case "IV" -> 4;
			case "V" -> 5; case "VI" -> 6; case "VII" -> 7; case "VIII" -> 8;
			case "IX" -> 9; case "X" -> 10; default -> -1;
		};
	}

	private boolean looksLikeNonLootLine(String value) {
		String normalized = value.toUpperCase(Locale.ROOT);
		return normalized.contains("[NPC]") || normalized.contains("EXTRA STATS")
			|| normalized.contains("TEAM SCORE") || normalized.contains("CLICK")
			|| normalized.contains("OPEN") || normalized.contains("CROESUS")
			|| normalized.contains("THE CATACOMBS") || normalized.contains("KUUDRA DOWN")
			|| normalized.contains("PERCENTAGE COMPLETE") || normalized.contains("TOKENS EARNED")
			|| normalized.contains("BITS EARNED") || normalized.startsWith("TIME:")
			|| normalized.contains("[BAZAAR]") || normalized.contains("BAZAAR")
			|| normalized.contains("SOLD ") || normalized.contains("BOUGHT ")
			|| normalized.contains("COINS!") || normalized.contains("RNG METER");
	}

	private boolean shouldIgnoreLootName(String value) {
		return value != null && sanitizeLootName(value).equalsIgnoreCase("Ancient Rose");
	}

	private boolean looksReasonableLootName(String value) {
		String normalized = value.toUpperCase(Locale.ROOT);
		return normalized.contains("ESSENCE") || normalized.contains("CHESTPLATE")
			|| normalized.contains("LEGGINGS") || normalized.contains("BOOTS")
			|| normalized.contains("HELMET") || normalized.contains("DAGGER")
			|| normalized.contains("FURY") || normalized.contains("BREATH")
			|| normalized.contains("STONE") || normalized.contains("RECOMBOBULATOR")
			|| normalized.contains("BOOK") || normalized.contains("DISC")
			|| normalized.contains("KEY") || normalized.contains("SHARD")
			|| normalized.contains("HANDLE") || normalized.contains("SCROLL")
			|| normalized.contains("CATALYST") || normalized.contains("SHINY")
			|| normalized.contains("KUUDRA") || normalized.contains("TEETH")
			|| normalized.contains("EMBERS") || normalized.contains("CORE")
			|| normalized.contains("DISINTEGRATOR") || normalized.contains("CRIMSON")
			|| normalized.contains("TERROR") || normalized.contains("AURORA")
			|| normalized.contains("FERVOR") || normalized.contains("HOLLOW");
	}

	private String lootKey(DungeonLootEntry entry) {
		if (entry.itemId != null && !entry.itemId.isBlank()) return "id:" + entry.itemId;
		return "raw:" + sanitizeLootName(entry.rawName).toUpperCase(Locale.ROOT);
	}

	private int parsePositiveInt(String value, int fallback) {
		if (value == null || value.isBlank()) return fallback;
		try {
			return Math.max(1, Integer.parseInt(value.trim()));
		} catch (NumberFormatException ignored) {
			return fallback;
		}
	}

	private static Map<String, String> createItemIdAliases() {
		Map<String, String> aliases = new HashMap<>();
		aliases.put("WITHER ESSENCE", "ESSENCE_WITHER");
		aliases.put("UNDEAD ESSENCE", "ESSENCE_UNDEAD");
		aliases.put("SPIDER ESSENCE", "ESSENCE_SPIDER");
		aliases.put("DRAGON ESSENCE", "ESSENCE_DRAGON");
		aliases.put("ICE ESSENCE", "ESSENCE_ICE");
		aliases.put("DIAMOND ESSENCE", "ESSENCE_DIAMOND");
		aliases.put("GOLD ESSENCE", "ESSENCE_GOLD");
		aliases.put("CRIMSON ESSENCE", "ESSENCE_CRIMSON");
		aliases.put("SHADOW ASSASSIN CHESTPLATE", "SHADOW_ASSASSIN_CHESTPLATE");
		aliases.put("SHADOW ASSASSIN LEGGINGS", "SHADOW_ASSASSIN_LEGGINGS");
		aliases.put("SHADOW ASSASSIN BOOTS", "SHADOW_ASSASSIN_BOOTS");
		aliases.put("SHADOW ASSASSIN HELMET", "SHADOW_ASSASSIN_HELMET");
		aliases.put("LAST BREATH", "LAST_BREATH");
		aliases.put("LIVID DAGGER", "LIVID_DAGGER");
		aliases.put("SHADOW FURY", "SHADOW_FURY");
		aliases.put("WARPED STONE", "WARPED_STONE");
		aliases.put("RECOMBOBULATOR 3000", "RECOMBOBULATOR_3000");
		aliases.put("DUNGEON CHEST KEY", "DUNGEON_CHEST_KEY");
		aliases.put("KISMET FEATHER", "KISMET_FEATHER");
		aliases.put("WHEEL OF FATE", "WHEEL_OF_FATE");
		for (int tier = 1; tier <= 10; tier++) {
			aliases.put("MASTER SKULL TIER " + tier, "MASTER_SKULL_TIER_" + tier);
			aliases.put("MASTER SKULL - TIER " + tier, "MASTER_SKULL_TIER_" + tier);
		}
		aliases.put("WITHER CATALYST", "WITHER_CATALYST");
		aliases.put("WITHER HELMET", "WITHER_HELMET");
		aliases.put("WITHER CHESTPLATE", "WITHER_CHESTPLATE");
		aliases.put("WITHER LEGGINGS", "WITHER_LEGGINGS");
		aliases.put("WITHER BOOTS", "WITHER_BOOTS");
		aliases.put("APEX DRAGON SHARD", "SHARD_APEX_DRAGON");
		aliases.put("NECRON'S HANDLE", "NECRON_HANDLE");
		aliases.put("SCARF'S STUDIES", "SCARF_STUDIES");
		aliases.put("SHADOW WARP", "SHADOW_WARP_SCROLL");
		aliases.put("SHADOW WARP SCROLL", "SHADOW_WARP_SCROLL");
		aliases.put("WITHER SHIELD", "WITHER_SHIELD_SCROLL");
		aliases.put("WITHER SHIELD SCROLL", "WITHER_SHIELD_SCROLL");
		aliases.put("IMPLOSION", "IMPLOSION_SCROLL");
		aliases.put("IMPLOSION SCROLL", "IMPLOSION_SCROLL");
		aliases.put("NECROMANCER LORD HELMET", "NECROMANCER_LORD_HELMET");
		aliases.put("NECROMANCER LORD CHESTPLATE", "NECROMANCER_LORD_CHESTPLATE");
		aliases.put("NECROMANCER LORD LEGGINGS", "NECROMANCER_LORD_LEGGINGS");
		aliases.put("NECROMANCER LORD BOOTS", "NECROMANCER_LORD_BOOTS");
		aliases.put("GIANTS SWORD", "GIANTS_SWORD");
		aliases.put("NECRON BLADE", "NECRON_BLADE");
		aliases.put("NECROMANCER SWORD", "NECROMANCER_SWORD");
		aliases.put("BONZO STAFF", "BONZO_STAFF");
		aliases.put("PHANTOM ROD", "PHANTOM_ROD");
		aliases.put("SCYTHE BLADE", "SCYTHE_BLADE");
		aliases.put("FUMING POTATO BOOK", "FUMING_POTATO_BOOK");
		aliases.put("SPIRIT SWORD", "SPIRIT_SWORD");
		aliases.put("SPIRIT WING", "SPIRIT_WING");
		aliases.put("SPIRIT LEAP", "SPIRIT_LEAP");
		aliases.put("SPIRIT MASK", "SPIRIT_MASK");
		aliases.put("SPIRIT BONE", "SPIRIT_BONE");
		aliases.put("SPIRIT DECOY", "SPIRIT_DECOY");
		aliases.put("SPIRIT STONE", "SPIRIT_STONE");
		aliases.put("SPIRIT PET", "SPIRIT_PET");
		aliases.put("SPIRIT BOOTS", "SPIRIT_BOOTS");
		aliases.put("TRAINING WEIGHTS", "TRAINING_WEIGHTS");
		aliases.put("ZOMBIE KNIGHT HELMET", "ZOMBIE_KNIGHT_HELMET");
		aliases.put("ZOMBIE KNIGHT CHESTPLATE", "ZOMBIE_KNIGHT_CHESTPLATE");
		aliases.put("ZOMBIE KNIGHT LEGGINGS", "ZOMBIE_KNIGHT_LEGGINGS");
		aliases.put("ZOMBIE KNIGHT BOOTS", "ZOMBIE_KNIGHT_BOOTS");
		aliases.put("ZOMBIE KNIGHT SWORD", "ZOMBIE_KNIGHT_SWORD");
		aliases.put("ZOMBIE SOLDIER HELMET", "ZOMBIE_SOLDIER_HELMET");
		aliases.put("ZOMBIE SOLDIER CHESTPLATE", "ZOMBIE_SOLDIER_CHESTPLATE");
		aliases.put("ZOMBIE SOLDIER LEGGINGS", "ZOMBIE_SOLDIER_LEGGINGS");
		aliases.put("ZOMBIE SOLDIER BOOTS", "ZOMBIE_SOLDIER_BOOTS");
		aliases.put("ZOMBIE SOLDIER CUTLASS", "ZOMBIE_SOLDIER_CUTLASS");
		aliases.put("LAPIS ARMOR HELMET", "LAPIS_ARMOR_HELMET");
		aliases.put("LAPIS ARMOR CHESTPLATE", "LAPIS_ARMOR_CHESTPLATE");
		aliases.put("LAPIS ARMOR LEGGINGS", "LAPIS_ARMOR_LEGGINGS");
		aliases.put("LAPIS ARMOR BOOTS", "LAPIS_ARMOR_BOOTS");
		aliases.put("ADAPTIVE HELMET", "ADAPTIVE_HELMET");
		aliases.put("ADAPTIVE CHESTPLATE", "ADAPTIVE_CHESTPLATE");
		aliases.put("ADAPTIVE LEGGINGS", "ADAPTIVE_LEGGINGS");
		aliases.put("ADAPTIVE BOOTS", "ADAPTIVE_BOOTS");
		aliases.put("KUUDRA TEETH", "KUUDRA_TEETH");
		aliases.put("KUUDRA'S TEETH", "KUUDRA_TEETH");
		aliases.put("BEZAL SHARD", "SHARD_BEZAL");
		aliases.put("KRAKEN SHARD", "SHARD_KRAKEN");
		aliases.put("MANA DISINTEGRATOR", "MANA_DISINTEGRATOR");
		aliases.put("SMOLDERING EMBERS", "SMOLDERING_EMBERS");
		aliases.put("KUUDRA MANDIBLE", "KUUDRA_MANDIBLE");
		aliases.put("KUUDRA TENTACLE", "KUUDRA_TENTACLE");
		aliases.put("AURORA STAFF", "AURORA_STAFF");
		aliases.put("HOLLOW WAND", "HOLLOW_WAND");
		aliases.put("MOLTEN BELT", "MOLTEN_BELT");
		aliases.put("MOLTEN BRACELET", "MOLTEN_BRACELET");
		aliases.put("MOLTEN CLOAK", "MOLTEN_CLOAK");
		aliases.put("MOLTEN NECKLACE", "MOLTEN_NECKLACE");
		aliases.put("TORMENTOR", "TORMENTOR");
		aliases.put("KUUDRA'S HEART", "KUUDRAS_HEART");
		aliases.put("KUUDRA HEART", "KUUDRAS_HEART");
		aliases.put("KUUDRA'S LUNG", "KUUDRAS_LUNG");
		aliases.put("KUUDRA LUNG", "KUUDRAS_LUNG");
		aliases.put("KUUDRA'S KIDNEY", "KUUDRAS_KIDNEY");
		aliases.put("KUUDRA KIDNEY", "KUUDRAS_KIDNEY");
		aliases.put("BURNING KUUDRA CORE", "BURNING_KUUDRA_CORE");
		aliases.put("FIERY KUUDRA CORE", "FIERY_KUUDRA_CORE");
		aliases.put("INFERNAL KUUDRA CORE", "INFERNAL_KUUDRA_CORE");
		addKuudraArmorAliases(aliases);
		return Map.copyOf(aliases);
	}

	private static void addKuudraArmorAliases(Map<String, String> aliases) {
		String[] sets = {"CRIMSON", "TERROR", "AURORA", "FERVOR", "HOLLOW"};
		String[] pieces = {"HELMET", "CHESTPLATE", "LEGGINGS", "BOOTS"};
		String[] tiers = {"HOT", "BURNING", "FIERY", "INFERNAL"};
		for (String set : sets) {
			for (String piece : pieces) {
				String baseName = set + " " + piece;
				aliases.put(baseName, set + "_" + piece);
				for (String tier : tiers) {
					aliases.put(tier + " " + baseName, tier + "_" + set + "_" + piece);
				}
			}
		}
	}

	private int clamp(int value, int min, int max) {
		return Math.max(min, Math.min(max, value));
	}
}
