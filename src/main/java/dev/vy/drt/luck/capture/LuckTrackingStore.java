package dev.vy.drt.luck.capture;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import dev.vy.drt.config.AtomicJsonFileStore;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Versioned Luck Rating capture file. It lives beside DRT's own config and never reads from or
 * writes to the existing loot logs. A file from a newer schema is left untouched and the store
 * becomes read-only; a malformed file is preserved under a timestamped name before replacement.
 */
public final class LuckTrackingStore {
	public static final String SCHEMA = "drt-luck";
	public static final int SCHEMA_VERSION = 1;

	public enum LoadState { NEW, LOADED, RECOVERED_FROM_MALFORMED, NEWER_SCHEMA_READ_ONLY, UNREADABLE_READ_ONLY }

	public enum AddResult { ADDED, DUPLICATE, READ_ONLY, SAVE_FAILED }

	static final class FileData {
		String schema = SCHEMA;
		int schemaVersion = SCHEMA_VERSION;
		LuckModifierProfile profile = LuckModifierProfile.unknown();
		List<LuckRunRecord> runs = new ArrayList<>();
	}

	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().serializeNulls().create();

	private final Path path;
	private FileData data = new FileData();
	private LoadState loadState = LoadState.NEW;
	private Path preservedMalformedFile;
	private IOException lastSaveError;

	public LuckTrackingStore(Path path) {
		this.path = Objects.requireNonNull(path, "path");
	}

	public synchronized LoadState load() {
		data = new FileData();
		preservedMalformedFile = null;
		if (!Files.exists(path)) {
			loadState = LoadState.NEW;
			return loadState;
		}
		String json;
		try {
			json = Files.readString(path, StandardCharsets.UTF_8);
		} catch (IOException e) {
			return recoverFromMalformed();
		}
		JsonObject root;
		try {
			JsonElement element = JsonParser.parseString(json);
			if (!element.isJsonObject()) return recoverFromMalformed();
			root = element.getAsJsonObject();
		} catch (JsonParseException e) {
			return recoverFromMalformed();
		}
		int version = root.has("schemaVersion") && root.get("schemaVersion").isJsonPrimitive()
			? root.get("schemaVersion").getAsInt()
			: 0;
		if (version > SCHEMA_VERSION) {
			loadState = LoadState.NEWER_SCHEMA_READ_ONLY;
			return loadState;
		}
		FileData parsed;
		try {
			parsed = GSON.fromJson(root, FileData.class);
		} catch (JsonParseException | IllegalStateException | NumberFormatException e) {
			return recoverFromMalformed();
		}
		data = migrate(parsed == null ? new FileData() : parsed);
		loadState = LoadState.LOADED;
		return loadState;
	}

	public synchronized LoadState loadState() {
		return loadState;
	}

	public synchronized boolean isReadOnly() {
		return loadState == LoadState.NEWER_SCHEMA_READ_ONLY || loadState == LoadState.UNREADABLE_READ_ONLY;
	}

	public synchronized Path preservedMalformedFile() {
		return preservedMalformedFile;
	}

	public synchronized IOException lastSaveError() {
		return lastSaveError;
	}

	public synchronized LuckModifierProfile profile() {
		return data.profile.copy();
	}

	/** Saves new modifier values. The revision only moves when a value actually changes. */
	public synchronized boolean updateProfile(LuckModifierProfile values, long nowEpochMillis) {
		if (isReadOnly() || values == null) return false;
		LuckModifierProfile next = values.copy();
		next.normalize();
		if (next.sameValues(data.profile)) return true;
		LuckModifierProfile previous = data.profile;
		next.revision = previous.revision + 1;
		next.updatedAtEpochMillis = Math.max(0L, nowEpochMillis);
		data.profile = next;
		if (save()) return true;
		data.profile = previous;
		return false;
	}

	public synchronized List<LuckRunRecord> runs() {
		return List.copyOf(data.runs);
	}

	public synchronized boolean containsCompletion(String completionFingerprint) {
		if (completionFingerprint == null || completionFingerprint.isBlank()) return false;
		for (LuckRunRecord run : data.runs) {
			if (completionFingerprint.equals(run.drtCompletionFingerprint)) return true;
		}
		return false;
	}

	/**
	 * Adds a completed run with the current modifier preset snapshotted onto it. A run whose DRT
	 * completion fingerprint or luck id is already stored is ignored.
	 */
	public synchronized AddResult addRun(LuckRunRecord run) {
		if (run == null) return AddResult.DUPLICATE;
		if (isReadOnly()) return AddResult.READ_ONLY;
		run.normalize();
		if (run.luckRunId.isBlank()) run.luckRunId = "luck-" + UUID.randomUUID();
		for (LuckRunRecord existing : data.runs) {
			boolean sameCompletion = !run.drtCompletionFingerprint.isBlank()
				&& run.drtCompletionFingerprint.equals(existing.drtCompletionFingerprint);
			if (sameCompletion || run.luckRunId.equals(existing.luckRunId)) return AddResult.DUPLICATE;
		}
		if (run.modifiers == null) run.modifiers = data.profile.copy();
		run.coverage = LuckCoverage.evaluate(run);
		data.runs.add(run);
		if (save()) return AddResult.ADDED;
		data.runs.remove(run);
		return AddResult.SAVE_FAILED;
	}

	private boolean save() {
		try {
			AtomicJsonFileStore.writeCrashSafe(path, GSON, data, false);
			lastSaveError = null;
			if (loadState == LoadState.NEW) loadState = LoadState.LOADED;
			return true;
		} catch (IOException e) {
			lastSaveError = e;
			return false;
		}
	}

	private LoadState recoverFromMalformed() {
		data = new FileData();
		try {
			Path target = path.resolveSibling(path.getFileName() + ".malformed-"
				+ DateTimeFormatter.ISO_INSTANT.format(Instant.now()).replace(':', '-'));
			Files.copy(path, target, StandardCopyOption.REPLACE_EXISTING);
			preservedMalformedFile = target;
			loadState = LoadState.RECOVERED_FROM_MALFORMED;
		} catch (IOException e) {
			loadState = LoadState.UNREADABLE_READ_ONLY;
		}
		return loadState;
	}

	private static FileData migrate(FileData parsed) {
		parsed.schema = SCHEMA;
		parsed.schemaVersion = SCHEMA_VERSION;
		if (parsed.profile == null) parsed.profile = LuckModifierProfile.unknown();
		parsed.profile.normalize();
		if (parsed.runs == null) parsed.runs = new ArrayList<>();
		parsed.runs.removeIf(Objects::isNull);
		for (LuckRunRecord run : parsed.runs) {
			run.normalize();
			run.coverage = LuckCoverage.evaluate(run);
		}
		return parsed;
	}
}
