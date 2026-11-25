//#file MC < 1.21.6
package com.whiteclient.mixin;

import com.whiteclient.WhiteClient;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphics;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Gui.class)
public abstract class GuiOverlayMixin {
	@Inject(method = "renderCrosshair", at = @At("HEAD"), cancellable = true, require = 0)
	private void whiteclient$crosshair(GuiGraphics context, DeltaTracker tickCounter, CallbackInfo ci) {
		WhiteClient wc = WhiteClient.get();
		if (wc != null && wc.drawOwnCrosshair(context)) {
			ci.cancel();
		}
	}

	@Inject(method = "renderEffects", at = @At("HEAD"), cancellable = true, require = 0)
	private void whiteclient$effects(GuiGraphics context, DeltaTracker tickCounter, CallbackInfo ci) {
		WhiteClient wc = WhiteClient.get();
		if (wc != null && wc.hidesVanillaEffects()) {
			ci.cancel();
		}
	}
}
