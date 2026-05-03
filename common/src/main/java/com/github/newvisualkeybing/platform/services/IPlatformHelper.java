package com.github.newvisualkeybing.platform.services;

import net.minecraft.client.KeyMapping;

public interface IPlatformHelper {

    String getPlatformName();

    boolean isModLoaded(String modId);

    boolean isDevelopmentEnvironment();

    default String getEnvironmentName() {
        return isDevelopmentEnvironment() ? "development" : "production";
    }

    /** 返回模组的人类可读显示名；找不到时返回 null。 */
    default String getModName(String modId) { return null; }

    /** 返回 KeyMapping 的冲突上下文（移植自 Forge IKeyConflictContext）。 */
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
