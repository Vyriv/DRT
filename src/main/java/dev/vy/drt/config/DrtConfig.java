package dev.vy.drt.config;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class DrtConfig {
	public boolean enabled = true;
	public int legacyRunsCompleted;
	public Map<String, Integer> floorRunCounts = new LinkedHashMap<>();
	public int hudX = 10;
	public int hudY = 10;
	public List<DungeonRunRecord> runHistory = new ArrayList<>();
	public String selectedFloor = null;
	public int witherEssenceValuePer = 2600;
	public int undeadEssenceValuePer = 722;
}
