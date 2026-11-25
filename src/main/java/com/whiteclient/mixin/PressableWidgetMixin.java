package com.whiteclient.mixin;

import com.whiteclient.gui.ButtonStyle;
import com.whiteclient.gui.PauseStyle;
//#if MC >= 1.21.11
import net.minecraft.client.gui.ActiveTextCollector;
//#else
//$ import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
//$ import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
//$ import com.mojang.blaze3d.pipeline.RenderPipeline;
//$ import net.minecraft.resources.Identifier;
//#endif
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractButton;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(AbstractButton.class)
public abstract class PressableWidgetMixin {
	//#if MC >= 1.21.11
	@Inject(method = "renderDefaultSprite", at = @At("HEAD"), cancellable = true, require = 0)
	private void whiteclient$drawButton(GuiGraphics context, CallbackInfo ci) {
		try {
			if (PauseStyle.owns(this)) {
				PauseStyle.drawWidget(context, (AbstractButton) (Object) this);
				ci.cancel();
				return;
			}
			if (ButtonStyle.shouldStyle()) {
				ButtonStyle.draw(context, (AbstractButton) (Object) this);
				ci.cancel();
			}
		} catch (Throwable ignored) {
		}
	}

	@Inject(method = "renderDefaultLabel", at = @At("HEAD"), cancellable = true, require = 0)
	private void whiteclient$drawLabel(ActiveTextCollector drawer, CallbackInfo ci) {
		try {
			if (PauseStyle.owns(this)) {
				ci.cancel();
			}
		} catch (Throwable ignored) {
		}
	}
	//#else
	//$ @Inject(method = "renderWidget", at = @At("HEAD"), cancellable = true, require = 0)
	//$ private void whiteclient$pauseOwned(GuiGraphics context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
	//$ 	try {
	//$ 		if (PauseStyle.owns(this)) {
	//$ 			PauseStyle.drawWidget(context, (AbstractButton) (Object) this);
	//$ 			ci.cancel();
	//$ 		}
	//$ 	} catch (Throwable ignored) {
	//$ 	}
	//$ }
	//$
	//$ @WrapOperation(method = "renderWidget", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GuiGraphics;blitSprite(Lcom/mojang/blaze3d/pipeline/RenderPipeline;Lnet/minecraft/resources/Identifier;IIIII)V"), require = 0)
	//$ private void whiteclient$sprite(GuiGraphics g, RenderPipeline pipeline, Identifier sprite, int x, int y, int w, int h, int color, Operation<Void> original) {
	//$ 	try {
	//$ 		if (ButtonStyle.shouldStyle()) {
	//$ 			ButtonStyle.draw(g, (AbstractButton) (Object) this);
	//$ 			return;
	//$ 		}
	//$ 	} catch (Throwable ignored) {
	//$ 	}
	//$ 	original.call(g, pipeline, sprite, x, y, w, h, color);
	//$ }
	//#endif
}
