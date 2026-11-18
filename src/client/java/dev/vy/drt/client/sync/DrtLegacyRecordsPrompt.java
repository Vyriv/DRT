package dev.vy.drt.client.sync;

import dev.vy.drt.client.screen.DrtLegacyRecordsScreen;
import dev.vy.drt.config.DrtConfig;
import dev.vy.drt.config.DrtConfigManager;
import java.util.function.BooleanSupplier;
import net.minecraft.client.Minecraft;

/**
 * Asks once whether records saved before DRT stored accounts belong to the logged-in account.
 * Only while Statistics Sharing is on, outside runs and with no other screen open; until it is
 * answered those records stay local.
 */
public final class DrtLegacyRecordsPrompt {
	private static final long FIRST_CHECK_DELAY_MS = 30_000L;
	private static final long CHECK_INTERVAL_MS = 60_000L;

	private static long nextCheckAtMillis;
	private static boolean shownThisSession;

	private DrtLegacyRecordsPrompt() {
	}

	public static void tick(Minecraft client, BooleanSupplier runInProgress) {
		if (shownThisSession || client.player == null || client.level == null) return;
		DrtConfig config = DrtConfigManager.getConfig();
		if (!config.statsSyncEnabled) return;
		long now = System.currentTimeMillis();
		if (nextCheckAtMillis == 0L) nextCheckAtMillis = now + FIRST_CHECK_DELAY_MS;
		if (now < nextCheckAtMillis || client.gui.screen() != null || runInProgress.getAsBoolean()) return;
		nextCheckAtMillis = now + CHECK_INTERVAL_MS;
		String uuid = DrtAccount.currentUuid();
		if (uuid == null) return;
		int records = DrtStatsSync.unclaimedLegacyRecords(uuid);
		if (records == 0) return;
		shownThisSession = true;
		client.gui.setScreen(new DrtLegacyRecordsScreen(uuid, DrtAccount.currentName(), records));
	}
}
