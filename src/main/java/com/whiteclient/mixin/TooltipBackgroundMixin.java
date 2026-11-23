package com.whiteclient.mixin;

import com.whiteclient.modules.InventoryTheme;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.tooltip.TooltipBackgroundRenderer;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Rounded dark tooltips with a thin light edge, in the WHITECLIENT style. */
@Mixin(TooltipBackgroundRenderer.class)
public abstract class TooltipBackgroundMixin {
	@Inject(method = "render", at = @At("HEAD"), cancellable = true, require = 0)
	private static void whiteclient$tooltip(DrawContext context, int x, int y, int width, int height, Identifier texture, CallbackInfo ci) {
		try {
			if (texture == null && InventoryTheme.themedTooltips()) {
				InventoryTheme.drawTooltip(context, x, y, width, height);
				ci.cancel();
			}
		} catch (Throwable ignored) {
		}
	}
}
