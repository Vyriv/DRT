package dev.vy.drt.client.sync;

import java.util.Locale;
import java.util.UUID;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;

/** The logged-in Minecraft account and installed versions, as reported to api.vyriv.dev. */
public final class DrtAccount {
	private DrtAccount() {
	}

	/** Undashed lowercase UUID of the session account, or null when no session is available. */
	public static String currentUuid() {
		try {
			Minecraft client = Minecraft.getInstance();
			if (client == null || client.getUser() == null) return null;
			UUID id = client.getUser().getProfileId();
			return id == null ? null : undashed(id);
		} catch (RuntimeException e) {
			return null;
		}
	}

	public static String currentName() {
		try {
			Minecraft client = Minecraft.getInstance();
			return client == null || client.getUser() == null ? "" : client.getUser().getName();
		} catch (RuntimeException e) {
			return "";
		}
	}

	public static String undashed(UUID id) {
		return id.toString().replace("-", "").toLowerCase(Locale.ROOT);
	}

	public static String modVersion() {
		return version("drt");
	}

	public static String minecraftVersion() {
		return version("minecraft");
	}

	private static String version(String modId) {
		return FabricLoader.getInstance().getModContainer(modId)
			.map(mod -> mod.getMetadata().getVersion().getFriendlyString())
			.orElse("");
	}
}
