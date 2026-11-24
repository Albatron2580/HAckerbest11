package com.whiteclient.hud;

import com.whiteclient.gui.Render2D;
import com.whiteclient.render.Colors;
import net.minecraft.client.gui.DrawContext;

public final class Hud {
	public static final int TEXT = 0xFFFFFFFF;
	public static final int SUB = 0xFFA8A8B4;

	private Hud() {
	}

	public static void panel(DrawContext ctx, float x, float y, float w, float h, float alpha) {
		Render2D.roundRect(ctx, x, y, w, h, 6, Colors.withAlpha(0x0D0D11, (int) (alpha * 0.82f * 255)));
		Render2D.roundRectOutline(ctx, x, y, w, h, 6, 1, Colors.withAlpha(0xFFFFFF, (int) (alpha * 0.14f * 255)));
	}

	public static int alpha(int argb, float a) {
		return Colors.mulAlpha(argb, a);
	}
}
