package com.whiteclient.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.whiteclient.gui.Tilt3D;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Draws 3D-tilted inventories: the screen's own drawing happens inside the tilt, with the mouse mapped into it. */
@Mixin(Screen.class)
public abstract class ScreenTiltMixin {
	@Inject(method = "renderWithTooltip", at = @At("HEAD"), require = 0)
	private void whiteclient$tiltFrame(DrawContext context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
		try {
			Tilt3D.frame((Screen) (Object) this);
		} catch (Throwable t) {
			Tilt3D.inside = false;
		}
	}

	@WrapOperation(method = "renderWithTooltip", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/client/gui/screen/Screen;render(Lnet/minecraft/client/gui/DrawContext;IIF)V"), require = 0)
	private void whiteclient$tiltRender(Screen screen, DrawContext context, int mouseX, int mouseY, float delta, Operation<Void> original) {
		if (!Tilt3D.active(screen)) {
			original.call(screen, context, mouseX, mouseY, delta);
			return;
		}
		// the real mouse position, mapped into the tilted panel
		double rx = mouseX, ry = mouseY;
		try {
			net.minecraft.client.MinecraftClient mc = net.minecraft.client.MinecraftClient.getInstance();
			rx = mc.mouse.getX() * mc.getWindow().getScaledWidth() / Math.max(1, mc.getWindow().getWidth());
			ry = mc.mouse.getY() * mc.getWindow().getScaledHeight() / Math.max(1, mc.getWindow().getHeight());
		} catch (Throwable ignored) {
		}
		int vx = (int) Math.floor(Tilt3D.toVirtualX(rx, ry)), vy = (int) Math.floor(Tilt3D.toVirtualY(rx, ry));
		Tilt3D.push(context);
		Tilt3D.inside = true;
		try {
			original.call(screen, context, vx, vy, delta);
		} finally {
			Tilt3D.inside = false;
			Tilt3D.pop(context);
		}
	}
}
