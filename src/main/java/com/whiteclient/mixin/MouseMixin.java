package com.whiteclient.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.whiteclient.gui.Tilt3D;
import com.whiteclient.hud.ClickCounter;
import net.minecraft.client.Mouse;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.input.MouseInput;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Counts clicks for the CPS displays (read-only, doesn't change input). */
@Mixin(Mouse.class)
public abstract class MouseMixin {
	@Inject(method = "onMouseButton", at = @At("HEAD"), require = 0)
	private void whiteclient$countClick(long window, MouseInput input, int action, CallbackInfo ci) {
		if (action == 1) {
			try {
				ClickCounter.click(input.button());
			} catch (Throwable ignored) {
			}
		}
	}

	// ---- 3D inventories: the screen gets the mouse position inside the tilted panel

	private static Click whiteclient$map(Click click) {
		if (!Tilt3D.mapsInput()) {
			return click;
		}
		try {
			MouseInput input = (MouseInput) Click.class.getRecordComponents()[2].getAccessor().invoke(click);
			double x = click.x(), y = click.y();
			return new Click(Tilt3D.toVirtualX(x, y), Tilt3D.toVirtualY(x, y), input);
		} catch (Throwable t) {
			return click;
		}
	}

	@WrapOperation(method = {"onMouseButton", "onCursorPos", "updateMouse", "tick"}, at = @At(value = "INVOKE",
			target = "Lnet/minecraft/client/gui/screen/Screen;mouseClicked(Lnet/minecraft/client/gui/Click;Z)Z"), require = 0)
	private boolean whiteclient$tiltClick(Screen screen, Click click, boolean doubled, Operation<Boolean> original) {
		return original.call(screen, whiteclient$map(click), doubled);
	}

	@WrapOperation(method = {"onMouseButton", "onCursorPos", "updateMouse", "tick"}, at = @At(value = "INVOKE",
			target = "Lnet/minecraft/client/gui/screen/Screen;mouseReleased(Lnet/minecraft/client/gui/Click;)Z"), require = 0)
	private boolean whiteclient$tiltRelease(Screen screen, Click click, Operation<Boolean> original) {
		return original.call(screen, whiteclient$map(click));
	}

	@WrapOperation(method = {"onMouseButton", "onCursorPos", "updateMouse", "tick"}, at = @At(value = "INVOKE",
			target = "Lnet/minecraft/client/gui/screen/Screen;mouseDragged(Lnet/minecraft/client/gui/Click;DD)Z"), require = 0)
	private boolean whiteclient$tiltDrag(Screen screen, Click click, double dx, double dy, Operation<Boolean> original) {
		return original.call(screen, whiteclient$map(click), dx, dy);
	}

	@WrapOperation(method = {"onMouseButton", "onCursorPos", "updateMouse", "tick"}, at = @At(value = "INVOKE",
			target = "Lnet/minecraft/client/gui/screen/Screen;mouseMoved(DD)V"), require = 0)
	private void whiteclient$tiltMove(Screen screen, double x, double y, Operation<Void> original) {
		if (Tilt3D.mapsInput()) {
			original.call(screen, Tilt3D.toVirtualX(x, y), Tilt3D.toVirtualY(x, y));
		} else {
			original.call(screen, x, y);
		}
	}

	@WrapOperation(method = "onMouseScroll", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/client/gui/screen/Screen;mouseScrolled(DDDD)Z"), require = 0)
	private boolean whiteclient$tiltScroll(Screen screen, double x, double y, double h, double v, Operation<Boolean> original) {
		if (Tilt3D.mapsInput()) {
			return original.call(screen, Tilt3D.toVirtualX(x, y), Tilt3D.toVirtualY(x, y), h, v);
		}
		return original.call(screen, x, y, h, v);
	}
}
