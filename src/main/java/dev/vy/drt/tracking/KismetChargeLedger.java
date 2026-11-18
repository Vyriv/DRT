package dev.vy.drt.tracking;

import java.util.ArrayDeque;
import java.util.Locale;

/**
 * One Kismet Feather rerolls one chest. Several chests in the same loot window can each take one.
 * A chat line and the chest-row change for that same use count once.
 */
public final class KismetChargeLedger {
	public enum FeatherUse {
		APPLY_TO_OPEN_CHEST,
		QUEUED
	}

	private int unassignedUses;
	private final ArrayDeque<String> rerolledTitles = new ArrayDeque<>();
	private long validUntilMillis;

	public FeatherUse onFeatherUsed(
		boolean chestOpen,
		boolean openChestAlreadyCharged,
		String openChestTitle,
		long now,
		long ttlMillis
	) {
		touch(now, ttlMillis);
		if (chestOpen && !openChestAlreadyCharged) {
			rerolledTitles.remove(normalize(openChestTitle));
			return FeatherUse.APPLY_TO_OPEN_CHEST;
		}
		unassignedUses++;
		return FeatherUse.QUEUED;
	}

	public void onChestContentsChanged(
		String chestTitle,
		boolean openChestAlreadyCharged,
		String openChestTitle,
		long now,
		long ttlMillis
	) {
		String title = normalize(chestTitle);
		if (title.isEmpty()) return;
		touch(now, ttlMillis);
		if (openChestAlreadyCharged && title.equals(normalize(openChestTitle))) return;
		if (!rerolledTitles.contains(title)) rerolledTitles.addLast(title);
	}

	/** Overlay hint for the chest row whose contents actually changed. Does not consume the use. */
	public boolean wouldChargeSpecific(String chestTitle, long now) {
		expireIfNeeded(now);
		String title = normalize(chestTitle);
		return !title.isEmpty() && rerolledTitles.contains(title);
	}

	public boolean commitCharge(String chestTitle, boolean chestAlreadyCharged, long now) {
		expireIfNeeded(now);
		if (chestAlreadyCharged) return false;
		String title = normalize(chestTitle);
		if (!title.isEmpty() && rerolledTitles.remove(title)) {
			if (unassignedUses > 0) unassignedUses--;
			return true;
		}
		if (unassignedUses > 0 && rerolledTitles.isEmpty()) {
			unassignedUses--;
			return true;
		}
		return false;
	}

	public void clear() {
		unassignedUses = 0;
		rerolledTitles.clear();
		validUntilMillis = 0L;
	}

	public int unassignedUses() {
		return unassignedUses;
	}

	private void touch(long now, long ttlMillis) {
		expireIfNeeded(now);
		validUntilMillis = now + Math.max(0L, ttlMillis);
	}

	private void expireIfNeeded(long now) {
		if (validUntilMillis > 0L && now > validUntilMillis) clear();
	}

	private static String normalize(String title) {
		return title == null ? "" : title.trim().toUpperCase(Locale.ROOT);
	}
}
