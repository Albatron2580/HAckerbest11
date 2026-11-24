package com.whiteclient.gui;

import com.whiteclient.WhiteClient;
import com.whiteclient.render.BackgroundShader;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;

import java.util.Map;
import java.util.WeakHashMap;

/**
 * The main-menu look for the other menus (multiplayer, singleplayer, options...): the silk background, the
 * big planet on the left and small planets drifting up the sides, with the middle kept dark so lists read well.
 */
public final class MenuScene {
	private static final Map<Screen, Long> OPENED = new WeakHashMap<>();

	private MenuScene() {
	}

	private static float intro(Screen s, float delay) {
		long now = System.nanoTime();
		Long t0 = OPENED.computeIfAbsent(s, k -> now);
		float t = (now - t0) / 1.0e9f - delay;
		float k = Math.max(0f, Math.min(1f, t / 0.45f));
		return 1f - (1f - k) * (1f - k) * (1f - k);
	}

	/** @param contentHalf half the width of the menu's content column (planets stay outside it) */
	public static void draw(DrawContext ctx, Screen s, float contentHalf) {
		draw(ctx, s, contentHalf, true);
	}

	/** @param fadeIn let the planets fade in when the screen opens */
	public static void draw(DrawContext ctx, Screen s, float contentHalf, boolean fadeIn) {
		int width = s.width, height = s.height;
		float time = WhiteClient.get().time();
		MenuBackgroundRenderer.draw(ctx, width, height, 1f, BackgroundShader.SILK);
		ctx.fillGradient(0, 0, width, height, 0x38000000, 0x8A000000);
		Render2D.hGradient(ctx, 0, 0, width * 0.5f, height, 0x50000000, 0x00000000);

		// big planet on the left, sliding a little off the edge on narrow windows
		float br = Math.max(40, Math.min(140, height * 0.25f));
		float bx = Math.min(width * 0.13f, width / 2f - contentHalf - br * 0.55f);
		float by = height * 0.62f + (float) Math.sin(time * 0.35f) * 3;
		float ba = fadeIn ? intro(s, 0f) : 1f;
		Render2D.bigPlanet(ctx, bx, by + (1 - ba) * 14, br, time, ba * 0.9f);

		// small planets drifting up both sides
		float pa = fadeIn ? intro(s, 0.15f) : 1f;
		float side = Math.max(30, width / 2f - contentHalf);
		for (int i = 0; i < 9; i++) {
			float seed = i * 12.9898f + 7.3f;
			float fx = frac(seed * 0.37f);
			float sp = 0.010f + 0.012f * frac(seed * 0.13f);
			float fy = frac(seed * 0.61f + time * sp);
			float px = (i % 2 == 0 ? side * fx : width - side * fx) + (float) Math.sin(time * 0.25f + seed) * 8;
			float py = height * (1.1f - fy * 1.2f);
			float r = 2f + 3f * frac(seed * 0.91f);
			float edge = Math.min(1f, Math.min(fy * 6f, (1f - fy) * 6f));
			float a = (0.18f + 0.32f * frac(seed * 0.23f)) * edge * pa;
			float ex = (px - bx) / (br * 1.9f), ey = (py - by) / (br * 1.15f);
			float d = ex * ex + ey * ey;
			if (d < 1f) {
				a *= Math.max(0f, (d - 0.7f) / 0.3f);
			}
			if (a > 0.02f) {
				Render2D.planetLogo(ctx, px, py, r, time * (0.6f + frac(seed) * 0.8f) + seed, (int) (255 * a));
			}
		}
		// a soft dark column behind the content so lists and text stay easy to read
		float cw = contentHalf + 24;
		Render2D.hGradient(ctx, width / 2f - cw - 40, 0, 40, height, 0x00000000, 0x30000000);
		ctx.fill(Math.round(width / 2f - cw), 0, Math.round(width / 2f + cw), height, 0x30000000);
		Render2D.hGradient(ctx, width / 2f + cw, 0, 40, height, 0x30000000, 0x00000000);
	}

	private static float frac(float v) {
		return v - (float) Math.floor(v);
	}
}
