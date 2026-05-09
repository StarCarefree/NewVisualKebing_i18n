package com.github.newvisualkeybing.client.screen;

import com.github.newvisualkeybing.client.keyboard.KeyBindingScanner;
import com.github.newvisualkeybing.client.keyboard.KeyboardLayoutData;
import com.github.newvisualkeybing.client.keyboard.KeybindPriorityEnforcer;
import com.github.newvisualkeybing.client.ui.UITheme;
import com.github.newvisualkeybing.mixin.KeyMappingAccessor;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Modal popover triggered from the Viewer's detail panel "Modify" button.
 * Shows every KeyMapping bound to the current virtual key, each with quick Rebind / Unbind controls.
 * In LISTEN mode the popover transforms into "press a key for X" with ESC to cancel.
 */
final class KeybindQuickEditPopover {

    private static final int CARD_W = 336;
    private static final int CARD_PAD = 16;
    private static final int HEADER_H = 32;
    private static final int FOOTER_H = 36;
    private static final int ROW_H = 28;
    private static final int ROW_GAP = 4;
    private static final int BTN_REBIND_W = 64;
    private static final int BTN_UNBIND_W = 22;
    private static final int BTN_GAP = 8;
    private static final int CLOSE_BTN_W = 72;

    private final KeyBindingScanner scanner;
    private final Consumer<String> noticeSink;
    private final Runnable onMutation;

    private boolean open;
    private int virtualKey;
    private KeyMapping listenMapping;

    private int cardX, cardY, cardW, cardH;
    private int closeX, closeY;
    private int headerCloseX, headerCloseY;
    private final List<RowHit> hits = new ArrayList<>();

    private record RowHit(int rebindX, int rebindY, int unbindX, int unbindY, int btnH, KeyMapping mapping) {}

    KeybindQuickEditPopover(KeyBindingScanner scanner, Consumer<String> noticeSink, Runnable onMutation) {
        this.scanner = scanner;
        this.noticeSink = noticeSink;
        this.onMutation = onMutation;
    }

    boolean isOpen() { return open; }

    void open(int virtualKey) {
        this.open = true;
        this.virtualKey = virtualKey;
        this.listenMapping = null;
    }

    void close() {
        this.open = false;
        this.listenMapping = null;
    }

    void render(GuiGraphics g, Font font, int screenW, int screenH, int mouseX, int mouseY) {
        if (!open) return;
        var c = UITheme.colors();

        // Dim backdrop
        g.fill(0, 0, screenW, screenH, 0xC0000000);

        List<KeyMapping> mappings = collectMappings();
        int rowCount = Math.max(1, mappings.size());
        int bodyH = listenMapping != null
                ? 80
                : rowCount * ROW_H + (rowCount - 1) * ROW_GAP + CARD_PAD * 2;
        cardW = CARD_W;
        cardH = HEADER_H + bodyH + FOOTER_H;
        cardX = (screenW - cardW) / 2;
        cardY = (screenH - cardH) / 2;

        UITheme.drawGlassPanel(g, cardX, cardY, cardW, cardH, 10);

        // Header
        String keyLabel = scanner.getVirtualKeyLabel(virtualKey);
        String title = Component.translatable("screen.newvisualkeybing.viewer.quick_edit.title").getString()
                + ": " + keyLabel;
        g.drawString(font, title, cardX + CARD_PAD, cardY + (HEADER_H - font.lineHeight) / 2 + 2,
                c.textPrimary(), false);
        g.fill(cardX + 8, cardY + HEADER_H - 1, cardX + cardW - 8, cardY + HEADER_H,
                UITheme.withAlpha(c.divider(), 0xA0));

        headerCloseX = cardX + cardW - CARD_PAD - 14;
        headerCloseY = cardY + (HEADER_H - 14) / 2 + 2;
        renderXButton(g, headerCloseX, headerCloseY, 14,
                KeybindViewerScreen.inside(mouseX, mouseY, headerCloseX, headerCloseY, 14, 14),
                c.textSecondary(), c.danger());

        hits.clear();
        if (listenMapping != null) {
            renderListenMode(g, font, c);
        } else {
            renderBrowseMode(g, font, mappings, mouseX, mouseY, c);
        }

        // Footer Close
        closeX = cardX + (cardW - CLOSE_BTN_W) / 2;
        closeY = cardY + cardH - FOOTER_H + (FOOTER_H - 20) / 2;
        boolean closeHover = KeybindViewerScreen.inside(mouseX, mouseY, closeX, closeY, CLOSE_BTN_W, 20);
        KeybindViewerScreen.renderActionButton(g, font, closeX, closeY, CLOSE_BTN_W, 20,
                Component.translatable("screen.newvisualkeybing.viewer.quick_edit.close").getString(),
                c.widgetBorder(), closeHover);
    }

    private void renderBrowseMode(GuiGraphics g, Font font, List<KeyMapping> mappings,
                                  int mouseX, int mouseY, UITheme.ColorPalette c) {
        int rowsTop = cardY + HEADER_H + CARD_PAD;
        int rowX = cardX + CARD_PAD;
        int rowW = cardW - CARD_PAD * 2;

        if (mappings.isEmpty()) {
            String empty = Component.translatable("screen.newvisualkeybing.viewer.quick_edit.empty").getString();
            g.drawString(font, empty, cardX + (cardW - font.width(empty)) / 2,
                    rowsTop + (ROW_H - font.lineHeight) / 2, c.textMuted(), false);
            return;
        }

        int btnH = ROW_H - 6;
        for (int i = 0; i < mappings.size(); i++) {
            KeyMapping km = mappings.get(i);
            int rowY = rowsTop + i * (ROW_H + ROW_GAP);

            UITheme.fillRoundedRect(g, rowX, rowY, rowW, ROW_H, 4,
                    UITheme.withAlpha(c.widgetBg(), 0x66));

            // Action label (truncated to fit before buttons)
            int rebindBtnX = rowX + rowW - BTN_UNBIND_W - BTN_GAP - BTN_REBIND_W;
            int unbindBtnX = rowX + rowW - BTN_UNBIND_W;
            int btnY = rowY + (ROW_H - btnH) / 2;

            String action = Component.translatable(km.getName()).getString();
            int labelMaxW = rebindBtnX - rowX - 12;
            String fitted = KeybindViewerScreen.fitToWidth(font, action, labelMaxW);
            g.drawString(font, fitted, rowX + 8, rowY + (ROW_H - font.lineHeight) / 2,
                    c.textPrimary(), false);

            boolean rebindHover = KeybindViewerScreen.inside(mouseX, mouseY, rebindBtnX, btnY, BTN_REBIND_W, btnH);
            KeybindViewerScreen.renderActionButton(g, font, rebindBtnX, btnY, BTN_REBIND_W, btnH,
                    Component.translatable("screen.newvisualkeybing.viewer.quick_edit.rebind").getString(),
                    c.accent(), rebindHover);

            boolean unbindHover = KeybindViewerScreen.inside(mouseX, mouseY, unbindBtnX, btnY, BTN_UNBIND_W, btnH);
            renderXButton(g, unbindBtnX, btnY, BTN_UNBIND_W, unbindHover, c.textSecondary(), c.danger());

            hits.add(new RowHit(rebindBtnX, btnY, unbindBtnX, btnY, btnH, km));
        }
    }

    private void renderListenMode(GuiGraphics g, Font font, UITheme.ColorPalette c) {
        int contentTop = cardY + HEADER_H + CARD_PAD;
        String action = Component.translatable(listenMapping.getName()).getString();
        String l1 = Component.translatable("screen.newvisualkeybing.viewer.quick_edit.listening", action).getString();
        String l2 = Component.translatable("screen.newvisualkeybing.viewer.waiting_hint").getString();
        g.drawString(font, l1, cardX + (cardW - font.width(l1)) / 2, contentTop + 12,
                c.accentLight(), true);
        g.drawString(font, l2, cardX + (cardW - font.width(l2)) / 2, contentTop + 36,
                c.textMuted(), false);
    }

    boolean mouseClicked(double mx, double my, int button) {
        if (!open) return false;

        // Listen mode: a mouse press binds to that mouse button
        if (listenMapping != null) {
            if (button >= GLFW.GLFW_MOUSE_BUTTON_1 && button <= GLFW.GLFW_MOUSE_BUTTON_LAST) {
                applyKey(InputConstants.Type.MOUSE.getOrCreate(button));
            }
            return true;
        }

        if (button != 0) return true;


        if (KeybindViewerScreen.inside(mx, my, headerCloseX, headerCloseY, 14, 14)
                || KeybindViewerScreen.inside(mx, my, closeX, closeY, CLOSE_BTN_W, 20)) {
            close();
            return true;
        }

        // Per-row buttons
        for (RowHit h : hits) {
            if (KeybindViewerScreen.inside(mx, my, h.rebindX, h.rebindY, BTN_REBIND_W, h.btnH)) {
                listenMapping = h.mapping;
                return true;
            }
            if (KeybindViewerScreen.inside(mx, my, h.unbindX, h.unbindY, BTN_UNBIND_W, h.btnH)) {
                unbind(h.mapping);
                return true;
            }
        }

        // Click outside the card closes the popover; click inside the card is consumed but no-op.
        if (KeybindViewerScreen.inside(mx, my, cardX, cardY, cardW, cardH)) return true;
        close();
        return true;
    }

    boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (!open) return false;
        if (listenMapping != null) {
            if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
                listenMapping = null;
                return true;
            }
            applyKey(InputConstants.getKey(keyCode, scanCode));
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            close();
            return true;
        }
        // Swallow other keys while modal is open so they don't fall through to viewer search etc.
        return true;
    }

    boolean mouseScrolled(double mx, double my, double delta) {
        return open;  // swallow scroll while modal is open
    }

    private void applyKey(InputConstants.Key key) {
        if (listenMapping == null) return;
        Minecraft mc = Minecraft.getInstance();
        String prev = listenMapping.getTranslatedKeyMessage().getString();
        String action = Component.translatable(listenMapping.getName()).getString();
        mc.options.setKey(listenMapping, key);
        KeybindPriorityEnforcer.resetAndEnforce();
        mc.options.save();
        listenMapping = null;
        if (onMutation != null) onMutation.run();
        noticeSink.accept(Component.translatable("screen.newvisualkeybing.viewer.rebound",
                action, key.getDisplayName().getString()).getString());
    }

    private void unbind(KeyMapping mapping) {
        Minecraft mc = Minecraft.getInstance();
        String action = Component.translatable(mapping.getName()).getString();
        mc.options.setKey(mapping, InputConstants.UNKNOWN);
        KeybindPriorityEnforcer.resetAndEnforce();
        mc.options.save();
        if (onMutation != null) onMutation.run();
        noticeSink.accept(Component.translatable("screen.newvisualkeybing.viewer.notice.unbind_one",
                action).getString());
        // If that was the last binding on this key, close the popover.
        if (collectMappings().isEmpty()) close();
    }

    /** Find every KeyMapping currently bound to the popover's virtual key. */
    private List<KeyMapping> collectMappings() {
        List<KeyMapping> out = new ArrayList<>();
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.options == null) return out;
        boolean wantMouse = KeyboardLayoutData.isMouse(virtualKey);
        int mouseBtn = wantMouse ? KeyboardLayoutData.virtualToMouseBtn(virtualKey) : -1;
        for (KeyMapping km : mc.options.keyMappings) {
            InputConstants.Key kmKey = ((KeyMappingAccessor) (Object) km).newvisualkeybing$getKey();
            if (kmKey == InputConstants.UNKNOWN) continue;
            if (wantMouse) {
                if (kmKey.getType() == InputConstants.Type.MOUSE && kmKey.getValue() == mouseBtn) out.add(km);
            } else {
                if (kmKey.getType() != InputConstants.Type.MOUSE && kmKey.getValue() == virtualKey) out.add(km);
            }
        }
        return out;
    }

    private static void renderXButton(GuiGraphics g, int x, int y, int size,
                                      boolean hovered, int idleColor, int hoverColor) {
        var c = UITheme.colors();
        if (hovered) {
            UITheme.fillRoundedRect(g, x, y, size, size, 3,
                    UITheme.lerpColor(c.widgetBg(), hoverColor, 0.55f));
            UITheme.drawRoundedBorder(g, x, y, size, size, 3, UITheme.withAlpha(hoverColor, 0xC0));
        }
        int cx = x + size / 2;
        int cy = y + size / 2;
        int markColor = hovered ? 0xFFFFFFFF : idleColor;
        int half = Math.max(2, size / 2 - 3);
        for (int d = -half; d <= half; d++) {
            g.fill(cx + d, cy + d, cx + d + 1, cy + d + 1, markColor);
            g.fill(cx + d, cy - d, cx + d + 1, cy - d + 1, markColor);
        }
    }
}
