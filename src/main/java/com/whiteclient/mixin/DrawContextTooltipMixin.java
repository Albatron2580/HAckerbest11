package com.whiteclient.mixin;

import com.whiteclient.gui.Tilt3D;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.tooltip.TooltipComponent;
import net.minecraft.client.gui.tooltip.TooltipPositioner;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

/** Tooltips of a 3D-tilted inventory are drawn flat, next to the real mouse cursor. */
@Mixin(DrawContext.class)
public abstract class DrawContextTooltipMixin {
	@Unique
	private static boolean whiteclient$again;

	@Inject(method = "drawTooltipImmediately", at = @At("HEAD"), cancellable = true, require = 0)
	private void whiteclient$flatTooltip(TextRenderer textRenderer, List<TooltipComponent> components, int x, int y,
										 TooltipPositioner positioner, Identifier texture, CallbackInfo ci) {
		if (whiteclient$again || Tilt3D.inside || !Tilt3D.active(MinecraftClient.getInstance().currentScreen)) {
			return;
		}
		int rx = (int) Math.round(Tilt3D.toRealX(x, y)), ry = (int) Math.round(Tilt3D.toRealY(x, y));
		if (rx == x && ry == y) {
			return;
		}
		whiteclient$again = true;
		try {
			((DrawContextInvoker) (Object) this).whiteclient$tooltipNow(textRenderer, components, rx, ry, positioner, texture);
		} finally {
			whiteclient$again = false;
		}
		ci.cancel();
	}
}
