package com.whiteclient.mixin;

import com.whiteclient.gui.ButtonStyle;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(AbstractSliderButton.class)
public abstract class SliderWidgetMixin extends AbstractWidget {
	@Shadow
	protected double value;

	public SliderWidgetMixin(int x, int y, int width, int height, Component message) {
		super(x, y, width, height, message);
	}

	@Inject(method = "renderWidget", at = @At("HEAD"), cancellable = true, require = 0)
	private void whiteclient$slider(GuiGraphics context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
		try {
			if (ButtonStyle.shouldStyle()) {
				ButtonStyle.drawSlider(context, this, value);
				ci.cancel();
			}
		} catch (Throwable ignored) {
		}
	}
}
