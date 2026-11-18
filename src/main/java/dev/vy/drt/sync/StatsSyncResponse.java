package dev.vy.drt.sync;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.util.LinkedHashSet;
import java.util.Set;

/** Validated acknowledgement from the statistics sync endpoint. */
public record StatsSyncResponse(Set<String> rejectedIds, Set<String> deletedIds, boolean hasRetractionResult) {
	public static StatsSyncResponse parse(String body) {
		try {
			JsonElement parsed = JsonParser.parseString(body == null ? "" : body);
			if (!parsed.isJsonObject()) return null;
			JsonObject object = parsed.getAsJsonObject();
			if (!object.has("success") || !object.get("success").isJsonPrimitive() || !object.get("success").getAsBoolean()) return null;
			Set<String> rejected = new LinkedHashSet<>();
			if (object.has("rejected")) {
				if (!object.get("rejected").isJsonArray()) return null;
				for (JsonElement element : object.getAsJsonArray("rejected")) {
					if (!element.isJsonObject()) return null;
					JsonElement id = element.getAsJsonObject().get("id");
					if (id != null && id.isJsonPrimitive() && id.getAsJsonPrimitive().isString()) rejected.add(id.getAsString());
				}
			}
			Set<String> deleted = stringSet(object, "deleted");
			return deleted == null ? null : new StatsSyncResponse(Set.copyOf(rejected), Set.copyOf(deleted), object.has("retracted"));
		} catch (RuntimeException e) {
			return null;
		}
	}

	private static Set<String> stringSet(JsonObject object, String field) {
		Set<String> values = new LinkedHashSet<>();
		if (!object.has(field)) return values;
		if (!object.get(field).isJsonArray()) return null;
		for (JsonElement element : object.getAsJsonArray(field)) {
			if (!element.isJsonPrimitive() || !element.getAsJsonPrimitive().isString()) return null;
			values.add(element.getAsString());
		}
		return values;
	}
}
