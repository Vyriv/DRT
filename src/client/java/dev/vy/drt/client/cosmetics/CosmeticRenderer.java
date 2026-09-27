package dev.vy.drt.client.cosmetics;

import dev.vy.drt.DungeonRunTracker;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Locale;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.message.v1.ClientSendMessageEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

/**
 * Decides which of BetterPV, DRT and Skylist may style cosmetic names, so only one of them
 * ever transforms a given name. The three mods keep identical copies of this logic and share
 * state through a JVM system property plus one config file, with no compile-time dependency.
 */
public final class CosmeticRenderer {
	private static final String PROPERTY = "vyriv.cosmeticRenderer";
	private static final String FILE_NAME = "vyriv-cosmetic-renderer.txt";
	private static final String COMMAND = "xyztogglecosmetics";
	private static final String SELF = "drt";
	private static final String[] AUTO_PRIORITY = {"betterpv", "drt", "skylist"};

	private static volatile boolean active = true;
	private static String seenMode;

	private CosmeticRenderer() {
	}

	public static void initialize() {
		refresh();
		ClientTickEvents.END_CLIENT_TICK.register(client -> refresh());
		// Not a Brigadier command on purpose: it stays out of help and autocomplete, and the
		// first mod to see it cancels the send so the others never handle it twice.
		ClientSendMessageEvents.ALLOW_COMMAND.register(CosmeticRenderer::handleCommand);
	}

	public static boolean active() {
		return active;
	}

	// Another mod may flip the shared property, so this runs every tick as well as on change.
	private static void refresh() {
		String mode = currentMode();
		if (mode.equals(seenMode)) return;
		boolean firstRun = seenMode == null;
		seenMode = mode;
		active = SELF.equals(owner(mode));
		if (!firstRun) NameStyler.clearCaches();
		DungeonRunTracker.LOGGER.info("[DRT] Cosmetic renderer: {} ({})", describe(mode), active ? "rendering here" : "skipped here");
	}

	private static boolean handleCommand(String command) {
		String trimmed = command.trim();
		String lower = trimmed.toLowerCase(Locale.ROOT);
		if (!lower.equals(COMMAND) && !lower.startsWith(COMMAND + " ")) return true;

		String mode = normalize(trimmed.substring(COMMAND.length()));
		if (mode == null) {
			chat("Cosmetic renderer: " + describe(currentMode()));
			chat("Usage: /" + COMMAND + " <BetterPV|DRT|SkyList|Auto|Off>");
			return false;
		}
		System.setProperty(PROPERTY, mode);
		save(mode);
		refresh();
		chat("Cosmetic renderer: " + describe(mode));
		return false;
	}

	private static String currentMode() {
		String mode = normalize(System.getProperty(PROPERTY));
		if (mode == null) {
			mode = readSaved();
			System.setProperty(PROPERTY, mode);
		}
		return mode;
	}

	private static String owner(String mode) {
		return switch (mode) {
			case "auto" -> {
				for (String id : AUTO_PRIORITY) {
					if (FabricLoader.getInstance().isModLoaded(id)) yield id;
				}
				yield null;
			}
			case "off" -> null;
			default -> mode;
		};
	}

	private static String describe(String mode) {
		if (mode.equals("auto")) {
			String owner = owner(mode);
			return "Auto (" + (owner == null ? "none" : label(owner)) + ")";
		}
		if (!mode.equals("off") && !FabricLoader.getInstance().isModLoaded(mode)) {
			return label(mode) + " (not installed)";
		}
		return label(mode);
	}

	private static String label(String mode) {
		return switch (mode) {
			case "betterpv" -> "BetterPV";
			case "drt" -> "DRT";
			case "skylist" -> "SkyList";
			case "off" -> "Off";
			default -> "Auto";
		};
	}

	private static String normalize(String value) {
		if (value == null) return null;
		return switch (value.trim().toLowerCase(Locale.ROOT)) {
			case "auto" -> "auto";
			case "betterpv", "pv" -> "betterpv";
			case "drt" -> "drt";
			case "skylist" -> "skylist";
			case "off", "none" -> "off";
			default -> null;
		};
	}

	private static Path file() {
		return FabricLoader.getInstance().getConfigDir().resolve(FILE_NAME);
	}

	private static String readSaved() {
		try {
			Path path = file();
			if (Files.isRegularFile(path)) {
				String mode = normalize(Files.readString(path, StandardCharsets.UTF_8));
				if (mode != null) return mode;
			}
		} catch (IOException | RuntimeException e) {
			DungeonRunTracker.LOGGER.warn("[DRT] Could not read {}: {}", FILE_NAME, e.toString());
		}
		return "auto";
	}

	private static void save(String mode) {
		Path path = file();
		Path tmp = path.resolveSibling(FILE_NAME + ".tmp");
		try {
			Files.writeString(tmp, mode, StandardCharsets.UTF_8);
			Files.move(tmp, path, StandardCopyOption.REPLACE_EXISTING);
		} catch (IOException | RuntimeException e) {
			DungeonRunTracker.LOGGER.warn("[DRT] Could not save {}: {}", FILE_NAME, e.toString());
		}
	}

	private static void chat(String text) {
		Minecraft client = Minecraft.getInstance();
		if (client.player == null) return;
		//? if >= 26.1 {
		client.player.sendSystemMessage(Component.literal(text));
		//? } else {
		/*client.player.displayClientMessage(Component.literal(text), false);
		*///?}
	}
}
