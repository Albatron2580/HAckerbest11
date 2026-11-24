package com.whiteclient.mixin;

import com.whiteclient.gui.ButtonStyle;
import com.whiteclient.gui.PauseStyle;
import net.minecraft.client.font.DrawnTextConsumer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.PressableWidget;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PressableWidget.class)
public abstract class PressableWidgetMixin {
	@Inject(method = "drawButton", at = @At("HEAD"), cancellable = true, require = 0)
	private void whiteclient$drawButton(DrawContext context, CallbackInfo ci) {
		try {
			if (PauseStyle.owns(this)) {
				PauseStyle.drawWidget(context, (PressableWidget) (Object) this);
				ci.cancel();
				return;
			}
			if (ButtonStyle.shouldStyle()) {
				ButtonStyle.draw(context, (PressableWidget) (Object) this);
				ci.cancel();
			}
		} catch (Throwable ignored) {
		}
	}

	@Inject(method = "drawLabel", at = @At("HEAD"), cancellable = true, require = 0)
	private void whiteclient$drawLabel(DrawnTextConsumer drawer, CallbackInfo ci) {
		try {
			if (PauseStyle.owns(this)) {
				ci.cancel();
			}
		} catch (Throwable ignored) {
		}
	}
}
