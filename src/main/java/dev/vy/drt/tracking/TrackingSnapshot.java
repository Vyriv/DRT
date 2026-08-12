package dev.vy.drt.tracking;

import dev.vy.drt.config.DungeonFloor;
import dev.vy.drt.config.ChestCostBreakdown;
import java.util.List;
import java.util.Map;

public record TrackingSnapshot(
	String activeRunId,
	RunMode activeMode,
	DungeonFloor activeFloor,
	String activeGrade,
	int completedRuns,
	int abandonedRuns,
	int chestCount,
	int committedChests,
	Map<String, RunState> runStates,
	Map<String, ChestState> chestStates,
	Map<String, String> chestOwners,
	Map<String, ChestCostBreakdown> chestCosts,
	Map<String, RunMode> chestModes,
	Map<String, DungeonFloor> chestFloors,
	Map<String, List<ResolvedLoot>> chestLoot,
	List<String> invariants
) {
}
