package com.github.newvisualkeybing.client.screen;

import com.github.newvisualkeybing.client.keyboard.KeyBindingScanner;
import com.github.newvisualkeybing.client.keyboard.KeybindComboStore;
import com.github.newvisualkeybing.client.keyboard.KeybindPriorityEnforcer;
import com.github.newvisualkeybing.client.keyboard.KeybindProfileStore;
import com.github.newvisualkeybing.client.keyboard.KeybindViewerConfig;
import com.github.newvisualkeybing.client.keyboard.KeyboardLayoutData;
import com.github.newvisualkeybing.client.keyboard.Pinyin;
import com.github.newvisualkeybing.client.ui.MCButton;
import com.github.newvisualkeybing.client.ui.MCEditBox;
import com.github.newvisualkeybing.client.ui.UITheme;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * A second visual keyboard screen, distinct from {@link KeybindViewerScreen}: it lists every
 * keybind <em>function</em> in a side palette and lets you bind one by dragging it onto a key. The
 * drop assigns the function as that key's single-key binding (clearing any chord on it).
 */
public class KeybindBindBoardScreen extends FixedScaleScreen {

    private static final int HEADER_H = 40;
    private static final int FOOTER_H = 22;
    private static final int PANEL_W = 224;
    private static final int PANEL_PAD = 10;
    private static final int SEARCH_H = 20;
    private static final int ROW_H = 22;
    private static final int CAT_H = 18;
    private static final int BODY_PAD = 12;
    private static final int COL_GAP = 10;
    private static final int PANEL_TOP = HEADER_H + 4;
    private static final int PANEL_CONTENT_TOP = 28;
    private static final int SEARCH_Y = PANEL_TOP + PANEL_CONTENT_TOP + 2;
    private static final int FILTER_H = 16;
    private static final int FILTER_Y = SEARCH_Y + SEARCH_H + 4;
    private static final int MOD_ROW_H = 16;
    private static final float FIXED_KEY_UNIT = 30.0f;

    private final Screen parent;
    private final KeyBindingScanner scanner = new KeyBindingScanner();
    private final KeybindKeyboardRenderer keyboardRenderer = new KeybindKeyboardRenderer(scanner);
    private final KeybindTooltipRenderer tooltipRenderer = new KeybindTooltipRenderer(scanner);
    private final KeybindProfileStore profileStore = KeybindProfileStore.global();

    private KeyboardLayoutData.Style currentStyle = KeybindViewerConfig.global().defaultLayoutStyle();
    private MCEditBox searchBox;
    private MCButton backButton;
    private MCButton layoutButton;
    private MCButton scopeButton;

    // Annotation map: when true every bound key gets a leader-line callout; when false only the
    // bindings of the mod-filter selection are labelled (others render as faint "hidden" keys).
    private boolean annotateAllMods = true;

    private final List<Object> entries = new ArrayList<>();
    private int listScroll;
    private int totalListH;

    private String selectedModId;
    private boolean modDropdownOpen;
    private int modDropdownScroll;

    private float keyScale;
    private int keyboardX;
    private int keyboardY;
    private int keyboardW;
    private int keyboardH;
    private int boardX;
    private int boardTop;
    private int boardW;
    private int boardH;
    private boolean calloutsEnabled;
    private Integer hoveredCalloutKey;

    private float animTick;

    // Drag-bind / drag-unbind: a drag begins only once the cursor moves past a small threshold,
    // so a press-then-release in place is treated as a click (used for click-to-select binding).
    private static final double DRAG_THRESHOLD_SQ = 16.0;
    private KeyMapping dragging;
    private String dragLabel;
    private int dragMouseX;
    private int dragMouseY;
    private Integer dropTargetKey;
    private boolean overUnbindZone;

    // Pending press, armed on mouse-down, promoted to a drag on movement or to a click on release.
    private boolean pressArmed;
    private KeyMapping pressMapping;
    private Integer pressFromKey;
    private double pressX;
    private double pressY;

    // Click-to-bind: a function selected in the palette, waiting for a key click to land on.
    private KeyMapping selectedMapping;
    private String selectedLabel;

    private String noticeMessage;
    private long noticeUntil;

    public KeybindBindBoardScreen(Screen parent) {
        super(Component.translatable("screen.newvisualkeybing.board.title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        super.init();
        applyFixedScaleMetrics();
        UITheme.setMode(UITheme.Mode.DARK);
        scanner.scan();

        int searchX = BODY_PAD + PANEL_PAD;
        int searchW = PANEL_W - PANEL_PAD * 2;
        searchBox = new MCEditBox(font, searchX, SEARCH_Y, searchW, SEARCH_H,
                Component.translatable("screen.newvisualkeybing.viewer.search"))
                .withPlaceholder(Component.translatable("screen.newvisualkeybing.viewer.search"))
                .withClearAffordance(true);
        searchBox.setResponder(value -> rebuildEntries());
        addRenderableWidget(searchBox);

        int btnH = 20;
        int btnY = (HEADER_H - btnH) / 2;
        int backW = 56;
        int layoutW = 92;
        int scopeW = 104;
        int xBack = width - 8 - backW;
        int xLayout = xBack - 6 - layoutW;
        int xScope = xLayout - 6 - scopeW;
        backButton = MCButton.create(xBack, btnY, backW, btnH,
                Component.translatable("gui.done"), b -> onClose());
        addRenderableWidget(backButton);
        layoutButton = MCButton.create(xLayout, btnY, layoutW, btnH,
                Component.literal(currentStyle.label()),
                b -> {
                    currentStyle = currentStyle.next();
                    b.setMessage(Component.literal(currentStyle.label()));
                });
        addRenderableWidget(layoutButton);
        scopeButton = MCButton.create(xScope, btnY, scopeW, btnH,
                annotateLabel(),
                b -> {
                    annotateAllMods = !annotateAllMods;
                    b.setMessage(annotateLabel());
                });
        addRenderableWidget(scopeButton);

        rebuildEntries();
    }

    private void rebuildEntries() {
        entries.clear();
        String q = searchBox == null ? "" : searchBox.getValue().toLowerCase(Locale.ROOT).trim();
        Map<String, List<KeyMapping>> grouped = new LinkedHashMap<>();
        for (KeyMapping km : profileStore.sortedMappings(Minecraft.getInstance().options.keyMappings)) {
            if (selectedModId != null && !selectedModId.equals(KeyBindingScanner.modIdOf(km))) continue;
            String cat = Component.translatable(km.getCategory()).getString();
            String name = Component.translatable(km.getName()).getString();
            if (!q.isEmpty()
                    && !name.toLowerCase(Locale.ROOT).contains(q)
                    && !cat.toLowerCase(Locale.ROOT).contains(q)
                    && !Pinyin.matches(name, q)
                    && !Pinyin.matches(cat, q)) {
                continue;
            }
            grouped.computeIfAbsent(cat, k -> new ArrayList<>()).add(km);
        }
        int h = 0;
        for (Map.Entry<String, List<KeyMapping>> e : grouped.entrySet()) {
            entries.add(new CategoryEntry(e.getKey()));
            h += CAT_H;
            for (KeyMapping km : e.getValue()) {
                entries.add(new FuncEntry(km));
                h += ROW_H;
            }
        }
        totalListH = h;
        listScroll = Mth.clamp(listScroll, 0, Math.max(0, totalListH - listHeight()));
    }

    private int listTop() { return FILTER_Y + FILTER_H + 6; }
    private int listHeight() { return height - listTop() - FOOTER_H - 8; }

    private static final int SUMMARY_H = 18;
    private static final int CALLOUT_GAP = 7;
    private static final int CALLOUT_LANE_GAP = 3;
    private static final int MIN_CALLOUT_GUTTER = 34;
    private static final int MIN_CALLOUT_BAND = 18;

    private void layoutBoard() {
        boardX = BODY_PAD + PANEL_W + COL_GAP;
        boardTop = HEADER_H + 8;
        boardW = width - boardX - BODY_PAD;
        boardH = height - boardTop - FOOTER_H - 8;

        float widthU = currentStyle.widthU();
        float heightU = currentStyle.heightU();
        float gapW = (widthU - 1) * KeyboardLayoutData.BASE_GAP;
        float gapH = (heightU - 1) * KeyboardLayoutData.BASE_GAP;
        float ws = (boardW - gapW) / widthU;
        float hs = (boardH - gapH) / heightU;
        keyScale = Math.max(1.0f, Math.min(FIXED_KEY_UNIT, Math.min(ws, hs)));

        keyboardW = KeyboardLayoutData.totalWidthPx(currentStyle, keyScale);
        keyboardH = KeyboardLayoutData.totalHeightPx(currentStyle, keyScale);
        keyboardX = boardX + Math.max(0, (boardW - keyboardW) / 2);
        keyboardY = boardTop + Math.max(0, (boardH - keyboardH) / 2);
        calloutsEnabled = keyboardX - boardX >= MIN_CALLOUT_GUTTER
                || boardX + boardW - (keyboardX + keyboardW) >= MIN_CALLOUT_GUTTER
                || horizontalBandAvailable(true)
                || horizontalBandAvailable(false);
    }

    @Override
    public void renderBackground(GuiGraphics graphics) {
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        applyFixedScaleMetrics();
        animTick += partialTick;
        int mx = fixedMouseX(mouseX);
        int my = fixedMouseY(mouseY);
        dragMouseX = mx;
        dragMouseY = my;
        layoutBoard();
        overUnbindZone = dragging != null && insideUnbindZone(mx, my);
        hoveredCalloutKey = null;
        // While dragging or while a function is selected, the key under the cursor is the live target;
        // the unbind zone overrides any key it overlaps so a drop there always means "remove".
        dropTargetKey = (dragging != null || selectedMapping != null) && !overUnbindZone
                ? keyAt(mx, my) : null;

        pushFixedScale(graphics);
        try {
            var c = UITheme.colors();
            graphics.fill(0, 0, width, height, c.panelBg() | 0xFF000000);
            renderHeader(graphics);
            renderPalette(graphics, mx, my);

            long nowMs = System.currentTimeMillis();
            // The keyboard is always drawn solid (never ghosted): every key renders fully, with
            // status colour distinguishing bound keys from free ones. Callouts carry the labels.
            keyboardRenderer.render(graphics, font, currentStyle, keyboardX, keyboardY, keyScale,
                    dropTargetKey, k -> true, k -> false, k -> false, mx, my, animTick, nowMs);

            renderCallouts(graphics, mx, my);
            renderLegend(graphics);

            if (dragging != null) renderUnbindZone(graphics, mx, my);
            renderFooter(graphics);
            super.render(graphics, mx, my, partialTick);
            if (dragging == null && selectedMapping == null) renderHoverDetails(graphics, mx, my);
            if (dragging != null) renderDragChip(graphics);
            renderNotice(graphics);
        } finally {
            popFixedScale(graphics);
        }
    }

    private void renderHeader(GuiGraphics g) {
        var c = UITheme.colors();
        g.fill(0, 0, width, HEADER_H, c.headerBg());
        g.fill(0, HEADER_H - 1, width, HEADER_H, c.divider());
        g.fill(0, HEADER_H, width, HEADER_H + 1, UITheme.withAlpha(c.accent(), 0x70));
        g.drawString(font, title.getString(), 12, (HEADER_H - font.lineHeight) / 2, c.textPrimary(), true);
    }

    private void renderPalette(GuiGraphics g, int mouseX, int mouseY) {
        var c = UITheme.colors();
        int x = BODY_PAD;
        int top = HEADER_H + 4;
        int h = height - top - FOOTER_H - 4;
        String title = Component.translatable("screen.newvisualkeybing.board.functions").getString();
        KeybindViewerScreen.paintPanelBase(g, font, x, top, PANEL_W, h, title);

        int listTop = listTop();
        int listH = listHeight();
        int innerX = x + PANEL_PAD;
        int innerW = PANEL_W - PANEL_PAD * 2;

        renderFilterBar(g, innerX, innerW, mouseX, mouseY);

        enableFixedScissor(g, innerX, listTop, innerX + innerW, listTop + listH);
        int drawY = listTop - listScroll;
        for (Object entry : entries) {
            int eh = entry instanceof CategoryEntry ? CAT_H : ROW_H;
            if (drawY + eh >= listTop && drawY <= listTop + listH) {
                if (entry instanceof CategoryEntry ce) {
                    g.drawString(font, KeybindViewerScreen.fitToWidth(font, ce.name, innerW - 4),
                            innerX + 2, drawY + (CAT_H - font.lineHeight) / 2, c.accentLight(), false);
                } else if (entry instanceof FuncEntry fe) {
                    boolean hovered = dragging == null
                            && KeybindViewerScreen.inside(mouseX, mouseY, innerX, drawY, innerW, ROW_H - 2);
                    boolean selected = selectedMapping == fe.mapping;
                    renderFuncRow(g, fe, innerX, drawY, innerW, hovered, selected);
                }
            }
            drawY += eh;
        }
        g.disableScissor();

        if (totalListH > listH) {
            int sbH = Math.max(20, (int) ((float) listH / totalListH * listH));
            int sbY = listTop + (int) ((float) listScroll / totalListH * listH);
            UITheme.fillRoundedRectFast(g, x + PANEL_W - 6, listTop, 4, listH, 2, c.scrollbarTrack());
            UITheme.fillRoundedRectFast(g, x + PANEL_W - 6, sbY, 4, sbH, 2, c.scrollbarThumb());
        }

        if (modDropdownOpen) renderModDropdown(g, innerX, innerW, mouseX, mouseY);
    }

    private void renderFilterBar(GuiGraphics g, int x, int w, int mouseX, int mouseY) {
        var c = UITheme.colors();
        boolean active = selectedModId != null;
        boolean hovered = KeybindViewerScreen.inside(mouseX, mouseY, x, FILTER_Y, w, FILTER_H);
        int fill = UITheme.lerpColor(c.widgetBg(), active ? c.accent() : c.accentAlt(), hovered ? 0.40f : 0.18f);
        UITheme.fillRoundedRectFast(g, x, FILTER_Y, w, FILTER_H, 4, fill);
        UITheme.drawRoundedBorderFast(g, x, FILTER_Y, w, FILTER_H, 4,
                UITheme.withAlpha(active ? c.accent() : c.widgetBorder(), 0xB0));
        String label = Component.translatable("screen.newvisualkeybing.board.filter",
                selectedModName()).getString();
        g.drawString(font, KeybindViewerScreen.fitToWidth(font, label, w - 16),
                x + 5, FILTER_Y + (FILTER_H - font.lineHeight) / 2, c.textPrimary(), false);
        // caret
        int cx = x + w - 9;
        int cy = FILTER_Y + FILTER_H / 2 - (modDropdownOpen ? 1 : 2);
        for (int i = 0; i < 3; i++) {
            int yy = modDropdownOpen ? cy + i : cy + (2 - i);
            g.fill(cx + i, yy, cx + i + 1, yy + 1, c.textSecondary());
            g.fill(cx + 4 - i, yy, cx + 5 - i, yy + 1, c.textSecondary());
        }
    }

    private void renderModDropdown(GuiGraphics g, int x, int w, int mouseX, int mouseY) {
        var c = UITheme.colors();
        int y = FILTER_Y + FILTER_H + 2;
        int h = height - y - FOOTER_H - 8;
        UITheme.fillRoundedRectFast(g, x, y, w, h, 5, UITheme.withAlpha(c.headerBg(), 0xF4));
        UITheme.drawRoundedBorderFast(g, x, y, w, h, 5, c.accent());

        List<String> ids = modIds();
        int visible = Math.max(1, (h - 6) / MOD_ROW_H);
        modDropdownScroll = Mth.clamp(modDropdownScroll, 0, Math.max(0, ids.size() - visible));
        enableFixedScissor(g, x + 1, y + 1, x + w - 1, y + h - 1);
        int rowY = y + 3;
        for (int i = modDropdownScroll; i < ids.size() && i < modDropdownScroll + visible; i++) {
            String id = ids.get(i);
            boolean sel = id == null ? selectedModId == null : id.equals(selectedModId);
            boolean hovered = KeybindViewerScreen.inside(mouseX, mouseY, x + 3, rowY, w - 6, MOD_ROW_H - 1);
            if (sel || hovered) {
                UITheme.fillRoundedRectFast(g, x + 3, rowY, w - 6, MOD_ROW_H - 1, 4,
                        UITheme.lerpColor(c.widgetBg(), c.accent(), sel ? 0.45f : 0.22f));
            }
            String name = id == null
                    ? Component.translatable("screen.newvisualkeybing.board.filter_all").getString()
                    : scanner.getAllRegisteredMods().getOrDefault(id, id);
            g.drawString(font, KeybindViewerScreen.fitToWidth(font, name, w - 14),
                    x + 7, rowY + (MOD_ROW_H - 1 - font.lineHeight) / 2,
                    sel ? c.textPrimary() : c.textSecondary(), false);
            rowY += MOD_ROW_H;
        }
        g.disableScissor();
    }

    private String selectedModName() {
        if (selectedModId == null) {
            return Component.translatable("screen.newvisualkeybing.board.filter_all").getString();
        }
        return scanner.getAllRegisteredMods().getOrDefault(selectedModId, selectedModId);
    }

    private List<String> modIds() {
        List<String> ids = new ArrayList<>();
        ids.add(null); // "All"
        ids.addAll(scanner.getAllRegisteredMods().keySet());
        return ids;
    }

    private void renderFuncRow(GuiGraphics g, FuncEntry fe, int x, int y, int w, boolean hovered, boolean selected) {
        var c = UITheme.colors();
        int rowH = ROW_H - 2;
        int fill = selected ? UITheme.lerpColor(c.widgetBg(), c.accent(), 0.50f)
                : hovered ? UITheme.lerpColor(c.widgetBg(), c.accent(), 0.30f)
                : UITheme.withAlpha(c.widgetBg(), 0x88);
        UITheme.fillRoundedRectFast(g, x, y, w, rowH, 5, fill);
        UITheme.drawRoundedBorderFast(g, x, y, w, rowH, 5,
                selected ? c.accent() : UITheme.withAlpha(c.widgetBorder(), 0x80));
        // grip dots to signal draggability
        int gx = x + 5;
        int gy = y + rowH / 2;
        for (int i = -1; i <= 1; i++) {
            g.fill(gx, gy + i * 3 - 1, gx + 2, gy + i * 3 + 1, UITheme.withAlpha(c.textMuted(), 0xC0));
            g.fill(gx + 3, gy + i * 3 - 1, gx + 5, gy + i * 3 + 1, UITheme.withAlpha(c.textMuted(), 0xC0));
        }
        String key = fe.mapping.getTranslatedKeyMessage().getString();
        int keyW = Math.min(font.width(key) + 8, w / 2);
        String name = KeybindViewerScreen.fitToWidth(font, Component.translatable(fe.mapping.getName()).getString(),
                w - 14 - keyW - 6);
        int textY = y + (rowH - font.lineHeight) / 2;
        g.drawString(font, name, x + 14, textY, c.textPrimary(), false);
        boolean unbound = fe.mapping.isUnbound();
        String keyFit = KeybindViewerScreen.fitToWidth(font, key, keyW);
        g.drawString(font, keyFit, x + w - font.width(keyFit) - 4, textY,
                unbound ? c.textMuted() : c.accentLight(), false);
    }

    private void renderFooter(GuiGraphics g) {
        var c = UITheme.colors();
        int y = height - FOOTER_H;
        g.fill(0, y, width, y + 1, c.divider());
        String hint;
        if (dragging != null) {
            hint = Component.translatable(
                    overUnbindZone ? "screen.newvisualkeybing.board.unbind_hint"
                            : "screen.newvisualkeybing.board.drop_hint",
                    Component.translatable(dragging.getName()).getString()).getString();
        } else if (selectedMapping != null) {
            hint = Component.translatable("screen.newvisualkeybing.board.select_hint", selectedLabel).getString();
        } else {
            hint = Component.translatable("screen.newvisualkeybing.board.hint").getString();
        }
        g.drawString(font, KeybindViewerScreen.fitToWidth(font, hint, width - 24),
                12, y + (FOOTER_H - font.lineHeight) / 2, c.textSecondary(), false);
    }

    private void renderDragChip(GuiGraphics g) {
        var c = UITheme.colors();
        int pad = 6;
        int w = font.width(dragLabel) + pad * 2;
        int h = font.lineHeight + pad;
        int x = dragMouseX + 8;
        int y = dragMouseY + 8;
        x = Math.min(x, width - w - 2);
        y = Math.min(y, height - h - 2);
        UITheme.fillSoftRoundedRect(g, x, y, w, h, 5, UITheme.withAlpha(c.headerBg(), 0xF0));
        int border = overUnbindZone ? c.danger() : dropTargetKey != null ? c.success() : c.accent();
        UITheme.drawSoftRoundedBorder(g, x, y, w, h, 5, border);
        g.drawString(font, dragLabel, x + pad, y + (h - font.lineHeight) / 2, c.textPrimary(), false);
    }

    /** Drop target shown while dragging — releasing a function here clears its binding. */
    private void renderUnbindZone(GuiGraphics g, int mouseX, int mouseY) {
        var c = UITheme.colors();
        int[] r = unbindZoneRect();
        boolean over = KeybindViewerScreen.inside(mouseX, mouseY, r[0], r[1], r[2], r[3]);
        int fill = UITheme.withAlpha(over ? c.danger() : c.headerBg(), over ? 0xC8 : 0xE6);
        UITheme.fillRoundedRectFast(g, r[0], r[1], r[2], r[3], 6, fill);
        UITheme.drawRoundedBorderFast(g, r[0], r[1], r[2], r[3], 6,
                over ? c.danger() : UITheme.withAlpha(c.danger(), 0x90));
        String label = Component.translatable("screen.newvisualkeybing.board.unbind_zone").getString();
        g.drawString(font, KeybindViewerScreen.fitToWidth(font, label, r[2] - 10),
                r[0] + (r[2] - font.width(label)) / 2, r[1] + (r[3] - font.lineHeight) / 2,
                over ? c.textPrimary() : c.textSecondary(), false);
    }

    /** {x, y, w, h} of the unbind drop zone, centered along the bottom of the keyboard column. */
    private int[] unbindZoneRect() {
        int w = Math.min(220, Math.max(120, boardW - 20));
        int h = 28;
        int x = boardX + (boardW - w) / 2;
        int y = height - FOOTER_H - h - 6;
        return new int[]{x, y, w, h};
    }

    private boolean insideUnbindZone(double mouseX, double mouseY) {
        int[] r = unbindZoneRect();
        return KeybindViewerScreen.inside(mouseX, mouseY, r[0], r[1], r[2], r[3]);
    }

    private void renderNotice(GuiGraphics g) {
        if (noticeMessage == null) return;
        if (System.currentTimeMillis() > noticeUntil) { noticeMessage = null; return; }
        var c = UITheme.colors();
        int w = font.width(noticeMessage) + 24;
        int x = (width - w) / 2;
        int y = height - FOOTER_H - 26;
        UITheme.fillRoundedRectFast(g, x, y, w, 20, 6, UITheme.withAlpha(c.headerBg(), 0xE0));
        UITheme.drawRoundedBorderFast(g, x, y, w, 20, 6, c.accent());
        g.drawString(font, noticeMessage, x + 12, y + 6, c.textPrimary(), false);
    }

    private void showNotice(String msg) {
        noticeMessage = msg;
        noticeUntil = System.currentTimeMillis() + 2200;
    }

    private Component annotateLabel() {
        return Component.translatable(annotateAllMods
                ? "screen.newvisualkeybing.board.annotate.all"
                : "screen.newvisualkeybing.board.annotate.filter");
    }

    /** The binding that should be labelled on a key under the current scope, or null. */
    private KeyBindingScanner.KeyBindingInfo shownBinding(int glfwKey) {
        List<KeyBindingScanner.KeyBindingInfo> binds = scanner.getBindings(glfwKey);
        if (binds.isEmpty()) return null;
        if (annotateAllMods || selectedModId == null) return binds.get(0);
        for (KeyBindingScanner.KeyBindingInfo info : binds) {
            if (selectedModId.equals(info.modId())) return info;
        }
        return null;
    }

    private int shownCount(int glfwKey) {
        List<KeyBindingScanner.KeyBindingInfo> binds = scanner.getBindings(glfwKey);
        if (annotateAllMods || selectedModId == null) return binds.size();
        int n = 0;
        for (KeyBindingScanner.KeyBindingInfo info : binds) {
            if (selectedModId.equals(info.modId())) n++;
        }
        return n;
    }

    private boolean isAnnotated(int glfwKey) {
        return shownBinding(glfwKey) != null;
    }

    private int statusColor(KeyBindingScanner.KeyStatus status) {
        var c = UITheme.colors();
        return switch (status) {
            case FREE -> c.widgetBorder();
            case SELF -> c.accent();
            case OTHER_SINGLE, BOUND -> c.success();
            case COMBO -> c.warning();
            case CONFLICT -> c.danger();
        };
    }

    /** Leader-line callouts arranged in a ring around the keyboard. */
    private void renderCallouts(GuiGraphics g, int mouseX, int mouseY) {
        if (!calloutsEnabled) return;
        List<CalloutItem> top = new ArrayList<>();
        List<CalloutItem> bottom = new ArrayList<>();
        List<CalloutItem> left = new ArrayList<>();
        List<CalloutItem> right = new ArrayList<>();
        int hidden = 0;
        int conflicts = 0;
        for (KeyboardLayoutData.KeyDef key : KeyboardLayoutData.getKeys(currentStyle)) {
            int glfw = key.glfwKey();
            if (scanner.getBindings(glfw).isEmpty()) continue;
            if (!isAnnotated(glfw)) {
                hidden++;
                continue;
            }
            int kx = key.screenX(keyboardX, keyScale);
            int ky = key.screenY(keyboardY, keyScale);
            int kw = key.screenW(keyScale);
            int kh = key.screenH(keyScale);
            int cx = kx + kw / 2;
            int cy = ky + kh / 2;
            CalloutItem item = new CalloutItem(glfw, kx, ky, kw, kh, cx, cy);
            if (scanner.getStatus(glfw) == KeyBindingScanner.KeyStatus.CONFLICT) conflicts++;
            switch (calloutSide(key, cx, cy)) {
                case TOP -> top.add(item);
                case BOTTOM -> bottom.add(item);
                case LEFT -> left.add(item);
                case RIGHT -> right.add(item);
            }
        }
        int total = top.size() + bottom.size() + left.size() + right.size();
        renderAnnotationSummary(g, total, conflicts, hidden);
        if (total == 0) return;

        int chipH = font.lineHeight + 4;
        placeHorizontalBand(g, top, true, chipH, mouseX, mouseY);
        placeHorizontalBand(g, bottom, false, chipH, mouseX, mouseY);
        placeSideColumn(g, left, true, chipH, mouseX, mouseY);
        placeSideColumn(g, right, false, chipH, mouseX, mouseY);
    }

    private CalloutSide calloutSide(KeyboardLayoutData.KeyDef key, int cx, int cy) {
        if (key.gridY() <= 1.15f && horizontalBandAvailable(true)) return CalloutSide.TOP;
        if (key.gridY() + key.height() >= currentStyle.heightU() - 0.05f
                && horizontalBandAvailable(false)) return CalloutSide.BOTTOM;
        return nearestSide(cx, cy);
    }

    private CalloutSide nearestSide(int cx, int cy) {
        int leftSpace = keyboardX - boardX;
        int rightSpace = boardX + boardW - (keyboardX + keyboardW);
        int topSpace = keyboardY - boardTop;
        int bottomSpace = boardTop + boardH - (keyboardY + keyboardH);
        int best = leftSpace;
        CalloutSide side = CalloutSide.LEFT;
        if (rightSpace > best) { best = rightSpace; side = CalloutSide.RIGHT; }
        if (horizontalBandAvailable(true) && topSpace > best && cy < keyboardY + keyboardH / 2) {
            best = topSpace;
            side = CalloutSide.TOP;
        }
        if (horizontalBandAvailable(false) && bottomSpace > best && cy >= keyboardY + keyboardH / 2) {
            side = CalloutSide.BOTTOM;
        }
        return side;
    }

    private boolean horizontalBandAvailable(boolean topSide) {
        int chipH = font.lineHeight + 4;
        int bandTop = topSide ? boardTop + SUMMARY_H + 5 : keyboardY + keyboardH + CALLOUT_GAP;
        int bandBottom = topSide ? keyboardY - CALLOUT_GAP : boardTop + boardH - 4;
        return bandBottom - bandTop >= chipH;
    }

    private void renderAnnotationSummary(GuiGraphics g, int total, int conflicts, int hidden) {
        var c = UITheme.colors();
        int x = boardX + 2;
        int y = boardTop + 2;
        int w = boardW - 4;
        UITheme.fillRoundedRectFast(g, x, y, w, SUMMARY_H, 5, UITheme.withAlpha(c.headerBg(), 0xC8));
        UITheme.drawRoundedBorderFast(g, x, y, w, SUMMARY_H, 5, UITheme.withAlpha(c.widgetBorder(), 0x80));

        String scope = annotateAllMods || selectedModId == null
                ? Component.translatable("screen.newvisualkeybing.board.filter_all").getString()
                : selectedModName();
        String summary = hidden > 0
                ? Component.translatable("screen.newvisualkeybing.board.summary_hidden",
                        scope, total, conflicts, hidden).getString()
                : Component.translatable("screen.newvisualkeybing.board.summary",
                        scope, total, conflicts).getString();
        int maxTextW = Math.max(80, w - 154);
        g.drawString(font, KeybindViewerScreen.fitToWidth(font, summary, maxTextW),
                x + 7, y + (SUMMARY_H - font.lineHeight) / 2, c.textSecondary(), false);
    }

    private void placeSideColumn(GuiGraphics g, List<CalloutItem> items, boolean leftSide,
                                 int chipH, int mouseX, int mouseY) {
        int n = items.size();
        if (n == 0) return;
        int colLeft = leftSide ? boardX + 2 : keyboardX + keyboardW + CALLOUT_GAP;
        int colRight = leftSide ? keyboardX - CALLOUT_GAP : boardX + boardW - 2;
        int colW = colRight - colLeft;
        if (colW < MIN_CALLOUT_GUTTER) return;

        items.sort((a, b) -> Integer.compare(a.cy(), b.cy()));
        int spanTop = keyboardY;
        int bottomLimit = keyboardY + keyboardH;
        int span = Math.max(chipH, bottomLimit - spanTop);
        int slot = Math.max(font.lineHeight + 1, Math.min(chipH + 3, span / n));
        int[] yTop = resolvedVerticalSlots(items, spanTop, bottomLimit, chipH, slot);
        int railX = leftSide ? keyboardX - 4 : keyboardX + keyboardW + 4;

        for (int i = 0; i < n; i++) {
            CalloutItem item = items.get(i);
            int labelTop = yTop[i];
            int chipW = preferredChipWidth(item, colW);
            int chipX = leftSide ? colRight - chipW : colLeft;
            boolean hover = calloutHovered(item, chipX, labelTop, chipW, chipH, mouseX, mouseY);
            int accent = statusColor(scanner.getStatus(item.glfw()));
            int lineColor = UITheme.withAlpha(accent, hover ? 0xFF : 0xA8);
            int labelAnchorX = leftSide ? chipX + chipW : chipX;
            int keyAnchorX = leftSide ? item.kx() : item.kx() + item.kw();
            int labelYc = labelTop + chipH / 2;
            fillSeg(g, keyAnchorX, railX, item.cy(), lineColor);
            fillVSeg(g, railX, item.cy(), labelYc, lineColor);
            fillSeg(g, railX, labelAnchorX, labelYc, lineColor);
            g.fill(keyAnchorX - 1, item.cy() - 1, keyAnchorX + 1, item.cy() + 1, lineColor);
            renderCalloutChip(g, item, chipX, labelTop, chipW, chipH,
                    leftSide ? CalloutSide.LEFT : CalloutSide.RIGHT, hover, accent);
        }
    }

    private void placeHorizontalBand(GuiGraphics g, List<CalloutItem> items, boolean topSide,
                                     int chipH, int mouseX, int mouseY) {
        int n = items.size();
        if (n == 0) return;
        items.sort((a, b) -> Integer.compare(a.cx(), b.cx()));
        int bandTop = topSide ? boardTop + SUMMARY_H + 5 : keyboardY + keyboardH + CALLOUT_GAP;
        int bandBottom = topSide ? keyboardY - CALLOUT_GAP : boardTop + boardH - 4;
        int bandH = bandBottom - bandTop;
        if (bandH < chipH) return;

        int lanes = Mth.clamp((bandH + CALLOUT_LANE_GAP) / (chipH + CALLOUT_LANE_GAP), 1, 4);
        int laneMax = Math.max(1, (n + lanes - 1) / lanes);
        int maxChipW = Mth.clamp((keyboardW - Math.max(0, laneMax - 1) * 4) / laneMax, 38, 116);
        List<List<CalloutItem>> laneItems = new ArrayList<>();
        for (int i = 0; i < lanes; i++) laneItems.add(new ArrayList<>());
        for (int i = 0; i < n; i++) laneItems.get(i % lanes).add(items.get(i));

        for (int lane = 0; lane < lanes; lane++) {
            List<CalloutItem> row = laneItems.get(lane);
            if (row.isEmpty()) continue;
            int y = topSide
                    ? bandBottom - chipH - lane * (chipH + CALLOUT_LANE_GAP)
                    : bandTop + lane * (chipH + CALLOUT_LANE_GAP);
            y = Mth.clamp(y, bandTop, bandBottom - chipH);
            int rowCount = row.size();
            for (int i = 0; i < rowCount; i++) {
                CalloutItem item = row.get(i);
                int slotLeft = keyboardX + Math.round(i * (keyboardW / (float) rowCount));
                int slotRight = keyboardX + Math.round((i + 1) * (keyboardW / (float) rowCount)) - 2;
                int chipW = preferredChipWidth(item, Math.min(maxChipW, Math.max(24, slotRight - slotLeft)));
                int chipX = Mth.clamp(item.cx() - chipW / 2, slotLeft, Math.max(slotLeft, slotRight - chipW));
                boolean hover = calloutHovered(item, chipX, y, chipW, chipH, mouseX, mouseY);
                int accent = statusColor(scanner.getStatus(item.glfw()));
                int lineColor = UITheme.withAlpha(accent, hover ? 0xFF : 0xA8);
                int railY = topSide ? keyboardY - 4 : keyboardY + keyboardH + 4;
                int keyAnchorY = topSide ? item.ky() : item.ky() + item.kh();
                int labelAnchorX = chipX + chipW / 2;
                int labelAnchorY = topSide ? y + chipH : y;
                fillVSeg(g, item.cx(), keyAnchorY, railY, lineColor);
                fillSeg(g, item.cx(), labelAnchorX, railY, lineColor);
                fillVSeg(g, labelAnchorX, railY, labelAnchorY, lineColor);
                g.fill(item.cx() - 1, keyAnchorY - 1, item.cx() + 1, keyAnchorY + 1, lineColor);
                renderCalloutChip(g, item, chipX, y, chipW, chipH,
                        topSide ? CalloutSide.TOP : CalloutSide.BOTTOM, hover, accent);
            }
        }
    }

    private int[] resolvedVerticalSlots(List<CalloutItem> items, int top, int bottom, int chipH, int slot) {
        int n = items.size();
        int[] yTop = new int[n];
        for (int i = 0; i < n; i++) {
            yTop[i] = Mth.clamp(items.get(i).cy() - chipH / 2, top, bottom - chipH);
        }
        for (int i = 1; i < n; i++) {
            if (yTop[i] < yTop[i - 1] + slot) yTop[i] = yTop[i - 1] + slot;
        }
        if (yTop[n - 1] + chipH > bottom) {
            yTop[n - 1] = bottom - chipH;
            for (int i = n - 2; i >= 0; i--) {
                if (yTop[i] + slot > yTop[i + 1]) yTop[i] = yTop[i + 1] - slot;
            }
        }
        for (int i = 0; i < n; i++) yTop[i] = Mth.clamp(yTop[i], top, bottom - chipH);
        return yTop;
    }

    private int preferredChipWidth(CalloutItem item, int maxW) {
        int safeMax = Math.max(24, maxW);
        String fitted = KeybindViewerScreen.fitToWidth(font, calloutText(item.glfw()), safeMax - 12);
        return Math.min(safeMax, Math.max(24, font.width(fitted) + 14));
    }

    private String calloutText(int glfw) {
        KeyBindingScanner.KeyBindingInfo info = shownBinding(glfw);
        if (info == null) return "";
        int count = shownCount(glfw);
        return count > 1 ? info.actionName() + "  +" + (count - 1) : info.actionName();
    }

    private boolean calloutHovered(CalloutItem item, int x, int y, int w, int h, int mouseX, int mouseY) {
        boolean hover = KeybindViewerScreen.inside(mouseX, mouseY, x, y, w, h)
                || KeybindViewerScreen.inside(mouseX, mouseY, item.kx(), item.ky(), item.kw(), item.kh());
        if (hover) hoveredCalloutKey = item.glfw();
        return hover;
    }

    private void renderCalloutChip(GuiGraphics g, CalloutItem item, int x, int y, int w, int h,
                                   CalloutSide side, boolean hover, int accent) {
        var c = UITheme.colors();
        int bg = UITheme.withAlpha(hover ? UITheme.lerpColor(c.headerBg(), accent, 0.30f) : c.headerBg(), 0xF0);
        UITheme.fillRoundedRectFast(g, x, y, w, h, 3, bg);
        UITheme.drawRoundedBorderFast(g, x, y, w, h, 3, UITheme.withAlpha(accent, hover ? 0xE0 : 0x96));
        switch (side) {
            case LEFT -> g.fill(x + w - 2, y + 1, x + w, y + h - 1, accent);
            case RIGHT -> g.fill(x, y + 1, x + 2, y + h - 1, accent);
            case TOP -> g.fill(x + 1, y + h - 2, x + w - 1, y + h, accent);
            case BOTTOM -> g.fill(x + 1, y, x + w - 1, y + 2, accent);
        }
        String fitted = KeybindViewerScreen.fitToWidth(font, calloutText(item.glfw()), w - 12);
        g.drawString(font, fitted, x + 5, y + (h - font.lineHeight) / 2 + 1, c.textPrimary(), false);
    }

    private static void fillSeg(GuiGraphics g, int x1, int x2, int y, int color) {
        g.fill(Math.min(x1, x2), y, Math.max(x1, x2) + 1, y + 1, color);
    }

    private static void fillVSeg(GuiGraphics g, int x, int y1, int y2, int color) {
        g.fill(x, Math.min(y1, y2), x + 1, Math.max(y1, y2) + 1, color);
    }

    private void renderHoverDetails(GuiGraphics g, int mouseX, int mouseY) {
        Integer key = hoveredCalloutKey != null ? hoveredCalloutKey : keyAt(mouseX, mouseY);
        if (key != null && isAnnotated(key)) {
            tooltipRenderer.render(g, font, width, height, key, mouseX, mouseY);
        }
    }

    private void renderLegend(GuiGraphics g) {
        var c = UITheme.colors();
        int y = boardTop + 5;
        String unused = Component.translatable("screen.newvisualkeybing.board.legend.unused").getString();
        int w = 34 + font.width(Component.translatable("screen.newvisualkeybing.board.legend.assigned").getString())
                + font.width(unused);
        int x = Math.max(boardX + 2, boardX + boardW - w - 12);
        x = legendItem(g, x, y, c.accent(), "screen.newvisualkeybing.board.legend.assigned");
        legendItem(g, x + 10, y, c.widgetBorder(), "screen.newvisualkeybing.board.legend.unused");
    }

    private int legendItem(GuiGraphics g, int x, int y, int swatch, String key) {
        var c = UITheme.colors();
        UITheme.fillRoundedRectFast(g, x, y + 1, 8, 8, 2, swatch);
        UITheme.drawRoundedBorderFast(g, x, y + 1, 8, 8, 2, UITheme.withAlpha(c.widgetBorder(), 0xB0));
        String label = Component.translatable(key).getString();
        g.drawString(font, label, x + 12, y, c.textSecondary(), false);
        return x + 12 + font.width(label);
    }

    /** Keyboard key (glfw code) whose on-screen rect contains the point, or null. */
    private Integer keyAt(double mouseX, double mouseY) {
        for (KeyboardLayoutData.KeyDef key : KeyboardLayoutData.getKeys(currentStyle)) {
            int kx = key.screenX(keyboardX, keyScale);
            int ky = key.screenY(keyboardY, keyScale);
            int kw = key.screenW(keyScale);
            int kh = key.screenH(keyScale);
            if (KeybindViewerScreen.inside(mouseX, mouseY, kx, ky, kw, kh)) return key.glfwKey();
        }
        return null;
    }

    private FuncEntry funcRowAt(double mouseX, double mouseY) {
        int listTop = listTop();
        int listH = listHeight();
        int innerX = BODY_PAD + PANEL_PAD;
        int innerW = PANEL_W - PANEL_PAD * 2;
        if (!KeybindViewerScreen.inside(mouseX, mouseY, innerX, listTop, innerW, listH)) return null;
        int drawY = listTop - listScroll;
        for (Object entry : entries) {
            int eh = entry instanceof CategoryEntry ? CAT_H : ROW_H;
            if (entry instanceof FuncEntry fe
                    && KeybindViewerScreen.inside(mouseX, mouseY, innerX, drawY, innerW, ROW_H - 2)) {
                return fe;
            }
            drawY += eh;
        }
        return null;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        applyFixedScaleMetrics();
        double mx = fixedMouseX(mouseX);
        double my = fixedMouseY(mouseY);
        int innerX = BODY_PAD + PANEL_PAD;
        int innerW = PANEL_W - PANEL_PAD * 2;
        if (KeybindViewerScreen.inside(mx, my, innerX, FILTER_Y, innerW, FILTER_H)) {
            modDropdownOpen = !modDropdownOpen;
            modDropdownScroll = 0;
            return true;
        }
        if (modDropdownOpen) {
            // Selecting a row applies the filter; clicking anywhere else just closes the dropdown.
            handleModDropdownClick(mx, my, innerX, innerW);
            modDropdownOpen = false;
            return true;
        }
        if (handleSearchClearClick(mx, my)) return true;
        if (super.mouseClicked(mx, my, button)) return true;
        if (button == 0) {
            Integer key = keyAt(mx, my);
            // Click-to-bind: a function is armed in the palette and the user clicks a key for it.
            if (selectedMapping != null && key != null) {
                KeyMapping mapping = selectedMapping;
                clearSelection();
                bindToKey(mapping, key);
                return true;
            }
            // Press on a palette row: arm for either a click (select) or a drag (drag-bind).
            FuncEntry fe = funcRowAt(mx, my);
            if (fe != null) {
                beginPress(fe.mapping, null, mx, my);
                if (searchBox != null) searchBox.setFocused(false);
                setFocused(null);
                return true;
            }
            // Press on a bound key: arm a drag that can move it to another key or drop it to unbind.
            if (key != null) {
                KeyMapping onKey = primaryMappingOn(key);
                if (onKey != null) {
                    beginPress(onKey, key, mx, my);
                    return true;
                }
            }
            // Click on empty space cancels a pending palette selection.
            if (selectedMapping != null) {
                clearSelection();
                return true;
            }
        }
        return false;
    }

    private void beginPress(KeyMapping mapping, Integer fromKey, double mx, double my) {
        pressArmed = true;
        pressMapping = mapping;
        pressFromKey = fromKey;
        pressX = mx;
        pressY = my;
    }

    private void clearPress() {
        pressArmed = false;
        pressMapping = null;
        pressFromKey = null;
    }

    private void endDrag() {
        dragging = null;
        dragLabel = null;
        overUnbindZone = false;
        clearPress();
    }

    private void clearSelection() {
        selectedMapping = null;
        selectedLabel = null;
    }

    /** The top (highest-priority) mapping currently bound to a keyboard key, or null if none. */
    private KeyMapping primaryMappingOn(int glfwKey) {
        List<KeyBindingScanner.KeyBindingInfo> binds = scanner.getBindings(glfwKey);
        if (binds.isEmpty()) return null;
        return mappingByName(binds.get(0).translationKey());
    }

    private KeyMapping mappingByName(String name) {
        if (name == null) return null;
        for (KeyMapping km : Minecraft.getInstance().options.keyMappings) {
            if (km.getName().equals(name)) return km;
        }
        return null;
    }

    private boolean handleModDropdownClick(double mouseX, double mouseY, int x, int w) {
        int y = FILTER_Y + FILTER_H + 2;
        int h = height - y - FOOTER_H - 8;
        if (!KeybindViewerScreen.inside(mouseX, mouseY, x, y, w, h)) return false;
        List<String> ids = modIds();
        int visible = Math.max(1, (h - 6) / MOD_ROW_H);
        int rowY = y + 3;
        for (int i = modDropdownScroll; i < ids.size() && i < modDropdownScroll + visible; i++) {
            if (KeybindViewerScreen.inside(mouseX, mouseY, x + 3, rowY, w - 6, MOD_ROW_H - 1)) {
                selectedModId = ids.get(i);
                modDropdownOpen = false;
                listScroll = 0;
                rebuildEntries();
                return true;
            }
            rowY += MOD_ROW_H;
        }
        return true; // click inside the dropdown panel (e.g. gap) is consumed
    }

    private boolean handleSearchClearClick(double mouseX, double mouseY) {
        if (searchBox == null || searchBox.getValue().isEmpty()) return false;
        if (searchBox.clearAffordanceClicked(mouseX, mouseY)) {
            searchBox.setValue("");
            searchBox.setFocused(true);
            setFocused(searchBox);
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        applyFixedScaleMetrics();
        double mx = fixedMouseX(mouseX);
        double my = fixedMouseY(mouseY);
        if (button == 0 && pressArmed && dragging == null) {
            double ddx = mx - pressX;
            double ddy = my - pressY;
            if (ddx * ddx + ddy * ddy >= DRAG_THRESHOLD_SQ) {
                dragging = pressMapping;
                dragLabel = Component.translatable(pressMapping.getName()).getString();
                clearSelection();
            }
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        applyFixedScaleMetrics();
        double mx = fixedMouseX(mouseX);
        double my = fixedMouseY(mouseY);
        if (button == 0 && dragging != null) {
            KeyMapping mapping = dragging;
            boolean toUnbind = insideUnbindZone(mx, my);
            Integer target = keyAt(mx, my);
            endDrag();
            if (toUnbind) {
                if (!mapping.isUnbound()) unbind(mapping);
            } else if (target != null) {
                bindToKey(mapping, target);
            }
            return true;
        }
        if (button == 0 && pressArmed) {
            // A press that never crossed the drag threshold counts as a click. Only palette rows
            // toggle the click-to-bind selection; clicking a bound key in place does nothing (drag
            // it onto another key to move, or onto the unbind zone to clear).
            KeyMapping mapping = pressMapping;
            Integer fromKey = pressFromKey;
            clearPress();
            if (fromKey == null) {
                if (selectedMapping == mapping) {
                    clearSelection();
                } else {
                    selectedMapping = mapping;
                    selectedLabel = Component.translatable(mapping.getName()).getString();
                }
            }
            return true;
        }
        return releaseLogicalMouse(mx, my, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollY) {
        applyFixedScaleMetrics();
        double mx = fixedMouseX(mouseX);
        if (modDropdownOpen) {
            modDropdownScroll = Math.max(0, modDropdownScroll - (int) Math.signum(scrollY));
            return true;
        }
        if (mx < BODY_PAD + PANEL_W) {
            listScroll = Mth.clamp(listScroll - (int) (scrollY * ROW_H * 2), 0,
                    Math.max(0, totalListH - listHeight()));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollY);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        applyFixedScaleMetrics();
        if (keyCode == GLFW.GLFW_KEY_ESCAPE && modDropdownOpen) {
            modDropdownOpen = false;
            return true;
        }
        if (dragging != null && keyCode == GLFW.GLFW_KEY_ESCAPE) {
            endDrag();
            return true;
        }
        if (selectedMapping != null && keyCode == GLFW.GLFW_KEY_ESCAPE) {
            clearSelection();
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_ESCAPE && searchBox != null && searchBox.isFocused()) {
            if (!searchBox.getValue().isEmpty()) {
                searchBox.setValue("");
                return true;
            }
            searchBox.setFocused(false);
            setFocused(null);
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            onClose();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    private void bindToKey(KeyMapping mapping, int glfwKey) {
        if (mapping == null || glfwKey == GLFW.GLFW_KEY_UNKNOWN) return;
        InputConstants.Key key = InputConstants.Type.KEYSYM.getOrCreate(glfwKey);
        Minecraft mc = Minecraft.getInstance();
        mc.options.setKey(mapping, key);
        KeybindComboStore.global().removeCombo(mapping.getName());
        KeybindPriorityEnforcer.resetAndEnforce();
        mc.options.save();
        scanner.scan();
        showNotice(Component.translatable("screen.newvisualkeybing.board.bound",
                Component.translatable(mapping.getName()).getString(),
                key.getDisplayName().getString()).getString());
    }

    private void unbind(KeyMapping mapping) {
        if (mapping == null) return;
        Minecraft mc = Minecraft.getInstance();
        mc.options.setKey(mapping, InputConstants.UNKNOWN);
        KeybindComboStore.global().removeCombo(mapping.getName());
        KeybindPriorityEnforcer.resetAndEnforce();
        mc.options.save();
        scanner.scan();
        showNotice(Component.translatable("screen.newvisualkeybing.board.unbound",
                Component.translatable(mapping.getName()).getString()).getString());
    }

    @Override
    public void onClose() {
        minecraft.setScreen(parent);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private enum CalloutSide { TOP, RIGHT, BOTTOM, LEFT }
    private record CalloutItem(int glfw, int kx, int ky, int kw, int kh, int cx, int cy) {}
    private record CategoryEntry(String name) {}
    private record FuncEntry(KeyMapping mapping) {}
}
