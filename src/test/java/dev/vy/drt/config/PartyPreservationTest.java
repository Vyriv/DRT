package dev.vy.drt.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.util.List;
import org.junit.jupiter.api.Test;

class PartyPreservationTest {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

	@Test
	void namesOnlyHistoryGainsClassesWhenCroesusShowsThem() {
		List<DungeonPartyMember> apiNamesOnly = List.of(new DungeonPartyMember("beganjava", "", "", 0), new DungeonPartyMember("Wzxh", "", "", 0));
		var merged = PartyMemberReconciler.reconcile(apiNamesOnly, List.of(
			new DungeonPartyMember("beganjava", "", "Berserk", 46), new DungeonPartyMember("Wzxh", "", "Tank", 50))).members();
		assertEquals("Berserk", merged.get(0).dungeonClass);
		assertEquals(46, merged.get(0).classLevel);
		assertEquals("Tank", merged.get(1).dungeonClass);
		assertEquals(50, merged.get(1).classLevel);
	}

	@Test
	void anUnknownValueNeverReplacesAKnownOne() {
		List<DungeonPartyMember> known = List.of(new DungeonPartyMember("catgirlVivid", "e8a20d35b48b4fa1bd924df9049ae76f", "Mage", 40, ""));
		var result = PartyMemberReconciler.reconcile(known, List.of(new DungeonPartyMember("catgirlVivid", "", "", 0)));
		assertFalse(result.changed());
		assertEquals("Mage", result.members().getFirst().dungeonClass);
		assertEquals(40, result.members().getFirst().classLevel);
		assertEquals("e8a20d35b48b4fa1bd924df9049ae76f", result.members().getFirst().uuid);
	}

	@Test
	void aRecordedClassIsKeptWhenALaterSourceDisagrees() {
		var result = PartyMemberReconciler.reconcile(List.of(new DungeonPartyMember("PlayerOne", "", "Mage", 41)),
			List.of(new DungeonPartyMember("PlayerOne", "", "Tank", 30)));
		assertFalse(result.changed());
		assertEquals("Mage", result.members().getFirst().dungeonClass);
		assertEquals(41, result.members().getFirst().classLevel);
	}

	@Test
	void aMissingLevelIsFilledOnlyForTheSameClass() {
		var same = PartyMemberReconciler.reconcile(List.of(new DungeonPartyMember("PlayerOne", "", "Mage", 0)),
			List.of(new DungeonPartyMember("PlayerOne", "", "Mage", 41))).members().getFirst();
		assertEquals(41, same.classLevel);
		var different = PartyMemberReconciler.reconcile(List.of(new DungeonPartyMember("PlayerOne", "", "Mage", 0)),
			List.of(new DungeonPartyMember("PlayerOne", "", "Tank", 30))).members().getFirst();
		assertEquals("Mage", different.dungeonClass);
		assertEquals(0, different.classLevel);
	}

	@Test
	void kuudraRolesMergeWithoutBecomingClasses() {
		var merged = PartyMemberReconciler.reconcile(List.of(new DungeonPartyMember("KanKraft", "", "", 0)),
			List.of(DungeonPartyMember.kuudra("KanKraft", "Routeless"), DungeonPartyMember.kuudra("Txatic", "Routeless"))).members();
		assertEquals(2, merged.size());
		assertEquals("Routeless", merged.getFirst().kuudraRole);
		assertEquals("", merged.getFirst().dungeonClass);
		assertEquals(0, merged.getFirst().classLevel);
	}

	@Test
	void repeatedScansAreIdempotent() {
		List<DungeonPartyMember> incoming = List.of(new DungeonPartyMember("beganjava", "", "Berserk", 46), DungeonPartyMember.kuudra("KanKraft", "Routeless"));
		var first = PartyMemberReconciler.reconcile(List.of(), incoming);
		assertTrue(first.changed());
		var second = PartyMemberReconciler.reconcile(first.members(), incoming);
		assertFalse(second.changed());
		assertEquals(first.members().size(), second.members().size());
	}

	@Test
	void partyDetailsSurviveASaveAndRestart() {
		DungeonRunCompletionRecord run = new DungeonRunCompletionRecord(1_800_000_000_000L, "KUUDRA", "K1", "100%", 312_239L, "k1", "k1");
		run.partyMembers.add(DungeonPartyMember.kuudra("KrauserNoborn", "Crowd Control"));
		run.partyMembers.add(new DungeonPartyMember("catgirlVivid", "e8a20d35b48b4fa1bd924df9049ae76f", "Mage", 40));
		DungeonRunCompletionRecord reloaded = GSON.fromJson(GSON.toJson(run), DungeonRunCompletionRecord.class);
		reloaded.normalize();
		assertEquals("Crowd Control", reloaded.partyMembers.get(0).kuudraRole);
		assertEquals("Mage", reloaded.partyMembers.get(1).dungeonClass);
		assertEquals(40, reloaded.partyMembers.get(1).classLevel);
	}

	@Test
	void recordsSavedBeforeKuudraRolesExistedStillLoad() {
		String saved = "{\"floor\":\"M5\",\"runSessionId\":\"m5\",\"partyMembers\":[{\"name\":\"PekkaE342\",\"uuid\":\"\",\"dungeonClass\":\"\",\"classLevel\":0}]}";
		DungeonRunCompletionRecord loaded = GSON.fromJson(saved, DungeonRunCompletionRecord.class);
		loaded.normalize();
		assertEquals("PekkaE342", loaded.partyMembers.getFirst().name);
		assertEquals("", loaded.partyMembers.getFirst().kuudraRole);
	}
}
