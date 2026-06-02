package com.github.newvisualkeybing.platform.services;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;

public interface IPlatformHelper {

    String getPlatformName();

    boolean isModLoaded(String modId);

    /** Ids of every loaded mod, used to attribute keybinds to their owning mod. */
    default java.util.Set<String> getLoadedModIds() {
        return java.util.Set.of();
    }

    boolean isDevelopmentEnvironment();

    default String getEnvironmentName() {
        return isDevelopmentEnvironment() ? "development" : "production";
    }

    
    default String getModName(String modId) { return null; }

    
    default ConflictContext getConflictContext(KeyMapping mapping) {
        return ConflictContext.UNIVERSAL;
    }

    /**
     * Whether {@code mapping}'s conflict context can fire in the current scene right now. Used by
     * priority resolution to fall through from a higher-priority binding that cannot trigger here
     * (e.g. a GUI-only binding while in-game) to a lower-priority one that can, instead of letting
     * the inactive high-priority binding block it. The default derives activeness from the
     * conflict context and whether a screen is open; platforms with a richer context API override.
     */
    default boolean isContextActive(KeyMapping mapping) {
        Minecraft mc = Minecraft.getInstance();
        boolean screenOpen = mc != null && mc.screen != null;
        return switch (getConflictContext(mapping)) {
            case IN_GAME -> !screenOpen;
            case GUI -> screenOpen;
            case UNIVERSAL, UNKNOWN -> true;
        };
    }

    default InputModifier getKeyModifier(KeyMapping mapping) {
        return InputModifier.NONE;
    }

    default InputModifier getDefaultKeyModifier(KeyMapping mapping) {
        return InputModifier.NONE;
    }

    enum ConflictContext {
        UNIVERSAL, IN_GAME, GUI, UNKNOWN;

        public boolean conflicts(ConflictContext other) {
            if (this == UNIVERSAL || other == UNIVERSAL) return true;
            return this == other;
        }
    }

    enum InputModifier {
        NONE(""),
        CONTROL("Ctrl"),
        SHIFT("Shift"),
        ALT("Alt"),
        UNKNOWN("");

        private final String displayName;

        InputModifier(String displayName) {
            this.displayName = displayName;
        }

        public boolean isCombination() {
            return this != NONE && this != UNKNOWN;
        }

        public String displayName() {
            return displayName;
        }
    }
}
