package dev.vy.drt.luck.meter;

/** How far observed meter readings settle one disputed meter rule. */
public enum MeterRuleStatus {
	/** Exactly one variant reproduces every reading. */
	CONFIRMED,
	/** Several variants reproduce every reading; the default is a choice, not a finding. */
	PROVISIONAL,
	/** No variant reproduces every reading: a reading is wrong (an uncaptured run, a misread score) or the model is. */
	CONFLICT;

	static MeterRuleStatus of(int consistentVariants) {
		return consistentVariants == 0 ? CONFLICT : consistentVariants == 1 ? CONFIRMED : PROVISIONAL;
	}
}
