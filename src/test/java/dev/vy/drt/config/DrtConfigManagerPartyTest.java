package dev.vy.drt.config;

import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DrtConfigManagerPartyTest {
	@Test
	void completeCroesusEntryRepairsClassLevelArtifactAndPreservesUnrelatedMember() {
		var corrupted = new DungeonPartyMember("41", "", "", 0);
		var unrelated = new DungeonPartyMember("OtherPlayer", "b".repeat(32), "Tank", 35);
		var parsed = new DungeonPartyMember("catgirlVivid", "", "Mage", 41);

		var merged = PartyMemberReconciler.reconcile(List.of(corrupted, unrelated), List.of(parsed)).members();

		assertEquals(2, merged.size());
		assertEquals("OtherPlayer", merged.getFirst().name);
		assertEquals("catgirlVivid", merged.get(1).name);
		assertEquals("Mage", merged.get(1).dungeonClass);
		assertEquals(41, merged.get(1).classLevel);
	}

	@Test
	void richerSamePlayerEntryRefreshesIncompleteSavedData() {
		var incomplete = new DungeonPartyMember("catgirlVivid", "", "", 0);
		var parsed = new DungeonPartyMember("catgirlVivid", "", "Mage", 41);
		var merged = PartyMemberReconciler.reconcile(List.of(incomplete), List.of(parsed)).members();
		assertEquals("Mage", merged.getFirst().dungeonClass);
		assertEquals(41, merged.getFirst().classLevel);
	}
}
