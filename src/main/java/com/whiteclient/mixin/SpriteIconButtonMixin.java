//#file MC >= 26.2
package com.whiteclient.mixin;

import com.whiteclient.gui.PauseStyle;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.SpriteIconButton;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(SpriteIconButton.class)
public abstract class SpriteIconButtonMixin {
	@Inject(method = "extractSprite", at = @At("HEAD"), cancellable = true, require = 0)
	private void whiteclient$hideIcon(GuiGraphicsExtractor graphics, int x, int y, CallbackInfo ci) {
		if (PauseStyle.owns(this)) {
			ci.cancel();
		}
	}

	@Inject(method = "extractLoadingStateIfLoading", at = @At("HEAD"), cancellable = true, require = 0)
	private void whiteclient$hideLoading(GuiGraphicsExtractor graphics, CallbackInfoReturnable<Boolean> cir) {
		if (PauseStyle.owns(this)) {
			cir.setReturnValue(false);
		}
	}
}
