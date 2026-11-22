package com.whiteclient.mixin;

import com.whiteclient.gui.ButtonStyle;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.PressableWidget;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Replaces the grey button texture with the rounded WHITECLIENT style in menus. */
@Mixin(PressableWidget.class)
public abstract class PressableWidgetMixin {
	@Inject(method = "drawButton", at = @At("HEAD"), cancellable = true, require = 0)
	private void whiteclient$drawButton(DrawContext context, CallbackInfo ci) {
		try {
			if (ButtonStyle.shouldStyle()) {
				ButtonStyle.draw(context, (PressableWidget) (Object) this);
				ci.cancel();
			}
		} catch (Throwable ignored) {
			// never break vanilla buttons
		}
	}
}
