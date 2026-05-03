package com.github.newvisualkeybing.client.keyboard;

import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class KeyboardLayoutData {

    public static final int BASE_UNIT = 24;
    public static final int BASE_GAP = 2;
    public static final int MOUSE_BTN_BASE = -100;
    public static final float TOTAL_WIDTH_U = 22.50f;
    public static final float TOTAL_HEIGHT_U = 6.10f;

    private KeyboardLayoutData() {
    }

    public record KeyDef(int glfwKey, String label, String subLabel, float gridX, float gridY, float width, float height) {
        public int screenX(int baseX, float scale) {
            return baseX + Math.round(gridX * (scale + BASE_GAP));
        }

        public int screenY(int baseY, float scale) {
            return baseY + Math.round(gridY * (scale + BASE_GAP));
        }

        public int screenW(float scale) {
            return Math.round(width * scale + (width - 1) * BASE_GAP);
        }

        public int screenH(float scale) {
            return Math.round(height * scale + (height - 1) * BASE_GAP);
        }
    }

    public static int mouseToVirtual(int mouseButton) {
        return MOUSE_BTN_BASE - mouseButton;
    }

    public static int virtualToMouseBtn(int virtualKey) {
        return MOUSE_BTN_BASE - virtualKey;
    }

    public static boolean isMouse(int virtualKey) {
        return virtualKey <= MOUSE_BTN_BASE && virtualKey >= MOUSE_BTN_BASE - 7;
    }

    public static int totalWidthPx(float scale) {
        return Math.round(TOTAL_WIDTH_U * scale + (TOTAL_WIDTH_U - 1) * BASE_GAP);
    }

    public static int totalHeightPx(float scale) {
        return Math.round(TOTAL_HEIGHT_U * scale + (TOTAL_HEIGHT_U - 1) * BASE_GAP);
    }

    public static final List<KeyDef> MOUSE_KEYS;
    public static final List<KeyDef> KEYS;

    static {
        List<KeyDef> mouse = new ArrayList<>();
        mouse.add(new KeyDef(MOUSE_BTN_BASE, "LMB", null, 0, 0, 2, 1));
        mouse.add(new KeyDef(MOUSE_BTN_BASE - 2, "MMB", null, 0, 0, 1, 1));
        mouse.add(new KeyDef(MOUSE_BTN_BASE - 1, "RMB", null, 0, 0, 2, 1));
        mouse.add(new KeyDef(MOUSE_BTN_BASE - 3, "M4", null, 0, 0, 1, 1));
        mouse.add(new KeyDef(MOUSE_BTN_BASE - 4, "M5", null, 0, 0, 1, 1));
        MOUSE_KEYS = Collections.unmodifiableList(mouse);

        List<KeyDef> keys = new ArrayList<>();
        keys.add(kd(GLFW.GLFW_KEY_ESCAPE, "Esc", null, 0.00f, 0.00f, 1.00f, 1.0f));
        keys.add(kd(GLFW.GLFW_KEY_F1, "F1", null, 1.50f, 0.00f, 1.00f, 1.0f));
        keys.add(kd(GLFW.GLFW_KEY_F2, "F2", null, 2.50f, 0.00f, 1.00f, 1.0f));
        keys.add(kd(GLFW.GLFW_KEY_F3, "F3", null, 3.50f, 0.00f, 1.00f, 1.0f));
        keys.add(kd(GLFW.GLFW_KEY_F4, "F4", null, 4.50f, 0.00f, 1.00f, 1.0f));
        keys.add(kd(GLFW.GLFW_KEY_F5, "F5", null, 5.75f, 0.00f, 1.00f, 1.0f));
        keys.add(kd(GLFW.GLFW_KEY_F6, "F6", null, 6.75f, 0.00f, 1.00f, 1.0f));
        keys.add(kd(GLFW.GLFW_KEY_F7, "F7", null, 7.75f, 0.00f, 1.00f, 1.0f));
        keys.add(kd(GLFW.GLFW_KEY_F8, "F8", null, 8.75f, 0.00f, 1.00f, 1.0f));
        keys.add(kd(GLFW.GLFW_KEY_F9, "F9", null, 10.00f, 0.00f, 1.00f, 1.0f));
        keys.add(kd(GLFW.GLFW_KEY_F10, "F10", null, 11.00f, 0.00f, 1.00f, 1.0f));
        keys.add(kd(GLFW.GLFW_KEY_F11, "F11", null, 12.00f, 0.00f, 1.00f, 1.0f));
        keys.add(kd(GLFW.GLFW_KEY_F12, "F12", null, 13.00f, 0.00f, 1.00f, 1.0f));
        keys.add(kd(GLFW.GLFW_KEY_PRINT_SCREEN, "Prt", null, 14.25f, 0.00f, 1.00f, 1.0f));
        keys.add(kd(GLFW.GLFW_KEY_SCROLL_LOCK, "Scr", null, 15.25f, 0.00f, 1.00f, 1.0f));
        keys.add(kd(GLFW.GLFW_KEY_PAUSE, "Pau", null, 16.25f, 0.00f, 1.00f, 1.0f));

        keys.add(kd(GLFW.GLFW_KEY_GRAVE_ACCENT, "`", "~", 0.00f, 1.10f, 1.00f, 1.0f));
        keys.add(kd(GLFW.GLFW_KEY_1, "1", "!", 1.00f, 1.10f, 1.00f, 1.0f));
        keys.add(kd(GLFW.GLFW_KEY_2, "2", "@", 2.00f, 1.10f, 1.00f, 1.0f));
        keys.add(kd(GLFW.GLFW_KEY_3, "3", "#", 3.00f, 1.10f, 1.00f, 1.0f));
        keys.add(kd(GLFW.GLFW_KEY_4, "4", "$", 4.00f, 1.10f, 1.00f, 1.0f));
        keys.add(kd(GLFW.GLFW_KEY_5, "5", "%", 5.00f, 1.10f, 1.00f, 1.0f));
        keys.add(kd(GLFW.GLFW_KEY_6, "6", "^", 6.00f, 1.10f, 1.00f, 1.0f));
        keys.add(kd(GLFW.GLFW_KEY_7, "7", "&", 7.00f, 1.10f, 1.00f, 1.0f));
        keys.add(kd(GLFW.GLFW_KEY_8, "8", "*", 8.00f, 1.10f, 1.00f, 1.0f));
        keys.add(kd(GLFW.GLFW_KEY_9, "9", "(", 9.00f, 1.10f, 1.00f, 1.0f));
        keys.add(kd(GLFW.GLFW_KEY_0, "0", ")", 10.00f, 1.10f, 1.00f, 1.0f));
        keys.add(kd(GLFW.GLFW_KEY_MINUS, "-", "_", 11.00f, 1.10f, 1.00f, 1.0f));
        keys.add(kd(GLFW.GLFW_KEY_EQUAL, "=", "+", 12.00f, 1.10f, 1.00f, 1.0f));
        keys.add(kd(GLFW.GLFW_KEY_BACKSPACE, "Bksp", null, 13.00f, 1.10f, 2.00f, 1.0f));
        keys.add(kd(GLFW.GLFW_KEY_INSERT, "Ins", null, 15.25f, 1.10f, 1.00f, 1.0f));
        keys.add(kd(GLFW.GLFW_KEY_HOME, "Home", null, 16.25f, 1.10f, 1.00f, 1.0f));
        keys.add(kd(GLFW.GLFW_KEY_PAGE_UP, "PgUp", null, 17.25f, 1.10f, 1.00f, 1.0f));
        keys.add(kd(GLFW.GLFW_KEY_NUM_LOCK, "Num", null, 18.50f, 1.10f, 1.00f, 1.0f));
        keys.add(kd(GLFW.GLFW_KEY_KP_DIVIDE, "/", null, 19.50f, 1.10f, 1.00f, 1.0f));
        keys.add(kd(GLFW.GLFW_KEY_KP_MULTIPLY, "*", null, 20.50f, 1.10f, 1.00f, 1.0f));
        keys.add(kd(GLFW.GLFW_KEY_KP_SUBTRACT, "-", null, 21.50f, 1.10f, 1.00f, 1.0f));

        keys.add(kd(GLFW.GLFW_KEY_TAB, "Tab", null, 0.00f, 2.10f, 1.50f, 1.0f));
        keys.add(kd(GLFW.GLFW_KEY_Q, "Q", null, 1.50f, 2.10f, 1.00f, 1.0f));
        keys.add(kd(GLFW.GLFW_KEY_W, "W", null, 2.50f, 2.10f, 1.00f, 1.0f));
        keys.add(kd(GLFW.GLFW_KEY_E, "E", null, 3.50f, 2.10f, 1.00f, 1.0f));
        keys.add(kd(GLFW.GLFW_KEY_R, "R", null, 4.50f, 2.10f, 1.00f, 1.0f));
        keys.add(kd(GLFW.GLFW_KEY_T, "T", null, 5.50f, 2.10f, 1.00f, 1.0f));
        keys.add(kd(GLFW.GLFW_KEY_Y, "Y", null, 6.50f, 2.10f, 1.00f, 1.0f));
        keys.add(kd(GLFW.GLFW_KEY_U, "U", null, 7.50f, 2.10f, 1.00f, 1.0f));
        keys.add(kd(GLFW.GLFW_KEY_I, "I", null, 8.50f, 2.10f, 1.00f, 1.0f));
        keys.add(kd(GLFW.GLFW_KEY_O, "O", null, 9.50f, 2.10f, 1.00f, 1.0f));
        keys.add(kd(GLFW.GLFW_KEY_P, "P", null, 10.50f, 2.10f, 1.00f, 1.0f));
        keys.add(kd(GLFW.GLFW_KEY_LEFT_BRACKET, "[", "{", 11.50f, 2.10f, 1.00f, 1.0f));
        keys.add(kd(GLFW.GLFW_KEY_RIGHT_BRACKET, "]", "}", 12.50f, 2.10f, 1.00f, 1.0f));
        keys.add(kd(GLFW.GLFW_KEY_BACKSLASH, "\\", "|", 13.50f, 2.10f, 1.50f, 1.0f));
        keys.add(kd(GLFW.GLFW_KEY_DELETE, "Del", null, 15.25f, 2.10f, 1.00f, 1.0f));
        keys.add(kd(GLFW.GLFW_KEY_END, "End", null, 16.25f, 2.10f, 1.00f, 1.0f));
        keys.add(kd(GLFW.GLFW_KEY_PAGE_DOWN, "PgDn", null, 17.25f, 2.10f, 1.00f, 1.0f));
        keys.add(kd(GLFW.GLFW_KEY_KP_7, "7", null, 18.50f, 2.10f, 1.00f, 1.0f));
        keys.add(kd(GLFW.GLFW_KEY_KP_8, "8", null, 19.50f, 2.10f, 1.00f, 1.0f));
        keys.add(kd(GLFW.GLFW_KEY_KP_9, "9", null, 20.50f, 2.10f, 1.00f, 1.0f));
        keys.add(kd(GLFW.GLFW_KEY_KP_ADD, "+", null, 21.50f, 2.10f, 1.00f, 2.0f));

        keys.add(kd(GLFW.GLFW_KEY_CAPS_LOCK, "Caps", null, 0.00f, 3.10f, 1.75f, 1.0f));
        keys.add(kd(GLFW.GLFW_KEY_A, "A", null, 1.75f, 3.10f, 1.00f, 1.0f));
        keys.add(kd(GLFW.GLFW_KEY_S, "S", null, 2.75f, 3.10f, 1.00f, 1.0f));
        keys.add(kd(GLFW.GLFW_KEY_D, "D", null, 3.75f, 3.10f, 1.00f, 1.0f));
        keys.add(kd(GLFW.GLFW_KEY_F, "F", null, 4.75f, 3.10f, 1.00f, 1.0f));
        keys.add(kd(GLFW.GLFW_KEY_G, "G", null, 5.75f, 3.10f, 1.00f, 1.0f));
        keys.add(kd(GLFW.GLFW_KEY_H, "H", null, 6.75f, 3.10f, 1.00f, 1.0f));
        keys.add(kd(GLFW.GLFW_KEY_J, "J", null, 7.75f, 3.10f, 1.00f, 1.0f));
        keys.add(kd(GLFW.GLFW_KEY_K, "K", null, 8.75f, 3.10f, 1.00f, 1.0f));
        keys.add(kd(GLFW.GLFW_KEY_L, "L", null, 9.75f, 3.10f, 1.00f, 1.0f));
        keys.add(kd(GLFW.GLFW_KEY_SEMICOLON, ";", ":", 10.75f, 3.10f, 1.00f, 1.0f));
        keys.add(kd(GLFW.GLFW_KEY_APOSTROPHE, "'", "\"", 11.75f, 3.10f, 1.00f, 1.0f));
        keys.add(kd(GLFW.GLFW_KEY_ENTER, "Enter", null, 12.75f, 3.10f, 2.25f, 1.0f));
        keys.add(kd(GLFW.GLFW_KEY_KP_4, "4", null, 18.50f, 3.10f, 1.00f, 1.0f));
        keys.add(kd(GLFW.GLFW_KEY_KP_5, "5", null, 19.50f, 3.10f, 1.00f, 1.0f));
        keys.add(kd(GLFW.GLFW_KEY_KP_6, "6", null, 20.50f, 3.10f, 1.00f, 1.0f));

        keys.add(kd(GLFW.GLFW_KEY_LEFT_SHIFT, "Shift", null, 0.00f, 4.10f, 2.25f, 1.0f));
        keys.add(kd(GLFW.GLFW_KEY_Z, "Z", null, 2.25f, 4.10f, 1.00f, 1.0f));
        keys.add(kd(GLFW.GLFW_KEY_X, "X", null, 3.25f, 4.10f, 1.00f, 1.0f));
        keys.add(kd(GLFW.GLFW_KEY_C, "C", null, 4.25f, 4.10f, 1.00f, 1.0f));
        keys.add(kd(GLFW.GLFW_KEY_V, "V", null, 5.25f, 4.10f, 1.00f, 1.0f));
        keys.add(kd(GLFW.GLFW_KEY_B, "B", null, 6.25f, 4.10f, 1.00f, 1.0f));
        keys.add(kd(GLFW.GLFW_KEY_N, "N", null, 7.25f, 4.10f, 1.00f, 1.0f));
        keys.add(kd(GLFW.GLFW_KEY_M, "M", null, 8.25f, 4.10f, 1.00f, 1.0f));
        keys.add(kd(GLFW.GLFW_KEY_COMMA, ",", "<", 9.25f, 4.10f, 1.00f, 1.0f));
        keys.add(kd(GLFW.GLFW_KEY_PERIOD, ".", ">", 10.25f, 4.10f, 1.00f, 1.0f));
        keys.add(kd(GLFW.GLFW_KEY_SLASH, "/", "?", 11.25f, 4.10f, 1.00f, 1.0f));
        keys.add(kd(GLFW.GLFW_KEY_RIGHT_SHIFT, "Shift", null, 12.25f, 4.10f, 2.75f, 1.0f));
        keys.add(kd(GLFW.GLFW_KEY_UP, "↑", null, 16.25f, 4.10f, 1.00f, 1.0f));
        keys.add(kd(GLFW.GLFW_KEY_KP_1, "1", null, 18.50f, 4.10f, 1.00f, 1.0f));
        keys.add(kd(GLFW.GLFW_KEY_KP_2, "2", null, 19.50f, 4.10f, 1.00f, 1.0f));
        keys.add(kd(GLFW.GLFW_KEY_KP_3, "3", null, 20.50f, 4.10f, 1.00f, 1.0f));
        keys.add(kd(GLFW.GLFW_KEY_KP_ENTER, "Ent", null, 21.50f, 4.10f, 1.00f, 2.0f));

        keys.add(kd(GLFW.GLFW_KEY_LEFT_CONTROL, "Ctrl", null, 0.00f, 5.10f, 1.25f, 1.0f));
        keys.add(kd(GLFW.GLFW_KEY_LEFT_SUPER, "Win", null, 1.25f, 5.10f, 1.25f, 1.0f));
        keys.add(kd(GLFW.GLFW_KEY_LEFT_ALT, "Alt", null, 2.50f, 5.10f, 1.25f, 1.0f));
        keys.add(kd(GLFW.GLFW_KEY_SPACE, "Space", null, 3.75f, 5.10f, 6.25f, 1.0f));
        keys.add(kd(GLFW.GLFW_KEY_RIGHT_ALT, "Alt", null, 10.00f, 5.10f, 1.25f, 1.0f));
        keys.add(kd(GLFW.GLFW_KEY_RIGHT_SUPER, "Win", null, 11.25f, 5.10f, 1.25f, 1.0f));
        keys.add(kd(GLFW.GLFW_KEY_MENU, "Menu", null, 12.50f, 5.10f, 1.00f, 1.0f));
        keys.add(kd(GLFW.GLFW_KEY_RIGHT_CONTROL, "Ctrl", null, 13.50f, 5.10f, 1.50f, 1.0f));
        keys.add(kd(GLFW.GLFW_KEY_LEFT, "←", null, 15.25f, 5.10f, 1.00f, 1.0f));
        keys.add(kd(GLFW.GLFW_KEY_DOWN, "↓", null, 16.25f, 5.10f, 1.00f, 1.0f));
        keys.add(kd(GLFW.GLFW_KEY_RIGHT, "→", null, 17.25f, 5.10f, 1.00f, 1.0f));
        keys.add(kd(GLFW.GLFW_KEY_KP_0, "0", null, 18.50f, 5.10f, 2.00f, 1.0f));
        keys.add(kd(GLFW.GLFW_KEY_KP_DECIMAL, ".", null, 20.50f, 5.10f, 1.00f, 1.0f));
        KEYS = Collections.unmodifiableList(keys);
    }

    private static KeyDef kd(int glfwKey, String label, String subLabel, float gridX, float gridY, float width, float height) {
        return new KeyDef(glfwKey, label, subLabel, gridX, gridY, width, height);
    }
}
