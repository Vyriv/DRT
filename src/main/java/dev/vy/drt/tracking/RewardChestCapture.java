package dev.vy.drt.tracking;

/**
 * Decisions for dungeon chest GUIs that fill in over more than one inventory snapshot.
 */
public final class RewardChestCapture {
	private RewardChestCapture() {
	}

	/**
	 * Slots can arrive before the Open Reward Chest button. A capture taken in that gap is a
	 * preview, not an opened chest, once the button shows up and chat never confirmed the open.
	 */
	public static boolean shouldDiscardUnconfirmedCapture(boolean chestAssigned, boolean chatConfirmed) {
		return chestAssigned && !chatConfirmed;
	}

	/** The chest is still open and its collection window has not closed, so a later slot update counts. */
	public static boolean shouldReadLaterSnapshot(boolean samePendingChest, boolean collectionWindowOpen) {
		return samePendingChest && collectionWindowOpen;
	}

	/**
	 * When chat already listed the chest, a later GUI snapshot may fill an item chat missed.
	 * It must not add a second copy of an item chat already recorded.
	 */
	public static boolean shouldAddGuiItem(boolean chatAlreadyAuthoritative, boolean alreadyPresent) {
		return !alreadyPresent || !chatAlreadyAuthoritative;
	}

	/**
	 * A rare-reward line inside the open chest summary is loot, even when it is the first item.
	 * A stray rare line must not create a chest by itself.
	 */
	public static boolean includeRareRewardLine(boolean rewardBlockLine, boolean pendingEmpty, boolean seededFromGui) {
		if (rewardBlockLine) return true;
		return !pendingEmpty || seededFromGui;
	}
}
