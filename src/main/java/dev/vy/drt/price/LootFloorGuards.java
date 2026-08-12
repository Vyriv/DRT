package dev.vy.drt.price;

import dev.vy.drt.config.DungeonFloor;
import dev.vy.drt.config.DungeonLootEntry;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Soft detection guards: warn in logs when loot looks abnormal vs expected tables, never drop entries.
 */
public final class LootFloorGuards {
	private static final Set<String> CATACOMBS_ESSENCE = Set.of(
		"ESSENCE_WITHER", "ESSENCE_UNDEAD", "ESSENCE_SPIDER", "ESSENCE_DRAGON",
		"ESSENCE_ICE", "ESSENCE_DIAMOND", "ESSENCE_GOLD"
	);
	private static final Set<String> KUUDRA_ESSENCE = Set.of("ESSENCE_CRIMSON");

	private LootFloorGuards() {
	}

	public static List<String> evaluate(DungeonFloor floor, DungeonLootEntry entry) {
		return evaluate(floor, null, entry);
	}

	public static List<String> evaluate(DungeonFloor floor, String chestTitle, DungeonLootEntry entry) {
		List<String> reasons = new ArrayList<>(3);
		if (entry == null) return reasons;
		String raw = entry.rawName == null ? "" : entry.rawName;
		String itemId = entry.itemId == null ? "" : entry.itemId;
		int qty = Math.max(1, entry.quantity);
		ExpectedLootTables.ChestTier chest = ExpectedLootTables.parseChestTier(chestTitle);

		if (isEssence(raw, itemId)) {
			addEssenceReasons(reasons, floor, chest, raw, itemId, qty);
			return reasons;
		}

		if (ExpectedLootTables.isKuudraTeeth(raw, itemId) && floor != null && floor.isKuudra()
			&& chest == ExpectedLootTables.ChestTier.PAID) {
			int min = ExpectedLootTables.paidKuudraTeethMin(floor);
			int max = ExpectedLootTables.paidKuudraTeethMax(floor);
			if (min > 0 && qty < min) {
				reasons.add("kuudra_teeth_below_expected_min=" + min);
			} else if (max > 0 && qty > max + 2) {
				reasons.add("kuudra_teeth_above_expected_max=" + max);
			}
			return reasons;
		}

		if (ExpectedLootTables.isKrakenShard(raw, itemId) && floor != null && floor.isKuudra()
			&& chest == ExpectedLootTables.ChestTier.PAID) {
			int expected = ExpectedLootTables.guaranteedKrakenShards(floor);
			if (expected > 0 && qty < expected) {
				reasons.add("kraken_shards_below_guaranteed=" + expected);
			} else if (expected > 0 && qty > expected + 3) {
				reasons.add("kraken_shards_above_guaranteed=" + expected);
			}
			return reasons;
		}

		if (floor != null && floor != DungeonFloor.UNKNOWN
			&& !ManualLootSuggestions.isKnownDrop(floor, raw, itemId)
			&& ManualLootSuggestions.isFloorUniqueDropElsewhere(floor, raw, itemId)) {
			reasons.add("wrong_floor_unique_drop");
		} else if (floor != null && floor != DungeonFloor.UNKNOWN
			&& !ManualLootSuggestions.isKnownDrop(floor, raw, itemId)) {
			reasons.add("not_in_known_drop_list");
		}
		return reasons;
	}

	/**
	 * Flush-time pass: warn when expected guaranteed lines are missing from the claimed chest.
	 * Never removes entries.
	 */
	public static List<String> evaluateChest(DungeonFloor floor, String chestTitle, List<DungeonLootEntry> entries) {
		List<String> reasons = new ArrayList<>();
		if (entries == null) return reasons;

		int dropLines = 0;
		for (DungeonLootEntry entry : entries) {
			if (entry != null) dropLines++;
		}
		// Reward chests normally yield several lines; fewer than 4 usually means a truncated capture.
		if (dropLines > 0 && dropLines < 4) {
			reasons.add("chest_drop_count_below_4 got=" + dropLines);
		}

		if (floor == null || floor == DungeonFloor.UNKNOWN) return reasons;
		ExpectedLootTables.ChestTier chest = ExpectedLootTables.parseChestTier(chestTitle);
		if (chest == ExpectedLootTables.ChestTier.UNKNOWN) return reasons;

		int undead = 0;
		int wither = 0;
		int crimson = 0;
		int teeth = 0;
		int kraken = 0;
		for (DungeonLootEntry entry : entries) {
			if (entry == null) continue;
			String raw = entry.rawName == null ? "" : entry.rawName;
			String id = entry.itemId == null ? "" : entry.itemId;
			int qty = Math.max(1, entry.quantity);
			if (ExpectedLootTables.isUndeadEssence(raw, id)) undead += qty;
			else if (ExpectedLootTables.isWitherEssence(raw, id)) wither += qty;
			else if (ExpectedLootTables.isCrimsonEssence(raw, id)) crimson += qty;
			else if (ExpectedLootTables.isKuudraTeeth(raw, id)) teeth += qty;
			else if (ExpectedLootTables.isKrakenShard(raw, id)) kraken += qty;
		}

		if (floor.isCatacombs() && chest.ordinal() <= ExpectedLootTables.ChestTier.BEDROCK.ordinal()) {
			int gu = ExpectedLootTables.guaranteedUndeadEssence(floor, chest);
			int gw = ExpectedLootTables.guaranteedWitherEssence(floor, chest);
			if (gu > 0 && undead < gu) {
				reasons.add("missing_guaranteed_undead_essence expected>=" + gu + " got=" + undead);
			}
			if (gw > 0 && wither < gw) {
				reasons.add("missing_guaranteed_wither_essence expected>=" + gw + " got=" + wither);
			}
		}

		if (floor.isKuudra() && chest == ExpectedLootTables.ChestTier.FREE) {
			int expected = ExpectedLootTables.freeCrimsonEssence(floor);
			if (expected > 0 && crimson < expected) {
				reasons.add("missing_free_crimson_essence expected>=" + expected + " got=" + crimson);
			}
		}

		if (floor.isKuudra() && chest == ExpectedLootTables.ChestTier.PAID) {
			int expectedCrimson = ExpectedLootTables.paidCrimsonEssenceDefault(floor);
			int teethMin = ExpectedLootTables.paidKuudraTeethMin(floor);
			int krakenExpected = ExpectedLootTables.guaranteedKrakenShards(floor);
			if (expectedCrimson > 0 && crimson < expectedCrimson) {
				reasons.add("missing_paid_crimson_essence expected>=" + expectedCrimson + " got=" + crimson + " (tunable_default)");
			}
			if (teethMin > 0 && teeth < teethMin) {
				reasons.add("missing_paid_kuudra_teeth expected>=" + teethMin + " got=" + teeth + " (tunable_default)");
			}
			if (krakenExpected > 0 && kraken < krakenExpected) {
				reasons.add("missing_guaranteed_kraken_shards expected>=" + krakenExpected + " got=" + kraken);
			}
		}

		return reasons;
	}

	private static void addEssenceReasons(
		List<String> reasons,
		DungeonFloor floor,
		ExpectedLootTables.ChestTier chest,
		String raw,
		String itemId,
		int qty
	) {
		if (qty >= ExpectedLootTables.ESSENCE_ABSOLUTE_SOFT_CEILING) {
			reasons.add("essence_qty_soft_ceiling>=" + ExpectedLootTables.ESSENCE_ABSOLUTE_SOFT_CEILING);
		}

		if (floor != null && floor != DungeonFloor.UNKNOWN) {
			if (floor.isKuudra() && looksLikeCatacombsEssence(raw, itemId)) {
				reasons.add("catacombs_essence_on_kuudra");
			} else if (!floor.isKuudra() && looksLikeKuudraEssence(raw, itemId)) {
				reasons.add("kuudra_essence_on_catacombs");
			}
		}

		if (floor == null || floor == DungeonFloor.UNKNOWN || chest == ExpectedLootTables.ChestTier.UNKNOWN) {
			return;
		}

		if (floor.isCatacombs()) {
			if (ExpectedLootTables.isUndeadEssence(raw, itemId)) {
				int guaranteed = ExpectedLootTables.guaranteedUndeadEssence(floor, chest);
				addGuaranteedEssenceDelta(reasons, "undead", guaranteed, qty);
			} else if (ExpectedLootTables.isWitherEssence(raw, itemId)) {
				int guaranteed = ExpectedLootTables.guaranteedWitherEssence(floor, chest);
				addGuaranteedEssenceDelta(reasons, "wither", guaranteed, qty);
			}
			return;
		}

		if (floor.isKuudra() && ExpectedLootTables.isCrimsonEssence(raw, itemId)) {
			if (chest == ExpectedLootTables.ChestTier.FREE) {
				int guaranteed = ExpectedLootTables.freeCrimsonEssence(floor);
				if (guaranteed > 0 && qty < guaranteed) {
					reasons.add("crimson_below_free_guaranteed=" + guaranteed);
				} else if (guaranteed > 0 && qty > guaranteed + ExpectedLootTables.ESSENCE_FILLER_HEADROOM) {
					reasons.add("crimson_above_free_expected=" + guaranteed);
				}
			} else if (chest == ExpectedLootTables.ChestTier.PAID) {
				int expected = ExpectedLootTables.paidCrimsonEssenceDefault(floor);
				if (expected > 0 && qty < expected) {
					reasons.add("crimson_below_paid_default=" + expected + " (tunable)");
				} else if (expected > 0 && qty > ExpectedLootTables.softMaxForGuaranteed(expected)) {
					reasons.add("crimson_above_paid_default=" + expected + " (tunable)");
				}
			}
		}
	}

	private static void addGuaranteedEssenceDelta(List<String> reasons, String kind, int guaranteed, int qty) {
		if (guaranteed <= 0) return;
		if (qty < guaranteed) {
			reasons.add(kind + "_essence_below_guaranteed=" + guaranteed);
		} else if (qty > ExpectedLootTables.softMaxForGuaranteed(guaranteed)) {
			reasons.add(kind + "_essence_above_expected_soft_max=" + ExpectedLootTables.softMaxForGuaranteed(guaranteed));
		}
	}

	private static boolean isEssence(String raw, String itemId) {
		String upperName = raw.toUpperCase(Locale.ROOT);
		String upperId = itemId.toUpperCase(Locale.ROOT);
		return upperId.contains("ESSENCE") || upperName.contains("ESSENCE");
	}

	private static boolean looksLikeCatacombsEssence(String raw, String itemId) {
		String upperId = itemId.toUpperCase(Locale.ROOT);
		if (CATACOMBS_ESSENCE.contains(upperId)) return true;
		String upperName = raw.toUpperCase(Locale.ROOT);
		return upperName.contains("WITHER ESSENCE")
			|| upperName.contains("UNDEAD ESSENCE")
			|| upperName.contains("SPIDER ESSENCE")
			|| upperName.contains("DRAGON ESSENCE")
			|| upperName.contains("ICE ESSENCE")
			|| upperName.contains("DIAMOND ESSENCE")
			|| upperName.contains("GOLD ESSENCE");
	}

	private static boolean looksLikeKuudraEssence(String raw, String itemId) {
		String upperId = itemId.toUpperCase(Locale.ROOT);
		if (KUUDRA_ESSENCE.contains(upperId)) return true;
		return raw.toUpperCase(Locale.ROOT).contains("CRIMSON ESSENCE");
	}
}
