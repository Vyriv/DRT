package dev.vy.drt.client;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import dev.vy.drt.DungeonRunTracker;
import dev.vy.drt.client.cosmetics.DrtCosmetics;
import dev.vy.drt.client.screen.DungeonLootScreen;
import dev.vy.drt.client.screen.DrtOnboardingScreen;
import dev.vy.drt.client.screen.DungeonTrackerPositionScreen;
import dev.vy.drt.client.tracker.DungeonRunTrackerFeature;
import dev.vy.drt.config.DrtConfig;
import dev.vy.drt.config.DrtConfigManager;
import dev.vy.drt.config.DungeonFloor;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
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
					context.getSource().sendError(Component.literal("§c[DRT] Invalid syntax, use /drt <config/loot/move/toggle/debug>"));
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
						sendToggleUsage(client);
						return Command.SINGLE_SUCCESS;
					})
					.then(ClientCommands.argument("param", StringArgumentType.word())
						.suggests((ctx, builder) -> suggestToggleParams(builder))
						.executes(context -> {
							Minecraft client = context.getSource().getClient();
							if (client.player == null) {
								context.getSource().sendError(Component.literal("§c[DRT] Not in game"));
								return 0;
							}
							String param = StringArgumentType.getString(context, "param");
							if (!applyToggleParam(client, param)) {
								context.getSource().sendError(Component.literal("§c[DRT] Unknown toggle '" + param + "'. Use /drt toggle <UI|tracking|CroOverlay|FancyMenu|essence>"));
								return 0;
							}
							return Command.SINGLE_SUCCESS;
						})
					)
				)
				.then(ClientCommands.literal("debug")
					.then(ClientCommands.literal("triggerincident")
						.executes(context -> {
							Minecraft client = context.getSource().getClient();
							if (client.player == null) {
								context.getSource().sendError(Component.literal("§c[DRT] Not in game"));
								return 0;
							}
							return tracker.triggerSyntheticDiagnosticIncident("") ? Command.SINGLE_SUCCESS : 0;
						})
						.then(ClientCommands.literal("duplicate")
							.executes(context -> {
								Minecraft client = context.getSource().getClient();
								if (client.player == null) {
									context.getSource().sendError(Component.literal("§c[DRT] Not in game"));
									return 0;
								}
								return tracker.triggerSyntheticDiagnosticIncident("duplicate") ? Command.SINGLE_SUCCESS : 0;
							})
						)
						.then(ClientCommands.literal("new")
							.executes(context -> {
								Minecraft client = context.getSource().getClient();
								if (client.player == null) {
									context.getSource().sendError(Component.literal("§c[DRT] Not in game"));
									return 0;
								}
								return tracker.triggerSyntheticDiagnosticIncident("new") ? Command.SINGLE_SUCCESS : 0;
							})
						)
					)
					.then(ClientCommands.literal("copyreport")
						.then(ClientCommands.argument("reportId", StringArgumentType.word())
							.executes(context -> {
								Minecraft client = context.getSource().getClient();
								if (client.player == null) {
									context.getSource().sendError(Component.literal("§c[DRT] Not in game"));
									return 0;
								}
								String reportId = StringArgumentType.getString(context, "reportId");
								return tracker.copyDiagnosticReportToClipboard(reportId) ? Command.SINGLE_SUCCESS : 0;
							})
						)
					)
					.then(ClientCommands.literal("savereplay")
						.then(ClientCommands.argument("reportId", StringArgumentType.word())
							.executes(context -> {
								Minecraft client = context.getSource().getClient();
								if (client.player == null) {
									context.getSource().sendError(Component.literal("§c[DRT] Not in game"));
									return 0;
								}
								String reportId = StringArgumentType.getString(context, "reportId");
								return tracker.saveDiagnosticReplay(reportId) ? Command.SINGLE_SUCCESS : 0;
							})
						)
					)
					.then(ClientCommands.literal("exportbug")
						.then(ClientCommands.argument("reportId", StringArgumentType.word())
							.executes(context -> {
								Minecraft client = context.getSource().getClient();
								if (client.player == null) {
									context.getSource().sendError(Component.literal("§c[DRT] Not in game"));
									return 0;
								}
								String reportId = StringArgumentType.getString(context, "reportId");
								return tracker.exportDiagnosticBug(reportId) ? Command.SINGLE_SUCCESS : 0;
							})
						)
					)
					.then(ClientCommands.literal("copyzip")
						.then(ClientCommands.argument("reportId", StringArgumentType.word())
							.executes(context -> {
								Minecraft client = context.getSource().getClient();
								if (client.player == null) {
									context.getSource().sendError(Component.literal("§c[DRT] Not in game"));
									return 0;
								}
								String reportId = StringArgumentType.getString(context, "reportId");
								return tracker.copyDiagnosticZipToClipboard(reportId) ? Command.SINGLE_SUCCESS : 0;
							})
						)
					)
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
			ScreenMouseEvents.allowMouseDrag(screen).register((s, event, horizontalAmount, verticalAmount) -> {
				if (tracker != null && tracker.shouldBlockContainerMouseInput(client)) {
					return false;
				}
				return true;
			});
			ScreenMouseEvents.allowMouseScroll(screen).register((s, mouseX, mouseY, horizontalAmount, verticalAmount) -> {
				if (tracker != null && tracker.shouldBlockContainerMouseInput(client)) {
					return false;
				}
				return true;
			});
			ScreenMouseEvents.allowMouseRelease(screen).register((s, event) -> {
				if (tracker != null && tracker.shouldBlockContainerMouseInput(client)) {
					return false;
				}
				return true;
			});
		});

		DungeonRunTracker.LOGGER.info("[DRT] Client initialized");
	}

	/** Used by mixin to skip vanilla AbstractContainerScreen extract when Fancy Menu owns the UI. */
	public static boolean shouldSuppressVanillaContainerPresentation(Minecraft client) {
		return tracker != null && tracker.shouldSuppressVanillaContainerPresentation(client);
	}

	private static CompletableFuture<Suggestions> suggestToggleParams(SuggestionsBuilder builder) {
		String remaining = builder.getRemaining().toLowerCase(Locale.ROOT);
		for (String option : new String[] {"UI", "tracking", "CroOverlay", "FancyMenu", "essence"}) {
			if (option.toLowerCase(Locale.ROOT).startsWith(remaining)) {
				builder.suggest(option);
			}
		}
		return builder.buildFuture();
	}

	private static void sendToggleUsage(Minecraft client) {
		DrtConfig config = DrtConfigManager.getConfig();
		sendSystemChat(client, Component.literal("§a[DRT] Usage: /drt toggle <UI|tracking|CroOverlay|FancyMenu|essence>"));
		sendSystemChat(client, Component.literal(
			"§7 UI: " + onOff(tracker.isEnabled())
				+ "  tracking: " + onOff(tracker.isTrackingEnabled())
				+ "  CroOverlay: " + onOff(tracker.isCroesusOverlayEnabled())
				+ "  FancyMenu: " + onOff(tracker.isFancyMenuEnabled())
				+ "  essence: " + onOff(config.essenceCountsTowardProfit)
		));
	}

	private static boolean applyToggleParam(Minecraft client, String param) {
		String key = param == null ? "" : param.trim().toLowerCase(Locale.ROOT);
		return switch (key) {
			case "ui", "hud" -> {
				boolean on = tracker.toggleHud();
				sendToggleState(client, "UI", on);
				yield true;
			}
			case "tracking", "track", "tracker" -> {
				boolean on = tracker.toggleTracking();
				sendToggleState(client, "tracking", on);
				yield true;
			}
			case "crooverlay", "croesus", "cro", "croesusoverlay" -> {
				boolean on = tracker.toggleCroesusOverlay();
				sendToggleState(client, "CroOverlay", on);
				yield true;
			}
			case "fancymenu", "fancy", "fancymenuenabled" -> {
				boolean on = tracker.toggleFancyMenu();
				sendToggleState(client, "FancyMenu", on);
				yield true;
			}
			case "essence", "ess" -> {
				boolean on = tracker.toggleEssenceCountsTowardProfit();
				sendToggleState(client, "essence", on);
				yield true;
			}
			default -> false;
		};
	}

	private static void sendToggleState(Minecraft client, String name, boolean on) {
		sendSystemChat(client, Component.literal("§a[DRT] " + name + ": " + onOff(on)));
	}

	private static void sendSystemChat(Minecraft client, Component message) {
		if (client == null || client.player == null || message == null) return;
		//? if >= 26.1 {
		client.player.sendSystemMessage(message);
		//? } else {
		/*client.player.displayClientMessage(message, false);
		*///?}
	}

	private static String onOff(boolean on) {
		return on ? "ON" : "OFF";
	}

	public static DungeonRunTrackerFeature getTracker() {
		return tracker;
	}
}
