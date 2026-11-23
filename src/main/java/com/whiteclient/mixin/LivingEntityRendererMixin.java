package com.whiteclient.mixin;

import com.whiteclient.modules.NameLogo;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.entity.LivingEntityRenderer;
import net.minecraft.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Shows your own name tag in third person (with the logo), like other clients do. */
@Mixin(LivingEntityRenderer.class)
public abstract class LivingEntityRendererMixin {
	@Inject(method = "hasLabel(Lnet/minecraft/entity/LivingEntity;D)Z", at = @At("RETURN"), cancellable = true, require = 0)
	private void whiteclient$ownLabel(LivingEntity entity, double squaredDistance, CallbackInfoReturnable<Boolean> cir) {
		try {
			NameLogo n = NameLogo.get();
			MinecraftClient mc = MinecraftClient.getInstance();
			if (n != null && n.isEnabled() && n.ownTag.isOn() && entity == mc.player && !cir.getReturnValueZ()
					&& !mc.options.getPerspective().isFirstPerson() && MinecraftClient.isHudEnabled() && !entity.isInvisible()
					&& mc.currentScreen == null) {
				cir.setReturnValue(true);
			}
		} catch (Throwable ignored) {
		}
	}
}
