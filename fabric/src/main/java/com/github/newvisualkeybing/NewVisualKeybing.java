package com.github.newvisualkeybing;

import net.fabricmc.api.ModInitializer;

public class NewVisualKeybing implements ModInitializer {

    @Override
    public void onInitialize() {
        Constants.LOG.info("Hello Fabric world!");
        CommonClass.init();
    }
}
