//? if >=26.3 {
/*package dev.vy.drt.client.compat;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

// Custom DRT fields consume CharacterEvents without using vanilla EditBox widgets.
public abstract class SdlScreen extends net.minecraft.client.gui.screens.Screen {
	protected SdlScreen(Component title) { super(title); }
	@Override public void added() {
		super.added();
		Minecraft.getInstance().onTextInputFocusChange(this, true);
	}
	@Override public void removed() {
		Minecraft.getInstance().onTextInputFocusChange(this, false);
		super.removed();
	}
	public final void requestTextInput() {
		Minecraft.getInstance().onTextInputFocusChange(this, true);
	}
}
*///?}
