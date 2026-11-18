package dev.vy.drt.history;

import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.vy.drt.config.DungeonPartyMember;
import java.util.List;
import org.junit.jupiter.api.Test;

class DungeonPartyTabParserTest {
	@Test
	void capturesOnlyGenuineClassEvidenceFromLiveDungeonTabLines() {
		var members = DungeonPartyTabParser.parse(List.of(
			"[MVP+] PlayerOne (Mage 41)",
			"PlayerTwo: Tank (37)",
			"[VIP] ArcherFriend (Archer 29)",
			"Party Members: 5",
			"NameWithoutClass",
			"Secrets Found: 42"
		));
		assertEquals(3, members.size());
		assertEquals("PlayerOne", members.get(0).name);
		assertEquals("Mage", members.get(0).dungeonClass);
		assertEquals(41, members.get(0).classLevel);
		assertEquals("Tank", members.get(1).dungeonClass);
		assertEquals(37, members.get(1).classLevel);
	}

	@Test
	void namesWithoutHistoricalClassDataRemainUnclassified() {
		assertEquals(List.of(), DungeonPartyTabParser.parse(List.of("PlayerOne", "PlayerTwo", "Players: 5")));
	}

	@Test
	void readsHypixelRomanNumeralClassLevelsWithTabDecorations() {
		var members = DungeonPartyTabParser.parse(List.of(
			"Party (5)",
			"[312] catgirlVivid (Mage XL)",
			"[M] Vyriv (Mage L)",
			"§b[289] Wzxh §6♲ §f(§eTank XLIX§f)",
			"[MVP+] BigSteinH7 ⚔ (Healer XLVII)",
			"[240] cornflakesbraix (archer xlviii)"
		));
		assertEquals(5, members.size());
		assertMember(members.get(0), "catgirlVivid", "Mage", 40);
		assertMember(members.get(1), "Vyriv", "Mage", 50);
		assertMember(members.get(2), "Wzxh", "Tank", 49);
		assertMember(members.get(3), "BigSteinH7", "Healer", 47);
		assertMember(members.get(4), "cornflakesbraix", "Archer", 48);
	}

	@Test
	void soloRunCapturesTheOnePartyMember() {
		var members = DungeonPartyTabParser.parse(List.of("Party (1)", "[312] catgirlVivid (Mage XL)", "Player Stats"));
		assertEquals(1, members.size());
		assertMember(members.getFirst(), "catgirlVivid", "Mage", 40);
	}

	@Test
	void partialSnapshotsKeepNamesWhoseClassIsNotShownYet() {
		var members = DungeonPartyTabParser.parse(List.of(
			"Party (3)",
			"[312] catgirlVivid (Mage XL)",
			"[200] Teammate_A (DEAD)",
			"[150] Teammate_B",
			"Dungeon Stats"
		));
		assertEquals(3, members.size());
		assertMember(members.get(0), "catgirlVivid", "Mage", 40);
		assertMember(members.get(1), "Teammate_A", "", 0);
		assertMember(members.get(2), "Teammate_B", "", 0);
	}

	@Test
	void partySectionStopsAtTheDeclaredCountAndAtTheNextWidget() {
		assertEquals(List.of("KanKraft", "Txatic"), DungeonPartyTabParser.partySectionNames(List.of(
			"Area: Kuudra's Hollow", "Party (2)", "[300] KanKraft", "[250] Txatic", "[100] LobbyPlayer")));
		assertEquals(List.of("KanKraft"), DungeonPartyTabParser.partySectionNames(List.of(
			"Party (4)", "[300] KanKraft", "Player Stats", "[250] Txatic")));
	}

	@Test
	void lobbyPlayersWithoutAPartyHeaderAreNeverRecorded() {
		assertEquals(List.of(), DungeonPartyTabParser.partySectionNames(List.of("[300] KanKraft", "[250] Txatic")));
		assertEquals(List.of(), DungeonPartyTabParser.parse(List.of("Players (24)", "[300] KanKraft", "[250] Txatic")));
	}

	@Test
	void romanLevelsAreValidatedStrictly() {
		assertEquals(1, DungeonPartyTabParser.level("I"));
		assertEquals(4, DungeonPartyTabParser.level("IV"));
		assertEquals(9, DungeonPartyTabParser.level("IX"));
		assertEquals(40, DungeonPartyTabParser.level("XL"));
		assertEquals(49, DungeonPartyTabParser.level("XLIX"));
		assertEquals(50, DungeonPartyTabParser.level("L"));
		assertEquals(37, DungeonPartyTabParser.level("37"));
		assertEquals(0, DungeonPartyTabParser.level("IIII"));
		assertEquals(0, DungeonPartyTabParser.level("ABC"));
		assertEquals(0, DungeonPartyTabParser.level(""));
	}

	private static void assertMember(DungeonPartyMember member, String name, String dungeonClass, int level) {
		assertEquals(name, member.name);
		assertEquals(dungeonClass, member.dungeonClass);
		assertEquals(level, member.classLevel);
		assertEquals("", member.kuudraRole);
	}
}
