package dev.vy.drt.luck.rules;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.ToDoubleFunction;

/**
 * Evaluates a metric under alternative rule variants. A result is only interpretable when every
 * plausible variant stays within the caller's materiality tolerance of the baseline.
 */
public final class SensitivityAnalysis {
	private SensitivityAnalysis() {
	}

	public record VariantValue(RuleVariants rules, double value) {
	}

	public record Result(double baseline, List<VariantValue> alternatives, double min, double max) {
		public double maxDeviation() {
			return Math.max(Math.abs(max - baseline), Math.abs(baseline - min));
		}

		public boolean isMaterial(double tolerance) {
			return maxDeviation() > tolerance;
		}
	}

	public static Result evaluate(RuleVariants baseline, List<RuleVariants> alternatives, ToDoubleFunction<RuleVariants> metric) {
		double base = metric.applyAsDouble(baseline);
		double min = base;
		double max = base;
		List<VariantValue> values = new ArrayList<>(alternatives.size());
		for (RuleVariants variant : alternatives) {
			double value = metric.applyAsDouble(variant);
			values.add(new VariantValue(variant, value));
			min = Math.min(min, value);
			max = Math.max(max, value);
		}
		return new Result(base, Collections.unmodifiableList(values), min, max);
	}

	public static Result oneAtATime(RuleVariants baseline, ToDoubleFunction<RuleVariants> metric) {
		return evaluate(baseline, baseline.oneAtATimeAlternatives(), metric);
	}
}
