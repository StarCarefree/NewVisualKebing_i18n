package com.github.newvisualkeybing.client.screen;

import com.github.newvisualkeybing.client.keyboard.KeyBindingScanner;
import com.github.newvisualkeybing.client.keyboard.KeyboardLayoutData;
import com.github.newvisualkeybing.client.ui.UITheme;
import com.github.newvisualkeybing.platform.services.IPlatformHelper.ConflictContext;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

import java.util.Collections;
import java.util.List;





final class KeybindDetailPanel {

    private static final int PANEL_PAD = 10;
    private static final int ACTION_BTN_H = 20;
    private static final int ACTION_BTN_GAP = 6;

    private final KeyBindingScanner scanner;

    private int scrollOffset;
    private int modifyX = -1, modifyY = -1;
    private int unbindX = -1, unbindY = -1;
    private int actionBtnW;

    KeybindDetailPanel(KeyBindingScanner scanner) {
        this.scanner = scanner;
    }

    void resetScroll() {
        scrollOffset = 0;
    }

    void scroll(int direction) {
        scrollOffset = Math.max(0, scrollOffset - direction);
    }

    boolean isModifyHit(double mx, double my) {
        return modifyX >= 0 && KeybindViewerScreen.inside(mx, my, modifyX, modifyY, actionBtnW, ACTION_BTN_H);
    }

    boolean isUnbindHit(double mx, double my) {
        return unbindX >= 0 && KeybindViewerScreen.inside(mx, my, unbindX, unbindY, actionBtnW, ACTION_BTN_H);
    }

    void render(GuiGraphics g, Font font, int x, int y, int w, int h,
                Integer virtualKey, int mouseX, int mouseY) {
        modifyX = modifyY = unbindX = unbindY = -1;

        var c = UITheme.colors();
        int contentY = KeybindViewerScreen.paintPanelBase(g, font, x, y, w, h,
                Component.translatable("screen.newvisualkeybing.viewer.details").getString());

        int innerX = x + PANEL_PAD;
        int innerW = w - PANEL_PAD * 2;
        int innerBottom = y + h - PANEL_PAD;

        if (virtualKey == null) {
            int boxH = 54;
            int boxY = contentY + Math.max(8, (innerBottom - contentY - boxH) / 2);
            UITheme.fillRoundedRect(g, innerX, boxY, innerW, boxH, 6,
                    UITheme.lerpColor(c.widgetBg(), c.panelBg(), 0.45f));
            UITheme.drawRoundedBorder(g, innerX, boxY, innerW, boxH, 6,
                    UITheme.withAlpha(c.widgetBorder(), 0x90));
            String hint = KeybindViewerScreen.fitToWidth(font,
                    Component.translatable("screen.newvisualkeybing.viewer.hover_hint").getString(),
                    innerW - 16);
            g.drawString(font, hint, innerX + 8, boxY + (boxH - font.lineHeight) / 2, c.textMuted(), false);
            return;
        }

        boolean isWheel = KeyboardLayoutData.isWheel(virtualKey);
        boolean isMouse = KeyboardLayoutData.isMouse(virtualKey);
        List<KeyBindingScanner.KeyBindingInfo> bindings = isWheel
                ? Collections.emptyList()
                : (isMouse
                    ? scanner.getMouseBindings(KeyboardLayoutData.virtualToMouseBtn(virtualKey))
                    : scanner.getBindings(virtualKey));
        KeyBindingScanner.KeyStatus status = isWheel ? KeyBindingScanner.KeyStatus.FREE
                : (isMouse
                    ? scanner.getMouseStatus(KeyboardLayoutData.virtualToMouseBtn(virtualKey))
                    : scanner.getStatus(virtualKey));

        String keyName = scanner.getVirtualKeyLabel(virtualKey);
        int chipW = renderStatusChip(g, font, 0, 0, status, true);
        int keyNameW = font.width(keyName);
        boolean stackHead = keyNameW + 8 + chipW > innerW && innerW < 160 || keyNameW > innerW - chipW - 12;
        int lineY;
        if (stackHead) {
            String displayKeyName = KeybindViewerScreen.fitToWidth(font, keyName, innerW);
            g.drawString(font, displayKeyName, innerX, contentY + 2, c.textPrimary(), false);
            renderStatusChip(g, font, innerX, contentY + font.lineHeight + 4, status, false);
            lineY = contentY + font.lineHeight + 4 + 12 + 4;
        } else {
            int chipX = innerX + innerW - chipW;
            renderStatusChip(g, font, chipX, contentY, status, false);
            int keyNameMaxW = innerW - chipW - 6;
            String displayKeyName = KeybindViewerScreen.fitToWidth(font, keyName, keyNameMaxW);
            g.drawString(font, displayKeyName, innerX, contentY + 2, c.textPrimary(), false);
            lineY = contentY + Math.max(font.lineHeight, 12) + 4;
        }

        if (!bindings.isEmpty()) {
            String catText = bindings.get(0).categoryName();
            if (bindings.size() > 1) {
                long distinct = bindings.stream().map(KeyBindingScanner.KeyBindingInfo::categoryName).distinct().count();
                if (distinct > 1) catText = catText + "  +" + (distinct - 1);
            }
            String catFit = KeybindViewerScreen.fitToWidth(font, catText, innerW);
            g.drawString(font, catFit, innerX, lineY, c.textMuted(), false);
            lineY += font.lineHeight + 2;
        }

        g.fill(innerX, lineY, innerX + innerW, lineY + 1, UITheme.withAlpha(c.divider(), 0x80));
        lineY += 4;

        boolean canEdit = !isWheel;
        int actionY = canEdit ? innerBottom - ACTION_BTN_H : innerBottom;
        int listBottom = actionY - 4;

        if (isWheel) {
            renderInfoBox(g, font, innerX, lineY, innerW,
                    Component.translatable("screen.newvisualkeybing.viewer.wheel_hint").getString(),
                    c.textMuted());
        } else if (bindings.isEmpty()) {
            renderInfoBox(g, font, innerX, lineY, innerW,
                    Component.translatable("screen.newvisualkeybing.viewer.unbound").getString(),
                    c.textMuted());
        } else {
            renderBindingList(g, font, innerX, lineY, innerW, listBottom - lineY, bindings);
        }

        if (canEdit) {
            int btnW = (innerW - ACTION_BTN_GAP) / 2;
            actionBtnW = btnW;
            modifyX = innerX;
            modifyY = actionY;
            KeybindViewerScreen.renderActionButton(g, font, modifyX, modifyY, btnW, ACTION_BTN_H,
                    Component.translatable("screen.newvisualkeybing.viewer.modify").getString(),
                    c.accent(), KeybindViewerScreen.inside(mouseX, mouseY, modifyX, modifyY, btnW, ACTION_BTN_H));

            unbindX = innerX + btnW + ACTION_BTN_GAP;
            unbindY = actionY;
            KeybindViewerScreen.renderActionButton(g, font, unbindX, unbindY, btnW, ACTION_BTN_H,
                    Component.translatable("screen.newvisualkeybing.viewer.unbind").getString(),
                    c.danger(), KeybindViewerScreen.inside(mouseX, mouseY, unbindX, unbindY, btnW, ACTION_BTN_H));
        }
    }

    private static void renderInfoBox(GuiGraphics g, Font font, int x, int y, int w, String text, int textColor) {
        var c = UITheme.colors();
        int h = font.lineHeight + 12;
        UITheme.fillRoundedRect(g, x, y, w, h, 6, UITheme.lerpColor(c.widgetBg(), c.panelBg(), 0.45f));
        UITheme.drawRoundedBorder(g, x, y, w, h, 6, UITheme.withAlpha(c.widgetBorder(), 0x80));
        String fit = KeybindViewerScreen.fitToWidth(font, text, w - 16);
        g.drawString(font, fit, x + 8, y + (h - font.lineHeight) / 2, textColor, false);
    }

    private static int renderStatusChip(GuiGraphics g, Font font, int x, int y, KeyBindingScanner.KeyStatus status, boolean measureOnly) {
        var c = UITheme.colors();
        int dot;
        int textColor;
        switch (status) {
            case FREE -> { dot = c.widgetBorder(); textColor = c.textSecondary(); }
            case SELF -> { dot = c.accent(); textColor = c.accent(); }
            case OTHER_SINGLE, BOUND -> { dot = c.success(); textColor = c.success(); }
            case CONFLICT -> { dot = c.danger(); textColor = c.danger(); }
            default -> { dot = c.widgetBorder(); textColor = c.textSecondary(); }
        }
        String label = Component.translatable(statusTranslation(status)).getString();
        int chipH = 12;
        int chipW = font.width(label) + 16;
        if (measureOnly) return chipW;
        int chipFill = UITheme.lerpColor(c.widgetBg(), dot, 0.18f);
        UITheme.fillRoundedRect(g, x, y, chipW, chipH, chipH / 2, chipFill);
        UITheme.drawRoundedBorder(g, x, y, chipW, chipH, chipH / 2, UITheme.withAlpha(dot, 0xC0));
        UITheme.fillRoundedRect(g, x + 5, y + (chipH - 4) / 2, 4, 4, 2, dot);
        g.drawString(font, label, x + 11, y + (chipH - font.lineHeight) / 2 + 1, textColor, false);
        return chipW;
    }

    
    private void renderBindingList(GuiGraphics g, Font font, int x, int y, int w, int h,
                                   List<KeyBindingScanner.KeyBindingInfo> bindings) {
        var c = UITheme.colors();
        int singleRowH = font.lineHeight + 4;
        int doubleRowH = font.lineHeight * 2 + 5;

        int[] rowHeights = new int[bindings.size()];
        boolean[] doubleLine = new boolean[bindings.size()];
        for (int i = 0; i < bindings.size(); i++) {
            KeyBindingScanner.KeyBindingInfo info = bindings.get(i);
            String ctxTag = contextTag(info.conflictContext());
            int rightBlockW = font.width(info.modName()) + 6 + (ctxTag.isEmpty() ? 0 : font.width(ctxTag) + 6);
            int actionMaxW = w - 8 - rightBlockW;
            doubleLine[i] = font.width(info.actionName()) > actionMaxW;
            rowHeights[i] = doubleLine[i] ? doubleRowH : singleRowH;
        }

        int totalContentH = 0;
        for (int rh : rowHeights) totalContentH += rh + 2;
        boolean overflow = totalContentH > h;
        if (overflow) {
            int maxOffset = 0;
            int accH = 0;
            for (int i = bindings.size() - 1; i >= 0; i--) {
                accH += rowHeights[i] + 2;
                if (accH > h - (font.lineHeight + 2)) {
                    maxOffset = i + 1;
                    break;
                }
            }
            scrollOffset = Mth.clamp(scrollOffset, 0, maxOffset);
        } else {
            scrollOffset = 0;
        }

        int rowY = y;
        int reservedForOverflow = overflow ? font.lineHeight + 4 : 0;
        int budgetH = h - reservedForOverflow;
        int end = bindings.size();
        int usedH = 0;
        for (int i = scrollOffset; i < bindings.size(); i++) {
            int rh = rowHeights[i];
            if (usedH + rh > budgetH) { end = i; break; }

            KeyBindingScanner.KeyBindingInfo info = bindings.get(i);
            renderBindingRow(g, font, x, rowY, w, rh, info, doubleLine[i]);
            rowY += rh + 2;
            usedH += rh + 2;
        }

        if (overflow) {
            int more = bindings.size() - end + scrollOffset;
            if (more > 0) {
                String s = "+" + more;
                g.drawString(font, s, x + w - font.width(s), y + h - font.lineHeight, c.textMuted(), false);
            }
        }
    }

    
    private static void renderBindingRow(GuiGraphics g, Font font, int x, int y, int w, int rowH,
                                         KeyBindingScanner.KeyBindingInfo info, boolean twoLines) {
        var c = UITheme.colors();
        if (info.self()) {
            UITheme.fillRoundedRect(g, x, y, w, rowH, 4,
                    UITheme.lerpColor(c.widgetBg(), c.accent(), 0.10f));
        }
        int sideColor = info.self() ? c.accent() : UITheme.withAlpha(c.widgetBorder(), 0xC0);
        g.fill(x, y + 2, x + 2, y + rowH - 2, sideColor);

        int actionColor = info.self() ? c.accent() : c.textPrimary();
        String ctxTag = contextTag(info.conflictContext());
        String modText = info.modName();

        if (twoLines) {
            String actionFit = KeybindViewerScreen.fitToWidth(font, info.actionName(), w - 8);
            g.drawString(font, actionFit, x + 6, y + 2, actionColor, false);
            int rightX = x + w - font.width(modText);
            int line2Y = y + font.lineHeight + 3;
            g.drawString(font, modText, rightX, line2Y, c.textMuted(), false);
            if (!ctxTag.isEmpty()) {
                int tagX = rightX - font.width(ctxTag) - 6;
                int tagBgW = font.width(ctxTag) + 4;
                int tagBgH = font.lineHeight + 1;
                UITheme.fillRoundedRect(g, tagX - 2, line2Y - 1, tagBgW, tagBgH, 3,
                        UITheme.lerpColor(c.widgetBg(), c.accentAlt(), 0.20f));
                g.drawString(font, ctxTag, tagX, line2Y, c.accentAlt(), false);
            }
        } else {
            int modW = font.width(modText);
            int rightBlockW = modW + (ctxTag.isEmpty() ? 0 : font.width(ctxTag) + 6);
            int actionMaxW = w - 8 - rightBlockW - 4;
            String actionText = KeybindViewerScreen.fitToWidth(font, info.actionName(), actionMaxW);
            int textY = y + (rowH - font.lineHeight) / 2;
            g.drawString(font, actionText, x + 6, textY, actionColor, false);
            int rightX = x + w - modW;
            g.drawString(font, modText, rightX, textY, c.textMuted(), false);
            if (!ctxTag.isEmpty()) {
                int tagX = rightX - font.width(ctxTag) - 6;
                int tagBgW = font.width(ctxTag) + 4;
                int tagBgH = font.lineHeight + 1;
                UITheme.fillRoundedRect(g, tagX - 2, textY - 1, tagBgW, tagBgH, 3,
                        UITheme.lerpColor(c.widgetBg(), c.accentAlt(), 0.20f));
                g.drawString(font, ctxTag, tagX, textY, c.accentAlt(), false);
            }
        }
    }

    private static String contextTag(ConflictContext ctx) {
        if (ctx == null) return "";
        return switch (ctx) {
            case UNIVERSAL -> "U";
            case IN_GAME   -> "I";
            case GUI       -> "G";
            case UNKNOWN   -> "";
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
}
