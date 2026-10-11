package dev.vy.drt.luck.sim;

import dev.vy.drt.luck.ChestTier;
import dev.vy.drt.luck.engine.ChestRoll;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

/**
 * Which chests a player buys after seeing every offer. Policies are assumptions about player
 * behaviour; purchased-reward probabilities depend on them, item appearance probabilities do not.
 */
public interface SelectionPolicy {
	String name();

	/** Tiers to buy, at most {@code view.run().maxPurchases()}, in purchase order. */
	List<ChestTier> choose(OfferView view);

	enum Fallback { NOTHING, HIGHEST_TIER }

	/** Always the highest tier chests. Purchases are independent of contents, so this policy has no selection bias. */
	static SelectionPolicy highestTier() {
		return new SelectionPolicy() {
			@Override
			public String name() {
				return "HIGHEST_TIER";
			}

			@Override
			public List<ChestTier> choose(OfferView view) {
				List<ChestTier> out = new ArrayList<>();
				for (int i = view.offers().size() - 1; i >= 0 && out.size() < view.run().maxPurchases(); i--) {
					out.add(view.offers().get(i).tier());
				}
				return out;
			}
		};
	}

	/**
	 * Buys the chest holding the rarest rolled item when it is at or below {@code threshold} per-run
	 * probability; a second chest (Chest Key) only if it also clears the threshold.
	 */
	static SelectionPolicy rarestItem(double threshold, Fallback fallback) {
		return new SelectionPolicy() {
			@Override
			public String name() {
				return "RAREST_ITEM(" + threshold + "," + fallback + ")";
			}

			@Override
			public List<ChestTier> choose(OfferView view) {
				List<ChestRoll> ranked = new ArrayList<>(view.offers());
				ranked.sort(Comparator.comparingDouble(view::rarestProbability)
					.thenComparing(chest -> -chest.tier().ordinal()));
				List<ChestTier> out = new ArrayList<>();
				for (ChestRoll chest : ranked) {
					if (out.size() >= view.run().maxPurchases()) break;
					if (view.rarestProbability(chest) <= threshold) out.add(chest.tier());
				}
				return out.isEmpty() ? fallback(view, fallback) : out;
			}
		};
	}

	/** Buys chests containing any target item (highest tier first), otherwise the fallback. */
	static SelectionPolicy targetItems(Set<String> targets, Fallback fallback) {
		Set<String> copy = Set.copyOf(targets);
		return new SelectionPolicy() {
			@Override
			public String name() {
				return "TARGET_ITEMS" + copy + "(" + fallback + ")";
			}

			@Override
			public List<ChestTier> choose(OfferView view) {
				List<ChestTier> out = new ArrayList<>();
				for (int i = view.offers().size() - 1; i >= 0 && out.size() < view.run().maxPurchases(); i--) {
					ChestRoll chest = view.offers().get(i);
					if (copy.stream().anyMatch(chest::contains)) out.add(chest.tier());
				}
				return out.isEmpty() ? fallback(view, fallback) : out;
			}
		};
	}

	private static List<ChestTier> fallback(OfferView view, Fallback fallback) {
		if (fallback == Fallback.NOTHING || view.offers().isEmpty()) return List.of();
		return List.of(view.offers().get(view.offers().size() - 1).tier());
	}
}
