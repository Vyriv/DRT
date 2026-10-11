package dev.vy.drt.luck.capture;

import java.util.ArrayList;
import java.util.List;

/**
 * Croesus chests DRT saw but could not tie to exactly one captured run. Kept as observed so a
 * later pass can link them; never merged into a run by guesswork.
 */
public final class UnmatchedCroesusRun {
	public long firstObservedAtEpochMillis;
	public long lastObservedAtEpochMillis;
	public String reason = "";
	public String viewTitle = "";
	public String floor;
	public Integer floorNumber;
	public Boolean masterMode;
	public String entryName;
	public List<String> players = new ArrayList<>();
	public String completedText;
	public Long completedAfterEpochMillis;
	public Long completedBeforeEpochMillis;
	public List<String> availableModifiers = new ArrayList<>();
	public List<LuckRunRecord.LuckChestRecord> chests = new ArrayList<>();
	public String fingerprint = "";
}
