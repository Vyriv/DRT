package dev.vy.drt.client.cosmetics;

import com.mojang.blaze3d.platform.NativeImage;
import dev.vy.drt.DungeonRunTracker;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.core.ClientAsset;
import net.minecraft.resources.Identifier;

final class CapeTextureManager {
	private static final int MAX_CAPE_BYTES = 2 * 1024 * 1024;
	private static final HttpClient HTTP = HttpClient.newBuilder()
		.connectTimeout(Duration.ofSeconds(5))
		.build();
	private static final Map<String, CapeTexture> REMOTE_CAPES = new ConcurrentHashMap<>();
	private static final Map<String, ClientAsset.Texture> RESOURCE_CAPES = new ConcurrentHashMap<>();

	private CapeTextureManager() {
	}

	static ClientAsset.Texture getCapeTexture(String capeResourcePath, String capeUrl) {
		if (capeResourcePath != null && !capeResourcePath.isBlank()) {
			ClientAsset.Texture resourceCape = RESOURCE_CAPES.computeIfAbsent(capeResourcePath, CapeTextureManager::resourceTexture);
			if (resourceCape != null) return resourceCape;
		}

		if (capeUrl == null || capeUrl.isBlank()) return null;
		CapeTexture state = REMOTE_CAPES.computeIfAbsent(capeUrl, CapeTexture::new);
		ClientAsset.Texture loaded = state.texture;
		if (loaded != null) return loaded;

		if (state.requested.compareAndSet(false, true)) {
			CompletableFuture
				.supplyAsync(() -> downloadCapeImage(capeUrl))
				.thenAccept(image -> registerCapeTexture(state, image))
				.exceptionally(error -> {
					DungeonRunTracker.LOGGER.warn("[DRT] Failed to load custom cape '{}': {}", capeUrl, error.getMessage());
					return null;
				});
		}
		return null;
	}

	static void invalidateRemoteCapes() {
		REMOTE_CAPES.clear();
	}

	private static ClientAsset.Texture resourceTexture(String path) {
		Identifier id = Identifier.tryParse(path);
		return id == null ? null : new ClientAsset.ResourceTexture(id, id);
	}

	private static NativeImage downloadCapeImage(String capeUrl) {
		try {
			URI uri = URI.create(capeUrl);
			if (!"https".equalsIgnoreCase(uri.getScheme())) {
				throw new IOException("cape URL must use https");
			}
			HttpRequest request = HttpRequest.newBuilder()
				.uri(uri)
				.timeout(Duration.ofSeconds(15))
				.header("accept", "image/png,image/*;q=0.8,*/*;q=0.1")
				.GET()
				.build();
			HttpResponse<byte[]> response = HTTP.send(request, HttpResponse.BodyHandlers.ofByteArray());
			if (response.statusCode() != 200) {
				throw new IOException("unexpected HTTP " + response.statusCode());
			}
			byte[] body = response.body();
			if (body == null || body.length == 0) {
				throw new IOException("empty cape response");
			}
			if (body.length > MAX_CAPE_BYTES) {
				throw new IOException("cape image exceeds " + MAX_CAPE_BYTES + " bytes");
			}
			return NativeImage.read(body);
		} catch (Exception e) {
			throw new IllegalStateException(e);
		}
	}

	private static void registerCapeTexture(CapeTexture state, NativeImage image) {
		Minecraft client = Minecraft.getInstance();
		if (client == null) {
			image.close();
			return;
		}

		client.execute(() -> {
			try {
				client.getTextureManager().register(state.textureId, new DynamicTexture(() -> "DRT custom cape", image));
				state.texture = new ClientAsset.ResourceTexture(state.textureId, state.textureId);
				DungeonRunTracker.LOGGER.info("[DRT] Loaded custom cape texture {}", state.textureId);
			} catch (Exception e) {
				image.close();
				DungeonRunTracker.LOGGER.warn("[DRT] Failed to register custom cape texture {}: {}", state.textureId, e.getMessage());
			}
		});
	}

	private static final class CapeTexture {
		final Identifier textureId;
		final AtomicBoolean requested = new AtomicBoolean();
		volatile ClientAsset.Texture texture;

		CapeTexture(String url) {
			this.textureId = Identifier.fromNamespaceAndPath(DungeonRunTracker.MOD_ID, "dynamic_capes/" + UUID
				.nameUUIDFromBytes(url.getBytes(StandardCharsets.UTF_8))
				.toString()
				.replace("-", ""));
		}
	}
}
