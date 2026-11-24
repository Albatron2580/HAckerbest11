package com.whiteclient.mixin;

import com.whiteclient.modules.InventoryTheme;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Mixin(HandledScreen.class)
public abstract class HandledScreenMixin {
	@ModifyArg(method = "drawForeground", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/client/gui/DrawContext;drawText(Lnet/minecraft/client/font/TextRenderer;Lnet/minecraft/text/Text;IIIZ)V"),
			index = 4, require = 0)
	private int whiteclient$titleColor(int color) {
		try {
			Integer c = InventoryTheme.titleColor();
			return c != null ? c : color;
		} catch (Throwable t) {
			return color;
		}
	}
}
