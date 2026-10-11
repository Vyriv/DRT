package dev.vy.drt.luck.capture;

/** A selectable drop on a floor's RNG Meter screen, as last read. */
public final class LuckMeterRequirement {
	public String floor = "";
	public String itemName = "";
	public String itemKey;
	public Long requiredScore;
	public String requiredText;
	public String appearsIn;
	public String oddsLabel;
	public Double oddsPercent;
	public String actionLine;
	public long observedAtEpochMillis;
}
