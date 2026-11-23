package com.whiteclient.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.whiteclient.gui.Tilt3D;
import com.whiteclient.modules.InventoryTheme;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/** Light, readable container titles on the dark themed inventories. */
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

	/** The container background is drawn before the rest of the screen: tilt it too, on top of its 3D slab. */
	@WrapOperation(method = {"renderBackground", "render"}, at = @At(value = "INVOKE",
			target = "Lnet/minecraft/client/gui/screen/ingame/HandledScreen;drawBackground(Lnet/minecraft/client/gui/DrawContext;FII)V"), require = 0)
	private void whiteclient$tiltBackground(HandledScreen<?> self, DrawContext context, float delta, int mouseX, int mouseY, Operation<Void> original) {
		Screen s = (Screen) (Object) this;
		if (!Tilt3D.active(s)) {
			original.call(self, context, delta, mouseX, mouseY);
			return;
		}
		if (Tilt3D.inside) { // background drawn from render(): already tilted
			Tilt3D.drawSlab(context, s);
			original.call(self, context, delta, mouseX, mouseY);
			return;
		}
		Tilt3D.push(context);
		Tilt3D.inside = true;
		try {
			Tilt3D.drawSlab(context, s);
			original.call(self, context, delta, mouseX, mouseY);
		} finally {
			Tilt3D.inside = false;
			Tilt3D.pop(context);
		}
	}
}
