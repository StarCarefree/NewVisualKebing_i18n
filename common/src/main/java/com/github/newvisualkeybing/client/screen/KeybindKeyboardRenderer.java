package com.github.newvisualkeybing.client.screen;

import com.github.newvisualkeybing.client.keyboard.KeyBindingScanner;
import com.github.newvisualkeybing.client.keyboard.KeyboardLayoutData;
import com.github.newvisualkeybing.client.ui.UITheme;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

import java.util.List;
import java.util.function.IntPredicate;





final class KeybindKeyboardRenderer {

    private final KeyBindingScanner scanner;

    KeybindKeyboardRenderer(KeyBindingScanner scanner) {
        this.scanner = scanner;
    }

    


    Integer render(GuiGraphics g, Font font, KeyboardLayoutData.Style style,
                   int keyboardX, int keyboardY, float keyScale,
                   Integer selectedVirtualKey, IntPredicate isVisibleKey,
                   IntPredicate isHiddenKey,
                   int mouseX, int mouseY, float animTick) {
        var c = UITheme.colors();
        renderChassis(g, style, keyboardX, keyboardY, keyScale);

        Integer hovered = null;
        for (KeyboardLayoutData.KeyDef key : KeyboardLayoutData.getKeys(style)) {
            int x = key.screenX(keyboardX, keyScale);
            int y = key.screenY(keyboardY, keyScale);
            int w = key.screenW(keyScale);
            int h = key.screenH(keyScale);

            boolean matched = isVisibleKey.test(key.glfwKey());
            boolean hidden = isHiddenKey.test(key.glfwKey());
            boolean hover = KeybindViewerScreen.inside(mouseX, mouseY, x, y, w, h);
            boolean selected = selectedVirtualKey != null && selectedVirtualKey == key.glfwKey();
            if (hidden) hover = false;
            if (hover) hovered = key.glfwKey();

            KeyBindingScanner.KeyStatus status = scanner.getStatus(key.glfwKey());
            int radius = w >= 34 || h >= 34 ? 4 : 3;
            int bindingCount = scanner.getBindingCount(key.glfwKey());

            if (matched && !hidden) {
                int haloColor = selected ? UITheme.withAlpha(KeybindViewerScreen.pulseAccent(animTick), 0x70)
                        : hover ? UITheme.withAlpha(c.accentAlt(), 0x55) : 0;
                if (haloColor != 0) {
                    UITheme.fillRoundedRect(g, x - 1, y - 1, w + 2, h + 1, radius + 1, haloColor);
                }

                int topFill = applyZoneTint(KeybindViewerScreen.keyStatusColor(status), key.glfwKey(), status);
                int faceH = h - 1;

                int shadow = UITheme.lerpColor(topFill, 0x000000, 0.50f);
                UITheme.fillRoundedRect(g, x, y + 1, w, h, radius, shadow);

                UITheme.fillRoundedRect(g, x, y, w, faceH, radius, topFill);

                int half = faceH / 2;
                int hlAlpha = hover ? 0x18 : 0x10;
                g.fill(x + 1, y + 1, x + w - 1, y + half, UITheme.withAlpha(0xFFFFFF, hlAlpha));
                g.fill(x + 1, y + faceH - 1, x + w - 1, y + faceH, UITheme.withAlpha(0x000000, 0x18));
                g.fill(x + radius, y, x + w - radius, y + 1, UITheme.withAlpha(0xFFFFFF, 0x30));
                renderStatusEdge(g, x, y, w, faceH, radius, status, hover || selected);

                int borderColor = selected ? KeybindViewerScreen.pulseAccent(animTick)
                        : hover ? c.accentAlt()
                        : status == KeyBindingScanner.KeyStatus.CONFLICT
                            ? UITheme.lerpColor(c.danger(), c.widgetBorder(), 0.30f)
                            : c.widgetBorder();
                UITheme.drawRoundedBorder(g, x, y, w, faceH, radius, borderColor);

                renderKeyLabels(g, font, key, x, y, w, h, KeybindViewerScreen.labelColorForStatus(status), true);
                renderInlineBindings(g, font, key.glfwKey(), x, y, w, h);
                renderBindingBadge(g, font, x, y, w, h, bindingCount, status);
            } else {
                int ghostFill = KeybindViewerScreen.keyStatusColor(status, false);
                if (hidden) ghostFill = UITheme.withAlpha(c.widgetBg(), 0x24);
                UITheme.fillRoundedRect(g, x, y, w, h - 1, radius, ghostFill);
                if (!hidden && status != KeyBindingScanner.KeyStatus.FREE) {
                    renderStatusEdge(g, x, y, w, h - 1, radius, status, false);
                }
                UITheme.drawRoundedBorder(g, x, y, w, h - 1, radius,
                        UITheme.withAlpha(c.widgetBorder(), hidden ? 0x28 : 0x60));
                if (!hidden) {
                    renderKeyLabels(g, font, key, x, y, w, h,
                            UITheme.withAlpha(c.textMuted(), 0x80), false);
                }
            }
        }
        return hovered;
    }

    
    private static int applyZoneTint(int base, int glfwKey, KeyBindingScanner.KeyStatus status) {
        if (status != KeyBindingScanner.KeyStatus.FREE) return base;
        var c = UITheme.colors();
        KeyboardLayoutData.KeyZone zone = KeyboardLayoutData.zoneOf(glfwKey);
        return switch (zone) {
            case ALPHA -> base;
            case MODIFIER -> UITheme.lerpColor(base, c.panelBg(), 0.22f);
            case FUNCTION -> UITheme.lerpColor(base, c.panelBg(), 0.18f);
            case EDIT -> UITheme.lerpColor(base, c.accentSecondary(), 0.10f);
            case ARROW -> UITheme.lerpColor(base, c.accentSecondary(), 0.14f);
            case NUMPAD -> UITheme.lerpColor(base, c.accentTertiary(), 0.10f);
        };
    }

    private static void renderStatusEdge(GuiGraphics g, int x, int y, int w, int h, int radius,
                                         KeyBindingScanner.KeyStatus status, boolean active) {
        if (w < 10 || h < 8) return;
        int color = KeybindViewerScreen.keyStatusColor(status);
        int alpha = active ? 0xD0 : 0x86;
        int edgeH = status == KeyBindingScanner.KeyStatus.CONFLICT ? 3 : 2;
        UITheme.fillRoundedRect(g, x + 2, y + h - edgeH - 1, w - 4, edgeH, Math.max(1, radius - 1),
                UITheme.withAlpha(color, alpha));
        if (status == KeyBindingScanner.KeyStatus.CONFLICT && w >= 18) {
            int sx = x + 4;
            while (sx < x + w - 4) {
                g.fill(sx, y + 2, Math.min(sx + 3, x + w - 4), y + 3, UITheme.withAlpha(color, 0xB0));
                sx += 7;
            }
        }
    }

    
    private static void renderChassis(GuiGraphics g, KeyboardLayoutData.Style style, int keyboardX, int keyboardY, float keyScale) {
        var c = UITheme.colors();
        int kbW = KeyboardLayoutData.totalWidthPx(style, keyScale);
        int kbH = KeyboardLayoutData.totalHeightPx(style, keyScale);
        int pad = Math.max(6, Math.round(keyScale * 0.30f));
        int x = keyboardX - pad;
        int y = keyboardY - pad;
        int w = kbW + pad * 2;
        int h = kbH + pad * 2;
        int radius = 8;

        UITheme.fillRoundedRect(g, x + 2, y + 3, w, h, radius + 1,
                UITheme.withAlpha(0x000000, 0x40));

        int chassisOuter = UITheme.lerpColor(c.panelBg(), 0x000000, 0.35f);
        UITheme.fillRoundedRect(g, x, y, w, h, radius, chassisOuter);

        int trayColor = UITheme.lerpColor(c.headerBg(), 0x000000, 0.20f);
        UITheme.fillRoundedRect(g, x + 2, y + 2, w - 4, h - 4, radius - 2, trayColor);

        g.fill(x + 4, y + 2, x + w - 4, y + 3, UITheme.withAlpha(0x000000, 0x60));
        g.fill(x + 2, y + 4, x + 3, y + h - 4, UITheme.withAlpha(0x000000, 0x40));

        g.fill(x + radius, y, x + w - radius, y + 1, UITheme.withAlpha(0xFFFFFF, 0x18));

        UITheme.drawRoundedBorder(g, x, y, w, h, radius,
                UITheme.withAlpha(c.widgetBorder(), 0xC0));
    }

    
    private static void renderKeyLabels(GuiGraphics g, Font font, KeyboardLayoutData.KeyDef key,
                                        int x, int y, int w, int h, int labelColor, boolean showSub) {
        var c = UITheme.colors();
        String main = KeybindViewerScreen.fitToWidth(font, key.label(), Math.max(0, w - 4));
        String sub = key.subLabel() == null ? null : KeybindViewerScreen.fitToWidth(font, key.subLabel(), Math.max(0, w - 4));
        if (showSub && sub != null && h >= 18) {
            int subColor = UITheme.withAlpha(c.textSecondary(), 0xB0);
            g.drawString(font, sub, x + (w - font.width(sub)) / 2, y + 3, subColor, false);
            g.drawString(font, main, x + (w - font.width(main)) / 2,
                    y + h - font.lineHeight - 4, labelColor, false);
        } else {
            g.drawString(font, main,
                    x + (w - font.width(main)) / 2,
                    y + (h - font.lineHeight) / 2,
                    labelColor, false);
        }
    }

    private void renderInlineBindings(GuiGraphics g, Font font, int glfwKey, int x, int y, int w, int h) {
        if (w < 34 || h < 24) return;
        List<KeyBindingScanner.KeyBindingInfo> bindings = scanner.getBindings(glfwKey);
        if (bindings.isEmpty()) return;
        var c = UITheme.colors();
        String text = bindings.get(0).modName();
        int maxW = w - 8;
        text = KeybindViewerScreen.fitToWidth(font, text, maxW);
        int textW = font.width(text);
        int chipW = textW + 6;
        int chipX = x + (w - chipW) / 2;
        int chipY = y + h - font.lineHeight - 3;
        int fill = UITheme.lerpColor(c.widgetBg(), c.accent(), 0.20f);
        UITheme.fillRoundedRect(g, chipX, chipY, chipW, font.lineHeight + 1, 4, UITheme.withAlpha(fill, 0xCC));
        g.drawString(font, text, chipX + 3, chipY + 1, c.textPrimary(), false);
    }

    
    private static void renderBindingBadge(GuiGraphics g, Font font, int x, int y, int w, int h,
                                           int count, KeyBindingScanner.KeyStatus status) {
        if (count <= 0 || h < 14) return;
        var c = UITheme.colors();
        if (count >= 2) {
            String s = String.valueOf(count);
            int bw = font.width(s) + 6;
            int bh = font.lineHeight;
            int bx = x + w - bw - 2;
            int by = y + 2;
            int chipColor = status == KeyBindingScanner.KeyStatus.CONFLICT ? c.danger() : c.accent();
            UITheme.fillRoundedRect(g, bx, by, bw, bh, bh / 2, chipColor);
            UITheme.drawRoundedBorder(g, bx, by, bw, bh, bh / 2, UITheme.withAlpha(0xFFFFFF, 0x40));
            g.drawString(font, s, bx + 3, by + 1, 0xFFFFFFFF, false);
        } else if (w >= 16) {
            int dotColor = status == KeyBindingScanner.KeyStatus.SELF ? c.accent() : c.success();
            UITheme.fillRoundedRect(g, x + w - 5, y + 3, 3, 3, 1, dotColor);
        }
    }
}
