package com.github.newvisualkeybing.client.ui;

import net.minecraft.client.gui.GuiGraphics;

/**
 * 现代Web风格主题系统 — 移植自 MemoryCatcher，并保留 KeybindViewerScreen 历史命名。
 * 设计语言：柔和圆角、微妙阴影层级、玻璃态、高对比但不刺眼的配色。
 */
public final class UITheme {

    public enum Mode { DARK, LIGHT }

    private static Mode currentMode = Mode.DARK;

    // ═══════════════ DARK (GitHub Dark / Vercel Dark) ═══════════════
    private static final ColorPalette DARK = new ColorPalette(
            0xF00D1117, 0xFF161B22, 0xFF21262D, 0xFF30363D, 0xFF8B949E,
            0xFF58A6FF, 0xFF79C0FF, 0xFFA5D6FF,
            0xFFF0F6FC, 0xFFC9D1D9, 0xFF8B949E,
            0xFF3FB950, 0xFFD29922, 0xFFF85149,
            0xFF0D1117, 0xFF21262D, 0xFF484F58,
            0x40000000,
            0xFF0D1117, 0xFF30363D, 0xFF58A6FF,
            0xFF1F6FEB, 0xFF8957E5,
            0xFF238636, 0xFFDA3633,
            0xE0FFFFFF, 0xFF484F58
    );

    // ═══════════════ LIGHT (GitHub Light / Vercel Light) ═══════════════
    private static final ColorPalette LIGHT = new ColorPalette(
            0xF0FFFFFF, 0xFFF6F8FA, 0xFFFFFFFF, 0xFFD0D7DE, 0xFF57606A,
            0xFF0969DA, 0xFF0550AE, 0xFF54AEFF,
            0xFF1F2328, 0xFF656D76, 0xFF8C959F,
            0xFF1A7F37, 0xFF9A6700, 0xFFCF222E,
            0xFFF6F8FA, 0xFFEAEEF2, 0xFFAFB8C1,
            0x20000000,
            0xFFFFFFFF, 0xFFD8DEE4, 0xFF0969DA,
            0xFF8250DF, 0xFFBF3989,
            0xFFDAFBE1, 0xFFFFE7E7,
            0xE0FFFFFF, 0xFFD8DEE4
    );

    private UITheme() {}

    public static void setMode(Mode mode) { currentMode = mode; }
    public static Mode getMode() { return currentMode; }
    public static ColorPalette colors() { return currentMode == Mode.DARK ? DARK : LIGHT; }

    // ═══════════════════════════════════════════════════════════
    // 绘制工具
    // ═══════════════════════════════════════════════════════════

    public static void fillRoundedRect(GuiGraphics g, int x, int y, int w, int h, int radius, int color) {
        if (radius <= 0 || w < radius * 2 || h < radius * 2) {
            g.fill(x, y, x + w, y + h, color);
            return;
        }
        g.fill(x + radius, y, x + w - radius, y + h, color);
        g.fill(x, y + radius, x + radius, y + h - radius, color);
        g.fill(x + w - radius, y + radius, x + w, y + h - radius, color);
        fillRoundedCorner(g, x, y, radius, color, true, true);
        fillRoundedCorner(g, x + w - radius, y, radius, color, false, true);
        fillRoundedCorner(g, x, y + h - radius, radius, color, true, false);
        fillRoundedCorner(g, x + w - radius, y + h - radius, radius, color, false, false);
    }

    private static void fillRoundedCorner(GuiGraphics g, int cx, int cy, int r, int color, boolean left, boolean top) {
        if (r <= 2) {
            for (int dy = 0; dy < r; dy++) {
                for (int dx = 0; dx < r; dx++) {
                    float distX = left ? (r - dx - 0.5f) : (dx + 0.5f);
                    float distY = top ? (r - dy - 0.5f) : (dy + 0.5f);
                    if (distX * distX + distY * distY <= (float) r * r) {
                        g.fill(cx + dx, cy + dy, cx + dx + 1, cy + dy + 1, color);
                    }
                }
            }
            return;
        }
        for (int dy = 0; dy < r; dy++) {
            float distY = top ? (r - dy - 0.5f) : (dy + 0.5f);
            int width = (int) Math.sqrt((float) r * r - distY * distY);
            if (left) {
                int startX = cx + (r - width);
                g.fill(startX, cy + dy, cx + r, cy + dy + 1, color);
            } else {
                int endX = cx + width;
                g.fill(cx, cy + dy, endX, cy + dy + 1, color);
            }
        }
    }

    public static void drawRoundedBorder(GuiGraphics g, int x, int y, int w, int h, int radius, int color) {
        g.fill(x + radius, y, x + w - radius, y + 1, color);
        g.fill(x + radius, y + h - 1, x + w - radius, y + h, color);
        g.fill(x, y + radius, x + 1, y + h - radius, color);
        g.fill(x + w - 1, y + radius, x + w, y + h - radius, color);
        if (radius >= 2) {
            g.fill(x + 1, y + 1, x + 2, y + 2, color);
            g.fill(x + w - 2, y + 1, x + w - 1, y + 2, color);
            g.fill(x + 1, y + h - 2, x + 2, y + h - 1, color);
            g.fill(x + w - 2, y + h - 2, x + w - 1, y + h - 1, color);
        }
    }

    public static void fillGradient(GuiGraphics g, int x, int y, int w, int h, int colorTop, int colorBottom) {
        if (h <= 0 || w <= 0) return;
        int steps = Math.min(h, 16);
        int stepH = Math.max(h / steps, 1);
        for (int i = 0; i < steps; i++) {
            float t = (float) i / Math.max(1, steps - 1);
            int color = lerpColor(colorTop, colorBottom, t);
            int sy = y + i * stepH;
            int ey = (i == steps - 1) ? y + h : sy + stepH;
            g.fill(x, sy, x + w, ey, color);
        }
    }

    public static void drawCardShadow(GuiGraphics g, int x, int y, int w, int h, int radius) {
        var c = colors();
        for (int i = 4; i >= 1; i--) {
            int alpha = Math.max(2, 8 - i * 2);
            fillRoundedRect(g, x + i, y + i, w, h, radius, withAlpha(c.shadow(), alpha));
        }
        fillRoundedRect(g, x + 2, y + 2, w, h, radius, withAlpha(c.shadow(), 0x20));
    }

    public static void drawGlassBackground(GuiGraphics g, int x, int y, int w, int h, int radius) {
        var c = colors();
        fillRoundedRect(g, x, y, w, h, radius, c.glassBg());
        fillRoundedRect(g, x, y, w, h / 2, radius, withAlpha(0xFFFFFF, 0x08));
        drawRoundedBorder(g, x, y, w, h, radius, withAlpha(c.widgetBorder(), 0x60));
    }

    /** 现代玻璃面板：阴影 + 圆角背景 + 顶部高光 + 边框。保留旧名以兼容历史调用点。 */
    public static void drawGlassPanel(GuiGraphics g, int x, int y, int w, int h, int radius) {
        var c = colors();
        drawCardShadow(g, x - 2, y - 2, w + 4, h + 4, radius + 2);
        fillRoundedRect(g, x, y, w, h, radius, c.panelBg());
        fillRoundedRect(g, x + 1, y + 1, w - 2, Math.max(3, h / 6), radius, withAlpha(0xFFFFFF, 0x10));
        drawRoundedBorder(g, x, y, w, h, radius, c.widgetBorder());
    }

    public static void drawGradientButton(GuiGraphics g, int x, int y, int w, int h, int radius,
                                          int colorTop, int colorBottom, float hoverProgress) {
        fillGradient(g, x, y, w, h, colorTop, colorBottom);
        drawRoundedBorder(g, x, y, w, h, radius, withAlpha(0xFFFFFF, 0x20));
        if (hoverProgress > 0.01f) {
            int glowAlpha = (int) (30 * hoverProgress);
            fillRoundedRect(g, x, y, w, h / 2, radius, withAlpha(0xFFFFFF, glowAlpha));
        }
    }

    public static void renderTooltipBackground(GuiGraphics g, int x, int y, int w, int h) {
        var c = colors();
        drawCardShadow(g, x - 2, y - 2, w + 4, h + 4, 6);
        fillRoundedRect(g, x, y, w, h, 6, withAlpha(c.headerBg(), 0xF5));
        drawRoundedBorder(g, x, y, w, h, 6, withAlpha(c.widgetBorderHover(), 0x80));
        fillRoundedRect(g, x, y, w, 3, 3, withAlpha(0xFFFFFF, 0x20));
    }

    public static void drawHLine(GuiGraphics g, int x, int y, int width, int color) {
        g.fill(x, y, x + width, y + 1, color);
    }

    // ═══════════════════════════════════════════════════════════
    // 缓动 & 颜色工具
    // ═══════════════════════════════════════════════════════════

    public static float easeOutCubic(float t) {
        t = Math.max(0, Math.min(1, t));
        float f = 1 - t;
        return 1 - f * f * f;
    }

    public static float easeInOutQuad(float t) {
        t = Math.max(0, Math.min(1, t));
        return t < 0.5f ? 2 * t * t : 1 - (-2 * t + 2) * (-2 * t + 2) / 2;
    }

    public static float smoothDamp(float current, float target, float speed) {
        return current + (target - current) * Math.min(1.0f, speed);
    }

    public static int lerpColor(int c1, int c2, float t) {
        t = Math.max(0, Math.min(1, t));
        int a = (int) (((c1 >> 24) & 0xFF) + (((c2 >> 24) & 0xFF) - ((c1 >> 24) & 0xFF)) * t);
        int r = (int) (((c1 >> 16) & 0xFF) + (((c2 >> 16) & 0xFF) - ((c1 >> 16) & 0xFF)) * t);
        int g = (int) (((c1 >> 8) & 0xFF) + (((c2 >> 8) & 0xFF) - ((c1 >> 8) & 0xFF)) * t);
        int b = (int) ((c1 & 0xFF) + ((c2 & 0xFF) - (c1 & 0xFF)) * t);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    public static int withAlpha(int color, int alpha) {
        return (color & 0x00FFFFFF) | (alpha << 24);
    }

    public static int brighten(int color, float factor) {
        int a = (color >> 24) & 0xFF;
        int r = Math.min(255, (int) (((color >> 16) & 0xFF) * (1 + factor)));
        int g = Math.min(255, (int) (((color >> 8) & 0xFF) * (1 + factor)));
        int b = Math.min(255, (int) ((color & 0xFF) * (1 + factor)));
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    /**
     * 统一调色板 — 同时兼容 MemoryCatcher 命名（successColor/dangerColor/glassBg ...）
     * 与本项目历史命名（success/danger/accentAlt/inputBg/divider/dimOverlay）。
     */
    public record ColorPalette(
            int panelBg,
            int headerBg,
            int widgetBg,
            int widgetBorder,
            int widgetBorderHover,
            int accent,
            int accentHover,
            int accentLight,
            int textPrimary,
            int textSecondary,
            int textMuted,
            int successColor,
            int warningColor,
            int dangerColor,
            int inputBg,
            int scrollbarTrack,
            int scrollbarThumb,
            int shadow,
            int graphBg,
            int gridLine,
            int graphLine,
            int accentSecondary,
            int accentTertiary,
            int successBg,
            int dangerBg,
            int glassBg,
            int divider
    ) {
        // ─── 历史命名桥接 ───
        public int success() { return successColor; }
        public int danger() { return dangerColor; }
        public int warning() { return warningColor; }
        public int accentAlt() { return accentLight; }
        public int dimOverlay() { return widgetBorderHover; }
    }
}

