package com.github.newvisualkeybing.client.ui;

import net.minecraft.client.gui.GuiGraphics;





public final class UITheme {

    public enum Mode { DARK, LIGHT }

    private static Mode currentMode = Mode.DARK;

    // Linear/Vercel 风格暗色板：更深更中性的背景、稍冷稍饱和的 accent、对比度更高。
    private static final ColorPalette DARK = new ColorPalette(
            // panelBg, headerBg, widgetBg, widgetBorder, widgetBorderHover
            0xF008090C, 0xFF111317, 0xFF1A1D22, 0xFF2A2D33, 0xFF7A7E87,
            // accent, accentHover, accentLight
            0xFF4A7BFF, 0xFF6B95FF, 0xFF9DBAFF,
            // textPrimary, textSecondary, textMuted
            0xFFF5F6F7, 0xFFC2C6CC, 0xFF7B8089,
            // successColor, warningColor, dangerColor
            0xFF3DD68C, 0xFFE5A33A, 0xFFFF5C5C,
            // inputBg, scrollbarTrack, scrollbarThumb
            0xFF0A0C0F, 0xFF1A1D22, 0xFF3F434A,
            // shadow
            0x60000000,
            // graphBg, gridLine, graphLine
            0xFF08090C, 0xFF2A2D33, 0xFF4A7BFF,
            // accentSecondary, accentTertiary
            0xFF3457D5, 0xFF7E5BD9,
            // successBg, dangerBg
            0xFF1B7A4A, 0xFFC53737,
            // glassBg, divider
            0xE0FFFFFF, 0xFF2A2D33
    );

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

    /**
     * 用源色的 alpha 通道乘以覆盖率，得到反走样像素的最终颜色。
     */
    private static int scaleAlpha(int color, float coverage) {
        if (coverage <= 0f) return 0;
        if (coverage >= 1f) return color;
        int baseAlpha = (color >>> 24) & 0xFF;
        int newAlpha = Math.round(baseAlpha * coverage);
        if (newAlpha <= 0) return 0;
        return (newAlpha << 24) | (color & 0x00FFFFFF);
    }

    /**
     * 反走样圆角填充：对每个像素先用其中心快速判断是否落在 r-1.5 内（铁定全覆盖），
     * 边界附近的像素用 4×4 supersampling 算 0..16 级覆盖再 alpha 缩放。
     * 半径 ≤ 1 时退化为单像素点。
     */
    private static void fillRoundedCorner(GuiGraphics g, int cx, int cy, int r, int color, boolean left, boolean top) {
        if (r <= 0) return;
        if (r == 1) {
            g.fill(cx, cy, cx + 1, cy + 1, color);
            return;
        }
        float r2 = (float) r * r;
        float rInner = Math.max(0f, r - 1.5f);
        float rInner2 = rInner * rInner;
        for (int dy = 0; dy < r; dy++) {
            for (int dx = 0; dx < r; dx++) {
                float pxC = dx + 0.5f;
                float pyC = dy + 0.5f;
                float dXc = left ? (r - pxC) : pxC;
                float dYc = top ? (r - pyC) : pyC;
                float dCenter2 = dXc * dXc + dYc * dYc;
                if (dCenter2 <= rInner2) {
                    g.fill(cx + dx, cy + dy, cx + dx + 1, cy + dy + 1, color);
                    continue;
                }
                if (dCenter2 > (r + 1.5f) * (r + 1.5f)) continue;
                int hits = 0;
                for (int sy = 0; sy < 4; sy++) {
                    for (int sx = 0; sx < 4; sx++) {
                        float fx = dx + (sx + 0.5f) / 4f;
                        float fy = dy + (sy + 0.5f) / 4f;
                        float dXs = left ? (r - fx) : fx;
                        float dYs = top ? (r - fy) : fy;
                        if (dXs * dXs + dYs * dYs <= r2) hits++;
                    }
                }
                if (hits == 0) continue;
                int finalColor = hits == 16 ? color : scaleAlpha(color, hits / 16f);
                g.fill(cx + dx, cy + dy, cx + dx + 1, cy + dy + 1, finalColor);
            }
        }
    }

    public static void drawRoundedBorder(GuiGraphics g, int x, int y, int w, int h, int radius, int color) {
        if (radius <= 0) {
            g.fill(x, y, x + w, y + 1, color);
            g.fill(x, y + h - 1, x + w, y + h, color);
            g.fill(x, y + 1, x + 1, y + h - 1, color);
            g.fill(x + w - 1, y + 1, x + w, y + h - 1, color);
            return;
        }
        g.fill(x + radius, y, x + w - radius, y + 1, color);
        g.fill(x + radius, y + h - 1, x + w - radius, y + h, color);
        g.fill(x, y + radius, x + 1, y + h - radius, color);
        g.fill(x + w - 1, y + radius, x + w, y + h - radius, color);
        drawCornerArc(g, x, y, radius, color, true, true);
        drawCornerArc(g, x + w - radius, y, radius, color, false, true);
        drawCornerArc(g, x, y + h - radius, radius, color, true, false);
        drawCornerArc(g, x + w - radius, y + h - radius, radius, color, false, false);
    }

    
    public static void fillRoundedRectEx(GuiGraphics g, int x, int y, int w, int h,
                                         int rTL, int rTR, int rBR, int rBL, int color) {
        rTL = Math.min(rTL, Math.min(w / 2, h / 2));
        rTR = Math.min(rTR, Math.min(w / 2, h / 2));
        rBR = Math.min(rBR, Math.min(w / 2, h / 2));
        rBL = Math.min(rBL, Math.min(w / 2, h / 2));

        int topMax = Math.max(rTL, rTR);
        int botMax = Math.max(rBL, rBR);

        
        if (h - topMax - botMax > 0) {
            g.fill(x, y + topMax, x + w, y + h - botMax, color);
        }

        
        g.fill(x + rTL, y, x + w - rTR, y + topMax, color);
        if (rTL < topMax) g.fill(x, y + rTL, x + rTL, y + topMax, color);
        if (rTR < topMax) g.fill(x + w - rTR, y + rTR, x + w, y + topMax, color);

        
        g.fill(x + rBL, y + h - botMax, x + w - rBR, y + h, color);
        if (rBL < botMax) g.fill(x, y + h - botMax, x + rBL, y + h - rBL, color);
        if (rBR < botMax) g.fill(x + w - rBR, y + h - botMax, x + w, y + h - rBR, color);

        
        if (rTL > 0) fillRoundedCorner(g, x, y, rTL, color, true, true);
        if (rTR > 0) fillRoundedCorner(g, x + w - rTR, y, rTR, color, false, true);
        if (rBL > 0) fillRoundedCorner(g, x, y + h - rBL, rBL, color, true, false);
        if (rBR > 0) fillRoundedCorner(g, x + w - rBR, y + h - rBR, rBR, color, false, false);
    }

    
    public static void drawRoundedBorderEx(GuiGraphics g, int x, int y, int w, int h,
                                           int rTL, int rTR, int rBR, int rBL, int color) {
        rTL = Math.min(rTL, Math.min(w / 2, h / 2));
        rTR = Math.min(rTR, Math.min(w / 2, h / 2));
        rBR = Math.min(rBR, Math.min(w / 2, h / 2));
        rBL = Math.min(rBL, Math.min(w / 2, h / 2));

        
        g.fill(x + rTL, y, x + w - rTR, y + 1, color);
        g.fill(x + rBL, y + h - 1, x + w - rBR, y + h, color);
        
        int leftStart = Math.max(rTL, 0);
        int leftEnd = h - Math.max(rBL, 0);
        g.fill(x, y + leftStart, x + 1, y + leftEnd, color);
        int rightStart = Math.max(rTR, 0);
        int rightEnd = h - Math.max(rBR, 0);
        g.fill(x + w - 1, y + rightStart, x + w, y + rightEnd, color);

        
        drawCornerArc(g, x, y, rTL, color, true, true);
        drawCornerArc(g, x + w - rTR, y, rTR, color, false, true);
        drawCornerArc(g, x, y + h - rBL, rBL, color, true, false);
        drawCornerArc(g, x + w - rBR, y + h - rBR, rBR, color, false, false);
    }

    
    /**
     * 反走样 1px 弧线：对每个像素 4×4 supersampling，
     * 落在外圆内 (d ≤ r) 且不在内圆内 (d ≥ r-1) 的子样计入覆盖率。
     */
    private static void drawCornerArc(GuiGraphics g, int cx, int cy, int r, int color,
                                      boolean left, boolean top) {
        if (r <= 0) return;
        if (r == 1) {
            g.fill(cx, cy, cx + 1, cy + 1, color);
            return;
        }
        float rOuter2 = (float) r * r;
        float rInner = r - 1f;
        float rInner2 = rInner * rInner;
        for (int dy = 0; dy < r; dy++) {
            for (int dx = 0; dx < r; dx++) {
                int hits = 0;
                for (int sy = 0; sy < 4; sy++) {
                    for (int sx = 0; sx < 4; sx++) {
                        float fx = dx + (sx + 0.5f) / 4f;
                        float fy = dy + (sy + 0.5f) / 4f;
                        float dXs = left ? (r - fx) : fx;
                        float dYs = top ? (r - fy) : fy;
                        float d2 = dXs * dXs + dYs * dYs;
                        if (d2 <= rOuter2 && d2 >= rInner2) hits++;
                    }
                }
                if (hits == 0) continue;
                int finalColor = hits == 16 ? color : scaleAlpha(color, hits / 16f);
                g.fill(cx + dx, cy + dy, cx + dx + 1, cy + dy + 1, finalColor);
            }
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
        public int success() { return successColor; }
        public int danger() { return dangerColor; }
        public int warning() { return warningColor; }
        public int accentAlt() { return accentLight; }
        public int dimOverlay() { return widgetBorderHover; }
    }
}

