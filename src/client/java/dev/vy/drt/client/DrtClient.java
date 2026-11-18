package dev.vy.drt.client;

import dev.vy.drt.DungeonRunTracker;
import dev.vy.drt.client.cosmetics.DrtCosmetics;
import dev.vy.drt.client.render.DrtNvgSpecialRenderer;
import dev.vy.drt.client.screen.DrtNavTab;
import dev.vy.drt.client.screen.DrtOnboardingScreen;
import dev.vy.drt.client.screen.DrtScreen;
import dev.vy.drt.client.screen.DungeonTrackerPositionScreen;
import dev.vy.drt.client.sync.DrtAccount;
import dev.vy.drt.client.sync.DrtActivityPinger;
import dev.vy.drt.client.sync.DrtStatsSync;
import dev.vy.drt.client.tracker.DungeonRunTrackerFeature;
import dev.vy.drt.config.DrtConfigManager;
import dev.vy.drt.config.DungeonFloor;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
//? if >= 26.1 {
import net.fabricmc.fabric.api.client.rendering.v1.PictureInPictureRendererRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
//? } else {
/*import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
*///?}
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenMouseEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
//? if >= 26.1 {
import net.minecraft.resources.Identifier;
//?}

public final class DrtClient implements ClientModInitializer {
	private static DungeonRunTrackerFeature tracker;
	private static boolean openUnifiedScreenNextTick;
	private static DrtNavTab pendingUnifiedTab = DrtNavTab.OVERVIEW;
	/** Only the tracker's own Loot requests carry a floor and search; command shortcuts open the tab as clicked. */
	private static boolean pendingUsesTrackerLootFilters;
	private static boolean openOnboardingNextTick;
	private static boolean autoOpenedOnboardingThisSession;
	private static boolean iconCachesLoaded;

	@Override
	public void onInitializeClient() {
		registerNvgRenderer();
		DrtConfigManager.setAccountUuidSupplier(DrtAccount::currentUuid);
		tracker = new DungeonRunTrackerFeature();
		tracker.applyConfig(DrtConfigManager.getConfig());
		DrtCosmetics.initialize();
		VyaddonsUpdateChecker.initialize();

        net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents.INIT.register((handler, client) -> dev.vy.drt.client.sync.auth.DrtSessionAuth.onPlayInit(handler));
        net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> dev.vy.drt.client.sync.auth.DrtSessionAuth.onPlayReady(handler));
        net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> dev.vy.drt.client.sync.auth.DrtSessionAuth.onPlayDisconnect(handler));
		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			if (!iconCachesLoaded && client.player != null && client.level != null) {
				iconCachesLoaded = DungeonRunTrackerFeature.loadIconCachesFromConfig();
			}

			tracker.tick(client);
			DrtActivityPinger.tick(client);
			DrtStatsSync.tick(client);

			if (!autoOpenedOnboardingThisSession
				&& client.player != null
				&& client.gui.screen() == null
				&& !openUnifiedScreenNextTick
				&& !DrtConfigManager.getConfig().onboardingComplete) {
				autoOpenedOnboardingThisSession = true;
				openOnboardingNextTick = true;
			}

			boolean lootPending = tracker.consumeLootScreenPending();
			boolean lootRequested = tracker.consumeOpenLootScreenRequest();
			if (lootPending || lootRequested) {
				openUnifiedScreenNextTick = true;
				openOnboardingNextTick = false;
				pendingUnifiedTab = DrtNavTab.LOOT;
				pendingUsesTrackerLootFilters = true;
			}

			if (!openUnifiedScreenNextTick && !openOnboardingNextTick) {
				return;
			}
			if (client.player == null) {
				openUnifiedScreenNextTick = false;
				openOnboardingNextTick = false;
				return;
			}

			if (openOnboardingNextTick) {
				openOnboardingNextTick = false;
				client.gui.setScreen(new DrtOnboardingScreen(tracker));
				return;
			}

			openUnifiedScreenNextTick = false;
			boolean trackerFilters = pendingUsesTrackerLootFilters;
			pendingUsesTrackerLootFilters = false;
			DungeonFloor floor = trackerFilters ? tracker.getPendingLootScreenFloorFilter() : null;
			String search = trackerFilters ? tracker.getPendingLootScreenSearchFilter() : null;
			client.gui.setScreen(new DrtScreen(pendingUnifiedTab, floor, search));
		});

		ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) ->
			dispatcher.register(DrtCommands.drt(new DrtCommands.Handler<FabricClientCommandSource>() {
				@Override
				public boolean inGame(FabricClientCommandSource source) {
					if (source.getClient().player != null) return true;
					source.sendError(Component.literal("§c[DRT] Not in game"));
					return false;
				}

				@Override
				public void openTab(DrtNavTab tab) {
					openUnifiedScreenNextTick = true;
					openOnboardingNextTick = false;
					pendingUnifiedTab = tab;
					pendingUsesTrackerLootFilters = false;
				}

				@Override
				public boolean copyReport(String reportId) {
					return tracker.copyDiagnosticReportToClipboard(reportId);
				}

				@Override
				public boolean saveReplay(String reportId) {
					return tracker.saveDiagnosticReplay(reportId);
				}

				@Override
				public boolean exportBug(String reportId) {
					return tracker.exportDiagnosticBug(reportId);
				}

				@Override
				public boolean reprice(Integer chestNumber) {
					return tracker.repriceHistory(chestNumber);
				}

				@Override
				public boolean openFolder(String reportId) {
					return tracker.openDiagnosticBugFolder(reportId);
				}

				@Override
				public boolean copyZip(String reportId) {
					return tracker.copyDiagnosticZipToClipboard(reportId);
				}
			})));

		ClientReceiveMessageEvents.GAME.register((message, overlay) -> {
			tracker.handleGameMessage(message, overlay);
		});
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

		DungeonRunTracker.LOGGER.info("[DRT] Client initialized for {}", DungeonRunTracker.version());
	}

	private static void registerNvgRenderer() {
		try {
			//? if >=26.2 {
			PictureInPictureRendererRegistry.register(context -> new DrtNvgSpecialRenderer());
			//?} else {
			/*PictureInPictureRendererRegistry.register(context -> new DrtNvgSpecialRenderer(context.bufferSource()));
			*///?}
		} catch (RuntimeException | LinkageError exception) {
			DungeonRunTracker.LOGGER.error("Failed to register DRT GUI vector renderer", exception);
		}
	}

	/** Used by mixin to skip vanilla AbstractContainerScreen extract when Fancy Menu owns the UI. */
	public static boolean shouldSuppressVanillaContainerPresentation(Minecraft client) {
		return tracker != null && tracker.shouldSuppressVanillaContainerPresentation(client);
	}

	/** Slot borders drawn during container extract, under vanilla item tooltips. */
	/** Croesus profit panel, drawn with the container contents so it stays beside the chest GUI. */
	public static void extractChestOverlay(
		Minecraft client,
		GuiGraphicsExtractor graphics,
		int mouseX,
		int mouseY
	) {
		if (tracker != null) tracker.extractChestOverlayRenderState(client, graphics, mouseX, mouseY);
	}

	public static void extractCroesusSlotHighlights(
		Minecraft client,
		GuiGraphicsExtractor graphics,
		int mouseX,
		int mouseY
	) {
		if (tracker != null) tracker.extractCroesusSlotHighlights(client, graphics, mouseX, mouseY);
	}

	public static DungeonRunTrackerFeature getTracker() {
		return tracker;
	}
}
