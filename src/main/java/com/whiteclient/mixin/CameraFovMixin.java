//#file MC >= 26.1
package com.whiteclient.mixin;

import com.whiteclient.modules.Zoom;
import net.minecraft.client.Camera;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Camera.class)
public abstract class CameraFovMixin {
	@Inject(method = "calculateFov", at = @At("RETURN"), cancellable = true, require = 0)
	private void whiteclient$zoom(float partialTicks, CallbackInfoReturnable<Float> cir) {
		try {
			float s = Zoom.fovScale();
			if (s != 1f) {
				cir.setReturnValue(cir.getReturnValue() * s);
			}
		} catch (Throwable ignored) {
		}
	}
}
