//#file MC >= 26.3
package com.whiteclient.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.blaze3d.vertex.PoseStack;
import com.whiteclient.modules.HandView;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.FirstPersonHandsAndItemsRenderer;
import net.minecraft.world.InteractionHand;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(FirstPersonHandsAndItemsRenderer.class)
public abstract class HandItemsMixin {
	@Inject(method = "submitArmWithItem", at = @At("HEAD"), require = 0)
	private void whiteclient$handStart(CallbackInfo ci, @Local(argsOnly = true) InteractionHand hand, @Local(argsOnly = true) PoseStack poseStack) {
		HandView.begin(Minecraft.getInstance().player, hand, poseStack);
	}

	@Inject(method = "submitArmWithItem", at = @At("RETURN"), require = 0)
	private void whiteclient$handEnd(CallbackInfo ci, @Local(argsOnly = true) PoseStack poseStack) {
		HandView.end(poseStack);
	}
}
