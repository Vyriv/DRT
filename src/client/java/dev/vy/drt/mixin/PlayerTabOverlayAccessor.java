package dev.vy.drt.mixin;

import java.util.List;
import net.minecraft.client.gui.components.PlayerTabOverlay;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(PlayerTabOverlay.class)
public interface PlayerTabOverlayAccessor {
	@Invoker("getPlayerInfos")
	List<PlayerInfo> drt$getPlayerInfos();

	@Invoker("getNameForDisplay")
	Component drt$getNameForDisplay(PlayerInfo playerInfo);

	@Accessor("header")
	Component drt$getHeader();

	@Accessor("footer")
	Component drt$getFooter();
}
