package com.github.newvisualkeybing.mixin;

import com.github.newvisualkeybing.client.screen.KeybindViewerScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.OptionsSubScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.controls.ControlsScreen;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ControlsScreen.class)
public abstract class MixinControlsScreen extends OptionsSubScreen {

    protected MixinControlsScreen(Screen lastScreen, Options options, Component title) {
        super(lastScreen, options, title);
    }

    @Inject(method = "init", at = @At("TAIL"))
    private void newvisualkeybing$injectViewerButton(CallbackInfo ci) {
        int x = this.width / 2 + 160;
        int y = this.height / 6 - 12;
        addRenderableWidget(Button.builder(Component.literal("KV"), button ->
                Minecraft.getInstance().setScreen(new KeybindViewerScreen((Screen) (Object) this)))
                .bounds(x, y, 30, 20)
                .build());
    }
}
