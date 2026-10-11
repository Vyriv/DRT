package dev.vy.drt.luck.capture;

import dev.vy.drt.luck.rules.ModifierPreset;
import java.util.Objects;

/**
 * The single global modifier preset a player configures for Luck Rating. Null fields mean
 * "unknown" and are evaluated as a range, never as zero. Stored as plain fields for Gson.
 */
public final class LuckModifierProfile {
	public String bossLuck;
	public String treasureAccessory;
	public Integer catacombsBoxLevel;
	public Integer echoOfBoxesLevel;
	public Integer echoOfEchoesLevel;
	public Integer pityLevel;
	public int revision;
	public long updatedAtEpochMillis;

	public static LuckModifierProfile unknown() {
		return new LuckModifierProfile();
	}

	public static LuckModifierProfile of(ModifierPreset preset) {
		LuckModifierProfile profile = new LuckModifierProfile();
		if (preset == null) return profile;
		profile.bossLuck = preset.bossLuck() == null ? null : preset.bossLuck().name();
		profile.treasureAccessory = preset.treasureAccessory() == null ? null : preset.treasureAccessory().name();
		profile.catacombsBoxLevel = preset.catacombsBoxLevel();
		profile.echoOfBoxesLevel = preset.echoOfBoxesLevel();
		profile.echoOfEchoesLevel = preset.echoOfEchoesLevel();
		profile.pityLevel = preset.pityLevel();
		return profile;
	}

	public ModifierPreset toPreset() {
		return new ModifierPreset(
			parseEnum(ModifierPreset.BossLuck.class, bossLuck),
			parseEnum(ModifierPreset.TreasureAccessory.class, treasureAccessory),
			level(catacombsBoxLevel, ModifierPreset.MAX_BOX_LEVEL),
			level(echoOfBoxesLevel, ModifierPreset.MAX_ECHO_LEVEL),
			level(echoOfEchoesLevel, ModifierPreset.MAX_ECHO_LEVEL),
			level(pityLevel, ModifierPreset.MAX_PITY_LEVEL)
		);
	}

	/** Drops values that do not parse or are out of range, turning them into unknowns. */
	public void normalize() {
		ModifierPreset preset = toPreset();
		bossLuck = preset.bossLuck() == null ? null : preset.bossLuck().name();
		treasureAccessory = preset.treasureAccessory() == null ? null : preset.treasureAccessory().name();
		catacombsBoxLevel = preset.catacombsBoxLevel();
		echoOfBoxesLevel = preset.echoOfBoxesLevel();
		echoOfEchoesLevel = preset.echoOfEchoesLevel();
		pityLevel = preset.pityLevel();
		revision = Math.max(0, revision);
		updatedAtEpochMillis = Math.max(0L, updatedAtEpochMillis);
	}

	public int unknownFieldCount() {
		ModifierPreset preset = toPreset();
		int unknown = 0;
		if (preset.bossLuck() == null) unknown++;
		if (preset.treasureAccessory() == null) unknown++;
		if (preset.catacombsBoxLevel() == null) unknown++;
		if (preset.echoOfBoxesLevel() == null) unknown++;
		if (preset.echoOfEchoesLevel() == null) unknown++;
		if (preset.pityLevel() == null) unknown++;
		return unknown;
	}

	public boolean sameValues(LuckModifierProfile other) {
		if (other == null) return false;
		return toPreset().equals(other.toPreset());
	}

	public LuckModifierProfile copy() {
		LuckModifierProfile copy = LuckModifierProfile.of(toPreset());
		copy.revision = revision;
		copy.updatedAtEpochMillis = updatedAtEpochMillis;
		return copy;
	}

	private static <E extends Enum<E>> E parseEnum(Class<E> type, String name) {
		if (name == null || name.isBlank()) return null;
		try {
			return Enum.valueOf(type, name.trim());
		} catch (IllegalArgumentException e) {
			return null;
		}
	}

	private static Integer level(Integer value, int max) {
		if (value == null || value < 0 || value > max) return null;
		return value;
	}

	@Override
	public boolean equals(Object o) {
		if (!(o instanceof LuckModifierProfile other)) return false;
		return revision == other.revision && sameValues(other);
	}

	@Override
	public int hashCode() {
		return Objects.hash(toPreset(), revision);
	}
}
