package dev.vy.drt.luck.capture;

/**
 * The run DRT just completed, while the player is still in the same dungeon instance. Only the
 * tracker creates one, and only from its own verified completion; it is never derived from a
 * Croesus screen or from timing alone.
 */
public record LiveRewardSession(String drtRunSessionId, String floor) {
	public LiveRewardSession {
		drtRunSessionId = drtRunSessionId == null ? "" : drtRunSessionId;
		floor = floor == null ? "" : floor;
	}
}
