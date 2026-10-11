package dev.vy.drt.luck.capture;

/**
 * Lets a screen through once its contents have stayed identical for
 * {@link LuckProbeRecorder#STABLE_TICKS} ticks, and again whenever they change and settle within
 * the same screen. Hypixel fills menus over several ticks, so earlier snapshots are incomplete.
 */
public final class StableScreenGate {
	private Object screenKey;
	private String pendingFingerprint;
	private int pendingStableTicks;
	private String releasedFingerprint;

	/** @return the sample when it should be handled on this tick, otherwise null */
	public LuckProbeRecorder.ScreenSample observe(Object key, LuckProbeRecorder.ScreenSample sample) {
		if (key != screenKey) {
			screenKey = key;
			pendingFingerprint = null;
			pendingStableTicks = 0;
			releasedFingerprint = null;
		}
		String fingerprint = sample.fingerprint();
		if (fingerprint.equals(pendingFingerprint)) {
			pendingStableTicks++;
		} else {
			pendingFingerprint = fingerprint;
			pendingStableTicks = 1;
		}
		if (pendingStableTicks < LuckProbeRecorder.STABLE_TICKS || fingerprint.equals(releasedFingerprint)) return null;
		releasedFingerprint = fingerprint;
		return sample;
	}

	public void reset() {
		screenKey = null;
		pendingFingerprint = null;
		pendingStableTicks = 0;
		releasedFingerprint = null;
	}
}
