package dev.vy.drt.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.vy.drt.DungeonRunTracker;
import dev.vy.drt.history.ManualRecords;
import dev.vy.drt.history.RunOwnership;
import dev.vy.drt.history.RunHistoryLinks;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Supplier;
import net.fabricmc.loader.api.FabricLoader;

public final class DrtConfigManager {
	private static final float MIN_HUD_SCALE = 0.5F;
	private static final float MAX_HUD_SCALE = 2.0F;
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final Path CONFIG_DIR = FabricLoader.getInstance().getConfigDir();
	private static final Path CONFIG_PATH = DrtConfigPaths.configFile(CONFIG_DIR);
	private static final Path BACKUP_DIR = DrtConfigPaths.drtDirectory(CONFIG_DIR).resolve("backups");
	private static final long COMPLETION_SESSION_DEDUP_WINDOW_MS = 30L * 60L * 1000L;
	private static DrtConfig config = new DrtConfig();
	private static Supplier<String> accountUuidSupplier = () -> null;
	private static boolean primaryConfigMalformed;
	private static long nextRollingBackupAtMillis;
	private static long historyRevision;

	private DrtConfigManager() {
	}

	public static synchronized void load() {
		try {
			if (DrtConfigPaths.migrateLegacyConfig(CONFIG_DIR)) {
				DungeonRunTracker.LOGGER.info("[DRT] Migrated legacy config to {}", CONFIG_PATH);
			}
			Files.createDirectories(CONFIG_PATH.getParent());
		} catch (IOException e) {
			DungeonRunTracker.LOGGER.error("[DRT] Failed to create config directory", e);
		}
		if (Files.notExists(CONFIG_PATH)) {
			config = new DrtConfig();
			applyFreshOverlayDefaults(config);
			config.startStatsSyncBoundary(System.currentTimeMillis());
			primaryConfigMalformed = false;
			historyRevision++;
			save();
			return;
		}
		try (Reader reader = Files.newBufferedReader(CONFIG_PATH, StandardCharsets.UTF_8)) {
			JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
			LoadedConfig loaded = normalizeLoadedConfig(GSON.fromJson(root, DrtConfig.class), root);
			config = loaded.config();
			primaryConfigMalformed = false;
			DungeonRunTracker.LOGGER.info("[DRT] Loaded config: {} loot records, {} run completions", config.runHistory.size(), config.runCompletions.size());
			if (loaded.migrated()) save();
		} catch (com.google.gson.JsonParseException | IllegalStateException e) {
			recoverUnreadablePrimary("[DRT] Config file malformed, preserving primary and attempting backup recovery", e);
		} catch (IOException e) {
			recoverUnreadablePrimary("[DRT] Config file could not be read, preserving primary and attempting backup recovery", e);
		}
		historyRevision++;
	}

	/** The primary is kept as a .malformed copy on the next save instead of being overwritten unseen. */
	private static void recoverUnreadablePrimary(String message, Exception e) {
		DungeonRunTracker.LOGGER.error(message, e);
		primaryConfigMalformed = true;
		DrtConfig recovered = tryRecoverFromBackup();
		if (recovered != null) {
			config = recovered;
			DungeonRunTracker.LOGGER.warn("[DRT] Recovered config from {}: {} loot records", AtomicJsonFileStore.backupPath(CONFIG_PATH), config.runHistory.size());
		} else {
			config = new DrtConfig();
			applyFreshOverlayDefaults(config);
			config.startStatsSyncBoundary(System.currentTimeMillis());
			DungeonRunTracker.LOGGER.error("[DRT] Backup recovery failed; using in-memory defaults, the unreadable primary is kept as a .malformed copy");
		}
	}

	private record LoadedConfig(DrtConfig config, boolean migrated) {
	}

	private static LoadedConfig normalizeLoadedConfig(DrtConfig loaded, JsonObject root) {
				if (loaded == null) loaded = new DrtConfig();
				if (loaded.floorRunCounts == null) loaded.floorRunCounts = new LinkedHashMap<>();
				if (loaded.floorRunTimeMs == null) loaded.floorRunTimeMs = new LinkedHashMap<>();
				if (loaded.runHistory == null) loaded.runHistory = new ArrayList<>();
				if (loaded.runCompletions == null) loaded.runCompletions = new ArrayList<>();
				if (loaded.pendingChestOffers == null) loaded.pendingChestOffers = new LinkedHashMap<>();
				loaded.hudScale = clampHudScale(loaded.hudScale);
				loaded.hudVisibilityMode = normalizeHudVisibilityMode(loaded.hudVisibilityMode);
				boolean migrated = normalizeToggleFlags(loaded, root);
				normalizeOnboardingSettings(loaded);
				if (loaded.legacyRunsCompleted > 0 && !loaded.floorRunCounts.containsKey("M5")) {
					loaded.floorRunCounts.put("M5", loaded.legacyRunsCompleted);
				}
				if (normalizeRunHistory(loaded)) migrated = true;
				if (normalizeRunCompletions(loaded)) migrated = true;
				if (recoverPersistedRunOwnership(loaded)) migrated = true;
				if (ManualRecords.markLegacy(loaded.runHistory, loaded.runCompletions)) migrated = true;
				if (restoreFloorRunCountsFromCompletions(loaded)) migrated = true;
				if (migrateOverlayPreset(loaded, root)) migrated = true;
				if (normalizeCustomOverlayLayout(loaded)) migrated = true;
				return new LoadedConfig(loaded, migrated);
	}

	private static DrtConfig tryRecoverFromBackup() {
		Path backup = AtomicJsonFileStore.backupPath(CONFIG_PATH);
		if (Files.notExists(backup)) return null;
		try (Reader reader = Files.newBufferedReader(backup, StandardCharsets.UTF_8)) {
			JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
			return normalizeLoadedConfig(GSON.fromJson(root, DrtConfig.class), root).config();
		} catch (Exception recoveryFailure) {
			DungeonRunTracker.LOGGER.error("[DRT] Failed to recover config backup", recoveryFailure);
			return null;
		}
	}

	public static synchronized void save() {
		rollBackupIfDue();
		try {
			AtomicJsonFileStore.writeCrashSafe(CONFIG_PATH, GSON, config, primaryConfigMalformed);
			primaryConfigMalformed = false;
		} catch (IOException e) {
			DungeonRunTracker.LOGGER.error("[DRT] Failed to save config", e);
		}
	}

	public static synchronized DrtConfig getConfig() {
		return config;
	}

	public static Path configDirectory() {
		return DrtConfigPaths.drtDirectory(CONFIG_DIR);
	}

	public static Path themesDirectory() {
		return DrtConfigPaths.themesDirectory(CONFIG_DIR);
	}

	/** Applies Settings-tab changes to the shared config, validates them, and persists once. */
	public static synchronized void updateUserSettings(Consumer<DrtConfig> update) {
		if (update != null) update.accept(config);
		DrtSettingsValidation.normalize(config);
		save();
	}

	/** Supplies the logged-in account's undashed UUID, stamped on every new run and chest record. */
	public static synchronized void setAccountUuidSupplier(Supplier<String> supplier) {
		accountUuidSupplier = supplier == null ? () -> null : supplier;
	}

	private static String currentAccountUuid() {
		try {
			String uuid = accountUuidSupplier.get();
			return uuid == null || uuid.isBlank() ? null : uuid;
		} catch (RuntimeException e) {
			return null;
		}
	}

	public static synchronized String getDungeonSelectedFloor() {
		return config.selectedFloor;
	}

	public static synchronized void updateSelectedFloor(String floor) {
		config.selectedFloor = (floor == null || floor.isBlank()) ? null : floor;
		save();
	}

	public static synchronized List<DungeonRunRecord> getRunHistory() {
		List<DungeonRunRecord> records = new ArrayList<>();
		for (DungeonRunRecord r : config.runHistory) {
			if (r != null) records.add(r.copy());
		}
		return records;
	}

	/** Changes whenever persisted loot history is loaded or mutated. */
	public static synchronized long historyRevision() {
		return historyRevision;
	}

	public static synchronized List<DungeonRunCompletionRecord> getRunCompletions() {
		List<DungeonRunCompletionRecord> records = new ArrayList<>();
		if (config.runCompletions == null) return records;
		for (DungeonRunCompletionRecord r : config.runCompletions) {
			if (r != null) records.add(r.copy());
		}
		return records;
	}

	public static synchronized DungeonRunCompletionRecord getRunCompletion(String runSessionId) {
		if (runSessionId == null || runSessionId.isBlank() || config.runCompletions == null) return null;
		for (DungeonRunCompletionRecord record : config.runCompletions) {
			if (record != null && runSessionId.equals(record.runSessionId)) return record.copy();
		}
		return null;
	}

	public static synchronized String getCurrentAccountUuid() {
		return currentAccountUuid();
	}

	public static synchronized boolean updateRunCompletionParty(String runSessionId, List<DungeonPartyMember> members) {
		if (runSessionId == null || runSessionId.isBlank() || members == null || members.isEmpty()) return false;
		DungeonRunCompletionRecord completion = findCompletion(runSessionId);
		if (completion == null) return false;
		PartyMemberReconciler.Result reconciled = PartyMemberReconciler.reconcile(completion.partyMembers, members);
		if (!reconciled.changed()) return false;
		completion.partyMembers = reconciled.members();
		historyRevision++;
		save();
		return true;
	}

	/** Applies a backend-confirmed UUID to historical name-only party entries without changing run ownership. */
	public static synchronized boolean resolvePartyIdentity(String name, String uuid) {
		if (name == null || name.isBlank() || uuid == null || uuid.isBlank() || config.runCompletions == null) return false;
		String normalized = uuid.replace("-", "").toLowerCase(java.util.Locale.ROOT);
		if (!normalized.matches("[0-9a-f]{32}")) return false;
		boolean changed = false;
		for (DungeonRunCompletionRecord completion : config.runCompletions) {
			if (completion == null || completion.partyMembers == null) continue;
			for (DungeonPartyMember member : completion.partyMembers) {
				if (member != null && member.name.equalsIgnoreCase(name) && !member.uuid.equals(normalized)) {
					member.uuid = normalized;
					changed = true;
				}
			}
		}
		if (changed) { historyRevision++; save(); }
		return changed;
	}


	public static synchronized boolean updateRunCompletionDuration(String runSessionId, long runTimeMs, String evidence) {
		if (runSessionId == null || runSessionId.isBlank() || !DungeonRunCompletionRecord.plausibleRunTime(runTimeMs)) return false;
		if (config.runCompletions == null) return false;
		for (DungeonRunCompletionRecord existing : config.runCompletions) {
			if (existing == null || !runSessionId.equals(existing.runSessionId)) continue;
			existing.normalize();
			CompletionDurationEvidence.Update update = CompletionDurationEvidence.apply(existing, runTimeMs, evidence);
			if (!update.changed()) return false;
			long previous = update.previousMillis();
			if (config.floorRunTimeMs != null && existing.floor != null && !existing.floor.isBlank()) {
				long delta = runTimeMs - (DungeonRunCompletionRecord.plausibleRunTime(previous) ? previous : 0L);
				config.floorRunTimeMs.merge(existing.floor, delta, (oldValue, change) -> Math.max(0L, oldValue + change));
			}
			historyRevision++;
			save();
			return true;
		}
		return false;
	}

	public static synchronized RunRecordCommitDecision addRunCompletionRecord(DungeonRunCompletionRecord record) {
		if (record == null) return RunRecordCommitDecision.KEEP_EXISTING;
		if (config.runCompletions == null) config.runCompletions = new ArrayList<>();
		if (config.floorRunCounts == null) config.floorRunCounts = new LinkedHashMap<>();
		DungeonRunCompletionRecord stored = record.copy();
		stored.normalize();
		if (stored.completionFingerprint == null || stored.completionFingerprint.isBlank()) {
			stored.completionFingerprint = completionFingerprint(stored);
		}
		for (DungeonRunCompletionRecord existing : config.runCompletions) {
			if (existing == null) continue;
			existing.normalize();
			boolean sameFingerprint = sameStableId(existing.completionFingerprint, stored.completionFingerprint);
			boolean sameRunSession = sameStableId(existing.runSessionId, stored.runSessionId);
			if (!sameFingerprint && !sameRunSession) continue;
			if (ChestOfferPersistence.attachPending(config, existing)) {
				historyRevision++;
				save();
			}
			if (existing.equivalentTo(stored) || sameFingerprint) {
				return RunRecordCommitDecision.KEEP_EXISTING;
			}
			DungeonRunTracker.LOGGER.warn(
				"[DRT] Conflicting duplicate run completion kept existing: existingRunSession={} incomingRunSession={} fingerprint={}",
				existing.runSessionId,
				stored.runSessionId,
				stored.completionFingerprint
			);
			return RunRecordCommitDecision.CONFLICT;
		}
		if (stored.completionId == null || stored.completionId.isBlank()) {
			stored.completionId = "completion-" + UUID.randomUUID();
		}
		if (stored.playerUuid == null) stored.playerUuid = currentAccountUuid();
		ChestOfferPersistence.attachPending(config, stored);
		config.runCompletions.add(stored);
		config.floorRunCounts.merge(stored.floor, 1, Integer::sum);
		save();
		return RunRecordCommitDecision.ADD_INCOMING;
	}

	public static synchronized boolean recordObservedChestOffer(String runSessionId, DungeonChestOffer incoming) {
		if (ChestOfferPersistence.record(config, runSessionId, incoming)) {
			historyRevision++;
			save();
			return true;
		}
		return false;
	}

	public static synchronized boolean markChestOfferPurchased(String runSessionId, String chestTitle) {
		if (ChestOfferPersistence.markPurchased(config, runSessionId, chestTitle)) {
			historyRevision++;
			save();
			return true;
		}
		return false;
	}

	public static synchronized RunRecordCommitDecision addRunRecord(DungeonRunRecord record) {
		if (record == null || record.lootEntries == null || record.lootEntries.isEmpty()) return RunRecordCommitDecision.KEEP_EXISTING;
		if (config.runHistory == null) config.runHistory = new ArrayList<>();
		DungeonRunRecord stored = record.copy();
		stored.normalizeCostBreakdown();
		if (isLinkedSource(stored.runLinkSource)) {
			DungeonRunCompletionRecord owner = findCompletion(stored.linkedRunId);
			if (owner == null) {
				DungeonRunTracker.LOGGER.warn("[DRT] Rejected chest with missing persisted owner: linkedRunId={} chest={}", stored.linkedRunId, stored.chestTitle);
				return RunRecordCommitDecision.CONFLICT;
			}
			stored.runSessionId = owner.runSessionId;
		}
		if (stored.commitFingerprint == null || stored.commitFingerprint.isBlank()) {
			stored.commitFingerprint = commitFingerprint(stored);
		}
		RunRecordDeduplicator.DuplicateDecision decision = RunRecordDeduplicator.decide(config.runHistory, stored);
		if (decision.action() == RunRecordCommitDecision.KEEP_EXISTING) {
			return RunRecordCommitDecision.KEEP_EXISTING;
		}
		if (decision.action() == RunRecordCommitDecision.CONFLICT) {
			DungeonRunTracker.LOGGER.warn(
				"[DRT] Conflicting duplicate run record kept existing: index={} reason={} incomingFingerprint={}",
				decision.existingIndex(),
				decision.reason(),
				stored.commitFingerprint
			);
			return RunRecordCommitDecision.CONFLICT;
		}
		if (decision.action() == RunRecordCommitDecision.REPLACE_EXISTING && decision.existingIndex() >= 0) {
			DungeonRunRecord existing = config.runHistory.get(decision.existingIndex());
			assignPersistentIds(stored, existing);
			config.runHistory.set(decision.existingIndex(), stored);
			historyRevision++;
			save();
			return RunRecordCommitDecision.REPLACE_EXISTING;
		}
		assignPersistentIds(stored, null);
		config.runHistory.add(stored);
		historyRevision++;
		save();
		return RunRecordCommitDecision.ADD_INCOMING;
	}

	private static void assignPersistentIds(DungeonRunRecord stored, DungeonRunRecord existing) {
		if (stored == null) return;
		if (existing != null) stored.manual = ManualRecord.merge(existing.manual, stored.manual);
		if (stored.playerUuid == null || stored.playerUuid.isBlank()) {
			stored.playerUuid = existing != null && existing.playerUuid != null && !existing.playerUuid.isBlank()
				? existing.playerUuid
				: currentAccountUuid();
		}
		if (stored.recordId == null || stored.recordId.isBlank()) {
			stored.recordId = existing != null && existing.recordId != null && !existing.recordId.isBlank()
				? existing.recordId
				: "record-" + UUID.randomUUID();
		}
		if (stored.runSessionId == null || stored.runSessionId.isBlank()) {
			stored.runSessionId = existing != null && existing.runSessionId != null && !existing.runSessionId.isBlank()
				? existing.runSessionId
				: "legacy-run-" + stored.recordId;
		}
		if (stored.chestSessionId == null || stored.chestSessionId.isBlank()) {
			stored.chestSessionId = existing != null && existing.chestSessionId != null && !existing.chestSessionId.isBlank()
				? existing.chestSessionId
				: "legacy-chest-" + stored.recordId;
		}
		if (existing != null && existing.chestNumber > 0) {
			stored.chestNumber = existing.chestNumber;
			config.nextChestLogNumber = Math.max(Math.max(1, config.nextChestLogNumber), existing.chestNumber + 1);
			return;
		}
		if (stored.chestNumber <= 0) {
			stored.chestNumber = nextChestLogNumber();
		} else {
			config.nextChestLogNumber = Math.max(Math.max(1, config.nextChestLogNumber), stored.chestNumber + 1);
		}
	}

	private static boolean isLinkedSource(String source) {
		return DungeonRunRecord.LINK_LIVE.equals(source)
			|| DungeonRunRecord.LINK_CROESUS_UNIQUE.equals(source)
			|| DungeonRunRecord.LINK_RECOVERED.equals(source);
	}

	private static DungeonRunCompletionRecord findCompletion(String runSessionId) {
		if (runSessionId == null || runSessionId.isBlank() || config.runCompletions == null) return null;
		for (DungeonRunCompletionRecord completion : config.runCompletions) {
			if (completion != null && runSessionId.equals(completion.runSessionId)) return completion;
		}
		return null;
	}

	private static String commitFingerprint(DungeonRunRecord record) {
		if (record == null) return "";
		StringBuilder sb = new StringBuilder();
		if (record.chestSessionId != null && !record.chestSessionId.isBlank()) {
			sb.append("chestSession=").append(record.chestSessionId).append('|');
		}
		sb.append(record.timestampEpochMillis).append('|')
			.append(normalizeText(record.floor)).append('|')
			.append(normalizeText(record.grade)).append('|')
			.append(normalizeText(record.chestTitle)).append('|')
			.append(record.totalCostCoins()).append('|');
		LinkedHashMap<String, Integer> counts = lootCounts(record);
		counts.forEach((key, quantity) -> sb.append(key).append('=').append(quantity).append(';'));
		return UUID.nameUUIDFromBytes(sb.toString().getBytes(StandardCharsets.UTF_8)).toString();
	}

	private static String completionFingerprint(DungeonRunCompletionRecord record) {
		if (record == null) return "";
		String payload = normalizeText(record.runSessionId) + '|'
			+ record.completedAtEpochMillis + '|'
			+ normalizeText(record.mode) + '|'
			+ normalizeText(record.floor) + '|'
			+ normalizeText(record.grade) + '|'
			+ Math.max(0L, record.runTimeMs);
		return UUID.nameUUIDFromBytes(payload.getBytes(StandardCharsets.UTF_8)).toString();
	}

	public static synchronized void updateFloorRunCount(String floor, int count) {
		if (config.floorRunCounts == null) config.floorRunCounts = new LinkedHashMap<>();
		config.floorRunCounts.put(floor, Math.max(0, count));
		save();
	}

	public static synchronized void updateFloorRunTimeMs(String floor, long totalMs) {
		if (config.floorRunTimeMs == null) config.floorRunTimeMs = new LinkedHashMap<>();
		config.floorRunTimeMs.put(floor, Math.max(0L, totalMs));
		save();
	}

	public static synchronized void addFloorRunTimeMs(String floor, long deltaMs) {
		if (deltaMs <= 0L || floor == null || floor.isBlank()) return;
		if (config.floorRunTimeMs == null) config.floorRunTimeMs = new LinkedHashMap<>();
		long next = Math.max(0L, config.floorRunTimeMs.getOrDefault(floor, 0L) + deltaMs);
		config.floorRunTimeMs.put(floor, next);
		save();
	}

	public static synchronized void updateHudPosition(int x, int y) {
		config.hudX = Math.max(0, x);
		config.hudY = Math.max(0, y);
		save();
	}

	public static synchronized void updateHudLayout(int x, int y, float scale) {
		config.hudX = Math.max(0, x);
		config.hudY = Math.max(0, y);
		config.hudScale = clampHudScale(scale);
		save();
	}

	public static synchronized void updateHudVisibilityMode(String mode) {
		config.hudVisibilityMode = normalizeHudVisibilityMode(mode);
		save();
	}

	public static synchronized void updateHudOverlayPreset(String preset) {
		OverlaySettings.applyPreset(config, preset);
		save();
	}

	public static synchronized void updateCustomOverlayLayout(String layout) {
		OverlaySettings.applyCustomLayout(config, layout);
		save();
	}

	public static synchronized void updateOverlaySettings(String preset, String customLayout) {
		OverlaySettings.applyPresetAndLayout(config, preset, customLayout);
		save();
	}

	/** Persist tab-detected faction reputation (and faction) when it changes. */
	public static synchronized void updateKuudraReputation(String faction, int reputation) {
		String normalizedFaction = normalizeKuudraFaction(faction);
		int clamped = clamp(reputation, 0, 100_000);
		boolean changed = false;
		if (!normalizedFaction.equals(config.kuudraFaction)) {
			config.kuudraFaction = normalizedFaction;
			changed = true;
		}
		if (config.kuudraReputation != clamped) {
			config.kuudraReputation = clamped;
			changed = true;
		}
		if (!config.kuudraReputationKnown) {
			config.kuudraReputationKnown = true;
			changed = true;
		}
		if (changed) save();
	}

	public static synchronized void updateOnboardingSettings(
		boolean onboardingComplete,
		String kuudraFaction,
		boolean kuudraPetEnabled,
		String kuudraPetRarity,
		int kuudraPetLevel,
		boolean forceSalvageArmor,
		boolean forceSalvageWands,
		boolean forceSalvageEquipment,
		boolean coolForgedEnabled,
		int coolForgedLevel,
		String bazaarPriceMode,
		int crimsonEssenceBonusPercent
	) {
		config.onboardingComplete = onboardingComplete;
		config.kuudraFaction = normalizeKuudraFaction(kuudraFaction);
		config.kuudraPetEnabled = kuudraPetEnabled;
		config.kuudraPetRarity = normalizePetRarity(kuudraPetRarity);
		config.kuudraPetLevel = clamp(kuudraPetLevel, 1, 100);
		config.forceSalvageArmor = forceSalvageArmor;
		config.forceSalvageWands = forceSalvageWands;
		config.forceSalvageEquipment = forceSalvageEquipment;
		config.coolForgedEnabled = coolForgedEnabled;
		config.coolForgedLevel = clamp(coolForgedLevel, 1, 5);
		config.bazaarPriceMode = normalizeBazaarPriceMode(bazaarPriceMode);
		config.crimsonEssenceBonusPercent = clamp(crimsonEssenceBonusPercent, 0, 100);
		save();
	}

	/** Stores a shop-read key cost. Returns true when it differed from the saved one. */
	public static synchronized boolean updateKuudraKeyShopCost(DungeonFloor tier, KuudraKeyShopCost cost) {
		if (tier == null || !tier.isKuudra() || cost == null || cost.coins <= 0L) return false;
		if (config.kuudraKeyShopCosts == null) config.kuudraKeyShopCosts = new LinkedHashMap<>();
		KuudraKeyShopCost previous = config.kuudraKeyShopCosts.get(tier.name());
		if (cost.sameCostAs(previous)) return false;
		config.kuudraKeyShopCosts.put(tier.name(), cost);
		save();
		return true;
	}

	/**
	 * Clears every floor's history after writing a before-clear backup.
	 *
	 * @param source what asked for the clear, for the log
	 * @return false when the backup failed and nothing was cleared
	 */
	public static synchronized boolean clearAllData(String source) {
		int records = config.runHistory == null ? 0 : config.runHistory.size();
		int completions = config.runCompletions == null ? 0 : config.runCompletions.size();
		Path backup = backupBeforeClear("all", source, records, completions);
		if (backup == null && records + completions > 0) return false;
		if (config.floorRunCounts != null) config.floorRunCounts.clear();
		if (config.floorRunTimeMs != null) config.floorRunTimeMs.clear();
		config.legacyRunsCompleted = 0;
		config.runHistory = new ArrayList<>();
		config.runCompletions = new ArrayList<>();
		config.pendingChestOffers = new LinkedHashMap<>();
		config.nextChestLogNumber = 1;
		historyRevision++;
		DungeonRunTracker.LOGGER.warn("[DRT] Cleared all history from {}: {} loot records, {} run completions. Backup: {}",
			source, records, completions, backup == null ? "none (nothing to clear)" : backup);
		save();
		return true;
	}

	/**
	 * Clears one floor's history after writing a before-clear backup of the whole config.
	 *
	 * @param source what asked for the clear, for the log
	 * @return false when the backup failed and nothing was cleared
	 */
	public static synchronized boolean clearFloorData(String floor, String source) {
		int records = config.runHistory == null ? 0 : (int) config.runHistory.stream().filter(r -> r != null && floor.equals(r.floor)).count();
		int completions = config.runCompletions == null ? 0 : (int) config.runCompletions.stream().filter(r -> r != null && floor.equals(r.floor)).count();
		Path backup = backupBeforeClear(floor, source, records, completions);
		if (backup == null && records + completions > 0) return false;
		if (config.floorRunCounts != null) config.floorRunCounts.remove(floor);
		if (config.floorRunTimeMs != null) config.floorRunTimeMs.remove(floor);
		if (config.runHistory != null) config.runHistory.removeIf(r -> r != null && floor.equals(r.floor));
		if (config.runCompletions != null) config.runCompletions.removeIf(r -> r != null && floor.equals(r.floor));
		if (config.pendingChestOffers != null) {
			config.pendingChestOffers.values().forEach(offers -> offers.removeIf(offer -> offer != null && offer.contextFloor != null && floor.equals(offer.contextFloor.name())));
			config.pendingChestOffers.entrySet().removeIf(entry -> entry.getValue() == null || entry.getValue().isEmpty());
		}
		historyRevision++;
		DungeonRunTracker.LOGGER.warn("[DRT] Cleared {} history from {}: {} loot records, {} run completions. Backup: {}",
			floor, source, records, completions, backup == null ? "none (nothing to clear)" : backup);
		save();
		return true;
	}

	/** @return the backup written, or null when there was nothing to back up or the write failed */
	private static Path backupBeforeClear(String scope, String source, int records, int completions) {
		if (records + completions == 0) return null;
		try {
			return HistoryBackups.writeBeforeClear(BACKUP_DIR, scope, GSON.toJson(config), System.currentTimeMillis());
		} catch (IOException | RuntimeException e) {
			DungeonRunTracker.LOGGER.error("[DRT] Clearing {} history from {} cancelled: the backup could not be written", scope, source, e);
			return null;
		}
	}

	private static void rollBackupIfDue() {
		long now = System.currentTimeMillis();
		if (primaryConfigMalformed || now < nextRollingBackupAtMillis) return;
		try {
			nextRollingBackupAtMillis = HistoryBackups.rollIfDue(BACKUP_DIR, CONFIG_PATH, now);
		} catch (IOException | RuntimeException e) {
			nextRollingBackupAtMillis = now + HistoryBackups.ROLLING_INTERVAL_MILLIS;
			DungeonRunTracker.LOGGER.warn("[DRT] Rolling history backup failed", e);
		}
	}

	public static synchronized boolean removeRunRecord(DungeonRunRecord target) {
		if (target == null || config.runHistory == null) return false;
		boolean removed = config.runHistory.removeIf(r -> matchesRunRecord(r, target));
		if (removed) {
			historyRevision++;
			save();
		}
		return removed;
	}

	/**
	 * Replaces the history entry matching {@code matchKey} with {@code replacement}.
	 * Identity fields (timestamp, chest number, floor, chest title) stay from the match key
	 * so the row remains addressable; loot/costs/profit come from the replacement.
	 */
	public static synchronized boolean updateRunRecord(DungeonRunRecord matchKey, DungeonRunRecord replacement) {
		if (matchKey == null || replacement == null || config.runHistory == null) return false;
		for (int i = 0; i < config.runHistory.size(); i++) {
			DungeonRunRecord existing = config.runHistory.get(i);
			if (!matchesRunRecord(existing, matchKey)) continue;
			DungeonRunRecord stored = replacement.copy();
			stored.timestampEpochMillis = existing.timestampEpochMillis;
			stored.chestNumber = existing.chestNumber;
			stored.runNumber = existing.runNumber;
			if (stored.runLinkSource == null) {
				stored.runLinkSource = existing.runLinkSource;
				stored.linkedRunId = existing.linkedRunId;
			}
			stored.manual = ManualRecord.merge(existing.manual, stored.manual);
			stored.normalizeCostBreakdown();
			config.runHistory.set(i, stored);
			historyRevision++;
			save();
			return true;
		}
		return false;
	}

	/**
	 * A change the player made by hand. The chest and the run it belongs to are marked edited for
	 * good, so neither is uploaded as DRT-recorded data again. Automatic updates such as repricing
	 * use {@link #updateRunRecord} and keep whatever marker the record already has.
	 */
	public static synchronized boolean editRunRecordManually(DungeonRunRecord matchKey, DungeonRunRecord replacement) {
		if (matchKey == null || replacement == null || config.runHistory == null) return false;
		DungeonRunRecord existing = null;
		for (DungeonRunRecord record : config.runHistory) {
			if (matchesRunRecord(record, matchKey)) {
				existing = record;
				break;
			}
		}
		if (existing == null) return false;
		RunHistoryLinks.Link link = RunHistoryLinks.resolve(config.runHistory, config.runCompletions).get(existing);
		DungeonRunRecord edited = replacement.copy();
		edited.manual = ManualRecord.edited(existing.manual);
		if (link != null && link.linked()) link.completion().manual = ManualRecord.edited(link.completion().manual);
		return updateRunRecord(matchKey, edited);
	}

	private static void decrementFloorRunCount(String floor) {
		if (floor == null || floor.isBlank() || config.floorRunCounts == null) return;
		int current = config.floorRunCounts.getOrDefault(floor, 0);
		if (current <= 1) config.floorRunCounts.remove(floor);
		else config.floorRunCounts.put(floor, current - 1);
	}

	private static void incrementFloorRunCount(String floor) {
		if (floor == null || floor.isBlank()) return;
		if (config.floorRunCounts == null) config.floorRunCounts = new LinkedHashMap<>();
		config.floorRunCounts.merge(floor, 1, Integer::sum);
	}

	private static boolean matchesRunRecord(DungeonRunRecord left, DungeonRunRecord right) {
		if (left == null || right == null) return false;
		if (left.timestampEpochMillis != right.timestampEpochMillis) return false;
		if (left.chestNumber != right.chestNumber) return false;
		return sameText(left.floor, right.floor) && sameText(left.chestTitle, right.chestTitle);
	}

	private static float clampHudScale(float scale) {
		if (!Float.isFinite(scale) || scale <= 0.0F) return 1.0F;
		return Math.max(MIN_HUD_SCALE, Math.min(MAX_HUD_SCALE, scale));
	}

	private static String normalizeHudVisibilityMode(String mode) {
		if (mode == null || mode.isBlank()) return "DEFAULT";
		String normalized = mode.trim().toUpperCase(java.util.Locale.ROOT);
		return switch (normalized) {
			case "GLOBAL", "DEFAULT", "DHUB" -> normalized;
			default -> "DEFAULT";
		};
	}

	private static boolean normalizeToggleFlags(DrtConfig loaded, JsonObject root) {
		// Gson may skip field initializers; missing keys on existing configs must stay ON
		// for tracking/croesus/essence. Fancy menu stays OFF unless explicitly enabled.
		boolean migrated = false;
		if (root == null || !root.has("trackingEnabled")) {
			loaded.trackingEnabled = true;
			migrated = true;
		}
		if (root == null || !root.has("croesusOverlayEnabled")) {
			loaded.croesusOverlayEnabled = true;
			migrated = true;
		}
		if (root == null || !root.has("fancyMenuEnabled")) {
			loaded.fancyMenuEnabled = false;
			migrated = true;
		}
		if (root == null || !root.has("essenceCountsTowardProfit")) {
			loaded.essenceCountsTowardProfit = true;
			migrated = true;
		}
		if (loaded.startStatsSyncBoundary(System.currentTimeMillis())) migrated = true;
		return migrated;
	}

	private static void normalizeOnboardingSettings(DrtConfig loaded) {
		DrtSettingsValidation.normalize(loaded);
		loaded.kuudraReputation = clamp(loaded.kuudraReputation, 0, 100_000);
		if (loaded.kuudraKeyShopCosts == null) loaded.kuudraKeyShopCosts = new LinkedHashMap<>();
	}

	private static void applyFreshOverlayDefaults(DrtConfig cfg) {
		cfg.hudOverlayPreset = "MODERN";
		if (cfg.customOverlayLayout == null || cfg.customOverlayLayout.isBlank()) {
			cfg.customOverlayLayout = defaultCustomOverlayLayout();
		}
	}

	private static boolean migrateOverlayPreset(DrtConfig loaded, JsonObject root) {
		boolean missingField = root == null || !root.has("hudOverlayPreset");
		String current = loaded.hudOverlayPreset;
		if (!missingField && current != null && !current.isBlank()) {
			String normalized = OverlaySettings.normalizePreset(current, false);
			if (!normalized.equals(current)) {
				loaded.hudOverlayPreset = normalized;
				return true;
			}
			return false;
		}
		// Existing installs without the field keep Legacy; brand-new empty configs use Modern.
		boolean hasPriorData = loaded.onboardingComplete
			|| loaded.legacyRunsCompleted > 0
			|| (loaded.floorRunCounts != null && !loaded.floorRunCounts.isEmpty())
			|| (loaded.runHistory != null && !loaded.runHistory.isEmpty())
			|| loaded.hudX != 10
			|| loaded.hudY != 10
			|| Math.abs(loaded.hudScale - 1.0F) > 0.001F;
		loaded.hudOverlayPreset = hasPriorData ? "LEGACY" : "MODERN";
		return true;
	}

	private static boolean normalizeCustomOverlayLayout(DrtConfig loaded) {
		if (loaded.customOverlayLayout != null && !loaded.customOverlayLayout.isBlank()) {
			String normalized = loaded.customOverlayLayout.replace("\r\n", "\n").replace('\r', '\n');
			if (!normalized.equals(loaded.customOverlayLayout)) {
				loaded.customOverlayLayout = normalized;
				return true;
			}
			return false;
		}
		loaded.customOverlayLayout = defaultCustomOverlayLayout();
		return true;
	}

	private static String defaultCustomOverlayLayout() {
		return "DRT [{floor}]\n"
			+ "Session: {runs.session} runs | {runs.avg} avg | {runs.hour}/hr\n"
			+ "Profit: {profit.session} | {profit.run}/run | {profit.hour}/hr";
	}

	private static String normalizeKuudraFaction(String faction) {
		if (faction == null || faction.isBlank()) return "MAGE";
		String normalized = faction.trim().toUpperCase(java.util.Locale.ROOT);
		return switch (normalized) {
			case "MAGE", "BARBARIAN" -> normalized;
			default -> "MAGE";
		};
	}

	private static String normalizePetRarity(String rarity) {
		if (rarity == null || rarity.isBlank()) return "LEGENDARY";
		String normalized = rarity.trim().toUpperCase(java.util.Locale.ROOT);
		return switch (normalized) {
			case "COMMON", "UNCOMMON", "RARE", "EPIC", "LEGENDARY" -> normalized;
			default -> "LEGENDARY";
		};
	}

	private static String normalizeBazaarPriceMode(String mode) {
		if (mode == null || mode.isBlank()) return "INSTANT";
		String normalized = mode.trim().toUpperCase(java.util.Locale.ROOT);
		return switch (normalized) {
			case "INSTANT", "INSTANT_SELL", "INSTANT_BUY" -> "INSTANT";
			case "ORDER", "SELL_OFFER", "BUY_ORDER" -> "ORDER";
			default -> "INSTANT";
		};
	}

	private static int clamp(int value, int min, int max) {
		return Math.max(min, Math.min(max, value));
	}

	private static boolean restoreFloorRunCountsFromCompletions(DrtConfig loaded) {
		if (loaded.runCompletions == null || loaded.runCompletions.isEmpty()) return false;
		if (loaded.floorRunCounts == null) loaded.floorRunCounts = new LinkedHashMap<>();
		boolean changed = false;
		LinkedHashMap<String, Integer> fromCompletions = new LinkedHashMap<>();
		for (DungeonRunCompletionRecord record : loaded.runCompletions) {
			if (record == null) continue;
			String floor = record.floor == null || record.floor.isBlank() ? "UNKNOWN" : record.floor;
			fromCompletions.merge(floor, 1, Integer::sum);
		}
		for (var entry : fromCompletions.entrySet()) {
			int persisted = loaded.floorRunCounts.getOrDefault(entry.getKey(), 0);
			if (persisted < entry.getValue()) {
				loaded.floorRunCounts.put(entry.getKey(), entry.getValue());
				changed = true;
			}
		}
		return changed;
	}

	private static boolean normalizeRunHistory(DrtConfig loaded) {
		boolean changed = false;
		if (loaded.nextChestLogNumber <= 0) {
			loaded.nextChestLogNumber = 1;
			changed = true;
		}
		Set<Integer> used = new HashSet<>();
		int next = Math.max(1, loaded.nextChestLogNumber);
		for (DungeonRunRecord record : loaded.runHistory) {
			if (record == null) continue;
			if (record.normalizeCostBreakdown()) changed = true;
			if (record.chestNumber > 0 && used.add(record.chestNumber)) {
				next = Math.max(next, record.chestNumber + 1);
				continue;
			}
			while (used.contains(next)) next++;
			record.chestNumber = next++;
			used.add(record.chestNumber);
			changed = true;
		}
		if (loaded.nextChestLogNumber != next) {
			loaded.nextChestLogNumber = next;
			changed = true;
		}
		if (deduplicateRunHistory(loaded.runHistory)) {
			changed = true;
		}
		return changed;
	}

	private static boolean normalizeRunCompletions(DrtConfig loaded) {
		if (loaded.runCompletions == null) {
			loaded.runCompletions = new ArrayList<>();
			return true;
		}
		boolean changed = false;
		Map<String, DungeonRunCompletionRecord> byFingerprint = new HashMap<>();
		Map<String, DungeonRunCompletionRecord> byRunSession = new HashMap<>();
		for (int i = loaded.runCompletions.size() - 1; i >= 0; i--) {
			DungeonRunCompletionRecord record = loaded.runCompletions.get(i);
			if (record == null) {
				loaded.runCompletions.remove(i);
				changed = true;
				continue;
			}
			String beforeId = record.completionId;
			String beforeFingerprint = record.completionFingerprint;
			record.normalize();
			if (record.completionFingerprint.isBlank()) {
				record.completionFingerprint = completionFingerprint(record);
			}
			if (record.completionId.isBlank()) {
				record.completionId = "completion-" + UUID.randomUUID();
			}
			DungeonRunCompletionRecord keptFingerprint = record.completionFingerprint.isBlank() ? null : byFingerprint.putIfAbsent(record.completionFingerprint, record);
			DungeonRunCompletionRecord keptRunSession = record.runSessionId.isBlank() ? null : byRunSession.putIfAbsent(record.runSessionId, record);
			if (keptFingerprint != null || keptRunSession != null) {
				DungeonRunCompletionRecord kept = keptFingerprint != null ? keptFingerprint : keptRunSession;
				mergeCompletion(kept, record);
				loaded.runCompletions.remove(i);
				changed = true;
				continue;
			}
			if (!sameText(beforeId, record.completionId) || !sameText(beforeFingerprint, record.completionFingerprint)) {
				changed = true;
			}
		}
		return changed;
	}

	private static void mergeCompletion(DungeonRunCompletionRecord kept, DungeonRunCompletionRecord incoming) {
		if (kept == null || incoming == null) return;
		kept.manual = ManualRecord.merge(kept.manual, incoming.manual);
		if ((kept.playerUuid == null || kept.playerUuid.isBlank()) && incoming.playerUuid != null) kept.playerUuid = incoming.playerUuid;
		if (!kept.hasRecordedScore() && incoming.hasRecordedScore()) {
			kept.score = incoming.score;
			kept.scoreRecorded = incoming.scoreRecorded;
		}
		if (incoming.runTimeEvidenceRank() > kept.runTimeEvidenceRank()) {
			kept.runTimeMs = incoming.runTimeMs;
			kept.runTimeSource = incoming.runTimeSource;
			kept.runTimeEvidence = incoming.runTimeEvidence;
		}
		kept.partyMembers = PartyMemberReconciler.reconcile(kept.partyMembers, incoming.partyMembers).members();
		if (kept.chestOffers == null) kept.chestOffers = new ArrayList<>();
		if (incoming.chestOffers == null) return;
		for (DungeonChestOffer offer : incoming.chestOffers) {
			if (offer == null) continue;
			DungeonChestOffer existing = null;
			for (DungeonChestOffer candidate : kept.chestOffers) {
				if (candidate != null && candidate.chestTitle.equalsIgnoreCase(offer.chestTitle)) {
					existing = candidate;
					break;
				}
			}
			if (existing == null) {
				kept.chestOffers.add(offer.copy());
			} else {
				existing.purchased |= offer.purchased;
				if ((existing.lootEntries == null || existing.lootEntries.isEmpty()) && offer.lootEntries != null && !offer.lootEntries.isEmpty()) {
					int index = kept.chestOffers.indexOf(existing);
					DungeonChestOffer replacement = offer.copy();
					replacement.purchased |= existing.purchased;
					kept.chestOffers.set(index, replacement);
				}
			}
		}
	}

	static boolean recoverPersistedRunOwnership(DrtConfig loaded) {
		return RunOwnership.recoverPersistedChestOwners(loaded, COMPLETION_SESSION_DEDUP_WINDOW_MS);
	}

	private static boolean deduplicateRunHistory(List<DungeonRunRecord> records) {
		if (records == null || records.size() < 2) return false;
		boolean changed = false;
		for (int i = 0; i < records.size(); i++) {
			DungeonRunRecord left = records.get(i);
			if (left == null) continue;
			for (int j = records.size() - 1; j > i; j--) {
				DungeonRunRecord right = records.get(j);
				if (!RunRecordDeduplicator.areDuplicateLootRecords(left, right)) continue;
				DungeonRunRecord keep = RunRecordDeduplicator.preferredForMigration(left, right);
				keep.manual = ManualRecord.merge(left.manual, right.manual);
				records.set(i, keep);
				records.remove(j);
				left = keep;
				changed = true;
			}
		}
		return changed;
	}

	private static LinkedHashMap<String, Integer> lootCounts(DungeonRunRecord record) {
		LinkedHashMap<String, Integer> counts = new LinkedHashMap<>();
		if (record == null || record.lootEntries == null) return counts;
		for (DungeonLootEntry entry : record.lootEntries) {
			if (entry == null) continue;
			counts.merge(lootKey(entry), Math.max(1, entry.quantity), Integer::sum);
		}
		return counts;
	}

	private static String lootKey(DungeonLootEntry entry) {
		if (entry == null) return "";
		String itemId = entry.itemId == null ? "" : entry.itemId.trim().toUpperCase(java.util.Locale.ROOT);
		if (!itemId.isBlank()) return itemId;
		return entry.rawName == null ? "" : entry.rawName.trim().toUpperCase(java.util.Locale.ROOT);
	}

	private static boolean sameText(String left, String right) {
		String leftText = left == null ? "" : left.trim();
		String rightText = right == null ? "" : right.trim();
		return leftText.equalsIgnoreCase(rightText);
	}

	private static boolean sameStableId(String left, String right) {
		return left != null && !left.isBlank() && right != null && !right.isBlank() && left.equals(right);
	}

	private static boolean completionTimesNear(DungeonRunCompletionRecord left, DungeonRunCompletionRecord right) {
		if (left == null || right == null) return false;
		return Math.abs(left.completedAtEpochMillis - right.completedAtEpochMillis) <= COMPLETION_SESSION_DEDUP_WINDOW_MS;
	}

	private static String normalizeText(String value) {
		return value == null ? "" : value.trim().toUpperCase(java.util.Locale.ROOT);
	}

	private static int nextChestLogNumber() {
		if (config.nextChestLogNumber <= 0) config.nextChestLogNumber = 1;
		return config.nextChestLogNumber++;
	}
}
