package dev.vy.drt.client;

/**
 * Documents where Fancy Menu must be painted relative to the vanilla ACS extract path.
 * Fancy cancels {@code AbstractContainerScreen.extractRenderState}, which also skips
 * {@code extractContents}. The Croesus highlight mixin only runs at extractContents TAIL,
 * so Fancy cannot rely on that path while it is active.
 */
public final class FancyMenuRenderContract {
	public enum DrawSite {
		/** Fancy owns the screen: draw from the extractRenderState cancel hook. */
		VANILLA_SUPPRESS_HOOK,
		/** Fancy is inactive: Croesus overlay / highlights draw from extractContents TAIL. */
		CONTENTS_TAIL
	}

	private FancyMenuRenderContract() {
	}

	public static DrawSite drawSite(boolean fancyMenuOwnsPresentation) {
		return fancyMenuOwnsPresentation ? DrawSite.VANILLA_SUPPRESS_HOOK : DrawSite.CONTENTS_TAIL;
	}

	public static boolean mustDrawBeforeVanillaCancel(boolean fancyMenuOwnsPresentation) {
		return fancyMenuOwnsPresentation;
	}
}
