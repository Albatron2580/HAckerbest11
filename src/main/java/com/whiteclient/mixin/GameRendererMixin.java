package com.whiteclient.mixin;

import com.whiteclient.modules.Zoom;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(GameRenderer.class)
public abstract class GameRendererMixin {
	@Inject(method = "getFov", at = @At("RETURN"), cancellable = true, require = 0)
	private void whiteclient$zoom(Camera camera, float tickProgress, boolean changingFov, CallbackInfoReturnable<Float> cir) {
		if (!changingFov) {
			return;
		}
		try {
			float s = Zoom.fovScale();
			if (s != 1f) {
				cir.setReturnValue(cir.getReturnValue() * s);
			}
		} catch (Throwable ignored) {
		}
	}
}
