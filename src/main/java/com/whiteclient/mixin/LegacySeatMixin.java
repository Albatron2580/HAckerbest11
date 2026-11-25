//#file MC < 1.21.2
package com.whiteclient.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.whiteclient.modules.CarEmote;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.world.entity.LivingEntity;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LivingEntityRenderer.class)
public abstract class LegacySeatMixin {
	@Shadow
	protected EntityModel<?> model;

	@Inject(method = "render(Lnet/minecraft/world/entity/LivingEntity;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
			at = @At(value = "FIELD", target = "Lnet/minecraft/client/model/EntityModel;riding:Z", opcode = Opcodes.PUTFIELD, shift = At.Shift.AFTER), require = 0)
	private void whiteclient$seat(LivingEntity entity, float yaw, float delta, PoseStack poseStack, MultiBufferSource buffers, int light, CallbackInfo ci) {
		try {
			if (CarEmote.seated(entity)) {
				model.riding = true;
			}
		} catch (Throwable ignored) {
		}
	}
}
