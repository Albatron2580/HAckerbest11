package com.whiteclient.mixin;

import com.whiteclient.hud.ClickCounter;
import net.minecraft.client.Mouse;
import net.minecraft.client.input.MouseInput;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Mouse.class)
public abstract class MouseMixin {
	@Inject(method = "onMouseButton", at = @At("HEAD"), require = 0)
	private void whiteclient$countClick(long window, MouseInput input, int action, CallbackInfo ci) {
		if (action == 1) {
			try {
				ClickCounter.click(input.button());
			} catch (Throwable ignored) {
			}
		}
	}
}
