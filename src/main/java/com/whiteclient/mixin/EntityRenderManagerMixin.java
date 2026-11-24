package com.whiteclient.mixin;

import com.whiteclient.modules.CarEmote;
import net.minecraft.client.render.entity.EntityRenderManager;
import net.minecraft.client.render.entity.state.EntityRenderState;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(EntityRenderManager.class)
public abstract class EntityRenderManagerMixin {
	@Inject(method = "getAndUpdateRenderState", at = @At("RETURN"), require = 0)
	private void whiteclient$carPose(Entity entity, float tickProgress, CallbackInfoReturnable<EntityRenderState> cir) {
		try {
			CarEmote.applyPose(entity, cir.getReturnValue());
		} catch (Throwable ignored) {
		}
	}
}
