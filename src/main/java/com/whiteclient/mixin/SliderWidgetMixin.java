package com.whiteclient.mixin;

import com.whiteclient.gui.ButtonStyle;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.client.gui.widget.SliderWidget;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(SliderWidget.class)
public abstract class SliderWidgetMixin extends ClickableWidget {
	@Shadow
	protected double value;

	public SliderWidgetMixin(int x, int y, int width, int height, Text message) {
		super(x, y, width, height, message);
	}

	@Inject(method = "renderWidget", at = @At("HEAD"), cancellable = true, require = 0)
	private void whiteclient$slider(DrawContext context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
		try {
			if (ButtonStyle.shouldStyle()) {
				ButtonStyle.drawSlider(context, this, value);
				ci.cancel();
			}
		} catch (Throwable ignored) {
		}
	}
}
