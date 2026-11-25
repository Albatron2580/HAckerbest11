package com.whiteclient.mixin;

import com.whiteclient.modules.CarEmote;
import net.minecraft.client.Camera;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(Camera.class)
public abstract class CameraMixin {
	@ModifyVariable(method = "getMaxZoom", at = @At("HEAD"), argsOnly = true, require = 0)
	private float whiteclient$carDistance(float distance) {
		try {
			return distance * CarEmote.cameraScale();
		} catch (Throwable t) {
			return distance;
		}
	}
}
