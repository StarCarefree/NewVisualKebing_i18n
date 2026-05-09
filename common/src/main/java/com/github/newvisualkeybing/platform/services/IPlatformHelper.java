package com.github.newvisualkeybing.platform.services;

import net.minecraft.client.KeyMapping;

public interface IPlatformHelper {

    String getPlatformName();

    boolean isModLoaded(String modId);

    boolean isDevelopmentEnvironment();

    default String getEnvironmentName() {
        return isDevelopmentEnvironment() ? "development" : "production";
    }

    
    default String getModName(String modId) { return null; }

    
    default ConflictContext getConflictContext(KeyMapping mapping) {
        return ConflictContext.UNIVERSAL;
    }

    enum ConflictContext {
        UNIVERSAL, IN_GAME, GUI, UNKNOWN;

        public boolean conflicts(ConflictContext other) {
            if (this == UNIVERSAL || other == UNIVERSAL) return true;
            return this == other;
        }
    }
}
