package dev.vy.drt.client.luck;

import dev.vy.drt.DungeonRunTracker;
import dev.vy.drt.luck.capture.CroesusMenuParser;
import dev.vy.drt.luck.capture.CroesusRunMatcher;
import dev.vy.drt.luck.capture.CroesusRunViewParser;
import dev.vy.drt.luck.capture.LuckCaptureCoordinator;
import dev.vy.drt.luck.capture.LuckProbeRecorder;
import dev.vy.drt.luck.capture.LuckRunRecord;
import dev.vy.drt.luck.capture.LuckTrackingStore;
import dev.vy.drt.luck.capture.RngMeterParser;
import dev.vy.drt.luck.capture.StableScreenGate;
import dev.vy.drt.luck.data.CatacombsLootDatabase;
import dev.vy.drt.mixin.AbstractContainerScreenAccessor;
import java.util.List;
import java.util.Objects;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.Slot;

/** Feeds stable Croesus and RNG Meter screens, and Croesus entry clicks, into the luck store. */
public final class LuckScreenCapture {
	private static final StableScreenGate GATE = new StableScreenGate();
	private static LuckCaptureCoordinator coordinator;
	private static CatacombsLootDatabase database;
	private static boolean databaseFailed;

	private LuckScreenCapture() {
	}

	public static void tick(Minecraft client) {
		if (client.player == null || !(client.screen instanceof AbstractContainerScreen<?> screen)) {
			GATE.reset();
			return;
		}
		String title = LuckProbeClient.clean(screen.getTitle().getString());
		if (!isCaptured(title)) {
			GATE.reset();
			return;
		}
		try {
			LuckProbeRecorder.ScreenSample sample = GATE.observe(screen, LuckProbeClient.sample(client, screen));
			if (sample != null) handle(sample, System.currentTimeMillis());
		} catch (RuntimeException e) {
			DungeonRunTracker.LOGGER.warn("[DRT] Luck screen capture failed on {}", title, e);
		}
	}

	/** Called on every container click before other handlers; remembers which Croesus entry was clicked. */
	public static void onMouseClick(Minecraft client, Screen screen) {
		if (client.player == null || !(screen instanceof AbstractContainerScreen<?> container)) return;
		if (!CroesusMenuParser.isMenuTitle(LuckProbeClient.clean(container.getTitle().getString()))) return;
		try {
			Slot slot = ((AbstractContainerScreenAccessor) container).drt$getHoveredSlot();
			if (slot == null || slot.container == client.player.getInventory()) return;
			coordinator().onCroesusClick(slot.index, System.currentTimeMillis());
		} catch (RuntimeException e) {
			DungeonRunTracker.LOGGER.warn("[DRT] Luck capture could not read the clicked Croesus entry", e);
		}
	}

	private static boolean isCaptured(String title) {
		return CroesusMenuParser.isMenuTitle(title)
			|| CroesusRunViewParser.isRunViewTitle(title)
			|| RngMeterParser.isOverviewTitle(title)
			|| RngMeterParser.floorMenuFloor(title) != null;
	}

	private static void handle(LuckProbeRecorder.ScreenSample sample, long now) {
		String title = sample.title();
		LuckCaptureCoordinator capture = coordinator();
		if (CroesusMenuParser.isMenuTitle(title)) {
			CroesusRunMatcher.Result result = capture.onCroesusMenu(sample, now);
			if (result != null) {
				long matched = result.bySlot().values().stream().filter(CroesusRunMatcher.EntryMatch::matched).count();
				DungeonRunTracker.LOGGER.info("[DRT] Luck Croesus page: {} entries, {} linked to captured runs", result.bySlot().size(), matched);
			}
		} else if (CroesusRunViewParser.isRunViewTitle(title)) {
			LuckCaptureCoordinator.ViewOutcome outcome = capture.onCroesusRunView(sample, now);
			if (outcome != null && outcome.luckRunId() != null) {
				DungeonRunTracker.LOGGER.info("[DRT] Luck chest offers {} for {} ({} chests)", outcome.offerResult(), outcome.luckRunId(), outcome.chests());
			} else if (outcome != null) {
				DungeonRunTracker.LOGGER.info("[DRT] Luck chest offers kept unmatched: {} ({} chests)", outcome.unmatchedReason(), outcome.chests());
			}
		} else if (RngMeterParser.isOverviewTitle(title)) {
			logAttachments(capture.onMeterOverview(sample, now));
		} else if (RngMeterParser.floorMenuFloor(title) != null) {
			logAttachments(capture.onMeterFloorMenu(sample, now));
		}
	}

	private static void logAttachments(List<LuckTrackingStore.MeterAttachment> attachments) {
		for (LuckTrackingStore.MeterAttachment attachment : attachments) {
			LuckRunRecord.LuckMeterSnapshot before = attachment.before();
			LuckRunRecord.LuckMeterSnapshot after = attachment.after();
			String gain = before != null && before.storedScore != null && after.storedScore != null
				&& Objects.equals(before.selectedItem, after.selectedItem)
				? String.valueOf(after.storedScore - before.storedScore)
				: "unknown";
			DungeonRunTracker.LOGGER.info("[DRT] Luck meter {} after run: stored {} (before {}), gain {}",
				attachment.floor(), after.storedText, before == null ? "unread" : before.storedText, gain);
		}
	}

	private static synchronized LuckCaptureCoordinator coordinator() {
		if (coordinator == null) {
			coordinator = new LuckCaptureCoordinator(LuckRunCapture.store(), floor -> {
				CatacombsLootDatabase loaded = database();
				return loaded == null ? null : loaded.floor(floor);
			});
		}
		return coordinator;
	}

	private static CatacombsLootDatabase database() {
		if (database == null && !databaseFailed) {
			try {
				database = CatacombsLootDatabase.loadBundled();
			} catch (RuntimeException e) {
				databaseFailed = true;
				DungeonRunTracker.LOGGER.warn("[DRT] Luck loot data could not be loaded; item names stay unresolved", e);
			}
		}
		return database;
	}
}
