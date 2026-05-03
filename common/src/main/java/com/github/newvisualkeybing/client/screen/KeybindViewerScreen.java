package com.github.newvisualkeybing.client.screen;

import com.github.newvisualkeybing.Constants;
import com.github.newvisualkeybing.client.keyboard.FilterTab;
import com.github.newvisualkeybing.client.keyboard.KeyBindingScanner;
import com.github.newvisualkeybing.client.keyboard.KeyboardLayoutData;
import com.github.newvisualkeybing.client.ui.MCButton;
import com.github.newvisualkeybing.client.ui.UITheme;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class KeybindViewerScreen extends Screen {

    private static final int PANEL_MARGIN = 18;
    private static final int MOD_PANEL_W = 172;
    private static final int DETAIL_PANEL_W = 220;
    private static final int MOUSE_PANEL_W = 154;
    private static final ResourceLocation MOUSE_TEXTURE = new ResourceLocation(Constants.MOD_ID, "textures/gui/mouse_outline.png");
    private static final int MOUSE_TEXTURE_W = 1024;
    private static final int MOUSE_TEXTURE_H = 1536;
    private static final int HEADER_H = 86;
    private static final int TAB_H = 22;
    private static final int SEARCH_H = 20;
    private static final int STATUS_H = 26;

    private final Screen parent;
    private final KeyBindingScanner scanner = new KeyBindingScanner();

    private EditBox searchBox;
    private MCButton themeButton;
    private MCButton closeButton;
    private MCButton manageButton;
    private MCButton modToggleButton;

    private FilterTab activeFilter = FilterTab.ALL;
    private Set<Integer> textFilteredKeys;
    private Set<Integer> tabFilteredKeys;
    private Set<Integer> modFilteredKeys;
    private Integer selectedVirtualKey;
    private Integer hoveredVirtualKey;
    private boolean modPanelOpen;
    private String selectedModId;
    private String modSearchQuery = "";
    private int modScrollOffset;

    private float keyScale;
    private int keyboardX;
    private int keyboardY;
    private int mousePanelX;
    private int mousePanelY;
    private int detailPanelX;
    private int detailPanelY;
    private int detailPanelW = DETAIL_PANEL_W;
    private int mousePanelW = MOUSE_PANEL_W;
    private int contentTop;
    private int contentBottom;
    private int detailModifyX = -1;
    private int detailModifyY = -1;
    private int detailUnbindX = -1;
    private int detailUnbindY = -1;

    private record Rect(int x, int y, int w, int h) {
        boolean contains(double mouseX, double mouseY) {
            return inside(mouseX, mouseY, x, y, w, h);
        }
    }

    public KeybindViewerScreen(Screen parent) {
        super(Component.translatable("screen.newvisualkeybing.viewer.title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        super.init();
        scanner.scan();

        int panelW = width - PANEL_MARGIN * 2;
        searchBox = new EditBox(font, PANEL_MARGIN + 18, PANEL_MARGIN + 55, Math.max(96, Math.min(260, panelW - 330)), SEARCH_H, Component.translatable("screen.newvisualkeybing.viewer.search"));
        searchBox.setHint(Component.translatable("screen.newvisualkeybing.viewer.search"));
        searchBox.setResponder(value -> textFilteredKeys = scanner.filterKeys(value));
        addRenderableWidget(searchBox);

        manageButton = MCButton.create(width - 292, PANEL_MARGIN + 51, 64, 20, Component.translatable("screen.newvisualkeybing.viewer.manage"), button ->
                minecraft.setScreen(new KeybindEditScreen(this)));
        addRenderableWidget(manageButton);

        modToggleButton = MCButton.create(width - 222, PANEL_MARGIN + 51, 58, 20, Component.translatable("screen.newvisualkeybing.viewer.mods"), button -> {
            modPanelOpen = !modPanelOpen;
            button.setMessage(Component.translatable("screen.newvisualkeybing.viewer.mods"));
        });
        addRenderableWidget(modToggleButton);

        themeButton = MCButton.create(width - 158, PANEL_MARGIN + 51, 64, 20, themeLabel(), button -> {
            UITheme.setMode(UITheme.getMode() == UITheme.Mode.DARK ? UITheme.Mode.LIGHT : UITheme.Mode.DARK);
            button.setMessage(themeLabel());
        });
        addRenderableWidget(themeButton);

        closeButton = MCButton.create(width - 88, PANEL_MARGIN + 51, 52, 20, Component.translatable("gui.done"), button -> onClose());
        addRenderableWidget(closeButton);

        refreshFilters();
    }

    @Override
    public void tick() {
        super.tick();
        searchBox.tick();
        scanner.refreshIfNeeded();
        refreshFilters();
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        scanner.refreshIfNeeded();
        refreshFilters();
        layoutPanels();
        hoveredVirtualKey = null;
        detailModifyX = detailModifyY = detailUnbindX = detailUnbindY = -1;

        var colors = UITheme.colors();
        int panelX = PANEL_MARGIN;
        int panelY = PANEL_MARGIN;
        int panelW = width - PANEL_MARGIN * 2;
        int panelH = height - PANEL_MARGIN * 2;

        graphics.fill(0, 0, width, height, UITheme.withAlpha(colors.panelBg(), 0xE0));
        UITheme.drawCardShadow(graphics, panelX - 2, panelY - 2, panelW + 4, panelH + 4, 12);
        UITheme.drawGlassPanel(graphics, panelX, panelY, panelW, panelH, 10);
        // 渐变标题栏
        UITheme.fillRoundedRect(graphics, panelX, panelY, panelW, 28, 10, UITheme.brighten(colors.headerBg(), 0.04f));
        UITheme.fillRoundedRect(graphics, panelX, panelY + 14, panelW, 14, 10, colors.headerBg());
        UITheme.fillRoundedRect(graphics, panelX + 2, panelY + 1, panelW - 4, 2, 2, UITheme.withAlpha(0xFFFFFFFF, 0x18));
        graphics.fill(panelX + 12, panelY + 27, panelX + panelW - 12, panelY + 28, colors.divider());
        graphics.drawString(font, title, panelX + 16, panelY + 10, colors.textPrimary(), true);
        graphics.drawString(font, Component.translatable("screen.newvisualkeybing.viewer.subtitle"), panelX + 16, panelY + 32, colors.textSecondary(), false);

        renderTabs(graphics, panelX + 16, panelY + 84, mouseX, mouseY);
        renderLegend(graphics, panelX + 16, panelY + 112);
        if (modPanelOpen) {
            renderModPanel(graphics, mouseX, mouseY);
        }
        renderKeyboard(graphics, mouseX, mouseY);
        renderMousePanel(graphics, mouseX, mouseY);
        renderDetailPanel(graphics, selectedVirtualKey != null ? selectedVirtualKey : hoveredVirtualKey, mouseX, mouseY);
        renderStatusBar(graphics, panelX, panelY, panelW, panelH);
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    private void renderTabs(GuiGraphics graphics, int x, int y, int mouseX, int mouseY) {
        int currentX = x;
        for (FilterTab tab : FilterTab.values()) {
            String label = tab.getLabel();
            int w = font.width(label) + 16;
            boolean active = tab == activeFilter;
            boolean hovered = inside(mouseX, mouseY, currentX, y, w, TAB_H);
            int fill = active
                    ? UITheme.lerpColor(UITheme.colors().widgetBg(), UITheme.colors().accent(), 0.55f)
                    : hovered ? UITheme.lerpColor(UITheme.colors().widgetBg(), UITheme.colors().accentAlt(), 0.24f) : UITheme.colors().widgetBg();
            UITheme.fillRoundedRect(graphics, currentX, y, w, TAB_H, 8, fill);
            UITheme.drawRoundedBorder(graphics, currentX, y, w, TAB_H, 8, active ? UITheme.colors().accent() : UITheme.colors().widgetBorder());
            graphics.drawString(font, label, currentX + 8, y + 7, active ? 0xFFFFFFFF : UITheme.colors().textSecondary(), false);
            currentX += w + 6;
        }
    }

    private void renderLegend(GuiGraphics graphics, int x, int y) {
        renderBadge(graphics, x, y, Component.translatable("screen.newvisualkeybing.viewer.legend.free").getString(), UITheme.colors().widgetBg());
        renderBadge(graphics, x + 68, y, Component.translatable("screen.newvisualkeybing.viewer.legend.self").getString(), UITheme.colors().accent());
        renderBadge(graphics, x + 136, y, Component.translatable("screen.newvisualkeybing.viewer.legend.other").getString(), UITheme.colors().success());
        renderBadge(graphics, x + 214, y, Component.translatable("screen.newvisualkeybing.viewer.legend.conflict").getString(), UITheme.colors().danger());
    }

    private void renderBadge(GuiGraphics graphics, int x, int y, String label, int color) {
        int w = font.width(label) + 14;
        UITheme.fillRoundedRect(graphics, x, y, w, 16, 8, color);
        graphics.drawString(font, label, x + 7, y + 4, 0xFFFFFFFF, false);
    }

    private void renderModPanel(GuiGraphics graphics, int mouseX, int mouseY) {
        var colors = UITheme.colors();
        int x = PANEL_MARGIN + 14;
        int y = contentTop;
        int w = MOD_PANEL_W;
        int h = contentBottom - contentTop;
        UITheme.drawGlassPanel(graphics, x, y, w, h, 10);
        graphics.drawString(font, Component.translatable("screen.newvisualkeybing.viewer.mods"), x + 12, y + 12, colors.textPrimary(), false);

        int searchY = y + 30;
        UITheme.fillRoundedRect(graphics, x + 8, searchY, w - 16, 18, 6, colors.inputBg());
        UITheme.drawRoundedBorder(graphics, x + 8, searchY, w - 16, 18, 6, colors.widgetBorder());
        String display = modSearchQuery.isBlank() ? Component.translatable("screen.newvisualkeybing.viewer.mod_search").getString() : modSearchQuery;
        graphics.drawString(font, display, x + 14, searchY + 5, modSearchQuery.isBlank() ? colors.textMuted() : colors.textPrimary(), false);

        List<Map.Entry<String, String>> mods = filteredModEntries();
        int rowY = searchY + 28;
        int rowH = 18;
        int visibleRows = Math.max(1, (h - 74) / rowH);
        modScrollOffset = Mth.clamp(modScrollOffset, 0, Math.max(0, mods.size() - visibleRows));
        for (int i = modScrollOffset; i < mods.size() && i < modScrollOffset + visibleRows; i++) {
            Map.Entry<String, String> mod = mods.get(i);
            boolean selected = mod.getKey().equals(selectedModId);
            boolean hovered = inside(mouseX, mouseY, x + 8, rowY, w - 16, rowH - 1);
            int fill = selected ? UITheme.lerpColor(colors.widgetBg(), colors.accent(), 0.40f)
                    : hovered ? UITheme.lerpColor(colors.widgetBg(), colors.accentAlt(), 0.16f) : colors.widgetBg();
            UITheme.fillRoundedRect(graphics, x + 8, rowY, w - 16, rowH - 1, 6, fill);
            graphics.drawString(font, trim(mod.getValue(), 18), x + 14, rowY + 5, selected ? 0xFFFFFFFF : colors.textSecondary(), false);
            rowY += rowH;
        }

        int clearY = y + h - 28;
        UITheme.fillRoundedRect(graphics, x + 8, clearY, w - 16, 18, 6, selectedModId == null ? colors.widgetBg() : UITheme.lerpColor(colors.widgetBg(), colors.danger(), 0.25f));
        UITheme.drawRoundedBorder(graphics, x + 8, clearY, w - 16, 18, 6, colors.widgetBorder());
        graphics.drawString(font, Component.translatable("screen.newvisualkeybing.viewer.clear_mod").getString(), x + 20, clearY + 5, selectedModId == null ? colors.textMuted() : colors.textPrimary(), false);
    }

    private void renderKeyboard(GuiGraphics graphics, int mouseX, int mouseY) {
        for (KeyboardLayoutData.KeyDef key : KeyboardLayoutData.KEYS) {
            int x = key.screenX(keyboardX, keyScale);
            int y = key.screenY(keyboardY, keyScale);
            int w = key.screenW(keyScale);
            int h = key.screenH(keyScale);

            boolean matched = isVisibleKey(key.glfwKey());
            boolean hovered = inside(mouseX, mouseY, x, y, w, h);
            boolean selected = selectedVirtualKey != null && selectedVirtualKey == key.glfwKey();
            if (hovered) {
                hoveredVirtualKey = key.glfwKey();
            }

            KeyBindingScanner.KeyStatus status = scanner.getStatus(key.glfwKey());
            int fill = keyStatusColor(status, matched);
            UITheme.fillRoundedRect(graphics, x, y, w, h, w >= 34 || h >= 34 ? 4 : 3, fill);
            UITheme.drawRoundedBorder(graphics, x, y, w, h, w >= 34 || h >= 34 ? 4 : 3, selected ? UITheme.colors().accent() : hovered ? UITheme.colors().accentAlt() : UITheme.colors().widgetBorder());

            int labelColor = matched ? UITheme.colors().textPrimary() : UITheme.colors().textMuted();
            int tx = x + (w - font.width(key.label())) / 2;
            int ty = y + (h - font.lineHeight) / 2;
            graphics.drawString(font, key.label(), tx, ty, labelColor, false);

            List<KeyBindingScanner.KeyBindingInfo> bindings = scanner.getBindings(key.glfwKey());
            if (!bindings.isEmpty()) {
                String count = String.valueOf(bindings.size());
                graphics.drawString(font, count, x + w - font.width(count) - 3, y + 3, UITheme.colors().accentAlt(), false);
            }
        }
    }

    private void renderMousePanel(GuiGraphics graphics, int mouseX, int mouseY) {
        var colors = UITheme.colors();
        int panelH = Math.min(194, Math.max(166, contentBottom - mousePanelY));
        UITheme.drawGlassPanel(graphics, mousePanelX, mousePanelY, mousePanelW, panelH, 10);
        graphics.drawString(font, Component.translatable("screen.newvisualkeybing.viewer.mouse"), mousePanelX + 12, mousePanelY + 12, colors.textPrimary(), false);

        int textureW = Math.min(mousePanelW - 30, 104);
        int textureH = Math.min(panelH - 42, Math.round(textureW * 1.50f));
        int textureX = mousePanelX + (mousePanelW - textureW) / 2 + 6;
        int textureY = mousePanelY + 34;

        graphics.blit(MOUSE_TEXTURE, textureX, textureY, textureW, textureH, 0.0f, 0.0f, MOUSE_TEXTURE_W, MOUSE_TEXTURE_H, MOUSE_TEXTURE_W, MOUSE_TEXTURE_H);

        for (int i = 0; i < KeyboardLayoutData.MOUSE_KEYS.size(); i++) {
            Rect bounds = mouseKeyBounds(i);
            if (bounds != null) {
                renderMouseKey(graphics, mouseX, mouseY, KeyboardLayoutData.MOUSE_KEYS.get(i), bounds.x(), bounds.y(), bounds.w(), bounds.h(), i == 1);
            }
        }
    }

    private void renderMouseKey(GuiGraphics graphics, int mouseX, int mouseY, KeyboardLayoutData.KeyDef key, int x, int y, int w, int h, boolean wheel) {
        int mouseButton = KeyboardLayoutData.virtualToMouseBtn(key.glfwKey());
        boolean matched = isVisibleKey(key.glfwKey());
        boolean hovered = inside(mouseX, mouseY, x, y, w, h);
        boolean selected = selectedVirtualKey != null && selectedVirtualKey == key.glfwKey();
        if (hovered) {
            hoveredVirtualKey = key.glfwKey();
        }

        int fill = UITheme.withAlpha(keyStatusColor(scanner.getMouseStatus(mouseButton), matched), wheel ? 0x8A : 0x72);
        UITheme.fillRoundedRect(graphics, x, y, w, h, Math.max(3, Math.min(8, w / 3)), fill);
        UITheme.drawRoundedBorder(graphics, x, y, w, h, Math.max(3, Math.min(8, w / 3)), selected ? UITheme.colors().accent() : hovered ? UITheme.colors().accentAlt() : UITheme.withAlpha(UITheme.colors().widgetBorder(), 0x86));
        if (w >= 16) {
            graphics.drawString(font, key.label(), x + (w - font.width(key.label())) / 2, y + (h - font.lineHeight) / 2, matched ? UITheme.colors().textPrimary() : UITheme.colors().textMuted(), false);
        }
    }

    private Rect mouseKeyBounds(int index) {
        int textureW = Math.min(mousePanelW - 30, 104);
        int panelH = Math.min(194, Math.max(166, contentBottom - mousePanelY));
        int textureH = Math.min(panelH - 42, Math.round(textureW * 1.50f));
        int x = mousePanelX + (mousePanelW - textureW) / 2 + 6;
        int y = mousePanelY + 34;

        return switch (index) {
            case 0 -> relRect(x, y, textureW, textureH, 0.245f, 0.080f, 0.235f, 0.360f);
            case 1 -> relRect(x, y, textureW, textureH, 0.455f, 0.112f, 0.110f, 0.335f);
            case 2 -> relRect(x, y, textureW, textureH, 0.580f, 0.080f, 0.245f, 0.360f);
            case 3 -> relRect(x, y, textureW, textureH, 0.080f, 0.350f, 0.120f, 0.165f);
            case 4 -> relRect(x, y, textureW, textureH, 0.090f, 0.525f, 0.125f, 0.175f);
            default -> null;
        };
    }

    private static Rect relRect(int x, int y, int w, int h, float rx, float ry, float rw, float rh) {
        return new Rect(
                x + Math.round(w * rx),
                y + Math.round(h * ry),
                Math.max(8, Math.round(w * rw)),
                Math.max(8, Math.round(h * rh))
        );
    }

    private void renderDetailPanel(GuiGraphics graphics, Integer virtualKey, int mouseX, int mouseY) {
        var colors = UITheme.colors();
        UITheme.drawGlassPanel(graphics, detailPanelX, detailPanelY, detailPanelW, contentBottom - contentTop, 10);

        String titleText = Component.translatable("screen.newvisualkeybing.viewer.details").getString();
        graphics.drawString(font, titleText, detailPanelX + 12, detailPanelY + 12, colors.textPrimary(), false);

        if (virtualKey == null) {
            graphics.drawString(font, Component.translatable("screen.newvisualkeybing.viewer.hover_hint"), detailPanelX + 12, detailPanelY + 34, colors.textMuted(), false);
            return;
        }

        String keyName = KeyboardLayoutData.isMouse(virtualKey)
                ? KeyBindingScanner.getMouseButtonLabel(KeyboardLayoutData.virtualToMouseBtn(virtualKey))
                : scanner.getKeyLabel(virtualKey);
        graphics.drawString(font, keyName, detailPanelX + 12, detailPanelY + 34, colors.accent(), false);

        List<KeyBindingScanner.KeyBindingInfo> bindings = KeyboardLayoutData.isMouse(virtualKey)
                ? scanner.getMouseBindings(KeyboardLayoutData.virtualToMouseBtn(virtualKey))
                : scanner.getBindings(virtualKey);
        KeyBindingScanner.KeyStatus status = KeyboardLayoutData.isMouse(virtualKey)
                ? scanner.getMouseStatus(KeyboardLayoutData.virtualToMouseBtn(virtualKey))
                : scanner.getStatus(virtualKey);

        graphics.drawString(font, Component.translatable(statusTranslation(status)).getString(), detailPanelX + 12, detailPanelY + 49, keyStatusColor(status, true), false);

        int lineY = detailPanelY + 68;
        if (bindings.isEmpty()) {
            graphics.drawString(font, Component.translatable("screen.newvisualkeybing.viewer.unbound"), detailPanelX + 12, lineY, colors.textMuted(), false);
        } else {
            for (int i = 0; i < bindings.size() && i < 8; i++) {
                KeyBindingScanner.KeyBindingInfo info = bindings.get(i);
                graphics.drawString(font, "• " + trim(info.actionName(), 24), detailPanelX + 12, lineY, info.self() ? colors.accent() : colors.textPrimary(), false);
                lineY += 11;
                graphics.drawString(font, trim(info.modName(), 24), detailPanelX + 20, lineY, colors.textSecondary(), false);
                lineY += 15;
            }
        }

        detailModifyX = detailPanelX + 12;
        detailModifyY = detailPanelY + (contentBottom - contentTop) - 54;
        int btnW = Math.max(74, (detailPanelW - 32) / 2);
        boolean modifyHover = inside(mouseX, mouseY, detailModifyX, detailModifyY, btnW, 18);
        UITheme.fillRoundedRect(graphics, detailModifyX, detailModifyY, btnW, 18, 6, UITheme.lerpColor(colors.widgetBg(), colors.accent(), modifyHover ? 0.48f : 0.26f));
        UITheme.drawRoundedBorder(graphics, detailModifyX, detailModifyY, btnW, 18, 6, colors.widgetBorder());
        String modifyText = Component.translatable("screen.newvisualkeybing.viewer.modify").getString();
        graphics.drawString(font, modifyText, detailModifyX + (btnW - font.width(modifyText)) / 2, detailModifyY + 5, colors.textPrimary(), false);

        detailUnbindX = detailModifyX + btnW + 8;
        detailUnbindY = detailModifyY;
        boolean unbindHover = inside(mouseX, mouseY, detailUnbindX, detailUnbindY, btnW, 18);
        UITheme.fillRoundedRect(graphics, detailUnbindX, detailUnbindY, btnW, 18, 6, UITheme.lerpColor(colors.widgetBg(), colors.danger(), unbindHover ? 0.40f : 0.22f));
        UITheme.drawRoundedBorder(graphics, detailUnbindX, detailUnbindY, btnW, 18, 6, colors.widgetBorder());
        String unbindText = Component.translatable("screen.newvisualkeybing.viewer.unbind").getString();
        graphics.drawString(font, unbindText, detailUnbindX + (btnW - font.width(unbindText)) / 2, detailUnbindY + 5, colors.textPrimary(), false);
    }

    private void renderStatusBar(GuiGraphics graphics, int panelX, int panelY, int panelW, int panelH) {
        KeyBindingScanner.ScanStats stats = scanner.getStats();
        int y = panelY + panelH - STATUS_H;
        graphics.fill(panelX + 12, y, panelX + panelW - 12, y + 1, UITheme.colors().divider());
        String summary = Component.translatable(
                "screen.newvisualkeybing.viewer.stats",
                stats.total(),
                stats.free(),
                stats.self(),
                stats.other(),
                stats.conflict()
        ).getString();
        graphics.drawString(font, summary, panelX + 16, y + 8, UITheme.colors().textSecondary(), false);
        String hint = Component.translatable("screen.newvisualkeybing.viewer.hint").getString();
        graphics.drawString(font, hint, panelX + panelW - font.width(hint) - 16, y + 8, UITheme.colors().textMuted(), false);
    }

    private void layoutPanels() {
        int leftInset = modPanelOpen ? MOD_PANEL_W + 16 : 12;
        int panelX = PANEL_MARGIN;
        int panelW = width - PANEL_MARGIN * 2;
        contentTop = PANEL_MARGIN + HEADER_H + 34;
        contentBottom = height - PANEL_MARGIN - STATUS_H - 8;

        float uiScale = Mth.clamp(Math.min(width / 980.0f, height / 560.0f), 0.78f, 1.0f);
        detailPanelW = Mth.clamp(Math.round(DETAIL_PANEL_W * uiScale), 170, DETAIL_PANEL_W);
        mousePanelW = Mth.clamp(Math.round(MOUSE_PANEL_W * uiScale), 126, MOUSE_PANEL_W);

        detailPanelX = panelX + panelW - detailPanelW - 12;
        detailPanelY = contentTop;
        mousePanelX = detailPanelX - mousePanelW - 12;
        mousePanelY = contentTop;

        int keyboardLeft = panelX + leftInset;
        int keyboardRight = mousePanelX - 16;
        int keyboardSpaceW = keyboardRight - keyboardLeft;
        int keyboardSpaceH = contentBottom - contentTop;
        float keyboardGapW = (KeyboardLayoutData.TOTAL_WIDTH_U - 1.0f) * KeyboardLayoutData.BASE_GAP;
        float keyboardGapH = (KeyboardLayoutData.TOTAL_HEIGHT_U - 1.0f) * KeyboardLayoutData.BASE_GAP;
        float scaleByW = (keyboardSpaceW - keyboardGapW) / KeyboardLayoutData.TOTAL_WIDTH_U;
        float scaleByH = (keyboardSpaceH - keyboardGapH) / KeyboardLayoutData.TOTAL_HEIGHT_U;
        keyScale = Mth.clamp(Math.min(scaleByW, scaleByH), 7.0f, 24.0f);

        int keyboardW = KeyboardLayoutData.totalWidthPx(keyScale);
        int keyboardH = KeyboardLayoutData.totalHeightPx(keyScale);
        keyboardX = keyboardLeft + Math.max(0, (keyboardSpaceW - keyboardW) / 2);
        keyboardY = contentTop + Math.max(0, (keyboardSpaceH - keyboardH) / 2);
    }

    private void refreshFilters() {
        textFilteredKeys = scanner.filterKeys(searchBox != null ? searchBox.getValue() : "");
        tabFilteredKeys = scanner.filterByStatus(activeFilter);
        modFilteredKeys = scanner.filterByMod(selectedModId);
    }

    private int keyStatusColor(KeyBindingScanner.KeyStatus status, boolean matched) {
        var colors = UITheme.colors();
        int base = switch (status) {
            case FREE -> colors.widgetBg();
            case SELF -> UITheme.lerpColor(colors.widgetBg(), colors.accent(), 0.58f);
            case OTHER_SINGLE, BOUND -> UITheme.lerpColor(colors.widgetBg(), colors.success(), 0.52f);
            case CONFLICT -> UITheme.lerpColor(colors.widgetBg(), colors.danger(), 0.68f);
        };
        return matched ? base : UITheme.lerpColor(base, colors.panelBg(), 0.56f);
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
            if (modSearchQuery.isBlank() || entry.getValue().toLowerCase().contains(modSearchQuery.toLowerCase()) || entry.getKey().toLowerCase().contains(modSearchQuery.toLowerCase())) {
                entries.add(entry);
            }
        }
        return entries;
    }

    private Component themeLabel() {
        return Component.literal(UITheme.getMode() == UITheme.Mode.DARK ? "Dark" : "Light");
    }

    private static String trim(String value, int maxLength) {
        return value.length() <= maxLength ? value : value.substring(0, maxLength - 2) + "..";
    }

    private static String statusTranslation(KeyBindingScanner.KeyStatus status) {
        return switch (status) {
            case FREE -> "screen.newvisualkeybing.viewer.legend.free";
            case SELF -> "screen.newvisualkeybing.viewer.legend.self";
            case OTHER_SINGLE, BOUND -> "screen.newvisualkeybing.viewer.legend.other";
            case CONFLICT -> "screen.newvisualkeybing.viewer.legend.conflict";
        };
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (super.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }
        if (button != 0) {
            return false;
        }

        int panelX = PANEL_MARGIN + 16;
        int tabY = PANEL_MARGIN + 84;
        int currentX = panelX;
        for (FilterTab tab : FilterTab.values()) {
            int w = font.width(tab.getLabel()) + 16;
            if (inside(mouseX, mouseY, currentX, tabY, w, TAB_H)) {
                activeFilter = tab;
                refreshFilters();
                return true;
            }
            currentX += w + 6;
        }

        if (modPanelOpen) {
            if (handleModPanelClick(mouseX, mouseY)) {
                return true;
            }
        }

        int detailButtonW = Math.max(74, (detailPanelW - 32) / 2);
        if (selectedVirtualKey != null && inside(mouseX, mouseY, detailModifyX, detailModifyY, detailButtonW, 18)) {
            minecraft.setScreen(new KeybindEditScreen(this, selectedVirtualKey));
            return true;
        }
        if (selectedVirtualKey != null && inside(mouseX, mouseY, detailUnbindX, detailUnbindY, detailButtonW, 18)) {
            KeybindEditScreen.unbindAllForVirtualKey(selectedVirtualKey);
            scanner.scan();
            refreshFilters();
            return true;
        }

        for (KeyboardLayoutData.KeyDef key : KeyboardLayoutData.KEYS) {
            int x = key.screenX(keyboardX, keyScale);
            int y = key.screenY(keyboardY, keyScale);
            int w = key.screenW(keyScale);
            int h = key.screenH(keyScale);
            if (inside(mouseX, mouseY, x, y, w, h)) {
                selectedVirtualKey = key.glfwKey();
                return true;
            }
        }

        for (int i = 0; i < KeyboardLayoutData.MOUSE_KEYS.size(); i++) {
            Rect bounds = mouseKeyBounds(i);
            if (bounds != null && bounds.contains(mouseX, mouseY)) {
                selectedVirtualKey = KeyboardLayoutData.MOUSE_KEYS.get(i).glfwKey();
                return true;
            }
        }

        selectedVirtualKey = null;
        return false;
    }

    private boolean handleModPanelClick(double mouseX, double mouseY) {
        int x = PANEL_MARGIN + 14;
        int y = contentTop;
        int h = contentBottom - contentTop;
        int searchY = y + 30;
        if (inside(mouseX, mouseY, x + 8, searchY, MOD_PANEL_W - 16, 18)) {
            modSearchQuery = "";
            return true;
        }

        List<Map.Entry<String, String>> mods = filteredModEntries();
        int rowY = searchY + 28;
        int rowH = 18;
        int visibleRows = Math.max(1, (h - 74) / rowH);
        for (int i = modScrollOffset; i < mods.size() && i < modScrollOffset + visibleRows; i++) {
            if (inside(mouseX, mouseY, x + 8, rowY, MOD_PANEL_W - 16, rowH - 1)) {
                selectedModId = mods.get(i).getKey().equals(selectedModId) ? null : mods.get(i).getKey();
                refreshFilters();
                return true;
            }
            rowY += rowH;
        }

        int clearY = y + h - 28;
        if (inside(mouseX, mouseY, x + 8, clearY, MOD_PANEL_W - 16, 18)) {
            selectedModId = null;
            refreshFilters();
            return true;
        }
        return false;
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        if (modPanelOpen && !searchBox.isFocused() && codePoint >= 32) {
            modSearchQuery += codePoint;
            modScrollOffset = 0;
            return true;
        }
        return super.charTyped(codePoint, modifiers);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (modPanelOpen && !searchBox.isFocused()) {
            if (keyCode == 259 && !modSearchQuery.isEmpty()) {
                modSearchQuery = modSearchQuery.substring(0, modSearchQuery.length() - 1);
                return true;
            }
            if (keyCode == 256) {
                modSearchQuery = "";
            }
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (modPanelOpen) {
            int x = PANEL_MARGIN + 14;
            if (mouseX >= x && mouseX <= x + MOD_PANEL_W) {
                int visibleRows = Math.max(1, (contentBottom - contentTop - 74) / 18);
                modScrollOffset = Mth.clamp(modScrollOffset - (int) Math.signum(delta), 0, Math.max(0, filteredModEntries().size() - visibleRows));
                return true;
            }
        }
        return super.mouseScrolled(mouseX, mouseY, delta);
    }

    private static boolean inside(double mouseX, double mouseY, int x, int y, int w, int h) {
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
