package com.whiteclient.mixin;

import com.whiteclient.gui.ButtonStyle;
import com.whiteclient.gui.PauseStyle;
import net.minecraft.client.gui.ActiveTextCollector;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractButton;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(AbstractButton.class)
public abstract class PressableWidgetMixin {
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
}
