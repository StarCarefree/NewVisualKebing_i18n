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
        int maxInnerW = Math.max(140, Math.min(340, screenW - 32));
        int innerW = Math.min(maxInnerW, Math.max(190, font.width(keyName) + 8 + chipW));
        String inputType = inputTypeName(virtualKey);
        String statusLine = Component.translatable("screen.newvisualkeybing.viewer.tooltip.status_line",
                inputType, contextCountText(bindings)).getString();
        innerW = Math.min(maxInnerW, Math.max(innerW, font.width(statusLine)));
        int maxRows = Math.min(bindings.size(), 4);
        for (int i = 0; i < maxRows; i++) {
            KeyBindingScanner.KeyBindingInfo info = bindings.get(i);
            String ctxTag = contextTag(info.conflictContext());
            int rowW = 8 + font.width(info.actionName()) + 8 + font.width(info.modName())
                    + (ctxTag.isEmpty() ? 0 : font.width(ctxTag) + 8);
            rowW = Math.max(rowW, 8 + font.width(info.categoryName() + " | " + contextName(info.conflictContext())));
            rowW = Math.max(rowW, 8 + font.width(info.translationKey() + " | "
                    + Component.translatable("screen.newvisualkeybing.viewer.tooltip.default_key",
                    info.defaultKeyName()).getString()));
            innerW = Math.min(maxInnerW, Math.max(innerW, rowW));
        }
        if (status == KeyBindingScanner.KeyStatus.CONFLICT) {
            innerW = Math.min(maxInnerW, Math.max(innerW, font.width(Component.translatable("screen.newvisualkeybing.viewer.tooltip.conflict_warning").getString())));
        }
        innerW = Math.min(maxInnerW, Math.max(innerW, font.width(Component.translatable("screen.newvisualkeybing.viewer.tooltip.click_hint").getString())));

        int titleH = Math.max(font.lineHeight, 12);
        int rowH = font.lineHeight * 3 + 7;
        int contentH = titleH + 6;
        contentH += font.lineHeight + 5;
        if (isWheel || bindings.isEmpty()) {
            contentH += font.lineHeight + 4;
        } else {
            contentH += font.lineHeight + 5;
            contentH += maxRows * rowH;
            if (bindings.size() > maxRows) contentH += font.lineHeight + 2;
        }
        if (status == KeyBindingScanner.KeyStatus.CONFLICT) contentH += font.lineHeight + 4;
        contentH += font.lineHeight + 6;

        int totalW = innerW + padX * 2;
        int totalH = contentH + padY * 2;
        int tx = clamp(mouseX + 12, 4, screenW - totalW - 4);
        int ty = clamp(mouseY + 12, 4, screenH - totalH - 4);

        UITheme.renderTooltipBackground(g, tx, ty, totalW, totalH);
        UITheme.fillRoundedRect(g, tx, ty, totalW, 2, 2, statusAccentColor(status));

        int curX = tx + padX;
        int curY = ty + padY;

        int chipX = tx + totalW - padX - chipW;
        renderStatusChip(g, font, chipX, curY, status, false);
        g.drawString(font, fitToWidth(font, keyName, chipX - curX - 6), curX, curY + 1, c.textPrimary(), false);
        curY += titleH + 4;

        g.fill(curX, curY, curX + innerW, curY + 1, UITheme.withAlpha(c.divider(), 0x70));
        curY += 4;
        g.drawString(font, fitToWidth(font, statusLine, innerW), curX, curY, c.textMuted(), false);
        curY += font.lineHeight + 5;

        if (isWheel) {
            g.drawString(font, Component.translatable("screen.newvisualkeybing.viewer.wheel_hint").getString(),
                    curX, curY, c.textMuted(), false);
            curY += font.lineHeight + 4;
        } else if (bindings.isEmpty()) {
            g.drawString(font, Component.translatable("screen.newvisualkeybing.viewer.unbound").getString(),
                    curX, curY, c.textMuted(), false);
            curY += font.lineHeight + 4;
        } else {
            String summary = Component.translatable("screen.newvisualkeybing.viewer.tooltip.summary",
                    bindings.size(), countSources(bindings), countCategories(bindings), countContexts(bindings)).getString();
            g.drawString(font, fitToWidth(font, summary, innerW), curX, curY, c.textMuted(), false);
            curY += font.lineHeight + 5;
            for (int i = 0; i < maxRows; i++) {
                KeyBindingScanner.KeyBindingInfo info = bindings.get(i);
                int sideColor = info.self() ? c.accent() : UITheme.withAlpha(c.widgetBorder(), 0xB0);
                UITheme.fillRoundedRect(g, curX, curY, innerW, rowH - 1, 4,
                        UITheme.withAlpha(c.widgetBg(), info.self() ? 0x80 : 0x55));
                g.fill(curX, curY + 2, curX + 2, curY + rowH - 2, sideColor);

                String ctxTag = contextTag(info.conflictContext());
                int tagW = ctxTag.isEmpty() ? 0 : font.width(ctxTag) + 6;
                int modMaxW = Math.max(44, Math.min(innerW / 3, innerW - 74));
                String modText = fitToWidth(font, info.modName(), modMaxW);
                int modW = font.width(modText);
                int rightBlockW = tagW + modW + 4;
                String actionFit = fitToWidth(font, info.actionName(), Math.max(30, innerW - 10 - rightBlockW));

                int textY = curY + 2;
                g.drawString(font, actionFit, curX + 6, textY,
                        info.self() ? c.accent() : c.textPrimary(), false);
                int rightX = curX + innerW - modW;
                g.drawString(font, modText, rightX, textY, c.textMuted(), false);
                if (!ctxTag.isEmpty()) {
                    int tagX = rightX - font.width(ctxTag) - 6;
                    g.drawString(font, ctxTag, tagX, textY, c.accentAlt(), false);
                }
                String meta = info.categoryName() + " | " + contextName(info.conflictContext());
                g.drawString(font, fitToWidth(font, meta, innerW - 6),
                        curX + 6, curY + font.lineHeight + 4, c.textMuted(), false);
                String keyMeta = info.translationKey() + " | "
                        + Component.translatable("screen.newvisualkeybing.viewer.tooltip.default_key",
                        info.defaultKeyName()).getString();
                g.drawString(font, fitToWidth(font, keyMeta, innerW - 6),
                        curX + 6, curY + font.lineHeight * 2 + 6, UITheme.withAlpha(c.textMuted(), 0xC0), false);
                curY += rowH;
            }
            if (bindings.size() > maxRows) {
                g.drawString(font, Component.translatable("screen.newvisualkeybing.viewer.tooltip.more",
                        bindings.size() - maxRows).getString(), curX, curY, c.textMuted(), false);
                curY += font.lineHeight + 2;
            }
        }

        if (status == KeyBindingScanner.KeyStatus.CONFLICT) {
            String warn = Component.translatable("screen.newvisualkeybing.viewer.tooltip.conflict_warning").getString();
            g.drawString(font, fitToWidth(font, warn, innerW), curX, curY, c.dangerColor(), false);
            curY += font.lineHeight + 4;
        }

        String hint = Component.translatable("screen.newvisualkeybing.viewer.tooltip.click_hint").getString();
        g.drawString(font, fitToWidth(font, hint, innerW), curX, curY, c.textMuted(), false);
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
        if (maxW <= 0) return "";
        if (font.width(text) <= maxW) return text;
        String ellipsis = "..";
        int eW = font.width(ellipsis);
        if (maxW <= eW) return ellipsis;
        return font.plainSubstrByWidth(text, maxW - eW) + ellipsis;
    }

    private static int clamp(int value, int min, int max) {
        if (max < min) return min;
        return Math.max(min, Math.min(value, max));
    }

    private static int countSources(List<KeyBindingScanner.KeyBindingInfo> bindings) {
        int count = 0;
        for (int i = 0; i < bindings.size(); i++) {
            String value = bindings.get(i).modName();
            boolean seen = false;
            for (int j = 0; j < i; j++) {
                if (value.equals(bindings.get(j).modName())) {
                    seen = true;
                    break;
                }
            }
            if (!seen) count++;
        }
        return count;
    }

    private static int countCategories(List<KeyBindingScanner.KeyBindingInfo> bindings) {
        int count = 0;
        for (int i = 0; i < bindings.size(); i++) {
            String value = bindings.get(i).categoryName();
            boolean seen = false;
            for (int j = 0; j < i; j++) {
                if (value.equals(bindings.get(j).categoryName())) {
                    seen = true;
                    break;
                }
            }
            if (!seen) count++;
        }
        return count;
    }

    private static int countContexts(List<KeyBindingScanner.KeyBindingInfo> bindings) {
        int count = 0;
        for (int i = 0; i < bindings.size(); i++) {
            ConflictContext value = bindings.get(i).conflictContext();
            boolean seen = false;
            for (int j = 0; j < i; j++) {
                if (value == bindings.get(j).conflictContext()) {
                    seen = true;
                    break;
                }
            }
            if (!seen) count++;
        }
        return count;
    }

    private static String contextCountText(List<KeyBindingScanner.KeyBindingInfo> bindings) {
        int count = bindings.isEmpty() ? 0 : countContexts(bindings);
        return Component.translatable("screen.newvisualkeybing.viewer.info.contexts", count).getString();
    }

    private static String inputTypeName(int virtualKey) {
        if (KeyboardLayoutData.isWheel(virtualKey)) {
            return Component.translatable("screen.newvisualkeybing.viewer.tooltip.input.wheel").getString();
        }
        if (KeyboardLayoutData.isMouse(virtualKey)) {
            return Component.translatable("screen.newvisualkeybing.viewer.tooltip.input.mouse").getString();
        }
        return Component.translatable("screen.newvisualkeybing.viewer.tooltip.input.keyboard").getString();
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

    private static String contextName(ConflictContext ctx) {
        if (ctx == null) return Component.translatable("screen.newvisualkeybing.viewer.context.unknown").getString();
        return switch (ctx) {
            case UNIVERSAL -> Component.translatable("screen.newvisualkeybing.viewer.context.short.universal").getString();
            case IN_GAME -> Component.translatable("screen.newvisualkeybing.viewer.context.short.in_game").getString();
            case GUI -> Component.translatable("screen.newvisualkeybing.viewer.context.short.gui").getString();
            case UNKNOWN -> Component.translatable("screen.newvisualkeybing.viewer.context.short.unknown").getString();
        };
    }
}
