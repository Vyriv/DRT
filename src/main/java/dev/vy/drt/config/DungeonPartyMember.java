package dev.vy.drt.config;

/** Historical party identity and class information observed for one completed run. */
public final class DungeonPartyMember {
	public String name = "";
	public String uuid = "";
	public String dungeonClass = "";
	public int classLevel;
	/** Kuudra participant role from Croesus (for example "Crowd Control"); never a Dungeon class. */
	public String kuudraRole = "";

	public DungeonPartyMember() {
	}

	public DungeonPartyMember(String name, String uuid, String dungeonClass, int classLevel) {
		this(name, uuid, dungeonClass, classLevel, "");
	}

	public DungeonPartyMember(String name, String uuid, String dungeonClass, int classLevel, String kuudraRole) {
		this.name = text(name);
		this.uuid = normalizeUuid(uuid);
		this.dungeonClass = text(dungeonClass);
		this.classLevel = Math.max(0, classLevel);
		this.kuudraRole = text(kuudraRole);
	}

	public static DungeonPartyMember kuudra(String name, String role) {
		return new DungeonPartyMember(name, "", "", 0, role);
	}

	public DungeonPartyMember copy() {
		return new DungeonPartyMember(name, uuid, dungeonClass, classLevel, kuudraRole);
	}

	public void normalize() {
		name = text(name);
		uuid = normalizeUuid(uuid);
		dungeonClass = text(dungeonClass);
		classLevel = Math.max(0, classLevel);
		kuudraRole = text(kuudraRole);
	}

	private static String normalizeUuid(String value) {
		return text(value).replace("-", "").toLowerCase(java.util.Locale.ROOT);
	}

	private static String text(String value) {
		return value == null ? "" : value.trim();
	}
}
