package com.github.newvisualkeybing;

import net.minecraftforge.fml.common.Mod;

@Mod(Constants.MOD_ID)
public class NewVisualKeybing {

    public NewVisualKeybing() {
        Constants.LOG.info("Hello Forge world!");
        CommonClass.init();
    }
}
