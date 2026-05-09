package com.github.newvisualkeybing.client.screen;

import com.github.newvisualkeybing.client.keyboard.KeyBindingScanner;
import com.github.newvisualkeybing.client.keyboard.KeyboardLayoutData;
import com.github.newvisualkeybing.client.ui.UITheme;
import com.github.newvisualkeybing.platform.services.IPlatformHelper.ConflictContext;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import java.util.Collections;
import java.util.List;

final class KeybindTooltipRenderer {

    private final KeyBindingScanner scanner;

    KeybindTooltipRenderer(KeyBindingScanner scanner) {
        this.scanner = scanner;
    }

    void render(GuiGraphics g, Font font, int screenW, int screenH, int virtualKey, int mouseX, int mouseY) {
        var c = UITheme.colors();
        boolean isWheel = KeyboardLayoutData.isWheel(virtualKey);
        boolean isMouseKey = KeyboardLayoutData.isMouse(virtualKey);
        String keyName = scanner.getVirtualKeyLabel(virtualKey);

        List<KeyBindingScanner.KeyBindingInfo> bindings;
        KeyBindingScanner.KeyStatus status;
        if (isWheel) {
            bindings = Collections.emptyList();
            status = KeyBindingScanner.KeyStatus.FREE;
        } else if (isMouseKey) {
            int btn = KeyboardLayoutData.virtualToMouseBtn(virtualKey);
            bindings = scanner.getMouseBindings(btn);
            status = scanner.getMouseStatus(btn);
        } else {
            bindings = scanner.getBindings(virtualKey);
            status = scanner.getStatus(virtualKey);
        }

        int chipW = renderStatusChip(g, font, 0, 0, status, true);
        int padX = 10;
        int padY = 8;
        int innerW = Math.max(180, font.width(keyName) + 6 + chipW);
        int maxRows = Math.min(bindings.size(), 5);
        for (int i = 0; i < maxRows; i++) {
            KeyBindingScanner.KeyBindingInfo info = bindings.get(i);
            String ctxTag = contextTag(info.conflictContext());
            int rowW = 8 + font.width(info.actionName()) + 8 + font.width(info.modName())
                    + (ctxTag.isEmpty() ? 0 : font.width(ctxTag) + 8);
            innerW = Math.max(innerW, rowW);
        }
        if (status == KeyBindingScanner.KeyStatus.CONFLICT) {
            innerW = Math.max(innerW, font.width(Component.translatable("screen.newvisualkeybing.viewer.tooltip.conflict_warning").getString()));
        }
        innerW = Math.max(innerW, font.width(Component.translatable("screen.newvisualkeybing.viewer.tooltip.click_hint").getString()));
        innerW = Math.min(innerW, 320);

        int titleH = Math.max(font.lineHeight, 12);
        int rowH = font.lineHeight + 4;
        int contentH = titleH + 6;
        if (isWheel || bindings.isEmpty()) {
            contentH += font.lineHeight + 4;
        } else {
            contentH += maxRows * rowH;
            if (bindings.size() > maxRows) contentH += font.lineHeight + 2;
        }
        if (status == KeyBindingScanner.KeyStatus.CONFLICT) contentH += font.lineHeight + 4;
        contentH += font.lineHeight + 6;

        int totalW = innerW + padX * 2;
        int totalH = contentH + padY * 2;
        int tx = Math.min(screenW - totalW - 4, mouseX + 12);
        int ty = Math.min(screenH - totalH - 4, mouseY + 12);

        UITheme.renderTooltipBackground(g, tx, ty, totalW, totalH);
        UITheme.fillRoundedRect(g, tx, ty, totalW, 2, 2, statusAccentColor(status));

        int curX = tx + padX;
        int curY = ty + padY;

        renderStatusChip(g, font, tx + totalW - padX - chipW, curY, status, false);
        g.drawString(font, fitToWidth(font, keyName, innerW - chipW - 6), curX, curY + 1, c.textPrimary(), false);
        curY += titleH + 4;

        g.fill(curX, curY, curX + innerW, curY + 1, UITheme.withAlpha(c.divider(), 0x70));
        curY += 4;

        if (isWheel) {
            g.drawString(font, Component.translatable("screen.newvisualkeybing.viewer.wheel_hint").getString(),
                    curX, curY, c.textMuted(), false);
            curY += font.lineHeight + 4;
        } else if (bindings.isEmpty()) {
            g.drawString(font, Component.translatable("screen.newvisualkeybing.viewer.unbound").getString(),
                    curX, curY, c.textMuted(), false);
            curY += font.lineHeight + 4;
        } else {
            for (int i = 0; i < maxRows; i++) {
                KeyBindingScanner.KeyBindingInfo info = bindings.get(i);
                int sideColor = info.self() ? c.accent() : UITheme.withAlpha(c.widgetBorder(), 0xB0);
                g.fill(curX, curY + 2, curX + 2, curY + rowH - 2, sideColor);

                String ctxTag = contextTag(info.conflictContext());
                String modText = info.modName();
                int modW = font.width(modText);
                int rightBlockW = (ctxTag.isEmpty() ? 0 : font.width(ctxTag) + 6) + modW + 4;
                String actionFit = fitToWidth(font, info.actionName(), innerW - 6 - rightBlockW);

                int textY = curY + (rowH - font.lineHeight) / 2;
                g.drawString(font, actionFit, curX + 6, textY,
                        info.self() ? c.accent() : c.textPrimary(), false);
                int rightX = curX + innerW - modW;
                g.drawString(font, modText, rightX, textY, c.textMuted(), false);
                if (!ctxTag.isEmpty()) {
                    int tagX = rightX - font.width(ctxTag) - 6;
                    g.drawString(font, ctxTag, tagX, textY, c.accentAlt(), false);
                }
                curY += rowH;
            }
            if (bindings.size() > maxRows) {
                g.drawString(font, "+" + (bindings.size() - maxRows), curX, curY, c.textMuted(), false);
                curY += font.lineHeight + 2;
            }
        }

        if (status == KeyBindingScanner.KeyStatus.CONFLICT) {
            String warn = Component.translatable("screen.newvisualkeybing.viewer.tooltip.conflict_warning").getString();
            g.drawString(font, warn, curX, curY, c.dangerColor(), false);
            curY += font.lineHeight + 4;
        }

        g.drawString(font, Component.translatable("screen.newvisualkeybing.viewer.tooltip.click_hint").getString(),
                curX, curY, c.textMuted(), false);
    }

    private int renderStatusChip(GuiGraphics g, Font font, int x, int y,
                                 KeyBindingScanner.KeyStatus status, boolean measureOnly) {
        var c = UITheme.colors();
        int dot = statusAccentColor(status);
        int textColor = switch (status) {
            case FREE -> c.textSecondary();
            case SELF -> c.accent();
            case OTHER_SINGLE, BOUND -> c.success();
            case CONFLICT -> c.danger();
        };
        String label = Component.translatable(statusTranslation(status)).getString();
        int chipH = 12;
        int chipW = font.width(label) + 14;
        if (measureOnly) return chipW;
        int chipFill = UITheme.lerpColor(c.widgetBg(), dot, 0.18f);
        UITheme.fillRoundedRect(g, x, y, chipW, chipH, 6, chipFill);
        UITheme.drawRoundedBorder(g, x, y, chipW, chipH, 6, UITheme.withAlpha(dot, 0xC0));
        UITheme.fillRoundedRect(g, x + 4, y + (chipH - 4) / 2, 4, 4, 2, dot);
        g.drawString(font, label, x + 10, y + (chipH - font.lineHeight) / 2 + 1, textColor, false);
        return chipW;
    }

    private static String fitToWidth(Font font, String text, int maxW) {
        if (font.width(text) <= maxW) return text;
        String ellipsis = "..";
        int eW = font.width(ellipsis);
        if (maxW <= eW) return ellipsis;
        return font.plainSubstrByWidth(text, maxW - eW) + ellipsis;
    }

    private static int statusAccentColor(KeyBindingScanner.KeyStatus status) {
        var c = UITheme.colors();
        return switch (status) {
            case FREE -> c.widgetBorder();
            case SELF -> c.accent();
            case OTHER_SINGLE, BOUND -> c.success();
            case CONFLICT -> c.danger();
        };
    }

    private static String statusTranslation(KeyBindingScanner.KeyStatus status) {
        return switch (status) {
            case FREE -> "screen.newvisualkeybing.viewer.legend.free";
            case SELF -> "screen.newvisualkeybing.viewer.legend.self";
            case OTHER_SINGLE, BOUND -> "screen.newvisualkeybing.viewer.legend.other";
            case CONFLICT -> "screen.newvisualkeybing.viewer.legend.conflict";
        };
    }

    private static String contextTag(ConflictContext ctx) {
        if (ctx == null) return "";
        return switch (ctx) {
            case UNIVERSAL -> "U";
            case IN_GAME -> "G";
            case GUI -> "UI";
            case UNKNOWN -> "?";
        };
    }
}
