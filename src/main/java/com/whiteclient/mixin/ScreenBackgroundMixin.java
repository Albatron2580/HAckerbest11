//#file MC < 1.21.6
package com.whiteclient.mixin;

import com.whiteclient.gui.MenuIntegration;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Screen.class)
public abstract class ScreenBackgroundMixin {
	@Inject(method = "renderBackground", at = @At("TAIL"), require = 0)
	private void whiteclient$afterBackground(GuiGraphics context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
		try {
			MenuIntegration.fireBackground((Screen) (Object) this, context, mouseX, mouseY, delta);
		} catch (Throwable ignored) {
		}
	}
}
