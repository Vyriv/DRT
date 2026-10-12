package dev.vy.drt.config;

import java.util.Objects;

/**
 * Two-click confirmation for clearing history. The confirming click must be on the same scope,
 * at least {@link #MIN_CONFIRM_DELAY_MILLIS} after the first and within {@link #CONFIRM_WINDOW_MILLIS},
 * so a fast double-click never confirms.
 */
public final class ClearConfirmation {
	public static final long MIN_CONFIRM_DELAY_MILLIS = 500L;
	public static final long CONFIRM_WINDOW_MILLIS = 3_000L;

	public enum Result { ARMED, TOO_SOON, CONFIRMED }

	private long armedAt = -1L;
	private String scope;

	/** @param scope the floor key being cleared, or null for every floor */
	public Result click(String scope, long now) {
		if (isArmed(scope, now)) {
			if (now - armedAt < MIN_CONFIRM_DELAY_MILLIS) return Result.TOO_SOON;
			reset();
			return Result.CONFIRMED;
		}
		armedAt = now;
		this.scope = scope;
		return Result.ARMED;
	}

	public boolean isArmed(String scope, long now) {
		return armedAt >= 0L && now >= armedAt && now - armedAt <= CONFIRM_WINDOW_MILLIS && Objects.equals(this.scope, scope);
	}

	public void reset() {
		armedAt = -1L;
		scope = null;
	}
}
