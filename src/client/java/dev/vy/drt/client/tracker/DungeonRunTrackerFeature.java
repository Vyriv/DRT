package dev.vy.drt.client.tracker;

import dev.vy.drt.DungeonRunTracker;
import dev.vy.drt.config.ChestCostBreakdown;
import dev.vy.drt.client.overlay.OverlayColors;
import dev.vy.drt.client.overlay.OverlayFormat;
import dev.vy.drt.client.overlay.OverlayLayout;
import dev.vy.drt.client.overlay.OverlayLine;
import dev.vy.drt.client.overlay.OverlayLineClick;
import dev.vy.drt.client.overlay.OverlayLineHover;
import dev.vy.drt.client.overlay.OverlayLayouts;
import dev.vy.drt.client.overlay.OverlayPreset;
import dev.vy.drt.client.overlay.OverlaySegment;
import dev.vy.drt.client.overlay.OverlayStats;
import dev.vy.drt.config.DrtConfig;
import dev.vy.drt.config.DrtConfigManager;
import dev.vy.drt.config.DungeonChestOffer;
import dev.vy.drt.config.DungeonFloor;
import dev.vy.drt.config.DungeonLootEntry;
import dev.vy.drt.config.DungeonRunCompletionRecord;
import dev.vy.drt.config.DungeonRunRecord;
import dev.vy.drt.mixin.AbstractContainerScreenAccessor;
import dev.vy.drt.price.DungeonProfitPricing;
import dev.vy.drt.price.LootFloorGuards;
import dev.vy.drt.price.PriceCache;
import dev.vy.drt.tracking.DetectionEvent;
import dev.vy.drt.tracking.DetectionEventType;
import dev.vy.drt.tracking.DetectionSource;
import dev.vy.drt.tracking.DiagnosticIncident;
import dev.vy.drt.tracking.DiagnosticRecorder;
import dev.vy.drt.tracking.DiagnosticSeverity;
import dev.vy.drt.tracking.EvidenceStrength;
import dev.vy.drt.tracking.LootIdentityStrength;
import dev.vy.drt.tracking.LootObservation;
import dev.vy.drt.tracking.ChestSession;
import dev.vy.drt.tracking.ChestState;
import dev.vy.drt.tracking.ResolvedLoot;
import dev.vy.drt.tracking.RunMode;
import dev.vy.drt.tracking.RunSession;
import dev.vy.drt.tracking.RunState;
import dev.vy.drt.tracking.SlotOwner;
import dev.vy.drt.tracking.SyntheticDiagnosticIncidentFactory;
import dev.vy.drt.tracking.TrackingSession;
import dev.vy.drt.tracking.TrackerInvariant;
import java.awt.Desktop;
import java.awt.Toolkit;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.io.File;
import java.io.IOException;
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
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.Style;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
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
	private static final long LOOT_WINDOW_MS = 180_000L;
	private static final long LATE_LOOT_REATTACH_MS = 15 * 60_000L;
	private static final long LOOT_COLLECTION_MS = 3_000L;
	private static final long REWARD_MODIFIER_SCAN_INTERVAL_MS = 300L;
	private static final Pattern ESSENCE_PATTERN = Pattern.compile("^(?:\\+\\s*)?(WITHER|UNDEAD|SPIDER|DRAGON|ICE|DIAMOND|GOLD|CRIMSON) ESSENCE(?:\\s*[xX×]\\s*(\\d+))?$");
	private static final Pattern RECEIVED_PATTERN = Pattern.compile("^YOU RECEIVED\\s+(.+?)(?:\\s*[xX×]\\s*(\\d+))?!?$");
	private static final Pattern PLUS_PATTERN = Pattern.compile("^\\+\\s*(.+?)(?:\\s*[xX×]\\s*(\\d+))?$");
	private static final Pattern TRAILING_QUANTITY_PATTERN = Pattern.compile("^(.+?)\\s*[xX×]\\s*(\\d+)$");
	/** Chest loot rare line, e.g. "RARE REWARD! Recombobulator 3000" (not party announcements). */
	private static final Pattern RARE_REWARD_ITEM_PATTERN = Pattern.compile(
		"^(?:RARE REWARD|CRAZY RARE(?: REWARD)?|INSANE REWARD|PRAY RNGESUS)!?\\s+(.+)$",
		Pattern.CASE_INSENSITIVE);
	private static final Pattern COIN_PATTERN = Pattern.compile("([-+]?\\d[\\d,]*(?:\\.\\d+)?)\\s*([kmb])?(?:\\s*Coins)?", Pattern.CASE_INSENSITIVE);
	private static final Pattern QUANTITY_PREFIX_PATTERN = Pattern.compile("^(\\d+)x?\\s+(.+)$", Pattern.CASE_INSENSITIVE);
	private static final Pattern QUANTITY_SUFFIX_PATTERN = Pattern.compile("^(.+?)\\s+[xX×]\\s*(\\d+)$");
	private static final Pattern ENCHANTED_BOOK_PATTERN = Pattern.compile("^Enchanted Book \\((.+) ([IVX]+)\\)$", Pattern.CASE_INSENSITIVE);
	private static final Pattern BOSS_TIME_PATTERN = Pattern.compile("Defeated .+ in (\\d+)m\\s+(\\d+)s", Pattern.CASE_INSENSITIVE);
	private static final Pattern SHORT_FLOOR_PATTERN = Pattern.compile("(?:^|[^A-Z0-9])([FM])\\s*([1-7])(?:$|[^A-Z0-9])");
	private static final Pattern KUUDRA_SHORT_TIER_PATTERN = Pattern.compile("(?:^|[^A-Z0-9])(?:K|T)\\s*([1-5])(?:$|[^A-Z0-9])", Pattern.CASE_INSENSITIVE);
	private static final Pattern KUUDRA_TIER_WORD_PATTERN = Pattern.compile("\\bTIER\\s*(?:-|:)?\\s*(I{1,3}|IV|V|[1-5])\\b", Pattern.CASE_INSENSITIVE);
	private static final Pattern KUUDRA_PAREN_TIER_PATTERN = Pattern.compile("\\(\\s*[KT]\\s*([1-5])\\s*\\)", Pattern.CASE_INSENSITIVE);
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
	/** Longest / most specific names first so Infernal cannot lose to a weaker token. */
	private static final List<Map.Entry<String, DungeonFloor>> KUUDRA_TIER_NAMES = List.of(
			Map.entry("INFERNAL", DungeonFloor.K5),
			Map.entry("BURNING", DungeonFloor.K3),
			Map.entry("FIERY", DungeonFloor.K4),
			Map.entry("BASIC", DungeonFloor.K1),
			Map.entry("HOT", DungeonFloor.K2)
	);
	/** Paid-chest Cost lines name the key; longest first so "HOT KUUDRA KEY" beats bare "KUUDRA KEY". */
	private static final List<Map.Entry<String, DungeonFloor>> KUUDRA_KEY_NAMES = List.of(
			Map.entry("INFERNAL KUUDRA KEY", DungeonFloor.K5),
			Map.entry("FIERY KUUDRA KEY", DungeonFloor.K4),
			Map.entry("BURNING KUUDRA KEY", DungeonFloor.K3),
			Map.entry("HOT KUUDRA KEY", DungeonFloor.K2),
			Map.entry("BASIC KUUDRA KEY", DungeonFloor.K1),
			Map.entry("KUUDRA KEY", DungeonFloor.K1)
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
	private static final int DIAGNOSTIC_EVENT_LIMIT = 512;

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

	/** Compatibility cache for HUD/history displays; persisted completion records own new run commits. */
	private final Map<String, Integer> floorRunCounts = new LinkedHashMap<>();
	private final Map<String, Long> floorRunTimeMs = new LinkedHashMap<>();
	private final Map<String, Long> floorProfitTotals = new LinkedHashMap<>();
	private DungeonFloor selectedFloor = null;
	private long hudResetConfirmUntilMillis;
	private String hudResetConfirmFloorKey;
	private final DiagnosticRecorder diagnostics = new DiagnosticRecorder(
		new dev.vy.drt.tracking.SystemTrackerClock(),
		DIAGNOSTIC_EVENT_LIMIT,
		this::diagnosticEnvironment
	);
	private final TrackingSession trackingSession = new TrackingSession(
		"live",
		"client",
		new dev.vy.drt.tracking.SystemTrackerClock(),
		diagnostics
	);
	private final String clientTrackingInstanceId = "live-" + UUID.randomUUID().toString().substring(0, 8);

	private boolean enabled;
	private boolean trackingEnabled = true;
	private boolean croesusOverlayEnabled = true;
	private HudVisibilityMode hudVisibilityMode = HudVisibilityMode.DEFAULT;
	private OverlayPreset overlayPreset = OverlayPreset.LEGACY;
	private String customOverlayLayout = OverlayLayouts.DEFAULT_CUSTOM_LAYOUT;
	private int hudX = 10;
	private int hudY = 10;
	private float hudScale = 1.0F;

	private int refreshCountdown;
	private boolean insideDungeon;
	private boolean insideKuudra;
	private boolean inDungeonHub;
	private boolean inCrimsonIsle;
	/** Compatibility projection of the active RunSession floor. Do not assign without evidence acceptance. */
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
	private String activeRunSessionId = "";
	private String currentRunCompletionFingerprint = "";

	private long lootWindowUntilMillis;
	private long lootCollectionUntilMillis;
	private int pendingLootRunNumber;
	private long pendingLootRunTimestamp;
	private long chestSessionSequence;
	/** Compatibility pointer to the active ChestSession ID, or a pre-open dedup placeholder. */
	private String pendingChestSessionId = "";
	private String pendingLootChestTitle = "";
	private ChestCostBreakdown pendingLootCostBreakdown = new ChestCostBreakdown();
	private DungeonFloor pendingLootFloor = DungeonFloor.UNKNOWN;
	private boolean pendingLootOrphaned;
	private boolean pendingLootSeededFromGui;
	private boolean pendingLootReconcilingGuiChat;
	private boolean pendingLootChestAssigned;
	/** Incident id waiting for a non-null player so chat notify can be delivered. */
	private String pendingIncidentChatNotifyId = "";
	private int openedRewardChestsInLootWindow;
	/** Canonical titles already counted in the current loot window (used to ignore Croesus re-views). */
	private final Set<String> openedRewardChestTitlesInLootWindow = new HashSet<>();
	private boolean nextOpenedChestUsesDungeonChestKey;
	private boolean nextOpenedChestUsesKismetFeather;
	private boolean nextOpenedChestUsesWheelOfFate;
	private boolean rewardMenuKismetRerollPending;
	private String rewardMenuKismetRerolledChestTitle = "";
	/** Compatibility overlay buffer; ChestSession LootObservations/ResolvedLoot are authoritative at commit. */
	private final List<DungeonLootEntry> pendingLootEntries = new ArrayList<>();
	private final Set<String> pendingChestLootDedupKeys = new HashSet<>();
	private final Set<String> ignoredPlayerInventoryDiagnosticKeys = new HashSet<>();
	private final Map<String, DungeonChestOffer> cachedChestOffersByTitle = new HashMap<>();
	private final Map<String, Integer> cachedChestOfferFingerprintsByTitle = new HashMap<>();
	private final Set<String> scannedRewardScreens = new HashSet<>();
	private String lastRewardModifierScanKey = "";
	private long lastRewardModifierScanMillis;
	private boolean lastRewardModifierScanHadKeyRequirement;
	private boolean lastRewardModifierScanHadKismetMarker;
	private String lastViewedOpenedRewardChestTitle = "";
	/** Survives GUI close so CHEST REWARDS chat without a tier name can still assign. */
	private String lastOpenedRewardChestTitleForChat = "";
	/** Set while viewing a Croesus preview with an Open button; consumed on the subsequent open. */
	private String armedPreviewRewardChestTitle = "";

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
	private final Set<String> lootGuardWarnedKeys = new HashSet<>();
	private boolean positionDirty;
	private int dragOffsetX;
	private int dragOffsetY;

	public void applyConfig(DrtConfig config) {
		enabled = config.enabled;
		trackingEnabled = config.trackingEnabled;
		croesusOverlayEnabled = config.croesusOverlayEnabled;
		hudVisibilityMode = HudVisibilityMode.fromConfig(config.hudVisibilityMode);
		OverlayPreset loadedPreset = OverlayPreset.fromConfig(config.hudOverlayPreset);
		overlayPreset = loadedPreset == null ? OverlayPreset.LEGACY : loadedPreset;
		customOverlayLayout = config.customOverlayLayout == null || config.customOverlayLayout.isBlank()
				? OverlayLayouts.DEFAULT_CUSTOM_LAYOUT
				: config.customOverlayLayout;
		hudX = Math.max(0, config.hudX);
		hudY = Math.max(0, config.hudY);
		hudScale = clampHudScale(config.hudScale);
		String savedFloor = config.selectedFloor;
		if (savedFloor != null && !savedFloor.isBlank()) {
			try { selectedFloor = DungeonFloor.valueOf(savedFloor); } catch (IllegalArgumentException ignored) { selectedFloor = null; }
		} else {
			selectedFloor = null;
		}
		rebuildLifetimeMaps(config);
	}

	/** Re-derives lifetime overlay maps from persisted config (run history + floor counts/times). */
	public synchronized void resyncLifetimeFromConfig() {
		rebuildLifetimeMaps(DrtConfigManager.getConfig());
	}

	public synchronized void notifyRunRemoved(DungeonRunRecord removed) {
		resyncLifetimeFromConfig();
		applySessionRemoval(removed);
	}

	public synchronized void notifyHistoryCleared(List<DungeonRunRecord> removed) {
		resyncLifetimeFromConfig();
		if (removed == null) return;
		for (DungeonRunRecord record : removed) {
			applySessionRemoval(record);
		}
	}

	public synchronized void notifyRunUpdated(DungeonRunRecord before, DungeonRunRecord after) {
		resyncLifetimeFromConfig();
		if (!sessionActive || before == null || after == null || sessionStartMillis <= 0L) return;
		if (before.timestampEpochMillis < sessionStartMillis) return;
		String oldKey = before.floor == null ? "UNKNOWN" : before.floor;
		String newKey = after.floor == null ? "UNKNOWN" : after.floor;
		long profitDelta = after.chestProfitCoins - before.chestProfitCoins;
		sessionTotalProfit += profitDelta;
		if (oldKey.equals(newKey)) {
			sessionFloorProfitTotals.merge(oldKey, profitDelta, Long::sum);
		} else {
			sessionFloorProfitTotals.merge(oldKey, -before.chestProfitCoins, Long::sum);
			sessionFloorProfitTotals.merge(newKey, after.chestProfitCoins, Long::sum);
			adjustSessionFloorRun(oldKey, -1);
			adjustSessionFloorRun(newKey, 1);
		}
		String oldGrade = before.grade == null || before.grade.isBlank() ? "S+" : before.grade;
		String newGrade = after.grade == null || after.grade.isBlank() ? "S+" : after.grade;
		if (!oldGrade.equals(newGrade)) {
			adjustSessionGrade(oldGrade, -1);
			adjustSessionGrade(newGrade, 1);
		}
	}

	private void rebuildLifetimeMaps(DrtConfig config) {
		if (config == null) config = new DrtConfig();
		floorRunCounts.clear();
		if (config.floorRunCounts != null) {
			config.floorRunCounts.forEach((k, v) -> {
				if (k != null && v != null) floorRunCounts.put(k, Math.max(0, v));
			});
		}
		if (config.runCompletions != null) {
			LinkedHashMap<String, Integer> fromCompletions = new LinkedHashMap<>();
			for (DungeonRunCompletionRecord record : config.runCompletions) {
				if (record == null) continue;
				String key = record.floor == null || record.floor.isBlank() ? "UNKNOWN" : record.floor;
				fromCompletions.merge(key, 1, Integer::sum);
			}
			fromCompletions.forEach((key, count) -> {
				if (floorRunCounts.getOrDefault(key, 0) < count) floorRunCounts.put(key, count);
			});
		}
		if (config.legacyRunsCompleted > 0 && !floorRunCounts.containsKey("M5")) {
			floorRunCounts.put("M5", config.legacyRunsCompleted);
		}
		floorRunTimeMs.clear();
		if (config.floorRunTimeMs != null) {
			config.floorRunTimeMs.forEach((k, v) -> {
				if (k != null && v != null) floorRunTimeMs.put(k, Math.max(0L, v));
			});
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

	private void applySessionRemoval(DungeonRunRecord removed) {
		if (!sessionActive || removed == null || sessionStartMillis <= 0L) return;
		if (removed.timestampEpochMillis < sessionStartMillis) return;
		String key = removed.floor == null ? "UNKNOWN" : removed.floor;
		sessionTotalProfit -= removed.chestProfitCoins;
		sessionFloorProfitTotals.merge(key, -removed.chestProfitCoins, Long::sum);
		sessionRuns = Math.max(0, sessionRuns - 1);
		adjustSessionFloorRun(key, -1);
		String g = removed.grade == null || removed.grade.isBlank() ? "S+" : removed.grade;
		adjustSessionGrade(g, -1);
	}

	private void adjustSessionFloorRun(String key, int delta) {
		if (key == null || delta == 0) return;
		sessionFloorRuns.merge(key, delta, (a, b) -> Math.max(0, a + b));
		if (sessionFloorRuns.getOrDefault(key, 0) == 0) sessionFloorRuns.remove(key);
	}

	private void adjustSessionGrade(String grade, int delta) {
		if (grade == null || delta == 0) return;
		sessionGradeRuns.merge(grade, delta, (a, b) -> Math.max(0, a + b));
		if (sessionGradeRuns.getOrDefault(grade, 0) == 0) sessionGradeRuns.remove(grade);
	}

	public void tick(Minecraft client) {
		if (client.player == null || client.level == null) {
			pauseCurrentRunForUnavailable(System.currentTimeMillis());
			clearRuntimeState();
			leftMouseDownLastTick = false;
			return;
		}

		long now = System.currentTimeMillis();
		flushPendingIncidentChatNotify();
		handleDragging(client);
		if (!trackingEnabled) {
			pauseCurrentRunForUnavailable(now);
			return;
		}
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
	}

	public void handleChatMessage(Component message) {
		if (!trackingEnabled || message == null) return;
		if (!markMessageForProcessing(message)) return;
		submitMessage(message, false);
	}

	public void handleRawSystemMessage(Component message) {
		if (!trackingEnabled || message == null) return;
		if (!markMessageForProcessing(message)) return;
		submitMessage(message, true);
	}

	public void handleGameMessage(Component message, boolean overlay) {
		if (!trackingEnabled || overlay) return;
		if (message == null) return;
		if (!markMessageForProcessing(message)) return;
		submitMessage(message, true);
	}

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
		OverlayLayout layout = buildHudLayout(client, showResetLine);

		Matrix3x2fStack pose = guiGraphics.pose();
		pose.pushMatrix();
		try {
			pose.translate(hudX, hudY);
			pose.scale(hudScale);
			for (OverlayLine line : layout.lines) {
				int x = 0;
				for (OverlaySegment segment : line.segments) {
					int drawX = segment.positioned() ? segment.x : x;
					seg(client, guiGraphics, segment.text, drawX, line.y, segment.color);
					x = drawX + client.font.width(segment.text);
				}
			}
		} finally {
			pose.popMatrix();
		}

		if (client.screen != null && !moveMode) {
			OverlayLayout.HitResult hit = hitTestHud(client, mouseX, mouseY, shouldShowResetLine(client, false));
			if (hit.hit()) {
				switch (hit.hover()) {
					case MODE -> drawTooltip(client, guiGraphics, "Mode: " + hudVisibilityMode.displayName, mouseX, mouseY);
					case FLOOR -> drawTooltip(client, guiGraphics, "Left/right click to cycle floors", mouseX, mouseY);
					case RUNS_HR -> drawTooltip(client, guiGraphics, runsPerHrPaused ? "Click to resume /hr timer" : "Click to pause /hr timer", mouseX, mouseY);
					case RUNS -> drawTooltip(client, guiGraphics, buildRunsHoverTooltip(), mouseX, mouseY);
					case PROFIT -> drawTooltip(client, guiGraphics, buildProfitHoverTooltip(), mouseX, mouseY);
					case RESET -> {
						String floorTag = selectedFloor == null ? "All" : selectedFloor.name();
						String tip = hudResetConfirmArmed(System.currentTimeMillis())
							? "Click again to permanently wipe " + floorTag
							: "Click to reset " + floorTag + " tracker";
						drawTooltip(client, guiGraphics, tip, mouseX, mouseY);
					}
					case NONE -> { }
				}
			}
		}
	}

	public void extractChestOverlayRenderState(Minecraft client, GuiGraphicsExtractor guiGraphics, int mouseX, int mouseY) {
		if (croesusOverlayEnabled) {
			if (extractRenderStateCroesusChestOverlay(client, guiGraphics, mouseX, mouseY)) return;
			extractRenderStateCroesusMainMenuHighlights(client, guiGraphics);
		}
		extractRenderStateChestBreakdownOverlay(client, guiGraphics, mouseX, mouseY);
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

	private List<String> buildRunsHoverTooltip() {
		if (overlayPreset == OverlayPreset.LEGACY) return buildRunsTooltip();
		OverlayStats stats = currentOverlayStats();
		List<String> lines = new ArrayList<>();
		lines.add("Total runs: " + stats.totalRuns);
		lines.add("S+: " + stats.sPlusCount);
		lines.add("S: " + stats.sCount);
		return lines;
	}

	private List<String> buildProfitHoverTooltip() {
		if (overlayPreset == OverlayPreset.LEGACY) {
			return List.of("Click to view " + (selectedFloor == null ? "all" : selectedFloor.name()) + " loot history");
		}
		OverlayStats stats = currentOverlayStats();
		List<String> lines = new ArrayList<>();
		lines.add("Total profit: " + OverlayFormat.coins(stats.totalProfit));
		if (stats.totalRuns > 0) {
			lines.add("Total profit per run: " + OverlayFormat.coins(stats.lifetimeProfitPerRun));
		}
		lines.add("Click to view " + (selectedFloor == null ? "all" : selectedFloor.name()) + " loot history");
		return lines;
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

	public boolean isTrackingEnabled() {
		return trackingEnabled;
	}

	public void setTrackingEnabled(boolean trackingEnabled) {
		this.trackingEnabled = trackingEnabled;
		if (!trackingEnabled) pauseCurrentRunForUnavailable(System.currentTimeMillis());
		DrtConfigManager.getConfig().trackingEnabled = trackingEnabled;
		DrtConfigManager.save();
	}

	public boolean toggleTracking() {
		setTrackingEnabled(!trackingEnabled);
		return trackingEnabled;
	}

	public boolean isCroesusOverlayEnabled() {
		return croesusOverlayEnabled;
	}

	public void setCroesusOverlayEnabled(boolean croesusOverlayEnabled) {
		this.croesusOverlayEnabled = croesusOverlayEnabled;
		DrtConfigManager.getConfig().croesusOverlayEnabled = croesusOverlayEnabled;
		DrtConfigManager.save();
	}

	public boolean toggleCroesusOverlay() {
		setCroesusOverlayEnabled(!croesusOverlayEnabled);
		return croesusOverlayEnabled;
	}

	public boolean toggleEssenceCountsTowardProfit() {
		DrtConfig config = DrtConfigManager.getConfig();
		config.essenceCountsTowardProfit = !config.essenceCountsTowardProfit;
		DrtConfigManager.save();
		return config.essenceCountsTowardProfit;
	}

	public OverlayPreset getOverlayPreset() {
		return overlayPreset;
	}

	public void setOverlayPreset(OverlayPreset preset) {
		if (preset == null) return;
		overlayPreset = preset;
		DrtConfigManager.updateHudOverlayPreset(preset.name());
	}

	public String getCustomOverlayLayout() {
		return customOverlayLayout;
	}

	public void setCustomOverlayLayout(String layout, boolean activateCustom) {
		customOverlayLayout = layout == null || layout.isBlank()
				? OverlayLayouts.DEFAULT_CUSTOM_LAYOUT
				: layout.replace("\r\n", "\n").replace('\r', '\n');
		if (activateCustom) {
			overlayPreset = OverlayPreset.CUSTOM;
			DrtConfigManager.updateOverlaySettings(OverlayPreset.CUSTOM.name(), customOverlayLayout);
		} else {
			DrtConfigManager.updateCustomOverlayLayout(customOverlayLayout);
		}
	}

	public void resetCustomOverlayLayout() {
		setCustomOverlayLayout(OverlayLayouts.DEFAULT_CUSTOM_LAYOUT, overlayPreset == OverlayPreset.CUSTOM);
	}

	public OverlayStats currentOverlayStats() {
		String floorTag = selectedFloor == null ? "All" : selectedFloor.name();
		long lifetimeProfit = selectedFloor == null
				? totalLifetimeProfit
				: floorProfitTotals.getOrDefault(selectedFloor.name(), 0L);
		int sessionRunCount = displaySessionRuns();
		long sessionProfit = displaySessionProfit();
		long sessionRunTimeMs = displaySessionRunTimeMs();
		long totalRunTimeMs = displayTotalRunTimeMs();
		long inRunElapsedMs = activeInRunElapsedMs();
		double hoursElapsed = inRunElapsedMs / 3_600_000.0;
		long profitPerHr = hoursElapsed > 0.001 ? (long) (sessionProfit / hoursElapsed) : 0L;
		double runsPerHr = hoursElapsed > 0.001 ? sessionRunCount / hoursElapsed : 0.0;
		long avgRunTimeMs = sessionRunCount > 0 ? sessionRunTimeMs / sessionRunCount : 0L;
		long avgProfitPerRun = sessionRunCount > 0 ? sessionProfit / sessionRunCount : 0L;
		int totalRuns = selectedFloor == null
				? totalRunsCompleted()
				: floorRunCounts.getOrDefault(selectedFloor.name(), 0);
		return new OverlayStats(
				floorTag,
				totalRuns,
				sessionRunCount,
				avgRunTimeMs,
				sessionRunTimeMs,
				totalRunTimeMs,
				runsPerHr,
				gradeRunCounts.getOrDefault("S", 0),
				gradeRunCounts.getOrDefault("S+", 0),
				lifetimeProfit,
				sessionProfit,
				avgProfitPerRun,
				profitPerHr,
				runsPerHrPaused,
				hudResetLabel(floorTag)
		);
	}

	public OverlayLayout buildHudLayout(Minecraft client, boolean includeResetLine) {
		return OverlayLayouts.build(
				overlayPreset,
				currentOverlayStats(),
				text -> client.font.width(text),
				client.font.lineHeight,
				customOverlayLayout,
				includeResetLine
		);
	}

	public OverlayLayout buildPreviewLayout(Minecraft client, OverlayPreset preset, String customText) {
		OverlayPreset safe = preset == null ? OverlayPreset.LEGACY : preset;
		return OverlayLayouts.build(
				safe,
				OverlayStats.previewSample(),
				text -> client.font.width(text),
				client.font.lineHeight,
				customText == null ? customOverlayLayout : customText,
				false
		);
	}

	private OverlayLayout.HitResult hitTestHud(Minecraft client, int mouseX, int mouseY, boolean includeResetLine) {
		if (client == null) return OverlayLayout.HitResult.miss();
		double localX = toHudLocalX(mouseX);
		double localY = toHudLocalY(mouseY);
		OverlayLayout layout = buildHudLayout(client, includeResetLine);
		int width = layout.width;
		int height = layout.height;
		if (localX < -3 || localX > width + 3 || localY < -2 || localY > height + 2) {
			return OverlayLayout.HitResult.miss();
		}
		return layout.hitTest(text -> client.font.width(text), localX, localY);
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

	private long displayTotalRunTimeMs() {
		if (selectedFloor != null) {
			return floorRunTimeMs.getOrDefault(selectedFloor.name(), 0L);
		}
		long total = 0L;
		for (long value : floorRunTimeMs.values()) total += Math.max(0L, value);
		return total;
	}

	private void accumulateLifetimeRunTime(String floorKey, long runTimeMs) {
		if (runTimeMs <= 0L || floorKey == null || floorKey.isBlank()) return;
		long next = floorRunTimeMs.merge(floorKey, runTimeMs, Long::sum);
		DrtConfigManager.updateFloorRunTimeMs(floorKey, next);
	}

	private String hudResetLabel(String floorTag) {
		if (hudResetConfirmArmed(System.currentTimeMillis())) {
			return "Confirm reset " + floorTag + "?";
		}
		return "Reset " + floorTag;
	}

	private boolean hudResetConfirmArmed(long now) {
		if (now > hudResetConfirmUntilMillis) return false;
		String currentKey = selectedFloor == null ? null : selectedFloor.name();
		if (hudResetConfirmFloorKey == null) return currentKey == null;
		return hudResetConfirmFloorKey.equals(currentKey);
	}

	private void clearHudResetConfirm() {
		hudResetConfirmUntilMillis = 0L;
		hudResetConfirmFloorKey = null;
	}

	private void resetSelectedFloor() {
		long now = System.currentTimeMillis();
		if (!hudResetConfirmArmed(now)) {
			hudResetConfirmUntilMillis = now + 3_000L;
			hudResetConfirmFloorKey = selectedFloor == null ? null : selectedFloor.name();
			return;
		}
		clearHudResetConfirm();
		if (selectedFloor == null) {
			floorRunCounts.clear();
			floorRunTimeMs.clear();
			totalLifetimeProfit = 0L;
			floorProfitTotals.clear();
			gradeRunCounts.clear();
			clearSession();
			DrtConfigManager.clearAllData();
		} else {
			String key = selectedFloor.name();
			totalLifetimeProfit -= floorProfitTotals.getOrDefault(key, 0L);
			floorRunCounts.remove(key);
			floorRunTimeMs.remove(key);
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
		clearHudResetConfirm();
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
		return buildHudLayout(client, includeResetLine).width;
	}

	private int getBaseDisplayHeight(Minecraft client, boolean includeResetLine) {
		return buildHudLayout(client, includeResetLine).height;
	}

	private boolean handleOverlayClick(OverlayLayout.HitResult hit) {
		if (hit == null || !hit.hit()) return false;
		OverlayLineClick click = hit.click();
		if (click == OverlayLineClick.NONE && hit.hover() == OverlayLineHover.FLOOR) {
			click = OverlayLineClick.CYCLE_FLOOR;
		}
		switch (click) {
			case CYCLE_MODE -> { cycleHudVisibilityMode(); return true; }
			case CYCLE_FLOOR -> { cycleSelectedFloor(); return true; }
			case TOGGLE_RUNS_HR -> { toggleRunsPerHrPause(); return true; }
			case OPEN_LOOT -> { requestOpenLootScreen(selectedFloor, null); return true; }
			case RESET -> { resetSelectedFloor(); return true; }
			case NONE -> { return false; }
		}
		return false;
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
		OverlayLayout.HitResult hit = hitTestHud(client, mx, my, showResetLine);
		if (!hit.hit()) return false;
		if (button == 1) {
			if (hit.click() == OverlayLineClick.CYCLE_FLOOR || hit.hover() == OverlayLineHover.FLOOR) {
				cycleSelectedFloorBackward();
				return true;
			}
			return false;
		}
		return handleOverlayClick(hit);
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
		if (!croesusOverlayEnabled) return false;
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
			if (!isServerOwnedSlot(menu.getSlot(slotIndex))) continue;
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
			if (shouldBeginNewDungeonRunFromEntry(now)) {
				beginNewDungeonRun(now);
			}
			insideDungeon = true;
		}

		Matcher bossTimeMatcher = BOSS_TIME_PATTERN.matcher(rawLine == null ? cleaned : rawLine);
		if (bossTimeMatcher.find()) {
			long minutes = parsePositiveInt(bossTimeMatcher.group(1), 0);
			long seconds = parsePositiveInt(bossTimeMatcher.group(2), 0);
			currentRunBossTimeMs = (minutes * 60L + seconds) * 1000L;
		}

		DungeonFloor lineFloor = detectFloorFromLine(cleaned);
		if (lineFloor != DungeonFloor.UNKNOWN) {
			updateActiveRunFloorProjection(lineFloor, EvidenceStrength.STRUCTURED_CHAT, DetectionSource.STRUCTURED_CHAT);
		}

		String preGrade = extractScoreGrade(cleaned);
		if (preGrade != null && pendingScoreGrade == null) {
			pendingScoreGrade = preGrade;
			trackingSession.updateActiveRunGrade(preGrade, EvidenceStrength.STRUCTURED_CHAT, DetectionSource.STRUCTURED_CHAT);
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
				DungeonFloor projectedFloor = activeRunFloorProjection();
				DungeonFloor contextFloor = projectedFloor != DungeonFloor.UNKNOWN ? projectedFloor : pendingSPlusFloor;
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
					DungeonFloor scoreFloor = awaitingExtraStatsFloor != DungeonFloor.UNKNOWN ? awaitingExtraStatsFloor : activeRunFloorProjection();
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
		// Infer from the chests actually in this menu. Sticky Kuudra state after dungeon->kuudra->dungeon
		// used to force FREE/PAID filtering and hide all Catacombs chests in Croesus.
		boolean sawDungeonChest = false;
		boolean sawKuudraChest = false;
		Map<String, ItemStack> stacksByTitle = new LinkedHashMap<>();
		Map<String, Integer> slotsByTitle = new HashMap<>();
		ScreenBounds bounds = currentContainerBounds(client);
		for (int slotIndex = 0; slotIndex < menu.slots.size(); slotIndex++) {
			Slot slot = menu.slots.get(slotIndex);
			if (!isServerOwnedSlot(slot)) {
				diagnoseRejectedPlayerInventoryStack(slot, "currentCroesusChestRows");
				continue;
			}
			ItemStack stack = slot.getItem();
			if (stack.isEmpty()) continue;
			String canonicalKey = canonicalChestTitleFromStack(stack);
			if (canonicalKey == null) continue;
			if (canonicalKey.equals("FREE CHEST") || canonicalKey.equals("PAID CHEST")) sawKuudraChest = true;
			if (isCatacombsRewardChest(canonicalKey)) sawDungeonChest = true;
			stacksByTitle.putIfAbsent(canonicalKey, stack.copy());
			slotsByTitle.putIfAbsent(canonicalKey, slotIndex);
		}
		boolean kuudraContext = sawKuudraChest && !sawDungeonChest;
		if (!sawDungeonChest && !sawKuudraChest) {
			kuudraContext = isKuudraRewardContext(normalizedTitle);
		}

		Map<String, CroesusChestRow> byTitle = new HashMap<>();
		for (Map.Entry<String, ItemStack> entry : stacksByTitle.entrySet()) {
			String canonicalKey = entry.getKey();
			if (!isTrackedRewardChest(canonicalKey, kuudraContext)) continue;
			ItemStack stack = entry.getValue();
			int slotIndex = slotsByTitle.getOrDefault(canonicalKey, 0);
			Slot slot = slotIndex >= 0 && slotIndex < menu.slots.size() ? menu.slots.get(slotIndex) : null;

			CHEST_ICON_CACHE.putIfAbsent(canonicalKey, stack.copy());
			DungeonChestOffer offer = cachedChestOffersByTitle.get(canonicalKey);
			if (offer == null) {
				offer = parseChestOffer(canonicalKey, stack);
				cachedChestOffersByTitle.put(canonicalKey, offer);
				cachedChestOfferFingerprintsByTitle.put(canonicalKey, chestOfferFingerprint(stack));
			} else {
				adoptKuudraKeyTier(detectKuudraKeyTierFromLore(cleanLoreLines(stack)));
			}
			List<DungeonLootEntry> entries = offer.lootEntries == null ? List.of() : offer.lootEntries;
			long value = offerValueCoins(canonicalKey, entries, offer.valueCoins);
			ChestCostBreakdown breakdown = offer.costBreakdown == null ? new ChestCostBreakdown() : offer.costBreakdown.copy();
			if (breakdown.usedKuudraKey) breakdown.kuudraKeyCostCoins = 0L;
			applyKuudraChestCostHint(canonicalKey, breakdown, cleanLoreLines(stack));
			populateKnownModifierCosts(breakdown);
			suppressDungeonChestKeyForKuudra(canonicalKey, breakdown);
			long keyCost = isCatacombsRewardChest(canonicalKey) ? resolveModifierItemCost(ITEM_DUNGEON_CHEST_KEY) : 0L;
			boolean kismetRerolled = breakdown.usedKismetFeather;
			String display = shortChestName(toDisplayChestTitle(canonicalKey));
			long normalProfit = value - breakdown.totalCostCoins();
			int slotX = slot == null ? bounds.left : bounds.left + slot.x;
			int slotY = slot == null ? bounds.top : bounds.top + slot.y;
			byTitle.put(canonicalKey, new CroesusChestRow(
					canonicalKey,
					display,
					stack.copy(),
					slotIndex,
					slotX,
					slotY,
					normalProfit,
					keyCost > 0L ? normalProfit - keyCost : Long.MIN_VALUE,
					offer.alreadyOpened,
					kismetRerolled
			));
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
			if (!isServerOwnedSlot(slot)) continue;
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

		// Paid-chest Cost lines name the key; use that over sticky Hot/K2 floor for open cost.
		rememberKuudraKeyTierFromMenu(client.player == null ? null : client.player.containerMenu);

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
				// Do not copy a stale Hot-key price; re-resolve from the best known tier below.
				breakdown.kuudraKeyCostCoins = 0L;
			}
		} else if (isCatacombsRewardChest(canonicalTitle) && shouldPreviewArmedKismetForChest(canonicalTitle)) {
			applyArmedRewardModifiersForPreview(breakdown);
		}
		applyKuudraChestCostHint(canonicalTitle, breakdown, null);
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
		DrtConfig config = DrtConfigManager.getConfig();
		long calculated = DungeonProfitPricing.calculateLootValue(entries == null ? List.of() : entries, config);
		if (isKuudraRewardChest(canonicalTitle) && calculated > 0L) return calculated;
		// Lore Value includes essence; when essence is excluded, prefer our calculated total.
		if (config != null && !config.essenceCountsTowardProfit && calculated > 0L) return calculated;
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

		boolean entry = isKuudraEntryMessage(cleaned);
		boolean completion = isKuudraCompletionMessage(cleaned);
		boolean strongLine = entry || completion || hasStrongKuudraTierSignal(cleaned);
		DungeonFloor lineTier = detectKuudraTierFromLine(cleaned, strongLine || insideKuudra || isCurrentFloorKuudra());

		// Never let arbitrary mid-run chat overwrite a known Infernal/T5 with a weak false positive.
		if (strongLine && lineTier.isKuudra()) {
			rememberKuudraTier(lineTier, true);
		}

		if (entry) {
			beginNewKuudraRun(now, bestKuudraTier(lineTier));
			return true;
		}

		if (!completion) return false;

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
		return activeRunFloorProjection();
	}

	private DungeonFloor activeRunFloorProjection() {
		RunSession activeRun = trackingSession.activeRun();
		if (activeRun != null && activeRun.floor().isKnown()) {
			DungeonFloor floor = activeRun.floor().value();
			return floor == null ? DungeonFloor.UNKNOWN : floor;
		}
		return currentFloor == null ? DungeonFloor.UNKNOWN : currentFloor;
	}

	private boolean updateActiveRunFloorProjection(DungeonFloor floor, EvidenceStrength strength, DetectionSource source) {
		if (floor == null || floor == DungeonFloor.UNKNOWN) return false;
		boolean accepted = trackingSession.updateActiveRunFloor(floor, strength, source);
		if (!accepted) return false;
		DungeonFloor projected = activeRunFloorProjection();
		if (projected != DungeonFloor.UNKNOWN) {
			currentFloor = projected;
			if (projected.isKuudra()) lastKnownKuudraFloor = projected;
		}
		return true;
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
		updateKuudraReputationFromLines(tabLines, scoreboardLines);
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
			// Leaving Kuudra/hub entirely: drop sticky tier so the next Hollow visit cannot
			// inherit a previous Hot (K2) run when Infernal detection is briefly unavailable.
			if (wasKuudra || (lastKnownKuudraFloor != null && lastKnownKuudraFloor.isKuudra())) {
				if (!currentRunActive) lastKnownKuudraFloor = DungeonFloor.UNKNOWN;
			}
			resetDungeonRuntimeState();
			return;
		}

		if (insideDungeon) {
			// Returning to dungeons after Kuudra must clear sticky Kuudra UI/pricing state.
			if (wasKuudra || (lastKnownKuudraFloor != null && lastKnownKuudraFloor.isKuudra())) {
				lastKnownKuudraFloor = DungeonFloor.UNKNOWN;
				insideKuudra = false;
				kuudraSignalUntilMillis = 0L;
			}
			DungeonFloor detected = detectFloorFromLines(scoreboardLines);
			DetectionSource floorSource = DetectionSource.CONFIRMED_SCOREBOARD;
			EvidenceStrength floorStrength = EvidenceStrength.CONFIRMED_SCOREBOARD;
			if (detected == DungeonFloor.UNKNOWN) {
				detected = detectFloorFromLines(tabLines);
				floorSource = DetectionSource.CONFIRMED_TAB;
				floorStrength = EvidenceStrength.CONFIRMED_TAB;
			}
			if (detected != DungeonFloor.UNKNOWN && detected != activeRunFloorProjection()) {
				updateActiveRunFloorProjection(detected, floorStrength, floorSource);
			}
		}

		if (insideKuudra && !insideDungeon) {
			if (detectedKuudraTier.isKuudra()) {
				rememberKuudraTier(detectedKuudraTier, true);
			}
			if (!wasKuudra && !currentRunActive && !runCountedThisDungeon) {
				beginNewKuudraRun(now, bestKuudraTier(detectedKuudraTier));
			}
		}
	}

	private void captureRewardChestCosts(Minecraft client) {
		if (!(client.screen instanceof AbstractContainerScreen<?> screen) || client.player == null) return;

		String normalizedTitle = normalize(screen.getTitle().getString());
		String canonicalRewardTitle = canonicalRewardChestTitle(normalizedTitle);
		DungeonFloor menuTitleFloor = rewardContextFloorFromTitle(normalizedTitle);
		long now = System.currentTimeMillis();
		rememberRunContextFromMenuTitle(normalizedTitle, now);

		if (canonicalRewardTitle != null) {
			if (lootWindowUntilMillis <= 0L || now > lootWindowUntilMillis) {
				startLateOwnedOrAdHocLootWindow(now, menuTitleFloor);
				if (menuTitleFloor != DungeonFloor.UNKNOWN) {
					pendingLootFloor = menuTitleFloor;
					updatePendingChestContextProjection(EvidenceStrength.GUI_TITLE_INFERENCE, DetectionSource.GUI_TITLE_INFERENCE);
				}
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
				// Arm so the paid open counts even if this tier was already opened earlier in Croesus
				// (offers are cached by title, so a second Wood from another run looks "Already Opened").
				armedPreviewRewardChestTitle = canonicalRewardTitle;
				// Keep key-requirement from this preview so the subsequent open can bill a key.
				lastViewedOpenedRewardChestTitle = "";
				return;
			}

			DungeonChestOffer cached = cachedChestOffersByTitle.get(canonicalRewardTitle);
			boolean openedFromArmedPreview = canonicalRewardTitle.equals(armedPreviewRewardChestTitle);
			if (openedFromArmedPreview) armedPreviewRewardChestTitle = "";
			// Skip pure re-views of an already-counted tier. Never skip a preview→Open sequence —
			// multi-run Croesus reuses titles (two Wood chests) and the title-keyed cache stays opened.
			if (!openedFromArmedPreview
				&& cached != null
				&& cached.alreadyOpened
				&& openedRewardChestTitlesInLootWindow.contains(canonicalRewardTitle)) {
				updateCachedRewardChestOffers(menu);
				lastViewedOpenedRewardChestTitle = "";
				return;
			}

			String screenKey = "opened#" + canonicalRewardTitle + "#" + menu.containerId;
			boolean firstOpenScan = scannedRewardScreens.add(screenKey);
			// Carry forward key requirement observed on the preview "Open Reward Chest" button.
			boolean paidWithKey = nextOpenedChestUsesDungeonChestKey || lastRewardModifierScanHadKeyRequirement;
			refreshRewardModifierScan(menu, screenKey, now);
			rememberKuudraKeyTierFromMenu(menu);
			if (lastRewardModifierScanHadKismetMarker) markKismetFeatherUsed();
			if (firstOpenScan || openedFromArmedPreview) {
				assignPendingOpenedChest(canonicalRewardTitle, cached, paidWithKey, openedFromArmedPreview, menu.containerId);
			}
			captureOpenedRewardChestLoot(client, menu, now);
			lastViewedOpenedRewardChestTitle = canonicalRewardTitle;
			lastOpenedRewardChestTitleForChat = canonicalRewardTitle;
			return;
		}

		// Leaving an opened chest GUI: do NOT flush yet. Chat CHEST REWARDS often arrives next;
		// flushing here was splitting one open into a partial GUI record + a second chat record.
		if (!lastViewedOpenedRewardChestTitle.isBlank()) {
			String leftTitle = lastViewedOpenedRewardChestTitle;
			lastViewedOpenedRewardChestTitle = "";
			lastOpenedRewardChestTitleForChat = leftTitle;
			// Allow a later real open of the same tier (new container id often reuses) to scan again.
			scannedRewardScreens.removeIf(key -> key.startsWith("opened#" + leftTitle + "#"));
		}

		if (!isRewardsMenuTitle(normalizedTitle) && !isCroesusChestListTitle(normalizedTitle)) return;

		// Back on the chest list — drop any unused preview arm (backed out without opening).
		armedPreviewRewardChestTitle = "";

		AbstractContainerMenu menu = client.player.containerMenu;
		if (menu == null) return;

		String screenKey = normalizedTitle + "#" + menu.containerId;
		boolean firstScan = scannedRewardScreens.add(screenKey);
		boolean dueScan = firstScan || !screenKey.equals(lastRewardModifierScanKey) || now - lastRewardModifierScanMillis >= REWARD_MODIFIER_SCAN_INTERVAL_MS;
		if (!dueScan) return;
		refreshRewardModifierScan(menu, screenKey, now);
		if (lastRewardModifierScanHadKismetMarker) armNextOpenedKismetReroll();

		updateCachedRewardChestOffers(menu);

		if (lootWindowUntilMillis > 0L
			&& now <= lootWindowUntilMillis
			&& pendingLootOrphaned
			&& pendingLootFloor == DungeonFloor.UNKNOWN
			&& menuTitleFloor != DungeonFloor.UNKNOWN) {
			pendingLootFloor = menuTitleFloor;
			updatePendingChestContextProjection(EvidenceStrength.GUI_TITLE_INFERENCE, DetectionSource.GUI_TITLE_INFERENCE);
		}
	}

	private void rememberRunContextFromMenuTitle(String normalizedTitle, long now) {
		if (normalizedTitle == null || normalizedTitle.isBlank()) return;
		DungeonFloor kuudraTier = detectKuudraTierFromLine(normalizedTitle, normalizedTitle.contains("KUUDRA") || insideKuudra || isCurrentFloorKuudra());
		if (kuudraTier != DungeonFloor.UNKNOWN) {
			if (menuTitleConflictsWithActiveRun(kuudraTier)) {
				if (pendingLootFloor == DungeonFloor.UNKNOWN && lootWindowUntilMillis > 0L && now <= lootWindowUntilMillis) {
					pendingLootFloor = kuudraTier;
					updatePendingChestContextProjection(EvidenceStrength.GUI_TITLE_INFERENCE, DetectionSource.GUI_TITLE_INFERENCE);
				}
				recordContextConflictDiagnostic("rememberRunContextFromMenuTitle", "kuudra_title_conflicts_with_active_run", kuudraTier);
				return;
			}
			rememberKuudraTier(kuudraTier, hasStrongKuudraTierSignal(normalizedTitle));
			insideKuudra = true;
			kuudraSignalUntilMillis = Math.max(kuudraSignalUntilMillis, now + DUNGEON_SIGNAL_GRACE_MS);
			if (pendingLootFloor == DungeonFloor.UNKNOWN && lootWindowUntilMillis > 0L && now <= lootWindowUntilMillis) {
				pendingLootFloor = kuudraTier;
				updatePendingChestContextProjection(EvidenceStrength.CONFIRMED_GUI_COMPONENT, DetectionSource.CONFIRMED_GUI_COMPONENT);
			}
			return;
		}

		DungeonFloor titleFloor = detectFloorFromLine(normalizedTitle);
		if (titleFloor != DungeonFloor.UNKNOWN && titleFloor != activeRunFloorProjection()) {
			if (menuTitleConflictsWithActiveRun(titleFloor)) {
				if (pendingLootFloor == DungeonFloor.UNKNOWN && lootWindowUntilMillis > 0L && now <= lootWindowUntilMillis) {
					pendingLootFloor = titleFloor;
					updatePendingChestContextProjection(EvidenceStrength.GUI_TITLE_INFERENCE, DetectionSource.GUI_TITLE_INFERENCE);
				}
				recordContextConflictDiagnostic("rememberRunContextFromMenuTitle", "catacombs_title_conflicts_with_active_run", titleFloor);
				return;
			}
			updateActiveRunFloorProjection(titleFloor, EvidenceStrength.GUI_TITLE_INFERENCE, DetectionSource.GUI_TITLE_INFERENCE);
			if (pendingLootFloor == DungeonFloor.UNKNOWN && lootWindowUntilMillis > 0L && now <= lootWindowUntilMillis) {
				pendingLootFloor = titleFloor;
				updatePendingChestContextProjection(EvidenceStrength.GUI_TITLE_INFERENCE, DetectionSource.GUI_TITLE_INFERENCE);
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
			if (!isServerOwnedSlot(menu.getSlot(slotIndex))) {
				diagnoseRejectedPlayerInventoryStack(menu.getSlot(slotIndex), "updateCachedRewardChestOffers");
				continue;
			}
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

			ParsedLootName parsed = parseLootDisplayName(line);
			if (parsed.name().isBlank() || shouldIgnoreLootName(parsed.name())) continue;

			String itemId = resolveItemId(parsed.name());
			if (!itemId.isEmpty() || looksReasonableLootName(parsed.name())) {
				entries.add(new DungeonLootEntry(parsed.name(), itemId, parsed.quantity()));
			}
		}
		ChestCostBreakdown breakdown = new ChestCostBreakdown(costCoins);
		applyModifierLoreHints(lore, breakdown);
		applyKuudraChestCostHint(normalizedTitle, breakdown, lore);
		populateKnownModifierCosts(breakdown);
		suppressDungeonChestKeyForKuudra(normalizedTitle, breakdown);
		DungeonChestOffer offer = new DungeonChestOffer(toDisplayChestTitle(normalizedTitle), breakdown, valueCoins, entries);
		offer.contextFloor = detectRewardFloorFromOffer(normalizedTitle, lore);
		offer.alreadyOpened = alreadyOpened;
		offer.normalize();
		return offer;
	}

	private DungeonFloor detectRewardFloorFromOffer(String normalizedTitle, List<String> lore) {
		DungeonFloor titleFloor = rewardContextFloorFromTitle(normalizedTitle);
		if (titleFloor != DungeonFloor.UNKNOWN) return titleFloor;
		if (lore == null || lore.isEmpty()) return DungeonFloor.UNKNOWN;
		List<String> normalizedLore = new ArrayList<>(lore.size());
		for (String line : lore) {
			String normalized = normalize(line);
			if (!normalized.isBlank()) normalizedLore.add(normalized);
		}
		DungeonFloor loreFloor = detectFloorFromLines(normalizedLore);
		if (loreFloor != DungeonFloor.UNKNOWN) return loreFloor;
		for (String line : normalizedLore) {
			DungeonFloor kuudraFloor = detectKuudraTierFromLine(line, isKuudraRewardContext(normalizedTitle));
			if (kuudraFloor != DungeonFloor.UNKNOWN) return kuudraFloor;
		}
		return DungeonFloor.UNKNOWN;
	}

	private void assignPendingOpenedChest(String normalizedTitle, DungeonChestOffer offer) {
		assignPendingOpenedChest(normalizedTitle, offer, false, false, -1);
	}

	private void assignPendingOpenedChest(String normalizedTitle, DungeonChestOffer offer, boolean paidWithDungeonChestKey) {
		assignPendingOpenedChest(normalizedTitle, offer, paidWithDungeonChestKey, false, -1);
	}

	private void assignPendingOpenedChest(
		String normalizedTitle,
		DungeonChestOffer offer,
		boolean paidWithDungeonChestKey,
		boolean forceNewOpen
	) {
		assignPendingOpenedChest(normalizedTitle, offer, paidWithDungeonChestKey, forceNewOpen, -1);
	}

	private void assignPendingOpenedChest(
		String normalizedTitle,
		DungeonChestOffer offer,
		boolean paidWithDungeonChestKey,
		boolean forceNewOpen,
		int containerId
	) {
		String displayTitle = toDisplayChestTitle(normalizedTitle);
		boolean samePendingChest = pendingLootChestAssigned && displayTitle.equalsIgnoreCase(pendingLootChestTitle);
		boolean hadPendingEntries = !pendingLootEntries.isEmpty();
		// Preview→Open of the same tier again (multi-run Croesus) is a new chest, not a re-scan.
		if (forceNewOpen && samePendingChest) {
			if (hadPendingEntries) flushPendingLootRecord(true);
			else resetPendingChestState();
			samePendingChest = false;
			hadPendingEntries = false;
		}
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
			pendingChestSessionId = openTrackingChestSession(displayTitle, containerId, DetectionSource.CONFIRMED_GUI_COMPONENT);
			pendingChestLootDedupKeys.clear();
			pendingLootReconcilingGuiChat = false;
			String countedTitle = normalizedTitle == null ? "" : normalizedTitle.trim().toUpperCase(Locale.ROOT);
			if (!countedTitle.isEmpty()) openedRewardChestTitlesInLootWindow.add(countedTitle);
			openedRewardChestsInLootWindow++;
		}

		pendingLootChestAssigned = true;
		pendingLootChestTitle = displayTitle;
		pendingLootCostBreakdown = offer == null ? new ChestCostBreakdown() : offer.costBreakdown.copy();
		applyOfferContextFloor(offer, "assignPendingOpenedChest");
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
		if (!pendingLootOrphaned && pendingLootFloor == DungeonFloor.UNKNOWN && kuudraFloor != null && kuudraFloor.isKuudra()) {
			pendingLootFloor = kuudraFloor;
		}
		updatePendingChestContextProjection(EvidenceStrength.CONFIRMED_GUI_COMPONENT, DetectionSource.CONFIRMED_GUI_COMPONENT);
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
		if (pendingLootCostBreakdown.usedKuudraKey) pendingLootCostBreakdown.kuudraKeyCostCoins = 0L;
		populateKnownModifierCosts(pendingLootCostBreakdown);
		suppressDungeonChestKeyForKuudra(normalizedTitle, pendingLootCostBreakdown);
		trackingSession.updateChestCost(pendingChestSessionId, pendingLootCostBreakdown);
		lootCollectionUntilMillis = System.currentTimeMillis() + LOOT_COLLECTION_MS;
	}

	private void applyOfferContextFloor(DungeonChestOffer offer, String handler) {
		if (offer == null || offer.contextFloor == null || offer.contextFloor == DungeonFloor.UNKNOWN) return;
		DungeonFloor offerFloor = offer.contextFloor;
		if (pendingLootFloor == DungeonFloor.UNKNOWN) {
			pendingLootFloor = offerFloor;
			updatePendingChestContextProjection(EvidenceStrength.CONFIRMED_GUI_COMPONENT, DetectionSource.CONFIRMED_GUI_COMPONENT);
			return;
		}
		if (pendingLootFloor == offerFloor) {
			updatePendingChestContextProjection(EvidenceStrength.CONFIRMED_GUI_COMPONENT, DetectionSource.CONFIRMED_GUI_COMPONENT);
			return;
		}
		updateChestContextProjection(
			pendingChestSessionId,
			offerFloor,
			EvidenceStrength.CONFIRMED_GUI_COMPONENT,
			DetectionSource.CONFIRMED_GUI_COMPONENT
		);
		recordContextConflictDiagnostic(handler, "croesus_offer_floor_conflicts_with_pending_context", offerFloor);
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
		lastOpenedRewardChestTitleForChat = canonical;
	}

	private void captureOpenedRewardChestLoot(Minecraft client, AbstractContainerMenu menu, long now) {
		if (client == null || client.player == null || menu == null || !pendingLootChestAssigned) return;
		// Chat CHEST REWARDS is authoritative once it starts; keep GUI scan from fighting it.
		if (!pendingLootSeededFromGui && !pendingLootEntries.isEmpty() && lootCollectionUntilMillis > now) {
			return;
		}
		var playerInventory = client.player.getInventory();
		boolean found = false;
		for (int slotIndex = 0; slotIndex < menu.slots.size(); slotIndex++) {
			Slot slot = menu.getSlot(slotIndex);
			if (slot == null || slot.container == playerInventory) continue;
			ItemStack stack = slot.getItem();
			if (stack.isEmpty() || stackIndicatesOpenRewardChest(stack) || isRewardChestUiStack(stack)) continue;
			if (canonicalChestTitleFromStack(stack) != null) continue;

			String rawName = cleanText(stack.getHoverName().getString());
			String cleaned = normalize(rawName);
			if (rawName.isBlank() || shouldIgnoreLootName(rawName) || looksLikeNonLootLine(cleaned)) continue;

			ParsedLootName parsed = parseLootDisplayName(rawName);
			if (parsed.name().isBlank() || shouldIgnoreLootName(parsed.name()) || looksLikeNonLootLine(normalize(parsed.name()))) continue;

			int quantity = Math.max(Math.max(1, stack.getCount()), parsed.quantity());
			String itemId = resolveItemId(parsed.name());
			if (itemId.isEmpty() && !looksReasonableLootName(parsed.name())) continue;
			DungeonLootEntry entry = new DungeonLootEntry(parsed.name(), itemId, quantity);
			observeTrackingLoot(entry, DetectionSource.CONFIRMED_GUI_COMPONENT, menu.containerId, slotIndex, SlotOwner.SERVER_CONTAINER, "gui");
			mergePendingLootEntry(entry, true);
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
		return name.contains("STAINED GLASS")
			|| name.equals("GO BACK")
			|| name.equals("CLOSE")
			|| name.equals("REROLL CHEST")
			|| name.startsWith("REROLL ");
	}

	private boolean shouldAssignArmedKismetToOpenedChest(String normalizedTitle) {
		if (!nextOpenedChestUsesKismetFeather && !rewardMenuKismetRerollPending) return false;
		if (rewardMenuKismetRerolledChestTitle == null || rewardMenuKismetRerolledChestTitle.isBlank()) return true;
		return rewardMenuKismetRerolledChestTitle.equals(normalizedTitle);
	}

	private boolean isCatacombsRewardChest(String normalizedTitle) {
		if (normalizedTitle == null || normalizedTitle.isBlank()) return false;
		if (!REWARD_CHEST_TITLES.contains(normalizedTitle)) return false;
		// Title alone decides this — Wood/Gold/… are always Catacombs. Do not gate on
		// isCurrentFloorKuudra(): sticky Kuudra currentFloor after leaving Hollow hid all
		// Croesus rows and skipped dungeon-key billing.
		return !normalizedTitle.equals("FREE CHEST") && !normalizedTitle.equals("PAID CHEST");
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

	private void suppressDungeonChestKeyForKuudra(String normalizedTitle, ChestCostBreakdown breakdown) {
		if (breakdown == null || !isKuudraRewardChest(normalizedTitle)) return;
		breakdown.usedDungeonChestKey = false;
		breakdown.dungeonChestKeyCostCoins = 0L;
	}

	private boolean isKuudraRewardContext(String normalizedTitle) {
		if (normalizedTitle != null && !normalizedTitle.isBlank()) {
			if (normalizedTitle.contains("VESUVIUS")) return true;
			if (normalizedTitle.contains("KUUDRA") && !normalizedTitle.contains("CATACOMBS")) return true;
			// Croesus / Catacombs reward menus are dungeon chests even if Kuudra state is sticky.
			if (normalizedTitle.contains("CROESUS") || normalizedTitle.contains("CATACOMBS")) return false;
		}
		// Never let a leftover lastKnownKuudraFloor flip dungeon Croesus into FREE/PAID mode.
		return insideKuudra && !insideDungeon;
	}

	private void applyKuudraChestCostHint(String canonicalTitle, ChestCostBreakdown breakdown) {
		applyKuudraChestCostHint(canonicalTitle, breakdown, null);
	}

	private void applyKuudraChestCostHint(String canonicalTitle, ChestCostBreakdown breakdown, List<String> lore) {
		if (breakdown == null || !isKuudraPaidRewardChest(canonicalTitle)) return;
		breakdown.usedKuudraKey = true;
		DungeonFloor keyTier = detectKuudraKeyTierFromLore(lore);
		if (keyTier.isKuudra()) {
			adoptKuudraKeyTier(keyTier);
		}
	}

	private void rememberKuudraKeyTierFromMenu(AbstractContainerMenu menu) {
		if (menu == null) return;
		DungeonFloor best = DungeonFloor.UNKNOWN;
		for (Slot slot : menu.slots) {
			if (slot == null) continue;
			ItemStack stack = slot.getItem();
			if (stack.isEmpty()) continue;
			if (!isServerOwnedSlot(slot)) {
				diagnoseRejectedPlayerInventoryStack(slot, "rememberKuudraKeyTierFromMenu");
				continue;
			}
			DungeonFloor fromName = detectKuudraKeyTierFromText(cleanText(stack.getHoverName().getString()));
			if (kuudraTierNumber(fromName) > kuudraTierNumber(best)) best = fromName;
			for (String line : cleanLoreLines(stack)) {
				DungeonFloor fromLine = detectKuudraKeyTierFromText(line);
				if (kuudraTierNumber(fromLine) > kuudraTierNumber(best)) best = fromLine;
			}
		}
		if (best.isKuudra()) adoptKuudraKeyTier(best);
	}

	private void adoptKuudraKeyTier(DungeonFloor keyTier) {
		if (keyTier == null || !keyTier.isKuudra()) return;
		if (pendingLootOrphaned) {
			if (pendingLootFloor == null
				|| !pendingLootFloor.isKuudra()
				|| kuudraTierNumber(keyTier) > kuudraTierNumber(pendingLootFloor)) {
				pendingLootFloor = keyTier;
				updatePendingChestContextProjection(EvidenceStrength.CONFIRMED_GUI_COMPONENT, DetectionSource.CONFIRMED_GUI_COMPONENT);
			}
			recordContextConflictDiagnostic("adoptKuudraKeyTier", "orphan_kuudra_key_tier_not_applied_to_active_run", keyTier);
			return;
		}
		rememberKuudraTier(keyTier, true);
		if (pendingLootFloor == null
				|| !pendingLootFloor.isKuudra()
				|| kuudraTierNumber(keyTier) > kuudraTierNumber(pendingLootFloor)) {
			pendingLootFloor = keyTier;
			updatePendingChestContextProjection(EvidenceStrength.CONFIRMED_GUI_COMPONENT, DetectionSource.CONFIRMED_GUI_COMPONENT);
		}
	}

	private DungeonFloor detectKuudraKeyTierFromLore(List<String> lore) {
		if (lore == null || lore.isEmpty()) return DungeonFloor.UNKNOWN;
		DungeonFloor best = DungeonFloor.UNKNOWN;
		for (String line : lore) {
			DungeonFloor tier = detectKuudraKeyTierFromText(line);
			if (kuudraTierNumber(tier) > kuudraTierNumber(best)) best = tier;
		}
		return best;
	}

	private DungeonFloor detectKuudraKeyTierFromText(String text) {
		if (text == null || text.isBlank()) return DungeonFloor.UNKNOWN;
		String upper = normalize(text);
		// Strip quantity prefixes like "1x Infernal Kuudra Key".
		Matcher pre = QUANTITY_PREFIX_PATTERN.matcher(upper);
		if (pre.matches()) upper = pre.group(2).trim();
		Matcher suf = QUANTITY_SUFFIX_PATTERN.matcher(upper);
		if (suf.matches()) upper = suf.group(1).trim();
		for (Map.Entry<String, DungeonFloor> entry : KUUDRA_KEY_NAMES) {
			if (upper.contains(entry.getKey())) return entry.getValue();
		}
		return DungeonFloor.UNKNOWN;
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
			if (!isServerOwnedSlot(menu.getSlot(slotIndex))) {
				diagnoseRejectedPlayerInventoryStack(menu.getSlot(slotIndex), "screenHasKismetRerollMarker");
				continue;
			}
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
			if (!isServerOwnedSlot(menu.getSlot(slotIndex))) {
				diagnoseRejectedPlayerInventoryStack(menu.getSlot(slotIndex), "screenHasDungeonChestKeyRequirement");
				continue;
			}
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
			trackingSession.updateChestCost(pendingChestSessionId, pendingLootCostBreakdown);
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
			trackingSession.updateChestCost(pendingChestSessionId, pendingLootCostBreakdown);
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
			trackingSession.updateChestCost(pendingChestSessionId, pendingLootCostBreakdown);
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
		if (breakdown.usedKuudraKey) {
			// Always re-resolve from the best known tier so a sticky Hot/K2 price cannot stick
			// after Infernal is learned from chest Cost lore or scoreboard.
			long resolved = DungeonProfitPricing.resolveKuudraKeyCost(currentKuudraFloorForPricing(), DrtConfigManager.getConfig());
			if (resolved > 0L) breakdown.kuudraKeyCostCoins = resolved;
		}
		breakdown.normalize();
	}

	private DungeonFloor currentKuudraFloorForPricing() {
		DungeonFloor chestFloor = authoritativePendingChestFloor();
		if (chestFloor != null && chestFloor.isKuudra()) return chestFloor;
		if (pendingLootOrphaned) return DungeonFloor.UNKNOWN;
		DungeonFloor activeFloor = activeRunFloorProjection();
		if (activeFloor != null && activeFloor.isKuudra()) return activeFloor;
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

	private void updateKuudraReputationFromLines(List<String> tabLines, List<String> scoreboardLines) {
		TabReader.FactionReputation parsed = TabReader.parseFactionReputation(tabLines);
		if (parsed == null) parsed = TabReader.parseFactionReputation(scoreboardLines);
		if (parsed == null) return;
		DrtConfigManager.updateKuudraReputation(parsed.faction(), parsed.reputation());
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
			if (line.contains("KUUDRA") && !line.contains("CORE") && (line.contains("TIER") || kuudraTierNameFromLine(line) != DungeonFloor.UNKNOWN)) {
				return true;
			}
		}
		return false;
	}

	private DungeonFloor detectKuudraTierFromLines(List<String> lines) {
		boolean kuudraContext = lines.stream().anyMatch(line ->
				line.contains("KUUDRA") || line.contains("HOLLOW"));
		DungeonFloor best = DungeonFloor.UNKNOWN;
		int bestScore = -1;
		for (String line : lines) {
			DungeonFloor tier = detectKuudraTierFromLine(line, kuudraContext);
			if (!tier.isKuudra()) continue;
			int score = kuudraTierConfidence(line, tier);
			if (score > bestScore || (score == bestScore && kuudraTierNumber(tier) > kuudraTierNumber(best))) {
				best = tier;
				bestScore = score;
			}
		}
		return best;
	}

	private DungeonFloor detectKuudraTierFromLine(String line, boolean kuudraContext) {
		if (line == null || line.isBlank()) return DungeonFloor.UNKNOWN;
		boolean hasKuudra = line.contains("KUUDRA");
		boolean hasHollow = line.contains("HOLLOW");
		boolean strongContext = hasKuudra || hasHollow || line.contains("TIER");

		// Named tiers first — "Infernal Tier!" must beat any later short-pattern noise.
		boolean tierNameContext = strongContext
				|| line.startsWith("TIER:")
				|| line.startsWith("TIER ")
				|| (kuudraContext && isLikelyStandaloneKuudraTierLine(line));
		if (tierNameContext) {
			DungeonFloor namedTier = kuudraTierNameFromLine(line);
			if (namedTier != DungeonFloor.UNKNOWN) return namedTier;
		}

		Matcher parenMatcher = KUUDRA_PAREN_TIER_PATTERN.matcher(line);
		if ((strongContext || kuudraContext) && parenMatcher.find()) {
			return kuudraTier(parsePositiveInt(parenMatcher.group(1), -1));
		}

		Matcher wordMatcher = KUUDRA_TIER_WORD_PATTERN.matcher(line);
		if ((strongContext || line.startsWith("TIER") || kuudraContext) && wordMatcher.find()) {
			return kuudraTier(parseTierNumber(wordMatcher.group(1)));
		}

		// Short T5/K5 only with line-local Kuudra/Hollow context — never from ambient
		// "we're in Kuudra" alone (that falsely matched digits in unrelated chat/scoreboard).
		Matcher shortMatcher = KUUDRA_SHORT_TIER_PATTERN.matcher(line);
		if (strongContext && shortMatcher.find()) {
			return kuudraTier(parsePositiveInt(shortMatcher.group(1), -1));
		}
		if (kuudraContext && isLikelyStandaloneKuudraTierLine(line) && shortMatcher.reset().find()) {
			return kuudraTier(parsePositiveInt(shortMatcher.group(1), -1));
		}
		return DungeonFloor.UNKNOWN;
	}

	private boolean isLikelyStandaloneKuudraTierLine(String line) {
		if (line == null || line.isBlank()) return false;
		String compact = line.replaceAll("[^A-Z0-9()]+", "");
		if (compact.length() <= 8) return true;
		return KUUDRA_PAREN_TIER_PATTERN.matcher(line).find()
				|| KUUDRA_TIER_WORD_PATTERN.matcher(line).find()
				|| kuudraTierNameFromLine(line) != DungeonFloor.UNKNOWN;
	}

	private boolean hasStrongKuudraTierSignal(String line) {
		if (line == null || line.isBlank()) return false;
		if (!(line.contains("KUUDRA") || line.contains("HOLLOW") || line.contains("TIER"))) return false;
		return detectKuudraTierFromLine(line, true) != DungeonFloor.UNKNOWN;
	}

	private int kuudraTierConfidence(String line, DungeonFloor tier) {
		if (tier == null || !tier.isKuudra()) return -1;
		int score = 10;
		if (line.contains("KUUDRA") || line.contains("HOLLOW")) score += 40;
		if (line.contains("TIER")) score += 20;
		if (kuudraTierNameFromLine(line) == tier) score += 30;
		if (KUUDRA_PAREN_TIER_PATTERN.matcher(line).find()) score += 25;
		score += kuudraTierNumber(tier);
		return score;
	}

	private int kuudraTierNumber(DungeonFloor tier) {
		if (tier == null) return -1;
		return switch (tier) {
			case K1 -> 1;
			case K2 -> 2;
			case K3 -> 3;
			case K4 -> 4;
			case K5 -> 5;
			default -> -1;
		};
	}

	private DungeonFloor kuudraTierNameFromLine(String line) {
		if (line == null || line.isBlank()) return DungeonFloor.UNKNOWN;
		String padded = " " + line.replaceAll("[^A-Z0-9]+", " ").trim() + " ";
		for (Map.Entry<String, DungeonFloor> entry : KUUDRA_TIER_NAMES) {
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

	private boolean shouldBeginNewDungeonRunFromEntry(long now) {
		if (currentRunActive || runCountedThisDungeon) return false;
		if (now <= dungeonSignalUntilMillis && activeRunSessionId != null && !activeRunSessionId.isBlank()) return false;
		return true;
	}

	private boolean isKuudraEntryMessage(String cleaned) {
		if (cleaned == null || cleaned.isBlank()) return false;
		if (cleaned.contains("KUUDRA'S HOLLOW") || cleaned.contains("KUUDRA HOLLOW")) return true;
		if (!cleaned.contains("KUUDRA") || cleaned.contains("CORE")) return false;
		if (cleaned.contains("STARTING") || cleaned.contains("TIER")) return true;
		// Word-boundary style checks — avoid matching HOT inside SHOT/PHOTO.
		String padded = " " + cleaned.replaceAll("[^A-Z0-9]+", " ").trim() + " ";
		return padded.contains(" BASIC ")
				|| padded.contains(" HOT ")
				|| padded.contains(" BURNING ")
				|| padded.contains(" FIERY ")
				|| padded.contains(" INFERNAL ");
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

	private void rememberKuudraTier(DungeonFloor tier, boolean trusted) {
		if (tier == null || !tier.isKuudra()) return;
		// Weak detections must not downgrade/overwrite a known tier mid-run (K5 -> K2).
		if (!trusted
				&& currentFloor != null
				&& currentFloor.isKuudra()
				&& currentFloor != tier
				&& (currentRunActive || insideKuudra)) {
			return;
		}
		boolean accepted = updateActiveRunFloorProjection(
			tier,
			trusted ? EvidenceStrength.CONFIRMED_GUI_COMPONENT : EvidenceStrength.RECENT_CONTEXT,
			trusted ? DetectionSource.CONFIRMED_GUI_COMPONENT : DetectionSource.RECENT_CONTEXT
		);
		if (accepted || trackingSession.activeRun() == null) {
			currentFloor = tier;
			lastKnownKuudraFloor = tier;
		}
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
		resetDungeonRuntimeState();
		lastKnownKuudraFloor = DungeonFloor.UNKNOWN;
		inDungeonHub = false;
		inCrimsonIsle = false;
		dungeonSignalUntilMillis = 0L;
		kuudraSignalUntilMillis = 0L;
		lastLevelIdentity = null;
		// Keep the loot window across brief player/level unavailability (dungeon → hub warps).
		// Clearing it here reset openedChestsThisWindow mid-Croesus and undercounted opens.
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
		RunSession run = trackingSession.startRun(
			RunMode.DUNGEON,
			DungeonFloor.UNKNOWN,
			DetectionSource.STRUCTURED_CHAT,
			EvidenceStrength.STRUCTURED_CHAT
		);
		activeRunSessionId = run.id();
		currentRunCompletionFingerprint = "";
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
		currentRunCompletionFingerprint = "";
		// Prefer a freshly detected tier. Do not silently inherit a sticky Hot (K2) floor from
		// earlier farming when this Hollow visit has not announced Infernal/T5 yet.
		DungeonFloor runTier = tier != null && tier.isKuudra() ? tier : DungeonFloor.UNKNOWN;
		RunSession run = trackingSession.startRun(
			RunMode.KUUDRA,
			runTier,
			DetectionSource.STRUCTURED_CHAT,
			EvidenceStrength.STRUCTURED_CHAT
		);
		activeRunSessionId = run.id();
		currentFloor = runTier;
		lastKnownKuudraFloor = runTier.isKuudra() ? runTier : DungeonFloor.UNKNOWN;
		if (runTier.isKuudra()) {
			DungeonRunTracker.LOGGER.info("[DRT] Kuudra run started: tier={}", runTier.name());
		} else {
			DungeonRunTracker.LOGGER.info("[DRT] Kuudra run started: tier=UNKNOWN (waiting for Infernal/T5 signal)");
		}
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
		boolean preserveOpenCount = lootWindowUntilMillis > now && openedRewardChestsInLootWindow > 0;
		int preservedOpenCount = openedRewardChestsInLootWindow;
		Set<String> preservedOpenTitles = preserveOpenCount
			? new HashSet<>(openedRewardChestTitlesInLootWindow)
			: Set.of();
		if (pendingLootChestAssigned && !pendingLootEntries.isEmpty() && pendingLootRunNumber > 0) {
			flushPendingLootRecord(true);
		}
		lootWindowUntilMillis = now + LOOT_WINDOW_MS;
		lootCollectionUntilMillis = 0L;
		pendingLootRunNumber = runNumber;
		pendingLootRunTimestamp = now;
		pendingChestSessionId = nextChestSessionId(runNumber > 0 ? activeRunSessionId : "");
		pendingLootFloor = floor;
		pendingLootOrphaned = runNumber <= 0;
		pendingLootChestTitle = "";
		pendingLootCostBreakdown = new ChestCostBreakdown();
		pendingLootSeededFromGui = false;
		pendingLootReconcilingGuiChat = false;
		pendingLootChestAssigned = false;
		openedRewardChestsInLootWindow = preserveOpenCount ? preservedOpenCount : 0;
		openedRewardChestTitlesInLootWindow.clear();
		if (preserveOpenCount) openedRewardChestTitlesInLootWindow.addAll(preservedOpenTitles);
		nextOpenedChestUsesDungeonChestKey = false;
		nextOpenedChestUsesKismetFeather = false;
		nextOpenedChestUsesWheelOfFate = false;
		rewardMenuKismetRerollPending = false;
		rewardMenuKismetRerolledChestTitle = "";
		pendingLootEntries.clear();
		pendingChestLootDedupKeys.clear();
	}

	private DungeonFloor rewardContextFloorFromTitle(String normalizedTitle) {
		if (normalizedTitle == null || normalizedTitle.isBlank()) return DungeonFloor.UNKNOWN;
		DungeonFloor kuudraTier = detectKuudraTierFromLine(normalizedTitle, normalizedTitle.contains("KUUDRA"));
		if (kuudraTier != DungeonFloor.UNKNOWN) return kuudraTier;
		return detectFloorFromLine(normalizedTitle);
	}

	public synchronized void recordManualRun(DungeonRunRecord record, long runTimeMs) {
		if (record == null) return;
		long now = System.currentTimeMillis();
		String key = record.floor;
		String g = record.grade;

		var recordCommit = DrtConfigManager.addRunRecord(record);
		if (recordCommit != dev.vy.drt.config.RunRecordCommitDecision.ADD_INCOMING) {
			recordDuplicateCommitDiagnostic(recordCommit, record);
			resyncLifetimeFromConfig();
			return;
		}
		String completionFingerprint = record.commitFingerprint == null || record.commitFingerprint.isBlank()
			? "manual|" + buildLootCommitFingerprint(record)
			: "manual|" + record.commitFingerprint;
		DrtConfigManager.addRunCompletionRecord(new DungeonRunCompletionRecord(
			now,
			configFloor(key).isKuudra() ? RunMode.KUUDRA.name() : RunMode.DUNGEON.name(),
			key,
			g,
			runTimeMs,
			record.runSessionId,
			completionFingerprint
		));

		resyncLifetimeFromConfig();
		int newCount = floorRunCounts.getOrDefault(key, 0);
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

		if (runTimeMs > 0L) {
			sessionTotalRunTimeMs += runTimeMs;
			sessionFloorRunTimeTotals.merge(key, runTimeMs, Long::sum);
			sessionInRunMillis += runTimeMs;
			accumulateLifetimeRunTime(key, runTimeMs);
		}
		DungeonRunTracker.LOGGER.info("[DRT] *** MANUAL RUN RECORDED: floor={} grade={} totalForFloor={} profit={} runTimeMs={}", key, g, newCount, profit, runTimeMs);
	}

	private synchronized void recordCompletedRun(long now, DungeonFloor floor, String grade) {
		String key = floor != null && floor != DungeonFloor.UNKNOWN ? floor.name() : "UNKNOWN";
		String g = grade == null || grade.isBlank() ? "?" : grade;
		long bossTimeMs = Math.max(0L, currentRunBossTimeMs);
		String incomingCompletionShape = key + "|" + g + "|" + bossTimeMs;
		RunSession existingRun = activeRunSessionId == null || activeRunSessionId.isBlank()
			? null
			: trackingSession.run(activeRunSessionId);
		if (existingRun != null && existingRun.state() == RunState.COMPLETED) {
			if (completionShape(currentRunCompletionFingerprint).equals(incomingCompletionShape)) {
				recordCompletionDuplicateDecision(currentRunCompletionFingerprint, now, key, g);
				runCountedThisDungeon = true;
				return;
			}
			existingRun = null;
			activeRunSessionId = "";
			currentRunCompletionFingerprint = "";
		}
		if (activeRunSessionId == null || activeRunSessionId.isBlank() || existingRun == null) {
			RunSession run = trackingSession.startRun(
				floor != null && floor.isKuudra() ? RunMode.KUUDRA : RunMode.DUNGEON,
				floor,
				DetectionSource.CONFIRMED_COMPLETION,
				EvidenceStrength.CONFIRMED_COMPLETION
			);
			activeRunSessionId = run.id();
		}
		String completionFingerprint = activeRunSessionId + "|" + incomingCompletionShape;
		boolean completedBySession = trackingSession.completeRun(activeRunSessionId, floor, g, completionFingerprint);
		if (!completedBySession) {
			if (completionFingerprint.equals(currentRunCompletionFingerprint)) {
				recordCompletionDuplicateDecision(completionFingerprint, now, key, g);
			} else {
				recordCompletionDuplicateDiagnostic(completionFingerprint, now, key, g);
			}
			DungeonRunTracker.LOGGER.info(
				"[DRT] Ignored duplicate/conflicting completion signal for runSession={}: floor={} grade={} previousFloor={} previousGrade={}",
				activeRunSessionId,
				key,
				g,
				lastRunRecordFloor != null ? lastRunRecordFloor.name() : "UNKNOWN",
				lastRunRecordGrade
			);
			runCountedThisDungeon = true;
			return;
		}
		currentRunCompletionFingerprint = completionFingerprint;
		lastRunRecordMillis = now;
		lastRunRecordFloor = floor != null ? floor : DungeonFloor.UNKNOWN;
		lastRunRecordGrade = g;

		if (!sessionActive) {
			sessionActive = true;
			sessionStartMillis = now;
		}
		long wallRunTimeMs = finishCurrentRunTiming(now);
		if (wallRunTimeMs <= 0L) wallRunTimeMs = lastFinishedRunTimeMs;
		long runTimeMs = currentRunBossTimeMs > 0L ? currentRunBossTimeMs : wallRunTimeMs;
		DungeonRunCompletionRecord completionRecord = new DungeonRunCompletionRecord(
			now,
			floor != null && floor.isKuudra() ? RunMode.KUUDRA.name() : RunMode.DUNGEON.name(),
			key,
			g,
			runTimeMs,
			activeRunSessionId,
			completionFingerprint
		);
		var completionCommit = DrtConfigManager.addRunCompletionRecord(completionRecord);
		if (completionCommit != dev.vy.drt.config.RunRecordCommitDecision.ADD_INCOMING) {
			recordCompletionPersistenceDiagnostic(completionCommit, completionFingerprint, now, key, g, runTimeMs);
			runCountedThisDungeon = true;
			return;
		}
		resyncLifetimeFromConfig();
		int newCount = floorRunCounts.getOrDefault(key, 0);
		gradeRunCounts.merge(g, 1, Integer::sum);
		sessionGradeRuns.merge(g, 1, Integer::sum);
		lastRecordedGrade = g;
		runCountedThisDungeon = true;
		DungeonRunTracker.LOGGER.info("[DRT] *** RUN RECORDED: floor={} grade={} totalForFloor={} allFloors={}", key, g, newCount, floorRunCounts);
		sessionTotalRunTimeMs += runTimeMs;
		sessionFloorRunTimeTotals.merge(key, runTimeMs, Long::sum);
		accumulateLifetimeRunTime(key, runTimeMs);
		currentRunBossTimeMs = 0L;
		lastFinishedRunTimeMs = 0L;
		sessionRuns++;
		sessionFloorRuns.merge(key, 1, Integer::sum);

		startLootWindow(now, totalRunsCompleted(), floor != null ? floor : DungeonFloor.UNKNOWN);
	}

	private String completionShape(String completionFingerprint) {
		if (completionFingerprint == null || completionFingerprint.isBlank()) return "";
		String[] parts = completionFingerprint.split("\\|", 4);
		return parts.length == 4 ? parts[1] + "|" + parts[2] + "|" + parts[3] : completionFingerprint;
	}

	private void startAdHocLootWindow(long now) {
		startLootWindow(now, 0, DungeonFloor.UNKNOWN);
		// Historical Croesus opens with no recent tracked completion are expected.
		// Only escalate to a user-facing bug export when ownership should have existed.
		boolean unexpectedOrphan = runCountedThisDungeon
			|| (activeRunSessionId != null && !activeRunSessionId.isBlank())
			|| (lastRunRecordMillis > 0L && now - lastRunRecordMillis <= LATE_LOOT_REATTACH_MS);
		recordOrphanChestDiagnostic("ad_hoc_reward_without_run_owner", now, unexpectedOrphan);
	}

	private void startLateOwnedOrAdHocLootWindow(long now, DungeonFloor floorHint) {
		DungeonFloor ownerFloor = lateLootOwnerFloor(floorHint, now);
		if (ownerFloor != DungeonFloor.UNKNOWN) {
			startLootWindow(now, Math.max(1, totalRunsCompleted()), ownerFloor);
			updatePendingChestContextProjection(EvidenceStrength.RECENT_CONTEXT, DetectionSource.RECENT_CONTEXT);
			return;
		}
		startAdHocLootWindow(now);
	}

	private DungeonFloor lateLootOwnerFloor(DungeonFloor floorHint, long now) {
		DungeonFloor live = lateLootOwnerFloorFromLiveMemory(floorHint, now);
		if (live != DungeonFloor.UNKNOWN) return live;
		return lateLootOwnerFloorFromPersistedCompletion(floorHint, now);
	}

	private DungeonFloor lateLootOwnerFloorFromLiveMemory(DungeonFloor floorHint, long now) {
		if (!runCountedThisDungeon) return DungeonFloor.UNKNOWN;
		// Croesus is normally opened outside the dungeon instance (hub / island). Do not
		// require insideDungeon/insideKuudra — only a recent completed run session.
		if (activeRunSessionId == null || activeRunSessionId.isBlank()) return DungeonFloor.UNKNOWN;
		if (lastRunRecordMillis <= 0L || now - lastRunRecordMillis > LATE_LOOT_REATTACH_MS) return DungeonFloor.UNKNOWN;
		RunSession run = trackingSession.run(activeRunSessionId);
		if (run == null || run.state() != RunState.COMPLETED) return DungeonFloor.UNKNOWN;
		return chooseLateLootFloor(floorHint, lastRunRecordFloor, currentFloor);
	}

	private DungeonFloor lateLootOwnerFloorFromPersistedCompletion(DungeonFloor floorHint, long now) {
		DungeonRunCompletionRecord recent = mostRecentPersistedCompletion(now);
		if (recent == null) return DungeonFloor.UNKNOWN;
		DungeonFloor recordedFloor = configFloor(recent.floor);
		DungeonFloor chosen = chooseLateLootFloor(floorHint, recordedFloor, currentFloor);
		if (chosen == DungeonFloor.UNKNOWN) return DungeonFloor.UNKNOWN;
		if (!restoreLiveOwnershipFromPersistedCompletion(recent, recordedFloor == DungeonFloor.UNKNOWN ? chosen : recordedFloor, now)) {
			return DungeonFloor.UNKNOWN;
		}
		DungeonRunTracker.LOGGER.info(
			"[DRT] Reattached late chest ownership from persisted completion: floor={} grade={} ageMs={}",
			chosen.name(),
			recent.grade,
			Math.max(0L, now - recent.completedAtEpochMillis)
		);
		return chosen;
	}

	private DungeonFloor chooseLateLootFloor(DungeonFloor floorHint, DungeonFloor recordedFloor, DungeonFloor contextFloor) {
		DungeonFloor hinted = floorHint == null ? DungeonFloor.UNKNOWN : floorHint;
		DungeonFloor recorded = recordedFloor == null ? DungeonFloor.UNKNOWN : recordedFloor;
		DungeonFloor context = contextFloor == null ? DungeonFloor.UNKNOWN : contextFloor;
		if (hinted != DungeonFloor.UNKNOWN && recorded != DungeonFloor.UNKNOWN && hinted != recorded) {
			return DungeonFloor.UNKNOWN;
		}
		if (hinted != DungeonFloor.UNKNOWN && context != DungeonFloor.UNKNOWN && hinted != context) {
			return DungeonFloor.UNKNOWN;
		}
		if (hinted != DungeonFloor.UNKNOWN) return hinted;
		if (recorded != DungeonFloor.UNKNOWN) return recorded;
		return context;
	}

	private DungeonRunCompletionRecord mostRecentPersistedCompletion(long now) {
		DungeonRunCompletionRecord best = null;
		for (DungeonRunCompletionRecord record : DrtConfigManager.getRunCompletions()) {
			if (record == null || record.completedAtEpochMillis <= 0L) continue;
			if (now - record.completedAtEpochMillis > LATE_LOOT_REATTACH_MS) continue;
			if (configFloor(record.floor) == DungeonFloor.UNKNOWN) continue;
			if (best == null || record.completedAtEpochMillis > best.completedAtEpochMillis) {
				best = record;
			}
		}
		return best;
	}

	private boolean restoreLiveOwnershipFromPersistedCompletion(
		DungeonRunCompletionRecord recent,
		DungeonFloor floor,
		long now
	) {
		if (recent == null || floor == null || floor == DungeonFloor.UNKNOWN) return false;
		RunMode mode = floor.isKuudra() ? RunMode.KUUDRA : RunMode.DUNGEON;
		RunSession run = trackingSession.startRun(
			mode,
			floor,
			DetectionSource.RECENT_CONTEXT,
			EvidenceStrength.RECENT_CONTEXT
		);
		String grade = recent.grade == null || recent.grade.isBlank() ? "?" : recent.grade;
		String fingerprint = recent.completionFingerprint;
		if (fingerprint == null || fingerprint.isBlank()) {
			fingerprint = run.id() + "|" + floor.name() + "|" + grade + "|" + Math.max(0L, recent.runTimeMs);
		}
		if (!trackingSession.completeRun(run.id(), floor, grade, fingerprint)) {
			return false;
		}
		activeRunSessionId = run.id();
		currentRunCompletionFingerprint = fingerprint;
		lastRunRecordMillis = recent.completedAtEpochMillis > 0L ? recent.completedAtEpochMillis : now;
		lastRunRecordFloor = floor;
		lastRunRecordGrade = grade;
		lastRecordedGrade = grade;
		runCountedThisDungeon = true;
		if (currentFloor == null || currentFloor == DungeonFloor.UNKNOWN) {
			currentFloor = floor;
		}
		return true;
	}

	private void clearLootWindow() {
		lootWindowUntilMillis = 0L;
		lootCollectionUntilMillis = 0L;
		pendingLootRunNumber = 0;
		pendingLootRunTimestamp = 0L;
		pendingChestSessionId = "";
		pendingLootFloor = DungeonFloor.UNKNOWN;
		pendingLootOrphaned = false;
		pendingLootChestTitle = "";
		pendingLootCostBreakdown = new ChestCostBreakdown();
		pendingLootSeededFromGui = false;
		pendingLootReconcilingGuiChat = false;
		pendingLootChestAssigned = false;
		openedRewardChestsInLootWindow = 0;
		openedRewardChestTitlesInLootWindow.clear();
		nextOpenedChestUsesDungeonChestKey = false;
		nextOpenedChestUsesKismetFeather = false;
		nextOpenedChestUsesWheelOfFate = false;
		rewardMenuKismetRerollPending = false;
		rewardMenuKismetRerolledChestTitle = "";
		pendingLootEntries.clear();
		lootGuardWarnedKeys.clear();
		cachedChestOffersByTitle.clear();
		cachedChestOfferFingerprintsByTitle.clear();
		scannedRewardScreens.clear();
		lastRewardModifierScanKey = "";
		lastRewardModifierScanMillis = 0L;
		lastRewardModifierScanHadKeyRequirement = false;
		lastRewardModifierScanHadKismetMarker = false;
		lastViewedOpenedRewardChestTitle = "";
		lastOpenedRewardChestTitleForChat = "";
		armedPreviewRewardChestTitle = "";
		recentLootMessages.clear();
		pendingChestLootDedupKeys.clear();
		ignoredPlayerInventoryDiagnosticKeys.clear();
	}

	private void handleLootMessage(String rawText, String cleaned, long now) {
		if (isDrtClientMessage(cleaned) || isDrtClientMessage(rawText)) return;
		if ((lootWindowUntilMillis <= 0L || now > lootWindowUntilMillis) && isLootHeader(cleaned)) {
			startLateOwnedOrAdHocLootWindow(now, rewardContextFloorFromTitle(cleaned));
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
			if (sameChest && pendingLootSeededFromGui) {
				// Same open as the GUI capture — reconcile chat with GUI instead of destroying GUI observations.
				pendingLootReconcilingGuiChat = true;
			} else if (sameChest && !pendingLootEntries.isEmpty()) {
				// Second CHEST REWARDS for the same tier (multi-run Croesus) — save the previous open.
				flushPendingLootRecord(true);
				assignOpenedChest(cleaned);
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
		if (!pendingLootChestAssigned) return;
		DungeonLootEntry parsed = parseLootEntry(rawText, cleaned);
		if (parsed == null) return;
		observeTrackingLoot(parsed, DetectionSource.STRUCTURED_CHAT, -1, -1, SlotOwner.SERVER_CONTAINER, cleaned);
		if (!markLootLineForProcessing(cleaned, now)) return;
		mergePendingLootEntry(parsed, pendingLootSeededFromGui || pendingLootReconcilingGuiChat);
		if (!pendingLootReconcilingGuiChat) pendingLootSeededFromGui = false;
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
		// Real headers are "OBSIDIAN CHEST REWARDS" / similar. Do not match bare
		// "DUNGEON CHEST" — that false-positives on key-requirement chat and flushes mid-loot.
		return cleaned.contains("CHEST REWARDS")
			|| cleaned.contains("REWARD CHEST");
	}

	private void assignOpenedChest(String cleaned) {
		for (String title : REWARD_CHEST_TITLES) {
			if (!cleaned.contains(title)) continue;
			DungeonChestOffer cached = cachedChestOffersByTitle.get(title);
			assignPendingOpenedChest(title, cached);
			return;
		}
		// Some CHEST REWARDS headers omit the tier name; fall back to the GUI we just left.
		String fallback = lastOpenedRewardChestTitleForChat;
		if (fallback == null || fallback.isBlank()) return;
		DungeonChestOffer cached = cachedChestOffersByTitle.get(fallback);
		assignPendingOpenedChest(fallback, cached);
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
		if (looksLikeNonLootLine(cleaned) || looksLikeNonLootLine(trimmedRaw)) return null;

		Matcher essenceMatcher = ESSENCE_PATTERN.matcher(cleaned);
		if (essenceMatcher.matches()) {
			String essenceName = essenceMatcher.group(1) + " ESSENCE";
			int quantity = parsePositiveInt(essenceMatcher.group(2), 1);
			return new DungeonLootEntry(essenceName, resolveItemId(essenceName), quantity);
		}

		String candidateName = null;
		int quantity = 1;

		Matcher rareRewardMatcher = RARE_REWARD_ITEM_PATTERN.matcher(trimmedRaw);
		if (!rareRewardMatcher.matches()) rareRewardMatcher = RARE_REWARD_ITEM_PATTERN.matcher(cleaned);
		if (rareRewardMatcher.matches()) {
			candidateName = rareRewardMatcher.group(1).trim();
		}

		Matcher receivedMatcher = RECEIVED_PATTERN.matcher(trimmedRaw);
		if (candidateName == null && receivedMatcher.matches()) {
			candidateName = receivedMatcher.group(1);
			quantity = parsePositiveInt(receivedMatcher.group(2), 1);
		} else if (candidateName == null) {
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

	private boolean isServerOwnedSlot(Slot slot) {
		return slot != null && !isPlayerInventorySlot(slot);
	}

	private boolean isPlayerInventorySlot(Slot slot) {
		if (slot == null) return false;
		Minecraft client = Minecraft.getInstance();
		return client != null
			&& client.player != null
			&& slot.container == client.player.getInventory();
	}

	private void diagnoseRejectedPlayerInventoryStack(Slot slot, String handler) {
		if (slot == null) return;
		ItemStack stack = slot.getItem();
		if (stack == null || stack.isEmpty()) return;
		String name = cleanText(stack.getHoverName().getString());
		String normalizedName = normalize(name);
		DungeonFloor keyTier = detectKuudraKeyTierFromText(name);
		boolean costAuthorityAttempt = false;
		boolean keyOrModifierLike = normalizedName.contains("KUUDRA KEY")
			|| normalizedName.contains("DUNGEON CHEST KEY")
			|| normalizedName.contains("KISMET FEATHER")
			|| normalizedName.contains("WHEEL OF FATE");
		for (String line : cleanLoreLines(stack)) {
			DungeonFloor fromLore = detectKuudraKeyTierFromText(line);
			if (kuudraTierNumber(fromLore) > kuudraTierNumber(keyTier)) keyTier = fromLore;
			String normalized = normalize(line);
			if (lineIndicatesDungeonChestKeyRequirement(normalized)
				|| lineIndicatesDungeonChestKeyUsed(normalized)
				|| lineIndicatesKismetUsed(normalized)
				|| lineIndicatesWheelOfFateUsed(normalized)) {
				costAuthorityAttempt = true;
			}
		}
		if (!keyTier.isKuudra() && !costAuthorityAttempt && !keyOrModifierLike) return;
		String category = keyTier.isKuudra()
			? "KUUDRA_KEY"
			: costAuthorityAttempt ? "CHEST_COST_MARKER" : "REWARD_MODIFIER_ITEM";
		String dedupKey = handler + "|" + category + "|" + floorName(keyTier);
		if (!ignoredPlayerInventoryDiagnosticKeys.add(dedupKey)) return;
		if (ignoredPlayerInventoryDiagnosticKeys.size() > 128) ignoredPlayerInventoryDiagnosticKeys.clear();
		DetectionEvent event = diagnostics.recordEvent(
			DetectionEventType.CONTAINER_SNAPSHOT,
			DetectionSource.PLAYER_INVENTORY,
			diagnosticPayload(
				"slotOwner", "PLAYER_INVENTORY",
				"itemCategory", category,
				"attemptedFloor", floorName(keyTier),
				"currentFloor", floorName(currentFloor),
				"pendingLootFloor", floorName(pendingLootFloor),
				"pendingChestSessionId", pendingChestSessionId,
				"pendingRunSessionId", activeRunSessionId
			)
		);
		diagnostics.recordDecision(
			event,
			"DungeonRunTrackerFeature." + handler,
			"IGNORE_PLAYER_INVENTORY_STACK",
			"passive_player_inventory_scan_has_no_tracking_authority",
			activeRunSessionId,
			pendingChestSessionId,
			"",
			dedupKey,
			diagnosticPayload(
				"slotOwner", "PLAYER_INVENTORY",
				"itemCategory", category,
				"attemptedFloor", floorName(keyTier)
			)
		);
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
		mergePendingLootEntry(incoming, pendingLootSeededFromGui);
	}

	private void observeTrackingLoot(
		DungeonLootEntry incoming,
		DetectionSource source,
		int containerId,
		int slotIndex,
		SlotOwner slotOwner,
		String dedupBasis
	) {
		if (incoming == null || pendingChestSessionId == null || pendingChestSessionId.isBlank()) return;
		String normalizedName = stripTrailingLootQuantity(sanitizeLootName(incoming.rawName)).toUpperCase(Locale.ROOT);
		String identityKey = lootKey(incoming);
		String dedupKey = lootObservationDedupKey(
			source,
			containerId,
			slotIndex,
			identityKey,
			dedupBasis,
			Math.max(1, incoming.quantity)
		);
		DetectionEvent event = diagnostics.recordEvent(
			DetectionEventType.LOOT_OBSERVED,
			source == null ? DetectionSource.NONE : source,
			diagnosticPayload(
				"pendingChestSessionId", pendingChestSessionId,
				"rawName", incoming.rawName,
				"normalizedName", normalizedName,
				"itemId", incoming.itemId,
				"quantity", Math.max(1, incoming.quantity),
				"containerId", containerId,
				"slotIndex", slotIndex,
				"slotOwner", slotOwner == null ? SlotOwner.UNKNOWN : slotOwner
			)
		);
		trackingSession.observeLoot(pendingChestSessionId, new LootObservation(
			pendingChestSessionId + "-loot-" + event.sequence(),
			event.sequence(),
			source,
			incoming.rawName,
			normalizedName,
			incoming.itemId,
			lootIdentityStrength(incoming),
			Math.max(1, incoming.quantity),
			containerId,
			slotIndex,
			slotOwner,
			dedupKey
		));
	}

	private String lootObservationDedupKey(
		DetectionSource source,
		int containerId,
		int slotIndex,
		String identityKey,
		String dedupBasis,
		int quantity
	) {
		// Always include identity + slot. A bare basis like "gui" previously collapsed every
		// qty=1 GUI stack into one ChestSession observation, so flush only kept the first drop.
		return (pendingChestSessionId == null ? "" : pendingChestSessionId) + "|"
			+ (source == null ? DetectionSource.NONE : source).name() + "|"
			+ containerId + "|"
			+ slotIndex + "|"
			+ (identityKey == null ? "" : identityKey) + "|"
			+ (dedupBasis == null || dedupBasis.isBlank() ? "-" : dedupBasis.trim()) + "|"
			+ Math.max(1, quantity);
	}

	private LootIdentityStrength lootIdentityStrength(DungeonLootEntry entry) {
		if (entry == null || entry.itemId == null || entry.itemId.isBlank()) return LootIdentityStrength.UNRESOLVED;
		return LootIdentityStrength.STRICT_ALIAS;
	}

	private void mergePendingLootEntry(DungeonLootEntry incoming, boolean maxOnDuplicate) {
		if (incoming == null) return;
		String incomingKey = lootKey(incoming);
		DungeonLootEntry target = null;
		for (DungeonLootEntry existing : pendingLootEntries) {
			if (lootKey(existing).equals(incomingKey)) {
				if (maxOnDuplicate) {
					existing.quantity = Math.max(existing.quantity, incoming.quantity);
				} else {
					existing.quantity += incoming.quantity;
				}
				target = existing;
				break;
			}
		}
		if (target == null) {
			pendingLootEntries.add(incoming);
			target = incoming;
		}
		warnLootGuardsForEntry(target);
	}

	private void warnLootGuardsForEntry(DungeonLootEntry entry) {
		if (entry == null) return;
		if (entry.itemId == null || entry.itemId.isBlank()) {
			recordUnresolvedItemDiagnostic(entry.rawName, sanitizeLootName(entry.rawName).toUpperCase(Locale.ROOT));
			return;
		}
		DungeonFloor guardFloor = authoritativePendingChestFloor();
		if (hasLootContextConflict()) {
			recordContextConflictDiagnostic("warnLootGuardsForEntry", "context_conflict_blocks_loot_guard", guardFloor);
			return;
		}
		for (String reason : LootFloorGuards.evaluate(guardFloor, pendingLootChestTitle, entry)) {
			String key = lootKey(entry) + "|" + reason;
			if (!lootGuardWarnedKeys.add(key)) continue;
			String report = buildLootGuardReport(reason, entry);
			DungeonRunTracker.LOGGER.warn(report.replace("\n", " | "));
			notifyLootGuardInChat(report);
		}
	}

	private void warnLootGuardsForChest(List<DungeonLootEntry> entries) {
		DungeonFloor guardFloor = authoritativePendingChestFloor();
		if (hasLootContextConflict()) {
			recordContextConflictDiagnostic("warnLootGuardsForChest", "context_conflict_blocks_loot_guard", guardFloor);
			return;
		}
		for (String reason : LootFloorGuards.evaluateChest(guardFloor, pendingLootChestTitle, entries)) {
			String key = "chest|" + reason;
			if (!lootGuardWarnedKeys.add(key)) continue;
			String report = buildLootGuardReport(reason, null);
			DungeonRunTracker.LOGGER.warn(report.replace("\n", " | "));
			notifyLootGuardInChat(report);
		}
	}

	private String buildLootGuardReport(String reason, DungeonLootEntry flaggedEntry) {
		Minecraft client = Minecraft.getInstance();
		long now = System.currentTimeMillis();
		StringBuilder sb = new StringBuilder(768);
		sb.append("=== DRT LOOT-GUARD REPORT ===\n");
		sb.append("Please DM vyriv on Discord with this entire message.\n");
		sb.append('\n');
		sb.append("timeUtc=").append(java.time.Instant.ofEpochMilli(now)).append('\n');
		sb.append("modVersion=").append(drtModVersion()).append('\n');
		sb.append("minecraft=").append(client.getVersionType()).append(' ').append(minecraftVersionName()).append('\n');
		sb.append("server=").append(connectedServerLabel(client)).append('\n');
		sb.append("player=").append(playerName(client)).append('\n');
		sb.append('\n');
		sb.append("--- detection ---\n");
		sb.append("reason=").append(reason).append('\n');
		sb.append("kept=true (soft guard; loot was not discarded)\n");
		if (flaggedEntry != null) {
			sb.append("flaggedItem='").append(nullToEmpty(flaggedEntry.rawName)).append("'\n");
			sb.append("flaggedItemId=").append(nullToEmpty(flaggedEntry.itemId)).append('\n');
			sb.append("flaggedQty=").append(Math.max(1, flaggedEntry.quantity)).append('\n');
		} else {
			sb.append("flaggedItem=(chest-level check)\n");
		}
		sb.append('\n');
		sb.append("--- floor context ---\n");
		sb.append("pendingLootFloor=").append(floorName(pendingLootFloor)).append('\n');
		sb.append("currentFloor=").append(floorName(currentFloor)).append('\n');
		sb.append("selectedHudFloor=").append(selectedFloor == null ? "ALL" : selectedFloor.name()).append('\n');
		sb.append("lastKnownKuudraFloor=").append(floorName(lastKnownKuudraFloor)).append('\n');
		sb.append("pendingSPlusFloor=").append(floorName(pendingSPlusFloor)).append('\n');
		sb.append("awaitingExtraStatsFloor=").append(floorName(awaitingExtraStatsFloor)).append('\n');
		sb.append("lastRunRecordFloor=").append(floorName(lastRunRecordFloor)).append('\n');
		sb.append("lastRunRecordGrade=").append(nullToEmpty(lastRunRecordGrade)).append('\n');
		sb.append("lastRunRecordAgeMs=").append(lastRunRecordMillis > 0L ? Math.max(0L, now - lastRunRecordMillis) : -1L).append('\n');
		sb.append("insideDungeon=").append(insideDungeon).append('\n');
		sb.append("insideKuudra=").append(insideKuudra).append('\n');
		sb.append("inDungeonHub=").append(inDungeonHub).append('\n');
		sb.append("inCrimsonIsle=").append(inCrimsonIsle).append('\n');
		sb.append("currentFloorIsKuudra=").append(isCurrentFloorKuudra()).append('\n');
		sb.append("kuudraFaction=").append(nullToEmpty(DrtConfigManager.getConfig().kuudraFaction)).append('\n');
		sb.append("kuudraReputation=").append(DrtConfigManager.getConfig().kuudraReputationKnown
			? Integer.toString(DrtConfigManager.getConfig().kuudraReputation)
			: "unknown").append('\n');
		sb.append("kuudraKeyCoinDiscountPercent=").append(DrtConfigManager.getConfig().kuudraReputationKnown
			? DungeonProfitPricing.kuudraKeyCoinDiscountPercent(DrtConfigManager.getConfig().kuudraReputation)
			: 0).append('\n');
		sb.append("dungeonSignalMsLeft=").append(Math.max(0L, dungeonSignalUntilMillis - now)).append('\n');
		sb.append("kuudraSignalMsLeft=").append(Math.max(0L, kuudraSignalUntilMillis - now)).append('\n');
		sb.append('\n');
		sb.append("--- chest / run ---\n");
		sb.append("chestTitle='").append(nullToEmpty(pendingLootChestTitle)).append("'\n");
		sb.append("chestAssigned=").append(pendingLootChestAssigned).append('\n');
		sb.append("seededFromGui=").append(pendingLootSeededFromGui).append('\n');
		sb.append("runNumber=").append(pendingLootRunNumber).append('\n');
		sb.append("runTimestampMs=").append(pendingLootRunTimestamp).append('\n');
		sb.append("currentRunActive=").append(currentRunActive).append('\n');
		sb.append("runCountedThisDungeon=").append(runCountedThisDungeon).append('\n');
		sb.append("gradePending=").append(pendingScoreGrade == null ? "" : pendingScoreGrade).append('\n');
		sb.append("gradeLast=").append(nullToEmpty(lastRecordedGrade)).append('\n');
		sb.append("openedChestsThisWindow=").append(openedRewardChestsInLootWindow).append('\n');
		sb.append("lootWindowMsLeft=").append(Math.max(0L, lootWindowUntilMillis - now)).append('\n');
		sb.append("lootCollectionMsLeft=").append(Math.max(0L, lootCollectionUntilMillis - now)).append('\n');
		ChestCostBreakdown cost = pendingLootCostBreakdown == null ? new ChestCostBreakdown() : pendingLootCostBreakdown;
		sb.append("cost.base=").append(cost.baseChestCostCoins)
			.append(" key=").append(cost.dungeonChestKeyCostCoins)
			.append(" kismet=").append(cost.kismetFeatherCostCoins)
			.append(" wheel=").append(cost.wheelOfFateCostCoins)
			.append(" kuudraKey=").append(cost.kuudraKeyCostCoins)
			.append(" total=").append(cost.totalCostCoins()).append('\n');
		sb.append("cost.flags key=").append(cost.usedDungeonChestKey)
			.append(" kismet=").append(cost.usedKismetFeather)
			.append(" wheel=").append(cost.usedWheelOfFate)
			.append(" kuudraKey=").append(cost.usedKuudraKey).append('\n');
		sb.append('\n');
		sb.append("--- pending loot (").append(pendingLootEntries.size()).append(") ---\n");
		if (pendingLootEntries.isEmpty()) {
			sb.append("(none)\n");
		} else {
			int i = 0;
			for (DungeonLootEntry e : pendingLootEntries) {
				if (e == null) continue;
				i++;
				boolean flagged = flaggedEntry != null
					&& lootKey(e).equals(lootKey(flaggedEntry));
				sb.append(i).append(flagged ? " [FLAGGED] " : " ")
					.append(nullToEmpty(e.rawName))
					.append(" | id=").append(nullToEmpty(e.itemId))
					.append(" | x").append(Math.max(1, e.quantity))
					.append('\n');
			}
		}
		List<DungeonLootEntry> authoritative = authoritativePendingLootEntries();
		if (authoritative.size() != pendingLootEntries.size()) {
			sb.append("authoritativeLootCount=").append(authoritative.size()).append('\n');
		}
		sb.append('\n');
		sb.append("--- end ---\n");
		return sb.toString();
	}

	private static String drtModVersion() {
		return net.fabricmc.loader.api.FabricLoader.getInstance()
			.getModContainer(DungeonRunTracker.MOD_ID)
			.map(c -> c.getMetadata().getVersion().getFriendlyString())
			.orElse("unknown");
	}

	private static String minecraftVersionName() {
		return net.minecraft.SharedConstants.getCurrentVersion().name();
	}

	private static String connectedServerLabel(Minecraft client) {
		if (client.getConnection() != null && client.getConnection().getServerData() != null) {
			return nullToEmpty(client.getConnection().getServerData().ip);
		}
		if (client.getSingleplayerServer() != null) return "singleplayer";
		return "unknown";
	}

	private static String playerName(Minecraft client) {
		if (client.player == null) return "?";
		return nullToEmpty(client.player.getGameProfile().name());
	}

	private static String floorName(DungeonFloor floor) {
		return floor == null ? "null" : floor.name();
	}

	private static DungeonFloor configFloor(String floor) {
		if (floor == null || floor.isBlank()) return DungeonFloor.UNKNOWN;
		try {
			return DungeonFloor.valueOf(floor.trim().toUpperCase(Locale.ROOT));
		} catch (IllegalArgumentException ignored) {
			return DungeonFloor.UNKNOWN;
		}
	}

	private static String nullToEmpty(String value) {
		return value == null ? "" : value;
	}

	private Map<String, Object> diagnosticEnvironment() {
		Minecraft client = Minecraft.getInstance();
		return diagnosticPayload(
			"reportSchema", DiagnosticRecorder.REPORT_SCHEMA,
			"modVersion", drtModVersion(),
			"minecraft", minecraftVersionName(),
			"java", System.getProperty("java.version", "unknown"),
			"os", System.getProperty("os.name", "unknown"),
			"server", client == null ? "unknown" : connectedServerLabel(client)
		);
	}

	private Map<String, Object> diagnosticState() {
		Map<String, Object> state = new LinkedHashMap<>(diagnosticPayload(
			"activeRunSessionId", activeRunSessionId,
			"pendingChestSessionId", pendingChestSessionId,
			"currentFloor", floorName(currentFloor),
			"pendingLootFloor", floorName(pendingLootFloor),
			"pendingLootOrphaned", pendingLootOrphaned,
			"pendingLootChestTitle", pendingLootChestTitle,
			"pendingLootRunNumber", pendingLootRunNumber,
			"currentRunActive", currentRunActive,
			"runCountedThisDungeon", runCountedThisDungeon,
			"completionFingerprint", currentRunCompletionFingerprint,
			"insideDungeon", insideDungeon,
			"insideKuudra", insideKuudra,
			"pendingLootEntries", pendingLootEntries.size()
		));
		return state;
	}

	private Map<String, Object> diagnosticState(DiagnosticIncident incident) {
		Map<String, Object> state = new LinkedHashMap<>(diagnosticState());
		if (incident != null && SyntheticDiagnosticIncidentFactory.INCIDENT_TYPE.equals(incident.incidentType())) {
			state.putAll(SyntheticDiagnosticIncidentFactory.expected(incident));
		}
		return state;
	}

	private Map<String, Object> diagnosticExpected(DiagnosticIncident incident) {
		Map<String, Object> expected = new LinkedHashMap<>(diagnosticPayload(
			"invariants", diagnostics.incidents().stream()
				.flatMap(existingIncident -> existingIncident.invariants().stream())
				.map(Enum::name)
				.distinct()
				.toList(),
			"activeRunSessionId", activeRunSessionId,
			"pendingChestSessionId", pendingChestSessionId,
			"pendingLootOrphaned", pendingLootOrphaned
		));
		if (incident != null && SyntheticDiagnosticIncidentFactory.INCIDENT_TYPE.equals(incident.incidentType())) {
			expected.putAll(SyntheticDiagnosticIncidentFactory.expected(incident));
		}
		return expected;
	}

	private Map<String, Object> diagnosticPayload(Object... keyValues) {
		Map<String, Object> payload = new LinkedHashMap<>();
		for (int i = 0; i + 1 < keyValues.length; i += 2) {
			Object key = keyValues[i];
			Object value = keyValues[i + 1];
			if (key != null && value != null) payload.put(String.valueOf(key), value);
		}
		return payload;
	}

	private void notifyTrackingIncidentInChat(DiagnosticIncident incident) {
		if (incident == null || incident.userNotified()) return;
		Minecraft client = Minecraft.getInstance();
		if (client.player == null) {
			pendingIncidentChatNotifyId = incident.id();
			return;
		}
		pendingIncidentChatNotifyId = "";
		incident.markUserNotified();

		Path replayPath = null;
		try {
			replayPath = diagnostics.saveReplayBundle(
				diagnosticReplayParentDir(),
				incident,
				diagnosticExpected(incident),
				diagnosticState(incident)
			);
		} catch (Exception e) {
			DungeonRunTracker.LOGGER.warn("[DRT] Failed to auto-save diagnostic bug export: {}", e.getMessage());
		}

		String reportId = incident.id();
		String pathText = replayPath == null ? "" : replayPath.toAbsolutePath().toString();
		DungeonRunTracker.LOGGER.warn(
			"[DRT] Tracking issue detected reportId={} path='{}'",
			reportId,
			pathText.isBlank() ? "(unsaved)" : pathText
		);

		var message = Component.literal("[DRT] Tracking issue detected (report " + reportId + ").\n")
			.withStyle(Style.EMPTY.withColor(ChatFormatting.GOLD))
			.append(Component.literal("Chest/run ownership looked inconsistent. Loot may be unassigned.\n")
				.withStyle(ChatFormatting.YELLOW));
		if (!pathText.isBlank()) {
			message = message.append(Component.literal("Bug zip saved:\n")
					.withStyle(ChatFormatting.GRAY))
				.append(Component.literal(pathText + "\n")
					.withStyle(Style.EMPTY
						.withColor(ChatFormatting.AQUA)
						.withClickEvent(new ClickEvent.CopyToClipboard(pathText))
						.withHoverEvent(new HoverEvent.ShowText(Component.literal("Click to copy zip path")))));
		}
		message = message.append(Component.literal("[Copy Bug Report]")
			.withStyle(Style.EMPTY
				.withColor(ChatFormatting.AQUA)
				.withUnderlined(true)
				.withClickEvent(new ClickEvent.SuggestCommand("/drt debug exportbug " + reportId))
				.withHoverEvent(new HoverEvent.ShowText(Component.literal("Click to fill /drt debug exportbug " + reportId)))));
		sendDrtSystemMessage(client, message);
	}

	private void flushPendingIncidentChatNotify() {
		if (pendingIncidentChatNotifyId == null || pendingIncidentChatNotifyId.isBlank()) return;
		DiagnosticIncident incident = diagnostics.incidentById(pendingIncidentChatNotifyId);
		if (incident == null) {
			pendingIncidentChatNotifyId = "";
			return;
		}
		notifyTrackingIncidentInChat(incident);
	}

	public boolean triggerSyntheticDiagnosticIncident(String mode) {
		String normalized = mode == null ? "" : mode.trim().toLowerCase(Locale.ROOT);
		String rootKey = SyntheticDiagnosticIncidentFactory.DEFAULT_ROOT_KEY;
		int repeats = "duplicate".equals(normalized) ? 3 : 1;
		if ("new".equals(normalized)) {
			rootKey = SyntheticDiagnosticIncidentFactory.DEFAULT_ROOT_KEY + "|new|" + UUID.randomUUID();
		}
		DiagnosticIncident first = null;
		for (int i = 0; i < repeats; i++) {
			DiagnosticIncident incident = SyntheticDiagnosticIncidentFactory.record(diagnostics, rootKey, i);
			if (first == null) first = incident;
			notifyTrackingIncidentInChat(incident);
		}
		DungeonRunTracker.LOGGER.info(
			"[DRT] Synthetic diagnostic incident triggered: mode={} reportId={} repeats={}",
			normalized.isBlank() ? "default" : normalized,
			first == null ? "" : first.id(),
			repeats
		);
		return first != null;
	}

	public boolean copyDiagnosticReportToClipboard(String reportId) {
		DiagnosticIncident incident = diagnostics.incidentById(reportId);
		Minecraft client = Minecraft.getInstance();
		if (client.player == null) return false;
		if (incident == null) {
			sendDrtSystemMessage(client, Component.literal("§c[DRT] Diagnostic report not found: " + nullToEmpty(reportId)));
			return false;
		}
		String report = diagnostics.buildHumanReport(incident, diagnosticState(incident));
		client.keyboardHandler.setClipboard(report);
		sendDrtSystemMessage(client, Component.literal("§a[DRT] Diagnostic report copied to clipboard."));
		return true;
	}

	public boolean saveDiagnosticReplay(String reportId) {
		return exportDiagnosticBug(reportId);
	}

	public boolean exportDiagnosticBug(String reportId) {
		DiagnosticIncident incident = diagnostics.incidentById(reportId);
		Minecraft client = Minecraft.getInstance();
		if (client.player == null) return false;
		if (incident == null) {
			sendDrtSystemMessage(client, Component.literal("§c[DRT] Diagnostic report not found: " + nullToEmpty(reportId)));
			return false;
		}
		try {
			Path replayPath = diagnostics.saveReplayBundle(
				diagnosticReplayParentDir(),
				incident,
				diagnosticExpected(incident),
				diagnosticState(incident)
			);
			return copyDiagnosticZipPathToClipboard(client, replayPath);
		} catch (Exception e) {
			DungeonRunTracker.LOGGER.warn("[DRT] Failed to save diagnostic bug export: {}", e.getMessage());
			sendDrtSystemMessage(client, Component.literal("§c[DRT] Failed to export bug: " + e.getMessage()));
			return false;
		}
	}

	public boolean copyDiagnosticZipToClipboard(String reportId) {
		DiagnosticIncident incident = diagnostics.incidentById(reportId);
		Minecraft client = Minecraft.getInstance();
		if (client.player == null) return false;
		if (incident == null) {
			sendDrtSystemMessage(client, Component.literal("§c[DRT] Diagnostic report not found: " + nullToEmpty(reportId)));
			return false;
		}
		Path replayPath;
		try {
			replayPath = diagnosticReplayZipPath(incident);
		} catch (Exception e) {
			DungeonRunTracker.LOGGER.warn("[DRT] Failed to prepare diagnostic zip for clipboard: {}", e.getMessage());
			sendDrtSystemMessage(client, Component.literal("§c[DRT] Failed to prepare bug export zip: " + e.getMessage()));
			return false;
		}

		return copyDiagnosticZipPathToClipboard(client, replayPath);
	}

	private boolean copyDiagnosticZipPathToClipboard(Minecraft client, Path replayPath) {
		try {
			Toolkit.getDefaultToolkit()
				.getSystemClipboard()
				.setContents(new SingleFileTransferable(replayPath.toAbsolutePath().toFile()), null);
			sendDrtSystemMessage(client, Component.literal("§a[DRT] Bug report copied. Please dm it to Vyirv on discord :)."));
			return true;
		} catch (Throwable copyFailure) {
			DungeonRunTracker.LOGGER.warn("[DRT] Failed to copy diagnostic zip file to clipboard: {}", copyFailure.getMessage());
			openDiagnosticZipFolder(client, replayPath);
			return false;
		}
	}

	private Path diagnosticReplayZipPath(DiagnosticIncident incident) throws IOException {
		if (incident == null) throw new IOException("Missing diagnostic incident");
		String replayPath = incident.replayPath();
		if (replayPath != null && !replayPath.isBlank()) {
			Path path = Path.of(replayPath);
			if (Files.isRegularFile(path)) return path;
		}
		return diagnostics.saveReplayBundle(
			diagnosticReplayParentDir(),
			incident,
			diagnosticExpected(incident),
			diagnosticState(incident)
		);
	}

	private void openDiagnosticZipFolder(Minecraft client, Path replayPath) {
		Path folder = replayPath == null ? diagnosticReplayParentDir() : replayPath.toAbsolutePath().getParent();
		if (folder == null) folder = diagnosticReplayParentDir();
		try {
			openFolder(folder);
			sendDrtSystemMessage(client, Component.literal("§e[DRT] Could not copy the ZIP directly, so opened the export folder."));
		} catch (Throwable openFailure) {
			DungeonRunTracker.LOGGER.warn("[DRT] Failed to open diagnostic export folder: {}", openFailure.getMessage());
			sendDrtSystemMessage(client, Component.literal("§c[DRT] Could not copy the ZIP or open the export folder."));
		}
	}

	private static void openFolder(Path folder) throws IOException {
		if (folder == null) throw new IOException("Missing export folder");
		File file = folder.toFile();
		if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.OPEN)) {
			Desktop.getDesktop().open(file);
			return;
		}
		String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
		if (os.contains("win")) {
			new ProcessBuilder("explorer", file.getAbsolutePath()).start();
		} else if (os.contains("mac")) {
			new ProcessBuilder("open", file.getAbsolutePath()).start();
		} else {
			new ProcessBuilder("xdg-open", file.getAbsolutePath()).start();
		}
	}

	private Path diagnosticReplayParentDir() {
		return net.fabricmc.loader.api.FabricLoader.getInstance().getConfigDir()
			.resolve("drt")
			.resolve("replays");
	}

	private void sendDrtSystemMessage(Minecraft client, Component message) {
		if (client == null || client.player == null || message == null) return;
		//? if >= 26.1 {
		client.player.sendSystemMessage(message);
		//? } else {
		/*client.player.displayClientMessage(message, false);
		*///?}
	}

	private boolean menuTitleConflictsWithActiveRun(DungeonFloor titleFloor) {
		if (titleFloor == null || titleFloor == DungeonFloor.UNKNOWN) return false;
		if (!currentRunActive && (activeRunSessionId == null || activeRunSessionId.isBlank())) return false;
		DungeonFloor activeFloor = activeRunFloorProjection();
		if (activeFloor == DungeonFloor.UNKNOWN) return false;
		return activeFloor != titleFloor;
	}

	private boolean hasLootContextConflict() {
		ChestSession chest = trackingSession.chest(pendingChestSessionId);
		if (chest != null && chest.state() == dev.vy.drt.tracking.ChestState.CONFLICTED) return true;
		DungeonFloor chestFloor = authoritativePendingChestFloor();
		if (chestFloor == DungeonFloor.UNKNOWN) return false;
		DungeonFloor activeFloor = activeRunFloorProjection();
		if (activeFloor == DungeonFloor.UNKNOWN) return false;
		if (pendingLootRunNumber > 0 && !pendingLootOrphaned) return false;
		return chestFloor != activeFloor;
	}

	private void recordContextConflictDiagnostic(String handler, String reason, DungeonFloor incomingFloor) {
		DetectionEvent event = diagnostics.recordEvent(
			DetectionEventType.RUN_EVIDENCE,
			DetectionSource.GUI_TITLE_INFERENCE,
			diagnosticPayload(
				"incomingFloor", floorName(incomingFloor),
				"currentFloor", floorName(activeRunFloorProjection()),
				"pendingLootFloor", floorName(pendingLootFloor),
				"activeRunSessionId", activeRunSessionId,
				"pendingChestSessionId", pendingChestSessionId
			)
		);
		DiagnosticIncident incident = diagnostics.recordInvariantViolation(
			TrackerInvariant.CONTEXT_CONFLICT_BLOCKS_NORMAL_LOOT_GUARD,
			DiagnosticSeverity.ERROR,
			event,
			"context-conflict|" + floorName(activeRunFloorProjection()) + "|" + floorName(incomingFloor) + "|" + pendingChestSessionId,
			"Reward context conflicted with active run context. The active run was preserved and normal floor-specific loot guard checks were blocked.",
			"DungeonRunTrackerFeature." + handler,
			handler,
			"REJECT_CONFLICTING_CONTEXT",
			reason,
			activeRunSessionId,
			pendingChestSessionId,
			"",
			"context|" + floorName(activeRunFloorProjection()) + "|" + floorName(incomingFloor),
			diagnosticPayload(
				"currentFloor", floorName(activeRunFloorProjection()),
				"incomingFloor", floorName(incomingFloor),
				"pendingLootFloor", floorName(pendingLootFloor),
				"pendingLootOrphaned", pendingLootOrphaned
			)
		);
		notifyTrackingIncidentInChat(incident);
	}

	private void recordCompletionDuplicateDiagnostic(String incomingFingerprint, long now, String floor, String grade) {
		DetectionEvent event = diagnostics.recordEvent(
			DetectionEventType.RUN_COMPLETED,
			DetectionSource.CONFIRMED_COMPLETION,
			diagnosticPayload(
				"incomingFingerprint", incomingFingerprint,
				"existingFingerprint", currentRunCompletionFingerprint,
				"floor", floor,
				"grade", grade,
				"atMillis", now
			)
		);
		DiagnosticIncident incident = diagnostics.recordInvariantViolation(
			TrackerInvariant.ONE_RUN_COMPLETION_COUNTS_AT_MOST_ONCE,
			DiagnosticSeverity.WARN,
			event,
			"completion-duplicate|" + activeRunSessionId,
			"A completion signal arrived for a RunSession that was already completed. It was ignored.",
			"DungeonRunTrackerFeature.recordCompletedRun",
			"recordCompletedRun",
			"IGNORE_DUPLICATE_COMPLETION",
			"run_session_already_completed",
			activeRunSessionId,
			pendingChestSessionId,
			"",
			incomingFingerprint,
			diagnosticPayload("existingFingerprint", currentRunCompletionFingerprint, "incomingFingerprint", incomingFingerprint)
		);
		notifyTrackingIncidentInChat(incident);
	}

	private void recordCompletionDuplicateDecision(String incomingFingerprint, long now, String floor, String grade) {
		DetectionEvent event = diagnostics.recordEvent(
			DetectionEventType.RUN_COMPLETED,
			DetectionSource.CONFIRMED_COMPLETION,
			diagnosticPayload(
				"incomingFingerprint", incomingFingerprint,
				"existingFingerprint", currentRunCompletionFingerprint,
				"floor", floor,
				"grade", grade,
				"atMillis", now
			)
		);
		diagnostics.recordDecision(
			event,
			"recordCompletedRun",
			"IGNORE_DUPLICATE_COMPLETION",
			"same_completion_signal",
			activeRunSessionId,
			pendingChestSessionId,
			"",
			incomingFingerprint,
			diagnosticPayload("existingFingerprint", currentRunCompletionFingerprint, "incomingFingerprint", incomingFingerprint)
		);
	}

	private void recordCompletionPersistenceDiagnostic(
		dev.vy.drt.config.RunRecordCommitDecision decision,
		String completionFingerprint,
		long now,
		String floor,
		String grade,
		long runTimeMs
	) {
		DetectionEvent event = diagnostics.recordEvent(
			DetectionEventType.PERSISTENCE,
			DetectionSource.PERSISTENCE,
			diagnosticPayload(
				"decision", decision == null ? "" : decision.name(),
				"completionFingerprint", completionFingerprint,
				"floor", floor,
				"grade", grade,
				"runTimeMs", runTimeMs,
				"atMillis", now
			)
		);
		DiagnosticIncident incident = diagnostics.recordInvariantViolation(
			TrackerInvariant.ONE_RUN_COMPLETION_COUNTS_AT_MOST_ONCE,
			decision == dev.vy.drt.config.RunRecordCommitDecision.CONFLICT ? DiagnosticSeverity.ERROR : DiagnosticSeverity.WARN,
			event,
			"completion-persistence|" + completionFingerprint,
			"Run completion persistence rejected a completed run. DRT did not add this completion to history.",
			"DungeonRunTrackerFeature.recordCompletedRun/DrtConfigManager.addRunCompletionRecord",
			"recordCompletedRun",
			decision == null ? "UNKNOWN" : decision.name(),
			"completion_persistence_rejected",
			activeRunSessionId,
			pendingChestSessionId,
			"",
			completionFingerprint,
			diagnosticPayload(
				"decision", decision == null ? "" : decision.name(),
				"completionFingerprint", completionFingerprint,
				"floor", floor,
				"grade", grade,
				"runTimeMs", runTimeMs
			)
		);
		notifyTrackingIncidentInChat(incident);
	}

	private void recordOrphanChestDiagnostic(String reason, long now) {
		recordOrphanChestDiagnostic(reason, now, true);
	}

	private void recordOrphanChestDiagnostic(String reason, long now, boolean notifyUser) {
		DetectionEvent event = diagnostics.recordEvent(
			DetectionEventType.CHEST_OPENED,
			DetectionSource.RECENT_CONTEXT,
			diagnosticPayload(
				"reason", reason,
				"atMillis", now,
				"currentFloor", floorName(currentFloor),
				"pendingLootFloor", floorName(pendingLootFloor),
				"pendingChestSessionId", pendingChestSessionId,
				"notifyUser", notifyUser
			)
		);
		DiagnosticIncident incident = diagnostics.recordInvariantViolation(
			TrackerInvariant.ORPHAN_CHEST_CANNOT_SILENTLY_INVENT_RUN,
			DiagnosticSeverity.WARN,
			event,
			"orphan-chest|" + pendingChestSessionId,
			"Reward loot appeared without deterministic run ownership. DRT kept it unassigned instead of inventing a synthetic run.",
			"DungeonRunTrackerFeature.startAdHocLootWindow/flushPendingLootRecord",
			"orphanChest",
			"KEEP_ORPHAN_UNCOMMITTED",
			reason,
			activeRunSessionId,
			pendingChestSessionId,
			"",
			pendingChestSessionId,
			diagnosticState()
		);
		if (notifyUser) {
			notifyTrackingIncidentInChat(incident);
		} else {
			DungeonRunTracker.LOGGER.info(
				"[DRT] Unowned reward chest kept unassigned (expected historical/no-recent-run open): chest={}",
				pendingChestSessionId
			);
			incident.markUserNotified();
		}
	}

	private void recordDuplicateCommitDiagnostic(dev.vy.drt.config.RunRecordCommitDecision decision, DungeonRunRecord record) {
		DetectionEvent event = diagnostics.recordEvent(
			DetectionEventType.PERSISTENCE,
			DetectionSource.PERSISTENCE,
			diagnosticPayload(
				"decision", decision,
				"commitFingerprint", record == null ? "" : record.commitFingerprint,
				"chestSessionId", record == null ? "" : record.chestSessionId
			)
		);
		DiagnosticIncident incident = diagnostics.recordInvariantViolation(
			TrackerInvariant.DUPLICATE_SIGNAL_CANNOT_CREATE_DUPLICATE_RECORD,
			decision == dev.vy.drt.config.RunRecordCommitDecision.CONFLICT ? DiagnosticSeverity.ERROR : DiagnosticSeverity.WARN,
			event,
			"duplicate-commit|" + (record == null ? "" : record.commitFingerprint),
			"A duplicate or conflicting chest commit reached persistence. The persistence layer did not append a duplicate record.",
			"DrtConfigManager.addRunRecord",
			"flushPendingLootRecord",
			decision == null ? "UNKNOWN" : decision.name(),
			"history_commit_deduplicated",
			record == null ? "" : record.runSessionId,
			record == null ? "" : record.chestSessionId,
			"",
			record == null ? "" : record.commitFingerprint,
			diagnosticPayload("decision", decision == null ? "" : decision.name())
		);
		notifyTrackingIncidentInChat(incident);
	}

	private void recordLootDedupDiagnostic(String key, String cleaned) {
		DetectionEvent event = diagnostics.recordEvent(
			DetectionEventType.LOOT_OBSERVED,
			DetectionSource.STRUCTURED_CHAT,
			diagnosticPayload("dedupKey", key, "line", cleaned, "pendingChestSessionId", pendingChestSessionId)
		);
		diagnostics.recordDecision(
			event,
			"markLootLineForProcessing",
			"IGNORE_DUPLICATE_LOOT_LINE",
			"chest_lifetime_dedup",
			activeRunSessionId,
			pendingChestSessionId,
			"",
			key,
			diagnosticPayload("line", cleaned)
		);
	}

	private void recordUnresolvedItemDiagnostic(String rawName, String normalizedName) {
		if (normalizedName == null || normalizedName.isBlank()) return;
		DetectionEvent event = diagnostics.recordEvent(
			DetectionEventType.LOOT_OBSERVED,
			DetectionSource.STRUCTURED_CHAT,
			diagnosticPayload("rawName", rawName, "normalizedName", normalizedName, "pendingChestSessionId", pendingChestSessionId)
		);
		DiagnosticIncident incident = diagnostics.recordInvariantViolation(
			TrackerInvariant.UNKNOWN_ITEM_CANNOT_USE_FIRST_SEARCH_RESULT,
			DiagnosticSeverity.WARN,
			event,
			"unresolved-item|" + normalizedName,
			"Loot identity was not an exact component ID, strict alias, or deterministic mapping. It was left unresolved.",
			"DungeonRunTrackerFeature.resolveItemId",
			"resolveItemId",
			"KEEP_UNRESOLVED",
			"no_strict_identity_mapping",
			activeRunSessionId,
			pendingChestSessionId,
			"",
			normalizedName,
			diagnosticPayload("rawName", rawName, "normalizedName", normalizedName)
		);
		if (!incident.userNotified()) DungeonRunTracker.LOGGER.warn("[DRT] Unresolved loot identity: raw='{}' normalized='{}'", rawName, normalizedName);
	}

	private String openTrackingChestSession(String displayTitle, int containerId, DetectionSource source) {
		String ownerRunId = pendingLootRunNumber > 0 && !pendingLootOrphaned ? activeRunSessionId : "";
		String chestId = trackingSession.openChest(
			ownerRunId,
			displayTitle == null ? "" : displayTitle,
			containerId,
			source == null ? DetectionSource.CONFIRMED_GUI_COMPONENT : source
		).id();
		updateChestContextProjection(
			chestId,
			pendingLootFloor == null ? DungeonFloor.UNKNOWN : pendingLootFloor,
			pendingLootOrphaned ? EvidenceStrength.GUI_TITLE_INFERENCE : EvidenceStrength.AUTHORITATIVE_INTERNAL_IDENTITY,
			pendingLootOrphaned ? DetectionSource.GUI_TITLE_INFERENCE : DetectionSource.AUTHORITATIVE_INTERNAL_IDENTITY
		);
		return chestId;
	}

	private String nextChestSessionId(String ownerRunId) {
		String owner = ownerRunId == null || ownerRunId.isBlank() ? clientTrackingInstanceId + "-orphan" : ownerRunId;
		return owner + "-chest-" + (++chestSessionSequence);
	}

	private String buildLootCommitFingerprint(DungeonRunRecord record) {
		StringBuilder sb = new StringBuilder();
		sb.append(record == null ? "" : nullToEmpty(record.runSessionId)).append('|')
			.append(record == null ? "" : nullToEmpty(record.chestSessionId)).append('|')
			.append(record == null ? "" : record.timestampEpochMillis).append('|')
			.append(record == null ? "" : nullToEmpty(record.floor)).append('|')
			.append(record == null ? "" : nullToEmpty(record.grade)).append('|')
			.append(record == null ? "" : nullToEmpty(record.chestTitle)).append('|')
			.append(record == null ? "" : record.totalCostCoins()).append('|');
		if (record != null && record.lootEntries != null) {
			for (DungeonLootEntry entry : record.lootEntries) {
				if (entry == null) continue;
				sb.append(lootKey(entry)).append('=').append(Math.max(1, entry.quantity)).append(';');
			}
		}
		return UUID.nameUUIDFromBytes(sb.toString().getBytes(StandardCharsets.UTF_8)).toString();
	}

	private void notifyLootGuardInChat(String report) {
		Minecraft client = Minecraft.getInstance();
		if (client.player == null) return;
		Component message = Component.literal("[DRT] Lootguard detected mismatched drop, click to copy error + dm vyriv on discord")
			.withStyle(Style.EMPTY
				.withColor(ChatFormatting.GOLD)
				.withUnderlined(true)
				.withClickEvent(new ClickEvent.CopyToClipboard(report))
				.withHoverEvent(new HoverEvent.ShowText(Component.literal("Click to copy full diagnostic report for vyriv"))));
		//? if >= 26.1 {
		client.player.sendSystemMessage(message);
		//? } else {
		/*client.player.displayClientMessage(message, false);
		*///?}
	}

	private void flushPendingLootRecord() {
		flushPendingLootRecord(false);
	}

	private List<DungeonLootEntry> authoritativePendingLootEntries() {
		ChestSession chest = trackingSession.chest(pendingChestSessionId);
		if (chest == null) {
			return new ArrayList<>(pendingLootEntries);
		}
		List<ResolvedLoot> resolvedLoot = chest.resolvedLoot();
		if (resolvedLoot.isEmpty()) {
			return new ArrayList<>(pendingLootEntries);
		}
		List<DungeonLootEntry> entries = new ArrayList<>(resolvedLoot.size());
		for (ResolvedLoot resolved : resolvedLoot) {
			entries.add(new DungeonLootEntry(
				resolved.rawName(),
				resolved.resolved() ? resolved.itemId() : "",
				resolved.quantity()
			));
		}
		return entries;
	}

	private ChestCostBreakdown authoritativePendingChestCost() {
		ChestSession chest = trackingSession.chest(pendingChestSessionId);
		if (chest != null) {
			return chest.cost();
		}
		return pendingLootCostBreakdown == null ? new ChestCostBreakdown() : pendingLootCostBreakdown.copy();
	}

	private DungeonFloor authoritativePendingChestFloor() {
		ChestSession chest = trackingSession.chest(pendingChestSessionId);
		if (chest != null && chest.contextFloor().isKnown()) {
			DungeonFloor floor = chest.contextFloor().value();
			return floor == null ? DungeonFloor.UNKNOWN : floor;
		}
		return pendingLootFloor == null ? DungeonFloor.UNKNOWN : pendingLootFloor;
	}

	private void updatePendingChestContextProjection(EvidenceStrength strength, DetectionSource source) {
		if (pendingChestSessionId == null || pendingChestSessionId.isBlank()) return;
		DungeonFloor floor = pendingLootFloor == null ? DungeonFloor.UNKNOWN : pendingLootFloor;
		updateChestContextProjection(pendingChestSessionId, floor, strength, source);
	}

	private void updateChestContextProjection(String chestId, DungeonFloor floor, EvidenceStrength strength, DetectionSource source) {
		if (chestId == null || chestId.isBlank()) return;
		if (floor == DungeonFloor.UNKNOWN) return;
		trackingSession.updateChestContextFloor(chestId, floor, strength, source);
		trackingSession.updateChestContextMode(
			chestId,
			floor.isKuudra() ? RunMode.KUUDRA : RunMode.DUNGEON,
			strength,
			source
		);
	}

	private void flushPendingLootRecord(boolean keepWindow) {
		List<DungeonLootEntry> committedLootEntries = authoritativePendingLootEntries();
		if (committedLootEntries.isEmpty()) {
			if (keepWindow) resetPendingChestState();
			else clearLootWindow();
			return;
		}
		boolean orphanCommit = canCommitUnassignedChestLoot();
		if ((pendingLootRunNumber <= 0 || pendingLootOrphaned) && !orphanCommit) {
			recordOrphanChestDiagnostic("orphan_loot_not_committed", System.currentTimeMillis());
			if (keepWindow) resetPendingChestState();
			else clearLootWindow();
			return;
		}
		DrtConfig config = DrtConfigManager.getConfig();
		for (DungeonLootEntry entry : committedLootEntries) {
			warnLootGuardsForEntry(entry);
		}
		warnLootGuardsForChest(committedLootEntries);
		long chestValueCoins = DungeonProfitPricing.calculateLootValue(committedLootEntries, config);
		ChestCostBreakdown costBreakdown = authoritativePendingChestCost();
		if (costBreakdown.usedKismetFeather) costBreakdown.kismetRerolledChestOpened = true;
		DungeonFloor kuudraFloor = currentKuudraFloorForPricing();
		if (!pendingLootOrphaned && pendingLootFloor == DungeonFloor.UNKNOWN && kuudraFloor != null && kuudraFloor.isKuudra()) {
			pendingLootFloor = kuudraFloor;
			updatePendingChestContextProjection(EvidenceStrength.RECENT_CONTEXT, DetectionSource.RECENT_CONTEXT);
		}
		String pendingChestTitle = pendingLootChestTitle == null ? "" : pendingLootChestTitle.toUpperCase(Locale.ROOT);
		applyKuudraChestCostHint(pendingChestTitle, costBreakdown);
		populateKnownModifierCosts(costBreakdown);
		suppressDungeonChestKeyForKuudra(pendingChestTitle, costBreakdown);
		long chestCostCoins = costBreakdown.totalCostCoins();
		long chestProfitCoins = chestValueCoins - chestCostCoins;
		DungeonRunTracker.LOGGER.info(
			"[DRT][FLUSH] entries={} value={} cost={} base={} key={} kismet={} wheel={} kuudraKey={} profit={} floor={} chest='{}'",
			committedLootEntries.size(),
			chestValueCoins,
			chestCostCoins,
			costBreakdown.baseChestCostCoins,
			costBreakdown.dungeonChestKeyCostCoins,
			costBreakdown.kismetFeatherCostCoins,
			costBreakdown.wheelOfFateCostCoins,
			costBreakdown.kuudraKeyCostCoins,
			chestProfitCoins,
			authoritativePendingChestFloor(),
			pendingLootChestTitle
		);
		DungeonFloor recordFloor = authoritativePendingChestFloor();
		String floorName = recordFloor != DungeonFloor.UNKNOWN ? recordFloor.name() : "UNKNOWN";
		String runGrade = orphanCommit ? "?" : pendingScoreGrade != null ? pendingScoreGrade : lastRecordedGrade;
		DungeonRunRecord record = new DungeonRunRecord(
			pendingLootRunTimestamp,
			orphanCommit ? 0 : pendingLootRunNumber,
			floorName,
			runGrade,
			pendingLootChestTitle,
			chestCostCoins,
			chestValueCoins,
			chestProfitCoins,
			committedLootEntries
		);
		record.runSessionId = orphanCommit ? "" : activeRunSessionId == null ? "" : activeRunSessionId;
		record.chestSessionId = pendingChestSessionId == null ? "" : pendingChestSessionId;
		record.commitFingerprint = buildLootCommitFingerprint(record);
		record.applyCostBreakdown(costBreakdown);
		var commitDecision = DrtConfigManager.addRunRecord(record);
		if (commitDecision == dev.vy.drt.config.RunRecordCommitDecision.ADD_INCOMING
			|| commitDecision == dev.vy.drt.config.RunRecordCommitDecision.REPLACE_EXISTING) {
			trackingSession.updateChestCost(record.chestSessionId, costBreakdown);
			trackingSession.commitChest(record.chestSessionId, record.commitFingerprint);
			if (!orphanCommit) {
				sessionTotalProfit += chestProfitCoins;
				sessionFloorProfitTotals.merge(floorName, chestProfitCoins, Long::sum);
			}
		} else {
			recordDuplicateCommitDiagnostic(commitDecision, record);
		}
		resyncLifetimeFromConfig();
		if (keepWindow) resetPendingChestState();
		else clearLootWindow();
	}

	private boolean canCommitUnassignedChestLoot() {
		if (!pendingLootOrphaned) return false;
		if (!pendingLootChestAssigned) return false;
		if (pendingChestSessionId == null || pendingChestSessionId.isBlank()) return false;
		ChestSession chest = trackingSession.chest(pendingChestSessionId);
		if (chest == null) return false;
		if (chest.state() == ChestState.COMMITTED) return false;
		return true;
	}

	private void resetPendingChestState() {
		lootCollectionUntilMillis = 0L;
		pendingChestSessionId = nextChestSessionId(pendingLootRunNumber > 0 ? activeRunSessionId : "");
		pendingLootOrphaned = pendingLootRunNumber <= 0;
		pendingLootChestTitle = "";
		pendingLootCostBreakdown = new ChestCostBreakdown();
		pendingLootSeededFromGui = false;
		pendingLootReconcilingGuiChat = false;
		pendingLootChestAssigned = false;
		pendingLootEntries.clear();
		lootGuardWarnedKeys.clear();
		recentLootMessages.clear();
		pendingChestLootDedupKeys.clear();
		ignoredPlayerInventoryDiagnosticKeys.clear();
	}

	private boolean markLootLineForProcessing(String cleaned, long now) {
		if (cleaned == null || cleaned.isBlank()) return true;
		String key = (pendingChestSessionId == null || pendingChestSessionId.isBlank()
			? Integer.toString(pendingLootRunNumber)
			: pendingChestSessionId) + "|" + cleaned.trim();
		if (!pendingChestLootDedupKeys.add(key)) {
			recordLootDedupDiagnostic(key, cleaned);
			return false;
		}
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

		String cleanedName = stripTrailingLootQuantity(sanitizeLootName(rawName)).toUpperCase(Locale.ROOT);
		// Shiny dungeon drops share the base item id/price (e.g. "Shiny Necron's Handle").
		if (cleanedName.startsWith("SHINY ")) {
			cleanedName = cleanedName.substring("SHINY ".length()).trim();
		}
		String alias = ITEM_ID_ALIASES.get(cleanedName);
		if (alias != null) return alias;

		String generatedKuudraId = generatedKuudraItemId(cleanedName);
		if (generatedKuudraId != null) return generatedKuudraId;

		recordUnresolvedItemDiagnostic(rawName, cleanedName);
		return "";
	}

	private record ParsedLootName(String name, int quantity) {}

	private ParsedLootName parseLootDisplayName(String rawName) {
		String sanitized = sanitizeLootName(rawName);
		if (sanitized.isBlank()) return new ParsedLootName("", 1);

		Matcher essenceMatcher = ESSENCE_PATTERN.matcher(sanitized.toUpperCase(Locale.ROOT));
		if (essenceMatcher.matches()) {
			String essenceName = essenceMatcher.group(1) + " ESSENCE";
			return new ParsedLootName(essenceName, parsePositiveInt(essenceMatcher.group(2), 1));
		}

		Matcher pre = QUANTITY_PREFIX_PATTERN.matcher(sanitized);
		if (pre.matches()) {
			return new ParsedLootName(pre.group(2).trim(), parsePositiveInt(pre.group(1), 1));
		}
		Matcher suf = QUANTITY_SUFFIX_PATTERN.matcher(sanitized);
		if (suf.matches()) {
			return new ParsedLootName(suf.group(1).trim(), parsePositiveInt(suf.group(2), 1));
		}
		Matcher trailing = TRAILING_QUANTITY_PATTERN.matcher(sanitized);
		if (trailing.matches()) {
			return new ParsedLootName(trailing.group(1).trim(), parsePositiveInt(trailing.group(2), 1));
		}
		return new ParsedLootName(sanitized, 1);
	}

	private String stripTrailingLootQuantity(String name) {
		if (name == null || name.isBlank()) return "";
		Matcher trailing = TRAILING_QUANTITY_PATTERN.matcher(name.trim());
		if (trailing.matches()) return trailing.group(1).trim();
		return name.trim();
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
		sanitized = sanitized.replace('\u00A0', ' ').replace('\u202F', ' ');
		sanitized = sanitized.replace('×', 'x').replace('✕', 'x').replace('✖', 'x');
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
		return isDrtClientMessage(value)
			|| normalized.contains("[NPC]") || normalized.contains("EXTRA STATS")
			|| normalized.contains("TEAM SCORE") || normalized.contains("CLICK")
			|| normalized.contains("OPEN") || normalized.contains("CROESUS")
			|| normalized.contains("THE CATACOMBS") || normalized.contains("KUUDRA DOWN")
			|| normalized.contains("PERCENTAGE COMPLETE") || normalized.contains("TOKENS EARNED")
			|| normalized.contains("BITS EARNED") || normalized.startsWith("TIME:")
			|| normalized.contains("[BAZAAR]") || normalized.contains("BAZAAR")
			|| normalized.contains("SOLD ") || normalized.contains("BOUGHT ")
			|| normalized.contains("COINS!") || normalized.contains("RNG METER")
			// Party rare-drop announcements only (not chest lines like "RARE REWARD! Recombobulator 3000")
			|| normalized.contains("FOUND A ") || normalized.contains("FOUND AN ")
			|| normalized.contains("IN THEIR ") || normalized.contains("IN HIS ") || normalized.contains("IN HER ");
	}

	private boolean isDrtClientMessage(String value) {
		if (value == null || value.isBlank()) return false;
		String normalized = value.trim().toUpperCase(Locale.ROOT);
		return normalized.startsWith("[DRT]")
			|| normalized.startsWith("[COPY REPORT]")
			|| normalized.startsWith("[COPY PATH]")
			|| normalized.startsWith("[SAVE REPLAY]")
			|| normalized.startsWith("[OPEN FOLDER]");
	}

	private static final class SingleFileTransferable implements Transferable {
		private final List<File> files;

		private SingleFileTransferable(File file) {
			this.files = List.of(file);
		}

		@Override
		public DataFlavor[] getTransferDataFlavors() {
			return new DataFlavor[] {DataFlavor.javaFileListFlavor};
		}

		@Override
		public boolean isDataFlavorSupported(DataFlavor flavor) {
			return DataFlavor.javaFileListFlavor.equals(flavor);
		}

		@Override
		public Object getTransferData(DataFlavor flavor) throws UnsupportedFlavorException {
			if (!isDataFlavorSupported(flavor)) throw new UnsupportedFlavorException(flavor);
			return files;
		}
	}

	private boolean shouldIgnoreLootName(String value) {
		if (value == null) return false;
		String sanitized = stripTrailingLootQuantity(sanitizeLootName(value)).toUpperCase(Locale.ROOT);
		return sanitized.equals("ANCIENT ROSE")
			|| sanitized.equals("ENCHANTED BOOK")
			|| sanitized.equals("GO BACK")
			|| sanitized.equals("CLOSE")
			|| sanitized.equals("REROLL CHEST")
			|| sanitized.startsWith("REROLL ");
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
				|| normalized.contains("CLOAK") || normalized.contains("SWORD")
				|| normalized.contains("KUUDRA") || normalized.contains("TEETH")
				|| normalized.contains("EMBERS") || normalized.contains("CORE")
				|| normalized.contains("DISINTEGRATOR") || normalized.contains("CRIMSON")
				|| normalized.contains("TERROR") || normalized.contains("AURORA")
				|| normalized.contains("FERVOR") || normalized.contains("HOLLOW");
	}

	private String lootKey(DungeonLootEntry entry) {
		if (entry.itemId != null && !entry.itemId.isBlank()) return "id:" + entry.itemId;
		return "raw:" + stripTrailingLootQuantity(sanitizeLootName(entry.rawName)).toUpperCase(Locale.ROOT);
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
		aliases.put("WITHER CLOAK", "WITHER_CLOAK");
		aliases.put("WITHER CLOAK SWORD", "WITHER_CLOAK");
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
		aliases.put("HOT POTATO BOOK", "HOT_POTATO_BOOK");
		aliases.put("DARK ORB", "DARK_ORB");
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