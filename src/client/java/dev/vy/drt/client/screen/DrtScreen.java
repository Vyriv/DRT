package dev.vy.drt.client.screen;

import dev.vy.drt.client.DrtClient;
import dev.vy.drt.client.overlay.OverlayLayouts;
import dev.vy.drt.client.overlay.OverlayPreset;
import dev.vy.drt.client.render.DrtUiText;
import dev.vy.drt.client.render.RoundedUiRenderer;
import dev.vy.drt.client.tracker.DungeonRunTrackerFeature;
import dev.vy.drt.client.tracker.NeuItemResolver;
import dev.vy.drt.client.sync.DrtAccount;
import dev.vy.drt.client.sync.DrtDungeonStatsClient;
import dev.vy.drt.client.sync.DrtStatsSync;
import dev.vy.drt.api.DungeonPlayerStats;
import dev.vy.drt.api.PlayerStatMath;
import dev.vy.drt.config.ClearConfirmation;
import dev.vy.drt.config.DrtConfig;
import dev.vy.drt.config.DrtConfigManager;
import dev.vy.drt.config.OverlaySettings;
import dev.vy.drt.config.ThemeDiscovery;
import dev.vy.drt.config.DungeonFloor;
import dev.vy.drt.config.DungeonLootEntry;
import dev.vy.drt.config.DungeonRunCompletionRecord;
import dev.vy.drt.config.DungeonRunRecord;
import dev.vy.drt.history.RunHistoryLinks;
import dev.vy.drt.history.LootHistoryProjection;
import dev.vy.drt.history.RunsDashboardData;
import dev.vy.drt.history.RunScore;
import dev.vy.drt.history.CroesusApiRecovery;
import dev.vy.drt.history.CroesusRecoveryFeedback;
import dev.vy.drt.price.DungeonProfitPricing;
import dev.vy.drt.tracking.EnchantedBookIdentity;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.util.Util;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.lwjgl.glfw.GLFW;

public class DrtScreen extends Screen {
	boolean themePreview;
	private final ThemePreviewData previewData = new ThemePreviewData();
	void previewTab(DrtNavTab tab) { activeTab = tab; }
	private int themePreviewTop;
	private int themePreviewBottom;
	void previewViewport(int top, int bottom) {
		themePreviewTop = Math.max(0, top);
		themePreviewBottom = Math.max(themePreviewTop + 1, bottom);
	}

	private static final double PANEL_W_RATIO = 0.72D;
	private static final int MIN_PANEL_W = 620;
	private static final int MAX_PANEL_W = 1380;
	private static final int SCREEN_EDGE_MARGIN = 12;
	private static final int MARGIN = 10;
	private static final int NAV_BAR_H = 22;
	private static final int NAV_PAD_TOP = 6;
	private static final int NAV_GAP = 5;
	private static final int TAB_H = 18;
	private static final int HEADER_H = 30;
	private static final int SECTION_H = 18;
	private static final int LOOT_SECTION_GAP = 6;
	private static final int MIN_LIST_W = 184;
	private static final int MAX_LIST_W = 220;
	private static final int ROW_H = 18;
	private static final int FOOTER_H = 24;
	private static final int FOOTER_GAP = 5;
	private static final int METRIC_CARD_H = 30;
	private static final int LOOT_CONTEXT_W = 128;
	private static final int LOOT_CONTEXT_ROW_H = 18;
	private static final int LOOT_CONTEXT_SEPARATOR_H = 7;
	private static final int LOOT_CONTEXT_H = 4 * LOOT_CONTEXT_ROW_H + LOOT_CONTEXT_SEPARATOR_H + 8;
	private static final int PANEL_BG = 0xFF111120;
	private static final int PANEL_BG_ALT = 0xFF141425;
	private static final int PANEL_BORDER = 0xFF2D2D50;
	private static final int PANEL_BORDER_HOVER = 0xFF494978;
	private static final int BORDER_ACTIVE = 0xFF6268A8;
	private static final int TEXT_PRIMARY = 0xFFE5E7F0;
	private static final int TEXT_SECONDARY = 0xFF9AA4BD;
	private static final int TEXT_MUTED = 0xFF78829D;
	private static final long OPEN_ANIMATION_MS = 200L;
	private static final float OPEN_START_SCALE = 0.94F;
	private static final java.time.format.DateTimeFormatter OVERVIEW_DATE_FORMAT = java.time.format.DateTimeFormatter
		.ofPattern("d MMM yyyy, HH:mm", Locale.ROOT).withZone(java.time.ZoneId.systemDefault());

	protected DrtNavTab activeTab;
	protected final Screen parent;

	// null = All floors
	private DungeonFloor selectedTab = null;
	private List<DungeonFloor> availableTabs = new ArrayList<>();
	private List<LootHistoryProjection.RunEntry> allRunEntries = List.of();
	private List<LootHistoryProjection.RunEntry> tabRunEntries = List.of();
	private List<DungeonRunRecord> tabHistory = List.of();
	private final Map<String, String> selectedChestByRun = new LinkedHashMap<>();
	private final Map<String, String> chestSelectionBeforeSearch = new LinkedHashMap<>();
	private int chestTabOffset;
	private boolean suppressNextSpaceCharacter;

	private int selectedRunIndex = -1;
	private String selectedLootKey = "";
	private String lootSearchBuffer = "";
	private long lootSearchLastTypedMillis;
	private boolean lootSearchFocused;
	private boolean lootSearchCommitted;
	private boolean lootSearchIncludeUnopened;
	private boolean initialLootSearchPending;
	private int listScroll;
	private int tableScroll;
	private int floorTabOffset;
	private ScrollbarDrag scrollbarDrag = ScrollbarDrag.NONE;
	private int scrollbarDragOffset;
	private final ClearConfirmation clearConfirm = new ClearConfirmation();
	private final LootContextMenuState lootContextMenu = new LootContextMenuState();
	private DungeonRunRecord lootContextRecord;
	private DungeonRunCompletionRecord lootContextCompletion;
	private String lootActionMessage = "";
	private long lootActionMessageUntil;
	private LootSortColumn lootSortColumn;
	private boolean lootSortAscending;
	private Map<DungeonRunRecord, RunHistoryLinks.Link> runLinks = Map.of();
	private Map<String, DungeonRunCompletionRecord> completionsBySession = Map.of();

	private final List<ClickTarget> clickTargets = new ArrayList<>();
	private final List<HoverTarget> hoverTargets = new ArrayList<>();
	private final long openedAtNanos = System.nanoTime();
	private OverviewDashboardData.Metric overviewMetric = OverviewDashboardData.Metric.PROFIT;
	private OverviewDashboardData.TimeRange overviewRange = OverviewDashboardData.TimeRange.DAYS_30;
	private String overviewFloor;
	private OverviewDashboardData.Snapshot overviewSnapshot;
	private long overviewSnapshotRevision = Long.MIN_VALUE;
	private OverviewDashboardData.Metric overviewSnapshotMetric;
	private OverviewDashboardData.TimeRange overviewSnapshotRange;
	private String overviewSnapshotFloor;
	private GraphGeometry overviewGraphGeometry;

	// Layout coords recomputed each frame
	private int ox, oy;
	private int winW, winH;
	private int navBarY, contentTopY;
	private int listX, listY, listW, listH;
	private int tableX, tableY, tableW, tableH;
	private int tabBarY;
	private int searchX, searchY, searchW, searchH;
	private RunsDashboardData.Snapshot runsSnapshot;
	private long runsSnapshotRevision = -1L;
	private String runsFloor = "";
	private String runsSearch = "";
	private RunsDashboardData.Sort runsSort = RunsDashboardData.Sort.NEWEST;
	private String selectedRunCompletionId = "";
	private boolean runsSearchFocused;
	private int runsListScroll;
	private int runsListX, runsListY, runsListW, runsListH;
	private int runsSearchX, runsSearchY, runsSearchW, runsSearchH;
	private String selectedPartyIdentity = "";
	private volatile DrtDungeonStatsClient.Result selectedPlayerStats;
	private volatile boolean playerStatsLoading;
	private String recoveryStatus = "";
	private int settingsViewportX, settingsViewportY, settingsViewportW, settingsViewportH;
	private SettingsDropdown settingsDropdown = SettingsDropdown.NONE;
	private int settingsDropdownX, settingsDropdownY, settingsDropdownW, settingsDropdownH, settingsDropdownAnchorY, settingsDropdownAnchorH;
	private int settingsDropdownScroll, settingsDropdownVisibleRows;
	private List<String> settingsThemes = List.of(ThemeDiscovery.DEFAULT_THEME);
	private String settingsStatus = "";
	private long settingsStatusUntil;
	private long settingsDeleteConfirmUntil;
	private int settingsRowHeight = SettingsLayout.MIN_ROW_HEIGHT;
	private int settingsScroll;
	private int settingsContentH;
	private int settingsSectionFirstRowY;
	private final OverlayPageState overlayPage = new OverlayPageState();
	private long overlayResetConfirmUntil;

	public DrtScreen() {
		this(DrtNavTab.OVERVIEW, null, null, null);
	}

	public DrtScreen(DrtNavTab initialTab) {
		this(initialTab, null, null, null);
	}

	public DrtScreen(DrtNavTab initialTab, DungeonFloor floorFilter, String searchFilter) {
		this(initialTab, floorFilter, searchFilter, null);
	}

	public DrtScreen(DrtNavTab initialTab, DungeonFloor floorFilter, String searchFilter, Screen parent) {
		super(Component.literal("DRT"));
		this.activeTab = initialTab != null ? initialTab : DrtNavTab.OVERVIEW;
		this.parent = parent;
		if (floorFilter != null) {
			this.selectedTab = floorFilter;
		}
		if (searchFilter != null) {
			this.lootSearchBuffer = searchFilter;
			this.initialLootSearchPending = !searchFilter.isBlank();
		}
	}

	@Override
	protected void init() {
		if (themePreview) { reloadHistory(); return; }
		if (selectedTab == null) {
			String saved = DrtConfigManager.getDungeonSelectedFloor();
			if (saved != null && !saved.isBlank()) {
				try {
					selectedTab = DungeonFloor.valueOf(saved);
				} catch (IllegalArgumentException ignored) {
					selectedTab = null;
				}
			}
		}
		reloadHistory();
		if (activeTab == DrtNavTab.SETTINGS) reloadThemes(false);
	}

	@Override
	public void onClose() {
		if (themePreview) return;
		DrtConfigManager.updateSelectedFloor(selectedTab == null ? null : selectedTab.name());
		if (minecraft != null && parent != null) {
			minecraft.gui.setScreen(parent);
			return;
		}
		super.onClose();
	}

	public void setActiveTab(DrtNavTab tab) {
		if (tab == null) tab = DrtNavTab.OVERVIEW;
		this.activeTab = tab;
		settingsDropdown = SettingsDropdown.NONE;
		overlayPage.cancel();
		overlayResetConfirmUntil = 0L;
		if (tab == DrtNavTab.SETTINGS && settingsThemes.size() == 1) reloadThemes(false);
		clearLootFocus();
	}

	public DrtNavTab getActiveTab() {
		return activeTab;
	}

	private void clearLootFocus() {
		selectedLootKey = "";
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	@Override
	public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
		dev.vy.drt.config.DrtTheme.ensureLoaded();
		ThemePreviewDraw.fill(g, 0, 0, width, height, dev.vy.drt.config.DrtTheme.color("scrim", 0xAA000000));
		updateLayout();
		float scale = openingScale();
		int logicalMouseX = logicalMouseX(mouseX, scale);
		int logicalMouseY = logicalMouseY(mouseY, scale);
		if (activeTab == DrtNavTab.LOOT && scrollbarDrag != ScrollbarDrag.NONE) {
			updateScrollbarDrag(logicalMouseY);
		}
		if (activeTab == DrtNavTab.LOOT && initialLootSearchPending) {
			initialLootSearchPending = false;
			searchLootRows();
		}

		clickTargets.clear();
		hoverTargets.clear();
		RoundedUiRenderer.beginFrame(g, width, height, scale);
		var pose = g.pose();
		pose.pushMatrix();
		pose.translate(width / 2.0F, height / 2.0F);
		pose.scale(scale);
		pose.translate(-width / 2.0F, -height / 2.0F);
		drawWindow(g);
		drawNavBar(g, logicalMouseX, logicalMouseY);

		switch (activeTab) {
			case LOOT -> {
				int contentMouseX = lootContextMenu.open() ? Integer.MIN_VALUE : logicalMouseX;
				int contentMouseY = lootContextMenu.open() ? Integer.MIN_VALUE : logicalMouseY;
				drawTabs(g, contentMouseX, contentMouseY);
				drawAddButton(g, contentMouseX, contentMouseY);
				drawHeader(g, contentMouseX, contentMouseY, headerContentY());
				drawSectionLabels(g, headerContentY() + HEADER_H + LOOT_SECTION_GAP);
				drawRunList(g, contentMouseX, contentMouseY);
				drawLootTable(g, contentMouseX, contentMouseY);
				drawFooter(g, contentMouseX, contentMouseY);
				drawLootActionMessage(g);
			}
			case OVERVIEW -> drawOverviewTab(g, logicalMouseX, logicalMouseY, contentTopY);
			case RUNS -> drawRunsTab(g, logicalMouseX, logicalMouseY, contentTopY);
			case OVERLAY -> drawOverlayTab(g, logicalMouseX, logicalMouseY, contentTopY);
			case SETTINGS -> drawSettingsTab(g, logicalMouseX, logicalMouseY, contentTopY);
		}
		if (activeTab == DrtNavTab.LOOT && lootContextMenu.open()) {
			// Submit popup shapes separately, above the completed content pass and item/text layers.
			RoundedUiRenderer.endFrame();
			g.nextStratum();
			RoundedUiRenderer.beginFrame(g, width, height, scale);
			drawLootContextMenu(g, logicalMouseX, logicalMouseY);
		}
		drawHoveredTooltip(g, logicalMouseX, logicalMouseY);
		RoundedUiRenderer.endFrame();
		if (scale < 0.999F) {
			int alpha = clamp((int) ((1.0F - animationProgress()) * 34.0F), 0, 34);
			ThemePreviewDraw.fill(g, ox, oy, ox + winW, oy + winH, alpha << 24);
		}
		pose.popMatrix();
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean isRepeat) {
		if (themePreview) return true;
		float scale = openingScale();
		int mouseX = logicalMouseX(event.x(), scale);
		int mouseY = logicalMouseY(event.y(), scale);
		if (settingsDropdownOpen()) {
			if (contains(settingsDropdownX, settingsDropdownY, settingsDropdownW, settingsDropdownH, mouseX, mouseY)) {
				for (int i = clickTargets.size() - 1; i >= 0; i--) {
					ClickTarget target = clickTargets.get(i);
					if (target.button == event.button() && contains(target.x, target.y, target.w, target.h, mouseX, mouseY)
						&& contains(settingsDropdownX, settingsDropdownY, settingsDropdownW, settingsDropdownH, target.x, target.y)) {
						target.action.run();
						return true;
					}
				}
				return true;
			}
			if (!contains(settingsDropdownX, settingsDropdownAnchorY, settingsDropdownW, settingsDropdownAnchorH, mouseX, mouseY)) {
				settingsDropdown = SettingsDropdown.NONE;
				return true;
			}
		}
		if (activeTab == DrtNavTab.LOOT && lootContextMenu.open()) {
			if (!lootContextMenu.contains(mouseX, mouseY)) {
				closeLootContextMenu();
				return true;
			}
			for (int i = clickTargets.size() - 1; i >= 0; i--) {
				ClickTarget target = clickTargets.get(i);
				if (target.button == event.button() && lootContextMenu.contains(target.x, target.y)
					&& contains(target.x, target.y, target.w, target.h, mouseX, mouseY)) {
					target.action.run();
					return true;
				}
			}
			return true;
		}
		if (activeTab == DrtNavTab.LOOT && event.button() == GLFW.GLFW_MOUSE_BUTTON_LEFT && beginScrollbarDrag(mouseX, mouseY)) {
			return true;
		}
		if (activeTab == DrtNavTab.LOOT && !contains(searchX, searchY, searchW, searchH, mouseX, mouseY)) {
			lootSearchFocused = false;
		}
		if (activeTab == DrtNavTab.RUNS && !contains(runsSearchX, runsSearchY, runsSearchW, runsSearchH, mouseX, mouseY)) {
			runsSearchFocused = false;
		}
		for (int i = clickTargets.size() - 1; i >= 0; i--) {
			ClickTarget t = clickTargets.get(i);
			if (t.button == event.button() && contains(t.x, t.y, t.w, t.h, mouseX, mouseY)) {
				t.action.run();
				return true;
			}
		}
		return super.mouseClicked(event, isRepeat);
	}

	@Override
	public boolean mouseReleased(MouseButtonEvent event) {
		if (event.button() == GLFW.GLFW_MOUSE_BUTTON_LEFT && scrollbarDrag != ScrollbarDrag.NONE) {
			scrollbarDrag = ScrollbarDrag.NONE;
			return true;
		}
		return super.mouseReleased(event);
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
		if (settingsDropdownOpen()) {
			float scale = openingScale();
			int logicalX = logicalMouseX(mouseX, scale);
			int logicalY = logicalMouseY(mouseY, scale);
			if (contains(settingsDropdownX, settingsDropdownY, settingsDropdownW, settingsDropdownH, logicalX, logicalY)) {
				int optionCount = settingsDropdownOptions().size();
				int maxScroll = Math.max(0, optionCount - settingsDropdownVisibleRows);
				settingsDropdownScroll = clamp(settingsDropdownScroll + (scrollY < 0.0D ? 1 : -1), 0, maxScroll);
			}
			return true;
		}
		if (activeTab == DrtNavTab.LOOT) {
			if (lootContextMenu.open()) {
				closeLootContextMenu();
				return true;
			}
			updateLayout();
			float scale = openingScale();
			mouseX = logicalMouseX(mouseX, scale);
			mouseY = logicalMouseY(mouseY, scale);
			int delta = (int) Math.copySign(ROW_H, scrollY);
			int tabBarY = headerContentY() - TAB_H - 4;
			if (contains(ox + MARGIN, tabBarY, winW - MARGIN * 2, TAB_H, (int) mouseX, (int) mouseY)) {
				int totalTabs = availableTabs.size() + 1;
				int tabDelta = (int) Math.copySign(1, -scrollY);
				floorTabOffset = clamp(floorTabOffset + tabDelta, 0, Math.max(0, totalTabs - 1));
				return true;
			}
			if (contains(listX, listY, listW, listH, (int) mouseX, (int) mouseY)) {
				int maxScroll = Math.max(0, (tabHistory.size() + 1) * ROW_H - listViewportH());
				listScroll = clamp(listScroll - delta, 0, maxScroll);
				return true;
			}
			if (contains(tableX, lootPanelY(), tableW, lootPanelH(), (int) mouseX, (int) mouseY)) {
				int viewportH = tableBodyH();
				int maxScroll = Math.max(0, buildDisplayRows().size() * ROW_H - viewportH);
				tableScroll = clamp(tableScroll - delta, 0, maxScroll);
				return true;
			}
		}
		if (activeTab == DrtNavTab.SETTINGS && settingsMaxScroll() > 0) {
			float scale = openingScale();
			int logicalX = logicalMouseX(mouseX, scale);
			int logicalY = logicalMouseY(mouseY, scale);
			if (contains(settingsViewportX, settingsViewportY, settingsViewportW, settingsViewportH, logicalX, logicalY)) {
				settingsScroll = clamp(settingsScroll - (int) Math.copySign(settingsRowHeight, scrollY), 0, settingsMaxScroll());
				return true;
			}
		}
		if (activeTab == DrtNavTab.RUNS && contains(runsListX, runsListY, runsListW, runsListH, (int) mouseX, (int) mouseY)) {
			int total = runsData().entries().size() * RunsPanelLayout.RUN_ROW_HEIGHT;
			int viewport = Math.max(1, runsListH - 22);
			int runsDelta = (int) Math.copySign(ROW_H, scrollY);
			runsListScroll = clamp(runsListScroll - runsDelta, 0, Math.max(0, total - viewport));
			return true;
		}
		return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		if (event.key() == GLFW.GLFW_KEY_ESCAPE) {
			if (activeTab == DrtNavTab.LOOT && lootContextMenu.open()) {
				closeLootContextMenu();
				return true;
			}
			onClose();
			return true;
		}
		if (activeTab == DrtNavTab.LOOT) {
			if (lootSearchFocused && event.key() == GLFW.GLFW_KEY_BACKSPACE && !lootSearchBuffer.isEmpty()) {
				lootSearchBuffer = LootSearchInput.backspace(lootSearchBuffer);
				resetLootSearchModeAfterEdit();
				selectedLootKey = "";
				searchLootRows();
				return true;
			}
			if (lootSearchFocused && event.key() == GLFW.GLFW_KEY_SPACE) {
				lootSearchBuffer = LootSearchInput.append(lootSearchBuffer, " ");
				resetLootSearchModeAfterEdit();
				suppressNextSpaceCharacter = true;
				selectedLootKey = "";
				searchLootRows();
				return true;
			}
			if (lootSearchFocused && (event.key() == GLFW.GLFW_KEY_ENTER || event.key() == GLFW.GLFW_KEY_KP_ENTER)) {
				boolean shiftDown = (event.modifiers() & GLFW.GLFW_MOD_SHIFT) != 0;
				LootSearchPolicy.Mode mode = LootSearchPolicy.committed(lootSearchBuffer, shiftDown);
				lootSearchCommitted = mode.committed();
				lootSearchIncludeUnopened = mode.includeUnopened();
				searchLootRows();
				return true;
			}
			if (event.key() == GLFW.GLFW_KEY_UP || event.key() == GLFW.GLFW_KEY_DOWN) {
				int delta = event.key() == GLFW.GLFW_KEY_UP ? -1 : 1;
				int next = clamp(selectedRunIndex + delta, -1, tabHistory.size() - 1);
				if (next != selectedRunIndex) {
					closeLootContextMenu();
					selectRunIndex(next);
					int rowTop = (next + 1) * ROW_H;
					if (rowTop < listScroll) {
						listScroll = rowTop;
					} else if (rowTop + ROW_H > listScroll + listViewportH()) {
						listScroll = rowTop + ROW_H - listViewportH();
					}
					int maxScroll = Math.max(0, (tabHistory.size() + 1) * ROW_H - listViewportH());
					listScroll = clamp(listScroll, 0, maxScroll);
					return true;
				}
			}
		}
		if (activeTab == DrtNavTab.RUNS) {
			if (runsSearchFocused && event.key() == GLFW.GLFW_KEY_BACKSPACE && !runsSearch.isEmpty()) {
				runsSearch = LootSearchInput.backspace(runsSearch);
				invalidateRunsData();
				return true;
			}
			if (runsSearchFocused && event.key() == GLFW.GLFW_KEY_SPACE) {
				runsSearch = LootSearchInput.append(runsSearch, " ");
				invalidateRunsData();
				return true;
			}
		}
		return super.keyPressed(event);
	}

	@Override
	public boolean charTyped(CharacterEvent event) {
		if (activeTab == DrtNavTab.RUNS && runsSearchFocused) {
			String typed = event.codepointAsString();
			if (typed != null && !typed.isEmpty() && !Character.isISOControl(typed.codePointAt(0)) && typed.codePointAt(0) != ' ') {
				runsSearch = LootSearchInput.append(runsSearch, typed);
				invalidateRunsData();
				return true;
			}
		}
		if (activeTab != DrtNavTab.LOOT || !lootSearchFocused) {
			return super.charTyped(event);
		}
		String typed = event.codepointAsString();
		if (typed == null || typed.isEmpty()) {
			return super.charTyped(event);
		}
		int codePoint = typed.codePointAt(0);
		if (codePoint == ' ' && suppressNextSpaceCharacter) {
			suppressNextSpaceCharacter = false;
			return true;
		}
		suppressNextSpaceCharacter = false;
		if (Character.isISOControl(codePoint)) {
			return super.charTyped(event);
		}

		lootSearchLastTypedMillis = System.currentTimeMillis();
		lootSearchBuffer = LootSearchInput.append(lootSearchBuffer, typed);
		resetLootSearchModeAfterEdit();
		searchLootRows();
		return true;
	}

	// ── Layout & Drawing ──────────────────────────────────────────────────────

	private void updateLayout() {
		int availableW = Math.max(1, width - SCREEN_EDGE_MARGIN * 2);
		int viewportTop = themePreview && themePreviewBottom > themePreviewTop ? themePreviewTop : 0;
		int viewportBottom = themePreview && themePreviewBottom > themePreviewTop ? themePreviewBottom : height;
		int viewportH = Math.max(1, viewportBottom - viewportTop);
		int availableH = Math.max(1, viewportH - SCREEN_EDGE_MARGIN * 2);
		winW = Math.min(availableW, clamp((int) Math.round(width * PANEL_W_RATIO), MIN_PANEL_W, MAX_PANEL_W));
		winH = MainWindowLayout.height(availableH);
		ox = (width - winW) / 2;
		oy = viewportTop + (viewportH - winH) / 2;

		navBarY = oy + NAV_PAD_TOP;
		contentTopY = navBarY + NAV_BAR_H + NAV_GAP;

		tabBarY = contentTopY;
		int contentY = headerContentY();
		int responsiveMinListW = winW < 560 ? 108 : MIN_LIST_W;
		int responsiveMaxListW = winW < 560 ? 150 : MAX_LIST_W;
		listW = clamp((int) Math.round(winW * 0.29D), responsiveMinListW, responsiveMaxListW);
		listX = ox + MARGIN;
		listY = contentY + HEADER_H + LOOT_SECTION_GAP + SECTION_H;
		listH = Math.max(ROW_H, winH - (listY - oy) - MARGIN - FOOTER_H - FOOTER_GAP);
		tableX = listX + listW + MARGIN;
		tableY = listY;
		tableW = winW - listW - MARGIN * 3;
		tableH = listH;
	}

	private int headerContentY() {
		return tabBarY + TAB_H + 4;
	}

	private void drawWindow(GuiGraphicsExtractor g) {
		roundedPanel(g, ox, oy, winW, winH, 4, dev.vy.drt.config.DrtTheme.color("window.background", 0xFF0E0E1E), dev.vy.drt.config.DrtTheme.color("window.border", 0xFF3A3A60));
	}

	private void drawNavBar(GuiGraphicsExtractor g, int mouseX, int mouseY) {
		int brandX = ox + MARGIN;
		int brandY = DrtUiText.centeredTextY(font, navBarY, NAV_BAR_H);
		ThemePreviewDraw.text(g, font, "DRT", brandX, brandY, dev.vy.drt.config.DrtTheme.color("text.accent", 0xFF9CA8FF), true);

		int tabX = brandX + font.width("DRT") + 14;
		int closeW = 20;
		int closeX = ox + winW - MARGIN - closeW;
		int fullTabsWidth = 0;
		for (DrtNavTab tab : DrtNavTab.values()) fullTabsWidth += font.width(tab.title()) + 14;
		fullTabsWidth += (DrtNavTab.values().length - 1) * 4;
		boolean compact = tabX + fullTabsWidth > closeX - 6;
		int horizontalPadding = compact ? 8 : 14;
		int tabGap = compact ? 2 : 4;
		for (DrtNavTab tab : DrtNavTab.values()) {
			String title = compact ? tab.compactTitle() : tab.title();
			int tabW = font.width(title) + horizontalPadding;
			boolean active = (tab == activeTab);
			boolean hovered = contains(tabX, navBarY, tabW, NAV_BAR_H, mouseX, mouseY);

			int bg = active ? dev.vy.drt.config.DrtTheme.color("tab.activeBackground", 0xFF202A52) : hovered ? dev.vy.drt.config.DrtTheme.color("tab.hoverBackground", 0xFF17172A) : dev.vy.drt.config.DrtTheme.color("tab.background", 0xFF0F0F1D);
			int border = active ? dev.vy.drt.config.DrtTheme.color("tab.activeBorder", 0xFF6A6AB0) : hovered ? dev.vy.drt.config.DrtTheme.color("tab.hoverBorder", PANEL_BORDER_HOVER) : dev.vy.drt.config.DrtTheme.color("tab.border", 0xFF343458);
			int textColor = active ? dev.vy.drt.config.DrtTheme.color("tab.textActive", TEXT_PRIMARY) : hovered ? dev.vy.drt.config.DrtTheme.color("tab.textHover", TEXT_PRIMARY) : dev.vy.drt.config.DrtTheme.color("tab.text", TEXT_SECONDARY);

			roundedPanel(g, tabX, navBarY, tabW, NAV_BAR_H, 4, bg, border);
			int tx = tabX + horizontalPadding / 2;
			int ty = DrtUiText.centeredTextY(font, navBarY, NAV_BAR_H);
			ThemePreviewDraw.text(g, font, title, tx, ty, textColor);

			clickTargets.add(new ClickTarget(tabX, navBarY, tabW, NAV_BAR_H, 0, () -> setActiveTab(tab)));
			tabX += tabW + tabGap;
		}

		// Close button on far right
		boolean closeHovered = contains(closeX, navBarY, closeW, NAV_BAR_H, mouseX, mouseY);
		int closeBg = closeHovered ? dev.vy.drt.config.DrtTheme.color("control.hover", 0xFF281822) : dev.vy.drt.config.DrtTheme.color("control.background", 0xFF12101E);
		int closeBorder = closeHovered ? 0xFFFF6B6B : dev.vy.drt.config.DrtTheme.color("control.border", 0xFF343458);
		roundedPanel(g, closeX, navBarY, closeW, NAV_BAR_H, 4, closeBg, closeBorder);
		int cx = closeX + (closeW - font.width("x")) / 2;
		int cy = DrtUiText.centeredTextY(font, navBarY, NAV_BAR_H);
		ThemePreviewDraw.text(g, font, "x", cx, cy, closeHovered ? 0xFFFF6B6B : dev.vy.drt.config.DrtTheme.color("control.text", TEXT_SECONDARY));
		clickTargets.add(new ClickTarget(closeX, navBarY, closeW, NAV_BAR_H, 0, this::onClose));

		// Divider line below nav bar
		ThemePreviewDraw.fill(g, ox + MARGIN, navBarY + NAV_BAR_H + 2, ox + winW - MARGIN, navBarY + NAV_BAR_H + 3, dev.vy.drt.config.DrtTheme.color("separator", PANEL_BORDER));
	}

	// ── Overview Tab ──────────────────────────────────────────────────────────

	private void drawOverviewTab(GuiGraphicsExtractor g, int mouseX, int mouseY, int topY) {
		int contentX = ox + MARGIN;
		int contentW = winW - MARGIN * 2;
		int contentH = winH - (topY - oy) - MARGIN;
		OverviewDashboardData.Snapshot data = overviewData();
		int titleY = topY + 2;
		ThemePreviewDraw.text(g, font, "Overview", contentX, titleY, dev.vy.drt.config.DrtTheme.color("text.pageTitle", TEXT_PRIMARY), true);
		String scope = overviewFloor == null ? "All floors" : overviewFloor;
		ThemePreviewDraw.text(g, font, scope, contentX + contentW - font.width(scope), titleY, dev.vy.drt.config.DrtTheme.color("text.accent", 0xFF9CA8FF));

		int cardsY = titleY + font.lineHeight + 4;
		int gap = 5;
		int cardH = METRIC_CARD_H;
		int cardW = (contentW - gap * 2) / 3;
		drawOverviewMetric(g, contentX, cardsY, cardW, cardH, "Total Runs", Integer.toString(data.totalRuns()), dev.vy.drt.config.DrtTheme.color("text.primary", TEXT_PRIMARY),
			data.totalRuns() + " persisted run completions");
		drawOverviewMetric(g, contentX + cardW + gap, cardsY, cardW, cardH, "Total Profit", formatSigned(data.totalProfit()), profitColor(data.totalProfit()),
			"Exact recorded chest profit: " + formatExactCoins(data.totalProfit()));
		String averageProfit = data.averageLinkedRunProfit() == null ? "Unknown" : formatSigned(data.averageLinkedRunProfit());
		drawOverviewMetric(g, contentX + (cardW + gap) * 2, cardsY, cardW, cardH, "Average Profit / Run", averageProfit,
			data.averageLinkedRunProfit() == null ? dev.vy.drt.config.DrtTheme.color("text.muted", TEXT_MUTED) : profitColor(data.averageLinkedRunProfit()),
			data.linkedProfitRuns() == 0 ? "No chest profits have a proven run link" : "Based on " + data.linkedProfitRuns() + " runs with proven chest links");

		int cardsY2 = cardsY + cardH + gap;
		drawOverviewMetric(g, contentX, cardsY2, cardW, cardH, "Average Run Time", formatOptionalTime(data.averageRunTimeMs()), dev.vy.drt.config.DrtTheme.color("time.medium", 0xFF8FB9FF),
			data.averageRunTimeMs() == null ? "No proven run times recorded" : "Average of proven, plausible run times");
		drawOverviewMetric(g, contentX + cardW + gap, cardsY2, cardW, cardH, "Best Run Time", formatOptionalTime(data.bestRunTimeMs()), dev.vy.drt.config.DrtTheme.color("time.fast", 0xFF8BCF9A),
			data.bestRunTimeMs() == null ? "No proven run times recorded" : "Fastest proven run: " + formatExactDuration(data.bestRunTimeMs()));
		String bestFloor = data.mostProfitableFloor() == null ? "Unknown" : data.mostProfitableFloor() + "  " + formatSigned(data.mostProfitableFloorProfit());
		drawOverviewMetric(g, contentX + (cardW + gap) * 2, cardsY2, cardW, cardH, "Most Profitable Floor", bestFloor,
			data.mostProfitableFloor() == null ? dev.vy.drt.config.DrtTheme.color("text.muted", TEXT_MUTED) : profitColor(data.mostProfitableFloorProfit()),
			data.mostProfitableFloor() == null ? "No recorded chest profit" : data.mostProfitableFloor() + " exact profit: " + formatExactCoins(data.mostProfitableFloorProfit()));

		int sectionGap = 6;
		int graphY = cardsY2 + cardH + sectionGap;
		MainWindowLayout.OverviewSplit split = MainWindowLayout.overviewSplit(topY + contentH - graphY - sectionGap);
		drawOverviewGraph(g, contentX, graphY, contentW, split.graphH(), data, mouseX, mouseY);

		int activityY = graphY + split.graphH() + sectionGap;
		drawOverviewActivity(g, contentX, activityY, contentW, split.activityH(), data, mouseX, mouseY);
	}

	private void drawOverviewMetric(GuiGraphicsExtractor g, int x, int y, int w, int h, String title, String value, int valueColor, String tooltip) {
		roundedPanel(g, x, y, w, h, 4, dev.vy.drt.config.DrtTheme.color("panel.alternate", PANEL_BG_ALT), dev.vy.drt.config.DrtTheme.color("panel.border", PANEL_BORDER));
		ThemePreviewDraw.text(g, font, title, x + 7, y + 5, dev.vy.drt.config.DrtTheme.color("text.metricLabel", TEXT_MUTED));
		String clipped = font.plainSubstrByWidth(value, Math.max(4, w - 14));
		ThemePreviewDraw.text(g, font, clipped, x + 7, y + 17, valueColor, true);
		hoverTargets.add(new HoverTarget(x, y, w, h, tooltip));
	}

	private void drawOverviewGraph(GuiGraphicsExtractor g, int x, int y, int w, int h, OverviewDashboardData.Snapshot data, int mouseX, int mouseY) {
		roundedPanel(g, x, y, w, h, 4, dev.vy.drt.config.DrtTheme.color("panel.background", PANEL_BG), dev.vy.drt.config.DrtTheme.color("panel.border", PANEL_BORDER));
		int controlY = y + 6;
		int controlH = 17;
		String metricLabel = metricControlLabel();
		int controlsX = x + 8;
		int labelY = DrtUiText.centeredTextY(font, controlY, controlH);
		ThemePreviewDraw.text(g, font, "Metric", controlsX, labelY, dev.vy.drt.config.DrtTheme.color("text.metricLabel", TEXT_MUTED));
		int metricX = controlsX + font.width("Metric") + 5;
		int metricW = clamp(font.width(metricLabel) + 14, 66, 126);
		drawCompactControl(g, metricX, controlY, metricW, controlH, metricLabel, true, mouseX, mouseY, () -> cycleOverviewMetric(1));
		clickTargets.add(new ClickTarget(metricX, controlY, metricW, controlH, GLFW.GLFW_MOUSE_BUTTON_RIGHT, () -> cycleOverviewMetric(-1)));
		hoverTargets.add(new HoverTarget(metricX, controlY, metricW, controlH, overviewMetric.label));

		int rangeX = metricX + metricW + 11;
		ThemePreviewDraw.text(g, font, "Period", rangeX, labelY, dev.vy.drt.config.DrtTheme.color("text.metricLabel", TEXT_MUTED));
		rangeX += font.width("Period") + 5;
		for (OverviewDashboardData.TimeRange range : OverviewDashboardData.TimeRange.values()) {
			int rw = font.width(range.label) + 10;
			drawCompactControl(g, rangeX, controlY, rw, controlH, range.label, range == overviewRange, mouseX, mouseY, () -> setOverviewRange(range));
			rangeX += rw + 3;
		}
		String floorText = overviewFloor == null ? "All floors" : overviewFloor;
		int floorW = Math.max(46, font.width(floorText) + 14);
		int floorX = x + w - floorW - 7;
		int floorLabelX = floorX - font.width("Floor") - 5;
		ThemePreviewDraw.text(g, font, "Floor", floorLabelX, labelY, dev.vy.drt.config.DrtTheme.color("text.metricLabel", TEXT_MUTED));
		drawCompactControl(g, floorX, controlY, floorW, controlH, floorText, true, mouseX, mouseY, () -> cycleOverviewFloor(1));
		clickTargets.add(new ClickTarget(floorX, controlY, floorW, controlH, GLFW.GLFW_MOUSE_BUTTON_RIGHT, () -> cycleOverviewFloor(-1)));

		List<OverviewDashboardData.Point> points = data.graphPoints();
		if (points.isEmpty()) {
			String empty = overviewMetric == OverviewDashboardData.Metric.RUN_PROFIT ? "No reliably linked run profits in this range" : "No recorded data in this range";
			ThemePreviewDraw.text(g, font, empty, x + Math.max(0, (w - font.width(empty)) / 2), DrtUiText.centeredTextY(font, y, h), dev.vy.drt.config.DrtTheme.color("text.muted", TEXT_MUTED));
			return;
		}

		long rawMin = Math.min(0L, points.stream().mapToLong(OverviewDashboardData.Point::value).min().orElse(0L));
		long rawMax = Math.max(0L, points.stream().mapToLong(OverviewDashboardData.Point::value).max().orElse(0L));
		long verticalPadding = Math.max(1L, (rawMax - rawMin) / 12L);
		List<Long> yTicks = calculateNiceYTicks(rawMin < 0L ? rawMin - verticalPadding : rawMin, rawMax + verticalPadding);
		long tickMin = yTicks.get(0);
		long tickMax = yTicks.get(yTicks.size() - 1);
		int maxYLabelW = yTicks.stream().mapToInt(t -> font.width(formatGraphTick(t))).max().orElse(20);

		int plotX = x + 8 + maxYLabelW + 7;
		int plotW = Math.max(20, (x + w - 11) - plotX);
		int plotY = y + 30;
		int plotH = Math.max(20, h - 49);

		GraphGeometry geometry = overviewGraphGeometry;
		if (geometry == null || geometry.snapshot != data || geometry.plotX != plotX || geometry.plotY != plotY || geometry.plotW != plotW || geometry.plotH != plotH) {
			long firstTime = points.get(0).timestamp();
			long lastTime = points.get(points.size() - 1).timestamp();
			long timeSpan = Math.max(1L, lastTime - firstTime);
			geometry = buildOverviewGraphGeometry(data, points, plotX, plotY, plotW, plotH, tickMin, tickMax, firstTime, timeSpan, yTicks);
		}
		long firstTime = geometry.firstTime;
		long timeSpan = geometry.timeSpan;

		for (long tickVal : geometry.yTicks) {
			float gy = plotY + plotH - 1.0F - (float) ((tickVal - tickMin) * (plotH - 1) / (double) (tickMax - tickMin));
			int lineCol = tickVal == 0L ? dev.vy.drt.config.DrtTheme.color("chart.zero", 0x66444870) : dev.vy.drt.config.DrtTheme.color("chart.grid", 0x24323252);
			RoundedUiRenderer.line(plotX, gy, plotX + plotW, gy, 1.0F, lineCol);
			String label = formatGraphTick(tickVal);
			int textY = clamp((int) Math.round(gy - font.lineHeight / 2.0F), plotY - 4, plotY + plotH - font.lineHeight + 4);
			ThemePreviewDraw.text(g, font, label, plotX - 6 - font.width(label), textY, dev.vy.drt.config.DrtTheme.color("text.muted", TEXT_MUTED));
		}

		if (overviewMetric == OverviewDashboardData.Metric.PROFIT_DAY || overviewMetric == OverviewDashboardData.Metric.RUN_PROFIT) {
			drawOverviewBars(g, geometry, -1);
		} else {
			RoundedUiRenderer.graph(geometry.xs, geometry.ys, geometry.count, geometry.baseline,
				overviewMetric == OverviewDashboardData.Metric.RUNS, dev.vy.drt.config.DrtTheme.color("chart.line", 0xFF7887D6), dev.vy.drt.config.DrtTheme.color("chart.fill", 0x151E4B88));
		}

		// X-axis baseline & tick marks
		RoundedUiRenderer.line(plotX, plotY + plotH, plotX + plotW, plotY + plotH, 1.0F, dev.vy.drt.config.DrtTheme.color("chart.axis", 0x44444870));
		RoundedUiRenderer.line(plotX, plotY + plotH, plotX, plotY + plotH + 2.5F, 1.0F, dev.vy.drt.config.DrtTheme.color("chart.zero", 0x66444870));
		RoundedUiRenderer.line(plotX + plotW - 1, plotY + plotH, plotX + plotW - 1, plotY + plotH + 2.5F, 1.0F, dev.vy.drt.config.DrtTheme.color("chart.zero", 0x66444870));
		if (plotW >= 240) {
			RoundedUiRenderer.line(plotX + plotW / 2.0F, plotY + plotH, plotX + plotW / 2.0F, plotY + plotH + 2.5F, 1.0F, dev.vy.drt.config.DrtTheme.color("chart.zero", 0x66444870));
		}

		drawOverviewXAxis(g, points, geometry, plotX, plotW, plotY + plotH + 4);

		int dataW = Math.max(1, plotW - 9);
		if (contains(plotX, plotY, dataW + 1, plotH, mouseX, mouseY)) {
			if (overviewMetric == OverviewDashboardData.Metric.PROFIT) {
				long cursorTime = points.size() == 1 ? points.get(0).timestamp() : firstTime + Math.round((mouseX - plotX) * timeSpan / (double) dataW);
				GraphHover hover = graphHover(points, cursorTime);
				drawSmoothGraphHover(g, hover, mouseX, plotX, plotY, plotH, dataW, tickMin, tickMax, x, y, w, h);
			} else if (overviewMetric == OverviewDashboardData.Metric.RUNS) {
				long cursorTime = points.size() == 1 ? points.get(0).timestamp() : firstTime + Math.round((mouseX - plotX) * timeSpan / (double) dataW);
				GraphHover hover = stepGraphHover(points, cursorTime);
				drawSmoothGraphHover(g, hover, mouseX, plotX, plotY, plotH, dataW, tickMin, tickMax, x, y, w, h);
			} else {
				int index = nearestGraphIndex(geometry.xs, geometry.count, mouseX);
				OverviewDashboardData.Point point = points.get(index);
				float px = geometry.xs[index];
				float py = geometry.ys[index];
				if (overviewMetric == OverviewDashboardData.Metric.PROFIT_DAY || overviewMetric == OverviewDashboardData.Metric.RUN_PROFIT) drawOverviewBars(g, geometry, index);
				RoundedUiRenderer.line(px, plotY, px, plotY + plotH, 1.0F, dev.vy.drt.config.DrtTheme.color("chart.hoverGuide", 0x557E8DE8));
				RoundedUiRenderer.fill(g, Math.round(px) - 2, Math.round(py) - 2, 4, 4, 2, dev.vy.drt.config.DrtTheme.color("chart.hoverPoint", 0xCC8E9BE6));
				hoverTargets.add(HoverTarget.bounded(plotX, plotY, dataW + 1, plotH, discreteGraphTooltip(point, index), x + 4, y + 3, w - 8, h - 6));
			}
		}
	}

	private void drawOverviewActivity(GuiGraphicsExtractor g, int x, int y, int w, int h, OverviewDashboardData.Snapshot data, int mouseX, int mouseY) {
		int gap = 6;
		int columnW = (w - gap * 2) / 3;
		drawOverviewColumn(g, x, y, columnW, h, "Recent runs");
		drawOverviewColumn(g, x + columnW + gap, y, columnW, h, "Recent chest profits");
		drawOverviewColumn(g, x + (columnW + gap) * 2, y, columnW, h, "Floor performance");

		int step = MainWindowLayout.OVERVIEW_ROW_STEP;
		int rowY = y + MainWindowLayout.OVERVIEW_ACTIVITY_HEADER;
		int rows = Math.max(1, MainWindowLayout.overviewActivityRows(h, 0));
		for (int i = 0; i < Math.min(rows, data.recentRuns().size()); i++) {
			OverviewDashboardData.RecentRun run = data.recentRuns().get(i);
			String left = run.floor() + "  " + RunsPanelLayout.gradeLabel(run.floor(), run.grade());
			String right = run.runTimeMs() == null ? "Time unknown" : formatOptionalTime(run.runTimeMs());
			drawOverviewRow(g, x + 6, rowY + i * step, columnW - 12, left, right, run.runTimeMs() == null ? dev.vy.drt.config.DrtTheme.color("text.muted", TEXT_MUTED) : 0xFF8FB9FF);
			hoverTargets.add(new HoverTarget(x + 2, rowY + i * step - 2, columnW - 4, step, "Completed " + formatOverviewDate(run.timestamp())));
		}
		if (data.recentRuns().isEmpty()) ThemePreviewDraw.text(g, font, "No completed runs recorded", x + 7, rowY, dev.vy.drt.config.DrtTheme.color("text.muted", TEXT_MUTED));

		int chestX = x + columnW + gap;
		for (int i = 0; i < Math.min(rows, data.recentChests().size()); i++) {
			OverviewDashboardData.RecentChest chest = data.recentChests().get(i);
			String left = chest.floor() + "  " + LootHistoryPresentation.compactChestName(chest.chest());
			drawOverviewRow(g, chestX + 6, rowY + i * step, columnW - 12, left, formatSigned(chest.profit()), profitColor(chest.profit()));
			hoverTargets.add(new HoverTarget(chestX + 2, rowY + i * step - 2, columnW - 4, step,
				formatOverviewDate(chest.timestamp()) + " | Exact profit " + formatExactCoins(chest.profit())));
		}
		if (data.recentChests().isEmpty()) ThemePreviewDraw.text(g, font, "No chest history recorded", chestX + 7, rowY, dev.vy.drt.config.DrtTheme.color("text.muted", TEXT_MUTED));

		int floorX = x + (columnW + gap) * 2;
		String timingSummary = "Best " + formatOptionalTime(data.bestRunTimeMs()) + "  Worst " + formatOptionalTime(data.worstRunTimeMs());
		String clippedTiming = font.plainSubstrByWidth(timingSummary, Math.max(8, columnW - 12));
		ThemePreviewDraw.text(g, font, clippedTiming, floorX + 6, rowY, dev.vy.drt.config.DrtTheme.color("text.muted", TEXT_MUTED));
		int floorRowY = rowY + step;
		int floorRows = MainWindowLayout.overviewActivityRows(h, 1);
		for (int i = 0; i < Math.min(floorRows, data.floors().size()); i++) {
			OverviewDashboardData.FloorPerformance floor = data.floors().get(i);
			String average = floor.timedRuns() == 0 ? "Time unknown" : formatOptionalTime(floor.totalTimeMs() / floor.timedRuns());
			drawOverviewRow(g, floorX + 6, floorRowY + i * step, columnW - 12, floor.floor() + "  " + floor.runs() + " runs", formatSigned(floor.profit()), profitColor(floor.profit()));
			hoverTargets.add(new HoverTarget(floorX + 2, floorRowY + i * step - 2, columnW - 4, step,
				floor.floor() + " | Average " + average + " | Profit " + formatExactCoins(floor.profit())));
		}
		if (data.floors().isEmpty() && floorRows > 0) ThemePreviewDraw.text(g, font, "No floor statistics recorded", floorX + 7, floorRowY, dev.vy.drt.config.DrtTheme.color("text.muted", TEXT_MUTED));
		hoverTargets.add(new HoverTarget(floorX, y, columnW, 18, timingSummary));
	}

	private void drawOverviewColumn(GuiGraphicsExtractor g, int x, int y, int w, int h, String title) {
		roundedPanel(g, x, y, w, h, 4, dev.vy.drt.config.DrtTheme.color("panel.background", PANEL_BG), dev.vy.drt.config.DrtTheme.color("panel.border", PANEL_BORDER));
		ThemePreviewDraw.text(g, font, title, x + 7, y + 6, dev.vy.drt.config.DrtTheme.color("text.sectionTitle", 0xFF9CA8FF), true);
		ThemePreviewDraw.fill(g, x + 6, y + 17, x + w - 6, y + 18, dev.vy.drt.config.DrtTheme.color("separator", 0xFF292943));
	}

	private void drawOverviewRow(GuiGraphicsExtractor g, int x, int y, int w, String left, String right, int rightColor) {
		int rightW = font.width(right);
		String clippedLeft = font.plainSubstrByWidth(left, Math.max(8, w - rightW - 7));
		ThemePreviewDraw.text(g, font, clippedLeft, x, y, dev.vy.drt.config.DrtTheme.color("text.secondary", TEXT_SECONDARY));
		ThemePreviewDraw.text(g, font, right, x + w - rightW, y, rightColor);
	}

	private void drawCompactControl(GuiGraphicsExtractor g, int x, int y, int w, int h, String label, boolean active, int mouseX, int mouseY, Runnable action) {
		boolean hovered = contains(x, y, w, h, mouseX, mouseY);
		int fill = active ? dev.vy.drt.config.DrtTheme.color("control.active", 0xFF202A52) : hovered ? dev.vy.drt.config.DrtTheme.color("control.hover", 0xFF1D2139) : dev.vy.drt.config.DrtTheme.color("control.background", 0xFF141425);
		int outline = active ? dev.vy.drt.config.DrtTheme.color("control.borderActive", BORDER_ACTIVE) : hovered ? dev.vy.drt.config.DrtTheme.color("control.borderHover", PANEL_BORDER_HOVER) : dev.vy.drt.config.DrtTheme.color("control.border", PANEL_BORDER);
		roundedPanel(g, x, y, w, h, 4, fill, outline);
		String clipped = font.plainSubstrByWidth(label, Math.max(4, w - 8));
		ThemePreviewDraw.text(g, font, clipped, x + (w - font.width(clipped)) / 2, DrtUiText.centeredTextY(font, y, h), active ? dev.vy.drt.config.DrtTheme.color("control.textActive", TEXT_PRIMARY) : dev.vy.drt.config.DrtTheme.color("control.text", TEXT_SECONDARY));
		clickTargets.add(new ClickTarget(x, y, w, h, 0, action));
	}

	private OverviewDashboardData.Snapshot overviewData() {
		long revision = DrtConfigManager.historyRevision();
		if (overviewSnapshot == null || overviewSnapshotRevision != revision || overviewSnapshotMetric != overviewMetric
			|| overviewSnapshotRange != overviewRange || !Objects.equals(overviewSnapshotFloor, overviewFloor)) {
			List<DungeonRunRecord> history = (themePreview ? previewData.chests : DrtConfigManager.getRunHistory());
			List<DungeonRunCompletionRecord> completions = (themePreview ? previewData.runs : DrtConfigManager.getRunCompletions());
			overviewSnapshot = OverviewDashboardData.build(completions, history, overviewMetric, overviewRange, overviewFloor, System.currentTimeMillis());
			if (overviewFloor != null && !overviewSnapshot.availableFloors().contains(overviewFloor)) {
				overviewFloor = null;
				overviewSnapshot = OverviewDashboardData.build(completions, history, overviewMetric, overviewRange, null, System.currentTimeMillis());
			}
			overviewSnapshotRevision = revision;
			overviewSnapshotMetric = overviewMetric;
			overviewSnapshotRange = overviewRange;
			overviewSnapshotFloor = overviewFloor;
		}
		return overviewSnapshot;
	}

	private void invalidateOverviewData() {
		overviewSnapshot = null;
		overviewGraphGeometry = null;
	}

	private void cycleOverviewMetric(int direction) {
		OverviewDashboardData.Metric[] values = OverviewDashboardData.Metric.values();
		overviewMetric = values[Math.floorMod(overviewMetric.ordinal() + direction, values.length)];
		invalidateOverviewData();
	}

	private void setOverviewRange(OverviewDashboardData.TimeRange range) {
		overviewRange = range;
		invalidateOverviewData();
	}

	private void cycleOverviewFloor(int direction) {
		List<String> floors = new ArrayList<>();
		floors.add(null);
		floors.addAll(overviewData().availableFloors());
		int current = floors.indexOf(overviewFloor);
		overviewFloor = floors.get(Math.floorMod(current + direction, floors.size()));
		invalidateOverviewData();
	}

	private String dataMetricLabel() {
		return overviewMetric.label;
	}

	private String metricControlLabel() {
		return switch (overviewMetric) {
			case PROFIT -> "Cumulative Profit";
			case PROFIT_DAY -> "Profit per Day";
			case RUNS -> "Runs Completed";
			case RUN_TIME -> "Run Times";
			case RUN_PROFIT -> "Profit per Run";
		};
	}

	private String formatOptionalTime(Long millis) {
		return millis == null ? "Unknown" : RunHistoryLinks.formatRunTime(millis);
	}

	private String formatExactDuration(long millis) {
		return RunHistoryLinks.formatRunTime(millis) + " (" + String.format(Locale.ROOT, "%,d ms", millis) + ")";
	}

	private String formatGraphValue(long value) {
		return switch (overviewMetric) {
			case RUNS -> Long.toString(value);
			case RUN_TIME -> RunHistoryLinks.formatRunTime(value);
			case PROFIT, PROFIT_DAY, RUN_PROFIT -> value < 0L ? "-" + formatCoins(Math.abs(value)) : formatCoins(value);
		};
	}

	private String formatOverviewDate(long timestamp) {
		if (timestamp <= 0L) return "Unknown date";
		return OVERVIEW_DATE_FORMAT.format(java.time.Instant.ofEpochMilli(timestamp));
	}

	private String graphTooltip(GraphHover hover) {
		String prefix = hover.recorded ? "" : "Approx. ";
		return switch (overviewMetric) {
			case PROFIT -> formatGraphHoverDate(hover.timestamp) + " | " + prefix + "cumulative " + formatCompactGraphProfit(hover.value);
			case PROFIT_DAY -> formatGraphHoverDate(hover.timestamp) + " | " + prefix + "daily profit " + formatCompactGraphProfit(hover.value);
			case RUN_PROFIT -> formatGraphHoverDate(hover.timestamp) + " | " + prefix + "run profit " + formatCompactGraphProfit(hover.value);
			case RUNS -> formatGraphHoverDate(hover.timestamp) + " | " + prefix + compactDecimal(hover.value) + " runs";
			case RUN_TIME -> formatGraphHoverDate(hover.timestamp) + " | " + prefix + RunHistoryLinks.formatRunTime(Math.round(hover.value));
		};
	}

	private String discreteGraphTooltip(OverviewDashboardData.Point point, int index) {
		return switch (overviewMetric) {
			case PROFIT_DAY -> formatGraphDay(point.timestamp()) + " | Daily profit " + formatCompactGraphProfit(point.value());
			case RUN_TIME -> "Run #" + (index + 1) + " | " + RunHistoryLinks.formatRunTime(point.value()) + " | " + formatOverviewDate(point.timestamp());
			case RUN_PROFIT -> "Run #" + (index + 1) + " | " + formatCompactGraphProfit(point.value()) + " | " + formatOverviewDate(point.timestamp());
			default -> point.tooltip();
		};
	}

	private void drawSmoothGraphHover(
		GuiGraphicsExtractor g, GraphHover hover, int mouseX, int plotX, int plotY, int plotH, int dataW,
		long tickMin, long tickMax, int panelX, int panelY, int panelW, int panelH
	) {
		float hoverY = plotY + plotH - 1.0F - (float) ((hover.value - tickMin) * (plotH - 1) / (double) (tickMax - tickMin));
		RoundedUiRenderer.line(mouseX + 0.5F, plotY, mouseX + 0.5F, plotY + plotH, 1.0F, dev.vy.drt.config.DrtTheme.color("chart.hoverGuide", 0x667E8DE8));
		RoundedUiRenderer.fill(g, mouseX - 2, Math.round(hoverY) - 2, 4, 4, 2, dev.vy.drt.config.DrtTheme.color("chart.hoverPoint", 0xCC8E9BE6));
		hoverTargets.add(HoverTarget.bounded(plotX, plotY, dataW + 1, plotH, graphTooltip(hover), panelX + 4, panelY + 3, panelW - 8, panelH - 6));
	}

	private GraphHover stepGraphHover(List<OverviewDashboardData.Point> points, long timestamp) {
		OverviewDashboardData.Point selected = points.get(0);
		for (OverviewDashboardData.Point point : points) {
			if (point.timestamp() > timestamp) break;
			selected = point;
		}
		return new GraphHover(timestamp, selected.value(), true);
	}

	private int nearestGraphIndex(float[] xs, int count, int mouseX) {
		int best = 0;
		float bestDistance = Float.MAX_VALUE;
		for (int i = 0; i < count; i++) {
			float distance = Math.abs(xs[i] - mouseX);
			if (distance < bestDistance) {
				best = i;
				bestDistance = distance;
			}
		}
		return best;
	}

	private void drawOverviewBars(GuiGraphicsExtractor g, GraphGeometry geometry, int highlightedIndex) {
		float spacing = geometry.count <= 1 ? 18.0F : Float.MAX_VALUE;
		for (int i = 1; i < geometry.count; i++) spacing = Math.min(spacing, Math.max(1.0F, geometry.xs[i] - geometry.xs[i - 1]));
		int barW = clamp((int) Math.floor(spacing * 0.62F), 3, 18);
		for (int i = 0; i < geometry.count; i++) {
			float top = Math.min(geometry.ys[i], geometry.baseline);
			float bottom = Math.max(geometry.ys[i], geometry.baseline);
			int barX = Math.round(geometry.xs[i] - barW / 2.0F);
			int barY = Math.round(top);
			int barH = Math.max(1, Math.round(bottom - top));
			int color = i == highlightedIndex ? dev.vy.drt.config.DrtTheme.color("chart.barHover", 0xDD929FEA) : geometry.ys[i] <= geometry.baseline ? dev.vy.drt.config.DrtTheme.color("chart.barPositive", 0xAA7483D1) : dev.vy.drt.config.DrtTheme.color("chart.barNegative", 0xAAA85F76);
			RoundedUiRenderer.fill(g, barX, barY, barW, barH, 2, color);
		}
	}

	private void drawOverviewXAxis(GuiGraphicsExtractor g, List<OverviewDashboardData.Point> points, GraphGeometry geometry, int plotX, int plotW, int y) {
		if (overviewMetric == OverviewDashboardData.Metric.RUN_TIME || overviewMetric == OverviewDashboardData.Metric.RUN_PROFIT) {
			String first = "#1";
			String middle = "#" + ((points.size() + 1) / 2);
			String last = "#" + points.size();
			drawAxisLabels(g, first, middle, last, plotX, plotW, y);
			return;
		}
		long firstTime = points.get(0).timestamp();
		long lastTime = points.get(points.size() - 1).timestamp();
		long span = Math.max(1L, lastTime - firstTime);
		long middleTime = overviewMetric == OverviewDashboardData.Metric.PROFIT_DAY
			? points.get(points.size() / 2).timestamp()
			: firstTime + span / 2L;
		drawAxisLabels(g, formatOverviewXAxisDate(firstTime, span), formatOverviewXAxisDate(middleTime, span),
			formatOverviewXAxisDate(lastTime, span), plotX, plotW, y);
	}

	private void drawAxisLabels(GuiGraphicsExtractor g, String first, String middle, String last, int x, int w, int y) {
		ThemePreviewDraw.text(g, font, first, x, y, dev.vy.drt.config.DrtTheme.color("text.muted", TEXT_MUTED));
		if (last.equals(first)) return;
		int lastX = x + w - font.width(last);
		if (lastX > x + font.width(first) + 12) ThemePreviewDraw.text(g, font, last, lastX, y, dev.vy.drt.config.DrtTheme.color("text.muted", TEXT_MUTED));
		if (w < 240 || middle.equals(first) || middle.equals(last)) return;
		int middleX = x + (w - font.width(middle)) / 2;
		if (middleX > x + font.width(first) + 8 && middleX + font.width(middle) + 8 < lastX) ThemePreviewDraw.text(g, font, middle, middleX, y, dev.vy.drt.config.DrtTheme.color("text.muted", TEXT_MUTED));
	}

	private String formatGraphHoverDate(long timestamp) {
		if (timestamp <= 0L) return "Unknown date";
		return java.time.format.DateTimeFormatter.ofPattern("d MMM yyyy, HH:mm:ss", Locale.ROOT)
			.withZone(java.time.ZoneId.systemDefault())
			.format(java.time.Instant.ofEpochMilli(timestamp));
	}

	private String formatGraphDay(long timestamp) {
		if (timestamp <= 0L) return "Unknown date";
		return java.time.format.DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ROOT)
			.withZone(java.time.ZoneId.systemDefault())
			.format(java.time.Instant.ofEpochMilli(timestamp));
	}

	private String formatCompactGraphProfit(double value) {
		double abs = Math.abs(value);
		String sign = value >= 0L ? "+" : "-";
		if (abs >= 1_000_000_000L) return sign + compactDecimal(abs / 1_000_000_000.0D) + "B";
		if (abs >= 1_000_000L) return sign + compactDecimal(abs / 1_000_000.0D) + "M";
		if (abs >= 1_000L) return sign + compactDecimal(abs / 1_000.0D) + "k";
		return sign + compactDecimal(abs);
	}

	private String compactDecimal(double value) {
		return value == Math.rint(value) ? String.format(Locale.ROOT, "%.0f", value) : String.format(Locale.ROOT, "%.1f", value);
	}

	private GraphHover graphHover(List<OverviewDashboardData.Point> points, long timestamp) {
		if (points.size() == 1) {
			OverviewDashboardData.Point point = points.get(0);
			return new GraphHover(point.timestamp(), point.value(), true);
		}
		int low = 0;
		int high = points.size() - 1;
		while (low < high) {
			int mid = (low + high) >>> 1;
			if (points.get(mid).timestamp() < timestamp) low = mid + 1;
			else high = mid;
		}
		OverviewDashboardData.Point right = points.get(low);
		if (low == 0 || right.timestamp() == timestamp) {
			return new GraphHover(right.timestamp(), right.value(), true);
		}
		OverviewDashboardData.Point left = points.get(low - 1);
		long segmentDuration = right.timestamp() - left.timestamp();
		if (segmentDuration <= 0L) return new GraphHover(timestamp, right.value(), false);
		double fraction = (timestamp - left.timestamp()) / (double) segmentDuration;
		double value = left.value() + (right.value() - left.value()) * fraction;
		return new GraphHover(timestamp, value, false);
	}

	private List<Long> calculateNiceYTicks(long minValue, long maxValue) {
		if (maxValue <= minValue) maxValue = minValue + 1L;
		if (overviewMetric == OverviewDashboardData.Metric.RUN_TIME) {
			long range = maxValue - minValue;
			long[] niceSeconds = { 1, 2, 5, 10, 15, 30, 60, 120, 180, 300, 600, 900, 1800, 3600 };
			long targetMs = Math.max(1000L, (long) (range / 3.5));
			long bestStep = 1000L;
			for (long s : niceSeconds) {
				long ms = s * 1000L;
				if (ms >= targetMs) {
					bestStep = ms;
					break;
				}
				bestStep = ms;
			}
			long tickMin = (long) Math.floor((double) minValue / bestStep) * bestStep;
			long tickMax = (long) Math.ceil((double) maxValue / bestStep) * bestStep;
			if (tickMax == tickMin) tickMax += bestStep;
			List<Long> ticks = new ArrayList<>();
			for (long t = tickMin; t <= tickMax; t += bestStep) {
				ticks.add(t);
				if (ticks.size() > 10) break;
			}
			return ticks;
		}
		long range = maxValue - minValue;
		double rawStep = Math.max(1.0, range / 3.5);
		double exponent = Math.floor(Math.log10(rawStep));
		double power = Math.pow(10.0, exponent);
		double fraction = rawStep / power;
		double niceFraction;
		if (fraction <= 1.5) niceFraction = 1.0;
		else if (fraction <= 3.0) niceFraction = 2.0;
		else if (fraction <= 7.0) niceFraction = 5.0;
		else niceFraction = 10.0;
		long step = Math.max(1L, (long) (niceFraction * power));

		long tickMin = (long) Math.floor((double) minValue / step) * step;
		long tickMax = (long) Math.ceil((double) maxValue / step) * step;
		if (tickMax == tickMin) tickMax += step;

		List<Long> ticks = new ArrayList<>();
		for (long t = tickMin; t <= tickMax; t += step) {
			ticks.add(t);
			if (ticks.size() > 10) break;
		}
		return ticks;
	}

	private String formatGraphTick(long value) {
		return switch (overviewMetric) {
			case RUNS -> Long.toString(value);
			case RUN_TIME -> RunHistoryLinks.formatRunTime(value);
			case PROFIT, PROFIT_DAY, RUN_PROFIT -> formatCoinsTick(value);
		};
	}

	private String formatCoinsTick(long value) {
		if (value == 0L) return "0";
		long abs = Math.abs(value);
		String sign = value < 0 ? "-" : "";
		if (abs >= 1_000_000_000L) {
			double b = abs / 1_000_000_000.0;
			return sign + (b == (long) b ? String.format(Locale.ROOT, "%dB", (long) b) : String.format(Locale.ROOT, "%.1fB", b));
		}
		if (abs >= 1_000_000L) {
			double m = abs / 1_000_000.0;
			return sign + (m == (long) m ? String.format(Locale.ROOT, "%dM", (long) m) : String.format(Locale.ROOT, "%.1fM", m));
		}
		if (abs >= 1_000L) {
			double k = abs / 1_000.0;
			return sign + (k == (long) k ? String.format(Locale.ROOT, "%dk", (long) k) : String.format(Locale.ROOT, "%.1fk", k));
		}
		return sign + abs;
	}

	private String formatOverviewXAxisDate(long timestamp, long timeSpan) {
		if (timestamp <= 0L) return "";
		java.time.Instant instant = java.time.Instant.ofEpochMilli(timestamp);
		java.time.ZoneId zone = java.time.ZoneId.systemDefault();
		if (timeSpan <= 86_400_000L) {
			return java.time.format.DateTimeFormatter.ofPattern("HH:mm", Locale.ROOT).withZone(zone).format(instant);
		} else if (timeSpan <= 120L * 86_400_000L) {
			return java.time.format.DateTimeFormatter.ofPattern("d MMM", Locale.ROOT).withZone(zone).format(instant);
		} else {
			return java.time.format.DateTimeFormatter.ofPattern("MMM yyyy", Locale.ROOT).withZone(zone).format(instant);
		}
	}

	private GraphGeometry buildOverviewGraphGeometry(
		OverviewDashboardData.Snapshot data,
		List<OverviewDashboardData.Point> points,
		int plotX,
		int plotY,
		int plotW,
		int plotH,
		long minValue,
		long maxValue,
		long firstTime,
		long timeSpan,
		List<Long> yTicks
	) {
		List<OverviewDashboardData.Point> rendered = points;
		float[] xs = new float[rendered.size()];
		float[] ys = new float[rendered.size()];
		int dataW = Math.max(1, plotW - 9);
		boolean indexedXAxis = overviewMetric == OverviewDashboardData.Metric.RUN_TIME || overviewMetric == OverviewDashboardData.Metric.RUN_PROFIT;
		for (int i = 0; i < rendered.size(); i++) {
			OverviewDashboardData.Point point = rendered.get(i);
			if (indexedXAxis || (overviewMetric == OverviewDashboardData.Metric.PROFIT_DAY && rendered.size() == 1)) {
				xs[i] = rendered.size() == 1 ? plotX + dataW / 2.0F : plotX + i * dataW / (float) (rendered.size() - 1);
			} else {
				xs[i] = plotX + (float) ((point.timestamp() - firstTime) * dataW / (double) timeSpan);
			}
			ys[i] = plotY + plotH - 1.0F - (float) ((point.value() - minValue) * (plotH - 1) / (double) (maxValue - minValue));
		}
		float rawBaseline = plotY + plotH - 1.0F - (float) ((0L - minValue) * (plotH - 1) / (double) (maxValue - minValue));
		float baseline = clamp(rawBaseline, (float) plotY, (float) (plotY + plotH - 1));
		overviewGraphGeometry = new GraphGeometry(data, plotX, plotY, plotW, plotH, xs, ys, rendered.size(), baseline, false, minValue, maxValue, firstTime, timeSpan, yTicks);
		return overviewGraphGeometry;
	}

	// ── Runs Tab ──────────────────────────────────────────────────────────────

	private void drawRunsTab(GuiGraphicsExtractor g, int mouseX, int mouseY, int topY) {
		int contentX = ox + MARGIN;
		int contentW = winW - MARGIN * 2;
		int contentH = winH - (topY - oy) - MARGIN;
		RunsDashboardData.Snapshot data = runsData();
		int titleY = DrtUiText.centeredTextY(font, topY, 22);
		ThemePreviewDraw.text(g, font, "Runs", contentX, titleY, dev.vy.drt.config.DrtTheme.color("text.pageTitle", TEXT_PRIMARY), true);

		runsSearchW = clamp(contentW / 5, 92, 150);
		runsSearchH = 18;
		runsSearchX = contentX + contentW - runsSearchW;
		runsSearchY = topY + 2;
		boolean searchHover = contains(runsSearchX, runsSearchY, runsSearchW, runsSearchH, mouseX, mouseY);
		roundedPanel(g, runsSearchX, runsSearchY, runsSearchW, runsSearchH, 4, dev.vy.drt.config.DrtTheme.color("input.background", 0xFF10101F),
			runsSearchFocused ? dev.vy.drt.config.DrtTheme.color("control.borderActive", BORDER_ACTIVE) : searchHover ? dev.vy.drt.config.DrtTheme.color("control.borderHover", PANEL_BORDER_HOVER) : dev.vy.drt.config.DrtTheme.color("control.border", PANEL_BORDER));
		String searchText = runsSearch.isBlank() ? "Search runs..." : runsSearch;
		ThemePreviewDraw.text(g, font, font.plainSubstrByWidth(searchText, runsSearchW - 12), runsSearchX + 6,
			DrtUiText.centeredTextY(font, runsSearchY, runsSearchH), runsSearch.isBlank() ? dev.vy.drt.config.DrtTheme.color("input.placeholder", TEXT_MUTED) : dev.vy.drt.config.DrtTheme.color("input.text", TEXT_PRIMARY));
		clickTargets.add(new ClickTarget(runsSearchX, runsSearchY, runsSearchW, runsSearchH, 0, () -> runsSearchFocused = true));

		int sortW = 78;
		int sortX = runsSearchX - sortW - 5;
		drawCustomButton(g, sortX, runsSearchY, sortW, runsSearchH, runsSortLabel(), false, mouseX, mouseY, () -> {
			runsSort = RunsDashboardData.Sort.values()[(runsSort.ordinal() + 1) % RunsDashboardData.Sort.values().length];
			invalidateRunsData();
		});

		List<String> floorLabels = new ArrayList<>();
		floorLabels.add("All");
		floorLabels.addAll(data.availableFloors());
		int[] floorWidths = floorLabels.stream().mapToInt(label -> font.width(label) + 12).toArray();
		int filterRows = Math.max(1, RunsPanelLayout.wrappedRows(contentW, 3, floorWidths));
		int floorX = contentX;
		int floorTop = 23;
		int floorStep = 20;
		int floorY = topY + floorTop;
		for (String floor : floorLabels) {
			int buttonW = font.width(floor) + 12;
			if (floorX > contentX && floorX + buttonW > contentX + contentW) {
				floorX = contentX;
				floorY += floorStep;
			}
			String value = floor.equals("All") ? "" : floor;
			drawRunsFloorButton(g, floorX, floorY, floor, runsFloor.equals(value), mouseX, mouseY, value);
			floorX += buttonW + 3;
		}
		int controlsH = floorTop + filterRows * floorStep - 2;

		int metricsY = topY + controlsH + 4;
		int metricsH = METRIC_CARD_H;
		drawRunsMetrics(g, data.metrics(), contentX, metricsY, contentW, metricsH);

		int mainY = metricsY + metricsH + 5;
		int mainH = Math.max(80, contentH - (mainY - topY));
		runsListX = contentX;
		runsListY = mainY;
		runsListW = clamp((int) (contentW * 0.38F), 220, 330);
		runsListH = mainH;
		roundedPanel(g, runsListX, runsListY, runsListW, runsListH, 4, dev.vy.drt.config.DrtTheme.color("panel.background", PANEL_BG), dev.vy.drt.config.DrtTheme.color("panel.border", PANEL_BORDER));
		drawRunsTimeline(g, data, mouseX, mouseY);

		int detailX = runsListX + runsListW + 7;
		int detailW = contentX + contentW - detailX;
		drawSelectedRunAnalysis(g, selectedRun(data), data, detailX, mainY, detailW, mainH, mouseX, mouseY);
	}

	private RunsDashboardData.Snapshot runsData() {
		long revision = DrtConfigManager.historyRevision();
		if (runsSnapshot == null || runsSnapshotRevision != revision) {
			runsSnapshot = RunsDashboardData.build((themePreview ? previewData.runs : DrtConfigManager.getRunCompletions()), (themePreview ? previewData.chests : DrtConfigManager.getRunHistory()),
				runsFloor, runsSearch, runsSort, themePreview ? null : DrtConfigManager.getCurrentAccountUuid(), themePreview ? "Theme preview" : DrtAccount.currentName());
			runsSnapshotRevision = revision;
			if (!selectedRunCompletionId.isBlank() && runsSnapshot.entries().stream().noneMatch(entry -> runKey(entry).equals(selectedRunCompletionId))) {
				selectedRunCompletionId = "";
			}
		}
		return runsSnapshot;
	}

	private void invalidateRunsData() {
		runsSnapshotRevision = -1L;
		runsListScroll = 0;
	}

	private String runsSortLabel() {
		return switch (runsSort) {
			case NEWEST -> "Newest";
			case OLDEST -> "Oldest";
			case FASTEST -> "Fastest";
			case HIGHEST_SCORE -> "Score";
		};
	}

	private void drawRunsFloorButton(GuiGraphicsExtractor g, int x, int y, String label, boolean active,
		int mouseX, int mouseY, String value) {
		int w = font.width(label) + 12;
		boolean hovered = contains(x, y, w, 18, mouseX, mouseY);
		roundedPanel(g, x, y, w, 18, 4, active ? dev.vy.drt.config.DrtTheme.color("control.active", 0xFF202A52) : hovered ? dev.vy.drt.config.DrtTheme.color("control.hover", 0xFF1D2139) : dev.vy.drt.config.DrtTheme.color("control.background", 0xFF141425),
			active ? dev.vy.drt.config.DrtTheme.color("control.borderActive", BORDER_ACTIVE) : hovered ? dev.vy.drt.config.DrtTheme.color("control.borderHover", PANEL_BORDER_HOVER) : dev.vy.drt.config.DrtTheme.color("control.border", PANEL_BORDER));
		int textColor = label.equals("All") ? (active ? dev.vy.drt.config.DrtTheme.color("control.textActive", TEXT_PRIMARY) : hovered ? dev.vy.drt.config.DrtTheme.color("control.textHover", 0xFFC2C8E0) : dev.vy.drt.config.DrtTheme.color("control.text", TEXT_SECONDARY))
			: tabTextColor(label, active, hovered);
		ThemePreviewDraw.text(g, font, label, x + (w - font.width(label)) / 2, DrtUiText.centeredTextY(font, y, 18),
			textColor);
		clickTargets.add(new ClickTarget(x, y, w, 18, 0, () -> {
			runsFloor = value;
			selectedRunCompletionId = "";
			invalidateRunsData();
		}));
	}

	private void drawRunsMetrics(GuiGraphicsExtractor g, RunsDashboardData.Metrics metrics, int x, int y, int w, int h) {
		String[] labels = {"Recorded", "Average time", "Fastest", "S+ rate"};
		String[] values = {
			String.valueOf(metrics.totalRuns()),
			metrics.timedRuns() == 0 ? "Unknown" : RunHistoryLinks.formatRunTime(metrics.averageDurationMs()),
			metrics.timedRuns() == 0 ? "Unknown" : RunHistoryLinks.formatRunTime(metrics.fastestDurationMs()),
			metrics.gradedRuns() == 0 ? "Unknown" : String.format(Locale.ROOT, "%d (%.1f%%)", metrics.sPlusRuns(), metrics.sPlusPercent())
		};
		int gap = 4;
		int cardW = (w - gap * 3) / 4;
		for (int i = 0; i < 4; i++) {
			int cx = x + i * (cardW + gap);
			roundedPanel(g, cx, y, i == 3 ? x + w - cx : cardW, h, 4, dev.vy.drt.config.DrtTheme.color("panel.alternate", PANEL_BG_ALT), dev.vy.drt.config.DrtTheme.color("panel.border", PANEL_BORDER));
			ThemePreviewDraw.text(g, font, labels[i], cx + 7, y + 5, dev.vy.drt.config.DrtTheme.color("text.metricLabel", TEXT_MUTED));
			int valueColor = i == 2
				? RunTimeColorScale.color(runsFloor, metrics.timedRuns() == 0 ? null : metrics.fastestDurationMs(), dev.vy.drt.config.DrtTheme.color("text.primary", TEXT_PRIMARY))
				: i == 3 ? dev.vy.drt.config.DrtTheme.color("text.accent", 0xFF9CA8FF) : dev.vy.drt.config.DrtTheme.color("text.primary", TEXT_PRIMARY);
			ThemePreviewDraw.text(g, font, font.plainSubstrByWidth(values[i], cardW - 12), cx + 7, y + 17, valueColor, true);
		}
	}

	private void drawRunsTimeline(GuiGraphicsExtractor g, RunsDashboardData.Snapshot data, int mouseX, int mouseY) {
		int headerH = 20;
		ThemePreviewDraw.text(g, font, "Runs (" + data.entries().size() + ")", runsListX + 8, DrtUiText.centeredTextY(font, runsListY, headerH), dev.vy.drt.config.DrtTheme.color("text.tableHeading", TEXT_MUTED));
		RoundedUiRenderer.line(runsListX + 1, runsListY + headerH, runsListX + runsListW - 1, runsListY + headerH, 1, dev.vy.drt.config.DrtTheme.color("separator", PANEL_BORDER));
		int bodyY = runsListY + headerH + 1;
		int bodyH = runsListH - headerH - 2;
		int rowH = RunsPanelLayout.RUN_ROW_HEIGHT;
		int totalH = data.entries().size() * rowH;
		runsListScroll = clamp(runsListScroll, 0, Math.max(0, totalH - bodyH));
		if (data.entries().isEmpty()) {
			String empty = runsSearch.isBlank() ? "No recorded completions" : "No matching runs";
			ThemePreviewDraw.text(g, font, empty, runsListX + (runsListW - font.width(empty)) / 2, bodyY + 16, dev.vy.drt.config.DrtTheme.color("text.muted", TEXT_MUTED));
			return;
		}
		int start = Math.max(0, runsListScroll / rowH);
		int end = Math.min(data.entries().size(), start + bodyH / rowH + 2);
		g.enableScissor(runsListX + 1, bodyY, runsListX + runsListW - 1, bodyY + bodyH);
		try {
			for (int i = start; i < end; i++) {
				RunsDashboardData.Entry entry = data.entries().get(i);
				int ry = bodyY + i * rowH - runsListScroll;
				int visibleY = Math.max(ry, bodyY);
				int visibleBottom = Math.min(ry + rowH, bodyY + bodyH);
				if (visibleBottom <= visibleY) continue;
				boolean selected = runKey(entry).equals(selectedRunCompletionId) || selectedRunCompletionId.isBlank() && i == 0;
				boolean hovered = contains(runsListX + 1, visibleY, runsListW - 2, visibleBottom - visibleY, mouseX, mouseY);
				boolean fullyVisible = ry >= bodyY && ry + rowH <= bodyY + bodyH;
				if (selected || hovered) {
					int fill = selected ? dev.vy.drt.config.DrtTheme.color("row.selected", 0xFF202542) : dev.vy.drt.config.DrtTheme.color("row.hover", 0xFF1A1A2D);
					int outline = selected ? dev.vy.drt.config.DrtTheme.color("control.borderActive", BORDER_ACTIVE) : dev.vy.drt.config.DrtTheme.color("control.borderHover", PANEL_BORDER_HOVER);
					if (fullyVisible) roundedPanel(g, runsListX + 3, ry + 2, runsListW - 7, rowH - 4, 4, fill, outline);
					else ThemePreviewDraw.fill(g, runsListX + 3, visibleY, runsListX + runsListW - 4, visibleBottom, fill);
				}
				DungeonRunCompletionRecord completion = entry.completion();
				RunHistoryLinks.RunTime time = RunHistoryLinks.runTime(completion);
				String duration = time == null ? "Unknown" : RunHistoryLinks.formatRunTime(time.millis());
				String floorName = floorDisplayName(completion.floor);
				int durationX = runsListX + runsListW - font.width(duration) - 9;
				boolean kuudraRun = RunsPanelLayout.isKuudra(completion.floor);
				String gradeLabel = RunsPanelLayout.gradeLabel(completion.floor, completion.grade);
				int gradeX = durationX - font.width(gradeLabel) - 10;
				int titleY = ry + 5;
				int detailY = ry + 17;
				ThemePreviewDraw.text(g, font, font.plainSubstrByWidth(floorName, Math.max(24, gradeX - runsListX - 14)), runsListX + 9, titleY,
					floorTypeColor(completion.floor), true);
				ThemePreviewDraw.text(g, font, gradeLabel, gradeX, titleY,
					kuudraRun ? dev.vy.drt.config.DrtTheme.color("text.secondary", TEXT_SECONDARY) : gradeColor(completion.grade), !kuudraRun);
				ThemePreviewDraw.text(g, font, duration, durationX, titleY,
					RunTimeColorScale.color(completion.floor, time == null ? null : time.millis(), dev.vy.drt.config.DrtTheme.color("text.primary", TEXT_PRIMARY)));
				RunScore.Resolved resolvedScore = RunScore.resolve(completion);
				String date = formatOverviewDate(completion.completedAtEpochMillis);
				ThemePreviewDraw.text(g, font, font.plainSubstrByWidth(date, runsListW / 2 - 14), runsListX + 9, detailY, dev.vy.drt.config.DrtTheme.color("text.metadata", TEXT_MUTED));
				String chest = kuudraRun ? "" : "Score " + resolvedScore.display();
				if (entry.primaryPurchase() != null) chest += (chest.isEmpty() ? "" : "  |  ") + LootHistoryPresentation.compactChestName(entry.primaryPurchase().chestTitle)
					+ " " + (entry.primaryPurchase().hasKnownProfit() ? formatSigned(entry.realisedProfit()) : "Unknown");
				int chestW = runsListW / 2 - 12;
				String shownChest = font.plainSubstrByWidth(chest, chestW);
				ThemePreviewDraw.text(g, font, shownChest, runsListX + runsListW - font.width(shownChest) - 9, detailY,
					entry.primaryPurchase() == null || !entry.primaryPurchase().hasKnownProfit()
						? dev.vy.drt.config.DrtTheme.color("text.secondary", TEXT_SECONDARY) : profitColor(entry.realisedProfit()));
				int separatorY = ry + rowH - 1;
				if (separatorY >= bodyY && separatorY < bodyY + bodyH) {
					RoundedUiRenderer.line(runsListX + 7, separatorY, runsListX + runsListW - 7, separatorY, 1, dev.vy.drt.config.DrtTheme.color("separator", 0x242D2D50));
				}
				int index = i;
				addClippedClickTarget(runsListX + 1, ry, runsListW - 2, rowH, bodyY, bodyH, 0,
					() -> selectedRunCompletionId = runKey(data.entries().get(index)));
				if (resolvedScore.inferred()) addClippedHoverTarget(runsListX + 1, ry, runsListW - 2, rowH,
					bodyY, bodyH, "* Estimated from grade");
			}
			if (totalH > bodyH) drawScrollbar(g, runsListX + runsListW - 6, bodyY, 4, bodyH, runsListScroll, totalH, mouseX, mouseY);
		} finally {
			g.disableScissor();
		}
	}

	private RunsDashboardData.Entry selectedRun(RunsDashboardData.Snapshot data) {
		if (data.entries().isEmpty()) return null;
		for (RunsDashboardData.Entry entry : data.entries()) if (runKey(entry).equals(selectedRunCompletionId)) return entry;
		RunsDashboardData.Entry first = data.entries().getFirst();
		selectedRunCompletionId = runKey(first);
		return first;
	}

	private String runKey(RunsDashboardData.Entry entry) {
		if (entry == null || entry.completion() == null) return "";
		String id = entry.completion().completionId;
		return id == null || id.isBlank() ? entry.completion().runSessionId : id;
	}

	private int gradeColor(String grade) {
		return switch (grade == null ? "" : grade.toUpperCase(Locale.ROOT)) {
			case "S+" -> dev.vy.drt.config.DrtTheme.color("grade.sPlus", 0xFFC79BE8);
			case "S" -> dev.vy.drt.config.DrtTheme.color("grade.s", 0xFF78D59B);
			case "A" -> dev.vy.drt.config.DrtTheme.color("grade.a", 0xFF9CA8FF);
			case "B" -> dev.vy.drt.config.DrtTheme.color("grade.b", 0xFFE5C07B);
			case "C", "D" -> dev.vy.drt.config.DrtTheme.color("grade.cOrD", 0xFFB09A78);
			default -> dev.vy.drt.config.DrtTheme.color("text.muted", TEXT_MUTED);
		};
	}

	private String floorDisplayName(String floor) {
		String value = floor == null ? "" : floor.trim().toUpperCase(Locale.ROOT);
		if (value.matches("M[1-7]")) return "Master Mode Floor " + value.substring(1);
		if (value.matches("F[1-7]")) return "Floor " + value.substring(1);
		if (value.matches("K[1-5]")) return "Kuudra Tier " + value.substring(1);
		return value.isBlank() ? "Unknown Floor" : value;
	}

	private int floorTypeColor(String floor) {
		String value = floor == null ? "" : floor.trim().toUpperCase(Locale.ROOT);
		if (value.matches("M[1-7]")) return dev.vy.drt.config.DrtTheme.color("floor.master", 0xFFFF7B7B);
		if (value.matches("F[1-7]")) return dev.vy.drt.config.DrtTheme.color("floor.catacombs", 0xFF7BB8FF);
		if (value.matches("K[1-5]")) return dev.vy.drt.config.DrtTheme.color("floor.kuudra", 0xFFFF8A50);
		return dev.vy.drt.config.DrtTheme.color("text.secondary", 0xFF9CA8FF);
	}

	private void drawSelectedRunAnalysis(GuiGraphicsExtractor g, RunsDashboardData.Entry selected, RunsDashboardData.Snapshot data,
		int x, int y, int w, int h, int mouseX, int mouseY) {
		roundedPanel(g, x, y, w, h, 4, dev.vy.drt.config.DrtTheme.color("panel.background", PANEL_BG), dev.vy.drt.config.DrtTheme.color("panel.border", PANEL_BORDER));
		if (selected == null) {
			ThemePreviewDraw.text(g, font, "Select a recorded run", x + 12, y + 12, dev.vy.drt.config.DrtTheme.color("text.muted", TEXT_MUTED));
			return;
		}
		drawUnifiedRunPanel(g, selected, data, x, y, w, h, mouseX, mouseY);
	}

	private void drawUnifiedRunPanel(GuiGraphicsExtractor g, RunsDashboardData.Entry entry, RunsDashboardData.Snapshot data,
		int x, int y, int w, int h, int mouseX, int mouseY) {
		DungeonRunCompletionRecord completion = entry.completion();
		int pad = 10;
		int contentX = x + pad;
		int contentW = w - pad * 2;
		RunScore.Resolved score = RunScore.resolve(completion);
		RunHistoryLinks.RunTime runTime = RunHistoryLinks.runTime(completion);
		String duration = runTime == null ? "Unknown" : RunHistoryLinks.formatRunTime(runTime.millis());
		DungeonRunRecord purchase = entry.primaryPurchase();
		boolean canViewLoot = purchase != null || entry.offerCount() > 0;
		int viewW = 82;
		int recoverW = 78;
		int viewX = x + w - pad - viewW;

		String floorName = floorDisplayName(completion.floor);
		boolean kuudraRun = RunsPanelLayout.isKuudra(completion.floor);
		String grade = RunsPanelLayout.gradeLabel(completion.floor, completion.grade);
		String scoreText = RunsPanelLayout.showsScore(completion.floor) ? "(Score: " + score.display() + ")" : "";
		int actionLeft = viewX - recoverW - 5;
		int headerFloorW = Math.max(10, actionLeft - contentX - font.width(grade) - font.width(scoreText) - 20);
		String shownFloor = font.plainSubstrByWidth(floorName, headerFloorW);
		int buttonY = y + 5;
		int headingY = buttonY + 3;
		int metaY = y + 25;
		int separatorY = y + 38;
		ThemePreviewDraw.text(g, font, shownFloor, contentX, headingY, floorTypeColor(completion.floor), true);
		int gradeX = contentX + font.width(shownFloor) + 6;
		ThemePreviewDraw.text(g, font, grade, gradeX, headingY,
			kuudraRun ? dev.vy.drt.config.DrtTheme.color("text.secondary", TEXT_SECONDARY) : gradeColor(grade), !kuudraRun);
		int scoreX = gradeX + font.width(grade) + 5;
		ThemePreviewDraw.text(g, font, scoreText, scoreX, headingY, dev.vy.drt.config.DrtTheme.color("text.secondary", TEXT_SECONDARY));
		if (canViewLoot) drawCustomButton(g, viewX, buttonY, viewW, 18, "View in Loot", false, mouseX, mouseY, () -> navigateRunToLoot(entry));
		else ThemePreviewDraw.text(g, font, "No loot", viewX + 22, buttonY + 4, dev.vy.drt.config.DrtTheme.color("text.muted", TEXT_MUTED));
		drawCustomButton(g, viewX - recoverW - 5, buttonY, recoverW, 18, "Recover API", false, mouseX, mouseY, this::recoverRecentCroesus);
		int metaW = recoveryStatus.isBlank() ? contentW : contentW * 3 / 5;
		String metaDate = formatOverviewDate(completion.completedAtEpochMillis);
		int durationW = font.width(duration);
		String shownDate = font.plainSubstrByWidth(metaDate, Math.max(1, metaW - durationW - font.width("   ")));
		ThemePreviewDraw.text(g, font, shownDate, contentX, metaY, dev.vy.drt.config.DrtTheme.color("text.secondary", TEXT_SECONDARY));
		int durationMetaX = contentX + font.width(shownDate) + font.width("   ");
		ThemePreviewDraw.text(g, font, duration, durationMetaX, metaY,
			RunTimeColorScale.color(completion.floor, runTime == null ? null : runTime.millis(), dev.vy.drt.config.DrtTheme.color("text.secondary", TEXT_SECONDARY)));
		if (!kuudraRun && score.inferred()) hoverTargets.add(new HoverTarget(scoreX, buttonY, font.width(scoreText), 16, "* Estimated from grade"));
		if (!recoveryStatus.isBlank()) {
			String shownStatus = font.plainSubstrByWidth(recoveryStatus, contentW * 2 / 5 - 6);
			ThemePreviewDraw.text(g, font, shownStatus, x + w - pad - font.width(shownStatus), metaY, dev.vy.drt.config.DrtTheme.color("text.muted", TEXT_MUTED));
			hoverTargets.add(new HoverTarget(x + w - pad - font.width(shownStatus), metaY - 3, font.width(shownStatus), 15, recoveryStatus));
		}
		RoundedUiRenderer.line(contentX, separatorY, x + w - pad, separatorY, 1, dev.vy.drt.config.DrtTheme.color("separator", 0x443D426B));

		boolean wrapSummary = false;
		if (purchase != null) {
			String openedLabel = "Opened:";
			String chestName = LootHistoryPresentation.compactChestName(purchase.chestTitle) + " Chest";
			boolean costKnown = purchase.hasKnownChestCost();
			String costText = "Cost: " + (costKnown ? entry.realisedCost() == 0L ? "Free" : formatCoins(entry.realisedCost()) : "Unknown");
			String valueText = "Value: " + formatCoins(entry.realisedValue());
			String profitText = "Profit: " + lootProfitLabel(purchase);
			String financialSeparator = "|";
			int summaryGap = 7;
			int separatorW = font.width(financialSeparator);
			int openedTextX = contentX + 3;
			int iconX = openedTextX + font.width(openedLabel) + 4;
			int chestX = iconX + 18;
			int openedWidth = font.width(openedLabel) + 4 + 16 + 2 + font.width(chestName);
			int requiredSummaryW = openedWidth + summaryGap * 5 + separatorW * 2
				+ font.width(costText) + font.width(valueText) + font.width(profitText);
			wrapSummary = requiredSummaryW > contentW;
			int openedRowY = separatorY + 4;
			int openedTextY = DrtUiText.centeredTextY(font, openedRowY, 16);
			ThemePreviewDraw.text(g, font, openedLabel, openedTextX, openedTextY, dev.vy.drt.config.DrtTheme.color("text.primary", TEXT_PRIMARY));
			ItemStack chestIcon = resolveChestIcon(purchase.chestTitle);
			if (!chestIcon.isEmpty()) ThemePreviewDraw.item(g, chestIcon, iconX, openedRowY);
			ThemePreviewDraw.text(g, font, chestName, chestX, openedTextY, chestColor(purchase.chestTitle));
			int financialX = wrapSummary ? contentX + 3 : openedTextX + openedWidth + summaryGap;
			int financialY = wrapSummary ? openedTextY + 13 : openedTextY;
			ThemePreviewDraw.text(g, font, costText, financialX, financialY, dev.vy.drt.config.DrtTheme.color("text.secondary", TEXT_SECONDARY));
			financialX += font.width(costText) + summaryGap;
			ThemePreviewDraw.text(g, font, financialSeparator, financialX, financialY, dev.vy.drt.config.DrtTheme.color("text.muted", TEXT_MUTED));
			financialX += separatorW + summaryGap;
			ThemePreviewDraw.text(g, font, valueText, financialX, financialY, dev.vy.drt.config.DrtTheme.color("text.secondary", TEXT_SECONDARY));
			financialX += font.width(valueText) + summaryGap;
			ThemePreviewDraw.text(g, font, financialSeparator, financialX, financialY, dev.vy.drt.config.DrtTheme.color("text.muted", TEXT_MUTED));
			financialX += separatorW + summaryGap;
			ThemePreviewDraw.text(g, font, profitText, financialX, financialY,
				purchase.hasKnownProfit() ? profitColor(entry.realisedProfit())
					: purchase.hasIncompleteKnownValue() ? 0xFFFFB45C
					: dev.vy.drt.config.DrtTheme.color("text.muted", TEXT_MUTED));
		}

		int partyOffset = (separatorY - y) + (purchase == null ? 12 : wrapSummary ? 33 : 24);
		int analyticsH = RunsPanelLayout.analyticsHeight(h, partyOffset);
		int partyY = y + partyOffset;
		int partyH = RunsPanelLayout.partyHeight(h, partyOffset, analyticsH);
		RoundedUiRenderer.line(contentX, partyY - 4, x + w - pad, partyY - 4, 1, dev.vy.drt.config.DrtTheme.color("separator", 0x343D426B));
		drawRunParty(g, entry, data, contentX, partyY, contentW, partyH, mouseX, mouseY);

		if (analyticsH <= 0) return;
		int analyticsY = y + h - analyticsH - RunsPanelLayout.PANEL_BOTTOM_PAD;
		roundedPanel(g, contentX, analyticsY, contentW, analyticsH, 4, dev.vy.drt.config.DrtTheme.color("panel.alternate", PANEL_BG_ALT), dev.vy.drt.config.DrtTheme.color("panel.border", 0xFF292946));
		g.enableScissor(contentX + 1, analyticsY + 1, contentX + contentW - 1, analyticsY + analyticsH - 1);
		try {
			drawRunsAnalysisGraph(g, entry, data, contentX + 3, analyticsY + 2, contentW - 6, analyticsH - 4, mouseX, mouseY);
		} finally {
			g.disableScissor();
		}
	}

	private void drawRunsAnalyticsSection(GuiGraphicsExtractor g, RunsDashboardData.Entry selected,
		RunsDashboardData.Snapshot data, int x, int y, int w, int h, int mouseX, int mouseY) {
		String floorId = selected.completion().floor;
		List<RunsDashboardData.Entry> floorEntries = data.entries().stream()
			.filter(entry -> Objects.equals(entry.completion().floor, floorId))
			.sorted(Comparator.comparingLong(entry -> entry.completion().completedAtEpochMillis)).toList();
		long exact = floorEntries.stream().map(entry -> RunScore.resolve(entry.completion())).filter(RunScore.Resolved::actual).count();
		long inferred = floorEntries.stream().map(entry -> RunScore.resolve(entry.completion())).filter(RunScore.Resolved::inferred).count();
		double exactAverage = floorEntries.stream().map(entry -> RunScore.resolve(entry.completion())).filter(RunScore.Resolved::actual)
			.mapToInt(RunScore.Resolved::value).average().orElse(0D);
		int exactBest = floorEntries.stream().map(entry -> RunScore.resolve(entry.completion())).filter(RunScore.Resolved::actual)
			.mapToInt(RunScore.Resolved::value).max().orElse(0);
		ThemePreviewDraw.text(g, font, floorId + " Analytics", x + 10, y + 7, dev.vy.drt.config.DrtTheme.color("text.sectionTitle", TEXT_PRIMARY), true);
		String exactSummary = exact == 0 ? "Exact score n=0" : String.format(Locale.ROOT, "Exact avg %.1f, best %d, n=%d", exactAverage, exactBest, exact);
		String samples = exactSummary + "  |  Inferred n=" + inferred + "  |  Runs n=" + floorEntries.size();
		ThemePreviewDraw.text(g, font, font.plainSubstrByWidth(samples, w - 20), x + 10, y + 21, dev.vy.drt.config.DrtTheme.color("text.muted", TEXT_MUTED));
			hoverTargets.add(new HoverTarget(x + 8, y + 17, Math.min(w - 16, font.width(samples) + 6), 15,
			"* Estimated from grade"));
		if (h < 170) {
			String grades = List.of("S+", "S", "A", "B", "C", "D").stream()
				.map(grade -> grade + " " + floorEntries.stream().filter(entry -> grade.equalsIgnoreCase(entry.completion().grade)).count())
				.reduce((left, right) -> left + "  |  " + right).orElse("");
			ThemePreviewDraw.text(g, font, "Compact analytics view", x + 10, y + 43, dev.vy.drt.config.DrtTheme.color("text.secondary", TEXT_SECONDARY));
			ThemePreviewDraw.text(g, font, font.plainSubstrByWidth(grades, w - 20), x + 10, y + 59, dev.vy.drt.config.DrtTheme.color("text.muted", TEXT_MUTED));
			return;
		}

		int gradeH = 54;
		int chartsTop = y + 39;
		int chartsBottom = Math.max(chartsTop + 80, y + h - gradeH - 7);
		int gap = 22;
		int chartH = Math.max(34, (chartsBottom - chartsTop - gap) / 2);
		drawDurationTrend(g, floorEntries, x + 10, chartsTop, w - 20, chartH, mouseX, mouseY);
		drawScoreTrend(g, floorEntries, x + 10, chartsTop + chartH + gap, w - 20, chartH, mouseX, mouseY);
		drawGradeDistribution(g, floorEntries, x + 10, y + h - gradeH, w - 20, gradeH);
	}

	private void drawDurationTrend(GuiGraphicsExtractor g, List<RunsDashboardData.Entry> entries,
		int x, int y, int w, int h, int mouseX, int mouseY) {
		ThemePreviewDraw.text(g, font, "Completion time", x, y - 12, dev.vy.drt.config.DrtTheme.color("text.sectionTitle", TEXT_MUTED));
		List<RunsDashboardData.Entry> timed = entries.stream().filter(entry -> RunHistoryLinks.runTime(entry.completion()) != null).toList();
		int graphX = x + 48;
		int graphW = Math.max(20, w - 52);
		RoundedUiRenderer.line(graphX, y + h, graphX + graphW, y + h, 1, dev.vy.drt.config.DrtTheme.color("chart.axis", 0x553D426B));
		if (timed.size() < 2) {
			ThemePreviewDraw.text(g, font, "Insufficient exact durations (n=" + timed.size() + ")", graphX + 4, y + 8, dev.vy.drt.config.DrtTheme.color("text.muted", TEXT_MUTED));
			return;
		}
		int start = Math.max(0, timed.size() - 30);
		List<RunsDashboardData.Entry> shown = timed.subList(start, timed.size());
		long min = shown.stream().mapToLong(entry -> RunHistoryLinks.runTime(entry.completion()).millis()).min().orElse(0L);
		long max = shown.stream().mapToLong(entry -> RunHistoryLinks.runTime(entry.completion()).millis()).max().orElse(min + 1);
		if (max == min) max++;
		ThemePreviewDraw.text(g, font, RunHistoryLinks.formatRunTime(max), x, y, dev.vy.drt.config.DrtTheme.color("text.muted", TEXT_MUTED));
		ThemePreviewDraw.text(g, font, RunHistoryLinks.formatRunTime(min), x, y + h - font.lineHeight, dev.vy.drt.config.DrtTheme.color("text.muted", TEXT_MUTED));
		float px = 0F, py = 0F;
		for (int i = 0; i < shown.size(); i++) {
			var entry = shown.get(i);
			long value = RunHistoryLinks.runTime(entry.completion()).millis();
			float nx = graphX + i * graphW / (float) Math.max(1, shown.size() - 1);
			float ny = y + h - (value - min) * h / (float) (max - min);
			if (i > 0) RoundedUiRenderer.line(px, py, nx, ny, 1.25F, dev.vy.drt.config.DrtTheme.color("chart.line", 0xFF747CB8));
			RoundedUiRenderer.fill(g, (int) nx - 1, (int) ny - 1, 3, 3, 1, dev.vy.drt.config.DrtTheme.color("text.accent", 0xFF9CA8FF));
			if (contains((int) nx - 4, (int) ny - 4, 8, 8, mouseX, mouseY)) hoverTargets.add(new HoverTarget(
				(int) nx - 4, (int) ny - 4, 8, 8, formatOverviewDate(entry.completion().completedAtEpochMillis)
					+ " | " + RunHistoryLinks.formatRunTime(value)));
			px = nx; py = ny;
		}
	}

	private void drawScoreTrend(GuiGraphicsExtractor g, List<RunsDashboardData.Entry> entries,
		int x, int y, int w, int h, int mouseX, int mouseY) {
		ThemePreviewDraw.text(g, font, "Score trend", x, y - 12, dev.vy.drt.config.DrtTheme.color("text.sectionTitle", TEXT_MUTED));
		ThemePreviewDraw.text(g, font, "Exact", x + 65, y - 12, dev.vy.drt.config.DrtTheme.color("chart.actual", 0xFF78D59B));
		ThemePreviewDraw.text(g, font, "Inferred*", x + 103, y - 12, dev.vy.drt.config.DrtTheme.color("chart.inferred", 0xFFC79BE8));
		record ScorePoint(RunsDashboardData.Entry entry, RunScore.Resolved score) {}
		List<ScorePoint> points = entries.stream().map(entry -> new ScorePoint(entry, RunScore.resolve(entry.completion())))
			.filter(point -> point.score().known()).toList();
		int graphX = x + 48;
		int graphW = Math.max(20, w - 52);
		RoundedUiRenderer.line(graphX, y + h, graphX + graphW, y + h, 1, dev.vy.drt.config.DrtTheme.color("chart.axis", 0x553D426B));
		if (points.isEmpty()) {
			ThemePreviewDraw.text(g, font, "No score or grade data", graphX + 4, y + 8, dev.vy.drt.config.DrtTheme.color("text.muted", TEXT_MUTED));
			return;
		}
		int start = Math.max(0, points.size() - 30);
		List<ScorePoint> shown = points.subList(start, points.size());
		int min = shown.stream().mapToInt(point -> point.score().value()).min().orElse(0);
		int max = shown.stream().mapToInt(point -> point.score().value()).max().orElse(min + 1);
		if (max == min) max++;
		ThemePreviewDraw.text(g, font, String.valueOf(max), x, y, dev.vy.drt.config.DrtTheme.color("text.muted", TEXT_MUTED));
		ThemePreviewDraw.text(g, font, String.valueOf(min), x, y + h - font.lineHeight, dev.vy.drt.config.DrtTheme.color("text.muted", TEXT_MUTED));
		float px = 0F, py = 0F;
		for (int i = 0; i < shown.size(); i++) {
			ScorePoint point = shown.get(i);
			float nx = graphX + i * graphW / (float) Math.max(1, shown.size() - 1);
			float ny = y + h - (point.score().value() - min) * h / (float) (max - min);
			if (i > 0) RoundedUiRenderer.line(px, py, nx, ny, 1, dev.vy.drt.config.DrtTheme.color("chart.scoreLine", 0xFF596078));
			int color = point.score().actual() ? dev.vy.drt.config.DrtTheme.color("chart.actual", 0xFF78D59B) : dev.vy.drt.config.DrtTheme.color("chart.inferred", 0xFFC79BE8);
			RoundedUiRenderer.fill(g, (int) nx - 2, (int) ny - 2, 4, 4, 1, color);
			if (contains((int) nx - 4, (int) ny - 4, 8, 8, mouseX, mouseY)) hoverTargets.add(new HoverTarget(
				(int) nx - 4, (int) ny - 4, 8, 8, formatOverviewDate(point.entry().completion().completedAtEpochMillis)
					+ " | Score " + point.score().display() + (point.score().inferred() ? " | Minimum inferred from grade" : " | Exact")));
			px = nx; py = ny;
		}
	}

	private void drawGradeDistribution(GuiGraphicsExtractor g, List<RunsDashboardData.Entry> entries, int x, int y, int w, int h) {
		ThemePreviewDraw.text(g, font, "Grade distribution", x, y, dev.vy.drt.config.DrtTheme.color("text.sectionTitle", TEXT_MUTED));
		List<String> grades = List.of("S+", "S", "A", "B", "C", "D");
		int cellW = Math.max(38, w / grades.size());
		Map<String, Long> counts = entries.stream().collect(java.util.stream.Collectors.groupingBy(
			entry -> entry.completion().grade == null ? "" : entry.completion().grade.toUpperCase(Locale.ROOT),
			java.util.stream.Collectors.counting()));
		long max = counts.values().stream().mapToLong(Long::longValue).max().orElse(1L);
		for (int i = 0; i < grades.size(); i++) {
			String grade = grades.get(i);
			long count = counts.getOrDefault(grade, 0L);
			int cx = x + i * cellW;
			ThemePreviewDraw.text(g, font, grade + "  " + count, cx, y + 14, grade.equals("S+") ? 0xFFC79BE8 : dev.vy.drt.config.DrtTheme.color("text.secondary", TEXT_SECONDARY));
			int barW = (int) Math.max(1L, (cellW - 8L) * count / Math.max(1L, max));
			RoundedUiRenderer.fill(g, cx, y + 29, barW, 7, 2, grade.equals("S+") ? dev.vy.drt.config.DrtTheme.color("chart.barHighlighted", 0xFF8B6BB5) : dev.vy.drt.config.DrtTheme.color("chart.bar", 0xFF555D91));
		}
	}

	private void drawRunDetails(GuiGraphicsExtractor g, RunsDashboardData.Entry entry, RunsDashboardData.Snapshot data,
		int x, int y, int w, int h, int mouseX, int mouseY) {
		DungeonRunCompletionRecord completion = entry.completion();
		ThemePreviewDraw.text(g, font, completion.floor, x + 10, y + 9, dev.vy.drt.config.DrtTheme.color("text.accent", 0xFF9CA8FF), true);
		ThemePreviewDraw.text(g, font, completion.grade, x + 17 + font.width(completion.floor), y + 9, gradeColor(completion.grade), true);
		String completed = formatOverviewDate(completion.completedAtEpochMillis);
		ThemePreviewDraw.text(g, font, completed, x + w - font.width(completed) - 10, y + 9, dev.vy.drt.config.DrtTheme.color("text.muted", TEXT_MUTED));
		RunHistoryLinks.RunTime time = RunHistoryLinks.runTime(completion);
		String duration = time == null ? "Unknown" : RunHistoryLinks.formatRunTime(time.millis());
		RunScore.Resolved resolvedScore = RunScore.resolve(completion);
		String runLine = "Time  " + duration + "     Score  " + resolvedScore.display();
		ThemePreviewDraw.text(g, font, runLine, x + 10, y + 27, dev.vy.drt.config.DrtTheme.color("text.secondary", TEXT_SECONDARY));
		if (resolvedScore.inferred()) {
			hoverTargets.add(new HoverTarget(x + 7, y + 23, Math.min(w - 14, font.width(runLine) + 6), 16,
				"* Estimated from grade"));
		}
		DungeonRunRecord purchase = entry.primaryPurchase();
		String chest = purchase == null ? "Chest  None"
			: "Chest  " + LootHistoryPresentation.compactChestName(purchase.chestTitle) + "     Cost  " + formatCoins(entry.realisedCost())
				+ "     Value  " + formatCoins(entry.realisedValue()) + "     Profit  " + formatSigned(entry.realisedProfit());
		ThemePreviewDraw.text(g, font, font.plainSubstrByWidth(chest, w - 20), x + 10, y + 45, purchase == null ? dev.vy.drt.config.DrtTheme.color("text.muted", TEXT_MUTED) : profitColor(entry.realisedProfit()));
		String offerLabel = entry.offerCount() == 0 ? "Offers  None" : "Offers  " + entry.offerCount();
		if (entry.offerCount() > 0) {
			String offers = entry.completion().chestOffers.stream().filter(Objects::nonNull)
				.map(offer -> offer.chestTitle + (dev.vy.drt.config.DungeonChestOffer.SOURCE_API_RECOVERED.equals(offer.captureSource) ? " (API)" : ""))
				.reduce((left, right) -> left + ", " + right).orElse("");
			offerLabel += "  |  " + offers;
		}
		ThemePreviewDraw.text(g, font, font.plainSubstrByWidth(offerLabel, w - 20), x + 10, y + 63, entry.offerCount() > 0 ? dev.vy.drt.config.DrtTheme.color("text.secondary", TEXT_SECONDARY) : dev.vy.drt.config.DrtTheme.color("text.muted", TEXT_MUTED));

		int chartY = y + 84;
		int chartH = Math.max(48, h - 120);
		if (chartH >= 48) drawRunsAnalysisGraph(g, entry, data, x + 2, chartY, w - 4, chartH, mouseX, mouseY);
		boolean canViewLoot = purchase != null || entry.offerCount() > 0;
		int bw = 88;
		int bx = x + w - bw - 8;
		int by = y + h - 24;
		if (canViewLoot) drawCustomButton(g, bx, by, bw, 18, "View in Loot", false, mouseX, mouseY, () -> navigateRunToLoot(entry));
		else ThemePreviewDraw.text(g, font, "No loot record", bx + 6, by + 4, dev.vy.drt.config.DrtTheme.color("text.muted", TEXT_MUTED));
		int recoverW = 82;
		drawCustomButton(g, bx - recoverW - 5, by, recoverW, 18, "Recover API", false, mouseX, mouseY, this::recoverRecentCroesus);
		if (!recoveryStatus.isBlank()) {
			int statusW = Math.max(20, bx - recoverW - x - 22);
			ThemePreviewDraw.text(g, font, font.plainSubstrByWidth(recoveryStatus, statusW), x + 10, by + 4, dev.vy.drt.config.DrtTheme.color("text.muted", TEXT_MUTED));
			hoverTargets.add(new HoverTarget(x + 7, by, statusW + 6, 18, recoveryStatus));
		}
	}

	private void drawRunsAnalysisGraph(GuiGraphicsExtractor g, RunsDashboardData.Entry selected,
		RunsDashboardData.Snapshot data, int x, int y, int w, int h, int mouseX, int mouseY) {
		String graphFloor = selected.completion().floor;
		// Kuudra has no grades, so its times chart takes the full width.
		boolean showGrades = RunsPanelLayout.showsScore(graphFloor);
		int timeW = showGrades ? Math.max(150, w * 64 / 100) : w;
		ThemePreviewDraw.text(g, font, graphFloor + " Times", x + 8, y + 5, dev.vy.drt.config.DrtTheme.color("text.sectionTitle", TEXT_MUTED));
		List<RunsDashboardData.Entry> floorEntries = data.entries().stream()
			.filter(entry -> Objects.equals(entry.completion().floor, graphFloor))
			.sorted(Comparator.comparingLong(entry -> entry.completion().completedAtEpochMillis)).toList();
		List<RunsDashboardData.Entry> timed = floorEntries.stream().filter(entry -> RunHistoryLinks.runTime(entry.completion()) != null).toList();
		int plotX = x + 52;
		int plotY = y + 20;
		int plotRight = x + timeW - 10;
		int plotBottom = y + h - 9;
		int plotW = Math.max(30, plotRight - plotX);
		int plotH = Math.max(12, plotBottom - plotY);
		RoundedUiRenderer.line(plotX, plotBottom, plotRight, plotBottom, 1, dev.vy.drt.config.DrtTheme.color("chart.axis", 0x553D426B));
		RoundedUiRenderer.line(plotX, plotY, plotX, plotBottom, 1, dev.vy.drt.config.DrtTheme.color("separator", 0x443D426B));
		if (timed.size() >= 2) {
			long min = timed.stream().mapToLong(entry -> RunHistoryLinks.runTime(entry.completion()).millis()).min().orElse(0L);
			long max = timed.stream().mapToLong(entry -> RunHistoryLinks.runTime(entry.completion()).millis()).max().orElse(min + 1L);
			if (max == min) max = min + 1L;
			int start = Math.max(0, timed.size() - 30);
			String maxLabel = RunHistoryLinks.formatRunTime(max);
			String minLabel = RunHistoryLinks.formatRunTime(min);
			ThemePreviewDraw.text(g, font, maxLabel, plotX - font.width(maxLabel) - 6, plotY - font.lineHeight / 2, dev.vy.drt.config.DrtTheme.color("text.muted", TEXT_MUTED));
			ThemePreviewDraw.text(g, font, minLabel, plotX - font.width(minLabel) - 5, plotBottom - font.lineHeight, dev.vy.drt.config.DrtTheme.color("text.muted", TEXT_MUTED));
			float px = 0F, py = 0F;
			for (int i = start; i < timed.size(); i++) {
				long value = RunHistoryLinks.runTime(timed.get(i).completion()).millis();
				float nx = plotX + (i - start) * plotW / (float) Math.max(1, timed.size() - start - 1);
				float ny = plotBottom - (value - min) * plotH / (float) (max - min);
				if (i > start) RoundedUiRenderer.line(px, py, nx, ny, 1.25F, dev.vy.drt.config.DrtTheme.color("chart.line", 0xFF747CB8));
				if (contains((int) nx - 3, (int) ny - 3, 6, 6, mouseX, mouseY)) {
					hoverTargets.add(new HoverTarget((int) nx - 4, (int) ny - 4, 8, 8,
						formatOverviewDate(timed.get(i).completion().completedAtEpochMillis) + " | " + RunHistoryLinks.formatRunTime(value)));
				}
				px = nx; py = ny;
			}
		} else {
			ThemePreviewDraw.text(g, font, "Not enough runs", plotX + 6, plotY + 4, dev.vy.drt.config.DrtTheme.color("text.muted", TEXT_MUTED));
		}
		if (!showGrades) return;
		int gradeX = x + timeW + 8;
		int gradeW = Math.max(30, x + w - gradeX - 7);
		RoundedUiRenderer.line(gradeX - 7, y + 7, gradeX - 7, y + h - 7, 1, dev.vy.drt.config.DrtTheme.color("separator", 0x443D426B));
		ThemePreviewDraw.text(g, font, "Grades", gradeX, y + 5, dev.vy.drt.config.DrtTheme.color("text.sectionTitle", TEXT_MUTED));
		Map<String, Long> gradeCounts = floorEntries.stream().collect(java.util.stream.Collectors.groupingBy(
			entry -> entry.completion().grade == null ? "" : entry.completion().grade.toUpperCase(Locale.ROOT),
			java.util.stream.Collectors.counting()));
		long maxGrade = gradeCounts.values().stream().mapToLong(Long::longValue).max().orElse(1L);
		List<String> grades = List.of("S+", "S", "A", "B", "C", "D");
		int gradeCellW = Math.max(24, gradeW / 2);
		int gradeStartY = y + 19;
		int gradeRowGap = Math.max(11, Math.min(16, (h - 25) / 3));
		int countW = Math.max(12, font.width(String.valueOf(maxGrade)) + 2);
		for (int i = 0; i < grades.size(); i++) {
			String grade = grades.get(i);
			long count = gradeCounts.getOrDefault(grade, 0L);
			int gx = gradeX + (i % 2) * gradeCellW;
			int gy = gradeStartY + (i / 2) * gradeRowGap;
			ThemePreviewDraw.text(g, font, grade, gx, gy, gradeColor(grade));
			String countText = String.valueOf(count);
			int countX = gx + gradeCellW - countW;
			int barX = gx + 17;
			int barMaxW = Math.max(0, countX - barX - 4);
			if (barMaxW > 0) {
				RoundedUiRenderer.fill(g, barX, gy + 4, barMaxW, 3, 1, dev.vy.drt.config.DrtTheme.color("separator", 0x443D426B));
				int barW = (int) Math.max(0L, barMaxW * count / Math.max(1L, maxGrade));
				RoundedUiRenderer.fill(g, barX, gy + 4, barW, 3, 1, gradeColor(grade));
			}
			ThemePreviewDraw.text(g, font, countText, gx + gradeCellW - font.width(countText) - 2, gy, dev.vy.drt.config.DrtTheme.color("text.secondary", TEXT_SECONDARY));
		}
	}

	private void drawRunParty(GuiGraphicsExtractor g, RunsDashboardData.Entry entry, RunsDashboardData.Snapshot data,
		int x, int y, int w, int h, int mouseX, int mouseY) {
		List<dev.vy.drt.config.DungeonPartyMember> members = entry.completion().partyMembers == null
			? List.of() : entry.completion().partyMembers.stream().filter(RunsDashboardData::isValidHistoricalMember).toList();
		int widestMember = font.width("Party");
		String partyFloor = entry.completion().floor;
		for (dev.vy.drt.config.DungeonPartyMember member : members) {
			String historical = member.name + " " + RunsPanelLayout.partyDetailText(partyFloor, member.dungeonClass, member.classLevel, member.kuudraRole);
			widestMember = Math.max(widestMember,
				font.width(historical) + font.width(">") + RunsPanelLayout.PARTY_SELECTION_GAP);
		}
		int leftW = RunsPanelLayout.partyWidth(w, widestMember);
		int dividerX = x + leftW;
		RoundedUiRenderer.line(dividerX, y + 2, dividerX, y + h - 2, 1, dev.vy.drt.config.DrtTheme.color("separator", 0x443D426B));
		g.enableScissor(x, y, dividerX, y + h);
		ThemePreviewDraw.text(g, font, "Party", x + 7, y + 1, dev.vy.drt.config.DrtTheme.color("text.sectionTitle", TEXT_PRIMARY), true);
		int py = y + RunsPanelLayout.PARTY_LIST_TOP;
		int memberStep = RunsPanelLayout.partyRowStep(h, members.size(), font.lineHeight);
		if (members.isEmpty()) {
			ThemePreviewDraw.text(g, font, "Not recorded", x + 7, py, dev.vy.drt.config.DrtTheme.color("text.muted", TEXT_MUTED));
		} else {
			for (int i = 0; i < Math.min(5, members.size()); i++) {
				dev.vy.drt.config.DungeonPartyMember member = members.get(i);
				String classText = RunsPanelLayout.partyDetailText(partyFloor, member.dungeonClass, member.classLevel, member.kuudraRole);
				String identity = member.uuid == null || member.uuid.isBlank() ? "name:" + member.name.toLowerCase(Locale.ROOT) : "uuid:" + member.uuid;
				boolean selected = identity.equals(selectedPartyIdentity);
				int arrowX = x + 7;
				int nameX = arrowX + (selected ? font.width(">") + RunsPanelLayout.PARTY_SELECTION_GAP : 0);
				int availableWidth = Math.max(0, dividerX - 7 - nameX);
				int classWidth = Math.min(font.width(classText), Math.max(0, availableWidth * 52 / 100));
				String shownClass = font.plainSubstrByWidth(classText, classWidth);
				classWidth = font.width(shownClass);
				int classX = dividerX - 7 - classWidth;
				String shownName = font.plainSubstrByWidth(member.name, Math.max(0, classX - nameX - 7));
				if (selected) {
					ThemePreviewDraw.text(g, font, ">", arrowX, py, dev.vy.drt.config.DrtTheme.color("row.indicator", 0xFF63D184), true);
				}
				ThemePreviewDraw.text(g, font, shownName, nameX, py, selected ? dev.vy.drt.config.DrtTheme.color("text.primary", TEXT_PRIMARY) : dev.vy.drt.config.DrtTheme.color("text.secondary", TEXT_SECONDARY));
				ThemePreviewDraw.text(g, font, shownClass, classX, py, dev.vy.drt.config.DrtTheme.color("text.muted", TEXT_MUTED));
				int rowY = py;
				clickTargets.add(new ClickTarget(x + 4, rowY - 1, leftW - 8, memberStep, 0,
					() -> selectPartyMember(identity, member.uuid == null || member.uuid.isBlank() ? member.name : member.uuid)));
				py += memberStep;
			}
		}
		g.disableScissor();

		int currentX = dividerX + 8;
		int currentW = Math.max(80, x + w - currentX - 8);
		drawCurrentPlayerStats(g, entry, currentX, y + 2, currentW, h - 4);
	}

	private void selectPartyMember(String identity, String lookup) {
		selectedPartyIdentity = identity;
		selectedPlayerStats = null;
		playerStatsLoading = true;
		DrtDungeonStatsClient.fetch(lookup).whenComplete((result, error) -> {
			if (minecraft == null) return;
			minecraft.execute(() -> {
				if (!Objects.equals(selectedPartyIdentity, identity)) return;
				playerStatsLoading = false;
				selectedPlayerStats = error == null ? result
					: new DrtDungeonStatsClient.Result(DrtDungeonStatsClient.State.UNAVAILABLE, null, "Current statistics unavailable");
				if (selectedPlayerStats != null && selectedPlayerStats.stats() != null
					&& lookup != null && !lookup.replace("-", "").matches("(?i)[0-9a-f]{32}")) {
					DrtConfigManager.resolvePartyIdentity(lookup, selectedPlayerStats.stats().uuid());
					selectedPartyIdentity = "uuid:" + selectedPlayerStats.stats().uuid();
					invalidateRunsData();
				}
			});
		});
	}

	private void drawCurrentPlayerStats(GuiGraphicsExtractor g, RunsDashboardData.Entry entry, int x, int y, int w, int h) {
		DungeonPlayerStats stats = selectedPlayerStats == null ? null : selectedPlayerStats.stats();
		g.enableScissor(x, y, x + w, y + h);
		try {
			if (playerStatsLoading) {
				ThemePreviewDraw.text(g, font, "Loading...", x, y, dev.vy.drt.config.DrtTheme.color("text.secondary", TEXT_SECONDARY));
				return;
			}
			if (stats == null) {
				String message = selectedPlayerStats == null ? "Select a party member" : selectedPlayerStats.message();
				ThemePreviewDraw.text(g, font, font.plainSubstrByWidth(message, w - 6), x, y, selectedPlayerStats == null ? dev.vy.drt.config.DrtTheme.color("text.muted", TEXT_MUTED) : 0xFFB09A78);
				return;
			}

			int sy = y;
			String cata = stats.catacombs() == null ? "Unknown" : String.format(Locale.ROOT, "%.2f", stats.catacombs().fractionalLevel());
			String averageClass = stats.averageClassLevel() == null ? "Unknown" : String.format(Locale.ROOT, "%.2f", stats.averageClassLevel());
			String secrets = stats.secretsFound() == null ? "Unknown" : formatCoins(stats.secretsFound());
			Double secretsPerRun = PlayerStatMath.secretsPerRun(stats.secretsFound(), stats.totalCompletions());
			String secretsAverage = secretsPerRun == null ? "Unknown" : String.format(Locale.ROOT, "%.2f", secretsPerRun);
			DungeonPlayerStats.Floor floor = stats.floor(entry.completion().floor);
			String totalRuns = stats.totalCompletions() == null ? "Unknown" : formatCoins(stats.totalCompletions());
			String floorRuns = floor == null || floor.completions() == null ? "Unknown" : String.valueOf(floor.completions());
			String fastest = formatCurrentPb(floor == null ? null : floor.fastestTimeMs());
			String sPlusPb = formatCurrentPb(floor == null ? null : floor.fastestSPlusTimeMs());
			String berserk = classLevel(stats, "berserk");
			String tank = classLevel(stats, "tank");
			String healer = classLevel(stats, "healer");
			String archer = classLevel(stats, "archer");
			String mage = classLevel(stats, "mage");
			String[] overallLabels = {"Cata", "Secrets", "Runs"};
			String[] overallValues = {cata, secrets, totalRuns};
			String[] floorLabels = {"Secrets/run", entry.completion().floor + " Runs"};
			String[] floorValues = {secretsAverage, floorRuns};
			String[] pbLabels = {"Fastest", "S+ PB"};
			String[] pbValues = {fastest, sPlusPb};
			String[] classValues = {berserk, tank, healer, archer, mage, averageClass};
			int statsW = w - 4;
			int rows = statGroupRows(statsW, overallLabels, overallValues) + statGroupRows(statsW, floorLabels, floorValues)
				+ statGroupRows(statsW, pbLabels, pbValues) + 1
				+ RunsPanelLayout.classGridRows(classValues.length, dungeonClassGridColumns(statsW, classValues));
			int rowStep = RunsPanelLayout.statsRowStep(h, rows, font.lineHeight);
			sy = drawMeasuredStatsGroup(g, x, sy, statsW, rowStep, overallLabels, overallValues);
			sy = drawMeasuredStatsGroup(g, x, sy, statsW, rowStep, floorLabels, floorValues);
			sy = drawTimedStatsGroup(g, x, sy, statsW, rowStep, entry.completion().floor, pbLabels, pbValues,
				new Long[] {floor == null ? null : floor.fastestTimeMs(), floor == null ? null : floor.fastestSPlusTimeMs()});
			sy += 4;
			RoundedUiRenderer.line(x, sy - 4, x + w - 4, sy - 4, 1, dev.vy.drt.config.DrtTheme.color("separator", 0x443D426B));
			ThemePreviewDraw.text(g, font, "Classes", x, sy, dev.vy.drt.config.DrtTheme.color("text.muted", TEXT_MUTED));
			sy += rowStep;
			drawDungeonClassGrid(g, x, sy, w - 4, rowStep,
				berserk, tank, healer, archer, mage, averageClass);
		} finally {
			g.disableScissor();
		}
	}

	private int drawMeasuredStatsGroup(GuiGraphicsExtractor g, int x, int y, int w, int rowStep,
		String[] labels, String[] values) {
		int cx = x;
		int rowY = y;
		for (int i = 0; i < labels.length && i < values.length; i++) {
			String text = labels[i] + ": " + values[i];
			int textW = font.width(text);
			int prefixW = cx == x ? 0 : font.width(" | ");
			if (cx > x && cx + prefixW + textW > x + w) {
				cx = x;
				rowY += rowStep;
				prefixW = 0;
			}
			if (prefixW > 0) {
				ThemePreviewDraw.text(g, font, "|", cx + 3, rowY, dev.vy.drt.config.DrtTheme.color("separator", 0xFF454A66));
				cx += prefixW;
			}
			ThemePreviewDraw.text(g, font, text, cx, rowY, dev.vy.drt.config.DrtTheme.color("text.secondary", TEXT_SECONDARY));
			cx += textW;
		}
		return rowY + rowStep;
	}

	private int drawTimedStatsGroup(GuiGraphicsExtractor g, int x, int y, int w, int rowStep, String floor,
		String[] labels, String[] values, Long[] millis) {
		int cx = x;
		int rowY = y;
		for (int i = 0; i < labels.length && i < values.length && i < millis.length; i++) {
			String prefix = labels[i] + ": ";
			int textW = font.width(prefix + values[i]);
			int separatorW = cx == x ? 0 : font.width(" | ");
			if (cx > x && cx + separatorW + textW > x + w) {
				cx = x;
				rowY += rowStep;
				separatorW = 0;
			}
			if (separatorW > 0) {
				ThemePreviewDraw.text(g, font, "|", cx + 3, rowY, dev.vy.drt.config.DrtTheme.color("separator", 0xFF454A66));
				cx += separatorW;
			}
			ThemePreviewDraw.text(g, font, prefix, cx, rowY, dev.vy.drt.config.DrtTheme.color("text.secondary", TEXT_SECONDARY));
			ThemePreviewDraw.text(g, font, values[i], cx + font.width(prefix), rowY,
				RunTimeColorScale.color(floor, millis[i], dev.vy.drt.config.DrtTheme.color("text.secondary", TEXT_SECONDARY)));
			cx += textW;
		}
		return rowY + rowStep;
	}

	/** Rows a stats group needs once its "label: value" entries wrap to {@code w}, matching the group drawers. */
	private int statGroupRows(int w, String[] labels, String[] values) {
		int rows = 1;
		int used = 0;
		for (int i = 0; i < labels.length && i < values.length; i++) {
			int textW = font.width(labels[i] + ": " + values[i]);
			int separatorW = used == 0 ? 0 : font.width(" | ");
			if (used > 0 && used + separatorW + textW > w) {
				rows++;
				used = textW;
			} else {
				used += separatorW + textW;
			}
		}
		return rows;
	}

	private static final String[] DUNGEON_CLASS_LABELS = {"Berserk", "Tank", "Healer", "Archer", "Mage", "Class Avg"};
	private static final int DUNGEON_CLASS_GAP = 10;

	private int dungeonClassGridColumns(int w, String[] values) {
		int requiredCellWidth = 1;
		for (int i = 0; i < DUNGEON_CLASS_LABELS.length && i < values.length; i++) {
			requiredCellWidth = Math.max(requiredCellWidth, classTextWidth(DUNGEON_CLASS_LABELS[i], values[i]));
		}
		return RunsPanelLayout.classGridColumns(w, DUNGEON_CLASS_GAP, requiredCellWidth);
	}

	private void drawDungeonClassGrid(GuiGraphicsExtractor g, int x, int y, int w, int rowStep,
		String berserk, String tank, String healer, String archer, String mage, String average) {
		String[] labels = DUNGEON_CLASS_LABELS;
		String[] values = {berserk, tank, healer, archer, mage, average};
		int[] colors = {0xFFFF5555, 0xFF55FF55, 0xFFFF55FF, 0xFFFFAA00, 0xFF55FFFF,
			dev.vy.drt.config.DrtTheme.color("text.accent", 0xFF9CA8FF)};
		int gap = DUNGEON_CLASS_GAP;
		int columns = dungeonClassGridColumns(w, values);
		int cellWidth = Math.max(1, (w - gap * (columns - 1)) / columns);
		for (int i = 0; i < labels.length; i++) {
			int column = i % columns;
			int row = i / columns;
			drawDungeonClassCell(g, x + column * (cellWidth + gap), y + row * rowStep,
				cellWidth, labels[i], values[i], colors[i], i == labels.length - 1);
		}
	}

	private int classTextWidth(String label, String value) {
		return font.width(label + ": " + value);
	}

	private void drawDungeonClassCell(GuiGraphicsExtractor g, int x, int y, int w,
		String label, String value, int labelColor, boolean calculated) {
		String prefix = label + ": ";
		String shownPrefix = font.plainSubstrByWidth(prefix, w);
		int prefixWidth = font.width(shownPrefix);
		String shownValue = font.plainSubstrByWidth(value, Math.max(0, w - prefixWidth));
		ThemePreviewDraw.text(g, font, shownPrefix, x, y, labelColor, calculated);
		ThemePreviewDraw.text(g, font, shownValue, x + prefixWidth, y, calculated ? labelColor : dev.vy.drt.config.DrtTheme.color("text.secondary", TEXT_SECONDARY), calculated);
	}

	private String classLevel(DungeonPlayerStats stats, String className) {
		DungeonPlayerStats.Level level = stats.classes().get(className);
		return level == null ? "Unknown" : String.format(Locale.ROOT, "%.2f", level.fractionalLevel());
	}

	private void drawStatsPair(GuiGraphicsExtractor g, int x, int y, int w,
		String leftLabel, String leftValue, String rightLabel, String rightValue) {
		int gap = 8;
		int cellW = Math.max(56, (w - gap) / 2);
		ThemePreviewDraw.text(g, font, font.plainSubstrByWidth(leftLabel, cellW), x, y, dev.vy.drt.config.DrtTheme.color("text.muted", TEXT_MUTED));
		ThemePreviewDraw.text(g, font, font.plainSubstrByWidth(leftValue, cellW), x, y + 12, dev.vy.drt.config.DrtTheme.color("text.secondary", TEXT_SECONDARY));
		int rightX = x + cellW + gap;
		ThemePreviewDraw.text(g, font, font.plainSubstrByWidth(rightLabel, cellW), rightX, y, dev.vy.drt.config.DrtTheme.color("text.muted", TEXT_MUTED));
		ThemePreviewDraw.text(g, font, font.plainSubstrByWidth(rightValue, cellW), rightX, y + 12, dev.vy.drt.config.DrtTheme.color("text.secondary", TEXT_SECONDARY));
	}

	private String formatCurrentPb(Long millis) {
		return millis == null ? "Unknown" : RunHistoryLinks.formatRunTime(millis);
	}

	private void drawTeammatePerformance(GuiGraphicsExtractor g, RunsDashboardData.Entry selected,
		RunsDashboardData.Snapshot data, RunsDashboardData.Teammate teammate, int x, int y, int w, int bottom) {
		if (teammate.timedRuns() == 0) {
			ThemePreviewDraw.text(g, font, "No times", x, y, dev.vy.drt.config.DrtTheme.color("text.secondary", TEXT_SECONDARY));
		} else {
			String average = "Avg " + RunHistoryLinks.formatRunTime(teammate.averageDurationMs()) + "  Fastest ";
			String fastest = RunHistoryLinks.formatRunTime(teammate.fastestDurationMs());
			String shownAverage = font.plainSubstrByWidth(average, w);
			ThemePreviewDraw.text(g, font, shownAverage, x, y, dev.vy.drt.config.DrtTheme.color("text.secondary", TEXT_SECONDARY));
			int fastestX = x + font.width(shownAverage);
			if (shownAverage.equals(average) && fastestX < x + w) {
				String shownFastest = font.plainSubstrByWidth(fastest, x + w - fastestX);
				ThemePreviewDraw.text(g, font, shownFastest, fastestX, y,
					RunTimeColorScale.color(selected.completion().floor, teammate.fastestDurationMs(), dev.vy.drt.config.DrtTheme.color("text.secondary", TEXT_SECONDARY)));
			}
		}
		y += 13;
		String score = teammate.scoredRuns() == 0 ? "Score unavailable" : String.format(Locale.ROOT, "Avg score %.1f", teammate.averageScore());
		String grades = teammate.gradedRuns() == 0 ? "S+ unavailable" : String.format(Locale.ROOT, "S+ %d/%d (%.0f%%)",
			teammate.sPlusRuns(), teammate.gradedRuns(), teammate.sPlusPercent());
		ThemePreviewDraw.text(g, font, font.plainSubstrByWidth(score + "  |  " + grades, w), x, y, dev.vy.drt.config.DrtTheme.color("text.secondary", TEXT_SECONDARY));
		y += 13;
		RunsDashboardData.FloorPerformance floor = teammate.floors().get(selected.completion().floor);
		if (floor != null && y <= bottom - 12) {
			String floorLine = floor.floor() + "  " + floor.runs() + " runs"
				+ (floor.timedRuns() == 0 ? "" : "  Avg " + RunHistoryLinks.formatRunTime(floor.averageDurationMs()))
				+ (floor.scoredRuns() == 0 ? "" : String.format(Locale.ROOT, "  Score %.1f", floor.averageScore()));
			ThemePreviewDraw.text(g, font, font.plainSubstrByWidth(floorLine, w), x, y, dev.vy.drt.config.DrtTheme.color("text.muted", TEXT_MUTED));
			y += 13;
		}
		int shown = 0;
		for (RunsDashboardData.Entry candidate : data.entries()) {
			String completionId = runKey(candidate);
			if (!teammate.completionIds().contains(completionId) || y > bottom - 12 || shown >= 3) continue;
			String label = "Shared run: " + candidate.completion().floor + "  "
				+ formatOverviewDate(candidate.completion().completedAtEpochMillis);
			ThemePreviewDraw.text(g, font, font.plainSubstrByWidth(label, w), x, y, dev.vy.drt.config.DrtTheme.color("text.accent", 0xFF9CA8FF));
			int rowY = y;
			clickTargets.add(new ClickTarget(x, rowY - 1, w, 12, 0, () -> selectedRunCompletionId = completionId));
			y += 13;
			shown++;
		}
	}

	private void recoverRecentCroesus() {
		recoveryStatus = "Loading recent Croesus data...";
		DrtDungeonStatsClient.fetch(DrtAccount.currentName()).whenComplete((result, error) -> {
			if (minecraft == null) return;
			minecraft.execute(() -> {
				if (error != null) {
					recoveryStatus = "Recovery failed: request error.";
					return;
				}
				if (result == null || result.stats() == null) {
					recoveryStatus = "Recovery unavailable: " + (result == null ? "no response" : result.message());
					return;
				}
				int recentRuns = result.stats().recentRuns().size();
				CroesusApiRecovery.Plan plan = CroesusApiRecovery.plan((themePreview ? previewData.runs : DrtConfigManager.getRunCompletions()), result.stats().recentRuns());
				int offers = 0;
				int partyUpdates = 0;
				for (CroesusApiRecovery.Match match : plan.matches()) {
					if (DrtConfigManager.updateRunCompletionParty(match.completion().runSessionId, match.party())) partyUpdates++;
					for (var offer : match.newOffers()) if (DrtConfigManager.recordObservedChestOffer(match.completion().runSessionId, offer)) offers++;
				}
				recoveryStatus = CroesusRecoveryFeedback.summarize(recentRuns, plan.matches().size(), offers,
					partyUpdates, plan.ambiguous(), plan.unmatched(), plan.alreadyCaptured());
				invalidateRunsData();
			});
		});
	}

	private void navigateRunToLoot(RunsDashboardData.Entry entry) {
		if (entry == null || entry.completion() == null) return;
		try { selectedTab = DungeonFloor.valueOf(entry.completion().floor); }
		catch (IllegalArgumentException ignored) { selectedTab = null; }
		setActiveTab(DrtNavTab.LOOT);
		reloadHistory();
		String key = LootHistoryProjection.completionKey(entry.completion());
		DungeonRunRecord prefer = entry.primaryPurchase();
		String preferId = prefer != null && prefer.recordId != null ? prefer.recordId : "";
		int fallback = -1;
		for (int i = 0; i < tabRunEntries.size(); i++) {
			LootHistoryProjection.RunEntry row = tabRunEntries.get(i);
			if (!key.equals(row.runKey()) && !key.equals(row.key())) continue;
			if (!preferId.isBlank() && row.representative() != null && preferId.equals(row.representative().recordId)) {
				selectRunIndex(i);
				return;
			}
			if (fallback < 0) fallback = i;
		}
		if (fallback >= 0) selectRunIndex(fallback);
	}

	// ── Overlay Tab ───────────────────────────────────────────────────────────

	private void drawOverlayTab(GuiGraphicsExtractor g, int mouseX, int mouseY, int topY) {
		int contentX = ox + MARGIN;
		int contentW = winW - MARGIN * 2;
		int titleY = topY + 4;
		ThemePreviewDraw.text(g, font, "Overlay", contentX, titleY, dev.vy.drt.config.DrtTheme.color("text.pageTitle", TEXT_PRIMARY), true);
		int subY = titleY + font.lineHeight + 3;
		ThemePreviewDraw.text(g, font, "Hover a setting for details.", contentX, subY, dev.vy.drt.config.DrtTheme.color("text.description", TEXT_SECONDARY));

		settingsViewportX = contentX;
		settingsViewportY = subY + font.lineHeight + 6;
		settingsViewportW = contentW;
		settingsViewportH = Math.max(40, oy + winH - MARGIN - settingsViewportY);
		DrtConfig config = DrtConfigManager.getConfig();
		DungeonRunTrackerFeature tracker = DrtClient.getTracker();
		boolean pointerCoveredByDropdown = settingsDropdown != SettingsDropdown.NONE
			&& contains(settingsDropdownX, settingsDropdownY, settingsDropdownW, settingsDropdownH, mouseX, mouseY);
		int contentMouseX = pointerCoveredByDropdown ? Integer.MIN_VALUE : mouseX;
		int contentMouseY = pointerCoveredByDropdown ? Integer.MIN_VALUE : mouseY;

		g.enableScissor(settingsViewportX, settingsViewportY, settingsViewportX + settingsViewportW, settingsViewportY + settingsViewportH);
		try {
			int gap = 6;
			int layoutRows = OverlayPageState.LAYOUTS.size();
			// The HUD toolbar only takes the rows its controls need; the layout band above absorbs the rest.
			OverlayToolbarLayout.Result toolbar = OverlayToolbarLayout.arrange(overlayToolbarGroups(), contentW - SETTINGS_PANEL_INSET * 2);
			int toolbarH = TOOLBAR_PAD_Y * 2 + toolbar.height();
			int topAvailable = settingsViewportH - toolbarH - SettingsLayout.SECTION_GAP;
			settingsRowHeight = SettingsLayout.fillRowHeight(topAvailable, layoutRows, SettingsLayout.OVERLAY_MAX_ROW_HEIGHT);
			int layoutW = clamp(contentW * 2 / 5, 200, 300);
			int topH = SettingsLayout.sectionHeight(settingsRowHeight, layoutRows);
			drawOverlayLayoutList(g, tracker, contentMouseX, contentMouseY, contentX, settingsViewportY, layoutW);
			drawOverlayPreview(g, tracker, contentMouseX, contentMouseY, contentX + layoutW + gap, settingsViewportY,
				contentW - layoutW - gap, topH);
			drawOverlayHudToolbar(g, tracker, config, toolbar, contentMouseX, contentMouseY, contentX,
				settingsViewportY + topH + SettingsLayout.SECTION_GAP, contentW, toolbarH);
		} finally {
			g.disableScissor();
		}

		if (settingsDropdown != SettingsDropdown.NONE) beginSettingsPopupLayer(g);
		drawSettingsDropdownMenu(g, mouseX, mouseY);
		if (!settingsStatus.isBlank() && System.currentTimeMillis() < settingsStatusUntil) {
			String shown = font.plainSubstrByWidth(settingsStatus, contentW / 2);
			ThemePreviewDraw.text(g, font, shown, contentX + contentW - font.width(shown), titleY, dev.vy.drt.config.DrtTheme.color("text.secondary", TEXT_SECONDARY));
		}
	}

	private static final int TOOLBAR_LABEL_GAP = 6;
	private static final int TOOLBAR_PAD_Y = SettingsLayout.SECTION_BOTTOM_PADDING;
	private static final int TOOLBAR_SWITCH_W = 30;
	private static final int TOOLBAR_STEPPER_SIDE = 19;

	private List<OverlayToolbarLayout.Group> overlayToolbarGroups() {
		return List.of(
			new OverlayToolbarLayout.Group(toolbarLabelW("HUD Overlay") + TOOLBAR_SWITCH_W, false),
			new OverlayToolbarLayout.Group(toolbarLabelW("HUD Display") + overlayDisplayDropdownW(), false),
			new OverlayToolbarLayout.Group(toolbarLabelW("Scale") + overlayScaleStepperW(), false),
			new OverlayToolbarLayout.Group(overlayMoveButtonW() + 4 + overlayResetButtonW(), true));
	}

	private int toolbarLabelW(String label) {
		return font.width(label) + TOOLBAR_LABEL_GAP;
	}

	private int overlayDisplayDropdownW() {
		int widest = 0;
		for (String option : List.of("Default", "Global", "DHub")) widest = Math.max(widest, font.width(option));
		return Math.max(70, widest + 34);
	}

	private int overlayScaleStepperW() {
		return TOOLBAR_STEPPER_SIDE * 2 + Math.max(40, font.width("200%") + 14);
	}

	private int overlayMoveButtonW() {
		return font.width("Move Overlay") + 16;
	}

	private int overlayResetButtonW() {
		return Math.max(font.width("Reset"), font.width("Confirm Reset")) + 16;
	}

	private void drawOverlayHudToolbar(GuiGraphicsExtractor g, DungeonRunTrackerFeature tracker, DrtConfig config,
		OverlayToolbarLayout.Result toolbar, int mouseX, int mouseY, int x, int y, int w, int h) {
		roundedPanel(g, x, y, w, h, 4, dev.vy.drt.config.DrtTheme.color("panel.background", PANEL_BG), dev.vy.drt.config.DrtTheme.color("panel.border", PANEL_BORDER));
		int innerX = x + SETTINGS_PANEL_INSET;
		int rowsTop = y + TOOLBAR_PAD_Y;
		int controlH = settingsControlHeight();
		int labelColor = dev.vy.drt.config.DrtTheme.color("text.primary", TEXT_PRIMARY);
		int separatorColor = dev.vy.drt.config.DrtTheme.color("separator", PANEL_BORDER);
		List<OverlayToolbarLayout.Placement> placements = toolbar.placements();
		for (int i = 0; i < placements.size(); i++) {
			OverlayToolbarLayout.Placement placement = placements.get(i);
			int gx = innerX + placement.x();
			int rowY = rowsTop + placement.row() * OverlayToolbarLayout.ROW_HEIGHT;
			int controlY = rowY + (OverlayToolbarLayout.ROW_HEIGHT - controlH) / 2;
			int textY = DrtUiText.centeredTextY(font, rowY, OverlayToolbarLayout.ROW_HEIGHT);
			if (i > 0 && i < 3 && placements.get(i - 1).row() == placement.row()) {
				int sepX = gx - OverlayToolbarLayout.GROUP_GAP / 2;
				RoundedUiRenderer.line(sepX, rowY + 6, sepX, rowY + OverlayToolbarLayout.ROW_HEIGHT - 6, 1, separatorColor);
			}
			switch (i) {
				case 0 -> {
					String tooltip = "Toggle the overlay ON/OFF";
					drawToolbarLabel(g, "HUD Overlay", gx, rowY, textY, labelColor, tooltip);
					drawSettingsSwitch(g, mouseX, mouseY, gx + toolbarLabelW("HUD Overlay"), rowY + (OverlayToolbarLayout.ROW_HEIGHT - 14) / 2,
						tracker.isEnabled(), tracker::toggleHud, tooltip);
				}
				case 1 -> {
					String tooltip = "Change where DRT's overlay is displayed";
					drawToolbarLabel(g, "HUD Display", gx, rowY, textY, labelColor, tooltip);
					drawSettingsDropdownControl(g, mouseX, mouseY, gx + toolbarLabelW("HUD Display"), controlY, overlayDisplayDropdownW(),
						visibilityLabel(config.hudVisibilityMode), SettingsDropdown.VISIBILITY, tooltip);
				}
				case 2 -> {
					String tooltip = "Resize the overlay";
					drawToolbarLabel(g, "Scale", gx, rowY, textY, labelColor, tooltip);
					drawSettingsMiniStepper(g, mouseX, mouseY, gx + toolbarLabelW("Scale"), controlY, overlayScaleStepperW(), tracker.getHudScalePercent() + "%",
						() -> tracker.shrinkHudScale(Minecraft.getInstance(), true, false), () -> tracker.growHudScale(Minecraft.getInstance(), true, false), tooltip);
				}
				default -> {
					int moveW = overlayMoveButtonW();
					drawSettingsButton(g, mouseX, mouseY, gx, controlY, moveW, controlH, "Move Overlay", false,
						() -> minecraft.gui.setScreen(new DungeonTrackerPositionScreen(tracker, this)), "Drag the overlay on screen; scroll there to resize");
					boolean confirming = System.currentTimeMillis() < overlayResetConfirmUntil;
					drawSettingsButton(g, mouseX, mouseY, gx + moveW + 4, controlY, overlayResetButtonW(), controlH, confirming ? "Confirm Reset" : "Reset", confirming,
						() -> {
							if (System.currentTimeMillis() < overlayResetConfirmUntil) {
								overlayResetConfirmUntil = 0L;
								tracker.setHudPosition(Minecraft.getInstance(), OverlaySettings.DEFAULT_HUD_X, OverlaySettings.DEFAULT_HUD_Y, false);
								tracker.setHudScale(Minecraft.getInstance(), OverlaySettings.DEFAULT_HUD_SCALE, true);
								setSettingsStatus("Overlay position and scale reset.");
							} else overlayResetConfirmUntil = System.currentTimeMillis() + 5_000L;
						}, "Move the overlay back to the top left at 100%");
				}
			}
		}
	}

	private void drawToolbarLabel(GuiGraphicsExtractor g, String label, int x, int rowY, int textY, int color, String tooltip) {
		ThemePreviewDraw.text(g, font, label, x, textY, color);
		addClippedHoverTarget(x, rowY, font.width(label), OverlayToolbarLayout.ROW_HEIGHT, settingsViewportY, settingsViewportH, tooltip);
	}

	private void drawOverlayLayoutList(GuiGraphicsExtractor g, DungeonRunTrackerFeature tracker, int mouseX, int mouseY, int x, int y, int w) {
		y = drawSettingsSection(g, x, y, w, "Layout", OverlayPageState.LAYOUTS.size());
		int rowX = x + SETTINGS_PANEL_INSET;
		int rowW = w - SETTINGS_PANEL_INSET * 2;
		OverlayPreset active = tracker.getOverlayPreset();
		OverlayPreset previewed = overlayPage.previewed(active);
		int buttonW = Math.max(font.width("Preview"), font.width("Active")) + 12;
		int editW = font.width("Edit Text") + 12;
		for (OverlayPreset preset : OverlayPageState.LAYOUTS) {
			String description = DrtOverlayEditorScreen.descriptionFor(preset);
			drawSettingsRowBase(g, rowX, y, rowW, preset.displayName(), description, mouseX, mouseY);
			addClippedClickTarget(rowX, y, rowW, settingsRowHeight, settingsViewportY, settingsViewportH, 0, () -> overlayPage.preview(preset));
			int right = rowX + rowW - 6;
			int controlY = settingsControlY(y);
			int controlH = settingsControlHeight();
			drawSettingsToggleButton(g, mouseX, mouseY, right - buttonW, controlY, buttonW, controlH,
				preset == active ? "Active" : "Preview", preset == previewed, () -> overlayPage.preview(preset), description);
			if (preset == OverlayPreset.CUSTOM) {
				drawSettingsButton(g, mouseX, mouseY, right - buttonW - 4 - editW, controlY, editW, controlH, "Edit Text", false,
					() -> minecraft.gui.setScreen(new DrtCustomOverlayEditorScreen(tracker, this)),
					"Edit the custom overlay text, colours and placeholders");
			}
			y += settingsRowHeight;
		}
	}

	private void drawOverlayPreview(GuiGraphicsExtractor g, DungeonRunTrackerFeature tracker, int mouseX, int mouseY, int x, int y, int w, int h) {
		roundedPanel(g, x, y, w, h, 4, dev.vy.drt.config.DrtTheme.color("panel.background", PANEL_BG), dev.vy.drt.config.DrtTheme.color("panel.border", PANEL_BORDER));
		int inset = SETTINGS_PANEL_INSET;
		OverlayPreset active = tracker.getOverlayPreset();
		OverlayPreset previewed = overlayPage.previewed(active);
		boolean pending = overlayPage.hasPendingChange(active);

		int buttonH = settingsControlHeight();
		int buttonY = y + 5;
		int applyW = font.width("Apply") + 28;
		int cancelW = font.width("Cancel") + 20;
		int applyX = x + w - inset - applyW;
		int cancelX = applyX - 4 - cancelW;
		drawSettingsButton(g, mouseX, mouseY, applyX, buttonY, applyW, buttonH, "Apply", pending, this::applyOverlayPreview,
			"Use " + previewed.displayName() + " on the HUD", pending);
		drawSettingsButton(g, mouseX, mouseY, cancelX, buttonY, cancelW, buttonH, "Cancel", false, overlayPage::cancel,
			"Return the preview to the active layout", pending);

		String heading = font.plainSubstrByWidth("Preview - " + previewed.displayName(), Math.max(8, cancelX - 6 - (x + inset)));
		ThemePreviewDraw.text(g, font, heading, x + inset, DrtUiText.centeredTextY(font, buttonY, buttonH),
			dev.vy.drt.config.DrtTheme.color("text.sectionTitle", 0xFF9CA8FF), true);
		int descriptionY = buttonY + buttonH + 4;
		ThemePreviewDraw.text(g, font, font.plainSubstrByWidth(DrtOverlayEditorScreen.descriptionFor(previewed), w - inset * 2), x + inset,
			descriptionY, dev.vy.drt.config.DrtTheme.color("text.description", TEXT_SECONDARY));

		int areaY = descriptionY + font.lineHeight + 5;
		int areaH = y + h - inset - areaY;
		if (areaH > 16) {
			roundedPanel(g, x + inset, areaY, w - inset * 2, areaH, 4, dev.vy.drt.config.DrtTheme.color("input.background", PANEL_BG_ALT), dev.vy.drt.config.DrtTheme.color("control.border", PANEL_BORDER));
			// Drawn at the HUD's own scale so the preview shows its real size, scaled down only when it would otherwise be
			// clipped; overlay colours never come from the UI theme.
			var layout = tracker.buildPreviewLayout(Minecraft.getInstance(), previewed, null);
			int previewW = w - inset * 2 - 8;
			int previewH = areaH - 8;
			float scale = Math.min(tracker.getHudScalePercent() / 100F, Math.min(
				previewW / (float) Math.max(1, layout.width + OverlayLayouts.SHADOW_PAD),
				previewH / (float) Math.max(1, layout.height + OverlayLayouts.SHADOW_PAD)));
			DrtOverlayEditorScreen.renderPreviewAt(g, font, layout, x + inset + 4, areaY + 4, previewW, previewH, scale);
		}
	}

	private void applyOverlayPreview() {
		DungeonRunTrackerFeature tracker = DrtClient.getTracker();
		OverlayPreset target = overlayPage.apply(tracker.getOverlayPreset());
		if (target == null) return;
		tracker.setOverlayPreset(target);
		setSettingsStatus(target.displayName() + " overlay applied.");
	}

	// Settings Tab

	private void drawSettingsTab(GuiGraphicsExtractor g, int mouseX, int mouseY, int topY) {
		int contentX = ox + MARGIN;
		int contentW = winW - MARGIN * 2;
		int titleY = topY + 4;
		ThemePreviewDraw.text(g, font, "Settings", contentX, titleY, dev.vy.drt.config.DrtTheme.color("text.pageTitle", TEXT_PRIMARY), true);
		int subY = titleY + font.lineHeight + 3;
		ThemePreviewDraw.text(g, font, "Hover a setting for details.", contentX, subY, dev.vy.drt.config.DrtTheme.color("text.description", TEXT_SECONDARY));

		settingsViewportX = contentX;
		settingsViewportY = subY + font.lineHeight + 6;
		settingsViewportW = contentW;
		settingsViewportH = Math.max(40, oy + winH - MARGIN - settingsViewportY);
		DrtConfig config = DrtConfigManager.getConfig();
		DungeonRunTrackerFeature tracker = DrtClient.getTracker();
		boolean pointerCoveredByDropdown = settingsDropdown != SettingsDropdown.NONE
			&& contains(settingsDropdownX, settingsDropdownY, settingsDropdownW, settingsDropdownH, mouseX, mouseY);
		int contentMouseX = pointerCoveredByDropdown ? Integer.MIN_VALUE : mouseX;
		int contentMouseY = pointerCoveredByDropdown ? Integer.MIN_VALUE : mouseY;

		g.enableScissor(settingsViewportX, settingsViewportY, settingsViewportX + settingsViewportW, settingsViewportY + settingsViewportH);
		try {
			int gap = 6;
			settingsRowHeight = SettingsLayout.fitRowHeight(settingsViewportH, SettingsLayout.MAX_ROW_HEIGHT, SETTINGS_LEFT_COLUMN, SETTINGS_RIGHT_COLUMN);
			settingsContentH = SettingsLayout.tallestColumn(settingsRowHeight, SETTINGS_LEFT_COLUMN, SETTINGS_RIGHT_COLUMN);
			settingsScroll = clamp(settingsScroll, 0, settingsMaxScroll());
			int scrollbarW = settingsMaxScroll() > 0 ? 6 : 0;
			int columnW = (contentW - gap - scrollbarW) / 2;
			int leftX = contentX;
			int rightX = leftX + columnW + gap;
			int leftY = settingsViewportY - settingsScroll;
			leftY = drawGeneralSettings(g, tracker, config, contentMouseX, contentMouseY, leftX, leftY, columnW) + SettingsLayout.SECTION_GAP;
			leftY = drawPricingSettings(g, tracker, config, contentMouseX, contentMouseY, leftX, leftY, columnW) + SettingsLayout.SECTION_GAP;
			drawPrivacySettings(g, config, contentMouseX, contentMouseY, leftX, leftY, columnW);
			int rightY = settingsViewportY - settingsScroll;
			rightY = drawKuudraSettings(g, config, contentMouseX, contentMouseY, rightX, rightY, columnW) + SettingsLayout.SECTION_GAP;
			drawThemeSettings(g, config, contentMouseX, contentMouseY, rightX, rightY, columnW);
			if (scrollbarW > 0) {
				drawScrollbar(g, contentX + contentW - 4, settingsViewportY, 4, settingsViewportH, settingsScroll, settingsContentH, contentMouseX, contentMouseY);
			}
		} finally {
			g.disableScissor();
		}

		if (settingsDropdown != SettingsDropdown.NONE) beginSettingsPopupLayer(g);
		drawSettingsDropdownMenu(g, mouseX, mouseY);
		if (!settingsStatus.isBlank() && System.currentTimeMillis() < settingsStatusUntil) {
			String shown = font.plainSubstrByWidth(settingsStatus, contentW / 2);
			ThemePreviewDraw.text(g, font, shown, contentX + contentW - font.width(shown), titleY, dev.vy.drt.config.DrtTheme.color("text.secondary", TEXT_SECONDARY));
		}
	}

	// The Overlay tab reuses the Settings rows, including the HUD Display dropdown.
	private boolean settingsDropdownOpen() {
		return (activeTab == DrtNavTab.SETTINGS || activeTab == DrtNavTab.OVERLAY) && settingsDropdown != SettingsDropdown.NONE;
	}

	private void beginSettingsPopupLayer(GuiGraphicsExtractor g) {
		var pose = g.pose();
		pose.popMatrix();
		float scale = openingScale();
		RoundedUiRenderer.beginFrame(g, width, height, scale);
		pose.pushMatrix();
		pose.translate(width / 2.0F, height / 2.0F);
		pose.scale(scale);
		pose.translate(-width / 2.0F, -height / 2.0F);
	}

	private static final int SETTINGS_SECTION_H = SettingsLayout.SECTION_HEIGHT;
	private static final int SETTINGS_PANEL_INSET = 7;
	// Rows per section: General, Loot and Pricing, Account and Privacy on the left; Kuudra and Themes on the right.
	private static final int[] SETTINGS_LEFT_COLUMN = {5, 2, 2};
	private static final int[] SETTINGS_RIGHT_COLUMN = {6, 4};

	private int settingsMaxScroll() {
		return Math.max(0, settingsContentH - settingsViewportH);
	}

	private int drawGeneralSettings(GuiGraphicsExtractor g, DungeonRunTrackerFeature tracker, DrtConfig config,
		int mouseX, int mouseY, int x, int y, int w) {
		y = drawSettingsSection(g, x, y, w, "General", 5);
		int rowX = x + SETTINGS_PANEL_INSET;
		int rowW = w - SETTINGS_PANEL_INSET * 2;
		y = drawSettingsToggleRow(g, mouseX, mouseY, rowX, y, rowW, "HUD Overlay", "Toggle the overlay ON/OFF", tracker.isEnabled(), tracker::toggleHud);
		y = drawSettingsToggleRow(g, mouseX, mouseY, rowX, y, rowW, "Tracking Engine", "Toggle DRT's tracking", tracker.isTrackingEnabled(), tracker::toggleTracking);
		y = drawSettingsToggleRow(g, mouseX, mouseY, rowX, y, rowW, "Fancy Menu", "Toggle DRT's fancy Croesus menu", tracker.isFancyMenuEnabled(), tracker::toggleFancyMenu);
		y = drawSettingsToggleRow(g, mouseX, mouseY, rowX, y, rowW, "Croesus Overlay", "Toggle Croesus chest overlay", tracker.isCroesusOverlayEnabled(), tracker::toggleCroesusOverlay);
		y = drawSettingsDropdownRow(g, mouseX, mouseY, rowX, y, rowW, "HUD Display", "Change where DRT's overlay is displayed", visibilityLabel(config.hudVisibilityMode), SettingsDropdown.VISIBILITY);
		return y + SettingsLayout.SECTION_BOTTOM_PADDING;
	}

	private int drawPricingSettings(GuiGraphicsExtractor g, DungeonRunTrackerFeature tracker, DrtConfig config,
		int mouseX, int mouseY, int x, int y, int w) {
		y = drawSettingsSection(g, x, y, w, "Loot & Pricing", 2);
		int rowX = x + SETTINGS_PANEL_INSET;
		int rowW = w - SETTINGS_PANEL_INSET * 2;
		y = drawSettingsDropdownRow(g, mouseX, mouseY, rowX, y, rowW, "Bazaar Pricing", "Change whether BZ items use instant or buy order for prices", priceModeLabel(config.bazaarPriceMode), SettingsDropdown.PRICE);
		y = drawSettingsToggleRow(g, mouseX, mouseY, rowX, y, rowW, "Include Essence", "Include essence in profit", config.essenceCountsTowardProfit, tracker::toggleEssenceCountsTowardProfit);
		return y + SettingsLayout.SECTION_BOTTOM_PADDING;
	}

	private int drawPrivacySettings(GuiGraphicsExtractor g, DrtConfig config, int mouseX, int mouseY,
		int x, int y, int w) {
		y = drawSettingsSection(g, x, y, w, "Account & Privacy", 2);
		int rowX = x + SETTINGS_PANEL_INSET;
		int rowW = w - SETTINGS_PANEL_INSET * 2;
		y = drawSettingsToggleRow(g, mouseX, mouseY, rowX, y, rowW, "Statistics Sharing", "Share DRT stats logs", config.statsSyncEnabled,
			() -> { settingsDeleteConfirmUntil = 0L; DrtStatsSync.setEnabled(!DrtConfigManager.getConfig().statsSyncEnabled); });
		if (!config.statsSyncEnabled && DrtStatsSync.holdsUploads()) {
			boolean confirming = System.currentTimeMillis() < settingsDeleteConfirmUntil;
			return drawSettingsActionRow(g, mouseX, mouseY, rowX, y, rowW, "Uploaded Data", "Delete records uploaded by this installation.",
				confirming ? "Confirm Delete" : "Delete Uploads", true, () -> {
					if (System.currentTimeMillis() < settingsDeleteConfirmUntil) {
						settingsDeleteConfirmUntil = 0L;
						DrtStatsSync.deleteUploads(minecraft);
					} else settingsDeleteConfirmUntil = System.currentTimeMillis() + 5_000L;
				}) + SettingsLayout.SECTION_BOTTOM_PADDING;
		}
		String importTooltip = "Import eligible DRT records saved before Statistics Sharing automatically";
		if (DrtStatsSync.historicalImportEnabled()) {
			return drawSettingsStatusRow(g, mouseX, mouseY, rowX, y, rowW, "Historical Run Import", importTooltip, "Enabled")
				+ SettingsLayout.SECTION_BOTTOM_PADDING;
		}
		return drawSettingsActionRow(g, mouseX, mouseY, rowX, y, rowW, "Historical Run Import", importTooltip,
			"Import Records", config.statsSyncEnabled, () -> DrtStatsSync.beginHistoricalImport(minecraft))
			+ SettingsLayout.SECTION_BOTTOM_PADDING;
	}

	private int drawKuudraSettings(GuiGraphicsExtractor g, DrtConfig config, int mouseX, int mouseY, int x, int y, int w) {
		y = drawSettingsSection(g, x, y, w, "Kuudra", 6);
		int rowX = x + SETTINGS_PANEL_INSET;
		int rowW = w - SETTINGS_PANEL_INSET * 2;
		y = drawSettingsDropdownRow(g, mouseX, mouseY, rowX, y, rowW, "Faction", "Sets your faction in DRT", titleCase(config.kuudraFaction), SettingsDropdown.FACTION);
		y = drawSettingsStepperRow(g, mouseX, mouseY, rowX, y, rowW, "Essence Shards", "Set your bonus essence % from shards", "+" + config.crimsonEssenceBonusPercent + "%",
			() -> updateSetting(c -> c.crimsonEssenceBonusPercent--), () -> updateSetting(c -> c.crimsonEssenceBonusPercent++));
		y = drawSettingsToggleRow(g, mouseX, mouseY, rowX, y, rowW, "Kuudra Pet", "Toggle if DRT should use your Kuudra pet towards calculations", config.kuudraPetEnabled,
			() -> updateSetting(c -> c.kuudraPetEnabled = !c.kuudraPetEnabled));
		y = drawPetSettingsRow(g, config, mouseX, mouseY, rowX, y, rowW);
		y = drawSalvageSettingsRow(g, config, mouseX, mouseY, rowX, y, rowW);
		y = drawCoolForgedSettingsRow(g, config, mouseX, mouseY, rowX, y, rowW);
		return y + SettingsLayout.SECTION_BOTTOM_PADDING;
	}

	private int drawThemeSettings(GuiGraphicsExtractor g, DrtConfig config, int mouseX, int mouseY,
		int x, int y, int w) {
		y = drawSettingsSection(g, x, y, w, "Themes", 4);
		int rowX = x + SETTINGS_PANEL_INSET;
		int rowW = w - SETTINGS_PANEL_INSET * 2;
		y = drawSettingsDropdownRow(g, mouseX, mouseY, rowX, y, rowW, "Theme", "Select your theme", config.selectedTheme, SettingsDropdown.THEME);
		y = drawSettingsActionRow(g, mouseX, mouseY, rowX, y, rowW, "Themes Folder", "Open themes folder", "Open Themes Folder", true, this::openThemesFolder);
		y = drawSettingsActionRow(g, mouseX, mouseY, rowX, y, rowW, "Theme Discovery", "Re-scan for new themes", "Reload Themes", true, () -> reloadThemes(true));
		y = drawSettingsActionRow(g, mouseX, mouseY, rowX, y, rowW, "Theme Editor", "Visually edit shared theme colours", "Edit Theme", true,
			() -> minecraft.gui.setScreen(new DrtThemeEditorScreen(this)));
		return y + SettingsLayout.SECTION_BOTTOM_PADDING;
	}

	private int drawPetSettingsRow(GuiGraphicsExtractor g, DrtConfig config, int mouseX, int mouseY, int x, int y, int w) {
		String tooltip = "Set your Kuudra pet rarity and level";
		drawSettingsRowBase(g, x, y, w, "Pet Rarity / Level", tooltip, mouseX, mouseY);
		int right = x + w - 6;
		int stepW = 72;
		int controlY = settingsControlY(y);
		drawSettingsMiniStepper(g, mouseX, mouseY, right - stepW, controlY, stepW, Integer.toString(config.kuudraPetLevel),
			() -> updateSetting(c -> c.kuudraPetLevel--), () -> updateSetting(c -> c.kuudraPetLevel++), tooltip);
		int dropdownW = 80;
		drawSettingsDropdownControl(g, mouseX, mouseY, right - stepW - 4 - dropdownW, controlY, dropdownW,
			titleCase(config.kuudraPetRarity), SettingsDropdown.PET_RARITY, tooltip);
		return y + settingsRowHeight;
	}

	private int drawSalvageSettingsRow(GuiGraphicsExtractor g, DrtConfig config, int mouseX, int mouseY, int x, int y, int w) {
		String tooltip = "Toggle if DRT should use salvage or AH price for Kuudra drops";
		drawSettingsRowBase(g, x, y, w, "Force Salvage", tooltip, mouseX, mouseY);
		int gap = 3;
		int right = x + w - 6;
		int armourW = font.width("Armour") + 12;
		int wandsW = font.width("Wands") + 12;
		int equipmentW = font.width("Equipment") + 12;
		int equipmentX = right - equipmentW;
		int wandX = equipmentX - gap - wandsW;
		int armorX = wandX - gap - armourW;
		int controlY = settingsControlY(y);
		int controlH = settingsControlHeight();
		drawSettingsToggleButton(g, mouseX, mouseY, armorX, controlY, armourW, controlH, "Armour", config.forceSalvageArmor,
			() -> updateSetting(c -> c.forceSalvageArmor = !c.forceSalvageArmor), tooltip);
		drawSettingsToggleButton(g, mouseX, mouseY, wandX, controlY, wandsW, controlH, "Wands", config.forceSalvageWands,
			() -> updateSetting(c -> c.forceSalvageWands = !c.forceSalvageWands), tooltip);
		drawSettingsToggleButton(g, mouseX, mouseY, equipmentX, controlY, equipmentW, controlH, "Equipment", config.forceSalvageEquipment,
			() -> updateSetting(c -> c.forceSalvageEquipment = !c.forceSalvageEquipment), tooltip);
		return y + settingsRowHeight;
	}

	private int drawCoolForgedSettingsRow(GuiGraphicsExtractor g, DrtConfig config, int mouseX, int mouseY, int x, int y, int w) {
		String tooltip = "Set your Cool Forged level";
		drawSettingsRowBase(g, x, y, w, "Cool Forged / Level", tooltip, mouseX, mouseY);
		int right = x + w - 6;
		int stepW = 72;
		int controlY = settingsControlY(y);
		drawSettingsMiniStepper(g, mouseX, mouseY, right - stepW, controlY, stepW, Integer.toString(config.coolForgedLevel),
			() -> updateSetting(c -> c.coolForgedLevel--), () -> updateSetting(c -> c.coolForgedLevel++), tooltip);
		drawSettingsSwitch(g, mouseX, mouseY, right - stepW - 4 - 30,
			y + (settingsRowHeight - 14) / 2, config.coolForgedEnabled,
			() -> updateSetting(c -> c.coolForgedEnabled = !c.coolForgedEnabled), tooltip);
		return y + settingsRowHeight;
	}

	private void drawSettingsMiniStepper(GuiGraphicsExtractor g, int mouseX, int mouseY, int x, int y, int w,
		String value, Runnable decrease, Runnable increase, String tooltip) {
		int controlH = settingsControlHeight();
		int side = controlH;
		drawSettingsButton(g, mouseX, mouseY, x, y, side, controlH, "-", false, decrease, tooltip);
		roundedPanel(g, x + side, y, w - side * 2, controlH, 4, dev.vy.drt.config.DrtTheme.color("input.background", PANEL_BG_ALT), dev.vy.drt.config.DrtTheme.color("control.border", PANEL_BORDER));
		String shown = font.plainSubstrByWidth(value, Math.max(2, w - side * 2 - 4));
		ThemePreviewDraw.text(g, font, shown, x + side + (w - side * 2 - font.width(shown)) / 2,
			DrtUiText.centeredTextY(font, y, controlH), dev.vy.drt.config.DrtTheme.color("input.text", TEXT_PRIMARY));
		drawSettingsButton(g, mouseX, mouseY, x + w - side, y, side, controlH, "+", false, increase, tooltip);
	}

	private int settingsControlHeight() {
		return clamp(settingsRowHeight - 4, 17, 19);
	}

	private int settingsControlY(int rowY) {
		return rowY + (settingsRowHeight - settingsControlHeight()) / 2;
	}

	private int drawSettingsSection(GuiGraphicsExtractor g, int x, int y, int w, String title, int rows) {
		int panelH = SettingsLayout.sectionHeight(settingsRowHeight, rows);
		roundedPanel(g, x, y, w, panelH, 4, dev.vy.drt.config.DrtTheme.color("panel.background", PANEL_BG), dev.vy.drt.config.DrtTheme.color("panel.border", PANEL_BORDER));
		ThemePreviewDraw.text(g, font, title.toUpperCase(Locale.ROOT), x + SETTINGS_PANEL_INSET,
			y + SettingsLayout.SECTION_HEADING_TOP_PADDING, dev.vy.drt.config.DrtTheme.color("text.sectionTitle", 0xFF9CA8FF), true);
		settingsSectionFirstRowY = y + SETTINGS_SECTION_H;
		return y + SETTINGS_SECTION_H;
	}

	private void drawSettingsRowBase(GuiGraphicsExtractor g, int x, int y, int w, String label, String description, int mouseX, int mouseY) {
		boolean hovered = contains(x, y, w, settingsRowHeight, mouseX, mouseY);
		if (hovered) RoundedUiRenderer.fill(g, x, y, w, settingsRowHeight, 4, dev.vy.drt.config.DrtTheme.color("row.hover", 0xFF1A1A2D));
		if (y > settingsSectionFirstRowY) {
			RoundedUiRenderer.line(x + 4, y, x + w - 4, y, 1, dev.vy.drt.config.DrtTheme.color("separator", PANEL_BORDER));
		}
		ThemePreviewDraw.text(g, font, label, x + 4, DrtUiText.centeredTextY(font, y, settingsRowHeight),
			dev.vy.drt.config.DrtTheme.color("text.primary", TEXT_PRIMARY));
		addClippedHoverTarget(x, y, w, settingsRowHeight, settingsViewportY, settingsViewportH, description);
	}

	private int drawSettingsToggleRow(GuiGraphicsExtractor g, int mouseX, int mouseY, int x, int y, int w,
		String label, String description, boolean enabled, Runnable action) {
		drawSettingsRowBase(g, x, y, w, label, description, mouseX, mouseY);
		drawSettingsSwitch(g, mouseX, mouseY, x + w - 36, y + (settingsRowHeight - 14) / 2,
			enabled, action, description);
		return y + settingsRowHeight;
	}

	private void drawSettingsSwitch(GuiGraphicsExtractor g, int mouseX, int mouseY, int x, int y,
		boolean enabled, Runnable action, String tooltip) {
		int w = 30;
		int h = 14;
		boolean hovered = contains(x, y, w, h, mouseX, mouseY);
		int track = enabled
			? dev.vy.drt.config.DrtTheme.color("toggle.trackOn", hovered ? 0xFF6A6AB0 : BORDER_ACTIVE)
			: dev.vy.drt.config.DrtTheme.color("toggle.trackOff", hovered ? PANEL_BORDER_HOVER : PANEL_BORDER);
		RoundedUiRenderer.fillBordered(g, x, y, w, h, 7, dev.vy.drt.config.DrtTheme.color("toggle.border", PANEL_BORDER), track);
		int knobX = enabled ? x + w - 12 : x + 2;
		RoundedUiRenderer.fill(g, knobX, y + 2, 10, 10, 5, enabled ? dev.vy.drt.config.DrtTheme.color("toggle.knobOn", TEXT_PRIMARY) : dev.vy.drt.config.DrtTheme.color("toggle.knobOff", TEXT_SECONDARY));
		addClippedClickTarget(x, y, w, h, settingsViewportY, settingsViewportH, 0, action);
		addClippedHoverTarget(x, y, w, h, settingsViewportY, settingsViewportH, tooltip);
	}

	private void drawSettingsToggleButton(GuiGraphicsExtractor g, int mouseX, int mouseY, int x, int y, int w, int h,
		String label, boolean enabled, Runnable action, String tooltip) {
		boolean hovered = contains(x, y, w, h, mouseX, mouseY);
		int bg = enabled ? dev.vy.drt.config.DrtTheme.color("control.active", 0xFF202A52) : hovered ? dev.vy.drt.config.DrtTheme.color("control.hover", 0xFF1D2139) : dev.vy.drt.config.DrtTheme.color("control.background", PANEL_BG_ALT);
		int border = enabled ? dev.vy.drt.config.DrtTheme.color("control.borderActive", BORDER_ACTIVE) : hovered ? dev.vy.drt.config.DrtTheme.color("control.borderHover", PANEL_BORDER_HOVER) : dev.vy.drt.config.DrtTheme.color("control.border", PANEL_BORDER);
		int text = enabled ? dev.vy.drt.config.DrtTheme.color("control.textActive", TEXT_PRIMARY) : dev.vy.drt.config.DrtTheme.color("control.text", TEXT_SECONDARY);
		roundedPanel(g, x, y, w, h, 4, bg, border);
		ThemePreviewDraw.text(g, font, label, x + (w - font.width(label)) / 2, DrtUiText.centeredTextY(font, y, h), text, true);
		addClippedClickTarget(x, y, w, h, settingsViewportY, settingsViewportH, 0, action);
		addClippedHoverTarget(x, y, w, h, settingsViewportY, settingsViewportH, tooltip);
	}

	private int drawSettingsDropdownRow(GuiGraphicsExtractor g, int mouseX, int mouseY, int x, int y, int w,
		String label, String description, String value, SettingsDropdown dropdown) {
		drawSettingsRowBase(g, x, y, w, label, description, mouseX, mouseY);
		int controlW = Math.min(112, Math.max(82, w / 2 - 10));
		int bx = x + w - controlW - 6;
		drawSettingsDropdownControl(g, mouseX, mouseY, bx, settingsControlY(y), controlW, value, dropdown, description);
		return y + settingsRowHeight;
	}

	private void drawSettingsDropdownControl(GuiGraphicsExtractor g, int mouseX, int mouseY, int x, int y, int w,
		String value, SettingsDropdown dropdown, String tooltip) {
		int h = settingsControlHeight();
		boolean open = settingsDropdown == dropdown;
		boolean hovered = contains(x, y, w, h, mouseX, mouseY);
		int bg = open ? dev.vy.drt.config.DrtTheme.color("control.active", 0xFF202A52) : hovered ? dev.vy.drt.config.DrtTheme.color("control.hover", 0xFF1D2139) : dev.vy.drt.config.DrtTheme.color("control.background", PANEL_BG_ALT);
		int border = open ? dev.vy.drt.config.DrtTheme.color("control.borderActive", BORDER_ACTIVE) : hovered ? dev.vy.drt.config.DrtTheme.color("control.borderHover", PANEL_BORDER_HOVER) : dev.vy.drt.config.DrtTheme.color("control.border", PANEL_BORDER);
		roundedPanel(g, x, y, w, h, 4, bg, border);
		String shown = font.plainSubstrByWidth(value, Math.max(4, w - 27));
		ThemePreviewDraw.text(g, font, shown, x + 7, DrtUiText.centeredTextY(font, y, h), open ? dev.vy.drt.config.DrtTheme.color("control.textActive", 0xFFFFFFFF) : dev.vy.drt.config.DrtTheme.color("input.text", TEXT_PRIMARY));
		drawSettingsChevron(g, x + w - 11, y + h / 2, open, open ? dev.vy.drt.config.DrtTheme.color("text.accent", 0xFF9CA8FF) : dev.vy.drt.config.DrtTheme.color("text.muted", TEXT_MUTED));
		addClippedClickTarget(x, y, w, h, settingsViewportY, settingsViewportH, 0, () -> {
				boolean opening = settingsDropdown != dropdown;
				settingsDropdown = opening ? dropdown : SettingsDropdown.NONE;
				if (opening) settingsDropdownScroll = 0;
				settingsDropdownX = x;
				settingsDropdownAnchorY = y;
				settingsDropdownAnchorH = h;
				settingsDropdownY = y + h + 1;
				settingsDropdownW = w;
			});
		addClippedHoverTarget(x, y, w, h, settingsViewportY, settingsViewportH, tooltip);
		if (settingsDropdown == dropdown) {
			settingsDropdownX = x;
			settingsDropdownAnchorY = y;
			settingsDropdownAnchorH = h;
			settingsDropdownY = y + h + 1;
			settingsDropdownW = w;
		}
	}

	private void drawSettingsChevron(GuiGraphicsExtractor g, int centerX, int centerY, boolean open, int color) {
		int tipY = open ? centerY - 2 : centerY + 2;
		int edgeY = open ? centerY + 1 : centerY - 1;
		RoundedUiRenderer.line(centerX - 3, edgeY, centerX, tipY, 1, color);
		RoundedUiRenderer.line(centerX, tipY, centerX + 3, edgeY, 1, color);
	}

	private int drawSettingsStepperRow(GuiGraphicsExtractor g, int mouseX, int mouseY, int x, int y, int w,
		String label, String description, String value, Runnable decrease, Runnable increase) {
		drawSettingsRowBase(g, x, y, w, label, description, mouseX, mouseY);
		int right = x + w - 6;
		int controlH = settingsControlHeight();
		int controlY = settingsControlY(y);
		drawSettingsButton(g, mouseX, mouseY, right - controlH, controlY, controlH, controlH, "+", false, increase, description);
		int valueW = Math.min(88, Math.max(62, w / 3));
		roundedPanel(g, right - controlH - valueW, controlY, valueW, controlH, 4, dev.vy.drt.config.DrtTheme.color("input.background", PANEL_BG_ALT), dev.vy.drt.config.DrtTheme.color("control.border", PANEL_BORDER));
		String shown = font.plainSubstrByWidth(value, valueW - 8);
		ThemePreviewDraw.text(g, font, shown, right - controlH - valueW + (valueW - font.width(shown)) / 2,
			DrtUiText.centeredTextY(font, controlY, controlH), dev.vy.drt.config.DrtTheme.color("input.text", TEXT_PRIMARY));
		drawSettingsButton(g, mouseX, mouseY, right - controlH - valueW - controlH, controlY, controlH, controlH,
			"-", false, decrease, description);
		return y + settingsRowHeight;
	}

	private int drawSettingsActionRow(GuiGraphicsExtractor g, int mouseX, int mouseY, int x, int y, int w,
		String label, String description, String button, boolean enabled, Runnable action) {
		drawSettingsRowBase(g, x, y, w, label, description, mouseX, mouseY);
		int buttonW = Math.min(w / 2, Math.max(78, font.width(button) + 12));
		drawSettingsButton(g, mouseX, mouseY, x + w - buttonW - 6, settingsControlY(y), buttonW, settingsControlHeight(), button, false,
			enabled ? action : () -> {}, description, enabled);
		return y + settingsRowHeight;
	}

	private int drawSettingsStatusRow(GuiGraphicsExtractor g, int mouseX, int mouseY, int x, int y, int w,
		String label, String description, String status) {
		drawSettingsRowBase(g, x, y, w, label, description, mouseX, mouseY);
		int statusW = Math.max(58, font.width(status) + 14);
		int statusH = settingsControlHeight();
		int statusX = x + w - statusW - 6;
		int statusY = settingsControlY(y);
		roundedPanel(g, statusX, statusY, statusW, statusH, 4, dev.vy.drt.config.DrtTheme.color("control.background", PANEL_BG_ALT), dev.vy.drt.config.DrtTheme.color("control.border", PANEL_BORDER));
		ThemePreviewDraw.text(g, font, status, statusX + (statusW - font.width(status)) / 2,
			DrtUiText.centeredTextY(font, statusY, statusH), dev.vy.drt.config.DrtTheme.color("status.success", 0xFF8BCF9A));
		addClippedHoverTarget(statusX, statusY, statusW, statusH, settingsViewportY, settingsViewportH, description);
		return y + settingsRowHeight;
	}

	private void drawSettingsButton(GuiGraphicsExtractor g, int mouseX, int mouseY, int x, int y, int w, int h,
		String label, boolean active, Runnable action, String tooltip) {
		drawSettingsButton(g, mouseX, mouseY, x, y, w, h, label, active, action, tooltip, true);
	}

	private void drawSettingsButton(GuiGraphicsExtractor g, int mouseX, int mouseY, int x, int y, int w, int h,
		String label, boolean active, Runnable action, String tooltip, boolean enabled) {
		boolean hovered = enabled && contains(x, y, w, h, mouseX, mouseY);
		int bg = !enabled ? dev.vy.drt.config.DrtTheme.color("control.disabled", PANEL_BG) : active ? dev.vy.drt.config.DrtTheme.color("control.active", 0xFF202A52) : hovered ? dev.vy.drt.config.DrtTheme.color("control.hover", 0xFF1D2139) : dev.vy.drt.config.DrtTheme.color("control.background", PANEL_BG_ALT);
		int border = !enabled ? dev.vy.drt.config.DrtTheme.color("control.borderDisabled", PANEL_BORDER) : active ? dev.vy.drt.config.DrtTheme.color("control.borderActive", BORDER_ACTIVE)
			: hovered ? dev.vy.drt.config.DrtTheme.color("control.borderHover", PANEL_BORDER_HOVER) : dev.vy.drt.config.DrtTheme.color("control.border", PANEL_BORDER);
		roundedPanel(g, x, y, w, h, 4, bg, border);
		String shown = font.plainSubstrByWidth(label, Math.max(4, w - 8));
		ThemePreviewDraw.text(g, font, shown, x + (w - font.width(shown)) / 2, DrtUiText.centeredTextY(font, y, h),
			enabled ? active ? dev.vy.drt.config.DrtTheme.color("control.textActive", TEXT_PRIMARY) : dev.vy.drt.config.DrtTheme.color("control.text", TEXT_SECONDARY) : dev.vy.drt.config.DrtTheme.color("control.textDisabled", TEXT_MUTED));
		if (enabled) addClippedClickTarget(x, y, w, h, settingsViewportY, settingsViewportH, 0, action);
		if (enabled && tooltip != null) addClippedHoverTarget(x, y, w, h, settingsViewportY, settingsViewportH, tooltip);
	}

	private void drawSettingsDropdownMenu(GuiGraphicsExtractor g, int mouseX, int mouseY) {
		if (settingsDropdown == SettingsDropdown.NONE) {
			settingsDropdownH = 0;
			return;
		}
		List<String> options = settingsDropdownOptions();
		int rowH = 18;
		settingsDropdownVisibleRows = SettingsLayout.dropdownVisibleRows(options.size(), settingsViewportH, rowH);
		settingsDropdownScroll = clamp(settingsDropdownScroll, 0, Math.max(0, options.size() - settingsDropdownVisibleRows));
		settingsDropdownH = settingsDropdownVisibleRows * rowH + 2;
		int y = settingsDropdownY;
		int viewportBottom = settingsViewportY + settingsViewportH;
		if (y + settingsDropdownH > viewportBottom) y = settingsDropdownAnchorY - settingsDropdownH - 1;
		y = clamp(y, settingsViewportY, Math.max(settingsViewportY, viewportBottom - settingsDropdownH));
		settingsDropdownY = y;
		roundedPanel(g, settingsDropdownX, y, settingsDropdownW, settingsDropdownH, 4, dev.vy.drt.config.DrtTheme.color("popup.background", PANEL_BG), dev.vy.drt.config.DrtTheme.color("popup.border", BORDER_ACTIVE));
		String selected = selectedSettingsDropdownValue();
		for (int i = 0; i < settingsDropdownVisibleRows; i++) {
			String option = options.get(settingsDropdownScroll + i);
			int rowY = y + 1 + i * rowH;
			boolean hovered = contains(settingsDropdownX + 1, rowY, settingsDropdownW - 2, rowH, mouseX, mouseY);
			boolean active = option.equalsIgnoreCase(selected);
			if (hovered || active) RoundedUiRenderer.fill(g, settingsDropdownX + 2, rowY + 1,
				settingsDropdownW - 4, rowH - 2, 3, active ? dev.vy.drt.config.DrtTheme.color("control.active", 0xFF202A52) : dev.vy.drt.config.DrtTheme.color("control.hover", 0xFF1D2139));
			ThemePreviewDraw.text(g, font, option, settingsDropdownX + 7, rowY + 5, active ? dev.vy.drt.config.DrtTheme.color("control.textActive", TEXT_PRIMARY) : dev.vy.drt.config.DrtTheme.color("control.text", TEXT_SECONDARY));
			clickTargets.add(new ClickTarget(settingsDropdownX + 1, rowY, settingsDropdownW - 2, rowH, 0,
				() -> selectSettingsDropdown(option)));
		}
	}

	private List<String> settingsDropdownOptions() {
		return switch (settingsDropdown) {
			case VISIBILITY -> List.of("Default", "Global", "DHub");
			case PRICE -> List.of("Instant", "Order");
			case FACTION -> List.of("Mage", "Barbarian");
			case PET_RARITY -> List.of("Common", "Uncommon", "Rare", "Epic", "Legendary");
			case THEME -> settingsThemes;
			case NONE -> List.of();
		};
	}

	private String selectedSettingsDropdownValue() {
		DrtConfig config = DrtConfigManager.getConfig();
		return switch (settingsDropdown) {
			case VISIBILITY -> visibilityLabel(config.hudVisibilityMode);
			case PRICE -> priceModeLabel(config.bazaarPriceMode);
			case FACTION -> titleCase(config.kuudraFaction);
			case PET_RARITY -> titleCase(config.kuudraPetRarity);
			case THEME -> config.selectedTheme;
			case NONE -> "";
		};
	}

	private void selectSettingsDropdown(String option) {
		SettingsDropdown dropdown = settingsDropdown;
		settingsDropdown = SettingsDropdown.NONE;
		switch (dropdown) {
			case VISIBILITY -> DrtClient.getTracker().setHudVisibilityMode(option);
			case PRICE -> updateSetting(c -> c.bazaarPriceMode = option);
			case FACTION -> updateSetting(c -> c.kuudraFaction = option);
			case PET_RARITY -> updateSetting(c -> c.kuudraPetRarity = option);
			case THEME -> updateSetting(c -> c.selectedTheme = option);
			case NONE -> { }
		}
	}

	private void updateSetting(java.util.function.Consumer<DrtConfig> update) {
		DrtConfigManager.updateUserSettings(update);
	}

	private String visibilityLabel(String mode) {
		if (mode == null) return "Default";
		return switch (mode.trim().toUpperCase(Locale.ROOT)) {
			case "GLOBAL" -> "Global";
			case "DHUB" -> "DHub";
			default -> "Default";
		};
	}

	private String priceModeLabel(String mode) {
		return mode != null && mode.equalsIgnoreCase("ORDER") ? "Order" : "Instant";
	}

	private String titleCase(String value) {
		if (value == null || value.isBlank()) return "";
		String lower = value.trim().toLowerCase(Locale.ROOT);
		return Character.toUpperCase(lower.charAt(0)) + lower.substring(1);
	}

	private void reloadThemes(boolean announce) {
		try {
			settingsThemes = ThemeDiscovery.discover(DrtConfigManager.themesDirectory());
			dev.vy.drt.config.DrtTheme.reload();
			DrtConfig config = DrtConfigManager.getConfig();
			if (settingsThemes.stream().noneMatch(name -> name.equalsIgnoreCase(config.selectedTheme))) {
				updateSetting(c -> c.selectedTheme = ThemeDiscovery.DEFAULT_THEME);
			}
			if (announce && !dev.vy.drt.config.DrtTheme.diagnostic().isEmpty()) setSettingsStatus(dev.vy.drt.config.DrtTheme.diagnostic());
			else if (announce) setSettingsStatus("Found " + settingsThemes.size() + " theme option" + (settingsThemes.size() == 1 ? "" : "s") + ".");
		} catch (IOException | RuntimeException exception) {
			settingsThemes = List.of(ThemeDiscovery.DEFAULT_THEME);
			setSettingsStatus("Could not reload themes.");
		}
	}

	private void openThemesFolder() {
		Path themes = DrtConfigManager.themesDirectory();
		try {
			Files.createDirectories(themes);
			Util.getPlatform().openUri(themes.toUri());
			setSettingsStatus("Opened themes folder.");
		} catch (IOException | RuntimeException exception) {
			setSettingsStatus("Could not open themes folder.");
		}
	}

	private void setSettingsStatus(String status) {
		settingsStatus = status == null ? "" : status;
		settingsStatusUntil = System.currentTimeMillis() + 4_000L;
	}

	private void drawCustomButton(GuiGraphicsExtractor g, int x, int y, int w, int h, String label, boolean active, int mouseX, int mouseY, Runnable action) {
		boolean hovered = contains(x, y, w, h, mouseX, mouseY);
		int bg = active ? dev.vy.drt.config.DrtTheme.color("control.active", 0xFF202A52) : hovered ? dev.vy.drt.config.DrtTheme.color("control.hover", 0xFF1D2139) : dev.vy.drt.config.DrtTheme.color("control.background", 0xFF141425);
		int border = active ? dev.vy.drt.config.DrtTheme.color("control.borderActive", BORDER_ACTIVE) : hovered ? dev.vy.drt.config.DrtTheme.color("control.borderHover", PANEL_BORDER_HOVER) : dev.vy.drt.config.DrtTheme.color("control.border", PANEL_BORDER);
		roundedPanel(g, x, y, w, h, 4, bg, border);
		int textColor = active ? dev.vy.drt.config.DrtTheme.color("control.textActive", 0xFF9CA8FF) : hovered ? dev.vy.drt.config.DrtTheme.color("control.textHover", TEXT_PRIMARY) : dev.vy.drt.config.DrtTheme.color("control.text", TEXT_SECONDARY);
		int tx = x + (w - font.width(label)) / 2;
		int ty = DrtUiText.centeredTextY(font, y, h);
		ThemePreviewDraw.text(g, font, label, tx, ty, textColor);
		clickTargets.add(new ClickTarget(x, y, w, h, 0, action));
	}

	// ── Loot Tab (Loot Log Integration) ───────────────────────────────────────

	private void drawTabs(GuiGraphicsExtractor g, int mouseX, int mouseY) {
		DungeonRunRecord selected = selectedPurchasedRecord();
		int addW = tabWidth("+");
		int editReserve = selected == null || isOfferRecord(selected) ? 0 : font.width("Edit") + 17;
		int actionLeft = ox + winW - MARGIN - addW - editReserve;
		searchH = TAB_H;
		searchW = clamp(winW / 6, 86, 132);
		searchX = actionLeft - searchW - 6;
		searchY = tabBarY;

		int tabsX = ox + MARGIN;
		int tabsRight = searchX - 6;
		int arrowW = Math.max(tabWidth("<"), tabWidth(">"));
		int totalTabs = availableTabs.size() + 1;
		int allTabsW = 0;
		for (int i = 0; i < totalTabs; i++) {
			String label = i == 0 ? "All" : availableTabs.get(i - 1).name();
			allTabsW += tabWidth(label) + (i == totalTabs - 1 ? 0 : 2);
		}
		boolean overflow = allTabsW > tabsRight - tabsX;
		if (!overflow) floorTabOffset = 0;
		else floorTabOffset = clamp(floorTabOffset, 0, Math.max(0, totalTabs - 1));
		if (overflow && floorTabOffset > 0) {
			drawTab(g, mouseX, mouseY, tabsX, tabBarY, "<", false, () -> floorTabOffset = Math.max(0, floorTabOffset - 1));
			tabsX += arrowW + 2;
		}
		int index = floorTabOffset;
		while (index < totalTabs) {
			String label = index == 0 ? "All" : availableTabs.get(index - 1).name();
			int reserveRightArrow = overflow && index < totalTabs - 1 ? arrowW + 2 : 0;
			if (tabsX + tabWidth(label) + reserveRightArrow > tabsRight) break;
			DungeonFloor floor = index == 0 ? null : availableTabs.get(index - 1);
			drawTab(g, mouseX, mouseY, tabsX, tabBarY, label, floor == selectedTab, () -> selectTab(floor));
			tabsX += tabWidth(label) + 2;
			index++;
		}
		if (overflow && index < totalTabs) {
			int arrowX = tabsRight - arrowW;
			drawTab(g, mouseX, mouseY, arrowX, tabBarY, ">", false, () -> floorTabOffset = Math.min(totalTabs - 1, floorTabOffset + 1));
		}

		boolean hovered = contains(searchX, searchY, searchW, searchH, mouseX, mouseY);
		int searchBorder = lootSearchFocused ? dev.vy.drt.config.DrtTheme.color("control.borderActive", BORDER_ACTIVE) : hovered ? dev.vy.drt.config.DrtTheme.color("control.borderHover", PANEL_BORDER_HOVER) : dev.vy.drt.config.DrtTheme.color("control.border", PANEL_BORDER);
		roundedPanel(g, searchX, searchY, searchW, searchH, 4, dev.vy.drt.config.DrtTheme.color("input.background", 0xFF10101F), searchBorder);
		String searchText = lootSearchBuffer.isBlank() ? "Search loot..." : lootSearchBuffer;
		int searchColor = lootSearchBuffer.isBlank() ? dev.vy.drt.config.DrtTheme.color("input.placeholder", TEXT_MUTED) : dev.vy.drt.config.DrtTheme.color("input.text", TEXT_PRIMARY);
		String clipped = font.plainSubstrByWidth(searchText, Math.max(8, searchW - 14));
		ThemePreviewDraw.text(g, font, clipped, searchX + 7, DrtUiText.centeredTextY(font, searchY, searchH), searchColor);
		if (lootSearchFocused && (System.currentTimeMillis() / 500L) % 2L == 0L) {
			int caretX = searchX + 7 + font.width(clipped);
			ThemePreviewDraw.fill(g, caretX, searchY + 4, caretX + 1, searchY + searchH - 4, dev.vy.drt.config.DrtTheme.color("input.caret", 0xFF9CA8FF));
		}
		clickTargets.add(new ClickTarget(searchX, searchY, searchW, searchH, 0, () -> lootSearchFocused = true));
	}

	private int tabWidth(String label) {
		return font.width(label) + 10;
	}

	private void drawTab(GuiGraphicsExtractor g, int mouseX, int mouseY, int x, int y, String label, boolean active, Runnable onClick) {
		int w = tabWidth(label);
		boolean hovered = contains(x, y, w, TAB_H, mouseX, mouseY);
		int bg = active ? dev.vy.drt.config.DrtTheme.color("tab.activeBackground", 0xFF202A52) : hovered ? dev.vy.drt.config.DrtTheme.color("tab.hoverBackground", 0xFF17172A) : dev.vy.drt.config.DrtTheme.color("tab.background", 0xFF0F0F1D);
		int border = active ? dev.vy.drt.config.DrtTheme.color("tab.activeBorder", 0xFF6A6AB0) : hovered ? dev.vy.drt.config.DrtTheme.color("tab.hoverBorder", PANEL_BORDER_HOVER) : dev.vy.drt.config.DrtTheme.color("tab.border", 0xFF343458);
		roundedPanel(g, x, y, w, TAB_H, 4, bg, border);
		int textColor = tabTextColor(label, active, hovered);
		ThemePreviewDraw.text(g, font, label, x + (w - font.width(label)) / 2, DrtUiText.centeredTextY(font, y, TAB_H), textColor);
		clickTargets.add(new ClickTarget(x, y, w, TAB_H, 0, onClick));
	}

	private int tabTextColor(String label, boolean active, boolean hovered) {
		if (active) return dev.vy.drt.config.DrtTheme.color("tab.textActive", TEXT_PRIMARY);
		if (hovered) return dev.vy.drt.config.DrtTheme.color("tab.textHover", 0xFFC2C8E0);
		int floorColor = floorTypeColor(label);
		return floorColor == dev.vy.drt.config.DrtTheme.color("text.accent", 0xFF9CA8FF) ? dev.vy.drt.config.DrtTheme.color("tab.text", TEXT_SECONDARY) : floorColor;
	}

	private void drawAddButton(GuiGraphicsExtractor g, int mouseX, int mouseY) {
		String label = "+";
		int w = tabWidth(label);
		int x = ox + winW - MARGIN - w;
		int y = tabBarY;
		boolean hovered = contains(x, y, w, TAB_H, mouseX, mouseY);
		int bg = hovered ? dev.vy.drt.config.DrtTheme.color("button.positiveHover", 0xFF1A2A1A) : dev.vy.drt.config.DrtTheme.color("button.positiveBackground", 0xFF112011);
		int border = hovered ? dev.vy.drt.config.DrtTheme.color("button.positiveBorderHover", 0xFF4A8A4A) : dev.vy.drt.config.DrtTheme.color("button.positiveBorder", 0xFF2D5A2D);
		roundedPanel(g, x, y, w, TAB_H, 4, bg, border);
		int tx = x + (w - font.width(label)) / 2;
		int ty = DrtUiText.centeredTextY(font, y, TAB_H);
		ThemePreviewDraw.text(g, font, label, tx, ty, hovered ? dev.vy.drt.config.DrtTheme.color("button.positiveTextHover", 0xFF8AE88A) : dev.vy.drt.config.DrtTheme.color("button.positiveText", 0xFF5AC05A));
		clickTargets.add(new ClickTarget(x, y, w, TAB_H, 0, () -> {
			if (minecraft != null) {
				minecraft.gui.setScreen(new ManualRunEntryScreen(this));
			}
		}));

		DungeonRunRecord selected = selectedPurchasedRecord();
		if (selected != null && !isOfferRecord(selected)) {
			String editLabel = "Edit";
			int editW = font.width(editLabel) + 12;
			int editX = x - editW - 5;
			boolean editHovered = contains(editX, y, editW, TAB_H, mouseX, mouseY);
			roundedPanel(g, editX, y, editW, TAB_H, 4, editHovered ? dev.vy.drt.config.DrtTheme.color("control.hover", 0xFF23233B) : dev.vy.drt.config.DrtTheme.color("control.background", 0xFF151526), editHovered ? dev.vy.drt.config.DrtTheme.color("control.borderHover", PANEL_BORDER_HOVER) : dev.vy.drt.config.DrtTheme.color("control.border", PANEL_BORDER));
			ThemePreviewDraw.text(g, font, editLabel, editX + (editW - font.width(editLabel)) / 2, ty, editHovered ? dev.vy.drt.config.DrtTheme.color("control.textHover", TEXT_PRIMARY) : dev.vy.drt.config.DrtTheme.color("control.text", TEXT_SECONDARY));
			clickTargets.add(new ClickTarget(editX, y, editW, TAB_H, 0, () -> {
				if (minecraft != null) minecraft.gui.setScreen(new ManualRunEntryScreen(this, selected));
			}));
		}
	}

	private void drawHeader(GuiGraphicsExtractor g, int mouseX, int mouseY, int headerY) {
		int w = winW - MARGIN * 2;
		int x = ox + MARGIN;
		roundedPanel(g, x, headerY, w, HEADER_H, 4, dev.vy.drt.config.DrtTheme.color("panel.alternate", PANEL_BG_ALT), dev.vy.drt.config.DrtTheme.color("panel.border", PANEL_BORDER));

		RunSelection sel = filterSelection();
		long cost = sel.totalCostCoins();
		long value = sel.totalValueCoins();
		long profit = sel.totalProfitCoins();
		DungeonRunRecord selected = selectedPurchasedRecord();
		boolean incomplete = selected != null && selected.hasIncompleteKnownValue() && sel.records().size() == 1;

		int colW = w / 4;
		drawHeaderStat(g, x, headerY, colW, "Total Cost", formatCoins(cost), 0xFFFFCC00);
		drawHeaderStat(g, x + colW, headerY, colW, "Total Value", formatCoins(value), incomplete ? 0xFFFFB45C : 0xFF8BCF9A);
		drawHeaderStat(g, x + colW * 2, headerY, colW, "Total Profit",
			incomplete ? "Incomplete" : formatSigned(profit),
			incomplete ? 0xFFFFB45C : profitColor(profit));

		String roiStr = incomplete ? "N/A" : (cost > 0) ? String.format(Locale.ROOT, "%+.1f%%", (profit / (double) cost) * 100D) : "N/A";
		drawHeaderStat(g, x + colW * 3, headerY, colW, "ROI", roiStr, incomplete ? 0xFFFFB45C : profitColor(profit));
		for (int i = 1; i < 4; i++) {
			int separatorX = x + colW * i;
			RoundedUiRenderer.line(separatorX, headerY + 6, separatorX, headerY + HEADER_H - 6, 1.0F, dev.vy.drt.config.DrtTheme.color("separator", 0x442D2D50));
		}
		hoverTargets.add(new HoverTarget(x, headerY, colW, HEADER_H, "Exact cost: " + formatExactCoins(cost)));
		hoverTargets.add(new HoverTarget(x + colW, headerY, colW, HEADER_H,
			incomplete ? "Known value only. One or more items still have no sell price." : "Exact value: " + formatExactCoins(value)));
		hoverTargets.add(new HoverTarget(x + colW * 2, headerY, colW, HEADER_H,
			incomplete ? "Profit stays incomplete until every item has a known price." : "Exact profit: " + formatExactCoins(profit)));
		hoverTargets.add(new HoverTarget(x + colW * 3, headerY, w - colW * 3, HEADER_H, "Return on investment: profit divided by total cost"));
	}

	private void drawHeaderStat(GuiGraphicsExtractor g, int x, int y, int w, String label, String value, int valueColor) {
		int labelY = y + 5;
		int valueY = y + 17;
		int labelX = x + (w - font.width(label)) / 2;
		int valueX = x + (w - font.width(value)) / 2;
		ThemePreviewDraw.text(g, font, label, labelX, labelY, dev.vy.drt.config.DrtTheme.color("text.metricLabel", TEXT_MUTED));
		ThemePreviewDraw.text(g, font, value, valueX, valueY, valueColor);
	}

	private void drawSectionLabels(GuiGraphicsExtractor g, int sectionY) {
		int sectionH = SECTION_H;
		roundedPanel(g, listX, sectionY, listW, sectionH + listH, 4, dev.vy.drt.config.DrtTheme.color("panel.background", PANEL_BG), dev.vy.drt.config.DrtTheme.color("panel.border", PANEL_BORDER));
		RoundedUiRenderer.line(listX + 1, sectionY + sectionH, listX + listW - 1, sectionY + sectionH, 1.0F, dev.vy.drt.config.DrtTheme.color("separator", PANEL_BORDER));
		String runsText = lootSearchBuffer.isBlank()
			? (selectedTab == null ? "All" : selectedTab.name()) + " Runs (" + tabHistory.size() + ")"
			: "Matching Runs (" + tabHistory.size() + ")";
		int runsTextY = DrtUiText.centeredTextY(font, sectionY, sectionH);
		ThemePreviewDraw.text(g, font, runsText, listX + 8, runsTextY, dev.vy.drt.config.DrtTheme.color("text.tableHeading", TEXT_MUTED));

		int tableY = sectionY;
		roundedPanel(g, tableX, tableY, tableW, sectionH + lootPanelH(), 4, dev.vy.drt.config.DrtTheme.color("panel.background", PANEL_BG), dev.vy.drt.config.DrtTheme.color("panel.border", PANEL_BORDER));
		RoundedUiRenderer.line(tableX + 1, tableY + sectionH, tableX + tableW - 1, tableY + sectionH, 1.0F, dev.vy.drt.config.DrtTheme.color("separator", PANEL_BORDER));

		String lootText;
		int lootColor = dev.vy.drt.config.DrtTheme.color("text.tableHeading", TEXT_MUTED);
		String timeText = "";
		int timeColor = dev.vy.drt.config.DrtTheme.color("text.muted", TEXT_MUTED);
		if (selectedRunIndex >= 0 && selectedRunIndex < tabHistory.size()) {
			lootText = "Loot";
		} else {
			lootText = "All Loot (" + totalLootItemCount() + " items)";
		}
		LootHistoryProjection.RunEntry selectedEntry = selectedRunEntry();
		if (selectedEntry != null) {
			DungeonRunCompletionRecord completion = resolveLootCompletion(selectedEntry);
			RunHistoryLinks.RunTime time = RunHistoryLinks.runTime(completion);
			timeText = time == null ? "Time unavailable" : RunHistoryLinks.formatRunTime(time.millis());
			timeColor = RunTimeColorScale.color(completion == null ? null : completion.floor,
				time == null ? null : time.millis(), dev.vy.drt.config.DrtTheme.color("text.muted", TEXT_MUTED));
		}
		int lootTextY = DrtUiText.centeredTextY(font, tableY, sectionH);
		int timeReserve = timeText.isBlank() ? 0 : font.width(timeText) + 10;
		String clippedLootText = font.plainSubstrByWidth(lootText, Math.max(30, tableW - 16 - timeReserve));
		ThemePreviewDraw.text(g, font, clippedLootText, tableX + 8, lootTextY, lootColor);
		if (!timeText.isBlank()) {
			int timeX = tableX + tableW - font.width(timeText) - 8;
			ThemePreviewDraw.text(g, font, timeText, Math.max(tableX + 8 + font.width(clippedLootText) + 4, timeX), lootTextY, timeColor);
		}
	}

	/** Prefer the selected chest's stored link, then the projected completion, then RunHistoryLinks. */
	private DungeonRunCompletionRecord resolveLootCompletion(LootHistoryProjection.RunEntry selectedEntry) {
		if (selectedEntry == null) return null;
		LootHistoryProjection.ChestOption option = selectedChestOption();
		DungeonRunRecord record = option != null ? option.record() : selectedEntry.representative();
		if (record != null) {
			String session = record.linkedRunId != null && !record.linkedRunId.isBlank()
				? record.linkedRunId
				: record.runSessionId;
			if (session != null && !session.isBlank()) {
				DungeonRunCompletionRecord bySession = completionsBySession.get(session);
				if (bySession != null) return bySession;
			}
		}
		if (selectedEntry.completion() != null) return selectedEntry.completion();
		RunHistoryLinks.Link link = runLinks.get(selectedEntry.representative());
		if (link == null && record != null) link = runLinks.get(record);
		return link != null && link.linked() ? link.completion() : null;
	}

	private int totalLootItemCount() {
		int count = 0;
		for (DungeonRunRecord r : tabRunEntries.stream().flatMap(entry -> entry.realisedRecords().stream()).toList()) {
			if (r.lootEntries != null) {
				for (DungeonLootEntry e : r.lootEntries) {
					count += Math.max(1, e.quantity);
				}
			}
		}
		return count;
	}

	private int lootPanelY() {
		return headerContentY() + HEADER_H + LOOT_SECTION_GAP + SECTION_H;
	}

	private int lootPanelH() {
		return Math.max(ROW_H, winH - (lootPanelY() - oy) - MARGIN - FOOTER_H - FOOTER_GAP);
	}

	private int listViewportH() {
		return Math.max(1, listH - 2);
	}

	private int tableBodyH() {
		return Math.max(1, lootPanelH() - chestTabsHeight() - ROW_H - 2);
	}

	private int chestTabsHeight() {
		LootHistoryProjection.RunEntry entry = selectedRunEntry();
		return entry == null || entry.chestOptions().isEmpty() ? 0 : 22;
	}

	private int tableBodyY() {
		return lootPanelY() + 1 + chestTabsHeight() + ROW_H;
	}

	private void drawRunList(GuiGraphicsExtractor g, int mouseX, int mouseY) {
		int contentY = listY + 1;
		int contentH = listViewportH();
		int visibleRows = contentH / ROW_H;
		int totalRows = tabHistory.size() + 1;
		listScroll = clamp(listScroll, 0, Math.max(0, totalRows * ROW_H - contentH));

		int startRow = clamp(listScroll / ROW_H, 0, Math.max(0, totalRows - 1));
		int endRow = Math.min(totalRows, startRow + visibleRows + 2);

		g.enableScissor(listX + 1, contentY, listX + listW - 1, contentY + contentH);
		try {
			for (int row = startRow; row < endRow; row++) {
				int ry = contentY + row * ROW_H - listScroll;
				if (ry + ROW_H <= contentY || ry >= contentY + contentH) continue;
				if (row == 0) {
					drawAllRunsRow(g, listX + 1, ry, listW - 2, ROW_H, mouseX, mouseY);
				} else {
					drawRunRow(g, listX + 1, ry, listW - 2, ROW_H, row - 1, mouseX, mouseY);
				}
			}
			if (totalRows * ROW_H > contentH) {
				drawScrollbar(g, listX + listW - 6, contentY, 4, contentH, listScroll, totalRows * ROW_H, mouseX, mouseY);
			}
		} finally {
			g.disableScissor();
		}
	}

	private void drawAllRunsRow(GuiGraphicsExtractor g, int x, int y, int w, int h, int mouseX, int mouseY) {
		if (!lootSearchBuffer.isBlank() && tabHistory.isEmpty()) {
			int ty = DrtUiText.centeredTextY(font, y, h);
			ThemePreviewDraw.text(g, font, "No matching chests", x + 8, ty, dev.vy.drt.config.DrtTheme.color("text.muted", TEXT_MUTED));
			return;
		}
		boolean selected = selectedRunIndex == -1;
		boolean hovered = contains(x, y, w, h, mouseX, mouseY) && contains(listX + 1, listY + 1, listW - 2, listViewportH(), mouseX, mouseY);
		if (selected) {
			ThemePreviewDraw.fill(g, x + 1, y, x + w - 1, y + h, dev.vy.drt.config.DrtTheme.color("row.selected", 0xCC202542));
			ThemePreviewDraw.fill(g, x + 1, y + 1, x + 4, y + h - 1, dev.vy.drt.config.DrtTheme.color("row.indicator", 0xFF6A74C9));
		} else if (hovered) {
			ThemePreviewDraw.fill(g, x + 1, y, x + w - 1, y + h, dev.vy.drt.config.DrtTheme.color("row.hover", 0x991A1A2D));
		}
		ThemePreviewDraw.fill(g, x + 7, y + h - 1, x + w - 7, y + h, dev.vy.drt.config.DrtTheme.color("separator", 0x242D2D50));
		int ty = DrtUiText.centeredTextY(font, y, h);
		String label = lootSearchBuffer.isBlank() ? "All Chests" : "All Matches";
		ThemePreviewDraw.text(g, font, label, x + 8, ty, selected ? dev.vy.drt.config.DrtTheme.color("text.primary", TEXT_PRIMARY) : dev.vy.drt.config.DrtTheme.color("text.secondary", TEXT_SECONDARY));
		addClippedClickTarget(x, y, w, h, listY + 1, listViewportH(), 0, () -> {
			closeLootContextMenu();
			selectedRunIndex = -1;
			clearLootFocus();
			tableScroll = 0;
		});
	}

	private void drawRunRow(GuiGraphicsExtractor g, int x, int y, int w, int h, int index, int mouseX, int mouseY) {
		if (index < 0 || index >= tabHistory.size()) return;
		DungeonRunRecord record = tabHistory.get(index);
		LootHistoryProjection.RunEntry runEntry = tabRunEntries.get(index);
		boolean selected = selectedRunIndex == index;
		boolean hovered = contains(x, y, w, h, mouseX, mouseY) && contains(listX + 1, listY + 1, listW - 2, listViewportH(), mouseX, mouseY);

		if (selected) {
			ThemePreviewDraw.fill(g, x + 1, y, x + w - 1, y + h, dev.vy.drt.config.DrtTheme.color("row.selected", 0xCC202542));
			ThemePreviewDraw.fill(g, x + 1, y + 1, x + 4, y + h - 1, dev.vy.drt.config.DrtTheme.color("row.indicator", 0xFF6A74C9));
		} else if (hovered) {
			ThemePreviewDraw.fill(g, x + 1, y, x + w - 1, y + h, dev.vy.drt.config.DrtTheme.color("row.hover", 0x991A1A2D));
		}
		ThemePreviewDraw.fill(g, x + 7, y + h - 1, x + w - 7, y + h, dev.vy.drt.config.DrtTheme.color("separator", 0x242D2D50));

		int ty = DrtUiText.centeredTextY(font, y, h);
		ItemStack chestIcon = resolveChestIcon(record.chestTitle);
		int textX = x + 8;
		if (!chestIcon.isEmpty()) {
			ThemePreviewDraw.item(g, chestIcon, x + 4, y + (h - 16) / 2);
			textX = x + 23;
		}
		boolean offerOnly = !runEntry.hasPurchasedChest();
		String runLabel = offerOnly ? "Run" : "#" + chestDisplayNumber(record, index);
		if (record.floor != null && selectedTab == null) runLabel = record.floor + (runLabel.isBlank() ? "" : " " + runLabel);
		String profitStr = offerOnly ? "Offers" : lootProfitLabel(record);
		int profitCol = offerOnly ? dev.vy.drt.config.DrtTheme.color("text.accent", 0xFF9CA8FF)
			: record.hasKnownProfit() ? profitColor(record.chestProfitCoins)
			: record.hasIncompleteKnownValue() ? 0xFFFFB45C
			: dev.vy.drt.config.DrtTheme.color("text.muted", TEXT_MUTED);
		int profitX = x + w - font.width(profitStr) - 8;
		List<ItemStack> modifiers = modifierIcons(record);
		int modifiersX = profitX - modifiers.size() * 18 - (modifiers.isEmpty() ? 0 : 4);
		int labelRight = modifiers.isEmpty() ? profitX - 5 : modifiersX - 3;
		String chestLabel = record.chestTitle == null || record.chestTitle.isBlank() ? "" : " " + LootHistoryPresentation.compactChestName(record.chestTitle);
		String label = font.plainSubstrByWidth(runLabel + chestLabel, Math.max(0, labelRight - textX));
		ThemePreviewDraw.text(g, font, label, textX, ty, selected ? dev.vy.drt.config.DrtTheme.color("text.primary", TEXT_PRIMARY) : offerOnly ? dev.vy.drt.config.DrtTheme.color("text.secondary", TEXT_SECONDARY) : chestColor(record.chestTitle));
		for (ItemStack modifier : modifiers) {
			if (!modifier.isEmpty()) ThemePreviewDraw.item(g, modifier, modifiersX, y + (h - 16) / 2);
			modifiersX += 18;
		}
		ThemePreviewDraw.text(g, font, profitStr, profitX, ty, profitCol);

		addClippedClickTarget(x, y, w, h, listY + 1, listViewportH(), 0, () -> {
			closeLootContextMenu();
			selectRunIndex(index);
		});
		if (runEntry.hasPurchasedChest()) addClippedClickTarget(x, y, w, h, listY + 1, listViewportH(), 1,
			() -> openLootContextMenu(index, mouseX, mouseY));
		if (offerOnly) {
			addClippedHoverTarget(x, y, w, h, listY + 1, listViewportH(),
				"Observed " + formatOverviewDate(record.timestampEpochMillis) + (record.hasKnownFinancials()
					? " | Available cost " + formatExactCoins(record.totalCostCoins())
						+ " | Recorded value " + formatExactCoins(record.chestValueCoins)
					: " | Cost and value unavailable from API"));
		}
	}

	private void openLootContextMenu(int index, int mouseX, int mouseY) {
		if (index < 0 || index >= tabHistory.size() || !tabRunEntries.get(index).hasPurchasedChest()) return;
		selectRunIndex(index);
		lootContextRecord = tabHistory.get(index);
		lootContextCompletion = tabRunEntries.get(index).completion();
		lootContextMenu.open(index, mouseX + 2, mouseY + 2, LOOT_CONTEXT_W, LOOT_CONTEXT_H,
			ox + 4, oy + 4, winW - 8, winH - 8);
		clearConfirm.reset();
	}

	private void closeLootContextMenu() {
		lootContextMenu.close();
		lootContextRecord = null;
		lootContextCompletion = null;
	}

	private void drawLootContextMenu(GuiGraphicsExtractor g, int mouseX, int mouseY) {
		if (!lootContextMenu.open() || lootContextRecord == null) return;
		int x = lootContextMenu.x();
		int y = lootContextMenu.y();
		RoundedUiRenderer.fill(g, x + 2, y + 3, LOOT_CONTEXT_W, LOOT_CONTEXT_H, 4, dev.vy.drt.config.DrtTheme.color("shadow", 0x66000000));
		RoundedUiRenderer.fillBordered(g, x, y, LOOT_CONTEXT_W, LOOT_CONTEXT_H, 3,
			dev.vy.drt.config.DrtTheme.color("menu.border", 0xFF6268A8), dev.vy.drt.config.DrtTheme.color("menu.background", 0xFF17172B));
		RoundedUiRenderer.fill(g, x + 1, y + 1, LOOT_CONTEXT_W - 2, LOOT_CONTEXT_H / 2, 2, dev.vy.drt.config.DrtTheme.color("menu.highlight", 0x221F2040));
		int rowY = y + 4;
		drawLootContextOption(g, x + 4, rowY, LOOT_CONTEXT_W - 8, "Copy", false, mouseX, mouseY,
			() -> copyContextChest(false));
		rowY += LOOT_CONTEXT_ROW_H;
		drawLootContextOption(g, x + 4, rowY, LOOT_CONTEXT_W - 8, "Copy (Detailed)", false, mouseX, mouseY,
			() -> copyContextChest(true));
		rowY += LOOT_CONTEXT_ROW_H;
		drawLootContextOption(g, x + 4, rowY, LOOT_CONTEXT_W - 8, "Edit", false, mouseX, mouseY,
			this::editContextChest);
		rowY += LOOT_CONTEXT_ROW_H;
		ThemePreviewDraw.fill(g, x + 9, rowY + 3, x + LOOT_CONTEXT_W - 9, rowY + 4, dev.vy.drt.config.DrtTheme.color("separator", 0xFF3D426B));
		rowY += LOOT_CONTEXT_SEPARATOR_H;
		String deleteLabel = lootContextMenu.deleteArmed() ? "Confirm Delete" : "Delete";
		drawLootContextOption(g, x + 4, rowY, LOOT_CONTEXT_W - 8, deleteLabel, true, mouseX, mouseY,
			this::deleteContextChest);
	}

	private void drawLootContextOption(GuiGraphicsExtractor g, int x, int y, int w, String label, boolean danger,
		int mouseX, int mouseY, Runnable action) {
		boolean hovered = contains(x, y, w, LOOT_CONTEXT_ROW_H, mouseX, mouseY);
		if (hovered) {
			RoundedUiRenderer.fillBordered(g, x, y, w, LOOT_CONTEXT_ROW_H, 2,
				danger ? 0xAA9A4452 : dev.vy.drt.config.DrtTheme.color("menu.borderHover", 0xAA747CC2), danger ? 0xCC3A1D25 : dev.vy.drt.config.DrtTheme.color("menu.hover", 0xCC292A50));
		}
		ThemePreviewDraw.text(g, font, label, x + 7, DrtUiText.centeredTextY(font, y, LOOT_CONTEXT_ROW_H),
			danger ? 0xFFFF6666 : hovered ? dev.vy.drt.config.DrtTheme.color("text.primary", TEXT_PRIMARY) : dev.vy.drt.config.DrtTheme.color("text.secondary", TEXT_SECONDARY));
		clickTargets.add(new ClickTarget(x, y, w, LOOT_CONTEXT_ROW_H, GLFW.GLFW_MOUSE_BUTTON_LEFT, action));
	}

	private void copyContextChest(boolean detailed) {
		DungeonRunRecord record = lootContextRecord;
		if (record == null || minecraft == null) return;
		String text = detailed
			? LootChestExport.detailed(record, lootContextCompletion, java.time.ZoneId.systemDefault(),
				item -> DungeonProfitPricing.resolveTotalPrice(item, DrtConfigManager.getConfig()))
			: LootChestExport.shortSummary(record, lootContextCompletion);
		minecraft.keyboardHandler.setClipboard(text);
		showLootActionMessage(detailed ? "Detailed summary copied" : "Summary copied");
		closeLootContextMenu();
	}

	private void editContextChest() {
		DungeonRunRecord record = lootContextRecord;
		closeLootContextMenu();
		if (record != null && minecraft != null) minecraft.gui.setScreen(new ManualRunEntryScreen(this, record));
	}

	private void deleteContextChest() {
		DungeonRunRecord record = lootContextRecord;
		if (record == null) return;
		if (!lootContextMenu.requestDelete()) {
			showLootActionMessage("Click Delete again to confirm");
			return;
		}
		if (DrtConfigManager.removeRunRecord(record)) {
			DrtClient.getTracker().notifyRunRemoved(record);
			clearLootFocus();
			closeLootContextMenu();
			reloadHistory();
		}
	}

	private void showLootActionMessage(String message) {
		lootActionMessage = message == null ? "" : message;
		lootActionMessageUntil = System.currentTimeMillis() + 1800L;
	}

	private void drawLootActionMessage(GuiGraphicsExtractor g) {
		if (lootActionMessage.isBlank() || System.currentTimeMillis() > lootActionMessageUntil) return;
		int w = font.width(lootActionMessage) + 14;
		int x = ox + (winW - w) / 2;
		int y = oy + winH - MARGIN - FOOTER_H - 22;
		roundedPanel(g, x, y, w, 17, 4, 0xF01A2A22, 0xFF4A8A68);
		ThemePreviewDraw.text(g, font, lootActionMessage, x + 7, DrtUiText.centeredTextY(font, y, 17), 0xFF87D7A8);
	}

	private int chestDisplayNumber(DungeonRunRecord record, int tabIndex) {
		if (record != null && record.chestNumber > 0) return record.chestNumber;
		return Math.max(1, tabHistory.size() - tabIndex);
	}

	private void drawLootTable(GuiGraphicsExtractor g, int mouseX, int mouseY) {
		int tableY = lootPanelY();
		int tableH = lootPanelH();

		int tabsH = chestTabsHeight();
		if (tabsH > 0) drawChestOptionTabs(g, tableY + 2, tabsH - 3, mouseX, mouseY);
		int itemLeft = tableX + 8;
		boolean showEach = tableW >= 300;
		int totalRight = tableX + tableW - 10;
		int eachRight = showEach ? tableX + tableW - 86 : totalRight;
		int qtyRight = showEach ? tableX + tableW - 148 : tableX + tableW - 80;
		int tableHeaderH = ROW_H;
		int headerY = tableY + 1 + tabsH;
		int headerTextY = DrtUiText.centeredTextY(font, headerY, tableHeaderH);

		int qtyHeaderWidth = headerHitWidth("Qty", LootSortColumn.QUANTITY, 42);
		int eachHeaderWidth = showEach ? headerHitWidth("Each", LootSortColumn.EACH, 50) : 0;
		int totalHeaderWidth = headerHitWidth("Total", LootSortColumn.TOTAL, 62);
		int itemHeaderWidth = Math.max(60, (qtyRight - qtyHeaderWidth - 10) - itemLeft);

		drawSortableHeader(g, itemLeft, headerY, itemHeaderWidth, tableHeaderH, "Item", LootSortColumn.ITEM, itemLeft, headerTextY, mouseX, mouseY);
		drawSortableHeader(g, qtyRight - qtyHeaderWidth, headerY, qtyHeaderWidth, tableHeaderH, "Qty", LootSortColumn.QUANTITY, qtyRight - font.width(sortHeaderLabel("Qty", LootSortColumn.QUANTITY)), headerTextY, mouseX, mouseY);
		if (showEach) drawSortableHeader(g, eachRight - eachHeaderWidth, headerY, eachHeaderWidth, tableHeaderH, "Each", LootSortColumn.EACH, eachRight - font.width(sortHeaderLabel("Each", LootSortColumn.EACH)), headerTextY, mouseX, mouseY);
		drawSortableHeader(g, totalRight - totalHeaderWidth, headerY, totalHeaderWidth, tableHeaderH, "Total", LootSortColumn.TOTAL, totalRight - font.width(sortHeaderLabel("Total", LootSortColumn.TOTAL)), headerTextY, mouseX, mouseY);

		ThemePreviewDraw.fill(g, tableX + 1, headerY + tableHeaderH - 1, tableX + tableW - 1, headerY + tableHeaderH, dev.vy.drt.config.DrtTheme.color("separator", PANEL_BORDER));

		int bodyY = headerY + tableHeaderH;
		int bodyH = tableBodyH();
		List<DisplayRow> rows = buildDisplayRows();
		tableScroll = clamp(tableScroll, 0, Math.max(0, rows.size() * ROW_H - bodyH));
		if (rows.isEmpty()) {
			String message = lootSearchBuffer.isBlank() ? "No recorded loot" : "No loot matches this search";
			ThemePreviewDraw.text(g, font, message, tableX + (tableW - font.width(message)) / 2, Math.max(bodyY + 4, DrtUiText.centeredTextY(font, bodyY, bodyH)), dev.vy.drt.config.DrtTheme.color("text.muted", TEXT_MUTED));
			return;
		}

		int visibleRows = bodyH / ROW_H;
		int startRow = clamp(tableScroll / ROW_H, 0, Math.max(0, rows.size() - 1));
		int endRow = Math.min(rows.size(), startRow + visibleRows + 2);

		g.enableScissor(tableX + 1, bodyY, tableX + tableW - 1, bodyY + bodyH);
		try {
			for (int row = startRow; row < endRow; row++) {
				int ry = bodyY + row * ROW_H - tableScroll;
				if (ry + ROW_H <= bodyY || ry >= bodyY + bodyH) continue;
				drawDisplayRow(g, tableX + 1, ry, tableW - 2, ROW_H, rows.get(row), mouseX, mouseY, itemLeft, qtyRight, eachRight, totalRight, showEach);
			}
			if (rows.size() * ROW_H > bodyH) {
				drawScrollbar(g, tableX + tableW - 6, bodyY, 4, bodyH, tableScroll, rows.size() * ROW_H, mouseX, mouseY);
			}
		} finally {
			g.disableScissor();
		}
	}

	private void drawChestOptionTabs(GuiGraphicsExtractor g, int y, int h, int mouseX, int mouseY) {
		LootHistoryProjection.RunEntry entry = selectedRunEntry();
		if (entry == null || entry.chestOptions().isEmpty()) return;
		List<LootHistoryProjection.ChestOption> options = entry.chestOptions();
		int left = tableX + 6;
		int right = tableX + tableW - 6;
		int gap = 3;
		int total = 0;
		for (LootHistoryProjection.ChestOption option : options) {
			total += Math.max(42, font.width(chestOptionLabel(options, option)) + 16) + gap;
		}
		boolean overflow = total - gap > right - left;
		int arrowW = 15;
		if (overflow) {
			boolean leftEnabled = chestTabOffset > 0;
			drawChestTabArrow(g, left, y, arrowW, h, "<", leftEnabled, mouseX, mouseY,
				() -> chestTabOffset = Math.max(0, chestTabOffset - 1));
			left += arrowW + gap;
			right -= arrowW + gap;
		}
		int index = clamp(chestTabOffset, 0, Math.max(0, options.size() - 1));
		while (index < options.size()) {
			LootHistoryProjection.ChestOption option = options.get(index);
			String label = chestOptionLabel(options, option);
			int w = Math.max(42, font.width(label) + 16);
			if (left + w > right) break;
			boolean selected = option.optionId().equals(selectedChestId());
			boolean searchMatch = !lootSearchBuffer.isBlank() && (option.purchased() || lootSearchIncludeUnopened)
				&& LootHistorySearchIndex.matchesChestOption(option, lootSearchBuffer);
			boolean hovered = contains(left, y, w, h, mouseX, mouseY);
			int border = searchMatch ? selected ? 0xFF78D59B : 0xFF4A9B69
				: selected ? dev.vy.drt.config.DrtTheme.color("control.borderActive", 0xFF6268A8) : hovered ? dev.vy.drt.config.DrtTheme.color("control.borderHover", PANEL_BORDER_HOVER) : dev.vy.drt.config.DrtTheme.color("control.border", PANEL_BORDER);
			int fill = selected ? dev.vy.drt.config.DrtTheme.color("control.active", 0xFF222642) : hovered ? dev.vy.drt.config.DrtTheme.color("control.hover", 0xFF1A1A30) : dev.vy.drt.config.DrtTheme.color("control.background", 0xFF151526);
			RoundedUiRenderer.fillBordered(g, left, y, w, h, 4, border, fill);
			if (option.purchased() && !selected) {
				ThemePreviewDraw.fill(g, left + 2, y + 3, left + 4, y + h - 3, 0xFF6F8F7C);
			}
			int color = selected ? 0xFFF2F4FF : dev.vy.drt.config.DrtTheme.color("text.secondary", TEXT_SECONDARY);
			ThemePreviewDraw.text(g, font, label, left + (w - font.width(label)) / 2, DrtUiText.centeredTextY(font, y, h), color);
			int tabX = left;
			clickTargets.add(new ClickTarget(tabX, y, w, h, 0, () -> {
				selectedChestByRun.put(entry.key(), option.optionId());
				clearLootFocus();
				tableScroll = 0;
			}));
			hoverTargets.add(new HoverTarget(tabX, y, w, h,
				(option.purchased() ? "Purchased" : "Offered") + (option.record().hasKnownFinancials()
					? " | Cost " + formatExactCoins(option.record().totalCostCoins())
						+ " | Value " + formatExactCoins(option.record().chestValueCoins)
					: " | Cost and value unavailable from API")));
			left += w + gap;
			index++;
		}
		if (overflow) {
			boolean rightEnabled = index < options.size();
			drawChestTabArrow(g, tableX + tableW - 6 - arrowW, y, arrowW, h, ">", rightEnabled, mouseX, mouseY,
				() -> chestTabOffset = Math.min(options.size() - 1, chestTabOffset + 1));
		}
	}

	private String selectedChestId() {
		LootHistoryProjection.RunEntry entry = selectedRunEntry();
		LootHistoryProjection.ChestOption option = entry == null ? null : entry.option(selectedChestByRun.get(entry.key()));
		return option == null ? "" : option.optionId();
	}

	private String lootProfitLabel(DungeonRunRecord record) {
		if (record == null) return "Unknown";
		if (record.hasKnownProfit()) return formatSigned(record.chestProfitCoins);
		if (record.hasIncompleteKnownValue()) return "Incomplete";
		return "Unknown";
	}

	private static String chestOptionLabel(List<LootHistoryProjection.ChestOption> options, LootHistoryProjection.ChestOption option) {
		String base = LootHistoryPresentation.compactChestName(option.chestTitle());
		int sameTitle = 0;
		for (LootHistoryProjection.ChestOption other : options) {
			if (other.chestTitle().equalsIgnoreCase(option.chestTitle())) sameTitle++;
		}
		if (sameTitle > 1 && option.record() != null && option.record().chestNumber > 0) {
			return base + " #" + option.record().chestNumber;
		}
		return base;
	}

	private void drawChestTabArrow(GuiGraphicsExtractor g, int x, int y, int w, int h, String label, boolean enabled,
		int mouseX, int mouseY, Runnable action) {
		boolean hovered = enabled && contains(x, y, w, h, mouseX, mouseY);
		RoundedUiRenderer.fillBordered(g, x, y, w, h, 4, hovered ? dev.vy.drt.config.DrtTheme.color("control.borderHover", PANEL_BORDER_HOVER) : dev.vy.drt.config.DrtTheme.color("control.border", PANEL_BORDER),
			hovered ? dev.vy.drt.config.DrtTheme.color("control.hover", 0xFF1A1A30) : dev.vy.drt.config.DrtTheme.color("control.background", 0xFF151526));
		int color = enabled ? dev.vy.drt.config.DrtTheme.color("control.text", TEXT_SECONDARY) : dev.vy.drt.config.DrtTheme.color("control.textDisabled", 0xFF4A4E65);
		ThemePreviewDraw.text(g, font, label, x + (w - font.width(label)) / 2, DrtUiText.centeredTextY(font, y, h), color);
		if (enabled) clickTargets.add(new ClickTarget(x, y, w, h, 0, action));
	}

	private int headerHitWidth(String label, LootSortColumn col, int minW) {
		String text = sortHeaderLabel(label, col);
		return Math.max(minW, font.width(text) + 8);
	}

	private String sortHeaderLabel(String label, LootSortColumn col) {
		if (lootSortColumn == col) {
			return label + (lootSortAscending ? " ^" : " v");
		}
		return label;
	}

	private void drawSortableHeader(GuiGraphicsExtractor g, int hx, int hy, int hw, int hh, String label, LootSortColumn col, int tx, int ty, int mouseX, int mouseY) {
		boolean hovered = contains(hx, hy, hw, hh, mouseX, mouseY);
		String text = sortHeaderLabel(label, col);
		int color = lootSortColumn == col ? dev.vy.drt.config.DrtTheme.color("text.accent", 0xFF9CA8FF) : hovered ? dev.vy.drt.config.DrtTheme.color("control.textHover", TEXT_PRIMARY) : dev.vy.drt.config.DrtTheme.color("text.tableHeading", TEXT_MUTED);
		ThemePreviewDraw.text(g, font, text, tx, ty, color);
		clickTargets.add(new ClickTarget(hx, hy, hw, hh, 0, () -> {
			if (lootSortColumn == col) {
				lootSortAscending = !lootSortAscending;
			} else {
				lootSortColumn = col;
				lootSortAscending = false;
			}
		}));
	}

	private void drawDisplayRow(GuiGraphicsExtractor g, int x, int y, int w, int h, DisplayRow row, int mouseX, int mouseY, int itemLeft, int qtyRight, int eachRight, int totalRight, boolean showEach) {
		int visibleBodyY = tableBodyY();
		boolean hovered = contains(x, y, w, h, mouseX, mouseY) && contains(tableX + 1, visibleBodyY, tableW - 2, tableBodyH(), mouseX, mouseY);
		boolean selected = row.label.equals(selectedLootKey);
		if (selected) {
			ThemePreviewDraw.fill(g, x + 1, y, x + w - 1, y + h, dev.vy.drt.config.DrtTheme.color("row.selected", 0xCC202542));
			ThemePreviewDraw.fill(g, x + 1, y + 1, x + 4, y + h - 1, dev.vy.drt.config.DrtTheme.color("row.indicator", 0xFF6A74C9));
		} else if (hovered) {
			ThemePreviewDraw.fill(g, x + 1, y, x + w - 1, y + h, dev.vy.drt.config.DrtTheme.color("row.hover", 0x991A1A2D));
		}
		ThemePreviewDraw.fill(g, x + 7, y + h - 1, x + w - 7, y + h, dev.vy.drt.config.DrtTheme.color("separator", 0x242D2D50));

		int ty = DrtUiText.centeredTextY(font, y, h);

		ItemStack icon = resolveItemIcon(row.itemId);
		int itemY = y + (h - 16) / 2;
		if (!icon.isEmpty()) {
			ThemePreviewDraw.item(g, icon, itemLeft, itemY);
		}
		int labelX = icon.isEmpty() ? itemLeft : itemLeft + 20;
		int salvageX = qtyRight - 32;
		int maxLabelW = Math.max(20, (row.forcedSalvage ? salvageX - 3 : qtyRight - 8) - labelX);
		String shownLabel = row.label + (row.unopened ? " (Unopened)" : "");
		String truncatedLabel = font.plainSubstrByWidth(shownLabel, maxLabelW);
		int itemColor = itemColor(row.itemId);
		if (isUltimateItem(row.itemId)) {
			Component styled = Component.literal(truncatedLabel).withStyle(Style.EMPTY.withBold(true).withColor(TextColor.fromRgb(0xFFFF55FF)));
			ThemePreviewDraw.text(g, font, styled, labelX, ty, 0xFFFF55FF);
		} else {
			ThemePreviewDraw.text(g, font, truncatedLabel, labelX, ty, itemColor);
		}
		if (row.forcedSalvage) {
			ThemePreviewDraw.item(g, forcedSalvageEssenceIcon(), salvageX, itemY);
		}

		String qtyStr = Integer.toString(row.quantity);
		ThemePreviewDraw.text(g, font, qtyStr, qtyRight - font.width(qtyStr), ty, dev.vy.drt.config.DrtTheme.color("text.secondary", TEXT_SECONDARY));

		if (showEach) {
			String eachStr = row.unitPrice >= 0 ? formatCoins(row.unitPrice) : "?";
			int eachColor = row.unitPrice >= 0 ? dev.vy.drt.config.DrtTheme.color("text.secondary", TEXT_SECONDARY) : 0xFFFF7A7A;
			ThemePreviewDraw.text(g, font, eachStr, eachRight - font.width(eachStr), ty, eachColor);
		}

		String totalStr = row.totalPrice >= 0 ? formatCoins(row.totalPrice) : "?";
		int totalColor = row.totalPrice < 0 ? 0xFFFFB45C : row.totalPrice > 0 ? 0xFF8BCF9A : dev.vy.drt.config.DrtTheme.color("text.secondary", TEXT_SECONDARY);
		ThemePreviewDraw.text(g, font, totalStr, totalRight - font.width(totalStr), ty, totalColor);

		int bodyY = lootPanelY() + 1 + ROW_H;
		addClippedClickTarget(x, y, w, h, bodyY, tableBodyH(), 0, () -> focusLootRow(row));
		addClippedClickTarget(x, y, w, h, bodyY, tableBodyH(), GLFW.GLFW_MOUSE_BUTTON_RIGHT, () -> copyLootShareMessage(row));
		String unopenedHint = row.unopened ? "Unopened chest offer | " : "";
		String salvageHint = row.forcedSalvage ? " | Crimson Essence icon: forced salvage valuation" : "";
		addClippedHoverTarget(x, y, w, h, bodyY, tableBodyH(), unopenedHint + row.label + ": " + formatExactPrice(row.unitPrice) + " each, " + formatExactPrice(row.totalPrice) + " total" + salvageHint);
	}

	private void focusLootRow(DisplayRow row) {
		selectedLootKey = rowKey(row);
	}

	private void copyLootShareMessage(DisplayRow row) {
		focusLootRow(row);
		if (minecraft != null) {
			minecraft.keyboardHandler.setClipboard("[DRT] " + row.label + " dropped: " + row.quantity + " | Total: " + formatCoins(row.totalPrice));
		}
	}

	private void drawFooter(GuiGraphicsExtractor g, int mouseX, int mouseY) {
		int footerY = oy + winH - MARGIN - FOOTER_H;
		int footerW = winW - MARGIN * 2;
		int x = ox + MARGIN;

		roundedPanel(g, x, footerY, footerW, FOOTER_H, 4, dev.vy.drt.config.DrtTheme.color("panel.alternate", PANEL_BG_ALT), dev.vy.drt.config.DrtTheme.color("panel.border", PANEL_BORDER));

		int clearW = Math.max(font.width("Clear"), font.width("Confirm?")) + 12;
		int closeW = font.width("Close") + 12;
		int buttonH = FOOTER_H - 6;
		int buttonY = footerY + 3;
		int closeX = x + footerW - closeW - 6;
		int clearX = closeX - clearW - 6;

		String hint = "Right click rows for bonus actions!";
		ThemePreviewDraw.text(g, font, font.plainSubstrByWidth(hint, Math.max(30, clearX - x - 16)), x + 8, DrtUiText.centeredTextY(font, footerY, FOOTER_H), dev.vy.drt.config.DrtTheme.color("text.muted", TEXT_MUTED));

		boolean closeHov = contains(closeX, buttonY, closeW, buttonH, mouseX, mouseY);
		boolean clearHov = contains(clearX, buttonY, clearW, buttonH, mouseX, mouseY);
		String clearScope = selectedTab == null ? null : selectedTab.name();
		boolean confirming = clearConfirm.isArmed(clearScope, System.currentTimeMillis());

		int clearBg = confirming ? 0xFF3D1818 : clearHov ? 0xFF281818 : 0xFF1A1212;
		int clearBorder = confirming ? 0xFFFF4444 : clearHov ? 0xFF8A3A3A : 0xFF4A2020;
		int clearText = confirming ? 0xFFFF8888 : clearHov ? 0xFFFF6B6B : 0xFFB05050;

		roundedPanel(g, clearX, buttonY, clearW, buttonH, 4, clearBg, clearBorder);
		String clearLabel = confirming ? "Confirm?" : "Clear";
		ThemePreviewDraw.text(g, font, clearLabel, clearX + (clearW - font.width(clearLabel)) / 2, DrtUiText.centeredTextY(font, buttonY, buttonH), clearText);

		int closeBg = closeHov ? dev.vy.drt.config.DrtTheme.color("control.hover", 0xFF1D2139) : dev.vy.drt.config.DrtTheme.color("control.background", 0xFF141425);
		int closeBorder = closeHov ? dev.vy.drt.config.DrtTheme.color("control.borderHover", PANEL_BORDER_HOVER) : dev.vy.drt.config.DrtTheme.color("control.border", PANEL_BORDER);
		roundedPanel(g, closeX, buttonY, closeW, buttonH, 4, closeBg, closeBorder);
		ThemePreviewDraw.text(g, font, "Close", closeX + (closeW - font.width("Close")) / 2, DrtUiText.centeredTextY(font, buttonY, buttonH), closeHov ? dev.vy.drt.config.DrtTheme.color("control.textHover", TEXT_PRIMARY) : dev.vy.drt.config.DrtTheme.color("control.text", TEXT_SECONDARY));

		clickTargets.add(new ClickTarget(closeX, buttonY, closeW, buttonH, 0, this::onClose));
		clickTargets.add(new ClickTarget(clearX, buttonY, clearW, buttonH, 0, () -> {
			ClearConfirmation.Result result = clearConfirm.click(clearScope, System.currentTimeMillis());
			if (result == ClearConfirmation.Result.ARMED) closeLootContextMenu();
			if (result != ClearConfirmation.Result.CONFIRMED) return;
			List<DungeonRunRecord> removed;
			boolean cleared;
			if (clearScope == null) {
				removed = (themePreview ? previewData.chests : DrtConfigManager.getRunHistory());
				cleared = DrtConfigManager.clearAllData("loot screen");
			} else {
				removed = (themePreview ? previewData.chests : DrtConfigManager.getRunHistory()).stream().filter(r -> r != null && clearScope.equals(r.floor)).toList();
				cleared = DrtConfigManager.clearFloorData(clearScope, "loot screen");
			}
			if (cleared) {
				DrtClient.getTracker().notifyHistoryCleared(removed);
				reloadHistory();
			}
		}));
	}

	private void drawScrollbar(GuiGraphicsExtractor g, int x, int y, int w, int h, int scroll, int totalH, int mouseX, int mouseY) {
		if (totalH <= h || totalH <= 0) return;
		int trackW = 2;
		int trackX = x + (w - trackW) / 2;
		RoundedUiRenderer.fill(g, trackX, y + 2, trackW, Math.max(1, h - 4), 1, dev.vy.drt.config.DrtTheme.color("scrollbar.track", 0x3823233C));
		int thumbH = scrollbarThumbHeight(h, totalH);
		int maxScroll = totalH - h;
		int thumbY = y + (int) Math.round((h - thumbH) * (scroll / (double) maxScroll));
		boolean hovered = contains(x - 2, y, w + 4, h, mouseX, mouseY);
		int thumbColor = hovered ? dev.vy.drt.config.DrtTheme.color("scrollbar.hover", 0xB87880B6) : dev.vy.drt.config.DrtTheme.color("scrollbar.thumb", 0x8C555B8A);
		RoundedUiRenderer.fill(g, trackX, thumbY, trackW, thumbH, 1, thumbColor);
	}

	private boolean beginScrollbarDrag(int mouseX, int mouseY) {
		int listTotalH = (tabHistory.size() + 1) * ROW_H;
		if (beginScrollbarDrag(ScrollbarDrag.LIST, listX + listW - 6, listY + 1, 4, listViewportH(), listTotalH, listScroll, mouseX, mouseY)) {
			return true;
		}
		int tableTotalH = buildDisplayRows().size() * ROW_H;
		return beginScrollbarDrag(ScrollbarDrag.TABLE, tableX + tableW - 6, tableBodyY(), 4, tableBodyH(), tableTotalH, tableScroll, mouseX, mouseY);
	}

	private boolean beginScrollbarDrag(ScrollbarDrag type, int x, int y, int w, int h, int totalH, int scroll, int mouseX, int mouseY) {
		if (totalH <= h || !contains(x - 3, y, w + 6, h, mouseX, mouseY)) return false;
		int thumbH = scrollbarThumbHeight(h, totalH);
		int maxScroll = totalH - h;
		int thumbY = y + (int) Math.round((h - thumbH) * (scroll / (double) maxScroll));
		scrollbarDrag = type;
		scrollbarDragOffset = mouseY >= thumbY && mouseY < thumbY + thumbH ? mouseY - thumbY : thumbH / 2;
		updateScrollbarDrag(mouseY);
		return true;
	}

	private void updateScrollbarDrag(int mouseY) {
		int y;
		int h;
		int totalH;
		if (scrollbarDrag == ScrollbarDrag.LIST) {
			y = listY + 1;
			h = listViewportH();
			totalH = (tabHistory.size() + 1) * ROW_H;
		} else if (scrollbarDrag == ScrollbarDrag.TABLE) {
			y = tableBodyY();
			h = tableBodyH();
			totalH = buildDisplayRows().size() * ROW_H;
		} else {
			return;
		}
		if (totalH <= h) {
			scrollbarDrag = ScrollbarDrag.NONE;
			return;
		}
		int thumbH = scrollbarThumbHeight(h, totalH);
		int thumbTravel = Math.max(1, h - thumbH);
		int thumbY = clamp(mouseY - scrollbarDragOffset - y, 0, thumbTravel);
		int next = (int) Math.round((totalH - h) * (thumbY / (double) thumbTravel));
		if (scrollbarDrag == ScrollbarDrag.LIST) listScroll = next;
		else tableScroll = next;
	}

	private int scrollbarThumbHeight(int viewportH, int totalH) {
		return Math.max(12, viewportH * viewportH / Math.max(1, totalH));
	}

	private float animationProgress() {
		long elapsedMs = (System.nanoTime() - openedAtNanos) / 1_000_000L;
		return Math.min(1.0F, Math.max(0.0F, elapsedMs / (float) OPEN_ANIMATION_MS));
	}

	private float openingScale() {
		if (themePreview) return 1;
		float t = animationProgress();
		float eased = 1.0F - (float) Math.pow(1.0F - t, 3.0D);
		return OPEN_START_SCALE + (1.0F - OPEN_START_SCALE) * eased;
	}

	private int logicalMouseX(double mouseX, float scale) {
		return (int) Math.floor(width / 2.0D + (mouseX - width / 2.0D) / scale);
	}

	private int logicalMouseY(double mouseY, float scale) {
		return (int) Math.floor(height / 2.0D + (mouseY - height / 2.0D) / scale);
	}

	private void roundedPanel(GuiGraphicsExtractor g, int x, int y, int w, int h, int radius, int fill, int outline) {
		RoundedUiRenderer.fillBordered(g, x, y, w, h, radius, outline, fill);
	}

	private void drawHoveredTooltip(GuiGraphicsExtractor g, int mouseX, int mouseY) {
		if (activeTab == DrtNavTab.LOOT && lootContextMenu.open()) return;
		if (settingsDropdownOpen()) return;
		for (int i = hoverTargets.size() - 1; i >= 0; i--) {
			HoverTarget target = hoverTargets.get(i);
			if (!contains(target.x, target.y, target.w, target.h, mouseX, mouseY)) continue;
			String text = target.text;
			int boundX = target.boundW > 0 ? target.boundX : ox + 4;
			int boundY = target.boundH > 0 ? target.boundY : oy + 4;
			int boundW = target.boundW > 0 ? target.boundW : winW - 8;
			int boundH = target.boundH > 0 ? target.boundH : winH - 8;
			String clippedText = font.plainSubstrByWidth(text, Math.max(24, boundW - 10));
			int tooltipW = font.width(clippedText) + 10;
			int tx = mouseX + 10;
			if (tx + tooltipW > boundX + boundW) tx = mouseX - tooltipW - 10;
			tx = clamp(tx, boundX, Math.max(boundX, boundX + boundW - tooltipW));
			int ty = clamp(mouseY + 10, boundY, Math.max(boundY, boundY + boundH - font.lineHeight - 8));
			ThemePreviewDraw.fill(g, tx, ty, tx + tooltipW, ty + font.lineHeight + 8, dev.vy.drt.config.DrtTheme.color("tooltip.background", 0xF218182A));
			border(g, tx, ty, tooltipW, font.lineHeight + 8, dev.vy.drt.config.DrtTheme.color("tooltip.border", 0xFF555A8C));
			ThemePreviewDraw.text(g, font, clippedText, tx + 5, ty + 4, dev.vy.drt.config.DrtTheme.color("tooltip.text", TEXT_PRIMARY));
			return;
		}
	}

	private void border(GuiGraphicsExtractor g, int x, int y, int w, int h, int color) {
		ThemePreviewDraw.fill(g, x, y, x + w, y + 1, color);
		ThemePreviewDraw.fill(g, x, y + h - 1, x + w, y + h, color);
		ThemePreviewDraw.fill(g, x, y + 1, x + 1, y + h - 1, color);
		ThemePreviewDraw.fill(g, x + w - 1, y + 1, x + w, y + h - 1, color);
	}

	private boolean contains(int x, int y, int w, int h, int mx, int my) {
		return mx >= x && mx < x + w && my >= y && my < y + h;
	}

	private void addClippedClickTarget(int x, int y, int w, int h, int clipY, int clipH, int button, Runnable action) {
		int visibleY = Math.max(y, clipY);
		int visibleBottom = Math.min(y + h, clipY + clipH);
		if (visibleBottom > visibleY) clickTargets.add(new ClickTarget(x, visibleY, w, visibleBottom - visibleY, button, action));
	}

	private void addClippedHoverTarget(int x, int y, int w, int h, int clipY, int clipH, String text) {
		int visibleY = Math.max(y, clipY);
		int visibleBottom = Math.min(y + h, clipY + clipH);
		if (visibleBottom > visibleY) hoverTargets.add(new HoverTarget(x, visibleY, w, visibleBottom - visibleY, text));
	}

	private int clamp(int v, int min, int max) {
		return Math.max(min, Math.min(max, v));
	}

	private float clamp(float v, float min, float max) {
		return Math.max(min, Math.min(max, v));
	}

	// ── History & Data Resolution ─────────────────────────────────────────────

	private void selectTab(DungeonFloor floor) {
		selectedTab = floor;
		selectedRunIndex = -1;
		clearLootFocus();
		listScroll = 0;
		tableScroll = 0;
		clearConfirm.reset();
		if (!themePreview) DrtConfigManager.updateSelectedFloor(floor == null ? null : floor.name());
		floorTabOffset = floor == null ? 0 : Math.max(0, availableTabs.indexOf(floor) + 1);
		rebuildTabHistory();
	}

	private void cycleFloor(int direction) {
		List<DungeonFloor> choices = new ArrayList<>();
		choices.add(null);
		choices.addAll(availableTabs);
		int current = choices.indexOf(selectedTab);
		int next = Math.floorMod(current + direction, choices.size());
		selectTab(choices.get(next));
	}

	private void reloadHistory() {
		List<DungeonRunRecord> purchasedHistory = new ArrayList<>((themePreview ? previewData.chests : DrtConfigManager.getRunHistory()));
		List<DungeonRunCompletionRecord> completions = (themePreview ? previewData.runs : DrtConfigManager.getRunCompletions());
		runLinks = RunHistoryLinks.resolve(purchasedHistory, completions);
		Map<String, DungeonRunCompletionRecord> bySession = new LinkedHashMap<>();
		for (DungeonRunCompletionRecord completion : completions) {
			if (completion == null || completion.runSessionId == null || completion.runSessionId.isBlank()) continue;
			bySession.putIfAbsent(completion.runSessionId, completion);
		}
		completionsBySession = Map.copyOf(bySession);
		allRunEntries = LootHistoryProjection.projectRuns(purchasedHistory, completions);

		java.util.Set<String> recordedFloors = new java.util.HashSet<>();
		for (LootHistoryProjection.RunEntry entry : allRunEntries) {
			DungeonRunRecord record = entry.representative();
			if (record != null && record.floor != null) recordedFloors.add(record.floor.toUpperCase(Locale.ROOT));
		}
		availableTabs = new ArrayList<>();
		for (DungeonFloor f : DungeonFloor.values()) {
			if (f == DungeonFloor.UNKNOWN || !recordedFloors.contains(f.name())) continue;
			availableTabs.add(f);
		}
		if (selectedTab != null && !availableTabs.contains(selectedTab)) {
			selectedTab = null;
			if (!themePreview) DrtConfigManager.updateSelectedFloor(null);
		}
		if (selectedTab != null) floorTabOffset = Math.max(0, availableTabs.indexOf(selectedTab) + 1);
		rebuildTabHistory();
	}

	private boolean isOfferRecord(DungeonRunRecord record) {
		return LootHistoryProjection.isOfferRecord(record);
	}

	private void rebuildTabHistory() {
		LootHistoryProjection.RunEntry previous = selectedRunEntry();
		java.util.stream.Stream<LootHistoryProjection.RunEntry> entries = allRunEntries.stream();
		if (selectedTab != null) entries = entries.filter(entry -> selectedTab.name().equals(entry.representative().floor));
		if (!lootSearchBuffer.isBlank()) entries = entries.filter(this::runMatchesLootSearch);
		tabRunEntries = entries.toList();
		tabHistory = tabRunEntries.stream().map(LootHistoryProjection.RunEntry::representative).toList();
		selectedRunIndex = previous == null ? -1 : indexOfRunKey(previous.key());
		if (!lootSearchBuffer.isBlank() && selectedRunIndex >= 0) selectMatchingChestForSearch(tabRunEntries.get(selectedRunIndex));
		if (!selectedLootKey.isBlank() && buildDisplayRows().stream().noneMatch(row -> rowKey(row).equals(selectedLootKey))) {
			clearLootFocus();
		}
	}

	private int indexOfRunKey(String key) {
		for (int i = 0; i < tabRunEntries.size(); i++) if (tabRunEntries.get(i).key().equals(key)) return i;
		return -1;
	}

	private boolean runMatchesLootSearch(LootHistoryProjection.RunEntry entry) {
		return LootHistorySearchIndex.matchesRun(entry, lootSearchBuffer, lootSearchIncludeUnopened);
	}

	private boolean recordMatchesLootSearch(DungeonRunRecord record) {
		if (record == null || record.lootEntries == null) return false;
		for (DungeonLootEntry entry : record.lootEntries) {
			if (entry != null && LootHistorySearchIndex.matchesItem(displayName(entry), entry.itemId, lootSearchBuffer)) return true;
		}
		return false;
	}

	private RunSelection filterSelection() {
		List<DungeonRunRecord> realised = tabRunEntries.stream().flatMap(entry -> entry.realisedRecords().stream()).toList();
		return RunSelection.logged(realised, sumCoins(realised, true, false), sumCoins(realised, false, false), sumProfit(realised));
	}

	private RunSelection currentSelection() {
		DungeonRunRecord r = selectedRecord();
		if (r == null) {
			return filterSelection();
		}
		return RunSelection.logged(List.of(r), shownCost(r), shownValue(r), shownProfit(r));
	}

	private DungeonRunRecord selectedRecord() {
		LootHistoryProjection.RunEntry entry = selectedRunEntry();
		if (entry == null) return null;
		LootHistoryProjection.ChestOption option = entry.option(selectedChestByRun.get(entry.key()));
		return option == null ? null : option.record();
	}

	private DungeonRunRecord selectedPurchasedRecord() {
		LootHistoryProjection.RunEntry entry = selectedRunEntry();
		return entry == null || entry.realisedRecords().isEmpty() ? null : entry.realisedRecords().getFirst();
	}

	private LootHistoryProjection.RunEntry selectedRunEntry() {
		return selectedRunIndex >= 0 && selectedRunIndex < tabRunEntries.size() ? tabRunEntries.get(selectedRunIndex) : null;
	}

	private LootHistoryProjection.ChestOption selectedChestOption() {
		LootHistoryProjection.RunEntry entry = selectedRunEntry();
		return entry == null ? null : entry.option(selectedChestByRun.get(entry.key()));
	}

	private void selectRunIndex(int index) {
		selectedRunIndex = index;
		chestTabOffset = 0;
		LootHistoryProjection.RunEntry entry = selectedRunEntry();
		if (entry != null && !lootSearchBuffer.isBlank()) {
			selectMatchingChestForSearch(entry);
		} else if (entry != null) {
			LootHistoryProjection.ChestOption chosen = entry.defaultOption();
			if (chosen != null) selectedChestByRun.put(entry.key(), chosen.optionId());
		}
		clearLootFocus();
		tableScroll = 0;
	}

	private void selectMatchingChestForSearch(LootHistoryProjection.RunEntry entry) {
		if (entry == null || lootSearchBuffer.isBlank()) return;
		String chest = LootHistorySearchIndex.firstMatchingChest(entry, lootSearchBuffer, lootSearchIncludeUnopened);
		if (chest != null) {
			chestSelectionBeforeSearch.putIfAbsent(entry.key(), selectedChestByRun.getOrDefault(entry.key(), ""));
			selectedChestByRun.put(entry.key(), chest);
		}
	}

	private List<DisplayRow> buildDisplayRows() {
		RunSelection selection = currentSelection();
		boolean aggregateSearch = selectedRecord() == null && !lootSearchBuffer.isBlank();
		List<LootRecordSource> sources;
		if (aggregateSearch) {
			sources = tabRunEntries.stream()
				.flatMap(entry -> entry.chestOptions().stream())
				.filter(option -> option.purchased() || lootSearchIncludeUnopened)
				.map(option -> new LootRecordSource(option.record(), !option.purchased()))
				.toList();
		} else {
			LootHistoryProjection.ChestOption option = selectedChestOption();
			if (!lootSearchBuffer.isBlank() && option != null && !option.purchased() && !lootSearchIncludeUnopened) {
				sources = List.of();
			} else {
				boolean markUnopened = !lootSearchBuffer.isBlank() && option != null && !option.purchased();
				sources = selection.records().stream().map(record -> new LootRecordSource(record, markUnopened)).toList();
			}
		}
		return buildDisplayRowsForSources(sources, aggregateSearch || !lootSearchBuffer.isBlank());
	}

	private List<DisplayRow> buildDisplayRowsForRecords(List<DungeonRunRecord> records, boolean filterItems) {
		return buildDisplayRowsForSources(records.stream().map(record -> new LootRecordSource(record, false)).toList(), filterItems);
	}

	private List<DisplayRow> buildDisplayRowsForSources(List<LootRecordSource> sources, boolean filterItems) {
		List<LootEntrySource> entries = new ArrayList<>();
		for (LootRecordSource source : sources) {
			DungeonRunRecord r = source.record();
			if (r == null || r.lootEntries == null) continue;
			for (DungeonLootEntry entry : r.lootEntries) {
				if (!filterItems || (entry != null && LootHistorySearchIndex.matchesItem(displayName(entry), entry.itemId, lootSearchBuffer))) {
					entries.add(new LootEntrySource(entry, source.unopened()));
				}
			}
		}
		return buildDisplayRowsForSourcesEntries(entries);
	}

	private List<DisplayRow> buildDisplayRowsForEntries(List<DungeonLootEntry> entries) {
		return buildDisplayRowsForSourcesEntries(entries.stream().map(entry -> new LootEntrySource(entry, false)).toList());
	}

	private List<DisplayRow> buildDisplayRowsForSourcesEntries(List<LootEntrySource> entries) {
		Map<String, MutableAgg> aggs = new LinkedHashMap<>();
		DrtConfig config = DrtConfigManager.getConfig();
		for (LootEntrySource source : entries) {
			DungeonLootEntry e = source.entry();
			if (e == null) continue;
			if (shouldHideLootEntry(e)) continue;
			String resolvedItemId = e.itemId == null ? "" : e.itemId.trim();
			if (resolvedItemId.isBlank()) {
				String raw = e.rawName == null ? "" : e.rawName.trim().toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]+", " ").trim().replaceAll("\\s+", " ");
				if (raw.contains("CRIMSON ESSENCE") || raw.equals("ESSENCE CRIMSON")) resolvedItemId = "ESSENCE_CRIMSON";
			}
			final String itemId = resolvedItemId;
			String key = (!itemId.isBlank() ? itemId : e.rawName.trim().toUpperCase(Locale.ROOT)) + "|" + source.unopened();
			MutableAgg agg = aggs.computeIfAbsent(key, k -> new MutableAgg(displayName(e), itemId, source.unopened()));
			agg.quantity += Math.max(1, e.quantity);
			if (DungeonProfitPricing.isForcedSalvageValued(e, config)) {
				agg.forcedSalvage = true;
			}
		}
		List<DisplayRow> rows = new ArrayList<>();
		for (MutableAgg agg : aggs.values()) {
			DungeonLootEntry priced = new DungeonLootEntry(agg.label, agg.itemId, Math.max(1, agg.quantity));
			boolean knownPrice = DungeonProfitPricing.hasKnownUnitPrice(priced, config);
			long unit = knownPrice ? DungeonProfitPricing.resolveUnitPrice(priced, config) : -1L;
			long total = knownPrice ? DungeonProfitPricing.resolveTotalPrice(priced, config) : -1L;
			rows.add(new DisplayRow(agg.label, agg.itemId, agg.quantity, unit, total, agg.forcedSalvage, agg.unopened));
		}
		Comparator<DisplayRow> comparator = displayRowComparator();
		if (comparator != null) {
			rows.sort(comparator);
		}
		return rows;
	}

	private boolean shouldHideLootEntry(DungeonLootEntry entry) {
		if (entry == null) return true;
		if (entry.rawName != null && entry.rawName.equalsIgnoreCase("EMPTY")) return true;
		String itemId = entry.itemId == null ? "" : entry.itemId.trim().toUpperCase(Locale.ROOT);
		String rawName = entry.rawName == null ? "" : entry.rawName.trim().toUpperCase(Locale.ROOT);
		return itemId.equals("ANCIENT_ROSE") || rawName.equals("ANCIENT ROSE");
	}

	private String displayName(DungeonLootEntry entry) {
		if (entry == null) return "";
		if (entry.rawName != null && !entry.rawName.isBlank()) {
			return EnchantedBookIdentity.presentationName(entry.rawName);
		}
		if (entry.itemId != null && !entry.itemId.isBlank()) return entry.itemId;
		return "Unknown";
	}

	private Comparator<DisplayRow> displayRowComparator() {
		if (lootSortColumn == null) return null;
		Comparator<DisplayRow> c = switch (lootSortColumn) {
			case ITEM -> Comparator.comparing((DisplayRow r) -> r.label.toLowerCase(Locale.ROOT));
			case QUANTITY -> Comparator.comparingInt(r -> r.quantity);
			case EACH -> Comparator.comparingLong(r -> r.unitPrice);
			case TOTAL -> Comparator.comparingLong(r -> r.totalPrice);
		};
		return lootSortAscending ? c : c.reversed();
	}

	private String rowKey(DisplayRow row) {
		return row == null ? "" : row.label + (row.unopened ? "|unopened" : "|opened");
	}

	private void resetLootSearchModeAfterEdit() {
		LootSearchPolicy.Mode mode = LootSearchPolicy.edited(lootSearchBuffer);
		lootSearchCommitted = mode.committed();
		lootSearchIncludeUnopened = mode.includeUnopened();
		closeLootContextMenu();
	}

	private void searchLootRows() {
		if (lootSearchBuffer.isBlank()) {
			restoreChestSelectionsAfterSearch();
			lootSearchCommitted = false;
			lootSearchIncludeUnopened = false;
		}
		rebuildTabHistory();
		listScroll = 0;
		tableScroll = 0;
		if (lootSearchBuffer.isBlank()) {
			clearLootFocus();
			return;
		}
		List<DisplayRow> rows = buildDisplayRows();
		for (int i = 0; i < rows.size(); i++) {
			DisplayRow r = rows.get(i);
			if (LootHistorySearchIndex.matchesItem(r.label, r.itemId, lootSearchBuffer)) {
				selectedLootKey = rowKey(r);
				int viewportH = tableBodyH();
				int targetScroll = i * ROW_H;
				int maxScroll = Math.max(0, rows.size() * ROW_H - viewportH);
				tableScroll = clamp(targetScroll, 0, maxScroll);
				return;
			}
		}
		clearLootFocus();
	}

	private void restoreChestSelectionsAfterSearch() {
		for (Map.Entry<String, String> entry : chestSelectionBeforeSearch.entrySet()) {
			if (entry.getValue().isBlank()) selectedChestByRun.remove(entry.getKey());
			else selectedChestByRun.put(entry.getKey(), entry.getValue());
		}
		chestSelectionBeforeSearch.clear();
	}

	private long sumCoins(List<DungeonRunRecord> records, boolean cost, boolean useBaseCost) {
		long total = 0;
		for (DungeonRunRecord r : records) {
			if (r == null) continue;
			if (cost) {
				total += useBaseCost ? r.chestCostCoins : r.totalCostCoins();
			} else {
				total += r.chestValueCoins;
			}
		}
		return total;
	}

	private long sumProfit(List<DungeonRunRecord> records) {
		long total = 0;
		for (DungeonRunRecord r : records) {
			if (r == null || !r.hasKnownProfit()) continue;
			total += r.chestProfitCoins;
		}
		return total;
	}

	private long shownCost(DungeonRunRecord r) {
		return r != null ? r.totalCostCoins() : 0L;
	}

	private long shownValue(DungeonRunRecord r) {
		return r != null ? r.chestValueCoins : 0L;
	}

	private long shownProfit(DungeonRunRecord r) {
		return r != null ? r.chestProfitCoins : 0L;
	}

	private int profitColor(long v) {
		return v >= 0 ? dev.vy.drt.config.DrtTheme.color("profit.positive", 0xFF8BCF9A) : dev.vy.drt.config.DrtTheme.color("profit.negative", 0xFFFF7A7A);
	}

	private String formatCoins(long v) {
		if (v >= 1_000_000_000L) return String.format(Locale.ROOT, "%.2fB", v / 1_000_000_000D);
		if (v >= 1_000_000L) return String.format(Locale.ROOT, "%.2fM", v / 1_000_000D);
		if (v >= 1_000L) return String.format(Locale.ROOT, "%.1fk", v / 1_000D);
		return Long.toString(v);
	}

	private String formatSigned(long v) {
		return (v >= 0 ? "+" : "-") + formatCoins(Math.abs(v));
	}

	private String formatExactCoins(long v) {
		return String.format(Locale.ROOT, "%,d coins", v);
	}

	private String formatExactPrice(long v) {
		return v < 0 ? "Unavailable" : formatExactCoins(v);
	}

	// ── Item Presentation Helpers ─────────────────────────────────────────────

	public static ItemStack lootChestIcon(String chestTitle) {
		return resolveChestIcon(chestTitle);
	}

	public static int lootChestColor(String chestTitle) {
		return chestColor(chestTitle);
	}

	public static ItemStack lootItemIcon(String itemId) {
		return resolveItemIcon(itemId);
	}

	public static int lootItemColor(String itemId) {
		return itemColor(itemId);
	}

	static ItemStack resolveChestIcon(String chestTitle) {
		if (chestTitle == null) return new ItemStack(Items.CHEST);
		String lower = chestTitle.toLowerCase(Locale.ROOT);
		if (lower.contains("paid chest")) {
			ItemStack icon = resolveItemIcon("KUUDRA_TEETH");
			return icon.isEmpty() ? new ItemStack(Items.PRISMARINE_SHARD) : icon;
		}
		if (lower.contains("free chest")) {
			ItemStack icon = resolveItemIcon("ESSENCE_CRIMSON");
			return icon.isEmpty() ? new ItemStack(Items.NETHER_STAR) : icon;
		}
		ItemStack cached = DungeonRunTrackerFeature.getChestIcon(chestTitle);
		if (!cached.isEmpty()) return cached;
		if (lower.contains("bedrock")) return new ItemStack(Items.BEDROCK);
		if (lower.contains("obsidian")) return new ItemStack(Items.OBSIDIAN);
		if (lower.contains("diamond")) return new ItemStack(Items.DIAMOND_BLOCK);
		if (lower.contains("emerald")) return new ItemStack(Items.EMERALD_BLOCK);
		if (lower.contains("gold")) return new ItemStack(Items.GOLD_BLOCK);
		if (lower.contains("iron")) return new ItemStack(Items.IRON_BLOCK);
		if (lower.contains("wood")) return new ItemStack(Items.OAK_PLANKS);
		return new ItemStack(Items.CHEST);
	}

	static int chestColor(String chestTitle) {
		if (chestTitle == null) return 0xFFFFFFFF;
		String lower = chestTitle.toLowerCase(Locale.ROOT);
		if (lower.contains("bedrock")) return 0xFFAAAAAA;
		if (lower.contains("obsidian")) return 0xFFAA00AA;
		if (lower.contains("diamond")) return 0xFF55FFFF;
		if (lower.contains("emerald")) return 0xFF55FF55;
		if (lower.contains("gold")) return 0xFFFFAA00;
		if (lower.contains("iron")) return 0xFFFFFFFF;
		if (lower.contains("wood")) return 0xFFAA5500;
		if (lower.contains("paid chest")) return 0xFFFF55FF;
		if (lower.contains("free chest")) return 0xFF55FFFF;
		return 0xFFFFFFFF;
	}

	static ItemStack resolveItemIcon(String itemId) {
		ItemStack cached = DungeonRunTrackerFeature.getItemIcon(itemId);
		if (!cached.isEmpty()) return cached;
		if (itemId == null || itemId.isBlank()) return ItemStack.EMPTY;
		String id = normalizeResolverItemId(itemId.trim().toUpperCase(Locale.ROOT));
		NeuItemResolver.enqueue(id);
		ItemStack neu = NeuItemResolver.getResolvedStack(id);
		if (NeuItemResolver.hasRenderableModel(neu)) return neu;
		return fallbackItemIcon(id);
	}

	private static ItemStack fallbackItemIcon(String id) {
		if (id == null) return ItemStack.EMPTY;
		if (id.equals("KUUDRA_TEETH")) return new ItemStack(Items.PRISMARINE_SHARD);
		if (id.equals("DUNGEON_CHEST_KEY")) return new ItemStack(Items.TRIPWIRE_HOOK);
		if (id.equals("KISMET_FEATHER") || id.equals("ANANKE_FEATHER")) return new ItemStack(Items.FEATHER);
		if (id.equals("WHEEL_OF_FATE")) return new ItemStack(Items.CLOCK);
		if (id.startsWith("ESSENCE_")) return new ItemStack(Items.NETHER_STAR);
		if (id.startsWith("ENCHANTMENT_")) return new ItemStack(Items.ENCHANTED_BOOK);
		if (id.startsWith("SHARD_")) return new ItemStack(Items.AMETHYST_SHARD);
		if (id.equals("RECOMBOBULATOR_3000")) return new ItemStack(Items.EMERALD);
		if (id.endsWith("MASTER_STAR")) return new ItemStack(Items.NETHER_STAR);
		if (id.equals("NECROMANCER_BROOCH")) return new ItemStack(Items.POPPY);
		if (id.equals("NECRON_HANDLE")) return new ItemStack(Items.STICK);
		if (id.contains("KUUDRA") || id.endsWith("_CORE")) return new ItemStack(Items.MAGMA_CREAM);
		if (id.contains("SCROLL") || id.contains("SHIELD")) return new ItemStack(Items.WRITABLE_BOOK);
		if (id.contains("SWORD") || id.contains("BLADE") || id.contains("DAGGER") || id.contains("CLOAK")) return new ItemStack(Items.IRON_SWORD);
		if (id.contains("BOW")) return new ItemStack(Items.BOW);
		if (id.contains("HELMET")) return new ItemStack(Items.LEATHER_HELMET);
		if (id.contains("CHESTPLATE")) return new ItemStack(Items.LEATHER_CHESTPLATE);
		if (id.contains("LEGGINGS")) return new ItemStack(Items.LEATHER_LEGGINGS);
		if (id.contains("BOOTS")) return new ItemStack(Items.LEATHER_BOOTS);
		return ItemStack.EMPTY;
	}

	static int itemColor(String itemId) {
		if (itemId == null || itemId.isBlank()) return TEXT_PRIMARY;
		String id = normalizeResolverItemId(itemId.trim().toUpperCase(Locale.ROOT));
		if (id.equals("ESSENCE_WITHER")) return 0xFFBAB4CE;
		if (id.equals("ESSENCE_UNDEAD")) return 0xFFC0C9D8;
		if (id.equals("ESSENCE_CRIMSON")) return 0xFFFF8D65;
		Integer neuColor = NeuItemResolver.getColor(id);
		if (neuColor != null) return neuColor;
		if (id.startsWith("ENCHANTMENT_ULTIMATE_")) return 0xFFFF55FF;
		if (id.startsWith("ENCHANTMENT_")) return 0xFF5555FF;
		if (id.startsWith("SHARD_")) return 0xFF5555FF;
		if (id.contains("HANDLE") || id.contains("MASTER_STAR") || id.contains("SCROLL")) return 0xFFFF55FF;
		if (id.contains("RECOMBOBULATOR") || id.contains("WITHER_") || id.contains("PRECURSOR") || id.contains("GIANT_SWORD") || id.contains("CLAYMORE")) return 0xFFFFAA00;
		if (id.contains("SHADOW_ASSASSIN") || id.contains("FEL_SKULL") || id.contains("BROOCH") || id.contains("WHEEL_OF_FATE")) return 0xFFAA00AA;
		if (id.contains("KUUDRA_TEETH") || id.contains("FEATHER")) return 0xFF5555FF;
		if (id.contains("KUUDRA_CORE")) return 0xFFFF5555;
		return TEXT_PRIMARY;
	}

	private static List<ItemStack> modifierIcons(DungeonRunRecord record) {
		if (record == null) return List.of();
		List<ItemStack> icons = new ArrayList<>();
		if (record.usedDungeonChestKey || record.dungeonChestKeyCostCoins > 0L) icons.add(resolveItemIcon("DUNGEON_CHEST_KEY"));
		if (record.usedKismetFeather || record.kismetFeatherCostCoins > 0L) icons.add(resolveItemIcon("KISMET_FEATHER"));
		if (record.usedWheelOfFate || record.wheelOfFateCostCoins > 0L) icons.add(resolveItemIcon("WHEEL_OF_FATE"));
		return icons;
	}

	private static String normalizeResolverItemId(String id) {
		return switch (id) {
			case "NECRONS_HANDLE" -> "NECRON_HANDLE";
			case "NECROMANCERS_BROOCH" -> "NECROMANCER_BROOCH";
			case "SHARD_WITHER_SPECTRE" -> "SHARD_WITHER_SPECTER";
			case "SCARFS_STUDIES" -> "SCARF_STUDIES";
			case "SPIRIT_STONE" -> "SPIRIT_DECOY";
			case "SPIRIT_BOOTS" -> "THORNS_BOOTS";
			case "SPIRIT_PET" -> "SPIRIT;3";
			case "WARPED_STONE" -> "AOTE_STONE";
			case "ADAPTIVE_BLADE" -> "STONE_BLADE";
			case "WITHER_CLOAK_SWORD" -> "WITHER_CLOAK";
			case "AURORA_STAFF" -> "RUNIC_STAFF";
			default -> {
				String attributeShardId = NeuItemResolver.getAttributeShardInternalName(id);
				yield attributeShardId != null ? attributeShardId : id;
			}
		};
	}

	private static boolean isUltimateItem(String itemId) {
		return itemId != null && itemId.toUpperCase(Locale.ROOT).startsWith("ENCHANTMENT_ULTIMATE_");
	}

	private static ItemStack forcedSalvageEssenceIcon() {
		ItemStack icon = resolveItemIcon("ESSENCE_CRIMSON");
		return icon.isEmpty() ? new ItemStack(Items.NETHER_STAR) : icon;
	}

	// ── Inner Classes / Records ───────────────────────────────────────────────

	private record ClickTarget(int x, int y, int w, int h, int button, Runnable action) {}
	private record GraphHover(long timestamp, double value, boolean recorded) {}
	private record HoverTarget(int x, int y, int w, int h, String text, int boundX, int boundY, int boundW, int boundH) {
		HoverTarget(int x, int y, int w, int h, String text) {
			this(x, y, w, h, text, 0, 0, 0, 0);
		}

		static HoverTarget bounded(int x, int y, int w, int h, String text, int boundX, int boundY, int boundW, int boundH) {
			return new HoverTarget(x, y, w, h, text, boundX, boundY, boundW, boundH);
		}
	}
	private record GraphGeometry(
		OverviewDashboardData.Snapshot snapshot,
		int plotX,
		int plotY,
		int plotW,
		int plotH,
		float[] xs,
		float[] ys,
		int count,
		float baseline,
		boolean stepped,
		long minValue,
		long maxValue,
		long firstTime,
		long timeSpan,
		List<Long> yTicks
	) {}

	private record DisplayRow(String label, String itemId, int quantity, long unitPrice, long totalPrice, boolean forcedSalvage, boolean unopened) {}
	private record LootRecordSource(DungeonRunRecord record, boolean unopened) {}
	private record LootEntrySource(DungeonLootEntry entry, boolean unopened) {}

	private record RunSelection(List<DungeonRunRecord> records, long totalCostCoins, long totalValueCoins, long totalProfitCoins) {
		static RunSelection logged(List<DungeonRunRecord> records, long cost, long value, long profit) {
			return new RunSelection(records, cost, value, profit);
		}
	}

	private enum LootSortColumn { ITEM, QUANTITY, EACH, TOTAL }
	private enum ScrollbarDrag { NONE, LIST, TABLE }
	private enum SettingsDropdown { NONE, VISIBILITY, PRICE, FACTION, PET_RARITY, THEME }

	private static final class MutableAgg {
		final String label;
		final String itemId;
		final boolean unopened;
		int quantity;
		boolean forcedSalvage;
		MutableAgg(String label, String itemId, boolean unopened) {
			this.label = label;
			this.itemId = itemId == null ? "" : itemId;
			this.unopened = unopened;
		}
	}
}
