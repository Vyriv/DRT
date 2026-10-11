package dev.vy.drt.luck.sim;

import dev.vy.drt.luck.data.FloorTable;
import dev.vy.drt.luck.data.LootEntry;
import dev.vy.drt.luck.engine.ChestRoll;
import java.util.List;

/** What a player sees before choosing: every offered chest plus the run's rarity reference. */
public record OfferView(FloorTable floor, RunContext run, List<ChestRoll> offers, RunRarity rarity) {
	public OfferView {
		offers = List.copyOf(offers);
	}

	/** Lowest per-run appearance probability among the chest's rolled items, or 1 when it has none. */
	public double rarestProbability(ChestRoll chest) {
		double rarest = 1.0;
		for (LootEntry entry : chest.rolledLuckItems()) rarest = Math.min(rarest, rarity.perRun(entry.key()));
		return rarest;
	}
}
