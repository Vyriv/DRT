package dev.vy.drt.mixin;

import dev.vy.drt.client.cosmetics.NameStyler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.util.FormattedCharSequence;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(Font.class)
public abstract class FontCosmeticsMixin {
	@Unique
	private static final ThreadLocal<Integer> drt$decorationDepth = ThreadLocal.withInitial(() -> 0);

	@Unique
	private static boolean drt$shouldDecorateRenderedText() {
		if (!NameStyler.hasGradientStyles()) return false;

		Minecraft client = Minecraft.getInstance();
		return client != null
			&& client.level != null
			&& client.player != null
			&& drt$decorationDepth.get() == 0;
	}

	@Unique
	private static boolean drt$shouldDecorateMeasuredText() {
		if (!NameStyler.hasGradientStyles() && !NameStyler.hasChatHeaderStyles()) return false;

		Minecraft client = Minecraft.getInstance();
		return client != null
			&& client.level != null
			&& client.player != null
			&& drt$decorationDepth.get() == 0;
	}

	@Unique
	private static <T> T drt$decorateSafely(java.util.function.Supplier<T> action) {
		drt$decorationDepth.set(drt$decorationDepth.get() + 1);
		try {
			return action.get();
		} finally {
			int depth = drt$decorationDepth.get() - 1;
			if (depth <= 0) {
				drt$decorationDepth.remove();
			} else {
				drt$decorationDepth.set(depth);
			}
		}
	}

	@ModifyVariable(method = "prepareText(Ljava/lang/String;FFIZI)Lnet/minecraft/client/gui/Font$PreparedText;", at = @At("HEAD"), argsOnly = true)
	private String drt$decoratePreparedString(String text) {
		if (!drt$shouldDecorateRenderedText()) return text;
		return drt$decorateSafely(() -> {
			String styled = text;
			if (NameStyler.hasChatHeaderStyles()) {
				styled = NameStyler.applyChatHeaderToString(styled);
			}
			if (NameStyler.hasGradientStyles()) {
				styled = NameStyler.applyGradientToString(styled);
			}
			return styled;
		});
	}

	@ModifyVariable(method = "prepareText(Lnet/minecraft/util/FormattedCharSequence;FFIZZI)Lnet/minecraft/client/gui/Font$PreparedText;", at = @At("HEAD"), argsOnly = true)
	private FormattedCharSequence drt$decoratePreparedOrderedText(FormattedCharSequence text) {
		if (text == null || !drt$shouldDecorateRenderedText()) return text;
		return drt$decorateSafely(() -> {
			FormattedCharSequence styled = text;
			if (NameStyler.hasChatHeaderStyles()) {
				styled = NameStyler.applyChatHeaderToOrderedText(styled);
			}
			if (NameStyler.hasGradientStyles()) {
				styled = NameStyler.applyGradientToOrderedText(styled);
			}
			return styled;
		});
	}

	@ModifyVariable(method = "width(Ljava/lang/String;)I", at = @At("HEAD"), argsOnly = true)
	private String drt$decorateMeasuredString(String text) {
		if (!drt$shouldDecorateMeasuredText()) return text;
		return drt$decorateSafely(() -> {
			String styled = text;
			if (NameStyler.hasChatHeaderStyles()) {
				styled = NameStyler.applyChatHeaderToString(styled);
			}
			if (NameStyler.hasGradientStyles()) {
				styled = NameStyler.applyGradientToString(styled);
			}
			return styled;
		});
	}

	@ModifyVariable(method = "width(Lnet/minecraft/network/chat/FormattedText;)I", at = @At("HEAD"), argsOnly = true)
	private FormattedText drt$decorateMeasuredText(FormattedText text) {
		if (text == null || !drt$shouldDecorateMeasuredText()) return text;
		return drt$decorateSafely(() -> {
			FormattedText styled = text;
			if (NameStyler.hasChatHeaderStyles()) {
				styled = NameStyler.applyChatHeaderToFormattedText(styled);
			}
			if (NameStyler.hasGradientStyles()) {
				styled = NameStyler.applyGradientToFormattedText(styled);
			}
			return styled;
		});
	}

	@ModifyVariable(method = "width(Lnet/minecraft/util/FormattedCharSequence;)I", at = @At("HEAD"), argsOnly = true)
	private FormattedCharSequence drt$decorateMeasuredOrderedText(FormattedCharSequence text) {
		if (text == null || !drt$shouldDecorateMeasuredText()) return text;
		return drt$decorateSafely(() -> {
			FormattedCharSequence styled = text;
			if (NameStyler.hasChatHeaderStyles()) {
				styled = NameStyler.applyChatHeaderToOrderedText(styled);
			}
			if (NameStyler.hasGradientStyles()) {
				styled = NameStyler.applyGradientToOrderedText(styled);
			}
			return styled;
		});
	}
}
