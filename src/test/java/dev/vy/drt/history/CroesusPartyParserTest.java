package dev.vy.drt.history;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import dev.vy.drt.config.DungeonPartyMember;
import java.util.List;
import org.junit.jupiter.api.Test;

class CroesusPartyParserTest {
	@Test
	void parsesObservedCroesusPlayerLoreAndTreatsNumberAsClassLevel() {
		var members = CroesusPartyParser.parse(List.of(
			"Floor V", "", "Players:", "Teammate_A: Tank (37)", "LocalPlayer: Mage (41)",
			"", "Completed: 15 secs ago"
		));
		assertEquals(2, members.size());
		assertEquals("Tank", members.getFirst().dungeonClass);
		assertEquals(37, members.getFirst().classLevel);
		assertEquals("Mage", members.get(1).dungeonClass);
		assertEquals(41, members.get(1).classLevel);
	}

	@Test
	void parsesActualSinglePlayerTooltipFormat() {
		var members = CroesusPartyParser.parse(List.of("Players:", "catgirlVivid: Mage (41)"));
		assertEquals(1, members.size());
		assertEquals("catgirlVivid", members.getFirst().name);
		assertEquals("Mage", members.getFirst().dungeonClass);
		assertEquals(41, members.getFirst().classLevel);
	}

	@Test
	void parsesAFullMasterModeRosterWithEveryClass() {
		var members = CroesusPartyParser.parse(List.of(
			"Master Mode Catacombs - Floor VII", "Players:",
			"beganjava: Berserk (46)", "Wzxh: Tank (50)", "CakeKnight: Mage (49)",
			"BigSteinH7: Healer (47)", "cornflakesbraix: Archer (48)", "Score: 302 (S+)", "Completed: 4 minutes ago"
		));
		assertEquals(5, members.size());
		assertMember(members.get(0), "beganjava", "Berserk", 46, "");
		assertMember(members.get(1), "Wzxh", "Tank", 50, "");
		assertMember(members.get(2), "CakeKnight", "Mage", 49, "");
		assertMember(members.get(3), "BigSteinH7", "Healer", 47, "");
		assertMember(members.get(4), "cornflakesbraix", "Archer", 48, "");
	}

	@Test
	void formattingCodesAndSpacingDoNotLoseMembers() {
		var members = CroesusPartyParser.parse(List.of(
			"§7Players:", "§bbeganjava§f:  §eBerserk §b(§e46§b)", "  §aWzxh§f : §eTank§b ( §e50§b )"));
		assertEquals(2, members.size());
		assertMember(members.get(0), "beganjava", "Berserk", 46, "");
		assertMember(members.get(1), "Wzxh", "Tank", 50, "");
	}

	@Test
	void kuudraRolesStaySeparateFromDungeonClasses() {
		var members = CroesusPartyParser.parse(List.of(
			"Kuudra - Basic Tier", "Players:",
			"KanKraft: Routeless", "KrauserNoborn: Crowd Control", "Txatic: Routeless", "catgirlVivid: Specialist",
			"Completed: 2 minutes ago", "Chests: Free Chest"
		));
		assertEquals(4, members.size());
		assertMember(members.get(0), "KanKraft", "", 0, "Routeless");
		assertMember(members.get(1), "KrauserNoborn", "", 0, "Crowd Control");
		assertMember(members.get(2), "Txatic", "", 0, "Routeless");
		assertMember(members.get(3), "catgirlVivid", "", 0, "Specialist");
	}

	@Test
	void loreKeysAfterThePlayersAreNeverTakenAsPeople() {
		var members = CroesusPartyParser.parse(List.of("Players:", "Chests: Free Chest", "KanKraft: Routeless"));
		assertEquals(List.of(), members, "a non-member line ends the Players section");
		assertEquals(List.of(), CroesusPartyParser.parse(List.of("KanKraft: Routeless")), "roles need a Players header");
	}

	@Test
	void realHypixelParticipantDisplayNamesKeepClassAndLevel() {
		assertMember(CroesusPartyParser.participant("§bcatgirllivid§f: §eMage§b (§e39§b)"), "catgirllivid", "Mage", 39, "");
		assertMember(CroesusPartyParser.participant("§asurume3600§f: §eBerserk§b (§e38§b)"), "surume3600", "Berserk", 38, "");
		assertMember(CroesusPartyParser.participant("§6__kaylaa§f: §eTank§b (§e0§b)"), "__kaylaa", "Tank", 0, "");
		assertNull(CroesusPartyParser.participant("§b[MVP§c+§b] Player"));
	}

	private static void assertMember(DungeonPartyMember member, String name, String dungeonClass, int level, String role) {
		assertEquals(name, member.name);
		assertEquals(dungeonClass, member.dungeonClass);
		assertEquals(level, member.classLevel);
		assertEquals(role, member.kuudraRole);
	}
}
