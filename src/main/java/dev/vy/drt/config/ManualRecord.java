package dev.vy.drt.config;

/**
 * Values of the {@code manual} field on run and chest records. Once set it is never cleared:
 * saving, reloading, repricing or an automatic re-capture keeps it, and a manual record never
 * counts as DRT-recorded data for online statistics.
 */
public final class ManualRecord {
	/** Entered by the player in the manual run screen. */
	public static final String CREATED = "CREATED";
	/** Recorded by DRT, then changed by the player. */
	public static final String EDITED = "EDITED";

	private ManualRecord() {
	}

	/** The stronger of two markers: created beats edited beats none. */
	public static String merge(String left, String right) {
		if (CREATED.equals(left) || CREATED.equals(right)) return CREATED;
		if (EDITED.equals(left) || EDITED.equals(right)) return EDITED;
		return null;
	}

	/** The marker after the player edits a record carrying {@code current}. */
	public static String edited(String current) {
		return merge(current, EDITED);
	}

	static String normalize(String value) {
		return merge(value, null);
	}
}
