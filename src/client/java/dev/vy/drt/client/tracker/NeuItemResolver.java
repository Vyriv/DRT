package dev.vy.drt.client.tracker;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.vy.drt.DungeonRunTracker;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.DyedItemColor;
import net.minecraft.world.item.component.ResolvableProfile;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class NeuItemResolver {

	private static final String NEU_BASE =
		"https://raw.githubusercontent.com/NotEnoughUpdates/NotEnoughUpdates-REPO/master/items/";
	private static final String NEU_CONSTANTS_BASE =
		"https://raw.githubusercontent.com/NotEnoughUpdates/NotEnoughUpdates-REPO/master/constants/";
	private static final Duration TIMEOUT = Duration.ofSeconds(8);

	private static final Pattern TEXTURE_VALUE = Pattern.compile("Value:\"([A-Za-z0-9+/=]+)\"");
	private static final Pattern LEATHER_COLOR = Pattern.compile("color:(\\d+)");
	private static final Pattern ITEM_MODEL = Pattern.compile("ItemModel:\"([^\"]+)\"");

	private static final HttpClient HTTP = HttpClient.newBuilder()
		.connectTimeout(Duration.ofSeconds(5))
		.build();

	private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor(r -> {
		Thread t = new Thread(r, "DRT-NeuResolver");
		t.setDaemon(true);
		return t;
	});

	private static final Set<String> FETCHED = ConcurrentHashMap.newKeySet();
	private static final ConcurrentHashMap<String, ItemStack> RESOLVED_STACKS = new ConcurrentHashMap<>();
	private static final ConcurrentHashMap<String, Integer>   RESOLVED_COLORS = new ConcurrentHashMap<>();
	private static final ConcurrentHashMap<String, String> ATTRIBUTE_SHARD_INTERNAL_NAMES = new ConcurrentHashMap<>(Map.of(
		"SHARD_BEZAL", "ATTRIBUTE_SHARD_BLAZING_RESISTANCE;1",
		"SHARD_KRAKEN", "ATTRIBUTE_SHARD_MENDING;1"
	));
	private static final AtomicBoolean ATTRIBUTE_SHARD_INDEX_REQUESTED = new AtomicBoolean();

	private NeuItemResolver() {}

	public static ItemStack getResolvedStack(String itemId) {
		if (itemId == null || itemId.isBlank()) return null;
		return RESOLVED_STACKS.get(itemId.toUpperCase(Locale.ROOT));
	}

	public static Integer getColor(String itemId) {
		if (itemId == null || itemId.isBlank()) return null;
		return RESOLVED_COLORS.get(itemId.toUpperCase(Locale.ROOT));
	}

	public static String getAttributeShardInternalName(String bazaarName) {
		if (bazaarName == null || bazaarName.isBlank()) return null;
		String key = bazaarName.toUpperCase(Locale.ROOT);
		String internalName = ATTRIBUTE_SHARD_INTERNAL_NAMES.get(key);
		if (internalName == null && key.startsWith("SHARD_")) {
			loadAttributeShardIndexAsync();
		}
		return internalName;
	}

	public static void enqueue(String itemId) {
		if (itemId == null || itemId.isBlank()) return;
		String key = itemId.toUpperCase(Locale.ROOT);
		if (FETCHED.add(key)) {
			EXECUTOR.submit(() -> fetchAndResolve(key));
		}
	}

	private static void loadAttributeShardIndexAsync() {
		if (ATTRIBUTE_SHARD_INDEX_REQUESTED.compareAndSet(false, true)) {
			EXECUTOR.submit(NeuItemResolver::fetchAttributeShardIndex);
		}
	}

	private static void fetchAttributeShardIndex() {
		try {
			HttpRequest req = HttpRequest.newBuilder(URI.create(NEU_CONSTANTS_BASE + "attribute_shards.json"))
				.timeout(TIMEOUT).GET().build();
			HttpResponse<String> resp = HTTP.send(req, HttpResponse.BodyHandlers.ofString());
			if (resp.statusCode() < 200 || resp.statusCode() >= 300) {
				DungeonRunTracker.LOGGER.warn("[NEU] HTTP {} for attribute_shards.json", resp.statusCode());
				return;
			}

			JsonObject json = JsonParser.parseString(resp.body()).getAsJsonObject();
			JsonArray attributes = json.getAsJsonArray("attributes");
			if (attributes == null) return;
			for (JsonElement element : attributes) {
				if (!element.isJsonObject()) continue;
				JsonObject object = element.getAsJsonObject();
				if (!object.has("bazaarName") || !object.has("internalName")) continue;
				String bazaarName = object.get("bazaarName").getAsString();
				String internalName = object.get("internalName").getAsString();
				if (bazaarName == null || bazaarName.isBlank() || internalName == null || internalName.isBlank()) continue;
				ATTRIBUTE_SHARD_INTERNAL_NAMES.putIfAbsent(
					bazaarName.toUpperCase(Locale.ROOT),
					internalName.toUpperCase(Locale.ROOT)
				);
			}
		} catch (Exception e) {
			DungeonRunTracker.LOGGER.warn("[NEU] Failed to load attribute shard index: {}", e.getMessage());
		}
	}

	public static void preload() {
		String[] skulls = {
			"SHADOW_ASSASSIN_HELMET",
			"NECROMANCER_LORD_HELMET",
			"WITHER_HELMET",
			"RECOMBOBULATOR_3000",
			"ADAPTIVE_HELMET",
			"TRAINING_WEIGHTS",
			"AOTE_STONE",
			"WARPED_STONE",
			"SPIRIT_WING", "SPIRIT_MASK", "SPIRIT_BONE", "SPIRIT_DECOY",
			"TERROR_HELMET",
			"MASTER_SKULL_TIER_1",  "MASTER_SKULL_TIER_2",  "MASTER_SKULL_TIER_3",
			"MASTER_SKULL_TIER_4",  "MASTER_SKULL_TIER_5",  "MASTER_SKULL_TIER_6",
			"MASTER_SKULL_TIER_7",  "MASTER_SKULL_TIER_8",  "MASTER_SKULL_TIER_9",
			"MASTER_SKULL_TIER_10",
		};
		for (String id : skulls) enqueue(id);
	}

	private static void fetchAndResolve(String itemId) {
		try {
			HttpRequest req = HttpRequest.newBuilder(URI.create(NEU_BASE + itemId + ".json"))
				.timeout(TIMEOUT).GET().build();
			HttpResponse<String> resp = HTTP.send(req, HttpResponse.BodyHandlers.ofString());
			if (resp.statusCode() == 404) return;
			if (resp.statusCode() < 200 || resp.statusCode() >= 300) {
				DungeonRunTracker.LOGGER.warn("[NEU] HTTP {} for {}", resp.statusCode(), itemId);
				return;
			}

			JsonObject json = JsonParser.parseString(resp.body()).getAsJsonObject();
			String neuItemId   = json.has("itemid")      ? json.get("itemid").getAsString()      : null;
			String displayname = json.has("displayname") ? json.get("displayname").getAsString() : null;
			int    damage      = json.has("damage")      ? json.get("damage").getAsInt()          : 0;
			String nbttag      = json.has("nbttag")      ? json.get("nbttag").getAsString()      : null;

			int color = extractColor(displayname);
			RESOLVED_COLORS.put(itemId, color);

			ItemStack stack = buildStack(neuItemId, damage, nbttag);
			RESOLVED_STACKS.put(itemId, stack);

		} catch (Exception e) {
			DungeonRunTracker.LOGGER.warn("[NEU] Failed to resolve {}: {}", itemId, e.getMessage());
		}
	}

	static ItemStack buildStack(String neuItemId, int damage, String nbttag) {
		if (isSkullItem(neuItemId, damage)) {
			String texture = extractSkullTexture(nbttag);
			if (texture != null && !texture.isBlank()) {
				ItemStack skull = skullFromTexture(texture);
				if (!skull.isEmpty()) return skull;
			}
			return new ItemStack(Items.PLAYER_HEAD);
		}
		if (isLegacySkullId(neuItemId)) {
			return new ItemStack(translateLegacySkullByDamage(damage));
		}

		String itemModel = extractItemModel(nbttag);
		Item item = translateItemId(itemModel != null ? itemModel : neuItemId);
		ItemStack stack = new ItemStack(item);
		Integer leatherColor = extractLeatherColor(nbttag);
		if (leatherColor != null && isLeatherArmor(item)) {
			stack.set(DataComponents.DYED_COLOR, new DyedItemColor(leatherColor));
		}
		return stack;
	}

	private static boolean isSkullItem(String neuItemId, int damage) {
		if (neuItemId == null) return false;
		String id = neuItemId.toLowerCase(Locale.ROOT).replace("minecraft:", "");
		return (id.equals("skull") || id.equals("skull_item") || id.equals("player_head")) && damage == 3;
	}

	private static boolean isLegacySkullId(String neuItemId) {
		if (neuItemId == null) return false;
		String id = neuItemId.toLowerCase(Locale.ROOT).replace("minecraft:", "");
		return id.equals("skull") || id.equals("skull_item") || id.equals("player_head");
	}

	private static Item translateLegacySkullByDamage(int damage) {
		return switch (damage) {
			case 0 -> Items.SKELETON_SKULL;
			case 1 -> Items.WITHER_SKELETON_SKULL;
			case 2 -> Items.ZOMBIE_HEAD;
			case 3 -> Items.PLAYER_HEAD;
			case 4 -> Items.CREEPER_HEAD;
			case 5 -> Items.DRAGON_HEAD;
			default -> Items.PLAYER_HEAD;
		};
	}

	static String extractSkullTexture(String nbttag) {
		if (nbttag == null || nbttag.isBlank()) return null;
		Matcher m = TEXTURE_VALUE.matcher(nbttag);
		return m.find() ? m.group(1) : null;
	}

	private static Integer extractLeatherColor(String nbttag) {
		if (nbttag == null || nbttag.isBlank()) return null;
		Matcher matcher = LEATHER_COLOR.matcher(nbttag);
		if (!matcher.find()) return null;
		try {
			return Integer.parseInt(matcher.group(1));
		} catch (NumberFormatException ignored) {
			return null;
		}
	}

	private static String extractItemModel(String nbttag) {
		if (nbttag == null || nbttag.isBlank()) return null;
		Matcher matcher = ITEM_MODEL.matcher(nbttag);
		return matcher.find() ? matcher.group(1) : null;
	}

	private static boolean isLeatherArmor(Item item) {
		return item == Items.LEATHER_HELMET
			|| item == Items.LEATHER_CHESTPLATE
			|| item == Items.LEATHER_LEGGINGS
			|| item == Items.LEATHER_BOOTS;
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
			DungeonRunTracker.LOGGER.warn("[NEU] skullFromTexture failed: {}", e.getMessage());
			return ItemStack.EMPTY;
		}
	}

	static int extractColor(String displayname) {
		if (displayname == null || displayname.isBlank()) return 0xFFAAAAAA;
		int lastColor = 0;
		for (int i = 0; i < displayname.length() - 1; i++) {
			char c = displayname.charAt(i);
			if (c == '§' || c == '&') {
				char code = Character.toLowerCase(displayname.charAt(i + 1));
				if (code == 'r') { i++; continue; }
				int color = legacyCodeToArgb(code);
				if (color != 0) lastColor = color;
				i++;
			}
		}
		return lastColor != 0 ? lastColor : 0xFFAAAAAA;
	}

	private static int legacyCodeToArgb(char code) {
		return switch (code) {
			case '0' -> 0xFF000000;
			case '1' -> 0xFF0000AA;
			case '2' -> 0xFF00AA00;
			case '3' -> 0xFF00AAAA;
			case '4' -> 0xFFAA0000;
			case '5' -> 0xFFAA00AA;
			case '6' -> 0xFFFFAA00;
			case '7' -> 0xFFAAAAAA;
			case '8' -> 0xFF555555;
			case '9' -> 0xFF5555FF;
			case 'a' -> 0xFF55FF55;
			case 'b' -> 0xFF55FFFF;
			case 'c' -> 0xFFFF5555;
			case 'd' -> 0xFFFF55FF;
			case 'e' -> 0xFFFFFF55;
			case 'f' -> 0xFFFFFFFF;
			default  -> 0;
		};
	}

	static Item translateItemId(String neuItemId) {
		if (neuItemId == null) return Items.BARRIER;
		String id = neuItemId.toLowerCase(Locale.ROOT).replace("minecraft:", "");
		return switch (id) {
			case "skull", "skull_item", "player_head" -> Items.PLAYER_HEAD;
			case "diamond_sword"                      -> Items.DIAMOND_SWORD;
			case "iron_sword"                         -> Items.IRON_SWORD;
			case "gold_sword", "golden_sword"         -> Items.GOLDEN_SWORD;
			case "wooden_sword"                       -> Items.WOODEN_SWORD;
			case "stone_sword"                        -> Items.STONE_SWORD;
			case "netherite_sword"                    -> Items.NETHERITE_SWORD;
			case "bow"                                -> Items.BOW;
			case "fishing_rod"                        -> Items.FISHING_ROD;
			case "iron_hoe"                           -> Items.IRON_HOE;
			case "enchanted_book"                     -> Items.ENCHANTED_BOOK;
			case "book"                               -> Items.BOOK;
			case "paper"                              -> Items.PAPER;
			case "writable_book"                      -> Items.WRITABLE_BOOK;
			case "nether_star"                        -> Items.NETHER_STAR;
			case "tripwire_hook"                      -> Items.TRIPWIRE_HOOK;
			case "stick"                              -> Items.STICK;
			case "emerald"                            -> Items.EMERALD;
			case "diamond"                            -> Items.DIAMOND;
			case "ender_pearl"                        -> Items.ENDER_PEARL;
			case "blaze_rod"                          -> Items.BLAZE_ROD;
			case "red_rose"                           -> Items.POPPY;
			case "gold_block"                         -> Items.GOLD_BLOCK;
			case "diamond_block"                      -> Items.DIAMOND_BLOCK;
			case "emerald_block"                      -> Items.EMERALD_BLOCK;
			case "obsidian"                           -> Items.OBSIDIAN;
			case "bedrock"                            -> Items.BEDROCK;
			case "sea_lantern"                        -> Items.SEA_LANTERN;
			case "leather_helmet"                     -> Items.LEATHER_HELMET;
			case "leather_chestplate"                 -> Items.LEATHER_CHESTPLATE;
			case "leather_leggings"                   -> Items.LEATHER_LEGGINGS;
			case "leather_boots"                      -> Items.LEATHER_BOOTS;
			case "gold_helmet",  "golden_helmet"      -> Items.GOLDEN_HELMET;
			case "gold_chestplate", "golden_chestplate" -> Items.GOLDEN_CHESTPLATE;
			case "gold_leggings",   "golden_leggings"   -> Items.GOLDEN_LEGGINGS;
			case "gold_boots",      "golden_boots"      -> Items.GOLDEN_BOOTS;
			case "chainmail_helmet"                   -> Items.CHAINMAIL_HELMET;
			case "chainmail_chestplate"               -> Items.CHAINMAIL_CHESTPLATE;
			case "chainmail_leggings"                 -> Items.CHAINMAIL_LEGGINGS;
			case "chainmail_boots"                    -> Items.CHAINMAIL_BOOTS;
			default                                   -> Items.BARRIER;
		};
	}
}
