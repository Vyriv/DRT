package dev.vy.drt.luck;

/** Dungeon score bands that change chest eligibility, chest quality or RNG Meter gain. */
public enum ScoreTier {
	BELOW_A(0),
	A(230),
	S(270),
	S_PLUS(300);

	private final int minimumScore;

	ScoreTier(int minimumScore) {
		this.minimumScore = minimumScore;
	}

	public int minimumScore() {
		return minimumScore;
	}

	public static ScoreTier fromScore(int score) {
		if (score >= 300) return S_PLUS;
		if (score >= 270) return S;
		if (score >= 230) return A;
		return BELOW_A;
	}
}
