package dev.vy.drt.config;

import java.util.ArrayList;
import java.util.List;

/** Order-independent association of observed chest offers with persisted run completions. */
public final class ChestOfferPersistence {
	private ChestOfferPersistence() {
	}

	public static boolean record(DrtConfig config, String runSessionId, DungeonChestOffer incoming) {
		if (config == null || runSessionId == null || runSessionId.isBlank() || incoming == null) return false;
		DungeonChestOffer stored = incoming.copy();
		stored.normalize();
		if (stored.chestTitle.isBlank() || stored.lootEntries.isEmpty()) return false;
		if (config.runCompletions != null) {
			for (DungeonRunCompletionRecord completion : config.runCompletions) {
				if (completion == null || !same(completion.runSessionId, runSessionId)) continue;
				completion.normalize();
				return upsert(completion.chestOffers, stored);
			}
		}
		if (config.pendingChestOffers == null) config.pendingChestOffers = new java.util.LinkedHashMap<>();
		return upsert(config.pendingChestOffers.computeIfAbsent(runSessionId, ignored -> new ArrayList<>()), stored);
	}

	public static boolean attachPending(DrtConfig config, DungeonRunCompletionRecord completion) {
		if (config == null || completion == null || completion.runSessionId == null || completion.runSessionId.isBlank()) return false;
		if (config.pendingChestOffers == null) return false;
		List<DungeonChestOffer> pending = config.pendingChestOffers.remove(completion.runSessionId);
		if (pending == null || pending.isEmpty()) return false;
		completion.normalize();
		boolean changed = true;
		for (DungeonChestOffer offer : pending) changed |= upsert(completion.chestOffers, offer);
		return changed;
	}

	public static boolean markPurchased(DrtConfig config, String runSessionId, String chestTitle) {
		if (config == null || runSessionId == null || runSessionId.isBlank() || chestTitle == null || chestTitle.isBlank()) return false;
		if (config.runCompletions != null) {
			for (DungeonRunCompletionRecord completion : config.runCompletions) {
				if (completion == null || !same(completion.runSessionId, runSessionId)) continue;
				completion.normalize();
				if (markPurchased(completion.chestOffers, chestTitle)) return true;
			}
		}
		if (config.pendingChestOffers == null) return false;
		return markPurchased(config.pendingChestOffers.get(runSessionId), chestTitle);
	}

	private static boolean markPurchased(List<DungeonChestOffer> offers, String chestTitle) {
		if (offers == null) return false;
		for (DungeonChestOffer offer : offers) {
			if (offer == null || !offer.chestTitle.equalsIgnoreCase(chestTitle) || offer.purchased) continue;
			offer.purchased = true;
			return true;
		}
		return false;
	}

	private static boolean upsert(List<DungeonChestOffer> offers, DungeonChestOffer incoming) {
		if (offers == null || incoming == null) return false;
		DungeonChestOffer stored = incoming.copy();
		for (int i = 0; i < offers.size(); i++) {
			DungeonChestOffer existing = offers.get(i);
			if (existing == null || !existing.chestTitle.equalsIgnoreCase(stored.chestTitle)) continue;
			stored.purchased |= existing.purchased;
			if (sameOffer(existing, stored)) return false;
			offers.set(i, stored);
			return true;
		}
		offers.add(stored);
		return true;
	}

	private static boolean sameOffer(DungeonChestOffer left, DungeonChestOffer right) {
		if (left.valueCoins != right.valueCoins || left.purchased != right.purchased) return false;
		if (left.costBreakdown.totalCostCoins() != right.costBreakdown.totalCostCoins()) return false;
		if (left.lootEntries.size() != right.lootEntries.size()) return false;
		for (int i = 0; i < left.lootEntries.size(); i++) {
			DungeonLootEntry a = left.lootEntries.get(i);
			DungeonLootEntry b = right.lootEntries.get(i);
			if (a == null || b == null) {
				if (a != b) return false;
				continue;
			}
			if (a.quantity != b.quantity || !same(a.itemId, b.itemId) || !same(a.rawName, b.rawName)) return false;
		}
		return true;
	}

	private static boolean same(String left, String right) {
		return (left == null ? "" : left).equalsIgnoreCase(right == null ? "" : right);
	}
}
