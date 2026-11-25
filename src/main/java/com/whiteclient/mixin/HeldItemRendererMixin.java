package com.whiteclient.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.whiteclient.modules.HandView;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ItemInHandRenderer.class)
public abstract class HeldItemRendererMixin {
	@Inject(method = "renderArmWithItem", at = @At("HEAD"), require = 0)
	private void whiteclient$handStart(AbstractClientPlayer player, float tickProgress, float pitch, InteractionHand hand, float swingProgress,
									   ItemStack item, float equipProgress, PoseStack matrices, SubmitNodeCollector queue, int light, CallbackInfo ci) {
		HandView.begin(player, hand, matrices);
	}

	@Inject(method = "renderArmWithItem", at = @At("RETURN"), require = 0)
	private void whiteclient$handEnd(AbstractClientPlayer player, float tickProgress, float pitch, InteractionHand hand, float swingProgress,
									 ItemStack item, float equipProgress, PoseStack matrices, SubmitNodeCollector queue, int light, CallbackInfo ci) {
		HandView.end(matrices);
	}
}
