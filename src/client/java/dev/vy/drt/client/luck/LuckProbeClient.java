package dev.vy.drt.client.luck;

import dev.vy.drt.DungeonRunTracker;
import dev.vy.drt.luck.capture.LuckProbeRecorder;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemLore;

public final class LuckProbeClient {
	private static final LuckProbeRecorder RECORDER = new LuckProbeRecorder(LuckProbeClient::append, Instant::now);
	private static boolean enabled;
	private static boolean writeFailureLogged;

	private LuckProbeClient() {
	}

	public static boolean isEnabled() {
		return enabled;
	}

	public static void setEnabled(boolean on) {
		if (enabled == on) return;
		enabled = on;
		RECORDER.screenClosed();
		RECORDER.writeMarker(on ? "probe enabled" : "probe disabled");
	}

	public static Path outputFile() {
		return FabricLoader.getInstance().getConfigDir()
			.resolve("drt")
			.resolve("luck-probe")
			.resolve("luck-probe-" + LocalDate.now() + ".jsonl");
	}

	public static void tick(Minecraft client) {
		if (!enabled) return;
		if (client.player == null || !(client.screen instanceof AbstractContainerScreen<?> screen)) {
			RECORDER.screenClosed();
			return;
		}
		String title = clean(screen.getTitle().getString());
		if (!LuckProbeRecorder.isRelevantScreenTitle(title)) {
			RECORDER.screenClosed();
			return;
		}
		List<LuckProbeRecorder.SlotSample> slots = new ArrayList<>();
		int containerSlots = 0;
		for (Slot slot : screen.getMenu().slots) {
			if (slot.container == client.player.getInventory()) continue;
			containerSlots++;
			ItemStack stack = slot.getItem();
			if (stack == null || stack.isEmpty()) continue;
			slots.add(new LuckProbeRecorder.SlotSample(
				slot.index,
				BuiltInRegistries.ITEM.getKey(stack.getItem()).toString(),
				stack.getCount(),
				clean(stack.getHoverName().getString()),
				loreLines(stack)
			));
		}
		RECORDER.observeScreen(screen, new LuckProbeRecorder.ScreenSample(title, containerSlots, slots));
	}

	public static void onMessage(Component message, boolean overlay) {
		if (!enabled || message == null) return;
		String raw = message.getString();
		RECORDER.observeChat(raw, clean(raw), overlay);
	}

	private static List<String> loreLines(ItemStack stack) {
		ItemLore lore = stack.get(DataComponents.LORE);
		if (lore == null) return List.of();
		List<String> lines = new ArrayList<>();
		lore.styledLines().forEach(line -> lines.add(clean(line.getString())));
		return lines;
	}

	private static String clean(String text) {
		if (text == null) return "";
		String stripped = ChatFormatting.stripFormatting(text);
		return stripped == null ? "" : stripped.trim();
	}

	private static void append(String jsonLine) {
		Path file = outputFile();
		try {
			Files.createDirectories(file.getParent());
			Files.writeString(file, jsonLine + System.lineSeparator(), StandardCharsets.UTF_8,
				StandardOpenOption.CREATE, StandardOpenOption.APPEND);
		} catch (IOException e) {
			if (!writeFailureLogged) {
				writeFailureLogged = true;
				DungeonRunTracker.LOGGER.warn("[DRT] Luck probe could not write {}", file, e);
			}
		}
	}
}
