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
			replace(".centeredText(", ".drawCenteredString(")
			replace(".text(", ".drawString(")
			replace(".item(", ".renderItem(")
		}

		// Word-bounded regex, not plain string(): "render" as a bare string()
		// match corrupts "renderer"/"rendering" packages (they contain "render"
		// as a substring) on every version where the direction is false and
		// generation actually runs — i.e. any non-vcsVersion version other than
		// 1.21.11. Harmless with only 2 versions (26.1.2 skipped generation as
		// vcsVersion) but breaks the 3rd+ version added via Stonecutter.
		regex(current.parsed < "26.1") {
			replace(
				"\\bextractRenderState\\b" to "render",
				"\\brender\\b" to "extractRenderState"
			)
		}

		// Mojang split screen/HUD ownership off Minecraft in 26.2: Minecraft#screen
		// and Minecraft#setScreen are gone, replaced by Gui#screen()/Gui#setScreen,
		// and Gui#getTabList() moved to Gui#hud#getTabList(). Canonical source
		// (26.1.2) still targets the old Minecraft-owned API, so only 26.2 needs
		// the swap. Regex (not string()) because "client.screen" is also a plain
		// substring of every "dev.vy.drt.client.screen" package/import path; the
		// negative lookbehind keeps it from matching there.
		regex(current.parsed >= "26.2") {
			replace(
				"(?<!\\.)\\bclient\\.screen\\b" to "client.gui.screen()",
				"client\\.gui\\.screen\\(\\)" to "client.screen"
			)
			replace(
				"\\bclient\\.setScreen\\(" to "client.gui.setScreen(",
				"client\\.gui\\.setScreen\\(" to "client.setScreen("
			)
			replace(
				"\\bminecraft\\.setScreen\\(" to "minecraft.gui.setScreen(",
				"minecraft\\.gui\\.setScreen\\(" to "minecraft.setScreen("
			)
			replace(
				"client\\.gui\\.getTabList\\(\\)" to "client.gui.hud.getTabList()",
				"client\\.gui\\.hud\\.getTabList\\(\\)" to "client.gui.getTabList()"
			)

			// 26.2 also collapsed the 16 per-color *_STAINED_GLASS_PANE item
			// constants into one Items.STAINED_GLASS_PANE : ColorCollection<Item>.
			// Lookbehind on "Items." (not word-boundary) because
			// GRAY_STAINED_GLASS_PANE is itself a suffix of
			// LIGHT_GRAY_STAINED_GLASS_PANE — a plain/word-bounded match would
			// still fire inside the LIGHT_ constant.
			replace(
				"(?<=Items\\.)LIGHT_GRAY_STAINED_GLASS_PANE\\b" to "STAINED_GLASS_PANE.lightGray()",
				"STAINED_GLASS_PANE\\.lightGray\\(\\)" to "LIGHT_GRAY_STAINED_GLASS_PANE"
			)
			replace(
				"(?<=Items\\.)GRAY_STAINED_GLASS_PANE\\b" to "STAINED_GLASS_PANE.gray()",
				"STAINED_GLASS_PANE\\.gray\\(\\)" to "GRAY_STAINED_GLASS_PANE"
			)
			replace(
				"(?<=Items\\.)BLACK_STAINED_GLASS_PANE\\b" to "STAINED_GLASS_PANE.black()",
				"STAINED_GLASS_PANE\\.black\\(\\)" to "BLACK_STAINED_GLASS_PANE"
			)
		}

		// Minecraft#getVersionType() (release/snapshot as a String) is gone in
		// 26.2; the closest surviving vanilla signal is WorldVersion#stable().
		string(current.parsed >= "26.2") {
			replace(
				"client.getVersionType()",
				"(net.minecraft.SharedConstants.getCurrentVersion().stable() ? \"release\" : \"snapshot\")"
			)
		}
	}
}
