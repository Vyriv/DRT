package dev.vy.drt.client.tracker;

import dev.vy.drt.mixin.PlayerTabOverlayAccessor;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.PlayerTabOverlay;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;

public final class TabReader {
	private TabReader() {
	}

	public record FactionReputation(String faction, int reputation) {
	}

	public static List<String> readRawLines(Minecraft client) {
		if (client == null || client.gui == null) return List.of();
		ClientPacketListener connection = client.getConnection();
		if (connection == null) return List.of();

		PlayerTabOverlay overlay = client.gui.getTabList();
		PlayerTabOverlayAccessor accessor = (PlayerTabOverlayAccessor) overlay;
		List<String> lines = new ArrayList<>();
		appendComponentLines(lines, accessor.drt$getHeader());
		for (PlayerInfo playerInfo : accessor.drt$getPlayerInfos()) {
			Component display = accessor.drt$getNameForDisplay(playerInfo);
			appendComponentLines(lines, display);
		}
		appendComponentLines(lines, accessor.drt$getFooter());
		return List.copyOf(lines);
	}

	public static List<String> readNormalizedLines(Minecraft client) {
		List<String> rawLines = readRawLines(client);
		if (rawLines.isEmpty()) return List.of();
		List<String> normalizedLines = new ArrayList<>(rawLines.size());
		for (String rawLine : rawLines) {
			String normalized = normalize(rawLine);
			if (!normalized.isEmpty()) normalizedLines.add(normalized);
		}
		return List.copyOf(normalizedLines);
	}

	public static boolean isMineshaftTab(List<String> normalizedLines) {
		for (String line : normalizedLines) {
			if (line.contains("AREA: MINESHAFT")
				|| line.contains("TIME IN MINESHAFT")
				|| line.contains("FROZEN CORPSES:")
				|| line.contains("GLACITE MINESHAFTS")) {
				return true;
			}
		}
		return false;
	}

	/**
	 * Reads Crimson Isle tab widget lines like:
	 * {@code Mage Reputation:} / {@code 470} / progress bar / {@code Neutral} {@code Friendly}.
	 */
	public static FactionReputation parseFactionReputation(List<String> normalizedLines) {
		if (normalizedLines == null || normalizedLines.isEmpty()) return null;
		for (int i = 0; i < normalizedLines.size(); i++) {
			String line = normalizedLines.get(i);
			if (line == null || line.isBlank()) continue;
			boolean mage = line.contains("MAGE REPUTATION");
			boolean barbarian = line.contains("BARBARIAN REPUTATION");
			if (!mage && !barbarian) continue;
			String faction = mage ? "MAGE" : "BARBARIAN";
			Integer inline = extractReputationNumber(line);
			if (inline != null) return new FactionReputation(faction, inline);
			for (int j = i + 1; j < Math.min(i + 5, normalizedLines.size()); j++) {
				String next = normalizedLines.get(j);
				if (next == null || next.isBlank()) continue;
				if (next.contains("%")) continue;
				if (next.contains("NEUTRAL") || next.contains("FRIENDLY")
					|| next.contains("TRUSTED") || next.contains("HONORED") || next.contains("HERO")
					|| next.contains("HOSTILE") || next.contains("UNFRIENDLY")
					|| next.contains("REPUTATION")) {
					break;
				}
				Integer value = extractReputationNumber(next);
				if (value != null) return new FactionReputation(faction, value);
				break;
			}
		}
		return null;
	}

	private static Integer extractReputationNumber(String normalizedLine) {
		if (normalizedLine == null || normalizedLine.isBlank()) return null;
		// Prefer an explicit trailing value: "MAGE REPUTATION: 12,000" or a bare "470".
		int colon = normalizedLine.lastIndexOf(':');
		String candidate = colon >= 0 ? normalizedLine.substring(colon + 1).trim() : normalizedLine.trim();
		if (candidate.isEmpty()) return null;
		// Reject progress labels / mixed text unless it is purely a number (optional commas).
		if (!candidate.matches("[0-9][0-9,]*")) return null;
		try {
			return Integer.parseInt(candidate.replace(",", ""));
		} catch (NumberFormatException ignored) {
			return null;
		}
	}

	public static String normalize(String value) {
		String stripped = ChatFormatting.stripFormatting(value);
		return stripped == null ? "" : stripped.trim().toUpperCase(Locale.ROOT);
	}

	private static void appendComponentLines(List<String> lines, Component component) {
		if (component == null) return;
		String text = component.getString();
		if (text == null || text.isBlank()) return;
		String[] splitLines = text.split("\\R");
		for (String line : splitLines) {
			if (!line.isBlank()) lines.add(line);
		}
	}
}
