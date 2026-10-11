package dev.vy.drt.luck.rules;

/** Fully known chest quality and meter modifiers for one evaluation. */
public record ResolvedModifiers(
	ModifierPreset.BossLuck bossLuck,
	ModifierPreset.TreasureAccessory treasureAccessory,
	int catacombsBoxLevel,
	int echoOfBoxesLevel,
	int echoOfEchoesLevel,
	int pityLevel
) {
	public static final ResolvedModifiers NONE = new ResolvedModifiers(
		ModifierPreset.BossLuck.NONE, ModifierPreset.TreasureAccessory.NONE, 0, 0, 0, 0);

	public ResolvedModifiers {
		if (bossLuck == null || treasureAccessory == null) throw new IllegalArgumentException("Modifiers must be known");
		requireRange("Catacombs Box", catacombsBoxLevel, ModifierPreset.MAX_BOX_LEVEL);
		requireRange("Echo of Boxes", echoOfBoxesLevel, ModifierPreset.MAX_ECHO_LEVEL);
		requireRange("Echo of Echoes", echoOfEchoesLevel, ModifierPreset.MAX_ECHO_LEVEL);
		requireRange("Pity", pityLevel, ModifierPreset.MAX_PITY_LEVEL);
	}

	/** Box quality bonus after Echo scaling, before any rounding variant. */
	public double catacombsBoxBonus() {
		double echoOfBoxesBonus = 0.02 * echoOfBoxesLevel * (1.0 + 0.05 * echoOfEchoesLevel);
		return catacombsBoxLevel * (1.0 + echoOfBoxesBonus);
	}

	private static void requireRange(String name, int value, int max) {
		if (value < 0 || value > max) throw new IllegalArgumentException(name + " level out of range: " + value);
	}
}
