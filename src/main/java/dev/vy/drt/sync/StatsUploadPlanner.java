package dev.vy.drt.sync;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import dev.vy.drt.config.DungeonLootEntry;
import dev.vy.drt.config.DungeonRunCompletionRecord;
import dev.vy.drt.config.DungeonRunRecord;
import dev.vy.drt.history.ManualRecords;
import dev.vy.drt.history.RunHistoryLinks;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Decides which saved runs and chests belong in a stats upload and builds their payloads. Only
 * records DRT captured itself for the uploading account are sent; records saved before sync was
 * turned on are flagged historical. Records saved before accounts were stamped go only to the one
 * account that claimed them. Manually created or edited records are never sent, and ones uploaded
 * before they were edited are retracted.
 */
public final class StatsUploadPlanner {
	public static final int MAX_ITEMS = 64;
	private static final Pattern FLOOR = Pattern.compile("^(F[1-7]|M[1-7]|K[1-5])$");
	private static final Pattern GRADE = Pattern.compile("^[A-Za-z0-9+%]{1,8}$");
	private static final Pattern ITEM_ID = Pattern.compile("^[A-Za-z0-9_:;.+-]{1,64}$");
	private static final Pattern FORMATTING = Pattern.compile("§.");

	/** Who owns the records being planned, and whether that account claimed the records saved without an account. */
	public record Scope(String uuid, long syncSinceMillis, boolean ownsUnstamped, boolean includeHistorical) {
	}

	public record Upload(String id, String revision, boolean run, boolean historical, long timestamp, JsonObject json) {
	}

	private StatsUploadPlanner() {
	}

	/** Every eligible record whose current revision has not been uploaded yet, live records first, oldest first. */
	public static List<Upload> pending(
		Scope scope,
		List<DungeonRunCompletionRecord> completions,
		List<DungeonRunRecord> history,
		Map<String, String> uploaded
	) {
		List<Upload> out = new ArrayList<>();
		for (Upload upload : eligible(scope, completions, history)) {
			if (!upload.revision().equals(uploaded.get(upload.id()))) out.add(upload);
		}
		return out;
	}

	public static List<Upload> eligible(Scope scope, List<DungeonRunCompletionRecord> completions, List<DungeonRunRecord> history) {
		List<Upload> out = new ArrayList<>();
		List<DungeonRunCompletionRecord> runs = completions == null ? List.of() : completions;
		List<DungeonRunRecord> chests = history == null ? List.of() : history;
		for (DungeonRunCompletionRecord completion : runs) {
			if (completion == null || blank(completion.completionId) || completion.isManual()) continue;
			if (!FLOOR.matcher(nullToEmpty(completion.floor)).matches()) continue;
			Boolean historical = historicalFlag(scope, completion.playerUuid, completion.completedAtEpochMillis);
			if (historical == null) continue;
			out.add(upload(completion.completionId, true, historical, completion.completedAtEpochMillis, runJson(completion)));
		}
		Map<DungeonRunRecord, RunHistoryLinks.Link> links = RunHistoryLinks.resolve(chests, runs);
		for (DungeonRunRecord chest : chests) {
			if (chest == null || blank(chest.recordId)) continue;
			RunHistoryLinks.Link link = links.get(chest);
			if (ManualRecords.isManual(chest, link)) continue;
			Boolean historical = historicalFlag(scope, chest.playerUuid, chest.timestampEpochMillis);
			if (historical == null) continue;
			boolean genuineRun = link != null && link.linked() && !link.completion().isManual() && !blank(link.completion().completionId);
			String runId = genuineRun ? link.completion().completionId : null;
			JsonObject json = chestJson(chest, runId);
			if (json == null) continue;
			out.add(upload(chest.recordId, false, historical, chest.timestampEpochMillis, json));
		}
		out.sort(Comparator.comparing(Upload::historical).thenComparingLong(Upload::timestamp));
		return out;
	}

	/**
	 * Ids this account already uploaded that are manual now, so the server deletes them instead of
	 * keeping the version from before the edit.
	 */
	public static List<String> retractions(List<DungeonRunCompletionRecord> completions, List<DungeonRunRecord> history, Map<String, String> uploaded) {
		if (uploaded == null || uploaded.isEmpty()) return List.of();
		Set<String> ids = new LinkedHashSet<>();
		Set<String> currentIds = new LinkedHashSet<>();
		List<DungeonRunRecord> chests = history == null ? List.of() : history;
		for (DungeonRunCompletionRecord completion : completions == null ? List.<DungeonRunCompletionRecord>of() : completions) {
			if (completion == null || blank(completion.completionId)) continue;
			currentIds.add(completion.completionId);
			if (completion.isManual() && uploaded.containsKey(completion.completionId)) ids.add(completion.completionId);
		}
		Map<DungeonRunRecord, RunHistoryLinks.Link> links = RunHistoryLinks.resolve(chests, completions);
		for (DungeonRunRecord chest : chests) {
			if (chest == null || blank(chest.recordId)) continue;
			currentIds.add(chest.recordId);
			if (ManualRecords.isManual(chest, links.get(chest)) && uploaded.containsKey(chest.recordId)) ids.add(chest.recordId);
		}
		for (String uploadedId : uploaded.keySet()) {
			if (!currentIds.contains(uploadedId)) ids.add(uploadedId);
		}
		List<String> ordered = new ArrayList<>(ids);
		ordered.sort(String::compareTo);
		return ordered;
	}

	/** False for a record saved after sync was turned on, true for older history, null when it is not this account's to upload. */
	static Boolean historicalFlag(Scope scope, String recordUuid, long timestamp) {
		if (scope == null || blank(scope.uuid())) return null;
		boolean live = scope.syncSinceMillis() > 0L && timestamp >= scope.syncSinceMillis();
		if (blank(recordUuid)) {
			if (!scope.ownsUnstamped() || (!live && !scope.includeHistorical())) return null;
			return live ? Boolean.FALSE : Boolean.TRUE;
		}
		if (!recordUuid.equals(scope.uuid())) return null;
		if (live) return Boolean.FALSE;
		return scope.includeHistorical() ? Boolean.TRUE : null;
	}

	static JsonObject runJson(DungeonRunCompletionRecord completion) {
		JsonObject json = new JsonObject();
		json.addProperty("id", completion.completionId);
		json.addProperty("floor", completion.floor);
		String grade = nullToEmpty(completion.grade).trim();
		if (GRADE.matcher(grade).matches()) json.addProperty("grade", grade);
		json.addProperty("completedAt", completion.completedAtEpochMillis);
		RunHistoryLinks.RunTime time = RunHistoryLinks.runTime(completion);
		if (time != null) {
			json.addProperty("durationMs", time.millis());
			json.addProperty("timeSource", switch (time.source()) {
				case OFFICIAL -> "official";
				case MEASURED -> "measured";
				case MANUAL -> "manual";
			});
		}
		return json;
	}

	static JsonObject chestJson(DungeonRunRecord chest, String runId) {
		String title = cleanText(chest.chestTitle, 40);
		if (title.isEmpty()) return null;
		JsonObject json = new JsonObject();
		json.addProperty("id", chest.recordId);
		if (runId != null) json.addProperty("runId", runId);
		String floor = nullToEmpty(chest.floor).trim();
		if (FLOOR.matcher(floor).matches()) json.addProperty("floor", floor);
		json.addProperty("chest", title);
		json.addProperty("openedAt", chest.timestampEpochMillis);
		JsonObject costs = new JsonObject();
		costs.addProperty("base", chest.baseChestCostCoins);
		costs.addProperty("key", chest.dungeonChestKeyCostCoins);
		costs.addProperty("kismet", chest.kismetFeatherCostCoins);
		costs.addProperty("wheel", chest.wheelOfFateCostCoins);
		costs.addProperty("kuudraKey", chest.kuudraKeyCostCoins);
		json.add("costs", costs);
		json.addProperty("kismetRerolled", chest.kismetRerolledChestOpened);
		json.addProperty("value", chest.chestValueCoins);
		json.addProperty("profit", chest.chestProfitCoins);
		JsonArray items = new JsonArray();
		for (DungeonLootEntry entry : chest.lootEntries == null ? List.<DungeonLootEntry>of() : chest.lootEntries) {
			if (entry == null) continue;
			if (items.size() > MAX_ITEMS) break;
			JsonObject item = new JsonObject();
			String id = nullToEmpty(entry.itemId).trim();
			if (ITEM_ID.matcher(id).matches()) {
				item.addProperty("id", id);
			} else {
				String name = cleanText(entry.rawName, 64);
				if (!name.isEmpty()) item.addProperty("name", name);
			}
			item.addProperty("qty", entry.quantity);
			items.add(item);
		}
		json.add("items", items);
		return json;
	}

	private static Upload upload(String id, boolean run, boolean historical, long timestamp, JsonObject json) {
		String revision = revision(json);
		if (historical) json.addProperty("historical", true);
		json.addProperty("rev", revision);
		return new Upload(id, revision, run, historical, timestamp, json);
	}

	/** Content hash of the uploaded fields, so a record is sent again only when it changes. */
	static String revision(JsonObject json) {
		try {
			byte[] digest = MessageDigest.getInstance("SHA-256").digest(json.toString().getBytes(StandardCharsets.UTF_8));
			return HexFormat.of().formatHex(digest, 0, 8);
		} catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException(e);
		}
	}

	static String cleanText(String value, int maxLength) {
		String stripped = FORMATTING.matcher(nullToEmpty(value)).replaceAll("");
		StringBuilder out = new StringBuilder();
		for (int i = 0; i < stripped.length(); i++) {
			char c = stripped.charAt(i);
			if (c >= 0x20 && c <= 0x7E) out.append(c);
		}
		String trimmed = out.toString().trim();
		return trimmed.length() > maxLength ? trimmed.substring(0, maxLength).trim() : trimmed;
	}

	private static boolean blank(String value) {
		return value == null || value.isBlank();
	}

	private static String nullToEmpty(String value) {
		return value == null ? "" : value;
	}
}
