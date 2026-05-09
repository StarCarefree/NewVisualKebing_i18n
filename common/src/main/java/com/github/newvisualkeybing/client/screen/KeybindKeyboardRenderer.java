package com.github.newvisualkeybing.client.screen;

import com.github.newvisualkeybing.client.keyboard.KeyBindingScanner;
import com.github.newvisualkeybing.client.keyboard.KeyboardLayoutData;
import com.github.newvisualkeybing.client.ui.UITheme;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

import java.util.List;
import java.util.function.IntPredicate;

/**
 * 渲染整块键盘（机箱 + 每个按键 + 标签 + 绑定计数 badge）。
 * 从 KeybindViewerScreen 拆出，保留同样的视觉规则与 zone 染色逻辑。
 */
final class KeybindKeyboardRenderer {

    private final KeyBindingScanner scanner;

    KeybindKeyboardRenderer(KeyBindingScanner scanner) {
        this.scanner = scanner;
    }

    /**
     * @return 当前帧鼠标悬停的虚拟键，若无则返回 {@code null}。
     */
    Integer render(GuiGraphics g, Font font, KeyboardLayoutData.Style style,
                   int keyboardX, int keyboardY, float keyScale,
                   Integer selectedVirtualKey, IntPredicate isVisibleKey,
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
            boolean hover = KeybindViewerScreen.inside(mouseX, mouseY, x, y, w, h);
            boolean selected = selectedVirtualKey != null && selectedVirtualKey == key.glfwKey();
            if (hover) hovered = key.glfwKey();

            KeyBindingScanner.KeyStatus status = scanner.getStatus(key.glfwKey());
            int radius = w >= 34 || h >= 34 ? 4 : 3;
            List<KeyBindingScanner.KeyBindingInfo> bindings = scanner.getBindings(key.glfwKey());

            if (matched) {
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

                int borderColor = selected ? KeybindViewerScreen.pulseAccent(animTick)
                        : hover ? c.accentAlt()
                        : status == KeyBindingScanner.KeyStatus.CONFLICT
                            ? UITheme.lerpColor(c.danger(), c.widgetBorder(), 0.30f)
                            : c.widgetBorder();
                UITheme.drawRoundedBorder(g, x, y, w, faceH, radius, borderColor);

                renderKeyLabels(g, font, key, x, y, w, h, KeybindViewerScreen.labelColorForStatus(status), true);
                renderBindingBadge(g, font, x, y, w, h, bindings.size(), status);
            } else {
                int ghostFill = KeybindViewerScreen.keyStatusColor(status, false);
                UITheme.fillRoundedRect(g, x, y, w, h - 1, radius, ghostFill);
                UITheme.drawRoundedBorder(g, x, y, w, h - 1, radius,
                        UITheme.withAlpha(c.widgetBorder(), 0x60));
                renderKeyLabels(g, font, key, x, y, w, h,
                        UITheme.withAlpha(c.textMuted(), 0x80), false);
            }
        }
        return hovered;
    }

    /** FREE 状态下按 zone 给微弱差异色，让分区视觉更分明。 */
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

    /** 键盘机箱：投影 + 外壳 + 内托盘 + 顶部高光。 */
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

    /** 主标签居中；副标签（Shift 字符）显示在键的上沿。 */
    private static void renderKeyLabels(GuiGraphics g, Font font, KeyboardLayoutData.KeyDef key,
                                        int x, int y, int w, int h, int labelColor, boolean showSub) {
        var c = UITheme.colors();
        String main = key.label();
        String sub = key.subLabel();
        if (showSub && sub != null && h >= 18) {
            int subColor = UITheme.withAlpha(c.textSecondary(), 0xB0);
            g.drawString(font, sub, x + (w - font.width(sub)) / 2, y + 2, subColor, false);
            g.drawString(font, main, x + (w - font.width(main)) / 2,
                    y + h - 2 - font.lineHeight, labelColor, false);
        } else {
            g.drawString(font, main,
                    x + (w - font.width(main)) / 2,
                    y + (h - font.lineHeight) / 2,
                    labelColor, false);
        }
    }

    /** 绑定数量 badge：≥2 用 pill 高亮，=1 在右上角画一个小圆点。 */
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
