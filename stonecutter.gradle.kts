plugins {
	id("dev.kikugie.stonecutter")
	id("net.fabricmc.fabric-loom") version "1.17-SNAPSHOT" apply false
	id("net.fabricmc.fabric-loom-remap") version "1.16-SNAPSHOT" apply false
}

stonecutter active "26.1.2"

stonecutter parameters {
	replacements {
		string(current.parsed < "26.1") {
			replace("ClientCommands", "ClientCommandManager")
			replace("GuiGraphicsExtractor", "GuiGraphics")
			replace("extractBackground", "renderBackground")
			replace("extractRenderState", "render")
			replace(".centeredText(", ".drawCenteredString(")
			replace(".text(", ".drawString(")
			replace(".item(", ".renderItem(")
			replace(
				"client.player.sendSystemMessage(Component.literal(\"§a[DRT] HUD \" + status + \". §7Loot tracking is still active.\"));",
				"client.player.displayClientMessage(Component.literal(\"§a[DRT] HUD \" + status + \". §7Loot tracking is still active.\"), false);"
			)
		}
	}
}
