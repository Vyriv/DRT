package dev.vy.drt.client.tracker;

import dev.vy.drt.DungeonRunTracker;
import dev.vy.drt.config.DrtConfig;
import dev.vy.drt.config.DrtConfigManager;
import dev.vy.drt.config.DungeonFloor;
import dev.vy.drt.config.DungeonLootEntry;
import dev.vy.drt.config.DungeonRunRecord;
import dev.vy.drt.price.DungeonProfitPricing;
import dev.vy.drt.price.PriceCache;
import java.util.ArrayList;
import java.util.Comparator;
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
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.item.component.ResolvableProfile;
import net.minecraft.world.scores.DisplaySlot;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.PlayerScoreEntry;
import net.minecraft.world.scores.Scoreboard;

public final class DungeonRunTrackerFeature {
	private static final int REFRESH_INTERVAL_TICKS = 10;
	private static final long EXTRA_STATS_TIMEOUT_MS = 8_000L;
	private static final long DUNGEON_SIGNAL_GRACE_MS = 15_000L;
	private static final long LOOT_WINDOW_MS = 180_000L;
	private static final long LOOT_COLLECTION_MS = 3_000L;
	private static final Pattern ESSENCE_PATTERN = Pattern.compile("^(?:\\+\\s*)?(WITHER|UNDEAD|SPIDER|DRAGON|ICE|DIAMOND|GOLD|CRIMSON) ESSENCE(?:\\s*[xX]\\s*(\\d+))?$");
	private static final Pattern RECEIVED_PATTERN = Pattern.compile("^YOU RECEIVED\\s+(.+?)(?:\\s*[xX]\\s*(\\d+))?!?$");
	private static final Pattern PLUS_PATTERN = Pattern.compile("^\\+\\s*(.+?)(?:\\s*[xX]\\s*(\\d+))?$");
	private static final Pattern TRAILING_QUANTITY_PATTERN = Pattern.compile("^(.+?)\\s*[xX]\\s*(\\d+)$");
	private static final Pattern COIN_PATTERN = Pattern.compile("([-+]?\\d[\\d,]*(?:\\.\\d+)?)\\s*([kmb])?(?:\\s*Coins)?", Pattern.CASE_INSENSITIVE);
	private static final Pattern QUANTITY_PREFIX_PATTERN = Pattern.compile("^(\\d+)x?\\s+(.+)$");
	private static final Pattern QUANTITY_SUFFIX_PATTERN = Pattern.compile("^(.+?)\\s+x(\\d+)$", Pattern.CASE_INSENSITIVE);
	private static final Pattern ENCHANTED_BOOK_PATTERN = Pattern.compile("^Enchanted Book \\((.+) ([IVX]+)\\)$", Pattern.CASE_INSENSITIVE);
	private static final Pattern BOSS_TIME_PATTERN = Pattern.compile(".*Defeated .+ in (\\d+)m (\\d+)s.*", Pattern.CASE_INSENSITIVE);
	private static final Set<String> REWARD_CHEST_TITLES = Set.of("WOOD CHEST", "GOLD CHEST", "DIAMOND CHEST", "EMERALD CHEST", "OBSIDIAN CHEST", "BEDROCK CHEST");
	private static final Map<String, String> TIER_NAME_TO_CHEST_TITLE = Map.of(
		"WOOD", "WOOD CHEST", "GOLD", "GOLD CHEST", "DIAMOND", "DIAMOND CHEST",
		"EMERALD", "EMERALD CHEST", "OBSIDIAN", "OBSIDIAN CHEST", "BEDROCK", "BEDROCK CHEST"
	);
	private static final Set<String> ULTIMATE_ENCHANTS = Set.of(
		"LEGION", "ULTIMATE_WISE", "LAST_STAND", "SOUL_EATER", "SWARM", "COMBO", "REND",
		"NO_PAIN_NO_GAIN", "ONE_FOR_ALL", "CHIMERA", "BANK", "JERRY", "INFERNO",
		"FATAL_TEMPO", "DUPLEX", "FLASH", "HABANERO_TACTICS"
	);
	private static final Map<String, String> ITEM_ID_ALIASES = createItemIdAliases();

	private static final Map<String, ItemStack> CHEST_ICON_CACHE = new LinkedHashMap<>();
	private static final Map<String, ItemStack> ITEM_ICON_CACHE = new LinkedHashMap<>();

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
	private static final String TEX_SHADOW_ASSASSIN_HELMET = "ewogICJ0aW1lc3RhbXAiIDogMTYxOTM0NjY3MzMxNCwKICAicHJvZmlsZUlkIiA6ICJiMGQ3MzJmZTAwZjc0MDdlOWU3Zjc0NjMwMWNkOThjYSIsCiAgInByb2ZpbGVOYW1lIiA6ICJPUHBscyIsCiAgInNpZ25hdHVyZVJlcXVpcmVkIiA6IHRydWUsCiAgInRleHR1cmVzIiA6IHsKICAgICJTS0lOIiA6IHsKICAgICAgInVybCIgOiAiaHR0cDovL3RleHR1cmVzLm1pbmVjcmFmdC5uZXQvdGV4dHVyZS9hMWFhMTRkZTljMTU4ZDM1MTM5NDljODVjYWJiOWUzNzg0OGI5ODRlYzNmYjViNDRhMzIxOWY0ZmI1OGEwMGRhIgogICAgfQogIH0KfQ==";
	private static final String TEX_NECROMANCER_LORD_HELMET = "ewogICJ0aW1lc3RhbXAiIDogMTcyMDA0NDAxNDg2MiwKICAicHJvZmlsZUlkIiA6ICJlY2Q0ZTI4NjdkMmE0MTE2OTljYzlkMjMzYmM1YmEyMyIsCiAgInByb2ZpbGVOYW1lIiA6ICJSYXRlZEtub3QiLAogICJzaWduYXR1cmVSZXF1aXJlZCIgOiB0cnVlLAogICJ0ZXh0dXJlcyIgOiB7CiAgICAiU0tJTiIgOiB7CiAgICAgICJ1cmwiIDogImh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvN2RhZGU4YmFlNTY3YTA2NzBmYmViMDI1ZTYzNTA2YTA2ZTQwMTBlZjY2NDRmZjdiNTU2YmRmOTNkMjYwODk1NCIKICAgIH0KICB9Cn0=";
	private static final String TEX_WITHER_HELMET = "ewogICJ0aW1lc3RhbXAiIDogMTY4OTY4MzY3MDM4NywKICAicHJvZmlsZUlkIiA6ICJkNzU2OTc4MWUyYjY0OWIyYjVlMjVlYTJhNDZkOGQxOSIsCiAgInByb2ZpbGVOYW1lIiA6ICJEckthcGRvciIsCiAgInNpZ25hdHVyZVJlcXVpcmVkIiA6IHRydWUsCiAgInRleHR1cmVzIiA6IHsKICAgICJTS0lOIiA6IHsKICAgICAgInVybCIgOiAiaHR0cDovL3RleHR1cmVzLm1pbmVjcmFmdC5uZXQvdGV4dHVyZS82OTExMjk0MDk3ODk1ODlhZTFiNDNlMWQ4MjhjMDZmYzlhMjEzYjc4MzQ4YTY4YjczMTNiZGQ0MDFkNzQ4NWNjIiwKICAgICAgIm1ldGFkYXRhIiA6IHsKICAgICAgICAibW9kZWwiIDogInNsaW0iCiAgICAgIH0KICAgIH0KICB9Cn0=";

	public static boolean loadIconCachesFromConfig() {
		boolean loaded = true;
		loaded &= cacheSkull(CHEST_ICON_CACHE, "WOOD CHEST", TEX_WOOD);
		loaded &= cacheSkull(CHEST_ICON_CACHE, "GOLD CHEST", TEX_GOLD);
		loaded &= cacheSkull(CHEST_ICON_CACHE, "DIAMOND CHEST", TEX_DIAMOND);
		loaded &= cacheSkull(CHEST_ICON_CACHE, "EMERALD CHEST", TEX_EMERALD);
		loaded &= cacheSkull(CHEST_ICON_CACHE, "OBSIDIAN CHEST", TEX_OBSIDIAN);
		loaded &= cacheSkull(CHEST_ICON_CACHE, "BEDROCK CHEST", TEX_BEDROCK);
		loaded &= cacheSkull(ITEM_ICON_CACHE, "ESSENCE_WITHER", TEX_ESSENCE_WITHER);
		loaded &= cacheSkull(ITEM_ICON_CACHE, "ESSENCE_UNDEAD", TEX_ESSENCE_UNDEAD);
		loaded &= cacheSkull(ITEM_ICON_CACHE, "SHADOW_ASSASSIN_HELMET", TEX_SHADOW_ASSASSIN_HELMET);
		loaded &= cacheSkull(ITEM_ICON_CACHE, "NECROMANCER_LORD_HELMET", TEX_NECROMANCER_LORD_HELMET);
		loaded &= cacheSkull(ITEM_ICON_CACHE, "WITHER_HELMET", TEX_WITHER_HELMET);
		if (!loaded) return false;
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
			DungeonRunTracker.LOGGER.warn("[DRT] skullFromTexture failed: {}", e.getMessage());
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
	private int hudX = 10;
	private int hudY = 10;
	private boolean lootScreenPending = false;

	private int refreshCountdown;
	private boolean insideDungeon;
	private boolean inDungeonHub;
	private DungeonFloor currentFloor = DungeonFloor.UNKNOWN;
	private long dungeonSignalUntilMillis;
	private long awaitingExtraStatsUntilMillis;
	private boolean awaitingExtraStatsScore;
	private DungeonFloor awaitingExtraStatsFloor = DungeonFloor.UNKNOWN;
	private String pendingScoreGrade = null;
	private String lastRecordedGrade = "?";
	private DungeonFloor pendingSPlusFloor = DungeonFloor.UNKNOWN;
	private long pendingSPlusUntilMillis;
	private boolean runCountedThisDungeon;
	private Object lastLevelIdentity;

	private long lootWindowUntilMillis;
	private long lootCollectionUntilMillis;
	private int pendingLootRunNumber;
	private long pendingLootRunTimestamp;
	private String pendingLootChestTitle = "";
	private long pendingLootChestCostCoins;
	private DungeonFloor pendingLootFloor = DungeonFloor.UNKNOWN;
	private boolean pendingLootSeededFromGui;
	private final List<DungeonLootEntry> pendingLootEntries = new ArrayList<>();
	private final Map<String, CachedChestData> cachedChestDataByTitle = new HashMap<>();
	private final Set<String> scannedRewardScreens = new HashSet<>();

	private boolean sessionActive;
	private long sessionStartMillis;
	private int sessionRuns;
	private long sessionTotalProfit;
	private long totalLifetimeProfit;
	private long sessionInRunMillis;
	private long sessionTotalRunTimeMs;
	private long currentRunStartMillis;
	private long currentRunPausedMillis;
	private long currentRunPauseStartMillis;
	private boolean currentRunActive;

	private final Map<String, Integer> gradeRunCounts = new LinkedHashMap<>();
	private final Map<String, Integer> sessionGradeRuns = new LinkedHashMap<>();
	private static final String[] ALL_GRADES = {"S+", "S", "A", "B", "C", "D"};

	private boolean runsPerHrPaused;

	private boolean dragging;
	private boolean leftMouseDownLastTick;
	private boolean positionDirty;
	private int dragOffsetX;
	private int dragOffsetY;

	public void applyConfig(DrtConfig config) {
		enabled = config.enabled;
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
			clearRuntimeState();
			leftMouseDownLastTick = false;
			return;
		}

		long now = System.currentTimeMillis();
		if (lootCollectionUntilMillis > 0L && now > lootCollectionUntilMillis) {
			flushPendingLootRecord();
		} else if (lootWindowUntilMillis > 0L && now > lootWindowUntilMillis) {
			clearLootWindow();
		}

		updateDungeonContext(client);
		captureRewardChestCosts(client);
		handleDragging(client);
	}

	public void handleChatMessage(Component message) {
		handleMessage(message, false);
	}

	public void handleGameMessage(Component message, boolean overlay) {
		if (overlay) return;
		handleMessage(message, true);
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

	public void extractRenderState(Minecraft client, GuiGraphicsExtractor guiGraphics, int mouseX, int mouseY) {
		if (!isTrackerVisible(client)) return;

		String floorTag = selectedFloor == null ? "All" : selectedFloor.name();
		long lifetimeProfit = selectedFloor == null
			? totalLifetimeProfit
			: floorProfitTotals.getOrDefault(selectedFloor.name(), 0L);
		long inRunElapsedMs = activeInRunElapsedMs();
		long completedRunTimeMs = completedRunElapsedMs();
		double hoursElapsed = inRunElapsedMs / 3_600_000.0;
		long profitPerHr = hoursElapsed > 0.001 ? (long) (sessionTotalProfit / hoursElapsed) : 0L;
		double runsPerHr = hoursElapsed > 0.001 ? sessionRuns / hoursElapsed : 0.0;
		long avgRunTimeMs = sessionRuns > 0 ? completedRunTimeMs / sessionRuns : 0L;
		long avgProfitPerRun = sessionRuns > 0 ? sessionTotalProfit / sessionRuns : 0L;
		int totalRuns = selectedFloor == null
			? totalRunsCompleted()
			: floorRunCounts.getOrDefault(selectedFloor.name(), 0);

		int lineH = client.font.lineHeight + 2;

		renderHudTitleLine(client, guiGraphics, hudX, hudY, floorTag);
		renderHudRunsLine(client, guiGraphics, hudX, hudY + lineH, totalRuns, runsPerHr, avgRunTimeMs);
		renderHudProfitLine(client, guiGraphics, hudX, hudY + lineH * 2, lifetimeProfit, sessionTotalProfit, profitPerHr, avgProfitPerRun);
		seg(client, guiGraphics, "Reset " + floorTag, hudX, hudY + lineH * 3, C_RESET);

		if (client.screen != null) {
			if (isHoveringFloorLabel(client, mouseX, mouseY)) {
				drawTooltip(client, guiGraphics, "Click to cycle floors", mouseX, mouseY);
			} else if (isHoveringRunsHrPart(client, mouseX, mouseY)) {
				drawTooltip(client, guiGraphics, runsPerHrPaused ? "Click to resume /hr timer" : "Click to pause /hr timer", mouseX, mouseY);
			} else if (isHoveringRunsLine(client, mouseX, mouseY)) {
				drawTooltip(client, guiGraphics, buildRunsTooltip(), mouseX, mouseY);
			} else if (isHoveringProfitLine(client, mouseX, mouseY)) {
				drawTooltip(client, guiGraphics, "Click to open loot log", mouseX, mouseY);
			} else if (isHoveringResetButton(client, mouseX, mouseY)) {
				drawTooltip(client, guiGraphics, "Click to reset " + floorTag + " tracker", mouseX, mouseY);
			}
		}
	}

	private void renderHudTitleLine(Minecraft client, GuiGraphicsExtractor g, int x, int y, String floorTag) {
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
		return C_FLOOR;
	}

	private void renderHudRunsLine(Minecraft client, GuiGraphicsExtractor g, int x, int y, int totalRuns, double runsPerHr, long avgRunTimeMs) {
		x = seg(client, g, "Runs ", x, y, C_LABEL);
		x = seg(client, g, String.valueOf(totalRuns), x, y, C_VALUE);
		x = seg(client, g, " | ", x, y, C_SEP);
		x = seg(client, g, String.valueOf(sessionRuns), x, y, C_VALUE);
		x = seg(client, g, " | ", x, y, C_SEP);
		x = seg(client, g, formatDuration(avgRunTimeMs), x, y, C_VALUE);
		x = seg(client, g, " | ", x, y, C_SEP);
		x = seg(client, g, formatRate(runsPerHr), x, y, C_RATE);
		x = seg(client, g, "/hr", x, y, C_DIM);
		if (runsPerHrPaused) seg(client, g, " [paused]", x, y, C_PAUSED);
	}

	private void renderHudProfitLine(Minecraft client, GuiGraphicsExtractor g, int x, int y,
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

	private List<String> getDisplayLines() {
		String floorTag = selectedFloor == null ? "All" : selectedFloor.name();
		String resetLine = "Reset " + floorTag;

		int totalRuns = selectedFloor == null
			? totalRunsCompleted()
			: floorRunCounts.getOrDefault(selectedFloor.name(), 0);
		long lifetimeProfit = selectedFloor == null
			? totalLifetimeProfit
			: floorProfitTotals.getOrDefault(selectedFloor.name(), 0L);

		long inRunElapsedMs = activeInRunElapsedMs();
		long completedRunTimeMs = completedRunElapsedMs();
		double hoursElapsed = inRunElapsedMs / 3_600_000.0;
		double runsPerHr = hoursElapsed > 0.001 ? sessionRuns / hoursElapsed : 0.0;
		double profitPerHr = hoursElapsed > 0.001 ? sessionTotalProfit / hoursElapsed : 0.0;
		long avgRunTimeMs = sessionRuns > 0 ? completedRunTimeMs / sessionRuns : 0L;
		long avgProfitPerRun = sessionRuns > 0 ? sessionTotalProfit / sessionRuns : 0L;

		String runsLine = "Runs " + totalRuns + " | " + sessionRuns + " | " + formatDuration(avgRunTimeMs) + " | " + formatRate(runsPerHr) + "/hr"
			+ (runsPerHrPaused ? " [paused]" : "");
		String profitLine = "Profit " + formatCoins(lifetimeProfit) + " | " + formatCoins(sessionTotalProfit) + " | " + formatCoins(avgProfitPerRun) + "/run | " + formatCoins((long) profitPerHr) + "/hr";
		return List.of("DRT [" + floorTag + "]", runsLine, profitLine, resetLine);
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
		List<DungeonFloor> available = new ArrayList<>();
		available.add(null);
		for (DungeonFloor f : DungeonFloor.values()) {
			if (f != DungeonFloor.UNKNOWN) available.add(f);
		}
		int idx = available.indexOf(selectedFloor);
		selectedFloor = available.get((idx + 1) % available.size());
		DrtConfigManager.updateSelectedFloor(selectedFloor == null ? null : selectedFloor.name());
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

	public int getDisplayWidth(Minecraft client) {
		if (client == null) return 0;
		return getDisplayLines().stream().mapToInt(l -> client.font.width(l)).max().orElse(0);
	}

	public int getDisplayHeight(Minecraft client) {
		if (client == null) return 0;
		List<String> lines = getDisplayLines();
		return lines.size() * (client.font.lineHeight + 2) - 2;
	}

	public void setHudPosition(Minecraft client, int x, int y, boolean save) {
		if (client == null) return;
		int maxX = Math.max(0, client.getWindow().getGuiScaledWidth() - getDisplayWidth(client));
		int maxY = Math.max(0, client.getWindow().getGuiScaledHeight() - getDisplayHeight(client));
		hudX = clamp(x, 0, maxX);
		hudY = clamp(y, 0, maxY);
		if (save) saveHudPosition();
	}

	public boolean handleScreenMouseClick(Minecraft client, double mouseX, double mouseY, int button) {
		if (!isTrackerVisible(client) || button != 0) return false;
		int mx = (int) mouseX;
		int my = (int) mouseY;
		if (isHoveringFloorLabel(client, mx, my)) { cycleSelectedFloor(); return true; }
		if (isHoveringRunsHrPart(client, mx, my)) { toggleRunsPerHrPause(); return true; }
		if (isHoveringProfitLine(client, mx, my)) { lootScreenPending = true; return true; }
		if (isHoveringResetButton(client, mx, my)) { resetSelectedFloor(); return true; }
		return false;
	}

	public boolean consumeLootScreenPending() {
		boolean v = lootScreenPending;
		lootScreenPending = false;
		return v;
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
		currentRunActive = false;
		runsPerHrPaused = false;
		sessionGradeRuns.clear();
	}

	private long activeInRunElapsedMs() {
		long elapsed = sessionInRunMillis;
		if (!currentRunActive || currentRunStartMillis <= 0L) return Math.max(0L, elapsed);
		long paused = currentRunPausedMillis;
		if (runsPerHrPaused && currentRunPauseStartMillis > 0L) {
			paused += System.currentTimeMillis() - currentRunPauseStartMillis;
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
	}

	private void finishCurrentRunTiming(long now) {
		if (!currentRunActive || currentRunStartMillis <= 0L) return;
		long paused = currentRunPausedMillis;
		if (runsPerHrPaused && currentRunPauseStartMillis > 0L) {
			paused += now - currentRunPauseStartMillis;
		}
		long elapsed = Math.max(0L, now - currentRunStartMillis - paused);
		sessionInRunMillis += elapsed;
		currentRunActive = false;
		currentRunStartMillis = 0L;
		currentRunPausedMillis = 0L;
		currentRunPauseStartMillis = 0L;
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
		if (!enabled || message == null) return;

		long now = System.currentTimeMillis();
		if (lootCollectionUntilMillis > 0L && now > lootCollectionUntilMillis) flushPendingLootRecord();
		if (lootWindowUntilMillis > 0L && now > lootWindowUntilMillis) clearLootWindow();

		String rawText = ChatFormatting.stripFormatting(message.getString());
		String cleaned = normalize(rawText);
		if (cleaned.isEmpty()) return;

		if (isDungeonEntryMessage(cleaned)) {
			beginNewDungeonRun(now);
			insideDungeon = true;
		}

		Matcher bossTimeMatcher = BOSS_TIME_PATTERN.matcher(rawText == null ? cleaned : rawText);
		if (bossTimeMatcher.matches()) {
			long minutes = parsePositiveInt(bossTimeMatcher.group(1), 0);
			long seconds = parsePositiveInt(bossTimeMatcher.group(2), 0);
			sessionTotalRunTimeMs += (minutes * 60L + seconds) * 1000L;
		}

		DungeonFloor lineFloor = detectFloorFromLine(cleaned);
		if (lineFloor != DungeonFloor.UNKNOWN) currentFloor = lineFloor;

		if (awaitingExtraStatsScore && now > awaitingExtraStatsUntilMillis) {
			awaitingExtraStatsScore = false;
			awaitingExtraStatsFloor = DungeonFloor.UNKNOWN;
		}
		if (pendingScoreGrade != null && now > pendingSPlusUntilMillis) {
			pendingScoreGrade = null;
			pendingSPlusFloor = DungeonFloor.UNKNOWN;
			pendingSPlusUntilMillis = 0L;
		}

		String preGrade = extractScoreGrade(cleaned);
		if (preGrade != null && pendingScoreGrade == null && !awaitingExtraStatsScore) {
			pendingScoreGrade = preGrade;
			pendingSPlusFloor = currentFloor;
			pendingSPlusUntilMillis = now + EXTRA_STATS_TIMEOUT_MS;
		}

		if (isExtraStatsHeader(cleaned)) {
			if (pendingScoreGrade != null && !runCountedThisDungeon) {
				DungeonFloor f = pendingSPlusFloor != DungeonFloor.UNKNOWN ? pendingSPlusFloor : currentFloor;
				recordCompletedRun(now, f, pendingScoreGrade);
				pendingScoreGrade = null;
				pendingSPlusFloor = DungeonFloor.UNKNOWN;
				pendingSPlusUntilMillis = 0L;
			} else {
				DungeonFloor contextFloor = currentFloor != DungeonFloor.UNKNOWN ? currentFloor : pendingSPlusFloor;
				awaitingExtraStatsScore = true;
				awaitingExtraStatsFloor = contextFloor;
				awaitingExtraStatsUntilMillis = now + EXTRA_STATS_TIMEOUT_MS;
			}
			handleLootMessage(rawText == null ? cleaned : rawText.trim(), cleaned, now);
			return;
		}

		if (awaitingExtraStatsScore) {
			if (lineFloor != DungeonFloor.UNKNOWN) {
				awaitingExtraStatsFloor = lineFloor;
				pendingSPlusFloor = lineFloor;
			}
			String postGrade = extractScoreGrade(cleaned);
			if (postGrade != null) {
				if (!runCountedThisDungeon) {
					DungeonFloor scoreFloor = awaitingExtraStatsFloor != DungeonFloor.UNKNOWN ? awaitingExtraStatsFloor : currentFloor;
					recordCompletedRun(now, scoreFloor, postGrade);
				}
				awaitingExtraStatsScore = false;
				awaitingExtraStatsFloor = DungeonFloor.UNKNOWN;
				awaitingExtraStatsUntilMillis = 0L;
			}
		}

		handleLootMessage(rawText == null ? cleaned : rawText.trim(), cleaned, now);
	}

	private void updateDungeonContext(Minecraft client) {
		long now = System.currentTimeMillis();
		Object currentLevel = client.level;
		if (lastLevelIdentity != currentLevel) {
			finishCurrentRunTiming(now);
			lastLevelIdentity = currentLevel;
			resetDungeonRuntimeState();
			inDungeonHub = false;
		}

		if (refreshCountdown > 0) {
			refreshCountdown--;
			return;
		}

		refreshCountdown = REFRESH_INTERVAL_TICKS;
		List<String> scoreboardLines = readScoreboardLines(client);
		List<String> tabLines = TabReader.readNormalizedLines(client);
		boolean wasDungeon = insideDungeon;
		inDungeonHub = isDungeonHub(scoreboardLines) || isDungeonHub(tabLines);
		insideDungeon = isDungeon(scoreboardLines) || isDungeonTab(tabLines) || now <= dungeonSignalUntilMillis;

		if (!insideDungeon) {
			finishCurrentRunTiming(now);
			resetDungeonRuntimeState();
			return;
		}

		DungeonFloor detected = detectFloorFromLines(scoreboardLines);
		if (detected == DungeonFloor.UNKNOWN) detected = detectFloorFromLines(tabLines);
		if (detected != DungeonFloor.UNKNOWN && detected != currentFloor) currentFloor = detected;
	}

	private void captureRewardChestCosts(Minecraft client) {
		if (!enabled || !(client.screen instanceof AbstractContainerScreen<?> screen) || client.player == null) return;

		String normalizedTitle = normalize(screen.getTitle().getString());

		if (REWARD_CHEST_TITLES.contains(normalizedTitle) && lootWindowUntilMillis > 0L) {
			String screenKey = "opened#" + normalizedTitle + "#" + client.player.containerMenu.containerId;
			if (scannedRewardScreens.add(screenKey)) {
				pendingLootChestTitle = toDisplayChestTitle(normalizedTitle);
				CachedChestData cached = cachedChestDataByTitle.get(normalizedTitle);
				if (cached != null) {
					pendingLootChestCostCoins = cached.costCoins;
					pendingLootSeededFromGui = false;
					lootCollectionUntilMillis = System.currentTimeMillis() + LOOT_COLLECTION_MS;
				} else {
					pendingLootChestCostCoins = 0L;
				}
			}
			return;
		}

		if (!isRewardsMenuTitle(normalizedTitle)) return;

		AbstractContainerMenu menu = client.player.containerMenu;
		if (menu == null) return;

		String screenKey = normalizedTitle + "#" + menu.containerId;
		if (!scannedRewardScreens.add(screenKey)) return;

		DungeonFloor titleFloor = detectFloorFromLine(normalizedTitle);
		if (titleFloor != DungeonFloor.UNKNOWN && titleFloor != currentFloor) currentFloor = titleFloor;

		for (int slotIndex = 0; slotIndex < menu.slots.size(); slotIndex++) {
			ItemStack stack = menu.getSlot(slotIndex).getItem();
			if (stack.isEmpty()) continue;
			String chestTitle = normalize(cleanText(stack.getHoverName().getString()));
			String canonicalKey = TIER_NAME_TO_CHEST_TITLE.get(chestTitle);
			if (canonicalKey == null) {
				for (String t : REWARD_CHEST_TITLES) {
					if (chestTitle.startsWith(t)) { canonicalKey = t; break; }
				}
			}
			if (canonicalKey == null) continue;
			CHEST_ICON_CACHE.putIfAbsent(canonicalKey, stack.copy());
			if (cachedChestDataByTitle.containsKey(canonicalKey)) continue;
			CachedChestData data = parseChestData(stack);
			cachedChestDataByTitle.put(canonicalKey, data);
		}

		long now = System.currentTimeMillis();
		if (lootWindowUntilMillis <= 0L || now > lootWindowUntilMillis) startAdHocLootWindow(now);
	}

	private CachedChestData parseChestData(ItemStack stack) {
		List<String> lore = cleanLoreLines(stack);
		long costCoins = 0L;
		long valueCoins = 0L;
		List<DungeonLootEntry> entries = new ArrayList<>();

		Long parsedCost = parseChestCost(stack);
		if (parsedCost != null) costCoins = parsedCost;
		for (int i = 0; i < lore.size(); i++) {
			String line = lore.get(i);
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
		return new CachedChestData(costCoins, entries);
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

	private static final class CachedChestData {
		final long costCoins;
		final List<DungeonLootEntry> lootEntries;
		CachedChestData(long costCoins, List<DungeonLootEntry> lootEntries) {
			this.costCoins = costCoins;
			this.lootEntries = lootEntries;
		}
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
			if (isHoveringFloorLabel(client, mouseX, mouseY)) { cycleSelectedFloor(); leftMouseDownLastTick = true; return; }
			if (isHoveringRunsHrPart(client, mouseX, mouseY)) { toggleRunsPerHrPause(); leftMouseDownLastTick = true; return; }
			if (isHoveringProfitLine(client, mouseX, mouseY)) { lootScreenPending = true; leftMouseDownLastTick = true; return; }
			if (isHoveringResetButton(client, mouseX, mouseY)) { resetSelectedFloor(); leftMouseDownLastTick = true; return; }
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
		DrtConfigManager.updateHudPosition(hudX, hudY);
	}

	private boolean isHoveringTracker(Minecraft client, int mouseX, int mouseY) {
		int width = getDisplayWidth(client);
		int height = getDisplayHeight(client);
		return mouseX >= hudX - 3 && mouseX <= hudX + width + 3 && mouseY >= hudY - 2 && mouseY <= hudY + height + 2;
	}

	private boolean isTrackerVisible(Minecraft client) {
		return enabled && client != null && client.player != null && (insideDungeon || inDungeonHub);
	}

	private boolean isHoveringRunsLine(Minecraft client, int mouseX, int mouseY) {
		int lineH = client.font.lineHeight + 2;
		int ly = hudY + lineH;
		int lineW = client.font.width(getDisplayLines().get(1));
		return mouseX >= hudX - 3 && mouseX <= hudX + lineW + 3
			&& mouseY >= ly - 2 && mouseY <= ly + client.font.lineHeight + 2;
	}

	private boolean isHoveringRunsHrPart(Minecraft client, int mouseX, int mouseY) {
		if (!isHoveringRunsLine(client, mouseX, mouseY)) return false;
		String runsLine = getDisplayLines().get(1);
		int firstSep = runsLine.indexOf(" | ");
		if (firstSep < 0) return false;
		int secondSep = runsLine.indexOf(" | ", firstSep + 3);
		if (secondSep < 0) return false;
		int thirdSep = runsLine.indexOf(" | ", secondSep + 3);
		if (thirdSep < 0) return false;
		String visibleRunsLine = runsLine.replace(" [paused]", "");
		int startX = hudX + client.font.width(runsLine.substring(0, thirdSep + 3));
		int endX = hudX + client.font.width(visibleRunsLine);
		return mouseX >= startX && mouseX <= endX;
	}

	private boolean isHoveringFloorLabel(Minecraft client, int mouseX, int mouseY) {
		List<String> lines = getDisplayLines();
		int labelWidth = client.font.width(lines.get(0));
		int lineH = client.font.lineHeight + 2;
		return mouseX >= hudX - 3 && mouseX <= hudX + labelWidth + 3
			&& mouseY >= hudY - 2 && mouseY <= hudY + lineH;
	}

	private boolean isHoveringProfitLine(Minecraft client, int mouseX, int mouseY) {
		int lineH = client.font.lineHeight + 2;
		int ly = hudY + lineH * 2;
		int lineW = client.font.width(getDisplayLines().get(2));
		return mouseX >= hudX - 3 && mouseX <= hudX + lineW + 3
			&& mouseY >= ly - 2 && mouseY <= ly + client.font.lineHeight + 2;
	}

	private boolean isHoveringResetButton(Minecraft client, int mouseX, int mouseY) {
		List<String> lines = getDisplayLines();
		int lastIdx = lines.size() - 1;
		int lineH = client.font.lineHeight + 2;
		int resetY = hudY + lastIdx * lineH;
		int resetWidth = client.font.width(lines.get(lastIdx));
		return mouseX >= hudX - 3 && mouseX <= hudX + resetWidth + 3
			&& mouseY >= resetY - 2 && mouseY <= resetY + client.font.lineHeight + 2;
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
			if (f == DungeonFloor.UNKNOWN) continue;
			String name = f.name();
			if (line.contains("(" + name + ")") || line.contains("DUNGEON: " + name)) return f;
		}
		boolean isMaster = masterContext || line.contains("MASTER");
		if ((isMaster || line.contains("CATACOMBS")) && line.contains("FLOOR")) {
			int n = parseFloorNumber(line);
			if (n >= 1 && n <= 7) return DungeonFloor.values()[isMaster ? 7 + n - 1 : n - 1];
		}
		return DungeonFloor.UNKNOWN;
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
		if (!line.contains("SCORE")) return null;
		if (line.contains("S+")) return "S+";
		if (line.contains("(S)") || line.endsWith(" S")) return "S";
		if (line.contains("(A)") || line.endsWith(" A")) return "A";
		if (line.contains("(B)") || line.endsWith(" B")) return "B";
		if (line.contains("(C)") || line.endsWith(" C")) return "C";
		if (line.contains("(D)") || line.endsWith(" D")) return "D";
		return null;
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
		inDungeonHub = false;
		dungeonSignalUntilMillis = 0L;
		lastLevelIdentity = null;
		clearLootWindow();
		dragging = false;
		positionDirty = false;
		clearSession();
	}

	private void resetDungeonRuntimeState() {
		insideDungeon = false;
	}

	private void beginNewDungeonRun(long now) {
		flushPendingLootRecord();
		startCurrentRunTiming(now);
		currentFloor = DungeonFloor.UNKNOWN;
		awaitingExtraStatsScore = false;
		awaitingExtraStatsFloor = DungeonFloor.UNKNOWN;
		awaitingExtraStatsUntilMillis = 0L;
		pendingScoreGrade = null;
		pendingSPlusFloor = DungeonFloor.UNKNOWN;
		pendingSPlusUntilMillis = 0L;
		runCountedThisDungeon = false;
		dungeonSignalUntilMillis = now + DUNGEON_SIGNAL_GRACE_MS;
		cachedChestDataByTitle.clear();
		scannedRewardScreens.clear();
	}

	private void startLootWindow(long now, int runNumber, DungeonFloor floor) {
		lootWindowUntilMillis = now + LOOT_WINDOW_MS;
		lootCollectionUntilMillis = 0L;
		pendingLootRunNumber = runNumber;
		pendingLootRunTimestamp = now;
		pendingLootFloor = floor;
		pendingLootChestTitle = "";
		pendingLootChestCostCoins = 0L;
		pendingLootSeededFromGui = false;
		pendingLootEntries.clear();
	}

	private void recordCompletedRun(long now, DungeonFloor floor, String grade) {
		String key = floor != null && floor != DungeonFloor.UNKNOWN ? floor.name() : "UNKNOWN";
		String g = grade == null || grade.isBlank() ? "?" : grade;
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
		finishCurrentRunTiming(now);
		sessionRuns++;

		startLootWindow(now, totalRunsCompleted(), floor != null ? floor : DungeonFloor.UNKNOWN);
	}

	private void startAdHocLootWindow(long now) {
		int nextRunNumber = Math.max(totalRunsCompleted(), DrtConfigManager.getRunHistory().size()) + 1;
		startLootWindow(now, nextRunNumber, currentFloor);
	}

	private void clearLootWindow() {
		lootWindowUntilMillis = 0L;
		lootCollectionUntilMillis = 0L;
		pendingLootRunNumber = 0;
		pendingLootRunTimestamp = 0L;
		pendingLootFloor = DungeonFloor.UNKNOWN;
		pendingLootChestTitle = "";
		pendingLootChestCostCoins = 0L;
		pendingLootSeededFromGui = false;
		pendingLootEntries.clear();
		cachedChestDataByTitle.clear();
		scannedRewardScreens.clear();
	}

	private void handleLootMessage(String rawText, String cleaned, long now) {
		if ((lootWindowUntilMillis <= 0L || now > lootWindowUntilMillis) && isLootHeader(cleaned)) {
			startAdHocLootWindow(now);
		}
		if (lootWindowUntilMillis <= 0L || now > lootWindowUntilMillis) return;
		if (cleaned.startsWith("YOU RECEIVED ")) {
			lootCollectionUntilMillis = now + LOOT_COLLECTION_MS;
		} else if (isLootHeader(cleaned)) {
			if (!pendingLootEntries.isEmpty()) flushPendingLootRecord(true);
			assignOpenedChest(cleaned);
			lootCollectionUntilMillis = now + LOOT_COLLECTION_MS;
			return;
		}
		if (lootCollectionUntilMillis <= 0L || now > lootCollectionUntilMillis) return;
		DungeonLootEntry parsed = parseLootEntry(rawText, cleaned);
		if (parsed == null) return;
		mergePendingLootEntry(parsed);
		lootCollectionUntilMillis = now + LOOT_COLLECTION_MS;
	}

	private boolean isLootHeader(String cleaned) {
		return cleaned.contains("DUNGEON CHEST")
			|| cleaned.contains("CHEST REWARDS")
			|| cleaned.contains("REWARD CHEST");
	}

	private void assignOpenedChest(String cleaned) {
		for (String title : REWARD_CHEST_TITLES) {
			if (!cleaned.contains(title)) continue;
			pendingLootChestTitle = toDisplayChestTitle(title);
			CachedChestData cached = cachedChestDataByTitle.get(title);
			if (cached != null) pendingLootChestCostCoins = cached.costCoins;
			pendingLootSeededFromGui = false;
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
		return normalizedTitle.startsWith("CATACOMBS - FLOOR ") || normalizedTitle.startsWith("MASTER CATACOMBS - FLOOR ");
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
		long chestProfitCoins = chestValueCoins - pendingLootChestCostCoins;
		DungeonRunTracker.LOGGER.info("[DRT][FLUSH] entries={} value={} cost={} profit={} floor={} chest='{}'",
			pendingLootEntries.size(), chestValueCoins, pendingLootChestCostCoins, chestProfitCoins, pendingLootFloor, pendingLootChestTitle);
		String floorName = pendingLootFloor != DungeonFloor.UNKNOWN ? pendingLootFloor.name() : "UNKNOWN";
		String runGrade = pendingScoreGrade != null ? pendingScoreGrade : lastRecordedGrade;
		DrtConfigManager.addRunRecord(new DungeonRunRecord(
			pendingLootRunTimestamp,
			pendingLootRunNumber,
			floorName,
			runGrade,
			pendingLootChestTitle,
			pendingLootChestCostCoins,
			chestValueCoins,
			chestProfitCoins,
			pendingLootEntries
		));
		totalLifetimeProfit += chestProfitCoins;
		sessionTotalProfit += chestProfitCoins;
		floorProfitTotals.merge(floorName, chestProfitCoins, Long::sum);
		if (keepWindow) resetPendingChestState();
		else clearLootWindow();
	}

	private void resetPendingChestState() {
		lootCollectionUntilMillis = 0L;
		pendingLootChestTitle = "";
		pendingLootChestCostCoins = 0L;
		pendingLootSeededFromGui = false;
		pendingLootEntries.clear();
	}

	private String resolveItemId(String rawName) {
		String enchantedBookId = resolveEnchantedBookId(rawName);
		if (enchantedBookId != null) return enchantedBookId;

		String cleanedName = sanitizeLootName(rawName).toUpperCase(Locale.ROOT);
		String alias = ITEM_ID_ALIASES.get(cleanedName);
		if (alias != null) return alias;

		List<PriceCache.SearchResult> searchResults = PriceCache.search(cleanedName, 5);
		if (!searchResults.isEmpty()) return searchResults.getFirst().itemId();
		return "";
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
			|| normalized.contains("THE CATACOMBS");
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
			|| normalized.contains("KEY");
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
		aliases.put("WITHER CATALYST", "WITHER_CATALYST");
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
		return Map.copyOf(aliases);
	}

	private int clamp(int value, int min, int max) {
		return Math.max(min, Math.min(max, value));
	}
}
