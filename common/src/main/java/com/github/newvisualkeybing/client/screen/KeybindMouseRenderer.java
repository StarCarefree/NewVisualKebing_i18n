package com.github.newvisualkeybing.client.screen;

import com.github.newvisualkeybing.client.keyboard.KeyBindingScanner;
import com.github.newvisualkeybing.client.keyboard.KeyboardLayoutData;
import com.github.newvisualkeybing.client.ui.UITheme;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

import java.util.function.IntPredicate;

/**
 * 鼠标面板：渲染机身 / 滚轮槽 / 主侧键，并提供基于上一次 render 几何的命中测试。
 */
final class KeybindMouseRenderer {

    static final int MOUSE_BODY_W = 80;
    static final int MOUSE_BODY_H = 116;
    static final int MOUSE_TOP_R = 26;
    static final int MOUSE_BOT_R = 14;
    static final int MOUSE_TOP_AREA_H = 50;
    static final int WHEEL_COL_W = 14;
    static final int WHEEL_TICK_H = 10;
    static final int SIDE_W = 14;
    static final int SIDE_H = 18;
    static final int SIDE_GAP = 3;
    static final int SIDE_OFFSET = 3;

    private static final int PANEL_PAD = 10;
    private static final int PANEL_CONTENT_TOP = 28;

    private final KeyBindingScanner scanner;

    private int panelX;
    private int panelY;
    private int panelW;
    private int panelH;

    KeybindMouseRenderer(KeyBindingScanner scanner) {
        this.scanner = scanner;
    }

    /**
     * @return 当前帧鼠标悬停的虚拟键，若未命中返回 {@code null}。
     */
    Integer render(GuiGraphics g, Font font, int x, int y, int w, int h,
                   Integer selectedVirtualKey, IntPredicate isVisibleKey,
                   int mouseX, int mouseY, float animTick) {
        var c = UITheme.colors();
        this.panelX = x;
        this.panelY = y;
        this.panelW = w;
        this.panelH = h;

        KeybindViewerScreen.paintPanelBase(g, font, x, y, w, h,
                Component.translatable("screen.newvisualkeybing.viewer.mouse").getString());

        int innerTop = y + PANEL_CONTENT_TOP;
        int innerBottom = y + h - PANEL_PAD;
        int availH = innerBottom - innerTop;

        float mouseScale = Mth.clamp(Math.min(
                (w - PANEL_PAD * 2 - SIDE_W * 2 - SIDE_OFFSET * 2) / (float) MOUSE_BODY_W,
                availH / (float) MOUSE_BODY_H), 0.78f, 1.0f);
        int bodyW = Math.round(MOUSE_BODY_W * mouseScale);
        int bodyH = Math.round(MOUSE_BODY_H * mouseScale);
        int bodyX = x + (w - bodyW) / 2;
        int bodyY = innerTop + (availH - bodyH) / 2;
        int rTop = Math.round(MOUSE_TOP_R * mouseScale);
        int rBot = Math.round(MOUSE_BOT_R * mouseScale);

        int shadow = UITheme.withAlpha(0x000000, 0x40);
        UITheme.fillRoundedRectEx(g, bodyX - 1, bodyY + 3, bodyW + 2, bodyH,
                rTop + 1, rTop + 1, rBot + 1, rBot + 1, shadow);

        int frameFill = UITheme.lerpColor(c.widgetBg(), c.panelBg(), 0.42f);
        UITheme.fillRoundedRectEx(g, bodyX, bodyY, bodyW, bodyH,
                rTop, rTop, rBot, rBot, frameFill);

        int hlH = Math.max(4, Math.round(MOUSE_TOP_AREA_H * mouseScale * 0.7f));
        UITheme.fillRoundedRectEx(g, bodyX + 2, bodyY + 2, bodyW - 4, hlH,
                rTop - 2, rTop - 2, 2, 2, UITheme.withAlpha(0xFFFFFF, 0x10));

        int splitY = bodyY + Math.round(MOUSE_TOP_AREA_H * mouseScale);
        g.fill(bodyX + 8, splitY, bodyX + bodyW - 8, splitY + 1,
                UITheme.withAlpha(c.divider(), 0xC0));
        g.fill(bodyX + 8, splitY + 1, bodyX + bodyW - 8, splitY + 2,
                UITheme.withAlpha(0x000000, 0x20));

        int leftW = (bodyW - WHEEL_COL_W) / 2;
        int wheelX = bodyX + leftW;
        int wheelDepthColor = UITheme.lerpColor(frameFill, 0x000000, 0.45f);
        UITheme.fillRoundedRect(g, wheelX, bodyY + 1, WHEEL_COL_W,
                Math.round(MOUSE_TOP_AREA_H * mouseScale) - 1, 4, wheelDepthColor);

        UITheme.drawRoundedBorderEx(g, bodyX, bodyY, bodyW, bodyH,
                rTop, rTop, rBot, rBot,
                UITheme.withAlpha(c.widgetBorder(), 0xD0));

        Integer hovered = null;
        for (int i = 0; i < KeyboardLayoutData.MOUSE_KEYS.size(); i++) {
            Rect b = boundsAt(i);
            if (b == null) continue;
            KeyboardLayoutData.KeyDef key = KeyboardLayoutData.MOUSE_KEYS.get(i);
            boolean wheel = KeyboardLayoutData.isWheel(key.glfwKey());
            int mouseButton = wheel ? -1 : KeyboardLayoutData.virtualToMouseBtn(key.glfwKey());
            boolean matched = isVisibleKey.test(key.glfwKey());
            boolean hover = KeybindViewerScreen.inside(mouseX, mouseY, b.x, b.y, b.w, b.h);
            boolean selected = selectedVirtualKey != null && selectedVirtualKey == key.glfwKey();
            if (hover) hovered = key.glfwKey();

            KeyBindingScanner.KeyStatus status = wheel
                    ? KeyBindingScanner.KeyStatus.FREE
                    : scanner.getMouseStatus(mouseButton);
            int radius = Math.max(3, Math.min(6, Math.min(b.w, b.h) / 4));

            if (matched && (hover || selected)) {
                int halo = UITheme.withAlpha(selected ? KeybindViewerScreen.pulseAccent(animTick) : c.accentAlt(),
                        selected ? 0x70 : 0x50);
                UITheme.fillRoundedRect(g, b.x - 1, b.y - 1, b.w + 2, b.h + 2, radius + 1, halo);
            }

            int fill = KeybindViewerScreen.keyStatusColor(status, matched);
            UITheme.fillRoundedRect(g, b.x, b.y, b.w, b.h, radius, fill);
            UITheme.drawRoundedBorder(g, b.x, b.y, b.w, b.h, radius,
                    selected ? KeybindViewerScreen.pulseAccent(animTick)
                            : hover ? c.accentAlt()
                            : matched ? c.widgetBorder()
                            : UITheme.withAlpha(c.widgetBorder(), 0x60));

            if (b.w >= 14 && b.h >= 10) {
                int textColor = matched ? KeybindViewerScreen.labelColorForStatus(status)
                        : UITheme.withAlpha(c.textMuted(), 0x80);
                String label = key.label();
                g.drawString(font, label,
                        b.x + (b.w - font.width(label)) / 2,
                        b.y + (b.h - font.lineHeight) / 2,
                        textColor, false);
            }
        }
        return hovered;
    }

    /**
     * 命中测试：用上一次 render 时缓存的几何，把 (mx,my) 映射到具体的鼠标虚拟键，未命中返回 {@code null}。
     */
    Integer hitTest(double mx, double my) {
        for (int i = 0; i < KeyboardLayoutData.MOUSE_KEYS.size(); i++) {
            Rect b = boundsAt(i);
            if (b != null && KeybindViewerScreen.inside(mx, my, b.x, b.y, b.w, b.h)) {
                return KeyboardLayoutData.MOUSE_KEYS.get(i).glfwKey();
            }
        }
        return null;
    }

    private float computeMouseScale() {
        int innerTop = panelY + PANEL_CONTENT_TOP;
        int innerBottom = panelY + panelH - PANEL_PAD;
        int availH = innerBottom - innerTop;
        return Mth.clamp(Math.min(
                (panelW - PANEL_PAD * 2 - SIDE_W * 2 - SIDE_OFFSET * 2) / (float) MOUSE_BODY_W,
                availH / (float) MOUSE_BODY_H), 0.78f, 1.0f);
    }

    private Rect boundsAt(int index) {
        int innerTop = panelY + PANEL_CONTENT_TOP;
        int innerBottom = panelY + panelH - PANEL_PAD;
        int availH = innerBottom - innerTop;
        float ms = computeMouseScale();
        int bodyW = Math.round(MOUSE_BODY_W * ms);
        int bodyH = Math.round(MOUSE_BODY_H * ms);
        int bodyX = panelX + (panelW - bodyW) / 2;
        int bodyY = innerTop + (availH - bodyH) / 2;

        int leftW = (bodyW - WHEEL_COL_W) / 2;
        int rightW = bodyW - leftW - WHEEL_COL_W;
        int topH = Math.round(MOUSE_TOP_AREA_H * ms);
        int wheelTickH = WHEEL_TICK_H;
        int mmbH = topH - wheelTickH * 2;

        int wheelX = bodyX + leftW;
        int wheelY = bodyY;

        int leftSideX = bodyX - SIDE_W - SIDE_OFFSET;
        int leftStackH = SIDE_H * 2 + SIDE_GAP;
        int leftSideY = bodyY + (bodyH - leftStackH) / 2;

        int rightSideX = bodyX + bodyW + SIDE_OFFSET;
        int rightStackH = SIDE_H * 3 + SIDE_GAP * 2;
        int rightSideY = bodyY + (bodyH - rightStackH) / 2;

        int rTop = Math.round(MOUSE_TOP_R * ms);
        int topInset = Math.max(2, rTop / 4);

        return switch (index) {
            case 0 -> new Rect(bodyX + topInset, bodyY + 2, leftW - topInset - 1, topH - 4);
            case 1 -> new Rect(wheelX + 1, wheelY + wheelTickH + 1, WHEEL_COL_W - 2, mmbH - 2);
            case 2 -> new Rect(bodyX + leftW + WHEEL_COL_W + 1, bodyY + 2,
                              rightW - topInset - 1, topH - 4);
            case 3 -> new Rect(leftSideX, leftSideY, SIDE_W, SIDE_H);
            case 4 -> new Rect(leftSideX, leftSideY + SIDE_H + SIDE_GAP, SIDE_W, SIDE_H);
            case 5 -> new Rect(rightSideX, rightSideY, SIDE_W, SIDE_H);
            case 6 -> new Rect(rightSideX, rightSideY + SIDE_H + SIDE_GAP, SIDE_W, SIDE_H);
            case 7 -> new Rect(rightSideX, rightSideY + (SIDE_H + SIDE_GAP) * 2, SIDE_W, SIDE_H);
            case 8 -> new Rect(wheelX + 1, wheelY + 1, WHEEL_COL_W - 2, wheelTickH - 2);
            case 9 -> new Rect(wheelX + 1, wheelY + wheelTickH + mmbH + 1, WHEEL_COL_W - 2, wheelTickH - 2);
            default -> null;
        };
    }

    private record Rect(int x, int y, int w, int h) {}
}
