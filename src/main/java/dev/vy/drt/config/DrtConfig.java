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
	public float hudScale = 1.0F;
	public String hudVisibilityMode = "DEFAULT";
	public List<DungeonRunRecord> runHistory = new ArrayList<>();
	public int nextChestLogNumber = 1;
	public String selectedFloor = null;
	public int witherEssenceValuePer = 2600;
	public int undeadEssenceValuePer = 722;
	public boolean onboardingComplete = false;
	public String kuudraFaction = "MAGE";
	public boolean kuudraPetEnabled = false;
	public String kuudraPetRarity = "LEGENDARY";
	public int kuudraPetLevel = 100;
	public boolean forceSalvageArmor = false;
	public boolean forceSalvageWands = false;
	public boolean forceSalvageEquipment = false;
	public boolean coolForgedEnabled = false;
	public int coolForgedLevel = 1;
	public String bazaarPriceMode = "INSTANT";
}
