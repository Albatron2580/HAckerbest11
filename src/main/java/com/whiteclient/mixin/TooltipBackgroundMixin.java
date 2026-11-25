package com.whiteclient.mixin;

import com.whiteclient.modules.InventoryTheme;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.tooltip.TooltipRenderUtil;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(TooltipRenderUtil.class)
public abstract class TooltipBackgroundMixin {
	@Inject(method = "renderTooltipBackground", at = @At("HEAD"), cancellable = true, require = 0)
	private static void whiteclient$tooltip(GuiGraphics context, int x, int y, int width, int height, Identifier texture, CallbackInfo ci) {
		try {
			if (texture == null && InventoryTheme.themedTooltips()) {
				InventoryTheme.drawTooltip(context, x, y, width, height);
				ci.cancel();
			}
		} catch (Throwable ignored) {
		}
	}
}
