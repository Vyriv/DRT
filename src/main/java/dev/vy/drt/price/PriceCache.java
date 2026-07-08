package dev.vy.drt.price;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.vy.drt.DungeonRunTracker;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class PriceCache {
	private static final URI PRICES_URI = URI.create("https://athen.aerii.xyz/prices");
	private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(10);
	private static final long REFRESH_MINUTES = 10L;
	private static final Pattern COFL_META_PRICE = Pattern.compile("<meta name=\"description\" content=\"[^\"]*?Price: ([0-9,]+) Coins", Pattern.CASE_INSENSITIVE);
	private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
		.connectTimeout(Duration.ofSeconds(5))
		.build();
	private static final ScheduledExecutorService EXECUTOR = Executors.newSingleThreadScheduledExecutor(runnable -> {
		Thread thread = new Thread(runnable, "DRT-PriceCache");
		thread.setDaemon(true);
		return thread;
	});

	private static volatile Map<String, Double> itemIdToPrice = Map.of();
	private static volatile Map<String, String> itemIdToSource = Map.of();
	private static volatile Map<String, AuctionPriceData> itemIdToAuctionData = Map.of();
	private static volatile Map<String, BazaarPriceData> itemIdToBazaarData = Map.of();

	private PriceCache() {
	}

	public static void start() {
		EXECUTOR.execute(PriceCache::refreshSafely);
		EXECUTOR.scheduleAtFixedRate(PriceCache::refreshSafely, REFRESH_MINUTES, REFRESH_MINUTES, TimeUnit.MINUTES);
	}

	public static PriceLookup get(String itemId) {
		if (itemId == null || itemId.isBlank()) return null;
		Double price = itemIdToPrice.get(itemId);
		if (price == null) return null;
		return new PriceLookup(itemId, price, itemIdToSource.getOrDefault(itemId, "unknown"));
	}

	public static boolean containsItemId(String itemId) {
		return itemId != null && itemIdToPrice.containsKey(itemId);
	}

	public static AuctionPriceData getAuctionHouse(String itemId) {
		if (itemId == null || itemId.isBlank()) return null;
		return itemIdToAuctionData.get(itemId);
	}

	public static BazaarPriceData getBazaar(String itemId) {
		if (itemId == null || itemId.isBlank()) return null;
		return itemIdToBazaarData.get(itemId);
	}

	public static List<SearchResult> search(String query, int limit) {
		String normalized = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
		if (normalized.isEmpty()) return List.of();

		List<SearchResult> results = new ArrayList<>();
		for (Map.Entry<String, Double> entry : itemIdToPrice.entrySet()) {
			String itemId = entry.getKey();
			String displayName = toDisplayName(itemId);
			String idLower = itemId.toLowerCase(Locale.ROOT);
			String nameLower = displayName.toLowerCase(Locale.ROOT);
			if (!idLower.contains(normalized) && !nameLower.contains(normalized)) continue;
			int rank = idLower.startsWith(normalized) || nameLower.startsWith(normalized) ? 0 : 1;
			results.add(new SearchResult(itemId, displayName, entry.getValue(), itemIdToSource.getOrDefault(itemId, "unknown"), rank));
		}

		results.sort(Comparator
			.comparingInt(SearchResult::rank)
			.thenComparing(SearchResult::displayName)
			.thenComparing(SearchResult::itemId));
		if (results.size() > limit) return List.copyOf(results.subList(0, limit));
		return List.copyOf(results);
	}

	private static void refreshSafely() {
		try {
			refresh();
		} catch (Exception exception) {
			DungeonRunTracker.LOGGER.warn("[DRT] Failed to refresh Athen prices, keeping previous cache", exception);
		}
	}

	private static void refresh() throws IOException, InterruptedException {
		HttpRequest request = HttpRequest.newBuilder(PRICES_URI)
			.timeout(REQUEST_TIMEOUT)
			.GET()
			.build();
		HttpResponse<String> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
		if (response.statusCode() < 200 || response.statusCode() >= 300) {
			throw new IOException("Unexpected price response status: " + response.statusCode());
		}

		JsonObject root = JsonParser.parseString(response.body()).getAsJsonObject();
		Map<String, Double> nextPrices = new ConcurrentHashMap<>();
		Map<String, String> nextSources = new ConcurrentHashMap<>();
		Map<String, AuctionPriceData> nextAuctionData = new ConcurrentHashMap<>();
		Map<String, BazaarPriceData> nextBazaarData = new ConcurrentHashMap<>();
		loadAuctionHouse(root.getAsJsonObject("auction_house"), nextPrices, nextSources, nextAuctionData);
		loadBazaar(root.getAsJsonObject("bazaar"), nextPrices, nextSources, nextBazaarData);
		loadCoflFallback("MASTER_SKULL_TIER_1", "MASTER_SKULL_TIER_1", nextPrices, nextSources);
		loadCoflFallback("PET_SPIRIT", "PET_SPIRIT", nextPrices, nextSources);
		if (!nextPrices.isEmpty()) {
			itemIdToPrice = Map.copyOf(nextPrices);
			itemIdToSource = Map.copyOf(nextSources);
			itemIdToAuctionData = Map.copyOf(nextAuctionData);
			itemIdToBazaarData = Map.copyOf(nextBazaarData);
			DungeonRunTracker.LOGGER.info("[DRT] Loaded {} Athen price entries", nextPrices.size());
		}
	}

	private static void loadAuctionHouse(
		JsonObject section,
		Map<String, Double> prices,
		Map<String, String> sources,
		Map<String, AuctionPriceData> auctionData
	) {
		if (section == null) return;
		for (Map.Entry<String, JsonElement> entry : section.entrySet()) {
			if (!entry.getValue().isJsonObject()) continue;
			JsonObject priceObject = entry.getValue().getAsJsonObject();
			Double lbin = positiveNumber(priceObject, "lbin");
			Double p3d = positiveNumber(priceObject, "p3d");
			Double p7d = positiveNumber(priceObject, "p7d");
			Double price = firstNumber(lbin, p3d, p7d);
			if (price == null || price <= 0.0D) continue;
			prices.put(entry.getKey(), price);
			sources.put(entry.getKey(), "auction_house");
			auctionData.put(entry.getKey(), new AuctionPriceData(entry.getKey(), lbin, p3d, p7d));
		}
	}

	private static void loadBazaar(
		JsonObject section,
		Map<String, Double> prices,
		Map<String, String> sources,
		Map<String, BazaarPriceData> bazaarData
	) {
		if (section == null) return;
		for (Map.Entry<String, JsonElement> entry : section.entrySet()) {
			if (!entry.getValue().isJsonObject()) continue;
			JsonObject priceObject = entry.getValue().getAsJsonObject();
			Double instantSell = positiveNumber(priceObject, "is");
			Double sellOffer = positiveNumber(priceObject, "ts");
			Double instantBuy = positiveNumber(priceObject, "ib");
			Double buyOrder = positiveNumber(priceObject, "tb");
			Double price = firstNumber(
				instantSell,
				sellOffer,
				instantBuy,
				buyOrder
			);
			if (price == null || price <= 0.0D) continue;
			bazaarData.put(entry.getKey(), new BazaarPriceData(entry.getKey(), instantSell, sellOffer, instantBuy, buyOrder));
			if (!prices.containsKey(entry.getKey())) {
				prices.put(entry.getKey(), price);
				sources.put(entry.getKey(), "bazaar");
			}
		}
	}

	private static Double positiveNumber(JsonObject object, String key) {
		JsonElement element = object.get(key);
		if (element != null && element.isJsonPrimitive() && element.getAsJsonPrimitive().isNumber()) {
			double value = element.getAsDouble();
			if (value > 0.0D) return value;
		}
		return null;
	}

	private static Double firstNumber(Double... values) {
		for (Double value : values) {
			if (value != null && value > 0.0D) return value;
		}
		return null;
	}

	private static void loadCoflFallback(String itemId, String coflTag, Map<String, Double> prices, Map<String, String> sources) {
		if (prices.containsKey(itemId)) return;
		try {
			Double price = fetchCoflItemPrice(coflTag);
			if (price == null || price <= 0.0D) return;
			prices.put(itemId, price);
			sources.put(itemId, "cofl_item_page");
		} catch (Exception exception) {
			DungeonRunTracker.LOGGER.debug("[DRT] Failed Cofl fallback price fetch for {}", itemId, exception);
		}
	}

	private static Double fetchCoflItemPrice(String tag) throws IOException, InterruptedException {
		HttpRequest request = HttpRequest.newBuilder(URI.create("https://sky.coflnet.com/item/" + tag))
			.timeout(REQUEST_TIMEOUT)
			.GET()
			.build();
		HttpResponse<String> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
		if (response.statusCode() < 200 || response.statusCode() >= 300) return null;
		Matcher matcher = COFL_META_PRICE.matcher(response.body());
		if (!matcher.find()) return null;
		try {
			return Double.parseDouble(matcher.group(1).replace(",", ""));
		} catch (NumberFormatException ignored) {
			return null;
		}
	}

	private static String toDisplayName(String itemId) {
		return itemId.toLowerCase(Locale.ROOT)
			.replace('_', ' ')
			.replace('-', ' ')
			.replaceAll("\\s+", " ")
			.trim();
	}

	public record PriceLookup(String itemId, double price, String source) {
		public PriceLookup {
			Objects.requireNonNull(itemId, "itemId");
			Objects.requireNonNull(source, "source");
		}
	}

	public record SearchResult(String itemId, String displayName, double price, String source, int rank) {
	}

	public record AuctionPriceData(String itemId, Double lbin, Double p3d, Double p7d) {
		public AuctionPriceData {
			Objects.requireNonNull(itemId, "itemId");
		}
	}

	public record BazaarPriceData(String itemId, Double instantSell, Double sellOffer, Double instantBuy, Double buyOrder) {
		public BazaarPriceData {
			Objects.requireNonNull(itemId, "itemId");
		}
	}
}
