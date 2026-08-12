package dev.vy.drt.tracking;

public enum EvidenceStrength {
	NONE(0),
	FALLBACK_GUESS(1),
	USER_SELECTION(2),
	RECENT_CONTEXT(3),
	GUI_TITLE_INFERENCE(4),
	CONFIRMED_TAB(5),
	STRUCTURED_CHAT(6),
	CONFIRMED_COMPLETION(7),
	CONFIRMED_SCOREBOARD(8),
	CONFIRMED_GUI_COMPONENT(9),
	AUTHORITATIVE_INTERNAL_IDENTITY(10);

	private final int rank;

	EvidenceStrength(int rank) {
		this.rank = rank;
	}

	public int rank() {
		return rank;
	}

	public boolean strongerThan(EvidenceStrength other) {
		return rank > (other == null ? NONE.rank : other.rank);
	}

	public boolean weakerThan(EvidenceStrength other) {
		return rank < (other == null ? NONE.rank : other.rank);
	}
}
