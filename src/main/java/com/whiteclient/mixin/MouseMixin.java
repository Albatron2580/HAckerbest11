package com.whiteclient.mixin;

import com.whiteclient.hud.ClickCounter;
import net.minecraft.client.MouseHandler;
//#if MC >= 1.21.9
import net.minecraft.client.input.MouseButtonInfo;
//#endif
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MouseHandler.class)
public abstract class MouseMixin {
	//#if MC >= 1.21.9
	@Inject(method = "onButton", at = @At("HEAD"), require = 0)
	private void whiteclient$countClick(long window, MouseButtonInfo input, int action, CallbackInfo ci) {
		int button = input.button();
	//#else
	//$ @Inject(method = "onPress", at = @At("HEAD"), require = 0)
	//$ private void whiteclient$countClick(long window, int button, int action, int mods, CallbackInfo ci) {
	//#endif
		if (action == 1) {
			try {
				ClickCounter.click(button);
			} catch (Throwable ignored) {
			}
		}
	}
}
