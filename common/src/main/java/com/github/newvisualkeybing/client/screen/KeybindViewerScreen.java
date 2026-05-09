package com.github.newvisualkeybing.client.screen;

import com.github.newvisualkeybing.client.keyboard.FilterTab;
import com.github.newvisualkeybing.client.keyboard.KeyBindingScanner;
import com.github.newvisualkeybing.client.keyboard.KeybindProfileStore;
import com.github.newvisualkeybing.client.keyboard.KeyboardLayoutData;
import com.github.newvisualkeybing.client.ui.MCButton;
import com.github.newvisualkeybing.client.ui.UITheme;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class KeybindViewerScreen extends Screen {

    private static final int HEADER_H = 36;
    private static final int TOOLBAR_H = 32;
    private static final int STATUS_H = 22;
    private static final int CHROME_GAP = 6;
    private static final int SEARCH_W_DEFAULT = 220;
    private static final int SEARCH_BH = 18;

    private static final int BODY_PAD = 8;
    private static final int COL_GAP = 8;
    private static final int MOD_PANEL_W = 160;
    private static final int MOUSE_PANEL_W = 132;
    private static final int DETAIL_PANEL_W = 210;
    private static final int COMPACT_WIDTH_THRESHOLD = 760;
    private static final int RIGHT_RAIL_STACK_THRESHOLD = 980;
    private static final int MOUSE_PANEL_STACK_H = 158;

    private static final int PANEL_PAD = 10;
    private static final int PANEL_RADIUS = 8;
    private static final int PANEL_TITLE_Y = 10;
    private static final int PANEL_CONTENT_TOP = 28;
    private static final int ACTION_BTN_H = 20;
    private static final int ACTION_BTN_GAP = 6;

    private final Screen parent;
    private final KeyBindingScanner scanner = new KeyBindingScanner();
    private final KeybindTooltipRenderer tooltipRenderer = new KeybindTooltipRenderer(scanner);
    private final KeybindProfileStore profileStore = KeybindProfileStore.global();
    private final KeybindProfilePanel profilePanel = new KeybindProfilePanel(
            profileStore, this::onProfileMutation, this::showNotice);
    private final KeybindKeyboardRenderer keyboardRenderer = new KeybindKeyboardRenderer(scanner);
    private final KeybindMouseRenderer mouseRenderer = new KeybindMouseRenderer(scanner);
    private final KeybindDetailPanel detailPanel = new KeybindDetailPanel(scanner);

    private EditBox searchBox;
    private MCButton closeButton;
    private MCButton manageButton;
    private MCButton modToggleButton;
    private MCButton profileToggleButton;
    private MCButton layoutButton;

    private KeyboardLayoutData.Style currentStyle = KeyboardLayoutData.Style.ANSI_104;

    private String noticeMsg;
    private long noticeTime;
    private float animTick;

    private FilterTab activeFilter = FilterTab.ALL;
    private Set<Integer> textFilteredKeys;
    private Set<Integer> tabFilteredKeys;
    private Set<Integer> modFilteredKeys;
    private Integer selectedVirtualKey;
    private Integer hoveredVirtualKey;
    private boolean modPanelOpen;
    private boolean profilePanelOpen;
    private String selectedModId;
    private String modSearchQuery = "";
    private int modScrollOffset;

    private float keyScale;
    private int keyboardX;
    private int keyboardY;
    private int cachedLayoutWidth = -1;
    private int cachedLayoutHeight = -1;
    private KeyboardLayoutData.Style cachedLayoutStyle;
    private boolean cachedLayoutModOpen;
    private int mousePanelX;
    private int mousePanelY;
    private int mousePanelH;
    private int detailPanelX;
    private int detailPanelY;
    private int detailPanelH;
    private int detailPanelW = DETAIL_PANEL_W;
    private int mousePanelW = MOUSE_PANEL_W;
    private boolean rightRailStacked;
    private int contentTop;
    private int contentBottom;

    private int toolbarTabsX;
    private int toolbarTabsW;
    private int toolbarSearchX;
    private int toolbarSearchW;
    private int toolbarLegendX;

    public KeybindViewerScreen(Screen parent) {
        super(Component.translatable("screen.newvisualkeybing.viewer.title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        super.init();
        UITheme.setMode(UITheme.Mode.DARK);
        scanner.scan();

        boolean compact = width < COMPACT_WIDTH_THRESHOLD;
        if (compact) {
            modPanelOpen = false;
            profilePanelOpen = false;
        }

        int btnH = 22;
        int btnGap = 4;
        int btnY = (HEADER_H - btnH) / 2;
        int btnCloseW = 50;
        int btnModsW = compact ? 38 : 56;
        int btnProfilesW = compact ? 52 : 68;
        int btnManageW = compact ? 48 : 64;
        int btnLayoutW = compact ? 56 : 78;

        int xClose = width - 8 - btnCloseW;
        int xMods = xClose - btnGap - btnModsW;
        int xProfiles = xMods - btnGap - btnProfilesW;
        int xManage = xProfiles - btnGap - btnManageW;
        int xLayout = xManage - btnGap - btnLayoutW;

        computeToolbarGeometry(compact);

        int searchBoxY = HEADER_H + (TOOLBAR_H - SEARCH_BH) / 2;
        searchBox = new EditBox(font, toolbarSearchX, searchBoxY, toolbarSearchW, SEARCH_BH,
                Component.translatable("screen.newvisualkeybing.viewer.search"));
        searchBox.setHint(Component.translatable("screen.newvisualkeybing.viewer.search"));
        searchBox.setResponder(value -> textFilteredKeys = scanner.filterKeys(value));
        addRenderableWidget(searchBox);

        layoutButton = MCButton.create(xLayout, btnY, btnLayoutW, btnH,
                layoutLabel(currentStyle), button -> {
            currentStyle = currentStyle.next();
            button.setMessage(layoutLabel(currentStyle));
        });
        addRenderableWidget(layoutButton);

        manageButton = MCButton.create(xManage, btnY, btnManageW, btnH,
                Component.translatable("screen.newvisualkeybing.viewer.manage"),
                button -> minecraft.setScreen(new KeybindEditScreen(this)));
        addRenderableWidget(manageButton);

        profileToggleButton = MCButton.create(xProfiles, btnY, btnProfilesW, btnH,
                Component.translatable("screen.newvisualkeybing.viewer.profiles"), button -> {
            profilePanelOpen = !profilePanelOpen;
            if (profilePanelOpen) modPanelOpen = false;
            invalidateLayoutCache();
        });
        addRenderableWidget(profileToggleButton);

        modToggleButton = MCButton.create(xMods, btnY, btnModsW, btnH,
                Component.translatable("screen.newvisualkeybing.viewer.mods"), button -> {
            modPanelOpen = !modPanelOpen;
            if (modPanelOpen) profilePanelOpen = false;
            invalidateLayoutCache();
        });
        addRenderableWidget(modToggleButton);

        closeButton = MCButton.create(xClose, btnY, btnCloseW, btnH,
                Component.translatable("gui.done"), button -> onClose());
        addRenderableWidget(closeButton);

        refreshFilters();
    }

    private void computeToolbarGeometry(boolean compact) {
        FilterTab[] tabs = FilterTab.values();
        int tabsW = 0;
        for (FilterTab t : tabs) tabsW += font.width(t.getLabel()) + 14;
        tabsW += (tabs.length - 1) * 4;

        int legendW = compact
                ? 4 * 14 + 3 * 4               
                : measureLegendWidth();
        int searchW = SEARCH_W_DEFAULT;
        int outerPad = 12;
        int innerGap = 12;
        int totalAvail = width - outerPad * 2;

        if (tabsW + searchW + legendW + innerGap * 2 > totalAvail) {
            searchW = Math.max(140, totalAvail - tabsW - legendW - innerGap * 2);
        }

        toolbarTabsX = outerPad;
        toolbarTabsW = tabsW;
        toolbarSearchX = (width - searchW) / 2;
        toolbarSearchW = searchW;
        toolbarLegendX = width - outerPad - legendW;

        int afterTabs = toolbarTabsX + tabsW + innerGap;
        if (toolbarSearchX < afterTabs) toolbarSearchX = afterTabs;
    }

    private int measureLegendWidth() {
        int total = 0;
        String[] keys = {
                "screen.newvisualkeybing.viewer.legend.free",
                "screen.newvisualkeybing.viewer.legend.self",
                "screen.newvisualkeybing.viewer.legend.other",
                "screen.newvisualkeybing.viewer.legend.conflict"
        };
        for (int i = 0; i < keys.length; i++) {
            total += 8 + 4 + font.width(Component.translatable(keys[i]).getString());
            if (i < keys.length - 1) total += 10;
        }
        return total;
    }

    @Override
    public void tick() {
        super.tick();
        searchBox.tick();
        scanner.refreshIfNeeded();
        refreshFilters();
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        animTick += partialTick;
        renderBackground(g);
        layoutPanels();
        hoveredVirtualKey = null;

        var c = UITheme.colors();
        g.fill(0, 0, width, height, UITheme.withAlpha(c.panelBg(), 0xE6));

        renderHeaderBar(g);
        renderToolbar(g, mouseX, mouseY);

        if (modPanelOpen && width >= COMPACT_WIDTH_THRESHOLD) {
            renderModPanel(g, mouseX, mouseY);
        } else if (profilePanelOpen && width >= COMPACT_WIDTH_THRESHOLD) {
            int x = BODY_PAD;
            int y = contentTop;
            int h = contentBottom - contentTop;
            profilePanel.render(g, font, x, y, h, mouseX, mouseY);
        }

        renderKeyboard(g, mouseX, mouseY);
        renderMousePanel(g, mouseX, mouseY);
        renderDetailPanel(g, selectedVirtualKey != null ? selectedVirtualKey : hoveredVirtualKey, mouseX, mouseY);
        renderStatusBar(g);

        super.render(g, mouseX, mouseY, partialTick);

        if (hoveredVirtualKey != null
                && (selectedVirtualKey == null || hoveredVirtualKey.intValue() != selectedVirtualKey.intValue())) {
            tooltipRenderer.render(g, font, width, height, hoveredVirtualKey, mouseX, mouseY);
        }

        renderNotice(g);
    }

    private void renderHeaderBar(GuiGraphics g) {
        var c = UITheme.colors();
        UITheme.fillGradient(g, 0, 0, width, HEADER_H,
                UITheme.lerpColor(c.headerBg(), c.panelBg(), 0.10f),
                c.headerBg());
        g.fill(0, HEADER_H - 1, width, HEADER_H, c.divider());
        g.fill(0, HEADER_H, width, HEADER_H + 1, UITheme.withAlpha(c.accent(), 0x70));

        g.drawString(font, title, 12, (HEADER_H - font.lineHeight) / 2, c.textPrimary(), true);
    }

    private void renderToolbar(GuiGraphics g, int mouseX, int mouseY) {
        var c = UITheme.colors();
        int y = HEADER_H;
        UITheme.fillGradient(g, 0, y, width, y + TOOLBAR_H,
                c.headerBg(), UITheme.lerpColor(c.headerBg(), c.panelBg(), 0.55f));
        g.fill(0, y + TOOLBAR_H - 1, width, y + TOOLBAR_H, c.divider());

        renderToolbarTabs(g, mouseX, mouseY);
        renderToolbarSearchFrame(g);
        renderToolbarLegend(g, mouseX, mouseY);
    }

    private void renderToolbarTabs(GuiGraphics g, int mouseX, int mouseY) {
        var c = UITheme.colors();
        FilterTab[] tabs = FilterTab.values();
        int x = toolbarTabsX;
        int y = HEADER_H + 4;
        int h = TOOLBAR_H - 8;
        for (FilterTab tab : tabs) {
            int w = font.width(tab.getLabel()) + 14;
            boolean active = tab == activeFilter;
            boolean hovered = inside(mouseX, mouseY, x, y, w, h);
            int fill = active
                    ? UITheme.lerpColor(c.widgetBg(), c.accent(), 0.55f)
                    : hovered ? UITheme.lerpColor(c.widgetBg(), c.accentAlt(), 0.20f) : c.widgetBg();
            UITheme.fillRoundedRect(g, x, y, w, h, h / 2, fill);
            UITheme.drawRoundedBorder(g, x, y, w, h, h / 2,
                    active ? c.accent() : UITheme.withAlpha(c.widgetBorder(), 0xB0));
            g.drawString(font, tab.getLabel(), x + 7, y + (h - font.lineHeight) / 2,
                    active ? 0xFFFFFFFF : c.textSecondary(), false);
            x += w + 4;
        }
    }

    private void renderToolbarSearchFrame(GuiGraphics g) {
        var c = UITheme.colors();
        int sx = toolbarSearchX - 3;
        int sy = HEADER_H + (TOOLBAR_H - SEARCH_BH) / 2 - 3;
        int sw = toolbarSearchW + 6;
        int sh = SEARCH_BH + 6;
        int focusColor = searchBox != null && searchBox.isFocused() ? c.accent() : UITheme.withAlpha(c.accent(), 0x40);
        UITheme.drawRoundedBorder(g, sx, sy, sw, sh, 6, focusColor);
    }

    private void renderToolbarLegend(GuiGraphics g, int mouseX, int mouseY) {
        var c = UITheme.colors();
        boolean compact = width < COMPACT_WIDTH_THRESHOLD;
        int x = toolbarLegendX;
        int y = HEADER_H + (TOOLBAR_H - 12) / 2;

        String[] labels = {
                Component.translatable("screen.newvisualkeybing.viewer.legend.free").getString(),
                Component.translatable("screen.newvisualkeybing.viewer.legend.self").getString(),
                Component.translatable("screen.newvisualkeybing.viewer.legend.other").getString(),
                Component.translatable("screen.newvisualkeybing.viewer.legend.conflict").getString()
        };
        int[] colors = { c.widgetBorder(), c.accent(), c.success(), c.danger() };

        for (int i = 0; i < labels.length; i++) {
            UITheme.fillRoundedRect(g, x, y + 2, 8, 8, 4, colors[i]);
            UITheme.drawRoundedBorder(g, x, y + 2, 8, 8, 4, UITheme.withAlpha(0xFFFFFF, 0x30));
            x += 8;
            if (!compact) {
                x += 4;
                g.drawString(font, labels[i], x, y + 2, c.textSecondary(), false);
                x += font.width(labels[i]);
            }
            if (i < labels.length - 1) x += 10;
        }
    }

    private void renderStatusBar(GuiGraphics g) {
        var c = UITheme.colors();
        int y = height - STATUS_H;
        UITheme.fillGradient(g, 0, y, width, height,
                UITheme.lerpColor(c.headerBg(), c.panelBg(), 0.50f),
                c.headerBg());
        g.fill(0, y, width, y + 1, c.divider());

        KeyBindingScanner.ScanStats stats = scanner.getStats();
        int chipY = y + (STATUS_H - 14) / 2;
        int x = 10;

        x = renderStatChip(g, x, chipY, c.widgetBorder(),
                Component.translatable("screen.newvisualkeybing.viewer.legend.free").getString(),
                stats.free());
        x += 6;
        x = renderStatChip(g, x, chipY, c.accent(),
                Component.translatable("screen.newvisualkeybing.viewer.legend.self").getString(),
                stats.self());
        x += 6;
        x = renderStatChip(g, x, chipY, c.success(),
                Component.translatable("screen.newvisualkeybing.viewer.legend.other").getString(),
                stats.other());
        x += 6;
        x = renderStatChip(g, x, chipY, c.danger(),
                Component.translatable("screen.newvisualkeybing.viewer.legend.conflict").getString(),
                stats.conflict());

        int textY = y + (STATUS_H - font.lineHeight) / 2;
        String scale = Component.translatable("screen.newvisualkeybing.viewer.scale", Math.round(keyScale)).getString();
        String layoutName = layoutLabel(currentStyle).getString();
        String middle = layoutName + "  |  " + scale;
        g.drawString(font, middle, (width - font.width(middle)) / 2, textY, c.textMuted(), false);

        String hint = Component.translatable("screen.newvisualkeybing.viewer.hint").getString();
        g.drawString(font, hint, width - font.width(hint) - 10, textY, c.textMuted(), false);
    }

    private int renderStatChip(GuiGraphics g, int x, int y, int dotColor, String label, int count) {
        var c = UITheme.colors();
        String text = count + " " + label;
        int chipW = font.width(text) + 18;
        int chipH = 14;
        int fill = UITheme.lerpColor(c.widgetBg(), dotColor, 0.14f);
        UITheme.fillRoundedRect(g, x, y, chipW, chipH, chipH / 2, fill);
        UITheme.drawRoundedBorder(g, x, y, chipW, chipH, chipH / 2, UITheme.withAlpha(dotColor, 0x90));
        UITheme.fillRoundedRect(g, x + 5, y + (chipH - 5) / 2, 5, 5, 2, dotColor);
        g.drawString(font, text, x + 13, y + (chipH - font.lineHeight) / 2 + 1, c.textSecondary(), false);
        return x + chipW;
    }

    static int paintPanelBase(GuiGraphics g, net.minecraft.client.gui.Font font, int x, int y, int w, int h, String title) {
        var c = UITheme.colors();
        UITheme.drawGlassPanel(g, x, y, w, h, PANEL_RADIUS);
        g.drawString(font, title, x + PANEL_PAD, y + PANEL_TITLE_Y, c.textPrimary(), false);
        int divY = y + PANEL_TITLE_Y + font.lineHeight + 4;
        g.fill(x + PANEL_PAD, divY, x + w - PANEL_PAD, divY + 1, UITheme.withAlpha(c.divider(), 0xA0));
        return y + PANEL_CONTENT_TOP;
    }

    private void renderModPanel(GuiGraphics g, int mouseX, int mouseY) {
        var c = UITheme.colors();
        int x = BODY_PAD;
        int y = contentTop;
        int w = MOD_PANEL_W;
        int h = contentBottom - contentTop;
        int contentY = paintPanelBase(g, font, x, y, w, h,
                Component.translatable("screen.newvisualkeybing.viewer.mods").getString());

        int fieldX = x + PANEL_PAD;
        int fieldW = w - PANEL_PAD * 2;
        int searchY = contentY + 4;
        UITheme.fillRoundedRect(g, fieldX, searchY, fieldW, 18, 6, c.inputBg());
        UITheme.drawRoundedBorder(g, fieldX, searchY, fieldW, 18, 6, c.widgetBorder());
        String display = modSearchQuery.isBlank()
                ? Component.translatable("screen.newvisualkeybing.viewer.mod_search").getString()
                : modSearchQuery;
        g.drawString(font, display, fieldX + 6, searchY + 5,
                modSearchQuery.isBlank() ? c.textMuted() : c.textPrimary(), false);

        int listY = searchY + 26;
        int listBottom = y + h - PANEL_PAD - ACTION_BTN_H - ACTION_BTN_GAP;
        List<Map.Entry<String, String>> mods = filteredModEntries();
        int rowH = 18;
        int visibleRows = Math.max(1, (listBottom - listY) / rowH);
        modScrollOffset = Mth.clamp(modScrollOffset, 0, Math.max(0, mods.size() - visibleRows));

        int rowY = listY;
        for (int i = modScrollOffset; i < mods.size() && i < modScrollOffset + visibleRows; i++) {
            Map.Entry<String, String> mod = mods.get(i);
            boolean selected = mod.getKey().equals(selectedModId);
            boolean hovered = inside(mouseX, mouseY, fieldX, rowY, fieldW, rowH - 1);
            int fill = selected ? UITheme.lerpColor(c.widgetBg(), c.accent(), 0.40f)
                    : hovered ? UITheme.lerpColor(c.widgetBg(), c.accentAlt(), 0.18f) : c.widgetBg();
            UITheme.fillRoundedRect(g, fieldX, rowY, fieldW, rowH - 1, 5, fill);
            if (!selected && !hovered && i < mods.size() - 1
                    && i < modScrollOffset + visibleRows - 1) {
                g.fill(fieldX + 6, rowY + rowH - 2, fieldX + fieldW - 6, rowY + rowH - 1,
                        UITheme.withAlpha(c.divider(), 0x30));
            }
            String modName = trim(mod.getValue(), 18);
            g.drawString(font, modName, fieldX + 6, rowY + 5,
                    selected ? 0xFFFFFFFF : c.textSecondary(), false);
            rowY += rowH;
        }

        int actionY = y + h - PANEL_PAD - ACTION_BTN_H;
        boolean clearHover = inside(mouseX, mouseY, fieldX, actionY, fieldW, ACTION_BTN_H);
        renderActionButton(g, font, fieldX, actionY, fieldW, ACTION_BTN_H,
                Component.translatable("screen.newvisualkeybing.viewer.clear_mod").getString(),
                selectedModId == null ? c.widgetBorder() : c.danger(), clearHover);
    }

    private void renderKeyboard(GuiGraphics g, int mouseX, int mouseY) {
        Integer hover = keyboardRenderer.render(g, font, currentStyle,
                keyboardX, keyboardY, keyScale,
                selectedVirtualKey, this::isVisibleKey, mouseX, mouseY, animTick);
        if (hover != null) hoveredVirtualKey = hover;
    }


    private Component layoutLabel(KeyboardLayoutData.Style style) {
        return switch (style) {
            case ANSI_104   -> Component.translatable("screen.newvisualkeybing.viewer.layout.ansi_104");
            case KEYS_98    -> Component.translatable("screen.newvisualkeybing.viewer.layout.keys_98");
            case TKL_87     -> Component.translatable("screen.newvisualkeybing.viewer.layout.tkl_87");
            case COMPACT_60 -> Component.translatable("screen.newvisualkeybing.viewer.layout.compact_60");
        };
    }

    private void renderMousePanel(GuiGraphics g, int mouseX, int mouseY) {
        Integer hover = mouseRenderer.render(g, font, mousePanelX, mousePanelY, mousePanelW, mousePanelH,
                selectedVirtualKey, this::isVisibleKey, mouseX, mouseY, animTick);
        if (hover != null) hoveredVirtualKey = hover;
    }


    private void renderDetailPanel(GuiGraphics g, Integer virtualKey, int mouseX, int mouseY) {
        detailPanel.render(g, font, detailPanelX, detailPanelY, detailPanelW, detailPanelH,
                virtualKey, mouseX, mouseY);
    }

    static String fitToWidth(net.minecraft.client.gui.Font font, String text, int maxW) {
        if (font.width(text) <= maxW) return text;
        String ellipsis = "..";
        int eW = font.width(ellipsis);
        if (maxW <= eW) return ellipsis;
        StringBuilder sb = new StringBuilder();
        int w = 0;
        for (int i = 0; i < text.length(); i++) {
            int cw = font.width(String.valueOf(text.charAt(i)));
            if (w + cw + eW > maxW) break;
            sb.append(text.charAt(i));
            w += cw;
        }
        return sb.append(ellipsis).toString();
    }

    static void renderActionButton(GuiGraphics g, net.minecraft.client.gui.Font font,
                                   int x, int y, int w, int h, String label, int accent, boolean hovered) {
        var c = UITheme.colors();
        int fill = UITheme.lerpColor(c.widgetBg(), accent, hovered ? 0.50f : 0.26f);
        UITheme.fillRoundedRect(g, x, y, w, h, h / 3, fill);
        UITheme.drawRoundedBorder(g, x, y, w, h, h / 3, UITheme.withAlpha(accent, 0xC0));
        UITheme.fillRoundedRect(g, x + 1, y + 1, w - 2, 1, h / 3, UITheme.withAlpha(0xFFFFFF, hovered ? 0x18 : 0x10));
        g.drawString(font, label,
                x + (w - font.width(label)) / 2,
                y + (h - font.lineHeight) / 2,
                c.textPrimary(), false);
    }

    private void renderHoverTooltip(GuiGraphics g, int virtualKey, int mouseX, int mouseY) {
        tooltipRenderer.render(g, font, width, height, virtualKey, mouseX, mouseY);
    }


    private void renderNotice(GuiGraphics g) {
        if (noticeMsg == null) return;
        long elapsed = System.currentTimeMillis() - noticeTime;
        if (elapsed > 2500) { noticeMsg = null; return; }
        float alpha = elapsed < 300 ? elapsed / 300f
                : elapsed > 2000 ? (2500f - elapsed) / 500f
                : 1f;
        var c = UITheme.colors();
        int alpha255 = Math.round(alpha * 0xE6);
        int textColor = UITheme.withAlpha(c.textPrimary(), alpha255);
        int bgColor = UITheme.withAlpha(c.headerBg(), Math.round(alpha * 0xCC));
        int accent = UITheme.withAlpha(c.accent(), Math.round(alpha * 0xC0));

        int textW = font.width(noticeMsg);
        int padX = 12;
        int padY = 6;
        int boxW = textW + padX * 2;
        int boxH = font.lineHeight + padY * 2;
        int boxX = (width - boxW) / 2;
        int boxY = height - STATUS_H - boxH - 8;

        UITheme.fillRoundedRect(g, boxX, boxY, boxW, boxH, 8, bgColor);
        UITheme.drawRoundedBorder(g, boxX, boxY, boxW, boxH, 8, accent);
        UITheme.fillRoundedRect(g, boxX, boxY, boxW, 2, 2, accent);
        g.drawString(font, noticeMsg, boxX + padX, boxY + padY, textColor, false);
    }

    private void showNotice(String msg) {
        this.noticeMsg = msg;
        this.noticeTime = System.currentTimeMillis();
    }

    private void onProfileMutation() {
        scanner.scan();
        refreshFilters();
    }

    private void invalidateLayoutCache() {
        cachedLayoutWidth = -1;
        cachedLayoutHeight = -1;
    }

    private void layoutPanels() {
        boolean leftRailOpen = modPanelOpen || profilePanelOpen;
        if (width == cachedLayoutWidth && height == cachedLayoutHeight
                && currentStyle == cachedLayoutStyle && leftRailOpen == cachedLayoutModOpen) {
            return;
        }
        cachedLayoutWidth = width;
        cachedLayoutHeight = height;
        cachedLayoutStyle = currentStyle;
        cachedLayoutModOpen = leftRailOpen;

        boolean compact = width < COMPACT_WIDTH_THRESHOLD;
        contentTop = HEADER_H + TOOLBAR_H + CHROME_GAP;
        contentBottom = height - STATUS_H - 6;
        int bodyH = contentBottom - contentTop;

        float uiScale = Mth.clamp(Math.min(width / 980.0f, height / 560.0f), 0.78f, 1.10f);
        int detailW = Mth.clamp(Math.round(DETAIL_PANEL_W * uiScale), 188, 250);
        int mouseW = Mth.clamp(Math.round(MOUSE_PANEL_W * uiScale), 124, 154);

        rightRailStacked = width < RIGHT_RAIL_STACK_THRESHOLD || bodyH > width * 0.46f;

        if (rightRailStacked) {
            int railW = Math.max(detailW, mouseW);
            detailPanelW = railW;
            mousePanelW = railW;
            mousePanelX = width - BODY_PAD - railW;
            mousePanelY = contentTop;
            mousePanelH = Math.min(MOUSE_PANEL_STACK_H, Math.max(128, bodyH / 3));
            detailPanelX = mousePanelX;
            detailPanelY = mousePanelY + mousePanelH + COL_GAP;
            detailPanelH = Math.max(96, contentBottom - detailPanelY);
        } else {
            detailPanelW = detailW;
            mousePanelW = mouseW;
            detailPanelX = width - BODY_PAD - detailPanelW;
            detailPanelY = contentTop;
            detailPanelH = bodyH;
            mousePanelX = detailPanelX - COL_GAP - mousePanelW;
            mousePanelY = contentTop;
            mousePanelH = bodyH;
        }

        int leftRailW = profilePanelOpen ? KeybindProfilePanel.WIDTH : MOD_PANEL_W;
        int leftMargin = (leftRailOpen && !compact) ? BODY_PAD + leftRailW + COL_GAP : BODY_PAD;
        int keyboardLeft = leftMargin;
        int keyboardRight = (rightRailStacked ? detailPanelX : mousePanelX) - COL_GAP;
        int keyboardSpaceW = keyboardRight - keyboardLeft;
        int keyboardSpaceH = bodyH;
        float widthU = currentStyle.widthU();
        float heightU = currentStyle.heightU();
        float gapW = (widthU - 1.0f) * KeyboardLayoutData.BASE_GAP;
        float gapH = (heightU - 1.0f) * KeyboardLayoutData.BASE_GAP;
        float scaleByW = (keyboardSpaceW - gapW) / widthU;
        float scaleByH = (keyboardSpaceH - gapH) / heightU;
        keyScale = Mth.clamp(Math.min(scaleByW, scaleByH), 6.0f, 30.0f);

        int kbW = KeyboardLayoutData.totalWidthPx(currentStyle, keyScale);
        int kbH = KeyboardLayoutData.totalHeightPx(currentStyle, keyScale);
        keyboardX = keyboardLeft + Math.max(0, (keyboardSpaceW - kbW) / 2);
        keyboardY = contentTop + Math.max(0, (keyboardSpaceH - kbH) / 2);
    }

    private void refreshFilters() {
        textFilteredKeys = scanner.filterKeys(searchBox != null ? searchBox.getValue() : "");
        tabFilteredKeys = scanner.filterByStatus(activeFilter);
        modFilteredKeys = scanner.filterByMod(selectedModId);
    }

    static int pulseAccent(float animTick) {
        var c = UITheme.colors();
        float pulse = 0.5f + 0.5f * (float) Math.sin(animTick * 0.18f);
        return UITheme.lerpColor(c.accent(), c.accentLight(), pulse);
    }

    private int statusAccentColor(KeyBindingScanner.KeyStatus status) {
        var c = UITheme.colors();
        return switch (status) {
            case FREE -> c.widgetBorder();
            case SELF -> c.accent();
            case OTHER_SINGLE, BOUND -> c.success();
            case CONFLICT -> c.danger();
        };
    }

    
    static int labelColorForStatus(KeyBindingScanner.KeyStatus status) {
        var c = UITheme.colors();
        return switch (status) {
            case CONFLICT -> 0xFFFFFFFF;
            case SELF -> 0xFFFFFFFF;
            case OTHER_SINGLE, BOUND -> c.textPrimary();
            case FREE -> c.textSecondary();
        };
    }

    static int keyStatusColor(KeyBindingScanner.KeyStatus status) {
        var c = UITheme.colors();
        return switch (status) {
            case FREE -> c.widgetBg();
            case SELF -> UITheme.lerpColor(c.widgetBg(), c.accent(), 0.68f);
            case OTHER_SINGLE, BOUND -> UITheme.lerpColor(c.widgetBg(), c.success(), 0.62f);
            case CONFLICT -> UITheme.lerpColor(c.widgetBg(), c.danger(), 0.82f);
        };
    }

    
    static int keyStatusColor(KeyBindingScanner.KeyStatus status, boolean matched) {
        int base = keyStatusColor(status);
        if (matched) return base;
        return UITheme.withAlpha(base, 0x55);
    }

    private boolean isVisibleKey(int virtualKey) {
        return matchesFilter(textFilteredKeys, virtualKey)
                && matchesFilter(tabFilteredKeys, virtualKey)
                && matchesFilter(modFilteredKeys, virtualKey);
    }

    private static boolean matchesFilter(Set<Integer> filter, int virtualKey) {
        return filter == null || filter.contains(virtualKey);
    }

    private List<Map.Entry<String, String>> filteredModEntries() {
        List<Map.Entry<String, String>> entries = new ArrayList<>();
        for (Map.Entry<String, String> entry : scanner.getAllRegisteredMods().entrySet()) {
            if (modSearchQuery.isBlank()
                    || entry.getValue().toLowerCase().contains(modSearchQuery.toLowerCase())
                    || entry.getKey().toLowerCase().contains(modSearchQuery.toLowerCase())) {
                entries.add(entry);
            }
        }
        return entries;
    }

    private static String trim(String value, int maxLength) {
        return value.length() <= maxLength ? value : value.substring(0, maxLength - 2) + "..";
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (super.mouseClicked(mouseX, mouseY, button)) return true;
        if (button != 0) return false;

        FilterTab[] tabs = FilterTab.values();
        int x = toolbarTabsX;
        int y = HEADER_H + 4;
        int h = TOOLBAR_H - 8;
        for (FilterTab tab : tabs) {
            int w = font.width(tab.getLabel()) + 14;
            if (inside(mouseX, mouseY, x, y, w, h)) {
                activeFilter = tab;
                refreshFilters();
                return true;
            }
            x += w + 4;
        }

        if (modPanelOpen && width >= COMPACT_WIDTH_THRESHOLD) {
            if (handleModPanelClick(mouseX, mouseY)) return true;
        } else if (profilePanelOpen && width >= COMPACT_WIDTH_THRESHOLD) {
            int px = BODY_PAD;
            int py = contentTop;
            int ph = contentBottom - contentTop;
            if (profilePanel.mouseClicked(mouseX, mouseY, px, py, ph)) return true;
        }

        boolean wheelSelected = selectedVirtualKey != null && KeyboardLayoutData.isWheel(selectedVirtualKey);
        if (selectedVirtualKey != null && !wheelSelected && detailPanel.isModifyHit(mouseX, mouseY)) {
            minecraft.setScreen(new KeybindEditScreen(this, selectedVirtualKey));
            return true;
        }
        if (selectedVirtualKey != null && !wheelSelected && detailPanel.isUnbindHit(mouseX, mouseY)) {
            int virtualKey = selectedVirtualKey;
            int countBefore = (KeyboardLayoutData.isMouse(virtualKey)
                    ? scanner.getMouseBindings(KeyboardLayoutData.virtualToMouseBtn(virtualKey))
                    : scanner.getBindings(virtualKey)).size();
            KeybindEditScreen.unbindAllForVirtualKey(virtualKey);
            scanner.scan();
            refreshFilters();
            String label = scanner.getVirtualKeyLabel(virtualKey);
            showNotice(Component.translatable("screen.newvisualkeybing.viewer.notice.unbind",
                    label, countBefore).getString());
            return true;
        }

        for (KeyboardLayoutData.KeyDef key : KeyboardLayoutData.getKeys(currentStyle)) {
            int kx = key.screenX(keyboardX, keyScale);
            int ky = key.screenY(keyboardY, keyScale);
            int kw = key.screenW(keyScale);
            int kh = key.screenH(keyScale);
            if (inside(mouseX, mouseY, kx, ky, kw, kh)) {
                selectedVirtualKey = key.glfwKey();
                detailPanel.resetScroll();
                return true;
            }
        }

        Integer mouseHit = mouseRenderer.hitTest(mouseX, mouseY);
        if (mouseHit != null) {
            selectedVirtualKey = mouseHit;
            detailPanel.resetScroll();
            return true;
        }

        selectedVirtualKey = null;
        return false;
    }

    private boolean handleModPanelClick(double mouseX, double mouseY) {
        int x = BODY_PAD;
        int y = contentTop;
        int w = MOD_PANEL_W;
        int h = contentBottom - contentTop;
        int contentY = y + PANEL_CONTENT_TOP;
        int fieldX = x + PANEL_PAD;
        int fieldW = w - PANEL_PAD * 2;
        int searchY = contentY + 4;
        int listY = searchY + 26;
        int actionY = y + h - PANEL_PAD - ACTION_BTN_H;
        int rowH = 18;
        int visibleRows = Math.max(1, (actionY - ACTION_BTN_GAP - listY) / rowH);

        if (inside(mouseX, mouseY, fieldX, searchY, fieldW, 18)) {
            modSearchQuery = "";
            return true;
        }

        List<Map.Entry<String, String>> mods = filteredModEntries();
        int rowY = listY;
        for (int i = modScrollOffset; i < mods.size() && i < modScrollOffset + visibleRows; i++) {
            if (inside(mouseX, mouseY, fieldX, rowY, fieldW, rowH - 1)) {
                selectedModId = mods.get(i).getKey().equals(selectedModId) ? null : mods.get(i).getKey();
                refreshFilters();
                return true;
            }
            rowY += rowH;
        }

        if (inside(mouseX, mouseY, fieldX, actionY, fieldW, ACTION_BTN_H)) {
            selectedModId = null;
            refreshFilters();
            return true;
        }
        return false;
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        if (modPanelOpen && width >= COMPACT_WIDTH_THRESHOLD && !searchBox.isFocused() && codePoint >= 32) {
            modSearchQuery += codePoint;
            modScrollOffset = 0;
            return true;
        }
        return super.charTyped(codePoint, modifiers);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (modPanelOpen && width >= COMPACT_WIDTH_THRESHOLD && !searchBox.isFocused()) {
            if (keyCode == 259 && !modSearchQuery.isEmpty()) {
                modSearchQuery = modSearchQuery.substring(0, modSearchQuery.length() - 1);
                return true;
            }
            if (keyCode == 256) modSearchQuery = "";
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (selectedVirtualKey != null
                && mouseX >= detailPanelX && mouseX <= detailPanelX + detailPanelW
                && mouseY >= detailPanelY && mouseY <= detailPanelY + detailPanelH) {
            detailPanel.scroll((int) Math.signum(delta));
            return true;
        }
        if (mouseX >= mousePanelX && mouseX <= mousePanelX + mousePanelW
                && mouseY >= mousePanelY && mouseY <= mousePanelY + mousePanelH) {
            selectedVirtualKey = delta > 0 ? KeyboardLayoutData.WHEEL_UP_VIRTUAL : KeyboardLayoutData.WHEEL_DOWN_VIRTUAL;
            return true;
        }
        if (modPanelOpen && width >= COMPACT_WIDTH_THRESHOLD) {
            int x = BODY_PAD;
            if (mouseX >= x && mouseX <= x + MOD_PANEL_W) {
                int actionY = contentTop + (contentBottom - contentTop) - PANEL_PAD - ACTION_BTN_H;
                int searchY = contentTop + PANEL_CONTENT_TOP + 4;
                int listY = searchY + 26;
                int visibleRows = Math.max(1, (actionY - ACTION_BTN_GAP - listY) / 18);
                modScrollOffset = Mth.clamp(modScrollOffset - (int) Math.signum(delta), 0,
                        Math.max(0, filteredModEntries().size() - visibleRows));
                return true;
            }
        }
        return super.mouseScrolled(mouseX, mouseY, delta);
    }

    static boolean inside(double mouseX, double mouseY, int x, int y, int w, int h) {
        return mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h;
    }

    @Override
    public void onClose() {
        minecraft.setScreen(parent);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
