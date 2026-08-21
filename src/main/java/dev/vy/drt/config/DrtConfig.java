package dev.vy.drt.config;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class DrtConfig {
	/** HUD overlay visibility. Does not disable tracking or onboarding. */
	public boolean enabled = true;
	/** When false, skip run detection, loot capture, and loot windows. */
	public boolean trackingEnabled = true;
	/** Croesus chest overlay and slot highlights. */
	public boolean croesusOverlayEnabled = true;
	/** Replaces Wood–Bedrock reward chest inventories with DRT's fancy reward panel. Default off. */
	public boolean fancyMenuEnabled = false;
	/** When false, essence items contribute 0 to profit calculations. */
	public boolean essenceCountsTowardProfit = true;
	public int legacyRunsCompleted;
	public Map<String, Integer> floorRunCounts = new LinkedHashMap<>();
	/** Lifetime accumulated run duration per floor, in milliseconds. */
	public Map<String, Long> floorRunTimeMs = new LinkedHashMap<>();
	public int hudX = 10;
	public int hudY = 10;
	public float hudScale = 1.0F;
	public String hudVisibilityMode = "DEFAULT";
	/** null until first load/migration; fresh installs become MODERN, existing configs LEGACY. */
	public String hudOverlayPreset = null;
	public String customOverlayLayout = null;
	public List<DungeonRunRecord> runHistory = new ArrayList<>();
	/** Idempotent committed run completions; floorRunCounts is the compatibility cache. */
	public List<DungeonRunCompletionRecord> runCompletions = new ArrayList<>();
	public int nextChestLogNumber = 1;
	public String selectedFloor = null;
	public int witherEssenceValuePer = 2600;
	public int undeadEssenceValuePer = 722;
	public boolean onboardingComplete = false;
	public String kuudraFaction = "MAGE";
	/** Last Mage/Barbarian reputation read from tab (Crimson Isle). Used for emissary key coin discounts. */
	public int kuudraReputation = 0;
	public boolean kuudraReputationKnown = false;
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
