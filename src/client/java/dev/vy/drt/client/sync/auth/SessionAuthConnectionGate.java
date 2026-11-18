package dev.vy.drt.client.sync.auth;

import java.util.concurrent.TimeUnit;
import java.util.function.LongSupplier;

/** Lifecycle state only. No caller may perform network I/O while holding this monitor. */
final class SessionAuthConnectionGate {
	private static final long SETTLE_NANOS = TimeUnit.SECONDS.toNanos(5L);
	private final LongSupplier clock;
	private long generation;
	private Object playListener;
	private boolean ready;
	private long readyAt;
	private boolean proofInProgress;

	SessionAuthConnectionGate(LongSupplier clock) {
		this.clock = clock;
	}

	synchronized boolean connecting() {
		generation++;
		playListener = null;
		ready = false;
		return proofInProgress;
	}

	synchronized void expectPlay(Object listener) {
		connecting();
		playListener = listener;
	}

	synchronized void playReady(Object listener) {
		if (listener != null && listener == playListener) {
			ready = true;
			readyAt = clock.getAsLong() + SETTLE_NANOS;
		}
	}

	synchronized void disconnected(Object listener) {
		if (listener == playListener) connecting();
	}

	synchronized long permit(Object listener) {
		return listener != null && listener == playListener && ready && clock.getAsLong() - readyAt >= 0L
			? generation : -1L;
	}

	synchronized boolean beginProof(long expectedGeneration) {
		if (expectedGeneration < 0L || generation != expectedGeneration || permit(playListener) < 0L || proofInProgress) return false;
		proofInProgress = true;
		return true;
	}

	synchronized boolean finishProof(long expectedGeneration) {
		proofInProgress = false;
		return generation == expectedGeneration && ready;
	}
}
