package dev.vy.drt.client.screen;

import dev.vy.drt.config.DungeonLootEntry;
import dev.vy.drt.config.DungeonRunCompletionRecord;
import dev.vy.drt.config.DungeonRunRecord;
import dev.vy.drt.history.RunHistoryLinks;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.function.ToLongFunction;

final class LootChestExport {
	private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("d MMM yyyy, HH:mm", Locale.ROOT);

	private LootChestExport() {}

	static String shortSummary(DungeonRunRecord record) {
		return shortSummary(record, null);
	}

	static String shortSummary(DungeonRunRecord record, DungeonRunCompletionRecord completion) {
		if (record == null) return "";
		StringBuilder out = new StringBuilder(header(record, completion));
		String grade = grade(record, completion);
		String duration = duration(completion);
		if (!grade.isBlank()) out.append(" | ").append(grade);
		if (!duration.isBlank()) out.append(" | ").append(duration);
		if (record.hasKnownProfit()) {
			out.append(" | Cost: ").append(cost(record))
				.append(" | Value: ").append(coins(record.chestValueCoins))
				.append(" | Profit: ").append(signed(record.chestProfitCoins))
				.append(roi(record));
		}
		return out.toString();
	}

	static String detailed(DungeonRunRecord record, long completionTime, ZoneId zone,
		ToLongFunction<DungeonLootEntry> itemValue) {
		return detailed(record, null, completionTime, zone, itemValue);
	}

	static String detailed(DungeonRunRecord record, DungeonRunCompletionRecord completion, ZoneId zone,
		ToLongFunction<DungeonLootEntry> itemValue) {
		return detailed(record, completion, completion == null ? 0L : completion.completedAtEpochMillis, zone, itemValue);
	}

	private static String detailed(DungeonRunRecord record, DungeonRunCompletionRecord completion, long completionTime,
		ZoneId zone, ToLongFunction<DungeonLootEntry> itemValue) {
		if (record == null) return "";
		StringBuilder out = new StringBuilder(header(record, completion));
		String grade = grade(record, completion);
		String duration = duration(completion);
		if (!grade.isBlank() || !duration.isBlank()) {
			out.append(" (");
			if (!grade.isBlank()) out.append(grade);
			if (!grade.isBlank() && !duration.isBlank()) out.append(" / ");
			if (!duration.isBlank()) out.append(duration);
			out.append(')');
		}
		long timestamp = completionTime > 0L ? completionTime : record.timestampEpochMillis;
		if (timestamp > 0L) out.append('\n').append(DATE.withZone(zone == null ? ZoneId.systemDefault() : zone).format(Instant.ofEpochMilli(timestamp)));
		if (record.hasKnownProfit()) {
			out.append("\n\nCost: ").append(cost(record))
				.append("\nValue: ").append(coins(record.chestValueCoins))
				.append("\nProfit: ").append(signed(record.chestProfitCoins))
				.append(roi(record));
		}
		if (record.lootEntries != null && !record.lootEntries.isEmpty()) {
			boolean heading = false;
			for (DungeonLootEntry item : record.lootEntries) {
				if (item == null) continue;
				String name = item.rawName == null || item.rawName.isBlank() ? item.itemId : item.rawName;
				if (name == null || name.isBlank()) continue;
				if (!heading) {
					out.append("\n\nItems:");
					heading = true;
				}
				out.append("\n- ").append(name).append(" x").append(Math.max(1, item.quantity));
				long value = itemValue == null ? -1L : itemValue.applyAsLong(item);
				if (value >= 0L) out.append(": ").append(coins(value));
			}
		}
		return out.toString();
	}

	private static String header(DungeonRunRecord record, DungeonRunCompletionRecord completion) {
		String completionFloor = completion == null ? "" : clean(completion.floor);
		String floor = !completionFloor.isBlank() && !completionFloor.equalsIgnoreCase("UNKNOWN")
			? completionFloor : record.floor == null || record.floor.isBlank() ? "Unknown" : record.floor;
		String chest = record.chestTitle == null || record.chestTitle.isBlank() ? "Unknown Chest" : record.chestTitle.trim();
		return "[DRT] " + floor + " | " + chest;
	}

	private static String grade(DungeonRunRecord record, DungeonRunCompletionRecord completion) {
		String grade = completion == null ? "" : clean(completion.grade);
		if (grade.isBlank() || grade.equals("?") || grade.equalsIgnoreCase("UNKNOWN")) grade = clean(record.grade);
		return grade.equals("?") || grade.equalsIgnoreCase("UNKNOWN") ? "" : grade;
	}

	private static String duration(DungeonRunCompletionRecord completion) {
		RunHistoryLinks.RunTime runTime = RunHistoryLinks.runTime(completion);
		return runTime == null ? "" : RunHistoryLinks.formatRunTime(runTime.millis()).replace(" ", "");
	}

	private static String roi(DungeonRunRecord record) {
		if (record == null || record.totalCostCoins() <= 0L) return "";
		double roi = record.chestProfitCoins * 100D / record.totalCostCoins();
		return " (" + String.format(Locale.ROOT, "%+.1f%%", roi) + ")";
	}

	private static String cost(DungeonRunRecord record) {
		return record.totalCostCoins() == 0L ? "Free" : coins(record.totalCostCoins());
	}

	private static String clean(String value) {
		return value == null ? "" : value.trim();
	}

	private static String signed(long value) {
		return (value >= 0L ? "+" : "-") + coins(Math.abs(value));
	}

	private static String coins(long value) {
		if (value >= 1_000_000_000L) return decimal(value / 1_000_000_000D, 2) + "B";
		if (value >= 1_000_000L) return decimal(value / 1_000_000D, 2) + "M";
		if (value >= 1_000L) return decimal(value / 1_000D, 1) + "k";
		return Long.toString(value);
	}

	private static String decimal(double value, int places) {
		String text = String.format(Locale.ROOT, "%." + places + "f", value);
		while (text.contains(".") && (text.endsWith("0") || text.endsWith("."))) {
			text = text.substring(0, text.length() - 1);
		}
		return text;
	}
}
