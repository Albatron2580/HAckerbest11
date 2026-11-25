package com.whiteclient.mixin;

import com.whiteclient.modules.InventoryTheme;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Mixin(AbstractContainerScreen.class)
public abstract class HandledScreenMixin {
	@ModifyArg(method = "renderLabels", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/client/gui/GuiGraphics;drawString(Lnet/minecraft/client/gui/Font;Lnet/minecraft/network/chat/Component;IIIZ)V"),
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
