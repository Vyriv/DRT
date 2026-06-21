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
