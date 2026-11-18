package dev.vy.drt.client.sync;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

final class DrtChat {
	private DrtChat() {
	}

	static void send(Minecraft client, Component message) {
		if (client == null || client.player == null || message == null) return;
		//? if >= 26.1 {
		client.player.sendSystemMessage(message);
		//? } else {
		/*client.player.displayClientMessage(message, false);
		*///?}
	}

	static void send(Minecraft client, String message) {
		send(client, Component.literal(message));
	}
}
