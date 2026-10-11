package dev.vy.drt.luck.stats;

/**
 * Mid-p percentile of an observed statistic against Monte Carlo null samples:
 * (below + ties / 2) / samples. Counting ties as half keeps the percentile centred at 0.5 under
 * the null even for discrete statistics, where many histories share the same value.
 */
public final class MidPPercentile {
	private MidPPercentile() {
	}

	/**
	 * @param monteCarloStandardError binomial standard error of the percentile from finite sampling
	 */
	public record Result(double percentile, int below, int ties, int above, int samples, double monteCarloStandardError) {
		/** True when the percentile is pinned to an end of the sample and only a bound is known. */
		public boolean atSampleLimit() {
			return below + ties == 0 || above + ties == 0;
		}
	}

	public static Result compute(double observed, double[] nullSamples, double tieTolerance) {
		if (nullSamples.length == 0) throw new IllegalArgumentException("No null samples");
		int below = 0;
		int ties = 0;
		int above = 0;
		for (double sample : nullSamples) {
			if (Math.abs(sample - observed) <= tieTolerance) ties++;
			else if (sample < observed) below++;
			else above++;
		}
		int n = nullSamples.length;
		double percentile = (below + 0.5 * ties) / n;
		double standardError = Math.sqrt(Math.max(percentile * (1 - percentile), 0.25 / n) / n);
		return new Result(percentile, below, ties, above, n, standardError);
	}
}
