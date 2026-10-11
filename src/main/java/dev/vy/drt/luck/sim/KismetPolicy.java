package dev.vy.drt.luck.sim;

import dev.vy.drt.luck.ChestTier;
import dev.vy.drt.luck.engine.ChestRoll;

/** Whether and which chest to reroll with a Kismet Feather, decided after seeing the offers. */
public interface KismetPolicy {
	String name();

	/** Tier to reroll, or null for no reroll. */
	ChestTier chooseReroll(OfferView view);

	static KismetPolicy never() {
		return new KismetPolicy() {
			@Override
			public String name() {
				return "NEVER";
			}

			@Override
			public ChestTier chooseReroll(OfferView view) {
				return null;
			}
		};
	}

	/** Rerolls the highest tier chest when no offered chest holds an item at or below {@code threshold}. */
	static KismetPolicy rerollTopUnlessRare(double threshold) {
		return new KismetPolicy() {
			@Override
			public String name() {
				return "REROLL_TOP_UNLESS_RARE(" + threshold + ")";
			}

			@Override
			public ChestTier chooseReroll(OfferView view) {
				if (view.offers().isEmpty()) return null;
				for (ChestRoll chest : view.offers()) {
					if (view.rarestProbability(chest) <= threshold) return null;
				}
				return view.offers().get(view.offers().size() - 1).tier();
			}
		};
	}
}
