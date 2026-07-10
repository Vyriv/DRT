package dev.vy.drt.client;

import com.mojang.brigadier.Command;
import dev.vy.drt.DungeonRunTracker;
import dev.vy.drt.client.cosmetics.DrtCosmetics;
import dev.vy.drt.client.screen.DungeonLootScreen;
import dev.vy.drt.client.screen.DrtOnboardingScreen;
import dev.vy.drt.client.screen.DungeonTrackerPositionScreen;
import dev.vy.drt.client.tracker.DungeonRunTrackerFeature;
import dev.vy.drt.config.DrtConfigManager;
import dev.vy.drt.config.DungeonFloor;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
//? if >= 26.1 {
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
//? } else {
/*import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
*///?}
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenMouseEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
//? if >= 26.1 {
import net.minecraft.resources.Identifier;
//?}

public final class DrtClient implements ClientModInitializer {
	private static DungeonRunTrackerFeature tracker;
	private static boolean openLootScreenNextTick;
	private static boolean openMoveBoxNextTick;
	private static boolean openOnboardingNextTick;
	private static boolean autoOpenedOnboardingThisSession;
	private static boolean iconCachesLoaded;

	@Override
	public void onInitializeClient() {
		tracker = new DungeonRunTrackerFeature();
		tracker.applyConfig(DrtConfigManager.getConfig());
		DrtCosmetics.initialize();

		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			if (!iconCachesLoaded && client.player != null && client.level != null) {
				iconCachesLoaded = DungeonRunTrackerFeature.loadIconCachesFromConfig();
			}

			tracker.tick(client);

			if (!autoOpenedOnboardingThisSession
				&& client.player != null
				&& client.screen == null
				&& !DrtConfigManager.getConfig().onboardingComplete) {
				autoOpenedOnboardingThisSession = true;
				openOnboardingNextTick = true;
			}

			if (tracker.consumeLootScreenPending()) {
				openLootScreenNextTick = true;
			}
			if (tracker.consumeOpenLootScreenRequest()) {
				openLootScreenNextTick = true;
			}

			if (!openLootScreenNextTick && !openMoveBoxNextTick && !openOnboardingNextTick) return;
			if (client.player == null) {
				openLootScreenNextTick = false;
				openMoveBoxNextTick = false;
				openOnboardingNextTick = false;
				return;
			}

			if (openOnboardingNextTick) {
				openOnboardingNextTick = false;
				client.setScreen(new DrtOnboardingScreen(tracker));
				return;
			}

			if (openLootScreenNextTick) {
				openLootScreenNextTick = false;
				DungeonFloor f = tracker.getPendingLootScreenFloorFilter();
				String s = tracker.getPendingLootScreenSearchFilter();
				client.setScreen(new DungeonLootScreen(f, s));
			}

			if (openMoveBoxNextTick) {
				openMoveBoxNextTick = false;
				client.setScreen(new DungeonTrackerPositionScreen(tracker));
			}
		});

		ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
			dispatcher.register(ClientCommands.literal("drt")
				.executes(context -> {
					context.getSource().sendError(Component.literal("§c[DRT] Invalid syntax, use /drt <config/loot/move/toggle>"));
					return 0;
				})
				.then(ClientCommands.literal("move")
					.executes(context -> {
						Minecraft client = context.getSource().getClient();
						if (client.player == null) {
							context.getSource().sendError(Component.literal("§c[DRT] Not in game"));
							return 0;
						}
						openMoveBoxNextTick = true;
						return Command.SINGLE_SUCCESS;
					})
				)
				.then(ClientCommands.literal("loot")
					.executes(context -> {
						Minecraft client = context.getSource().getClient();
						if (client.player == null) {
							context.getSource().sendError(Component.literal("§c[DRT] Not in game"));
							return 0;
						}
						openLootScreenNextTick = true;
						return Command.SINGLE_SUCCESS;
					})
				)
				.then(ClientCommands.literal("setup")
					.executes(context -> {
						Minecraft client = context.getSource().getClient();
						if (client.player == null) {
							context.getSource().sendError(Component.literal("§c[DRT] Not in game"));
							return 0;
						}
						openOnboardingNextTick = true;
						return Command.SINGLE_SUCCESS;
					})
				)
				.then(ClientCommands.literal("onboarding")
					.executes(context -> {
						Minecraft client = context.getSource().getClient();
						if (client.player == null) {
							context.getSource().sendError(Component.literal("§c[DRT] Not in game"));
							return 0;
						}
						openOnboardingNextTick = true;
						return Command.SINGLE_SUCCESS;
					})
				)
				.then(ClientCommands.literal("config")
					.executes(context -> {
						Minecraft client = context.getSource().getClient();
						if (client.player == null) {
							context.getSource().sendError(Component.literal("§c[DRT] Not in game"));
							return 0;
						}
						openOnboardingNextTick = true;
						return Command.SINGLE_SUCCESS;
					})
				)
				.then(ClientCommands.literal("toggle")
					.executes(context -> {
						Minecraft client = context.getSource().getClient();
						if (client.player == null) {
							context.getSource().sendError(Component.literal("§c[DRT] Not in game"));
							return 0;
						}
						boolean hudShown = tracker.toggleHud();
						String status = hudShown ? "shown" : "hidden";
						client.player.sendSystemMessage(Component.literal("§a[DRT] HUD " + status + ". §7Loot tracking is still active."));
						return Command.SINGLE_SUCCESS;
					})
				)
			);
		});

		ClientReceiveMessageEvents.GAME.register((message, overlay) -> tracker.handleGameMessage(message, overlay));
		ClientReceiveMessageEvents.ALLOW_CHAT.register((message, signedMessage, sender, params, receptionTimestamp) -> {
			tracker.handleChatMessage(message);
			return true;
		});

		//? if >= 26.1 {
		HudElementRegistry.attachElementAfter(VanillaHudElements.HOTBAR, Identifier.fromNamespaceAndPath(DungeonRunTracker.MOD_ID, "tracker"), (guiGraphics, deltaTracker) -> {
		//? } else {
		/*HudRenderCallback.EVENT.register((guiGraphics, deltaTracker) -> {
		*///?}
			Minecraft client = Minecraft.getInstance();
			double gsx = (double) client.getWindow().getGuiScaledWidth() / Math.max(1, client.getWindow().getWidth());
			double gsy = (double) client.getWindow().getGuiScaledHeight() / Math.max(1, client.getWindow().getHeight());
			int mx = (int) Math.round(client.mouseHandler.xpos() * gsx);
			int my = (int) Math.round(client.mouseHandler.ypos() * gsy);
			tracker.extractRenderState(client, guiGraphics, mx, my);
		});

		ScreenEvents.AFTER_INIT.register((client, screen, scaledWidth, scaledHeight) -> {
			if (screen instanceof AbstractContainerScreen<?>) {
				//? if >= 26.1 {
				ScreenEvents.afterExtract(screen).register((s, graphics, mouseX, mouseY, tickProgress) -> {
					if (tracker != null) tracker.extractChestOverlayRenderState(client, graphics, mouseX, mouseY);
				});
				//? } else {
				/*ScreenEvents.afterRender(screen).register((s, graphics, mouseX, mouseY, tickProgress) -> {
					if (tracker != null) tracker.extractChestOverlayRenderState(client, graphics, mouseX, mouseY);
				});
				*///?}
			}
			ScreenMouseEvents.allowMouseClick(screen).register(new ScreenMouseEvents.AllowMouseClick() {
				@Override
				public boolean allowMouseClick(Screen s, MouseButtonEvent event) {
					if (s instanceof DungeonTrackerPositionScreen) {
						return true;
					}
					if (tracker != null && tracker.handleScreenMouseClick(client, event.x(), event.y(), event.button())) {
						return false;
					}
					return true;
				}
			});
		});

		DungeonRunTracker.LOGGER.info("[DRT] Client initialized");
	}

	public static DungeonRunTrackerFeature getTracker() {
		return tracker;
	}
}
