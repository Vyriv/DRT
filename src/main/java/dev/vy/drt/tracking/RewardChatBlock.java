package dev.vy.drt.tracking;

/**
 * The indented lines Hypixel prints under a "CHEST REWARDS" header, one item per line:
 * <pre>
 *   WOOD CHEST REWARDS
 *     Enchanted Book (Rejuvenate I)
 *     Undead Essence x19
 * </pre>
 * Only lines that follow the header without a break and keep the indentation count; a blank or
 * unindented line ends the block, so ordinary chat can never be read as a reward.
 */
public final class RewardChatBlock {
	private static final int MIN_INDENT = 2;

	private boolean open;

	/** Starts a block after a header that was assigned to the chest being collected. */
	public void open() {
		open = true;
	}

	public void close() {
		open = false;
	}

	public boolean isOpen() {
		return open;
	}

	/**
	 * Whether this line, with its leading whitespace intact, is a reward line of the open block.
	 * Any other line ends the block.
	 */
	public boolean acceptLine(String untrimmedLine) {
		if (!open) return false;
		if (untrimmedLine == null || untrimmedLine.isBlank() || !indented(untrimmedLine)) {
			open = false;
			return false;
		}
		return true;
	}

	static boolean indented(String line) {
		int spaces = 0;
		while (spaces < line.length() && line.charAt(spaces) == ' ') spaces++;
		return spaces >= MIN_INDENT && spaces < line.length();
	}
}
