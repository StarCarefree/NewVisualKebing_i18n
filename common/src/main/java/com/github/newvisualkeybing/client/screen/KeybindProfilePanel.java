package com.github.newvisualkeybing.client.screen;

import com.github.newvisualkeybing.client.keyboard.KeybindProfileStore;
import com.github.newvisualkeybing.client.ui.UITheme;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import java.nio.file.Path;
import java.util.List;

final class KeybindProfilePanel {

    static final int WIDTH = 164;
    private static final int ROW_H = 20;
    private static final int BUTTON_H = 18;

    private final KeybindProfileStore profileStore;
    private final Runnable rebuildEntries;
    private final NoticeSink noticeSink;

    KeybindProfilePanel(KeybindProfileStore profileStore, Runnable rebuildEntries, NoticeSink noticeSink) {
        this.profileStore = profileStore;
        this.rebuildEntries = rebuildEntries;
        this.noticeSink = noticeSink;
    }

    void render(GuiGraphics graphics, Font font, int x, int y, int h, int mouseX, int mouseY) {
        var colors = UITheme.colors();
        UITheme.fillRoundedRect(graphics, x, y, WIDTH, h, 8, UITheme.withAlpha(colors.headerBg(), 0xC8));
        UITheme.drawRoundedBorder(graphics, x, y, WIDTH, h, 8, colors.widgetBorder());

        String title = Component.translatable("screen.newvisualkeybing.viewer.profile.title").getString();
        graphics.drawString(font, title, x + 10, y + 9, colors.textPrimary(), false);
        graphics.fill(x + 8, y + 25, x + WIDTH - 8, y + 26, UITheme.withAlpha(colors.divider(), 0xA0));

        int rowY = y + 32;
        List<KeybindProfileStore.Profile> profiles = profileStore.profiles();
        int selected = profileStore.selectedIndex();
        int buttonTop = buttonTop(y, h);
        int rowBottom = buttonTop - 6;
        for (int i = 0; i < profiles.size() && rowY + ROW_H <= rowBottom; i++) {
            KeybindProfileStore.Profile profile = profiles.get(i);
            boolean active = i == selected;
            boolean hovered = inside(mouseX, mouseY, x + 8, rowY, WIDTH - 16, ROW_H - 2);
            int fill = active ? UITheme.lerpColor(colors.widgetBg(), colors.accent(), 0.42f)
                    : hovered ? UITheme.withAlpha(colors.widgetBg(), 0xB0)
                    : UITheme.withAlpha(colors.widgetBg(), 0x66);
            UITheme.fillRoundedRect(graphics, x + 8, rowY, WIDTH - 16, ROW_H - 2, 5, fill);
            if (active) graphics.fill(x + 10, rowY + 3, x + 12, rowY + ROW_H - 5, colors.accent());
            String label = fit(font, profileStore.compactProfileLabel(profile), WIDTH - 30);
            graphics.drawString(font, label, x + 16, rowY + 5, active ? colors.textPrimary() : colors.textSecondary(), false);
            rowY += ROW_H;
        }

        if (profiles.isEmpty()) {
            String empty = Component.translatable("screen.newvisualkeybing.viewer.profile.empty").getString();
            graphics.drawString(font, fit(font, empty, WIDTH - 20), x + 10, rowY + 4, colors.textMuted(), false);
        }

        int halfW = (WIDTH - 22) / 2;
        renderButton(graphics, font, x + 8, buttonTop, halfW, BUTTON_H,
                Component.translatable("screen.newvisualkeybing.viewer.profile.save").getString(),
                colors.accent(), inside(mouseX, mouseY, x + 8, buttonTop, halfW, BUTTON_H));
        renderButton(graphics, font, x + 14 + halfW, buttonTop, halfW, BUTTON_H,
                Component.translatable("screen.newvisualkeybing.viewer.profile.new").getString(),
                colors.accentLight(), inside(mouseX, mouseY, x + 14 + halfW, buttonTop, halfW, BUTTON_H));
        renderButton(graphics, font, x + 8, buttonTop + BUTTON_H + 5, WIDTH - 16, BUTTON_H,
                Component.translatable("screen.newvisualkeybing.viewer.profile.apply").getString(),
                colors.successColor(), inside(mouseX, mouseY, x + 8, buttonTop + BUTTON_H + 5, WIDTH - 16, BUTTON_H));
        renderButton(graphics, font, x + 8, buttonTop + (BUTTON_H + 5) * 2, halfW, BUTTON_H,
                Component.translatable("screen.newvisualkeybing.viewer.profile.export").getString(),
                colors.warningColor(), inside(mouseX, mouseY, x + 8, buttonTop + (BUTTON_H + 5) * 2, halfW, BUTTON_H));
        renderButton(graphics, font, x + 14 + halfW, buttonTop + (BUTTON_H + 5) * 2, halfW, BUTTON_H,
                Component.translatable("screen.newvisualkeybing.viewer.profile.import").getString(),
                colors.accent(), inside(mouseX, mouseY, x + 14 + halfW, buttonTop + (BUTTON_H + 5) * 2, halfW, BUTTON_H));
        renderButton(graphics, font, x + 8, buttonTop + (BUTTON_H + 5) * 3, WIDTH - 16, BUTTON_H,
                Component.translatable("screen.newvisualkeybing.viewer.profile.delete").getString(),
                colors.dangerColor(), inside(mouseX, mouseY, x + 8, buttonTop + (BUTTON_H + 5) * 3, WIDTH - 16, BUTTON_H));
    }

    boolean mouseClicked(double mouseX, double mouseY, int x, int y, int h) {
        if (!inside(mouseX, mouseY, x, y, WIDTH, h)) return false;

        int buttonTop = buttonTop(y, h);
        int halfW = (WIDTH - 22) / 2;
        if (inside(mouseX, mouseY, x + 8, buttonTop, halfW, BUTTON_H)) {
            KeybindProfileStore.Profile profile = profileStore.saveCurrentProfile();
            rebuildEntries.run();
            noticeSink.notice(Component.translatable("screen.newvisualkeybing.viewer.profile.saved", profile.name).getString());
            return true;
        }
        if (inside(mouseX, mouseY, x + 14 + halfW, buttonTop, halfW, BUTTON_H)) {
            KeybindProfileStore.Profile profile = profileStore.createProfileFromCurrent();
            rebuildEntries.run();
            noticeSink.notice(Component.translatable("screen.newvisualkeybing.viewer.profile.created", profile.name).getString());
            return true;
        }
        if (inside(mouseX, mouseY, x + 8, buttonTop + BUTTON_H + 5, WIDTH - 16, BUTTON_H)) {
            if (profileStore.applySelectedProfile()) {
                rebuildEntries.run();
                noticeSink.notice(Component.translatable("screen.newvisualkeybing.viewer.profile.applied",
                        profileStore.selectedProfile().name).getString());
            } else {
                noticeSink.notice(Component.translatable("screen.newvisualkeybing.viewer.profile.no_selection").getString());
            }
            return true;
        }
        if (inside(mouseX, mouseY, x + 8, buttonTop + (BUTTON_H + 5) * 2, halfW, BUTTON_H)) {
            Path path = profileStore.exportSelectedProfile();
            if (path != null) {
                noticeSink.notice(Component.translatable("screen.newvisualkeybing.viewer.profile.exported",
                        path.getFileName().toString()).getString());
            } else {
                noticeSink.notice(Component.translatable("screen.newvisualkeybing.viewer.profile.no_selection").getString());
            }
            return true;
        }
        if (inside(mouseX, mouseY, x + 14 + halfW, buttonTop + (BUTTON_H + 5) * 2, halfW, BUTTON_H)) {
            KeybindProfileStore.Profile profile = profileStore.importLatestExport();
            if (profile != null) {
                rebuildEntries.run();
                noticeSink.notice(Component.translatable("screen.newvisualkeybing.viewer.profile.imported", profile.name).getString());
            } else {
                noticeSink.notice(Component.translatable("screen.newvisualkeybing.viewer.profile.no_exports").getString());
            }
            return true;
        }
        if (inside(mouseX, mouseY, x + 8, buttonTop + (BUTTON_H + 5) * 3, WIDTH - 16, BUTTON_H)) {
            String name = profileStore.selectedProfile() == null ? "" : profileStore.selectedProfile().name;
            if (profileStore.deleteSelectedProfile()) {
                rebuildEntries.run();
                noticeSink.notice(Component.translatable("screen.newvisualkeybing.viewer.profile.deleted", name).getString());
            } else {
                noticeSink.notice(Component.translatable("screen.newvisualkeybing.viewer.profile.no_selection").getString());
            }
            return true;
        }

        int rowY = y + 32;
        int rowBottom = buttonTop - 6;
        List<KeybindProfileStore.Profile> profiles = profileStore.profiles();
        for (int i = 0; i < profiles.size() && rowY + ROW_H <= rowBottom; i++) {
            if (inside(mouseX, mouseY, x + 8, rowY, WIDTH - 16, ROW_H - 2)) {
                profileStore.select(i);
                return true;
            }
            rowY += ROW_H;
        }
        return true;
    }

    private int buttonTop(int y, int h) {
        return y + h - BUTTON_H * 4 - 23;
    }

    private void renderButton(GuiGraphics graphics, Font font, int x, int y, int w, int h, String label, int accent, boolean hovered) {
        var colors = UITheme.colors();
        UITheme.fillRoundedRect(graphics, x, y, w, h, 4,
                UITheme.lerpColor(colors.widgetBg(), accent, hovered ? 0.42f : 0.24f));
        UITheme.drawRoundedBorder(graphics, x, y, w, h, 4, UITheme.withAlpha(accent, 0xA0));
        String fitted = fit(font, label, w - 8);
        graphics.drawString(font, fitted, x + (w - font.width(fitted)) / 2,
                y + (h - font.lineHeight) / 2, colors.textPrimary(), false);
    }

    private static String fit(Font font, String text, int maxWidth) {
        if (font.width(text) <= maxWidth) return text;
        String suffix = "..";
        return font.plainSubstrByWidth(text, Math.max(0, maxWidth - font.width(suffix))) + suffix;
    }

    private static boolean inside(double mouseX, double mouseY, int x, int y, int w, int h) {
        return mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h;
    }

    interface NoticeSink {
        void notice(String message);
    }
}
