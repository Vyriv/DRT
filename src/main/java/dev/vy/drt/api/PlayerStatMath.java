package dev.vy.drt.api;

/** Pure derived values for the current-player Runs projection. */
public final class PlayerStatMath {
	private PlayerStatMath() {}

	public static Double secretsPerRun(Long secretsFound, Long totalCompletions) {
		if (secretsFound == null || totalCompletions == null || totalCompletions <= 0L) return null;
		return secretsFound.doubleValue() / totalCompletions.doubleValue();
	}
}
