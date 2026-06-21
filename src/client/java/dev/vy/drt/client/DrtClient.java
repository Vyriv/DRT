package dev.vy.drt.client;

import com.mojang.brigadier.Command;
import dev.vy.drt.DungeonRunTracker;
import dev.vy.drt.client.screen.DungeonLootScreen;
import dev.vy.drt.client.screen.DungeonTrackerPositionScreen;
import dev.vy.drt.client.tracker.DungeonRunTrackerFeature;
import dev.vy.drt.config.DrtConfigManager;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenMouseEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

public final class DrtClient implements ClientModInitializer {
	private static DungeonRunTrackerFeature tracker;
	private static boolean openLootScreenNextTick;
	private static boolean openMoveBoxNextTick;

	@Override
	public void onInitializeClient() {
		tracker = new DungeonRunTrackerFeature();
		tracker.applyConfig(DrtConfigManager.getConfig());
		DungeonRunTrackerFeature.loadIconCachesFromConfig();

		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			tracker.tick(client);

			if (tracker.consumeLootScreenPending()) {
				openLootScreenNextTick = true;
			}

			if (!openLootScreenNextTick && !openMoveBoxNextTick) return;
			if (client.player == null) {
				openLootScreenNextTick = false;
				openMoveBoxNextTick = false;
				return;
			}

			if (openLootScreenNextTick) {
				openLootScreenNextTick = false;
				client.setScreen(new DungeonLootScreen());
			}

			if (openMoveBoxNextTick) {
				openMoveBoxNextTick = false;
				client.setScreen(new DungeonTrackerPositionScreen(tracker));
			}
		});

		ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
			dispatcher.register(ClientCommandManager.literal("drt")
				.then(ClientCommandManager.literal("move")
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
				.then(ClientCommandManager.literal("loot")
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
			);
		});

		ClientReceiveMessageEvents.GAME.register((message, overlay) -> tracker.handleGameMessage(message, overlay));
		ClientReceiveMessageEvents.ALLOW_CHAT.register((message, signedMessage, sender, params, receptionTimestamp) -> {
			tracker.handleChatMessage(message);
			return true;
		});

		HudRenderCallback.EVENT.register((guiGraphics, deltaTracker) -> {
			Minecraft client = Minecraft.getInstance();
			double gsx = (double) client.getWindow().getGuiScaledWidth() / Math.max(1, client.getWindow().getWidth());
			double gsy = (double) client.getWindow().getGuiScaledHeight() / Math.max(1, client.getWindow().getHeight());
			int mx = (int) Math.round(client.mouseHandler.xpos() * gsx);
			int my = (int) Math.round(client.mouseHandler.ypos() * gsy);
			tracker.render(client, guiGraphics, mx, my);
		});

		ScreenEvents.AFTER_INIT.register((client, screen, scaledWidth, scaledHeight) -> {
			ScreenMouseEvents.allowMouseClick(screen).register(new ScreenMouseEvents.AllowMouseClick() {
				@Override
				public boolean allowMouseClick(Screen s, MouseButtonEvent event) {
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
