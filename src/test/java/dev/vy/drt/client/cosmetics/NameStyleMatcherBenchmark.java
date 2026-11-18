package dev.vy.drt.client.cosmetics;

import com.sun.management.ThreadMXBean;
import java.lang.management.ManagementFactory;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public final class NameStyleMatcherBenchmark {
	private static final int WARMUP_ITERATIONS = 8;
	private static final int MEASURED_ITERATIONS = 15;
	private static volatile Object blackhole;

	private NameStyleMatcherBenchmark() {
	}

	public static void main(String[] args) {
		List<PlayerCustomizationRegistry.NameCandidate> candidates = candidates(96);
		NameStyleMatcher.Index index = NameStyleMatcher.compile(candidates);
		benchmark("no-match-200k", "meow".repeat(50_000), candidates, index);
		benchmark("match-near-end-200k", "meow".repeat(50_000) + " CosmeticUser095", candidates, index);
	}

	private static void benchmark(String label, String text, List<PlayerCustomizationRegistry.NameCandidate> candidates,
	                              NameStyleMatcher.Index index) {
		for (int iteration = 0; iteration < WARMUP_ITERATIONS; iteration++) {
			blackhole = oldFindFirstNameMatch(text, candidates, 0);
			blackhole = NameStyleMatcher.findFirstNameMatch(text, index, 0);
		}

		Measurement oldMeasurement = measure(() -> oldFindFirstNameMatch(text, candidates, 0));
		Measurement newMeasurement = measure(() -> NameStyleMatcher.findFirstNameMatch(text, index, 0));
		double speedup = (double) oldMeasurement.medianNanos / (double) newMeasurement.medianNanos;
		System.out.printf("%s old median %.3f ms, indexed median %.3f ms, speedup %.2fx, old allocation %d B/op, indexed allocation %d B/op%n",
			label,
			oldMeasurement.medianNanos / 1_000_000.0D,
			newMeasurement.medianNanos / 1_000_000.0D,
			speedup,
			oldMeasurement.allocatedBytesPerOperation,
			newMeasurement.allocatedBytesPerOperation);
	}

	private static Measurement measure(Operation operation) {
		long[] times = new long[MEASURED_ITERATIONS];
		ThreadMXBean allocationBean = allocationBean();
		long threadId = Thread.currentThread().threadId();
		long allocationBefore = allocationBean == null ? -1L : allocationBean.getThreadAllocatedBytes(threadId);
		for (int iteration = 0; iteration < MEASURED_ITERATIONS; iteration++) {
			long start = System.nanoTime();
			blackhole = operation.run();
			times[iteration] = System.nanoTime() - start;
		}
		long allocationAfter = allocationBean == null ? -1L : allocationBean.getThreadAllocatedBytes(threadId);
		Arrays.sort(times);
		long allocated = allocationBefore < 0L ? -1L : (allocationAfter - allocationBefore) / MEASURED_ITERATIONS;
		return new Measurement(times[times.length / 2], allocated);
	}

	private static ThreadMXBean allocationBean() {
		if (!(ManagementFactory.getThreadMXBean() instanceof ThreadMXBean bean)) return null;
		if (!bean.isThreadAllocatedMemorySupported()) return null;
		if (!bean.isThreadAllocatedMemoryEnabled()) bean.setThreadAllocatedMemoryEnabled(true);
		return bean;
	}

	private static NameStyleMatcher.MatchedCustomization oldFindFirstNameMatch(String text,
	                                                                            List<PlayerCustomizationRegistry.NameCandidate> candidates,
	                                                                            int startIndex) {
		int bestIndex = Integer.MAX_VALUE;
		PlayerCustomizationRegistry.NameCandidate bestCandidate = null;
		for (PlayerCustomizationRegistry.NameCandidate candidate : candidates) {
			int searchIndex = startIndex;
			while (searchIndex < text.length()) {
				int candidateIndex = oldIndexOfIgnoreCase(text, candidate.value(), searchIndex);
				if (candidateIndex == -1 || candidateIndex > bestIndex) break;
				if (!candidate.requiresBoundary() || boundary(text, candidateIndex, candidateIndex + candidate.value().length())) {
					if (candidateIndex < bestIndex || (candidateIndex == bestIndex
						&& (bestCandidate == null || candidate.value().length() > bestCandidate.value().length()))) {
						bestIndex = candidateIndex;
						bestCandidate = candidate;
					}
					break;
				}
				searchIndex = candidateIndex + 1;
			}
		}
		return bestCandidate == null ? null
			: new NameStyleMatcher.MatchedCustomization(bestIndex, bestCandidate.value(), bestCandidate.customization());
	}

	private static int oldIndexOfIgnoreCase(String text, String target, int fromIndex) {
		int maximum = text.length() - target.length();
		for (int index = Math.max(0, fromIndex); index <= maximum; index++) {
			if (text.regionMatches(true, index, target, 0, target.length())) return index;
		}
		return -1;
	}

	private static boolean boundary(String text, int start, int end) {
		return boundaryCharacter(start > 0 ? text.charAt(start - 1) : null)
			&& boundaryCharacter(end < text.length() ? text.charAt(end) : null);
	}

	private static boolean boundaryCharacter(Character character) {
		return character == null || (!Character.isLetterOrDigit(character) && character != '_');
	}

	private static List<PlayerCustomizationRegistry.NameCandidate> candidates(int count) {
		List<PlayerCustomizationRegistry.NameCandidate> candidates = new ArrayList<>();
		for (int index = 0; index < count; index++) {
			String name = "CosmeticUser%03d".formatted(index);
			candidates.add(CosmeticsTestFixtures.candidate(name, false));
		}
		return List.copyOf(candidates);
	}

	@FunctionalInterface
	private interface Operation {
		Object run();
	}

	private record Measurement(long medianNanos, long allocatedBytesPerOperation) {
	}
}
