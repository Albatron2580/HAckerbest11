//#file MC < 1.21.2
package com.whiteclient.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import com.whiteclient.modules.CarEmote;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(EntityRenderDispatcher.class)
public abstract class LegacyCarOffsetMixin {
	@ModifyExpressionValue(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/entity/EntityRenderer;getRenderOffset(Lnet/minecraft/world/entity/Entity;F)Lnet/minecraft/world/phys/Vec3;"), require = 0)
	private Vec3 whiteclient$carLift(Vec3 offset, @Local(argsOnly = true) Entity entity) {
		try {
			float lift = CarEmote.lift(entity);
			return lift == 0f ? offset : offset.add(0, lift, 0);
		} catch (Throwable ignored) {
			return offset;
		}
	}
}
