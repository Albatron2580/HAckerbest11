package com.whiteclient.gui;

import com.whiteclient.render.Colors;
import com.whiteclient.update.AutoUpdater;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;

/** A small pill at the top of the main and pause menu when an update is downloading or ready to install. */
public final class UpdateBadge {
	private UpdateBadge() {
	}

	public static void draw(DrawContext ctx, TextRenderer tr, int width, int height, float time) {
		String text = AutoUpdater.badge();
		if (text == null) {
			return;
		}
		boolean ready = AutoUpdater.readyVersion() != null;
		float scale = 0.85f;
		float tw = UiFont.width(tr, text) * scale;
		float w = tw + 30, h = 16;
		// top middle, between the account / world chip on the left and the icon buttons on the right
		float x = width / 2f - w / 2f, y = 12;
		float pulse = 0.5f + 0.5f * (float) Math.sin(time * 3.0);
		Render2D.roundRect(ctx, x, y, w, h, 8, Colors.argb(0x0B0B10, 0.88f));
		Render2D.roundRectOutline(ctx, x, y, w, h, 8, 1, Colors.argb(ready ? 0x8BE39A : 0xFFFFFF, 0.35f + 0.3f * pulse));
		// down arrow
		float ax = x + 12, ay = y + h / 2f;
		int c = Colors.argb(ready ? 0x8BE39A : 0xEDEDF2, 1f);
		Render2D.roundRect(ctx, ax - 0.75f, ay - 4.5f, 1.5f, 6f, 0.75f, c);
		Render2D.roundRect(ctx, ax - 3.5f, ay + 2.5f, 7f, 1.3f, 0.6f, c);
		UiFont.drawScaled(ctx, tr, text, x + 22, y + 5f, scale, 0xFFEDEDF2);
	}
}
