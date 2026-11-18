package dev.vy.drt.client.cosmetics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import com.mojang.authlib.GameProfile;
import java.util.List;
import java.util.UUID;
import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class RenderedTabNameTest {
	private static final GameProfile HYPIXEL_PLACEHOLDER = new GameProfile(UUID.randomUUID(), "!B-a");

	@BeforeEach
	public void registerOwner() {
		PlayerCustomizationRegistry.initialize(List.of(new CosmeticsContentManager.LoadedPlayerCustomization(
			"Vyriv", null, null, List.of(),
			new CosmeticsContentManager.LoadedNameStyle(CosmeticsContentManager.LoadedNameStyle.Mode.GRADIENT,
				0xFF0000, 0x0000FF, false, List.of(), 5.0F, 120, 1.0F),
			new CosmeticsContentManager.LoadedBadge(":3", 0xFF55FF, false),
			null, null, null)));
		NameStyler.clearCaches();
	}

	@AfterEach
	public void resetRegistry() {
		PlayerCustomizationRegistry.initialize(List.of());
		NameStyler.clearCaches();
	}

	@Test
	public void placeholderEntryContainingOwnerNameGetsSuffix() {
		Component line = Component.literal("[435] Vyriv");
		assertEquals("[435] Vyriv :3", DrtCosmetics.styleRenderedTabName(line, HYPIXEL_PLACEHOLDER).getString());
	}

	@Test
	public void ownerProfileIsLeftToTheProfilePath() {
		Component styled = DrtCosmetics.styleDisplayName(Component.literal("[435] Vyriv"), new GameProfile(UUID.randomUUID(), "Vyriv"));
		assertEquals("[435] Vyriv :3", styled.getString());
		assertSame(styled, DrtCosmetics.styleRenderedTabName(styled, new GameProfile(UUID.randomUUID(), "Vyriv")));
	}

	@Test
	public void otherPlayersAreUntouched() {
		Component line = Component.literal("[300] Someone");
		assertSame(line, DrtCosmetics.styleRenderedTabName(line, HYPIXEL_PLACEHOLDER));
	}

	@Test
	public void existingSuffixIsNotDuplicated() {
		Component line = Component.literal("[435] Vyriv :3");
		assertEquals("[435] Vyriv :3", DrtCosmetics.styleRenderedTabName(line, HYPIXEL_PLACEHOLDER).getString());
	}
}
