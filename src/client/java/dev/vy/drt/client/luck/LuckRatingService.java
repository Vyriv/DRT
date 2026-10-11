package dev.vy.drt.client.luck;

import dev.vy.drt.DungeonRunTracker;
import dev.vy.drt.luck.capture.LuckHistoryEvaluation;
import dev.vy.drt.luck.capture.LuckMeterGains;
import dev.vy.drt.luck.capture.LuckRunRecord;
import dev.vy.drt.luck.capture.LuckTrackingStore;
import dev.vy.drt.luck.data.CatacombsLootDatabase;
import dev.vy.drt.luck.meter.MeterGainObservation;
import dev.vy.drt.luck.meter.MeterRuleEvidence;
import dev.vy.drt.luck.meter.SRunMeterGainEvidence;
import dev.vy.drt.luck.rules.RuleEvidence;
import dev.vy.drt.luck.rules.RuleVariants;
import dev.vy.drt.luck.sim.SelectionPolicy;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.ForkJoinWorkerThread;

/**
 * Evaluates the stored luck history off the render thread. Results are cached against the store's
 * revision, so opening the screen again only recomputes after a run, an offer or a meter reading
 * was captured.
 */
public final class LuckRatingService {
	public static final int SAMPLES = 1000;
	private static final long SEED = 0x4c75636b52617465L;
	private static final ForkJoinPool POOL = new ForkJoinPool(
		Math.max(1, Runtime.getRuntime().availableProcessors() / 2),
		pool -> {
			ForkJoinWorkerThread thread = ForkJoinPool.defaultForkJoinWorkerThreadFactory.newThread(pool);
			thread.setName("DRT Luck Rating " + thread.getPoolIndex());
			thread.setDaemon(true);
			thread.setPriority(Thread.MIN_PRIORITY);
			return thread;
		},
		null,
		false
	);

	public enum State {
		IDLE,
		CALCULATING,
		READY,
		FAILED
	}

	/**
	 * @param evaluation the latest finished result, kept while a newer one calculates
	 * @param revision   store revision the evaluation was made from, -1 before the first
	 */
	public record Snapshot(State state, LuckHistoryEvaluation evaluation, long revision, long finishedAtMillis, long elapsedMillis, String error) {
		public boolean stale(long current) {
			return revision != current;
		}
	}

	private static Snapshot latest = new Snapshot(State.IDLE, null, -1L, 0L, 0L, null);
	private static boolean running;
	private static boolean rerunRequested;

	private LuckRatingService() {
	}

	public static synchronized Snapshot current() {
		return latest;
	}

	/** Starts an evaluation when the cached one is older than the store, or when forced. */
	public static synchronized void refresh(boolean force) {
		long revision = LuckRunCapture.store().revision();
		if (running) {
			if (force || latest.revision() != revision) rerunRequested = true;
			return;
		}
		if (!force && latest.state() != State.IDLE && latest.revision() == revision) return;
		start(revision);
	}

	private static void start(long revision) {
		running = true;
		rerunRequested = false;
		latest = new Snapshot(State.CALCULATING, latest.evaluation(), latest.revision(), latest.finishedAtMillis(), latest.elapsedMillis(), null);
		List<LuckRunRecord> runs = LuckRunCapture.store().runSnapshots();
		POOL.execute(() -> run(runs, revision));
	}

	private static void run(List<LuckRunRecord> runs, long revision) {
		long started = System.currentTimeMillis();
		LuckHistoryEvaluation evaluation = null;
		String error = null;
		try {
			CatacombsLootDatabase database = LuckScreenCapture.database();
			if (database == null) {
				error = "Loot data could not be loaded";
			} else {
				evaluation = LuckHistoryEvaluation.evaluate(runs, database, RuleVariants.defaults(), evidence(runs),
					SelectionPolicy.highestTier(), SAMPLES, SEED);
			}
		} catch (RuntimeException e) {
			DungeonRunTracker.LOGGER.warn("[DRT] Luck Rating evaluation failed", e);
			error = e.getClass().getSimpleName() + (e.getMessage() == null ? "" : ": " + e.getMessage());
		}
		long finished = System.currentTimeMillis();
		finish(evaluation, error, revision, finished, finished - started);
	}

	private static synchronized void finish(LuckHistoryEvaluation evaluation, String error, long revision, long finished, long elapsed) {
		running = false;
		latest = error == null
			? new Snapshot(State.READY, evaluation, revision, finished, elapsed, null)
			: new Snapshot(State.FAILED, latest.evaluation(), revision, finished, elapsed, error);
		if (rerunRequested || LuckRunCapture.store().revision() != revision) start(LuckRunCapture.store().revision());
	}

	/** Meter rules still plausible after the recorded readings and every captured gain. */
	private static RuleEvidence evidence(List<LuckRunRecord> runs) {
		List<MeterGainObservation> all = new ArrayList<>(SRunMeterGainEvidence.RECORDED);
		all.addAll(LuckMeterGains.all(runs));
		return MeterRuleEvidence.from(all, RuleVariants.defaults());
	}
}
